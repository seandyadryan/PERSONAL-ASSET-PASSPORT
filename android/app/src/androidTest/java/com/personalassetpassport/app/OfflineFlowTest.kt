package com.personalassetpassport.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Run on a clean EN-language emulator/installation; system pickers and Google need manual checks.
 */
@RunWith(AndroidJUnit4::class)
class OfflineFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun onboardCreateAndOpenPassport() {
        compose.onNodeWithText("Continue on this device").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Add asset").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Add asset").performClick()
        compose.onNodeWithText("Asset name").performTextInput("Device acceptance camera")
        compose.onNodeWithText("Save passport").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText("Edit asset").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("Edit asset").assertExists()
    }
}
