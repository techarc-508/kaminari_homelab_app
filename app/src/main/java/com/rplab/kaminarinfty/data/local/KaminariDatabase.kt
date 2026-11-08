package com.rplab.kaminarinfty.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration

/**
 * Room is the single source of truth for notification history, topic
 * subscriptions and server profiles.
 *
 * v1 is the initial schema, so there is nothing to migrate yet. Destructive
 * migrations are intentionally NOT enabled: a future schema change must append
 * an explicit [MIGRATION] here (see AGENTS.md section 5).
 */
@Database(
    entities = [
        ServerProfileEntity::class,
        TopicSubscriptionEntity::class,
        MessageEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class KaminariDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun topicSubscriptionDao(): TopicSubscriptionDao
    abstract fun messageDao(): MessageDao

    companion object {
        /** Append explicit migrations here when the schema version increases. */
        val MIGRATIONS: Array<Migration> = emptyArray()

        fun build(context: Context): KaminariDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                KaminariDatabase::class.java,
                "kaminari.db",
            )
                .addMigrations(*MIGRATIONS)
                .build()
    }
}
