package com.personalassetpassport.app

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.drawToBitmap
import com.personalassetpassport.app.ui.PassportTheme
import com.personalassetpassport.app.ui.WelcomeContent
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en-rUS-w430dp-h960dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WelcomeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun localModeAndGoogleActionsRemainSeparate() {
        var local = 0
        var google = 0
        compose.setContent {
            PassportTheme { WelcomeContent(onLocal = { local++ }, onGoogle = { google++ }) }
        }
        val output = File("build/reports/ui/welcome.png").apply { parentFile?.mkdirs() }
        compose.runOnIdle {
            val bitmap = compose.activity.window.decorView.drawToBitmap()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        compose.onNodeWithText("Continue on this device").performScrollTo().performClick()
        assertEquals(1, local)
        assertEquals(0, google)
        compose.onNodeWithText("Continue with Google").performScrollTo().performClick()
        assertEquals(1, google)
    }

    @Test
    fun busyStateDisablesRepeatedSignIn() {
        compose.setContent {
            PassportTheme { WelcomeContent(busy = true, onLocal = {}, onGoogle = {}) }
        }
        compose.onNodeWithText("Continue with Google").assertIsNotEnabled()
        compose.onNodeWithText("Continue on this device").assertIsNotEnabled()
    }
}
