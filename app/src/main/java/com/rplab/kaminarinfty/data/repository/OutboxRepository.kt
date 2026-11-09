package com.rplab.kaminarinfty.data.repository

import androidx.room.withTransaction
import com.rplab.kaminarinfty.data.api.NtfyEndpoint
import com.rplab.kaminarinfty.data.api.PublishRequest
import com.rplab.kaminarinfty.data.api.PublishResult
import com.rplab.kaminarinfty.data.api.PublishValidator
import com.rplab.kaminarinfty.data.local.ActivityEventEntity
import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.data.local.OutboxEntryEntity
import com.rplab.kaminarinfty.model.ActivitySeverity
import com.rplab.kaminarinfty.model.ActivityType
import com.rplab.kaminarinfty.model.OutboxEntry
import com.rplab.kaminarinfty.model.OutboxState
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.json.JSONArray

sealed interface SendOutcome {
    /** The server accepted the publish. This never implies recipient delivery. */
    data class Accepted(val serverMessageId: String?) : SendOutcome
    data class Failed(val reason: String, val retryable: Boolean) : SendOutcome
    data class Ambiguous(val reason: String) : SendOutcome
    data object Busy : SendOutcome
    data object NotFound : SendOutcome

    /** A retry from AMBIGUOUS needs an explicit "may duplicate" confirmation. */
    data class ConfirmationRequired(val reason: String) : SendOutcome
}

interface OutboxRepository {
    fun observe(profileId: String, limit: Int = 100): Flow<List<OutboxEntry>>

    /** Validates and persists one intent; never touches the network. */
    suspend fun enqueue(profileId: String, request: PublishRequest): Result<String>

    /** Claims and publishes exactly once. */
    suspend fun send(profileId: String, clientId: String): SendOutcome

    /** Explicit user retry. Ambiguous rows require [allowAmbiguous]. */
    suspend fun retry(
        profileId: String,
        clientId: String,
        allowAmbiguous: Boolean = false,
    ): SendOutcome

    suspend fun cancel(profileId: String, clientId: String): Boolean

    /** Moves orphaned SENDING rows to AMBIGUOUS; never back to QUEUED. */
    suspend fun recoverStuck(profileId: String): Int

    suspend fun prune(profileId: String)
}

/**
 * Room-backed outbox orchestration.
 *
 * [publish] is the Stage B profile-scoped publish entry point; the default
 * wiring in [com.rplab.kaminarinfty.AppContainer] points at
 * [NftyRepository.publish]. Credentials are loaded by that call from
 * [ProfileRepository] and never persisted in an outbox row.
 */
