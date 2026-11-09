package com.rplab.kaminarinfty.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.PublishOutcome
import com.rplab.kaminarinfty.PublishUiState
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.ui.components.NftyInlineNotice
import com.rplab.kaminarinfty.ui.components.NftyTopBar
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily

@Composable
fun PublishScreen(
    state: PublishUiState,
    authState: AuthState,
    profileLabel: String,
    isSampleData: Boolean,
    onTopicChange: (String) -> Unit,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onPriorityChange: (Int) -> Unit,
    onTagsChange: (String) -> Unit,
    onClickUrlChange: (String) -> Unit,
    onAttachmentUrlChange: (String) -> Unit,
    onDelayChange: (String) -> Unit,
    onToggleAdvanced: () -> Unit,
    onSend: () -> Unit,
    onRetryLast: () -> Unit,
    onDismissOutcome: () -> Unit,
    onClearValidation: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val form = state.form
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
    ) {
        NftyTopBar(
            title = "Compose",
            subtitle = "PUBLISH // $profileLabel",
            authState = authState,
            isSampleData = isSampleData,
            onSettingsClick = onSettingsClick,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.activeProfileId == null) {
                NftyInlineNotice(
                    text = "Select a server profile in Settings before publishing.",
                    isError = true,
                )
            }

            PublishCard(title = "MESSAGE") {
                if (state.topics.isNotEmpty()) {
                    Text(
                        text = "SUBSCRIBED TOPICS",
                        style = KaminariTheme.techTypography.diagnosticCode,
                        color = KaminariTheme.colors.textSecondary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        state.topics.forEach { topic ->
                            val selected = form.topic == topic.name
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(
                                        if (selected) KaminariTheme.colors.accent
                                        else KaminariTheme.colors.surfaceSecondary
                                    )
                                    .border(
                                        0.8.dp,
                                        if (selected) KaminariTheme.colors.accent
                                        else KaminariTheme.colors.border,
                                        RoundedCornerShape(7.dp),
                                    )
                                    .clickable { onTopicChange(topic.name) }
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                                    .testTag("compose_topic_${topic.name}")
                            ) {
                                Text(
                                    text = "#${topic.name}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = if (selected) KaminariTheme.colors.background
                                    else KaminariTheme.colors.textPrimary,
                                )
                            }
                        }
                    }
                }

                PublishTextField(
                    value = form.topic,
                    onValueChange = onTopicChange,
                    label = "Topic",
                    placeholder = "homelab-alerts",
                    testTag = "compose_topic_input",
                )

                PublishTextField(
                    value = form.title,
                    onValueChange = onTitleChange,
                    label = "Title (optional)",
                    placeholder = "Short heading",
                    testTag = "compose_title_input",
                )

                OutlinedTextField(
                    value = form.body,
                    onValueChange = onBodyChange,
                    label = { Text("Message", fontFamily = RubikFontFamily) },
                    placeholder = {
                        Text(
                            "Plain-text payload. Markdown is not rendered.",
                            fontFamily = RubikFontFamily,
                            fontSize = 12.sp,
                        )
                    },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("compose_body_input"),
                    colors = publishFieldColors(),
                    shape = RoundedCornerShape(8.dp),
                )
                Text(
                    text = "${form.body.length} characters",
                    style = KaminariTheme.techTypography.metricSubtext,
                    color = KaminariTheme.colors.textSecondary,
                )
            }

            PublishCard(title = "DELIVERY OPTIONS") {
                Text(
                    text = "PRIORITY",
                    style = KaminariTheme.techTypography.diagnosticCode,
                    color = KaminariTheme.colors.textSecondary,
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    (1..5).forEach { priority ->
                        val selected = form.priority == priority
                        val label = when (priority) {
                            1 -> "1 Min"
                            2 -> "2 Low"
                            3 -> "3 Default"
                            4 -> "4 High"
                            else -> "5 Urgent"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .background(
                                    if (selected) KaminariTheme.colors.accent
                                    else KaminariTheme.colors.surfaceSecondary
                                )
                                .border(
                                    0.8.dp,
                                    if (selected) KaminariTheme.colors.accent
                                    else KaminariTheme.colors.border,
                                    RoundedCornerShape(7.dp),
                                )
                                .clickable { onPriorityChange(priority) }
                                .padding(horizontal = 9.dp, vertical = 6.dp)
                                .testTag("compose_priority_$priority")
                        ) {
                            Text(
                                text = label,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.sp,
                                color = if (selected) KaminariTheme.colors.background
                                else KaminariTheme.colors.textPrimary,
                            )
                        }
                    }
                }

                PublishTextField(
                    value = form.tagsRaw,
                    onValueChange = onTagsChange,
                    label = "Tags (optional)",
                    placeholder = "warning, homelab",
                    testTag = "compose_tags_input",
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(KaminariTheme.colors.surfaceSecondary)
                        .clickable(onClick = onToggleAdvanced)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("compose_advanced_toggle"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            text = "Links, attachment URL and delay",
                            fontFamily = RubikFontFamily,
                            fontSize = 12.sp,
                            color = KaminariTheme.colors.textPrimary,
                        )
                        Text(
                            text = "Optional ntfy metadata",
                            style = KaminariTheme.techTypography.metricSubtext,
                            color = KaminariTheme.colors.textSecondary,
                        )
                    }
                    Icon(
                        imageVector = if (form.showAdvanced) Icons.Default.ExpandLess
                        else Icons.Default.ExpandMore,
                        contentDescription = if (form.showAdvanced) "Hide advanced fields"
                        else "Show advanced fields",
                        tint = KaminariTheme.colors.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                AnimatedVisibility(visible = form.showAdvanced) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PublishTextField(
                            value = form.clickUrl,
                            onValueChange = onClickUrlChange,
                            label = "Click URL",
                            placeholder = "https://example.test/details",
                            leadingIcon = true,
                            testTag = "compose_click_url_input",
                        )
                        PublishTextField(
                            value = form.attachmentUrl,
                            onValueChange = onAttachmentUrlChange,
                            label = "Attachment URL",
                            placeholder = "https://example.test/file.pdf",
                            leadingIcon = false,
                            testTag = "compose_attachment_url_input",
                        )
                        Text(
                            text = "The ntfy server fetches attachment URLs. Binary upload is not supported.",
                            style = KaminariTheme.techTypography.metricSubtext,
                            color = KaminariTheme.colors.textSecondary,
                        )
                        PublishTextField(
                            value = form.delay,
                            onValueChange = onDelayChange,
                            label = "Delay (optional)",
                            placeholder = "30m, 2h or a Unix timestamp",
                            leadingIcon = false,
                            testTag = "compose_delay_input",
                        )
                    }
                }
            }

            state.validationError?.let { error ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(7.dp))
                        .background(KaminariTheme.colors.error.copy(alpha = 0.10f))
                        .border(0.8.dp, KaminariTheme.colors.error.copy(alpha = 0.6f), RoundedCornerShape(7.dp))
                        .padding(10.dp)
                        .testTag("compose_validation_error"),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = null,
                        tint = KaminariTheme.colors.error,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = error,
                        modifier = Modifier.weight(1f),
                        fontFamily = RubikFontFamily,
                        fontSize = 12.sp,
                        color = KaminariTheme.colors.textPrimary,
                        lineHeight = 16.sp,
                    )
                    Text(
                        text = "Dismiss",
                        modifier = Modifier.clickable(onClick = onClearValidation),
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        color = KaminariTheme.colors.error,
                    )
                }
            }

            Button(
                onClick = onSend,
                enabled = !state.isSending && state.activeProfileId != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .testTag("compose_send_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaminariTheme.colors.accent,
                    contentColor = KaminariTheme.colors.background,
                ),
            ) {
                if (state.isSending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = KaminariTheme.colors.background,
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Queueing and sending…", fontFamily = RubikFontFamily)
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text("Queue & Send", fontFamily = RubikFontFamily, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                text = "Accepted means the ntfy server accepted the request. It is not a delivery or read receipt.",
                style = KaminariTheme.techTypography.metricSubtext,
                color = KaminariTheme.colors.textSecondary,
                modifier = Modifier.testTag("compose_acceptance_note"),
            )

            state.outcome?.let { outcome ->
                PublishOutcomeCard(
                    outcome = outcome,
                    isSending = state.isSending,
                    onRetry = onRetryLast,
                    onDismiss = onDismissOutcome,
                )
            }

            state.notice?.let { notice ->
                NftyInlineNotice(text = notice)
            }

            Spacer(modifier = Modifier.height(72.dp))
        }
    }
}

