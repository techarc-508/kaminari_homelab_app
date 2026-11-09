package com.rplab.kaminarinfty.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CommandDao {
    @Insert
    suspend fun insert(command: CommandRequestEntity)

    @Query(
        "SELECT * FROM command_requests WHERE profileId = :profileId " +
            "ORDER BY createdAt DESC, commandId DESC LIMIT :limit"
    )
    fun observeRecent(profileId: String, limit: Int): Flow<List<CommandRequestEntity>>

    /** Profile-scoped lookup: a caller cannot read another profile's command by ID. */
    @Query(
        "SELECT * FROM command_requests WHERE profileId = :profileId AND commandId = :commandId LIMIT 1"
    )
    suspend fun getById(profileId: String, commandId: String): CommandRequestEntity?

    @Query(
        "SELECT * FROM command_requests WHERE profileId = :profileId AND nonce = :nonce LIMIT 1"
    )
    suspend fun findByNonce(profileId: String, nonce: String): CommandRequestEntity?

    /**
     * Only SUBMITTED -> terminal is legal, and only before expiry. A stale,
     * expired, or already-terminal row returns 0 and keeps its result.
     */
    @Query(
        "UPDATE command_requests SET status = :status, resultJson = :resultJson, " +
            "resultSummary = :resultSummary, updatedAt = :now " +
            "WHERE profileId = :profileId AND commandId = :commandId " +
            "AND status = 'SUBMITTED' AND expiresAt > :now " +
            "AND :status IN ('COMPLETED', 'FAILED', 'EXPIRED', 'REJECTED')"
    )
    suspend fun markStatus(
        profileId: String,
        commandId: String,
        status: String,
        resultJson: String?,
        resultSummary: String?,
        now: Long,
    ): Int

    @Query(
        "UPDATE command_requests SET status = 'EXPIRED', updatedAt = :now " +
            "WHERE profileId = :profileId AND status = 'SUBMITTED' AND expiresAt < :now"
    )
    suspend fun expireOverdue(profileId: String, now: Long): Int

    @Query(
        "DELETE FROM command_requests WHERE profileId = :profileId " +
            "AND status IN ('COMPLETED', 'FAILED', 'EXPIRED', 'REJECTED') AND updatedAt < :cutoff"
    )
    suspend fun pruneOlderThan(profileId: String, cutoff: Long): Int

    /** Keeps the newest terminal rows only; SUBMITTED rows are never eligible. */
    @Query(
        "DELETE FROM command_requests WHERE profileId = :profileId " +
            "AND status IN ('COMPLETED', 'FAILED', 'EXPIRED', 'REJECTED') " +
            "AND commandId NOT IN (" +
            "SELECT commandId FROM command_requests WHERE profileId = :profileId " +
            "AND status IN ('COMPLETED', 'FAILED', 'EXPIRED', 'REJECTED') " +
            "ORDER BY createdAt DESC, commandId DESC LIMIT :keepMax)"
    )
    suspend fun pruneBeyond(profileId: String, keepMax: Int): Int

    @Transaction
    suspend fun prune(profileId: String, cutoff: Long, keepMax: Int): Int =
        pruneOlderThan(profileId, cutoff) + pruneBeyond(profileId, keepMax)

    @Query("SELECT COUNT(*) FROM command_requests WHERE profileId = :profileId")
    suspend fun count(profileId: String): Int
}
