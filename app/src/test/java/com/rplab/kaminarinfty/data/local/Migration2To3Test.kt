package com.rplab.kaminarinfty.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Migrates a real v2 database built from the exported 2.json schema to v3.
 * Existing subscriptions must keep their rows and gain safe defaults; Room's
 * schema validation on open is the final assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration2To3Test {

    private val dbName = "migration-2-3-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun schemaFile(): File {
        val candidates = listOf(
            File("schemas/com.rplab.kaminarinfty.data.local.KaminariDatabase/2.json"),
            File("app/schemas/com.rplab.kaminarinfty.data.local.KaminariDatabase/2.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("v2 schema not found from ${File(".").absolutePath}")
    }

    private fun seedV2() {
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()
        val sqLite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        val database = JSONObject(schemaFile().readText()).getJSONObject("database")
        val entities = database.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            sqLite.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (i in 0 until indices.length()) {
                sqLite.execSQL(
                    indices.getJSONObject(i).getString("createSql").replace("\${TABLE_NAME}", table)
                )
            }
        }
        sqLite.execSQL(
            "INSERT INTO server_profiles (`id`, `displayName`, `baseUrl`, `authMethod`, `createdAt`, `updatedAt`) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("primary", "Primary", "https://ntfy.test", "GUEST", 1L, 1L),
        )
        sqLite.execSQL(
            "INSERT INTO topic_subscriptions (`profileId`, `topic`, `isSubscribed`, `isPaused`, `priorityThreshold`, `createdAt`) " +
                "VALUES (?, ?, ?, ?, ?, ?)",
            arrayOf("primary", "legacy-topic", 1, 0, 3, 1L),
        )
        sqLite.execSQL(
            "INSERT INTO messages (`profileId`, `messageId`, `time`, `topic`, `title`, `body`, `priority`, " +
                "`tagsJson`, `clickUrl`, `attachmentJson`, `actionsJson`, `expiry`, `isRead`, `isSaved`, `isSample`, `receivedAt`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf("primary", "m1", 1000L, "legacy-topic", "Title", "Body", 3, "[]", null, null, null, null, 0, 0, 0, 1000L),
        )
        sqLite.version = 2
        sqLite.close()
    }

    private fun openMigrated(): KaminariDatabase =
        Room.databaseBuilder(context, KaminariDatabase::class.java, dbName)
            .addMigrations(KaminariDatabase.MIGRATION_1_2, KaminariDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `v2 rows gain category and priority defaults`() = runTest {
        seedV2()
        val database = openMigrated()
        try {
            assertEquals(3, database.openHelper.writableDatabase.version)
            val subscription = database.topicSubscriptionDao().getForProfile("primary").single()
            assertEquals("legacy-topic", subscription.topic)
            assertEquals("OTHER", subscription.category)
            assertEquals(3, subscription.priority)
            assertEquals(3, subscription.priorityThreshold)
            assertTrue(subscription.isSubscribed)
            assertEquals("Body", database.messageDao().getById("primary", "m1")!!.body)
        } finally {
            database.close()
        }
    }

    @Test
    fun `new catalogue metadata is stored and survives reopen`() = runTest {
        seedV2()
        val database = openMigrated()
        try {
            database.topicSubscriptionDao().insert(
                TopicSubscriptionEntity(
                    profileId = "primary",
                    topic = "kf-music-pipeline",
                    category = "MUSIC",
                    priority = 4,
                )
            )
            val music = database.topicSubscriptionDao().getForProfile("primary")
                .single { it.topic == "kf-music-pipeline" }
            assertEquals("MUSIC", music.category)
            assertEquals(4, music.priority)
        } finally {
            database.close()
        }

        val reopened = openMigrated()
        try {
            val music = reopened.topicSubscriptionDao().getForProfile("primary")
                .single { it.topic == "kf-music-pipeline" }
            assertEquals("MUSIC", music.category)
            assertEquals(4, music.priority)
        } finally {
            reopened.close()
        }
    }
}
