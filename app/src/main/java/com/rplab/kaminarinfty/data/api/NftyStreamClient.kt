package com.rplab.kaminarinfty.data.api

import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyErrorCategory
import com.rplab.kaminarinfty.model.NftyException
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.model.usesCredentials
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.BufferedSource
import org.json.JSONObject
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

sealed class NftyStreamEvent {
    data class Connected(val authState: AuthState, val httpCode: Int) : NftyStreamEvent()
    data class MessageReceived(val message: NftyMessage) : NftyStreamEvent()
    data class MessageDeleted(val messageId: String, val topic: String?) : NftyStreamEvent()
    data class TopicCleared(val topic: String?) : NftyStreamEvent()
    data class KeepAlive(val timestamp: Long) : NftyStreamEvent()

    /**
     * A line exceeded the stream line limit and was discarded, but the
     * connection was kept and reading continues on the next line (G-04).
     */
    data class OversizedLine(val limitBytes: Int) : NftyStreamEvent()

    /** A stream line that is not valid JSON or lacks required message fields. */
    data class Malformed(val reason: String) : NftyStreamEvent()
    data class Error(
        val category: NftyErrorCategory,
        val message: String,
        val canRetry: Boolean,
    ) : NftyStreamEvent()
}

/** Narrow API surface so the reconnect engine is unit-testable with a fake. */
interface NtfyApi {
    fun testConnection(config: ServerConfig): Result<AuthState>

    /**
     * Subscribes to the documented ntfy JSON stream.
     *
     * @param since ntfy history cursor: `all`, `30m`, a Unix timestamp or a message ID.
     * @param poll when true requests `poll=1`, a bounded catch-up stream that ends.
     */
    fun openJsonStream(
        config: ServerConfig,
        topics: List<String>,
        since: String = NtfyEndpoint.DEFAULT_SINCE,
        poll: Boolean = false,
    ): Flow<NftyStreamEvent>
}

