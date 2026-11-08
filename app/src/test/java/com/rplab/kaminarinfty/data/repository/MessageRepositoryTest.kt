package com.rplab.kaminarinfty.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.data.local.ServerProfileEntity
import com.rplab.kaminarinfty.model.NftyAction
import com.rplab.kaminarinfty.model.NftyAttachment
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.NftyPriority
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MessageRepositoryTest {

    private lateinit var database: KaminariDatabase
    private lateinit var repository: MessageRepository

    private val profileId = "primary"

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KaminariDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        database.serverProfileDao().upsert(
            ServerProfileEntity(
                id = profileId,
                displayName = "test",
                baseUrl = "https://ntfy.test",
                authMethod = "GUEST",
                createdAt = 0,
                updatedAt = 0,
            )
        )
        repository = RoomMessageRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun message(
        id: String,
        time: Long,
        topic: String = "ops",
        saved: Boolean = false,
    ) = NftyMessage(
        id = id,
        time = time,
        topic = topic,
        title = "title $id",
        message = "body $id",
        priority = NftyPriority.HIGH,
        tags = listOf("warning"),
        click = "https://pve.local/$id",
        attachment = NftyAttachment(name = "a.png", url = "https://ntfy.test/f/$id", type = "image/png", size = 42),
        actions = listOf(NftyAction(id = "view", action = "view", label = "Open", url = "https://pve.local")),
        expiry = 1_700_003_600L,
        isSaved = saved,
    )

    @Test
    fun `store persists the message and returns true only once`() = runTest {
        assertTrue(repository.store(profileId, message("m1", 1_000)))
        assertFalse(repository.store(profileId, message("m1", 1_000)))
        assertEquals(1, repository.count(profileId))
    }

    @Test
    fun `stored messages survive a fresh repository instance`() = runTest {
        repository.store(profileId, message("m1", 1_000))

        val reopened = RoomMessageRepository(database)
        val rows = reopened.observe(profileId).first()

        assertEquals(listOf("m1"), rows.map { it.id })
    }

    @Test
    fun `full message metadata round-trips through the database`() = runTest {
        repository.store(profileId, message("m1", 1_000))

        val stored = repository.observe(profileId).first().single()

        assertEquals(NftyPriority.HIGH, stored.priority)
        assertEquals(listOf("warning"), stored.tags)
        assertEquals("https://pve.local/m1", stored.click)
        assertEquals("a.png", stored.attachment?.name)
        assertEquals(42L, stored.attachment?.size)
        assertEquals("view", stored.actions.single().id)
        assertEquals(1_700_003_600L, stored.expiry)
    }

    @Test
    fun `latestCursor is the newest stored message id`() = runTest {
        assertNull(repository.latestCursor(profileId))

        repository.store(profileId, message("older", 1_000))
        repository.store(profileId, message("newest", 3_000))
        repository.store(profileId, message("middle", 2_000))

        assertEquals("newest", repository.latestCursor(profileId))
    }

    @Test
    fun `page returns bounded windows newest first`() = runTest {
        repository.store(profileId, message("m1", 1_000))
        repository.store(profileId, message("m2", 2_000))
        repository.store(profileId, message("m3", 3_000))

        assertEquals(listOf("m3", "m2"), repository.page(profileId, limit = 2, offset = 0).map { it.id })
        assertEquals(listOf("m1"), repository.page(profileId, limit = 2, offset = 2).map { it.id })
    }

    @Test
    fun `exists reflects durable dedupe`() = runTest {
        repository.store(profileId, message("m1", 1_000))

        assertTrue(repository.exists(profileId, "m1"))
        assertFalse(repository.exists(profileId, "m2"))
    }

    @Test
    fun `clearTopic removes unsaved messages but keeps starred ones`() = runTest {
        repository.store(profileId, message("m1", 1_000, topic = "ops"))
        repository.store(profileId, message("m2", 2_000, topic = "ops", saved = true))
        repository.store(profileId, message("m3", 3_000, topic = "media"))

        repository.clearTopic(profileId, "ops")

        assertEquals(listOf("m3", "m2"), repository.observe(profileId).first().map { it.id })
    }

    @Test
    fun `delete removes only the requested message`() = runTest {
        repository.store(profileId, message("m1", 1_000))
        repository.store(profileId, message("m2", 2_000))

        repository.delete(profileId, "m1")

        assertEquals(listOf("m2"), repository.observe(profileId).first().map { it.id })
    }

    @Test
    fun `messages are scoped per profile`() = runTest {
        database.serverProfileDao().upsert(
            ServerProfileEntity(
                id = "secondary",
                displayName = "second",
                baseUrl = "https://ntfy.test",
                authMethod = "GUEST",
                createdAt = 0,
                updatedAt = 0,
            )
        )
        repository.store(profileId, message("m1", 1_000))
        repository.store("secondary", message("m1", 1_000))

        assertEquals(1, repository.count(profileId))
        assertEquals(1, repository.count("secondary"))
    }
}
