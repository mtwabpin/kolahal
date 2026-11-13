package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

/** What a widget's card says, and what a screen reader hears. */
class WidgetWordsTest {
    private val utc = ZoneId.of("UTC")
    private val base = WidgetBase()

    private fun at(day: Int, hour: Int, minute: Int = 0, second: Int = 0) =
        ZonedDateTime.of(2026, 12, day, hour, minute, second, 0, utc).toEpochSecond()

    @Test fun aNumberDropsANeedlessPoint() {
        assertEquals("9", WidgetWords.num(9f))
        assertEquals("-4", WidgetWords.num(-4f))
        assertEquals("2.5", WidgetWords.num(2.5f))
        assertEquals("0.1", WidgetWords.num(0.1f))
        assertEquals("two decimals at most", "3.14", WidgetWords.num(3.14159f))
        assertEquals("–", WidgetWords.num(Float.NaN))
    }

    @Test fun aUnitThatStartsWithALetterGetsASpace() {
        assertEquals("24°C", WidgetWords.joined("24", "°C"))
        assertEquals("68%", WidgetWords.joined("68", "%"))
        assertEquals("12 km", WidgetWords.joined("12", "km"))
        assertEquals("12", WidgetWords.joined("12", null))
        assertEquals("12", WidgetWords.joined("12", ""))
    }

    @Test fun aBarIsFullAtItsMaximumAndNeverMore() {
        assertEquals(0.5f, WidgetWords.fraction(4f, 8f), 0f)
        assertEquals(1f, WidgetWords.fraction(9f, 8f), 0f)
        assertEquals(0f, WidgetWords.fraction(-1f, 8f), 0f)
        assertEquals("a scale of nothing is empty", 0f, WidgetWords.fraction(3f, 0f), 0f)
        assertEquals(0f, WidgetWords.fraction(Float.NaN, 8f), 0f)
        assertEquals("68%", WidgetWords.percent(0.68f, 1f))
        assertEquals("100%", WidgetWords.percent(12f, 8f))
    }

    @Test fun aTrendHasAnArrowAndAWord() {
        assertEquals("↑", WidgetWords.arrow("up"))
        assertEquals("↓", WidgetWords.arrow("down"))
        assertEquals("→", WidgetWords.arrow("flat"))
        assertNull(WidgetWords.arrow("sideways"))
        assertNull(WidgetWords.arrow(null))
        assertEquals("no change", WidgetWords.trendWord("flat"))
        assertNull(WidgetWords.trendWord(null))
    }

    @Test fun aChartIsSaidInWords() {
        assertEquals("7 values, from 3 to 12, the latest 9", WidgetWords.chartSummary(listOf(5f, 3f, 12f, 7f, 8f, 4f, 9f)))
        assertEquals("2 values, from 1.5 to 2, the latest 1.5", WidgetWords.chartSummary(listOf(2f, 1.5f)))
        assertEquals("1 value: 2.5", WidgetWords.chartSummary(listOf(2.5f)))
        assertEquals("No values", WidgetWords.chartSummary(emptyList()))
    }

    @Test fun aSpanIsNamedByItsLargestUnit() {
        assertEquals("12 days", WidgetWords.span(12 * 86_400L + 3 * 3600))
        assertEquals("1 day", WidgetWords.span(86_400L))
        assertEquals("5 hours", WidgetWords.span(5 * 3600L + 59 * 60))
        assertEquals("1 hour", WidgetWords.span(3600L))
        assertEquals("12 min", WidgetWords.span(12 * 60L + 30))
        assertEquals("under a minute", WidgetWords.span(59))
    }

    @Test fun aClockShowsDaysOnlyPastTwoDays() {
        assertEquals("03:25:10", WidgetWords.clock(3 * 3600L + 25 * 60 + 10))
        assertEquals("00:00:05", WidgetWords.clock(5))
        assertEquals("48:00:00", WidgetWords.clock(48 * 3600L))
        assertEquals("12d 03:25:10", WidgetWords.clock(12 * 86_400L + 3 * 3600 + 25 * 60 + 10))
        assertEquals("00:00:00", WidgetWords.clock(-3))
    }

