package com.rplab.kaminarinfty

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rplab.kaminarinfty.delivery.NftyNotifier
import com.rplab.kaminarinfty.ui.components.NftyConfirmationSheet
import com.rplab.kaminarinfty.ui.navigation.NftyBottomNavigationBar
import com.rplab.kaminarinfty.ui.screens.ActivityScreen
import com.rplab.kaminarinfty.ui.screens.InboxScreen
import com.rplab.kaminarinfty.ui.screens.NotificationDetailScreen
import com.rplab.kaminarinfty.ui.screens.OutboxScreen
import com.rplab.kaminarinfty.ui.screens.ProfileChangePreviewDialog
import com.rplab.kaminarinfty.ui.screens.PublishScreen
import com.rplab.kaminarinfty.ui.screens.SearchScreen
import com.rplab.kaminarinfty.ui.screens.SettingsScreen
import com.rplab.kaminarinfty.ui.screens.SetupScreen
import com.rplab.kaminarinfty.ui.screens.TopicsScreen
import com.rplab.kaminarinfty.ui.theme.KaminariTheme
import com.rplab.kaminarinfty.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    /** Message ID from a notification tap, consumed once the UI is ready. */
    private val pendingMessageId = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingMessageId.value = intent?.getStringExtra(NftyNotifier.EXTRA_MESSAGE_ID)
        intent?.removeExtra(NftyNotifier.EXTRA_MESSAGE_ID)
        setContent {
            val container = (application as KaminariApp).container
            val mainViewModel: MainViewModel = viewModel(factory = MainViewModelFactory(container))
            val publishViewModel: PublishViewModel =
                viewModel(factory = PublishViewModelFactory(container))
            val activityViewModel: ActivityViewModel =
                viewModel(factory = ActivityViewModelFactory(container))
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
            val publishState by publishViewModel.uiState.collectAsStateWithLifecycle()
            val activityState by activityViewModel.uiState.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val notificationsEnabled = rememberNotificationsEnabled()

            // Android 13+ runtime permission, requested once a profile exists.
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { /* Denial is surfaced in Settings; delivery keeps working in-app. */ }
            LaunchedEffect(uiState.activeProfileId) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    uiState.activeProfileId != null &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS,
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // Deep link from a message notification.
            LaunchedEffect(pendingMessageId.value) {
                pendingMessageId.value?.let { messageId ->
                    mainViewModel.openMessageFromNotification(messageId)
                    pendingMessageId.value = null
                }
            }

            val isDarkTheme = when (uiState.themeSetting) {
                ThemeSetting.SYSTEM -> isSystemInDarkTheme()
                ThemeSetting.NIGHT -> true
                ThemeSetting.DAY -> false
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                KaminariApp(
                    mainViewModel = mainViewModel,
                    uiState = uiState,
                    publishViewModel = publishViewModel,
                    publishState = publishState,
                    activityViewModel = activityViewModel,
                    activityState = activityState,
                    isDarkTheme = isDarkTheme,
                    notificationsEnabled = notificationsEnabled,
                    onOpenNotificationSettings = {
                        // Per-app notification settings only exist on API 26+;
                        // older devices fall back to the app details page.
                        val settingsIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        } else {
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.fromParts("package", context.packageName, null),
                            )
                        }
                        runCatching { context.startActivity(settingsIntent) }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingMessageId.value = intent.getStringExtra(NftyNotifier.EXTRA_MESSAGE_ID)
        intent.removeExtra(NftyNotifier.EXTRA_MESSAGE_ID)
    }
}

@Composable
private fun rememberNotificationsEnabled(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var enabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                enabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return enabled
}

