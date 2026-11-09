package com.rplab.kaminarinfty.ui.components

import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.rplab.kaminarinfty.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Gate finding G-08 / audit finding A-06: the clock must tick at second
 * boundaries while remaining idle between them. The previous test asserted
 * `true` without rendering the composable, so it could not catch a regression
 * to the per-frame `withFrameNanos` loop.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class NiftyTopBarClockTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var now = 1_700_000_000_000L

    private fun expectedTime(millis: Long): String =
        SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(millis))

    @Test
    fun `clock renders the injected time and advances at the next second`() {
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                NftyTopBar(nowMillis = { now })
            }
        }
        composeRule.mainClock.advanceTimeByFrame()

        composeRule.onNodeWithTag("top_bar_clock").assertTextEquals(expectedTime(now))

        // Within the same second the text must not change, and the clock must
        // not demand a frame for every millisecond.
        composeRule.mainClock.advanceTimeBy(400)
        composeRule.onNodeWithTag("top_bar_clock").assertTextEquals(expectedTime(now))

        now += 1_000
        composeRule.mainClock.advanceTimeBy(700)
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithTag("top_bar_clock").assertTextEquals(expectedTime(now))
    }

    @Test
    fun `top bar reaches idle instead of requesting a frame every frame`() {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                NftyTopBar(nowMillis = { now })
            }
        }

        // Under the old `withFrameNanos` loop this never returned and eventually
        // threw AppNotIdleException after 100k frame attempts.
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("top_bar_clock").assertExists()
    }
}