    @Test fun aCountdownCountsDaysWhileFarAndIsAClockWhenNear() {
        val to = at(day = 20, hour = 0)
        // 12 days and an hour before.
        assertEquals(WidgetWords.Count("12 days", ticks = false), WidgetWords.countdown(to, to - 12 * 86_400L - 3600, null, null, utc))
        // Just over 48 hours: still days.
        assertEquals(WidgetWords.Count("2 days", ticks = false), WidgetWords.countdown(to, to - 48 * 3600L - 1, null, null, utc))
        // 48 hours and less: a clock, which needs a tick every second.
        assertEquals(WidgetWords.Count("48:00:00", ticks = true), WidgetWords.countdown(to, to - 48 * 3600L, null, null, utc))
        assertEquals(WidgetWords.Count("03:25:10", ticks = true), WidgetWords.countdown(to, to - (3 * 3600L + 25 * 60 + 10), null, null, utc))
    }

    @Test fun aCountdownFollowsItsStyle() {
        val to = at(day = 20, hour = 0)
        assertEquals("days, even when near", WidgetWords.Count("5 hours", ticks = false), WidgetWords.countdown(to, to - 5 * 3600L, "days", null, utc))
        assertEquals("a clock, even when far", WidgetWords.Count("12d 00:00:10", ticks = true), WidgetWords.countdown(to, to - 12 * 86_400L - 10, "clock", null, utc))
    }

    @Test fun afterTheMomentItSaysTodayAndThenTheTimeSince() {
        val to = at(day = 20, hour = 10)
        assertEquals(WidgetWords.Count("today", ticks = false), WidgetWords.countdown(to, to, null, null, utc))
        assertEquals("today", WidgetWords.countdown(to, to + 3600, null, null, utc).text)
        assertEquals("the script's own words", "Happy birthday", WidgetWords.countdown(to, to + 3600, null, "Happy birthday", utc).text)
        assertEquals("3 days ago", WidgetWords.countdown(to, to + 3 * 86_400L + 5, null, null, utc).text)
        assertEquals("the day after, the clock style too", "14 hours ago", WidgetWords.countdown(to, at(day = 21, hour = 0, minute = 30), "clock", null, utc).text)
        // Ten seconds before midnight, seen twenty seconds later: another day.
        val late = at(day = 20, hour = 23, minute = 59, second = 50)
        assertEquals("under a minute ago", WidgetWords.countdown(late, late + 20, null, null, utc).text)
    }

    @Test fun theDayIsThePhonesDay() {
        // 23:30 in UTC is 18:30 in New York. An hour later it is the next day in UTC, the same day there.
        val to = at(day = 20, hour = 23, minute = 30)
        assertEquals("1 hour ago", WidgetWords.countdown(to, to + 3600, null, null, utc).text)
        assertEquals("today", WidgetWords.countdown(to, to + 3600, null, null, ZoneId.of("America/New_York")).text)
    }

    @Test fun aCountdownsBarRunsFromItsStartToTheMoment() {
        assertNull("no start: no bar", WidgetWords.countdownFraction(null, 100, 50))
        assertNull("a start after the moment: no bar", WidgetWords.countdownFraction(200, 100, 50))
        assertEquals(0.25f, WidgetWords.countdownFraction(0, 100, 25)!!, 0f)
        assertEquals(0f, WidgetWords.countdownFraction(0, 100, -5)!!, 0f)
        assertEquals(1f, WidgetWords.countdownFraction(0, 100, 250)!!, 0f)
    }

    @Test fun aChartsScaleComesFromTheDataUnlessTheScriptGivesIt() {
        val points = listOf(3f, 12f, 9f)
        assertEquals("a line: from its lowest to its highest", 3f to 12f, WidgetWords.scale(points, null, null, bars = false))
        assertEquals("bars stand on zero", 0f to 12f, WidgetWords.scale(points, null, null, bars = true))
        assertEquals("unless a value is below it", -2f to 5f, WidgetWords.scale(listOf(-2f, 5f), null, null, bars = true))
        assertEquals("the script's scale wins", 0f to 20f, WidgetWords.scale(points, 0f, 20f, bars = false))
        assertEquals(5f to 12f, WidgetWords.scale(points, 5f, null, bars = true))
    }

