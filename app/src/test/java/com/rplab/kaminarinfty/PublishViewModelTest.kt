package com.rplab.kaminarinfty

import com.rplab.kaminarinfty.data.repository.SendOutcome
import com.rplab.kaminarinfty.model.NftyTopic
import com.rplab.kaminarinfty.model.OutboxEntry
import com.rplab.kaminarinfty.model.OutboxState
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.testing.FakeOutboxRepository
import com.rplab.kaminarinfty.testing.FakeProfileRepository
import com.rplab.kaminarinfty.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PublishViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var outbox: FakeOutboxRepository
    private lateinit var profiles: FakeProfileRepository

    @Before
    fun setUp() {
        outbox = FakeOutboxRepository()
        profiles = FakeProfileRepository().apply {
            activeFlow.value = "primary"
            config = ServerConfig(serverUrl = "https://ntfy.test")
            topicsFlow.value = listOf(
                NftyTopic(name = "ops", description = "", isSubscribed = true),
                NftyTopic(name = "backups", description = "", isSubscribed = true),
            )
        }
    }

    private fun buildViewModel() = PublishViewModel(outbox, profiles)

    private fun PublishViewModel.fillValidDraft() {
        setTopic("ops")
        setTitle("Alert")
        setBody("Disk almost full")
        setPriority(4)
        setTags("warning, disk")
    }

    @Test
    fun `blank message is rejected before any enqueue`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.setTopic("ops")
        vm.setBody("")

        vm.send()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.validationError.orEmpty().contains("blank"))
        assertTrue(outbox.sendCalls.isEmpty())
        assertNull(outbox.lastRequest)
    }

    @Test
    fun `invalid click url is rejected before the network`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.fillValidDraft()
        vm.setClickUrl("not-a-url")

        vm.send()
        advanceUntilIdle()

        assertTrue(vm.uiState.value.validationError.orEmpty().contains("Click URL"))
        assertTrue(outbox.sendCalls.isEmpty())
    }

    @Test
    fun `successful send shows server acceptance and clears the draft`() = runTest(mainRule.dispatcher) {
        outbox.sendOutcomes.add(SendOutcome.Accepted("srv-42"))
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.fillValidDraft()

        vm.send()
        advanceUntilIdle()

        val outcome = vm.uiState.value.outcome
        assertTrue(outcome is PublishOutcome.Accepted)
        assertEquals("srv-42", (outcome as PublishOutcome.Accepted).serverMessageId)
        assertEquals("", vm.uiState.value.form.body)
        assertEquals("ops", vm.uiState.value.form.topic)
        assertEquals(1, outbox.sendCalls.size)
        assertEquals("Disk almost full", outbox.lastRequest?.message)
        assertEquals(OutboxState.ACCEPTED, outbox.entries.value.single().state)
    }

    @Test
    fun `failed send preserves the draft and exposes retryability`() = runTest(mainRule.dispatcher) {
        outbox.sendOutcomes.add(SendOutcome.Failed("Server error (503)", retryable = true))
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.fillValidDraft()

        vm.send()
        advanceUntilIdle()

        val outcome = vm.uiState.value.outcome as PublishOutcome.Failed
        assertEquals("Server error (503)", outcome.reason)
        assertTrue(outcome.retryable)
        assertEquals("Disk almost full", vm.uiState.value.form.body)
        assertEquals("Alert", vm.uiState.value.form.title)
    }

    @Test
    fun `ambiguous send requires explicit confirmation before retry`() = runTest(mainRule.dispatcher) {
        outbox.sendOutcomes.add(SendOutcome.Ambiguous("Timeout after sending"))
        outbox.retryOutcomes.add(SendOutcome.Accepted("srv-retry"))
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.fillValidDraft()

        vm.send()
        advanceUntilIdle()
        val clientId = (vm.uiState.value.outcome as PublishOutcome.Ambiguous).clientId

        vm.retryLast()
        advanceUntilIdle()
        assertEquals(clientId, vm.uiState.value.pendingAmbiguousRetry)
        assertTrue(outbox.retryCalls.isEmpty())

        vm.confirmAmbiguousRetry()
        advanceUntilIdle()

        assertEquals(1, outbox.retryCalls.size)
        assertEquals(Triple("primary", clientId, true), outbox.retryCalls.single())
        assertTrue(vm.uiState.value.outcome is PublishOutcome.Accepted)
        assertNull(vm.uiState.value.pendingAmbiguousRetry)
    }

    @Test
    fun `double tap publishes once while the first send is in flight`() = runTest(mainRule.dispatcher) {
        val gate = CompletableDeferred<Unit>()
        outbox.sendGate = gate
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.fillValidDraft()

        vm.send()
        vm.send()
        runCurrent()

        assertTrue(vm.uiState.value.isSending)
        assertEquals(1, outbox.sendCalls.size)

        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, outbox.sendCalls.size)
        assertFalse(vm.uiState.value.isSending)
    }

    @Test
    fun `in-flight send cannot leak its result into a newly selected profile`() =
        runTest(mainRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            outbox.sendGate = gate
            outbox.sendOutcomes.add(SendOutcome.Accepted("old-profile-server"))
            val vm = buildViewModel()
            advanceUntilIdle()
            vm.fillValidDraft()

            vm.send()
            runCurrent()
            assertEquals(1, outbox.sendCalls.size)

            profiles.activeFlow.value = "secondary"
            runCurrent()
            assertEquals("secondary", vm.uiState.value.activeProfileId)

            gate.complete(Unit)
            advanceUntilIdle()

            assertNull(vm.uiState.value.outcome)
            assertEquals("secondary", vm.uiState.value.activeProfileId)
            assertEquals(1, outbox.sendCalls.size)
            vm.retryLast()
            advanceUntilIdle()
            assertEquals(1, outbox.sendCalls.size)
        }

    @Test
    fun `outbox ambiguous row also asks before retry`() = runTest(mainRule.dispatcher) {
        outbox.entries.value = listOf(ambiguousEntry())
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.retryEntry("client-amb")
        advanceUntilIdle()

        assertEquals("client-amb", vm.uiState.value.pendingAmbiguousRetry)
        assertTrue(outbox.retryCalls.isEmpty())
    }

    @Test
    fun `cancel only removes unsent rows and reports success`() = runTest(mainRule.dispatcher) {
        outbox.entries.value = listOf(ambiguousEntry())
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.cancelEntry("client-amb")
        advanceUntilIdle()

        assertTrue(outbox.cancelled.contains("client-amb"))
        assertTrue(vm.uiState.value.notice.orEmpty().contains("removed"))
    }

    @Test
    fun `active profile switch clears the compose draft`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.fillValidDraft()
        assertNotNull(vm.uiState.value.form.body.takeIf { it.isNotBlank() })

        profiles.activeFlow.value = "secondary"
        advanceUntilIdle()

        assertEquals("secondary", vm.uiState.value.activeProfileId)
        assertEquals("", vm.uiState.value.form.body)
    }

    private fun ambiguousEntry() = OutboxEntry(
        clientId = "client-amb",
        profileId = "primary",
        topic = "ops",
        title = null,
        body = "uncertain",
        priority = 3,
        tags = emptyList(),
        clickUrl = null,
        attachmentUrl = null,
        delay = null,
        state = OutboxState.AMBIGUOUS,
        attemptCount = 1,
        retryable = true,
        lastError = "Timeout",
        serverMessageId = null,
        createdAt = 0L,
        updatedAt = 0L,
        acceptedAt = null,
    )
}
