package com.rplab.kaminarinfty.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OutboxDaoTest {

    private lateinit var database: KaminariDatabase
    private lateinit var dao: OutboxDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KaminariDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.outboxDao()
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

    private fun entry(
        clientId: String,
        profileId: String = "primary",
        state: String = "QUEUED",
        createdAt: Long = 1L,
        updatedAt: Long = createdAt,
        retryable: Boolean = false,
    ) = OutboxEntryEntity(
        clientId = clientId,
        profileId = profileId,
        topic = "ops",
        title = null,
        body = "hello",
        priority = 3,
        tagsJson = "[]",
        clickUrl = null,
        attachmentUrl = null,
        actionsJson = null,
        delay = null,
        state = state,
        attemptCount = 0,
        retryable = retryable,
        lastError = null,
        serverMessageId = null,
        createdAt = createdAt,
        updatedAt = updatedAt,
        acceptedAt = null,
    )

    @Test
    fun `claim is single winner and complete only works from SENDING`() = runTest {
        seedProfile()
        dao.insert(entry("c1"))

        assertEquals(1, dao.claim("primary", "c1", now = 10))
        assertEquals(0, dao.claim("primary", "c1", now = 11))
        val sending = dao.getById("primary", "c1")!!
        assertEquals("SENDING", sending.state)
        assertEquals(1, sending.attemptCount)

        assertEquals(
            1,
            dao.complete("primary", "c1", "ACCEPTED", "server-1", null, false, 12, 12),
        )
        assertEquals(0, dao.complete("primary", "c1", "FAILED", null, "late", true, null, 13))
        val accepted = dao.getById("primary", "c1")!!
        assertEquals("ACCEPTED", accepted.state)
        assertEquals("server-1", accepted.serverMessageId)
        assertEquals(12L, accepted.acceptedAt)
    }

    @Test
    fun `requeue is limited to failed and ambiguous states`() = runTest {
        seedProfile()
        dao.insert(entry("failed", state = "FAILED", retryable = true))
        dao.insert(entry("ambiguous", state = "AMBIGUOUS"))
        dao.insert(entry("accepted", state = "ACCEPTED"))

        assertEquals(1, dao.requeue("primary", "failed", now = 20))
        assertEquals(1, dao.requeue("primary", "ambiguous", now = 21))
        assertEquals(0, dao.requeue("primary", "accepted", now = 22))
        assertEquals("QUEUED", dao.getById("primary", "failed")!!.state)
        assertEquals("QUEUED", dao.getById("primary", "ambiguous")!!.state)
        assertEquals("ACCEPTED", dao.getById("primary", "accepted")!!.state)
    }

    @Test
    fun `stuck sending recovery marks only old rows ambiguous`() = runTest {
        seedProfile("primary")
        seedProfile("secondary")
        dao.insert(entry("old", state = "SENDING", updatedAt = 100))
        dao.insert(entry("recent", state = "SENDING", updatedAt = 500))
        dao.insert(entry("queued", state = "QUEUED", updatedAt = 100))
        dao.insert(entry("other", profileId = "secondary", state = "SENDING", updatedAt = 100))

        assertEquals(
            1,
            dao.recoverStuckSending("primary", cutoff = 200, now = 600, reason = "process restart"),
        )
        assertEquals("AMBIGUOUS", dao.getById("primary", "old")!!.state)
        assertEquals("SENDING", dao.getById("primary", "recent")!!.state)
        assertEquals("QUEUED", dao.getById("primary", "queued")!!.state)
        assertEquals("SENDING", dao.getById("secondary", "other")!!.state)
    }

    @Test
    fun `cancel refuses accepted rows`() = runTest {
        seedProfile()
        dao.insert(entry("queued"))
        dao.insert(entry("accepted", state = "ACCEPTED"))

        assertEquals(1, dao.deleteIfUnsent("primary", "queued"))
        assertEquals(0, dao.deleteIfUnsent("primary", "accepted"))
        assertNull(dao.getById("primary", "queued"))
        assertEquals("ACCEPTED", dao.getById("primary", "accepted")!!.state)
    }

    @Test
    fun `prune enforces the 30 day policy and keeps active work`() = runTest {
        seedProfile()
        val now = TimeUnit.DAYS.toMillis(31)
        dao.insert(entry("old-a", state = "ACCEPTED", createdAt = 1))
        dao.insert(entry("old-f", state = "FAILED", createdAt = 2))
        dao.insert(entry("old-m", state = "AMBIGUOUS", createdAt = 3))
        dao.insert(entry("queued", state = "QUEUED", createdAt = 4))
        dao.insert(entry("new-a", state = "ACCEPTED", createdAt = now - 1_000))

        assertEquals(3, dao.prune("primary", now))
        assertEquals(2, dao.count("primary"))
        assertNull(dao.getById("primary", "old-a"))
        assertTrue(dao.getById("primary", "queued") != null)
        assertTrue(dao.getById("primary", "new-a") != null)
    }

    @Test
    fun `pruneBeyond keeps newest terminal rows and never active rows`() = runTest {
        seedProfile()
        dao.insert(entry("term-old", state = "ACCEPTED", createdAt = 10))
        dao.insert(entry("term-mid", state = "FAILED", createdAt = 20))
        dao.insert(entry("term-new", state = "AMBIGUOUS", createdAt = 30))
        dao.insert(entry("queued", state = "QUEUED", createdAt = 5))
        dao.insert(entry("sending", state = "SENDING", createdAt = 6))

        assertEquals(1, dao.pruneBeyond("primary", keepMax = 2))
        assertNull(dao.getById("primary", "term-old"))
        assertTrue(dao.getById("primary", "term-mid") != null)
        assertTrue(dao.getById("primary", "term-new") != null)
        assertTrue(dao.getById("primary", "queued") != null)
        assertTrue(dao.getById("primary", "sending") != null)
    }

    @Test
    fun `id based operations reject another profile`() = runTest {
        seedProfile("primary")
        seedProfile("secondary")
        dao.insert(entry("shared", profileId = "primary"))
        dao.insert(entry("other", profileId = "secondary"))

        assertTrue(dao.getById("primary", "shared") != null)
        assertNull(dao.getById("primary", "other"))
        assertEquals(0, dao.claim("primary", "other", now = 10))
        assertEquals(
            0,
            dao.complete("primary", "other", "ACCEPTED", "server-1", null, false, 11, 11),
        )
        assertEquals(0, dao.requeue("primary", "other", now = 12))
        assertEquals(0, dao.deleteIfUnsent("primary", "other"))

        assertEquals(1, dao.claim("primary", "shared", now = 10))
        assertEquals(
            1,
            dao.complete("primary", "shared", "ACCEPTED", "server-1", null, false, 11, 11),
        )
        assertEquals("QUEUED", dao.getById("secondary", "other")!!.state)
    }

    @Test
    fun `queries are profile scoped and cascade on profile delete`() = runTest {
        seedProfile("primary")
        seedProfile("secondary")
        dao.insert(entry("a", profileId = "primary"))
        dao.insert(entry("b", profileId = "secondary"))

        assertEquals(listOf("a"), dao.observeRecent("primary", 10).first().map { it.clientId })
        assertEquals(1, dao.count("primary"))
        assertEquals(1, dao.count("secondary"))

        database.serverProfileDao().deleteById("primary")
        assertEquals(0, dao.count("primary"))
        assertEquals(1, dao.count("secondary"))
    }
}