    @Test fun aChartsScaleAlwaysHasSomeHeight() {
        assertEquals("a flat line sits in the middle", 4.5f to 5.5f, WidgetWords.scale(listOf(5f, 5f, 5f), null, null, bars = false))
        assertEquals(8.5f to 9.5f, WidgetWords.scale(listOf(9f), null, null, bars = false))
        assertEquals("bars of nothing", 0f to 1f, WidgetWords.scale(listOf(0f, 0f), null, null, bars = true))
        assertEquals("a maximum under the data", 5f to 6f, WidgetWords.scale(listOf(5f, 9f), null, 3f, bars = false))
        assertEquals("no points", -0.5f to 0.5f, WidgetWords.scale(emptyList(), null, null, bars = false))
        assertEquals("a value that is no number is left out", 1f to 2f, WidgetWords.scale(listOf(1f, Float.NaN, 2f), null, null, bars = false))
    }

    @Test fun aCardIsOneSentenceForAScreenReader() {
        val now = at(day = 20, hour = 0)
        val weather = WidgetSpec.Stat(
            base, value = "24", unit = "°C", label = "Sunny", delta = "+2", trend = "up",
            more = listOf(WidgetSpec.Small("68%", "humidity"), WidgetSpec.Small("12 km/h", null)), progress = 0.5f,
        )
        assertEquals("Weather: 24°C, Sunny, up +2, 68% humidity, 12 km/h, 50%", WidgetWords.description("Weather", weather, now, utc))
        assertEquals("no title, no colon", "24", WidgetWords.description(null, WidgetSpec.Stat(base, value = "24"), now, utc))
        assertEquals("Quote: Be kind., Anon", WidgetWords.description("Quote", WidgetSpec.Text(base, text = "Be kind.", caption = "Anon"), now, utc))
    }

    @Test fun aListSaysEveryRowAlsoThoseThatDoNotFit() {
        val now = at(day = 20, hour = 0)
        val rows = WidgetSpec.Rows(base, items = listOf(WidgetSpec.Row("Milk", detail = "2 litres", trailing = "today"), WidgetSpec.Row("Bread")))
        assertEquals("Shopping: Milk, 2 litres, today, Bread", WidgetWords.description("Shopping", rows, now, utc))
        assertEquals("Shopping: Nothing new", WidgetWords.description("Shopping", WidgetSpec.Rows(base, items = emptyList(), empty = "Nothing new"), now, utc))
    }

    @Test fun progressChartAndCountdownHaveWords() {
        val now = at(day = 20, hour = 0)
        assertEquals("Water: 5 / 8, glasses", WidgetWords.description("Water", WidgetSpec.Progress(base, value = 5f, max = 8f, text = "5 / 8", label = "glasses"), now, utc))
        val bars = WidgetSpec.Progress(base, value = 0f, items = listOf(WidgetSpec.Bar("Disk", 3f, 4f, null, null), WidgetSpec.Bar("Memory", 1f, 2f, "1 of 2 GB", null)))
        assertEquals("Phone: Disk 75%, Memory 1 of 2 GB", WidgetWords.description("Phone", bars, now, utc))
        val chart = WidgetSpec.Chart(base, points = listOf(3f, 12f, 9f), value = "9", label = "steps")
        assertEquals("Steps: 9, steps, 3 values, from 3 to 12, the latest 9", WidgetWords.description("Steps", chart, now, utc))
        assertEquals("the script's own summary", "Steps: 9, steps, Rising", WidgetWords.description("Steps", chart.copy(summary = "Rising"), now, utc))
        // A countdown that draws a clock is still said in words: a running clock is noise.
        val trip = WidgetSpec.Countdown(base, to = now + 3 * 3600L + 20, label = "Goa trip")
        assertEquals("Trip: 3 hours, Goa trip", WidgetWords.description("Trip", trip, now, utc))
    }

