package com.rplab.kaminarinfty.commands

import com.rplab.kaminarinfty.data.api.NtfyEndpoint
import java.util.UUID
import kotlin.math.abs

/** One typed argument in the fixed command catalogue. Values are always strings. */
data class CommandArgument(
    val key: String,
    val label: String,
    val placeholder: String,
    val pattern: Regex,
    val hint: String,
)

/** An allowlisted action. There is deliberately no arbitrary-command variant. */
data class CommandActionSpec(
    val id: String,
    val title: String,
    val description: String,
    val arguments: List<CommandArgument> = emptyList(),
    val requiresConfirmation: Boolean = false,
    val mockResult: (Map<String, String>) -> String,
)

object CommandCatalog {
    private val resourceName = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")

    val actions: List<CommandActionSpec> = listOf(
        CommandActionSpec(
            id = "host.health",
            title = "Host health",
            description = "Read-only health summary from the mock target.",
            mockResult = { "host healthy" },
        ),
        CommandActionSpec(
            id = "service.status",
            title = "Service status",
            description = "Read-only status for one allowlisted service name.",
            arguments = listOf(
                CommandArgument(
                    key = "service",
                    label = "Service",
                    placeholder = "navidrome",
                    pattern = resourceName,
                    hint = "lowercase letters, digits, _ or - (max 32)",
                ),
            ),
            mockResult = { args -> "${args["service"]} running" },
        ),
        CommandActionSpec(
            id = "storage.summary",
            title = "Storage summary",
            description = "Read-only storage utilisation summary.",
            mockResult = { "42% used" },
        ),
        CommandActionSpec(
            id = "backup.latest_status",
            title = "Latest backup status",
            description = "Read-only status of the latest simulated backup.",
            mockResult = { "last backup OK" },
        ),
        CommandActionSpec(
            id = "container.status",
            title = "Container status",
            description = "Read-only status for one allowlisted container name.",
            arguments = listOf(
                CommandArgument(
                    key = "container",
                    label = "Container",
                    placeholder = "jellyfin",
                    pattern = resourceName,
                    hint = "lowercase letters, digits, _ or - (max 32)",
                ),
            ),
            mockResult = { args -> "${args["container"]} running" },
        ),
        CommandActionSpec(
            id = "service.restart",
            title = "Restart service (simulated)",
            description = "Destructive action; confirmation is required. Phase 4 only simulates it.",
            arguments = listOf(
                CommandArgument(
                    key = "service",
                    label = "Service",
                    placeholder = "navidrome",
                    pattern = resourceName,
                    hint = "lowercase letters, digits, _ or - (max 32)",
                ),
            ),
            requiresConfirmation = true,
            mockResult = { args -> "restart of ${args["service"]} simulated; no real service was changed" },
        ),
    )

    fun action(id: String): CommandActionSpec? = actions.firstOrNull { it.id == id }

    fun validateArguments(action: CommandActionSpec, args: Map<String, String>): String? {
        val expected = action.arguments.map { it.key }.toSet()
        if (args.keys != expected) {
            val missing = expected - args.keys
            if (missing.isNotEmpty()) return "Missing argument: ${missing.sorted().first()}"
            return "Unknown argument: ${(args.keys - expected).sorted().first()}"
        }
        action.arguments.forEach { spec ->
            val value = args[spec.key].orEmpty()
            if (value.isEmpty()) return "${spec.label} is required"
            if (value.length > 32 || !spec.pattern.matches(value)) {
                return "${spec.label} must be ${spec.hint}"
            }
        }
        return null
    }
}

sealed interface CommandValidation {
    data object Valid : CommandValidation
    data class Invalid(val reason: String) : CommandValidation
}

/**
 * Validates the typed envelope and allowlist before persistence and again before
 * execution. [enforceFreshIssue] is true only when a request is created; a
 * five-minute request remains executable after the initial 60-second skew.
 */
object CommandPolicy {
    private val requestedByPattern = Regex("^android:[A-Za-z0-9_-]{1,56}$")

    fun validate(
        envelope: CommandEnvelope,
        nowMillis: Long,
        expectedTarget: String = MOCK_COMMAND_TARGET,
        confirmedAt: Long? = null,
        enforceFreshIssue: Boolean = true,
    ): CommandValidation {
        if (envelope.schema != COMMAND_SCHEMA) {
            return CommandValidation.Invalid("Unknown command schema")
        }
        if (!isCanonicalUuid(envelope.commandId)) {
            return CommandValidation.Invalid("command_id must be a UUID")
        }
        if (!CommandNonce.isValid(envelope.nonce)) {
            return CommandValidation.Invalid("nonce must be 32 random bytes in URL-safe Base64")
        }
        if (envelope.expiresAt <= envelope.issuedAt) {
            return CommandValidation.Invalid("expires_at must be after issued_at")
        }
        if (envelope.expiresAt - envelope.issuedAt > COMMAND_EXPIRY_MS) {
            return CommandValidation.Invalid("command window cannot exceed five minutes")
        }
        if (enforceFreshIssue && abs(nowMillis - envelope.issuedAt) > COMMAND_CLOCK_SKEW_MS) {
            return CommandValidation.Invalid("issued_at is outside the allowed 60-second skew")
        }
        if (nowMillis >= envelope.expiresAt) {
            return CommandValidation.Invalid("Command request has expired")
        }
        if (envelope.target != expectedTarget || envelope.target != MOCK_COMMAND_TARGET) {
            return CommandValidation.Invalid("Target is not the approved mock target")
        }
        if (!requestedByPattern.matches(envelope.requestedBy)) {
            return CommandValidation.Invalid("requested_by is invalid")
        }
        if (!NtfyEndpoint.isValidTopic(envelope.replyTopic)) {
            return CommandValidation.Invalid("reply_topic is not a valid ntfy topic")
        }

        val action = CommandCatalog.action(envelope.action)
            ?: return CommandValidation.Invalid("Unknown command action")
        if (envelope.requiresConfirmation != action.requiresConfirmation) {
            return CommandValidation.Invalid("requires_confirmation does not match the action policy")
        }
        CommandCatalog.validateArguments(action, envelope.args)?.let { reason ->
            return CommandValidation.Invalid(reason)
        }
        if (action.requiresConfirmation && !enforceFreshIssue) {
            if (confirmedAt == null) {
                return CommandValidation.Invalid("This destructive command requires confirmation")
            }
            if (confirmedAt < envelope.issuedAt || confirmedAt >= envelope.expiresAt) {
                return CommandValidation.Invalid("Confirmation timestamp is outside the request window")
            }
        }
        return CommandValidation.Valid
    }

    private fun isCanonicalUuid(value: String): Boolean = runCatching {
        UUID.fromString(value).toString().equals(value, ignoreCase = true)
    }.getOrDefault(false)
}
