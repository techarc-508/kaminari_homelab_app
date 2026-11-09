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

        assertEquals(1, dao.claim("c1", now = 10))
        assertEquals(0, dao.claim("c1", now = 11))
        val sending = dao.getById("c1")!!
        assertEquals("SENDING", sending.state)
        assertEquals(1, sending.attemptCount)

        assertEquals(
            1,
            dao.complete("c1", "ACCEPTED", "server-1", null, false, 12, 12),
        )
        assertEquals(0, dao.complete("c1", "FAILED", null, "late", true, null, 13))
        val accepted = dao.getById("c1")!!
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

        assertEquals(1, dao.requeue("failed", now = 20))
        assertEquals(1, dao.requeue("ambiguous", now = 21))
        assertEquals(0, dao.requeue("accepted", now = 22))
        assertEquals("QUEUED", dao.getById("failed")!!.state)
        assertEquals("QUEUED", dao.getById("ambiguous")!!.state)
        assertEquals("ACCEPTED", dao.getById("accepted")!!.state)
    }

    @Test
    fun `stuck sending recovery marks only old rows ambiguous`() = runTest {
        seedProfile()
        dao.insert(entry("old", state = "SENDING", updatedAt = 100))
        dao.insert(entry("recent", state = "SENDING", updatedAt = 500))
        dao.insert(entry("queued", state = "QUEUED", updatedAt = 100))

        assertEquals(1, dao.recoverStuckSending(cutoff = 200, now = 600, reason = "process restart"))
        assertEquals("AMBIGUOUS", dao.getById("old")!!.state)
        assertEquals("SENDING", dao.getById("recent")!!.state)
        assertEquals("QUEUED", dao.getById("queued")!!.state)
    }

    @Test
    fun `cancel refuses accepted rows`() = runTest {
        seedProfile()
        dao.insert(entry("queued"))
        dao.insert(entry("accepted", state = "ACCEPTED"))

        assertEquals(1, dao.deleteIfUnsent("queued"))
        assertEquals(0, dao.deleteIfUnsent("accepted"))
        assertNull(dao.getById("queued"))
        assertEquals("ACCEPTED", dao.getById("accepted")!!.state)
    }

    @Test
    fun `prune removes old terminal rows but keeps queued and newest`() = runTest {
        seedProfile()
        dao.insert(entry("a10", state = "ACCEPTED", createdAt = 10))
        dao.insert(entry("f20", state = "FAILED", createdAt = 20))
        dao.insert(entry("m30", state = "AMBIGUOUS", createdAt = 30))
        dao.insert(entry("q15", state = "QUEUED", createdAt = 15))
        dao.insert(entry("a100", state = "ACCEPTED", createdAt = 100))

        assertEquals(3, dao.prune("primary", cutoff = 50, keepMax = 1))
        assertEquals(2, dao.count("primary"))
        assertTrue(dao.getById("q15") != null)
        assertTrue(dao.getById("a100") != null)
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
