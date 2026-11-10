package com.rplab.kaminarinfty.model

import org.json.JSONObject

/** Typed command lifecycle. Persisted by name. */
enum class CommandStatus {
    SUBMITTED,
    COMPLETED,
    FAILED,
    EXPIRED,
    REJECTED;

    val isTerminal: Boolean
        get() = this != SUBMITTED

    companion object {
        fun fromPersisted(value: String): CommandStatus =
            entries.firstOrNull { it.name == value } ?: REJECTED
    }
}

/**
 * Deterministic result from the mock executor. [mock] is always true in Phase 4;
 * no result represents a real infrastructure change.
 */
data class CommandResult(
    val mock: Boolean,
    val ok: Boolean,
    val summary: String,
    val detailJson: String,
) {
    fun toJson(): String = JSONObject()
        .put("mock", mock)
        .put("ok", ok)
        .put("summary", summary)
        .put("detail", detailJson)
        .put("realInfrastructureChanged", false)
        .toString()

    companion object {
        fun fromJson(raw: String): CommandResult? = runCatching {
            val json = JSONObject(raw)
            CommandResult(
                mock = json.getBoolean("mock"),
                ok = json.getBoolean("ok"),
                summary = json.getString("summary"),
                detailJson = json.optString("detail", ""),
            )
        }.getOrNull()
    }
}

/** Domain view of one profile-scoped command request. */
data class CommandRequest(
    val commandId: String,
    val profileId: String,
    val actionId: String,
    val target: String,
    val args: Map<String, String>,
    val nonce: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val requiresConfirmation: Boolean,
    val confirmedAt: Long?,
    val status: CommandStatus,
    val resultJson: String?,
    val resultSummary: String?,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun isExpired(now: Long): Boolean =
        status == CommandStatus.SUBMITTED && now >= expiresAt

    val canBeConfirmed: Boolean
        get() = status == CommandStatus.SUBMITTED && requiresConfirmation && confirmedAt == null

    val canExecute: Boolean
        get() = status == CommandStatus.SUBMITTED &&
            (confirmedAt != null || !requiresConfirmation)

    fun result(): CommandResult? = resultJson?.let(CommandResult::fromJson)
}
