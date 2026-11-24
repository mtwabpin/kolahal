package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.ui.theme.Palettes
import com.mtwabpin.kolahal.ui.theme.accents
import com.mtwabpin.kolahal.ui.theme.contrast
import com.mtwabpin.kolahal.ui.theme.schemeFrom
import com.mtwabpin.kolahal.ui.widgets.ShapeSteps.BatteryForm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.atan2
import kotlin.math.hypot

/** The Shape look's shapes that carry a value, and the motion of shapes and buttons ([ShapeSteps]). */
class ShapeStepsTest {
    private val hour = 3600L
    private val day = 86_400L
    private val now = 1_800_000_000L

    /** A countdown in days: a ray a day, at most 12, and the day itself a plain circle. */
    @Test
    fun aSunLosesARayAsEachDayGoes() {
        assertEquals(12, ShapeSteps.rays(now + 30 * day, null, now))
        assertEquals(12, ShapeSteps.rays(now + 12 * day + hour, null, now))
        assertEquals(7, ShapeSteps.rays(now + 7 * day + hour, null, now))
        assertEquals(2, ShapeSteps.rays(now + 2 * day + hour, null, now))
        assertEquals(1, ShapeSteps.rays(now + day, null, now))
        // With a start days before, the last day is the day itself.
        assertEquals(0, ShapeSteps.rays(now + 5 * hour, now - 10 * day, now))
    }

    /** A shorter wait counts hours, and one under an hour five minutes. */
    @Test
    fun aShortWaitCountsHoursOrFiveMinutes() {
        assertEquals(5, ShapeSteps.rays(now + 5 * hour + 60, null, now))
        assertEquals(12, ShapeSteps.rays(now + 23 * hour, null, now))
        assertEquals(8, ShapeSteps.rays(now + 40 * 60, null, now))
        assertEquals(0, ShapeSteps.rays(now + 4 * 60, null, now))
        // A wait of six hours counts hours to its end.
        assertEquals(3, ShapeSteps.rays(now + 3 * hour, now - 3 * hour, now))
        // A wait of 40 minutes counts five minutes.
        assertEquals(2, ShapeSteps.rays(now + 10 * 60, now - 30 * 60, now))
    }

    @Test
    fun aMomentThatHasComeIsAPlainCircle() {
        assertEquals(0, ShapeSteps.rays(now, null, now))
        assertEquals(0, ShapeSteps.rays(now - day, now - 10 * day, now))
    }

    /**
     * For each count from 12 to 0: twelve places round the body, a ray at each of the first n
     * clockwise from the top (a dial running down), the others on the body. Every count has the
     * same corners at the same angles, so a morph pairs each corner with itself.
     */
    @Test
    fun aSunHasItsRaysAtTheFirstPlacesClockwise() {
        val body = ShapeSteps.SUN_BODY
        val steps = ShapeSteps.SUN_STEPS
        val top = steps / 2
        // A ray is a tenth of the shape's width deep: 8 dp on a 1×1 card's 80 dp shape.
        assertEquals(0.1f, ShapeSteps.OUTER - body, 1e-6f)
        val angles = ShapeSteps.sunCorners(12).let { c ->
            List(c.size / 2) { Math.toDegrees(atan2((c[2 * it] - 0.5f).toDouble(), (0.5f - c[2 * it + 1]).toDouble())) }
        }
        for (n in 12 downTo 0) {
            assertEquals("$n rays", List(12) { it < n }, ShapeSteps.sunRays(n))
            val corners = ShapeSteps.sunCorners(n)
            assertEquals("$n rays: the same corners at every count", 12 * steps * 2, corners.size)
            fun radius(i: Int) = hypot(corners[2 * i] - 0.5f, corners[2 * i + 1] - 0.5f)
            for (i in 0 until corners.size / 2) {
                val angle = Math.toDegrees(atan2((corners[2 * i] - 0.5f).toDouble(), (0.5f - corners[2 * i + 1]).toDouble()))
                assertEquals("$n rays: corner $i at its angle", angles[i], angle, 1e-3)
            }
            for (place in 0 until 12) {
                val first = place * steps
                assertEquals("$n rays: place $place starts on the body", body, radius(first), 1e-5f)
                // The tip of a place: at the edge with a ray, on the body without.
                val tip = if (place < n) ShapeSteps.OUTER else body
                assertEquals("$n rays: the top of place $place", tip, radius(first + top), 1e-5f)
                for (s in 0 until steps) {
                    val r = radius(first + s)
                    if (place < n) assertTrue("$n rays: place $place in its box", r in body - 1e-5f..ShapeSteps.OUTER + 1e-5f)
                    else assertEquals("$n rays: place $place on the body", body, r, 1e-5f)
                }
            }
        }
        // The first ray's tip is at the top, the second a twelfth of the way round.
        val c = ShapeSteps.sunCorners(2)
        assertEquals(0.5f, c[2 * top], 1e-5f)
        assertEquals(0f, c[2 * top + 1], 1e-5f)
        assertEquals(30.0, angles[steps + top], 1e-3)
    }

