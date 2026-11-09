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
