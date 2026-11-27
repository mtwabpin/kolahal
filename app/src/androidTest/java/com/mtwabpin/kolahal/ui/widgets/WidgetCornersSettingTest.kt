package com.mtwabpin.kolahal.ui.widgets

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.settings.SettingItem
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.widgets.TestWidgets
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSource
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Widget corners (`widgets.corners`) in Settings → Widgets: widgets page 1 under the choice
 * ([WidgetsPagePreview]), and a pick changes its cards at once. With no widget, a line says so.
 */
@RunWith(AndroidJUnit4::class)
class WidgetCornersSettingTest {
    @get:Rule
    val vms = com.mtwabpin.kolahal.TestViewModels()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)
    private val t = TestWidgets()

    @After
    fun tearDown() {
        config.reset(Keys.WIDGET_CORNERS)
        t.close()
    }

    private fun show(): TerminalViewModel {
        lateinit var vm: TerminalViewModel
        compose.runOnUiThread { vm = vms.terminal(app) }
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    val version = vm.config.version.collectAsState()
                    SettingItem(vm, Keys.WIDGET_CORNERS, version, 0, 1)
                }
            }
        }
        return vm
    }

    private fun preview() = compose.onNodeWithTag(PAGE_PREVIEW_TAG, useUnmergedTree = true).captureToImage().toPixelMap()

    @Test
    fun aPickChangesTheCardsInThePreview() {
        assumeTrue(
            "widgets page 1 had widgets before the test",
            t.host.widgets.value.none { it.location == WidgetLocation.PANEL }
        )
        show()
        compose.onNodeWithText(NO_WIDGETS_YET, useUnmergedTree = true).assertExists()

        assumeTrue("the battery card was placed before the test", t.host.placeBuiltin(WidgetSource.BATTERY, WidgetSize.S4X1))
        compose.waitFor("the preview has no card") {
            compose.onAllNodes(hasTestTag(PAGE_PREVIEW_TAG), useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() &&
                compose.onAllNodes(androidx.compose.ui.test.hasText(NO_WIDGETS_YET), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
        val own = preview()

        compose.onNodeWithText("Square").performClick()
        compose.waitFor("the setting isn't kept") { config[Keys.WIDGET_CORNERS] == "square" }
        compose.waitForIdle()
        val square = preview()
        assertEquals(own.width, square.width)
        val differ = (0 until own.width step 2).sumOf { x ->
            (0 until own.height step 2).count { y -> own[x, y] != square[x, y] }
        }
        assertTrue("Square changed only $differ pixels of the preview", differ > 20)

        compose.onNodeWithText("Its own").performClick()
        compose.waitFor("the setting isn't its own again") { config[Keys.WIDGET_CORNERS] == "own" }
        compose.waitForIdle()
        val again = preview()
        val left = (0 until own.width step 2).sumOf { x ->
            (0 until own.height step 2).count { y -> own[x, y] != again[x, y] }
        }
        assertTrue("Its own left $left pixels changed", left < differ / 4)
    }

    /** Now playing in Split: its tiles are the card's outline, and Square changes them in the preview too. */
    @Test
    fun splitsTilesChangeInThePreview() {
        assumeTrue(
            "widgets page 1 had widgets before the test",
            t.host.widgets.value.none { it.location == WidgetLocation.PANEL }
        )
        assumeTrue("Now playing was placed before the test", t.host.placeBuiltin(WidgetSource.MEDIA, WidgetSize.S4X1))
        val placed = t.host.widgets.value.single { it.source == WidgetSource.builtin(WidgetSource.MEDIA) }
        t.host.setLook(placed.key, WidgetLook.SPLIT)
        show()
        compose.waitFor("the preview has no card") {
            compose.onAllNodes(androidx.compose.ui.test.hasText(NO_WIDGETS_YET), useUnmergedTree = true).fetchSemanticsNodes().isEmpty()
        }
        compose.waitForIdle()
        val own = preview()

        compose.onNodeWithText("Square").performClick()
        compose.waitFor("the setting isn't kept") { config[Keys.WIDGET_CORNERS] == "square" }
        compose.waitForIdle()
        val square = preview()
        val differ = (0 until own.width step 2).sumOf { x ->
            (0 until own.height step 2).count { y -> own[x, y] != square[x, y] }
        }
        assertTrue("Square changed only $differ pixels of Split's tiles in the preview", differ > 20)
    }
}
