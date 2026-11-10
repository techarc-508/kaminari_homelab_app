package com.rplab.kaminarinfty.commands

import com.rplab.kaminarinfty.model.CommandResult
import org.json.JSONObject

/**
 * Executor boundary. A real gateway is Phase 6 and requires a separate security
 * review; Phase 4 ships only [MockCommandExecutor].
 */
interface CommandExecutor {
    suspend fun execute(
        envelope: CommandEnvelope,
        confirmedAt: Long?,
    ): CommandExecutionResult
}

data class CommandExecutionResult(
    val result: CommandResult,
    val expired: Boolean = false,
)

/**
 * Pure, deterministic, in-process executor.
 *
 * This class intentionally has no Android context, HTTP client, process, shell,
 * socket, SSH or PVE dependency. It can only return a simulated result for a
 * typed envelope that passes the same allowlist policy as the repository.
 */
class MockCommandExecutor(
    private val now: () -> Long = System::currentTimeMillis,
    private val failActions: Set<String> = emptySet(),
) : CommandExecutor {

    override suspend fun execute(
        envelope: CommandEnvelope,
        confirmedAt: Long?,
    ): CommandExecutionResult {
        val timestamp = now()
        val validation = CommandPolicy.validate(
            envelope = envelope,
            nowMillis = timestamp,
            expectedTarget = MOCK_COMMAND_TARGET,
            confirmedAt = confirmedAt,
            enforceFreshIssue = false,
        )
        if (validation is CommandValidation.Invalid) {
            return rejection(validation.reason, timestamp >= envelope.expiresAt)
        }

        val action = CommandCatalog.action(envelope.action)
            ?: return rejection("Unknown command action", expired = false)
        val simulatedFailure = envelope.action in failActions
        val summary = if (simulatedFailure) {
            "MOCK: simulated failure for ${action.id} (no real infrastructure changed)"
        } else {
            "MOCK: ${action.mockResult(envelope.args)}"
        }
        val detail = JSONObject()
            .put("mock", true)
            .put("simulated", true)
            .put("realInfrastructureChanged", false)
            .put("action", action.id)
            .put("target", envelope.target)
            .put("simulatedFailure", simulatedFailure)
            .toString()
        return CommandExecutionResult(
            result = CommandResult(
                mock = true,
                ok = !simulatedFailure,
                summary = summary,
                detailJson = detail,
            ),
        )
    }

    private fun rejection(reason: String, expired: Boolean): CommandExecutionResult =
        CommandExecutionResult(
            result = CommandResult(
                mock = true,
                ok = false,
                summary = "MOCK rejected: $reason",
                detailJson = JSONObject()
                    .put("mock", true)
                    .put("simulated", true)
                    .put("realInfrastructureChanged", false)
                    .put("rejected", true)
                    .toString(),
            ),
            expired = expired,
        )
}
