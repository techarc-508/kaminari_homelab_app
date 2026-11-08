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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.InboxFilter
import com.rplab.kaminarinfty.SimulationState
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.NftyPriority
import com.rplab.kaminarinfty.model.NftyErrorCategory
import com.rplab.kaminarinfty.ui.components.NftyEmptyState
import com.rplab.kaminarinfty.ui.components.NftyErrorState
import com.rplab.kaminarinfty.ui.components.NftyLoadingIndicator
import com.rplab.kaminarinfty.ui.components.NftyNotificationRow
import com.rplab.kaminarinfty.ui.components.NftyTopBar
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily

@Composable
fun InboxScreen(
    messages: List<NftyMessage>,
    authState: AuthState,
    activeFilter: InboxFilter,
    selectedTopicFilter: String?,
    simulationState: SimulationState,
    isSampleData: Boolean,
    isCompactDensity: Boolean = false,
    errorCategory: NftyErrorCategory = NftyErrorCategory.UNREACHABLE,
    customErrorMessage: String? = null,
    onFilterChange: (InboxFilter) -> Unit,
    onClearTopicFilter: () -> Unit,
    onSimulationChange: (SimulationState) -> Unit,
    onRetryStream: (() -> Unit)? = null,
    onMessageClick: (NftyMessage) -> Unit,
    onToggleSave: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onSearchClick: () -> Unit,
    onToggleThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now = System.currentTimeMillis()
    val day = 24 * 3600 * 1000L

    val filteredMessages = messages.filter { msg ->
        val matchesTopic = selectedTopicFilter == null || msg.topic.equals(selectedTopicFilter, ignoreCase = true)
        val matchesFilter = when (activeFilter) {
            InboxFilter.ALL -> true
            InboxFilter.UNREAD -> !msg.isRead
            InboxFilter.HIGH_URGENT -> msg.priority == NftyPriority.HIGH || msg.priority == NftyPriority.URGENT
            InboxFilter.TODAY -> (now - msg.time) < day
        }
        matchesTopic && matchesFilter
    }

    val todayMessages = filteredMessages.filter { (now - it.time) < day }
    val yesterdayMessages = filteredMessages.filter {
        val diff = now - it.time
        diff in day until (2 * day)
    }
    val earlierMessages = filteredMessages.filter { (now - it.time) >= (2 * day) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
    ) {
        // Technical Header with live clock and Nominal status badge
        NftyTopBar(
            title = "KAMINARI-FUJI",
            subtitle = "pve node • nfty.rplab.qzz.io",
            authState = authState,
            isSampleData = isSampleData,
            onSearchClick = onSearchClick,
            onToggleThemeClick = onToggleThemeClick,
            onSettingsClick = onSettingsClick,
        )

        // Telemetry Monitor Strip (Directly inspired by PVE node monitor metric cards in user's screenshots)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TelemetryMetricCard(
                title = "STREAM",
                value = if (authState.isConnected) "ACTIVE" else "STANDBY",
                subtext = "HTTP/2 SSE",
                valueColor = if (authState.isConnected) KaminariTheme.colors.success else KaminariTheme.colors.warning,
            )
            TelemetryMetricCard(
                title = "INTERNET",
                value = "38.4 ms",
                subtext = "HTTPS // 443",
                valueColor = KaminariTheme.colors.accent,
            )
            TelemetryMetricCard(
                title = "UNREAD QUEUE",
                value = "${messages.count { !it.isRead }} Alerts",
                subtext = "${messages.count { it.priority == NftyPriority.URGENT }} urgent",
                valueColor = if (messages.any { it.priority == NftyPriority.URGENT && !it.isRead }) KaminariTheme.colors.error else KaminariTheme.colors.textPrimary,
            )
            TelemetryMetricCard(
                title = "LOCAL CACHE",
                value = "${messages.size} Msgs",
                subtext = "Room SQLite",
                valueColor = KaminariTheme.colors.textPrimary,
            )
        }

        // Filter Pills Strip (Matching Screenshot 2 exact aesthetic)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            InboxFilter.values().forEach { filter ->
                val isSelected = activeFilter == filter
                val pillBg = when {
                    isSelected && KaminariTheme.colors.isDark -> KaminariTheme.colors.accent
                    isSelected && !KaminariTheme.colors.isDark -> KaminariTheme.colors.accent
                    else -> KaminariTheme.colors.surfaceSecondary.copy(alpha = 0.5f)
                }
                val pillBorder = when {
                    isSelected -> KaminariTheme.colors.accent
                    else -> KaminariTheme.colors.border.copy(alpha = 0.6f)
                }
                val pillTextColor = when {
                    isSelected -> if (KaminariTheme.colors.isDark) KaminariTheme.colors.background else KaminariTheme.colors.surfaceMain
                    else -> KaminariTheme.colors.textSecondary
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(pillBg)
                        .border(0.85.dp, pillBorder, RoundedCornerShape(8.dp))
                        .clickable { onFilterChange(filter) }
                        .padding(horizontal = 13.dp, vertical = 6.dp)
                        .testTag("inbox_filter_${filter.name}")
                ) {
                    Text(
                        text = filter.label,
                        fontFamily = RubikFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = pillTextColor,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Quick Mark-Read Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(KaminariTheme.colors.surfaceSecondary)
                    .border(0.75.dp, KaminariTheme.colors.border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .clickable(onClick = onMarkAllRead)
                    .padding(horizontal = 9.dp, vertical = 6.dp)
                    .testTag("mark_all_read_button")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.DoneAll,
                        contentDescription = "Mark all read",
                        tint = KaminariTheme.colors.textSecondary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Read all",
                        fontFamily = RubikFontFamily,
                        fontSize = 11.sp,
                        color = KaminariTheme.colors.textSecondary,
                    )
                }
            }
        }

        // Active Topic Filter Chip
        if (selectedTopicFilter != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(KaminariTheme.colors.surfaceMain)
                    .border(0.75.dp, KaminariTheme.colors.accent.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "FILTER // TOPIC: #$selectedTopicFilter",
                        style = KaminariTheme.techTypography.topic,
                        color = KaminariTheme.colors.accent,
                    )
                    IconButton(onClick = onClearTopicFilter, modifier = Modifier.size(18.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear topic filter",
                            tint = KaminariTheme.colors.textSecondary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        // Offline Banner
        if (simulationState == SimulationState.OFFLINE || authState == AuthState.DISCONNECTED) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 3.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(KaminariTheme.colors.warning.copy(alpha = 0.15f))
                    .border(0.85.dp, KaminariTheme.colors.warning.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.WifiOff,
                        contentDescription = "Offline",
                        tint = KaminariTheme.colors.warning,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        text = "OFFLINE MODE // CACHED DATA ONLY",
                        style = KaminariTheme.techTypography.httpStatus,
                        color = KaminariTheme.colors.warning,
                    )
                }
            }
        }

        // Main List or State Views
        when (simulationState) {
            SimulationState.LOADING -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    NftyLoadingIndicator(
                        status = "Subscribing to ntfy JSON event stream...",
                        technicalDetail = "GET /topics/json?poll=0 // STREAM CONNECTING"
                    )
                }
            }

            SimulationState.ERROR -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    NftyErrorState(
                        errorCategory = errorCategory,
                        customMessage = customErrorMessage,
                        onRetryClick = {
                            if (onRetryStream != null) onRetryStream()
                            else onSimulationChange(SimulationState.NORMAL)
                        }
                    )
                }
            }

            SimulationState.EMPTY -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NftyEmptyState(
                        title = "No notifications in inbox",
                        message = "Listening for inbound events on subscribed topics. New homelab notifications will arrive in real-time.",
                        actionLabel = "Reload Sample Data",
                        onActionClick = { onSimulationChange(SimulationState.NORMAL) },
                    )
                }
            }

            SimulationState.NORMAL, SimulationState.OFFLINE -> {
                if (filteredMessages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        NftyEmptyState(
                            title = "No matching notifications",
                            message = "Try selecting a different filter above or clear your topic selection.",
                            actionLabel = "Show All Notifications",
                            onActionClick = { onFilterChange(InboxFilter.ALL) }
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("inbox_notification_list"),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (todayMessages.isNotEmpty()) {
                            item(key = "header_today") {
                                SectionHeader(title = "TODAY", count = todayMessages.size)
                            }
                            items(todayMessages, key = { it.id }) { message ->
                                NftyNotificationRow(
                                    message = message,
                                    isCompact = isCompactDensity,
                                    onClick = { onMessageClick(message) },
                                    onToggleSave = { onToggleSave(message.id) },
                                )
                            }
                        }

                        if (yesterdayMessages.isNotEmpty()) {
                            item(key = "header_yesterday") {
                                SectionHeader(title = "YESTERDAY", count = yesterdayMessages.size)
                            }
                            items(yesterdayMessages, key = { it.id }) { message ->
                                NftyNotificationRow(
                                    message = message,
                                    isCompact = isCompactDensity,
                                    onClick = { onMessageClick(message) },
                                    onToggleSave = { onToggleSave(message.id) },
                                )
                            }
                        }

                        if (earlierMessages.isNotEmpty()) {
                            item(key = "header_earlier") {
                                SectionHeader(title = "EARLIER", count = earlierMessages.size)
                            }
                            items(earlierMessages, key = { it.id }) { message ->
                                NftyNotificationRow(
                                    message = message,
                                    isCompact = isCompactDensity,
                                    onClick = { onMessageClick(message) },
                                    onToggleSave = { onToggleSave(message.id) },
                                )
                            }
                        }

                        item(key = "inbox_footer_spacer") {
                            Spacer(modifier = Modifier.height(72.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TelemetryMetricCard(
    title: String,
    value: String,
    subtext: String,
    valueColor: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(0.85.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = KaminariTheme.techTypography.metricSubtext,
                    color = KaminariTheme.colors.textSecondary,
                )
                Text(
                    text = value,
                    style = KaminariTheme.techTypography.metricValue,
                    color = valueColor,
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtext,
                style = KaminariTheme.techTypography.metricSubtext,
                color = KaminariTheme.colors.textSecondary.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = KaminariTheme.techTypography.httpStatus.copy(letterSpacing = 1.sp),
            color = KaminariTheme.colors.textSecondary,
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(KaminariTheme.colors.surfaceSecondary)
                .border(0.5.dp, KaminariTheme.colors.border.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "$count",
                style = KaminariTheme.techTypography.httpStatus,
                color = KaminariTheme.colors.textSecondary,
            )
        }
    }
}
