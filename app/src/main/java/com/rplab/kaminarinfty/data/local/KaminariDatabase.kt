package com.rplab.kaminarinfty.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        OutboxEntryEntity::class,
        ActivityEventEntity::class,
        CommandRequestEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class KaminariDatabase : RoomDatabase() {
    abstract fun serverProfileDao(): ServerProfileDao
    abstract fun topicSubscriptionDao(): TopicSubscriptionDao
    abstract fun messageDao(): MessageDao
    abstract fun outboxDao(): OutboxDao
    abstract fun activityDao(): ActivityDao
    abstract fun commandDao(): CommandDao

    companion object {
        /**
         * Additive v1→v2 migration. Existing profiles, topics and messages are
         * preserved; no destructive fallback is registered anywhere.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `outbox_entries` (" +
                        "`clientId` TEXT NOT NULL, `profileId` TEXT NOT NULL, " +
                        "`topic` TEXT NOT NULL, `title` TEXT, `body` TEXT NOT NULL, " +
                        "`priority` INTEGER NOT NULL, `tagsJson` TEXT NOT NULL, " +
                        "`clickUrl` TEXT, `attachmentUrl` TEXT, `actionsJson` TEXT, " +
                        "`delay` TEXT, `state` TEXT NOT NULL, `attemptCount` INTEGER NOT NULL, " +
                        "`retryable` INTEGER NOT NULL, `lastError` TEXT, " +
                        "`serverMessageId` TEXT, `createdAt` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, `acceptedAt` INTEGER, " +
                        "PRIMARY KEY(`clientId`), " +
                        "FOREIGN KEY(`profileId`) REFERENCES `server_profiles`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_outbox_entries_profileId_createdAt` " +
                        "ON `outbox_entries` (`profileId`, `createdAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_outbox_entries_profileId_state` " +
                        "ON `outbox_entries` (`profileId`, `state`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `activity_events` (" +
                        "`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, `type` TEXT NOT NULL, " +
                        "`severity` TEXT NOT NULL, `summary` TEXT NOT NULL, " +
                        "`correlationId` TEXT, `messageId` TEXT, `topic` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`), " +
                        "FOREIGN KEY(`profileId`) REFERENCES `server_profiles`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_activity_events_profileId_createdAt` " +
                        "ON `activity_events` (`profileId`, `createdAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_activity_events_correlationId` " +
                        "ON `activity_events` (`correlationId`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `command_requests` (" +
                        "`commandId` TEXT NOT NULL, `profileId` TEXT NOT NULL, " +
                        "`actionId` TEXT NOT NULL, `target` TEXT NOT NULL, " +
                        "`argsJson` TEXT NOT NULL, `nonce` TEXT NOT NULL, " +
                        "`issuedAt` INTEGER NOT NULL, `expiresAt` INTEGER NOT NULL, " +
                        "`requiresConfirmation` INTEGER NOT NULL, `confirmedAt` INTEGER, " +
                        "`status` TEXT NOT NULL, `resultJson` TEXT, `resultSummary` TEXT, " +
                        "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`commandId`), " +
                        "FOREIGN KEY(`profileId`) REFERENCES `server_profiles`(`id`) " +
                        "ON UPDATE NO ACTION ON DELETE CASCADE )"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_command_requests_profileId_createdAt` " +
                        "ON `command_requests` (`profileId`, `createdAt`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_command_requests_nonce` " +
                        "ON `command_requests` (`nonce`)"
                )
            }
        }

        /** Append explicit migrations here when the schema version increases. */
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)

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
