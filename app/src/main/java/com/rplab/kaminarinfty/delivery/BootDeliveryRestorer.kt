package com.rplab.kaminarinfty.delivery

import com.rplab.kaminarinfty.data.preferences.AppPreferences
import com.rplab.kaminarinfty.model.DeliveryMode
import kotlinx.coroutines.flow.first

/**
 * Restores the persisted delivery engine after a reboot.
 *
 * Extracted from [BootReceiver] so the platform-free decision logic is testable.
 * Instant mode cannot be restarted from `BOOT_COMPLETED` (Android 15+ forbids a
 * `dataSync` foreground-service launch there), so it posts a resume notice
 * instead of pretending delivery resumed. Battery Saver is re-enqueued.
 */
class BootDeliveryRestorer(
    private val preferences: AppPreferences,
    private val controller: DeliveryController,
) {
    suspend fun restore() {
        when (preferences.data.first().deliveryMode) {
            DeliveryMode.INSTANT -> {
                controller.ensureNotificationChannels()
                controller.postInstantResumeNotification()
            }
            DeliveryMode.BATTERY_SAVER -> controller.scheduleBatterySaver()
        }
    }
}
