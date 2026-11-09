package com.rplab.kaminarinfty.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "server_profiles")
data class ServerProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val baseUrl: String,
    val authMethod: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "topic_subscriptions",
    foreignKeys = [
        ForeignKey(
            entity = ServerProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index(value = ["profileId", "topic"], unique = true)],
)
data class TopicSubscriptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: String,
    val topic: String,
    val isSubscribed: Boolean = true,
    val isPaused: Boolean = false,
    val priorityThreshold: Int? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Local notification history. Durable identity is the unique
 * (profileId, messageId) pair; stream replays must never duplicate rows.
 */
@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ServerProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["profileId", "messageId"], unique = true),
        Index(value = ["profileId", "time"]),
        Index(value = ["isSaved"]),
        Index(value = ["isRead"]),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val profileId: String,
    val messageId: String,
    val time: Long,
    val topic: String,
    val title: String?,
    val body: String,
    val priority: Int,
    val tagsJson: String,
    val clickUrl: String?,
    val attachmentJson: String?,
    val actionsJson: String?,
    val expiry: Long?,
    val isRead: Boolean = false,
    val isSaved: Boolean = false,
    val isSample: Boolean = false,
    val receivedAt: Long = System.currentTimeMillis(),
)

/**
 * Durable publish intent. [state] is one of [com.rplab.kaminarinfty.model.OutboxState].
 * Every transition is guarded by the expected previous state in [OutboxDao].
 */
@Entity(
    tableName = "outbox_entries",
    foreignKeys = [
        ForeignKey(
            entity = ServerProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["profileId", "createdAt"]),
        Index(value = ["profileId", "state"]),
    ],
)
data class OutboxEntryEntity(
    @PrimaryKey val clientId: String,
    val profileId: String,
    val topic: String,
    val title: String?,
    val body: String,
    val priority: Int,
    val tagsJson: String,
    val clickUrl: String?,
    val attachmentUrl: String?,
    val actionsJson: String?,
    val delay: String?,
    val state: String = "QUEUED",
    val attemptCount: Int = 0,
    val retryable: Boolean = false,
    val lastError: String? = null,
    val serverMessageId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val acceptedAt: Long? = null,
)

/**
 * Bounded, sanitized activity history. Never stores message bodies or secrets.
 */
@Entity(
    tableName = "activity_events",
    foreignKeys = [
        ForeignKey(
            entity = ServerProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["profileId", "createdAt"]),
        Index(value = ["correlationId"]),
    ],
)
data class ActivityEventEntity(
    @PrimaryKey val id: String,
    val profileId: String,
    val type: String,
    val severity: String,
    val summary: String,
    val correlationId: String? = null,
    val messageId: String? = null,
    val topic: String? = null,
    val createdAt: Long,
)

/**
 * Typed command request. [nonce] is unique for replay protection. [status] is
 * one of [com.rplab.kaminarinfty.model.CommandStatus].
 */
@Entity(
    tableName = "command_requests",
    foreignKeys = [
        ForeignKey(
            entity = ServerProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [
        Index(value = ["profileId", "createdAt"]),
        Index(value = ["nonce"], unique = true),
    ],
)
data class CommandRequestEntity(
    @PrimaryKey val commandId: String,
    val profileId: String,
    val actionId: String,
    val target: String,
    val argsJson: String,
    val nonce: String,
    val issuedAt: Long,
    val expiresAt: Long,
    val requiresConfirmation: Boolean,
    val confirmedAt: Long? = null,
    val status: String = "SUBMITTED",
    val resultJson: String? = null,
    val resultSummary: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)
