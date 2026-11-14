package com.rplab.kaminarinfty

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rplab.kaminarinfty.commands.CommandCatalog
import com.rplab.kaminarinfty.commands.CommandActionSpec
import com.rplab.kaminarinfty.commands.GatewayConfigStore
import com.rplab.kaminarinfty.commands.GatewayProbe
import com.rplab.kaminarinfty.commands.GatewayUrlPolicy
import com.rplab.kaminarinfty.data.repository.CommandRepository
import com.rplab.kaminarinfty.data.repository.ProfileRepository
import com.rplab.kaminarinfty.model.CommandRequest
import com.rplab.kaminarinfty.model.CommandStatus
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CommandUiState(
    val backendLabel: String = "GATEWAY // not configured",
    val activeProfileId: String? = null,
    val actions: List<CommandActionSpec> = CommandCatalog.actions,
    val selectedActionId: String = CommandCatalog.actions.first().id,
    val formArgs: Map<String, String> = emptyMap(),
    val commands: List<CommandRequest> = emptyList(),
    val isLoading: Boolean = true,
    val isSubmitting: Boolean = false,
    val validationError: String? = null,
    val notice: String? = null,
    val error: String? = null,
    val pendingConfirmation: CommandRequest? = null,
    val confirmationInput: String = "",
    val confirmationError: String? = null,
    /** Gateway provisioning (token input is write-only; stored token is never shown). */
    val gatewayUrl: String = "",
    val gatewayTokenInput: String = "",
    val gatewayTokenStored: Boolean = false,
    val gatewayConfigured: Boolean = false,
    val gatewayBusy: Boolean = false,
    val gatewayStatus: String? = null,
    val gatewayError: String? = null,
    val gatewayMutationsEnabled: Boolean? = null,
    val gatewayStepUpEnrolled: Boolean? = null,
    val approvalCode: String = "",
) {
    val selectedAction: CommandActionSpec? get() = actions.firstOrNull { it.id == selectedActionId }
    val confirmationRequired: Boolean get() = pendingConfirmation != null
}

