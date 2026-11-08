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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Topic
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
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyTopic
import com.rplab.kaminarinfty.model.TopicPermission
import com.rplab.kaminarinfty.ui.components.NftyTopBar
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily

@Composable
fun TopicsScreen(
    topics: List<NftyTopic>,
    authState: AuthState,
    isSampleData: Boolean,
    onTopicSelected: (String) -> Unit,
    onToggleSubscription: (String) -> Unit,
    onTogglePause: (String) -> Unit,
    onAddTopic: (String, String) -> Unit,
    onSearchClick: () -> Unit,
    onToggleThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var newTopicName by remember { mutableStateOf("") }
    var newTopicDesc by remember { mutableStateOf("") }
    var nameError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
    ) {
        NftyTopBar(
            title = "Topics",
            subtitle = "SUBSCRIPTION REGISTRY // ${topics.count { it.isSubscribed }} ACTIVE",
            authState = authState,
            isSampleData = isSampleData,
            onSearchClick = onSearchClick,
            onToggleThemeClick = onToggleThemeClick,
            onSettingsClick = onSettingsClick,
        )

        // Add topic header row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "SUBSCRIBED TOPICS",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = KaminariTheme.colors.textSecondary,
                        letterSpacing = 1.sp,
                    )
                    Text(
                        text = "Tap a topic to view its filtered stream",
                        fontFamily = RubikFontFamily,
                        fontSize = 12.sp,
                        color = KaminariTheme.colors.textSecondary,
                    )
                }

                Button(
                    onClick = { showAddDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KaminariTheme.colors.accent,
                        contentColor = KaminariTheme.colors.background,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("add_topic_button"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text("Add Topic", fontFamily = RubikFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Topics List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(topics, key = { it.name }) { topic ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(KaminariTheme.colors.surfaceMain)
                        .border(
                            1.dp,
                            if (topic.isSubscribed && !topic.isPaused) KaminariTheme.colors.border.copy(alpha = 0.8f)
                            else KaminariTheme.colors.border.copy(alpha = 0.4f),
                            RoundedCornerShape(12.dp)
                        )
                        .clickable { onTopicSelected(topic.name) }
                        .padding(14.dp)
                        .testTag("topic_item_${topic.name}")
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
                                Icon(
                                    imageVector = Icons.Default.Topic,
                                    contentDescription = null,
                                    tint = if (topic.isSubscribed) KaminariTheme.colors.accent else KaminariTheme.colors.textSecondary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "#${topic.name}",
                                    style = KaminariTheme.techTypography.topic.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                                    color = KaminariTheme.colors.textPrimary,
                                )
                            }

                            // Permission badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        when (topic.permission) {
                                            TopicPermission.GUEST_READ -> KaminariTheme.colors.surfaceSecondary
                                            TopicPermission.AUTH_REQUIRED -> KaminariTheme.colors.warning.copy(alpha = 0.18f)
                                            TopicPermission.RESTRICTED -> KaminariTheme.colors.error.copy(alpha = 0.18f)
                                        }
                                    )
                                    .border(
                                        0.75.dp,
                                        when (topic.permission) {
                                            TopicPermission.GUEST_READ -> KaminariTheme.colors.border.copy(alpha = 0.7f)
                                            TopicPermission.AUTH_REQUIRED -> KaminariTheme.colors.warning
                                            TopicPermission.RESTRICTED -> KaminariTheme.colors.error
                                        },
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = topic.permission.label,
                                    style = KaminariTheme.techTypography.httpStatus.copy(fontSize = 9.sp),
                                    color = when (topic.permission) {
                                        TopicPermission.GUEST_READ -> KaminariTheme.colors.textSecondary
                                        TopicPermission.AUTH_REQUIRED -> KaminariTheme.colors.warning
                                        TopicPermission.RESTRICTED -> KaminariTheme.colors.error
                                    },
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = topic.description,
                            fontFamily = RubikFontFamily,
                            fontSize = 12.sp,
                            color = KaminariTheme.colors.textSecondary,
                            lineHeight = 16.sp,
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Controls: pause toggle & subscribe switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text(
                                    text = "${topic.messageCount} messages cached",
                                    style = KaminariTheme.techTypography.metricSubtext,
                                    color = KaminariTheme.colors.textSecondary,
                                )

                                if (topic.isSubscribed) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(KaminariTheme.colors.surfaceSecondary)
                                            .border(0.75.dp, KaminariTheme.colors.border, RoundedCornerShape(4.dp))
                                            .clickable { onTogglePause(topic.name) }
                                            .padding(horizontal = 6.dp, vertical = 3.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        ) {
                                            Icon(
                                                imageVector = if (topic.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                                contentDescription = null,
                                                tint = KaminariTheme.colors.accent,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = if (topic.isPaused) "Resume" else "Pause",
                                                fontFamily = RubikFontFamily,
                                                fontSize = 10.sp,
                                                color = KaminariTheme.colors.textPrimary,
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = if (topic.isSubscribed) "Subscribed" else "Unsubscribed",
                                    fontFamily = RubikFontFamily,
                                    fontSize = 11.sp,
                                    color = if (topic.isSubscribed) KaminariTheme.colors.accent else KaminariTheme.colors.textSecondary,
                                )
                                Switch(
                                    checked = topic.isSubscribed,
                                    onCheckedChange = { onToggleSubscription(topic.name) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = KaminariTheme.colors.accent),
                                    modifier = Modifier.testTag("switch_topic_${topic.name}")
                                )
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Add Topic Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = KaminariTheme.colors.surfaceMain,
            title = {
                Text(
                    text = "Subscribe to Topic",
                    fontFamily = RubikFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = KaminariTheme.colors.textPrimary,
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Enter the topic name configured on your ntfy server (e.g. homelab-alerts). Names should use lowercase letters, numbers, hyphens, and underscores.",
                        fontFamily = RubikFontFamily,
                        fontSize = 12.sp,
                        color = KaminariTheme.colors.textSecondary,
                        lineHeight = 16.sp,
                    )

                    OutlinedTextField(
                        value = newTopicName,
                        onValueChange = {
                            newTopicName = it
                            nameError = if (it.isBlank()) "Topic name cannot be empty" else null
                        },
                        label = { Text("Topic Name", fontFamily = RubikFontFamily) },
                        prefix = { Text("#", fontFamily = JetBrainsMonoFontFamily, color = KaminariTheme.colors.accent) },
                        isError = nameError != null,
                        supportingText = nameError?.let { { Text(it, color = KaminariTheme.colors.error) } },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KaminariTheme.colors.accent,
                            unfocusedBorderColor = KaminariTheme.colors.border,
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("add_topic_name_input")
                    )

                    OutlinedTextField(
                        value = newTopicDesc,
                        onValueChange = { newTopicDesc = it },
                        label = { Text("Description (Optional)", fontFamily = RubikFontFamily) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = KaminariTheme.colors.accent,
                            unfocusedBorderColor = KaminariTheme.colors.border,
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val sanitized = newTopicName.trim().lowercase().replace(Regex("[^a-z0-9_-]"), "")
                        if (sanitized.isNotBlank()) {
                            onAddTopic(sanitized, newTopicDesc)
                            newTopicName = ""
                            newTopicDesc = ""
                            showAddDialog = false
                        } else {
                            nameError = "Valid topic name required"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KaminariTheme.colors.accent,
                        contentColor = KaminariTheme.colors.background,
                    ),
                    modifier = Modifier.testTag("dialog_confirm_add_topic"),
                ) {
                    Text("Subscribe", fontFamily = RubikFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showAddDialog = false },
                    border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
                ) {
                    Text("Cancel", fontFamily = RubikFontFamily, color = KaminariTheme.colors.textSecondary)
                }
            }
        )
    }
}
