package com.rplab.kaminarinfty

import com.rplab.kaminarinfty.data.SampleDataProvider
import com.rplab.kaminarinfty.delivery.DeliveryEngine
import com.rplab.kaminarinfty.delivery.DeliveryPhase
import com.rplab.kaminarinfty.delivery.DeliveryStatusStore
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.DeliveryMode
import com.rplab.kaminarinfty.model.NftyErrorCategory
import com.rplab.kaminarinfty.model.NftyException
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.model.ServerProfile
import com.rplab.kaminarinfty.model.StreamUpdate
import com.rplab.kaminarinfty.testing.FakeDeliveryController
import com.rplab.kaminarinfty.testing.FakeMessageRepository
import com.rplab.kaminarinfty.testing.FakeOutboxRepository
import com.rplab.kaminarinfty.testing.FakePreferences
import com.rplab.kaminarinfty.testing.FakeProfileRepository
import com.rplab.kaminarinfty.testing.FakeStreamRepository
import com.rplab.kaminarinfty.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
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
class MainViewModelTest {

    @get:Rule
    val mainRule = MainDispatcherRule()

    private lateinit var preferences: FakePreferences
    private lateinit var messages: FakeMessageRepository
    private lateinit var profiles: FakeProfileRepository
    private lateinit var stream: FakeStreamRepository
    private lateinit var delivery: FakeDeliveryController
    private lateinit var deliveryStatus: DeliveryStatusStore

    @Before
    fun setUp() {
        preferences = FakePreferences()
        messages = FakeMessageRepository()
        profiles = FakeProfileRepository()
        stream = FakeStreamRepository()
        delivery = FakeDeliveryController()
        deliveryStatus = DeliveryStatusStore()
        profiles.onSetActive = { id ->
            preferences.state.value = preferences.state.value.copy(activeProfileId = id)
        }
    }

    private fun buildViewModel(
        outbox: FakeOutboxRepository? = null,
    ) = MainViewModel(
        messages, profiles, preferences, stream, delivery, deliveryStatus, outbox,
    )

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
    fun `HTTP 401 probe failure surfaces AUTHENTICATION_FAILED`() = runTest(mainRule.dispatcher) {
        stream.testResult = Result.failure(
            NftyException(NftyErrorCategory.AUTH, "HTTP 401: invalid credentials")
        )
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.testConnection(ServerConfig(serverUrl = "https://ntfy.test"))
        advanceUntilIdle()

        assertEquals(AuthState.AUTHENTICATION_FAILED, vm.uiState.value.authState)
        assertEquals(NftyErrorCategory.AUTH, vm.uiState.value.errorCategory)
        assertTrue(vm.uiState.value.bannerMessage.orEmpty().contains("HTTP 401"))
    }

    @Test
    fun `DNS probe failure is not reported as an authentication failure`() = runTest(mainRule.dispatcher) {
        stream.testResult = Result.failure(
            NftyException(NftyErrorCategory.DNS, "DNS resolution failed for server host")
        )
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.testConnection(ServerConfig(serverUrl = "https://ntfy.test"))
        advanceUntilIdle()

        assertEquals(AuthState.DISCONNECTED, vm.uiState.value.authState)
        assertEquals(NftyErrorCategory.DNS, vm.uiState.value.errorCategory)
        assertTrue(vm.uiState.value.bannerMessage.orEmpty().contains("DNS"))
    }

    @Test
    fun `TLS probe failure keeps the TLS category`() = runTest(mainRule.dispatcher) {
        stream.testResult = Result.failure(
            NftyException(NftyErrorCategory.TLS, "TLS handshake failed (certificate verification)")
        )
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.testConnection(ServerConfig(serverUrl = "https://ntfy.test"))
        advanceUntilIdle()

        assertEquals(AuthState.DISCONNECTED, vm.uiState.value.authState)
        assertEquals(NftyErrorCategory.TLS, vm.uiState.value.errorCategory)
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

    @Test
    fun `switching profile stops delivery and keeps stored history`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        messages.stored += liveMessage("keep-me")
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.switchProfile("profile-test")
        advanceUntilIdle()

        assertEquals("profile-test", preferences.state.value.activeProfileId)
        assertEquals("profile-test", vm.uiState.value.activeProfileId)
        assertTrue(delivery.instantStops >= 1)
        assertTrue(delivery.batteryCancels >= 1)
        assertEquals(0, messages.clearHistoryCalls)
        assertTrue(messages.stored.any { it.id == "keep-me" })
    }

