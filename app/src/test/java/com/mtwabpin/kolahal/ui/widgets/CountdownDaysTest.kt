package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

/** A countdown that counts days on the calendar: a dot a day (`style = "dots"`) and a calendar page (`style = "page"`). */
class CountdownDaysTest {
    private val utc = ZoneId.of("UTC")

    private fun at(month: Int, day: Int, hour: Int = 0, minute: Int = 0, year: Int = 2026) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, utc).toEpochSecond()

    /** Tuesday 24 November 2026, at noon. The trip is on Sunday 20 December, at midnight. */
    private val now = at(11, 24, 12)
    private val trip = at(12, 20)

    @Test
    fun onlyDotsAndPageCountDays() {
        assertTrue(CountdownDays.counts("dots"))
        assertTrue(CountdownDays.counts("page"))
        assertFalse(CountdownDays.counts("days"))
        assertFalse(CountdownDays.counts("clock"))
        assertFalse(CountdownDays.counts(null))
    }

    /** A day is the phone's day: 26 days at any hour of either day. */
    @Test
    fun theDaysLeftAreDaysOnTheCalendar() {
        assertEquals(26, CountdownDays.daysLeft(trip, at(11, 24), utc))
        assertEquals(26, CountdownDays.daysLeft(trip + 23 * 3600, at(11, 24, 23, 59), utc))
        assertEquals(0, CountdownDays.daysLeft(trip, at(12, 20, 18), utc))
        assertEquals(-3, CountdownDays.daysLeft(trip, at(12, 23, 1), utc))
        // At 23:00 UTC on the 24th it is already the 25th in Tokyo.
        assertEquals(25, CountdownDays.daysLeft(trip + 9 * 3600, at(11, 24, 23), ZoneId.of("Asia/Tokyo")))
    }

    /** The mockup: 40 days from the start, 14 of them over, today's the 15th, and 26 to come with it. */
    @Test
    fun aStartKeepsTheDaysOverAsDots() {
        assertEquals(CountdownDays.Dots(40, 14, 14, false), CountdownDays.dots(trip, at(11, 10, 9), now, utc))
        // Without a start: the days left, today's first, one fewer each day.
        assertEquals(CountdownDays.Dots(26, 0, 0, false), CountdownDays.dots(trip, null, now, utc))
        assertEquals(CountdownDays.Dots(25, 0, 0, false), CountdownDays.dots(trip, null, at(11, 25, 8), utc))
        // A wait that starts later: all to come, and no dot is today.
        assertEquals(CountdownDays.Dots(19, 0, null, false), CountdownDays.dots(trip, at(12, 1), now, utc))
        // On the day and after: every dot is out; without a start there is none to draw.
        assertEquals(CountdownDays.Dots(40, 40, null, false), CountdownDays.dots(trip, at(11, 10), at(12, 20, 10), utc))
        assertEquals(CountdownDays.Dots(40, 40, null, false), CountdownDays.dots(trip, at(11, 10), at(12, 28), utc))
        assertNull(CountdownDays.dots(trip, null, at(12, 20, 10), utc))
        assertNull("a start on the moment's day", CountdownDays.dots(trip + 3600, trip, now, utc))
    }

    /** Up to 60 dots a day; then a dot a week, up to 60 weeks; past that, no dots. */
    @Test
    fun aLongWaitHasADotAWeek() {
        val day = 86_400L
        assertEquals(CountdownDays.Dots(60, 0, 0, false), CountdownDays.dots(at(11, 24) + 60 * day, null, now, utc))
        assertEquals(CountdownDays.Dots(9, 0, 0, true), CountdownDays.dots(at(11, 24) + 61 * day, null, now, utc))
        assertEquals(CountdownDays.Dots(15, 0, 0, true), CountdownDays.dots(at(11, 24) + 100 * day, null, now, utc))
        // 120 days from 20 days ago: 18 weeks, two of them over, today in the third.
        assertEquals(
            CountdownDays.Dots(18, 2, 2, true),
            CountdownDays.dots(at(11, 4) + 120 * day, at(11, 4), now, utc)
        )
        assertEquals(CountdownDays.Dots(60, 0, 0, true), CountdownDays.dots(at(11, 24) + 420 * day, null, now, utc))
        assertNull(CountdownDays.dots(at(11, 24) + 421 * day, null, now, utc))
    }

    @Test
    fun theNumberCountsDaysLeftThenDaysAgo() {
        assertEquals(WidgetWords.Count("26", "days left", false), CountdownDays.count(trip, now, utc, null))
        assertEquals(WidgetWords.Count("1", "day left", false), CountdownDays.count(trip, at(12, 19, 23), utc, null))
        assertEquals(WidgetWords.Count("Today", null, false), CountdownDays.count(trip + 18 * 3600, at(12, 20, 9), utc, "Bon voyage"))
        assertEquals(
            "the script's words once the moment has passed",
            WidgetWords.Count("Bon voyage", null, false),
            CountdownDays.count(trip, at(12, 20, 9), utc, "Bon voyage")
        )
        assertEquals(WidgetWords.Count("1", "day ago", false), CountdownDays.count(trip, at(12, 21), utc, null))
        assertEquals(WidgetWords.Count("3", "days ago", false), CountdownDays.count(trip, at(12, 23), utc, null))
    }

    @Test
    fun aPageSaysWhenInWords() {
        assertEquals("In 26 days", CountdownDays.inWords(trip, now, utc, null))
        assertEquals("Tomorrow", CountdownDays.inWords(trip, at(12, 19, 8), utc, null))
        assertEquals("Today", CountdownDays.inWords(trip, at(12, 20, 8), utc, null))
        assertEquals("Lift-off", CountdownDays.inWords(trip, at(12, 20, 8), utc, "Lift-off"))
        assertEquals("Yesterday", CountdownDays.inWords(trip, at(12, 21, 8), utc, null))
        assertEquals("3 days ago", CountdownDays.inWords(trip, at(12, 23, 8), utc, null))
    }

    /** The month on the band, the day, the weekday; the year when it is not this one; the time on the day. */
    @Test
    fun aPageHasTheMonthTheDayAndTheWeekday() {
        assertEquals(
            CountdownDays.Page("DEC", "20", "Sunday", "In 26 days"),
            CountdownDays.page(trip, now, utc, null, clock24 = true, locale = Locale.UK)
        )
        assertEquals(
            CountdownDays.Page("JAN 2027", "3", "Sunday", "In 40 days"),
            CountdownDays.page(at(1, 3, year = 2027), now, utc, null, clock24 = true, locale = Locale.UK)
        )
        // On the day, the time of a moment that isn't midnight.
        assertEquals(
            CountdownDays.Page("DEC", "20", "17:25", "Today"),
            CountdownDays.page(at(12, 20, 17, 25), at(12, 20, 9), utc, null, clock24 = true, locale = Locale.UK)
        )
        assertEquals(
            CountdownDays.Page("DEC", "20", "5:25 PM", "Today"),
            CountdownDays.page(at(12, 20, 17, 25), at(12, 20, 9), utc, null, clock24 = false, locale = Locale.US)
        )
    }

    @Test
    fun aScreenReaderHearsWhenAndTheDay() {
        val dots = WidgetSpec.Countdown(WidgetBase(), to = trip, label = "To Paris", style = "dots")
        assertEquals("26 days left, Sunday 20 December", CountdownDays.said(dots, now, utc, Locale.UK))
        assertEquals("In 26 days, Sunday 20 December", CountdownDays.said(dots.copy(style = "page"), now, utc, Locale.UK))
        assertEquals(
            "In 40 days, Sunday 3 January 2027",
            CountdownDays.said(dots.copy(style = "page", to = at(1, 3, year = 2027)), now, utc, Locale.UK)
        )
        assertNull(CountdownDays.said(dots.copy(style = "days"), now, utc, Locale.UK))
        assertEquals(
            "Paris: 26 days left, Sunday 20 December, To Paris",
            WidgetWords.description("Paris", dots, now, utc, locale = Locale.UK)
        )
    }

    /** The styles that draw days count in days up to the moment, never as a clock. */
    @Test
    fun dotsAndPageNeverTickAsAClock() {
        val soon = now + 10 * 3600
        assertEquals(WidgetWords.Count("10", "hours", false), WidgetWords.countdown(soon, now, "dots", null, utc))
        assertEquals(WidgetWords.Count("10", "hours", false), WidgetWords.countdown(soon, now, "page", null, utc))
        assertTrue(WidgetWords.countdown(soon, now, null, null, utc).ticks)
    }

    /** The dots take the grid with the largest room between them; the mockup's 40 in a 2×2 card are 10 by 4. */
    @Test
    fun theDotsFillTheirRoom() {
        assertEquals(CountdownDays.Grid(10, 4, 16f), CountdownDays.grid(40, 160f, 68f, 18f))
        assertEquals("fewer rows for the same room", CountdownDays.Grid(3, 1, 18f), CountdownDays.grid(3, 300f, 60f, 18f))
        val many = CountdownDays.grid(60, 70f, 68f, 18f)
        assertTrue(many.columns * many.rows >= 60)
        assertTrue(many.columns * many.pitch <= 70f && many.rows * many.pitch <= 68f)
        assertEquals(CountdownDays.Grid(0, 0, 0f), CountdownDays.grid(0, 160f, 68f, 18f))
    }
}
