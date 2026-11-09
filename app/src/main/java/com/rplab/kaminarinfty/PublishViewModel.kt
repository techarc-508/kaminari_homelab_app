package com.rplab.kaminarinfty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rplab.kaminarinfty.data.api.PublishRequest
import com.rplab.kaminarinfty.data.api.PublishValidator
import com.rplab.kaminarinfty.data.repository.OutboxRepository
import com.rplab.kaminarinfty.data.repository.ProfileRepository
import com.rplab.kaminarinfty.data.repository.SendOutcome
import com.rplab.kaminarinfty.model.NftyTopic
import com.rplab.kaminarinfty.model.OutboxEntry
import com.rplab.kaminarinfty.model.OutboxState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PublishForm(
    val topic: String = "",
    val title: String = "",
    val body: String = "",
    val priority: Int = PublishRequest.PRIORITY_DEFAULT,
    val tagsRaw: String = "",
    val clickUrl: String = "",
    val attachmentUrl: String = "",
    val delay: String = "",
    val showAdvanced: Boolean = false,
) {
    fun toRequest(): PublishRequest = PublishRequest(
        topic = topic.trim(),
        message = body,
        title = title.trim().ifBlank { null },
        priority = priority,
        tags = tagsRaw.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() },
        clickUrl = clickUrl.trim().ifBlank { null },
        attachUrl = attachmentUrl.trim().ifBlank { null },
        delay = delay.trim().ifBlank { null },
    )
}

sealed interface PublishOutcome {
    val clientId: String

    data class Accepted(val serverMessageId: String?, override val clientId: String) : PublishOutcome
    data class Failed(
        val reason: String,
        val retryable: Boolean,
        override val clientId: String,
    ) : PublishOutcome
    data class Ambiguous(val reason: String, override val clientId: String) : PublishOutcome
    data class Busy(override val clientId: String) : PublishOutcome
}

data class PublishUiState(
    val activeProfileId: String? = null,
    val topics: List<NftyTopic> = emptyList(),
    val form: PublishForm = PublishForm(),
    val isSending: Boolean = false,
    val validationError: String? = null,
    val outcome: PublishOutcome? = null,
    val outbox: List<OutboxEntry> = emptyList(),
    val isOutboxLoading: Boolean = true,
    val outboxError: String? = null,
    val pendingAmbiguousRetry: String? = null,
    val notice: String? = null,
)

/**
 * Compose and outbox state. A send is exactly one enqueue followed by one
 * explicit `send`; there is no timer, polling loop or automatic retry.
 */
