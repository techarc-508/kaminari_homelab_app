package com.rplab.kaminarinfty.delivery

import com.rplab.kaminarinfty.data.preferences.AppPreferences
import com.rplab.kaminarinfty.model.DeliveryMode

/**
 * Platform-forced fallback when Android ends the `dataSync` foreground service
 * at its 6-hour limit. The budget is not extended; persisted mode moves to
 * Battery Saver so the user keeps receiving inexact catch-up instead of a
 * silent gap. Each step is best-effort and independent.
 */
class InstantTimeoutFallback(
    private val preferences: AppPreferences,
    private val controller: DeliveryController,
) {
    suspend fun switchToBatterySaver() {
        runCatching { preferences.setDeliveryMode(DeliveryMode.BATTERY_SAVER) }
        runCatching { controller.scheduleBatterySaver() }
    }
}
