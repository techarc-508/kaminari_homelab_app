package com.rplab.kaminarinfty.data.local

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Migrates a real v1 database built from the exported 1.json schema, then opens
 * it with Room at v2. Room's own schema validation is the final assertion.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Migration1To2Test {

    private val dbName = "migration-1-2-test.db"
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
            File("schemas/com.rplab.kaminarinfty.data.local.KaminariDatabase/1.json"),
            File("app/schemas/com.rplab.kaminarinfty.data.local.KaminariDatabase/1.json"),
        )
        return candidates.firstOrNull { it.exists() }
            ?: error("v1 schema not found from ${File(".").absolutePath}")
    }

    /** Builds the v1 database from the exported schema, not from hand-written DDL. */
    private fun seedV1() {
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
                val createSql = indices.getJSONObject(i).getString("createSql")
                sqLite.execSQL(createSql.replace("\${TABLE_NAME}", table))
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
            arrayOf("primary", "ops", 1, 0, null, 1L),
        )
        sqLite.execSQL(
            "INSERT INTO messages (`profileId`, `messageId`, `time`, `topic`, `title`, `body`, `priority`, " +
                "`tagsJson`, `clickUrl`, `attachmentJson`, `actionsJson`, `expiry`, `isRead`, `isSaved`, `isSample`, `receivedAt`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            arrayOf("primary", "m1", 1000L, "ops", "Title", "Body", 3, "[]", null, null, null, null, 0, 1, 0, 1000L),
        )
        sqLite.version = 1
        sqLite.close()
    }

    private fun openMigrated(): KaminariDatabase =
        Room.databaseBuilder(context, KaminariDatabase::class.java, dbName)
            .addMigrations(KaminariDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()

    @Test
    fun `migration preserves v1 rows and creates v2 tables`() = runTest {
        seedV1()
        val database = openMigrated()
        try {
            assertEquals(2, database.openHelper.writableDatabase.version)
            assertEquals("Primary", database.serverProfileDao().getById("primary")!!.displayName)
            val subscription = database.topicSubscriptionDao().getForProfile("primary").single()
            assertEquals("ops", subscription.topic)
            assertTrue(subscription.isSubscribed)
            assertFalse(subscription.isPaused)
            assertNull(subscription.priorityThreshold)
            val message = database.messageDao().getById("primary", "m1")!!
            assertEquals("Body", message.body)
            assertFalse(message.isRead)
            assertTrue(message.isSaved)
            assertEquals(0, database.outboxDao().count("primary"))
            assertEquals(0, database.activityDao().count("primary"))
            assertEquals(0, database.commandDao().count("primary"))
        } finally {
            database.close()
        }
    }

    @Test
    fun `new tables accept rows and cascade with the profile`() = runTest {
        seedV1()
        val database = openMigrated()
        try {
            database.outboxDao().insert(
                OutboxEntryEntity(
                    clientId = "c1",
                    profileId = "primary",
                    topic = "ops",
                    title = null,
                    body = "hello",
                    priority = 3,
                    tagsJson = "[]",
                    clickUrl = null,
                    attachmentUrl = "https://example.test/a.log",
                    actionsJson = null,
                    delay = null,
                    state = "QUEUED",
                    attemptCount = 0,
                    retryable = false,
                    lastError = null,
                    serverMessageId = null,
                    createdAt = 1,
                    updatedAt = 1,
                    acceptedAt = null,
                )
            )
            database.activityDao().insert(
                ActivityEventEntity(
                    id = "a1",
                    profileId = "primary",
                    type = "MESSAGE_RECEIVED",
                    severity = "INFO",
                    summary = "Message received in ops",
                    correlationId = null,
                    messageId = "m1",
                    topic = "ops",
                    createdAt = 1,
                )
            )
            database.commandDao().insert(
                CommandRequestEntity(
                    commandId = "cmd1",
                    profileId = "primary",
                    actionId = "host.health",
                    target = "mock-pve",
                    argsJson = "{}",
                    nonce = "nonce-1",
                    issuedAt = 1,
                    expiresAt = 2,
                    requiresConfirmation = false,
                    confirmedAt = null,
                    status = "SUBMITTED",
                    resultJson = null,
                    resultSummary = null,
                    createdAt = 1,
                    updatedAt = 1,
                )
            )
            assertEquals(1, database.outboxDao().count("primary"))
            assertEquals(1, database.activityDao().count("primary"))
            assertEquals(1, database.commandDao().count("primary"))

            database.serverProfileDao().deleteById("primary")
            assertEquals(0, database.messageDao().countForProfile("primary"))
            assertEquals(0, database.outboxDao().count("primary"))
            assertEquals(0, database.activityDao().count("primary"))
            assertEquals(0, database.commandDao().count("primary"))
        } finally {
            database.close()
        }
    }
}