class PublishViewModel(
    private val outboxRepository: OutboxRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PublishUiState())
    val uiState: StateFlow<PublishUiState> = _uiState.asStateFlow()

    private var topicJob: Job? = null
    private var outboxJob: Job? = null
    private var lastClientId: String? = null

    init {
        viewModelScope.launch {
            profileRepository.observeActiveProfileId().collect { profileId ->
                bindProfile(profileId)
            }
        }
    }

    private suspend fun bindProfile(profileId: String?) {
        topicJob?.cancel()
        outboxJob?.cancel()
        lastClientId = null
        if (profileId.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    activeProfileId = null,
                    topics = emptyList(),
                    outbox = emptyList(),
                    isOutboxLoading = false,
                    pendingAmbiguousRetry = null,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                activeProfileId = profileId,
                form = PublishForm(),
                outcome = null,
                validationError = null,
                isSending = false,
                isOutboxLoading = true,
                outboxError = null,
                pendingAmbiguousRetry = null,
            )
        }
        try {
            outboxRepository.recoverStuck(profileId)
        } catch (ce: CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Rows remain SENDING; the next explicit send/recovery pass retries
            // this non-network transition.
        }
        topicJob = viewModelScope.launch {
            profileRepository.observeTopics(profileId)
                .catch { _uiState.update { it.copy(topics = emptyList()) } }
                .collect { topics ->
                    _uiState.update { current ->
                        val available = topics.filter { it.isSubscribed }.ifEmpty { topics }
                        val selected = current.form.topic.ifBlank {
                            available.firstOrNull()?.name.orEmpty()
                        }
                        current.copy(
                            topics = available,
                            form = current.form.copy(topic = selected),
                        )
                    }
                }
        }
        outboxJob = viewModelScope.launch {
            outboxRepository.observe(profileId)
                .catch { failure ->
                    _uiState.update {
                        it.copy(
                            isOutboxLoading = false,
                            outboxError = failure.message ?: "Could not read the outbox",
                        )
                    }
                }
                .collect { entries ->
                    _uiState.update {
                        it.copy(outbox = entries, isOutboxLoading = false, outboxError = null)
                    }
                }
        }
    }

    fun setTopic(value: String) = updateForm { it.copy(topic = value) }
    fun setTitle(value: String) = updateForm { it.copy(title = value) }
    fun setBody(value: String) = updateForm { it.copy(body = value) }
    fun setPriority(value: Int) = updateForm { it.copy(priority = value.coerceIn(1, 5)) }
    fun setTags(value: String) = updateForm { it.copy(tagsRaw = value) }
    fun setClickUrl(value: String) = updateForm { it.copy(clickUrl = value) }
    fun setAttachmentUrl(value: String) = updateForm { it.copy(attachmentUrl = value) }
    fun setDelay(value: String) = updateForm { it.copy(delay = value) }
    fun toggleAdvanced() = updateForm { it.copy(showAdvanced = !it.showAdvanced) }

    fun validateNow(): String? {
        val error = PublishValidator.validateRequest(_uiState.value.form.toRequest())
        _uiState.update { it.copy(validationError = error) }
        return error
    }

    fun send() {
        val state = _uiState.value
        if (state.isSending) return
        val profileId = state.activeProfileId
        if (profileId == null) {
            _uiState.update { it.copy(validationError = "Select a server profile before publishing") }
            return
        }
        val request = state.form.toRequest()
        PublishValidator.validateRequest(request)?.let { error ->
            _uiState.update { it.copy(validationError = error) }
            return
        }

        _uiState.update {
            it.copy(isSending = true, validationError = null, outcome = null, notice = null)
        }
        viewModelScope.launch {
            val enqueued = outboxRepository.enqueue(profileId, request)
            val clientId = enqueued.getOrElse { failure ->
                if (_uiState.value.activeProfileId == profileId) {
                    _uiState.update {
                        it.copy(
                            isSending = false,
                            validationError = failure.message ?: "Could not queue the publish",
                        )
                    }
                }
                return@launch
            }
            val outcome = outboxRepository.send(profileId, clientId)
            // The row belongs to the original profile. If the user switched while
            // the request was in flight, its result must not surface as the new
            // profile's outcome; the persisted outbox row will be read when that
            // profile is active again.
            if (_uiState.value.activeProfileId != profileId) return@launch
            lastClientId = clientId
            applySendOutcome(outcome, clientId, clearDraftOnAccepted = true)
        }
    }

    /** Explicit retry for the publish shown in the compose result card. */
    fun retryLast() {
        val clientId = lastClientId ?: _uiState.value.outcome?.clientId ?: return
        retryEntry(clientId)
    }

    /**
     * Retries one outbox row. AMBIGUOUS first raises a confirmation; the second
     * call (after confirmation) is the only path that passes `allowAmbiguous`.
     */
    fun retryEntry(clientId: String, allowAmbiguous: Boolean = false) {
        val state = _uiState.value
        val profileId = state.activeProfileId ?: return
        val entry = state.outbox.firstOrNull { it.clientId == clientId }
        val ambiguous = entry?.state == OutboxState.AMBIGUOUS ||
            (state.outcome?.clientId == clientId && state.outcome is PublishOutcome.Ambiguous)
        if (ambiguous && !allowAmbiguous) {
            _uiState.update { it.copy(pendingAmbiguousRetry = clientId) }
            return
        }
        if (state.isSending) return
        _uiState.update { it.copy(isSending = true, notice = null) }
        viewModelScope.launch {
            val outcome = outboxRepository.retry(profileId, clientId, allowAmbiguous)
            if (_uiState.value.activeProfileId != profileId) return@launch
            if (clientId == lastClientId) {
                applySendOutcome(outcome, clientId, clearDraftOnAccepted = false)
            } else {
                _uiState.update {
                    it.copy(
                        isSending = false,
                        pendingAmbiguousRetry = null,
                        notice = outcome.noticeText(),
                    )
                }
            }
        }
    }

    fun confirmAmbiguousRetry() {
        val clientId = _uiState.value.pendingAmbiguousRetry ?: return
        _uiState.update { it.copy(pendingAmbiguousRetry = null) }
        retryEntry(clientId, allowAmbiguous = true)
    }

    fun dismissAmbiguousRetry() {
        _uiState.update { it.copy(pendingAmbiguousRetry = null) }
    }

    fun cancelEntry(clientId: String) {
        val profileId = _uiState.value.activeProfileId ?: return
        viewModelScope.launch {
            val removed = outboxRepository.cancel(profileId, clientId)
            if (_uiState.value.activeProfileId != profileId) return@launch
            if (clientId == lastClientId) lastClientId = null
            _uiState.update {
                it.copy(
                    notice = if (removed) {
                        "Queued publish removed"
                    } else {
                        "Only an unsent publish can be removed"
                    },
                    pendingAmbiguousRetry = null,
                )
            }
        }
    }

    fun dismissOutcome() {
        _uiState.update { it.copy(outcome = null) }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun clearValidationError() {
        _uiState.update { it.copy(validationError = null) }
    }

    private fun updateForm(transform: (PublishForm) -> PublishForm) {
        _uiState.update {
            it.copy(
                form = transform(it.form),
                validationError = null,
                outcome = null,
            )
        }
    }

    private fun applySendOutcome(
        outcome: SendOutcome,
        clientId: String,
        clearDraftOnAccepted: Boolean,
    ) {
        val mapped = when (outcome) {
            is SendOutcome.Accepted -> PublishOutcome.Accepted(outcome.serverMessageId, clientId)
            is SendOutcome.Failed -> PublishOutcome.Failed(outcome.reason, outcome.retryable, clientId)
            is SendOutcome.Ambiguous -> PublishOutcome.Ambiguous(outcome.reason, clientId)
            SendOutcome.Busy -> PublishOutcome.Busy(clientId)
            SendOutcome.NotFound -> PublishOutcome.Failed(
                "The outbox entry was not found for this profile",
                retryable = false,
                clientId = clientId,
            )
            is SendOutcome.ConfirmationRequired -> PublishOutcome.Ambiguous(outcome.reason, clientId)
        }
        _uiState.update { current ->
            val accepted = mapped is PublishOutcome.Accepted
            current.copy(
                isSending = false,
                outcome = mapped,
                form = if (accepted && clearDraftOnAccepted) {
                    current.form.copy(
                        title = "",
                        body = "",
                        tagsRaw = "",
                        clickUrl = "",
                        attachmentUrl = "",
                        delay = "",
                    )
                } else {
                    // A failed or ambiguous send keeps every draft field intact.
                    current.form
                },
            )
        }
    }

    private fun SendOutcome.noticeText(): String = when (this) {
        is SendOutcome.Accepted -> "Publish accepted by the server"
        is SendOutcome.Failed -> if (retryable) "Publish failed: $reason" else "Publish failed: $reason"
        is SendOutcome.Ambiguous -> "Outcome unknown: $reason"
        SendOutcome.Busy -> "That publish is already sending"
        SendOutcome.NotFound -> "That outbox entry is no longer available"
        is SendOutcome.ConfirmationRequired -> reason
    }
}

class PublishViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = PublishViewModel(
        outboxRepository = container.outboxRepository,
        profileRepository = container.profileRepository,
    ) as T
}
