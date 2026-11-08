package com.rplab.kaminarinfty

import com.rplab.kaminarinfty.data.SampleDataProvider
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.model.StreamUpdate
import com.rplab.kaminarinfty.testing.FakeMessageRepository
import com.rplab.kaminarinfty.testing.FakePreferences
import com.rplab.kaminarinfty.testing.FakeProfileRepository
import com.rplab.kaminarinfty.testing.FakeStreamRepository
import com.rplab.kaminarinfty.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var preferences: FakePreferences
    private lateinit var messages: FakeMessageRepository
    private lateinit var profiles: FakeProfileRepository
    private lateinit var stream: FakeStreamRepository

    @Before
    fun setUp() {
        preferences = FakePreferences()
        messages = FakeMessageRepository()
        profiles = FakeProfileRepository()
        stream = FakeStreamRepository()
        profiles.onSetActive = { id ->
            preferences.state.value = preferences.state.value.copy(activeProfileId = id)
        }
    }

    private fun buildViewModel() = MainViewModel(messages, profiles, preferences, stream)

    private fun liveMessage(id: String = "live_1") = NftyMessage(
        id = id,
        time = 1_700_000_000_000,
        topic = "ops",
        title = "Live event",
        message = "real server data",
        isSample = false,
    )

    @Test
    fun `first run without profile starts in setup with no sample data`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(AppDestination.SETUP, vm.uiState.value.currentDestination)
        assertTrue(vm.uiState.value.messages.isEmpty())
        assertFalse(vm.uiState.value.isSampleData)
        assertFalse(vm.uiState.value.sampleDataEnabled)
        assertEquals(AuthState.NOT_CONFIGURED, vm.uiState.value.authState)
    }

    @Test
    fun `demo mode shows sample data without writing to the database`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setSampleDataEnabled(true)
        advanceUntilIdle()

        assertEquals(SampleDataProvider.getSampleMessages().size, vm.uiState.value.messages.size)
        assertTrue(vm.uiState.value.isSampleData)
        assertTrue(messages.stored.isEmpty())
    }

    @Test
    fun `a real incoming message disables demo mode and persists to Room`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.setSampleDataEnabled(true)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.isSampleData)

        stream.emit(liveMessage())
        advanceUntilIdle()

        assertFalse(preferences.state.value.sampleDataEnabled)
        assertEquals(1, messages.stored.size)
        assertEquals("real server data", messages.stored.first().message)
    }

    @Test
    fun `save and delete are persisted for the active profile`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        messages.flow.value = listOf(liveMessage())
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.toggleSaveMessage("live_1")
        vm.deleteMessage("live_1")
        advanceUntilIdle()

        assertEquals(Triple("primary", "live_1", true), messages.saveCalls.single())
        assertEquals(listOf("live_1"), messages.deleted)
    }

    @Test
    fun `mark all read reaches the repository`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.markAllAsRead()
        advanceUntilIdle()

        assertEquals(1, messages.markAllReadCalls)
    }

    @Test
    fun `saveConfig persists profile then starts the stream for subscribed topics`() = runTest(mainRule.dispatcher) {
        profiles.subscribedNames = listOf("ops")
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.saveConfig(
            ServerConfig(
                serverUrl = "https://nfty.example.test",
                authMethod = AuthMethod.ACCESS_TOKEN,
                tokenOrPassword = "tk_secret",
            ),
            connect = true,
        )
        advanceUntilIdle()

        assertEquals("primary", preferences.state.value.activeProfileId)
        assertEquals("https://nfty.example.test", profiles.config?.serverUrl)
        assertEquals(listOf(listOf("ops")), stream.startCalls)
        assertEquals(AppDestination.INBOX, vm.uiState.value.currentDestination)
    }

    @Test
    fun `saveConfig rejects invalid URLs before persisting`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.saveConfig(ServerConfig(serverUrl = "nfty.example.test"), connect = true)
        advanceUntilIdle()

        assertEquals(null, profiles.config)
        assertTrue(vm.uiState.value.bannerMessage.orEmpty().contains("https://"))
    }

    @Test
    fun `failed connection test surfaces AUTHENTICATION_FAILED`() = runTest(mainRule.dispatcher) {
        stream.testResult = Result.failure(IOException("HTTP 401: invalid credentials"))
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.testConnection(ServerConfig(serverUrl = "https://ntfy.test"))
        advanceUntilIdle()

        assertEquals(AuthState.AUTHENTICATION_FAILED, vm.uiState.value.authState)
        assertTrue(vm.uiState.value.bannerMessage.orEmpty().contains("failed"))
    }

    @Test
    fun `demo topic changes are not persisted`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()
        vm.setSampleDataEnabled(true)
        advanceUntilIdle()

        vm.addTopic("Homelab Alerts!", "")
        advanceUntilIdle()

        assertTrue(profiles.addedTopics.isEmpty())
        assertTrue(vm.uiState.value.bannerMessage.orEmpty().contains("Demo mode"))
    }

    @Test
    fun `server message_delete removes the stored message`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        val vm = buildViewModel()
        advanceUntilIdle()

        stream.emitUpdate(StreamUpdate.Removed("live_1"))
        advanceUntilIdle()

        assertEquals(listOf("live_1"), messages.deleted)
    }

    @Test
    fun `server message_clear clears the topic locally`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        val vm = buildViewModel()
        advanceUntilIdle()

        stream.emitUpdate(StreamUpdate.TopicCleared("ops"))
        advanceUntilIdle()

        assertEquals(listOf("ops"), messages.clearedTopics)
    }

    @Test
    fun `replayed message id is persisted once`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        val vm = buildViewModel()
        advanceUntilIdle()

        stream.emit(liveMessage("dupe"))
        stream.emit(liveMessage("dupe"))
        advanceUntilIdle()

        assertEquals(1, messages.stored.size)
    }

    @Test
    fun `subscription cursor provider reads the newest durable message id`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        profiles.subscribedNames = listOf("ops")
        val vm = buildViewModel()
        advanceUntilIdle()

        stream.emit(liveMessage("live_9"))
        advanceUntilIdle()

        val cursor = stream.lastSinceProvider?.invoke()
        assertEquals("live_9", cursor)
    }

    @Test
    fun `adding a second profile activates it`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.addServerProfile(
            ServerConfig(
                serverUrl = "https://work.example.test",
                authMethod = AuthMethod.ACCESS_TOKEN,
                tokenOrPassword = "tk_work",
            )
        )
        advanceUntilIdle()

        assertEquals("profile-test", preferences.state.value.activeProfileId)
        assertEquals("https://work.example.test", vm.uiState.value.serverConfig.serverUrl)
        assertEquals("profile-test", vm.uiState.value.activeProfileId)
    }

    @Test
    fun `switching to another profile rebinds the connection`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        profiles.subscribedNames = listOf("ops")
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.switchProfile("profile-test")
        advanceUntilIdle()

        assertEquals("profile-test", preferences.state.value.activeProfileId)
        assertEquals("profile-test", vm.uiState.value.activeProfileId)
    }
}
