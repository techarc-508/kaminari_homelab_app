package com.rplab.kaminarinfty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rplab.kaminarinfty.data.SampleDataProvider
import com.rplab.kaminarinfty.data.api.NtfyEndpoint
import com.rplab.kaminarinfty.data.preferences.AppPreferences
import com.rplab.kaminarinfty.data.repository.MessageRepository
import com.rplab.kaminarinfty.data.repository.OutboxRepository
import com.rplab.kaminarinfty.data.repository.ProfileRepository
import com.rplab.kaminarinfty.data.repository.StreamRepository
import com.rplab.kaminarinfty.delivery.DeliveryController
import com.rplab.kaminarinfty.delivery.DeliveryStatus
import com.rplab.kaminarinfty.delivery.DeliveryStatusStore
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.DeliveryMode
import com.rplab.kaminarinfty.model.NftyErrorCategory
import com.rplab.kaminarinfty.model.NftyException
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.NftyTopic
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.model.ServerProfile
import com.rplab.kaminarinfty.model.StreamUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class AppDestination {
    SETUP,
    INBOX,
    DETAIL,
    TOPICS,
    COMPOSE,
    OUTBOX,
    ACTIVITY,
    COMMANDS,
    SETTINGS,
    SEARCH,
}

enum class InboxFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    SAVED("Saved"),
    HIGH_URGENT("Urgent & High"),
    TODAY("Today"),
}

/** Editable, non-secret profile fields. The stored credential never enters UI state. */
data class ProfileEditUi(
    val profileId: String,
    val displayName: String,
    val originalUrl: String,
    val serverUrl: String,
    val authMethod: AuthMethod,
    val username: String,
    val useHttpsOnly: Boolean,
    val hasStoredSecret: Boolean,
)

enum class ThemeSetting(val label: String) {
    SYSTEM("Follow System"),
    NIGHT("Always Night"),
    DAY("Always Day"),
}

data class MainUiState(
    val currentDestination: AppDestination = AppDestination.SETUP,
    val selectedTab: AppDestination = AppDestination.INBOX,
    val serverConfig: ServerConfig = ServerConfig(serverUrl = ""),
    val authState: AuthState = AuthState.NOT_CONFIGURED,
    val isSampleData: Boolean = false,
    val sampleDataEnabled: Boolean = false,
    val messages: List<NftyMessage> = emptyList(),
    val topics: List<NftyTopic> = emptyList(),
    val profiles: List<ServerProfile> = emptyList(),
    val activeProfileId: String? = null,
    val activeFilter: InboxFilter = InboxFilter.ALL,
    val selectedTopicFilter: String? = null,
    val searchQuery: String = "",
    val selectedMessage: NftyMessage? = null,
    val errorCategory: NftyErrorCategory = NftyErrorCategory.UNREACHABLE,
    val customErrorMessage: String? = null,
    val themeSetting: ThemeSetting = ThemeSetting.SYSTEM,
    val isCompactDensity: Boolean = false,
    val malformedLines: Int = 0,
    val reconnectAttempt: Int = 0,
    val isSyncing: Boolean = false,
    val lastCursor: String? = null,
    val urlToConfirm: String? = null,
    val bannerMessage: String? = null,
    val deliveryMode: DeliveryMode = DeliveryMode.DEFAULT,
    /** Service-owned state while Instant mode is selected. */
    val deliveryStatus: DeliveryStatus = DeliveryStatus(),
    val isSwitchingProfile: Boolean = false,
    val profileEdit: ProfileEditUi? = null,
    val profileEditError: String? = null,
    /** Counts only; the pending URL/token stays private until confirmation. */
    val profileChangePreview: ProfileChangePreview? = null,
)

data class ProfileChangePreview(
    val destinationUrl: String,
    val messages: Int,
    val topics: Int,
    val outbox: Int,
)

