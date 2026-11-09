package com.rplab.kaminarinfty.data.repository

import com.rplab.kaminarinfty.data.local.ActivityEventEntity
import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.model.ActivityEvent
import com.rplab.kaminarinfty.model.ActivitySeverity
import com.rplab.kaminarinfty.model.ActivityType
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/**
 * Room-backed timeline. Writes are profile-scoped and bounded by
 * [com.rplab.kaminarinfty.data.local.RetentionPolicy].
 *
 * Callers must never pass message bodies, credentials or authorization headers
 * in [record]'s summary; the repository still flattens newlines and caps length
 * so a stray multi-line value cannot corrupt the timeline layout.
 */
interface ActivityRepository {
    fun observe(profileId: String, limit: Int = 200): Flow<List<ActivityEvent>>

    /**
     * Inserts one event. [eventId] makes a transition idempotent: recording the
     * same deterministic ID twice is ignored, so a double tap or a retried
     * transition cannot duplicate history. Returns true only for a new row.
     */
    suspend fun record(
        profileId: String,
        type: ActivityType,
        severity: ActivitySeverity,
        summary: String,
        correlationId: String? = null,
        messageId: String? = null,
        topic: String? = null,
        eventId: String? = null,
    ): Boolean

    suspend fun prune(profileId: String)
}

class RoomActivityRepository(
    database: KaminariDatabase,
    private val now: () -> Long = System::currentTimeMillis,
) : ActivityRepository {

    private val dao = database.activityDao()

    override fun observe(profileId: String, limit: Int): Flow<List<ActivityEvent>> {
        if (profileId.isBlank()) return flowOf(emptyList())
        return dao.observeRecent(profileId, limit.coerceAtLeast(1)).map { rows ->
            rows.mapNotNull(::toDomain)
        }
    }

    override suspend fun record(
        profileId: String,
        type: ActivityType,
        severity: ActivitySeverity,
        summary: String,
        correlationId: String?,
        messageId: String?,
        topic: String?,
        eventId: String?,
    ): Boolean {
        if (profileId.isBlank()) return false
        val timestamp = now()
        val inserted = dao.insert(
            ActivityEventEntity(
                id = eventId ?: "event-${UUID.randomUUID()}",
                profileId = profileId,
                type = type.name,
                severity = severity.name,
                summary = sanitize(summary),
                correlationId = correlationId,
                messageId = messageId,
                topic = topic,
                createdAt = timestamp,
            )
        ) != -1L
        dao.prune(profileId, timestamp)
        return inserted
    }

    override suspend fun prune(profileId: String) {
        if (profileId.isBlank()) return
        dao.prune(profileId, now())
    }

    private fun toDomain(entity: ActivityEventEntity): ActivityEvent? {
        val type = runCatching { ActivityType.valueOf(entity.type) }.getOrNull() ?: return null
        val severity = runCatching { ActivitySeverity.valueOf(entity.severity) }
            .getOrDefault(ActivitySeverity.INFO)
        return ActivityEvent(
            id = entity.id,
            profileId = entity.profileId,
            type = type,
            severity = severity,
            summary = entity.summary,
            correlationId = entity.correlationId,
            messageId = entity.messageId,
            topic = entity.topic,
            createdAt = entity.createdAt,
        )
    }

    private fun sanitize(summary: String): String {
        val flattened = summary.replace('\r', ' ').replace('\n', ' ').trim()
        val capped = if (flattened.length > MAX_SUMMARY_CHARS) {
            flattened.take(MAX_SUMMARY_CHARS - 1) + "…"
        } else {
            flattened
        }
        return capped.ifBlank { "No details" }
    }

    private companion object {
        const val MAX_SUMMARY_CHARS = 300
    }
}
