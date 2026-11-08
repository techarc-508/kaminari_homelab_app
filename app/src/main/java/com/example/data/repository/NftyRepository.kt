package com.example.data.repository

import com.example.data.api.NftyStreamClient
import com.example.data.api.NftyStreamEvent
import com.example.model.AuthState
import com.example.model.NftyErrorCategory
import com.example.model.NftyMessage
import com.example.model.ServerConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

class NftyRepository(
    private val client: NftyStreamClient = NftyStreamClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private var streamJob: Job? = null

    private val _connectionState = MutableStateFlow(AuthState.DISCONNECTED)
    val connectionState: StateFlow<AuthState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<Pair<NftyErrorCategory, String>?>(null)
    val lastError: StateFlow<Pair<NftyErrorCategory, String>?> = _lastError.asStateFlow()

    private val _lastHeartbeat = MutableStateFlow<Long>(0)
    val lastHeartbeat: StateFlow<Long> = _lastHeartbeat.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<NftyMessage>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<NftyMessage> = _incomingMessages.asSharedFlow()

    // Deduplication set for received message IDs
    private val processedMessageIds = mutableSetOf<String>()

    fun testConnection(config: ServerConfig): Result<AuthState> {
        return client.testConnection(config)
    }

    fun startSubscription(
        config: ServerConfig,
        topics: List<String>,
        since: String = "12h"
    ) {
        // Cancel existing streaming job if running
        stopSubscription()

        val activeTopics = topics.filter { it.isNotBlank() }
        if (activeTopics.isEmpty()) {
            _connectionState.value = AuthState.DISCONNECTED
            _lastError.value = Pair(NftyErrorCategory.UNREACHABLE, "No topics configured for subscription")
            return
        }

        streamJob = scope.launch {
            var attempt = 0
            val maxBackoffMs = 30_000L
            val baseBackoffMs = 1_500L

            while (isActive) {
                _connectionState.value = if (attempt == 0) AuthState.CONNECTING else AuthState.RECONNECTING
                _lastError.value = null

                try {
                    client.openJsonStream(config, activeTopics, since = since).collect { event ->
                        when (event) {
                            is NftyStreamEvent.Connected -> {
                                attempt = 0 // Reset backoff on successful connection
                                _connectionState.value = event.authState
                                _lastError.value = null
                            }
                            is NftyStreamEvent.MessageReceived -> {
                                if (processedMessageIds.add(event.message.id)) {
                                    _incomingMessages.emit(event.message)
                                }
                            }
                            is NftyStreamEvent.KeepAlive -> {
                                _lastHeartbeat.value = event.timestamp
                            }
                            is NftyStreamEvent.Error -> {
                                _lastError.value = Pair(event.category, event.message)
                                if (!event.canRetry) {
                                    _connectionState.value = when (event.category) {
                                        NftyErrorCategory.AUTH -> AuthState.AUTHENTICATION_FAILED
                                        NftyErrorCategory.FORBIDDEN -> AuthState.PERMISSION_DENIED
                                        else -> AuthState.DISCONNECTED
                                    }
                                    return@collect
                                }
                            }
                        }
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (e: Exception) {
                    _lastError.value = Pair(NftyErrorCategory.STREAM, e.message ?: "Stream disconnected")
                }

                if (!isActive) break

                // Bounded exponential backoff with jitter
                attempt++
                val expDelay = (baseBackoffMs * (1L shl (attempt - 1).coerceAtMost(5))).coerceAtMost(maxBackoffMs)
                // Add +/- 20% jitter
                val jitter = (expDelay * 0.2 * (Random.nextDouble() * 2 - 1)).toLong()
                val totalDelay = (expDelay + jitter).coerceAtLeast(1000L)

                _connectionState.value = AuthState.RECONNECTING
                delay(totalDelay)
            }
        }
    }

    fun stopSubscription() {
        streamJob?.cancel()
        streamJob = null
        _connectionState.value = AuthState.DISCONNECTED
    }

    fun registerMessageId(id: String) {
        processedMessageIds.add(id)
    }
}
