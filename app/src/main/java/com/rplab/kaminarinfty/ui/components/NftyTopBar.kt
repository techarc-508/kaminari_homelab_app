package com.rplab.kaminarinfty.ui.components

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NftyTopBar(
    title: String = "KAMINARI-FUJI",
    subtitle: String = "ntfy node • ntfy.rplab.qzz.io",
    authState: AuthState = AuthState.GUEST,
    isSampleData: Boolean = false,
    showBackButton: Boolean = false,
    onBackClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    onToggleThemeClick: (() -> Unit)? = null,
    onSettingsClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    // Injectable so the ticking behaviour is testable without wall-clock sleeps.
    nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    // Live clock restored after the phase 2 gate (G-08). Sleeps until the next
    // second boundary instead of requesting a frame per frame, so the
    // low-RAM/E-Ink cost is one small recomposition per second rather than a
    // wake-up on every frame. Cancelled automatically when the composition
    // leaves.
    val clockMillis by produceState(initialValue = nowMillis()) {
        while (true) {
            val now = nowMillis()
            value = now
            delay((1_000L - now % 1_000L).coerceAtLeast(16L))
        }
    }
    val timeFormat = remember { SimpleDateFormat("hh:mm:ss a", Locale.getDefault()) }
    val dateFormat = remember { SimpleDateFormat("EEE, MMM d", Locale.getDefault()) }
    val currentTimeString = remember(clockMillis) { timeFormat.format(Date(clockMillis)) }
    val currentDateString = remember(clockMillis) { dateFormat.format(Date(clockMillis)) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(KaminariTheme.colors.surfaceMain)
            .border(
                width = 0.85.dp,
                color = KaminariTheme.colors.border.copy(alpha = if (KaminariTheme.colors.isDark) 0.8f else 0.6f),
                shape = RoundedCornerShape(0.dp)
            )
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Back button or Kaminari-Fuji identity with Nominal status badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showBackButton && onBackClick != null) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(KaminariTheme.colors.surfaceSecondary)
                            .border(0.75.dp, KaminariTheme.colors.border, RoundedCornerShape(8.dp))
                            .testTag("top_bar_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = KaminariTheme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Pulsing status dot
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (authState.isConnected) KaminariTheme.colors.success
                                else KaminariTheme.colors.accent
                            )
                    )

                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = title,
                                fontFamily = RubikFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = KaminariTheme.colors.textPrimary,
                                letterSpacing = 0.5.sp,
                            )

                            // "Nominal" green badge straight from user's website screenshot
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(KaminariTheme.colors.success.copy(alpha = 0.12f))
                                    .border(0.75.dp, KaminariTheme.colors.success.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = if (authState.isConnected) "Nominal" else "Standby",
                                    fontFamily = KaminariTheme.techTypography.httpStatus.fontFamily,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = KaminariTheme.colors.success,
                                )
                            }
                        }

                        Text(
                            text = subtitle,
                            style = KaminariTheme.techTypography.metricSubtext,
                            color = KaminariTheme.colors.textSecondary,
                        )
                    }
                }
            }

            // Right: Live clock + technical buttons matching screenshot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // Live clock in monospace
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 4.dp)
                ) {
                    Text(
                        text = currentTimeString,
                        style = KaminariTheme.techTypography.liveClock,
                        color = KaminariTheme.colors.textPrimary,
                        modifier = Modifier.testTag("top_bar_clock"),
                    )
                    Text(
                        text = currentDateString,
                        style = KaminariTheme.techTypography.metricSubtext,
                        color = KaminariTheme.colors.textSecondary,
                    )
                }

                if (onSearchClick != null) {
                    IconButton(
                        onClick = onSearchClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(KaminariTheme.colors.surfaceSecondary)
                            .border(0.75.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .testTag("top_bar_search_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search notifications",
                            tint = KaminariTheme.colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (onToggleThemeClick != null) {
                    IconButton(
                        onClick = onToggleThemeClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(KaminariTheme.colors.surfaceSecondary)
                            .border(0.75.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .testTag("top_bar_theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (KaminariTheme.colors.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Day/Night Theme",
                            tint = KaminariTheme.colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (onSettingsClick != null) {
                    IconButton(
                        onClick = onSettingsClick,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(KaminariTheme.colors.surfaceSecondary)
                            .border(0.75.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                            .testTag("top_bar_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = KaminariTheme.colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
