package com.rplab.kaminarinfty.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.ui.theme.KaminariTheme

@Composable
fun NftySearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search cached notifications...",
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(0.85.dp, KaminariTheme.colors.border.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = KaminariTheme.colors.textSecondary,
                modifier = Modifier.size(17.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (query.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = KaminariTheme.techTypography.endpointUrl,
                        color = KaminariTheme.colors.textSecondary.copy(alpha = 0.6f),
                    )
                }

                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_text_input"),
                    textStyle = TextStyle(
                        fontFamily = KaminariTheme.techTypography.endpointUrl.fontFamily,
                        fontSize = 13.sp,
                        color = KaminariTheme.colors.textPrimary,
                    ),
                    cursorBrush = SolidColor(KaminariTheme.colors.accent),
                    singleLine = true,
                )
            }

            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear search",
                        tint = KaminariTheme.colors.textSecondary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            } else {
                // Key hint [/] matching live website screenshot
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(KaminariTheme.colors.surfaceSecondary)
                        .border(0.75.dp, KaminariTheme.colors.border.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "/",
                        style = KaminariTheme.techTypography.diagnosticCode.copy(fontSize = 10.sp),
                        color = KaminariTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}
