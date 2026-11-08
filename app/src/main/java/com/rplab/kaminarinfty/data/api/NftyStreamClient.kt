package com.rplab.kaminarinfty.data.api

import android.util.Base64
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyAction
import com.rplab.kaminarinfty.model.NftyAttachment
import com.rplab.kaminarinfty.model.NftyErrorCategory
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.NftyPriority
import com.rplab.kaminarinfty.model.ServerConfig
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

sealed class NftyStreamEvent {
    data class Connected(val authState: AuthState, val httpCode: Int) : NftyStreamEvent()
    data class MessageReceived(val message: NftyMessage) : NftyStreamEvent()
    data class KeepAlive(val timestamp: Long) : NftyStreamEvent()
    data class Error(val category: NftyErrorCategory, val message: String, val canRetry: Boolean) : NftyStreamEvent()
}

class NftyStreamClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS) // Read timeout 0 for long-lived SSE streaming
        .writeTimeout(15, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()
) {

    /**
     * Checks server reachability and credentials against endpoint using a short poll request.
     */
    fun testConnection(config: ServerConfig): Result<AuthState> {
        val baseUrl = config.serverUrl.trim().trimEnd('/')
        if (!baseUrl.startsWith("https://", ignoreCase = true) && !baseUrl.startsWith("http://", ignoreCase = true)) {
            return Result.failure(IllegalArgumentException("Server URL must include https://"))
        }

        // Test with a lightweight query to verify status
        val testUrl = "$baseUrl/v1/health" // Standard ntfy health endpoint or root
        val requestBuilder = Request.Builder()
            .url(testUrl)
            .get()
            .header("User-Agent", "KaminariNfty-Android/1.0.0")

        applyAuthHeaders(requestBuilder, config)

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                when (response.code) {
                    200, 204 -> {
                        val authState = when (config.authMethod) {
                            AuthMethod.GUEST -> AuthState.GUEST
                            AuthMethod.USERNAME_PASSWORD, AuthMethod.ACCESS_TOKEN ->
                                if (config.tokenOrPassword.isNotBlank()) AuthState.AUTHENTICATED else AuthState.GUEST
                        }
                        Result.success(authState)
                    }
                    401 -> Result.failure(IOException("HTTP 401 Unauthorized: Credentials rejected"))
                    403 -> Result.failure(IOException("HTTP 403 Forbidden: Server policy denied access"))
                    404 -> {
                        // /v1/health might be disabled or filtered; test against root
                        testRootEndpoint(baseUrl, config)
                    }
                    else -> Result.failure(IOException("Server returned HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(mapException(e).second)
        }
    }

    private fun testRootEndpoint(baseUrl: String, config: ServerConfig): Result<AuthState> {
        val requestBuilder = Request.Builder()
            .url(baseUrl)
            .get()
            .header("User-Agent", "KaminariNfty-Android/1.0.0")
        applyAuthHeaders(requestBuilder, config)

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful || response.code == 302 || response.code == 301) {
                    val authState = when (config.authMethod) {
                        AuthMethod.GUEST -> AuthState.GUEST
                        else -> if (config.tokenOrPassword.isNotBlank()) AuthState.AUTHENTICATED else AuthState.GUEST
                    }
                    Result.success(authState)
                } else {
                    Result.failure(IOException("Server returned HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(mapException(e).second)
        }
    }

    /**
     * Subscribes to ntfy JSON stream for the specified topics.
     * Yields events incrementally without buffering infinite response.
     */
    fun openJsonStream(
        config: ServerConfig,
        topics: List<String>,
        since: String = "12h"
    ): Flow<NftyStreamEvent> = flow {
        if (topics.isEmpty()) {
            emit(NftyStreamEvent.Error(NftyErrorCategory.UNREACHABLE, "No topics configured for subscription", false))
            return@flow
        }

        val baseUrl = config.serverUrl.trim().trimEnd('/')
        val joinedTopics = topics.joinToString(",")
        val streamUrl = "$baseUrl/$joinedTopics/json?since=$since"

        val requestBuilder = Request.Builder()
            .url(streamUrl)
            .get()
            .header("Accept", "application/x-ndjson, application/json")
            .header("User-Agent", "KaminariNfty-Android/1.0.0")

        applyAuthHeaders(requestBuilder, config)

        var response: Response? = null
        try {
            response = client.newCall(requestBuilder.build()).execute()

            when (response.code) {
                200 -> {
                    val establishedState = when (config.authMethod) {
                        AuthMethod.GUEST -> AuthState.GUEST
                        AuthMethod.USERNAME_PASSWORD, AuthMethod.ACCESS_TOKEN ->
                            if (config.tokenOrPassword.isNotBlank()) AuthState.AUTHENTICATED else AuthState.GUEST
                    }
                    emit(NftyStreamEvent.Connected(establishedState, response.code))

                    // Parse stream incrementally line by line
                    val source = response.body?.source() ?: throw IOException("Empty response body from ntfy")
                    while (currentCoroutineContext().isActive && !source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (line.isBlank()) continue

                        parseJsonLine(line)?.let { event ->
                            emit(event)
                        }
                    }
                }
                401 -> {
                    emit(NftyStreamEvent.Error(NftyErrorCategory.AUTH, "HTTP 401 Unauthorized: Invalid credentials", false))
                }
                403 -> {
                    emit(NftyStreamEvent.Error(NftyErrorCategory.FORBIDDEN, "HTTP 403 Forbidden: Access denied to subscribed topic(s)", false))
                }
                else -> {
                    emit(NftyStreamEvent.Error(NftyErrorCategory.UNREACHABLE, "Server returned HTTP ${response.code}", true))
                }
            }
        } catch (e: Exception) {
            val (category, exception) = mapException(e)
            emit(NftyStreamEvent.Error(category, exception.message ?: "Connection failure", true))
        } finally {
            response?.close()
        }
    }

    private fun parseJsonLine(line: String): NftyStreamEvent? {
        return try {
            val json = JSONObject(line)
            val eventType = json.optString("event")

            when (eventType) {
                "open" -> null
                "keepalive" -> {
                    val time = json.optLong("time", System.currentTimeMillis() / 1000) * 1000L
                    NftyStreamEvent.KeepAlive(time)
                }
                "message" -> {
                    val rawTime = json.optLong("time", System.currentTimeMillis() / 1000)
                    val timeMillis = if (rawTime < 10000000000L) rawTime * 1000L else rawTime

                    val id = json.optString("id", "msg_${System.currentTimeMillis()}")
                    val topic = json.optString("topic", "general")
                    val title = if (json.has("title") && !json.isNull("title")) json.optString("title") else null
                    val message = json.optString("message", "")
                    val priority = NftyPriority.fromLevel(json.optInt("priority", 3))

                    val tagsList = mutableListOf<String>()
                    val tagsArray = json.optJSONArray("tags")
                    if (tagsArray != null) {
                        for (i in 0 until tagsArray.length()) {
                            tagsList.add(tagsArray.getString(i))
                        }
                    }

                    val click = if (json.has("click") && !json.isNull("click")) json.optString("click") else null

                    var attachment: NftyAttachment? = null
                    val attachmentJson = json.optJSONObject("attachment")
                    if (attachmentJson != null) {
                        attachment = NftyAttachment(
                            name = attachmentJson.optString("name", "attachment"),
                            url = attachmentJson.optString("url", ""),
                            type = attachmentJson.optString("type", null),
                            size = if (attachmentJson.has("size")) attachmentJson.optLong("size") else null,
                        )
                    }

                    val actionsList = mutableListOf<NftyAction>()
                    val actionsArray = json.optJSONArray("actions")
                    if (actionsArray != null) {
                        for (i in 0 until actionsArray.length()) {
                            val actJson = actionsArray.getJSONObject(i)
                            actionsList.add(
                                NftyAction(
                                    id = actJson.optString("id", "act_$i"),
                                    action = actJson.optString("action", "view"),
                                    label = actJson.optString("label", "Action"),
                                    url = actJson.optString("url", null),
                                )
                            )
                        }
                    }

                    val nftyMessage = NftyMessage(
                        id = id,
                        time = timeMillis,
                        event = "message",
                        topic = topic,
                        title = title,
                        message = message,
                        priority = priority,
                        tags = tagsList,
                        click = click,
                        attachment = attachment,
                        actions = actionsList,
                        isRead = false,
                        isSaved = false,
                        isSample = false, // Live incoming message from server
                    )
                    NftyStreamEvent.MessageReceived(nftyMessage)
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun applyAuthHeaders(builder: Request.Builder, config: ServerConfig) {
        when (config.authMethod) {
            AuthMethod.GUEST -> {
                // Anonymous guest access: omit Authorization header
            }
            AuthMethod.USERNAME_PASSWORD -> {
                if (config.username.isNotBlank() && config.tokenOrPassword.isNotBlank()) {
                    val basicAuth = Credentials.basic(config.username, config.tokenOrPassword)
                    builder.header("Authorization", basicAuth)
                }
            }
            AuthMethod.ACCESS_TOKEN -> {
                if (config.tokenOrPassword.isNotBlank()) {
                    builder.header("Authorization", "Bearer ${config.tokenOrPassword.trim()}")
                }
            }
        }
    }

    private fun mapException(e: Exception): Pair<NftyErrorCategory, Exception> {
        return when (e) {
            is UnknownHostException -> Pair(NftyErrorCategory.DNS, IOException("DNS Resolution Error: Cannot resolve '${e.message}'"))
            is SSLException -> Pair(NftyErrorCategory.TLS, IOException("TLS Handshake Failure: Endpoint certificate verification failed"))
            is SocketTimeoutException -> Pair(NftyErrorCategory.TIMEOUT, IOException("Connection Timed Out: Server did not respond within deadline"))
            is ConnectException -> Pair(NftyErrorCategory.UNREACHABLE, IOException("Server Unreachable: Connection refused on port 443"))
            else -> Pair(NftyErrorCategory.STREAM, IOException(e.message ?: "Network stream failure"))
        }
    }
}
