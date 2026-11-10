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
class CommandDaoTest {

    private lateinit var database: KaminariDatabase
    private lateinit var dao: CommandDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KaminariDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.commandDao()
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

    private fun command(
        commandId: String,
        nonce: String = "nonce-$commandId",
        profileId: String = "primary",
        status: String = "SUBMITTED",
        expiresAt: Long = 100,
        createdAt: Long = 1,
        updatedAt: Long = 1,
    ) = CommandRequestEntity(
        commandId = commandId,
        profileId = profileId,
        actionId = "host.health",
        target = "mock-pve",
        argsJson = "{}",
        nonce = nonce,
        issuedAt = 1,
        expiresAt = expiresAt,
        requiresConfirmation = false,
        confirmedAt = null,
        status = status,
        resultJson = null,
        resultSummary = null,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    @Test
    fun `nonce is unique per database`() = runTest {
        seedProfile()
        dao.insert(command("c1", nonce = "same"))
        val duplicate = runCatching { dao.insert(command("c2", nonce = "same")) }
        assertTrue(duplicate.isFailure)
        assertEquals(1, dao.count("primary"))
    }

    @Test
    fun `valid submitted transition completes before expiry`() = runTest {
        seedProfile()
        dao.insert(command("c1", expiresAt = 500))

        assertEquals(
            1,
            dao.markStatus("primary", "c1", "COMPLETED", "{\"ok\":true}", "OK", 10),
        )
        val row = dao.getById("primary", "c1")!!
        assertEquals("COMPLETED", row.status)
        assertEquals("OK", row.resultSummary)
    }

    @Test
    fun `terminal state cannot be overwritten by terminal or nonterminal targets`() = runTest {
        seedProfile()
        dao.insert(command("done", status = "COMPLETED", expiresAt = 500))

        assertEquals(0, dao.markStatus("primary", "done", "FAILED", null, "late", 10))
        assertEquals(0, dao.markStatus("primary", "done", "SUBMITTED", null, null, 10))
        assertEquals(0, dao.markStatus("primary", "done", "SENDING", null, null, 10))

        val row = dao.getById("primary", "done")!!
        assertEquals("COMPLETED", row.status)
        assertNull(row.resultSummary)
    }

    @Test
    fun `expired submitted transition is rejected`() = runTest {
        seedProfile()
        dao.insert(command("expired", expiresAt = 50))

        assertEquals(0, dao.markStatus("primary", "expired", "COMPLETED", null, "late", 100))
        assertEquals("SUBMITTED", dao.getById("primary", "expired")!!.status)
    }

    @Test
    fun `stale update after completion is rejected`() = runTest {
        seedProfile()
        dao.insert(command("c1", expiresAt = 500))
        assertEquals(1, dao.markStatus("primary", "c1", "COMPLETED", "{\"ok\":true}", "OK", 10))

        assertEquals(0, dao.markStatus("primary", "c1", "FAILED", null, "late", 11))
        val row = dao.getById("primary", "c1")!!
        assertEquals("COMPLETED", row.status)
        assertEquals("OK", row.resultSummary)
    }

    @Test
    fun `expireOverdue only expires submitted rows in the profile`() = runTest {
        seedProfile("primary")
        seedProfile("secondary")
        dao.insert(command("old", expiresAt = 50))
        dao.insert(command("future", expiresAt = 500))
        dao.insert(command("done", status = "COMPLETED", expiresAt = 50))
        dao.insert(command("other", profileId = "secondary", expiresAt = 50))

        assertEquals(1, dao.expireOverdue("primary", now = 100))
        assertEquals("EXPIRED", dao.getById("primary", "old")!!.status)
        assertEquals("SUBMITTED", dao.getById("primary", "future")!!.status)
        assertEquals("COMPLETED", dao.getById("primary", "done")!!.status)
        assertEquals("SUBMITTED", dao.getById("secondary", "other")!!.status)
    }

    @Test
    fun `prune keeps the newest terminal rows and never drops submitted`() = runTest {
        seedProfile()
        dao.insert(command("old", status = "COMPLETED", createdAt = 10, updatedAt = 10))
        dao.insert(command("mid", status = "FAILED", createdAt = 20, updatedAt = 20))
        dao.insert(command("new", status = "COMPLETED", createdAt = 30, updatedAt = 30))
        dao.insert(command("pending", status = "SUBMITTED", createdAt = 5, updatedAt = 5))

        assertEquals(2, dao.prune("primary", cutoff = 15, keepMax = 1))
        assertEquals(2, dao.count("primary"))
        assertTrue(dao.getById("primary", "new") != null)
        assertTrue(dao.getById("primary", "pending") != null)
    }

    @Test
    fun `id and nonce lookups are profile scoped`() = runTest {
        seedProfile("primary")
        seedProfile("secondary")
        dao.insert(command("a", nonce = "n-a", createdAt = 10))
        dao.insert(command("b", nonce = "n-b", createdAt = 20))
        dao.insert(command("other", nonce = "n-other", profileId = "secondary", createdAt = 30))

        assertEquals(listOf("b", "a"), dao.observeRecent("primary", 10).first().map { it.commandId })
        assertEquals("a", dao.findByNonce("primary", "n-a")!!.commandId)
        assertNull(dao.getById("primary", "other"))
        assertNull(dao.findByNonce("primary", "n-other"))
        assertEquals(
            0,
            dao.markStatus("primary", "other", "COMPLETED", null, "wrong profile", 10),
        )
        assertEquals("SUBMITTED", dao.getById("secondary", "other")!!.status)
    }

    @Test
    fun `expireOverdue expires at the five minute boundary`() = runTest {
        seedProfile()
        dao.insert(command("boundary", expiresAt = 100))

        assertEquals(1, dao.expireOverdue("primary", now = 100))
        assertEquals("EXPIRED", dao.getById("primary", "boundary")!!.status)
    }
}
