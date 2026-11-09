package com.rplab.kaminarinfty.delivery

import com.rplab.kaminarinfty.model.AuthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryStatusTest {

    @Test
    fun `live service keeps the observed auth result instead of reporting offline`() {
        val status = DeliveryStatus(
            engine = DeliveryEngine.INSTANT,
            phase = DeliveryPhase.LIVE,
            authState = AuthState.AUTHENTICATED,
        )
        assertEquals(AuthState.AUTHENTICATED, status.toAuthState())
        assertFalse(status.isTerminal)
    }

    @Test
    fun `active startup phases map to connecting`() {
        DeliveryPhase.entries
            .filter {
                it == DeliveryPhase.STARTING ||
                    it == DeliveryPhase.CATCHING_UP ||
                    it == DeliveryPhase.CONNECTING
            }
            .forEach {
                assertEquals(AuthState.CONNECTING, DeliveryStatus(phase = it).toAuthState())
            }
        assertEquals(
            AuthState.RECONNECTING,
            DeliveryStatus(phase = DeliveryPhase.RECONNECTING).toAuthState(),
        )
    }

    @Test
    fun `terminal phases map to honest failure states`() {
        assertEquals(
            AuthState.AUTHENTICATION_FAILED,
            DeliveryStatus(phase = DeliveryPhase.AUTH_FAILED).toAuthState(),
        )
        assertEquals(
            AuthState.PERMISSION_DENIED,
            DeliveryStatus(phase = DeliveryPhase.PERMISSION_DENIED).toAuthState(),
        )
        assertEquals(
            AuthState.DISCONNECTED,
            DeliveryStatus(phase = DeliveryPhase.TIMED_OUT).toAuthState(),
        )
        assertEquals(
            AuthState.DISCONNECTED,
            DeliveryStatus(phase = DeliveryPhase.FAILED).toAuthState(),
        )
        assertTrue(DeliveryStatus(phase = DeliveryPhase.TIMED_OUT).isTerminal)
        assertTrue(DeliveryStatus(phase = DeliveryPhase.AUTH_FAILED).isTerminal)
    }

    @Test
    fun `store stamps each update with an increasing sequence`() {
        val store = DeliveryStatusStore()
        val first = store.publish(DeliveryEngine.INSTANT, DeliveryPhase.STARTING)
        val second = store.publish(DeliveryEngine.INSTANT, DeliveryPhase.LIVE, topicCount = 2)

        assertTrue(second.sequence > first.sequence)
        assertEquals(DeliveryPhase.LIVE, store.status.value.phase)
        assertEquals("Live delivery active — 2 topics", second.displayText())
    }

    @Test
    fun `terminal display never claims live delivery`() {
        assertEquals(
            "Live delivery paused by the Android foreground-service limit",
            DeliveryStatus(phase = DeliveryPhase.TIMED_OUT).displayText(),
        )
        assertEquals(
            "Reconnecting…",
            DeliveryStatus(phase = DeliveryPhase.RECONNECTING).displayText(),
        )
    }
}
