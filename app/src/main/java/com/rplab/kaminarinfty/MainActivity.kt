package com.rplab.kaminarinfty

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rplab.kaminarinfty.ui.components.NftyConfirmationSheet
import com.rplab.kaminarinfty.ui.navigation.NftyBottomNavigationBar
import com.rplab.kaminarinfty.ui.screens.InboxScreen
import com.rplab.kaminarinfty.ui.screens.NotificationDetailScreen
import com.rplab.kaminarinfty.ui.screens.SavedScreen
import com.rplab.kaminarinfty.ui.screens.SearchScreen
import com.rplab.kaminarinfty.ui.screens.SettingsScreen
import com.rplab.kaminarinfty.ui.screens.SetupScreen
import com.rplab.kaminarinfty.ui.screens.TopicsScreen
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val container = (application as KaminariApp).container
            val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(container))
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()

            val isDarkTheme = when (uiState.themeSetting) {
                ThemeSetting.SYSTEM -> isSystemInDarkTheme()
                ThemeSetting.NIGHT -> true
                ThemeSetting.DAY -> false
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                KaminariApp(
                    viewModel = mainViewModel,
                    uiState = uiState,
                    isDarkTheme = isDarkTheme,
                )
            }
        }
    }
}

@Composable
fun KaminariApp(
    viewModel: MainViewModel,
    uiState: MainUiState,
    isDarkTheme: Boolean,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Banner message triggers snackbar
    LaunchedEffect(uiState.bannerMessage) {
        uiState.bannerMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBanner()
        }
    }

    val showBottomBar = uiState.currentDestination in listOf(
        AppDestination.INBOX,
        AppDestination.TOPICS,
        AppDestination.SAVED,
        AppDestination.SETTINGS,
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = KaminariTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NftyBottomNavigationBar(
                    currentTab = uiState.selectedTab,
                    onTabSelected = { viewModel.selectTab(it) },
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(KaminariTheme.colors.background)
                .padding(bottom = if (showBottomBar) innerPadding.calculateBottomPadding() else 0.dp)
        ) {
            when (uiState.currentDestination) {
                AppDestination.SETUP -> {
                    SetupScreen(
                        serverConfig = uiState.serverConfig,
                        authState = uiState.authState,
                        onSaveConfig = { viewModel.saveConfig(it, connect = true) },
                        onSaveAsNewProfile = { viewModel.addServerProfile(it) },
                        hasExistingProfile = uiState.activeProfileId != null,
                        onTestConnection = { viewModel.testConnection(it) },
                    )
                }

                AppDestination.INBOX -> {
                    InboxScreen(
                        messages = uiState.messages,
                        serverUrl = uiState.serverConfig.serverUrl,
                        authState = uiState.authState,
                        activeFilter = uiState.activeFilter,
                        selectedTopicFilter = uiState.selectedTopicFilter,
                        isSampleData = uiState.isSampleData,
                        isCompactDensity = uiState.isCompactDensity,
                        errorCategory = uiState.errorCategory,
                        customErrorMessage = uiState.customErrorMessage,
                        onFilterChange = { viewModel.setFilter(it) },
                        onClearTopicFilter = { viewModel.setTopicFilter(null) },
                        onRetryStream = { viewModel.retryLiveStream() },
                        onMessageClick = { viewModel.openMessageDetail(it) },
                        onToggleSave = { viewModel.toggleSaveMessage(it) },
                        onMarkAllRead = { viewModel.markAllAsRead() },
                        onSearchClick = { viewModel.navigateTo(AppDestination.SEARCH) },
                        onToggleThemeClick = {
                            viewModel.setThemeSetting(
                                if (isDarkTheme) ThemeSetting.DAY else ThemeSetting.NIGHT
                            )
                        },
                        onSettingsClick = { viewModel.navigateTo(AppDestination.SETTINGS) },
                    )
                }

                AppDestination.DETAIL -> {
                    uiState.selectedMessage?.let { message ->
                        NotificationDetailScreen(
                            message = message,
                            onBackClick = { viewModel.navigateTo(uiState.selectedTab) },
                            onToggleSave = { viewModel.toggleSaveMessage(it) },
                            onDeleteMessage = { viewModel.deleteMessage(it) },
                            onOpenUrl = { viewModel.promptExternalUrl(it) },
                        )
                    } ?: run {
                        viewModel.navigateTo(AppDestination.INBOX)
                    }
                }

                AppDestination.TOPICS -> {
                    TopicsScreen(
                        topics = uiState.topics,
                        authState = uiState.authState,
                        isSampleData = uiState.isSampleData,
                        onTopicSelected = { topicName ->
                            viewModel.setTopicFilter(topicName)
                            viewModel.navigateTo(AppDestination.INBOX)
                        },
                        onToggleSubscription = { viewModel.toggleTopicSubscription(it) },
                        onTogglePause = { viewModel.toggleTopicPause(it) },
                        onAddTopic = { name, desc -> viewModel.addTopic(name, desc) },
                        onSearchClick = { viewModel.navigateTo(AppDestination.SEARCH) },
                        onToggleThemeClick = {
                            viewModel.setThemeSetting(
                                if (isDarkTheme) ThemeSetting.DAY else ThemeSetting.NIGHT
                            )
                        },
                        onSettingsClick = { viewModel.navigateTo(AppDestination.SETTINGS) },
                    )
                }

                AppDestination.SAVED -> {
                    val savedMessages = uiState.messages.filter { it.isSaved }
                    SavedScreen(
                        savedMessages = savedMessages,
                        authState = uiState.authState,
                        isSampleData = uiState.isSampleData,
                        onMessageClick = { viewModel.openMessageDetail(it) },
                        onToggleSave = { viewModel.toggleSaveMessage(it) },
                        onSearchClick = { viewModel.navigateTo(AppDestination.SEARCH) },
                        onToggleThemeClick = {
                            viewModel.setThemeSetting(
                                if (isDarkTheme) ThemeSetting.DAY else ThemeSetting.NIGHT
                            )
                        },
                        onSettingsClick = { viewModel.navigateTo(AppDestination.SETTINGS) },
                    )
                }

                AppDestination.SEARCH -> {
                    SearchScreen(
                        messages = uiState.messages,
                        onBackClick = { viewModel.navigateTo(uiState.selectedTab) },
                        onMessageClick = { viewModel.openMessageDetail(it) },
                        onToggleSave = { viewModel.toggleSaveMessage(it) },
                    )
                }

                AppDestination.SETTINGS -> {
                    SettingsScreen(
                        serverConfig = uiState.serverConfig,
                        authState = uiState.authState,
                        profiles = uiState.profiles,
                        activeProfileId = uiState.activeProfileId,
                        reconnectAttempt = uiState.reconnectAttempt,
                        lastCursor = uiState.lastCursor,
                        themeSetting = uiState.themeSetting,
                        isCompactDensity = uiState.isCompactDensity,
                        isSampleData = uiState.isSampleData,
                        sampleDataEnabled = uiState.sampleDataEnabled,
                        historyCount = uiState.messages.size,
                        malformedLines = uiState.malformedLines,
                        onThemeChange = { viewModel.setThemeSetting(it) },
                        onToggleDensity = { viewModel.toggleDensity() },
                        onTestConnection = { viewModel.testConnection(uiState.serverConfig) },
                        onSelectProfile = { viewModel.switchProfile(it) },
                        onCatchUp = { viewModel.retryLiveStream() },
                        onNavigateToSetup = { viewModel.navigateTo(AppDestination.SETUP) },
                        onClearHistory = { viewModel.clearHistory() },
                        onClearSaved = { viewModel.clearSaved() },
                        onToggleSampleData = { viewModel.setSampleDataEnabled(it) },
                        onSearchClick = { viewModel.navigateTo(AppDestination.SEARCH) },
                    )
                }
            }
        }
    }

    // External URL Confirmation Dialog (Field manual security requirement)
    uiState.urlToConfirm?.let { url ->
        NftyConfirmationSheet(
            title = "Open External Link",
            message = "This action will open an external link in your web browser. Ensure you trust the server source before proceeding.",
            actionUrl = url,
            confirmLabel = "Open Browser",
            onConfirm = {
                viewModel.dismissUrlPrompt()
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(browserIntent)
                } catch (e: Exception) {
                    // Silently ignore or show error
                }
            },
            onDismiss = { viewModel.dismissUrlPrompt() }
        )
    }
}
