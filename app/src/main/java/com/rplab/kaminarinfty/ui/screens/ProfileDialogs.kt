package com.rplab.kaminarinfty.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.ProfileChangePreview
import com.rplab.kaminarinfty.ProfileEditUi
import com.rplab.kaminarinfty.data.api.NtfyEndpoint
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily

@Composable
fun ProfileEditDialog(
    edit: ProfileEditUi,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (url: String, authMethod: AuthMethod, username: String, newSecret: String, httpsOnly: Boolean) -> Unit,
) {
    var url by remember(edit.profileId, edit.originalUrl) { mutableStateOf(edit.serverUrl) }
    var authMethod by remember(edit.profileId) { mutableStateOf(edit.authMethod) }
    var username by remember(edit.profileId) { mutableStateOf(edit.username) }
    var secret by remember(edit.profileId) { mutableStateOf("") }
    var httpsOnly by remember(edit.profileId) { mutableStateOf(edit.useHttpsOnly) }
    var showSecret by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaminariTheme.colors.surfaceMain,
        title = {
            Column {
                Text(
                    text = "Edit ${edit.displayName}",
                    fontFamily = RubikFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = KaminariTheme.colors.textPrimary,
                )
                Text(
                    text = "Stored credentials are never displayed.",
                    style = KaminariTheme.techTypography.metricSubtext,
                    color = KaminariTheme.colors.textSecondary,
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .testTag("profile_edit_dialog"),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                ProfileField(
                    value = url,
                    onValueChange = { url = it },
                    label = "Server URL",
                    testTag = "profile_edit_url",
                )
                Text(
                    text = "AUTHENTICATION",
                    style = KaminariTheme.techTypography.diagnosticCode,
                    color = KaminariTheme.colors.textSecondary,
                )
                AuthMethod.values().forEach { method ->
                    val selected = authMethod == method
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                if (selected) KaminariTheme.colors.accent.copy(alpha = 0.14f)
                                else KaminariTheme.colors.surfaceSecondary
                            )
                            .border(
                                0.8.dp,
                                if (selected) KaminariTheme.colors.accent
                                else KaminariTheme.colors.border,
                                RoundedCornerShape(7.dp),
                            )
                            .clickable { authMethod = method }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("profile_edit_auth_${method.name}"),
                    ) {
                        Text(
                            text = method.displayName,
                            fontFamily = RubikFontFamily,
                            fontSize = 12.sp,
                            color = if (selected) KaminariTheme.colors.accent
                            else KaminariTheme.colors.textPrimary,
                        )
                    }
                }
                if (authMethod == AuthMethod.USERNAME_PASSWORD) {
                    ProfileField(
                        value = username,
                        onValueChange = { username = it },
                        label = "Username",
                        testTag = "profile_edit_username",
                        leadingPerson = true,
                    )
                }
                if (authMethod != AuthMethod.GUEST) {
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = {
                            Text(
                                if (edit.hasStoredSecret) "New credential (blank keeps stored)"
                                else "Credential",
                                fontFamily = RubikFontFamily,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Key,
                                contentDescription = null,
                                tint = KaminariTheme.colors.textSecondary,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    imageVector = if (showSecret) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = if (showSecret) "Hide credential"
                                    else "Show credential",
                                    tint = KaminariTheme.colors.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                        visualTransformation = if (showSecret) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("profile_edit_secret"),
                        colors = profileFieldColors(),
                        shape = RoundedCornerShape(7.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Enforce HTTPS",
                        fontFamily = RubikFontFamily,
                        fontSize = 12.sp,
                        color = KaminariTheme.colors.textPrimary,
                    )
                    Switch(
                        checked = httpsOnly,
                        onCheckedChange = { httpsOnly = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = KaminariTheme.colors.accent
                        ),
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        color = KaminariTheme.colors.error,
                        fontFamily = RubikFontFamily,
                        fontSize = 11.sp,
                    )
                }
                Text(
                    text = "Changing the URL opens a preview first. Existing messages, topics and outbox rows are never deleted by a switch.",
                    style = KaminariTheme.techTypography.metricSubtext,
                    color = KaminariTheme.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(url, authMethod, username, secret, httpsOnly) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaminariTheme.colors.accent,
                    contentColor = KaminariTheme.colors.background,
                ),
                modifier = Modifier.testTag("profile_edit_save"),
            ) {
                Text("Review / Save", fontFamily = RubikFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
            ) {
                Text(
                    "Cancel",
                    fontFamily = RubikFontFamily,
                    color = KaminariTheme.colors.textSecondary,
                )
            }
        },
    )
}

@Composable
fun AddProfileDialog(
    onDismiss: () -> Unit,
    onAdd: (ServerConfig) -> Unit,
) {
    var url by remember { mutableStateOf("https://") }
    var authMethod by remember { mutableStateOf(AuthMethod.GUEST) }
    var username by remember { mutableStateOf("") }
    var secret by remember { mutableStateOf("") }
    var httpsOnly by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showSecret by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaminariTheme.colors.surfaceMain,
        title = {
            Text(
                text = "Add server profile",
                fontFamily = RubikFontFamily,
                fontWeight = FontWeight.Bold,
                color = KaminariTheme.colors.textPrimary,
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                ProfileField(
                    value = url,
                    onValueChange = { url = it },
                    label = "Server URL",
                    testTag = "add_profile_url",
                )
                Text(
                    text = "AUTHENTICATION",
                    style = KaminariTheme.techTypography.diagnosticCode,
                    color = KaminariTheme.colors.textSecondary,
                )
                AuthMethod.values().forEach { method ->
                    val selected = authMethod == method
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(
                                if (selected) KaminariTheme.colors.accent.copy(alpha = 0.14f)
                                else KaminariTheme.colors.surfaceSecondary
                            )
                            .border(
                                0.8.dp,
                                if (selected) KaminariTheme.colors.accent
                                else KaminariTheme.colors.border,
                                RoundedCornerShape(7.dp),
                            )
                            .clickable { authMethod = method }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        Text(
                            text = method.displayName,
                            fontFamily = RubikFontFamily,
                            fontSize = 12.sp,
                            color = if (selected) KaminariTheme.colors.accent
                            else KaminariTheme.colors.textPrimary,
                        )
                    }
                }
                if (authMethod == AuthMethod.USERNAME_PASSWORD) {
                    ProfileField(
                        value = username,
                        onValueChange = { username = it },
                        label = "Username",
                        testTag = "add_profile_username",
                        leadingPerson = true,
                    )
                }
                if (authMethod != AuthMethod.GUEST) {
                    OutlinedTextField(
                        value = secret,
                        onValueChange = { secret = it },
                        label = {
                            Text(
                                if (authMethod == AuthMethod.ACCESS_TOKEN) "Access token"
                                else "Password",
                                fontFamily = RubikFontFamily,
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { showSecret = !showSecret }) {
                                Icon(
                                    imageVector = if (showSecret) Icons.Default.VisibilityOff
                                    else Icons.Default.Visibility,
                                    contentDescription = "Toggle credential visibility",
                                    tint = KaminariTheme.colors.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        },
                        visualTransformation = if (showSecret) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("add_profile_secret"),
                        colors = profileFieldColors(),
                        shape = RoundedCornerShape(7.dp),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Enforce HTTPS",
                        fontFamily = RubikFontFamily,
                        fontSize = 12.sp,
                        color = KaminariTheme.colors.textPrimary,
                    )
                    Switch(
                        checked = httpsOnly,
                        onCheckedChange = { httpsOnly = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = KaminariTheme.colors.accent
                        ),
                    )
                }
                error?.let {
                    Text(
                        text = it,
                        color = KaminariTheme.colors.error,
                        fontFamily = RubikFontFamily,
                        fontSize = 11.sp,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val normalized = NtfyEndpoint.normalizeBaseUrl(url, httpsOnly).getOrElse {
                        error = it.message ?: "Invalid server URL"
                        return@Button
                    }
                    if (authMethod != AuthMethod.GUEST && secret.isBlank()) {
                        error = "Enter a credential for ${authMethod.displayName}"
                        return@Button
                    }
                    if (authMethod == AuthMethod.USERNAME_PASSWORD && username.isBlank()) {
                        error = "Username is required"
                        return@Button
                    }
                    onAdd(
                        ServerConfig(
                            serverUrl = normalized,
                            authMethod = authMethod,
                            username = username.trim(),
                            tokenOrPassword = secret.trim(),
                            useHttpsOnly = httpsOnly,
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaminariTheme.colors.accent,
                    contentColor = KaminariTheme.colors.background,
                ),
                modifier = Modifier.testTag("add_profile_save"),
            ) {
                Text("Add & Activate", fontFamily = RubikFontFamily, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
            ) {
                Text(
                    "Cancel",
                    fontFamily = RubikFontFamily,
                    color = KaminariTheme.colors.textSecondary,
                )
            }
        },
    )
}

@Composable
fun ProfileChangePreviewDialog(
    preview: ProfileChangePreview,
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onKeepAndMix: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KaminariTheme.colors.surfaceMain,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = KaminariTheme.colors.warning,
            )
        },
        title = {
            Text(
                text = "Server URL changed",
                fontFamily = RubikFontFamily,
                fontWeight = FontWeight.Bold,
                color = KaminariTheme.colors.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Destination: ${preview.destinationUrl}",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = KaminariTheme.colors.accent,
                )
                Text(
                    text = "This profile currently has ${preview.messages} messages, ${preview.topics} topics and ${preview.outbox} outbox entries.",
                    fontFamily = RubikFontFamily,
                    fontSize = 12.sp,
                    color = KaminariTheme.colors.textPrimary,
                )
                Text(
                    text = "Keeping this profile mixes rows received from two servers under one ID. Creating a new profile keeps the old data separate.",
                    fontFamily = RubikFontFamily,
                    fontSize = 12.sp,
                    color = KaminariTheme.colors.textSecondary,
                    lineHeight = 16.sp,
                )
                Text(
                    text = "Nothing is moved, merged or deleted automatically.",
                    style = KaminariTheme.techTypography.metricSubtext,
                    color = KaminariTheme.colors.textSecondary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onCreateNew,
                colors = ButtonDefaults.buttonColors(
                    containerColor = KaminariTheme.colors.accent,
                    contentColor = KaminariTheme.colors.background,
                ),
                modifier = Modifier.testTag("profile_preview_create_new"),
            ) {
                Text("Create new (recommended)", fontFamily = RubikFontFamily)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("profile_preview_cancel"),
                ) {
                    Text(
                        "Cancel",
                        fontFamily = RubikFontFamily,
                        fontSize = 11.sp,
                        color = KaminariTheme.colors.textSecondary,
                    )
                }
                TextButton(
                    onClick = onKeepAndMix,
                    modifier = Modifier.testTag("profile_preview_keep"),
                ) {
                    Text(
                        "Keep & mix",
                        fontFamily = RubikFontFamily,
                        fontSize = 11.sp,
                        color = KaminariTheme.colors.warning,
                    )
                }
            }
        },
    )
}

@Composable
private fun ProfileField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    testTag: String,
    leadingPerson: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontFamily = RubikFontFamily) },
        leadingIcon = if (leadingPerson) {
            {
                Icon(
                    Icons.Default.Person,
                    contentDescription = null,
                    tint = KaminariTheme.colors.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else {
            null
        },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        colors = profileFieldColors(),
        shape = RoundedCornerShape(7.dp),
        textStyle = androidx.compose.ui.text.TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 12.sp,
            color = KaminariTheme.colors.textPrimary,
        ),
    )
}

@Composable
private fun profileFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = KaminariTheme.colors.accent,
    unfocusedBorderColor = KaminariTheme.colors.border,
    focusedContainerColor = KaminariTheme.colors.surfaceSecondary,
    unfocusedContainerColor = KaminariTheme.colors.surfaceSecondary,
)
