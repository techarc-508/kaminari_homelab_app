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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NftyConfirmationSheet(
    title: String,
    message: String,
    actionUrl: String? = null,
    confirmLabel: String = "Proceed",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = KaminariTheme.colors.surfaceMain,
        contentColor = KaminariTheme.colors.textPrimary,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = if (isDestructive) Icons.Default.Warning else Icons.Default.OpenInBrowser,
                    contentDescription = null,
                    tint = if (isDestructive) KaminariTheme.colors.error else KaminariTheme.colors.accent,
                    modifier = Modifier.size(24.dp)
                )

                Text(
                    text = title,
                    fontFamily = RubikFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = KaminariTheme.colors.textPrimary,
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = message,
                fontFamily = RubikFontFamily,
                fontSize = 14.sp,
                color = KaminariTheme.colors.textSecondary,
                lineHeight = 19.sp,
            )

            if (actionUrl != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(KaminariTheme.colors.surfaceSecondary)
                        .border(1.dp, KaminariTheme.colors.border, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = actionUrl,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 12.sp,
                        color = KaminariTheme.colors.accent,
                        maxLines = 2,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dialog_cancel_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, KaminariTheme.colors.border),
                ) {
                    Text(
                        text = "Cancel",
                        fontFamily = RubikFontFamily,
                        color = KaminariTheme.colors.textSecondary,
                    )
                }

                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("dialog_confirm_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isDestructive) KaminariTheme.colors.error else KaminariTheme.colors.accent,
                        contentColor = KaminariTheme.colors.background,
                    ),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = confirmLabel,
                        fontFamily = RubikFontFamily,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
