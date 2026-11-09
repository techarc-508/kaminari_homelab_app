package com.rplab.kaminarinfty.testing

import com.rplab.kaminarinfty.data.api.PublishRequest
import com.rplab.kaminarinfty.data.preferences.AppPreferences
import com.rplab.kaminarinfty.data.preferences.AppPreferencesData
import com.rplab.kaminarinfty.data.repository.ActivityRepository
import com.rplab.kaminarinfty.data.repository.CatchUpOutcome
import com.rplab.kaminarinfty.data.repository.MessageRepository
import com.rplab.kaminarinfty.data.repository.OutboxRepository
import com.rplab.kaminarinfty.data.repository.ProfileRepository
import com.rplab.kaminarinfty.data.repository.ProfileSaveResult
import com.rplab.kaminarinfty.data.repository.SendOutcome
import com.rplab.kaminarinfty.data.repository.StreamRepository
import com.rplab.kaminarinfty.delivery.DeliveryController
import com.rplab.kaminarinfty.ThemeSetting
import com.rplab.kaminarinfty.model.ActivityEvent
import com.rplab.kaminarinfty.model.ActivitySeverity
import com.rplab.kaminarinfty.model.ActivityType
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.DeliveryMode
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyErrorCategory
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.NftyTopic
import com.rplab.kaminarinfty.model.OutboxEntry
import com.rplab.kaminarinfty.model.OutboxState
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.model.ServerProfile
import com.rplab.kaminarinfty.model.StreamUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakePreferences : AppPreferences {
    val state = MutableStateFlow(AppPreferencesData())
    override val data: Flow<AppPreferencesData> = state

    override suspend fun activeProfileId(): String? = state.value.activeProfileId

    override suspend fun setTheme(theme: ThemeSetting) {
        state.update { it.copy(theme = theme) }
    }

    override suspend fun setCompact(compact: Boolean) {
        state.update { it.copy(isCompact = compact) }
    }

    override suspend fun setSampleDataEnabled(enabled: Boolean) {
        state.update { it.copy(sampleDataEnabled = enabled) }
    }

    override suspend fun setActiveProfileId(id: String) {
        state.update { it.copy(activeProfileId = id) }
    }

    override suspend fun clearActiveProfileId() {
        state.update { it.copy(activeProfileId = null) }
    }

    override suspend fun setDeliveryMode(mode: DeliveryMode) {
        state.update { it.copy(deliveryMode = mode) }
    }
}

class FakeMessageRepository : MessageRepository {
    val stored = mutableListOf<NftyMessage>()
    val readCalls = mutableListOf<Pair<String, String>>()
    val saveCalls = mutableListOf<Triple<String, String, Boolean>>()
    val deleted = mutableListOf<String>()
    val clearedTopics = mutableListOf<String>()
    var markAllReadCalls = 0
    var clearHistoryCalls = 0
    var clearSavedCalls = 0
    val flow = MutableStateFlow<List<NftyMessage>>(emptyList())

    override fun observe(profileId: String, limit: Int): Flow<List<NftyMessage>> = flow

    override suspend fun page(profileId: String, limit: Int, offset: Int): List<NftyMessage> =
        flow.value.drop(offset).take(limit)

    override suspend fun store(profileId: String, message: NftyMessage): Boolean {
        if (stored.any { it.id == message.id }) return false
        stored += message
        flow.update { listOf(message) + it }
        return true
    }

    override suspend fun exists(profileId: String, messageId: String): Boolean =
        stored.any { it.id == messageId }

    override suspend fun getById(profileId: String, messageId: String): NftyMessage? =
        stored.firstOrNull { it.id == messageId }

    override suspend fun latestCursor(profileId: String): String? =
        stored.maxByOrNull { it.time }?.id

    override suspend fun count(profileId: String): Int = stored.size

    override suspend fun markRead(profileId: String, messageId: String) {
        readCalls += profileId to messageId
    }

    override suspend fun markAllRead(profileId: String) {
        markAllReadCalls++
    }

