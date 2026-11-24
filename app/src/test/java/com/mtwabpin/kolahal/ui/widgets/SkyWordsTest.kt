package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetParser
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** Where a sky card puts the sun or the moon, and what it says ([SkyWords]). */
class SkyWordsTest {
    private val lisbon = ZoneId.of("Europe/Lisbon")
    private val utc = ZoneId.of("UTC")

    private fun at(day: Int, hour: Int, minute: Int = 0, zone: ZoneId = utc) =
        ZonedDateTime.of(2026, 11, day, hour, minute, 0, 0, zone).toEpochSecond()

    // The sun of 24 November: up at 07:38, down at 17:20.
    private val rises = at(24, 7, 38)
    private val sets = at(24, 17, 20)

    private fun sun(now: Long, zone: ZoneId = utc) = SkyWords.place(rises, sets, now, moon = false, zone)

    @Test
    fun byDayTheSunIsOnItsArc() {
        val noon = sun(at(24, 12, 29))
        assertTrue(noon.up)
        assertEquals(0.5f, noon.along, 0.001f)
        assertEquals(rises, noon.rises)
        assertEquals(sets, noon.sets)
        assertEquals(sets, noon.next)
        assertEquals(0f, sun(rises).along, 0f)
        assertTrue(sun(rises).up)
    }

    /** After sunset the sun is under the horizon until the next sunrise: the arc's ends are that rise and the last set. */
    @Test
    fun atNightTheSunIsUnderTheHorizon() {
        val evening = sun(at(24, 17, 20))
        assertFalse(evening.up)
        assertEquals(0f, evening.along, 0f)
        assertEquals(sets, evening.sets)
        assertEquals(at(25, 7, 38), evening.rises)
        assertEquals(at(25, 7, 38), evening.next)
        // Half the night: 14 h 18 min from 17:20 to 07:38, so half is at 00:29.
        assertEquals(0.5f, sun(at(25, 0, 29)).along, 0.001f)
    }

    /** Before sunrise it is still the night after yesterday's sunset. */
    @Test
    fun beforeSunriseItIsLastNight() {
        val early = sun(at(24, 6, 0))
        assertFalse(early.up)
        assertEquals(at(23, 17, 20), early.sets)
        assertEquals(rises, early.next)
    }

    /** Times of an earlier day still put the sun right: they move by whole days, with no run of the script. */
    @Test
    fun oldTimesMoveByWholeDays() {
        val later = sun(at(28, 12, 29))
        assertTrue(later.up)
        assertEquals(0.5f, later.along, 0.001f)
        assertEquals(at(28, 7, 38), later.rises)
        assertEquals(at(28, 17, 20), later.sets)
        // Times of a later day work the same way.
        assertEquals(at(20, 17, 20), sun(at(20, 18, 0)).sets)
    }

    /** A day of the sun is a day on the clock: across the change of the clocks, sunrise keeps its time of day. */
    @Test
    fun aSunriseKeepsItsTimeWhereTheClocksChange() {
        // Lisbon goes back an hour in the night of 24 to 25 October 2026.
        val r = ZonedDateTime.of(2026, 10, 24, 7, 30, 0, 0, lisbon).toEpochSecond()
        val s = ZonedDateTime.of(2026, 10, 24, 18, 40, 0, 0, lisbon).toEpochSecond()
        val next = ZonedDateTime.of(2026, 10, 25, 9, 0, 0, 0, lisbon).toEpochSecond()
        val place = SkyWords.place(r, s, next, moon = false, lisbon)
        assertTrue(place.up)
        assertEquals(ZonedDateTime.of(2026, 10, 25, 7, 30, 0, 0, lisbon).toEpochSecond(), place.rises)
    }

    /** The moon rises about 50 minutes later each day: its times move by its own day. */
    @Test
    fun theMoonMovesByItsOwnDay() {
        val r = at(24, 13, 50)
        val s = at(25, 3, 12)
        val place = SkyWords.place(r, s, r + WidgetParser.MOON_DAY + 60, moon = true, utc)
        assertTrue(place.up)
        assertEquals(r + WidgetParser.MOON_DAY, place.rises)
        assertEquals(s + WidgetParser.MOON_DAY, place.sets)
    }

