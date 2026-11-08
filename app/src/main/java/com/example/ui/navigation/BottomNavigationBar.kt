package com.example.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Topic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Topic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.AppDestination
import com.example.ui.theme.KaminariTheme
import com.example.ui.theme.RubikFontFamily

sealed class NavigationItem(
    val destination: AppDestination,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
) {
    object Inbox : NavigationItem(
        AppDestination.INBOX,
        "Inbox",
        Icons.Default.Notifications,
        Icons.Default.NotificationsNone,
        "nav_inbox",
    )

    object Topics : NavigationItem(
        AppDestination.TOPICS,
        "Topics",
        Icons.Default.Topic,
        Icons.Outlined.Topic,
        "nav_topics",
    )

    object Saved : NavigationItem(
        AppDestination.SAVED,
        "Saved",
        Icons.Default.Bookmark,
        Icons.Default.BookmarkBorder,
        "nav_saved",
    )

    object Settings : NavigationItem(
        AppDestination.SETTINGS,
        "Settings",
        Icons.Default.Settings,
        Icons.Outlined.Settings,
        "nav_settings",
    )
}

val NavItems = listOf(
    NavigationItem.Inbox,
    NavigationItem.Topics,
    NavigationItem.Saved,
    NavigationItem.Settings,
)

@Composable
fun NftyBottomNavigationBar(
    currentTab: AppDestination,
    onTabSelected: (AppDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(KaminariTheme.colors.surfaceMain)
            .border(
                width = 0.85.dp,
                color = KaminariTheme.colors.border.copy(alpha = if (KaminariTheme.colors.isDark) 0.6f else 0.45f),
                shape = RoundedCornerShape(0.dp)
            )
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItems.forEach { item ->
                val isSelected = currentTab == item.destination

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (isSelected) {
                                if (KaminariTheme.colors.isDark) KaminariTheme.colors.surfaceElevated
                                else KaminariTheme.colors.surfaceSecondary
                            } else Color.Transparent
                        )
                        .clickable { onTabSelected(item.destination) }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag(item.testTag),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                            contentDescription = item.title,
                            tint = if (isSelected) KaminariTheme.colors.accent else KaminariTheme.colors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = item.title,
                            fontFamily = RubikFontFamily,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) KaminariTheme.colors.textPrimary else KaminariTheme.colors.textSecondary,
                        )
                    }
                }
            }
        }
    }
}
