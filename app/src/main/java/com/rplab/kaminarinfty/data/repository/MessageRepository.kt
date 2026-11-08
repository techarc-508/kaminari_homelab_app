package com.rplab.kaminarinfty.data.repository

import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.data.local.MessageMapper
import com.rplab.kaminarinfty.model.NftyMessage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface MessageRepository {
    /** Bounded observable history window; the inbox renders this list lazily. */
    fun observe(profileId: String, limit: Int = 300): Flow<List<NftyMessage>>

    /** Offset page for historical synchronisation and future paging UI. */
    suspend fun page(profileId: String, limit: Int, offset: Int): List<NftyMessage>

    /** Durable dedupe on (profileId, messageId). Returns false for a replay. */
    suspend fun store(profileId: String, message: NftyMessage): Boolean

    suspend fun exists(profileId: String, messageId: String): Boolean

    /** Newest message ID, used as the ntfy `since` cursor after a reconnect. */
    suspend fun latestCursor(profileId: String): String?

    suspend fun count(profileId: String): Int

    suspend fun markRead(profileId: String, messageId: String)
    suspend fun markAllRead(profileId: String)
    suspend fun setSaved(profileId: String, messageId: String, saved: Boolean)
    suspend fun delete(profileId: String, messageId: String)

    /** Handles ntfy `message_clear`; saved items are preserved. */
    suspend fun clearTopic(profileId: String, topic: String)

    /** Removes unsaved history, preserving starred items. */
    suspend fun clearHistory(profileId: String)
    suspend fun clearSaved(profileId: String)
}

class RoomMessageRepository(database: KaminariDatabase) : MessageRepository {

    private val dao = database.messageDao()

    override fun observe(profileId: String, limit: Int): Flow<List<NftyMessage>> =
        dao.observeRecent(profileId, limit).map { rows -> rows.map(MessageMapper::toDomain) }

    override suspend fun page(profileId: String, limit: Int, offset: Int): List<NftyMessage> =
        dao.page(profileId, limit, offset).map(MessageMapper::toDomain)

    override suspend fun store(profileId: String, message: NftyMessage): Boolean =
        dao.insert(MessageMapper.toEntity(profileId, message)) != -1L

    override suspend fun exists(profileId: String, messageId: String): Boolean =
        dao.exists(profileId, messageId)

    override suspend fun latestCursor(profileId: String): String? =
        dao.latestMessageId(profileId)

    override suspend fun count(profileId: String): Int = dao.countForProfile(profileId)

    override suspend fun markRead(profileId: String, messageId: String) =
        dao.markRead(profileId, messageId)

    override suspend fun markAllRead(profileId: String) = dao.markAllRead(profileId)

    override suspend fun setSaved(profileId: String, messageId: String, saved: Boolean) =
        dao.setSaved(profileId, messageId, saved)

    override suspend fun delete(profileId: String, messageId: String) =
        dao.delete(profileId, messageId)

    override suspend fun clearTopic(profileId: String, topic: String) =
        dao.clearTopicKeepingSaved(profileId, topic)

    override suspend fun clearHistory(profileId: String) =
        dao.clearHistoryKeepingSaved(profileId)

    override suspend fun clearSaved(profileId: String) = dao.clearSaved(profileId)
}
