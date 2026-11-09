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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Outbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import com.rplab.kaminarinfty.PublishUiState
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.OutboxEntry
import com.rplab.kaminarinfty.model.OutboxState
import com.rplab.kaminarinfty.ui.components.NftyConfirmationSheet
import com.rplab.kaminarinfty.ui.components.NftyEmptyState
import com.rplab.kaminarinfty.ui.components.NftyInlineNotice
import com.rplab.kaminarinfty.ui.components.NftyLoadingIndicator
import com.rplab.kaminarinfty.ui.components.NftyTopBar
import com.rplab.kaminarinfty.ui.components.OutboxStatusBadge
import com.rplab.kaminarinfty.ui.components.formatRelativeTime
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun OutboxScreen(
    state: PublishUiState,
    authState: AuthState,
    profileLabel: String,
    isSampleData: Boolean,
    onRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onClearNotice: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedId by remember { mutableStateOf<String?>(null) }
    var cancelId by remember { mutableStateOf<String?>(null) }
    val entries = state.outbox

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
    ) {
        NftyTopBar(
            title = "Outbox",
            subtitle = "DURABLE QUEUE // $profileLabel",
            authState = authState,
            isSampleData = isSampleData,
            onSettingsClick = onSettingsClick,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            OutboxState.values().forEach { outboxState ->
                OutboxSummary(
                    label = outboxState.name,
                    count = entries.count { it.state == outboxState },
                    state = outboxState,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        state.notice?.let { notice ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NftyInlineNotice(text = notice, modifier = Modifier.weight(1f))
                Text(
                    text = "Dismiss",
                    modifier = Modifier
                        .clickable(onClick = onClearNotice)
                        .padding(4.dp)
                        .testTag("outbox_dismiss_notice"),
                    fontFamily = RubikFontFamily,
                    fontSize = 10.sp,
                    color = KaminariTheme.colors.textSecondary,
                )
            }
        }

        when {
            state.isOutboxLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NftyLoadingIndicator(
                    status = "Reading the durable outbox…",
                    technicalDetail = "ROOM // OUTBOX_ENTRIES",
                )
            }

            state.outboxError != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                NftyInlineNotice(
                    text = "Outbox unavailable: ${state.outboxError}",
                    isError = true,
                )
            }

            entries.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NftyEmptyState(
                    title = "Outbox is empty",
                    message = "Publishes you queue from Compose appear here. Nothing is sent automatically.",
                    technicalCode = "OUTBOX // 0_ENTRIES",
                    icon = Icons.Default.Outbox,
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("outbox_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                items(entries, key = { it.clientId }) { entry ->
                    OutboxCard(
                        entry = entry,
                        expanded = expandedId == entry.clientId,
                        onToggleExpand = {
                            expandedId = if (expandedId == entry.clientId) null else entry.clientId
                        },
                        onRetry = { onRetry(entry.clientId) },
                        onCancel = { cancelId = entry.clientId },
                    )
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Manual retry only. ACCEPTED is a server receipt, not delivery.",
                            style = KaminariTheme.techTypography.metricSubtext,
                            color = KaminariTheme.colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(64.dp))
                    }
                }
            }
        }
    }

    val cancelling = entries.firstOrNull { it.clientId == cancelId }
    if (cancelling != null) {
        NftyConfirmationSheet(
            title = "Remove queued publish?",
            message = "This removes the unsent entry from the outbox. No request was sent, and this cannot be undone.",
            confirmLabel = "Remove",
            isDestructive = true,
            onConfirm = {
                onCancel(cancelling.clientId)
                cancelId = null
            },
            onDismiss = { cancelId = null },
        )
    }
}

