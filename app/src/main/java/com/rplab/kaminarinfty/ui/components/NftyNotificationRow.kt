package com.rplab.kaminarinfty.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeviceThermostat
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.model.NftyPriority
import com.rplab.kaminarinfty.ui.theme.AccentCategoryAmber
import com.rplab.kaminarinfty.ui.theme.AccentCategoryCoral
import com.rplab.kaminarinfty.ui.theme.AccentCategoryCyan
import com.rplab.kaminarinfty.ui.theme.AccentCategoryPink
import com.rplab.kaminarinfty.ui.theme.AccentCategoryPurple
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NftyNotificationRow(
    message: NftyMessage,
    onClick: () -> Unit,
    onToggleSave: () -> Unit,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
) {
    val (iconBg, iconVector) = getCategoryVisuals(message)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(
                width = 0.85.dp,
                color = KaminariTheme.colors.border.copy(alpha = if (KaminariTheme.colors.isDark) 0.85f else 0.7f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .testTag("notification_row_${message.id}")
            .padding(if (isCompact) 10.dp else 13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Category Icon container (Matching live telemetry card badges)
            Box(
                modifier = Modifier
                    .size(if (isCompact) 38.dp else 42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = iconVector,
                    contentDescription = message.topic,
                    tint = Color.White,
                    modifier = Modifier.size(if (isCompact) 19.dp else 21.dp)
                )
            }

            // Notification Text Content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = message.title ?: message.topic,
                            fontFamily = RubikFontFamily,
                            fontWeight = if (!message.isRead) FontWeight.Bold else FontWeight.SemiBold,
                            fontSize = 14.5.sp,
                            color = KaminariTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        // Unread indicator dot
                        if (!message.isRead) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(KaminariTheme.colors.accent)
                            )
                        }
                    }

                    // Priority Marker (Monospace badge)
                    NftyPriorityMarker(priority = message.priority)
                }

                Spacer(modifier = Modifier.height(3.dp))

                // Message snippet in clean compact sans-serif (Rubik)
                Text(
                    text = message.message,
                    fontFamily = RubikFontFamily,
                    fontSize = 13.sp,
                    color = KaminariTheme.colors.textSecondary,
                    maxLines = if (isCompact) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 18.sp,
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Technical Footer: Monospace topic identifier and timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = "#${message.topic}",
                            style = KaminariTheme.techTypography.topic,
                            color = KaminariTheme.colors.accent,
                        )

                        Text(
                            text = "•",
                            color = KaminariTheme.colors.textSecondary.copy(alpha = 0.5f),
                            style = KaminariTheme.techTypography.timestamp,
                        )

                        Text(
                            text = formatRelativeTime(message.time),
                            style = KaminariTheme.techTypography.timestamp,
                            color = KaminariTheme.colors.textSecondary,
                        )
                    }

                    // Bookmark Save Icon Button
                    IconButton(
                        onClick = onToggleSave,
                        modifier = Modifier
                            .size(28.dp)
                            .minimumInteractiveComponentSize()
                            .testTag("save_button_${message.id}")
                    ) {
                        Icon(
                            imageVector = if (message.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (message.isSaved) "Remove from saved" else "Save notification",
                            tint = if (message.isSaved) KaminariTheme.colors.accent else KaminariTheme.colors.textSecondary.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun getCategoryVisuals(message: NftyMessage): Pair<Color, ImageVector> {
    val topic = message.topic.lowercase()
    val tags = message.tags.map { it.lowercase() }

    return when {
        message.priority == NftyPriority.URGENT || tags.contains("ups") || tags.contains("power") ->
            Pair(AccentCategoryPink, Icons.Default.Power)
        tags.contains("temperature") || tags.contains("thermometer") ->
            Pair(AccentCategoryAmber, Icons.Default.DeviceThermostat)
        topic.contains("backup") || tags.contains("backup") || tags.contains("postgres") ->
            Pair(AccentCategoryCoral, Icons.Default.Storage)
        topic.contains("build") || tags.contains("docker") || tags.contains("ci-cd") ->
            Pair(AccentCategoryCyan, Icons.Default.Build)
        topic.contains("security") || tags.contains("security") || tags.contains("ssl") ->
            Pair(AccentCategoryPurple, Icons.Default.Lock)
        else ->
            Pair(AccentCategoryCyan, Icons.Default.Notifications)
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val min = 60 * 1000L
    val hour = 60 * min
    val day = 24 * hour

    return when {
        diff < 2 * min -> "Just now"
        diff < hour -> "${diff / min}m ago"
        diff < day -> "${diff / hour}h ago"
        diff < 2 * day -> "Yesterday"
        else -> {
            val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
