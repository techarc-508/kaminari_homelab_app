package com.rplab.kaminarinfty.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rplab.kaminarinfty.InboxFilter
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.NftyMessage
import com.rplab.kaminarinfty.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class InboxScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setInbox(
        messages: List<NftyMessage> = emptyList(),
        authState: AuthState = AuthState.NOT_CONFIGURED,
        customErrorMessage: String? = null,
        activeFilter: InboxFilter = InboxFilter.ALL,
        searchQuery: String = "",
    ) {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                InboxScreen(
                    messages = messages,
                    serverUrl = "https://ntfy.test",
                    authState = authState,
                    activeFilter = activeFilter,
                    selectedTopicFilter = null,
                    isSampleData = false,
                    customErrorMessage = customErrorMessage,
                    searchQuery = searchQuery,
                    onFilterChange = {},
                    onClearTopicFilter = {},
                    onMessageClick = {},
                    onToggleSave = {},
                    onMarkAllRead = {},
                    onSearchClick = {},
                    onToggleThemeClick = {},
                    onSettingsClick = {},
                )
            }
        }
    }

    @Test
    fun `empty inbox explains what to do`() {
        setInbox()
        composeRule.onNodeWithText("No notifications yet").assertIsDisplayed()
    }

    @Test
    fun `authentication failure shows the error state with the concrete message`() {
        setInbox(
            authState = AuthState.AUTHENTICATION_FAILED,
            customErrorMessage = "HTTP 401: invalid credentials",
        )
        composeRule.onNodeWithText("HTTP 401: invalid credentials").assertIsDisplayed()
    }

    @Test
    fun `filter chips are interactive`() {
        setInbox()
        composeRule.onNodeWithText("Unread").performClick()
        composeRule.onNodeWithText("Unread").assertIsDisplayed()
    }

    @Test
    fun `guest empty state mentions subscribed topics`() {
        setInbox(authState = AuthState.GUEST)
        composeRule.onNodeWithText(
            "Connected as guest. Messages published to subscribed topics will appear here."
        ).assertIsDisplayed()
    }

    @Test
    fun `saved filter shows only saved messages`() {
        setInbox(
            messages = listOf(
                message(id = "saved", title = "Saved item", body = "keep me", saved = true),
                message(id = "plain", title = "Plain item", body = "not saved", saved = false),
            ),
            activeFilter = InboxFilter.SAVED,
        )

        composeRule.onNodeWithText("Saved item").assertIsDisplayed()
        composeRule.onNodeWithText("Plain item").assertDoesNotExist()
    }

    @Test
    fun `search filters by body without touching persisted state`() {
        setInbox(
            messages = listOf(
                message(id = "needle", title = "Needle", body = "disk full on node one"),
                message(id = "other", title = "Other", body = "routine backup"),
            ),
            searchQuery = "disk full",
        )

        composeRule.onNodeWithText("Needle").assertIsDisplayed()
        composeRule.onNodeWithText("Other").assertDoesNotExist()
    }

    private fun message(
        id: String,
        title: String,
        body: String,
        saved: Boolean = false,
    ) = NftyMessage(
        id = id,
        time = System.currentTimeMillis(),
        topic = "ops",
        title = title,
        message = body,
        isSaved = saved,
    )
}
