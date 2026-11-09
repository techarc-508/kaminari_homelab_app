package com.rplab.kaminarinfty.delivery

import com.rplab.kaminarinfty.model.DeliveryMode
import com.rplab.kaminarinfty.testing.FakeDeliveryController
import com.rplab.kaminarinfty.testing.FakePreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class InstantTimeoutFallbackTest {

    @Test
    fun `timeout fallback persists battery saver and schedules catch-up`() = runTest {
        val preferences = FakePreferences()
        val controller = FakeDeliveryController()
        preferences.state.value =
            preferences.state.value.copy(deliveryMode = DeliveryMode.INSTANT)

        InstantTimeoutFallback(preferences, controller).switchToBatterySaver()

        assertEquals(DeliveryMode.BATTERY_SAVER, preferences.state.value.deliveryMode)
        assertEquals(1, controller.batterySchedules)
        // The service is already stopping itself; fallback must not restart it.
        assertEquals(0, controller.instantStarts)
    }
}
