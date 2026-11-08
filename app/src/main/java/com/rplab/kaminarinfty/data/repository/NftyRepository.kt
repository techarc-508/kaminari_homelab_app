package com.rplab.kaminarinfty.data.repository

import com.rplab.kaminarinfty.data.api.NtfyApi
import com.rplab.kaminarinfty.data.api.NtfyEndpoint
import com.rplab.kaminarinfty.data.api.NftyStreamClient
import com.rplab.kaminarinfty.data.api.NftyStreamEvent
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyErrorCategory
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.model.StreamUpdate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random

/** Result of a bounded history catch-up request. */
data class CatchUpOutcome(
    val received: Int = 0,
    val duplicates: Int = 0,
    val failed: Boolean = false,
)

/** Transport/stream boundary so the ViewModel can be tested with a fake. */
interface StreamRepository {
    val connectionState: StateFlow<AuthState>
    val lastError: StateFlow<Pair<NftyErrorCategory, String>?>
    val updates: SharedFlow<StreamUpdate>
    val malformedLineCount: StateFlow<Int>
    val reconnectAttempt: StateFlow<Int>

    fun testConnection(config: ServerConfig): Result<AuthState>

    /**
     * Opens the live stream and keeps it alive with bounded exponential backoff.
     * [sinceProvider] is evaluated before every connection attempt so a
     * reconnect resumes from the newest durable cursor instead of a fixed
     * window (prevents silent gaps after a drop).
     */
    fun startSubscription(
        config: ServerConfig,
        topics: List<String>,
        sinceProvider: suspend () -> String = { NtfyEndpoint.DEFAULT_SINCE },
    )

    /** One bounded `poll=1` history sync that ends when the server drains. */
    suspend fun catchUp(config: ServerConfig, topics: List<String>, since: String): CatchUpOutcome

    fun stopSubscription()
    fun close()
}

