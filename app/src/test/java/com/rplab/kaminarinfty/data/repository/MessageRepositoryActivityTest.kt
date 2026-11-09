package com.rplab.kaminarinfty.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.data.local.ServerProfileEntity
import com.rplab.kaminarinfty.model.ActivityType
import com.rplab.kaminarinfty.model.NftyMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MessageRepositoryActivityTest {

    private lateinit var database: KaminariDatabase
    private lateinit var repository: MessageRepository

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KaminariDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedProfile("primary")
        seedProfile("secondary")
        repository = RoomMessageRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun seedProfile(id: String) {
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

    private fun message(id: String, topic: String = "ops") = NftyMessage(
        id = id,
        time = 1_000,
        topic = topic,
        title = "secret title",
        message = "secret body",
    )

    @Test
    fun `store writes one receive event with topic but no body or title`() = runTest {
        assertTrue(repository.store("primary", message("m1")))

        val event = database.activityDao().observeRecent("primary", 10).first().single()
        assertEquals(ActivityType.MESSAGE_RECEIVED.name, event.type)
        assertEquals("primary", event.profileId)
        assertEquals("m1", event.messageId)
        assertEquals("ops", event.topic)
        assertEquals("Message received in ops", event.summary)
        assertFalse(event.summary.contains("secret body"))
        assertFalse(event.summary.contains("secret title"))
    }

    @Test
    fun `duplicate store writes no second event`() = runTest {
        assertTrue(repository.store("primary", message("m1")))
        assertFalse(repository.store("primary", message("m1")))

        assertEquals(1, repository.count("primary"))
        assertEquals(1, database.activityDao().count("primary"))
    }

    @Test
    fun `receive events are profile scoped`() = runTest {
        repository.store("primary", message("m1"))
        repository.store("secondary", message("m2"))

        val primary = database.activityDao().observeRecent("primary", 10).first()
        val secondary = database.activityDao().observeRecent("secondary", 10).first()

        assertEquals(listOf("m1"), primary.map { it.messageId })
        assertEquals(listOf("m2"), secondary.map { it.messageId })
    }

    @Test
    fun `receive history survives repository recreation`() = runTest {
        repository.store("primary", message("m1"))

        val reopened = RoomMessageRepository(database)
        reopened.store("primary", message("m2"))

        assertEquals(2, database.activityDao().count("primary"))
    }
}
