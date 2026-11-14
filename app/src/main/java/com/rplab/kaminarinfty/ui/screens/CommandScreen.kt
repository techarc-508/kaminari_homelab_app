package com.rplab.kaminarinfty.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.CommandUiState
import com.rplab.kaminarinfty.commands.CommandActionSpec
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.CommandRequest
import com.rplab.kaminarinfty.model.CommandStatus
import com.rplab.kaminarinfty.ui.components.NftyEmptyState
import com.rplab.kaminarinfty.ui.components.NftyInlineNotice
import com.rplab.kaminarinfty.ui.components.NftyLoadingIndicator
import com.rplab.kaminarinfty.ui.components.NftyTopBar
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommandScreen(
    state: CommandUiState,
    authState: AuthState,
    profileLabel: String,
    isSampleData: Boolean,
    onSelectAction: (String) -> Unit,
    onArgChange: (String, String) -> Unit,
    onSubmit: () -> Unit,
    onApprovalCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onCancelConfirmation: () -> Unit,
    onClearNotice: () -> Unit,
    onGatewayUrlChange: (String) -> Unit,
    onGatewayTokenChange: (String) -> Unit,
    onSaveGateway: () -> Unit,
    onTestGateway: () -> Unit,
    onClearGateway: () -> Unit,
    onSettingsClick: () -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
    ) {
        NftyTopBar(
            title = "Command Centre",
            subtitle = "${state.backendLabel} // $profileLabel",
            authState = authState,
            isSampleData = isSampleData,
            onSettingsClick = onSettingsClick,
        )

        BackendBanner(state)

        state.notice?.let { notice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NftyInlineNotice(text = notice, modifier = Modifier.weight(1f))
                Text(
                    text = "Dismiss",
                    modifier = Modifier
                        .clickable(onClick = onClearNotice)
                        .padding(4.dp)
                        .testTag("command_dismiss_notice"),
                    fontFamily = RubikFontFamily,
                    fontSize = 10.sp,
                    color = KaminariTheme.colors.textSecondary,
                )
            }
        }
        state.error?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                NftyInlineNotice(text = error, isError = true)
            }
        }
        state.validationError?.let { validation ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                NftyInlineNotice(text = validation, isError = true)
            }
        }

        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NftyLoadingIndicator(
                    status = "Reading command history…",
                    technicalDetail = "ROOM // COMMAND_REQUESTS",
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("command_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    GatewayPanel(
                        state = state,
                        onUrlChange = onGatewayUrlChange,
                        onTokenChange = onGatewayTokenChange,
                        onSave = onSaveGateway,
                        onTest = onTestGateway,
                        onClear = onClearGateway,
                    )
                }
                item {
                    CommandCatalogue(
                        actions = state.actions,
                        selectedActionId = state.selectedActionId,
                        onSelectAction = onSelectAction,
                    )
                }
                item {
                    CommandForm(
                        state = state,
                        onArgChange = onArgChange,
                        onSubmit = onSubmit,
                    )
                }
                if (state.pendingConfirmation != null) {
                    item {
                        CommandConfirmation(
                            command = state.pendingConfirmation!!,
                            approvalCode = state.approvalCode,
                            error = state.confirmationError,
                            isSubmitting = state.isSubmitting,
                            onCodeChange = onApprovalCodeChange,
                            onConfirm = onConfirm,
                            onReject = onReject,
                            onCancel = onCancelConfirmation,
                        )
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "COMMAND HISTORY",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = KaminariTheme.colors.textSecondary,
                        )
                        OutlinedButton(onClick = onRefresh) {
                            Text("Refresh", fontSize = 10.sp)
                        }
                    }
                }
                if (state.commands.isEmpty()) {
                    item {
                        NftyEmptyState(
                            title = "No command history",
                            message = "Typed commands you create appear here with their real gateway outcome.",
                            technicalCode = "COMMANDS // 0_ENTRIES",
                            icon = Icons.Default.Build,
                        )
                    }
                } else {
                    items(state.commands, key = { it.commandId }) { command ->
                        CommandHistoryCard(command = command)
                    }
                }
                item { Spacer(modifier = Modifier.height(64.dp)) }
            }
        }
    }
}

@Composable
private fun BackendBanner(state: CommandUiState) {
    val warning = !state.gatewayConfigured || state.gatewayMutationsEnabled == false
    val text = when {
        !state.gatewayConfigured -> "GATEWAY NOT CONFIGURED — EXECUTION DISABLED"
        state.gatewayMutationsEnabled == false -> "GATEWAY CONNECTED — MUTATIONS DISABLED BY SERVER"
        else -> "GATEWAY CONNECTED — MUTATIONS ENABLED"
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (warning) KaminariTheme.colors.warning.copy(alpha = 0.16f)
                else KaminariTheme.colors.success.copy(alpha = 0.14f)
            )
            .border(0.75.dp, if (warning) KaminariTheme.colors.warning else KaminariTheme.colors.success)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .testTag("command_backend_banner"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (warning) KaminariTheme.colors.warning else KaminariTheme.colors.success,
        )
    }
}

