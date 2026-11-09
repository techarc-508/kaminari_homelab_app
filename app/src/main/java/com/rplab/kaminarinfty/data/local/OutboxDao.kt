package com.rplab.kaminarinfty.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert
    suspend fun insert(entry: OutboxEntryEntity)

    @Query(
        "SELECT * FROM outbox_entries WHERE profileId = :profileId " +
            "ORDER BY createdAt DESC, clientId DESC LIMIT :limit"
    )
    fun observeRecent(profileId: String, limit: Int): Flow<List<OutboxEntryEntity>>

    /** Profile-scoped lookup: a caller cannot read another profile's row by ID. */
    @Query(
        "SELECT * FROM outbox_entries WHERE profileId = :profileId AND clientId = :clientId LIMIT 1"
    )
    suspend fun getById(profileId: String, clientId: String): OutboxEntryEntity?

    /** Claims a QUEUED row exactly once. Returns 0 when another sender won. */
    @Query(
        "UPDATE outbox_entries SET state = 'SENDING', attemptCount = attemptCount + 1, " +
            "updatedAt = :now WHERE profileId = :profileId AND clientId = :clientId " +
            "AND state = 'QUEUED'"
    )
    suspend fun claim(profileId: String, clientId: String, now: Long): Int

    /** Completes a claimed row. Returns 0 when it is no longer SENDING. */
    @Query(
        "UPDATE outbox_entries SET state = :state, serverMessageId = :serverMessageId, " +
            "lastError = :lastError, retryable = :retryable, acceptedAt = :acceptedAt, " +
            "updatedAt = :now WHERE profileId = :profileId AND clientId = :clientId " +
            "AND state = 'SENDING'"
    )
    suspend fun complete(
        profileId: String,
        clientId: String,
        state: String,
        serverMessageId: String?,
        lastError: String?,
        retryable: Boolean,
        acceptedAt: Long?,
        now: Long,
    ): Int

    /** Requeues a terminal row for an explicit user retry. */
    @Query(
        "UPDATE outbox_entries SET state = 'QUEUED', updatedAt = :now " +
            "WHERE profileId = :profileId AND clientId = :clientId " +
            "AND state IN ('FAILED', 'AMBIGUOUS')"
    )
    suspend fun requeue(profileId: String, clientId: String, now: Long): Int

    /** Process restart: a SENDING row older than the cutoff is uncertain, not safe to resend. */
    @Query(
        "UPDATE outbox_entries SET state = 'AMBIGUOUS', lastError = :reason, updatedAt = :now " +
            "WHERE profileId = :profileId AND state = 'SENDING' AND updatedAt < :cutoff"
    )
    suspend fun recoverStuckSending(
        profileId: String,
        cutoff: Long,
        now: Long,
        reason: String,
    ): Int

    @Query(
        "DELETE FROM outbox_entries WHERE profileId = :profileId AND clientId = :clientId " +
            "AND state IN ('QUEUED', 'FAILED', 'AMBIGUOUS')"
    )
    suspend fun deleteIfUnsent(profileId: String, clientId: String): Int

    /** Low-level retention query; normal callers use [prune]. */
    @Query(
        "DELETE FROM outbox_entries WHERE profileId = :profileId " +
            "AND state IN ('ACCEPTED', 'FAILED', 'AMBIGUOUS') AND updatedAt < :cutoff"
    )
    suspend fun pruneOlderThan(profileId: String, cutoff: Long): Int

    /** Keeps the newest terminal rows only; QUEUED and SENDING rows are never eligible. */
    @Query(
        "DELETE FROM outbox_entries WHERE profileId = :profileId " +
            "AND state IN ('ACCEPTED', 'FAILED', 'AMBIGUOUS') AND clientId NOT IN (" +
            "SELECT clientId FROM outbox_entries WHERE profileId = :profileId " +
            "AND state IN ('ACCEPTED', 'FAILED', 'AMBIGUOUS') " +
            "ORDER BY createdAt DESC, clientId DESC LIMIT :keepMax)"
    )
    suspend fun pruneBeyond(profileId: String, keepMax: Int): Int

    /** Applies the approved 30-day / 500-terminal-row policy. */
    @Transaction
    suspend fun prune(profileId: String, now: Long): Int =
        pruneOlderThan(profileId, RetentionPolicy.outboxCutoff(now)) +
            pruneBeyond(profileId, RetentionPolicy.OUTBOX_MAX_TERMINAL_ENTRIES_PER_PROFILE)

    @Query("SELECT COUNT(*) FROM outbox_entries WHERE profileId = :profileId")
    suspend fun count(profileId: String): Int
}