    override suspend fun setSaved(profileId: String, messageId: String, saved: Boolean) {
        saveCalls += Triple(profileId, messageId, saved)
    }

    override suspend fun delete(profileId: String, messageId: String) {
        deleted += messageId
        stored.removeAll { it.id == messageId }
        flow.update { list -> list.filterNot { it.id == messageId } }
    }

    override suspend fun clearTopic(profileId: String, topic: String) {
        clearedTopics += topic
        stored.removeAll { it.topic == topic && !it.isSaved }
        flow.update { list -> list.filterNot { it.topic == topic && !it.isSaved } }
    }

    override suspend fun clearHistory(profileId: String) {
        clearHistoryCalls++
    }

    override suspend fun clearSaved(profileId: String) {
        clearSavedCalls++
    }
}

class FakeProfileRepository : ProfileRepository {
    var config: ServerConfig? = null
    val topicsFlow = MutableStateFlow<List<NftyTopic>>(emptyList())
    val profilesFlow = MutableStateFlow<List<ServerProfile>>(emptyList())
    val addedTopics = mutableListOf<String>()
    var subscribedNames: List<String> = emptyList()
    var activeId: String? = null
    val activeFlow = MutableStateFlow<String?>(null)
    val updatedProfiles = mutableListOf<Pair<String, ServerConfig>>()
    var onSetActive: (String) -> Unit = {}

    /** Simulates an unavailable Keystore when non-null. */
    var credentialError: String? = null

    override fun observeProfiles(): Flow<List<ServerProfile>> = profilesFlow

    override fun observeActiveProfileId(): Flow<String?> = activeFlow

    override fun observeTopics(profileId: String): Flow<List<NftyTopic>> = topicsFlow

    override suspend fun loadActiveConfig(): ServerConfig? = config

    override suspend fun loadConfig(profileId: String): ServerConfig? = config

    override suspend fun saveProfile(config: ServerConfig): ProfileSaveResult {
        this.config = config
        profilesFlow.update { existing ->
            existing.filterNot { it.id == "primary" } + ServerProfile(
                id = "primary",
                displayName = config.username.ifBlank { "ntfy" },
                baseUrl = config.serverUrl,
                authMethod = config.authMethod,
                hasStoredSecret = config.tokenOrPassword.isNotBlank(),
                createdAt = 0L,
                updatedAt = 0L,
            )
        }
        return ProfileSaveResult(
            profileId = "primary",
            credentialStored = credentialError == null,
            credentialError = credentialError,
        )
    }

    override suspend fun createProfile(config: ServerConfig, displayName: String): ProfileSaveResult {
        this.config = config
        val id = "profile-test"
        profilesFlow.update { existing ->
            existing + ServerProfile(
                id = id,
                displayName = displayName.ifBlank { config.username.ifBlank { "ntfy" } },
                baseUrl = config.serverUrl,
                authMethod = config.authMethod,
                hasStoredSecret = config.tokenOrPassword.isNotBlank(),
                createdAt = 0L,
                updatedAt = 0L,
            )
        }
        return ProfileSaveResult(
            profileId = id,
            credentialStored = credentialError == null,
            credentialError = credentialError,
        )
    }

    override suspend fun updateProfile(profileId: String, config: ServerConfig): ProfileSaveResult {
        this.config = config
        updatedProfiles += profileId to config
        return ProfileSaveResult(
            profileId = profileId,
            credentialStored = credentialError == null,
            credentialError = credentialError,
        )
    }

    override suspend fun setActiveProfileId(id: String) {
        activeId = id
        activeFlow.value = id
        onSetActive(id)
    }

    override suspend fun deleteProfile(id: String) {
        profilesFlow.update { existing -> existing.filterNot { it.id == id } }
    }

    override suspend fun addTopic(profileId: String, topic: String) {
        addedTopics += topic
        topicsFlow.update {
            it + NftyTopic(name = topic, description = "", isSubscribed = true, isPaused = false, lastActivityTime = 0L)
        }
    }