class CommandViewModel(
    private val commandRepository: CommandRepository,
    private val profileRepository: ProfileRepository,
    private val gatewayConfig: GatewayConfigStore,
    private val gatewayProbe: GatewayProbe,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CommandUiState())
    val uiState: StateFlow<CommandUiState> = _uiState.asStateFlow()

    private var commandsJob: Job? = null
    private var activeProfileId: String? = null
    private var hasBoundProfile = false

    init {
        viewModelScope.launch { refreshGatewayConfig() }
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
        commandsJob?.cancel()
        if (profileId.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    activeProfileId = null,
                    commands = emptyList(),
                    isLoading = false,
                    pendingConfirmation = null,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                activeProfileId = profileId,
                commands = emptyList(),
                isLoading = true,
                error = null,
                pendingConfirmation = null,
            )
        }
        viewModelScope.launch {
            runCatching { commandRepository.expireOverdue(profileId) }
        }
        commandsJob = viewModelScope.launch {
            commandRepository.observe(profileId)
                .catch { failure ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = failure.message ?: "Could not read command history",
                        )
                    }
                }
                .collect { commands ->
                    _uiState.update {
                        it.copy(commands = commands, isLoading = false, error = null)
                    }
                }
        }
    }

    private suspend fun refreshGatewayConfig() {
        val config = runCatching { gatewayConfig.state() }.getOrNull()
        _uiState.update {
            it.copy(
                gatewayUrl = config?.url.orEmpty(),
                gatewayTokenStored = config?.hasToken == true,
                gatewayConfigured = config?.isConfigured == true,
                backendLabel = if (config?.isConfigured == true) {
                    "GATEWAY // configured"
                } else {
                    "GATEWAY // not configured"
                },
            )
        }
    }

    fun updateGatewayUrl(value: String) {
        _uiState.update { it.copy(gatewayUrl = value, gatewayError = null) }
    }

    fun updateGatewayToken(value: String) {
        _uiState.update { it.copy(gatewayTokenInput = value, gatewayError = null) }
    }

    fun updateApprovalCode(value: String) {
        val digits = value.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(approvalCode = digits, confirmationError = null) }
    }

    /** Saves the endpoint and (optionally) a new token, then probes the gateway. */
    fun saveGateway() {
        val state = _uiState.value
        val urlError = GatewayUrlPolicy.validationError(state.gatewayUrl)
        if (urlError != null) {
            _uiState.update { it.copy(gatewayError = urlError) }
            return
        }
        if (state.gatewayTokenInput.isBlank() && !state.gatewayTokenStored) {
            _uiState.update { it.copy(gatewayError = "Enter the gateway token") }
            return
        }
        _uiState.update { it.copy(gatewayBusy = true, gatewayError = null, gatewayStatus = null) }
        viewModelScope.launch {
            try {
                gatewayConfig.save(state.gatewayUrl, state.gatewayTokenInput)
                _uiState.update { it.copy(gatewayTokenInput = "") }
                refreshGatewayConfig()
                probeGateway()
            } catch (ce: IllegalArgumentException) {
                _uiState.update { it.copy(gatewayBusy = false, gatewayError = ce.message ?: "Could not store the token") }
            } catch (_: Exception) {
                _uiState.update { it.copy(gatewayBusy = false, gatewayError = "Could not save gateway settings") }
            }
        }
    }

    fun testGateway() {
        viewModelScope.launch { probeGateway() }
    }

    private suspend fun probeGateway() {
        _uiState.update { it.copy(gatewayBusy = true, gatewayError = null) }
        val health = gatewayProbe.probe(runCatching { gatewayConfig.connection() }.getOrNull())
        _uiState.update {
            it.copy(
                gatewayBusy = false,
                gatewayStatus = health.message,
                gatewayError = if (health.reachable) null else health.message,
                gatewayMutationsEnabled = if (health.reachable) health.mutationsEnabled else null,
                gatewayStepUpEnrolled = if (health.reachable) health.stepUpEnrolled else null,
                backendLabel = if (health.reachable) "GATEWAY // connected" else it.backendLabel,
            )
        }
    }

    fun clearGateway() {
        viewModelScope.launch {
            runCatching { gatewayConfig.clear() }
            _uiState.update {
                it.copy(
                    gatewayUrl = "",
                    gatewayTokenInput = "",
                    gatewayTokenStored = false,
                    gatewayConfigured = false,
                    gatewayBusy = false,
                    gatewayStatus = null,
                    gatewayError = null,
                    gatewayMutationsEnabled = null,
                    gatewayStepUpEnrolled = null,
                    backendLabel = "GATEWAY // not configured",
                )
            }
        }
    }

    fun selectAction(actionId: String) {
        val action = _uiState.value.actions.firstOrNull { it.id == actionId } ?: return
        _uiState.update {
            it.copy(
                selectedActionId = action.id,
                formArgs = action.arguments.associate { it.key to "" },
                validationError = null,
            )
        }
    }

    fun updateArg(key: String, value: String) {
        _uiState.update { current ->
            current.copy(formArgs = current.formArgs + (key to value), validationError = null)
        }
    }

    fun submit() {
        val state = _uiState.value
        val profileId = state.activeProfileId ?: run {
            _uiState.update { it.copy(error = "Select a server profile first") }
            return
        }
        val action = state.selectedAction ?: run {
            _uiState.update { it.copy(error = "Select a command action") }
            return
        }
        val validation = CommandCatalog.validateArguments(action, state.formArgs)
        if (validation != null) {
            _uiState.update { it.copy(validationError = validation) }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, validationError = null, error = null) }
        viewModelScope.launch {
            when (val result = commandRepository.create(profileId, action.id, state.formArgs)) {
                is com.rplab.kaminarinfty.data.repository.CommandCreateResult.AwaitingConfirmation ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            pendingConfirmation = result.command,
                            confirmationInput = "",
                            confirmationError = null,
                            notice = "Step-up approval required for this operation",
                        )
                    }
                is com.rplab.kaminarinfty.data.repository.CommandCreateResult.Completed ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            notice = result.command.resultSummary
                                ?: "Command completed",
                        )
                    }
                is com.rplab.kaminarinfty.data.repository.CommandCreateResult.Rejected ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = result.reason)
                    }
            }
            runCatching { commandRepository.expireOverdue(profileId) }
        }
    }

    fun updateConfirmationInput(value: String) {
        _uiState.update { it.copy(confirmationInput = value, confirmationError = null) }
    }

    fun confirmPending() {
        val state = _uiState.value
        val profileId = state.activeProfileId ?: return
        val pending = state.pendingConfirmation ?: return
        val code = state.approvalCode.trim()
        if (pending.requiresConfirmation && state.gatewayConfigured && !Regex("^[0-9]{6}$").matches(code)) {
            _uiState.update {
                it.copy(confirmationError = "Enter the current 6-digit authenticator code")
            }
            return
        }
        _uiState.update { it.copy(isSubmitting = true, confirmationError = null) }
        viewModelScope.launch {
            when (val result = commandRepository.confirmAndExecute(
                profileId = profileId,
                commandId = pending.commandId,
                nonce = pending.nonce,
                approvalCode = code.ifBlank { null },
            )) {
                is com.rplab.kaminarinfty.data.repository.CommandActionResult.Completed ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            pendingConfirmation = null,
                            confirmationInput = "",
                            approvalCode = "",
                            notice = result.command.resultSummary
                                ?: "Command completed",
                        )
                    }
                is com.rplab.kaminarinfty.data.repository.CommandActionResult.Expired ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            pendingConfirmation = null,
                            confirmationInput = "",
                            error = "Command request expired before execution",
                        )
                    }
                is com.rplab.kaminarinfty.data.repository.CommandActionResult.Rejected ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            confirmationError = result.reason,
                        )
                    }
            }
            runCatching { commandRepository.expireOverdue(profileId) }
        }
    }

    fun rejectPending() {
        val state = _uiState.value
        val profileId = state.activeProfileId ?: return
        val pending = state.pendingConfirmation ?: return
        _uiState.update { it.copy(isSubmitting = true) }
        viewModelScope.launch {
            when (val result = commandRepository.reject(
                profileId = profileId,
                commandId = pending.commandId,
                nonce = pending.nonce,
            )) {
                is com.rplab.kaminarinfty.data.repository.CommandActionResult.Completed ->
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            pendingConfirmation = null,
                            confirmationInput = "",
                            notice = "Command rejected without execution",
                        )
                    }
                else ->
                    _uiState.update {
                        it.copy(isSubmitting = false, error = "Could not reject the command request")
                    }
            }
            runCatching { commandRepository.expireOverdue(profileId) }
        }
    }

    fun cancelPending() {
        _uiState.update {
            it.copy(
                pendingConfirmation = null,
                confirmationInput = "",
                confirmationError = null,
            )
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null, error = null, validationError = null) }
    }

    fun refresh() {
        val profileId = _uiState.value.activeProfileId ?: return
        viewModelScope.launch {
            runCatching { commandRepository.expireOverdue(profileId) }
        }
    }
}

class CommandViewModelFactory(
    private val container: AppContainer,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = CommandViewModel(
        commandRepository = container.commandRepository,
        profileRepository = container.profileRepository,
        gatewayConfig = container.gatewayConfig,
        gatewayProbe = container.gatewayProbe,
    ) as T
}
