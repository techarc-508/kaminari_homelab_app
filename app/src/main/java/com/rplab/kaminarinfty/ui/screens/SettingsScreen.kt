package com.rplab.kaminarinfty.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.ThemeSetting
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.ui.components.NftyTopBar
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily

@Composable
fun SettingsScreen(
    serverConfig: ServerConfig,
    authState: AuthState,
    themeSetting: ThemeSetting,
    isCompactDensity: Boolean,
    isSampleData: Boolean,
    onThemeChange: (ThemeSetting) -> Unit,
    onToggleDensity: () -> Unit,
    onTestConnection: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onClearHistory: () -> Unit,
    onClearSaved: () -> Unit,
    onResetSampleData: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var localNotificationsEnabled by remember { mutableStateOf(true) }
    var soundVibrationEnabled by remember { mutableStateOf(true) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
    ) {
        NftyTopBar(
            title = "Settings",
            subtitle = "KAMINARI // CONFIGURATION & TELEMETRY",
            authState = authState,
            isSampleData = isSampleData,
            onSearchClick = onSearchClick,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Section 1: CONNECTION
            SettingsSectionCard(title = "CONNECTION & IDENTITY", icon = Icons.Default.Wifi) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Server Endpoint", fontFamily = RubikFontFamily, fontSize = 12.sp, color = KaminariTheme.colors.textSecondary)
                            Text(serverConfig.serverUrl, fontFamily = JetBrainsMonoFontFamily, fontSize = 13.sp, color = KaminariTheme.colors.textPrimary)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(KaminariTheme.colors.surfaceSecondary)
                                .border(0.75.dp, KaminariTheme.colors.border, RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = authState.statusLabel,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.sp,
                                color = if (authState.isConnected) KaminariTheme.colors.success else KaminariTheme.colors.textSecondary,
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Auth Protocol", fontFamily = RubikFontFamily, fontSize = 12.sp, color = KaminariTheme.colors.textSecondary)
                            Text(serverConfig.authMethod.displayName, fontFamily = RubikFontFamily, fontSize = 13.sp, color = KaminariTheme.colors.textPrimary)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onTestConnection,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
                        ) {
                            Text("Test Link", fontFamily = RubikFontFamily, fontSize = 12.sp, color = KaminariTheme.colors.textPrimary)
                        }

                        Button(
                            onClick = onNavigateToSetup,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KaminariTheme.colors.surfaceSecondary,
                                contentColor = KaminariTheme.colors.accent,
                            ),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
                        ) {
                            Text("Reconfigure", fontFamily = RubikFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Section 2: APPEARANCE
            SettingsSectionCard(title = "APPEARANCE & THEME", icon = Icons.Default.Palette) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Choose visual mode (Parchment Day or Observatory Navy Night):",
                        fontFamily = RubikFontFamily,
                        fontSize = 12.sp,
                        color = KaminariTheme.colors.textSecondary,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeSetting.values().forEach { mode ->
                            val isSelected = themeSetting == mode
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        if (isSelected) KaminariTheme.colors.accent.copy(alpha = 0.2f)
                                        else KaminariTheme.colors.surfaceSecondary
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) KaminariTheme.colors.accent
                                        else KaminariTheme.colors.border,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onThemeChange(mode) }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .testTag("theme_button_${mode.name}"),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = when (mode) {
                                            ThemeSetting.DAY -> Icons.Default.LightMode
                                            ThemeSetting.NIGHT -> Icons.Default.DarkMode
                                            ThemeSetting.SYSTEM -> Icons.Default.BrightnessAuto
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) KaminariTheme.colors.accent else KaminariTheme.colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = when (mode) {
                                            ThemeSetting.DAY -> "Day"
                                            ThemeSetting.NIGHT -> "Night"
                                            ThemeSetting.SYSTEM -> "System"
                                        },
                                        fontFamily = RubikFontFamily,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) KaminariTheme.colors.textPrimary else KaminariTheme.colors.textSecondary,
                                    )
                                }
                            }
                        }
                    }

                    // Density toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Compact Density", fontFamily = RubikFontFamily, fontSize = 13.sp, color = KaminariTheme.colors.textPrimary)
                            Text("Reduces vertical row padding for dense monitoring", fontFamily = RubikFontFamily, fontSize = 11.sp, color = KaminariTheme.colors.textSecondary)
                        }
                        Switch(
                            checked = isCompactDensity,
                            onCheckedChange = { onToggleDensity() },
                            colors = SwitchDefaults.colors(checkedThumbColor = KaminariTheme.colors.accent),
                        )
                    }
                }
            }

            // Section 3: NOTIFICATIONS & DELIVERY
            SettingsSectionCard(title = "NOTIFICATIONS & DELIVERY", icon = Icons.Default.Notifications) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Client Notifications", fontFamily = RubikFontFamily, fontSize = 13.sp, color = KaminariTheme.colors.textPrimary)
                            Text("Show status bar alerts for urgent homelab events", fontFamily = RubikFontFamily, fontSize = 11.sp, color = KaminariTheme.colors.textSecondary)
                        }
                        Switch(
                            checked = localNotificationsEnabled,
                            onCheckedChange = { localNotificationsEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = KaminariTheme.colors.accent),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Sound & Vibration", fontFamily = RubikFontFamily, fontSize = 13.sp, color = KaminariTheme.colors.textPrimary)
                            Text("Respect Android system DND and priority channels", fontFamily = RubikFontFamily, fontSize = 11.sp, color = KaminariTheme.colors.textSecondary)
                        }
                        Switch(
                            checked = soundVibrationEnabled,
                            onCheckedChange = { soundVibrationEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = KaminariTheme.colors.accent),
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(KaminariTheme.colors.surfaceSecondary)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Background Delivery: Live SSE stream active in foreground. Phase 6 will implement battery-conscious persistent relay handling.",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = KaminariTheme.colors.textSecondary,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }

            // Section 4: LOCAL DATA & RETENTION
            SettingsSectionCard(title = "LOCAL DATA & CACHE", icon = Icons.Default.Storage) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Retention Policy", fontFamily = RubikFontFamily, fontSize = 13.sp, color = KaminariTheme.colors.textPrimary)
                        Text("30 Days (Standard)", fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = KaminariTheme.colors.accent)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Local Cache Size", fontFamily = RubikFontFamily, fontSize = 13.sp, color = KaminariTheme.colors.textPrimary)
                        Text("124 KB (8 messages)", fontFamily = JetBrainsMonoFontFamily, fontSize = 11.sp, color = KaminariTheme.colors.textSecondary)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = onClearHistory,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
                        ) {
                            Text("Clear History", fontFamily = RubikFontFamily, fontSize = 11.sp, color = KaminariTheme.colors.textPrimary)
                        }

                        OutlinedButton(
                            onClick = onClearSaved,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
                        ) {
                            Text("Clear Saved", fontFamily = RubikFontFamily, fontSize = 11.sp, color = KaminariTheme.colors.textPrimary)
                        }
                    }

                    Button(
                        onClick = onResetSampleData,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KaminariTheme.colors.surfaceSecondary,
                            contentColor = KaminariTheme.colors.accent,
                        ),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("Restore Sample Notifications & Topics", fontFamily = RubikFontFamily, fontSize = 12.sp)
                        }
                    }
                }
            }

            // Section 5: DIAGNOSTICS
            SettingsSectionCard(title = "DIAGNOSTICS & TELEMETRY", icon = Icons.Default.Info) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DiagnosticRow(label = "CLIENT VERSION", value = "1.0.0-rc1 (Build 20261015)")
                    DiagnosticRow(label = "STREAM PROTOCOL", value = "HTTP/2 Server-Sent Events")
                    DiagnosticRow(label = "CIPHER SUITE", value = "TLS_AES_256_GCM_SHA384")
                    DiagnosticRow(label = "SECURITY BOUNDARY", value = "Zero plaintext log redaction active")
                    DiagnosticRow(label = "DESIGN FAMILY", value = "RPLab / Kaminari-Fuji")
                }
            }

            Spacer(modifier = Modifier.height(60.dp))
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(1.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = KaminariTheme.colors.accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = title,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = KaminariTheme.colors.textSecondary,
                    letterSpacing = 1.sp,
                )
            }
            content()
        }
    }
}

@Composable
private fun DiagnosticRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = KaminariTheme.techTypography.diagnosticCode,
            color = KaminariTheme.colors.textSecondary,
        )
        Text(
            text = value,
            style = KaminariTheme.techTypography.metricSubtext.copy(fontSize = 10.sp),
            color = KaminariTheme.colors.textPrimary,
        )
    }
}