    override suspend fun setTopicSubscribed(profileId: String, topic: String, subscribed: Boolean) = Unit

    override suspend fun setTopicPaused(profileId: String, topic: String, paused: Boolean) = Unit

    override suspend fun subscribedTopicNames(profileId: String): List<String> = subscribedNames
}

class FakeStreamRepository : StreamRepository {
    private val _connectionState = MutableStateFlow(AuthState.DISCONNECTED)
    override val connectionState: StateFlow<AuthState> = _connectionState.asStateFlow()

    private val _lastError = MutableStateFlow<Pair<NftyErrorCategory, String>?>(null)
    override val lastError: StateFlow<Pair<NftyErrorCategory, String>?> = _lastError.asStateFlow()

    private val _updates = MutableSharedFlow<StreamUpdate>(extraBufferCapacity = 32)
    override val updates: SharedFlow<StreamUpdate> = _updates.asSharedFlow()

    private val _malformed = MutableStateFlow(0)
    override val malformedLineCount: StateFlow<Int> = _malformed.asStateFlow()

    private val _reconnectAttempt = MutableStateFlow(0)
    override val reconnectAttempt: StateFlow<Int> = _reconnectAttempt.asStateFlow()

    val startCalls = mutableListOf<List<String>>()
    val sinceCalls = mutableListOf<String?>()
    var lastSinceProvider: (suspend () -> String)? = null
    var stopCalls = 0
    var catchUpCalls = 0
    var catchUpSince: String? = null
    var catchUpOutcome = CatchUpOutcome()
    var testResult: Result<AuthState> = Result.success(AuthState.GUEST)

    fun emit(message: NftyMessage) {
        _updates.tryEmit(StreamUpdate.NewMessage(message))
    }

    fun emitUpdate(update: StreamUpdate) {
        _updates.tryEmit(update)
    }

    override fun testConnection(config: ServerConfig): Result<AuthState> = testResult

    override fun startSubscription(
        config: ServerConfig,
        topics: List<String>,
        sinceProvider: suspend () -> String,
    ) {
        startCalls += topics
        lastSinceProvider = sinceProvider
    }

    override suspend fun catchUp(
        config: ServerConfig,
        topics: List<String>,
        since: String,
    ): CatchUpOutcome {
        catchUpCalls++
        catchUpSince = since
        return catchUpOutcome
    }

    override fun stopSubscription() {
        stopCalls++
    }

    override fun close() = Unit
}

class FakeOutboxRepository : OutboxRepository {
    val entries = MutableStateFlow<List<OutboxEntry>>(emptyList())
    val sendOutcomes = ArrayDeque<SendOutcome>()
    val retryOutcomes = ArrayDeque<SendOutcome>()
    val sendCalls = mutableListOf<String>()
    val retryCalls = mutableListOf<Triple<String, String, Boolean>>()
    val cancelled = mutableListOf<String>()
    var enqueueResult: Result<String>? = null
    var lastRequest: PublishRequest? = null
    var sendGate: CompletableDeferred<Unit>? = null
    private var nextId = 1

    override fun observe(profileId: String, limit: Int): Flow<List<OutboxEntry>> = entries

    override suspend fun count(profileId: String): Int = entries.value.size

    override suspend fun enqueue(profileId: String, request: PublishRequest): Result<String> {
        lastRequest = request
        enqueueResult?.let { return it }
        val clientId = "client-${nextId++}"
        entries.update { current ->
            current + OutboxEntry(
                clientId = clientId,
                profileId = profileId,
                topic = request.topic,
                title = request.title,
                body = request.message,
                priority = request.priority,
                tags = request.tags,
                clickUrl = request.clickUrl,
                attachmentUrl = request.attachUrl,
                delay = request.delay,
                state = OutboxState.QUEUED,
                attemptCount = 0,
                retryable = false,
                lastError = null,
                serverMessageId = null,
                createdAt = 0L,
                updatedAt = 0L,
                acceptedAt = null,
            )
        }
        return Result.success(clientId)
    }

