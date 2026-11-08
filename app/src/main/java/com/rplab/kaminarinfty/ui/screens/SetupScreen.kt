package com.rplab.kaminarinfty.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.ui.components.NftyConnectionIndicator
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily

@Composable
fun SetupScreen(
    serverConfig: ServerConfig,
    authState: AuthState,
    onSaveConfig: (ServerConfig) -> Unit,
    onTestConnection: () -> Unit,
    onProceed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var url by remember { mutableStateOf(serverConfig.serverUrl) }
    var selectedAuthMethod by remember { mutableStateOf(serverConfig.authMethod) }
    var username by remember { mutableStateOf(serverConfig.username) }
    var tokenOrPassword by remember { mutableStateOf(serverConfig.tokenOrPassword) }
    var showPassword by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var enforceHttps by remember { mutableStateOf(serverConfig.useHttpsOnly) }
    var autoReconnect by remember { mutableStateOf(serverConfig.autoReconnect) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // App Emblem Header
        Box(
            modifier = Modifier
                .size(68.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(KaminariTheme.colors.surfaceSecondary)
                .border(1.dp, KaminariTheme.colors.border, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Security,
                contentDescription = null,
                tint = KaminariTheme.colors.accent,
                modifier = Modifier.size(36.dp),
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Kaminari Nfty",
            fontFamily = RubikFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            color = KaminariTheme.colors.textPrimary,
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "Your notifications. Your homelab.",
            fontFamily = RubikFontFamily,
            fontSize = 14.sp,
            color = KaminariTheme.colors.textSecondary,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "RPLAB / KAMINARI-FUJI CLIENT // v1.0.0",
            style = KaminariTheme.techTypography.diagnosticCode,
            color = KaminariTheme.colors.accent,
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Server Connection Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(KaminariTheme.colors.surfaceMain)
                .border(0.85.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .padding(18.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SERVER ENDPOINT",
                        style = KaminariTheme.techTypography.diagnosticCode,
                        color = KaminariTheme.colors.textSecondary,
                    )
                    NftyConnectionIndicator(authState = authState, isSampleData = false)
                }

                // Server URL input
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        onSaveConfig(
                            serverConfig.copy(
                                serverUrl = it,
                                authMethod = selectedAuthMethod,
                                username = username,
                                tokenOrPassword = tokenOrPassword,
                            )
                        )
                    },
                    label = { Text("Server URL", fontFamily = RubikFontFamily) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Public,
                            contentDescription = null,
                            tint = KaminariTheme.colors.accent,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("server_url_input"),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp,
                        color = KaminariTheme.colors.textPrimary,
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = KaminariTheme.colors.accent,
                        unfocusedBorderColor = KaminariTheme.colors.border,
                        focusedContainerColor = KaminariTheme.colors.surfaceSecondary,
                        unfocusedContainerColor = KaminariTheme.colors.surfaceSecondary,
                    ),
                    shape = RoundedCornerShape(8.dp),
                )

                // Auth Method Selection
                Text(
                    text = "AUTHENTICATION METHOD",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = KaminariTheme.colors.textSecondary,
                    letterSpacing = 0.5.sp,
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthMethod.values().forEach { method ->
                        val isSelected = selectedAuthMethod == method
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) KaminariTheme.colors.accent.copy(alpha = 0.15f)
                                    else KaminariTheme.colors.surfaceSecondary
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) KaminariTheme.colors.accent else KaminariTheme.colors.border,
                                    RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("auth_method_${method.name}")
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(
                                        imageVector = when (method) {
                                            AuthMethod.GUEST -> Icons.Default.Public
                                            AuthMethod.USERNAME_PASSWORD -> Icons.Default.Person
                                            AuthMethod.ACCESS_TOKEN -> Icons.Default.Key
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) KaminariTheme.colors.accent else KaminariTheme.colors.textSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = method.displayName,
                                        fontFamily = RubikFontFamily,
                                        fontSize = 14.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) KaminariTheme.colors.textPrimary else KaminariTheme.colors.textSecondary,
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = KaminariTheme.colors.accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Dynamic Credential Fields
                AnimatedVisibility(visible = selectedAuthMethod == AuthMethod.USERNAME_PASSWORD) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = {
                                username = it
                                onSaveConfig(
                                    serverConfig.copy(
                                        serverUrl = url,
                                        authMethod = selectedAuthMethod,
                                        username = it,
                                        tokenOrPassword = tokenOrPassword,
                                    )
                                )
                            },
                            label = { Text("Username", fontFamily = RubikFontFamily) },
                            leadingIcon = {
                                Icon(Icons.Default.Person, contentDescription = null, tint = KaminariTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                            },
                            modifier = Modifier.fillMaxWidth().testTag("username_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KaminariTheme.colors.accent,
                                unfocusedBorderColor = KaminariTheme.colors.border,
                            ),
                        )

                        OutlinedTextField(
                            value = tokenOrPassword,
                            onValueChange = {
                                tokenOrPassword = it
                                onSaveConfig(
                                    serverConfig.copy(
                                        serverUrl = url,
                                        authMethod = selectedAuthMethod,
                                        username = username,
                                        tokenOrPassword = it,
                                    )
                                )
                            },
                            label = { Text("Password", fontFamily = RubikFontFamily) },
                            leadingIcon = {
                                Icon(Icons.Default.Lock, contentDescription = null, tint = KaminariTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "Toggle password visibility",
                                        tint = KaminariTheme.colors.textSecondary,
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            modifier = Modifier.fillMaxWidth().testTag("password_input"),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = KaminariTheme.colors.accent,
                                unfocusedBorderColor = KaminariTheme.colors.border,
                            ),
                        )
                    }
                }

                AnimatedVisibility(visible = selectedAuthMethod == AuthMethod.ACCESS_TOKEN) {
                    OutlinedTextField(
                        value = tokenOrPassword,
                        onValueChange = {
                            tokenOrPassword = it
                            onSaveConfig(
                                serverConfig.copy(
                                    serverUrl = url,
                                    authMethod = selectedAuthMethod,
                                    username = username,
                                    tokenOrPassword = it,
                                )
                            )
                        },
                        label = { Text("Access Token (tk_...)", fontFamily = RubikFontFamily) },
                        leadingIcon = {
                            Icon(Icons.Default.Key, contentDescription = null, tint = KaminariTheme.colors.textSecondary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle token visibility",
                                    tint = KaminariTheme.colors.textSecondary,
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth().testTag("token_input"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KaminariTheme.colors.accent,
                            unfocusedBorderColor = KaminariTheme.colors.border,
                        ),
                    )
                }

                // Advanced Settings Toggle
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(KaminariTheme.colors.surfaceSecondary)
                        .border(1.dp, KaminariTheme.colors.border, RoundedCornerShape(8.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = KaminariTheme.colors.accent, modifier = Modifier.size(16.dp))
                                Text("Advanced Security Settings", fontFamily = RubikFontFamily, fontSize = 13.sp, color = KaminariTheme.colors.textPrimary)
                            }
                            IconButton(onClick = { showAdvanced = !showAdvanced }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = "Toggle advanced",
                                    tint = KaminariTheme.colors.textSecondary,
                                )
                            }
                        }

                        AnimatedVisibility(visible = showAdvanced) {
                            Column(
                                modifier = Modifier.padding(top = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text("Enforce HTTPS Only", fontFamily = RubikFontFamily, fontSize = 12.sp, color = KaminariTheme.colors.textPrimary)
                                        Text("Refuses unencrypted plaintext connections", fontFamily = RubikFontFamily, fontSize = 10.sp, color = KaminariTheme.colors.textSecondary)
                                    }
                                    Switch(
                                        checked = enforceHttps,
                                        onCheckedChange = { enforceHttps = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = KaminariTheme.colors.accent)
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column {
                                        Text("Auto Reconnect with Jitter", fontFamily = RubikFontFamily, fontSize = 12.sp, color = KaminariTheme.colors.textPrimary)
                                        Text("Bounded exponential backoff on disconnect", fontFamily = RubikFontFamily, fontSize = 10.sp, color = KaminariTheme.colors.textSecondary)
                                    }
                                    Switch(
                                        checked = autoReconnect,
                                        onCheckedChange = { autoReconnect = it },
                                        colors = SwitchDefaults.colors(checkedThumbColor = KaminariTheme.colors.accent)
                                    )
                                }
                            }
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onTestConnection,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("test_connection_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
                    ) {
                        if (authState == AuthState.CONNECTING) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = KaminariTheme.colors.accent)
                        } else {
                            Text("Test Link", fontFamily = RubikFontFamily, fontSize = 13.sp, color = KaminariTheme.colors.textPrimary)
                        }
                    }

                    Button(
                        onClick = onProceed,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("enter_app_button"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KaminariTheme.colors.accent,
                            contentColor = KaminariTheme.colors.background,
                        ),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Text("Connect", fontFamily = RubikFontFamily, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Phase 1 Sample Data Notice
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(KaminariTheme.colors.surfaceSecondary)
                .border(1.dp, KaminariTheme.colors.border, RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "FIELD MANUAL NOTICE // PHASE 1",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = KaminariTheme.colors.accent,
                )
                Text(
                    text = "Phase 1 implements the full UI experience with realistic homelab sample telemetry. No credentials are transmitted to external servers in this stage.",
                    fontFamily = RubikFontFamily,
                    fontSize = 11.sp,
                    color = KaminariTheme.colors.textSecondary,
                    lineHeight = 15.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
    }
}
