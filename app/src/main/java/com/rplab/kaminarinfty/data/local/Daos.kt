package com.rplab.kaminarinfty.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ServerProfileDao {
    @Query("SELECT * FROM server_profiles ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ServerProfileEntity>>

    @Query("SELECT * FROM server_profiles WHERE id = :id")
    suspend fun getById(id: String): ServerProfileEntity?

    @Upsert
    suspend fun upsert(profile: ServerProfileEntity)

    @Query("DELETE FROM server_profiles WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TopicSubscriptionDao {
    @Query("SELECT * FROM topic_subscriptions WHERE profileId = :profileId ORDER BY topic ASC")
    fun observeForProfile(profileId: String): Flow<List<TopicSubscriptionEntity>>

    @Query("SELECT * FROM topic_subscriptions WHERE profileId = :profileId ORDER BY topic ASC")
    suspend fun getForProfile(profileId: String): List<TopicSubscriptionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TopicSubscriptionEntity): Long

    @Query("UPDATE topic_subscriptions SET isSubscribed = :subscribed WHERE profileId = :profileId AND topic = :topic")
    suspend fun setSubscribed(profileId: String, topic: String, subscribed: Boolean)

    @Query("UPDATE topic_subscriptions SET isPaused = :paused WHERE profileId = :profileId AND topic = :topic")
    suspend fun setPaused(profileId: String, topic: String, paused: Boolean)

    @Query("DELETE FROM topic_subscriptions WHERE profileId = :profileId AND topic = :topic")
    suspend fun delete(profileId: String, topic: String)
}

@Dao
interface MessageDao {
    /** Returns the new rowId, or -1 when the (profileId, messageId) pair already exists. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Query(
        "SELECT * FROM messages WHERE profileId = :profileId " +
            "ORDER BY time DESC, localId DESC LIMIT :limit"
    )
    fun observeRecent(profileId: String, limit: Int): Flow<List<MessageEntity>>

    /** Offset paging over a bounded window; localId breaks timestamp ties. */
    @Query(
        "SELECT * FROM messages WHERE profileId = :profileId " +
            "ORDER BY time DESC, localId DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun page(profileId: String, limit: Int, offset: Int): List<MessageEntity>

    /** Newest durable cursor; used as `since=<message id>` on reconnect. */
    @Query(
        "SELECT messageId FROM messages WHERE profileId = :profileId " +
            "ORDER BY time DESC, localId DESC LIMIT 1"
    )
    suspend fun latestMessageId(profileId: String): String?

    @Query(
        "SELECT EXISTS(SELECT 1 FROM messages WHERE profileId = :profileId AND messageId = :messageId)"
    )
    suspend fun exists(profileId: String, messageId: String): Boolean

    @Query("SELECT COUNT(*) FROM messages WHERE profileId = :profileId")
    suspend fun countForProfile(profileId: String): Int

    @Query("UPDATE messages SET isRead = 1 WHERE profileId = :profileId AND messageId = :messageId")
    suspend fun markRead(profileId: String, messageId: String)

    @Query("UPDATE messages SET isRead = 1 WHERE profileId = :profileId")
    suspend fun markAllRead(profileId: String)

    @Query("UPDATE messages SET isSaved = :saved WHERE profileId = :profileId AND messageId = :messageId")
    suspend fun setSaved(profileId: String, messageId: String, saved: Boolean)

    @Query("UPDATE messages SET isSaved = 0 WHERE profileId = :profileId")
    suspend fun clearSaved(profileId: String)

    @Query("DELETE FROM messages WHERE profileId = :profileId AND messageId = :messageId")
    suspend fun delete(profileId: String, messageId: String)

    /** Handles ntfy `message_clear`: local starred items are user data and survive. */
    @Query("DELETE FROM messages WHERE profileId = :profileId AND topic = :topic AND isSaved = 0")
    suspend fun clearTopicKeepingSaved(profileId: String, topic: String)

    @Query("DELETE FROM messages WHERE profileId = :profileId AND isSaved = 0")
    suspend fun clearHistoryKeepingSaved(profileId: String)
}
