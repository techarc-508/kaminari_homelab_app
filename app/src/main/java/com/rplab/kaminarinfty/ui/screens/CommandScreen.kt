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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.CommandUiState
import com.rplab.kaminarinfty.commands.CommandCatalog
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
    onConfirmationInputChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onReject: () -> Unit,
    onCancelConfirmation: () -> Unit,
    onClearNotice: () -> Unit,
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
            subtitle = "MOCK // $profileLabel",
            authState = authState,
            isSampleData = isSampleData,
            onSettingsClick = onSettingsClick,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(KaminariTheme.colors.warning.copy(alpha = 0.16f))
                .border(0.75.dp, KaminariTheme.colors.warning, RoundedCornerShape(0.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("command_mock_banner"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "MOCK EXECUTION — NO REAL INFRASTRUCTURE CHANGES",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = KaminariTheme.colors.warning,
            )
        }

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
                            input = state.confirmationInput,
                            error = state.confirmationError,
                            isSubmitting = state.isSubmitting,
                            onInputChange = onConfirmationInputChange,
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
                            message = "Typed mock commands you create appear here. Nothing touches real infrastructure.",
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
private fun CommandCatalogue(
    actions: List<com.rplab.kaminarinfty.commands.CommandActionSpec>,
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
                        if (action.requiresConfirmation) {
                            Text(
                                text = "DESTRUCTIVE",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = KaminariTheme.colors.warning,
                            )
                        }
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
                        Text(
                            text = spec.hint,
                            fontSize = 9.sp,
                        )
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
                    text = action.mockResult(state.formArgs),
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = KaminariTheme.colors.textPrimary,
                )
                Text(
                    text = "Result is simulated. No real infrastructure is queried or changed.",
                    fontFamily = RubikFontFamily,
                    fontSize = 9.sp,
                    color = KaminariTheme.colors.textSecondary,
                )
            }
        }

        if (action.requiresConfirmation) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(KaminariTheme.colors.warning.copy(alpha = 0.12f))
                    .border(0.75.dp, KaminariTheme.colors.warning, RoundedCornerShape(7.dp))
                    .padding(9.dp),
            ) {
                Text(
                    text = "Destructive action. Creating this command only prepares it; execution requires a second confirmation bound to its nonce.",
                    fontFamily = RubikFontFamily,
                    fontSize = 10.sp,
                    color = KaminariTheme.colors.warning,
                )
            }
        }

        Button(
            onClick = onSubmit,
            enabled = !state.isSubmitting,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("command_submit"),
        ) {
            Text(
                text = if (state.isSubmitting) "Submitting…" else "Create mock command",
                fontFamily = RubikFontFamily,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun CommandConfirmation(
    command: CommandRequest,
    input: String,
    error: String?,
    isSubmitting: Boolean,
    onInputChange: (String) -> Unit,
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
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "CONFIRMATION REQUIRED",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = KaminariTheme.colors.warning,
        )
        Text(
            text = "Command ${command.commandId} expires ${formatCommandTime(command.expiresAt)}. Confirming executes only a MOCK result.",
            fontFamily = RubikFontFamily,
            fontSize = 11.sp,
            color = KaminariTheme.colors.textPrimary,
        )
        Text(
            text = "Nonce: ${command.nonce}",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            color = KaminariTheme.colors.textSecondary,
        )
        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            label = { Text("Repeat nonce to confirm") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("command_confirmation_input"),
            singleLine = true,
        )
        error?.let {
            NftyInlineNotice(text = it, isError = true)
        }
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
                Text("Confirm mock execution", fontSize = 11.sp)
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
                text = "Destructive • confirmation ${if (command.confirmedAt == null) "pending" else "accepted"}",
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

private fun formatCommandTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(timestamp))
