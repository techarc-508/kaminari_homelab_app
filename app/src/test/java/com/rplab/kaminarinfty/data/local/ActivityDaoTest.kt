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
class ActivityDaoTest {

    private lateinit var database: KaminariDatabase
    private lateinit var dao: ActivityDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KaminariDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.activityDao()
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

    private fun event(
        id: String,
        profileId: String = "primary",
        createdAt: Long = 1L,
        type: String = "MESSAGE_RECEIVED",
    ) = ActivityEventEntity(
        id = id,
        profileId = profileId,
        type = type,
        severity = "INFO",
        summary = "Message received in ops",
        correlationId = null,
        messageId = null,
        topic = "ops",
        createdAt = createdAt,
    )

    @Test
    fun `observeRecent is bounded, newest first and profile scoped`() = runTest {
        seedProfile("primary")
        seedProfile("secondary")
        dao.insert(event("a", createdAt = 10))
        dao.insert(event("b", createdAt = 20))
        dao.insert(event("c", createdAt = 30))
        dao.insert(event("other", profileId = "secondary", createdAt = 40))

        assertEquals(listOf("c", "b"), dao.observeRecent("primary", 2).first().map { it.id })
        assertEquals(listOf("other"), dao.observeRecent("secondary", 10).first().map { it.id })
    }

    @Test
    fun `prune enforces age and row caps without touching other profiles`() = runTest {
        seedProfile("primary")
        seedProfile("secondary")
        dao.insert(event("old", createdAt = 10))
        dao.insert(event("keep1", createdAt = 20))
        dao.insert(event("keep2", createdAt = 30))
        dao.insert(event("keep3", createdAt = 40))
        dao.insert(event("other", profileId = "secondary", createdAt = 10))

        val removed = dao.prune("primary", cutoff = 15, keepMax = 2)
        assertEquals(2, removed)
        assertEquals(2, dao.count("primary"))
        assertEquals(1, dao.count("secondary"))
        assertTrue(dao.observeRecent("primary", 10).first().map { it.id }.containsAll(listOf("keep2", "keep3")))
    }

    @Test
    fun `activity rows cascade with the profile`() = runTest {
        seedProfile("primary")
        dao.insert(event("a"))
        database.serverProfileDao().deleteById("primary")
        assertEquals(0, dao.count("primary"))
    }
}
