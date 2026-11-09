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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.BuildConfig
import com.rplab.kaminarinfty.ThemeSetting
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.delivery.DeliveryStatus
import com.rplab.kaminarinfty.model.DeliveryMode
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.model.ServerProfile
import com.rplab.kaminarinfty.ui.components.NftyTopBar
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily

@Composable
fun SettingsScreen(
    serverConfig: ServerConfig,
    authState: AuthState,
    profiles: List<ServerProfile>,
    activeProfileId: String?,
    reconnectAttempt: Int,
    lastCursor: String?,
    themeSetting: ThemeSetting,
    isCompactDensity: Boolean,
    isSampleData: Boolean,
    sampleDataEnabled: Boolean,
    historyCount: Int,
    malformedLines: Int,
    onThemeChange: (ThemeSetting) -> Unit,
    onToggleDensity: () -> Unit,
    onTestConnection: () -> Unit,
    onSelectProfile: (String) -> Unit,
    onCatchUp: () -> Unit,
    onNavigateToSetup: () -> Unit,
    onClearHistory: () -> Unit,
    onClearSaved: () -> Unit,
    onToggleSampleData: (Boolean) -> Unit,
    deliveryMode: DeliveryMode,
    deliveryStatus: DeliveryStatus,
    notificationsEnabled: Boolean,
    onSelectDeliveryMode: (DeliveryMode) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
    ) {
        NftyTopBar(
            title = "Settings",
            subtitle = "KAMINARI // CONFIGURATION",
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
                            Text(
                                serverConfig.serverUrl.ifBlank { "Not configured" },
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 13.sp,
                                color = KaminariTheme.colors.textPrimary,
                            )
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

            // Section 2: SERVER PROFILES (local, switchable, no secrets shown)
            SettingsSectionCard(title = "SERVER PROFILES", icon = Icons.Default.Storage) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (profiles.isEmpty()) {
                        Text(
                            text = "No saved profiles yet — connect once to create the first one.",
                            fontFamily = RubikFontFamily,
                            fontSize = 12.sp,
                            color = KaminariTheme.colors.textSecondary,
                        )
                    } else {
                        profiles.forEach { profile ->
                            val isActive = profile.id == activeProfileId
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        profile.displayName.ifBlank { profile.baseUrl },
                                        fontFamily = RubikFontFamily,
                                        fontSize = 13.sp,
                                        color = KaminariTheme.colors.textPrimary,
                                    )
                                    Text(
                                        profile.baseUrl,
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 10.sp,
                                        color = KaminariTheme.colors.textSecondary,
                                    )
                                    Text(
                                        text = if (profile.hasStoredSecret) {
                                            "${profile.authMethod.displayName} — encrypted credential stored"
                                        } else {
                                            profile.authMethod.displayName
                                        },
                                        fontFamily = RubikFontFamily,
                                        fontSize = 10.sp,
                                        color = KaminariTheme.colors.textSecondary,
                                    )
                                }
                                if (isActive) {
                                    Text(
                                        text = "ACTIVE",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 9.sp,
                                        color = KaminariTheme.colors.success,
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = { onSelectProfile(profile.id) },
                                        shape = RoundedCornerShape(6.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
                                        modifier = Modifier.testTag("use_profile_${profile.id}"),
                                    ) {
                                        Text("Use", fontFamily = RubikFontFamily, fontSize = 11.sp, color = KaminariTheme.colors.textPrimary)
                                    }
                                }
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = onCatchUp,
                        modifier = Modifier.fillMaxWidth().testTag("resync_history_button"),
                        shape = RoundedCornerShape(6.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
                    ) {
                        Text("Re-sync missed history", fontFamily = RubikFontFamily, fontSize = 12.sp, color = KaminariTheme.colors.textPrimary)
                    }
                }
            }

            // Section 3: APPEARANCE
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
                                        if (isSelected) KaminariTheme.colors.accent else KaminariTheme.colors.border,
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

            // Section 4: NOTIFICATIONS & DELIVERY (owner-selected modes)
            SettingsSectionCard(title = "NOTIFICATIONS & DELIVERY", icon = Icons.Default.Notifications) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!notificationsEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(6.dp))
                                .background(KaminariTheme.colors.surfaceSecondary)
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "System notifications are blocked. Android will not show message alerts until you allow them.",
                                modifier = Modifier.weight(1f),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 10.sp,
                                color = KaminariTheme.colors.textSecondary,
                                lineHeight = 14.sp,
                            )
                            OutlinedButton(
                                onClick = onOpenNotificationSettings,
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
                                modifier = Modifier.testTag("open_notification_settings"),
                            ) {
                                Text("Allow", fontFamily = RubikFontFamily, fontSize = 11.sp, color = KaminariTheme.colors.textPrimary)
                            }
                        }
                    }

                    DeliveryMode.values().forEach { mode ->
                        val isSelected = deliveryMode == mode
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) KaminariTheme.colors.accent.copy(alpha = 0.12f)
                                    else KaminariTheme.colors.surfaceSecondary
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) KaminariTheme.colors.accent else KaminariTheme.colors.border,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onSelectDeliveryMode(mode) }
                                .padding(10.dp)
                                .testTag("delivery_mode_${mode.name}"),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = mode.label,
                                    fontFamily = RubikFontFamily,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = KaminariTheme.colors.textPrimary,
                                )
                                if (isSelected) {
                                    Text(
                                        text = "ACTIVE",
                                        fontFamily = JetBrainsMonoFontFamily,
                                        fontSize = 9.sp,
                                        color = KaminariTheme.colors.accent,
                                    )
                                }
                            }
                            Text(
                                text = mode.summary,
                                fontFamily = RubikFontFamily,
                                fontSize = 11.sp,
                                color = KaminariTheme.colors.textPrimary,
                            )
                            Text(
                                text = mode.tradeoff,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 9.sp,
                                color = KaminariTheme.colors.textSecondary,
                                lineHeight = 13.sp,
                            )
                        }
                    }

                    if (deliveryMode == DeliveryMode.INSTANT) {
                        Text(
                            text = "STATUS // ${deliveryStatus.displayText()}",
                            modifier = Modifier.testTag("delivery_status_text"),
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 10.sp,
                            color = if (deliveryStatus.isTerminal) {
                                KaminariTheme.colors.warning
                            } else {
                                KaminariTheme.colors.textSecondary
                            },
                            lineHeight = 14.sp,
                        )
                    }

                    Text(
                        text = "Android, Doze and device power management can interrupt any background mode. " +
                            "No mode guarantees instant delivery; Instant uses a visible persistent notification. " +
                            "Android 15+ limits dataSync services to 6 hours per 24 hours.",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 9.sp,
                        color = KaminariTheme.colors.textSecondary,
                        lineHeight = 13.sp,
                    )
                }
            }

            // Section 5: LOCAL DATA
            SettingsSectionCard(title = "LOCAL DATA & CACHE", icon = Icons.Default.Storage) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Stored notifications", fontFamily = RubikFontFamily, fontSize = 13.sp, color = KaminariTheme.colors.textPrimary)
                        Text(
                            "$historyCount",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = KaminariTheme.colors.accent,
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Demo Dataset", fontFamily = RubikFontFamily, fontSize = 13.sp, color = KaminariTheme.colors.textPrimary)
                            Text("Shows sample notifications only; never written to history", fontFamily = RubikFontFamily, fontSize = 11.sp, color = KaminariTheme.colors.textSecondary)
                        }
                        Switch(
                            checked = sampleDataEnabled,
                            onCheckedChange = onToggleSampleData,
                            colors = SwitchDefaults.colors(checkedThumbColor = KaminariTheme.colors.accent),
                            modifier = Modifier.testTag("sample_data_switch"),
                        )
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
                }
            }

            // Section 6: DIAGNOSTICS (real values only)
            SettingsSectionCard(title = "DIAGNOSTICS", icon = Icons.Default.Info) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    DiagnosticRow(
                        label = "CLIENT VERSION",
                        value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    )
                    DiagnosticRow(label = "CONNECTION", value = authState.statusLabel)
                    DiagnosticRow(label = "MALFORMED LINES", value = "$malformedLines")
                    DiagnosticRow(label = "RECONNECT ATTEMPT", value = "$reconnectAttempt")
                    DiagnosticRow(
                        label = "HISTORY CURSOR",
                        value = lastCursor ?: "none (first sync uses 12h window)",
                    )
                    DiagnosticRow(
                        label = "SERVER VERSION",
                        value = "unverified — health probe pending",
                    )
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
