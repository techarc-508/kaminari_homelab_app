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