@Composable
private fun OutboxSummary(
    label: String,
    count: Int,
    state: OutboxState,
    modifier: Modifier = Modifier,
) {
    val color = when (state) {
        OutboxState.QUEUED -> KaminariTheme.colors.textSecondary
        OutboxState.SENDING -> KaminariTheme.colors.warning
        OutboxState.ACCEPTED -> KaminariTheme.colors.success
        OutboxState.FAILED -> KaminariTheme.colors.error
        OutboxState.AMBIGUOUS -> KaminariTheme.colors.warning
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(0.75.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
            .padding(horizontal = 5.dp, vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$count",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label.take(4),
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 7.sp,
            color = KaminariTheme.colors.textSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun OutboxCard(
    entry: OutboxEntry,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(
                0.85.dp,
                when (entry.state) {
                    OutboxState.FAILED -> KaminariTheme.colors.error.copy(alpha = 0.55f)
                    OutboxState.AMBIGUOUS -> KaminariTheme.colors.warning.copy(alpha = 0.55f)
                    else -> KaminariTheme.colors.border.copy(alpha = 0.7f)
                },
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onToggleExpand)
            .padding(12.dp)
            .testTag("outbox_entry_${entry.clientId}"),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "#${entry.topic}",
                    style = KaminariTheme.techTypography.topic,
                    color = KaminariTheme.colors.accent,
                    maxLines = 1,
                )
                OutboxStatusBadge(entry.state)
            }
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Hide details" else "Show details",
                tint = KaminariTheme.colors.textSecondary,
                modifier = Modifier.size(16.dp),
            )
        }

        entry.title?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                fontFamily = RubikFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = KaminariTheme.colors.textPrimary,
                maxLines = 1,
            )
        }
        Text(
            text = entry.body,
            fontFamily = RubikFontFamily,
            fontSize = 12.sp,
            color = KaminariTheme.colors.textSecondary,
            maxLines = if (expanded) 8 else 2,
            lineHeight = 16.sp,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${formatRelativeTime(entry.createdAt)} • attempt ${entry.attemptCount}",
                style = KaminariTheme.techTypography.metricSubtext,
                color = KaminariTheme.colors.textSecondary,
            )
            if (entry.state == OutboxState.ACCEPTED) {
                Text(
                    text = "SERVER ACCEPTED",
                    style = KaminariTheme.techTypography.diagnosticCode,
                    color = KaminariTheme.colors.success,
                )
            }
        }

        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(7.dp))
                    .background(KaminariTheme.colors.surfaceSecondary)
                    .padding(9.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutboxDetailRow("CLIENT ID", entry.clientId)
                    OutboxDetailRow(
                        "CREATED",
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(entry.createdAt)),
                    )
                    OutboxDetailRow(
                        "UPDATED",
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                            .format(Date(entry.updatedAt)),
                    )
                    OutboxDetailRow("PRIORITY", entry.priority.toString())
                    OutboxDetailRow("ATTEMPTS", entry.attemptCount.toString())
                    if (entry.tags.isNotEmpty()) {
                        OutboxDetailRow("TAGS", entry.tags.joinToString(", "))
                    }
                    entry.delay?.let { OutboxDetailRow("DELAY", it) }
                    entry.clickUrl?.let { OutboxDetailRow("CLICK", it) }
                    entry.attachmentUrl?.let { OutboxDetailRow("ATTACHMENT", it) }
                    entry.serverMessageId?.let { OutboxDetailRow("SERVER ID", it) }
                    entry.lastError?.let { OutboxDetailRow("LAST ERROR", it) }
                }
            }
        }

        val canSend = entry.state == OutboxState.QUEUED
        val canRetry = entry.state == OutboxState.FAILED && entry.retryable
        val canConfirmRetry = entry.state == OutboxState.AMBIGUOUS
        val canCancel = entry.state == OutboxState.QUEUED ||
            entry.state == OutboxState.FAILED ||
            entry.state == OutboxState.AMBIGUOUS

        if (canSend || canRetry || canConfirmRetry || canCancel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (canSend || canRetry) {
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(7.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.9.dp,
                            KaminariTheme.colors.accent,
                        ),
                        modifier = Modifier.testTag("outbox_retry_${entry.clientId}"),
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = KaminariTheme.colors.accent,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = if (canSend) "Send now" else "Retry",
                            fontFamily = RubikFontFamily,
                            fontSize = 11.sp,
                            color = KaminariTheme.colors.accent,
                        )
                    }
                }
                if (canConfirmRetry) {
                    OutlinedButton(
                        onClick = onRetry,
                        shape = RoundedCornerShape(7.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.9.dp,
                            KaminariTheme.colors.warning,
                        ),
                        modifier = Modifier.testTag("outbox_retry_${entry.clientId}"),
                    ) {
                        Text(
                            text = "Retry (may duplicate)…",
                            fontFamily = RubikFontFamily,
                            fontSize = 11.sp,
                            color = KaminariTheme.colors.warning,
                        )
                    }
                }
                if (canCancel) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(7.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            0.9.dp,
                            KaminariTheme.colors.border,
                        ),
                        modifier = Modifier.testTag("outbox_cancel_${entry.clientId}"),
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = null,
                            tint = KaminariTheme.colors.textSecondary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                        Text(
                            text = "Remove",
                            fontFamily = RubikFontFamily,
                            fontSize = 11.sp,
                            color = KaminariTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OutboxDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = KaminariTheme.techTypography.diagnosticCode,
            color = KaminariTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 1.dp),
        )
        Text(
            text = value,
            style = KaminariTheme.techTypography.metricSubtext,
            color = KaminariTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}