    /**
     * A flower's corners: [ShapeSteps.FLOWER_STEPS] for each leaf, the first where it meets the leaf
     * before, the middle one its top; the first leaf's top at the top.
     */
    @Test
    fun aLeafRisesFromTheBodyOrLiesOnIt() {
        val body = ShapeSteps.FLOWER_BODY
        val steps = ShapeSteps.FLOWER_STEPS
        val out = listOf(true, false, true, false, false, false, false)
        val corners = ShapeSteps.lobeCorners(out, body, ShapeSteps.FLOWER_SHARPNESS, steps)
        assertEquals(7 * steps * 2, corners.size)
        fun radius(i: Int) = hypot(corners[2 * i] - 0.5f, corners[2 * i + 1] - 0.5f)
        val top = steps / 2
        assertEquals(0.5f, corners[2 * top], 1e-4f)
        assertEquals(0f, corners[2 * top + 1], 1e-4f)
        assertTrue("the second leaf is right of the middle", corners[2 * (steps + top)] > 0.5f)
        for (leaf in 0 until 7) {
            val first = leaf * steps
            assertEquals("leaf $leaf meets the one before", body, radius(first), 1e-4f)
            val expected = if (out[leaf]) ShapeSteps.OUTER else body
            assertEquals("the top of leaf $leaf", expected, radius(first + top), 1e-4f)
            for (s in 0 until steps) {
                assertTrue("leaf $leaf stays in its box", radius(first + s) in body - 1e-4f..ShapeSteps.OUTER + 1e-4f)
            }
        }
    }

    /** The flower is for a habit's week: dots of 0 and 1, a week at most. A longer run keeps its calendar. */
    @Test
    fun aWeekOfAHabitIsAFlower() {
        assertTrue(ShapeSteps.weekFlower("dots", listOf(1f, 0f, 1f, 1f, 1f, 1f, 1f)))
        assertTrue(ShapeSteps.weekFlower("dots", listOf(1f)))
        assertFalse(ShapeSteps.weekFlower("dots", List(8) { 1f }))
        assertFalse(ShapeSteps.weekFlower("dots", listOf(1f, 2f, 3f)))
        assertFalse(ShapeSteps.weekFlower("line", listOf(1f, 0f)))
        assertFalse(ShapeSteps.weekFlower("dots", emptyList()))
    }

    /** A leaf for each day of the week: the days up to the last point from the points, the days after it still to come. */
    @Test
    fun theLeavesAreTheWeeksDaysDone() {
        // The last point is a Wednesday where weeks start on Monday: column 2.
        val week = listOf(1f, 0f, 1f, 1f, 0f, 1f, 1f)
        assertEquals(
            listOf(false, true, true, false, false, false, false),
            ShapeSteps.weekLeaves(week, 2)
        )
        // A Sunday: the whole week, a whole flower when all are done.
        assertEquals(List(7) { true }, ShapeSteps.weekLeaves(List(7) { 1f }, 6))
        // Fewer points than days so far: a day with no point is not done.
        assertEquals(
            listOf(false, false, false, true, false, false, false),
            ShapeSteps.weekLeaves(listOf(1f), 3)
        )
    }

    /** Round when full, scalloped between, spiky when low; the clover while charging. */
    @Test
    fun theBatteryGoesFromRoundToSpiky() {
        assertEquals(BatteryForm.ROUND, ShapeSteps.batteryForm(100, false))
        assertEquals(BatteryForm.ROUND, ShapeSteps.batteryForm(92, false))
        assertEquals(BatteryForm.FINE_SCALLOPS, ShapeSteps.batteryForm(68, false))
        assertEquals(BatteryForm.SCALLOPS, ShapeSteps.batteryForm(48, false))
        assertEquals(BatteryForm.SOFT_SPIKES, ShapeSteps.batteryForm(BatteryCards.LOW + 1, false))
        // The sharpest is where the card turns to the bad color.
        assertEquals(BatteryForm.SPIKES, ShapeSteps.batteryForm(BatteryCards.LOW, false))
        assertEquals(BatteryForm.SPIKES, ShapeSteps.batteryForm(0, false))
        assertEquals(null, ShapeSteps.batteryForm(11, true))
        assertEquals(null, ShapeSteps.batteryForm(null, false))
    }

