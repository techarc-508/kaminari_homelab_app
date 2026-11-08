package com.rplab.kaminarinfty.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.ui.components.NftyEmptyState
import com.rplab.kaminarinfty.ui.components.NftyNotificationRow
import com.rplab.kaminarinfty.ui.components.NftySearchField
import com.rplab.kaminarinfty.ui.components.NftyTopicChip
import com.rplab.kaminarinfty.ui.theme.JetBrainsMonoFontFamily
import com.rplab.kaminarinfty.ui.theme.KaminariTheme

@Composable
fun SearchScreen(
    messages: List<NftyMessage>,
    onBackClick: () -> Unit,
    onMessageClick: (NftyMessage) -> Unit,
    onToggleSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onBackClick() }

    var query by remember { mutableStateOf("") }
    var selectedTopicFilter by remember { mutableStateOf<String?>(null) }

    // Unique topics
    val availableTopics = remember(messages) {
        messages.map { it.topic }.distinct().sorted()
    }

    val searchResults = remember(query, selectedTopicFilter, messages) {
        if (query.isBlank() && selectedTopicFilter == null) {
            emptyList()
        } else {
            messages.filter { msg ->
                val matchesTopic = selectedTopicFilter == null || msg.topic.equals(selectedTopicFilter, ignoreCase = true)
                val matchesQuery = query.isBlank() ||
                        msg.title?.contains(query, ignoreCase = true) == true ||
                        msg.message.contains(query, ignoreCase = true) ||
                        msg.topic.contains(query, ignoreCase = true) ||
                        msg.tags.any { it.contains(query, ignoreCase = true) }
                matchesTopic && matchesQuery
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(KaminariTheme.colors.background)
            .statusBarsPadding()
    ) {
        // Search Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(
                onClick = onBackClick,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(KaminariTheme.colors.surfaceSecondary)
                    .testTag("search_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = KaminariTheme.colors.textPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            NftySearchField(
                query = query,
                onQueryChange = { query = it },
                modifier = Modifier.weight(1f),
                placeholder = "Search messages, topics, tags...",
            )
        }

        // Topic Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "TOPIC:",
                style = KaminariTheme.techTypography.diagnosticCode,
                color = KaminariTheme.colors.textSecondary,
            )

            availableTopics.forEach { topic ->
                val isSelected = selectedTopicFilter == topic
                NftyTopicChip(
                    topic = topic,
                    isSelected = isSelected,
                    onClick = {
                        selectedTopicFilter = if (isSelected) null else topic
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Results Status Bar
        if (query.isNotBlank() || selectedTopicFilter != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "SEARCH RESULTS // ${searchResults.size} MATCHES",
                    style = KaminariTheme.techTypography.diagnosticCode,
                    color = KaminariTheme.colors.accent,
                )
            }
        }

        // Main List or Empty States
        when {
            query.isBlank() && selectedTopicFilter == null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NftyEmptyState(
                        title = "Local Cache Search",
                        message = "Search across cached notifications, topics, priorities, and tags stored on this device.",
                        technicalCode = "SEARCH // READY",
                        icon = Icons.Default.Search,
                    )
                }
            }

            searchResults.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    NftyEmptyState(
                        title = "No results found",
                        message = "No cached notifications match '$query'. Try broader terms or remove topic filters.",
                        technicalCode = "QUERY // 0_MATCHES",
                        icon = Icons.Default.Search,
                    )
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(searchResults, key = { it.id }) { message ->
                        NftyNotificationRow(
                            message = message,
                            onClick = { onMessageClick(message) },
                            onToggleSave = { onToggleSave(message.id) },
                        )
                    }

                    item {
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
    }
}
