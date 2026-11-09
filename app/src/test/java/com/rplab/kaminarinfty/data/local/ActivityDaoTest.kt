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
import java.util.concurrent.TimeUnit

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
    fun `prune enforces the 90 day policy without touching other profiles`() = runTest {
        seedProfile("primary")
        seedProfile("secondary")
        val now = TimeUnit.DAYS.toMillis(91)
        dao.insert(event("old", createdAt = 1))
        dao.insert(event("recent", createdAt = now - 1_000))
        dao.insert(event("other", profileId = "secondary", createdAt = 1))

        assertEquals(1, dao.prune("primary", now))
        assertEquals(1, dao.count("primary"))
        assertEquals(1, dao.count("secondary"))
        assertTrue(dao.observeRecent("primary", 10).first().map { it.id } == listOf("recent"))
    }

    @Test
    fun `pruneBeyond keeps only the newest events per profile`() = runTest {
        seedProfile("primary")
        dao.insert(event("old", createdAt = 10))
        dao.insert(event("mid", createdAt = 20))
        dao.insert(event("new", createdAt = 30))

        assertEquals(1, dao.pruneBeyond("primary", keepMax = 2))
        assertEquals(2, dao.count("primary"))
        assertTrue(
            dao.observeRecent("primary", 10).first().map { it.id } == listOf("new", "mid")
        )
    }

    @Test
    fun `activity rows cascade with the profile`() = runTest {
        seedProfile("primary")
        dao.insert(event("a"))
        database.serverProfileDao().deleteById("primary")
        assertEquals(0, dao.count("primary"))
    }
}
