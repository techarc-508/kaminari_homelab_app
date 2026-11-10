package com.rplab.kaminarinfty.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.rplab.kaminarinfty.commands.COMMAND_EXPIRY_MS
import com.rplab.kaminarinfty.commands.COMMAND_REPLY_TOPIC
import com.rplab.kaminarinfty.commands.CommandCatalog
import com.rplab.kaminarinfty.commands.CommandEnvelope
import com.rplab.kaminarinfty.commands.CommandExecutionResult
import com.rplab.kaminarinfty.commands.CommandExecutor
import com.rplab.kaminarinfty.commands.CommandNonce
import com.rplab.kaminarinfty.commands.CommandPolicy
import com.rplab.kaminarinfty.commands.CommandValidation
import com.rplab.kaminarinfty.commands.MOCK_COMMAND_TARGET
import com.rplab.kaminarinfty.data.local.ActivityEventEntity
import com.rplab.kaminarinfty.data.local.CommandRequestEntity
import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.data.local.RetentionPolicy
import com.rplab.kaminarinfty.model.ActivitySeverity
import com.rplab.kaminarinfty.model.ActivityType
import com.rplab.kaminarinfty.model.CommandRequest
import com.rplab.kaminarinfty.model.CommandResult
import com.rplab.kaminarinfty.model.CommandStatus
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONObject

sealed interface CommandCreateResult {
    data class AwaitingConfirmation(val command: CommandRequest) : CommandCreateResult
    data class Completed(val command: CommandRequest) : CommandCreateResult
    data class Rejected(val reason: String) : CommandCreateResult
}

sealed interface CommandActionResult {
    data class Completed(val command: CommandRequest) : CommandActionResult
    data class Expired(val command: CommandRequest?) : CommandActionResult
    data class Rejected(val reason: String) : CommandActionResult
}

interface CommandRepository {
    fun observe(profileId: String, limit: Int = 100): Flow<List<CommandRequest>>

    /** Creates one typed request. Non-destructive actions execute as MOCK immediately. */
    suspend fun create(
        profileId: String,
        actionId: String,
        args: Map<String, String>,
    ): CommandCreateResult

    /** Confirms the exact profile/request/nonce and then executes it as MOCK. */
    suspend fun confirmAndExecute(
        profileId: String,
        commandId: String,
        nonce: String,
    ): CommandActionResult

    /** Resumes a SUBMITTED request that already has its required confirmation. */
    suspend fun execute(profileId: String, commandId: String): CommandActionResult

    /** Explicitly rejects a pending confirmation; never executes. */
    suspend fun reject(
        profileId: String,
        commandId: String,
        nonce: String,
    ): CommandActionResult

    /** No background timer: callers invoke this when a command surface loads. */
    suspend fun expireOverdue(profileId: String): Int

    suspend fun prune(profileId: String)
}

/**
 * The only Phase 4 command orchestration. [executor] is the isolated mock
 * boundary; this class has no HTTP, PVE, SSH, shell or process dependency.
 */
