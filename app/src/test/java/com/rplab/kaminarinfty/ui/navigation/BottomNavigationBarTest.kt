package com.rplab.kaminarinfty.ui.navigation

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.rplab.kaminarinfty.AppDestination
import com.rplab.kaminarinfty.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w360dp-h740dp")
class BottomNavigationBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `all six primary destinations are reachable`() {
        var selected: AppDestination? = null
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                NftyBottomNavigationBar(
                    currentTab = AppDestination.INBOX,
                    onTabSelected = { selected = it },
                )
            }
        }

        listOf(
            "nav_inbox",
            "nav_topics",
            "nav_compose",
            "nav_outbox",
            "nav_activity",
            "nav_settings",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }

        composeRule.onNodeWithTag("nav_compose").performClick()
        assertEquals(AppDestination.COMPOSE, selected)
    }
}
