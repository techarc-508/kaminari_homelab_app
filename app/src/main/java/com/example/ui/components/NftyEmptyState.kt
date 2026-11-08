package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KaminariTheme
import com.example.ui.theme.RubikFontFamily

@Composable
fun NftyEmptyState(
    title: String = "No notifications yet",
    message: String = "Your inbox is clear. Messages published to subscribed topics will appear here.",
    technicalCode: String = "SYS.STAT // 0_PENDING",
    icon: ImageVector = Icons.Default.Inbox,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(KaminariTheme.colors.surfaceSecondary)
                .border(1.dp, KaminariTheme.colors.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = KaminariTheme.colors.accent,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = technicalCode,
            style = KaminariTheme.techTypography.diagnosticCode,
            color = KaminariTheme.colors.accent,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = title,
            fontFamily = RubikFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            color = KaminariTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = message,
            fontFamily = RubikFontFamily,
            fontSize = 13.sp,
            color = KaminariTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp,
        )

        if (actionLabel != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onActionClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaminariTheme.colors.surfaceSecondary,
                    contentColor = KaminariTheme.colors.textPrimary,
                ),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
            ) {
                Text(
                    text = actionLabel,
                    fontFamily = RubikFontFamily,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
