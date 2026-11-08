package com.rplab.kaminarinfty.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.model.NftyPriority
import com.rplab.kaminarinfty.ui.theme.KaminariTheme

@Composable
fun NftyPriorityMarker(
    priority: NftyPriority,
    modifier: Modifier = Modifier,
) {
    if (priority == NftyPriority.DEFAULT || priority == NftyPriority.LOW || priority == NftyPriority.MIN) {
        return
    }

    val (badgeBg, badgeBorder, badgeText) = when (priority) {
        NftyPriority.URGENT -> Triple(
            KaminariTheme.colors.error.copy(alpha = 0.16f),
            KaminariTheme.colors.error,
            KaminariTheme.colors.error,
        )
        NftyPriority.HIGH -> Triple(
            KaminariTheme.colors.warning.copy(alpha = 0.16f),
            KaminariTheme.colors.warning,
            KaminariTheme.colors.warning,
        )
        else -> Triple(Color.Transparent, Color.Transparent, Color.Transparent)
    }

    Box(
        modifier = modifier
            .background(badgeBg, RoundedCornerShape(4.dp))
            .border(0.85.dp, badgeBorder, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = priority.label,
            style = KaminariTheme.techTypography.httpStatus.copy(fontSize = 9.sp),
            color = badgeText,
        )
    }
}