class MainViewModel(
    private val messageRepository: MessageRepository,
    private val profileRepository: ProfileRepository,
    private val preferences: AppPreferences,
    private val streamRepository: StreamRepository,
    private val deliveryController: DeliveryController,
    private val deliveryStatusStore: DeliveryStatusStore = DeliveryStatusStore(),
    private val outboxRepository: OutboxRepository? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var activeProfileId: String? = null
    private var activeTopics: List<String> = emptyList()
    private var sampleDataEnabled = false
    private var sampleMessages: List<NftyMessage> = emptyList()
    private var dbMessages: List<NftyMessage> = emptyList()
    private var dbTopics: List<NftyTopic> = emptyList()
    private var profileJob: Job? = null
    private var preferencesLoaded = false
    private var newestStoredTime: Long = 0L
    private var lastAppliedDeliveryMode: DeliveryMode? = null
    private var lastStatusNoticeSequence: Long = 0L
    private var reapplyModeAfterSwitch = false
    private var pendingProfileConfig: ServerConfig? = null

    init {
        deliveryController.ensureNotificationChannels()
        // Set initial UI state from preferences defaults immediately.
        // This ensures the state is correct even before the preferences Flow emits.
        _uiState.update {
            it.copy(
                themeSetting = ThemeSetting.SYSTEM,
                isCompactDensity = false,
                sampleDataEnabled = false,
            )
        }
        activeProfileId = null
        sampleDataEnabled = false

        viewModelScope.launch {
            preferences.data.collect { prefs ->
                val profileChanged = prefs.activeProfileId != activeProfileId
                activeProfileId = prefs.activeProfileId
                sampleDataEnabled = prefs.sampleDataEnabled
                _uiState.update {
                    it.copy(
                        themeSetting = prefs.theme,
                        isCompactDensity = prefs.isCompact,
                        sampleDataEnabled = prefs.sampleDataEnabled,
                        activeProfileId = prefs.activeProfileId,
                        deliveryMode = prefs.deliveryMode,
                    )
                }
                if (!preferencesLoaded || profileChanged) {
                    preferencesLoaded = true
                    bindProfile(prefs.activeProfileId)
                }
                if (lastAppliedDeliveryMode != prefs.deliveryMode) {
                    lastAppliedDeliveryMode = prefs.deliveryMode
                    // Startup: bindProfile owns the foreground stream, so only the
                    // durable background engine is reconciled here.
                    applyDeliveryMode(prefs.deliveryMode, fromUser = false)
                }
                refreshDisplayedData()
            }
        }

        viewModelScope.launch {
            profileRepository.observeProfiles().collect { profiles ->
                _uiState.update { it.copy(profiles = profiles) }
            }
        }

        viewModelScope.launch {
            streamRepository.connectionState.collect { state ->
                // Instant mode is owned by the foreground service; the ViewModel
                // must not report its own stopped stream as the app's status.
                if (_uiState.value.deliveryMode == DeliveryMode.INSTANT) return@collect
                // Only update authState if there's an active profile;
                // otherwise keep NOT_CONFIGURED until a profile is bound.
                val newAuthState = if (activeProfileId != null) state else AuthState.NOT_CONFIGURED
                _uiState.update { it.copy(authState = newAuthState) }
            }
        }

        viewModelScope.launch {
            deliveryStatusStore.status.collect { status ->
                val instant = _uiState.value.deliveryMode == DeliveryMode.INSTANT
                _uiState.update { current ->
                    current.copy(
                        deliveryStatus = status,
                        authState = if (instant) status.toAuthState() else current.authState,
                    )
                }
                // Surface each terminal service event once, using the service's
                // own reason rather than a generic "offline" label.
                if (instant && status.isTerminal && status.sequence != lastStatusNoticeSequence) {
                    lastStatusNoticeSequence = status.sequence
                    _uiState.update {
                        it.copy(bannerMessage = status.detail ?: status.displayText())
                    }
                }
            }
        }

        viewModelScope.launch {
            streamRepository.lastError.collect { error ->
                error?.let { (category, message) ->
                    _uiState.update {
                        it.copy(errorCategory = category, customErrorMessage = message)
                    }
                }
            }
        }

        viewModelScope.launch {
            streamRepository.malformedLineCount.collect { count ->
                _uiState.update { it.copy(malformedLines = count) }
            }
        }

        viewModelScope.launch {
            streamRepository.reconnectAttempt.collect { attempt ->
                _uiState.update { it.copy(reconnectAttempt = attempt) }
            }
        }

        viewModelScope.launch {
            streamRepository.updates.collect { update ->
                val profileId = activeProfileId ?: return@collect
                when (update) {
                    is StreamUpdate.NewMessage -> {
                        if (update.message.isSample) return@collect
                        // Real data must never hide behind the demo dataset.
                        if (sampleDataEnabled) preferences.setSampleDataEnabled(false)
                        val inserted = messageRepository.store(profileId, update.message)
                        if (inserted) {
                            // In Instant mode the foreground service is the single
                            // notification owner; posting here would duplicate it.
                            if (_uiState.value.deliveryMode != DeliveryMode.INSTANT) {
                                deliveryController.postMessageNotification(update.message)
                            }
                            if (update.message.time >= newestStoredTime) {
                                newestStoredTime = update.message.time
                                _uiState.update { it.copy(lastCursor = update.message.id) }
                            }
                        }
                    }
                    is StreamUpdate.Removed ->
                        messageRepository.delete(profileId, update.messageId)
                    is StreamUpdate.TopicCleared -> {
                        val topic = update.topic
                        if (topic.isNullOrBlank()) {
                            // Gate finding G-07: an unscoped clear must never delete
                            // unrelated local history. Ignore it and say so.
                            _uiState.update {
                                it.copy(
                                    bannerMessage =
                                        "Ignored a topic-less server clear to protect local history",
                                )
                            }
                        } else {
                            messageRepository.clearTopic(profileId, topic)
                        }
                    }
                }
            }
        }
    }

    private fun bindProfile(profileId: String?) {
        profileJob?.cancel()
        activeTopics = emptyList()
        newestStoredTime = 0L
        if (profileId == null) {
            dbMessages = emptyList()
            dbTopics = emptyList()
            streamRepository.stopSubscription()
            deliveryController.stopInstant()
            deliveryController.cancelBatterySaver()
            _uiState.update {
                it.copy(
                    authState = AuthState.NOT_CONFIGURED,
                    currentDestination = AppDestination.SETUP,
                    lastCursor = null,
                    isSyncing = false,
                    isSwitchingProfile = false,
                    messages = emptyList(),
                    topics = emptyList(),
                    selectedMessage = null,
                )
            }
            refreshDisplayedData()
            return
        }

        profileJob = viewModelScope.launch {
            val config = profileRepository.loadConfig(profileId)
            if (config == null) {
                _uiState.update {
                    it.copy(
                        isSwitchingProfile = false,
                        authState = AuthState.NOT_CONFIGURED,
                        bannerMessage = "The selected server profile is no longer available",
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    serverConfig = config.withoutSecret(),
                    activeProfileId = profileId,
                    isSwitchingProfile = false,
                    currentDestination = if (it.currentDestination == AppDestination.SETUP) {
                        AppDestination.INBOX
                    } else {
                        it.currentDestination
                    },
                )
            }
            if (reapplyModeAfterSwitch) {
                reapplyModeAfterSwitch = false
                if (_uiState.value.deliveryMode == DeliveryMode.BATTERY_SAVER) {
                    deliveryController.scheduleBatterySaver()
                }
            }
            launch {
                messageRepository.observe(profileId).collect { rows ->
                    dbMessages = rows
                    refreshDisplayedData()
                }
            }
            launch {
                profileRepository.observeTopics(profileId).collect { topics ->
                    dbTopics = topics
                    refreshDisplayedData()
                    syncLiveStream(config)
                }
            }
            // Kick off once even if the topic flow has not emitted yet (no topics).
            syncLiveStream(config)
        }
    }

    /**
     * Reconciles the live subscription with the durable topic set.
     *
     * Runs a bounded `poll=1` catch-up from the newest stored message ID before
     * opening the live stream, so a reconnect after a long gap cannot silently
     * skip messages. No-op when the subscribed topic set is unchanged.
     */
    private suspend fun syncLiveStream(config: ServerConfig, force: Boolean = false) {
        val profileId = activeProfileId ?: return
        val subscribed = profileRepository.subscribedTopicNames(profileId).sorted()

        // Instant mode: the foreground service owns the stream (and notifications).
        // Restart it only when the durable subscription set changed.
        if (_uiState.value.deliveryMode == DeliveryMode.INSTANT) {
            if (force || subscribed != activeTopics) {
                activeTopics = subscribed
                if (subscribed.isEmpty()) {
                    deliveryController.stopInstant()
                } else if (!deliveryController.startInstant()) {
                    _uiState.update {
                        it.copy(bannerMessage = "Instant mode could not start — reopen the app and try again")
                    }
                }
            }
            return
        }

        if (!force && subscribed == activeTopics) return
        activeTopics = subscribed

        if (subscribed.isEmpty()) {
            streamRepository.stopSubscription()
            return
        }

        val cursor = messageRepository.latestCursor(profileId)
        _uiState.update { it.copy(isSyncing = true, lastCursor = cursor) }

        val since = cursor ?: NtfyEndpoint.DEFAULT_SINCE
        val outcome = withTimeoutOrNull(CATCH_UP_TIMEOUT_MS) {
            streamRepository.catchUp(config, subscribed, since)
        } ?: run {
            _uiState.update {
                it.copy(
                    isSyncing = false,
                    bannerMessage = "History sync timed out — live stream continues",
                )
            }
            null
        }

        _uiState.update { state ->
            val syncedCursor = messageRepository.latestCursor(profileId)
            state.copy(
                isSyncing = false,
                lastCursor = syncedCursor ?: state.lastCursor,
                bannerMessage = when {
                    outcome == null -> state.bannerMessage
                    outcome.failed -> "History sync incomplete — see diagnostics"
                    outcome.received > 0 -> "Synced ${outcome.received} missed notification(s)"
                    else -> state.bannerMessage
                },
            )
        }

        streamRepository.startSubscription(config, subscribed) {
            messageRepository.latestCursor(profileId) ?: NtfyEndpoint.DEFAULT_SINCE
        }
    }

    private fun refreshDisplayedData() {
        val samples = sampleDataEnabled
        if (samples && sampleMessages.isEmpty()) sampleMessages = SampleDataProvider.getSampleMessages()
        val messages = if (samples) sampleMessages else dbMessages
        val rawTopics = if (samples) SampleDataProvider.getSampleTopics() else dbTopics
        val counts = messages.groupingBy { it.topic }.eachCount()
        val topics = rawTopics.map { topic -> topic.copy(messageCount = counts[topic.name] ?: 0) }
        _uiState.update { current ->
            current.copy(
                messages = messages,
                topics = topics,
                isSampleData = samples,
                selectedMessage = current.selectedMessage?.let { selected ->
                    messages.firstOrNull { it.id == selected.id }
                },
            )
        }
    }

    // ---- Setup / connection -------------------------------------------------

    /** Persists the profile (secret via Keystore) and optionally connects. */
    fun saveConfig(config: ServerConfig, connect: Boolean) {
        val error = validate(config)
        if (error != null) {
            _uiState.update { it.copy(bannerMessage = error) }
            return
        }
        viewModelScope.launch {
            val result = profileRepository.saveProfile(config)
            if (result.credentialError != null) {
                // The profile row is saved, but it cannot authenticate: report the
                // storage failure instead of activating a broken profile (G-06).
                _uiState.update {
                    it.copy(
                        bannerMessage =
                            "Profile saved, but the credential could not be stored securely — not activated",
                        customErrorMessage = result.credentialError,
                    )
                }
                return@launch
            }
            prepareProfileSwitch()
            activeProfileId = result.profileId
            profileRepository.setActiveProfileId(result.profileId)
            _uiState.update {
                it.copy(
                    serverConfig = config.withoutSecret(),
                    customErrorMessage = null,
                    bannerMessage = if (connect) "Profile saved — connecting" else "Profile saved",
                    currentDestination = if (connect) AppDestination.INBOX else it.currentDestination,
                )
            }
            bindProfile(result.profileId)
        }
    }

    /** Saves the current form as an additional server profile and activates it. */
    fun addServerProfile(config: ServerConfig) {
        val error = validate(config)
        if (error != null) {
            _uiState.update { it.copy(bannerMessage = error) }
            return
        }
        viewModelScope.launch {
            val result = profileRepository.createProfile(config)
            if (result.credentialError != null) {
                _uiState.update {
                    it.copy(
                        bannerMessage =
                            "New profile saved, but the credential could not be stored securely — not activated",
                        customErrorMessage = result.credentialError,
                    )
                }
                return@launch
            }
            prepareProfileSwitch()
            activeProfileId = result.profileId
            profileRepository.setActiveProfileId(result.profileId)
            _uiState.update {
                it.copy(
                    serverConfig = config.withoutSecret(),
                    customErrorMessage = null,
                    currentDestination = AppDestination.INBOX,
                    bannerMessage = "New server profile added",
                )
            }
            bindProfile(result.profileId)
        }
    }

    /**
     * Safe switch: stop the old transport and background engine, clear the
     * in-memory window (never the database), then bind the new profile and
     * re-apply its saved delivery mode.
     */
    fun switchProfile(profileId: String) {
        if (profileId.isBlank() || profileId == activeProfileId) return
        viewModelScope.launch {
            val config = profileRepository.loadConfig(profileId)
            if (config == null) {
                _uiState.update {
                    it.copy(bannerMessage = "Cannot switch — that server profile is no longer available")
                }
                return@launch
            }
            prepareProfileSwitch()
            activeProfileId = profileId
            profileRepository.setActiveProfileId(profileId)
            _uiState.update {
                it.copy(
                    serverConfig = config.withoutSecret(),
                    activeProfileId = profileId,
                    bannerMessage = "Switching to ${config.serverUrl}",
                )
            }
            bindProfile(profileId)
        }
    }

    private fun prepareProfileSwitch() {
        profileJob?.cancel()
        activeTopics = emptyList()
        newestStoredTime = 0L
        reapplyModeAfterSwitch = true
        streamRepository.stopSubscription()
        deliveryController.stopInstant()
        deliveryController.cancelBatterySaver()
        dbMessages = emptyList()
        dbTopics = emptyList()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                topics = emptyList(),
                selectedMessage = null,
                activeFilter = InboxFilter.ALL,
                selectedTopicFilter = null,
                searchQuery = "",
                lastCursor = null,
                isSyncing = false,
                isSwitchingProfile = true,
            )
        }
    }

    // ---- Profile editing and URL-change preview -----------------------------

    fun startProfileEdit(profileId: String) {
        viewModelScope.launch {
            val config = profileRepository.loadConfig(profileId) ?: return@launch
            val profile = _uiState.value.profiles.firstOrNull { it.id == profileId }
            _uiState.update {
                it.copy(
                    profileEdit = ProfileEditUi(
                        profileId = profileId,
                        displayName = profile?.displayName ?: "Server profile",
                        originalUrl = config.serverUrl,
                        serverUrl = config.serverUrl,
                        authMethod = config.authMethod,
                        username = config.username,
                        useHttpsOnly = config.useHttpsOnly,
                        hasStoredSecret = profile?.hasStoredSecret == true,
                    ),
                    profileEditError = null,
                )
            }
        }
    }

    fun cancelProfileEdit() {
        pendingProfileConfig = null
        _uiState.update { it.copy(profileEdit = null, profileEditError = null, profileChangePreview = null) }
    }

    fun saveProfileEdit(
        serverUrl: String,
        authMethod: AuthMethod,
        username: String,
        newSecret: String,
        useHttpsOnly: Boolean,
    ) {
        val edit = _uiState.value.profileEdit ?: return
        val normalized = NtfyEndpoint.normalizeBaseUrl(serverUrl, useHttpsOnly).getOrElse { failure ->
            _uiState.update { it.copy(profileEditError = failure.message ?: "Invalid server URL") }
            return
        }
        if (authMethod != AuthMethod.GUEST &&
            newSecret.isBlank() &&
            !(edit.hasStoredSecret && authMethod == edit.authMethod)
        ) {
            _uiState.update {
                it.copy(profileEditError = "Enter a credential for ${authMethod.displayName}")
            }
            return
        }
        if (authMethod == AuthMethod.USERNAME_PASSWORD && username.isBlank()) {
            _uiState.update { it.copy(profileEditError = "Username is required") }
            return
        }
        val config = ServerConfig(
            serverUrl = normalized,
            authMethod = authMethod,
            username = username.trim(),
            tokenOrPassword = newSecret.trim(),
            useHttpsOnly = useHttpsOnly,
        )
        pendingProfileConfig = config
        val originalNormalized = NtfyEndpoint.normalizeBaseUrl(edit.originalUrl, edit.useHttpsOnly)
            .getOrNull()
            ?: edit.originalUrl
        if (normalized != originalNormalized) {
            viewModelScope.launch {
                val preview = ProfileChangePreview(
                    destinationUrl = normalized,
                    messages = messageRepository.count(edit.profileId),
                    topics = profileRepository.observeTopics(edit.profileId).first().size,
                    outbox = outboxRepository?.count(edit.profileId) ?: 0,
                )
                _uiState.update {
                    it.copy(profileChangePreview = preview, profileEditError = null)
                }
            }
        } else {
            applyProfileUpdate(edit.profileId, config)
        }
    }

    fun dismissProfileChangePreview() {
        pendingProfileConfig = null
        _uiState.update { it.copy(profileChangePreview = null) }
    }

    /**
     * Confirms a URL change. `createNew` is the recommended path so server data
     * can never be mixed under one profile ID.
     */
    fun confirmProfileChange(createNew: Boolean) {
        val config = pendingProfileConfig ?: return
        val edit = _uiState.value.profileEdit ?: return
        if (createNew) {
            // A blank field means "keep the existing secret", which only works for
            // in-place updates. A new profile must never inherit a credential for a
            // different server URL silently.
            if (config.authMethod != AuthMethod.GUEST && config.tokenOrPassword.isBlank()) {
                pendingProfileConfig = null
                _uiState.update {
                    it.copy(
                        profileChangePreview = null,
                        profileEditError =
                            "Enter the credential again to create a separate profile for the new URL",
                    )
                }
                return
            }
            viewModelScope.launch {
                val result = profileRepository.createProfile(config)
                if (result.credentialError != null) {
                    _uiState.update {
                        it.copy(profileEditError = result.credentialError)
                    }
                    return@launch
                }
                prepareProfileSwitch()
                activeProfileId = result.profileId
                profileRepository.setActiveProfileId(result.profileId)
                pendingProfileConfig = null
                _uiState.update {
                    it.copy(
                        serverConfig = config.withoutSecret(),
                        activeProfileId = result.profileId,
                        profileEdit = null,
                        profileEditError = null,
                        profileChangePreview = null,
                        currentDestination = AppDestination.INBOX,
                        bannerMessage = "New profile created for the changed server URL",
                    )
                }
                bindProfile(result.profileId)
            }
        } else {
            applyProfileUpdate(edit.profileId, config)
        }
    }

    private fun applyProfileUpdate(profileId: String, config: ServerConfig) {
        viewModelScope.launch {
            val result = profileRepository.updateProfile(profileId, config)
            if (result.credentialError != null) {
                _uiState.update { it.copy(profileEditError = result.credentialError) }
                return@launch
            }
            pendingProfileConfig = null
            _uiState.update {
                it.copy(
                    profileEdit = null,
                    profileEditError = null,
                    profileChangePreview = null,
                    serverConfig = config.withoutSecret(),
                    bannerMessage = "Server profile updated — existing local data was kept",
                )
            }
            if (profileId == activeProfileId) {
                val reloaded = profileRepository.loadConfig(profileId)
                if (reloaded != null) {
                    _uiState.update { it.copy(serverConfig = reloaded.withoutSecret()) }
                    syncLiveStream(reloaded, force = true)
                }
            }
        }
    }

    fun addProfile(
        serverUrl: String,
        authMethod: AuthMethod,
        username: String,
        secret: String,
        useHttpsOnly: Boolean,
    ) {
        addServerProfile(
            ServerConfig(
                serverUrl = serverUrl.trim(),
                authMethod = authMethod,
                username = username.trim(),
                tokenOrPassword = secret.trim(),
                useHttpsOnly = useHttpsOnly,
            )
        )
    }

    fun testActiveConnection() {
        viewModelScope.launch {
            val profileId = activeProfileId ?: return@launch
            val config = profileRepository.loadConfig(profileId) ?: return@launch
            testConnection(config)
        }
    }

    fun deleteServerProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.deleteProfile(profileId)
            _uiState.update { it.copy(bannerMessage = "Server profile and its local history removed") }
        }
    }

    /** Tests a transient config without persisting it. */
    fun testConnection(config: ServerConfig) {
        val error = validate(config)
        if (error != null) {
            _uiState.update { it.copy(bannerMessage = error) }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(authState = AuthState.CONNECTING, bannerMessage = "Testing ${config.serverUrl}…")
            }
            val result = streamRepository.testConnection(config)
            result.fold(
                onSuccess = { verified ->
                    _uiState.update {
                        it.copy(
                            authState = verified,
                            bannerMessage = "Server reachable (${verified.statusLabel})",
                        )
                    }
                },
                onFailure = { failure ->
                    // Gate finding G-05: keep the real cause. Only HTTP 401/403
                    // mean credentials or permissions; DNS/TLS/timeouts do not.
                    val category = (failure as? NftyException)?.category
                        ?: NftyErrorCategory.UNREACHABLE
                    val state = when (category) {
                        NftyErrorCategory.AUTH -> AuthState.AUTHENTICATION_FAILED
                        NftyErrorCategory.FORBIDDEN -> AuthState.PERMISSION_DENIED
                        else -> AuthState.DISCONNECTED
                    }
                    _uiState.update {
                        it.copy(
                            authState = state,
                            errorCategory = category,
                            customErrorMessage = failure.message,
                            bannerMessage = "Link check failed (${category.label}): ${failure.message}",
                        )
                    }
                },
            )
        }
    }

    private fun validate(config: ServerConfig): String? {
        val normalized = NtfyEndpoint.normalizeBaseUrl(config.serverUrl, config.useHttpsOnly)
        if (normalized.isFailure) {
            return normalized.exceptionOrNull()?.message ?: "Invalid server URL"
        }
        if (config.requiresCredentials() && !config.hasCredentials()) {
            return "Credentials are required for ${config.authMethod.displayName}"
        }
        return null
    }

    // ---- Delivery modes -----------------------------------------------------

    /** Switches the background delivery engine and persists the choice. */
    fun setDeliveryMode(mode: DeliveryMode) {
        if (mode == _uiState.value.deliveryMode) return
        lastAppliedDeliveryMode = mode
        val message = when (mode) {
            DeliveryMode.INSTANT -> "Instant mode on — live connection with a visible notification"
            DeliveryMode.BATTERY_SAVER -> "Battery Saver on — periodic checks, delivery is delayed"
        }
        _uiState.update { it.copy(deliveryMode = mode, bannerMessage = message) }
        viewModelScope.launch {
            preferences.setDeliveryMode(mode)
            applyDeliveryMode(mode, fromUser = true)
        }
    }

    private fun applyDeliveryMode(mode: DeliveryMode, fromUser: Boolean) {
        when (mode) {
            DeliveryMode.INSTANT -> {
                deliveryController.cancelBatterySaver()
                streamRepository.stopSubscription()
                activeTopics = emptyList()
                _uiState.update {
                    it.copy(authState = deliveryStatusStore.status.value.toAuthState())
                }
                if (fromUser && !deliveryController.startInstant()) {
                    _uiState.update {
                        it.copy(bannerMessage = "Instant mode could not start — reopen the app and try again")
                    }
                }
            }
            DeliveryMode.BATTERY_SAVER -> {
                deliveryController.stopInstant()
                deliveryController.scheduleBatterySaver()
                if (fromUser) resumeForegroundStream()
            }
        }
    }

    /** Re-opens the in-app stream after leaving Instant mode. */
    private fun resumeForegroundStream() {
        if (activeProfileId == null) return
        viewModelScope.launch {
            activeTopics = emptyList()
            syncLiveStream(_uiState.value.serverConfig, force = true)
        }
    }

    /**
     * Opens the message behind a notification. Cold starts begin with an empty
     * in-memory history, so a cache miss falls back to a direct Room lookup by ID
     * instead of reporting a false "outside the loaded window" error.
     */
    fun openMessageFromNotification(messageId: String) {
        viewModelScope.launch {
            val profileId = activeProfileId ?: preferences.data.first().activeProfileId
            if (profileId == null) {
                _uiState.update {
                    it.copy(bannerMessage = "Notification opened before a server profile was configured")
                }
                return@launch
            }
            val cached = _uiState.value.messages.firstOrNull { it.id == messageId }
            val message = cached ?: messageRepository.getById(profileId, messageId)
            if (message == null) {
                _uiState.update {
                    it.copy(bannerMessage = "Notification is no longer in local history")
                }
                return@launch
            }
            openMessageDetail(message)
        }
    }

    fun retryLiveStream() {
        _uiState.update { it.copy(customErrorMessage = null) }
        val config = _uiState.value.serverConfig
        viewModelScope.launch {
            activeTopics = emptyList()
            syncLiveStream(config, force = true)
        }
    }

    // ---- Navigation ---------------------------------------------------------

    fun navigateTo(destination: AppDestination) {
        _uiState.update { current ->
            val newTab = when (destination) {
                AppDestination.INBOX,
                AppDestination.TOPICS,
                AppDestination.COMPOSE,
                AppDestination.OUTBOX,
                AppDestination.ACTIVITY,
                AppDestination.COMMANDS,
                AppDestination.SETTINGS,
                -> destination
                else -> current.selectedTab
            }
            current.copy(currentDestination = destination, selectedTab = newTab)
        }
    }

    fun selectTab(destination: AppDestination) {
        _uiState.update { it.copy(currentDestination = destination, selectedTab = destination) }
    }

    // ---- Message operations (Room-backed unless demo mode) ------------------

    fun openMessageDetail(message: NftyMessage) {
        markAsRead(message.id)
        _uiState.update {
            it.copy(
                selectedMessage = message.copy(isRead = true),
                currentDestination = AppDestination.DETAIL,
            )
        }
    }

    fun toggleSaveMessage(id: String) {
        val current = _uiState.value.messages.firstOrNull { it.id == id } ?: return
        val profileId = activeProfileId
        if (sampleDataEnabled) {
            sampleMessages = sampleMessages.map {
                if (it.id == id) it.copy(isSaved = !it.isSaved) else it
            }
            refreshDisplayedData()
            return
        }
        if (profileId == null) return
        viewModelScope.launch {
            messageRepository.setSaved(profileId, id, !current.isSaved)
        }
    }

    fun markAsRead(id: String) {
        if (sampleDataEnabled) {
            sampleMessages = sampleMessages.map { if (it.id == id) it.copy(isRead = true) else it }
            refreshDisplayedData()
            return
        }
        val profileId = activeProfileId ?: return
        viewModelScope.launch { messageRepository.markRead(profileId, id) }
    }

    fun markAllAsRead() {
        if (sampleDataEnabled) {
            sampleMessages = sampleMessages.map { it.copy(isRead = true) }
            refreshDisplayedData()
        } else {
            val profileId = activeProfileId ?: return
            viewModelScope.launch {
                messageRepository.markAllRead(profileId)
                _uiState.update { it.copy(bannerMessage = "All notifications marked as read") }
            }
            return
        }
        _uiState.update { it.copy(bannerMessage = "All notifications marked as read") }
    }

    fun deleteMessage(id: String) {
        if (sampleDataEnabled) {
            sampleMessages = sampleMessages.filter { it.id != id }
            refreshDisplayedData()
        } else {
            val profileId = activeProfileId ?: return
            viewModelScope.launch { messageRepository.delete(profileId, id) }
        }
        _uiState.update { current ->
            val wasSelected = current.selectedMessage?.id == id
            current.copy(
                selectedMessage = if (wasSelected) null else current.selectedMessage,
                currentDestination = if (wasSelected) AppDestination.INBOX else current.currentDestination,
                bannerMessage = "Notification removed from local history",
            )
        }
    }

    fun clearHistory() {
        if (sampleDataEnabled) {
            sampleMessages = sampleMessages.filter { it.isSaved }
            refreshDisplayedData()
        } else {
            val profileId = activeProfileId ?: return
            viewModelScope.launch { messageRepository.clearHistory(profileId) }
        }
        _uiState.update {
            it.copy(bannerMessage = "Local history cleared (saved items preserved)")
        }
    }

    fun clearSaved() {
        if (sampleDataEnabled) {
            sampleMessages = sampleMessages.map { it.copy(isSaved = false) }
            refreshDisplayedData()
        } else {
            val profileId = activeProfileId ?: return
            viewModelScope.launch { messageRepository.clearSaved(profileId) }
        }
        _uiState.update { it.copy(bannerMessage = "All saved items removed") }
    }

    // ---- Filters / search ---------------------------------------------------

    fun setFilter(filter: InboxFilter) {
        _uiState.update { it.copy(activeFilter = filter) }
    }

    fun setTopicFilter(topic: String?) {
        _uiState.update { it.copy(selectedTopicFilter = topic) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    // ---- Topics -------------------------------------------------------------

    fun toggleTopicSubscription(name: String) {
        if (sampleDataEnabled) {
            _uiState.update { current ->
                current.copy(
                    topics = current.topics.map {
                        if (it.name == name) it.copy(isSubscribed = !it.isSubscribed) else it
                    },
                    bannerMessage = "Demo mode — topic changes are not persisted",
                )
            }
            return
        }
        val profileId = activeProfileId ?: return
        val subscribed = _uiState.value.topics.firstOrNull { it.name == name }?.isSubscribed ?: return
        viewModelScope.launch {
            profileRepository.setTopicSubscribed(profileId, name, !subscribed)
        }
    }

    fun toggleTopicPause(name: String) {
        if (sampleDataEnabled) {
            _uiState.update { current ->
                current.copy(
                    topics = current.topics.map {
                        if (it.name == name) it.copy(isPaused = !it.isPaused) else it
                    },
                    bannerMessage = "Demo mode — topic changes are not persisted",
                )
            }
            return
        }
        val profileId = activeProfileId ?: return
        val paused = _uiState.value.topics.firstOrNull { it.name == name }?.isPaused ?: return
        viewModelScope.launch {
            profileRepository.setTopicPaused(profileId, name, !paused)
        }
    }

    fun addTopic(name: String, description: String) {
        val sanitized = name.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (sanitized.isBlank()) {
            _uiState.update { it.copy(bannerMessage = "Topic name is empty after sanitizing") }
            return
        }
        if (sampleDataEnabled) {
            _uiState.update { it.copy(bannerMessage = "Demo mode — topic changes are not persisted") }
            return
        }
        val profileId = activeProfileId
        if (profileId == null) {
            _uiState.update { it.copy(bannerMessage = "Configure a server profile first") }
            return
        }
        viewModelScope.launch {
            if (_uiState.value.topics.any { it.name == sanitized }) {
                _uiState.update { it.copy(bannerMessage = "Topic #$sanitized is already subscribed") }
            } else {
                profileRepository.addTopic(profileId, sanitized)
                _uiState.update { it.copy(bannerMessage = "Subscribed to #$sanitized") }
            }
        }
    }

    // ---- Preferences --------------------------------------------------------

    fun setThemeSetting(theme: ThemeSetting) {
        _uiState.update { it.copy(themeSetting = theme) }
        viewModelScope.launch { preferences.setTheme(theme) }
    }

    fun toggleDensity() {
        val next = !_uiState.value.isCompactDensity
        _uiState.update { it.copy(isCompactDensity = next) }
        viewModelScope.launch { preferences.setCompact(next) }
    }

    fun setSampleDataEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setSampleDataEnabled(enabled)
            if (enabled) {
                _uiState.update {
                    it.copy(bannerMessage = "Demo mode on — sample data only, never written to history")
                }
            }
        }
    }

    // ---- Misc ---------------------------------------------------------------

    fun promptExternalUrl(url: String) {
        _uiState.update { it.copy(urlToConfirm = url) }
    }

    fun dismissUrlPrompt() {
        _uiState.update { it.copy(urlToConfirm = null) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        streamRepository.stopSubscription()
    }

    private companion object {
        const val CATCH_UP_TIMEOUT_MS = 30_000L
    }
}

class MainViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = MainViewModel(
        messageRepository = container.messageRepository,
        profileRepository = container.profileRepository,
        preferences = container.preferences,
        streamRepository = container.streamRepository,
        deliveryController = container.deliveryController,
        deliveryStatusStore = container.deliveryStatusStore,
        outboxRepository = container.outboxRepository,
    ) as T
}

/** The live configuration keeps its secret; the UI copy never does. */
private fun ServerConfig.withoutSecret(): ServerConfig = copy(tokenOrPassword = "")
