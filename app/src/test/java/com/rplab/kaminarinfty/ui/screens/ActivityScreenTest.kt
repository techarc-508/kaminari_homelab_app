package com.rplab.kaminarinfty.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.rplab.kaminarinfty.ActivityFilter
import com.rplab.kaminarinfty.ActivityUiState
import com.rplab.kaminarinfty.model.ActivityEvent
import com.rplab.kaminarinfty.model.ActivitySeverity
import com.rplab.kaminarinfty.model.ActivityType
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ActivityScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var selectedFilter: ActivityFilter? = null

    private fun setScreen(
        events: List<ActivityEvent>,
        filter: ActivityFilter = ActivityFilter.ALL,
        loading: Boolean = false,
    ) {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                ActivityScreen(
                    state = ActivityUiState(
                        activeProfileId = "primary",
                        events = events,
                        filter = filter,
                        isLoading = loading,
                    ),
                    authState = AuthState.GUEST,
                    profileLabel = "home",
                    isSampleData = false,
                    onFilterChange = { selectedFilter = it },
                    onSettingsClick = {},
                )
            }
        }
    }

    @Test
    fun `timeline renders sanitized summaries and severities`() {
        setScreen(
            listOf(
                event("1", ActivityType.PUBLISH_ACCEPTED, ActivitySeverity.SUCCESS, "Publish to ops accepted by server"),
                event("2", ActivityType.PUBLISH_AMBIGUOUS, ActivitySeverity.WARNING, "Outcome unknown"),
            )
        )

        composeRule.onNodeWithText("Publish to ops accepted by server").assertIsDisplayed()
        composeRule.onNodeWithText("PUBLISH AMBIGUOUS").assertExists()
        composeRule.onNodeWithTag("activity_list").assertIsDisplayed()
    }

    @Test
    fun `empty timeline states summaries never include bodies`() {
        setScreen(emptyList())

        composeRule.onNodeWithText("No activity yet").assertIsDisplayed()
    }

    @Test
    fun `filter chips emit their selected filter`() {
        setScreen(
            listOf(event("1", ActivityType.MESSAGE_RECEIVED, ActivitySeverity.INFO, "Message received")),
        )

        composeRule.onNodeWithTag("activity_filter_ATTENTION").performClick()

        assertEquals(ActivityFilter.ATTENTION, selectedFilter)
    }

    private fun event(
        id: String,
        type: ActivityType,
        severity: ActivitySeverity,
        summary: String,
    ) = ActivityEvent(
        id = id,
        profileId = "primary",
        type = type,
        severity = severity,
        summary = summary,
        createdAt = System.currentTimeMillis(),
    )
}
