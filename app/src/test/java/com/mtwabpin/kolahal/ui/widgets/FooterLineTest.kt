package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.ui.widgets.UpdatedLine.Place
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The script's footer on a card's one foot line: where it goes ([UpdatedLine.footer]), and how it
 * shares the line with the note and the age ([FootRoom]). Sizes in dp, as on a phone 411 dp wide:
 * 1×1 is 94 by 94, 2×1 196 by 94, 4×1 403 by 94, 2×2 196 by 196.
 */
class FooterLineTest {
    private val small = 94 to 94
    private val wide = 196 to 94
    private val row = 403 to 94
    private val large = 196 to 196
    private val base = WidgetBase(title = "Air", footer = "City sensors")

    private val stat = WidgetSpec.Stat(base, "AQI 42", label = "Good · Lisbon")
    private val list = WidgetSpec.Rows(base, listOf(WidgetSpec.Row("Oat milk")))
    private val match = WidgetSpec.Versus(base, listOf(WidgetSpec.Side("North"), WidgetSpec.Side("South")), score = "2–1")
    private val photo = WidgetSpec.Image(base, "https://example.com/a.png", "Hills")

    private fun place(spec: WidgetSpec, look: WidgetLook, size: Pair<Int, Int>) =
        UpdatedLine.footer(spec, look, size.first, size.second)

    /** Large in Plain and Tonal: always, whatever the content, a picture that covers its card too (in its chip). */
    @Test
    fun aLargeCardAlwaysHasIt() {
        for (look in listOf(WidgetLook.PLAIN, WidgetLook.TONAL)) {
            for (spec in listOf(stat, list, match, photo)) assertEquals("$look $spec", Place.CARD, place(spec, look, large))
        }
    }

    /** Wide: where the age may show, with Last updated on or off; a versus card writes it in its body; never in a picture's chip. */
    @Test
    fun aWideCardHasItWhereTheAgeMayShow() {
        assertEquals(Place.CARD, place(stat, WidgetLook.PLAIN, wide))
        assertEquals(Place.CARD, place(WidgetSpec.Text(base, "Carry an umbrella"), WidgetLook.TONAL, row))
        // A list's rows fill the card: no line free.
        assertEquals(Place.NONE, place(list, WidgetLook.PLAIN, row))
        assertEquals(Place.NONE, place(WidgetSpec.Stat(base, "6,240", points = listOf(1f, 3f, 2f)), WidgetLook.PLAIN, row))
        assertEquals(Place.BODY, place(match, WidgetLook.PLAIN, row))
        // 2×1 versus: the names are under the marks.
        assertEquals(Place.NONE, place(match, WidgetLook.PLAIN, wide))
        assertEquals(Place.NONE, place(photo, WidgetLook.PLAIN, row))
    }

    /** Never at 1×1, nor in Bold, Split, Shape or Poster; nothing without a footer. */
    @Test
    fun neverAt1x1NorInALookThatIsTheContent() {
        assertEquals(Place.NONE, place(stat, WidgetLook.PLAIN, small))
        for (look in listOf(WidgetLook.BOLD, WidgetLook.SPLIT, WidgetLook.SHAPE, WidgetLook.POSTER)) {
            assertEquals(look.id, Place.NONE, place(stat, look, large))
            assertEquals(look.id, Place.NONE, place(match, look, row))
        }
        assertEquals(Place.NONE, place(stat.copy(base = WidgetBase(title = "Air")), WidgetLook.PLAIN, large))
    }

    /** The age's short form drops "Updated", where what is left still says it is an age. */
    @Test
    fun theAgeHasAShortForm() {
        fun short(text: String, failed: Boolean = false) = FootRoom.shortAge(WidgetWords.Footer(text, text, failed))
        assertEquals("3 min ago", short("Updated 3 min ago"))
        assertEquals("2 h ago", short("Updated 2 h ago"))
        assertEquals("just now", short("Updated just now"))
        assertNull(short("Updated yesterday"))
        assertNull(short("Updated Tue 20 Oct"))
        assertNull(short("Failed 10:42", failed = true))
    }

    /**
     * A 2×2 card (161 dp inside): "City sensors" doesn't fit whole beside "Updated 3 min ago" and
     * would keep less than half the line, so the age takes its short form. A 4×2 card (363 dp)
     * keeps the age whole and cuts the footer.
     */
    @Test
    fun theAgeIsShortOnlyOnAShortLine() {
        assertEquals(FootRoom.Plan(footer = true, short = true), FootRoom.plan(161f, 0f, 66f, 92f, 50f))
        assertEquals(FootRoom.Plan(footer = true, short = false), FootRoom.plan(363f, 33f, 260f, 80f, 40f))
        // Everything fits: the age keeps its words.
        assertEquals(FootRoom.Plan(footer = true, short = false), FootRoom.plan(363f, 0f, 66f, 92f, 50f))
        // A failure has no short form.
        assertEquals(FootRoom.Plan(footer = true, short = false), FootRoom.plan(161f, 0f, 66f, 92f, null))
        // No age: the footer alone.
        assertEquals(FootRoom.Plan(footer = true, short = false), FootRoom.plan(161f, 0f, 300f, 0f, null))
    }

    /** The footer is cut first, and goes where too little of it would be left; the age is never cut. */
    @Test
    fun theFooterGivesWayFirst() {
        // 120 dp: the age (92) and its gap leave the footer 20 dp, under the least it is drawn at.
        assertFalse(FootRoom.plan(120f, 0f, 66f, 92f, null).footer)
        // A long note leaves no room for the footer, and is cut itself beside the age.
        assertFalse(FootRoom.plan(161f, 100f, 66f, 50f, null).footer)
        // A footer shorter than the least is drawn where it fits whole.
        assertTrue(FootRoom.plan(120f, 0f, 18f, 92f, null).footer)
    }
}
