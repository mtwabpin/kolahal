package com.mtwabpin.kolahal.ui.home

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
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
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

    /** The view home is drawn in, for putting the keyboard away ([keyboardDown]). */
    private var view: android.view.View? = null

    /**
     * Home as MainActivity shows it: the terminal, with the screens over it, the keyboard down unless
     * [keyboardUp]. In the tty style by default: matrix draws the grid, so only that style runs it.
     */
    private fun show(saver: String = "matrix", keyboardUp: Boolean = false, style: String = "tty"): TerminalViewModel {
        config.set(Keys.HOME_STYLE, style)
        config.set(Keys.SCREEN_SAVER, saver)
        config.set(Keys.SCREEN_SAVER_AFTER, "1")
        val vm = vms.terminal(app)
        compose.setContent {
            view = androidx.compose.ui.platform.LocalView.current
            KolahalTheme {
                CompositionLocalProvider(LocalScreenSaverTiming provides fast) {
                    Box(Modifier.fillMaxSize()) {
                        TerminalScreen(vm)
                        HomeScreenOverlay(vm)
                    }
                }
            }
        }
        if (!keyboardUp) keyboardDown()
        return vm
    }

    /**
     * Puts the keyboard away, as on an idle home: the keyboard up counts as typing, and the test's
     * home focuses the prompt, so the screensaver would never come.
     */
    private fun keyboardDown() {
        compose.waitForIdle()
        val v = view ?: return
        val ime = androidx.core.view.WindowInsetsCompat.Type.ime()
        compose.runOnUiThread {
            val window = (v.context as? android.app.Activity)?.window ?: return@runOnUiThread
            androidx.core.view.WindowCompat.getInsetsController(window, v).hide(ime)
        }
        compose.waitUntil(5_000) { androidx.core.view.ViewCompat.getRootWindowInsets(v)?.isVisible(ime) != true }
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

    /**
     * With keyboard.on-home the keyboard stays up on an idle home, so it isn't typing: the
     * screensaver comes anyway and puts the keyboard away.
     */
    @Test
    fun keyboardOnHomeStillOpensIt() {
        config.set(Keys.FOCUS_ON_HOME, "on")
        val vm = show(keyboardUp = true)
        compose.waitUntil(5_000) { vm.ttyScreen.value == "matrix" }
        val v = view ?: return
        val ime = androidx.core.view.WindowInsetsCompat.Type.ime()
        compose.waitUntil(5_000) { androidx.core.view.ViewCompat.getRootWindowInsets(v)?.isVisible(ime) != true }
    }

    /** In the gui style a screensaver that draws the grid is off; the stored value stays for the tty style. */
    @Test
    fun matrixWaitsForTheTtyStyle() {
        val vm = show(style = "gui")
        idleFor(1_500)
        assertNull(vm.ttyScreen.value)
        assertEquals("matrix", config[Keys.SCREEN_SAVER])
    }

    @Test
    fun offNeverOpens() {
        val vm = show(saver = "off")
        idleFor(1_500)
        assertNull(vm.ttyScreen.value)
        assertFalse(vm.screenSaverOn.value)
    }
}
