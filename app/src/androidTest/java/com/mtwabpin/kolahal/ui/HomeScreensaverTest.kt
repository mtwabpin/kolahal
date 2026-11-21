package com.mtwabpin.kolahal.ui

import android.app.Application
import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.ScreenSaver
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The idle screensaver (`screen.saver`): it opens over home after its quiet minutes, and the first
 * touch or key only closes it. A minute here is 300 ms. The phone always allows it: device tests run
 * with animations off, which would keep it closed (matrix then shows one still frame).
 */
@RunWith(AndroidJUnit4::class)
class HomeScreensaverTest {
    @get:Rule
    val vms = TestViewModels()

    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val configReset = ConfigReset()

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)

    private val fast = ScreenSaverTiming(
        minuteMs = 300L,
        phone = { ScreenSaver.Phone(screenOn = true, batterySaver = false, animationsOff = false) },
    )

    private val saverNode = hasContentDescription("Screensaver: Matrix rain")

    /** Home as MainActivity shows it: the terminal, with the screens over it. */
    private fun show(saver: String = "matrix"): TerminalViewModel {
        config.set(Keys.SCREEN_SAVER, saver)
        config.set(Keys.SCREEN_SAVER_AFTER, "1")
        val vm = vms.terminal(app)
        compose.setContent {
            KolahalTheme {
                CompositionLocalProvider(LocalScreenSaverTiming provides fast) {
                    Box(Modifier.fillMaxSize()) {
                        TerminalScreen(vm)
                        HomeScreenOverlay(vm)
                    }
                }
            }
        }
        return vm
    }

    /** Lets [ms] of real time go by, with frames running. */
    private fun idleFor(ms: Long) {
        val until = SystemClock.uptimeMillis() + ms
        compose.waitUntil(ms + 5_000) { SystemClock.uptimeMillis() >= until }
    }

    @Test
    fun itOpensAfterTheQuietAndATapClosesIt() {
        val vm = show()
        compose.waitUntil(5_000) { vm.ttyScreen.value == "matrix" }
        assertTrue(vm.screenSaverOn.value)
        compose.onNode(saverNode).assertExists()
        compose.onNode(saverNode).performTouchInput { click(center) }
        compose.waitUntil(5_000) { vm.ttyScreen.value == null }
        assertFalse(vm.screenSaverOn.value)
        // The tap only closed it: it reached nothing under it, and nothing was typed.
        assertEquals("", vm.input.text)
    }

    /** A key closes it too, and isn't typed into the prompt. */
    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aKeyClosesItAndTypesNothing() {
        val vm = show()
        compose.waitUntil(5_000) { vm.ttyScreen.value == "matrix" }
        compose.waitUntil(5_000) { compose.onAllNodes(saverNode and isFocused()).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(saverNode).performKeyInput { pressKey(Key.A) }
        compose.waitUntil(5_000) { vm.ttyScreen.value == null }
        assertEquals("", vm.input.text)
    }

    /** Not while something is typed at the prompt; once it is cleared, the quiet starts again. */
    @Test
    fun notWhileTyping() {
        val vm = show()
        compose.runOnIdle { vm.insert("echo") }
        idleFor(1_500)
        assertNull(vm.ttyScreen.value)
        compose.runOnIdle { vm.onInputChange(TextFieldValue("")) }
        compose.waitUntil(5_000) { vm.ttyScreen.value == "matrix" }
    }

    @Test
    fun offNeverOpens() {
        val vm = show(saver = "off")
        idleFor(1_500)
        assertNull(vm.ttyScreen.value)
        assertFalse(vm.screenSaverOn.value)
    }
}
