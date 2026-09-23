package com.personalassetpassport.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.personalassetpassport.app.ui.PassportApp
import com.personalassetpassport.app.ui.PassportViewModel
import org.junit.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w430dp-h960dp")
class PassportFlowTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()

    @After
    fun cleanup() {
        store.clear()
    }

    @Test
    fun localOnboardingFormValidationAndCreateUseRealRoom() {
        val app = ApplicationProvider.getApplicationContext<PassportApplication>()
        val vm =
            ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(app))[
                PassportViewModel::class.java]
        compose.setContent { PassportApp(vm) }
        compose.onNodeWithText("Continue on this device").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            vm.assets.value != null || vm.storageFailed.value
        }
        Assert.assertFalse("Room must initialize successfully", vm.storageFailed.value)
        compose.onNodeWithTag("add_asset_fab").performClick()
        compose.onNodeWithText("Save passport").performScrollTo().performClick()
        compose.onNodeWithText("Asset name").performScrollTo()
        compose.onNodeWithText("This field is required.").assertExists()
        compose.onNodeWithText("Asset name").performTextInput("My camera")
        compose.onNodeWithText("Save passport").performScrollTo().performClick()
        compose.waitUntil(10_000) {
            compose.waitForIdle()
            vm.assets.value?.any { it.name == "My camera" } == true
        }
        compose.onNodeWithText("Edit asset").assertExists()
    }
}