class RoomCommandRepository(
    private val database: KaminariDatabase,
    private val activityRepository: ActivityRepository,
    private val executor: CommandExecutor,
    private val now: () -> Long = System::currentTimeMillis,
    private val nonceGenerator: () -> String = CommandNonce::generate,
    private val commandIdGenerator: () -> String = { UUID.randomUUID().toString() },
) : CommandRepository {

    private val commandTarget: String = executor.target
    private val commandDao = database.commandDao()
    private val activityDao = database.activityDao()
    private val profileDao = database.serverProfileDao()

    override fun observe(profileId: String, limit: Int): Flow<List<CommandRequest>> {
        if (profileId.isBlank()) return flowOf(emptyList())
        return commandDao.observeRecent(profileId, limit.coerceAtLeast(1))
            .map { rows -> rows.map(::toDomain) }
    }

    override suspend fun create(
        profileId: String,
        actionId: String,
        args: Map<String, String>,
    ): CommandCreateResult {
        if (profileId.isBlank()) return CommandCreateResult.Rejected("Select a server profile first")
        val action = CommandCatalog.action(actionId)
            ?: return CommandCreateResult.Rejected("Unknown command action")
        val normalizedArgs = args.mapValues { (_, value) -> value.trim() }
        CommandCatalog.validateArguments(action, normalizedArgs)?.let { reason ->
            return CommandCreateResult.Rejected(reason)
        }
        if (profileDao.getById(profileId) == null) {
            return CommandCreateResult.Rejected("The selected server profile is no longer available")
        }

        val timestamp = now()
        val commandId = commandIdGenerator()
        val nonce = nonceGenerator()
        val envelope = CommandEnvelope(
            commandId = commandId,
            nonce = nonce,
            issuedAt = timestamp,
            expiresAt = timestamp + COMMAND_EXPIRY_MS,
            requestedBy = "android:$profileId",
            target = commandTarget,
            action = action.id,
            args = normalizedArgs,
            replyTopic = COMMAND_REPLY_TOPIC,
            requiresConfirmation = action.requiresConfirmation,
        )
        val validation = CommandPolicy.validate(
            envelope = envelope,
            nowMillis = timestamp,
            expectedTarget = commandTarget,
            enforceFreshIssue = true,
        )
        if (validation is CommandValidation.Invalid) {
            return CommandCreateResult.Rejected(validation.reason)
        }

        val entity = CommandRequestEntity(
            commandId = commandId,
            profileId = profileId,
            actionId = action.id,
            target = envelope.target,
            argsJson = argsJson(normalizedArgs),
            nonce = nonce,
            issuedAt = timestamp,
            expiresAt = timestamp + COMMAND_EXPIRY_MS,
            requiresConfirmation = action.requiresConfirmation,
            confirmedAt = null,
            status = CommandStatus.SUBMITTED.name,
            resultJson = null,
            resultSummary = null,
            createdAt = timestamp,
            updatedAt = timestamp,
        )

        try {
            database.withTransaction {
                if (commandDao.findByNonce(profileId, nonce) != null) {
                    error("duplicate nonce")
                }
                commandDao.insert(entity)
                activityDao.insert(
                    event(
                        profileId = profileId,
                        commandId = commandId,
                        type = ActivityType.COMMAND_SUBMITTED,
                        severity = ActivitySeverity.INFO,
                        summary = "Mock command ${action.id} requested (no real execution yet)",
                        eventId = "command:$commandId:submitted",
                        timestamp = timestamp,
                    )
                )
                if (action.requiresConfirmation) {
                    activityDao.insert(
                        event(
                            profileId = profileId,
                            commandId = commandId,
                            type = ActivityType.COMMAND_CONFIRMATION_REQUIRED,
                            severity = ActivitySeverity.WARNING,
                            summary = "Destructive mock command ${action.id} requires confirmation",
                            eventId = "command:$commandId:confirmation_required",
                            timestamp = timestamp,
                        )
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: SQLiteConstraintException) {
            return CommandCreateResult.Rejected("That command nonce has already been used")
        } catch (e: Exception) {
            val reason = e.message.orEmpty()
            return if (reason.contains("duplicate nonce", ignoreCase = true)) {
                CommandCreateResult.Rejected("That command nonce has already been used")
            } else {
                CommandCreateResult.Rejected("Could not persist the command request")
            }
        }

        prune(profileId)
        val created = toDomain(entity)
        return if (action.requiresConfirmation) {
            CommandCreateResult.AwaitingConfirmation(created)
        } else {
            when (val outcome = executeClaimed(profileId, commandId)) {
                is CommandActionResult.Completed -> CommandCreateResult.Completed(outcome.command)
                is CommandActionResult.Expired -> CommandCreateResult.Rejected("Command request expired")
                is CommandActionResult.Rejected -> CommandCreateResult.Rejected(outcome.reason)
            }
        }
    }

    override suspend fun confirmAndExecute(
        profileId: String,
        commandId: String,
        nonce: String,
    ): CommandActionResult {
        if (profileId.isBlank() || commandId.isBlank() || nonce.isBlank()) {
            return CommandActionResult.Rejected("Confirmation does not match an active request")
        }
        val timestamp = now()
        val confirmed = try {
            database.withTransaction {
                val rows = commandDao.confirm(profileId, commandId, nonce, timestamp)
                if (rows > 0) {
                    activityDao.insert(
                        event(
                            profileId = profileId,
                            commandId = commandId,
                            type = ActivityType.COMMAND_CONFIRMED,
                            severity = ActivitySeverity.INFO,
                            summary = "Confirmation accepted for mock command $commandId",
                            eventId = "command:$commandId:confirmed",
                            timestamp = timestamp,
                        )
                    )
                }
                rows
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            return CommandActionResult.Rejected("Could not record confirmation")
        }

        if (confirmed == 0) return diagnosePending(profileId, commandId, nonce)
        return executeClaimed(profileId, commandId)
    }

    override suspend fun execute(profileId: String, commandId: String): CommandActionResult {
        if (profileId.isBlank() || commandId.isBlank()) {
            return CommandActionResult.Rejected("Command request was not found")
        }
        val current = commandDao.getById(profileId, commandId)
            ?: return CommandActionResult.Rejected("Command request was not found")
        if (current.status != CommandStatus.SUBMITTED.name) {
            return CommandActionResult.Rejected(
                "Command is already ${current.status.lowercase()}"
            )
        }
        if (current.expiresAt <= now()) {
            expireOverdue(profileId)
            return CommandActionResult.Expired(
                commandDao.getById(profileId, commandId)?.let(::toDomain)
            )
        }
        if (current.requiresConfirmation && current.confirmedAt == null) {
            return CommandActionResult.Rejected("Confirm this destructive request first")
        }
        return executeClaimed(profileId, commandId)
    }

    override suspend fun reject(
        profileId: String,
        commandId: String,
        nonce: String,
    ): CommandActionResult {
        if (profileId.isBlank() || commandId.isBlank() || nonce.isBlank()) {
            return CommandActionResult.Rejected("Rejection does not match an active request")
        }
        val timestamp = now()
        val rejected = try {
            database.withTransaction {
                val rows = commandDao.reject(profileId, commandId, nonce, timestamp)
                if (rows > 0) {
                    activityDao.insert(
                        event(
                            profileId = profileId,
                            commandId = commandId,
                            type = ActivityType.COMMAND_REJECTED,
                            severity = ActivitySeverity.WARNING,
                            summary = "Mock command $commandId was rejected without execution",
                            eventId = "command:$commandId:rejected",
                            timestamp = timestamp,
                        )
                    )
                }
                rows
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            return CommandActionResult.Rejected("Could not record rejection")
        }

        if (rejected == 0) return diagnosePending(profileId, commandId, nonce)
        prune(profileId)
        val command = commandDao.getById(profileId, commandId)?.let(::toDomain)
        return CommandActionResult.Completed(
            command ?: return CommandActionResult.Rejected("Command request was not found")
        )
    }

    override suspend fun expireOverdue(profileId: String): Int {
        if (profileId.isBlank()) return 0
        val timestamp = now()
        val overdue = commandDao.getOverdueSubmitted(profileId, timestamp)
        if (overdue.isEmpty()) return 0
        var expired = 0
        try {
            database.withTransaction {
                overdue.forEach { candidate ->
                    if (commandDao.expireOne(profileId, candidate.commandId, timestamp) > 0) {
                        expired++
                        activityDao.insert(
                            event(
                                profileId = profileId,
                                commandId = candidate.commandId,
                                type = ActivityType.COMMAND_EXPIRED,
                                severity = ActivitySeverity.WARNING,
                                summary = "Mock command ${candidate.actionId} expired before execution",
                                eventId = "command:${candidate.commandId}:expired",
                                timestamp = timestamp,
                            )
                        )
                    }
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            return 0
        }
        if (expired > 0) prune(profileId)
        return expired
    }

    override suspend fun prune(profileId: String) {
        if (profileId.isBlank()) return
        val timestamp = now()
        commandDao.prune(
            profileId = profileId,
            cutoff = RetentionPolicy.commandCutoff(timestamp),
            keepMax = RetentionPolicy.COMMAND_MAX_TERMINAL_ENTRIES_PER_PROFILE,
        )
        activityRepository.prune(profileId)
    }

    private sealed interface Claim {
        data class Acquired(val command: CommandRequest) : Claim
        data class Rejected(val reason: String) : Claim
        data object Expired : Claim
    }

    private suspend fun executeClaimed(profileId: String, commandId: String): CommandActionResult {
        val timestamp = now()
        val claim = try {
            database.withTransaction {
                val current = commandDao.getById(profileId, commandId)
                    ?: return@withTransaction Claim.Rejected("Command request was not found")
                when {
                    current.status != CommandStatus.SUBMITTED.name -> Claim.Rejected(
                        "Command is already ${current.status.lowercase()}"
                    )
                    current.expiresAt <= timestamp -> {
                        if (commandDao.expireOne(profileId, commandId, timestamp) > 0) {
                            activityDao.insert(
                                event(
                                    profileId = profileId,
                                    commandId = commandId,
                                    type = ActivityType.COMMAND_EXPIRED,
                                    severity = ActivitySeverity.WARNING,
                                    summary = "Mock command ${current.actionId} expired before execution",
                                    eventId = "command:$commandId:expired",
                                    timestamp = timestamp,
                                )
                            )
                        }
                        Claim.Expired
                    }
                    current.requiresConfirmation && current.confirmedAt == null ->
                        Claim.Rejected("Confirm this destructive request first")
                    activityDao.insert(
                        event(
                            profileId = profileId,
                            commandId = commandId,
                            type = ActivityType.COMMAND_EXECUTION_STARTED,
                            severity = ActivitySeverity.INFO,
                            summary = "Mock execution started for ${current.actionId}",
                            eventId = "command:$commandId:execution_started",
                            timestamp = timestamp,
                        )
                    ) == -1L -> Claim.Rejected(
                        "This mock execution was already claimed; it is not retried automatically"
                    )
                    else -> Claim.Acquired(toDomain(current))
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            return CommandActionResult.Rejected("Could not claim the mock execution")
        }

        when (claim) {
            Claim.Expired -> {
                prune(profileId)
                return CommandActionResult.Expired(
                    commandDao.getById(profileId, commandId)?.let(::toDomain)
                )
            }
            is Claim.Rejected -> return CommandActionResult.Rejected(claim.reason)
            is Claim.Acquired -> Unit
        }

        val command = (claim as Claim.Acquired).command
        val envelope = command.toEnvelope()
        val execution = try {
            executor.execute(envelope, command.confirmedAt)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            CommandExecutionResult(
                result = CommandResult(
                    mock = true,
                    ok = false,
                    summary = "MOCK executor failed before producing a result",
                    detailJson = "{}",
                ),
                expired = false,
            )
        }

        val timestampAfter = now()
        val status = when {
            execution.expired -> CommandStatus.EXPIRED
            execution.result.ok -> CommandStatus.COMPLETED
            else -> CommandStatus.FAILED
        }
        val transition = try {
            database.withTransaction {
                if (status == CommandStatus.EXPIRED) {
                    val rows = commandDao.expireOne(profileId, commandId, timestampAfter)
                    if (rows > 0) {
                        activityDao.insert(
                            event(
                                profileId = profileId,
                                commandId = commandId,
                                type = ActivityType.COMMAND_EXPIRED,
                                severity = ActivitySeverity.WARNING,
                                summary = "Mock command ${command.actionId} expired before execution",
                                eventId = "command:$commandId:expired",
                                timestamp = timestampAfter,
                            )
                        )
                    }
                    rows
                } else {
                    val rows = commandDao.markStatus(
                        profileId = profileId,
                        commandId = commandId,
                        status = status.name,
                        resultJson = execution.result.toJson(),
                        resultSummary = execution.result.summary,
                        now = timestampAfter,
                    )
                    if (rows > 0) {
                        activityDao.insert(
                            event(
                                profileId = profileId,
                                commandId = commandId,
                                type = ActivityType.COMMAND_RESULT,
                                severity = if (execution.result.ok) {
                                    ActivitySeverity.SUCCESS
                                } else {
                                    ActivitySeverity.ERROR
                                },
                                summary = "Mock execution ${if (execution.result.ok) "completed" else "failed"} " +
                                    "for ${command.actionId}: ${execution.result.summary}",
                                eventId = "command:$commandId:result",
                                timestamp = timestampAfter,
                            )
                        )
                    }
                    rows
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            0
        }

        if (transition == 0) {
            return CommandActionResult.Rejected(
                "The command state changed before the mock result could be recorded"
            )
        }
        prune(profileId)
        return if (status == CommandStatus.EXPIRED) {
            CommandActionResult.Expired(commandDao.getById(profileId, commandId)?.let(::toDomain))
        } else {
            val updated = commandDao.getById(profileId, commandId)?.let(::toDomain)
                ?: return CommandActionResult.Rejected("Command request was not found")
            CommandActionResult.Completed(updated)
        }
    }

    private suspend fun diagnosePending(
        profileId: String,
        commandId: String,
        nonce: String,
    ): CommandActionResult {
        expireOverdue(profileId)
        val current = commandDao.getById(profileId, commandId)
            ?: return CommandActionResult.Rejected("Command request was not found")
        if (current.nonce != nonce) {
            return CommandActionResult.Rejected("Confirmation does not match this request")
        }
        return when (CommandStatus.fromPersisted(current.status)) {
            CommandStatus.EXPIRED -> CommandActionResult.Expired(toDomain(current))
            CommandStatus.SUBMITTED -> CommandActionResult.Rejected(
                if (current.confirmedAt != null) {
                    "Confirmation was already recorded"
                } else {
                    "Confirmation does not match an active request"
                }
            )
            else -> CommandActionResult.Rejected(
                "Command is already ${current.status.lowercase()}"
            )
        }
    }

    private fun toDomain(entity: CommandRequestEntity): CommandRequest = CommandRequest(
        commandId = entity.commandId,
        profileId = entity.profileId,
        actionId = entity.actionId,
        target = entity.target,
        args = parseArgs(entity.argsJson),
        nonce = entity.nonce,
        issuedAt = entity.issuedAt,
        expiresAt = entity.expiresAt,
        requiresConfirmation = entity.requiresConfirmation,
        confirmedAt = entity.confirmedAt,
        status = CommandStatus.fromPersisted(entity.status),
        resultJson = entity.resultJson,
        resultSummary = entity.resultSummary,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
    )

    private fun CommandRequest.toEnvelope(): CommandEnvelope = CommandEnvelope(
        commandId = commandId,
        nonce = nonce,
        issuedAt = issuedAt,
        expiresAt = expiresAt,
        requestedBy = "android:$profileId",
        target = target,
        action = actionId,
        args = args,
        replyTopic = COMMAND_REPLY_TOPIC,
        requiresConfirmation = requiresConfirmation,
    )

    private fun argsJson(args: Map<String, String>): String {
        val json = JSONObject()
        args.forEach { (key, value) -> json.put(key, value) }
        return json.toString()
    }

    private fun parseArgs(raw: String): Map<String, String> = runCatching {
        val json = JSONObject(raw)
        buildMap {
            json.keys().forEach { key -> put(key, json.getString(key)) }
        }
    }.getOrDefault(emptyMap())

    private fun event(
        profileId: String,
        commandId: String,
        type: ActivityType,
        severity: ActivitySeverity,
        summary: String,
        eventId: String,
        timestamp: Long,
    ) = ActivityEventEntity(
        id = eventId,
        profileId = profileId,
        type = type.name,
        severity = severity.name,
        summary = summary,
        correlationId = commandId,
        messageId = null,
        topic = null,
        createdAt = timestamp,
    )

}
