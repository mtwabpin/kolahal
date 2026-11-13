package com.mtwabpin.kolahal.ui

import android.app.Application
import android.content.Context
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * With three-button navigation, as MainActivity draws (edge to edge, the bar see-through): what a
 * page keeps at its bottom sits above the buttons, and a list scrolling under them doesn't show
 * between them. The emulator is switched to three buttons for these, and back after.
 */
@RunWith(AndroidJUnit4::class)
class NavigationButtonsTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private lateinit var vm: TerminalViewModel

    /** Where home notes that its first run's tour was shown: set, so no tour takes a page's place. */
    private val launcher = app.getSharedPreferences("launcher", Context.MODE_PRIVATE)
    private val toured = launcher.contains("intro_shown")

    @After fun tearDown() {
        if (!toured) launcher.edit().remove("intro_shown").commit()
    }

    companion object {
        private const val THREE_BUTTONS = "com.android.internal.systemui.navbar.threebutton"

        /** The navigation overlay and mode the emulator had, put back after. */
        private var overlay: String? = null
        private var mode = ""

        private fun navigationMode() = shell("settings get secure navigation_mode").trim()

        /** Switches to [to] and waits for it: activities started while it applies are started again, under a test. */
        private fun switchTo(to: String, modeAfter: String) {
            shell("cmd overlay enable-exclusive --category $to")
            val until = System.currentTimeMillis() + 10_000
            while (navigationMode() != modeAfter && System.currentTimeMillis() < until) Thread.sleep(200)
            Thread.sleep(2_000)
        }

        @BeforeClass @JvmStatic fun threeButtons() {
            mode = navigationMode()
            overlay = shell("cmd overlay list").lines().firstOrNull { "[x]" in it && ".navbar." in it }?.substringAfter("]")?.trim()
            if (overlay != THREE_BUTTONS) switchTo(THREE_BUTTONS, "0")
        }

        @AfterClass @JvmStatic fun putBack() {
            overlay?.takeIf { it != THREE_BUTTONS }?.let { switchTo(it, mode) }
        }
    }

    /** [screen] filling the window as MainActivity shows it, once the window has the buttons' inset. */
    private fun show(screen: Screen, content: @Composable () -> Unit) {
        launcher.edit().putBoolean("intro_shown", true).commit()
        compose.runOnUiThread {
            compose.activity.enableEdgeToEdge(SystemBarStyle.dark(Color.TRANSPARENT), SystemBarStyle.dark(Color.TRANSPARENT))
            vm = vms.terminal(app)
            vm.openScreen(screen)
        }
        compose.setContent { KolahalTheme { content() } }
        val buttons = runCatching { compose.waitUntil(10_000) { buttonsHeight() > 0.dp } }.isSuccess
        assumeTrue("no three-button navigation on this device", buttons)
    }

    /** How tall the navigation buttons are: 0 with gesture navigation, whose handle takes no taps. */
    private fun buttonsHeight(): Dp = compose.runOnIdle {
        val insets = ViewCompat.getRootWindowInsets(compose.activity.window.decorView) ?: return@runOnIdle 0.dp
        (insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom / compose.activity.resources.displayMetrics.density).dp
    }

    /** Where the buttons start, from the top of the window. */
    private fun buttonsTop(): Dp = compose.onRoot().getUnclippedBoundsInRoot().bottom - buttonsHeight()

    private fun assertAboveTheButtons(what: String, bottom: Dp) =
        assertTrue("$what ends at $bottom, the buttons start at ${buttonsTop()}", bottom <= buttonsTop())

    @Test fun setupsButtonIsAboveTheButtons() {
        show(Screen.SETUP) { SetupScreen(vm) }
        val button = compose.onNode(hasText("Continue to terminal") or hasText("Done")).getUnclippedBoundsInRoot()
        assertAboveTheButtons("Continue to terminal", button.bottom)
    }

    @Test fun theToursDotsAndNextAreAboveTheButtons() {
        show(Screen.ONBOARDING) { OnboardingScreen(vm) }
        assertAboveTheButtons("Next", compose.onNodeWithTag("tour-next").getUnclippedBoundsInRoot().bottom)
        assertAboveTheButtons("the page dots", compose.onNodeWithContentDescription("Page 1 of", substring = true).getUnclippedBoundsInRoot().bottom)
    }

    /** Settings' rows run on under the buttons, and only the page's colour shows between them. */
    @Test fun aListShowsNothingBetweenTheButtons() {
        show(Screen.SETTINGS) { SettingsOverHome(vm) }
        val density = compose.activity.resources.displayMetrics.density
        val top = (buttonsTop().value * density).toInt() + 1
        val under = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().count { it.boundsInRoot.bottom > top }
        assertTrue("no row reaches the buttons", under > 0)
        val image = compose.onRoot().captureToImage().toPixelMap()
        val page = Term.palette.bg.toArgb()
        val other = (top until image.height).sumOf { y -> (0 until image.width).count { x -> image[x, y].toArgb() != page } }
        assertEquals("pixels not the page's colour between the buttons", 0, other)
    }
}
