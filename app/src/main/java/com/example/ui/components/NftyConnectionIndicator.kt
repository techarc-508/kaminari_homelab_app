package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AuthState
import com.example.ui.theme.KaminariTheme

@Composable
fun NftyConnectionIndicator(
    authState: AuthState,
    modifier: Modifier = Modifier,
    isSampleData: Boolean = true,
) {
    val (dotColor, statusText) = when {
        isSampleData -> Pair(
            KaminariTheme.colors.accent,
            "SAMPLE DATA"
        )
        authState == AuthState.AUTHENTICATED -> Pair(
            KaminariTheme.colors.success,
            "SUPERUSER"
        )
        authState == AuthState.GUEST -> Pair(
            KaminariTheme.colors.success,
            "GUEST"
        )
        authState == AuthState.CONNECTING || authState == AuthState.RECONNECTING -> Pair(
            KaminariTheme.colors.warning,
            "CONNECTING"
        )
        authState == AuthState.AUTHENTICATION_FAILED || authState == AuthState.PERMISSION_DENIED -> Pair(
            KaminariTheme.colors.error,
            "AUTH ERR"
        )
        else -> Pair(
            KaminariTheme.colors.textSecondary,
            "OFFLINE"
        )
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(KaminariTheme.colors.surfaceSecondary)
            .border(0.75.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Text(
            text = statusText,
            style = KaminariTheme.techTypography.httpStatus.copy(fontSize = 9.sp),
            color = KaminariTheme.colors.textSecondary,
        )
    }
}
