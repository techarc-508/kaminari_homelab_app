package com.rplab.kaminarinfty.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Insert
    suspend fun insert(event: ActivityEventEntity)

    @Query(
        "SELECT * FROM activity_events WHERE profileId = :profileId " +
            "ORDER BY createdAt DESC, id DESC LIMIT :limit"
    )
    fun observeRecent(profileId: String, limit: Int): Flow<List<ActivityEventEntity>>

    @Query("DELETE FROM activity_events WHERE profileId = :profileId AND createdAt < :cutoff")
    suspend fun pruneOlderThan(profileId: String, cutoff: Long): Int

    @Query(
        "DELETE FROM activity_events WHERE profileId = :profileId AND id NOT IN (" +
            "SELECT id FROM activity_events WHERE profileId = :profileId " +
            "ORDER BY createdAt DESC, id DESC LIMIT :keepMax)"
    )
    suspend fun pruneBeyond(profileId: String, keepMax: Int): Int

    @Transaction
    suspend fun prune(profileId: String, cutoff: Long, keepMax: Int): Int =
        pruneOlderThan(profileId, cutoff) + pruneBeyond(profileId, keepMax)

    @Query("SELECT COUNT(*) FROM activity_events WHERE profileId = :profileId")
    suspend fun count(profileId: String): Int
}
