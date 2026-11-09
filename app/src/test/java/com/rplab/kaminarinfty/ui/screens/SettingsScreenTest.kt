package com.rplab.kaminarinfty.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.rplab.kaminarinfty.ThemeSetting
import com.rplab.kaminarinfty.delivery.DeliveryStatus
import com.rplab.kaminarinfty.model.AuthMethod
import com.rplab.kaminarinfty.model.AuthState
import com.rplab.kaminarinfty.model.DeliveryMode
import com.rplab.kaminarinfty.model.ServerConfig
import com.rplab.kaminarinfty.model.ServerProfile
import com.rplab.kaminarinfty.ui.theme.MyApplicationTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class SettingsScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var selectedMode: DeliveryMode? = null
    private var switchedProfile: String? = null

    private fun setScreen(
        activeProfileId: String = "primary",
        deliveryMode: DeliveryMode = DeliveryMode.BATTERY_SAVER,
    ) {
        composeRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                SettingsScreen(
                    serverConfig = ServerConfig(
                        serverUrl = "https://ntfy.test",
                        authMethod = AuthMethod.GUEST,
                        tokenOrPassword = "super-secret-value",
                    ),
                    authState = AuthState.GUEST,
                    profiles = listOf(
                        ServerProfile(
                            id = "primary",
                            displayName = "home",
                            baseUrl = "https://ntfy.test",
                            authMethod = AuthMethod.GUEST,
                            hasStoredSecret = false,
                            createdAt = 0L,
                            updatedAt = 0L,
                        ),
                        ServerProfile(
                            id = "secondary",
                            displayName = "away",
                            baseUrl = "https://away.test",
                            authMethod = AuthMethod.GUEST,
                            hasStoredSecret = false,
                            createdAt = 0L,
                            updatedAt = 0L,
                        ),
                    ),
                    activeProfileId = activeProfileId,
                    reconnectAttempt = 0,
                    lastCursor = null,
                    themeSetting = ThemeSetting.DAY,
                    isCompactDensity = false,
                    isSampleData = false,
                    sampleDataEnabled = false,
                    historyCount = 0,
                    malformedLines = 0,
                    profileEdit = null,
                    profileEditError = null,
                    onThemeChange = {},
                    onToggleDensity = {},
                    onTestConnection = {},
                    onSelectProfile = { switchedProfile = it },
                    onEditProfile = {},
                    onAddProfile = {},
                    onCancelProfileEdit = {},
                    onSaveProfileEdit = { _, _, _, _, _ -> },
                    onCatchUp = {},
                    onClearHistory = {},
                    onClearSaved = {},
                    onToggleSampleData = {},
                    deliveryMode = deliveryMode,
                    deliveryStatus = DeliveryStatus(),
                    notificationsEnabled = true,
                    onSelectDeliveryMode = { selectedMode = it },
                    onOpenNotificationSettings = {},
                    onSearchClick = {},
                )
            }
        }
    }

    @Test
    fun `active profile is marked and another profile can be selected`() {
        setScreen()

        composeRule.onNodeWithText("away").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("use_profile_secondary").performScrollTo().performClick()

        assertEquals("secondary", switchedProfile)
    }

    @Test
    fun `delivery mode selection reports the chosen mode`() {
        setScreen(deliveryMode = DeliveryMode.BATTERY_SAVER)

        composeRule.onNodeWithTag("delivery_mode_INSTANT").performScrollTo().performClick()

        assertEquals(DeliveryMode.INSTANT, selectedMode)
    }

    @Test
    fun `settings never render stored credentials`() {
        setScreen()

        composeRule.onNodeWithText("super-secret-value", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("encrypted credential stored", substring = true)
            .assertDoesNotExist()
    }
}
