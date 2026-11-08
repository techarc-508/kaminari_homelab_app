package com.example

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.SampleDataProvider
import com.example.data.repository.NftyRepository
import com.example.model.AuthMethod
import com.example.model.AuthState
import com.example.model.NftyErrorCategory
import com.example.model.NftyMessage
import com.example.model.NftyPriority
import com.example.model.NftyTopic
import com.example.model.ServerConfig
import com.example.model.TopicPermission
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

enum class SimulationState(val label: String) {
    NORMAL("Live Stream"),
    LOADING("Simulate Loading"),
    EMPTY("Simulate Empty"),
    OFFLINE("Simulate Offline"),
    ERROR("Simulate Error"),
}

data class MainUiState(
    val currentDestination: AppDestination = AppDestination.INBOX,
    val selectedTab: AppDestination = AppDestination.INBOX,
    val serverConfig: ServerConfig = ServerConfig(),
    val authState: AuthState = AuthState.CONNECTING,
    val isSampleData: Boolean = true,
    val messages: List<NftyMessage> = emptyList(),
    val topics: List<NftyTopic> = emptyList(),
    val activeFilter: InboxFilter = InboxFilter.ALL,
    val selectedTopicFilter: String? = null,
    val searchQuery: String = "",
    val selectedMessage: NftyMessage? = null,
    val simulationState: SimulationState = SimulationState.NORMAL,
    val errorCategory: NftyErrorCategory = NftyErrorCategory.UNREACHABLE,
    val customErrorMessage: String? = null,
    val themeSetting: ThemeSetting = ThemeSetting.SYSTEM,
    val isCompactDensity: Boolean = false,
    val urlToConfirm: String? = null,
    val bannerMessage: String? = null,
)

