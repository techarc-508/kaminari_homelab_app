package com.rplab.kaminarinfty.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MessageDaoTest {

    private lateinit var database: KaminariDatabase
    private lateinit var dao: MessageDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KaminariDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.messageDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedProfile(id: String = "primary") {
        database.serverProfileDao().upsert(
            ServerProfileEntity(
                id = id,
                displayName = "test",
                baseUrl = "https://ntfy.test",
                authMethod = "GUEST",
                createdAt = 0,
                updatedAt = 0,
            )
        )
    }

    private fun message(messageId: String, time: Long) = MessageEntity(
        profileId = "primary",
        messageId = messageId,
        time = time,
        topic = "ops",
        title = null,
        body = "body $messageId",
        priority = 3,
        tagsJson = "[]",
        clickUrl = null,
        attachmentJson = null,
        actionsJson = null,
        expiry = null,
    )

    @Test
    fun `duplicate profileId messageId pair is rejected`() = runTest {
        seedProfile()
        assertTrue(dao.insert(message("m1", 1000)) > 0)
        assertEquals(-1L, dao.insert(message("m1", 1000)))
        assertEquals(1, dao.countForProfile("primary"))
    }

    @Test
    fun `same messageId on another profile is stored separately`() = runTest {
        seedProfile("primary")
        seedProfile("secondary")
        dao.insert(message("m1", 1000))
        val other = message("m1", 1000).copy(profileId = "secondary")
        assertTrue(dao.insert(other) > 0)
        assertEquals(1, dao.countForProfile("secondary"))
    }

    @Test
    fun `observeRecent orders newest first and applies limit`() = runTest {
        seedProfile()
        dao.insert(message("m1", 1000))
        dao.insert(message("m2", 3000))
        dao.insert(message("m3", 2000))

        val rows = dao.observeRecent("primary", limit = 2).first()

        assertEquals(listOf("m2", "m3"), rows.map { it.messageId })
    }

    @Test
    fun `markRead targets a single row`() = runTest {
        seedProfile()
        dao.insert(message("m1", 1000))
        dao.insert(message("m2", 2000))

        dao.markRead("primary", "m1")

        val rows = dao.observeRecent("primary", limit = 10).first()
        assertEquals(true, rows.first { it.messageId == "m1" }.isRead)
        assertEquals(false, rows.first { it.messageId == "m2" }.isRead)
    }

    @Test
    fun `markAllRead marks every row for the profile`() = runTest {
        seedProfile()
        dao.insert(message("m1", 1000))
        dao.insert(message("m2", 2000))

        dao.markAllRead("primary")

        assertTrue(dao.observeRecent("primary", limit = 10).first().all { it.isRead })
    }

    @Test
    fun `clearHistoryKeepingSaved preserves starred items`() = runTest {
        seedProfile()
        dao.insert(message("m1", 1000))
        dao.insert(message("m2", 2000))
        dao.insert(message("m3", 3000))
        dao.setSaved("primary", "m2", true)

        dao.clearHistoryKeepingSaved("primary")

        val rows = dao.observeRecent("primary", limit = 10).first()
        assertEquals(listOf("m2"), rows.map { it.messageId })
    }

    @Test
    fun `clearSaved unsets every saved flag`() = runTest {
        seedProfile()
        dao.insert(message("m1", 1000))
        dao.setSaved("primary", "m1", true)

        dao.clearSaved("primary")

        assertEquals(false, dao.observeRecent("primary", limit = 10).first().first().isSaved)
    }

    @Test
    fun `delete removes only the requested message`() = runTest {
        seedProfile()
        dao.insert(message("m1", 1000))
        dao.insert(message("m2", 2000))

        dao.delete("primary", "m1")

        assertEquals(listOf("m2"), dao.observeRecent("primary", limit = 10).first().map { it.messageId })
    }

    @Test
    fun `page applies limit and offset over newest first ordering`() = runTest {
        seedProfile()
        dao.insert(message("m1", 1000))
        dao.insert(message("m2", 2000))
        dao.insert(message("m3", 3000))

        assertEquals(listOf("m3", "m2"), dao.page("primary", limit = 2, offset = 0).map { it.messageId })
        assertEquals(listOf("m1"), dao.page("primary", limit = 2, offset = 2).map { it.messageId })
    }

    @Test
    fun `latestMessageId returns the newest durable cursor`() = runTest {
        seedProfile()
        assertEquals(null, dao.latestMessageId("primary"))

        dao.insert(message("m1", 1000))
        dao.insert(message("m2", 3000))

        assertEquals("m2", dao.latestMessageId("primary"))
    }

    @Test
    fun `exists reflects the unique profile and message id pair`() = runTest {
        seedProfile()
        dao.insert(message("m1", 1000))

        assertTrue(dao.exists("primary", "m1"))
        assertEquals(false, dao.exists("primary", "m2"))
    }

    @Test
    fun `clearTopicKeepingSaved only deletes unsaved rows in the topic`() = runTest {
        seedProfile()
        dao.insert(message("m1", 1000).copy(topic = "ops"))
        dao.insert(message("m2", 2000).copy(topic = "ops"))
        dao.insert(message("m3", 3000).copy(topic = "media"))
        dao.setSaved("primary", "m2", true)

        dao.clearTopicKeepingSaved("primary", "ops")

        assertEquals(
            listOf("m3", "m2"),
            dao.observeRecent("primary", limit = 10).first().map { it.messageId },
        )
    }
}