class NftyRepository(
    private val client: NtfyApi = NftyStreamClient(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val random: Random = Random.Default,
) : StreamRepository {

    private var streamJob: Job? = null

    private val _connectionState = MutableStateFlow(AuthState.DISCONNECTED)
    override val connectionState: StateFlow<AuthState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<Pair<NftyErrorCategory, String>?>(null)
    override val lastError: StateFlow<Pair<NftyErrorCategory, String>?> = _lastError.asStateFlow()

    private val _updates = MutableSharedFlow<StreamUpdate>(extraBufferCapacity = 128)
    override val updates: SharedFlow<StreamUpdate> = _updates.asSharedFlow()

    private val _malformedLineCount = MutableStateFlow(0)
    override val malformedLineCount: StateFlow<Int> = _malformedLineCount.asStateFlow()

    private val _reconnectAttempt = MutableStateFlow(0)
    override val reconnectAttempt: StateFlow<Int> = _reconnectAttempt.asStateFlow()

    /**
     * Bounded in-memory replay guard. Durable dedupe is the unique
     * (profileId, messageId) Room index; this only avoids re-emitting an ID the
     * stream just replayed on reconnect.
     */
    private val recentIds = LinkedHashMap<String, Unit>(256, 0.75f, true)

    override fun testConnection(config: ServerConfig): Result<AuthState> =
        client.testConnection(config)

    override fun startSubscription(
        config: ServerConfig,
        topics: List<String>,
        sinceProvider: suspend () -> String,
    ) {
        stopSubscription()

        val activeTopics = topics.map { it.trim() }.filter { it.isNotBlank() }
        if (activeTopics.isEmpty()) {
            _connectionState.value = AuthState.DISCONNECTED
            _lastError.value = Pair(
                NftyErrorCategory.UNREACHABLE,
                "No topics configured for subscription",
            )
            return
        }
        synchronized(recentIds) { recentIds.clear() }
        _reconnectAttempt.value = 0
        _lastError.value = null

        streamJob = scope.launch {
            var attempt = 0
            var terminal = false

            while (isActive && !terminal) {
                _connectionState.value = if (attempt == 0) AuthState.CONNECTING else AuthState.RECONNECTING
                val since = try {
                    sinceProvider()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Exception) {
                    NtfyEndpoint.DEFAULT_SINCE
                }

                var streamOpened = false
                try {
                    client.openJsonStream(config, activeTopics, since = since).collect { event ->
                        when (event) {
                            is NftyStreamEvent.Connected -> {
                                streamOpened = true
                                attempt = 0
                                _reconnectAttempt.value = 0
                                _connectionState.value = event.authState
                                _lastError.value = null
                            }
                            is NftyStreamEvent.MessageReceived -> {
                                if (markSeen(event.message.id)) {
                                    _updates.emit(StreamUpdate.NewMessage(event.message))
                                }
                            }
                            is NftyStreamEvent.MessageDeleted ->
                                _updates.emit(StreamUpdate.Removed(event.messageId))
                            is NftyStreamEvent.TopicCleared ->
                                _updates.emit(StreamUpdate.TopicCleared(event.topic))
                            is NftyStreamEvent.KeepAlive -> Unit
                            is NftyStreamEvent.Malformed -> _malformedLineCount.value += 1
                            is NftyStreamEvent.Error -> {
                                _lastError.value = Pair(event.category, event.message)
                                if (!event.canRetry) {
                                    _connectionState.value = when (event.category) {
                                        NftyErrorCategory.AUTH -> AuthState.AUTHENTICATION_FAILED
                                        NftyErrorCategory.FORBIDDEN -> AuthState.PERMISSION_DENIED
                                        else -> AuthState.DISCONNECTED
                                    }
                                    terminal = true
                                }
                            }
                        }
                    }

                    // A live stream ending without a terminal error is a drop,
                    // not a clean idle state. Report it honestly.
                    if (isActive && !terminal && streamOpened) {
                        _lastError.value = Pair(
                            NftyErrorCategory.STREAM,
                            "Stream closed by server",
                        )
                    } else if (isActive && !terminal) {
                        _lastError.value = _lastError.value ?: Pair(
                            NftyErrorCategory.STREAM,
                            "Stream ended before it opened",
                        )
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    if (isActive) {
                        _lastError.value = Pair(
                            NftyErrorCategory.STREAM,
                            e.message ?: "Stream disconnected",
                        )
                    }
                }

                if (!isActive || terminal) break

                attempt++
                _reconnectAttempt.value = attempt
                _connectionState.value = AuthState.RECONNECTING
                delay(StreamBackoff.delayMillis(attempt, random.nextDouble(-1.0, 1.0)))
            }
        }
    }

    override suspend fun catchUp(
        config: ServerConfig,
        topics: List<String>,
        since: String,
    ): CatchUpOutcome {
        val activeTopics = topics.map { it.trim() }.filter { it.isNotBlank() }
        if (activeTopics.isEmpty()) return CatchUpOutcome(failed = false)

        var received = 0
        var duplicates = 0
        var failed = false

        try {
            client.openJsonStream(config, activeTopics, since = since, poll = true).collect { event ->
                when (event) {
                    is NftyStreamEvent.MessageReceived -> {
                        if (markSeen(event.message.id)) {
                            received++
                            _updates.emit(StreamUpdate.NewMessage(event.message))
                        } else {
                            duplicates++
                        }
                    }
                    is NftyStreamEvent.MessageDeleted ->
                        _updates.emit(StreamUpdate.Removed(event.messageId))
                    is NftyStreamEvent.TopicCleared ->
                        _updates.emit(StreamUpdate.TopicCleared(event.topic))
                    is NftyStreamEvent.Malformed -> _malformedLineCount.value += 1
                    is NftyStreamEvent.Error -> {
                        failed = true
                        _lastError.value = Pair(event.category, event.message)
                    }
                    else -> Unit
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            failed = true
            _lastError.value = Pair(NftyErrorCategory.STREAM, e.message ?: "History sync failed")
        }

        return CatchUpOutcome(received = received, duplicates = duplicates, failed = failed)
    }

    override fun stopSubscription() {
        streamJob?.cancel()
        streamJob = null
        _reconnectAttempt.value = 0
        _connectionState.value = AuthState.DISCONNECTED
    }

    override fun close() {
        stopSubscription()
        scope.cancel()
    }

    private fun markSeen(id: String): Boolean = synchronized(recentIds) {
        if (recentIds.containsKey(id)) {
            false
        } else {
            recentIds[id] = Unit
            if (recentIds.size > MAX_RECENT_IDS) {
                recentIds.remove(recentIds.keys.first())
            }
            true
        }
    }

    private companion object {
        const val MAX_RECENT_IDS = 512
    }
}

/**
 * Bounded exponential backoff with symmetric jitter. Pure so the policy can be
 * asserted directly in tests instead of by timing alone.
 */
object StreamBackoff {
    const val BASE_DELAY_MS = 1_500L
    const val MAX_DELAY_MS = 60_000L
    private const val MIN_DELAY_MS = 1_000L
    private const val MAX_JITTER_RATIO = 0.2

    fun delayMillis(attempt: Int, jitter: Double = 0.0): Long {
        val exponent = (attempt.coerceAtLeast(1) - 1).coerceAtMost(10)
        val exponential = BASE_DELAY_MS shl exponent
        val capped = exponential.coerceAtMost(MAX_DELAY_MS)
        val jitterMs = (capped * MAX_JITTER_RATIO * jitter.coerceIn(-1.0, 1.0)).toLong()
        return (capped + jitterMs).coerceIn(MIN_DELAY_MS, MAX_DELAY_MS + (MAX_DELAY_MS * 0.2).toLong())
    }
}