    /** A shape that settles grows a little while its spring swings past either end, and its morph stays in its range. */
    @Test
    fun aShapeSettlesByGrowingALittle() {
        assertEquals(1f, ShapeSteps.settleScale(0f), 0f)
        assertEquals(1f, ShapeSteps.settleScale(0.6f), 0f)
        assertEquals(1f, ShapeSteps.settleScale(1f), 0f)
        assertEquals(1.05f, ShapeSteps.settleScale(1.1f), 1e-5f)
        assertEquals(1.05f, ShapeSteps.settleScale(-0.1f), 1e-5f)
        assertEquals(1f, ShapeSteps.settleMorph(1.1f), 0f)
        assertEquals(0f, ShapeSteps.settleMorph(-0.1f), 0f)
        assertEquals(0.4f, ShapeSteps.settleMorph(0.4f), 0f)
    }

    /** A held button widens by Material's share and its neighbours give that up, so the row keeps its width. */
    @Test
    fun aHeldButtonWidensAndItsNeighboursGiveWay() {
        val r = ShapeSteps.GIVE_WAY
        assertEquals(listOf(1f - r / 2, 1f + r, 1f - r / 2, 1f), ShapeSteps.giveWay(4, 1))
        assertEquals(listOf(1f + r, 1f - r, 1f), ShapeSteps.giveWay(3, 0))
        assertEquals(listOf(1f, 1f - r, 1f + r), ShapeSteps.giveWay(3, 2))
        for (pressed in 0 until 4) {
            assertEquals(4f, ShapeSteps.giveWay(4, pressed).sum(), 1e-5f)
        }
        // None held, one from another row, or one alone: nothing moves.
        assertEquals(List(4) { 1f }, ShapeSteps.giveWay(4, -1))
        assertEquals(List(4) { 1f }, ShapeSteps.giveWay(4, 5))
        assertEquals(listOf(1f), ShapeSteps.giveWay(1, 0))
    }

    /**
     * A level's number reads on both sides of the line, in every theme and color: on each side it
     * is in the text color or the fill's "on" color, whichever has more contrast there.
     */
    @Test
    fun aLevelsNumberReadsOnBothSidesOfTheLine() {
        for (name in Keys.BUILT_IN_THEMES) for (dark in listOf(true, false)) {
            val p = Palettes.named(name, dark)
            val scheme = schemeFrom(p)
            val text = scheme.onSurface
            val onFill = scheme.surface
            val empty = scheme.surfaceContainerHighest
            for ((role, fill) in accents(p)) {
                val what = "$name, ${if (dark) "dark" else "light"}, $role"
                val over = inkOn(fill, onFill, text)
                assertTrue(
                    "$what: on the fill",
                    contrast(over, fill) >= maxOf(contrast(onFill, fill), contrast(text, fill))
                )
                val above = inkOn(empty, text, onFill)
                assertTrue(
                    "$what: over the empty part",
                    contrast(above, empty) >= maxOf(contrast(onFill, empty), contrast(text, empty))
                )
            }
        }
        // The light Rosé Pine: over the empty part above the line (the highest container, a light
        // gray there) the number is in the dark text color, not the light one.
        val dawn = Palettes.named("rose-pine", dark = false)
        val scheme = schemeFrom(dawn)
        assertEquals(scheme.onSurface, inkOn(scheme.surfaceContainerHighest, scheme.onSurface, scheme.surface))
    }

    /**
     * Both parts of a level's number read, at 3:1 at the least, in every built-in theme, dark and
     * light, and every color a card can have. The two inks are what [OnLevel] gives a number that
     * the fill's line crosses (48%): over the empty part (the frame fills a progress card with the
     * highest container) and over the fill (the card's color).
     */
    @Test
    fun bothPartsOfACrossedNumberReachThreeToOne() {
        for (name in Keys.BUILT_IN_THEMES) for (dark in listOf(true, false)) {
            val p = Palettes.named(name, dark)
            val scheme = schemeFrom(p)
            val empty = scheme.surfaceContainerHighest
            for ((role, fill) in accents(p) + ("muted" to p.dim) + ("text" to p.fg)) {
                val what = "$name, ${if (dark) "dark" else "light"}, $role"
                val above = inkOn(empty, scheme.onSurface, scheme.surface)
                val over = inkOn(fill, scheme.surface, scheme.onSurface)
                assertTrue("$what: above the line ${contrast(above, empty)}", contrast(above, empty) >= INK_MIN)
                assertTrue("$what: on the fill ${contrast(over, fill)}", contrast(over, fill) >= INK_MIN)
            }
        }
    }

    /** The words in the flower's middle fit it: "this week" in a large card, "of 7" in a small one. */
    @Test
    fun theFlowersWordsFitItsMiddle() {
        assertEquals("this week", ShapeSteps.weekWords(5, room = true))
        assertEquals("all week", ShapeSteps.weekWords(7, room = true))
        assertEquals("of 7", ShapeSteps.weekWords(5, room = false))
        assertEquals("of 7", ShapeSteps.weekWords(7, room = false))
    }
}
