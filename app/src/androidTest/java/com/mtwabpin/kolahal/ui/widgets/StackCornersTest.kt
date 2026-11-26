package com.mtwabpin.kolahal.ui.widgets

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.productivity.Note
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.widgets.WidgetLook
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The Notes card in the Stack look follows widgets.corners: its sheet fills the card, so it is the
 * card in another form. Square makes it square; its own corners leave it round ([CardCorners.inner]).
 */
@RunWith(AndroidJUnit4::class)
class StackCornersTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val config = Config.get(InstrumentationRegistry.getInstrumentation().targetContext)

    @After
    fun tearDown() = config.reset(Keys.WIDGET_CORNERS)

    private val zone = ZoneId.of("UTC")
    private val notes = listOf(Note(2, "Parcel comes Tuesday", 1_700_000_000_000L), Note(1, "Book", 1_699_000_000_000L))
    private val spec = checkNotNull(OwnCards.notes(notes, zone = zone, clock24 = true))
    private val body = noteStackBody(checkNotNull(OwnDesigns.stack(notes, zone, clock24 = true)))

    private fun show() {
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    CompositionLocalProvider(LocalWidgetLook provides WidgetLook.STACK) {
                        Box(Modifier.width(368.dp).testTag("card")) {
                            WidgetCard(title = "Notes", height = 184.dp, spec = spec, note = null, description = "Notes", body = body)
                        }
                    }
                }
            }
        }
    }

    private fun pixels(): PixelMap = compose.onNodeWithTag("card").captureToImage().toPixelMap()

    @Test
    fun theSheetFollowsTheSetting() {
        config.set(Keys.WIDGET_CORNERS, "own")
        show()
        compose.waitForIdle()
        val own = pixels()
        compose.runOnIdle { config.set(Keys.WIDGET_CORNERS, "square") }
        compose.waitForIdle()
        val square = pixels()
        val density = compose.density.density
        // The top sheet is 20 dp in from the start and 18 dp from the top of a 4×2 card (the card's room and the stack's own 4 dp).
        val left = (20 * density).roundToInt() + 1
        val top = (18 * density).roundToInt() + 1
        // Its color, at its start edge halfway down, clear of its words.
        val sheet = square[left, own.height / 2]
        assertEquals("the square sheet's corner isn't the sheet", sheet, square[left, top])
        assertNotEquals("the sheet's own corner is square too", sheet, own[left, top])
    }
}
