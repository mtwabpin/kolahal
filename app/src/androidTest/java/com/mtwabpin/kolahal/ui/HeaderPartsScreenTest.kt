package com.mtwabpin.kolahal.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Arrange the header: its parts under Left side, Right side and Not shown, dragged or moved by a screen reader. */
@RunWith(AndroidJUnit4::class)
class HeaderPartsScreenTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val compose = createComposeRule()
    @get:Rule val configReset = ConfigReset()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val config = Config.get(app)
    private lateinit var vm: TerminalViewModel

    private fun show() {
        compose.runOnUiThread { vm = vms.terminal(app) }
        compose.setContent {
            KolahalTheme {
                Box(Modifier.size(360.dp, 900.dp).consumeWindowInsets(WindowInsets.safeDrawing)) { HeaderPartsScreen(vm) }
            }
        }
    }

    /** The headings and the parts, as the settings have them, with the top of home above. */
    @Test fun theSidesShowAsTheSettingsHaveThem() {
        show()
        for (text in listOf("Left side", "Right side", "Not shown", "Time", "Battery", "Network")) {
            compose.onNodeWithText(text).assertExists()
        }
        compose.onNodeWithTag("header-preview").assertExists()
        // Today's layout on the left: the time above the date.
        val time = compose.onNodeWithTag("part-time").getUnclippedBoundsInRoot()
        val date = compose.onNodeWithTag("part-date").getUnclippedBoundsInRoot()
        assertEquals(true, time.top < date.top)
    }

    /** A screen reader moves a part to another side, or out of the header, and the settings follow. */
    @OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)
    @Test fun aScreenReaderMovesParts() {
        show()
        compose.onNodeWithTag("part-time").performCustomAccessibilityActionWithLabel("Move to the right side")
        compose.onNodeWithTag("part-battery").performCustomAccessibilityActionWithLabel("Don't show")
        compose.onNodeWithTag("part-network").performCustomAccessibilityActionWithLabel("Move to the left side")
        compose.runOnIdle {
            assertEquals("date,network", config[Keys.HEADER_LEFT])
            assertEquals("alarm,todo,time", config[Keys.HEADER_RIGHT])
        }
        // Within a side: the date down, after the network.
        compose.onNodeWithTag("part-date").performCustomAccessibilityActionWithLabel("Move down")
        compose.runOnIdle { assertEquals("network,date", config[Keys.HEADER_LEFT]) }
    }

    /** Dragged by its handle up past Right side, the battery ends the left side. */
    @Test fun aDragPastAHeadingChangesSides() {
        show()
        val heading = compose.onNodeWithText("Right side").getUnclippedBoundsInRoot()
        val battery = compose.onNodeWithTag("part-battery").getUnclippedBoundsInRoot()
        // The battery's middle past the heading row's middle (its text, with room above it): into the
        // left side's end, and no further.
        val px = with(compose.density) { (heading.top - 8.dp - (battery.top + battery.bottom) / 2).toPx() }
        compose.onNodeWithTag("drag-battery", useUnmergedTree = true).performTouchInput {
            down(center)
            repeat(12) { moveBy(Offset(0f, px / 12)) }
            up()
        }
        compose.runOnIdle {
            assertEquals("time,date,battery", config[Keys.HEADER_LEFT])
            assertEquals("alarm,todo", config[Keys.HEADER_RIGHT])
        }
    }
}