    @Test
    fun `URL change preview reports profile-scoped counts before saving`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        profiles.profilesFlow.value = listOf(
            ServerProfile(
                id = "primary",
                displayName = "home",
                baseUrl = "https://ntfy.test",
                authMethod = AuthMethod.GUEST,
                hasStoredSecret = false,
                createdAt = 0L,
                updatedAt = 0L,
            )
        )
        profiles.topicsFlow.value = listOf(
            com.rplab.kaminarinfty.model.NftyTopic(name = "ops", description = "")
        )
        messages.stored += liveMessage("one")
        messages.stored += liveMessage("two")
        val outbox = FakeOutboxRepository().apply {
            entries.value = listOf(
                com.rplab.kaminarinfty.model.OutboxEntry(
                    clientId = "a", profileId = "primary", topic = "ops", title = null,
                    body = "x", priority = 3, tags = emptyList(), clickUrl = null,
                    attachmentUrl = null, delay = null,
                    state = com.rplab.kaminarinfty.model.OutboxState.QUEUED,
                    attemptCount = 0, retryable = false, lastError = null,
                    serverMessageId = null, createdAt = 0L, updatedAt = 0L,
                    acceptedAt = null,
                ),
                com.rplab.kaminarinfty.model.OutboxEntry(
                    clientId = "b", profileId = "primary", topic = "ops", title = null,
                    body = "y", priority = 3, tags = emptyList(), clickUrl = null,
                    attachmentUrl = null, delay = null,
                    state = com.rplab.kaminarinfty.model.OutboxState.FAILED,
                    attemptCount = 1, retryable = true, lastError = "no",
                    serverMessageId = null, createdAt = 0L, updatedAt = 0L,
                    acceptedAt = null,
                ),
            )
        }
        val vm = buildViewModel(outbox)
        advanceUntilIdle()

        vm.startProfileEdit("primary")
        advanceUntilIdle()
        vm.saveProfileEdit(
            serverUrl = "https://new.example.test",
            authMethod = AuthMethod.GUEST,
            username = "",
            newSecret = "",
            useHttpsOnly = true,
        )
        advanceUntilIdle()

        val preview = vm.uiState.value.profileChangePreview
        assertEquals("https://new.example.test", preview?.destinationUrl)
        assertEquals(2, preview?.messages)
        assertEquals(1, preview?.topics)
        assertEquals(2, preview?.outbox)
        assertTrue(profiles.updatedProfiles.isEmpty())

        vm.confirmProfileChange(createNew = false)
        advanceUntilIdle()

