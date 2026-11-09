package com.rplab.kaminarinfty.model

/**
 * Owner-selected background delivery strategy.
 *
 * Only the two owner-locked modes are selectable. The pre-remediation build
 * exposed a third `SAVED` (foreground-only) value; [fromPersisted] migrates that
 * stored value to [BATTERY_SAVER] so existing installs keep receiving background
 * catch-up instead of silently losing delivery. Saved *messages* are unrelated
 * and remain available in the Saved tab.
 *
 * None of these modes can promise guaranteed instant delivery on modern
 * Android: OEM process management, Doze and foreground-service policy can all
 * interrupt work. The labels below are what the UI shows to keep that honest.
 */
enum class DeliveryMode(
    val label: String,
    val summary: String,
    val tradeoff: String,
) {
    INSTANT(
        label = "Instant",
        summary = "Live connection with a visible notification",
        tradeoff = "Lowest latency, highest battery use. Android may stop the service under aggressive OEM power management or after 6 hours of dataSync foreground time in 24 hours.",
    ),
    BATTERY_SAVER(
        label = "Battery Saver",
        summary = "Periodic catch-up every ~15 minutes",
        tradeoff = "Lower power, delayed delivery. WorkManager timing is inexact under Doze and after reboot.",
    ),
    ;

    companion object {
        /** Fresh installs receive periodic catch-up rather than no background delivery. */
        val DEFAULT: DeliveryMode = BATTERY_SAVER

        /** Removed Phase 3 mode: persisted only, never selectable. */
        private const val LEGACY_SAVED = "SAVED"

        /**
         * Maps a persisted DataStore string to a delivery mode.
         *
         * - `INSTANT` -> [INSTANT]
         * - `BATTERY_SAVER` -> [BATTERY_SAVER]
         * - legacy `SAVED` -> [BATTERY_SAVER] (explicit one-way migration)
         * - missing/corrupt -> [DEFAULT]
         */
        fun fromPersisted(raw: String?): DeliveryMode = when (raw) {
            INSTANT.name -> INSTANT
            BATTERY_SAVER.name -> BATTERY_SAVER
            LEGACY_SAVED -> BATTERY_SAVER
            else -> DEFAULT
        }
    }
}
