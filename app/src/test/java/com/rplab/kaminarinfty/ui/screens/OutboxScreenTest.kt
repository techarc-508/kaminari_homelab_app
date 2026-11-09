package com.rplab.kaminarinfty.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.rplab.kaminarinfty.PublishUiState
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.OutboxEntry
import com.rplab.kaminarinfty.model.OutboxState
import com.rplab.kaminarinfty.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class OutboxScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(entries: List<OutboxEntry>, loading: Boolean = false) {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                OutboxScreen(
                    state = PublishUiState(
                        activeProfileId = "primary",
                        outbox = entries,
                        isOutboxLoading = loading,
                    ),
                    authState = AuthState.GUEST,
                    profileLabel = "home",
                    isSampleData = false,
                    onRetry = {},
                    onCancel = {},
                    onClearNotice = {},
                    onSettingsClick = {},
                )
            }
        }
    }

    @Test
    fun `empty outbox explains that nothing sends automatically`() {
        setScreen(emptyList())
        composeRule.onNodeWithText("Outbox is empty").assertIsDisplayed()
        composeRule.onNodeWithText("Nothing is sent automatically.", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun `accepted and failed entries keep their explicit status labels`() {
        setScreen(
            listOf(
                entry("a", OutboxState.ACCEPTED, "accepted body"),
                entry("b", OutboxState.FAILED, "failed body", retryable = false, error = "Bad topic"),
            )
        )

        composeRule.onNodeWithText("ACCEPTED").assertExists()
        composeRule.onNodeWithText("FAILED").assertExists()
        composeRule.onNodeWithTag("outbox_entry_a").assertIsDisplayed()
        composeRule.onNodeWithTag("outbox_entry_b").assertIsDisplayed()
    }

    @Test
    fun `ambiguous entry offers confirmed retry instead of a plain retry`() {
        setScreen(
            listOf(
                entry(
                    "amb",
                    OutboxState.AMBIGUOUS,
                    "uncertain body",
                    retryable = true,
                    error = "Timeout",
                )
            )
        )

        composeRule.onNodeWithText("AMBIGUOUS").assertExists()
        composeRule.onNodeWithText("Retry (may duplicate)…").assertIsDisplayed()
        composeRule.onNodeWithTag("outbox_retry_amb").assertExists()
    }

    @Test
    fun `queued entry can be sent or removed`() {
        setScreen(listOf(entry("q", OutboxState.QUEUED, "queued body")))

        composeRule.onNodeWithText("Send now").assertIsDisplayed()
        composeRule.onNodeWithText("Remove").assertIsDisplayed()
    }

    private fun entry(
        id: String,
        state: OutboxState,
        body: String,
        retryable: Boolean = false,
        error: String? = null,
    ) = OutboxEntry(
        clientId = id,
        profileId = "primary",
        topic = "ops",
        title = null,
        body = body,
        priority = 3,
        tags = emptyList(),
        clickUrl = null,
        attachmentUrl = null,
        delay = null,
        state = state,
        attemptCount = 1,
        retryable = retryable,
        lastError = error,
        serverMessageId = if (state == OutboxState.ACCEPTED) "srv-$id" else null,
        createdAt = 0L,
        updatedAt = 0L,
        acceptedAt = null,
    )
}
