package com.rplab.kaminarinfty.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.rplab.kaminarinfty.ui.theme.KaminariTheme

@Composable
fun NftyTopicChip(
    topic: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bg = if (isSelected) {
        KaminariTheme.colors.accent.copy(alpha = 0.22f)
    } else {
        KaminariTheme.colors.surfaceSecondary
    }
    val borderColor = if (isSelected) {
        KaminariTheme.colors.accent
    } else {
        KaminariTheme.colors.border.copy(alpha = 0.6f)
    }
    val textColor = if (isSelected) {
        KaminariTheme.colors.accent
    } else {
        KaminariTheme.colors.textSecondary
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.75.dp, borderColor, RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Text(
            text = "#$topic",
            style = KaminariTheme.techTypography.topic,
            color = textColor,
        )
    }
}
