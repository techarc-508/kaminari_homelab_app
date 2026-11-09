package com.rplab.kaminarinfty.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rplab.kaminarinfty.data.local.ActivityEventEntity
import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.data.local.RetentionPolicy
import com.rplab.kaminarinfty.data.local.ServerProfileEntity
import com.rplab.kaminarinfty.model.ActivitySeverity
import com.rplab.kaminarinfty.model.ActivityType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ActivityRepositoryTest {

    private lateinit var database: KaminariDatabase
    private lateinit var repository: ActivityRepository
    private var clock = 10_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KaminariDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = RoomActivityRepository(database) { clock }
        seedProfile("primary")
        seedProfile("secondary")
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun seedProfile(id: String) = kotlinx.coroutines.runBlocking {
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

    @Test
    fun `record persists a sanitized event and observe is newest first`() = runTest {
        repository.record(
            "primary",
            ActivityType.PUBLISH_QUEUED,
            ActivitySeverity.INFO,
            "Publish to ops\nqueued",
            topic = "ops",
        )
        clock = 11_000
        repository.record(
            "primary",
            ActivityType.PUBLISH_ACCEPTED,
            ActivitySeverity.SUCCESS,
            "Publish to ops accepted by server",
            topic = "ops",
        )

        val events = repository.observe("primary").first()

        assertEquals(
            listOf(ActivityType.PUBLISH_ACCEPTED, ActivityType.PUBLISH_QUEUED),
            events.map { it.type },
        )
        assertFalse(events.last().summary.contains('\n'))
        assertEquals(10_000L, events.last().createdAt)
        assertEquals("ops", events.last().topic)
    }

    @Test
    fun `deterministic event id prevents duplicate history`() = runTest {
        val first = repository.record(
            "primary",
            ActivityType.PUBLISH_STARTED,
            ActivitySeverity.INFO,
            "Publishing to ops",
            topic = "ops",
            eventId = "outbox:abc:started:1",
        )
        val second = repository.record(
            "primary",
            ActivityType.PUBLISH_STARTED,
            ActivitySeverity.INFO,
            "Publishing to ops",
            topic = "ops",
            eventId = "outbox:abc:started:1",
        )

        assertTrue(first)
        assertFalse(second)
        assertEquals(1, repository.observe("primary").first().size)
    }

    @Test
    fun `blank profile is rejected without writing`() = runTest {
        assertFalse(
            repository.record(
                "  ",
                ActivityType.PUBLISH_QUEUED,
                ActivitySeverity.INFO,
                "ignored",
            )
        )
        assertEquals(0, database.activityDao().count("primary"))
        assertTrue(repository.observe("  ").first().isEmpty())
    }

    @Test
    fun `events are profile scoped`() = runTest {
        repository.record("primary", ActivityType.PUBLISH_QUEUED, ActivitySeverity.INFO, "a")
        repository.record("secondary", ActivityType.PUBLISH_QUEUED, ActivitySeverity.INFO, "b")

        assertEquals(listOf("a"), repository.observe("primary").first().map { it.summary })
        assertEquals(listOf("b"), repository.observe("secondary").first().map { it.summary })
    }

    @Test
    fun `history survives repository recreation`() = runTest {
        repository.record(
            "primary",
            ActivityType.PUBLISH_ACCEPTED,
            ActivitySeverity.SUCCESS,
            "Publish to ops accepted by server",
            topic = "ops",
        )

        val reopened = RoomActivityRepository(database) { clock }

        assertEquals(
            listOf(ActivityType.PUBLISH_ACCEPTED),
            reopened.observe("primary").first().map { it.type },
        )
    }

    @Test
    fun `record prunes events older than the 90 day policy`() = runTest {
        clock = TimeUnit.DAYS.toMillis(100)
        val dao = database.activityDao()
        dao.insert(
            ActivityEventEntity(
                id = "old",
                profileId = "primary",
                type = ActivityType.MESSAGE_RECEIVED.name,
                severity = ActivitySeverity.INFO.name,
                summary = "old",
                createdAt = 1,
            )
        )

        repository.record("primary", ActivityType.PUBLISH_QUEUED, ActivitySeverity.INFO, "new")

        assertEquals(1, dao.count("primary"))
        assertEquals(
            listOf("new"),
            dao.observeRecent("primary", 10).first().map { it.summary },
        )
    }

    @Test
    fun `prune enforces cutoff and keeps other profiles`() = runTest {
        clock = TimeUnit.DAYS.toMillis(91)
        val dao = database.activityDao()
        dao.insert(event("old", "primary", createdAt = 1))
        dao.insert(event("recent", "primary", createdAt = clock - 1_000))
        dao.insert(event("other", "secondary", createdAt = 1))

        repository.prune("primary")

        assertEquals(
            listOf("recent"),
            dao.observeRecent("primary", 10).first().map { it.id },
        )
        assertEquals(1, dao.count("secondary"))
    }

    @Test
    fun `prune keeps only the newest 5000 events per profile`() = runTest {
        val dao = database.activityDao()
        repeat(RetentionPolicy.ACTIVITY_MAX_EVENTS_PER_PROFILE + 1) { index ->
            val createdAt = index + 1L
            dao.insert(
                event(
                    id = "e$index",
                    profileId = "primary",
                    createdAt = createdAt,
                )
            )
        }
        clock = RetentionPolicy.ACTIVITY_MAX_EVENTS_PER_PROFILE + 1L

        repository.prune("primary")

        assertEquals(RetentionPolicy.ACTIVITY_MAX_EVENTS_PER_PROFILE, dao.count("primary"))
        assertNull(dao.observeRecent("primary", 5_000).first().firstOrNull { it.id == "e0" })
        assertEquals("e5000", dao.observeRecent("primary", 1).first().single().id)
    }

    @Test
    fun `summary length is capped`() = runTest {
        repository.record(
            "primary",
            ActivityType.PUBLISH_FAILED,
            ActivitySeverity.ERROR,
            "x".repeat(500),
        )

        assertTrue(repository.observe("primary").first().single().summary.length <= 300)
    }

    private fun event(id: String, profileId: String, createdAt: Long) = ActivityEventEntity(
        id = id,
        profileId = profileId,
        type = ActivityType.MESSAGE_RECEIVED.name,
        severity = ActivitySeverity.INFO.name,
        summary = "event $id",
        createdAt = createdAt,
    )
}
