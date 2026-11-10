package com.rplab.kaminarinfty.data.api

import com.rplab.kaminarinfty.model.ServerConfig
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * One publish intent, independent of the profile it will be sent through.
 * Keeping this free of credentials lets validation be shared and tested
 * without ever touching a secret.
 */
data class PublishRequest(
    val topic: String,
    val message: String,
    val title: String? = null,
    val priority: Int = PRIORITY_DEFAULT,
    val tags: List<String> = emptyList(),
    val clickUrl: String? = null,
    val attachUrl: String? = null,
    /** ntfy delay: a duration such as `30m` or a Unix timestamp in seconds. */
    val delay: String? = null,
) {
    companion object {
        const val PRIORITY_MIN = 1
        const val PRIORITY_MAX = 5
        const val PRIORITY_DEFAULT = 3
    }
}

/**
 * Outcome of a publish attempt.
 *
 * [Accepted] means the ntfy server accepted the publish. It does **not** mean
 * any recipient received the message.
 */
sealed class PublishResult {
    data class Accepted(val serverMessageId: String?) : PublishResult()
    data class ValidationFailed(val reason: String) : PublishResult()
    data class AuthenticationFailed(val statusCode: Int, val reason: String) : PublishResult()
    data class PermissionFailed(val statusCode: Int, val reason: String) : PublishResult()
    data class ServerError(val statusCode: Int, val reason: String) : PublishResult()

    /** Definite transport failure: the request never reached a decision. */
    data class NetworkError(val reason: String, val cause: IOException? = null) : PublishResult()

    /** Outcome unknown (e.g. a non-transport failure after the request was sent). Never auto-resend. */
    data class Ambiguous(val reason: String) : PublishResult()
}

interface NtfyPublish {
    suspend fun publish(config: ServerConfig, request: PublishRequest): PublishResult
}

/**
 * HTTP publish client for ntfy.
 *
 * Validation ([PublishValidator]) and authentication ([NtfyAuth]) are separate
 * collaborators. This class only builds the request and maps the response.
 * Timeouts are bounded; it never retries and never auto-resends an ambiguous
 * result. Credentials never appear in a URL.
 */
class NtfyPublishClient(
    private val httpClient: OkHttpClient = defaultClient(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : NtfyPublish {

    override suspend fun publish(config: ServerConfig, request: PublishRequest): PublishResult {
        PublishValidator.validate(config, request)?.let { return PublishResult.ValidationFailed(it) }

        val url = NtfyEndpoint.publishUrl(config.serverUrl, request.topic, config.useHttpsOnly)
            .getOrElse { failure ->
                return PublishResult.ValidationFailed(failure.message ?: "Invalid publish URL")
            }

        val useJson = request.title != null ||
            request.tags.isNotEmpty() ||
            request.clickUrl != null ||
            request.attachUrl != null ||
            request.delay != null
        val body = if (useJson) encodeJson(request) else request.message
        val mediaType = if (useJson) JSON_MEDIA_TYPE else TEXT_MEDIA_TYPE

        val builder = Request.Builder()
            .url(url)
            .post(body.toRequestBody(mediaType))
            .header("User-Agent", USER_AGENT)
        NtfyAuth.apply(builder, config)
        val httpRequest = builder.build()

        return withContext(ioDispatcher) {
            try {
                httpClient.newCall(httpRequest).execute().use { response ->
                    mapResponse(response)
                }
            } catch (e: IOException) {
                // Definite transport failure. Ambiguous outcomes are never retried.
                PublishResult.NetworkError(e.message ?: "Network failure", e)
            } catch (e: Exception) {
                PublishResult.Ambiguous("Unexpected publish error: ${e.message}")
            }
        }
    }

    private fun mapResponse(response: Response): PublishResult {
        val code = response.code
        val body = response.body?.string().orEmpty()
        return when {
            code in 200..299 -> PublishResult.Accepted(parseMessageId(body))
            code == 400 -> PublishResult.ValidationFailed(body.ifBlank { "Server rejected the publish" })
            code == 401 -> PublishResult.AuthenticationFailed(code, body.ifBlank { "Authentication failed" })
            code == 403 -> PublishResult.PermissionFailed(code, body.ifBlank { "Permission denied" })
            code == 404 -> PublishResult.ValidationFailed(body.ifBlank { "Topic or endpoint not found" })
            code == 413 -> PublishResult.ValidationFailed(body.ifBlank { "Message too large" })
            code == 429 -> PublishResult.ServerError(code, body.ifBlank { "Rate limited — retry later" })
            code in 500..599 -> PublishResult.ServerError(code, body.ifBlank { "Server error" })
            else -> PublishResult.ServerError(code, body.ifBlank { "Unexpected HTTP status $code" })
        }
    }

    private fun encodeJson(request: PublishRequest): String {
        val json = StringBuilder(128)
        json.append('{')
        var first = true

        fun field(name: String, value: String) {
            if (!first) json.append(',')
            first = false
            json.append('"').append(name).append("\":\"").append(escapeJson(value)).append('"')
        }

        field("message", request.message)
        request.title?.let { field("title", it) }
        if (!first) json.append(',')
        first = false
        json.append("\"priority\":").append(request.priority)

        if (request.tags.isNotEmpty()) {
            json.append(",\"tags\":[")
            request.tags.forEachIndexed { index, tag ->
                if (index > 0) json.append(',')
                json.append('"').append(escapeJson(tag)).append('"')
            }
            json.append(']')
        }
        request.clickUrl?.let { field("click", it) }
        request.attachUrl?.let { field("attach", it) }
        request.delay?.let { field("delay", it) }

        json.append('}')
        return json.toString()
    }

    private fun parseMessageId(body: String): String? =
        MESSAGE_ID_PATTERN.find(body)?.groupValues?.getOrNull(1)

    private fun escapeJson(value: String): String {
        val out = StringBuilder(value.length + 8)
        for (c in value) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') {
                    out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(c)
                }
            }
        }
        return out.toString()
    }

    companion object {
        private const val USER_AGENT = "KaminariNfty/1.0 (Android)"
        private val JSON_MEDIA_TYPE: MediaType = "application/json; charset=utf-8".toMediaType()
        private val TEXT_MEDIA_TYPE: MediaType = "text/plain; charset=utf-8".toMediaType()
        private val MESSAGE_ID_PATTERN = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            // A hidden redirect or connection retry could replay a publish and
            // create a duplicate send. Publish retries are explicit and manual.
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .build()
    }
}
