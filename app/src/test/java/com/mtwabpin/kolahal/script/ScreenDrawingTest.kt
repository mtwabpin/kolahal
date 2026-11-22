package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.script.ScreenFrame.Companion.BAR
import com.mtwabpin.kolahal.script.ScreenFrame.Companion.BOX
import com.mtwabpin.kolahal.script.ScreenFrame.Companion.CLEAR
import com.mtwabpin.kolahal.script.ScreenFrame.Companion.FILL
import com.mtwabpin.kolahal.script.ScreenFrame.Companion.PUT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** A script's drawing calls (`g.put`, `g.fill`…), as they come from either engine. */
class ScreenDrawingTest {
    private fun drawing(cols: Int = 10, rows: Int = 4) = ScreenDrawing().apply { resize(cols, rows) }

    private fun ScreenDrawing.ops(): List<Int> = take(false).ops.toList()

    private fun style(fg: ColorRole? = null, bg: ColorRole? = null, flags: Int = 0) =
        ScreenFrame.style(fg, bg, flags)

    /** Runs [block] and expects an error that holds [says]. */
    private fun fails(says: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Exception) {
            assertTrue("${e.message}", says in e.message.orEmpty())
            return
        }
        fail("expected an error with “$says”")
    }

    @Test
    fun aTextGoesWithItsPlaceAndStyle() {
        val d = drawing()
        // Lua's numbers come as doubles, JavaScript's as they are.
        d.call("put", listOf(2.0, 1, "hi", mapOf("color" to "accent", "bold" to true)))
        val frame = d.take(tick = true)
        assertEquals(listOf(PUT, 2, 1, 0, style(ColorRole.ACCENT, flags = ScreenFrame.BOLD)), frame.ops.toList())
        assertEquals(listOf("hi"), frame.texts)
        assertTrue(frame.tick)
        assertEquals(10, frame.cols)
        assertEquals(4, frame.rows)
        // Nothing is left for the next frame.
        assertEquals(emptyList<Int>(), d.ops())
    }

    /** A color's name alone is a style; a style without a color takes the theme's. */
    @Test
    fun aColorNameIsAStyle() {
        val d = drawing()
        d.call("put", listOf(0, 0, "x", "good"))
        d.call("put", listOf(0, 0, "x"))
        d.call("clear", listOf(mapOf("bg" to "muted", "inverse" to true, "bold" to false)))
        assertEquals(
            listOf(
                PUT, 0, 0, 0, style(ColorRole.GOOD),
                PUT, 0, 0, 1, 0,
                CLEAR, style(bg = ColorRole.MUTED, flags = ScreenFrame.INVERSE),
            ), d.ops()
        )
    }

    /** Every color role and every flag comes back from a style as it went in. */
    @Test
    fun styleNumbersReadBack() {
        for (fg in ColorRole.entries + null) for (bg in ColorRole.entries + null) {
            val style = style(fg, bg, ScreenFrame.FLAG_MASK)
            assertEquals(fg, ScreenFrame.fg(style))
            assertEquals(bg, ScreenFrame.bg(style))
            assertEquals(ScreenFrame.FLAG_MASK, style and ScreenFrame.FLAG_MASK)
        }
        assertEquals(setOf("bold", "faint", "italic", "underline", "strike", "inverse"), ScreenFrame.FLAGS.keys)
    }

    /** A value that isn't text is shown as text: a whole number without ".0", a list as JSON. */
    @Test
    fun anyValueIsShownAsText() {
        val d = drawing()
        for (v in listOf(3.0, 2, 1.5, true, listOf(1, 2), null)) d.call("put", listOf(0, 0, v))
        assertEquals(listOf("3", "2", "1.5", "true", "[1,2]", ""), d.take(false).texts)
    }

    /** What is off the grid isn't sent, and a rectangle partly off it is cut to the part that shows. */
    @Test
    fun whatIsOffTheGridIsNotSent() {
        val d = drawing(10, 4)
        d.call("put", listOf(0, 4, "below"))
        d.call("put", listOf(10, 0, "right of it"))
        d.call("put", listOf(0, -1, "above"))
        d.call("fill", listOf(-2, -1, 5, 3, "#"))
        d.call("fill", listOf(20, 0, 3, 3, "#"))
        assertEquals(listOf(FILL, 0, 0, 3, 2, '#'.code, 0), d.ops())
        // A text that goes over several rows may start above the grid.
        d.call("put", listOf(0, -1, "above\nfirst row"))
        assertEquals(listOf(PUT, 0, -1, 0, 0), d.ops())
    }

    @Test
    fun aBarAndABoxAndABlank() {
        val d = drawing()
        d.call("bar", listOf(1, 0, 4, 0.5, "good"))
        d.call("bar", listOf(1, 0, 4, 7.0))
        d.call("bar", listOf(1, 0, 4, -1))
        d.call("box", listOf(0, 0, 5, 3, "link"))
        d.call("fill", listOf(0, 0, 2, 1, null))
        assertEquals(
            listOf(
                BAR, 1, 0, 4, 16, style(ColorRole.GOOD),
                BAR, 1, 0, 4, 32, 0,
                BAR, 1, 0, 4, 0, 0,
                BOX, 0, 0, 5, 3, style(ColorRole.LINK),
                FILL, 0, 0, 2, 1, ' '.code, 0,
            ), d.ops()
        )
    }

    /** Places are whole numbers: a fraction is dropped, and a far one is kept near the grid. */
    @Test
    fun placesAreWholeNumbers() {
        val d = drawing()
        d.call("put", listOf(1.9, 2.1, "x"))
        assertEquals(listOf(PUT, 1, 2, 0, 0), d.ops())
        d.call("bar", listOf(-1e300, 0, 5, 1))
        assertEquals(BAR, d.take(false).ops.first())
        d.call("bar", listOf(1, 0, 1e300, 0.5))
        // A very long bar is kept to a length of its own.
        assertEquals(listOf(BAR, 1, 0, 20_000, 80_000, 0), d.ops())
    }

    @Test
    fun aMistakeSaysWhatToPass() {
        val d = drawing()
        fails("g.put: col must be a number") { d.call("put", listOf("a", 1, "x")) }
        fails("g.put: col must be a number") { d.call("put", emptyList()) }
        fails("g.put: row must be a number") { d.call("put", listOf(0, Double.NaN, "x")) }
        fails("g.fill: width must be a number") { d.call("fill", listOf(0, 0)) }
        fails("g.bar: value must be a number") { d.call("bar", listOf(0, 0, 4, "half")) }
        fails("char is one character") { d.call("fill", listOf(0, 0, 1, 1, "ab")) }
        fails("char is one character") { d.call("fill", listOf(0, 0, 1, 1, 7)) }
        fails("no color 'red'") { d.call("put", listOf(0, 0, "x", "red")) }
        fails("no style 'colour'") { d.call("put", listOf(0, 0, "x", mapOf("colour" to "good"))) }
        fails("the style is a color") { d.call("put", listOf(0, 0, "x", 5)) }
        fails("isn't a drawing call") { d.call("line", listOf(0, 0)) }
    }

    /** A style of a Lua table with no keys has no look of its own. */
    @Test
    fun anEmptyStyleIsNone() {
        val d = drawing()
        d.call("put", listOf(0, 0, "x", emptyMap<String, Any?>()))
        assertEquals(listOf(PUT, 0, 0, 0, 0), d.ops())
    }

    /** A call may draw a few screens' worth, and no more, whatever it draws. The next one starts afresh. */
    @Test
    fun aCallDrawsOnlySoMuch() {
        val d = drawing(60, 45)
        fails("drew more than 8 full screens") { repeat(9) { d.call("clear", emptyList()) } }
        d.take(false)
        repeat(7) { d.call("clear", emptyList()) }
        d.take(false)
        fails("drew more than 8 full screens") { d.call("put", listOf(0, 0, "x".repeat(60 * 45 * 8 + 1))) }
        d.take(false)
        // Every call costs something, so a loop of empty ones ends too.
        fails("drew more than 8 full screens") { while (true) d.call("put", listOf(0, 0, "")) }
    }

    /** The smallest grid can still take a fair amount. */
    @Test
    fun aSmallGridHasRoomToo() {
        val d = drawing(1, 1)
        d.call("put", listOf(0, 0, "x".repeat(4_000)))
        assertEquals(1, d.take(false).texts.size)
    }

    /** Secrets are masked in what it draws, and a style of `fmt` can't split one apart first. */
    @Test
    fun theTextIsMaskedAndHasNoStylesLeft() {
        val d = ScreenDrawing { it.replace("secret", "••••") }.apply { resize(40, 2) }
        val bold = StyledText.wrap(StyledText.Style.BOLD, "cre")
        d.call("put", listOf(0, 0, "a se${bold}t b"))
        assertEquals(listOf("a •••• b"), d.take(false).texts)
    }
}
