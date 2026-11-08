package com.rplab.kaminarinfty.data.repository

import com.rplab.kaminarinfty.data.api.NtfyApi
import com.rplab.kaminarinfty.data.api.NftyStreamEvent
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyErrorCategory
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.model.StreamUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamEngineTest {

    private val config = ServerConfig(serverUrl = "https://ntfy.test")

    private fun message(id: String, time: Long = 1_700_000_000_000) = NftyMessage(
        id = id,
        time = time,
        topic = "ops",
        message = "body $id",
    )

    /** Replays the given batches, one per connection attempt, recording every request. */
    private class RecordingApi(
        private val batches: List<List<NftyStreamEvent>>,
    ) : NtfyApi {
        val sinceValues = mutableListOf<String>()
        val pollValues = mutableListOf<Boolean>()

        override fun testConnection(config: ServerConfig): Result<AuthState> =
            Result.success(AuthState.GUEST)

        override fun openJsonStream(
            config: ServerConfig,
            topics: List<String>,
            since: String,
            poll: Boolean,
        ): Flow<NftyStreamEvent> = flow {
            sinceValues += since
            pollValues += poll
            val batch = batches[minOf(sinceValues.lastIndex, batches.lastIndex)]
            batch.forEach { emit(it) }
        }
    }

    private fun repository(api: NtfyApi, scope: CoroutineScope) = NftyRepository(api, scope)

    @Test
    fun `reconnect resumes from the durable cursor each attempt`() = runTest {
        val api = RecordingApi(
            listOf(
                listOf(NftyStreamEvent.Connected(AuthState.GUEST, 200)),
                listOf(NftyStreamEvent.Connected(AuthState.GUEST, 200)),
            )
        )
        val cursors = ArrayDeque(listOf("cursorOne", "cursorTwo"))
        val repo = repository(api, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))

        repo.startSubscription(config, listOf("ops")) { cursors.removeFirstOrNull() ?: "12h" }
        runCurrent()
        advanceTimeBy(4_000)
        runCurrent()

        assertTrue("expected at least 2 connections, got ${api.sinceValues}", api.sinceValues.size >= 2)
        assertEquals("cursorOne", api.sinceValues[0])
        assertEquals("cursorTwo", api.sinceValues[1])
        repo.close()
    }

    @Test
    fun `duplicate ids replayed on reconnect are emitted once`() = runTest {
        val replay = NftyStreamEvent.MessageReceived(message("m1"))
        val api = RecordingApi(
            listOf(
                listOf(NftyStreamEvent.Connected(AuthState.GUEST, 200), replay),
                listOf(NftyStreamEvent.Connected(AuthState.GUEST, 200), replay),
            )
        )
        val repo = repository(api, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        val received = mutableListOf<StreamUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.updates.collect { received += it }
        }

        repo.startSubscription(config, listOf("ops"))
        runCurrent()
        advanceTimeBy(4_000)
        runCurrent()

        assertEquals(1, received.filterIsInstance<StreamUpdate.NewMessage>().size)
        repo.close()
    }

    @Test
    fun `message_delete and message_clear are forwarded as typed updates`() = runTest {
        val api = RecordingApi(
            listOf(
                listOf(
                    NftyStreamEvent.Connected(AuthState.GUEST, 200),
                    NftyStreamEvent.MessageDeleted("gone", "ops"),
                    NftyStreamEvent.TopicCleared("ops"),
                )
            )
        )
        val repo = repository(api, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        val received = mutableListOf<StreamUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.updates.collect { received += it }
        }

        repo.startSubscription(config, listOf("ops"))
        runCurrent()

        assertEquals("gone", received.filterIsInstance<StreamUpdate.Removed>().single().messageId)
        assertEquals("ops", received.filterIsInstance<StreamUpdate.TopicCleared>().single().topic)
        repo.close()
    }

    @Test
    fun `terminal auth error is not retried and is visible`() = runTest {
        val api = RecordingApi(
            listOf(listOf(NftyStreamEvent.Error(NftyErrorCategory.AUTH, "HTTP 401", canRetry = false)))
        )
        val repo = repository(api, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))

        repo.startSubscription(config, listOf("ops"))
        advanceUntilIdle()
        advanceTimeBy(120_000)

        assertEquals(1, api.sinceValues.size)
        assertEquals(AuthState.AUTHENTICATION_FAILED, repo.connectionState.value)
        assertEquals(NftyErrorCategory.AUTH, repo.lastError.value?.first)
        repo.close()
    }

    @Test
    fun `dropped stream is reported as a drop and reconnect attempt is tracked`() = runTest {
        val api = RecordingApi(listOf(listOf(NftyStreamEvent.Connected(AuthState.GUEST, 200))))
        val repo = repository(api, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))

        repo.startSubscription(config, listOf("ops"))
        runCurrent()

        assertEquals(NftyErrorCategory.STREAM, repo.lastError.value?.first)
        assertTrue(repo.reconnectAttempt.value >= 1)
        assertNotEquals(AuthState.AUTHENTICATED, repo.connectionState.value)
        repo.close()
    }

    @Test
    fun `catchUp requests a bounded poll stream and forwards messages`() = runTest {
        val api = RecordingApi(
            listOf(listOf(NftyStreamEvent.Connected(AuthState.GUEST, 200), NftyStreamEvent.MessageReceived(message("h1"))))
        )
        val repo = repository(api, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))
        val received = mutableListOf<StreamUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.updates.collect { received += it }
        }

        val outcome = repo.catchUp(config, listOf("ops"), since = "m0")

        assertEquals(listOf(true), api.pollValues)
        assertEquals(listOf("m0"), api.sinceValues)
        assertEquals(1, outcome.received)
        assertEquals(0, outcome.duplicates)
        assertEquals("h1", received.filterIsInstance<StreamUpdate.NewMessage>().single().message.id)
        repo.close()
    }

    @Test
    fun `catchUp reports duplicates without re-emitting`() = runTest {
        val api = RecordingApi(
            listOf(
                listOf(
                    NftyStreamEvent.MessageReceived(message("dupe")),
                    NftyStreamEvent.MessageReceived(message("dupe")),
                )
            )
        )
        val repo = repository(api, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))

        val outcome = repo.catchUp(config, listOf("ops"), since = "all")

        assertEquals(1, outcome.received)
        assertEquals(1, outcome.duplicates)
        repo.close()
    }

    @Test
    fun `backoff grows exponentially and stays bounded`() {
        val attemptOne = StreamBackoff.delayMillis(1, jitter = 0.0)
        val attemptThree = StreamBackoff.delayMillis(3, jitter = 0.0)
        val attemptTwenty = StreamBackoff.delayMillis(20, jitter = 0.0)
        val maxJittered = StreamBackoff.delayMillis(20, jitter = 1.0)

        assertEquals(1_500L, attemptOne)
        assertEquals(6_000L, attemptThree)
        assertEquals(60_000L, attemptTwenty)
        assertTrue(maxJittered in 60_000L..72_000L)
        assertTrue(StreamBackoff.delayMillis(1, jitter = -1.0) >= 1_000L)
    }

    @Test
    fun `stopping the subscription cancels the stream job`() = runTest {
        val api = RecordingApi(listOf(listOf(NftyStreamEvent.Connected(AuthState.GUEST, 200))))
        val repo = repository(api, CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)))

        repo.startSubscription(config, listOf("ops"))
        runCurrent()
        repo.stopSubscription()
        advanceTimeBy(60_000)
        val connectionsAfterStop = api.sinceValues.size

        advanceTimeBy(60_000)
        assertEquals(connectionsAfterStop, api.sinceValues.size)
        assertEquals(AuthState.DISCONNECTED, repo.connectionState.value)
        repo.close()
    }

    @Test
    fun `switching topics after a reconnect clears stale replay ids`() = runTest {
        val api = RecordingApi(
            listOf(
                listOf(NftyStreamEvent.Connected(AuthState.GUEST, 200), NftyStreamEvent.MessageReceived(message("m1"))),
                listOf(NftyStreamEvent.Connected(AuthState.GUEST, 200), NftyStreamEvent.MessageReceived(message("m1"))),
            )
        )
        val scope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val repo = repository(api, scope)
        val received = mutableListOf<StreamUpdate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            repo.updates.collect { received += it }
        }

        repo.startSubscription(config, listOf("ops"))
        runCurrent()
        repo.startSubscription(config, listOf("media"))
        runCurrent()

        // A new subscription starts with a clean replay guard; durable dedupe is Room's job.
        assertEquals(2, received.filterIsInstance<StreamUpdate.NewMessage>().size)
        repo.close()
    }
}