class MainViewModel(
    private val repository: NftyRepository = NftyRepository()
) : ViewModel() {

    private val initialSamples = SampleDataProvider.getSampleMessages()

    private val _uiState = MutableStateFlow(
        MainUiState(
            messages = initialSamples,
            topics = SampleDataProvider.getSampleTopics(),
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        // Register sample message IDs to avoid duplicates if re-fetched
        initialSamples.forEach { repository.registerMessageId(it.id) }

        // Observe repository connection state
        viewModelScope.launch {
            repository.connectionState.collect { state ->
                // Only override authState if not in a simulated mode
                if (_uiState.value.simulationState == SimulationState.NORMAL) {
                    _uiState.update { it.copy(authState = state) }
                }
            }
        }

        // Observe repository errors
        viewModelScope.launch {
            repository.lastError.collect { errorPair ->
                if (errorPair != null) {
                    val (category, message) = errorPair
                    _uiState.update {
                        it.copy(
                            errorCategory = category,
                            customErrorMessage = message,
                        )
                    }
                }
            }
        }

        // Observe incoming live messages from stream
        viewModelScope.launch {
            repository.incomingMessages.collect { incoming ->
                _uiState.update { current ->
                    // Prepend new live message
                    val updated = listOf(incoming) + current.messages
                    current.copy(
                        messages = updated,
                        isSampleData = false, // Has live server data
                        bannerMessage = "New event on #${incoming.topic}: ${incoming.title ?: incoming.message.take(24)}"
                    )
                }
            }
        }

        // Start real stream connection for configured subscribed topics
        restartLiveStream()
    }

    private fun restartLiveStream() {
        val config = _uiState.value.serverConfig
        val subscribedTopics = _uiState.value.topics
            .filter { it.isSubscribed && !it.isPaused }
            .map { it.name }

        if (subscribedTopics.isNotEmpty()) {
            repository.startSubscription(config, subscribedTopics)
        } else {
            repository.stopSubscription()
        }
    }

    fun navigateTo(destination: AppDestination) {
        _uiState.update { current ->
            val newTab = when (destination) {
                AppDestination.INBOX -> AppDestination.INBOX
                AppDestination.TOPICS -> AppDestination.TOPICS
                AppDestination.SAVED -> AppDestination.SAVED
                AppDestination.SETTINGS -> AppDestination.SETTINGS
                else -> current.selectedTab
            }
            current.copy(
                currentDestination = destination,
                selectedTab = newTab
            )
        }
    }

    fun selectTab(destination: AppDestination) {
        _uiState.update { it.copy(currentDestination = destination, selectedTab = destination) }
    }

    fun openMessageDetail(message: NftyMessage) {
        markAsRead(message.id)
        _uiState.update {
            it.copy(
                selectedMessage = message.copy(isRead = true),
                currentDestination = AppDestination.DETAIL
            )
        }
    }

    fun toggleSaveMessage(id: String) {
        _uiState.update { current ->
            val updated = current.messages.map { msg ->
                if (msg.id == id) msg.copy(isSaved = !msg.isSaved) else msg
            }
            val currentSelected = current.selectedMessage?.let { selected ->
                if (selected.id == id) selected.copy(isSaved = !selected.isSaved) else selected
            }
            current.copy(
                messages = updated,
                selectedMessage = currentSelected,
            )
        }
    }

    fun markAsRead(id: String) {
        _uiState.update { current ->
            val updated = current.messages.map { msg ->
                if (msg.id == id) msg.copy(isRead = true) else msg
            }
            current.copy(messages = updated)
        }
    }

    fun markAllAsRead() {
        _uiState.update { current ->
            val updated = current.messages.map { it.copy(isRead = true) }
            current.copy(
                messages = updated,
                bannerMessage = "All notifications marked as read",
            )
        }
    }

    fun deleteMessage(id: String) {
        _uiState.update { current ->
            val updated = current.messages.filter { it.id != id }
            current.copy(
                messages = updated,
                selectedMessage = if (current.selectedMessage?.id == id) null else current.selectedMessage,
                currentDestination = if (current.selectedMessage?.id == id) AppDestination.INBOX else current.currentDestination,
                bannerMessage = "Notification removed from local history",
            )
        }
    }

    fun setFilter(filter: InboxFilter) {
        _uiState.update { it.copy(activeFilter = filter, selectedTopicFilter = null) }
    }

    fun setTopicFilter(topic: String?) {
        _uiState.update { it.copy(selectedTopicFilter = topic) }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun updateServerConfig(config: ServerConfig) {
        _uiState.update { it.copy(serverConfig = config) }
        restartLiveStream()
    }

    fun testServerConnection() {
        viewModelScope.launch {
            _uiState.update { it.copy(authState = AuthState.CONNECTING, bannerMessage = "Testing TLS link to ${it.serverConfig.serverUrl}...") }
            val result = withContext(Dispatchers.IO) {
                repository.testConnection(_uiState.value.serverConfig)
            }
            result.fold(
                onSuccess = { verifiedAuthState ->
                    _uiState.update {
                        it.copy(
                            authState = verifiedAuthState,
                            bannerMessage = "Connected successfully (${verifiedAuthState.statusLabel})"
                        )
                    }
                    restartLiveStream()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            authState = AuthState.AUTHENTICATION_FAILED,
                            customErrorMessage = error.message,
                            bannerMessage = "Link check failed: ${error.message}"
                        )
                    }
                }
            )
        }
    }

    fun retryLiveStream() {
        _uiState.update { it.copy(simulationState = SimulationState.NORMAL, customErrorMessage = null) }
        restartLiveStream()
    }

    fun setSimulationState(state: SimulationState) {
        _uiState.update {
            it.copy(
                simulationState = state,
                authState = when (state) {
                    SimulationState.OFFLINE -> AuthState.DISCONNECTED
                    SimulationState.ERROR -> AuthState.AUTHENTICATION_FAILED
                    SimulationState.LOADING -> AuthState.CONNECTING
                    SimulationState.NORMAL -> repository.connectionState.value
                    else -> it.authState
                }
            )
        }
        if (state == SimulationState.NORMAL) {
            restartLiveStream()
        } else {
            repository.stopSubscription()
        }
    }

    fun setThemeSetting(theme: ThemeSetting) {
        _uiState.update { it.copy(themeSetting = theme) }
    }

    fun toggleDensity() {
        _uiState.update { it.copy(isCompactDensity = !it.isCompactDensity) }
    }

    fun toggleTopicSubscription(name: String) {
        _uiState.update { current ->
            val updated = current.topics.map { t ->
                if (t.name == name) t.copy(isSubscribed = !t.isSubscribed) else t
            }
            current.copy(topics = updated)
        }
        restartLiveStream()
    }

    fun toggleTopicPause(name: String) {
        _uiState.update { current ->
            val updated = current.topics.map { t ->
                if (t.name == name) t.copy(isPaused = !t.isPaused) else t
            }
            current.copy(topics = updated)
        }
        restartLiveStream()
    }

    fun addTopic(name: String, description: String) {
        val sanitized = name.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
        if (sanitized.isBlank()) return

        _uiState.update { current ->
            if (current.topics.any { it.name == sanitized }) {
                current.copy(bannerMessage = "Topic #$sanitized is already subscribed")
            } else {
                val newTopic = NftyTopic(
                    name = sanitized,
                    description = description.ifBlank { "User subscribed topic" },
                    messageCount = 0,
                    isSubscribed = true,
                    permission = TopicPermission.GUEST_READ,
                )
                current.copy(
                    topics = listOf(newTopic) + current.topics,
                    bannerMessage = "Subscribed to #$sanitized",
                )
            }
        }
        restartLiveStream()
    }

    fun promptExternalUrl(url: String) {
        _uiState.update { it.copy(urlToConfirm = url) }
    }

    fun dismissUrlPrompt() {
        _uiState.update { it.copy(urlToConfirm = null) }
    }

    fun clearHistory() {
        _uiState.update {
            it.copy(
                messages = it.messages.filter { msg -> msg.isSaved },
                bannerMessage = "Cached notification history cleared (saved preserved)",
            )
        }
    }

    fun clearSaved() {
        _uiState.update { current ->
            val updated = current.messages.map { it.copy(isSaved = false) }
            current.copy(
                messages = updated,
                bannerMessage = "All saved items removed",
            )
        }
    }

    fun resetSampleData() {
        _uiState.update {
            it.copy(
                messages = SampleDataProvider.getSampleMessages(),
                topics = SampleDataProvider.getSampleTopics(),
                simulationState = SimulationState.NORMAL,
                bannerMessage = "Sample notifications restored",
            )
        }
        restartLiveStream()
    }

    fun clearBanner() {
        _uiState.update { it.copy(bannerMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        repository.stopSubscription()
    }
}
