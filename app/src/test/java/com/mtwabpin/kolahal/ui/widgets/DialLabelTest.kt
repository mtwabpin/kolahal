package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.ui.widgets.DialLabel.RISE
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Where the place's name sits on the Shape clock's dial: above the middle, or under it while the
 * hour hand is in the way.
 */
class DialLabelTest {
    /** A name's box in the dial's units, as a card of [sideDp] draws a name [widthDp] by [heightDp]. */
    private data class Name(val what: String, val width: Float, val height: Float)

    private fun name(what: String, sideDp: Float, widthDp: Float, heightDp: Float): Name {
        val unit = sideDp / 100f
        // The name gets at most half the dial's width, and is cut with "…" past it.
        return Name(what, minOf(widthDp / unit, 50f), heightDp / unit)
    }

    /** "Tokyo" in a 2×2 card: a dial 180 dp across, the name 36 dp by 16 dp. */
    private val usual = name("usual", 180f, 36f, 16f)

    /** A name cut short at half the dial. */
    private val long = name("long", 180f, 200f, 16f)

    /** The smallest dial with a name, 96 dp. */
    private val small = name("small", 96f, 36f, 16f)

    /** The phone's font size at 130% on the smallest dial. */
    private val larger = name("larger", 96f, 47f, 21f)

    /** The phone's font size at 200% on a 2×2 card. */
    private val largest = name("largest", 180f, 72f, 32f)

    /** The phone's font size at 200% on the smallest dial: the name reaches the dial's middle. */
    private val huge = name("huge", 96f, 72f, 32f)

    private val names = listOf(usual, long, small, larger, largest)

    private fun y(n: Name, hour: Int, minute: Int) =
        DialLabel.labelY(DialLabel.hourTurns(hour, minute), n.width, n.height)

    /** Each minute of a day from midnight: its hour and minute. */
    private val day = (0 until 24 * 60).map { it / 60 to it % 60 }

    @Test
    fun theHourHandTurnsOnceInTwelveHours() {
        assertEquals(0f, DialLabel.hourTurns(0, 0), 0f)
        assertEquals(0f, DialLabel.hourTurns(12, 0), 0f)
        assertEquals(3.5f / 12, DialLabel.hourTurns(15, 30), 1e-6f)
        assertEquals(DialLabel.hourTurns(3, 30), DialLabel.hourTurns(15, 30), 0f)
        assertTrue(DialLabel.hourTurns(23, 59) < 1f)
    }

    /**
     * Each hour of the day at :00, :20 and :40 on the usual card: the name is under the middle from
     * 10:18 to 1:42, and above the rest of the time, in the morning and in the evening.
     */
    @Test
    fun theNameGoesUnderWhileTheHourHandPointsAtIt() {
        for (hour in 0 until 24) for (minute in listOf(0, 20, 40)) {
            val h = hour % 12
            val under = h == 11 || h == 0 || (h == 10 && minute >= 20) || (h == 1 && minute <= 40)
            assertEquals("$hour:$minute", if (under) RISE else -RISE, y(usual, hour, minute), 0f)
        }
        // The edges of the range, to the minute.
        assertEquals(-RISE, y(usual, 10, 17), 0f)
        assertEquals(RISE, y(usual, 10, 18), 0f)
        assertEquals(RISE, y(usual, 13, 42), 0f)
        assertEquals(-RISE, y(usual, 13, 43), 0f)
    }

    /**
     * The hour hand only moves forward: in each twelve hours the name goes under once, while the
     * hand comes up to 12, and back once, after it, with no move back and forth at an edge.
     */
    @Test
    fun oneMoveUnderAndOneBackInEachPass() {
        for (n in names) {
            val ys = day.map { (h, m) -> y(n, h, m) }
            val moves = ys.indices.filter { ys[it] != ys[(it + ys.size - 1) % ys.size] }
            assertEquals(n.what, 4, moves.size)
            for (i in moves) {
                val hour = day[i].first % 12
                if (ys[i] == RISE) assertTrue("${n.what} under at ${day[i]}", hour in 8..11)
                else assertTrue("${n.what} back at ${day[i]}", hour in 0..3)
            }
        }
    }

    /** Where the name moved to, the hour hand is clear of it. */
    @Test
    fun theHourHandIsClearOfTheNameWhereItIs() {
        for (n in names) for ((h, m) in day) {
            val y = y(n, h, m)
            assertFalse(
                "${n.what} at $h:$m",
                DialLabel.covers(DialLabel.hourTurns(h, m), y, n.width, n.height)
            )
        }
    }

    /** The name under the middle is inside the dial, clear of the dot for 6. */
    @Test
    fun theNameUnderTheMiddleIsInsideTheDial() {
        for (n in names) {
            val bottom = RISE + n.height / 2
            assertTrue(n.what, hypot(n.width / 2, bottom) < 50f)
            assertTrue(n.what, bottom < 34f - 1.8f)
            assertTrue(n.what, RISE - n.height / 2 > 0f)
        }
    }

    /** A name that reaches the dial's middle is in the hand's way at every hour: it stays above. */
    @Test
    fun aNameAtTheMiddleStaysAbove() {
        for ((h, m) in day) assertEquals("$h:$m", -RISE, y(huge, h, m), 0f)
    }

    /** The minute hand never moves the name: it is over it at 3:00 and away at 3:20. */
    @Test
    fun theMinuteHandDoesNotMoveTheName() {
        assertEquals(-RISE, y(usual, 3, 0), 0f)
        assertEquals(-RISE, y(usual, 3, 20), 0f)
        // 6:30: the minute hand points at the place under the middle; the name is above anyway.
        assertEquals(-RISE, y(usual, 6, 30), 0f)
    }
}
