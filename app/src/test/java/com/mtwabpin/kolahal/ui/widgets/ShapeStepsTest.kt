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
     * The rays that are left are spread round the whole sun, the first at the top. Their largest gap
     * is as small as it can be: k rays in 12 places can't all be closer than 12 / k places apart
     * (rounded up), and they are never further.
     */
    @Test
    fun theRaysLeftAreSpreadRoundTheSun() {
        val places = (12 downTo 1).associateWith { k ->
            ShapeSteps.sunRays(k).withIndex().filter { it.value }.map { it.index }
        }
        assertEquals(List(12) { it }, places[12])
        assertEquals(listOf(0, 2, 3, 5, 7, 9, 10), places[7])
        assertEquals(listOf(0, 4, 8), places[3])
        assertEquals(listOf(0, 6), places[2])
        assertEquals(listOf(0), places[1])
        for ((k, kept) in places) {
            assertEquals("$k rays", k, kept.size)
            assertEquals("$k rays: one at the top", 0, kept.first())
            val gaps = kept.zipWithNext { a, b -> b - a } + (12 - kept.last() + kept.first())
            assertEquals("$k rays: the largest gap $gaps", (12 + k - 1) / k, gaps.max())
        }
        assertEquals(List(12) { false }, ShapeSteps.sunRays(0))
    }

    /**
     * A shape's corners: [ShapeSteps.SUN_STEPS] for each ray, the first where it meets the ray before,
     * the middle one its top; the first ray's top at the top, the next ray clockwise from it.
     */
    @Test
    fun aRayRisesFromTheBodyOrLiesOnIt() {
        val body = ShapeSteps.SUN_BODY
        val steps = ShapeSteps.SUN_STEPS
        val out = ShapeSteps.sunRays(2)
        val corners = ShapeSteps.lobeCorners(out, body, ShapeSteps.SUN_SHARPNESS, steps)
        assertEquals(12 * steps * 2, corners.size)
        fun radius(i: Int) = hypot(corners[2 * i] - 0.5f, corners[2 * i + 1] - 0.5f)
        val top = steps / 2
        assertEquals(0.5f, corners[2 * top], 1e-4f)
        assertEquals(0f, corners[2 * top + 1], 1e-4f)
        assertTrue("the second place is right of the middle", corners[2 * (steps + top)] > 0.5f)
        for (ray in 0 until 12) {
            val first = ray * steps
            assertEquals("ray $ray meets the one before", body, radius(first), 1e-4f)
            val expected = if (out[ray]) ShapeSteps.OUTER else body
            assertEquals("the top of ray $ray", expected, radius(first + top), 1e-4f)
            for (s in 0 until steps) {
                assertTrue("ray $ray stays in its box", radius(first + s) in body - 1e-4f..ShapeSteps.OUTER + 1e-4f)
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