@Composable
fun KaminariApp(
    mainViewModel: MainViewModel,
    uiState: MainUiState,
    publishViewModel: PublishViewModel,
    publishState: PublishUiState,
    activityViewModel: ActivityViewModel,
    activityState: ActivityUiState,
    isDarkTheme: Boolean,
    notificationsEnabled: Boolean,
    onOpenNotificationSettings: () -> Unit,
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var messageToDelete by remember { mutableStateOf<String?>(null) }
    var clearKind by remember { mutableStateOf<String?>(null) }
    var profileToSwitch by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Banner message triggers snackbar
    LaunchedEffect(uiState.bannerMessage) {
        uiState.bannerMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            mainViewModel.clearBanner()
        }
    }
    LaunchedEffect(publishState.notice) {
        publishState.notice?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            publishViewModel.clearNotice()
        }
    }

    val mainDestinations = listOf(
        AppDestination.INBOX,
        AppDestination.TOPICS,
        AppDestination.COMPOSE,
        AppDestination.OUTBOX,
        AppDestination.ACTIVITY,
        AppDestination.SETTINGS,
    )
    val showBottomBar = uiState.currentDestination in mainDestinations
    val activeProfileName = uiState.profiles
        .firstOrNull { it.id == uiState.activeProfileId }
        ?.displayName
        ?.takeIf { it.isNotBlank() }
        ?: uiState.serverConfig.serverUrl.ifBlank { "no profile" }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = KaminariTheme.colors.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NftyBottomNavigationBar(
                    currentTab = uiState.selectedTab,
                    onTabSelected = { mainViewModel.selectTab(it) },
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
                        onSaveConfig = { mainViewModel.saveConfig(it, connect = true) },
                        onSaveAsNewProfile = { mainViewModel.addServerProfile(it) },
                        hasExistingProfile = uiState.activeProfileId != null,
                        onTestConnection = { mainViewModel.testConnection(it) },
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
                        searchQuery = uiState.searchQuery,
                        onSearchQueryChange = { mainViewModel.setSearchQuery(it) },
                        onFilterChange = { mainViewModel.setFilter(it) },
                        onClearTopicFilter = { mainViewModel.setTopicFilter(null) },
                        onRetryStream = { mainViewModel.retryLiveStream() },
                        onRefresh = { mainViewModel.retryLiveStream() },
                        onMessageClick = { mainViewModel.openMessageDetail(it) },
                        onToggleSave = { mainViewModel.toggleSaveMessage(it) },
                        onMarkAllRead = { mainViewModel.markAllAsRead() },
                        onSearchClick = { mainViewModel.navigateTo(AppDestination.SEARCH) },
                        onToggleThemeClick = {
                            mainViewModel.setThemeSetting(
                                if (isDarkTheme) ThemeSetting.DAY else ThemeSetting.NIGHT
                            )
                        },
                        onSettingsClick = { mainViewModel.navigateTo(AppDestination.SETTINGS) },
                    )
                }

                AppDestination.DETAIL -> {
                    uiState.selectedMessage?.let { message ->
                        NotificationDetailScreen(
                            message = message,
                            onBackClick = { mainViewModel.navigateTo(uiState.selectedTab) },
                            onToggleSave = { mainViewModel.toggleSaveMessage(it) },
                            onDeleteMessage = { messageToDelete = it },
                            onOpenUrl = { mainViewModel.promptExternalUrl(it) },
                        )
                    } ?: run {
                        mainViewModel.navigateTo(AppDestination.INBOX)
                    }
                }

                AppDestination.TOPICS -> {
                    TopicsScreen(
                        topics = uiState.topics,
                        authState = uiState.authState,
                        isSampleData = uiState.isSampleData,
                        errorCategory = uiState.errorCategory,
                        customErrorMessage = uiState.customErrorMessage,
                        onTopicSelected = { topicName ->
                            mainViewModel.setTopicFilter(topicName)
                            mainViewModel.navigateTo(AppDestination.INBOX)
                        },
                        onToggleSubscription = { mainViewModel.toggleTopicSubscription(it) },
                        onTogglePause = { mainViewModel.toggleTopicPause(it) },
                        onAddTopic = { name, desc -> mainViewModel.addTopic(name, desc) },
                        onRetryStream = { mainViewModel.retryLiveStream() },
                        onSearchClick = { mainViewModel.navigateTo(AppDestination.SEARCH) },
                        onToggleThemeClick = {
                            mainViewModel.setThemeSetting(
                                if (isDarkTheme) ThemeSetting.DAY else ThemeSetting.NIGHT
                            )
                        },
                        onSettingsClick = { mainViewModel.navigateTo(AppDestination.SETTINGS) },
                    )
                }

                AppDestination.COMPOSE -> {
                    PublishScreen(
                        state = publishState,
                        authState = uiState.authState,
                        profileLabel = activeProfileName,
                        isSampleData = uiState.isSampleData,
                        onTopicChange = publishViewModel::setTopic,
                        onTitleChange = publishViewModel::setTitle,
                        onBodyChange = publishViewModel::setBody,
                        onPriorityChange = publishViewModel::setPriority,
                        onTagsChange = publishViewModel::setTags,
                        onClickUrlChange = publishViewModel::setClickUrl,
                        onAttachmentUrlChange = publishViewModel::setAttachmentUrl,
                        onDelayChange = publishViewModel::setDelay,
                        onToggleAdvanced = publishViewModel::toggleAdvanced,
                        onSend = publishViewModel::send,
                        onRetryLast = publishViewModel::retryLast,
                        onDismissOutcome = publishViewModel::dismissOutcome,
                        onClearValidation = publishViewModel::clearValidationError,
                        onSettingsClick = { mainViewModel.navigateTo(AppDestination.SETTINGS) },
                    )
                }

                AppDestination.OUTBOX -> {
                    OutboxScreen(
                        state = publishState,
                        authState = uiState.authState,
                        profileLabel = activeProfileName,
                        isSampleData = uiState.isSampleData,
                        onRetry = { publishViewModel.retryEntry(it) },
                        onCancel = publishViewModel::cancelEntry,
                        onClearNotice = publishViewModel::clearNotice,
                        onSettingsClick = { mainViewModel.navigateTo(AppDestination.SETTINGS) },
                    )
                }

                AppDestination.ACTIVITY -> {
                    ActivityScreen(
                        state = activityState,
                        authState = uiState.authState,
                        profileLabel = activeProfileName,
                        isSampleData = uiState.isSampleData,
                        onFilterChange = activityViewModel::setFilter,
                        onSettingsClick = { mainViewModel.navigateTo(AppDestination.SETTINGS) },
                    )
                }

                AppDestination.SEARCH -> {
                    SearchScreen(
                        messages = uiState.messages,
                        onBackClick = { mainViewModel.navigateTo(uiState.selectedTab) },
                        onMessageClick = { mainViewModel.openMessageDetail(it) },
                        onToggleSave = { mainViewModel.toggleSaveMessage(it) },
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
                        profileEdit = uiState.profileEdit,
                        profileEditError = uiState.profileEditError,
                        onThemeChange = { mainViewModel.setThemeSetting(it) },
                        onToggleDensity = { mainViewModel.toggleDensity() },
                        onTestConnection = { mainViewModel.testActiveConnection() },
                        onSelectProfile = { profileId ->
                            val profile = uiState.profiles.firstOrNull { it.id == profileId }
                            if (profile != null) {
                                profileToSwitch = profile.id to profile.displayName
                            }
                        },
                        onEditProfile = { mainViewModel.startProfileEdit(it) },
                        onAddProfile = { mainViewModel.addServerProfile(it) },
                        onCancelProfileEdit = { mainViewModel.cancelProfileEdit() },
                        onSaveProfileEdit = { url, auth, username, secret, https ->
                            mainViewModel.saveProfileEdit(url, auth, username, secret, https)
                        },
                        onCatchUp = { mainViewModel.retryLiveStream() },
                        onClearHistory = { clearKind = "history" },
                        onClearSaved = { clearKind = "saved" },
                        onToggleSampleData = { mainViewModel.setSampleDataEnabled(it) },
                        deliveryMode = uiState.deliveryMode,
                        deliveryStatus = uiState.deliveryStatus,
                        notificationsEnabled = notificationsEnabled,
                        onSelectDeliveryMode = { mainViewModel.setDeliveryMode(it) },
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onSearchClick = { mainViewModel.navigateTo(AppDestination.SEARCH) },
                    )
                }
            }
        }
    }

    // Destructive message actions always require confirmation.
    messageToDelete?.let { id ->
        NftyConfirmationSheet(
            title = "Delete notification?",
            message = "This removes the message from local history on this device. Saved messages are also removed.",
            confirmLabel = "Delete",
            isDestructive = true,
            onConfirm = {
                mainViewModel.deleteMessage(id)
                messageToDelete = null
            },
            onDismiss = { messageToDelete = null },
        )
    }

    clearKind?.let { kind ->
        NftyConfirmationSheet(
            title = if (kind == "history") "Clear local history?" else "Remove all saved items?",
            message = if (kind == "history") {
                "Unsaved notifications are removed for this profile. Saved items are preserved."
            } else {
                "Every saved flag is cleared for this profile. Messages themselves stay in history."
            },
            confirmLabel = if (kind == "history") "Clear History" else "Clear Saved",
            isDestructive = true,
            onConfirm = {
                if (kind == "history") mainViewModel.clearHistory() else mainViewModel.clearSaved()
                clearKind = null
            },
            onDismiss = { clearKind = null },
        )
    }

    profileToSwitch?.let { (id, name) ->
        NftyConfirmationSheet(
            title = "Switch to $name?",
            message = "The current profile's messages, topics, outbox and credentials stay on this device. The active connection will restart for the selected profile.",
            confirmLabel = "Switch Profile",
            onConfirm = {
                mainViewModel.switchProfile(id)
                profileToSwitch = null
            },
            onDismiss = { profileToSwitch = null },
        )
    }

    if (publishState.pendingAmbiguousRetry != null) {
        NftyConfirmationSheet(
            title = "Retry ambiguous publish?",
            message = "The previous attempt may already have been accepted. Retrying can publish the same message twice. Only continue if a duplicate is acceptable.",
            confirmLabel = "Retry Anyway",
            isDestructive = true,
            onConfirm = { publishViewModel.confirmAmbiguousRetry() },
            onDismiss = { publishViewModel.dismissAmbiguousRetry() },
        )
    }

    uiState.profileChangePreview?.let { preview ->
        ProfileChangePreviewDialog(
            preview = preview,
            onDismiss = { mainViewModel.dismissProfileChangePreview() },
            onCreateNew = { mainViewModel.confirmProfileChange(createNew = true) },
            onKeepAndMix = { mainViewModel.confirmProfileChange(createNew = false) },
        )
    }

    // External URL Confirmation Dialog (Field manual security requirement)
    uiState.urlToConfirm?.let { url ->
        NftyConfirmationSheet(
            title = "Open External Link",
            message = "This action will open an external link in your web browser. Ensure you trust the server source before proceeding.",
            actionUrl = url,
            confirmLabel = "Open Browser",
            onConfirm = {
                mainViewModel.dismissUrlPrompt()
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(browserIntent)
                } catch (_: Exception) {
                    // The platform reports a missing browser; nothing else to do.
                }
            },
            onDismiss = { mainViewModel.dismissUrlPrompt() },
        )
    }
}
