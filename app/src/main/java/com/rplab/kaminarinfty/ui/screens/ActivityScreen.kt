package com.rplab.kaminarinfty.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.ActivityFilter
import com.rplab.kaminarinfty.ActivityUiState
import com.rplab.kaminarinfty.model.ActivityEvent
import com.rplab.kaminarinfty.model.ActivitySeverity
import com.rplab.kaminarinfty.model.ActivityType
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.ui.components.ActivitySeverityBadge
import com.rplab.kaminarinfty.ui.components.NftyEmptyState
import com.rplab.kaminarinfty.ui.components.NftyLoadingIndicator
import com.rplab.kaminarinfty.ui.components.NftyTopBar
import com.rplab.kaminarinfty.ui.components.formatRelativeTime
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ActivityScreen(
    state: ActivityUiState,
    authState: AuthState,
    profileLabel: String,
    isSampleData: Boolean,
    onFilterChange: (ActivityFilter) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val visible = state.visibleEvents
    val grouped = visible.groupBy {
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(it.createdAt))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
    ) {
        NftyTopBar(
            title = "Activity",
            subtitle = "TIMELINE // $profileLabel",
            authState = authState,
            isSampleData = isSampleData,
            onSettingsClick = onSettingsClick,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityFilter.values().forEach { filter ->
                val selected = state.filter == filter
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
                        .clickable { onFilterChange(filter) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("activity_filter_${filter.name}")
                ) {
                    Text(
                        text = filter.label,
                        fontFamily = RubikFontFamily,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) KaminariTheme.colors.background
                        else KaminariTheme.colors.textSecondary,
                    )
                }
            }
            Text(
                text = "${visible.size} events",
                style = KaminariTheme.techTypography.metricSubtext,
                color = KaminariTheme.colors.textSecondary,
            )
        }

        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NftyLoadingIndicator(
                    status = "Reading the activity timeline…",
                    technicalDetail = "ROOM // ACTIVITY_EVENTS",
                )
            }

            state.error != null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center,
            ) {
                NftyEmptyState(
                    title = "Timeline unavailable",
                    message = state.error,
                    technicalCode = "ACTIVITY // READ_ERROR",
                    icon = Icons.Default.Error,
                )
            }

            state.events.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NftyEmptyState(
                    title = "No activity yet",
                    message = "Received messages and publish transitions appear here. Activity contains summaries, never message bodies.",
                    technicalCode = "ACTIVITY // 0_EVENTS",
                    icon = Icons.Default.History,
                )
            }

            visible.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NftyEmptyState(
                    title = "No matching events",
                    message = "No timeline entries match this filter.",
                    technicalCode = "FILTER // EMPTY_SET",
                    icon = Icons.Default.History,
                    actionLabel = "Show All",
                    onActionClick = { onFilterChange(ActivityFilter.ALL) },
                )
            }

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("activity_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                grouped.forEach { (day, events) ->
                    item(key = "day_$day") {
                        Text(
                            text = day,
                            style = KaminariTheme.techTypography.diagnosticCode,
                            color = KaminariTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                        )
                    }
                    items(events, key = { it.id }) { event ->
                        ActivityRow(event)
                    }
                }
                item {
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(event: ActivityEvent) {
    val color = severityColor(event.severity)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(0.8.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(9.dp))
            .padding(10.dp)
            .testTag("activity_event_${event.id}"),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = typeIcon(event.type, event.severity),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(15.dp),
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = typeLabel(event.type),
                        style = KaminariTheme.techTypography.diagnosticCode,
                        color = color,
                    )
                    ActivitySeverityBadge(event.severity)
                }
                Text(
                    text = event.summary,
                    fontFamily = RubikFontFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = KaminariTheme.colors.textPrimary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = event.topic?.let { "#$it" } ?: "No topic",
                        style = KaminariTheme.techTypography.topic,
                        color = KaminariTheme.colors.textSecondary,
                    )
                    Text(
                        text = formatRelativeTime(event.createdAt),
                        style = KaminariTheme.techTypography.timestamp,
                        color = KaminariTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun severityColor(severity: ActivitySeverity): Color = when (severity) {
    ActivitySeverity.INFO -> KaminariTheme.colors.textSecondary
    ActivitySeverity.SUCCESS -> KaminariTheme.colors.success
    ActivitySeverity.WARNING -> KaminariTheme.colors.warning
    ActivitySeverity.ERROR -> KaminariTheme.colors.error
}

private fun typeIcon(type: ActivityType, severity: ActivitySeverity) = when (type) {
    ActivityType.MESSAGE_RECEIVED -> Icons.Default.Inbox
    ActivityType.PUBLISH_ACCEPTED -> Icons.Default.CheckCircle
    ActivityType.PUBLISH_FAILED -> Icons.Default.Error
    ActivityType.PUBLISH_AMBIGUOUS -> Icons.Default.Warning
    ActivityType.PUBLISH_QUEUED,
    ActivityType.PUBLISH_STARTED,
    ActivityType.PUBLISH_RETRY,
    -> Icons.AutoMirrored.Filled.Send
    else -> if (severity == ActivitySeverity.ERROR) Icons.Default.Error else Icons.Default.NotificationsActive
}

private fun typeLabel(type: ActivityType): String = when (type) {
    ActivityType.MESSAGE_RECEIVED -> "MESSAGE RECEIVED"
    ActivityType.PUBLISH_QUEUED -> "PUBLISH QUEUED"
    ActivityType.PUBLISH_STARTED -> "PUBLISH STARTED"
    ActivityType.PUBLISH_ACCEPTED -> "PUBLISH ACCEPTED"
    ActivityType.PUBLISH_FAILED -> "PUBLISH FAILED"
    ActivityType.PUBLISH_AMBIGUOUS -> "PUBLISH AMBIGUOUS"
    ActivityType.PUBLISH_RETRY -> "PUBLISH RETRY"
    else -> type.name.replace('_', ' ')
}
