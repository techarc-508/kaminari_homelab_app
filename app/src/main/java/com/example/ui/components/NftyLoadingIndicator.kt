package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KaminariTheme

@Composable
fun NftyLoadingIndicator(
    status: String = "Connecting to ntfy server...",
    technicalDetail: String = "ESTABLISHING TLS // HTTPS 443",
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(0.75.dp, KaminariTheme.colors.border.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(28.dp),
                color = KaminariTheme.colors.accent,
                strokeWidth = 2.5.dp,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = status,
                fontFamily = KaminariTheme.colors.textPrimary.let { JetBrainsMonoFontFamily },
                fontSize = 12.sp,
                color = KaminariTheme.colors.textPrimary,
            )

            Text(
                text = technicalDetail,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 10.sp,
                color = KaminariTheme.colors.textSecondary,
                letterSpacing = 0.5.sp,
            )
        }
    }
}
