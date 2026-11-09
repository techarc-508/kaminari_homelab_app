package com.rplab.kaminarinfty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rplab.kaminarinfty.data.repository.ActivityRepository
import com.rplab.kaminarinfty.data.repository.ProfileRepository
import com.rplab.kaminarinfty.model.ActivityEvent
import com.rplab.kaminarinfty.model.ActivitySeverity
import com.rplab.kaminarinfty.model.ActivityType
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ActivityFilter(val label: String) {
    ALL("All"),
    PUBLISH("Publish"),
    RECEIVE("Inbox"),
    ATTENTION("Attention"),
}

data class ActivityUiState(
    val activeProfileId: String? = null,
    val events: List<ActivityEvent> = emptyList(),
    val filter: ActivityFilter = ActivityFilter.ALL,
    val isLoading: Boolean = true,
    val error: String? = null,
) {
    val visibleEvents: List<ActivityEvent>
        get() = events.filter { event ->
            when (filter) {
                ActivityFilter.ALL -> true
                ActivityFilter.PUBLISH -> event.type.name.startsWith("PUBLISH_")
                ActivityFilter.RECEIVE -> event.type == ActivityType.MESSAGE_RECEIVED
                ActivityFilter.ATTENTION ->
                    event.severity == ActivitySeverity.WARNING || event.severity == ActivitySeverity.ERROR
            }
        }
}

class ActivityViewModel(
    private val activityRepository: ActivityRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

    private var eventsJob: Job? = null
    private var activeProfileId: String? = null
    private var hasBoundProfile = false

    init {
        viewModelScope.launch {
            profileRepository.observeActiveProfileId().collect { profileId ->
                if (hasBoundProfile && profileId == activeProfileId) return@collect
                hasBoundProfile = true
                activeProfileId = profileId
                bindProfile(profileId)
            }
        }
    }

    private fun bindProfile(profileId: String?) {
        eventsJob?.cancel()
        if (profileId.isNullOrBlank()) {
            _uiState.update {
                it.copy(activeProfileId = null, events = emptyList(), isLoading = false)
            }
            return
        }
        _uiState.update {
            it.copy(
                activeProfileId = profileId,
                events = emptyList(),
                isLoading = true,
                error = null,
            )
        }
        eventsJob = viewModelScope.launch {
            activityRepository.observe(profileId)
                .catch { failure ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = failure.message ?: "Could not read the activity timeline",
                        )
                    }
                }
                .collect { events ->
                    _uiState.update {
                        it.copy(events = events, isLoading = false, error = null)
                    }
                }
        }
    }

    fun setFilter(filter: ActivityFilter) {
        _uiState.update { it.copy(filter = filter) }
    }
}

class ActivityViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = ActivityViewModel(
        activityRepository = container.activityRepository,
        profileRepository = container.profileRepository,
    ) as T
}
