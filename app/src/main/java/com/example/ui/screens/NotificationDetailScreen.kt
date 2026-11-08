package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.NftyMessage
import com.example.ui.components.NftyPriorityMarker
import com.example.ui.components.NftyTopicChip
import com.example.ui.theme.KaminariTheme
import com.example.ui.theme.RubikFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun NotificationDetailScreen(
    message: NftyMessage,
    onBackClick: () -> Unit,
    onToggleSave: (String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBackClick() }

    val context = LocalContext.current
    val fullDateString = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.getDefault()).format(Date(message.time))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top Action Bar
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
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(KaminariTheme.colors.surfaceSecondary)
                        .border(0.85.dp, KaminariTheme.colors.border, RoundedCornerShape(8.dp))
                        .testTag("detail_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = KaminariTheme.colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Bookmark toggle
                    IconButton(
                        onClick = { onToggleSave(message.id) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(KaminariTheme.colors.surfaceSecondary)
                            .border(0.85.dp, KaminariTheme.colors.border, RoundedCornerShape(8.dp))
                            .testTag("detail_save_button")
                    ) {
                        Icon(
                            imageVector = if (message.isSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = if (message.isSaved) "Saved" else "Save",
                            tint = if (message.isSaved) KaminariTheme.colors.accent else KaminariTheme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Share
                    IconButton(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TITLE, message.title ?: "Notification")
                                putExtra(Intent.EXTRA_TEXT, "${message.title?.let { "$it\n\n" } ?: ""}${message.message}\n\n[Topic: #${message.topic}]")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Notification"))
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(KaminariTheme.colors.surfaceSecondary)
                            .border(0.85.dp, KaminariTheme.colors.border, RoundedCornerShape(8.dp))
                            .testTag("detail_share_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Share",
                            tint = KaminariTheme.colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Delete
                    IconButton(
                        onClick = { onDeleteMessage(message.id) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(KaminariTheme.colors.surfaceSecondary)
                            .border(0.85.dp, KaminariTheme.colors.border, RoundedCornerShape(8.dp))
                            .testTag("detail_delete_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = KaminariTheme.colors.error,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // Detail Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp, vertical = 6.dp)
        ) {
            // Topic, Priority and Time Metadata Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NftyTopicChip(topic = message.topic)
                NftyPriorityMarker(priority = message.priority)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Title in clean compact sans-serif (Rubik)
            if (!message.title.isNullOrBlank()) {
                Text(
                    text = message.title,
                    fontFamily = RubikFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = KaminariTheme.colors.textPrimary,
                    lineHeight = 26.sp,
                    letterSpacing = (-0.2).sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Timestamp strictly in Monospace
            Text(
                text = fullDateString,
                style = KaminariTheme.techTypography.timestamp,
                color = KaminariTheme.colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Body Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(KaminariTheme.colors.surfaceMain)
                    .border(0.85.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text(
                        text = "PAYLOAD // MESSAGE BODY",
                        style = KaminariTheme.techTypography.diagnosticCode,
                        color = KaminariTheme.colors.textSecondary,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = message.message,
                        fontFamily = RubikFontFamily,
                        fontSize = 14.5.sp,
                        color = KaminariTheme.colors.textPrimary,
                        lineHeight = 21.sp,
                    )
                }
            }

            // Tags
            if (message.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "TAGS",
                    style = KaminariTheme.techTypography.diagnosticCode,
                    color = KaminariTheme.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    message.tags.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(KaminariTheme.colors.surfaceSecondary)
                                .border(0.75.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = tag,
                                style = KaminariTheme.techTypography.topic,
                                color = KaminariTheme.colors.textSecondary,
                            )
                        }
                    }
                }
            }

            // Attachment Card
            if (message.attachment != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(KaminariTheme.colors.surfaceSecondary)
                        .border(0.85.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Attachment,
                                contentDescription = null,
                                tint = KaminariTheme.colors.accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Column {
                                Text(
                                    text = message.attachment.name,
                                    fontFamily = RubikFontFamily,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp,
                                    color = KaminariTheme.colors.textPrimary,
                                )
                                Text(
                                    text = "${message.attachment.type ?: "File"} • ${(message.attachment.size ?: 0) / 1024} KB",
                                    style = KaminariTheme.techTypography.metricSubtext,
                                    color = KaminariTheme.colors.textSecondary,
                                )
                            }
                        }

                        Button(
                            onClick = { onOpenUrl(message.attachment.url) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KaminariTheme.colors.surfaceMain,
                                contentColor = KaminariTheme.colors.accent,
                            ),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
                        ) {
                            Text("Open", fontFamily = RubikFontFamily, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Click URL / Actions (Safe execution with confirmation)
            if (message.click != null || message.actions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "ACTIONS & EXTERNAL LINKS",
                    style = KaminariTheme.techTypography.diagnosticCode,
                    color = KaminariTheme.colors.textSecondary,
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (message.click != null) {
                    OutlinedButton(
                        onClick = { onOpenUrl(message.click) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_link_button"),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(0.85.dp, KaminariTheme.colors.accent),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInNew,
                                contentDescription = null,
                                tint = KaminariTheme.colors.accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Open Attached Link (Requires Confirmation)",
                                fontFamily = RubikFontFamily,
                                fontSize = 13.sp,
                                color = KaminariTheme.colors.accent,
                            )
                        }
                    }
                }

                message.actions.forEach { action ->
                    if (action.url != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { onOpenUrl(action.url) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KaminariTheme.colors.surfaceSecondary,
                                contentColor = KaminariTheme.colors.textPrimary,
                            ),
                            border = androidx.compose.foundation.BorderStroke(0.85.dp, KaminariTheme.colors.border),
                        ) {
                            Text(text = action.label, fontFamily = RubikFontFamily, fontSize = 13.sp)
                        }
                    }
                }
            }

            // Copy Message Action
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("ntfy message", message.message)
                    clipboard.setPrimaryClip(clip)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("copy_message_button"),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(0.85.dp, KaminariTheme.colors.border),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy message",
                        tint = KaminariTheme.colors.textPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "Copy Payload to Clipboard",
                        fontFamily = RubikFontFamily,
                        fontSize = 13.sp,
                        color = KaminariTheme.colors.textPrimary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
