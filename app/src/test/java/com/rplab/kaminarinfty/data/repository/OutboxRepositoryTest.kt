package com.rplab.kaminarinfty.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.rplab.kaminarinfty.data.api.PublishRequest
import com.rplab.kaminarinfty.data.api.PublishResult
import com.rplab.kaminarinfty.data.local.ActivityEventEntity
import com.rplab.kaminarinfty.data.local.KaminariDatabase
import com.rplab.kaminarinfty.data.local.OutboxEntryEntity
import com.rplab.kaminarinfty.data.local.ServerProfileEntity
import com.rplab.kaminarinfty.model.ActivityType
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.OutboxState
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.testing.FakeProfileRepository
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
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
class OutboxRepositoryTest {

    private lateinit var database: KaminariDatabase
    private lateinit var profiles: MapProfileRepository
    private lateinit var activity: ActivityRepository
    private lateinit var outbox: OutboxRepository
    private var clock = 10_000L
    private val configs = mutableMapOf(
        "primary" to config("token-a"),
        "secondary" to config("token-b"),
    )
    private val published = mutableListOf<Pair<String, PublishRequest>>()
    private var onPublish: suspend (String, PublishRequest) -> PublishResult =
        { _, _ -> PublishResult.Accepted("server-1") }

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, KaminariDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        seedProfile("primary")
        seedProfile("secondary")
        profiles = MapProfileRepository(configs)
        activity = RoomActivityRepository(database) { clock }
        outbox = RoomOutboxRepository(
            database = database,
            profileRepository = profiles,
            activityRepository = activity,
            publish = { profileId, request ->
                published += profileId to request
                onPublish(profileId, request)
            },
            now = { clock },
        )
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
                authMethod = "ACCESS_TOKEN",
                createdAt = 0,
                updatedAt = 0,
            )
        )
    }

    private fun config(token: String) = ServerConfig(
        serverUrl = "https://ntfy.test",
        authMethod = AuthMethod.ACCESS_TOKEN,
        tokenOrPassword = token,
    )

    private val request = PublishRequest(
        topic = "ops",
        message = "top secret body",
        title = "secret title",
        tags = listOf("warning"),
    )

    private fun entry(
        clientId: String,
        profileId: String = "primary",
        state: String = "QUEUED",
        attemptCount: Int = 0,
        retryable: Boolean = false,
        updatedAt: Long = clock,
        createdAt: Long = updatedAt,
    ) = OutboxEntryEntity(
        clientId = clientId,
        profileId = profileId,
        topic = "ops",
        title = "secret title",
        body = "top secret body",
        priority = 3,
        tagsJson = "[\"warning\"]",
        clickUrl = null,
        attachmentUrl = null,
        actionsJson = null,
        delay = null,
        state = state,
        attemptCount = attemptCount,
        retryable = retryable,
        lastError = null,
        serverMessageId = null,
        createdAt = createdAt,
        updatedAt = updatedAt,
        acceptedAt = null,
    )

    private suspend fun events(profileId: String = "primary"): List<ActivityEventEntity> =
        database.activityDao().observeRecent(profileId, 100).first()

    private suspend fun row(clientId: String, profileId: String = "primary") =
        database.outboxDao().getById(profileId, clientId)

    @Test
    fun `enqueue persists QUEUED and records a bounded activity event`() = runTest {
        val clientId = outbox.enqueue("primary", request).getOrThrow()

        val stored = row(clientId)!!
        assertEquals("QUEUED", stored.state)
        assertEquals(0, stored.attemptCount)
        assertEquals("top secret body", stored.body)
        assertEquals("[\"warning\"]", stored.tagsJson)

        val event = events().single()
        assertEquals(ActivityType.PUBLISH_QUEUED, ActivityType.valueOf(event.type))
        assertEquals("ops", event.topic)
        assertEquals(clientId, event.correlationId)
        assertFalse(event.summary.contains("top secret body"))
        assertFalse(event.summary.contains("secret title"))
        assertEquals(0, published.size)
    }

    @Test
    fun `enqueue rejects invalid input and unknown profiles without writing`() = runTest {
        assertTrue(outbox.enqueue("primary", request.copy(message = " ")).isFailure)
        assertTrue(outbox.enqueue("missing", request).isFailure)
        assertTrue(outbox.enqueue(" ", request).isFailure)

        assertEquals(0, database.outboxDao().count("primary"))
        assertEquals(0, database.activityDao().count("primary"))
        assertEquals(0, published.size)
    }

    @Test
    fun `outbox reads are profile scoped`() = runTest {
        val primaryId = outbox.enqueue("primary", request).getOrThrow()
        val secondaryId = outbox.enqueue("secondary", request).getOrThrow()

        assertEquals(
            listOf(primaryId),
            outbox.observe("primary").first().map { it.clientId },
        )
        assertEquals(
            listOf(secondaryId),
            outbox.observe("secondary").first().map { it.clientId },
        )
        assertTrue(outbox.observe(" ").first().isEmpty())
    }

    @Test
    fun `successful send accepts once and records started and accepted`() = runTest {
        val clientId = outbox.enqueue("primary", request).getOrThrow()
        clock = 11_000

        val outcome = outbox.send("primary", clientId)

        assertEquals(SendOutcome.Accepted("server-1"), outcome)
        val stored = row(clientId)!!
        assertEquals("ACCEPTED", stored.state)
        assertEquals("server-1", stored.serverMessageId)
        assertEquals(1, stored.attemptCount)
        assertEquals(11_000L, stored.acceptedAt)
        assertEquals("primary", published.single().first)
        assertEquals(request.message, published.single().second.message)

        val types = events().map { ActivityType.valueOf(it.type) }
        assertTrue(types.contains(ActivityType.PUBLISH_STARTED))
        assertTrue(types.contains(ActivityType.PUBLISH_ACCEPTED))
        assertTrue(events().none { it.summary.contains("top secret body") })
    }

    @Test
    fun `send refuses a profile that disappeared after enqueue`() = runTest {
        val clientId = outbox.enqueue("primary", request).getOrThrow()
        configs.remove("primary")

        val outcome = outbox.send("primary", clientId)

        assertTrue(outcome is SendOutcome.Failed)
        assertEquals(false, (outcome as SendOutcome.Failed).retryable)
        assertEquals("FAILED", row(clientId)!!.state)
        assertEquals(0, published.size)
    }

    @Test
    fun `preflight credential failure never calls the network`() = runTest {
        val clientId = outbox.enqueue("primary", request).getOrThrow()
        configs["primary"] = config("")

        val outcome = outbox.send("primary", clientId)

        assertTrue(outcome is SendOutcome.Failed)
        assertTrue((outcome as SendOutcome.Failed).retryable)
        assertEquals("FAILED", row(clientId)!!.state)
        assertEquals(0, published.size)
        assertTrue(events().any { ActivityType.valueOf(it.type) == ActivityType.PUBLISH_FAILED })
    }

    @Test
    fun `authentication and permission failures are retryable FAILED`() = runTest {
        onPublish = { _, _ -> PublishResult.AuthenticationFailed(401, "bad token") }
        val authId = outbox.enqueue("primary", request).getOrThrow()
        val auth = outbox.send("primary", authId)
        assertTrue(auth is SendOutcome.Failed)
        assertTrue((auth as SendOutcome.Failed).retryable)
        assertEquals("FAILED", row(authId)!!.state)

        onPublish = { _, _ -> PublishResult.PermissionFailed(403, "denied") }
        val permissionId = outbox.enqueue("primary", request).getOrThrow()
        val permission = outbox.send("primary", permissionId)
        assertTrue(permission is SendOutcome.Failed)
        assertTrue((permission as SendOutcome.Failed).retryable)
    }

    @Test
    fun `server error is retryable but client rejection is not`() = runTest {
        onPublish = { _, _ -> PublishResult.ServerError(503, "busy") }
        val serverId = outbox.enqueue("primary", request).getOrThrow()
        val server = outbox.send("primary", serverId)
        assertTrue(server is SendOutcome.Failed)
        assertTrue((server as SendOutcome.Failed).retryable)

        onPublish = { _, _ -> PublishResult.ValidationFailed("bad topic") }
        val rejectedId = outbox.enqueue("primary", request).getOrThrow()
        val rejected = outbox.send("primary", rejectedId)
        assertTrue(rejected is SendOutcome.Failed)
        assertFalse((rejected as SendOutcome.Failed).retryable)
    }

    @Test
    fun `timeout and unknown server outcomes become AMBIGUOUS`() = runTest {
        onPublish = { _, _ ->
            PublishResult.NetworkError("timeout", SocketTimeoutException("read"))
        }
        val timeoutId = outbox.enqueue("primary", request).getOrThrow()
        val timeout = outbox.send("primary", timeoutId)

        assertTrue(timeout is SendOutcome.Ambiguous)
        assertEquals("AMBIGUOUS", row(timeoutId)!!.state)

        onPublish = { _, _ -> PublishResult.Ambiguous("unexpected") }
        val ambiguousId = outbox.enqueue("primary", request).getOrThrow()
        val ambiguous = outbox.send("primary", ambiguousId)

        assertTrue(ambiguous is SendOutcome.Ambiguous)
        assertEquals("AMBIGUOUS", row(ambiguousId)!!.state)
    }

    @Test
    fun `definite connect failure is retryable FAILED`() = runTest {
        onPublish = { _, _ -> PublishResult.NetworkError("unreachable", ConnectException()) }
        val clientId = outbox.enqueue("primary", request).getOrThrow()

        val outcome = outbox.send("primary", clientId)

        assertTrue(outcome is SendOutcome.Failed)
        assertTrue((outcome as SendOutcome.Failed).retryable)
        assertEquals("FAILED", row(clientId)!!.state)
    }

    @Test
    fun `unexpected publisher exception is ambiguous, never accepted`() = runTest {
        onPublish = { _, _ -> throw IOException("socket vanished") }
        val clientId = outbox.enqueue("primary", request).getOrThrow()

        val outcome = outbox.send("primary", clientId)

        assertTrue(outcome is SendOutcome.Ambiguous)
        assertEquals("AMBIGUOUS", row(clientId)!!.state)
    }

    @Test
    fun `stuck SENDING becomes AMBIGUOUS on recovery and send refuses to resend`() = runTest {
        database.outboxDao().insert(
            entry("stuck", state = "SENDING", attemptCount = 1, updatedAt = clock - 180_000)
        )

        val outcome = outbox.send("primary", "stuck")

        assertEquals(SendOutcome.Busy, outcome)
        assertEquals("AMBIGUOUS", row("stuck")!!.state)
        assertEquals(0, published.size)
        assertTrue(
            events().any {
                ActivityType.valueOf(it.type) == ActivityType.PUBLISH_AMBIGUOUS &&
                    it.summary.contains("interrupted")
            }
        )
    }

    @Test
    fun `recovery only touches SENDING rows older than the cutoff`() = runTest {
        database.outboxDao().insert(
            entry("old", state = "SENDING", attemptCount = 1, updatedAt = clock - 180_000)
        )
        database.outboxDao().insert(
            entry("recent", state = "SENDING", attemptCount = 1, updatedAt = clock - 1_000)
        )

        assertEquals(1, outbox.recoverStuck("primary"))

        assertEquals("AMBIGUOUS", row("old")!!.state)
        assertEquals("SENDING", row("recent")!!.state)
    }

    @Test
    fun `ambiguous retry requires an explicit confirmation`() = runTest {
        database.outboxDao().insert(
            entry("ambiguous", state = "AMBIGUOUS", attemptCount = 1, retryable = true)
        )

        val outcome = outbox.retry("primary", "ambiguous")

        assertTrue(outcome is SendOutcome.ConfirmationRequired)
        assertEquals("AMBIGUOUS", row("ambiguous")!!.state)
        assertEquals(0, published.size)
        assertTrue(events().any { ActivityType.valueOf(it.type) == ActivityType.PUBLISH_RETRY })
    }

    @Test
    fun `confirmed ambiguous retry requeues and sends exactly once`() = runTest {
        database.outboxDao().insert(
            entry("ambiguous", state = "AMBIGUOUS", attemptCount = 1, retryable = true)
        )
        clock = 12_000

        val outcome = outbox.retry("primary", "ambiguous", allowAmbiguous = true)

        assertEquals(SendOutcome.Accepted("server-1"), outcome)
        assertEquals(1, published.size)
        val stored = row("ambiguous")!!
        assertEquals("ACCEPTED", stored.state)
        assertEquals(2, stored.attemptCount)
        val types = events().map { ActivityType.valueOf(it.type) }
        assertTrue(types.contains(ActivityType.PUBLISH_RETRY))
        assertTrue(types.contains(ActivityType.PUBLISH_ACCEPTED))
    }

    @Test
    fun `non-retryable failure and accepted entries cannot be retried`() = runTest {
        database.outboxDao().insert(entry("failed", state = "FAILED", retryable = false))
        database.outboxDao().insert(entry("accepted", state = "ACCEPTED", retryable = false))

        val failed = outbox.retry("primary", "failed")
        val accepted = outbox.retry("primary", "accepted")

        assertTrue(failed is SendOutcome.Failed)
        assertFalse((failed as SendOutcome.Failed).retryable)
        assertTrue(accepted is SendOutcome.Failed)
        assertEquals("FAILED", row("failed")!!.state)
        assertEquals("ACCEPTED", row("accepted")!!.state)
        assertEquals(0, published.size)
    }

    @Test
    fun `retryable FAILED entry can be sent again without losing its attempt count`() = runTest {
        database.outboxDao().insert(
            entry("failed", state = "FAILED", attemptCount = 1, retryable = true)
        )

        val outcome = outbox.retry("primary", "failed")

        assertEquals(SendOutcome.Accepted("server-1"), outcome)
        assertEquals(1, published.size)
        assertEquals(2, row("failed")!!.attemptCount)
    }

    @Test
    fun `a queued retry is protected by the same claim`() = runTest {
        val clientId = outbox.enqueue("primary", request).getOrThrow()

        val outcome = outbox.retry("primary", clientId)

        assertEquals(SendOutcome.Accepted("server-1"), outcome)
        assertEquals(1, published.size)
    }

    @Test
    fun `double send publishes once and the loser gets Busy`() = runTest {
        val clientId = outbox.enqueue("primary", request).getOrThrow()

        assertTrue(outbox.send("primary", clientId) is SendOutcome.Accepted)
        assertEquals(SendOutcome.Busy, outbox.send("primary", clientId))
        assertEquals(1, published.size)
        assertEquals(1, row(clientId)!!.attemptCount)
    }

    @Test
    fun `repeated retry taps publish once`() = runTest {
        database.outboxDao().insert(
            entry("failed", state = "FAILED", attemptCount = 1, retryable = true)
        )

        assertTrue(outbox.retry("primary", "failed") is SendOutcome.Accepted)
        assertTrue(outbox.retry("primary", "failed") is SendOutcome.Failed)

        assertEquals(1, published.size)
        assertEquals("ACCEPTED", row("failed")!!.state)
    }

    @Test
    fun `concurrent claims publish once`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        onPublish = { _, _ ->
            gate.await()
            PublishResult.Accepted("server-1")
        }
        val clientId = outbox.enqueue("primary", request).getOrThrow()

        val first = async(Dispatchers.Default) { outbox.send("primary", clientId) }
        withTimeout(10_000) {
            while (row(clientId)?.state != "SENDING") delay(5)
        }
        val second = outbox.send("primary", clientId)
        gate.complete(Unit)

        assertEquals(SendOutcome.Busy, second)
        assertEquals(SendOutcome.Accepted("server-1"), first.await())
        assertEquals(1, published.size)
        assertEquals("ACCEPTED", row(clientId)!!.state)
    }

    @Test
    fun `ambiguous entries are never resent automatically`() = runTest {
        onPublish = { _, _ -> PublishResult.Ambiguous("unknown") }
        val clientId = outbox.enqueue("primary", request).getOrThrow()

        assertTrue(outbox.send("primary", clientId) is SendOutcome.Ambiguous)

        assertEquals(SendOutcome.Busy, outbox.send("primary", clientId))
        assertEquals(1, published.size)
        assertEquals("AMBIGUOUS", row(clientId)!!.state)
    }

    @Test
    fun `failed entries are not retried automatically`() = runTest {
        onPublish = { _, _ -> PublishResult.ServerError(500, "down") }
        val clientId = outbox.enqueue("primary", request).getOrThrow()

        assertTrue(outbox.send("primary", clientId) is SendOutcome.Failed)

        assertEquals(SendOutcome.Busy, outbox.send("primary", clientId))
        assertEquals(1, published.size)
        assertEquals("FAILED", row(clientId)!!.state)
    }

    @Test
    fun `cancel removes only unsent entries`() = runTest {
        database.outboxDao().insert(entry("queued"))
        database.outboxDao().insert(entry("accepted", state = "ACCEPTED"))

        assertTrue(outbox.cancel("primary", "queued"))
        assertFalse(outbox.cancel("primary", "accepted"))

        assertNull(row("queued"))
        assertTrue(row("accepted") != null)
    }

    @Test
    fun `send and retry reject another profile's client id`() = runTest {
        val clientId = outbox.enqueue("primary", request).getOrThrow()

        assertEquals(SendOutcome.NotFound, outbox.send("secondary", clientId))
        assertEquals(SendOutcome.NotFound, outbox.retry("secondary", clientId))
        assertEquals(SendOutcome.NotFound, outbox.send(" ", clientId))

        assertEquals(0, published.size)
        assertEquals("QUEUED", row(clientId)!!.state)
        assertEquals("QUEUED", row(clientId, "primary")!!.state)
    }

    @Test
    fun `prune removes old terminal rows but keeps active work and other profiles`() = runTest {
        clock = TimeUnit.DAYS.toMillis(60)
        val old = clock - TimeUnit.DAYS.toMillis(31)
        database.outboxDao().insert(entry("old-accepted", state = "ACCEPTED", updatedAt = old))
        database.outboxDao().insert(entry("old-failed", state = "FAILED", updatedAt = old))
        database.outboxDao().insert(entry("old-ambiguous", state = "AMBIGUOUS", updatedAt = old))
        database.outboxDao().insert(entry("old-queued", state = "QUEUED", updatedAt = old))
        database.outboxDao().insert(entry("old-sending", state = "SENDING", updatedAt = old))
        database.outboxDao().insert(entry("new-accepted", state = "ACCEPTED", updatedAt = clock - 1_000))
        database.outboxDao().insert(
            entry("other", profileId = "secondary", state = "ACCEPTED", updatedAt = old)
        )

        outbox.prune("primary")

        assertNull(row("old-accepted"))
        assertNull(row("old-failed"))
        assertNull(row("old-ambiguous"))
        assertTrue(row("old-queued") != null)
        assertTrue(row("old-sending") != null)
        assertTrue(row("new-accepted") != null)
        assertTrue(row("other", "secondary") != null)
    }

    @Test
    fun `outbox survives repository recreation`() = runTest {
        val clientId = outbox.enqueue("primary", request).getOrThrow()
        clock = 11_000
        outbox.send("primary", clientId)

        val reopened = RoomOutboxRepository(
            database,
            profiles,
            RoomActivityRepository(database) { clock },
            publish = { _, _ -> PublishResult.Accepted("unused") },
            now = { clock },
        )

        val stored = reopened.observe("primary").first().single()
        assertEquals(OutboxState.ACCEPTED, stored.state)
        assertEquals("server-1", stored.serverMessageId)
        assertEquals("ops", stored.topic)
        assertEquals(listOf("warning"), stored.tags)
    }

    private class MapProfileRepository(
        private val configs: Map<String, ServerConfig>,
    ) : ProfileRepository by FakeProfileRepository() {
        override suspend fun loadConfig(profileId: String): ServerConfig? = configs[profileId]
        override suspend fun loadActiveConfig(): ServerConfig? = configs.values.firstOrNull()
    }
}