        assertEquals(1, profiles.updatedProfiles.size)
        assertEquals("primary", profiles.updatedProfiles.single().first)
        assertEquals(2, messages.stored.size)
        assertTrue(messages.clearHistoryCalls == 0)
    }

    @Test
    fun `creating a profile for a new URL never copies a stored secret implicitly`() =
        runTest(mainRule.dispatcher) {
            preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
            profiles.config = ServerConfig(
                serverUrl = "https://ntfy.test",
                authMethod = AuthMethod.ACCESS_TOKEN,
                tokenOrPassword = "old-secret",
            )
            profiles.profilesFlow.value = listOf(
                ServerProfile(
                    id = "primary",
                    displayName = "home",
                    baseUrl = "https://ntfy.test",
                    authMethod = AuthMethod.ACCESS_TOKEN,
                    hasStoredSecret = true,
                    createdAt = 0L,
                    updatedAt = 0L,
                )
            )
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.startProfileEdit("primary")
            advanceUntilIdle()
            vm.saveProfileEdit(
                serverUrl = "https://new.example.test",
                authMethod = AuthMethod.ACCESS_TOKEN,
                username = "",
                newSecret = "",
                useHttpsOnly = true,
            )
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.profileChangePreview)

            vm.confirmProfileChange(createNew = true)
            advanceUntilIdle()

            assertNull(vm.uiState.value.profileChangePreview)
            assertTrue(vm.uiState.value.profileEditError.orEmpty().contains("Enter the credential"))
            assertEquals("https://ntfy.test", profiles.config?.serverUrl)
            assertEquals("old-secret", profiles.config?.tokenOrPassword)
            assertEquals("primary", preferences.state.value.activeProfileId)
        }

    @Test
    fun `a topic-less server clear never wipes local history`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        val vm = buildViewModel()
        advanceUntilIdle()

        stream.emitUpdate(StreamUpdate.TopicCleared(null))
        advanceUntilIdle()

        assertEquals(0, messages.clearHistoryCalls)
        assertTrue(messages.clearedTopics.isEmpty())
        assertTrue(vm.uiState.value.bannerMessage.orEmpty().contains("topic-less"))
    }

    @Test
    fun `a credential storage failure is surfaced and the profile is not activated`() =
        runTest(mainRule.dispatcher) {
            profiles.credentialError = "Secure credential storage is unavailable"
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

            assertNull(preferences.state.value.activeProfileId)
            assertTrue(stream.startCalls.isEmpty())
            assertTrue(vm.uiState.value.bannerMessage.orEmpty().contains("credential"))
            assertEquals(
                "Secure credential storage is unavailable",
                vm.uiState.value.customErrorMessage,
            )
        }

    // ---- Phase 3: delivery modes and notifications --------------------------

    @Test
    fun `delivery mode defaults to battery saver and channels are created`() = runTest(mainRule.dispatcher) {
        val vm = buildViewModel()
        advanceUntilIdle()

        assertEquals(DeliveryMode.BATTERY_SAVER, vm.uiState.value.deliveryMode)
        assertEquals(DeliveryMode.BATTERY_SAVER, preferences.state.value.deliveryMode)
        assertEquals(1, delivery.channelsEnsured)
    }

    @Test
    fun `instant mode stops the in-app stream and starts the foreground service`() =
        runTest(mainRule.dispatcher) {
            preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
            profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
            profiles.subscribedNames = listOf("ops")
            val vm = buildViewModel()
            advanceUntilIdle()
            val startsBefore = stream.startCalls.size
            val stopsBefore = stream.stopCalls

            vm.setDeliveryMode(DeliveryMode.INSTANT)
            advanceUntilIdle()

            assertEquals(DeliveryMode.INSTANT, preferences.state.value.deliveryMode)
            assertEquals(DeliveryMode.INSTANT, vm.uiState.value.deliveryMode)
            assertTrue(delivery.instantStarts >= 1)
            assertTrue(stream.stopCalls > stopsBefore)
            assertEquals(startsBefore, stream.startCalls.size)
            assertTrue(delivery.batteryCancels >= 1)
        }

    @Test
    fun `battery saver mode schedules periodic work and resumes the in-app stream`() =
        runTest(mainRule.dispatcher) {
            preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
            profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
            profiles.subscribedNames = listOf("ops")
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.setDeliveryMode(DeliveryMode.BATTERY_SAVER)
            advanceUntilIdle()

            assertEquals(DeliveryMode.BATTERY_SAVER, preferences.state.value.deliveryMode)
            assertTrue(delivery.batterySchedules >= 1)
            assertTrue(delivery.instantStops >= 1)
            assertTrue(stream.startCalls.isNotEmpty())
        }

    @Test
    fun `instant mode at startup starts the service without opening an in-app stream`() =
        runTest(mainRule.dispatcher) {
            preferences.state.value = preferences.state.value.copy(
                activeProfileId = "primary",
                deliveryMode = DeliveryMode.INSTANT,
            )
            profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
            profiles.subscribedNames = listOf("ops")

            val vm = buildViewModel()
            advanceUntilIdle()

            assertEquals(DeliveryMode.INSTANT, vm.uiState.value.deliveryMode)
            assertTrue(delivery.instantStarts >= 1)
            assertTrue(stream.startCalls.isEmpty())
        }

    @Test
    fun `received messages post notifications outside instant mode`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        val vm = buildViewModel()
        advanceUntilIdle()

        stream.emit(liveMessage())
        advanceUntilIdle()

        assertEquals(listOf("live_1"), delivery.notified.map { it.id })
        assertEquals(DeliveryMode.BATTERY_SAVER, vm.uiState.value.deliveryMode)
    }

    @Test
    fun `instant mode never double-posts notifications from the view model`() =
        runTest(mainRule.dispatcher) {
            preferences.state.value = preferences.state.value.copy(
                activeProfileId = "primary",
                deliveryMode = DeliveryMode.INSTANT,
            )
            profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
            profiles.subscribedNames = listOf("ops")
            val vm = buildViewModel()
            advanceUntilIdle()

            stream.emit(liveMessage())
            advanceUntilIdle()

            assertTrue(delivery.instantStarts >= 1)
            assertTrue(delivery.notified.isEmpty())
        }

    @Test
    fun `a failed instant start is surfaced instead of claiming success`() =
        runTest(mainRule.dispatcher) {
            delivery.startResult = false
            preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
            profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)

            val vm = buildViewModel()
            advanceUntilIdle()

            vm.setDeliveryMode(DeliveryMode.INSTANT)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.bannerMessage.orEmpty().contains("could not start"))
        }

    @Test
    fun `notification deep link opens the matching message detail`() = runTest(mainRule.dispatcher) {
        preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
        profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
        messages.flow.value = listOf(liveMessage())
        val vm = buildViewModel()
        advanceUntilIdle()

        vm.openMessageFromNotification("live_1")
        advanceUntilIdle()

        assertEquals(AppDestination.DETAIL, vm.uiState.value.currentDestination)
        assertEquals("live_1", vm.uiState.value.selectedMessage?.id)
    }

    @Test
    fun `unknown notification deep link reports instead of opening an empty detail`() =
        runTest(mainRule.dispatcher) {
            preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
            profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
            val vm = buildViewModel()
            advanceUntilIdle()

            vm.openMessageFromNotification("missing")
            advanceUntilIdle()

            assertTrue(
                vm.uiState.value.bannerMessage.orEmpty().contains("no longer in local history")
            )
            assertEquals(AppDestination.INBOX, vm.uiState.value.currentDestination)
        }

    @Test
    fun `cold start deep link loads a persisted message outside the in-memory window`() =
        runTest(mainRule.dispatcher) {
            preferences.state.value = preferences.state.value.copy(activeProfileId = "primary")
            profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
            val target = liveMessage(id = "cold_target")
            messages.stored += target
            // Simulate a cold start: Room has the row, the displayed window is empty.
            messages.flow.value = emptyList()

            val vm = buildViewModel()
            advanceUntilIdle()
            assertTrue(vm.uiState.value.messages.isEmpty())

            vm.openMessageFromNotification("cold_target")
            advanceUntilIdle()

            assertEquals(AppDestination.DETAIL, vm.uiState.value.currentDestination)
            assertEquals("cold_target", vm.uiState.value.selectedMessage?.id)
        }

    @Test
    fun `instant mode reflects live service state without opening a second stream`() =
        runTest(mainRule.dispatcher) {
            preferences.state.value = preferences.state.value.copy(
                activeProfileId = "primary",
                deliveryMode = DeliveryMode.INSTANT,
            )
            profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
            profiles.subscribedNames = listOf("ops")
            val vm = buildViewModel()
            advanceUntilIdle()

            deliveryStatus.publish(
                engine = DeliveryEngine.INSTANT,
                phase = DeliveryPhase.LIVE,
                topicCount = 1,
                authState = AuthState.AUTHENTICATED,
            )
            advanceUntilIdle()

            assertEquals(AuthState.AUTHENTICATED, vm.uiState.value.authState)
            assertEquals(DeliveryPhase.LIVE, vm.uiState.value.deliveryStatus.phase)
            assertEquals(0, stream.startCalls.size)
        }

    @Test
    fun `terminal service state is surfaced with the service's own reason`() =
        runTest(mainRule.dispatcher) {
            preferences.state.value = preferences.state.value.copy(
                activeProfileId = "primary",
                deliveryMode = DeliveryMode.INSTANT,
            )
            profiles.config = ServerConfig(serverUrl = "https://ntfy.test", authMethod = AuthMethod.GUEST)
            profiles.subscribedNames = listOf("ops")
            val vm = buildViewModel()
            advanceUntilIdle()

            deliveryStatus.publish(
                engine = DeliveryEngine.INSTANT,
                phase = DeliveryPhase.TIMED_OUT,
                detail = "Android reached its 6-hour dataSync limit",
            )
            advanceUntilIdle()

            assertEquals(AuthState.DISCONNECTED, vm.uiState.value.authState)
            assertEquals(
                "Android reached its 6-hour dataSync limit",
                vm.uiState.value.bannerMessage,
            )
        }
}