@Composable
private fun GatewayPanel(
    state: CommandUiState,
    onUrlChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onSave: () -> Unit,
    onTest: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(0.85.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("gateway_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "GATEWAY CONNECTION",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = KaminariTheme.colors.textSecondary,
        )
        Text(
            text = "The gateway runs inside the homelab network. Enter its base URL and the " +
                "least-privilege gateway token; the token is encrypted with the Android Keystore " +
                "and is never displayed again.",
            fontFamily = RubikFontFamily,
            fontSize = 10.sp,
            color = KaminariTheme.colors.textSecondary,
        )
        OutlinedTextField(
            value = state.gatewayUrl,
            onValueChange = onUrlChange,
            label = { Text("Gateway URL") },
            placeholder = { Text("https://gateway.example.lan") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gateway_url_input"),
        )
        OutlinedTextField(
            value = state.gatewayTokenInput,
            onValueChange = onTokenChange,
            label = {
                Text(if (state.gatewayTokenStored) "Replace token (stored)" else "Gateway token")
            },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("gateway_token_input"),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onSave,
                enabled = !state.gatewayBusy,
                modifier = Modifier
                    .weight(1f)
                    .testTag("gateway_save"),
            ) {
                Text(if (state.gatewayBusy) "Checking…" else "Save & test", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = onTest,
                enabled = !state.gatewayBusy && state.gatewayConfigured,
                modifier = Modifier
                    .weight(1f)
                    .testTag("gateway_test"),
            ) {
                Text("Test", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = onClear,
                enabled = !state.gatewayBusy && (state.gatewayConfigured || state.gatewayUrl.isNotBlank()),
                modifier = Modifier.testTag("gateway_clear"),
            ) {
                Text("Clear", fontSize = 11.sp)
            }
        }
        state.gatewayError?.let { NftyInlineNotice(text = it, isError = true) }
        state.gatewayStatus?.let { status ->
            Text(
                text = "STATUS // $status",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                color = if (state.gatewayConfigured) {
                    KaminariTheme.colors.textSecondary
                } else {
                    KaminariTheme.colors.warning
                },
                modifier = Modifier.testTag("gateway_status"),
            )
        }
        state.gatewayStepUpEnrolled?.let { enrolled ->
            Text(
                text = "STEP-UP // ${if (enrolled) "enrolled" else "not enrolled"}   " +
                    "MUTATIONS // ${if (state.gatewayMutationsEnabled == true) "enabled" else "disabled"}",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                color = KaminariTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun CommandCatalogue(
    actions: List<CommandActionSpec>,
    selectedActionId: String?,
    onSelectAction: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "ALLOWED COMMANDS",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = KaminariTheme.colors.textSecondary,
        )
        actions.forEach { action ->
            val selected = action.id == selectedActionId
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (selected) KaminariTheme.colors.surfaceElevated
                        else KaminariTheme.colors.surfaceMain
                    )
                    .border(
                        0.85.dp,
                        if (selected) KaminariTheme.colors.accent
                        else KaminariTheme.colors.border.copy(alpha = 0.7f),
                        RoundedCornerShape(8.dp),
                    )
                    .clickable { onSelectAction(action.id) }
                    .padding(10.dp)
                    .testTag("command_action_${action.id}"),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = action.id,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = KaminariTheme.colors.textPrimary,
                        )
                        Text(
                            text = riskLabel(action),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (action.mutation) {
                                KaminariTheme.colors.warning
                            } else {
                                KaminariTheme.colors.success
                            },
                        )
                    }
                    Text(
                        text = action.description,
                        fontFamily = RubikFontFamily,
                        fontSize = 11.sp,
                        color = KaminariTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CommandForm(
    state: CommandUiState,
    onArgChange: (String, String) -> Unit,
    onSubmit: () -> Unit,
) {
    val action = state.selectedAction ?: return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(0.85.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "PARAMETERS",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = KaminariTheme.colors.textSecondary,
        )
        if (action.arguments.isEmpty()) {
            Text(
                text = "No parameters required.",
                fontFamily = RubikFontFamily,
                fontSize = 11.sp,
                color = KaminariTheme.colors.textSecondary,
            )
        } else {
            action.arguments.forEach { spec ->
                val key = spec.key
                OutlinedTextField(
                    value = state.formArgs[key].orEmpty(),
                    onValueChange = { onArgChange(key, it) },
                    label = { Text("${spec.label}") },
                    placeholder = { Text(spec.placeholder) },
                    supportingText = {
                        Text(text = spec.hint, fontSize = 9.sp)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("command_arg_$key"),
                    singleLine = true,
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(7.dp))
                .background(KaminariTheme.colors.surfaceSecondary)
                .padding(9.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "PREVIEW",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = KaminariTheme.colors.textSecondary,
                )
                Text(
                    text = "${action.id} → gateway:$action.target? target is bound server-side",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = KaminariTheme.colors.textPrimary,
                )
                Text(
                    text = action.description,
                    fontFamily = RubikFontFamily,
                    fontSize = 9.sp,
                    color = KaminariTheme.colors.textSecondary,
                )
                Text(
                    text = if (action.mutation) {
                        "Mutation. Nothing runs until you approve with a current authenticator code."
                    } else {
                        "Read-only. The gateway authorises and audits the request; no state changes."
                    },
                    fontFamily = RubikFontFamily,
                    fontSize = 9.sp,
                    color = KaminariTheme.colors.textSecondary,
                )
            }
        }

        if (action.mutation) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(KaminariTheme.colors.warning.copy(alpha = 0.12f))
                    .border(0.75.dp, KaminariTheme.colors.warning, RoundedCornerShape(7.dp))
                    .padding(9.dp),
            ) {
                Text(
                    text = "Risk ${action.risk}. This action changes guest state. It requires a fresh " +
                        "TOTP challenge bound to this command; the approval is single-use.",
                    fontFamily = RubikFontFamily,
                    fontSize = 10.sp,
                    color = KaminariTheme.colors.warning,
                )
            }
        }

        Button(
            onClick = onSubmit,
            enabled = !state.isSubmitting && state.gatewayConfigured,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("command_submit"),
        ) {
            Text(
                text = when {
                    !state.gatewayConfigured -> "Configure the gateway to execute"
                    state.isSubmitting -> "Submitting…"
                    action.mutation -> "Request step-up approval"
                    else -> "Run read-only command"
                },
                fontFamily = RubikFontFamily,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun CommandConfirmation(
    command: CommandRequest,
    approvalCode: String,
    error: String?,
    isSubmitting: Boolean,
    onCodeChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KaminariTheme.colors.warning.copy(alpha = 0.10f))
            .border(0.85.dp, KaminariTheme.colors.warning, RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("command_confirmation_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "STEP-UP APPROVAL REQUIRED",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = KaminariTheme.colors.warning,
        )
        Text(
            text = "${command.actionId} on ${command.target}",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = KaminariTheme.colors.textPrimary,
        )
        Text(
            text = "Arguments: ${command.args.entries.joinToString(", ") { "${it.key}=${it.value}" }}",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = KaminariTheme.colors.textSecondary,
        )
        Text(
            text = "This exact command expires ${formatCommandTime(command.expiresAt)}. " +
                "Approval is bound to its id, action, target and arguments and can be used once.",
            fontFamily = RubikFontFamily,
            fontSize = 10.sp,
            color = KaminariTheme.colors.textPrimary,
        )
        OutlinedTextField(
            value = approvalCode,
            onValueChange = onCodeChange,
            label = { Text("6-digit authenticator code") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("command_confirmation_input"),
        )
        error?.let { NftyInlineNotice(text = it, isError = true) }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = onConfirm,
                enabled = !isSubmitting,
                modifier = Modifier
                    .weight(1f)
                    .testTag("command_confirm"),
            ) {
                Text(if (isSubmitting) "Approving…" else "Approve & execute", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = onReject,
                enabled = !isSubmitting,
                modifier = Modifier.weight(1f),
            ) {
                Text("Reject", fontSize = 11.sp)
            }
        }
        OutlinedButton(
            onClick = onCancel,
            enabled = !isSubmitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Cancel", fontSize = 11.sp)
        }
    }
}

@Composable
private fun CommandHistoryCard(command: CommandRequest) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(0.85.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("command_history_${command.commandId}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = command.actionId,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = KaminariTheme.colors.textPrimary,
            )
            Text(
                text = command.status.name,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = when (command.status) {
                    CommandStatus.COMPLETED -> KaminariTheme.colors.success
                    CommandStatus.FAILED -> KaminariTheme.colors.error
                    CommandStatus.EXPIRED -> KaminariTheme.colors.warning
                    CommandStatus.REJECTED -> KaminariTheme.colors.textSecondary
                    CommandStatus.SUBMITTED -> KaminariTheme.colors.accent
                },
            )
        }
        Text(
            text = "Expires ${formatCommandTime(command.expiresAt)}",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = KaminariTheme.colors.textSecondary,
        )
        if (command.requiresConfirmation) {
            Text(
                text = "Mutation • approval ${if (command.confirmedAt == null) "pending" else "accepted"}",
                fontFamily = RubikFontFamily,
                fontSize = 10.sp,
                color = KaminariTheme.colors.warning,
            )
        }
        command.resultSummary?.let { summary ->
            Text(
                text = summary,
                fontFamily = RubikFontFamily,
                fontSize = 11.sp,
                color = KaminariTheme.colors.textSecondary,
                maxLines = 4,
            )
        }
        Text(
            text = command.commandId,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 8.sp,
            color = KaminariTheme.colors.textSecondary.copy(alpha = 0.7f),
        )
    }
}

private fun riskLabel(action: CommandActionSpec): String = when {
    !action.mutation -> "READ-ONLY"
    action.ownerConfirmation -> "DESTRUCTIVE"
    else -> "MUTATION"
}

private fun formatCommandTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