class NftyStreamClient(
    private val client: OkHttpClient = streamClient(),
) : NtfyApi {

    /** Short-lived client for health checks: a hung probe must not hang for minutes. */
    private val healthClient: OkHttpClient = client.newBuilder()
        .readTimeout(10, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun testConnection(config: ServerConfig): Result<AuthState> {
        val healthUrl = NtfyEndpoint.healthUrl(config.serverUrl, config.useHttpsOnly)
            .getOrElse {
                return Result.failure(
                    NftyException(
                        NftyErrorCategory.UNREACHABLE,
                        it.message ?: "Invalid server URL",
                    )
                )
            }

        val requestBuilder = Request.Builder()
            .url(healthUrl)
            .get()
            .header("User-Agent", USER_AGENT)
        applyAuthHeaders(requestBuilder, config)

        return try {
            healthClient.newCall(requestBuilder.build()).execute().use { response ->
                when (response.code) {
                    200, 204 -> {
                        if (responseBodyHealthy(response)) {
                            Result.success(reachableAuthState(config))
                        } else {
                            Result.failure(
                                NftyException(
                                    NftyErrorCategory.STREAM,
                                    "Server reports unhealthy status",
                                )
                            )
                        }
                    }
                    // /v1/health may be disabled behind a reverse proxy; try the root.
                    404 -> testRootEndpoint(config)
                    401 -> Result.failure(
                        NftyException(NftyErrorCategory.AUTH, "HTTP 401: credentials rejected by server")
                    )
                    403 -> Result.failure(
                        NftyException(NftyErrorCategory.FORBIDDEN, "HTTP 403: server policy denied access")
                    )
                    502, 503, 504 -> Result.failure(
                        NftyException(
                            NftyErrorCategory.UNREACHABLE,
                            "HTTP ${response.code}: server gateway is unavailable",
                        )
                    )
                    in 500..599 -> Result.failure(
                        NftyException(NftyErrorCategory.STREAM, "HTTP ${response.code}: server error")
                    )
                    else -> Result.failure(
                        NftyException(
                            NftyErrorCategory.UNREACHABLE,
                            "Server returned HTTP ${response.code}",
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e.toNftyException())
        }
    }

    private fun testRootEndpoint(config: ServerConfig): Result<AuthState> {
        val rootUrl = NtfyEndpoint.rootUrl(config.serverUrl, config.useHttpsOnly)
            .getOrElse {
                return Result.failure(
                    NftyException(
                        NftyErrorCategory.UNREACHABLE,
                        it.message ?: "Invalid server URL",
                    )
                )
            }

        val requestBuilder = Request.Builder()
            .url(rootUrl)
            .get()
            .header("User-Agent", USER_AGENT)
        applyAuthHeaders(requestBuilder, config)

        return try {
            healthClient.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful || response.code == 301 || response.code == 302) {
                    Result.success(reachableAuthState(config))
                } else {
                    Result.failure(
                        NftyException(
                            NftyErrorCategory.UNREACHABLE,
                            "Server returned HTTP ${response.code}",
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Result.failure(e.toNftyException())
        }
    }

    /**
     * Subscribes to the documented ntfy JSON stream and emits events
     * incrementally; the response body is never buffered in full.
     *
     * The blocking OkHttp read runs on [Dispatchers.IO]. `awaitClose` cancels the
     * underlying call as soon as the collector is cancelled, which unblocks the
     * pending socket read — otherwise a cancelled collector would leak the
     * connection and its thread until the server spoke again.
     */
    override fun openJsonStream(
        config: ServerConfig,
        topics: List<String>,
        since: String,
        poll: Boolean,
    ): Flow<NftyStreamEvent> = callbackFlow {
        val streamUrl = NtfyEndpoint.streamUrl(
            rawBaseUrl = config.serverUrl,
            topics = topics,
            since = since,
            requireHttps = config.useHttpsOnly,
            poll = poll,
        ).getOrElse { failure ->
            trySend(
                NftyStreamEvent.Error(
                    NftyErrorCategory.UNREACHABLE,
                    failure.message ?: "Invalid subscription request",
                    canRetry = false,
                )
            )
            close()
            return@callbackFlow
        }

        val requestBuilder = Request.Builder()
            .url(streamUrl)
            .get()
            .header("Accept", "application/x-ndjson")
            .header("User-Agent", USER_AGENT)
        applyAuthHeaders(requestBuilder, config)

        val call = client.newCall(requestBuilder.build())

        val worker = launch(Dispatchers.IO) {
            try {
                call.execute().use { response ->
                    if (response.code == 200) {
                        send(NftyStreamEvent.Connected(connectedAuthState(config), response.code))
                        readStreamBody(response) { event -> send(event) }
                    } else {
                        send(errorForHttpCode(response.code))
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentCoroutineContext().isActive) {
                    val (category, mapped) = mapException(e)
                    send(
                        NftyStreamEvent.Error(
                            category,
                            mapped.message ?: "Connection failure",
                            canRetry = category != NftyErrorCategory.AUTH &&
                                category != NftyErrorCategory.FORBIDDEN,
                        )
                    )
                }
            } finally {
                close()
            }
        }

        awaitClose {
            call.cancel()
            worker.cancel()
        }
    }

    private suspend fun readStreamBody(
        response: Response,
        emit: suspend (NftyStreamEvent) -> Unit,
    ) {
        val body = response.body ?: throw IOException("Empty response body from ntfy")
        val source = body.source()
        val limit = NtfyEventParser.MAX_LINE_BYTES.toLong()

        while (currentCoroutineContext().isActive) {
            val line = try {
                source.readUtf8LineStrict(limit)
            } catch (_: EOFException) {
                // Okio raises EOFException both at a real end of stream and when
                // no newline appears within `limit`. Only the over-limit case
                // leaves more than `limit` bytes buffered, so that is the signal
                // (gate finding G-04).
                if (source.buffer.size > limit) {
                    emit(NftyStreamEvent.OversizedLine(NtfyEventParser.MAX_LINE_BYTES))
                    if (!discardOversizedLine(source)) {
                        emit(
                            NftyStreamEvent.Malformed(
                                "oversized stream line exceeded the $MAX_DISCARD_BYTES byte discard limit"
                            )
                        )
                        break
                    }
                    continue
                }
                break
            }

            if (line.isBlank()) continue
            emit(parseLine(line))
        }
    }

    /**
     * Consumes the rest of a line that was too long to return, so the next read
     * starts at the following line instead of the same one. Returns false when
     * the stream ends or the line is absurdly large, because reconnecting would
     * only replay the same unreadable data.
     */
    private fun discardOversizedLine(source: BufferedSource): Boolean {
        var discarded = 0L
        while (discarded < MAX_DISCARD_BYTES) {
            val buffered = source.buffer.size
            if (buffered > 0) {
                val newline = source.buffer.indexOf(NEWLINE, 0L, buffered)
                if (newline >= 0L) {
                    source.buffer.skip(newline + 1L)
                    return true
                }
                source.buffer.skip(buffered)
                discarded += buffered
            }
            if (!source.request(1)) return false
        }
        return false
    }

    private fun Exception.toNftyException(): NftyException {
        val (category, mapped) = mapException(this)
        return NftyException(category, mapped.message ?: "Connection failure")
    }

    private fun parseLine(line: String): NftyStreamEvent = when (val result = NtfyEventParser.parse(line)) {
        is NtfyParseResult.Invalid -> NftyStreamEvent.Malformed(result.reason)
        is NtfyParseResult.Parsed -> when (val event = result.event) {
            is NtfyEvent.Message -> NftyStreamEvent.MessageReceived(event.message)
            is NtfyEvent.MessageDeleted -> NftyStreamEvent.MessageDeleted(event.messageId, event.topic)
            is NtfyEvent.TopicCleared -> NftyStreamEvent.TopicCleared(event.topic)
            is NtfyEvent.Open -> NftyStreamEvent.KeepAlive(event.timestamp)
            is NtfyEvent.KeepAlive -> NftyStreamEvent.KeepAlive(event.timestamp)
            is NtfyEvent.PollRequest -> NftyStreamEvent.KeepAlive(System.currentTimeMillis())
            is NtfyEvent.Unknown -> NftyStreamEvent.Malformed("unknown event type: ${event.eventType}")
        }
    }

    private fun responseBodyHealthy(response: Response): Boolean = try {
        val text = response.body?.string().orEmpty()
        if (text.isBlank()) true else JSONObject(text).optBoolean("healthy", true)
    } catch (_: Exception) {
        // A non-JSON 200 from a reverse proxy is not proof of health.
        false
    }

    private fun reachableAuthState(config: ServerConfig): AuthState =
        if (config.authMethod.usesCredentials() && config.hasCredentials()) {
            // Health is typically unauthenticated: reachability is proven,
            // credential acceptance is not. Do not claim AUTHENTICATED here.
            AuthState.REACHABLE
        } else {
            AuthState.GUEST
        }

    private fun connectedAuthState(config: ServerConfig): AuthState =
        if (config.authMethod.usesCredentials() && config.hasCredentials()) {
            // ntfy only opens a subscription after authorizing read access.
            AuthState.AUTHENTICATED
        } else {
            AuthState.GUEST
        }

    private fun errorForHttpCode(code: Int): NftyStreamEvent.Error = when (code) {
        401 -> NftyStreamEvent.Error(NftyErrorCategory.AUTH, "HTTP 401: invalid credentials", false)
        403 -> NftyStreamEvent.Error(
            NftyErrorCategory.FORBIDDEN,
            "HTTP 403: access denied to subscribed topic(s)",
            false,
        )
        // ntfy returns 404 for unknown topics and unauthorized topics alike.
        404 -> NftyStreamEvent.Error(
            NftyErrorCategory.FORBIDDEN,
            "HTTP 404: topic not found or not authorized",
            false,
        )
        400 -> NftyStreamEvent.Error(
            NftyErrorCategory.STREAM,
            "HTTP 400: server rejected the subscription request",
            false,
        )
        429 -> NftyStreamEvent.Error(NftyErrorCategory.UNREACHABLE, "HTTP 429: rate limited", true)
        502, 503, 504 -> NftyStreamEvent.Error(
            NftyErrorCategory.UNREACHABLE,
            "HTTP $code: server gateway is unavailable",
            true,
        )
        in 500..599 -> NftyStreamEvent.Error(
            NftyErrorCategory.STREAM,
            "HTTP $code: server error",
            true,
        )
        else -> NftyStreamEvent.Error(
            NftyErrorCategory.STREAM,
            "Server returned HTTP $code",
            true,
        )
    }

    private fun applyAuthHeaders(builder: Request.Builder, config: ServerConfig) {
        when (config.authMethod) {
            AuthMethod.GUEST -> Unit
            AuthMethod.USERNAME_PASSWORD -> {
                if (config.username.isNotBlank() && config.tokenOrPassword.isNotBlank()) {
                    builder.header(
                        "Authorization",
                        Credentials.basic(config.username, sanitizeHeaderValue(config.tokenOrPassword)),
                    )
                }
            }
            AuthMethod.ACCESS_TOKEN -> {
                if (config.tokenOrPassword.isNotBlank()) {
                    builder.header(
                        "Authorization",
                        "Bearer ${sanitizeHeaderValue(config.tokenOrPassword)}",
                    )
                }
            }
        }
    }

    /** Prevents header injection through a pasted token containing CR/LF. */
    private fun sanitizeHeaderValue(raw: String): String =
        raw.trim().replace("\r", "").replace("\n", "")

    private fun mapException(e: Exception): Pair<NftyErrorCategory, Exception> = when (e) {
        is UnknownHostException -> Pair(
            NftyErrorCategory.DNS,
            IOException("DNS resolution failed for server host"),
        )
        is SSLException -> Pair(
            NftyErrorCategory.TLS,
            IOException("TLS handshake failed (certificate verification)"),
        )
        is SocketTimeoutException -> Pair(
            NftyErrorCategory.TIMEOUT,
            IOException("Server did not respond in time"),
        )
        is ConnectException -> Pair(
            NftyErrorCategory.UNREACHABLE,
            IOException("Server unreachable (connection refused)"),
        )
        else -> Pair(NftyErrorCategory.STREAM, IOException(e.message ?: "Network failure"))
    }

    private companion object {
        private const val USER_AGENT = "KaminariNfty-Android/1.0.0"

        /** Upper bound on bytes skipped while discarding one oversized line. */
        private const val MAX_DISCARD_BYTES = 8L * 1024 * 1024
        private val NEWLINE: Byte = '\n'.code.toByte()

        private fun streamClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // ntfy keepalives default to 45s; 120s detects a dead socket without
            // dropping a healthy stream. No custom TrustManager: platform TLS only.
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .build()
    }
}
