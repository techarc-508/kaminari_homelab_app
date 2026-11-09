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

    @Query("SELECT * FROM command_requests WHERE commandId = :commandId LIMIT 1")
    suspend fun getById(commandId: String): CommandRequestEntity?

    @Query("SELECT * FROM command_requests WHERE nonce = :nonce LIMIT 1")
    suspend fun findByNonce(nonce: String): CommandRequestEntity?

    /** Conditional transition; returns 0 when the row is no longer in [expectedStatus]. */
    @Query(
        "UPDATE command_requests SET status = :status, resultJson = :resultJson, " +
            "resultSummary = :resultSummary, updatedAt = :now " +
            "WHERE commandId = :commandId AND status = :expectedStatus"
    )
    suspend fun markStatus(
        commandId: String,
        expectedStatus: String,
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

    @Query(
        "DELETE FROM command_requests WHERE profileId = :profileId " +
            "AND status IN ('COMPLETED', 'FAILED', 'EXPIRED', 'REJECTED') " +
            "AND commandId NOT IN (" +
            "SELECT commandId FROM command_requests WHERE profileId = :profileId " +
            "ORDER BY createdAt DESC, commandId DESC LIMIT :keepMax)"
    )
    suspend fun pruneBeyond(profileId: String, keepMax: Int): Int

    @Transaction
    suspend fun prune(profileId: String, cutoff: Long, keepMax: Int): Int =
        pruneOlderThan(profileId, cutoff) + pruneBeyond(profileId, keepMax)

    @Query("SELECT COUNT(*) FROM command_requests WHERE profileId = :profileId")
    suspend fun count(profileId: String): Int
}