    override suspend fun send(profileId: String, clientId: String): SendOutcome {
        sendCalls += clientId
        sendGate?.await()
        val outcome = sendOutcomes.removeFirstOrNull() ?: SendOutcome.Accepted("server-1")
        applyOutcome(clientId, outcome)
        return outcome
    }

    override suspend fun retry(
        profileId: String,
        clientId: String,
        allowAmbiguous: Boolean,
    ): SendOutcome {
        retryCalls += Triple(profileId, clientId, allowAmbiguous)
        val outcome = retryOutcomes.removeFirstOrNull() ?: SendOutcome.Accepted("server-retry")
        applyOutcome(clientId, outcome)
        return outcome
    }

    override suspend fun cancel(profileId: String, clientId: String): Boolean {
        val entry = entries.value.firstOrNull { it.clientId == clientId } ?: return false
        if (entry.state !in listOf(OutboxState.QUEUED, OutboxState.FAILED, OutboxState.AMBIGUOUS)) {
            return false
        }
        cancelled += clientId
        entries.update { current -> current.filterNot { it.clientId == clientId } }
        return true
    }

    override suspend fun recoverStuck(profileId: String): Int = 0

    override suspend fun prune(profileId: String) = Unit

    private fun applyOutcome(clientId: String, outcome: SendOutcome) {
        val state = when (outcome) {
            is SendOutcome.Accepted -> OutboxState.ACCEPTED
            is SendOutcome.Failed -> OutboxState.FAILED
            is SendOutcome.Ambiguous -> OutboxState.AMBIGUOUS
            SendOutcome.Busy, SendOutcome.NotFound, is SendOutcome.ConfirmationRequired ->
                return
        }
        entries.update { current ->
            current.map { entry ->
                if (entry.clientId != clientId) entry
                else entry.copy(
                    state = state,
                    attemptCount = entry.attemptCount + 1,
                    retryable = (outcome as? SendOutcome.Failed)?.retryable ?: false,
                    lastError = when (outcome) {
                        is SendOutcome.Failed -> outcome.reason
                        is SendOutcome.Ambiguous -> outcome.reason
                        else -> null
                    },
                )
            }
        }
    }
}

class FakeActivityRepository : ActivityRepository {
    val events = MutableStateFlow<List<ActivityEvent>>(emptyList())
    val observedProfiles = mutableListOf<String>()
    var pruneCalls = 0

    override fun observe(profileId: String, limit: Int): Flow<List<ActivityEvent>> {
        observedProfiles += profileId
        return events
    }

    override suspend fun record(
        profileId: String,
        type: ActivityType,
        severity: ActivitySeverity,
        summary: String,
        correlationId: String?,
        messageId: String?,
        topic: String?,
        eventId: String?,
    ): Boolean {
        events.update {
            it + ActivityEvent(
                id = eventId ?: "event-${it.size + 1}",
                profileId = profileId,
                type = type,
                severity = severity,
                summary = summary,
                correlationId = correlationId,
                messageId = messageId,
                topic = topic,
                createdAt = 0L,
            )
        }
        return true
    }

    override suspend fun prune(profileId: String) {
        pruneCalls++
    }
}

class FakeDeliveryController : DeliveryController {
    var channelsEnsured = 0
    var instantStarts = 0
    var instantStops = 0
    var batterySchedules = 0
    var batteryCancels = 0
    var resumeNotices = 0
    var startResult = true
    val notified = mutableListOf<NftyMessage>()

    override fun ensureNotificationChannels() {
        channelsEnsured++
    }

    override fun startInstant(): Boolean {
        instantStarts++
        return startResult
    }

    override fun stopInstant() {
        instantStops++
    }

    override fun scheduleBatterySaver() {
        batterySchedules++
    }

    override fun cancelBatterySaver() {
        batteryCancels++
    }

    override fun postInstantResumeNotification() {
        resumeNotices++
    }

    override fun postMessageNotification(message: NftyMessage) {
        notified += message
    }
}
