package com.rplab.kaminarinfty.delivery

import com.rplab.kaminarinfty.model.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicLong

/** Which delivery engine currently owns background work. */
enum class DeliveryEngine {
    NONE,
    INSTANT,
    BATTERY_SAVER,
}

/**
 * Honest lifecycle of the delivery engine. The UI must derive its connection
 * indicator from this while Instant mode owns the stream, because the ViewModel
 * deliberately does not open a second subscription.
 */
enum class DeliveryPhase {
    IDLE,
    STARTING,
    CATCHING_UP,
    CONNECTING,
    LIVE,
    RECONNECTING,
    AUTH_FAILED,
    PERMISSION_DENIED,
    TIMED_OUT,
    FAILED,
}

data class DeliveryStatus(
    val engine: DeliveryEngine = DeliveryEngine.NONE,
    val phase: DeliveryPhase = DeliveryPhase.IDLE,
    /** Human-readable, sanitised explanation for terminal phases. */
    val detail: String? = null,
    val topicCount: Int = 0,
    /** Auth result observed on the service-owned stream (guest vs authenticated). */
    val authState: AuthState = AuthState.DISCONNECTED,
    /** Monotonic counter so the UI can surface each terminal event once. */
    val sequence: Long = 0L,
) {
    val isTerminal: Boolean
        get() = phase == DeliveryPhase.AUTH_FAILED ||
            phase == DeliveryPhase.PERMISSION_DENIED ||
            phase == DeliveryPhase.TIMED_OUT ||
            phase == DeliveryPhase.FAILED

    val isActive: Boolean
        get() = phase == DeliveryPhase.STARTING ||
            phase == DeliveryPhase.CATCHING_UP ||
            phase == DeliveryPhase.CONNECTING ||
            phase == DeliveryPhase.LIVE ||
            phase == DeliveryPhase.RECONNECTING

    /**
     * Auth state the app should display for this status. Terminal phases map to
     * the existing honest failure states; active phases keep the observed auth
     * result so Inbox never shows "offline" over a live service connection.
     */
    fun toAuthState(): AuthState = when (phase) {
        DeliveryPhase.LIVE -> authState
        DeliveryPhase.STARTING,
        DeliveryPhase.CATCHING_UP,
        DeliveryPhase.CONNECTING,
        -> AuthState.CONNECTING
        DeliveryPhase.RECONNECTING -> AuthState.RECONNECTING
        DeliveryPhase.AUTH_FAILED -> AuthState.AUTHENTICATION_FAILED
        DeliveryPhase.PERMISSION_DENIED -> AuthState.PERMISSION_DENIED
        DeliveryPhase.IDLE,
        DeliveryPhase.TIMED_OUT,
        DeliveryPhase.FAILED,
        -> AuthState.DISCONNECTED
    }

    /** Short line for the Settings delivery card. */
    fun displayText(): String = when (phase) {
        DeliveryPhase.IDLE -> "No delivery engine running"
        DeliveryPhase.STARTING -> "Starting live service…"
        DeliveryPhase.CATCHING_UP -> "Catching up missed history…"
        DeliveryPhase.CONNECTING -> "Connecting to ntfy…"
        DeliveryPhase.LIVE -> "Live delivery active — $topicCount topic${if (topicCount == 1) "" else "s"}"
        DeliveryPhase.RECONNECTING -> "Reconnecting…"
        DeliveryPhase.AUTH_FAILED -> "Live delivery stopped — sign-in required"
        DeliveryPhase.PERMISSION_DENIED -> "Live delivery stopped — access denied"
        DeliveryPhase.TIMED_OUT -> "Live delivery paused by the Android foreground-service limit"
        DeliveryPhase.FAILED -> detail ?: "Live delivery stopped"
    }
}

/**
 * Process-wide status owned by the app container. The foreground service writes;
 * the ViewModel reads without ever opening a second stream.
 */
class DeliveryStatusStore {
    private val sequence = AtomicLong(0)
    private val _status = MutableStateFlow(DeliveryStatus())

    val status: StateFlow<DeliveryStatus> = _status.asStateFlow()

    fun update(status: DeliveryStatus): DeliveryStatus {
        val stamped = status.copy(sequence = sequence.incrementAndGet())
        _status.value = stamped
        return stamped
    }

    fun publish(
        engine: DeliveryEngine,
        phase: DeliveryPhase,
        detail: String? = null,
        topicCount: Int = 0,
        authState: AuthState = AuthState.DISCONNECTED,
    ): DeliveryStatus = update(
        DeliveryStatus(
            engine = engine,
            phase = phase,
            detail = detail,
            topicCount = topicCount,
            authState = authState,
        )
    )
}
