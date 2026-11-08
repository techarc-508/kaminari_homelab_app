package com.rplab.kaminarinfty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rplab.kaminarinfty.data.SampleDataProvider
import com.rplab.kaminarinfty.data.api.NtfyEndpoint
import com.rplab.kaminarinfty.data.preferences.AppPreferences
import com.rplab.kaminarinfty.data.repository.MessageRepository
import com.rplab.kaminarinfty.data.repository.ProfileRepository
import com.rplab.kaminarinfty.data.repository.StreamRepository
import com.rplab.kaminarinfty.model.AuthState
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

enum class AppDestination {
    SETUP,
    INBOX,
    DETAIL,
    TOPICS,
    SAVED,
    SETTINGS,
    SEARCH,
}

enum class InboxFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    HIGH_URGENT("Urgent & High"),
    TODAY("Today"),
}

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
)

class MainViewModel(
    private val messageRepository: MessageRepository,
    private val profileRepository: ProfileRepository,
    private val preferences: AppPreferences,
    private val streamRepository: StreamRepository,
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

    init {
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
                    )
                }
                if (!preferencesLoaded || profileChanged) {
                    preferencesLoaded = true
                    bindProfile(prefs.activeProfileId)
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
                // Only update authState if there's an active profile;
                // otherwise keep NOT_CONFIGURED until a profile is bound.
                val newAuthState = if (activeProfileId != null) state else AuthState.NOT_CONFIGURED
                _uiState.update { it.copy(authState = newAuthState) }
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
                        if (inserted && update.message.time >= newestStoredTime) {
                            newestStoredTime = update.message.time
                            _uiState.update { it.copy(lastCursor = update.message.id) }
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
            _uiState.update {
                it.copy(
                    authState = AuthState.NOT_CONFIGURED,
                    currentDestination = AppDestination.SETUP,
                    lastCursor = null,
                    isSyncing = false,
                )
            }
            refreshDisplayedData()
            return
        }

        profileJob = viewModelScope.launch {
            val config = profileRepository.loadConfig(profileId) ?: return@launch
            _uiState.update {
                it.copy(
                    serverConfig = config,
                    activeProfileId = profileId,
                    currentDestination = if (it.currentDestination == AppDestination.SETUP) {
                        AppDestination.INBOX
                    } else {
                        it.currentDestination
                    },
                )
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
        val topics = if (samples) SampleDataProvider.getSampleTopics() else dbTopics
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
            profileRepository.setActiveProfileId(result.profileId)
            activeProfileId = result.profileId
            newestStoredTime = 0L
            activeTopics = emptyList()
            _uiState.update {
                it.copy(
                    serverConfig = config,
                    activeProfileId = result.profileId,
                    customErrorMessage = null,
                    bannerMessage = if (connect) "Profile saved — connecting" else "Profile saved",
                    currentDestination = if (connect) AppDestination.INBOX else it.currentDestination,
                )
            }
            if (connect) {
                syncLiveStream(config, force = true)
            }
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
            profileRepository.setActiveProfileId(result.profileId)
            activeProfileId = result.profileId
            activeTopics = emptyList()
            newestStoredTime = 0L
            _uiState.update {
                it.copy(
                    serverConfig = config,
                    activeProfileId = result.profileId,
                    customErrorMessage = null,
                    currentDestination = AppDestination.INBOX,
                    bannerMessage = "New server profile added",
                )
            }
        }
    }

    fun switchProfile(profileId: String) {
        viewModelScope.launch {
            if (profileId == activeProfileId) return@launch
            activeTopics = emptyList()
            newestStoredTime = 0L
            profileRepository.setActiveProfileId(profileId)
            _uiState.update { it.copy(bannerMessage = "Switching server profile") }
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
                AppDestination.SAVED,
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
        _uiState.update { it.copy(activeFilter = filter, selectedTopicFilter = null) }
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
    ) as T
}