@Composable
private fun PublishOutcomeCard(
    outcome: PublishOutcome,
    isSending: Boolean,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
) {
    val color = when (outcome) {
        is PublishOutcome.Accepted -> KaminariTheme.colors.success
        is PublishOutcome.Failed -> KaminariTheme.colors.error
        is PublishOutcome.Ambiguous -> KaminariTheme.colors.warning
        is PublishOutcome.Busy -> KaminariTheme.colors.warning
    }
    val icon = when (outcome) {
        is PublishOutcome.Accepted -> Icons.Default.CheckCircle
        is PublishOutcome.Failed -> Icons.Default.Error
        is PublishOutcome.Ambiguous -> Icons.Default.Warning
        is PublishOutcome.Busy -> Icons.Default.Warning
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(0.9.dp, color.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(12.dp)
            .testTag("compose_outcome_card"),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Text(
                    text = when (outcome) {
                        is PublishOutcome.Accepted -> "ACCEPTED BY SERVER"
                        is PublishOutcome.Failed -> "PUBLISH FAILED"
                        is PublishOutcome.Ambiguous -> "OUTCOME UNKNOWN"
                        is PublishOutcome.Busy -> "ALREADY SENDING"
                    },
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
            }
            when (outcome) {
                is PublishOutcome.Accepted -> {
                    Text(
                        text = "The server accepted this publish. No recipient delivery receipt exists.",
                        fontFamily = RubikFontFamily,
                        fontSize = 12.sp,
                        color = KaminariTheme.colors.textPrimary,
                    )
                    outcome.serverMessageId?.let {
                        Text(
                            text = "SERVER ID // $it",
                            style = KaminariTheme.techTypography.diagnosticCode,
                            color = KaminariTheme.colors.textSecondary,
                        )
                    }
                    Text(
                        text = "The draft fields were cleared; the topic and priority are kept.",
                        style = KaminariTheme.techTypography.metricSubtext,
                        color = KaminariTheme.colors.textSecondary,
                    )
                }
                is PublishOutcome.Failed -> {
                    Text(
                        text = outcome.reason,
                        fontFamily = RubikFontFamily,
                        fontSize = 12.sp,
                        color = KaminariTheme.colors.textPrimary,
                        lineHeight = 16.sp,
                    )
                    Text(
                        text = if (outcome.retryable) {
                            "The draft is preserved. Retrying sends the same queued entry."
                        } else {
                            "This failure is not retryable. Fix the input and create a new publish."
                        },
                        style = KaminariTheme.techTypography.metricSubtext,
                        color = KaminariTheme.colors.textSecondary,
                    )
                }
                is PublishOutcome.Ambiguous -> {
                    Text(
                        text = outcome.reason,
                        fontFamily = RubikFontFamily,
                        fontSize = 12.sp,
                        color = KaminariTheme.colors.textPrimary,
                        lineHeight = 16.sp,
                    )
                    Text(
                        text = "The server may already have accepted it. A retry can duplicate the publish and needs explicit confirmation.",
                        style = KaminariTheme.techTypography.metricSubtext,
                        color = KaminariTheme.colors.warning,
                    )
                }
                is PublishOutcome.Busy -> {
                    Text(
                        text = "Another send already claimed this entry. No second request was made.",
                        fontFamily = RubikFontFamily,
                        fontSize = 12.sp,
                        color = KaminariTheme.colors.textPrimary,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val canRetry = when (outcome) {
                    is PublishOutcome.Failed -> outcome.retryable
                    is PublishOutcome.Ambiguous -> true
                    else -> false
                }
                if (canRetry) {
                    OutlinedButton(
                        onClick = onRetry,
                        enabled = !isSending,
                        shape = RoundedCornerShape(7.dp),
                        border = androidx.compose.foundation.BorderStroke(0.9.dp, color),
                        modifier = Modifier.testTag("compose_retry_button"),
                    ) {
                        Text(
                            text = if (outcome is PublishOutcome.Ambiguous) "Retry (may duplicate)…" else "Retry",
                            fontFamily = RubikFontFamily,
                            fontSize = 11.sp,
                            color = color,
                        )
                    }
                }
                Text(
                    text = "Dismiss",
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                        .testTag("compose_dismiss_outcome"),
                    fontFamily = RubikFontFamily,
                    fontSize = 11.sp,
                    color = KaminariTheme.colors.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun PublishCard(title: String, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(0.85.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
            .padding(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(
                text = title,
                style = KaminariTheme.techTypography.diagnosticCode,
                color = KaminariTheme.colors.accent,
            )
            content()
        }
    }
}

@Composable
private fun PublishTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    testTag: String,
    leadingIcon: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = RubikFontFamily) },
        placeholder = {
            Text(placeholder, fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp)
        },
        leadingIcon = if (leadingIcon) {
            {
                Icon(
                    imageVector = Icons.Default.Link,
                    contentDescription = null,
                    tint = KaminariTheme.colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = publishFieldColors(),
        shape = RoundedCornerShape(8.dp),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.sp,
            color = KaminariTheme.colors.textPrimary,
        ),
    )
}

@Composable
private fun publishFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = KaminariTheme.colors.accent,
    unfocusedBorderColor = KaminariTheme.colors.border,
    focusedContainerColor = KaminariTheme.colors.surfaceSecondary,
    unfocusedContainerColor = KaminariTheme.colors.surfaceSecondary,
)
