package com.rplab.kaminarinfty.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import com.rplab.kaminarinfty.PublishForm
import com.rplab.kaminarinfty.PublishOutcome
import com.rplab.kaminarinfty.PublishUiState
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class PublishScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setScreen(
        state: PublishUiState = PublishUiState(activeProfileId = "primary"),
    ) {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                PublishScreen(
                    state = state,
                    authState = AuthState.GUEST,
                    profileLabel = "home",
                    isSampleData = false,
                    onTopicChange = {},
                    onTitleChange = {},
                    onBodyChange = {},
                    onPriorityChange = {},
                    onTagsChange = {},
                    onClickUrlChange = {},
                    onAttachmentUrlChange = {},
                    onDelayChange = {},
                    onToggleAdvanced = {},
                    onSend = {},
                    onRetryLast = {},
                    onDismissOutcome = {},
                    onClearValidation = {},
                    onSettingsClick = {},
                )
            }
        }
    }

    @Test
    fun `compose form exposes topic and acceptance semantics`() {
        setScreen()

        composeRule.onNodeWithTag("compose_topic_input").assertIsDisplayed()
        composeRule.onNodeWithTag("compose_acceptance_note").performScrollTo()
        composeRule.onNodeWithTag("compose_acceptance_note").assertIsDisplayed()
    }

    @Test
    fun `validation error is visible before enqueue`() {
        setScreen(
            PublishUiState(
                activeProfileId = "primary",
                validationError = "Message must not be blank",
            )
        )

        composeRule.onNodeWithTag("compose_validation_error").performScrollTo()
        composeRule.onNodeWithText("Message must not be blank").assertIsDisplayed()
    }

    @Test
    fun `ambiguous result explains duplicate risk and offers confirmed retry`() {
        setScreen(
            PublishUiState(
                activeProfileId = "primary",
                form = PublishForm(topic = "ops", body = "still here"),
                outcome = PublishOutcome.Ambiguous("Timeout after sending", "client-1"),
            )
        )

        composeRule.onNodeWithTag("compose_outcome_card").performScrollTo()
        composeRule.onNodeWithText("OUTCOME UNKNOWN").assertIsDisplayed()
        composeRule.onNodeWithText("Retry (may duplicate)…").assertExists()
        composeRule.onNodeWithText("still here", substring = true).assertExists()
    }

    @Test
    fun `accepted result states it is not a delivery receipt`() {
        setScreen(
            PublishUiState(
                activeProfileId = "primary",
                outcome = PublishOutcome.Accepted("srv-9", "client-1"),
            )
        )

        composeRule.onNodeWithTag("compose_outcome_card").performScrollTo()
        composeRule.onNodeWithText("ACCEPTED BY SERVER").assertIsDisplayed()
        composeRule.onNodeWithText("No recipient delivery receipt exists.", substring = true)
            .assertExists()
    }

    @Test
    fun `advanced fields stay behind the optional section`() {
        setScreen(
            PublishUiState(
                activeProfileId = "primary",
                form = PublishForm(showAdvanced = true, topic = "ops", body = "hello"),
            )
        )

        composeRule.onNodeWithTag("compose_click_url_input").performScrollTo()
        composeRule.onNodeWithTag("compose_click_url_input").assertIsDisplayed()
        composeRule.onNodeWithTag("compose_attachment_url_input").assertExists()
    }
}
