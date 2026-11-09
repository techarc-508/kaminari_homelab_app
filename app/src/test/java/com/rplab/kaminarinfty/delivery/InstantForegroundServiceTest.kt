package com.rplab.kaminarinfty.delivery

import android.content.pm.ServiceInfo
import androidx.test.core.app.ApplicationProvider
import com.rplab.kaminarinfty.KaminariApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InstantForegroundServiceTest {

    @Test
    fun `android dataSync timeout records an honest terminal state and stops`() {
        val controller = Robolectric.buildService(InstantForegroundService::class.java)
        val service = controller.get()

        service.onTimeout(7, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)

        val status = ApplicationProvider.getApplicationContext<KaminariApp>()
            .container.deliveryStatusStore.status.value
        assertEquals(DeliveryEngine.INSTANT, status.engine)
        assertEquals(DeliveryPhase.TIMED_OUT, status.phase)
        assertTrue(status.isTerminal)
        assertTrue(status.detail.orEmpty().contains("6-hour"))
        assertTrue(shadowOf(service).isStoppedBySelf)
    }
}
