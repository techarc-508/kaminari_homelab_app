package com.rplab.kaminarinfty.data.local

/**
 * Single source of truth for bounded retention. DAO wrappers use these values;
 * low-level prune queries remain available for tests and maintenance only.
 */
object RetentionPolicy {
    const val ACTIVITY_RETENTION_DAYS = 90L
    const val ACTIVITY_MAX_EVENTS_PER_PROFILE = 5_000
    const val OUTBOX_RETENTION_DAYS = 30L
    const val OUTBOX_MAX_TERMINAL_ENTRIES_PER_PROFILE = 500

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

    fun activityCutoff(now: Long): Long = now - ACTIVITY_RETENTION_DAYS * MILLIS_PER_DAY

    fun outboxCutoff(now: Long): Long = now - OUTBOX_RETENTION_DAYS * MILLIS_PER_DAY
}
