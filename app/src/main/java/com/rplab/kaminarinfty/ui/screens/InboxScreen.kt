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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.rplab.kaminarinfty.InboxFilter
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyErrorCategory
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.NftyPriority
import com.rplab.kaminarinfty.ui.components.NftyEmptyState
import com.rplab.kaminarinfty.ui.components.NftyErrorState
import com.rplab.kaminarinfty.ui.components.NftyLoadingIndicator
import com.rplab.kaminarinfty.ui.components.NftyNotificationRow
import com.rplab.kaminarinfty.ui.components.NftySearchField
import com.rplab.kaminarinfty.ui.components.NftyTopBar
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily

@Composable
fun InboxScreen(
    messages: List<NftyMessage>,
    serverUrl: String,
    authState: AuthState,
    activeFilter: InboxFilter,
    selectedTopicFilter: String?,
    isSampleData: Boolean,
    isCompactDensity: Boolean = false,
    errorCategory: NftyErrorCategory = NftyErrorCategory.UNREACHABLE,
    customErrorMessage: String? = null,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    onFilterChange: (InboxFilter) -> Unit,
    onClearTopicFilter: () -> Unit,
    onRetryStream: (() -> Unit)? = null,
    onRefresh: () -> Unit = {},
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
            InboxFilter.SAVED -> msg.isSaved
            InboxFilter.HIGH_URGENT -> msg.priority == NftyPriority.HIGH || msg.priority == NftyPriority.URGENT
            InboxFilter.TODAY -> (now - msg.time) < day
        }
        val query = searchQuery.trim()
        val matchesQuery = query.isBlank() ||
            msg.title?.contains(query, ignoreCase = true) == true ||
            msg.message.contains(query, ignoreCase = true) ||
            msg.topic.contains(query, ignoreCase = true) ||
            msg.tags.any { it.contains(query, ignoreCase = true) }
        matchesTopic && matchesFilter && matchesQuery
    }

    val todayMessages = filteredMessages.filter { (now - it.time) < day }
    val yesterdayMessages = filteredMessages.filter { (now - it.time) in day until (2 * day) }
    val earlierMessages = filteredMessages.filter { (now - it.time) >= (2 * day) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
    ) {
        NftyTopBar(
            title = "KAMINARI-FUJI",
            subtitle = serverUrl.removePrefix("https://").removePrefix("http://").ifBlank {
                "no server configured"
            },
            authState = authState,
            isSampleData = isSampleData,
            onSearchClick = onSearchClick,
            onToggleThemeClick = onToggleThemeClick,
            onSettingsClick = onSettingsClick,
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
        ) {
            NftySearchField(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                placeholder = "Search title, message, topic or tag…",
            )
        }

        // Live metrics only — every value is derived from real app state.
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
                value = when (authState) {
                    AuthState.GUEST, AuthState.AUTHENTICATED -> "ACTIVE"
                    AuthState.CONNECTING, AuthState.RECONNECTING -> "LINKING"
                    else -> "STANDBY"
                },
                subtext = if (isSampleData) "demo dataset" else authState.statusLabel,
                valueColor = if (authState.isConnected) KaminariTheme.colors.success else KaminariTheme.colors.warning,
            )
            TelemetryMetricCard(
                title = "UNREAD",
                value = "${messages.count { !it.isRead }}",
                subtext = "${messages.count { it.priority == NftyPriority.URGENT }} urgent",
                valueColor = if (messages.any { it.priority == NftyPriority.URGENT && !it.isRead }) {
                    KaminariTheme.colors.error
                } else {
                    KaminariTheme.colors.textPrimary
                },
            )
            TelemetryMetricCard(
                title = "LOCAL HISTORY",
                value = "${messages.size}",
                subtext = if (isSampleData) "sample rows" else "device database",
                valueColor = KaminariTheme.colors.textPrimary,
            )
        }

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
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isSelected) KaminariTheme.colors.accent
                            else KaminariTheme.colors.surfaceSecondary.copy(alpha = 0.5f)
                        )
                        .border(
                            0.85.dp,
                            if (isSelected) KaminariTheme.colors.accent
                            else KaminariTheme.colors.border.copy(alpha = 0.6f),
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { onFilterChange(filter) }
                        .padding(horizontal = 13.dp, vertical = 6.dp)
                        .testTag("inbox_filter_${filter.name}")
                ) {
                    Text(
                        text = filter.label,
                        fontFamily = RubikFontFamily,
                        fontSize = 12.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) KaminariTheme.colors.background else KaminariTheme.colors.textSecondary,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KaminariTheme.colors.surfaceSecondary)
                    .border(0.75.dp, KaminariTheme.colors.border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .testTag("inbox_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh live stream and missed history",
                    tint = KaminariTheme.colors.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
            }

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

        if (authState == AuthState.DISCONNECTED || authState == AuthState.RECONNECTING) {
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
                        text = if (authState == AuthState.RECONNECTING) {
                            "RECONNECTING // CACHED HISTORY SHOWN"
                        } else {
                            "OFFLINE // CACHED HISTORY ONLY"
                        },
                        style = KaminariTheme.techTypography.httpStatus,
                        color = KaminariTheme.colors.warning,
                    )
                }
            }
        }

        when {
            messages.isEmpty() &&
                (authState == AuthState.CONNECTING || authState == AuthState.RECONNECTING) -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    NftyLoadingIndicator(
                        status = "Subscribing to ntfy JSON event stream…",
                        technicalDetail = "GET /{topics}/json // STREAM CONNECTING"
                    )
                }
            }

            messages.isEmpty() &&
                (authState == AuthState.AUTHENTICATION_FAILED ||
                    authState == AuthState.PERMISSION_DENIED ||
                    customErrorMessage != null) -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    NftyErrorState(
                        errorCategory = errorCategory,
                        customMessage = customErrorMessage,
                        onRetryClick = onRetryStream,
                    )
                }
            }

            messages.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NftyEmptyState(
                        title = "No notifications yet",
                        message = when (authState) {
                            AuthState.NOT_CONFIGURED ->
                                "Configure your ntfy server and subscribe to a topic in Setup."
                            AuthState.GUEST ->
                                "Connected as guest. Messages published to subscribed topics will appear here."
                            else ->
                                "Listening for events on subscribed topics. Published messages will appear here."
                        },
                        technicalCode = "SYS.STAT // EMPTY",
                    )
                }
            }

            filteredMessages.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NftyEmptyState(
                        title = if (searchQuery.isBlank()) {
                            "No matching notifications"
                        } else {
                            "No search results"
                        },
                        message = if (searchQuery.isBlank()) {
                            "Try selecting a different filter above or clear your topic selection."
                        } else {
                            "No cached notification matches \"$searchQuery\". Try a broader search or another filter."
                        },
                        actionLabel = "Show All Notifications",
                        onActionClick = {
                            onFilterChange(InboxFilter.ALL)
                            onSearchQueryChange("")
                        },
                    )
                }
            }

            else -> {
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

@Composable
private fun TelemetryMetricCard(
    title: String,
    value: String,
    subtext: String,
    valueColor: Color,
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
