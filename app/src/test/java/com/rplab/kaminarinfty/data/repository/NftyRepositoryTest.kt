package com.rplab.kaminarinfty.data.repository

import com.rplab.kaminarinfty.data.api.NtfyApi
import com.rplab.kaminarinfty.data.api.NftyStreamEvent
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyErrorCategory
import com.rplab.kaminarinfty.model.ServerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NftyRepositoryTest {

    private val config = ServerConfig(serverUrl = "https://ntfy.test")

    private class ScriptedApi(
        private val batches: List<List<NftyStreamEvent>>,
    ) : NtfyApi {
        var calls = 0

        override fun testConnection(config: ServerConfig): Result<AuthState> =
            Result.success(AuthState.GUEST)

        override fun openJsonStream(
            config: ServerConfig,
            topics: List<String>,
            since: String,
            poll: Boolean,
        ): Flow<NftyStreamEvent> = flow {
            val batch = batches[minOf(calls, batches.lastIndex)]
            calls++
            batch.forEach { emit(it) }
        }
    }

    @Test
    fun `retriable errors loop with backoff`() = runTest {
        val api = ScriptedApi(
            listOf(listOf(NftyStreamEvent.Error(NftyErrorCategory.STREAM, "boom", canRetry = true)))
        )
        val repository = NftyRepository(
            api,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )

        repository.startSubscription(config, listOf("ops"))
        advanceTimeBy(10_000)

        assertTrue("expected retries, got ${api.calls}", api.calls >= 2)
        assertEquals(AuthState.RECONNECTING, repository.connectionState.value)
        assertTrue("retriable errors must not be terminal", !repository.isTerminal.value)
        repository.close()
    }

    @Test
    fun `terminal auth errors stop the reconnect loop`() = runTest {
        val api = ScriptedApi(
            listOf(listOf(NftyStreamEvent.Error(NftyErrorCategory.AUTH, "401", canRetry = false)))
        )
        val repository = NftyRepository(
            api,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )

        repository.startSubscription(config, listOf("ops"))
        advanceUntilIdle()
        advanceTimeBy(120_000)

        assertEquals(1, api.calls)
        assertEquals(AuthState.AUTHENTICATION_FAILED, repository.connectionState.value)
        // Background owners (InstantForegroundService) observe this to stop the
        // service instead of leaving a "live delivery" notification over a dead stream.
        assertTrue(repository.isTerminal.value)
        repository.close()
    }

    @Test
    fun `arbitrary status codes are no longer treated as authentication`() = runTest {
        val api = ScriptedApi(
            listOf(listOf(NftyStreamEvent.Error(NftyErrorCategory.UNREACHABLE, "HTTP 418", canRetry = true)))
        )
        val repository = NftyRepository(
            api,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )

        repository.startSubscription(config, listOf("ops"))
        advanceTimeBy(5_000)

        assertTrue(repository.connectionState.value != AuthState.AUTHENTICATED)
        assertTrue(repository.connectionState.value != AuthState.GUEST)
        repository.close()
    }

    @Test
    fun `malformed stream lines are counted, not silently swallowed`() = runTest {
        val api = ScriptedApi(
            listOf(
                listOf(
                    NftyStreamEvent.Malformed("unparseable stream line"),
                    NftyStreamEvent.KeepAlive(0),
                )
            )
        )
        val repository = NftyRepository(
            api,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )

        repository.startSubscription(config, listOf("ops"))
        runCurrent()

        assertEquals(1, repository.malformedLineCount.value)
        repository.close()
    }

    @Test
    fun `empty topic list never opens a stream`() = runTest {
        val api = ScriptedApi(listOf(emptyList()))
        val repository = NftyRepository(
            api,
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler)),
        )

        repository.startSubscription(config, emptyList())
        advanceUntilIdle()

        assertEquals(0, api.calls)
        assertEquals(
            "No topics configured for subscription",
            repository.lastError.value?.second,
        )
        repository.close()
    }
}
