package com.mtwabpin.kolahal.ui

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId
import kotlin.math.abs

/**
 * A list whose rows have a range draws a bar for each row, in every look: one scale and one
 * column for all of them, so they compare. In a narrow card the icons give way first, and then
 * the bars; the texts never do.
 */
@RunWith(AndroidJUnit4::class)
class RangeBarsTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** Three days of a forecast: the scale is 20 to 31. */
    private val forecast = WidgetSpec.Rows(
        WidgetBase(title = "Pune"),
        listOf(
            WidgetSpec.Row(
                "Sat",
                trailing = "22–31°",
                icon = "sun",
                range = WidgetSpec.Range(22f, 31f)
            ),
            WidgetSpec.Row(
                "Sun",
                trailing = "21–30°",
                icon = "cloud",
                range = WidgetSpec.Range(21f, 30f)
            ),
            WidgetSpec.Row(
                "Mon",
                trailing = "20–27°",
                icon = "rain",
                range = WidgetSpec.Range(20f, 27f)
            ),
        ),
    )

    /** What is drawn: [spec]'s card in [look], [width] by [height], left to right or not. */
    private data class Card(
        val spec: WidgetSpec,
        val look: WidgetLook,
        val width: Dp,
        val height: Dp,
        val rtl: Boolean = false
    )

    /** A 4×2 card and a 2×2 one, as the widgets page sizes them on a phone 411 dp wide. */
    private val wide = 395.dp
    private val square = 193.dp

    private var card by mutableStateOf(Card(forecast, WidgetLook.PLAIN, wide, square))

    @Before
    fun setUp() {
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    val c = card
                    val direction = if (c.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                    CompositionLocalProvider(
                        LocalWidgetLook provides c.look,
                        LocalLayoutDirection provides direction
                    ) {
                        Box(Modifier.width(c.width)) {
                            WidgetCard(
                                title = "Pune",
                                height = c.height,
                                spec = c.spec,
                                note = null,
                                description = WidgetWords.description(
                                    "Pune",
                                    c.spec,
                                    0L,
                                    ZoneId.systemDefault()
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private fun show(c: Card) {
        card = c
        compose.waitForIdle()
    }

    private val density get() = compose.density.density

    /** Where each bar is, top to bottom. */
    private fun bars(): List<Rect> =
        compose.onAllNodes(hasTestTag("range-bar"), useUnmergedTree = true).fetchSemanticsNodes()
            .map { it.boundsInRoot }

    /** The picture of bar [index]. */
    private fun bar(index: Int): Bitmap =
        compose.onAllNodes(hasTestTag("range-bar"), useUnmergedTree = true)[index].captureToImage()
            .asAndroidBitmap()

    /** The colour across [bar] at [fraction] of its length from its left, in the middle of its line. */
    private fun colourAt(bar: Bitmap, fraction: Float): Int =
        bar.getPixel((bar.width * fraction).toInt().coerceIn(0, bar.width - 1), bar.height / 2)

    private fun near(a: Int, b: Int): Boolean =
        listOf(16, 8, 0).all { shift -> abs((a shr shift and 0xFF) - (b shr shift and 0xFF)) <= 3 }

    /** The card's colour for its bars: its role, the accent, as the theme draws it. */
    private val role: Int get() = roleColour(null).toArgb()

    /** How far [text] starts from the card's left, in dp. */
    private fun startOf(text: String): Float {
        val left = compose.onNodeWithTag("widget-card", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left
        return (compose.onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot.left - left) / density
    }

    /** Whether [text] is drawn cut: with "…", or past its room. */
    private fun cut(text: String): Boolean {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(
            results
        )
        val layout = results.single()
        return layout.hasVisualOverflow || layout.isLineEllipsized(0)
    }

    /**
     * Each look draws a bar for each row it shows, at 4×2 and at 2×2, all in one column. Bold shows
     * its first row large, with its bar at the end of its second line, and one line under it.
     */
    @Test
    fun eachLookDrawsItsBarsInOneColumn() {
        for (width in listOf(wide, square)) for (look in WidgetLook.entries) {
            show(Card(forecast, look, width, square))
            val bars = bars()
            val at = "$look, ${width.value.toInt()} dp wide"
            assertEquals(at, if (look == WidgetLook.BOLD) 2 else 3, bars.size)
            for (b in bars) {
                assertEquals("$at: one start", bars[0].left, b.left, 1f)
                assertEquals("$at: one length", bars[0].width, b.width, 1f)
            }
            assertTrue(
                "$at: a bar of ${bars[0].width / density} dp",
                bars[0].width / density >= WidgetWords.RANGE_MIN_DP - 0.5f
            )
            // Bold's first row has its numbers in its second line, and Monday is under "+1 more".
            val texts = if (look == WidgetLook.BOLD) listOf(
                "Sat",
                "22–31°",
                "Sun",
                "21–30°"
            ) else listOf("Sat", "Sun", "Mon", "22–31°", "21–30°", "20–27°")
            for (text in texts) assertFalse("$at: $text is cut", cut(text))
        }
    }

    /**
     * A 2×2 Plain card has no room for the bars beside the icons: its rows have 8 dp at their ends,
     * not 12, and the icons give way. A narrower card has no room for a bar at all: it keeps its
     * icons, and the rows are as they were.
     */
    @Test
    fun theIconsGiveWayFirstThenTheBars() {
        show(Card(forecast, WidgetLook.PLAIN, square, square))
        assertEquals(3, bars().size)
        // The card's 16 dp and the row's 8 dp: no icon in front.
        assertEquals(24f, startOf("Sat"), 1f)
        show(Card(forecast, WidgetLook.PLAIN, 150.dp, square))
        assertEquals(0, bars().size)
        // The card's 16 dp, the row's 12 dp and the icon's 28 dp.
        assertEquals(56f, startOf("Sat"), 1f)
        assertFalse(cut("22–31°"))
    }

    /** Each range is on the card's scale: Monday's 20 to 27 is the left two thirds, Saturday's 22 to 31 the right four fifths. */
    @Test
    fun theBarsShareOneScale() {
        show(Card(forecast, WidgetLook.PLAIN, wide, square))
        val sat = bar(0)
        assertFalse("Saturday starts at 22", near(colourAt(sat, 0.08f), role))
        assertTrue(near(colourAt(sat, 0.6f), role))
        assertTrue(near(colourAt(sat, 0.97f), role))
        val mon = bar(2)
        assertTrue(near(colourAt(mon, 0.05f), role))
        assertTrue(near(colourAt(mon, 0.5f), role))
        assertFalse("Monday ends at 27", near(colourAt(mon, 0.85f), role))
    }

    /** Right to left, the scale runs from the right: Monday's bar is the right two thirds. */
    @Test
    fun rightToLeftTheScaleRunsFromTheRight() {
        show(Card(forecast, WidgetLook.PLAIN, wide, square, rtl = true))
        val mon = bar(2)
        assertFalse(near(colourAt(mon, 0.15f), role))
        assertTrue(near(colourAt(mon, 0.5f), role))
        assertTrue(near(colourAt(mon, 0.95f), role))
    }

    /** A value is a dot on its bar, with a ring in the row's colour around it, so it shows on the bar too. */
    @Test
    fun aValueIsADotWithARing() {
        val today = forecast.copy(
            items = listOf(
                forecast.items[0].copy(
                    range = WidgetSpec.Range(
                        22f,
                        31f,
                        27f
                    )
                )
            ) + forecast.items.drop(1)
        )
        show(Card(today, WidgetLook.PLAIN, wide, square))
        val sat = bar(0)
        val dot = 7f / 11
        assertTrue("the dot", near(colourAt(sat, dot), role))
        val ring = 5 * density / sat.width
        assertFalse("its ring, over the bar", near(colourAt(sat, dot + ring), role))
        assertTrue("the bar past the ring", near(colourAt(sat, dot + 3 * ring), role))
    }
}
