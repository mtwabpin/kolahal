package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.ui.widgets.UpdatedLine.Place
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a card writes "Updated 3 min ago" ([UpdatedLine]): a large card in Plain and Tonal as
 * always; with Last updated on, a wide card whose content leaves a line free, and a versus card from
 * 4×1 in each of its looks. Never at 1×1, nor in a look that is the content. Sizes in dp, as on a
 * phone 411 dp wide: 1×1 is 94 by 94, 2×1 196 by 94, 4×1 403 by 94, 2×2 196 by 196.
 */
class UpdatedLineTest {
    private val small = 94 to 94
    private val wide = 196 to 94
    private val row = 403 to 94
    private val large = 196 to 196
    private val base = WidgetBase(title = "Card")

    private val match = WidgetSpec.Versus(base, listOf(WidgetSpec.Side("Harbor"), WidgetSpec.Side("Ridge")), score = "2–1")
    private val stat = WidgetSpec.Stat(base, "42", label = "AQI")
    private val list = WidgetSpec.Rows(base, listOf(WidgetSpec.Row("Keyboard")))

    private fun place(spec: WidgetSpec, look: WidgetLook, size: Pair<Int, Int>, asked: Boolean = true) =
        UpdatedLine.place(spec, look, size.first, size.second, asked)

    private fun why(spec: WidgetSpec?, look: WidgetLook, size: Pair<Int, Int>, label: String, kind: WidgetKind? = null) =
        UpdatedLine.why(spec, kind, look, size.first, size.second, label)

    /** The case that started it: a versus card at 4×1 has the line in each of its looks, placed by the card. */
    @Test
    fun aVersusCardAt4x1HasItInEachOfItsLooks() {
        WidgetLook.offeredFor(WidgetKind.VERSUS).forEach {
            assertEquals(it.id, Place.BODY, place(match, it, row))
            assertNull(it.id, why(match, it, row, "4×1"))
            assertEquals(it.id, Place.NONE, place(match, it, small))
        }
        // At 2×1 the names are under the marks: no line free.
        assertEquals(Place.NONE, place(match, WidgetLook.PLAIN, wide))
        assertEquals("Not at 2×1", why(match, WidgetLook.PLAIN, wide, "2×1"))
        // Large: under the content in Plain and Tonal, as every card; with its last line in Bold and Split.
        assertEquals(Place.CARD, place(match, WidgetLook.PLAIN, large))
        assertEquals(Place.BODY, place(match, WidgetLook.BOLD, large))
        assertEquals(Place.BODY, place(match, WidgetLook.SPLIT, large))
        // Before its first run, by its kind.
        assertNull(why(null, WidgetLook.SPLIT, row, "4×1", WidgetKind.VERSUS))
    }

    /** Never at 1×1, nor in a look that is the content but a versus card's; why, in a few words. */
    @Test
    fun notAt1x1NorInALookThatIsTheContent() {
        for (spec in listOf(stat, list, match)) {
            assertEquals(Place.NONE, place(spec, WidgetLook.PLAIN, small))
            assertEquals("Not at 1×1", why(spec, WidgetLook.PLAIN, small, "1×1"))
        }
        assertEquals("Not at this size", why(stat, WidgetLook.PLAIN, small, ""))
        for (look in listOf(WidgetLook.BOLD, WidgetLook.SPLIT, WidgetLook.SHAPE, WidgetLook.POSTER)) {
            assertEquals(look.id, Place.NONE, place(stat, look, large))
            assertEquals(look.id, Place.NONE, place(stat, look, wide))
            assertEquals("Not in ${look.label}", why(stat, look, large, "2×2"))
        }
    }

    /**
     * Wide in Plain and Tonal: content that sits in the middle of the card with a line free under it.
     * Not what fills the card's height and would shrink or lose a line: a chart, a list's rows, a
     * sparkline, a gauge, a countdown's dots or page, a picture that doesn't cover the card.
     */
    @Test
    fun aWideCardHasItWhenItsContentLeavesALineFree() {
        val roomy = listOf(
            stat,
            WidgetSpec.Text(base, "Carry an umbrella"),
            WidgetSpec.Progress(base, 0.72f),
            WidgetSpec.Progress(base, 0.72f, style = "bar"),
            WidgetSpec.Countdown(base, to = 2_000_000_000L),
            WidgetSpec.Image(base, "https://example.com/a.png", "Hills"),
        )
        val full = listOf(
            list,
            WidgetSpec.Stat(base, "6,240", points = listOf(1f, 3f, 2f)),
            WidgetSpec.Progress(base, 118f, style = "gauge"),
            WidgetSpec.Progress(base, 0.5f, style = "ring"),
            WidgetSpec.Countdown(base, to = 2_000_000_000L, style = "dots"),
            WidgetSpec.Countdown(base, to = 2_000_000_000L, style = "page"),
            WidgetSpec.Image(base, "https://example.com/a.png", "Hills", fit = "fit"),
        )
        for (look in listOf(WidgetLook.PLAIN, WidgetLook.TONAL)) {
            roomy.forEach {
                assertEquals("$it", Place.CARD, place(it, look, wide))
                assertEquals("$it", Place.CARD, place(it, look, row))
                assertNull(why(it, look, wide, "2×1"))
            }
            full.forEach {
                assertEquals("$it", Place.NONE, place(it, look, wide))
                assertEquals("$it", "Not at 4×1", why(it, look, row, "4×1"))
                assertNull("$it", why(it, look, large, "2×2"))
            }
        }
        // Before its first run, by its kind.
        assertNull(why(null, WidgetLook.PLAIN, wide, "2×1", WidgetKind.STAT))
        assertEquals("Not at 2×1", why(null, WidgetLook.PLAIN, wide, "2×1", WidgetKind.CHART))
    }

    /** Off, only a large card in Plain or Tonal writes the line, for old content or a failed run, as before. */
    @Test
    fun offOnlyALargeCardWritesIt() {
        assertEquals(Place.CARD, place(stat, WidgetLook.PLAIN, large, asked = false))
        assertEquals(Place.CARD, place(list, WidgetLook.TONAL, large, asked = false))
        assertEquals(Place.NONE, place(stat, WidgetLook.PLAIN, wide, asked = false))
        assertEquals(Place.NONE, place(match, WidgetLook.PLAIN, row, asked = false))
        assertEquals(Place.NONE, place(stat, WidgetLook.BOLD, large, asked = false))
    }
}
