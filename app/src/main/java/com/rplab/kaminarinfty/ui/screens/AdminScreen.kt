package com.rplab.kaminarinfty.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rplab.kaminarinfty.admin.AdminCapability
import com.rplab.kaminarinfty.admin.AdminCapabilityState
import com.rplab.kaminarinfty.admin.AdminCatalog
import com.rplab.kaminarinfty.admin.AdminSection
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.ui.components.NftyTopBar
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.RubikFontFamily

/**
 * Complete administration surface, preserved per P-03 while privileged
 * execution is gateway-only. Phase 1 shows honest capability state: nothing is
 * simulated and nothing claims to have executed.
 */
@Composable
fun AdminScreen(
    authState: AuthState,
    profileLabel: String,
    onBackClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
            .testTag("admin_screen"),
    ) {
        NftyTopBar(
            title = "Administration",
            subtitle = "gateway-backed • $profileLabel",
            authState = authState,
            showBackButton = true,
            onBackClick = onBackClick,
            onSettingsClick = onSettingsClick,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                AdminNotice()
            }
            AdminSection.entries.forEach { section ->
                val capabilities = AdminCatalog.bySection(section)
                if (capabilities.isNotEmpty()) {
                    item(key = "section_${section.name}") {
                        SectionHeader(section)
                    }
                    items(capabilities, key = { it.id }) { capability ->
                        CapabilityRow(capability)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }
}

@Composable
private fun AdminNotice() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(
                width = 1.dp,
                color = KaminariTheme.colors.border,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "PHASE 1 FOUNDATION",
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = KaminariTheme.colors.accent,
        )
        Text(
            text = "The administration UI is preserved, but every privileged operation is " +
                "gateway-backed. No gateway is deployed yet, so no operation below can run and " +
                "nothing here is simulated.",
            fontFamily = RubikFontFamily,
            fontSize = 12.sp,
            color = KaminariTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun SectionHeader(section: AdminSection) {
    Text(
        text = section.label.uppercase(),
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = KaminariTheme.colors.textSecondary,
        modifier = Modifier.padding(top = 10.dp, start = 2.dp, bottom = 2.dp),
    )
}

@Composable
private fun CapabilityRow(capability: AdminCapability) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(KaminariTheme.colors.surfaceMain)
            .border(
                width = 1.dp,
                color = KaminariTheme.colors.border.copy(alpha = 0.7f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(14.dp)
            .testTag("admin_capability_${capability.id}"),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = capability.title,
                fontFamily = RubikFontFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = KaminariTheme.colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            StateBadge(capability.state)
        }
        Text(
            text = capability.description,
            fontFamily = RubikFontFamily,
            fontSize = 11.sp,
            color = KaminariTheme.colors.textSecondary,
        )
        capability.operationId?.let { operationId ->
            Text(
                text = "op: $operationId" + if (capability.mutation) "  •  mutation" else "",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 9.sp,
                color = KaminariTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun StateBadge(state: AdminCapabilityState) {
    val color = when (state) {
        AdminCapabilityState.PLANNED -> KaminariTheme.colors.textSecondary
        AdminCapabilityState.GATEWAY_REQUIRED -> KaminariTheme.colors.accent
        AdminCapabilityState.UNAVAILABLE -> KaminariTheme.colors.textSecondary
    }
    Text(
        text = state.label.uppercase(),
        fontFamily = JetBrainsMonoFontFamily,
        fontSize = 9.sp,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
