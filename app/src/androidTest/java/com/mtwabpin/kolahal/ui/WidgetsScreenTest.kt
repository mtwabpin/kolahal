package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings → Widgets → Arrange widgets. */
@RunWith(AndroidJUnit4::class)
class WidgetsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val config = Config.get(app)

    @After fun tearDown() = config.reset(Keys.WIDGETS_PAGE)

    /** With the widgets page off, the screen says so, rather than "swipe sideways", and turns it on in a tap. */
    @Test fun withThePageOffItSaysSoAndTurnsItOn() {
        config.set(Keys.WIDGETS_PAGE, "off")
        lateinit var vm: TerminalViewModel
        instrumentation.runOnMainSync { vm = TerminalViewModel(app, SavedStateHandle()) }
        compose.setContent { WidgetsScreen(vm, onReAdd = { _, _ -> }) }
        compose.onNodeWithText("The widgets page is off").assertExists()
        compose.onNodeWithText("Turn on the widgets page").performClick()
        compose.waitForIdle()
        assertEquals("left", config[Keys.WIDGETS_PAGE])
        compose.onNodeWithText("Turn on the widgets page").assertDoesNotExist()
        compose.onNodeWithText("The widgets page is off").assertDoesNotExist()
    }
}
