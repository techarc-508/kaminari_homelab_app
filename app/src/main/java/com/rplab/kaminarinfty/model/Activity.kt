package com.rplab.kaminarinfty.model

/** Activity timeline event types. Persisted by name. */
enum class ActivityType {
    MESSAGE_RECEIVED,
    PUBLISH_QUEUED,
    PUBLISH_STARTED,
    PUBLISH_ACCEPTED,
    PUBLISH_FAILED,
    PUBLISH_AMBIGUOUS,
    PUBLISH_RETRY,
    COMMAND_SUBMITTED,
    COMMAND_CONFIRMATION_REQUIRED,
    COMMAND_CONFIRMED,
    COMMAND_EXECUTION_STARTED,
    COMMAND_RESULT,
    COMMAND_EXPIRED,
    COMMAND_REJECTED,
}

enum class ActivitySeverity {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

/** Bounded, sanitized timeline entry. Never contains a message body or secret. */
data class ActivityEvent(
    val id: String,
    val profileId: String,
    val type: ActivityType,
    val severity: ActivitySeverity,
    val summary: String,
    val correlationId: String? = null,
    val messageId: String? = null,
    val topic: String? = null,
    val createdAt: Long,
)
