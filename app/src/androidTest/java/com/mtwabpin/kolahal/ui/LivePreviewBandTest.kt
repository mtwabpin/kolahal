package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The band that says it's a preview, on the home screen. */
@RunWith(AndroidJUnit4::class)
class LivePreviewBandTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)
    private lateinit var vm: TerminalViewModel

    @Before fun show() {
        compose.runOnUiThread { vm = vms.terminal(app) }
        compose.setContent {
            KolahalTheme {
                Box(Modifier.size(360.dp, 700.dp).consumeWindowInsets(WindowInsets.safeDrawing)) {
                    TerminalScreen(vm, onPickFolder = {})
                }
            }
        }
        compose.waitForIdle()
    }

    @After fun tearDown() = Keys.ALL.forEach(config::reset)

    private fun type(line: String) {
        compose.runOnUiThread { vm.onInputChange(TextFieldValue(line)) }
        compose.waitForIdle()
    }

    @Test fun itSaysWhatItShowsRightAboveThePrompt() {
        compose.onNodeWithTag("live-preview").assertDoesNotExist()
        type("theme dra")
        compose.onNodeWithTag("live-preview").assertDoesNotExist()
        type("theme dracula")
        compose.onNodeWithText("Preview: dracula  ·  Enter to apply").assertIsDisplayed()
        val band = compose.onNodeWithTag("live-preview").getUnclippedBoundsInRoot()
        val prompt = compose.onNodeWithTag("prompt").getUnclippedBoundsInRoot()
        assertTrue("band ${band.bottom}, prompt ${prompt.top}", band.bottom <= prompt.top && prompt.top - band.bottom < 24.dp)
        type("font fira-code")
        compose.onNodeWithText("Preview: font fira-code  ·  Enter to apply").assertIsDisplayed()
        type("config set font.size 20")
        compose.onNodeWithText("Preview: font size 20  ·  Enter to apply").assertIsDisplayed()
        type("")
        compose.onNodeWithTag("live-preview").assertDoesNotExist()
    }
}
