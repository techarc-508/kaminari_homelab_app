package com.rplab.kaminarinfty.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.ui.components.NftyEmptyState
import com.rplab.kaminarinfty.ui.components.NftyNotificationRow
import com.rplab.kaminarinfty.ui.components.NftySearchField
import com.rplab.kaminarinfty.ui.components.NftyTopBar
import com.rplab.kaminarinfty.ui.theme.KaminariTheme

@Composable
fun SavedScreen(
    savedMessages: List<NftyMessage>,
    authState: AuthState,
    isSampleData: Boolean,
    onMessageClick: (NftyMessage) -> Unit,
    onToggleSave: (String) -> Unit,
    onSearchClick: () -> Unit,
    onToggleThemeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredSaved = savedMessages.filter {
        if (searchQuery.isBlank()) true
        else it.title?.contains(searchQuery, ignoreCase = true) == true ||
                it.message.contains(searchQuery, ignoreCase = true) ||
                it.topic.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
    ) {
        NftyTopBar(
            title = "Saved",
            subtitle = "BOOKMARKED NOTIFICATIONS // ${savedMessages.size} ENTRIES",
            authState = authState,
            isSampleData = isSampleData,
            onSearchClick = onSearchClick,
            onToggleThemeClick = onToggleThemeClick,
            onSettingsClick = onSettingsClick,
        )

        if (savedMessages.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                NftySearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = "Filter in saved messages...",
                )
            }
        }

        if (savedMessages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NftyEmptyState(
                    title = "No saved notifications",
                    message = "Tap the bookmark icon on any notification in the inbox to pin it here for offline reference.",
                    technicalCode = "SYS.STAT // 0_BOOKMARKS",
                    icon = Icons.Default.BookmarkBorder,
                )
            }
        } else if (filteredSaved.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                NftyEmptyState(
                    title = "No saved matches",
                    message = "No bookmarked notifications matched your query '$searchQuery'.",
                    technicalCode = "FILTER // EMPTY_SET",
                    actionLabel = "Clear Filter",
                    onActionClick = { searchQuery = "" }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(filteredSaved, key = { it.id }) { message ->
                    NftyNotificationRow(
                        message = message,
                        onClick = { onMessageClick(message) },
                        onToggleSave = { onToggleSave(message.id) },
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
    }
}
