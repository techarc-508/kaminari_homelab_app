package com.rplab.kaminarinfty.model

/** Durable outbox state. Persisted by name; unknown values fail closed to AMBIGUOUS. */
enum class OutboxState {
    QUEUED,
    SENDING,
    ACCEPTED,
    FAILED,
    AMBIGUOUS;

    companion object {
        /**
         * Unknown or corrupt persisted values must never become QUEUED: that
         * could trigger an automatic resend. Treat them as uncertain instead.
         */
        fun fromPersisted(raw: String?): OutboxState =
            values().firstOrNull { it.name == raw } ?: AMBIGUOUS
    }
}

/**
 * A durable publish intent as exposed to the UI. The body is stored because the
 * entry must be publishable after a restart; credentials never live here.
 */
data class OutboxEntry(
    val clientId: String,
    val profileId: String,
    val topic: String,
    val title: String?,
    val body: String,
    val priority: Int,
    val tags: List<String>,
    val clickUrl: String?,
    val attachmentUrl: String?,
    val delay: String?,
    val state: OutboxState,
    val attemptCount: Int,
    val retryable: Boolean,
    val lastError: String?,
    val serverMessageId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val acceptedAt: Long?,
)

