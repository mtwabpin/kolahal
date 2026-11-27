package com.mtwabpin.kolahal.ui.widgets

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/**
 * A stat's target ("of 8,000") and the bar past it, and the badge of a stat, a chart and a
 * countdown: where each look and size draws them, and where they are left out or give way.
 */
@RunWith(AndroidJUnit4::class)
class StatTargetAndBadgeCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** What is drawn: [spec]'s card in [look], [width] by [height]. */
    private data class Card(val spec: WidgetSpec, val look: WidgetLook, val width: Dp, val height: Dp)

    /** The sizes on a phone 411 dp wide: 1×1, 2×1, 2×2 and 4×1. */
    private val cell = 92.dp
    private val two = 193.dp
    private val four = 395.dp

    private val steps = WidgetSpec.Stat(
        WidgetBase(title = "Steps"),
        "6,240",
        label = "Steps",
        progress = 0.78f,
        target = "8,000",
    )

    private val visitors = WidgetSpec.Stat(WidgetBase(title = "Visitors", badge = "live"), "42", label = "Visitors now")

    private val warmest = WidgetSpec.Chart(
        WidgetBase(title = "Warmest", badge = "Sat"),
        listOf(14f, 16f, 15f, 18f, 17f, 16f, 15f),
        value = "18°",
        label = "Warmest day",
    )

    private val trip = WidgetSpec.Countdown(
        WidgetBase(title = "Trip", badge = "Fri"),
        System.currentTimeMillis() / 1000 + 12 * 86_400,
        label = "Tax return",
    )

    private var card by mutableStateOf(Card(steps, WidgetLook.PLAIN, two, two))

    @Before
    fun setUp() {
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    val c = card
                    CompositionLocalProvider(LocalWidgetLook provides c.look) {
                        Box(Modifier.width(c.width)) {
                            WidgetCard(
                                title = c.spec.base.title.orEmpty(),
                                height = c.height,
                                spec = c.spec,
                                note = null,
                                description = WidgetWords.description(
                                    c.spec.base.title,
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

    private fun count(text: String): Int =
        compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().size

    private fun bounds(text: String): Rect =
        compose.onAllNodes(hasText(text), useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot

    private fun marks(): Int =
        compose.onAllNodes(hasTestTag(TARGET_MARK_TAG), useUnmergedTree = true).fetchSemanticsNodes().size

    /**
     * A 4×1 card has the target after the value, a 2×1 under the label, a 2×2 after the value or,
     * when the value leaves no room, at the start of the label's line; a 1×1 and Poster leave it out.
     */
    @Test
    fun theTargetIsByTheValueWhereThereIsRoom() {
        show(Card(steps, WidgetLook.PLAIN, four, cell))
        assertEquals(1, count("of 8,000"))
        assertTrue("after the value", bounds("of 8,000").left > bounds("6,240").right)
        assertTrue("before the label", bounds("of 8,000").right < bounds("Steps").left)

        show(Card(steps, WidgetLook.PLAIN, two, cell))
        assertTrue("under the label", bounds("of 8,000").top >= bounds("Steps").bottom - 1f)

        show(Card(steps, WidgetLook.PLAIN, two, two))
        if (count("of 8,000") == 1) assertTrue("after the value", bounds("of 8,000").left > bounds("6,240").right)
        else assertEquals("at the start of the label's line", 1, count("of 8,000 · Steps"))
        // A short value leaves the room.
        show(Card(steps.copy(value = "62"), WidgetLook.PLAIN, two, two))
        assertTrue("after the value", bounds("of 8,000").left > bounds("62").right)

        for (look in listOf(WidgetLook.BOLD, WidgetLook.SPLIT, WidgetLook.SHAPE)) {
            show(Card(steps, look, two, two))
            assertTrue("$look draws the target", count("of 8,000 · Steps") + count("Steps of 8,000 · Steps") == 1)
        }

        show(Card(steps, WidgetLook.PLAIN, cell, cell))
        assertEquals(0, count("of 8,000"))
        show(Card(steps, WidgetLook.POSTER, two, two))
        assertEquals(0, count("of 8,000"))
    }

    /** Past the target the bar is full with a mark, and a large card writes the share; under it, neither. */
    @Test
    fun pastTheTargetTheBarHasItsMarkAndTheShare() {
        val over = steps.copy(value = "8,960", progress = 1.12f)
        for (look in listOf(WidgetLook.PLAIN, WidgetLook.TONAL, WidgetLook.BOLD, WidgetLook.SPLIT)) {
            show(Card(over, look, two, two))
            assertEquals("$look's mark", 1, marks())
            assertEquals("$look's share", 1, count(WidgetWords.share(1.12f)))
        }
        // A 2×1 card has the mark, and no room for the share.
        show(Card(over, WidgetLook.PLAIN, two, cell))
        assertEquals(1, marks())
        assertEquals(0, count("112%"))

        show(Card(steps, WidgetLook.PLAIN, two, two))
        assertEquals(0, marks())
        assertEquals(0, count("78%"))
        // Without a target the bar is held at 1, with no mark.
        show(Card(steps.copy(target = null, progress = 1f), WidgetLook.PLAIN, two, two))
        assertEquals(0, marks())
    }

    /** The badge is drawn in Plain, Tonal, Bold and Split, wide and large, on a stat, a chart and a countdown. */
    @Test
    fun eachKindHasItsBadgeInEachLook() {
        for (spec in listOf<WidgetSpec>(visitors, warmest, trip)) {
            val badge = spec.base.badge!!
            for (look in listOf(WidgetLook.PLAIN, WidgetLook.TONAL, WidgetLook.BOLD, WidgetLook.SPLIT)) {
                for ((width, height) in listOf(two to two, four to cell)) {
                    show(Card(spec, look, width, height))
                    assertEquals("$badge in $look, ${width.value.toInt()}×${height.value.toInt()}", 1, count(badge))
                }
            }
        }
    }

    /** At 1×1, in Poster and in a card that is one shape the badge is left out. */
    @Test
    fun theBadgeIsLeftOutWhereThereIsNoRoom() {
        for (spec in listOf<WidgetSpec>(visitors, warmest, trip)) {
            val badge = spec.base.badge!!
            show(Card(spec, WidgetLook.PLAIN, cell, cell))
            assertEquals("$badge at 1×1", 0, count(badge))
            show(Card(spec, WidgetLook.SHAPE, two, two))
            assertEquals("$badge in one shape", 0, count(badge))
        }
        show(Card(visitors, WidgetLook.POSTER, two, two))
        assertEquals(0, count("live"))
    }

    /** A large stat has the badge at the end of the value's line; when the value fills the line, the badge goes. */
    @Test
    fun theBadgeNeverCoversTheValue() {
        show(Card(visitors, WidgetLook.PLAIN, two, two))
        val value = bounds("42")
        val badge = bounds("live")
        assertTrue("beside the value", badge.left > value.right)
        assertTrue("on its line", badge.top < value.bottom && badge.bottom > value.top)

        show(Card(visitors.copy(value = "1,234,567,890,123"), WidgetLook.PLAIN, two, two))
        assertEquals(0, count("live"))
    }
}
