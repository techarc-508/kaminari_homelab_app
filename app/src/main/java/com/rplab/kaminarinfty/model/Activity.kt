package com.rplab.kaminarinfty.model

/** Activity timeline event types. Persisted by name. */
enum class ActivityType {
    MESSAGE_RECEIVED,
    PUBLISH_QUEUED,
    PUBLISH_ACCEPTED,
    PUBLISH_FAILED,
    PUBLISH_AMBIGUOUS,
    PUBLISH_RETRY,
    COMMAND_SUBMITTED,
    COMMAND_CONFIRMED,
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
