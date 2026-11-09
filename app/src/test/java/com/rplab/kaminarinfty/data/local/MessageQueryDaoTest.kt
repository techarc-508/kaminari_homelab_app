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
class MessageQueryDaoTest {

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

    private fun message(
        messageId: String,
        profileId: String = "primary",
        time: Long = 1,
        topic: String = "ops",
        title: String? = null,
        body: String = "body",
        read: Boolean = false,
        saved: Boolean = false,
    ) = MessageEntity(
        profileId = profileId,
        messageId = messageId,
        time = time,
        topic = topic,
        title = title,
        body = body,
        priority = 3,
        tagsJson = "[]",
        clickUrl = null,
        attachmentJson = null,
        actionsJson = null,
        expiry = null,
        isRead = read,
        isSaved = saved,
    )

    @Test
    fun `search matches title body and topic within one profile`() = runTest {
        seedProfile("primary")
        seedProfile("secondary")
        dao.insert(message("title", time = 10, title = "Backup failed", body = "disk full"))
        dao.insert(message("body", time = 20, body = "backup completed"))
        dao.insert(message("topic", time = 30, topic = "backup", body = "other"))
        dao.insert(message("read", time = 40, body = "backup elsewhere", read = true))
        dao.insert(message("other", profileId = "secondary", time = 50, body = "backup"))

        val results = dao.search("primary", "backup", limit = 10, offset = 0)
        assertEquals(listOf("read", "topic", "body", "title"), results.map { it.messageId })
        assertTrue(dao.search("secondary", "backup", 10, 0).map { it.messageId } == listOf("other"))
    }

    @Test
    fun `search offset is stable and bounded`() = runTest {
        seedProfile()
        repeat(5) { index ->
            dao.insert(message("m$index", time = index.toLong(), body = "match $index"))
        }

        assertEquals(
            listOf("m3", "m2"),
            dao.search("primary", "match", limit = 2, offset = 1).map { it.messageId },
        )
    }

    @Test
    fun `saved flow returns only saved messages for the profile`() = runTest {
        seedProfile("primary")
        seedProfile("secondary")
        dao.insert(message("saved", time = 10, saved = true))
        dao.insert(message("plain", time = 20))
        dao.insert(message("other", profileId = "secondary", time = 30, saved = true))

        assertEquals(listOf("saved"), dao.observeSaved("primary", 10).first().map { it.messageId })
    }

    @Test
    fun `unread counts are grouped by topic and ignore read rows`() = runTest {
        seedProfile()
        dao.insert(message("a", time = 1, topic = "ops"))
        dao.insert(message("b", time = 2, topic = "ops"))
        dao.insert(message("c", time = 3, topic = "alerts"))
        dao.insert(message("d", time = 4, topic = "alerts", read = true))
        dao.insert(message("e", time = 5, topic = "saved", saved = true, read = true))

        val counts = dao.observeUnreadCounts("primary").first().associate { it.topic to it.count }
        assertEquals(mapOf("ops" to 2, "alerts" to 1), counts)
    }
}
