package com.rplab.kaminarinfty.model

/** Durable outbox state. Persisted by name; unknown values are treated as QUEUED. */
enum class OutboxState {
    QUEUED,
    SENDING,
    ACCEPTED,
    FAILED,
    AMBIGUOUS;

    companion object {
        fun fromPersisted(raw: String?): OutboxState =
            values().firstOrNull { it.name == raw } ?: QUEUED
    }
}
