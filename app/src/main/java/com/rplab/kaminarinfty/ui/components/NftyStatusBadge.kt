package com.rplab.kaminarinfty.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.model.ActivitySeverity
import com.rplab.kaminarinfty.model.OutboxState
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme

/** Text + icon status; never relies on color alone. */
@Composable
fun NftyStatusBadge(
    label: String,
    color: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(color.copy(alpha = 0.14f))
            .border(0.8.dp, color.copy(alpha = 0.75f), RoundedCornerShape(5.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(11.dp),
        )
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
        )
    }
}

@Composable
fun OutboxStatusBadge(state: OutboxState, modifier: Modifier = Modifier) {
    val color = when (state) {
        OutboxState.QUEUED -> KaminariTheme.colors.textSecondary
        OutboxState.SENDING -> KaminariTheme.colors.warning
        OutboxState.ACCEPTED -> KaminariTheme.colors.success
        OutboxState.FAILED -> KaminariTheme.colors.error
        OutboxState.AMBIGUOUS -> KaminariTheme.colors.warning
    }
    val icon = when (state) {
        OutboxState.QUEUED -> Icons.Default.Pending
        OutboxState.SENDING -> Icons.Default.Sync
        OutboxState.ACCEPTED -> Icons.Default.CheckCircle
        OutboxState.FAILED -> Icons.Default.Error
        OutboxState.AMBIGUOUS -> Icons.Default.Warning
    }
    NftyStatusBadge(label = state.name, color = color, icon = icon, modifier = modifier)
}

@Composable
fun ActivitySeverityBadge(severity: ActivitySeverity, modifier: Modifier = Modifier) {
    val color = when (severity) {
        ActivitySeverity.INFO -> KaminariTheme.colors.textSecondary
        ActivitySeverity.SUCCESS -> KaminariTheme.colors.success
        ActivitySeverity.WARNING -> KaminariTheme.colors.warning
        ActivitySeverity.ERROR -> KaminariTheme.colors.error
    }
    val icon = when (severity) {
        ActivitySeverity.INFO -> Icons.Default.Pending
        ActivitySeverity.SUCCESS -> Icons.Default.CheckCircle
        ActivitySeverity.WARNING -> Icons.Default.Warning
        ActivitySeverity.ERROR -> Icons.Default.Error
    }
    NftyStatusBadge(label = severity.name, color = color, icon = icon, modifier = modifier)
}

@Composable
fun NftyInlineNotice(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(7.dp))
            .background(
                (if (isError) KaminariTheme.colors.error else KaminariTheme.colors.accent)
                    .copy(alpha = 0.10f)
            )
            .border(
                0.8.dp,
                (if (isError) KaminariTheme.colors.error else KaminariTheme.colors.accent)
                    .copy(alpha = 0.5f),
                RoundedCornerShape(7.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            lineHeight = 14.sp,
            color = if (isError) KaminariTheme.colors.error else KaminariTheme.colors.textPrimary,
        )
    }
}
