package com.rplab.kaminarinfty.delivery

import com.rplab.kaminarinfty.model.DeliveryMode
import com.rplab.kaminarinfty.testing.FakeDeliveryController
import com.rplab.kaminarinfty.testing.FakePreferences
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BootDeliveryRestorerTest {

    private val preferences = FakePreferences()
    private val controller = FakeDeliveryController()
    private val restorer = BootDeliveryRestorer(preferences, controller)

    @Test
    fun `battery saver is re-scheduled after reboot`() = runTest {
        preferences.state.value =
            preferences.state.value.copy(deliveryMode = DeliveryMode.BATTERY_SAVER)

        restorer.restore()

        assertEquals(1, controller.batterySchedules)
        assertEquals(0, controller.resumeNotices)
        assertEquals(0, controller.instantStarts)
    }

    @Test
    fun `instant mode posts a resume notice instead of starting a dataSync service`() = runTest {
        preferences.state.value =
            preferences.state.value.copy(deliveryMode = DeliveryMode.INSTANT)

        restorer.restore()

        assertEquals(1, controller.resumeNotices)
        assertEquals(1, controller.channelsEnsured)
        // Android 15+ forbids this start from BOOT_COMPLETED.
        assertEquals(0, controller.instantStarts)
        assertEquals(0, controller.batterySchedules)
    }
}
