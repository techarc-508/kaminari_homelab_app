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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NftyErrorCategory
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.KaminariTheme
import com.example.ui.theme.RubikFontFamily

@Composable
fun NftyErrorState(
    errorCategory: NftyErrorCategory = NftyErrorCategory.UNREACHABLE,
    customMessage: String? = null,
    onRetryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val description = customMessage ?: when (errorCategory) {
        NftyErrorCategory.DNS -> "Unable to resolve hostname 'nfty.rplab.qzz.io'. Check network connectivity or local DNS settings."
        NftyErrorCategory.TLS -> "TLS handshake verification failed. The endpoint certificate could not be validated safely."
        NftyErrorCategory.AUTH -> "Provided credentials were not accepted by the ntfy server. Check username or access token."
        NftyErrorCategory.FORBIDDEN -> "Server policy denies access to this topic or action. Privileges must be granted server-side."
        NftyErrorCategory.TIMEOUT -> "Request timed out waiting for server response. The gateway may be overloaded or offline."
        NftyErrorCategory.UNREACHABLE -> "Cannot establish TCP connection to port 443. Verify server state and reverse proxy."
        NftyErrorCategory.STREAM -> "Live subscription stream terminated unexpectedly. Bounded exponential backoff active."
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(1.dp, KaminariTheme.colors.error.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(KaminariTheme.colors.error.copy(alpha = 0.15f))
                .border(1.dp, KaminariTheme.colors.error.copy(alpha = 0.4f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = KaminariTheme.colors.error,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "[${errorCategory.code}]",
            style = KaminariTheme.techTypography.httpStatus,
            color = KaminariTheme.colors.error,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = errorCategory.label,
            fontFamily = RubikFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = KaminariTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = description,
            fontFamily = RubikFontFamily,
            fontSize = 12.sp,
            color = KaminariTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 16.sp,
        )

        if (onRetryClick != null) {
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetryClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaminariTheme.colors.surfaceSecondary,
                    contentColor = KaminariTheme.colors.textPrimary,
                ),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = KaminariTheme.colors.textPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Retry Connection",
                        fontFamily = RubikFontFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
