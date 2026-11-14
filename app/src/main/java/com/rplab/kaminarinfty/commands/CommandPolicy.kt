package com.rplab.kaminarinfty.commands

import com.rplab.kaminarinfty.contract.KfActionCatalog
import com.rplab.kaminarinfty.contract.KfRisk
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
    val risk: KfRisk = KfRisk.L0,
    val mutation: Boolean = false,
    val stepUpRequired: Boolean = false,
    val ownerConfirmation: Boolean = false,
    val mockResult: (Map<String, String>) -> String,
)

/**
 * Android's typed view of the shared action catalogue. The operation IDs, risk
 * levels and wire confirmation rule come from [KfActionCatalog]; a contract
 * test asserts parity with `contracts/kf_contract.v1.json` and the gateway.
 */
object CommandCatalog {
    private val nodePattern = Regex("^[a-z0-9][a-z0-9_-]{0,31}$")
    private val vmidPattern = Regex("^[0-9]{1,10}$")

    private fun argument(key: String): CommandArgument = when (key) {
        "node" -> CommandArgument(
            key = "node",
            label = "Node",
            placeholder = "pve",
            pattern = nodePattern,
            hint = "lowercase letters, digits, _ or - (max 32)",
        )
        "vmid" -> CommandArgument(
            key = "vmid",
            label = "Guest ID",
            placeholder = "100",
            pattern = vmidPattern,
            hint = "digits only (max 10)",
        )
        else -> error("Unsupported command argument: $key")
    }

    private val mockResults: Map<String, (Map<String, String>) -> String> = mapOf(
        "host.health" to { "host healthy (simulated)" },
        "node.list" to { "1 node (simulated)" },
        "vm.list" to { args -> "VM inventory for ${args["node"]} (simulated)" },
        "container.list" to { args -> "container inventory for ${args["node"]} (simulated)" },
        "vm.status" to { args -> "VM ${args["vmid"]} simulated running" },
        "container.status" to { args -> "container ${args["vmid"]} simulated running" },
        "storage.summary" to { args -> "storage on ${args["node"]} simulated" },
        "task.history" to { args -> "recent tasks on ${args["node"]} simulated" },
        "vm.start" to { args -> "start of VM ${args["vmid"]} simulated; no real guest changed" },
        "vm.stop" to { args -> "stop of VM ${args["vmid"]} simulated; no real guest changed" },
        "vm.restart" to { args -> "restart of VM ${args["vmid"]} simulated; no real guest changed" },
        "container.start" to { args ->
            "start of container ${args["vmid"]} simulated; no real guest changed"
        },
        "container.stop" to { args ->
            "stop of container ${args["vmid"]} simulated; no real guest changed"
        },
        "container.restart" to { args ->
            "restart of container ${args["vmid"]} simulated; no real guest changed"
        },
    )

    val actions: List<CommandActionSpec> = KfActionCatalog.actions.map { spec ->
        CommandActionSpec(
            id = spec.id,
            title = spec.title,
            description = spec.description,
            arguments = spec.args.map(::argument),
            // The wire field is true for every mutation so the gateway can bind
            // an approval to it; risk metadata still distinguishes L1 from L2.
            requiresConfirmation = spec.mutation,
            risk = spec.risk,
            mutation = spec.mutation,
            stepUpRequired = spec.stepUp,
            ownerConfirmation = spec.ownerConfirmation,
            mockResult = mockResults.getValue(spec.id),
        )
    }

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
            if (!spec.pattern.matches(value)) {
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
    private val replyTopics = setOf(
        COMMAND_REPLY_TOPIC,
        LEGACY_COMMAND_REPLY_TOPIC,
    )

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
        if (envelope.target != expectedTarget) {
            return CommandValidation.Invalid("Target is not the approved command target")
        }
        if (!requestedByPattern.matches(envelope.requestedBy)) {
            return CommandValidation.Invalid("requested_by is invalid")
        }
        if (envelope.replyTopic !in replyTopics) {
            return CommandValidation.Invalid("reply_topic is not an approved result topic")
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
