package com.rplab.kaminarinfty.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    /** Deterministic event IDs make retries idempotent: an existing row is ignored. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(event: ActivityEventEntity): Long

    @Query(
        "SELECT * FROM activity_events WHERE profileId = :profileId " +
            "ORDER BY createdAt DESC, id DESC LIMIT :limit"
    )
    fun observeRecent(profileId: String, limit: Int): Flow<List<ActivityEventEntity>>

    /** Low-level retention query; normal callers use [prune]. */
    @Query("DELETE FROM activity_events WHERE profileId = :profileId AND createdAt < :cutoff")
    suspend fun pruneOlderThan(profileId: String, cutoff: Long): Int

    @Query(
        "DELETE FROM activity_events WHERE profileId = :profileId AND id NOT IN (" +
            "SELECT id FROM activity_events WHERE profileId = :profileId " +
            "ORDER BY createdAt DESC, id DESC LIMIT :keepMax)"
    )
    suspend fun pruneBeyond(profileId: String, keepMax: Int): Int

    /** Applies the approved 90-day / 5,000-event policy. */
    @Transaction
    suspend fun prune(profileId: String, now: Long): Int =
        pruneOlderThan(profileId, RetentionPolicy.activityCutoff(now)) +
            pruneBeyond(profileId, RetentionPolicy.ACTIVITY_MAX_EVENTS_PER_PROFILE)

    @Query("SELECT COUNT(*) FROM activity_events WHERE profileId = :profileId")
    suspend fun count(profileId: String): Int
}