    @Test
    fun aPlaceOnTheArcRunsFromTheRiseOverTheTopToTheSet() {
        fun near(expected: Pair<Float, Float>, actual: Pair<Float, Float>) {
            assertEquals(expected.first, actual.first, 0.0001f)
            assertEquals(expected.second, actual.second, 0.0001f)
        }
        near(-1f to 0f, SkyWords.onArc(0f, up = true))
        near(0f to -1f, SkyWords.onArc(0.5f, up = true))
        near(1f to 0f, SkyWords.onArc(1f, up = true))
        // The night arc runs back under the horizon, from the set to the rise.
        near(1f to 0f, SkyWords.onArc(0f, up = false))
        near(0f to 1f, SkyWords.onArc(0.5f, up = false))
        near(-1f to 0f, SkyWords.onArc(1f, up = false))
    }

    /** The arcs and the body on them keep inside the sky's box, day and night. */
    @Test
    fun theArcsKeepInsideTheirBox() {
        for ((w, h) in listOf(161f to 100f, 74f to 68f, 300f to 60f, 50f to 40f)) {
            val a = SkyWords.arcs(w, h, unit = 1f)
            assertTrue("$w×$h: left", a.cx - a.rx - a.body >= 0f)
            assertTrue("$w×$h: right", a.cx + a.rx + a.body <= w)
            assertTrue("$w×$h: night", a.horizon + a.depth + a.body <= h)
            assertTrue("$w×$h: body", a.body in 7f..16f)
            if (h >= 60f) assertTrue("$w×$h: top", a.horizon - a.ry - a.body >= 0f)
        }
    }

    @Test
    fun whatComesNextIsSaidByTheMinute() {
        val now = at(24, 15, 10)
        assertEquals(listOf("Sunset in 2 h 10 min", "Sunset"), SkyWords.nextLines(sun(now), now, moon = false))
        val night = at(24, 22, 38)
        assertEquals(listOf("Sunrise in 9 h", "Sunrise"), SkyWords.nextLines(sun(night), night, moon = false))
        assertEquals("Moonset in 45 min", SkyWords.nextLines(sun(at(24, 16, 35)), at(24, 16, 35), moon = true)[0])
        // What is still to come is never "0 min".
        assertEquals("1 min", SkyWords.span(1))
        assertEquals("1 min", SkyWords.span(60))
        assertEquals("2 min", SkyWords.span(61))
        assertEquals("3 h", SkyWords.span(3 * 3600))
        assertEquals("2 hours 10 minutes", SkyWords.spanSaid(7800))
        assertEquals("1 hour", SkyWords.spanSaid(3600))
        assertEquals("1 minute", SkyWords.spanSaid(30))
    }

    @Test
    fun theMoonsPhaseHasItsName() {
        assertEquals("New moon", SkyWords.moonName(0.01f, waxing = true))
        assertEquals("Waxing crescent", SkyWords.moonName(0.2f, waxing = true))
        assertEquals("First quarter", SkyWords.moonName(0.5f, waxing = true))
        assertEquals("Waxing gibbous", SkyWords.moonName(0.78f, waxing = true))
        assertEquals("Full moon", SkyWords.moonName(0.99f, waxing = false))
        assertEquals("Waning gibbous", SkyWords.moonName(0.78f, waxing = false))
        assertEquals("Last quarter", SkyWords.moonName(0.52f, waxing = false))
        assertEquals("Waning crescent", SkyWords.moonName(0.1f, waxing = false))
        assertEquals("78%", SkyWords.lit(0.78f))
    }

    /** The lit side is the right one while the moon waxes; the line between light and dark bows toward it in a crescent. */
    @Test
    fun theMoonIsDrawnInItsPhase() {
        val crescent = SkyWords.moonShape(0.2f, waxing = true)
        assertTrue(crescent.litRight && crescent.crescent)
        assertEquals(0.6f, crescent.edge, 0.0001f)
        val gibbous = SkyWords.moonShape(0.78f, waxing = false)
        assertFalse(gibbous.litRight || gibbous.crescent)
        assertEquals(0.56f, gibbous.edge, 0.0001f)
        assertEquals(0f, SkyWords.moonShape(0.5f, waxing = true).edge, 0f)
        assertEquals(1f, SkyWords.moonShape(1f, waxing = true).edge, 0f)
    }

    /** A phase that has come is left out, with no run; the others come in their order. */
    @Test
    fun onlyThePhasesToComeAreShown() {
        val now = at(24, 12, 0)
        val full = WidgetSpec.Phase(1f, waxing = true, at = at(26, 0, 0))
        val past = WidgetSpec.Phase(0.5f, waxing = true, at = at(20, 0, 0))
        val last = WidgetSpec.Phase(0.5f, waxing = false, at = at(30, 0, 0))
        assertEquals(listOf(full, last), SkyWords.upcoming(listOf(last, past, full), now))
        assertEquals("26 Nov", SkyWords.phaseDay(full.at, utc, Locale.ENGLISH))
    }

