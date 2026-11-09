package com.rplab.kaminarinfty.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rplab.kaminarinfty.data.api.NtfyPublish
import com.rplab.kaminarinfty.data.api.PublishRequest
import com.rplab.kaminarinfty.data.api.PublishResult
import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.data.local.ServerProfileEntity
import com.rplab.kaminarinfty.model.ActivityType
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.testing.FakeProfileRepository
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
class OutboxIntegrationTest {

    private lateinit var database: KaminariDatabase
    private lateinit var publisher: RecordingPublisher
    private lateinit var outbox: OutboxRepository
    private var clock = 20_000L

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KaminariDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        listOf("primary", "secondary").forEach { id ->
            database.serverProfileDao().upsert(
                ServerProfileEntity(
                    id = id,
                    displayName = id,
                    baseUrl = "https://ntfy.test",
                    authMethod = "ACCESS_TOKEN",
                    createdAt = 0,
                    updatedAt = 0,
                )
            )
        }
        publisher = RecordingPublisher()
        val profiles = MapProfileRepository(
            mapOf(
                "primary" to config("token-a"),
                "secondary" to config("token-b"),
            )
        )
        val stream = NftyRepository(profileRepository = profiles, publisher = publisher)
        val activity = RoomActivityRepository(database) { clock }
        outbox = RoomOutboxRepository(
            database = database,
            profileRepository = profiles,
            activityRepository = activity,
            publish = { profileId, request -> stream.publish(profileId, request) },
            now = { clock },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun config(token: String) = ServerConfig(
        serverUrl = "https://ntfy.test",
        authMethod = AuthMethod.ACCESS_TOKEN,
        tokenOrPassword = token,
        useHttpsOnly = true,
    )

    private val request = PublishRequest(topic = "alerts", message = "hello")

    @Test
    fun `outbox uses the Stage B repository with the entry's profile config`() = runTest {
        val clientId = outbox.enqueue("primary", request).getOrThrow()

        val outcome = outbox.send("primary", clientId)

        assertEquals(SendOutcome.Accepted("ntfy-1"), outcome)
        assertEquals(1, publisher.calls.size)
        assertEquals("token-a", publisher.calls.single().first.tokenOrPassword)
        assertEquals("https://ntfy.test", publisher.calls.single().first.serverUrl)
        assertEquals("alerts", publisher.calls.single().second.topic)

        val row = database.outboxDao().getById("primary", clientId)!!
        assertEquals("ACCEPTED", row.state)
        assertEquals("ntfy-1", row.serverMessageId)
        val types = database.activityDao().observeRecent("primary", 10).first()
            .map { ActivityType.valueOf(it.type) }
        assertTrue(types.contains(ActivityType.PUBLISH_ACCEPTED))
    }

    @Test
    fun `secondary profile uses only its own token`() = runTest {
        val clientId = outbox.enqueue("secondary", request).getOrThrow()

        assertTrue(outbox.send("secondary", clientId) is SendOutcome.Accepted)

        assertEquals("token-b", publisher.calls.single().first.tokenOrPassword)
    }

    @Test
    fun `a Stage B authentication result becomes a retryable FAILED entry`() = runTest {
        publisher.result = PublishResult.AuthenticationFailed(401, "bad token")
        val clientId = outbox.enqueue("primary", request).getOrThrow()

        val outcome = outbox.send("primary", clientId)

        assertTrue(outcome is SendOutcome.Failed)
        assertTrue((outcome as SendOutcome.Failed).retryable)
        val row = database.outboxDao().getById("primary", clientId)!!
        assertEquals("FAILED", row.state)
        assertEquals(null, row.serverMessageId)
    }

    @Test
    fun `unknown profile is rejected before reaching the publisher`() = runTest {
        assertTrue(outbox.enqueue("missing", request).isFailure)
        assertEquals(0, publisher.calls.size)
        assertEquals(0, database.outboxDao().count("primary"))
    }

    private class RecordingPublisher : NtfyPublish {
        val calls = mutableListOf<Pair<ServerConfig, PublishRequest>>()
        var result: PublishResult = PublishResult.Accepted("ntfy-1")

        override suspend fun publish(config: ServerConfig, request: PublishRequest): PublishResult {
            calls += config to request
            return result
        }
    }

    private class MapProfileRepository(
        private val configs: Map<String, ServerConfig>,
    ) : ProfileRepository by FakeProfileRepository() {
        override suspend fun loadConfig(profileId: String): ServerConfig? = configs[profileId]
        override suspend fun loadActiveConfig(): ServerConfig? = configs.values.firstOrNull()
    }
}