class RoomOutboxRepository(
    private val database: KaminariDatabase,
    private val profileRepository: ProfileRepository,
    private val activityRepository: ActivityRepository,
    private val publish: suspend (profileId: String, request: PublishRequest) -> PublishResult,
    private val now: () -> Long = System::currentTimeMillis,
) : OutboxRepository {

    private val outboxDao = database.outboxDao()
    private val activityDao = database.activityDao()

    override fun observe(profileId: String, limit: Int): Flow<List<OutboxEntry>> {
        if (profileId.isBlank()) return flowOf(emptyList())
        return outboxDao.observeRecent(profileId, limit.coerceAtLeast(1))
            .map { rows -> rows.map(::toDomain) }
    }

    override suspend fun enqueue(profileId: String, request: PublishRequest): Result<String> {
        if (profileId.isBlank()) {
            return Result.failure(IllegalArgumentException("Select a server profile before publishing"))
        }
        return try {
            val config = profileRepository.loadConfig(profileId)
                ?: return Result.failure(IllegalArgumentException("Unknown server profile"))
            PublishValidator.validate(config, request)?.let { reason ->
                return Result.failure(IllegalArgumentException(reason))
            }

            val clientId = UUID.randomUUID().toString()
            val timestamp = now()
            database.withTransaction {
                outboxDao.insert(
                    OutboxEntryEntity(
                        clientId = clientId,
                        profileId = profileId,
                        topic = request.topic,
                        title = request.title,
                        body = request.message,
                        priority = request.priority,
                        tagsJson = JSONArray(request.tags).toString(),
                        clickUrl = request.clickUrl,
                        attachmentUrl = request.attachUrl,
                        actionsJson = null,
                        delay = request.delay,
                        state = OutboxState.QUEUED.name,
                        attemptCount = 0,
                        retryable = false,
                        lastError = null,
                        serverMessageId = null,
                        createdAt = timestamp,
                        updatedAt = timestamp,
                        acceptedAt = null,
                    )
                )
                activityDao.insert(
                    event(
                        entryId = clientId,
                        profileId = profileId,
                        type = ActivityType.PUBLISH_QUEUED,
                        severity = ActivitySeverity.INFO,
                        summary = "Publish to ${request.topic} queued",
                        topic = request.topic,
                        eventId = "outbox:$clientId:${ActivityType.PUBLISH_QUEUED.name}",
                        timestamp = timestamp,
                    )
                )
            }
            activityRepository.prune(profileId)
            Result.success(clientId)
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun send(profileId: String, clientId: String): SendOutcome {
        if (profileId.isBlank() || clientId.isBlank()) return SendOutcome.NotFound
        return try {
            // A row left SENDING by a dead process is uncertain, never resumable.
            recoverStuck(profileId)

            val entry = outboxDao.getById(profileId, clientId) ?: return SendOutcome.NotFound
            if (entry.profileId != profileId) return SendOutcome.NotFound

            if (outboxDao.claim(profileId, clientId, now()) == 0) return SendOutcome.Busy
            val attempt = entry.attemptCount + 1
            recordStarted(entry, attempt)
            publishClaimed(entry, attempt)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Storage can fail before a claim (nothing was sent) or after one
            // (the row stays SENDING and recovery will mark it AMBIGUOUS).
            SendOutcome.Ambiguous("Local storage failed; the publish outcome is unknown")
        }
    }

    override suspend fun retry(
        profileId: String,
        clientId: String,
        allowAmbiguous: Boolean,
    ): SendOutcome {
        if (profileId.isBlank() || clientId.isBlank()) return SendOutcome.NotFound
        return try {
            val entry = outboxDao.getById(profileId, clientId) ?: return SendOutcome.NotFound
            if (entry.profileId != profileId) return SendOutcome.NotFound

            when (OutboxState.fromPersisted(entry.state)) {
                OutboxState.QUEUED -> {
                    // Another tap may have raced the first send; the claim decides.
                    recordRetry(entry, blocked = false)
                    return send(profileId, clientId)
                }
                OutboxState.SENDING -> return SendOutcome.Busy
                OutboxState.ACCEPTED ->
                    return SendOutcome.Failed("Already accepted by the server", retryable = false)
                OutboxState.FAILED -> {
                    if (!entry.retryable) {
                        recordRetry(entry, blocked = true)
                        return SendOutcome.Failed(
                            "This failure is not retryable; create a new publish",
                            retryable = false,
                        )
                    }
                }
                OutboxState.AMBIGUOUS -> {
                    if (!allowAmbiguous) {
                        recordRetry(entry, blocked = true)
                        return SendOutcome.ConfirmationRequired(
                            "The server may already have accepted this publish; retrying could duplicate it",
                        )
                    }
                }
            }

            recordRetry(entry, blocked = false)
            if (outboxDao.requeue(profileId, clientId, now()) == 0) return SendOutcome.Busy
            send(profileId, clientId)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            // No network call happens until the requeue succeeds.
            SendOutcome.Failed("Local storage failed; retry was not started", retryable = true)
        }
    }

    override suspend fun cancel(profileId: String, clientId: String): Boolean {
        if (profileId.isBlank() || clientId.isBlank()) return false
        return try {
            outboxDao.deleteIfUnsent(profileId, clientId) > 0
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            false
        }
    }

    override suspend fun recoverStuck(profileId: String): Int {
        if (profileId.isBlank()) return 0
        return try {
            val timestamp = now()
            val cutoff = timestamp - STUCK_SENDING_TIMEOUT_MS
            val stale = outboxDao.getStuckSending(profileId, cutoff)
            if (stale.isEmpty()) return 0

            val recovered = database.withTransaction {
                val rows = outboxDao.recoverStuckSending(profileId, cutoff, timestamp, STUCK_REASON)
                if (rows > 0) {
                    stale.forEach { candidate ->
                        val current = outboxDao.getById(profileId, candidate.clientId) ?: return@forEach
                        if (OutboxState.fromPersisted(current.state) != OutboxState.AMBIGUOUS) return@forEach
                        if (current.lastError != STUCK_REASON) return@forEach
                        activityDao.insert(
                            event(
                                entryId = candidate.clientId,
                                profileId = profileId,
                                type = ActivityType.PUBLISH_AMBIGUOUS,
                                severity = ActivitySeverity.WARNING,
                                summary = "Publish to ${candidate.topic} was interrupted; outcome unknown",
                                topic = candidate.topic,
                                eventId = "outbox:${candidate.clientId}:recovered",
                                timestamp = timestamp,
                            )
                        )
                    }
                }
                rows
            }
            if (recovered > 0) activityRepository.prune(profileId)
            recovered
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Rows stay SENDING; a later recovery pass can safely try again.
            0
        }
    }

    override suspend fun prune(profileId: String) {
        if (profileId.isBlank()) return
        outboxDao.prune(profileId, now())
        activityRepository.prune(profileId)
    }

    private suspend fun publishClaimed(entry: OutboxEntryEntity, attempt: Int): SendOutcome {
        val config = profileRepository.loadConfig(entry.profileId)
            ?: return finish(
                entry = entry,
                attempt = attempt,
                state = OutboxState.FAILED,
                serverMessageId = null,
                lastError = "The selected server profile is no longer available",
                retryable = false,
                acceptedAt = null,
            )

        val request = entry.toPublishRequest()
        PublishValidator.validate(config, request)?.let { reason ->
            return finish(
                entry = entry,
                attempt = attempt,
                state = OutboxState.FAILED,
                serverMessageId = null,
                lastError = reason,
                retryable = NtfyEndpoint.isValidTopic(entry.topic),
                acceptedAt = null,
            )
        }

        val result = try {
            publish(entry.profileId, request)
        } catch (ce: CancellationException) {
            // Process/screen cancellation leaves the row SENDING; recovery turns it
            // into AMBIGUOUS. Never invent an outcome after cancellation.
            throw ce
        } catch (e: Exception) {
            PublishResult.Ambiguous("Unexpected publish failure: ${e.message.orEmpty()}")
        }

        return when (result) {
            is PublishResult.Accepted -> finish(
                entry = entry,
                attempt = attempt,
                state = OutboxState.ACCEPTED,
                serverMessageId = result.serverMessageId,
                lastError = null,
                retryable = false,
                acceptedAt = now(),
            )
            is PublishResult.ValidationFailed -> finish(
                entry = entry,
                attempt = attempt,
                state = OutboxState.FAILED,
                serverMessageId = null,
                lastError = result.reason,
                retryable = false,
                acceptedAt = null,
            )
            is PublishResult.AuthenticationFailed -> finish(
                entry = entry,
                attempt = attempt,
                state = OutboxState.FAILED,
                serverMessageId = null,
                lastError = "Authentication failed (${result.statusCode}); update the profile credentials",
                retryable = true,
                acceptedAt = null,
            )
            is PublishResult.PermissionFailed -> finish(
                entry = entry,
                attempt = attempt,
                state = OutboxState.FAILED,
                serverMessageId = null,
                lastError = "Permission denied (${result.statusCode}); check the ntfy topic permissions",
                retryable = true,
                acceptedAt = null,
            )
            is PublishResult.ServerError -> finish(
                entry = entry,
                attempt = attempt,
                state = OutboxState.FAILED,
                serverMessageId = null,
                lastError = "Server error (${result.statusCode}); retry later",
                retryable = true,
                acceptedAt = null,
            )
            is PublishResult.NetworkError -> {
                if (definitelyNotSent(result.cause)) {
                    finish(
                        entry = entry,
                        attempt = attempt,
                        state = OutboxState.FAILED,
                        serverMessageId = null,
                        lastError = "Network unreachable; the publish was not sent",
                        retryable = true,
                        acceptedAt = null,
                    )
                } else {
                    finish(
                        entry = entry,
                        attempt = attempt,
                        state = OutboxState.AMBIGUOUS,
                        serverMessageId = null,
                        lastError = "Network failure after sending; acceptance is unconfirmed",
                        retryable = true,
                        acceptedAt = null,
                    )
                }
            }
            is PublishResult.Ambiguous -> finish(
                entry = entry,
                attempt = attempt,
                state = OutboxState.AMBIGUOUS,
                serverMessageId = null,
                lastError = result.reason,
                retryable = true,
                acceptedAt = null,
            )
        }
    }

    private suspend fun finish(
        entry: OutboxEntryEntity,
        attempt: Int,
        state: OutboxState,
        serverMessageId: String?,
        lastError: String?,
        retryable: Boolean,
        acceptedAt: Long?,
    ): SendOutcome {
        val timestamp = now()
        val reason = lastError?.let(::sanitizeReason)
        val completed = database.withTransaction {
            val rows = outboxDao.complete(
                profileId = entry.profileId,
                clientId = entry.clientId,
                state = state.name,
                serverMessageId = serverMessageId,
                lastError = reason,
                retryable = retryable,
                acceptedAt = acceptedAt,
                now = timestamp,
            )
            if (rows > 0) {
                val (type, severity, summary) = terminalEvent(entry, state, reason)
                activityDao.insert(
                    event(
                        entryId = entry.clientId,
                        profileId = entry.profileId,
                        type = type,
                        severity = severity,
                        summary = summary,
                        topic = entry.topic,
                        eventId = "outbox:${entry.clientId}:${type.name}:$attempt",
                        timestamp = timestamp,
                    )
                )
            }
            rows
        }
        if (completed == 0) return SendOutcome.Busy

        activityRepository.prune(entry.profileId)
        outboxDao.prune(entry.profileId, timestamp)
        return when (state) {
            OutboxState.ACCEPTED -> SendOutcome.Accepted(serverMessageId)
            OutboxState.FAILED -> SendOutcome.Failed(
                reason ?: "Publish failed",
                retryable,
            )
            OutboxState.AMBIGUOUS -> SendOutcome.Ambiguous(
                reason ?: "Publish outcome is unknown",
            )
            OutboxState.QUEUED, OutboxState.SENDING ->
                error("A publish cannot finish in $state")
        }
    }

    private suspend fun recordStarted(entry: OutboxEntryEntity, attempt: Int) {
        activityDao.insert(
            event(
                entryId = entry.clientId,
                profileId = entry.profileId,
                type = ActivityType.PUBLISH_STARTED,
                severity = ActivitySeverity.INFO,
                summary = "Publishing to ${entry.topic}",
                topic = entry.topic,
                eventId = "outbox:${entry.clientId}:started:$attempt",
                timestamp = now(),
            )
        )
    }

    private suspend fun recordRetry(entry: OutboxEntryEntity, blocked: Boolean) {
        activityDao.insert(
            event(
                entryId = entry.clientId,
                profileId = entry.profileId,
                type = ActivityType.PUBLISH_RETRY,
                severity = if (blocked) ActivitySeverity.WARNING else ActivitySeverity.INFO,
                summary = when {
                    blocked && OutboxState.fromPersisted(entry.state) == OutboxState.AMBIGUOUS ->
                        "Retry blocked for ${entry.topic}: a previous attempt may have been accepted"
                    blocked -> "Retry blocked for ${entry.topic}: this failure is not retryable"
                    else -> "Retry requested for ${entry.topic}"
                },
                topic = entry.topic,
                eventId = "outbox:${entry.clientId}:retry:${entry.attemptCount}:$blocked",
                timestamp = now(),
            )
        )
    }

    private fun event(
        entryId: String,
        profileId: String,
        type: ActivityType,
        severity: ActivitySeverity,
        summary: String,
        topic: String?,
        eventId: String,
        timestamp: Long,
    ) = ActivityEventEntity(
        id = eventId,
        profileId = profileId,
        type = type.name,
        severity = severity.name,
        summary = summary,
        correlationId = entryId,
        messageId = null,
        topic = topic,
        createdAt = timestamp,
    )

    private fun terminalEvent(
        entry: OutboxEntryEntity,
        state: OutboxState,
        reason: String?,
    ): Triple<ActivityType, ActivitySeverity, String> = when (state) {
        OutboxState.ACCEPTED -> Triple(
            ActivityType.PUBLISH_ACCEPTED,
            ActivitySeverity.SUCCESS,
            "Publish to ${entry.topic} accepted by server",
        )
        OutboxState.FAILED -> Triple(
            ActivityType.PUBLISH_FAILED,
            ActivitySeverity.ERROR,
            "Publish to ${entry.topic} failed: ${reason ?: "unknown error"}",
        )
        OutboxState.AMBIGUOUS -> Triple(
            ActivityType.PUBLISH_AMBIGUOUS,
            ActivitySeverity.WARNING,
            "Publish to ${entry.topic} could not be confirmed — no automatic resend",
        )
        else -> error("Not a terminal state: $state")
    }

    private fun definitelyNotSent(cause: IOException?): Boolean =
        cause is UnknownHostException || cause is ConnectException || cause is SSLException

    private fun sanitizeReason(raw: String): String {
        val flattened = raw.replace('\r', ' ').replace('\n', ' ').trim()
        return if (flattened.length > MAX_REASON_CHARS) {
            flattened.take(MAX_REASON_CHARS - 1) + "…"
        } else {
            flattened.ifBlank { "Unknown error" }
        }
    }

    private fun OutboxEntryEntity.toPublishRequest(): PublishRequest = PublishRequest(
        topic = topic,
        message = body,
        title = title,
        priority = priority,
        tags = parseTags(tagsJson),
        clickUrl = clickUrl,
        attachUrl = attachmentUrl,
        delay = delay,
    )

    private fun parseTags(json: String?): List<String> = try {
        val array = JSONArray(json ?: "[]")
        buildList { for (i in 0 until array.length()) add(array.getString(i)) }
    } catch (_: Exception) {
        emptyList()
    }

    private fun toDomain(entity: OutboxEntryEntity): OutboxEntry = OutboxEntry(
        clientId = entity.clientId,
        profileId = entity.profileId,
        topic = entity.topic,
        title = entity.title,
        body = entity.body,
        priority = entity.priority,
        tags = parseTags(entity.tagsJson),
        clickUrl = entity.clickUrl,
        attachmentUrl = entity.attachmentUrl,
        delay = entity.delay,
        state = OutboxState.fromPersisted(entity.state),
        attemptCount = entity.attemptCount,
        retryable = entity.retryable,
        lastError = entity.lastError,
        serverMessageId = entity.serverMessageId,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt,
        acceptedAt = entity.acceptedAt,
    )

    private companion object {
        /** Longer than the 30 s call timeout, so an active send is never "stuck". */
        const val STUCK_SENDING_TIMEOUT_MS = 2L * 60L * 1_000L
        const val STUCK_REASON = "Publish was interrupted before an outcome was recorded"
        const val MAX_REASON_CHARS = 300
    }
}
