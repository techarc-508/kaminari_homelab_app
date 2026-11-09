package com.rplab.kaminarinfty.model

/** Typed command lifecycle. Persisted by name. */
enum class CommandStatus {
    SUBMITTED,
    COMPLETED,
    FAILED,
    EXPIRED,
    REJECTED,
}