    @Test fun anAgeIsShortOnTheCardAndInWholeWordsForAScreenReader() {
        val now = 1_800_000_000_000L
        val minute = 60_000L
        val hour = 60 * minute
        assertEquals("just now", WidgetWords.age(now - 30_000, now))
        assertEquals("5 min ago", WidgetWords.age(now - 5 * minute, now))
        assertEquals("2 h ago", WidgetWords.age(now - 2 * hour - 5, now))
        assertEquals("3 d ago", WidgetWords.age(now - 3 * 24 * hour, now))
        assertEquals("5 minutes ago", WidgetWords.age(now - 5 * minute, now, whole = true))
        assertEquals("1 minute ago", WidgetWords.age(now - minute, now, whole = true))
        assertEquals("1 hour ago", WidgetWords.age(now - hour, now, whole = true))
        assertEquals("2 days ago", WidgetWords.age(now - 50 * hour, now, whole = true))
        assertEquals("just now", WidgetWords.age(now - 30_000, now, whole = true))
    }

    @Test fun theFooterSaysAFailureOrTheAgeOfOldContent() {
        val hour = 3_600_000L
        val now = ZonedDateTime.of(2026, 12, 20, 12, 0, 0, 0, utc).toInstant().toEpochMilli()
        assertNull("current content: no footer", WidgetWords.footer(hasContent = true, old = false, setAt = now - hour, triedAt = now - hour, failed = false, now = now, zone = utc))
        assertEquals(
            WidgetWords.Footer("2 h ago", "Updated 2 hours ago", failed = false),
            WidgetWords.footer(hasContent = true, old = true, setAt = now - 2 * hour, triedAt = now - 2 * hour, failed = false, now = now, zone = utc),
        )
        assertNull("nothing stored: nothing has an age", WidgetWords.footer(hasContent = false, old = true, setAt = 0, triedAt = 0, failed = false, now = now, zone = utc))
        // A failure wins over the age, and the old content stays on the card.
        val at = ZonedDateTime.of(2026, 12, 20, 10, 42, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals(
            WidgetWords.Footer("Failed 10:42", "Failed at 10:42", failed = true),
            WidgetWords.footer(hasContent = true, old = true, setAt = now - 5 * hour, triedAt = at, failed = true, now = now, zone = utc),
        )
        assertEquals(
            "not today: how long ago",
            WidgetWords.Footer("Failed 2 d ago", "Failed 2 days ago", failed = true),
            WidgetWords.footer(hasContent = false, old = false, setAt = 0, triedAt = now - 49 * hour, failed = true, now = now, zone = utc),
        )
        // The time is the phone's: 10:42 in UTC is 16:12 in India.
        assertEquals("Failed 16:12", WidgetWords.footer(hasContent = false, old = false, setAt = 0, triedAt = at, failed = true, now = now, zone = ZoneId.of("Asia/Kolkata"))!!.text)
    }

    @Test fun anErrorsFirstLineIsItsFirstLineWithText() {
        assertEquals("wttr.in answered HTTP 500", WidgetWords.firstLine("\n  wttr.in answered HTTP 500\n  at line 7"))
        assertNull(WidgetWords.firstLine("  \n "))
        assertNull(WidgetWords.firstLine(null))
    }

    @Test fun aListDrawsTheRowsThatFitAndCountsTheRest() {
        // All fit: no "+ more" line.
        assertEquals(3, WidgetWords.rowsShown(3, 152, 48, 20))
        assertFalse(WidgetWords.showsMore(3, 3, 152, 48, 20))
        // Not all fit: room is kept for the "+ more" line.
        assertEquals(2, WidgetWords.rowsShown(8, 152, 48, 20))
        assertTrue(WidgetWords.showsMore(8, 2, 152, 48, 20))
        // One row's room: the row, and no "+ more" line.
        assertEquals(1, WidgetWords.rowsShown(5, 48, 48, 20))
        assertFalse(WidgetWords.showsMore(5, 1, 48, 48, 20))
        // Less than one row's room: still one row.
        assertEquals(1, WidgetWords.rowsShown(5, 30, 48, 20))
        assertEquals(0, WidgetWords.rowsShown(0, 152, 48, 20))
    }
}