    @Test
    fun aScreenReaderHearsWhereTheSunIsAndWhatComesNext() {
        val spec = WidgetSpec.Sky(WidgetBase(title = "Lisbon"), rises, sets)
        assertEquals(
            listOf("Sunset in 2 hours 10 minutes", "sunrise 07:38", "sunset 17:20"),
            SkyWords.said(spec, at(24, 15, 10), utc, clock24 = true, Locale.ENGLISH)
        )
        assertEquals(
            "Lisbon: Sunset in 2 hours 10 minutes, sunrise 7:38 AM, sunset 5:20 PM",
            WidgetWords.description("Lisbon", spec, at(24, 15, 10), utc, clock24 = false, Locale.ENGLISH)
        )
        assertTrue(WidgetWords.timed(spec))
    }

    @Test
    fun aScreenReaderHearsTheMoonAndItsPhases() {
        val spec = WidgetSpec.Sky(
            WidgetBase(),
            at(24, 13, 50),
            at(25, 3, 12),
            phase = 0.78f,
            waxing = true,
            phases = listOf(WidgetSpec.Phase(1f, waxing = true, at = at(26, 4, 0))),
        )
        assertEquals(
            listOf(
                "Waxing gibbous",
                "78% lit",
                "Moonset in 12 hours 12 minutes",
                "moonrise 13:50",
                "moonset 03:12",
                "Full moon 26 Nov"
            ),
            SkyWords.said(spec, at(24, 15, 0), utc, clock24 = true, Locale.ENGLISH)
        )
    }

    @Test
    fun theBigTextIsTheNextTimeOrTheLitPart() {
        val spec = WidgetSpec.Sky(WidgetBase(), rises, sets)
        val now = at(24, 15, 10)
        assertEquals("17:20", SkyWords.main(spec, sun(now), utc, clock24 = true, Locale.ENGLISH))
        assertEquals("78%", SkyWords.main(spec.copy(phase = 0.78f), sun(now), utc, clock24 = true, Locale.ENGLISH))
    }

    /**
     * The words of a wide card always read whole: a sun's label shortens to its event's name, and
     * a moon's phase gets smaller, then takes two lines. Here a character is 0.5 of its size wide.
     */
    @Test
    fun theWordsShortenOrGetSmallerButAreNeverCut() {
        val width = { text: String, sp: Float -> text.length * sp * 0.5f }
        // "Sunrise in 11 h 59 min" is 132 wide at 12 sp: in 90 the card writes "Sunrise".
        assertEquals(
            SkyWords.LineFit("Sunrise", 12f, twoLines = false),
            SkyWords.fitLine(listOf("Sunrise in 11 h 59 min", "Sunrise"), 12f, 10f, 90f, width)
        )
        assertEquals(
            SkyWords.LineFit("Sunrise in 11 h 59 min", 12f, twoLines = false),
            SkyWords.fitLine(listOf("Sunrise in 11 h 59 min", "Sunrise"), 12f, 10f, 140f, width)
        )
        // "Waning crescent" (15 characters) is 90 wide at 12 sp, 82.5 at 11, 75 at 10.
        assertEquals(SkyWords.LineFit("Waning crescent", 12f, twoLines = false), SkyWords.fitLine(listOf("Waning crescent"), 12f, 10f, 95f, width))
        assertEquals(SkyWords.LineFit("Waning crescent", 11f, twoLines = false), SkyWords.fitLine(listOf("Waning crescent"), 12f, 10f, 85f, width))
        assertEquals(SkyWords.LineFit("Waning crescent", 10f, twoLines = false), SkyWords.fitLine(listOf("Waning crescent"), 12f, 10f, 76f, width))
        assertEquals(SkyWords.LineFit("Waxing gibbous", 11f, twoLines = true), SkyWords.fitLine(listOf("Waxing gibbous"), 12f, 10f, 60f, width))
        // The names the card can give are these, the longest of 15 characters.
        val names = listOf(0.01f, 0.2f, 0.5f, 0.78f, 0.99f).flatMap { p -> listOf(true, false).map { SkyWords.moonName(p, it) } }
        assertEquals(15, names.maxOf { it.length })
    }
}
