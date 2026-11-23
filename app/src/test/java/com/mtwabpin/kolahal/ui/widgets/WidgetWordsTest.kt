package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.runOutcome
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a widget's card says, and what a screen reader hears. */
class WidgetWordsTest {
    private val utc = ZoneId.of("UTC")
    private val base = WidgetBase()

    private fun at(day: Int, hour: Int, minute: Int = 0, second: Int = 0) =
        ZonedDateTime.of(2026, 12, day, hour, minute, second, 0, utc).toEpochSecond()

    @Test
    fun aNumberDropsANeedlessPoint() {
        assertEquals("9", WidgetWords.num(9f))
        assertEquals("-4", WidgetWords.num(-4f))
        assertEquals("2.5", WidgetWords.num(2.5f))
        assertEquals("0.1", WidgetWords.num(0.1f))
        assertEquals("two decimals at most", "3.14", WidgetWords.num(3.14159f))
        assertEquals("–", WidgetWords.num(Float.NaN))
    }

    @Test
    fun aUnitThatStartsWithALetterGetsASpace() {
        assertEquals("24°C", WidgetWords.joined("24", "°C"))
        assertEquals("68%", WidgetWords.joined("68", "%"))
        assertEquals("12 km", WidgetWords.joined("12", "km"))
        assertEquals("12", WidgetWords.joined("12", null))
        assertEquals("12", WidgetWords.joined("12", ""))
    }

    @Test
    fun aBarIsFullAtItsMaximumAndNeverMore() {
        assertEquals(0.5f, WidgetWords.fraction(4f, 8f), 0f)
        assertEquals(1f, WidgetWords.fraction(9f, 8f), 0f)
        assertEquals(0f, WidgetWords.fraction(-1f, 8f), 0f)
        assertEquals("a scale of nothing is empty", 0f, WidgetWords.fraction(3f, 0f), 0f)
        assertEquals(0f, WidgetWords.fraction(Float.NaN, 8f), 0f)
        assertEquals("68%", WidgetWords.percent(0.68f, 1f))
        assertEquals("100%", WidgetWords.percent(12f, 8f))
    }

    @Test
    fun aTrendHasAnArrowAndAWord() {
        assertEquals("↑", WidgetWords.arrow("up"))
        assertEquals("↓", WidgetWords.arrow("down"))
        assertEquals("→", WidgetWords.arrow("flat"))
        assertNull(WidgetWords.arrow("sideways"))
        assertNull(WidgetWords.arrow(null))
        assertEquals("no change", WidgetWords.trendWord("flat"))
        assertNull(WidgetWords.trendWord(null))
    }

    @Test
    fun aChartIsSaidInWords() {
        assertEquals(
            "7 values, from 3 to 12, the latest 9",
            WidgetWords.chartSummary(listOf(5f, 3f, 12f, 7f, 8f, 4f, 9f))
        )
        assertEquals(
            "2 values, from 1.5 to 2, the latest 1.5",
            WidgetWords.chartSummary(listOf(2f, 1.5f))
        )
        assertEquals("1 value: 2.5", WidgetWords.chartSummary(listOf(2.5f)))
        assertEquals("No values", WidgetWords.chartSummary(emptyList()))
    }

    /** A chart of dots is said as days, the last one today: how many were done, or what the days had. */
    @Test
    fun aChartOfDotsIsSaidAsDays() {
        assertEquals(
            "Done on 3 of the last 5 days",
            WidgetWords.dotsSummary(listOf(1f, 0f, 1f, 1f, 0f))
        )
        assertEquals("Done on 0 of the last 35 days", WidgetWords.dotsSummary(List(35) { 0f }))
        assertEquals("Done today", WidgetWords.dotsSummary(listOf(1f)))
        assertEquals("Not done today", WidgetWords.dotsSummary(listOf(0f)))
        assertEquals(
            "4 days, from 26 to 62, today 42",
            WidgetWords.dotsSummary(listOf(44f, 26f, 62f, 42f))
        )
        assertEquals("Today: 2.5", WidgetWords.dotsSummary(listOf(2.5f)))
        assertEquals("No values", WidgetWords.dotsSummary(emptyList()))
    }

    /** A countdown in days names the time left by its largest unit. */
    @Test
    fun aSpanIsNamedByItsLargestUnit() {
        val now = 1_700_000_000L
        fun span(seconds: Long) = WidgetWords.countdown(now + seconds, now, "days", null, utc).text
        assertEquals("12 days", span(12 * 86_400L + 3 * 3600))
        assertEquals("1 day", span(86_400L))
        assertEquals("5 hours", span(5 * 3600L + 59 * 60))
        assertEquals("1 hour", span(3600L))
        assertEquals("12 min", span(12 * 60L + 30))
        assertEquals("under a minute", span(59))
    }

    @Test
    fun aClockShowsDaysOnlyPastTwoDays() {
        assertEquals("03:25:10", WidgetWords.clock(3 * 3600L + 25 * 60 + 10))
        assertEquals("00:00:05", WidgetWords.clock(5))
        assertEquals("48:00:00", WidgetWords.clock(48 * 3600L))
        assertEquals("12d 03:25:10", WidgetWords.clock(12 * 86_400L + 3 * 3600 + 25 * 60 + 10))
        assertEquals("00:00:00", WidgetWords.clock(-3))
    }

    @Test
    fun aCountdownCountsDaysWhileFarAndIsAClockWhenNear() {
        val to = at(day = 20, hour = 0)
        // 12 days and an hour before.
        assertEquals(
            WidgetWords.Count("12", "days", ticks = false),
            WidgetWords.countdown(to, to - 12 * 86_400L - 3600, null, null, utc)
        )
        // Just over 48 hours: still days.
        assertEquals(
            WidgetWords.Count("2", "days", ticks = false),
            WidgetWords.countdown(to, to - 48 * 3600L - 1, null, null, utc)
        )
        // 48 hours and less: a clock, which needs a tick every second.
        assertEquals(
            WidgetWords.Count("48:00:00", null, ticks = true),
            WidgetWords.countdown(to, to - 48 * 3600L, null, null, utc)
        )
        assertEquals(
            WidgetWords.Count("03:25:10", null, ticks = true),
            WidgetWords.countdown(to, to - (3 * 3600L + 25 * 60 + 10), null, null, utc)
        )
    }

    @Test
    fun aCountdownFollowsItsStyle() {
        val to = at(day = 20, hour = 0)
        assertEquals(
            "days, even when near",
            WidgetWords.Count("5", "hours", ticks = false),
            WidgetWords.countdown(to, to - 5 * 3600L, "days", null, utc)
        )
        assertEquals(
            "a clock, even when far",
            WidgetWords.Count("12d 00:00:10", null, ticks = true),
            WidgetWords.countdown(to, to - 12 * 86_400L - 10, "clock", null, utc)
        )
    }

    @Test
    fun afterTheMomentItSaysTodayAndThenTheTimeSince() {
        val to = at(day = 20, hour = 10)
        assertEquals(
            WidgetWords.Count("today", null, ticks = false),
            WidgetWords.countdown(to, to, null, null, utc)
        )
        assertEquals("today", WidgetWords.countdown(to, to + 3600, null, null, utc).text)
        assertEquals(
            "the script's own words",
            "Happy birthday",
            WidgetWords.countdown(to, to + 3600, null, "Happy birthday", utc).text
        )
        assertEquals(
            "3 days ago",
            WidgetWords.countdown(to, to + 3 * 86_400L + 5, null, null, utc).text
        )
        assertEquals(
            "the day after, the clock style too",
            "14 hours ago",
            WidgetWords.countdown(to, at(day = 21, hour = 0, minute = 30), "clock", null, utc).text
        )
        // Ten seconds before midnight, seen twenty seconds later: another day.
        val late = at(day = 20, hour = 23, minute = 59, second = 50)
        assertEquals(
            "under a minute ago",
            WidgetWords.countdown(late, late + 20, null, null, utc).text
        )
    }

    /** A card draws a countdown's number big and its unit small. A text with no number is all value. */
    @Test
    fun aCountdownGivesItsNumberAndItsUnitApart() {
        val to = at(day = 20, hour = 10)
        assertEquals(
            WidgetWords.Count("1", "day", ticks = false),
            WidgetWords.countdown(to, to - 86_400L, "days", null, utc)
        )
        assertEquals(
            WidgetWords.Count("12", "min", ticks = false),
            WidgetWords.countdown(to, to - 12 * 60L, "days", null, utc)
        )
        assertEquals(
            WidgetWords.Count("3", "days ago", ticks = false),
            WidgetWords.countdown(to, to + 3 * 86_400L + 5, null, null, utc)
        )
        assertEquals(
            WidgetWords.Count("under a minute", null, ticks = false),
            WidgetWords.countdown(to, to - 30, "days", null, utc)
        )
        val late = at(day = 20, hour = 23, minute = 59, second = 50)
        assertEquals(
            WidgetWords.Count("under a minute ago", null, ticks = false),
            WidgetWords.countdown(late, late + 20, null, null, utc)
        )
    }

    @Test
    fun theDayIsThePhonesDay() {
        // 23:30 in UTC is 18:30 in New York. An hour later it is the next day in UTC, the same day there.
        val to = at(day = 20, hour = 23, minute = 30)
        assertEquals("1 hour ago", WidgetWords.countdown(to, to + 3600, null, null, utc).text)
        assertEquals(
            "today",
            WidgetWords.countdown(to, to + 3600, null, null, ZoneId.of("America/New_York")).text
        )
    }

    @Test
    fun aCountdownsBarRunsFromItsStartToTheMoment() {
        assertNull("no start: no bar", WidgetWords.countdownFraction(null, 100, 50))
        assertNull("a start after the moment: no bar", WidgetWords.countdownFraction(200, 100, 50))
        assertEquals(0.25f, WidgetWords.countdownFraction(0, 100, 25)!!, 0f)
        assertEquals(0f, WidgetWords.countdownFraction(0, 100, -5)!!, 0f)
        assertEquals(1f, WidgetWords.countdownFraction(0, 100, 250)!!, 0f)
    }

    /** A large countdown writes its start and its moment under the bar's ends: days, or times on one day. */
    @Test
    fun aCountdownsBarHasItsDaysAtItsEnds() {
        fun at(text: String) = ZonedDateTime.parse(text).toEpochSecond()
        val now = at("2026-11-14T10:00:00Z")
        assertEquals(
            "1 Oct" to "20 Dec",
            WidgetWords.countdownEnds(
                at("2026-10-01T00:00:00Z"),
                at("2026-12-20T00:00:00Z"),
                now,
                utc,
                clock24 = true,
                Locale.ENGLISH
            )
        )
        // Another year than now's has its year.
        assertEquals(
            "15 Jun" to "15 Jun 2027",
            WidgetWords.countdownEnds(
                at("2026-06-15T00:00:00Z"),
                at("2027-06-15T00:00:00Z"),
                now,
                utc,
                clock24 = true,
                Locale.ENGLISH
            )
        )
        // On one day: the times, in the form the setting says.
        assertEquals(
            "09:30" to "17:25",
            WidgetWords.countdownEnds(
                at("2026-11-14T09:30:00Z"),
                at("2026-11-14T17:25:00Z"),
                now,
                utc,
                clock24 = true,
                Locale.ENGLISH
            )
        )
        assertEquals(
            "9:30 AM" to "5:25 PM",
            WidgetWords.countdownEnds(
                at("2026-11-14T09:30:00Z"),
                at("2026-11-14T17:25:00Z"),
                now,
                utc,
                clock24 = false,
                Locale.ENGLISH
            )
        )
        // The day is the phone's: 20 Dec at 01:00 in Kolkata is still 19 Dec in UTC.
        assertEquals(
            "20 Dec",
            WidgetWords.countdownEnds(
                at("2026-10-01T00:00:00Z"),
                at("2026-12-19T19:30:00Z"),
                now,
                ZoneId.of("Asia/Kolkata"),
                clock24 = true,
                Locale.ENGLISH
            ).second
        )
    }

    /** The bar of a countdown that ticks gets a new value only when it has moved a step: not every second. */
    /** The moment under a countdown is written again at midnight where the phone is, not at midnight in UTC. */
    @Test
    fun aCountdownsDayChangesAtMidnightHere() {
        fun at(text: String) = ZonedDateTime.parse(text).toEpochSecond()
        val kolkata = ZoneId.of("Asia/Kolkata")
        val newYork = ZoneId.of("America/New_York")
        val nov14 = java.time.LocalDate.of(2026, 11, 14).toEpochDay()
        // East of UTC: midnight in Kolkata is 18:30 in UTC the day before.
        assertEquals(nov14, WidgetWords.localDay(at("2026-11-14T18:29:59Z"), kolkata))
        assertEquals(nov14 + 1, WidgetWords.localDay(at("2026-11-14T18:30:00Z"), kolkata))
        assertEquals(nov14 + 1, WidgetWords.localDay(at("2026-11-15T05:00:00Z"), kolkata))
        // West of UTC: at 02:00 in UTC on the 15th it is still the 14th in New York, until 05:00.
        assertEquals(nov14, WidgetWords.localDay(at("2026-11-15T02:00:00Z"), newYork))
        assertEquals(nov14, WidgetWords.localDay(at("2026-11-15T04:59:59Z"), newYork))
        assertEquals(nov14 + 1, WidgetWords.localDay(at("2026-11-15T05:00:00Z"), newYork))
        assertEquals(nov14, WidgetWords.localDay(at("2026-11-14T23:59:59Z"), utc))
    }

    @Test
    fun aCountdownsBarMovesInSteps() {
        assertEquals(0.25f, WidgetWords.barStep(0.25f), 0f)
        assertEquals(0.333f, WidgetWords.barStep(1f / 3), 0f)
        assertEquals(0f, WidgetWords.barStep(0.0004f), 0f)
        assertEquals(1f, WidgetWords.barStep(0.9996f), 0f)
        assertEquals(0f, WidgetWords.barStep(Float.NaN), 0f)
        // Two days of seconds: the next second is the same step, so the bar is left alone.
        val day = 86_400L
        val steps = (0L..600L).map {
            WidgetWords.barStep(
                WidgetWords.countdownFraction(
                    0,
                    2 * day,
                    day + it
                )!!
            )
        }.distinct()
        assertEquals(
            "ten minutes of a two-day countdown move the bar a few times, not 600",
            true,
            steps.size in 2..5
        )
        // A countdown of two minutes still moves at every second.
        assertEquals(
            121,
            (0L..120L).map { WidgetWords.barStep(WidgetWords.countdownFraction(0, 120, it)!!) }
                .distinct().size
        )
    }

    @Test
    fun aChartsScaleComesFromTheDataUnlessTheScriptGivesIt() {
        val points = listOf(3f, 12f, 9f)
        assertEquals(
            "a line: from its lowest to its highest",
            3f to 12f,
            WidgetWords.scale(points, null, null, bars = false)
        )
        assertEquals(
            "bars stand on zero",
            0f to 12f,
            WidgetWords.scale(points, null, null, bars = true)
        )
        assertEquals(
            "unless a value is below it",
            -2f to 5f,
            WidgetWords.scale(listOf(-2f, 5f), null, null, bars = true)
        )
        assertEquals(
            "the script's scale wins",
            0f to 20f,
            WidgetWords.scale(points, 0f, 20f, bars = false)
        )
        assertEquals(5f to 12f, WidgetWords.scale(points, 5f, null, bars = true))
    }

    @Test
    fun aChartsScaleAlwaysHasSomeHeight() {
        assertEquals(
            "a flat line sits in the middle",
            4.5f to 5.5f,
            WidgetWords.scale(listOf(5f, 5f, 5f), null, null, bars = false)
        )
        assertEquals(8.5f to 9.5f, WidgetWords.scale(listOf(9f), null, null, bars = false))
        assertEquals(
            "bars of nothing",
            0f to 1f,
            WidgetWords.scale(listOf(0f, 0f), null, null, bars = true)
        )
        assertEquals(
            "a maximum under the data",
            5f to 6f,
            WidgetWords.scale(listOf(5f, 9f), null, 3f, bars = false)
        )
        assertEquals(
            "no points",
            -0.5f to 0.5f,
            WidgetWords.scale(emptyList(), null, null, bars = false)
        )
        assertEquals(
            "a value that is no number is left out",
            1f to 2f,
            WidgetWords.scale(listOf(1f, Float.NaN, 2f), null, null, bars = false)
        )
    }

    // ---- a list's ranges -------------------------------------------------------------------

    private val days =
        listOf(WidgetSpec.Range(22f, 31f), WidgetSpec.Range(21f, 30f), WidgetSpec.Range(20f, 27f))

    /** One scale for the rows shown: from the lowest low to the highest high. Each range sits on it from 0 to 1. */
    @Test
    fun rangesShareOneScale() {
        val scale = WidgetWords.rangeScale(days)!!
        assertEquals(20f to 31f, scale)
        val sat = WidgetWords.rangePlace(days[0], scale)
        assertEquals(2f / 11, sat.start, 1e-6f)
        assertEquals(1f, sat.end, 1e-6f)
        val mon = WidgetWords.rangePlace(days[2], scale)
        assertEquals(0f, mon.start, 1e-6f)
        assertEquals(7f / 11, mon.end, 1e-6f)
        assertNull("no value, no dot", mon.value)
        // A row without a range has no say in the scale, and rows without any have none.
        assertEquals(20f to 31f, WidgetWords.rangeScale(days + null))
        assertNull(WidgetWords.rangeScale(listOf(null, null)))
        assertNull(WidgetWords.rangeScale(emptyList()))
    }

    /** A range of one number sits at its place, as a dot. A scale of one number puts everything in the middle. */
    @Test
    fun aRangeOfOneNumber() {
        val scale =
            WidgetWords.rangeScale(listOf(WidgetSpec.Range(20f, 30f), WidgetSpec.Range(25f, 25f)))!!
        val place = WidgetWords.rangePlace(WidgetSpec.Range(25f, 25f), scale)
        assertEquals(0.5f, place.start, 1e-6f)
        assertEquals(0.5f, place.end, 1e-6f)
        val alone = WidgetSpec.Range(25f, 25f, 25f)
        assertEquals(
            WidgetWords.RangePlace(0.5f, 0.5f, 0.5f),
            WidgetWords.rangePlace(alone, WidgetWords.rangeScale(listOf(alone))!!)
        )
    }

    /** A single row is its own scale: its bar is full. */
    @Test
    fun aSingleRowFillsItsTrack() {
        val price = WidgetSpec.Range(94_100f, 97_900f)
        assertEquals(
            WidgetWords.RangePlace(0f, 1f, null),
            WidgetWords.rangePlace(price, WidgetWords.rangeScale(listOf(price))!!)
        )
    }

    /** A value is a dot at its place on the scale. */
    @Test
    fun aValueIsADotOnTheScale() {
        val price = WidgetSpec.Range(94_100f, 97_900f, 97_420f)
        val place = WidgetWords.rangePlace(price, WidgetWords.rangeScale(listOf(price))!!)
        assertEquals(3_320f / 3_800, place.value!!, 1e-4f)
        // On a list's scale, the value sits where its number is: today is at 27°, on 20° to 31°.
        val today = WidgetSpec.Range(22f, 31f, 27f)
        assertEquals(
            7f / 11,
            WidgetWords.rangePlace(today, WidgetWords.rangeScale(days.drop(1) + today)!!).value!!,
            1e-6f
        )
        // Numbers as large as a float holds: the scale's length is counted in doubles.
        val huge = WidgetSpec.Range(-3e38f, 3e38f, 0f)
        assertEquals(
            WidgetWords.RangePlace(0f, 1f, 0.5f),
            WidgetWords.rangePlace(huge, WidgetWords.rangeScale(listOf(huge))!!)
        )
    }

    /** The bars take what the words and the numbers leave, up to half the row. The icons go first; then the bars. */
    @Test
    fun aBarFitsOrTheRowHasNone() {
        // A 2×2 forecast: no room with the icons, room without them.
        assertEquals(
            WidgetWords.RangeFit(57f, marks = false),
            WidgetWords.rangeFit(145f, 29f, 47f, 28f)
        )
        // A 4×2 one: room for both, and the bar takes half the row.
        assertEquals(
            WidgetWords.RangeFit(173.5f, marks = true),
            WidgetWords.rangeFit(347f, 41f, 47f, 28f)
        )
        // Too narrow even without the icons: no bars, and the icons stay.
        assertNull(WidgetWords.rangeFit(100f, 29f, 47f, 28f))
        // Without trailing texts, one gap.
        assertEquals(
            WidgetWords.RangeFit(100f, marks = true),
            WidgetWords.rangeFit(200f, 50f, 0f, 0f)
        )
        // A bar is never shorter than RANGE_MIN_DP: half of a row 94 dp wide is too short.
        assertEquals(WidgetWords.RangeFit(48f, marks = true), WidgetWords.rangeFit(96f, 0f, 0f, 0f))
        assertNull(WidgetWords.rangeFit(94f, 0f, 0f, 0f))
    }

    /** A row says its range only when it has no trailing text: there, the numbers are said already. */
    @Test
    fun aRangeHasWordsWhereTheRowHasNoNumbers() {
        assertEquals("from 22 to 31", WidgetWords.rangeWords(WidgetSpec.Range(22f, 31f)))
        assertEquals("27, from 22 to 31", WidgetWords.rangeWords(WidgetSpec.Range(22f, 31f, 27f)))
        assertEquals("from -3.5 to 4", WidgetWords.rangeWords(WidgetSpec.Range(-3.5f, 4f)))
        val now = at(day = 20, hour = 0)
        val rows = WidgetSpec.Rows(
            base,
            items = listOf(
                WidgetSpec.Row("Sat", trailing = "22–31°", range = WidgetSpec.Range(22f, 31f)),
                WidgetSpec.Row("BTC", range = WidgetSpec.Range(94_100f, 97_900f, 97_420f)),
                WidgetSpec.Row(
                    "Pune",
                    trailing = "22–31°",
                    range = WidgetSpec.Range(22f, 31f, 27f)
                ),
            ),
        )
        assertEquals(
            "Prices: Sat, 22–31°, BTC, 97420, from 94100 to 97900, Pune, 22–31°, 27, from 22 to 31",
            WidgetWords.description("Prices", rows, now, utc)
        )
        assertNull(WidgetWords.rowRange(rows.items[0]))
        assertEquals("97420, from 94100 to 97900", WidgetWords.rowRange(rows.items[1]))
        // A value is shown only by its dot, so it is said even beside the numbers.
        assertEquals("27, from 22 to 31", WidgetWords.rowRange(rows.items[2]))
    }

    @Test
    fun aCardIsOneSentenceForAScreenReader() {
        val now = at(day = 20, hour = 0)
        val weather = WidgetSpec.Stat(
            base,
            value = "24",
            unit = "°C",
            label = "Sunny",
            delta = "+2",
            trend = "up",
            more = listOf(WidgetSpec.Small("68%", "humidity"), WidgetSpec.Small("12 km/h", null)),
            progress = 0.5f,
        )
        assertEquals(
            "Weather: 24°C, Sunny, up +2, 68% humidity, 12 km/h, 50%",
            WidgetWords.description("Weather", weather, now, utc)
        )
        assertEquals(
            "no title, no colon",
            "24",
            WidgetWords.description(null, WidgetSpec.Stat(base, value = "24"), now, utc)
        )
        assertEquals(
            "Quote: Be kind., Anon",
            WidgetWords.description(
                "Quote",
                WidgetSpec.Text(base, text = "Be kind.", caption = "Anon"),
                now,
                utc
            )
        )
        // A picture is said by its words. They are the same whether it has loaded or not.
        val picture = WidgetSpec.Image(
            base,
            image = "https://example.com/a.png",
            alt = "A graph of sleep",
            caption = "Monday"
        )
        assertEquals(
            "Comic: A graph of sleep, Monday",
            WidgetWords.description("Comic", picture, now, utc)
        )
        assertEquals(
            "A graph of sleep",
            WidgetWords.description(null, picture.copy(caption = null), now, utc)
        )
        assertFalse(WidgetWords.timed(picture))
    }

    /** A few fields may be styled. A screen reader hears their plain words: no mark is said or shown as a box. */
    @Test
    fun aScreenReaderHearsStyledTextPlain() {
        val now = at(day = 20, hour = 0)
        fun bold(s: String) = StyledText.wrap(StyledText.Style.BOLD, s)
        fun good(s: String) = StyledText.color(StyledText.ColorRole.GOOD, s)
        val quote = WidgetSpec.Text(base, text = "Be " + bold("kind") + ".", caption = good("Anon"))
        assertEquals("Quote: Be kind., Anon", WidgetWords.description("Quote", quote, now, utc))
        val rows = WidgetSpec.Rows(
            base,
            items = listOf(
                WidgetSpec.Row(
                    bold("BTC"),
                    detail = good("▲ 1.2%"),
                    trailing = bold("67,120")
                ), WidgetSpec.Row("ETH", done = true)
            )
        )
        assertEquals(
            "Prices: BTC, ▲ 1.2%, 67,120, ETH, done",
            WidgetWords.description("Prices", rows, now, utc)
        )
        assertEquals(
            "24, Sunny",
            WidgetWords.description(
                null,
                WidgetSpec.Stat(base, value = "24", label = good("Sunny")),
                now,
                utc
            )
        )
        val picture = WidgetSpec.Image(
            base,
            image = "https://example.com/a.png",
            alt = "A graph",
            caption = bold("Monday")
        )
        assertEquals("A graph, Monday", WidgetWords.description(null, picture, now, utc))
        // No description has a mark in it.
        listOf(
            quote,
            rows,
            picture
        ).forEach { assertFalse(StyledText.has(WidgetWords.description("T", it, now, utc))) }
    }

    @Test
    fun aListSaysEveryRowAlsoThoseThatDoNotFit() {
        val now = at(day = 20, hour = 0)
        val rows = WidgetSpec.Rows(
            base,
            items = listOf(
                WidgetSpec.Row("Milk", detail = "2 liters", trailing = "today"),
                WidgetSpec.Row("Bread")
            )
        )
        assertEquals(
            "Shopping: Milk, 2 liters, today, Bread",
            WidgetWords.description("Shopping", rows, now, utc)
        )
        assertEquals(
            "Shopping: Nothing new",
            WidgetWords.description(
                "Shopping",
                WidgetSpec.Rows(base, items = emptyList(), empty = "Nothing new"),
                now,
                utc
            )
        )
    }

    @Test
    fun progressChartAndCountdownHaveWords() {
        val now = at(day = 20, hour = 0)
        assertEquals(
            "Water: 5 / 8, glasses",
            WidgetWords.description(
                "Water",
                WidgetSpec.Progress(base, value = 5f, max = 8f, text = "5 / 8", label = "glasses"),
                now,
                utc
            )
        )
        val bars = WidgetSpec.Progress(
            base,
            value = 0f,
            items = listOf(
                WidgetSpec.Bar("Disk", 3f, 4f, null, null),
                WidgetSpec.Bar("Memory", 1f, 2f, "1 of 2 GB", null)
            )
        )
        assertEquals(
            "Phone: Disk 75%, Memory 1 of 2 GB",
            WidgetWords.description("Phone", bars, now, utc)
        )
        val chart =
            WidgetSpec.Chart(base, points = listOf(3f, 12f, 9f), value = "9", label = "steps")
        assertEquals(
            "Steps: 9, steps, 3 values, from 3 to 12, the latest 9",
            WidgetWords.description("Steps", chart, now, utc)
        )
        assertEquals(
            "the script's own summary",
            "Steps: 9, steps, Rising",
            WidgetWords.description("Steps", chart.copy(summary = "Rising"), now, utc)
        )
        // Five weeks of a habit as dots, done today: the days done, after the note, the streak and its label.
        val days = List(34) { if (it % 5 == 0) 0f else 1f } + 1f
        val habit = WidgetSpec.Chart(
            base.copy(note = "Done today"),
            points = days,
            style = "dots",
            value = "5",
            label = "Days in a row"
        )
        assertEquals(
            "Exercise, Done today: 5, Days in a row, Done on 28 of the last 35 days",
            WidgetWords.description("Exercise", habit, now, utc)
        )
        assertEquals(
            "Exercise, Done today: 5, Days in a row, Rising",
            WidgetWords.description("Exercise", habit.copy(summary = "Rising"), now, utc)
        )
        // The same points as bars are values.
        assertEquals(
            "Exercise, Done today: 5, Days in a row, 35 values, from 0 to 1, the latest 1",
            WidgetWords.description("Exercise", habit.copy(style = "bars"), now, utc)
        )
        // A countdown that draws a clock is still said in words: a running clock is noise.
        val trip = WidgetSpec.Countdown(base, to = now + 3 * 3600L + 20, label = "Goa trip")
        assertEquals("Trip: 3 hours, Goa trip", WidgetWords.description("Trip", trip, now, utc))
    }

    private val go = WidgetTap(args = "go")

    /** A row with a checkbox says its state. A row without one says nothing about it. */
    @Test
    fun aRowWithACheckboxSaysWhetherItIsDone() {
        val now = at(day = 20, hour = 0)
        val todos = WidgetSpec.Rows(
            base,
            items = listOf(
                WidgetSpec.Row("Buy milk", done = false),
                WidgetSpec.Row("Call home", detail = "after 6", done = true),
                WidgetSpec.Row("Plain")
            )
        )
        assertEquals(
            "Todos: Buy milk, not done, Call home, after 6, done, Plain",
            WidgetWords.description("Todos", todos, now, utc)
        )
        assertEquals("done", WidgetWords.doneWord(true))
        assertEquals("not done", WidgetWords.doneWord(false))
        assertNull(WidgetWords.doneWord(null))
    }

    /** The card names its buttons and its switches. Each of them is an item of its own for a screen reader too. */
    @Test
    fun buttonsTogglesAndMediaHaveWords() {
        val now = at(day = 20, hour = 0)
        val buttons = WidgetSpec.Buttons(
            base,
            items = listOf(WidgetSpec.Button("Torch", go), WidgetSpec.Button("Timer", go))
        )
        assertEquals(
            "Shortcuts: Torch, Timer",
            WidgetWords.description("Shortcuts", buttons, now, utc)
        )
        val toggles = WidgetSpec.Toggles(
            base,
            items = listOf(
                WidgetSpec.Toggle("Do Not Disturb", on = true, tap = go),
                WidgetSpec.Toggle("Work", on = false, tap = go)
            )
        )
        assertEquals(
            "Quick toggles: Do Not Disturb on, Work off",
            WidgetWords.description("Quick toggles", toggles, now, utc)
        )
        val media = WidgetSpec.Media(
            base,
            title = "Song",
            artist = "Singer",
            app = "Player",
            playing = true,
            position = 0,
            positionAt = 0,
            speed = 1f,
            duration = 0
        )
        assertEquals(
            "Now playing: Song, Singer, Player, playing",
            WidgetWords.description("Now playing", media, now, utc)
        )
        assertEquals(
            "Now playing: Player, paused",
            WidgetWords.description(
                "Now playing",
                media.copy(title = null, artist = null, playing = false),
                now,
                utc
            )
        )
        // With nothing playing, the last player's track.
        val last = media.copy(playing = false, lastPlayed = true)
        assertEquals(
            "Now playing: Song, Singer, Player, last played",
            WidgetWords.description("Now playing", last, now, utc)
        )
        assertEquals(
            "Now playing: Player, last played",
            WidgetWords.description("Now playing", last.copy(title = null, artist = null), now, utc)
        )
    }

    /** The design's numbers: 2 buttons at 2×1, 4 at 2×2 and at 4×1, 8 at 4×2. The boxes are those of a page 344 dp wide. */
    @Test
    fun aGridShowsFewerButtonsNeverSmallerOnes() {
        // 2×1: the body is 136 by 48 dp.
        assertEquals(
            WidgetWords.Grid(columns = 2, rows = 1, shown = 2),
            WidgetWords.grid(8, 136, 48, 4)
        )
        // 2×2: 136 by 124 dp.
        assertEquals(
            WidgetWords.Grid(columns = 2, rows = 2, shown = 4),
            WidgetWords.grid(8, 136, 124, 4)
        )
        // 4×1: 312 by 48 dp. Five would fit across, but a row has four at most.
        assertEquals(
            WidgetWords.Grid(columns = 4, rows = 1, shown = 4),
            WidgetWords.grid(8, 312, 48, 4)
        )
        // 4×2: 312 by 124 dp.
        assertEquals(
            WidgetWords.Grid(columns = 4, rows = 2, shown = 8),
            WidgetWords.grid(8, 312, 124, 4)
        )
        // 1×1: one button.
        assertEquals(
            WidgetWords.Grid(columns = 1, rows = 1, shown = 1),
            WidgetWords.grid(8, 64, 72, 4)
        )
        // Every cell is at least 48 dp wide, with 8 dp between cells.
        for (width in 1..400) {
            val grid = WidgetWords.grid(8, width, 48, 6)
            if (grid.columns > 1) assertTrue(
                "$width: $grid",
                (width - 8 * (grid.columns - 1)) / grid.columns >= 48
            )
        }
    }

    /** Few buttons get wide cells, and two rows are spread evenly. */
    @Test
    fun aGridSpreadsItsButtonsEvenly() {
        assertEquals(
            WidgetWords.Grid(columns = 3, rows = 1, shown = 3),
            WidgetWords.grid(3, 312, 124, 4)
        )
        assertEquals(
            WidgetWords.Grid(columns = 3, rows = 2, shown = 5),
            WidgetWords.grid(5, 312, 124, 4)
        )
        assertEquals(
            WidgetWords.Grid(columns = 3, rows = 2, shown = 6),
            WidgetWords.grid(6, 312, 124, 4)
        )
        assertEquals(
            WidgetWords.Grid(columns = 4, rows = 2, shown = 7),
            WidgetWords.grid(7, 312, 124, 4)
        )
        assertEquals(
            WidgetWords.Grid(columns = 1, rows = 1, shown = 1),
            WidgetWords.grid(1, 312, 124, 4)
        )
        // Switches: up to six in a row when the box is wide enough.
        assertEquals(
            WidgetWords.Grid(columns = 5, rows = 1, shown = 5),
            WidgetWords.grid(6, 312, 48, 6)
        )
        assertEquals(
            WidgetWords.Grid(columns = 6, rows = 1, shown = 6),
            WidgetWords.grid(6, 328, 48, 6)
        )
        assertEquals(
            WidgetWords.Grid(columns = 3, rows = 2, shown = 6),
            WidgetWords.grid(6, 312, 124, 6)
        )
        // A box too small for one still gets one: a button is never left out altogether.
        assertEquals(
            WidgetWords.Grid(columns = 1, rows = 1, shown = 1),
            WidgetWords.grid(4, 20, 20, 4)
        )
        assertEquals(WidgetWords.Grid(0, 0, 0), WidgetWords.grid(0, 312, 124, 4))
    }

    private val song = WidgetSpec.Media(
        base,
        title = "Song",
        artist = "Singer",
        app = "Player",
        playing = true,
        position = 60_000,
        positionAt = 1_000_000,
        speed = 1f,
        duration = 180_000
    )

    /** The position moves only while playing, at the player's speed, and stays inside the track. */
    @Test
    fun aMediaPositionMovesOnlyWhilePlaying() {
        assertEquals(60_000, WidgetWords.mediaPosition(song, 1_000_000))
        assertEquals(75_000, WidgetWords.mediaPosition(song, 1_015_000))
        assertEquals(
            "at double speed",
            90_000,
            WidgetWords.mediaPosition(song.copy(speed = 2f), 1_015_000)
        )
        assertEquals(
            "paused",
            60_000,
            WidgetWords.mediaPosition(song.copy(playing = false), 1_015_000)
        )
        assertEquals("never past the end", 180_000, WidgetWords.mediaPosition(song, 9_000_000))
        // The two clocks are read at different moments, so "now" can be just before the position's time.
        assertEquals(60_000, WidgetWords.mediaPosition(song, 999_000))
        assertEquals(
            "never before the start",
            0,
            WidgetWords.mediaPosition(song.copy(position = -5_000, playing = false), 1_000_000)
        )
        assertEquals(
            "no length known",
            9_060_000,
            WidgetWords.mediaPosition(song.copy(duration = 0), 10_000_000)
        )
        // A speed that is no number counts as 1, and a huge one does not wrap around.
        assertEquals(75_000, WidgetWords.mediaPosition(song.copy(speed = Float.NaN), 1_015_000))
        assertEquals(
            180_000,
            WidgetWords.mediaPosition(song.copy(speed = Float.MAX_VALUE), 1_015_000)
        )
    }

    @Test
    fun aMediaBarRunsFromTheStartToTheEndOfTheTrack() {
        assertEquals(1f / 3, WidgetWords.mediaFraction(song, 1_000_000)!!, 1e-6f)
        assertEquals(0.5f, WidgetWords.mediaFraction(song, 1_030_000)!!, 1e-6f)
        assertEquals(1f, WidgetWords.mediaFraction(song, 9_000_000)!!, 0f)
        assertNull(
            "no length, no bar",
            WidgetWords.mediaFraction(song.copy(duration = 0), 1_000_000)
        )
        assertNull(WidgetWords.mediaFraction(song.copy(duration = -1), 1_000_000))
    }

    @Test
    fun aMediaTimeIsWrittenAsPlayersWriteIt() {
        assertEquals("0:00", WidgetWords.mediaTime(0))
        assertEquals("0:07", WidgetWords.mediaTime(7_900))
        assertEquals("3:45", WidgetWords.mediaTime(225_000))
        assertEquals("59:59", WidgetWords.mediaTime(3_599_000))
        assertEquals("1:02:03", WidgetWords.mediaTime(3_723_000))
        assertEquals("0:00", WidgetWords.mediaTime(-5_000))
    }

    /** The app's name is the big line when the player gives no title, and then it is not said twice. */
    @Test
    fun aMediaCardNamesTheTrackOrElseTheApp() {
        assertEquals("Song", WidgetWords.mediaTitle(song))
        assertEquals("Singer · Player", WidgetWords.mediaLine(song))
        assertEquals("Player", WidgetWords.mediaLine(song.copy(artist = null)))
        assertEquals("Player", WidgetWords.mediaTitle(song.copy(title = null)))
        assertEquals("Singer", WidgetWords.mediaLine(song.copy(title = " ")))
        assertEquals("", WidgetWords.mediaLine(song.copy(title = null, artist = null)))
    }

    /** A narrow card has fewer media buttons, never smaller ones. Play or pause is always there. */
    @Test
    fun aNarrowMediaCardHasFewerButtons() {
        // Beside the texts: 4×1 has all three. 2×1 has play or pause alone.
        assertEquals(3, WidgetWords.mediaButtons(312, besideText = true))
        assertEquals(3, WidgetWords.mediaButtons(260, besideText = true))
        assertEquals(1, WidgetWords.mediaButtons(136, besideText = true))
        assertEquals(2, WidgetWords.mediaButtons(204, besideText = true))
        // In a row of their own: 4×2 has three, 2×2 has two.
        assertEquals(3, WidgetWords.mediaButtons(312, besideText = false))
        assertEquals(2, WidgetWords.mediaButtons(136, besideText = false))
        assertEquals(1, WidgetWords.mediaButtons(64, besideText = false))
        assertEquals(1, WidgetWords.mediaButtons(0, besideText = true))
        // Play or pause is 16 dp wider where there is room, a little wider where there is a little.
        assertEquals(64, WidgetWords.mediaPlayDp(312, besideText = true, count = 3))
        assertEquals(52, WidgetWords.mediaPlayDp(260, besideText = true, count = 3))
        assertEquals(64, WidgetWords.mediaPlayDp(76, besideText = false, count = 1))
        assertEquals(48, WidgetWords.mediaPlayDp(48, besideText = false, count = 1))
        assertEquals(48, WidgetWords.mediaPlayDp(0, besideText = true, count = 1))
        // The words the buttons hand over are the ones the Now playing widget reads.
        assertEquals(
            listOf("prev", "toggle", "next"),
            listOf(WidgetWords.MEDIA_PREV, WidgetWords.MEDIA_TOGGLE, WidgetWords.MEDIA_NEXT)
        )
    }

    /** The bar stands for the whole track: its ends are the start and the end, in whole milliseconds. */
    @Test
    fun aSeekBarsPlaceIsATimeInTheTrack() {
        assertEquals(0L, WidgetWords.seekTarget(0f, 185_000))
        assertEquals(185_000L, WidgetWords.seekTarget(1f, 185_000))
        assertEquals(92_500L, WidgetWords.seekTarget(0.5f, 185_000))
        assertEquals(72_000L, WidgetWords.seekTarget(72f / 185, 185_000))
        // Past either end is the end, and a place that isn't a number is the start.
        assertEquals(185_000L, WidgetWords.seekTarget(1.2f, 185_000))
        assertEquals(0L, WidgetWords.seekTarget(-0.3f, 185_000))
        assertEquals(0L, WidgetWords.seekTarget(Float.NaN, 185_000))
        // No length known: nowhere to go.
        assertEquals(0L, WidgetWords.seekTarget(0.5f, 0))
        assertEquals(0L, WidgetWords.seekTarget(0.5f, -1))
        // A ten-hour book is a place to the second.
        assertEquals(18_000_000.0, WidgetWords.seekTarget(0.5f, 36_000_000).toDouble(), 1_000.0)
        // A screen reader hears the place and the length, as the card writes them.
        assertEquals("1:12 of 3:05", WidgetWords.seekSaid(72f / 185, 185_000))
        assertEquals("0:00 of 3:05", WidgetWords.seekSaid(0f, 185_000))
        assertEquals("5:00:00 of 10:00:00", WidgetWords.seekSaid(0.5f, 36_000_000))
        // On release the bar hands over the place in milliseconds.
        assertEquals("seek 72000", WidgetWords.mediaSeek(72_000))
    }

    /** Only a track the player can seek in, with a known length, has a bar that moves it. */
    @Test
    fun aMediaBarSeeksWhenThePlayerCan() {
        assertFalse(WidgetWords.mediaSeeks(song))
        assertTrue(WidgetWords.mediaSeeks(song.copy(canSeek = true)))
        assertFalse("no length", WidgetWords.mediaSeeks(song.copy(canSeek = true, duration = 0)))
        assertFalse(
            "nothing playing",
            WidgetWords.mediaSeeks(song.copy(canSeek = true, lastPlayed = true))
        )
    }

    private fun event(
        title: String,
        starts: Long,
        ends: Long? = null,
        allDay: Boolean = false,
        where: String? = null
    ) =
        WidgetSpec.Event(title, starts, ends, allDay, where)

    private fun said(days: List<WidgetWords.AgendaDay>) =
        days.map { day -> day.label to day.lines.map { "${it.time} ${it.event.title}" } }

    /** 20 December 2026 is a Sunday. */
    @Test
    fun anAgendaListsItsEventsUnderTodayTomorrowAndDates() {
        val now = at(day = 20, hour = 10)
        val spec = WidgetSpec.Agenda(
            base,
            events = listOf(
                event("Lunch", at(20, 13), at(20, 14)),
                event("Dentist", at(21, 9, 30)),
                event("Trip", at(22, 6), allDay = true),
                event("Standup", at(20, 11)),
                event("Review", at(22, 16)),
                event("Too far", at(23, 9)),
            ),
        )
        assertEquals(
            listOf(
                "Today" to listOf("11:00 Standup", "13:00 Lunch"),
                "Tomorrow" to listOf("09:30 Dentist"),
                // An all-day event comes first in its day, whatever its time.
                "Tue 22 Dec" to listOf("all day Trip", "16:00 Review"),
            ),
            said(WidgetWords.agenda(spec, now, utc, clock24 = true, locale = Locale.US)),
        )
        // The time is written as the setting says.
        assertEquals(
            listOf("11:00 AM Standup", "1:00 PM Lunch"),
            said(WidgetWords.agenda(spec, now, utc, clock24 = false, locale = Locale.US))[0].second
        )
        // `days` says how many days, counted from today.
        assertEquals(
            listOf("Today"),
            WidgetWords.agenda(spec.copy(days = 1), now, utc, true, Locale.US).map { it.label })
        assertEquals(
            listOf("Today", "Tomorrow"),
            WidgetWords.agenda(spec.copy(days = 2), now, utc, true, Locale.US).map { it.label })
        assertEquals(4, WidgetWords.agenda(spec.copy(days = 7), now, utc, true, Locale.US).size)
        // The day is the phone's day: at 10:00 UTC it is 05:00 in New York, and 13:00 UTC is 08:00 there.
        val newYork = ZoneId.of("America/New_York")
        assertEquals(
            "Today" to listOf("06:00 Standup", "08:00 Lunch"),
            said(WidgetWords.agenda(spec, now, newYork, true, Locale.US))[0]
        )
    }

    /** An event that has ended is gone on the next minute's tick, with no run of the script. */
    @Test
    fun anAgendaDropsAnEventWhenItHasEnded() {
        val spec = WidgetSpec.Agenda(
            base,
            events = listOf(
                event("Lunch", at(20, 13), at(20, 14)),
                event("Launch", at(20, 9)),
                event("Holiday", at(20, 0), at(21, 0), allDay = true),
                event("Yesterday", at(19, 15)),
                event("Camp", at(19, 8), at(21, 8)),
            ),
            days = 1,
        )

        fun todayAt(now: Long) = said(
            WidgetWords.agenda(
                spec,
                now,
                utc,
                clock24 = true,
                locale = Locale.US
            )
        ).firstOrNull()?.second
        // An event that goes on says "now". One with no end keeps its time: nobody knows whether it goes on.
        assertEquals(
            listOf("all day Holiday", "now Camp", "09:00 Launch", "13:00 Lunch"),
            todayAt(at(20, 10))
        )
        assertEquals(
            listOf("all day Holiday", "now Camp", "09:00 Launch", "now Lunch"),
            todayAt(at(20, 13, 30))
        )
        assertEquals(
            "at its end it is gone",
            listOf("all day Holiday", "now Camp", "09:00 Launch"),
            todayAt(at(20, 14))
        )
        // After midnight: the all-day event has ended, and the event with no end is over with its day.
        // The one that began two days ago and still goes on is under Today.
        assertEquals(listOf("now Camp"), todayAt(at(21, 0)))
        assertNull("nothing is left", todayAt(at(21, 8)))
        assertEquals(
            emptyList<WidgetWords.AgendaDay>(),
            WidgetWords.agenda(WidgetSpec.Agenda(base, events = emptyList()), at(20, 10), utc, true)
        )
    }

    @Test
    fun anAgendaDrawsTheEventsThatFitAndCountsTheRest() {
        // Headings are 20 dp, events 32 dp, and "+2 more" needs 20 dp.
        assertEquals(2, WidgetWords.agendaShown(listOf(2, 1, 1), 124, 20, 32, 20))
        assertEquals(
            "all of them, with no room kept for the line",
            2,
            WidgetWords.agendaShown(listOf(1, 1), 104, 20, 32, 20)
        )
        assertEquals(
            "the second day's heading needs its event",
            1,
            WidgetWords.agendaShown(listOf(1, 1), 103, 20, 32, 20)
        )
        assertEquals(4, WidgetWords.agendaShown(listOf(2, 1, 1), 400, 20, 32, 20))
        assertEquals(
            "one event, however low the box",
            1,
            WidgetWords.agendaShown(listOf(3), 40, 20, 32, 20)
        )
        assertEquals(0, WidgetWords.agendaShown(emptyList(), 124, 20, 32, 20))
        // A 4x2 card's 136 dp, with each heading inside its day's first 44 dp tap row (no height of
        // its own): two events of today and one of tomorrow, as in the mockup. A heading of 16 dp
        // of its own would leave room for two.
        assertEquals(3, WidgetWords.agendaShown(listOf(2, 1), 136, 0, 44, 0))
        assertEquals(2, WidgetWords.agendaShown(listOf(2, 1), 136, 16, 44, 0))
    }

    @Test
    fun anAgendaIsSaidByDay() {
        val now = at(day = 20, hour = 10)
        val spec = WidgetSpec.Agenda(
            base,
            events = listOf(
                event("Lunch", at(20, 13), at(20, 14), where = "Cafe"),
                event("Dentist", at(21, 9, 30)),
                event("Gone", at(20, 8), at(20, 9))
            )
        )
        assertEquals(
            "Agenda: Today, 13:00 Lunch, Cafe, Tomorrow, 09:30 Dentist",
            WidgetWords.description("Agenda", spec, now, utc, clock24 = true, locale = Locale.US)
        )
        assertEquals(
            "Agenda: Today, 1:00 PM Lunch, Cafe, Tomorrow, 9:30 AM Dentist",
            WidgetWords.description("Agenda", spec, now, utc, clock24 = false, locale = Locale.US)
        )
        // With nothing left to show, it says the script's words, or its own.
        assertEquals(
            "Agenda: Nothing planned",
            WidgetWords.description("Agenda", spec.copy(empty = "Nothing planned"), at(23, 0), utc)
        )
        assertEquals("Agenda: No events", WidgetWords.description("Agenda", spec, at(23, 0), utc))
    }

    private fun clockFormat(clock24: Boolean, seconds: Boolean = false) =
        DateTimeFormatter.ofPattern(WidgetWords.clockPattern(clock24, seconds), Locale.US)

    private val places = listOf(
        WidgetSpec.Zone("Asia/Kolkata", "Pune"),
        WidgetSpec.Zone("America/New_York"),
        WidgetSpec.Zone("UTC"),
        WidgetSpec.Zone("+05:45")
    )

    /**
     * At 20:30:05 UTC on the 20th it is 02:00 on the 21st in Kolkata, and 15:30 on the 20th in New
     * York. Each place has its difference from here in minutes, and the phone's own zone is "here".
     */
    @Test
    fun aClockSaysTheTimeInEachPlaceAndItsDay() {
        val now = at(20, 20, 30, 5) * 1000
        assertEquals(
            listOf(
                WidgetWords.ZoneTime("Pune", "02:00", "tomorrow", "+1", ahead = 330),
                WidgetWords.ZoneTime("New York", "15:30", null, null, ahead = -300),
                WidgetWords.ZoneTime("UTC", "20:30", null, null, ahead = 0, here = true),
                WidgetWords.ZoneTime("+05:45", "02:15", "tomorrow", "+1", ahead = 345),
            ),
            WidgetWords.zoneTimes(places, now, utc, clockFormat(true)),
        )
        // Seen from Tokyo it is the 21st already, so New York and UTC are a day behind.
        val tokyo = WidgetWords.zoneTimes(places, now, ZoneId.of("Asia/Tokyo"), clockFormat(true))
        assertEquals(listOf(null, "yesterday", "yesterday", null), tokyo.map { it.day })
        assertEquals(listOf(null, "−1", "−1", null), tokyo.map { it.dayShort })
        assertEquals(listOf(-210, -840, -540, -195), tokyo.map { it.ahead })
        assertEquals(listOf(false, false, false, false), tokyo.map { it.here })
        // The form of the time is the setting's, and seconds show only when asked for.
        assertEquals(
            listOf("2:00 AM", "3:30 PM", "8:30 PM", "2:15 AM"),
            WidgetWords.zoneTimes(places, now, utc, clockFormat(false)).map { it.time })
        assertEquals(
            listOf("02:00:05", "15:30:05", "20:30:05", "02:15:05"),
            WidgetWords.zoneTimes(places, now, utc, clockFormat(true, seconds = true))
                .map { it.time })
        assertEquals(
            "2:00:05 AM",
            WidgetWords.zoneTimes(places, now, utc, clockFormat(false, seconds = true))[0].time
        )
        // A zone the phone does not know is left out. The parser has refused it already.
        assertEquals(
            listOf("UTC"),
            WidgetWords.zoneTimes(
                listOf(WidgetSpec.Zone("Not/AZone"), WidgetSpec.Zone("UTC")),
                now,
                utc,
                clockFormat(true)
            ).map { it.label })
    }

    @Test
    fun aPlaceWithoutALabelIsNamedByItsZone() {
        assertEquals("Kolkata", WidgetWords.zoneLabel(WidgetSpec.Zone("Asia/Kolkata")))
        assertEquals("New York", WidgetWords.zoneLabel(WidgetSpec.Zone("America/New_York")))
        assertEquals(
            "Buenos Aires",
            WidgetWords.zoneLabel(WidgetSpec.Zone("America/Argentina/Buenos_Aires"))
        )
        assertEquals("UTC", WidgetWords.zoneLabel(WidgetSpec.Zone("UTC")))
        assertEquals("Home", WidgetWords.zoneLabel(WidgetSpec.Zone("Asia/Kolkata", "Home")))
    }

    /** The size of the digits comes from the room and the form of the time, never from the digits themselves. */
    @Test
    fun aClocksDigitsFillTheirRoom() {
        assertEquals(
            listOf("HH:mm", "h:mm a", "HH:mm:ss", "h:mm:ss a"),
            listOf(
                WidgetWords.clockPattern(true, false),
                WidgetWords.clockPattern(false, false),
                WidgetWords.clockPattern(true, true),
                WidgetWords.clockPattern(false, true)
            )
        )
        assertEquals(
            listOf(5, 8, 8, 11),
            listOf(
                WidgetWords.clockChars(true, false),
                WidgetWords.clockChars(false, false),
                WidgetWords.clockChars(true, true),
                WidgetWords.clockChars(false, true)
            )
        )
        assertEquals(20, WidgetWords.clockTextDp(64, 5, 28))
        assertEquals("never over the height's limit", 28, WidgetWords.clockTextDp(300, 5, 28))
        assertEquals("never under 12", 12, WidgetWords.clockTextDp(40, 11, 28))
        assertEquals(12, WidgetWords.clockTextDp(300, 5, 4))
    }

    /** The boxes are those of a page 344 dp wide: 136 dp for 2×1 and 2×2, 312 dp for 4×1. */
    @Test
    fun aClockShowsAsManyPlacesAsFit() {
        assertEquals(2, WidgetWords.clockColumns(4, 136, 5))
        assertEquals(2, WidgetWords.clockColumns(4, 136, 8))
        assertEquals("12-hour time with seconds is long", 1, WidgetWords.clockColumns(4, 136, 11))
        assertEquals(4, WidgetWords.clockColumns(4, 312, 5))
        assertEquals(4, WidgetWords.clockColumns(4, 312, 8))
        assertEquals(3, WidgetWords.clockColumns(4, 312, 11))
        assertEquals("no more columns than places", 2, WidgetWords.clockColumns(2, 312, 5))
        assertEquals(1, WidgetWords.clockColumns(3, 10, 5))
        // A large box has a row for each place, 28 dp at least.
        assertEquals(4, WidgetWords.clockRows(4, 124))
        assertEquals(3, WidgetWords.clockRows(4, 90))
        assertEquals(2, WidgetWords.clockRows(2, 124))
        assertEquals(1, WidgetWords.clockRows(4, 10))
        // Rows that need more room, at a large font size: fewer of them.
        assertEquals(3, WidgetWords.clockRows(4, 118, 2, rowDp = 36))
    }

    /**
     * A screen reader hears each place with its time, without seconds, its day when that differs,
     * and its difference in whole words.
     */
    @Test
    fun aClockIsSaidWithItsPlacesAndTheirDays() {
        val now = at(20, 20, 30, 5)
        val clock = WidgetSpec.Clock(base, zones = places.take(3), seconds = true)
        assertEquals(
            "World: Pune 02:00 tomorrow, 5 hours 30 minutes ahead, New York 15:30, 5 hours behind, UTC 20:30, here",
            WidgetWords.description("World", clock, now, utc, clock24 = true, locale = Locale.US),
        )
        assertEquals(
            "World: Pune 2:00 AM tomorrow, 5 hours 30 minutes ahead, New York 3:30 PM, 5 hours behind, UTC 8:30 PM, here",
            WidgetWords.description("World", clock, now, utc, clock24 = false, locale = Locale.US),
        )
    }

    /** At 08:00 on 20 November in Kolkata: 02:30 in London, 21:30 the day before in New York, 08:15 in Kathmandu. */
    private val kolkataMorning =
        ZonedDateTime.of(2026, 11, 20, 8, 0, 0, 0, ZoneId.of("Asia/Kolkata")).toInstant()
            .toEpochMilli()

    private fun seenFrom(here: String, now: Long, vararg zones: String) =
        WidgetWords.zoneTimes(
            zones.map { WidgetSpec.Zone(it) },
            now,
            ZoneId.of(here),
            clockFormat(true)
        )

    /** Under a place, its difference from the phone's time in hours and minutes, in each of its forms. */
    @Test
    fun aPlaceSaysHowFarAheadOrBehindItIs() {
        assertEquals("5 h 30 min behind", WidgetWords.difference(-330, here = false))
        assertEquals("3 h ahead", WidgetWords.difference(180, here = false))
        assertEquals("45 min ahead", WidgetWords.difference(45, here = false))
        assertEquals("13 h 45 min ahead", WidgetWords.difference(825, here = false))
        assertEquals("Same time", WidgetWords.difference(0, here = false))
        assertEquals("Here", WidgetWords.difference(0, here = true))
        // In little room: hours and minutes with a sign, a real minus.
        assertEquals("−5:30", WidgetWords.differenceShort(-330, here = false))
        assertEquals("+3:00", WidgetWords.differenceShort(180, here = false))
        assertEquals("+0:45", WidgetWords.differenceShort(45, here = false))
        assertEquals("−10:30", WidgetWords.differenceShort(-630, here = false))
        assertEquals("±0:00", WidgetWords.differenceShort(0, here = false))
        assertEquals("Here", WidgetWords.differenceShort(0, here = true))
        // A screen reader hears whole words.
        assertEquals("5 hours 30 minutes behind", WidgetWords.differenceSaid(-330, here = false))
        assertEquals("1 hour ahead", WidgetWords.differenceSaid(60, here = false))
        assertEquals("1 hour 1 minute ahead", WidgetWords.differenceSaid(61, here = false))
        assertEquals("45 minutes behind", WidgetWords.differenceSaid(-45, here = false))
        assertEquals("same time", WidgetWords.differenceSaid(0, here = false))
        assertEquals("here", WidgetWords.differenceSaid(0, here = true))
    }

    /** Half-hour and 45-minute zones, a place on another day, the phone's own zone, and one that keeps its time. */
    @Test
    fun aPlacesLinesGoFromTheLongestToTheShortest() {
        val (kolkata, london, newYork, kathmandu, colombo) =
            seenFrom(
                "Asia/Kolkata",
                kolkataMorning,
                "Asia/Kolkata",
                "Europe/London",
                "America/New_York",
                "Asia/Kathmandu",
                "Asia/Colombo"
            )
        assertEquals(listOf("Here"), WidgetWords.placeLines(kolkata))
        assertEquals(listOf("5 h 30 min behind", "−5:30"), WidgetWords.placeLines(london))
        assertEquals(
            listOf("10 h 30 min behind · Yesterday", "−10:30 · Yesterday", "−10:30 −1"),
            WidgetWords.placeLines(newYork)
        )
        assertEquals(
            "Nepal is 15 minutes ahead of India",
            listOf("15 min ahead", "+0:15"),
            WidgetWords.placeLines(kathmandu)
        )
        assertEquals(
            "another zone with the same time",
            listOf("Same time", "±0:00"),
            WidgetWords.placeLines(colombo)
        )
        // The other way round: Kolkata is ahead of New York, and on the next day.
        assertEquals(
            listOf("10 h 30 min ahead · Tomorrow", "+10:30 · Tomorrow", "+10:30 +1"),
            WidgetWords.placeLines(seenFrom("America/New_York", kolkataMorning, "Asia/Kolkata")[0])
        )
    }

    /** "Here" is the phone's own zone, or the same fixed offset by another name; another zone with the same time is not. */
    @Test
    fun hereIsThePhonesOwnZone() {
        val now = kolkataMorning
        assertEquals(
            listOf(true, true, true, false),
            seenFrom("UTC", now, "UTC", "GMT", "Etc/UTC", "Europe/London").map { it.here })
        assertEquals(
            "London keeps UTC's time in November",
            0,
            seenFrom("UTC", now, "Europe/London")[0].ahead
        )
        assertEquals(
            listOf(true, false),
            seenFrom("Asia/Kolkata", now, "Asia/Kolkata", "Asia/Colombo").map { it.here })
    }

    /** The difference is the two clocks' at that moment, so it follows summer time in both places. */
    @Test
    fun aDifferenceFollowsSummerTime() {
        fun utcAt(text: String) = java.time.Instant.parse(text).toEpochMilli()

        // New York starts summer time on 8 March, London on 29 March: for three weeks they are 4 hours apart.
        fun newYorkFromLondon(text: String) =
            seenFrom("Europe/London", utcAt(text), "America/New_York")[0].ahead
        assertEquals(-300, newYorkFromLondon("2026-03-01T12:00:00Z"))
        assertEquals(-240, newYorkFromLondon("2026-03-15T12:00:00Z"))
        assertEquals(-300, newYorkFromLondon("2026-04-01T12:00:00Z"))
        // At the minute New York's clocks go forward.
        assertEquals(
            300,
            seenFrom("America/New_York", utcAt("2026-03-08T06:59:00Z"), "Europe/London")[0].ahead
        )
        assertEquals(
            240,
            seenFrom("America/New_York", utcAt("2026-03-08T07:00:00Z"), "Europe/London")[0].ahead
        )
        // Lord Howe Island moves its clocks by half an hour: the same time as Sydney in January, 30 minutes ahead in July.
        assertEquals(
            listOf("Same time", "±0:00"),
            WidgetWords.placeLines(
                seenFrom(
                    "Australia/Sydney",
                    utcAt("2026-01-15T00:00:00Z"),
                    "Australia/Lord_Howe"
                )[0]
            )
        )
        assertEquals(
            listOf("30 min ahead", "+0:30"),
            WidgetWords.placeLines(
                seenFrom(
                    "Australia/Sydney",
                    utcAt("2026-07-15T00:00:00Z"),
                    "Australia/Lord_Howe"
                )[0]
            )
        )
        // The Chatham Islands in their summer: 13 hours 45 minutes ahead of UTC, on the next day at noon.
        assertEquals(
            listOf("13 h 45 min ahead · Tomorrow", "+13:45 · Tomorrow", "+13:45 +1"),
            WidgetWords.placeLines(
                seenFrom(
                    "UTC",
                    utcAt("2026-01-15T12:00:00Z"),
                    "Pacific/Chatham"
                )[0]
            )
        )
    }

    /** The parts of a day by fixed hours: night before 06:00 and from 22:00, working hours from 09:00 to 18:00. */
    @Test
    fun aDayHasFixedParts() {
        fun part(hour: Int, minute: Int = 0) = WidgetWords.dayPart(hour * 60 + minute)
        assertEquals(WidgetWords.DayPart.NIGHT, part(0))
        assertEquals(WidgetWords.DayPart.NIGHT, part(5, 59))
        assertEquals(WidgetWords.DayPart.DAY, part(6))
        assertEquals(WidgetWords.DayPart.DAY, part(8, 59))
        assertEquals(WidgetWords.DayPart.WORK, part(9))
        assertEquals(WidgetWords.DayPart.WORK, part(17, 59))
        assertEquals(WidgetWords.DayPart.DAY, part(18))
        assertEquals(WidgetWords.DayPart.DAY, part(21, 59))
        assertEquals(WidgetWords.DayPart.NIGHT, part(22))
        assertEquals(WidgetWords.DayPart.NIGHT, part(23, 59))
    }

    private fun stretch(from: Int, to: Int, part: WidgetWords.DayPart) =
        WidgetWords.Stretch(from / 96f, to / 96f, part)

    /**
     * A day strip is the phone's day from 00 to 24, in quarter hours, shaded by the time at the
     * place. Kathmandu is 5:45 ahead of UTC, so its parts change at a quarter past.
     */
    @Test
    fun aDayStripFollowsThePlacesTimeOfDay() {
        val day = java.time.LocalDate.of(2026, 11, 20)
        val night = WidgetWords.DayPart.NIGHT
        val light = WidgetWords.DayPart.DAY
        val work = WidgetWords.DayPart.WORK
        assertEquals(
            listOf(
                stretch(0, 24, night),
                stretch(24, 36, light),
                stretch(36, 72, work),
                stretch(72, 88, light),
                stretch(88, 96, night)
            ),
            WidgetWords.dayStrip(day, utc, utc),
        )
        assertEquals(
            listOf(
                stretch(0, 1, night),
                stretch(1, 13, light),
                stretch(13, 49, work),
                stretch(49, 65, light),
                stretch(65, 96, night)
            ),
            WidgetWords.dayStrip(day, utc, ZoneId.of("Asia/Kathmandu")),
        )
        // New York seen from Kolkata, 10:30 behind: its working day is the evening and the night here.
        assertEquals(
            listOf(
                stretch(0, 18, work),
                stretch(18, 34, light),
                stretch(34, 66, night),
                stretch(66, 78, light),
                stretch(78, 96, work)
            ),
            WidgetWords.dayStrip(day, ZoneId.of("Asia/Kolkata"), ZoneId.of("America/New_York")),
        )
    }

    /** On the day the clocks go forward here, the strip goes by the clock: the place's hours come an hour later on it. */
    @Test
    fun aDayStripFollowsSummerTimeHere() {
        val forward = java.time.LocalDate.of(2026, 3, 29)
        assertEquals(
            listOf(
                stretch(0, 28, WidgetWords.DayPart.NIGHT),
                stretch(28, 40, WidgetWords.DayPart.DAY),
                stretch(40, 76, WidgetWords.DayPart.WORK),
                stretch(76, 92, WidgetWords.DayPart.DAY),
                stretch(92, 96, WidgetWords.DayPart.NIGHT)
            ),
            WidgetWords.dayStrip(forward, ZoneId.of("Europe/London"), utc),
        )
    }

    /** A tap on a strip picks the nearest hour of the day, from 0 to 24. A right-to-left page runs from the right. */
    @Test
    fun aTapOnAStripPicksTheNearestHour() {
        assertEquals(0, WidgetWords.hourAt(0f, 240f, rtl = false))
        assertEquals(12, WidgetWords.hourAt(120f, 240f, rtl = false))
        assertEquals(24, WidgetWords.hourAt(240f, 240f, rtl = false))
        assertEquals("2.4 hours in", 2, WidgetWords.hourAt(24f, 240f, rtl = false))
        assertEquals("2.6 hours in", 3, WidgetWords.hourAt(26f, 240f, rtl = false))
        assertEquals("15.2 hours in", 15, WidgetWords.hourAt(152f, 240f, rtl = false))
        assertEquals("outside the strip", 0, WidgetWords.hourAt(-10f, 240f, rtl = false))
        assertEquals("outside the strip", 24, WidgetWords.hourAt(300f, 240f, rtl = false))
        assertEquals(24, WidgetWords.hourAt(0f, 240f, rtl = true))
        assertEquals(0, WidgetWords.hourAt(240f, 240f, rtl = true))
        assertEquals(9, WidgetWords.hourAt(150f, 240f, rtl = true))
        assertEquals("no strip", 0, WidgetWords.hourAt(10f, 0f, rtl = false))
    }

    /** The hour picked is a moment of the phone's day, and the line stands where that moment is on the strip. */
    @Test
    fun anHourPickedIsAMomentOfTheDayHere() {
        val kolkata = ZoneId.of("Asia/Kolkata")
        val day = java.time.LocalDate.of(2026, 11, 20)
        val three = WidgetWords.hourMoment(day, 15, kolkata)
        assertEquals(java.time.Instant.parse("2026-11-20T09:30:00Z").toEpochMilli(), three)
        assertEquals(
            "24 is the next day's 00:00",
            java.time.Instant.parse("2026-11-20T18:30:00Z").toEpochMilli(),
            WidgetWords.hourMoment(day, 24, kolkata)
        )
        assertEquals(15 / 24f, WidgetWords.stripAt(three, day, kolkata), 1e-6f)
        assertEquals(
            1f,
            WidgetWords.stripAt(WidgetWords.hourMoment(day, 24, kolkata), day, kolkata),
            1e-6f
        )
        assertEquals(
            "08:00",
            (8 * 60) / 1440f,
            WidgetWords.stripAt(kolkataMorning, day, kolkata),
            1e-6f
        )
        assertEquals(
            "the day before",
            0f,
            WidgetWords.stripAt(kolkataMorning - 86_400_000, day, kolkata),
            1e-6f
        )
        // Every place's time follows: at 15:00 in Kolkata it is 09:30 in London.
        assertEquals("09:30", seenFrom("Asia/Kolkata", three, "Europe/London")[0].time)
        // An hour the clocks skip is the hour after it.
        val london = ZoneId.of("Europe/London")
        assertEquals(
            java.time.Instant.parse("2026-03-29T01:00:00Z").toEpochMilli(),
            WidgetWords.hourMoment(java.time.LocalDate.of(2026, 3, 29), 1, london)
        )
    }

    /** The hours under a strip, in the clock setting's form. */
    @Test
    fun aStripHasItsHoursUnderIt() {
        assertEquals(
            listOf("00", "06", "12", "18", "24"),
            WidgetWords.hourMarks(clock24 = true, Locale.US)
        )
        assertEquals(
            listOf("12 AM", "6 AM", "12 PM", "6 PM", "12 AM"),
            WidgetWords.hourMarks(clock24 = false, Locale.US)
        )
    }

    /**
     * A switch waits 10 seconds for its script. A tap within 2 seconds of the last one with the
     * same arguments starts no run, so the switch does not wait for it.
     */
    @Test
    fun aSwitchWaitsTenSecondsAndNotForATapThatStartsNoRun() {
        assertEquals(10_000L, WidgetWords.WORKING_MS)
        assertFalse("the first tap", WidgetWords.tooSoon(null, 5_000))
        assertTrue(WidgetWords.tooSoon(5_000, 5_000))
        assertTrue(WidgetWords.tooSoon(5_000, 6_999))
        assertFalse(WidgetWords.tooSoon(5_000, 7_000))
        assertFalse("a clock that went back", WidgetWords.tooSoon(5_000, 4_000))
        // The window is the view model's: the same as for a button under a log line.
        assertEquals(2_000L, WidgetWords.TAP_WINDOW_MS)
    }

    /** A countdown, a clock and an agenda say the time, so their words are made again as it passes. No other kind's are. */
    @Test
    fun onlyAKindThatTellsTheTimeIsSaidAgainAsTimePasses() {
        assertTrue(WidgetWords.timed(WidgetSpec.Countdown(base, to = 5)))
        assertTrue(
            WidgetWords.timed(
                WidgetSpec.Clock(
                    base,
                    zones = listOf(WidgetSpec.Zone("UTC"))
                )
            )
        )
        assertTrue(WidgetWords.timed(WidgetSpec.Agenda(base, events = emptyList())))
        assertFalse(WidgetWords.timed(WidgetSpec.Stat(base, value = "1")))
        assertFalse(
            WidgetWords.timed(
                WidgetSpec.Buttons(
                    base,
                    items = listOf(WidgetSpec.Button("A", go))
                )
            )
        )
        assertFalse(WidgetWords.timed(null))
    }

    /** A card that has no picture says why. Out of view it shows the words for the picture, and never "Loading". */
    @Test
    fun anImageCardSaysWhyItHasNoPicture() {
        assertEquals(
            "Loading picture…",
            WidgetWords.pictureNote(failed = false, inView = true, alt = "A graph")
        )
        // Out of view nothing is fetched, so "Loading" would never end.
        assertEquals(
            "A graph",
            WidgetWords.pictureNote(failed = false, inView = false, alt = "A graph")
        )
        assertEquals(
            "Picture unavailable",
            WidgetWords.pictureNote(failed = true, inView = true, alt = "A graph")
        )
        assertEquals(
            "Picture unavailable",
            WidgetWords.pictureNote(failed = true, inView = false, alt = "A graph")
        )
    }

    /** A picture is decoded for its place: at most twice as wide, with at most twice its pixels. */
    @Test
    fun aPictureIsDecodedNoLargerThanTwiceItsPlace() {
        assertEquals(
            WidgetWords.PictureSize(widthPx = 640, maxPixels = 2f * 320 * 240),
            WidgetWords.pictureSize(320, 240)
        )
        // A place is counted in steps of 16 pixels, so a place one pixel wider needs no new decode.
        assertEquals(WidgetWords.pictureSize(320, 240), WidgetWords.pictureSize(305, 225))
        assertEquals(
            WidgetWords.PictureSize(widthPx = 672, maxPixels = 2f * 336 * 240),
            WidgetWords.pictureSize(321, 240)
        )
        // A place with no size, and one with no limit, still give a size that can be decoded.
        assertEquals(
            WidgetWords.PictureSize(widthPx = 32, maxPixels = 2f * 16 * 16),
            WidgetWords.pictureSize(0, -5)
        )
        assertEquals(
            WidgetWords.PictureSize(widthPx = 8192, maxPixels = 2f * 4096 * 4096),
            WidgetWords.pictureSize(Int.MAX_VALUE, Int.MAX_VALUE)
        )
        // The largest card of a phone stays under the cache: 8 bytes for each pixel of its place.
        val full = WidgetWords.pictureSize(1080, 1200)
        assertTrue(full.maxPixels * 4 <= 12 * 1024 * 1024)
    }

    @Test
    fun anAgeIsShortOnTheCardAndInWholeWordsForAScreenReader() {
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

    @Test
    fun theFooterSaysAFailureOrTheAgeOfOldContent() {
        val hour = 3_600_000L
        val now = ZonedDateTime.of(2026, 12, 20, 12, 0, 0, 0, utc).toInstant().toEpochMilli()
        assertNull(
            "current content: no footer",
            WidgetWords.footer(
                hasContent = true,
                old = false,
                setAt = now - hour,
                triedAt = now - hour,
                failed = false,
                now = now,
                zone = utc,
                clock24 = true
            )
        )
        assertEquals(
            WidgetWords.Footer("2 h ago", "Updated 2 hours ago", failed = false),
            WidgetWords.footer(
                hasContent = true,
                old = true,
                setAt = now - 2 * hour,
                triedAt = now - 2 * hour,
                failed = false,
                now = now,
                zone = utc,
                clock24 = true
            ),
        )
        assertNull(
            "nothing stored: nothing has an age",
            WidgetWords.footer(
                hasContent = false,
                old = true,
                setAt = 0,
                triedAt = 0,
                failed = false,
                now = now,
                zone = utc,
                clock24 = true
            )
        )
        // A failure wins over the age, and the old content stays on the card.
        val at = ZonedDateTime.of(2026, 12, 20, 10, 42, 0, 0, utc).toInstant().toEpochMilli()
        assertEquals(
            WidgetWords.Footer("Failed 10:42", "Failed at 10:42", failed = true),
            WidgetWords.footer(
                hasContent = true,
                old = true,
                setAt = now - 5 * hour,
                triedAt = at,
                failed = true,
                now = now,
                zone = utc,
                clock24 = true
            ),
        )
        assertEquals(
            "not today: how long ago",
            WidgetWords.Footer("Failed 2 d ago", "Failed 2 days ago", failed = true),
            WidgetWords.footer(
                hasContent = false,
                old = false,
                setAt = 0,
                triedAt = now - 49 * hour,
                failed = true,
                now = now,
                zone = utc,
                clock24 = true
            ),
        )
        // The time is the phone's: 10:42 in UTC is 16:12 in India.
        assertEquals(
            "Failed 16:12",
            WidgetWords.footer(
                hasContent = false,
                old = false,
                setAt = 0,
                triedAt = at,
                failed = true,
                now = now,
                zone = ZoneId.of("Asia/Kolkata"),
                clock24 = true
            )!!.text
        )
        // Without the 24-hour setting, as the rest of Kolahal writes the time of day.
        com.mtwabpin.kolahal.withDefaultLocale(Locale.US) {
            assertEquals(
                "Failed 10:42 AM",
                WidgetWords.footer(
                    hasContent = false,
                    old = false,
                    setAt = 0,
                    triedAt = at,
                    failed = true,
                    now = now,
                    zone = utc,
                    clock24 = false
                )!!.text.replace('\u202F', ' ')
            )
        }
    }

    @Test
    fun anErrorsFirstLineIsItsFirstLineWithText() {
        assertEquals(
            "wttr.in answered HTTP 500",
            WidgetWords.firstLine("\n  wttr.in answered HTTP 500\n  at line 7")
        )
        assertNull(WidgetWords.firstLine("  \n "))
        assertNull(WidgetWords.firstLine(null))
    }

    /**
     * A Lua error's log line, as a run makes it ([runOutcome]), on a card: the script's name once,
     * what went wrong, and the line. Lua's `weather:12:` is not repeated.
     */
    @Test
    fun aFailedCardSaysTheNameOnce() {
        val outcome = runOutcome(
            "weather",
            RuntimeException("weather:12: attempt to index a nil value\nstack traceback: …"),
            stop = null,
            repl = false,
            redact = { it },
            errorLine = { 12 },
        )
        assertEquals(
            "weather: attempt to index a nil value (line 12)",
            WidgetWords.cardError(outcome.line)
        )
    }

    /** The stored error is the log line's text (ScriptWorker). A tap on the card refreshes or opens Scripts, so the card leaves the log line's taps out. */
    @Test
    fun aCardsErrorPromisesNoTapItDoesNotHave() {
        // A failed run: the log line opens the editor at the line. The card keeps the line's number.
        assertEquals(
            "weather: attempt to index a nil value (line 7)",
            WidgetWords.cardError("weather: attempt to index a nil value (tap to edit line 7)")
        )
        // A switch that is off stopped the run: the log line allows it.
        assertEquals(
            "weather: internet is off for this script",
            WidgetWords.cardError("weather: internet is off for this script — tap to allow")
        )
        // Any other error stays as it is.
        assertEquals(
            "weather: timed out after 30s",
            WidgetWords.cardError("weather: timed out after 30s")
        )
        assertEquals(
            "weather: no network",
            WidgetWords.cardError("\n  weather: no network  \nstack traceback: …")
        )
        // Only at the end of the first line: a script's own words stay.
        assertEquals(
            "quiz: (tap to edit line 3) is no answer",
            WidgetWords.cardError("quiz: (tap to edit line 3) is no answer")
        )
        assertEquals(
            "quiz: say — tap to allow — aloud (line 12)",
            WidgetWords.cardError("quiz: say — tap to allow — aloud (tap to edit line 12)")
        )
        assertNull(WidgetWords.cardError("  \n "))
        assertNull(WidgetWords.cardError(null))
    }

    @Test
    fun aListDrawsTheRowsThatFitAndCountsTheRest() {
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

    /** Segmented rows have 2 dp between two. A card with a title line says "+3 more" there, so it keeps no room for it. */
    @Test
    fun rowsWithGapsCountTheGapsOnlyBetweenRows() {
        // A 2x2 card's content is 136 dp: three rows of 44 dp with two gaps of 2 take all of it.
        assertEquals(3, WidgetWords.rowsShown(3, 136, 44, 0, gapDp = 2))
        assertEquals(3, WidgetWords.rowsShown(5, 136, 44, 0, gapDp = 2))
        assertEquals("one dp short", 2, WidgetWords.rowsShown(5, 135, 44, 0, gapDp = 2))
        // With a "+ more" line in the body.
        assertEquals(2, WidgetWords.rowsShown(5, 136, 44, 20, gapDp = 2))
        assertTrue(WidgetWords.showsMore(5, 2, 136, 44, 20, gapDp = 2))
        assertFalse(WidgetWords.showsMore(3, 3, 136, 44, 20, gapDp = 2))
        // Large clock rows, 28 dp each, with gaps of 2: four take 118.
        assertEquals(4, WidgetWords.clockRows(4, 118, gapDp = 2))
        assertEquals(3, WidgetWords.clockRows(4, 117, gapDp = 2))
    }

    /** A row of text keeps its height at the usual font size, and grows to its lines with a larger one: fewer rows fit. */
    @Test
    fun rowsGrowWithTheFontSize() {
        // Lines one under the other, in whole dp at a font scale, as a card measures them ([linesDp]).
        fun lines(scale: Float, vararg sp: Int) =
            kotlin.math.ceil(sp.sumOf { (it * scale).toDouble() }).toInt()
        // At the usual size, the heights are those of today: a list's row with a title (20 sp) and a
        // second line (16 sp), one without, an agenda's heading, and a narrow agenda's two lines.
        assertEquals(44, WidgetWords.textRowDp(TAP_ROW_DP, lines(1f, 20, 16)))
        assertEquals(44, WidgetWords.textRowDp(TAP_ROW_DP, lines(1f, 20)))
        assertEquals(32, WidgetWords.textRowDp(32, lines(1f, 20)))
        assertEquals(16, WidgetWords.textRowDp(16, lines(1f, 16)))
        assertEquals(32, WidgetWords.textRowDp(32, lines(1f, 16, 16)))
        // At 1.3 times, 26 and 20.8 dp of lines don't fit in 44: the rows grow, and one-line rows keep their height.
        val two = WidgetWords.textRowDp(TAP_ROW_DP, lines(1.3f, 20, 16))
        assertEquals(47, two)
        assertEquals(44, WidgetWords.textRowDp(TAP_ROW_DP, lines(1.3f, 20)))
        assertEquals(32, WidgetWords.textRowDp(32, lines(1.3f, 20)))
        assertEquals(21, WidgetWords.textRowDp(16, lines(1.3f, 16)))
        assertEquals(42, WidgetWords.textRowDp(32, lines(1.3f, 16, 16)))
        // A 2x2 card's 136 dp: three rows at the usual size, two at 1.3 times.
        assertEquals(3, WidgetWords.rowsShown(5, 136, 44, 0, gapDp = 2))
        assertEquals(2, WidgetWords.rowsShown(5, 136, two, 0, gapDp = 2))
        // A narrow agenda: rows of 32 dp and a heading of 16 (48 dp for a day's first) fit three events;
        // at 1.3 times rows of 42 and a heading of 21 (63) fit two.
        assertEquals(3, WidgetWords.agendaShown(listOf(2, 1), 136, 16, 32, 20))
        val row = WidgetWords.textRowDp(32, lines(1.3f, 16, 16))
        val headed = maxOf(row, WidgetWords.textRowDp(16, lines(1.3f, 16)) + WidgetWords.textRowDp(32, lines(1.3f, 16, 16)))
        assertEquals(63, headed)
        assertEquals(2, WidgetWords.agendaShown(listOf(2, 1), 136, headed - row, row, 20))
    }

    /** A short list fills its card: its rows share the height, up to 56 dp. A list with rows left out keeps its rows' height. */
    @Test
    fun rowsThatAllFitShareTheHeight() {
        // Three rows of a forecast in a 2x2 card's 136 dp: each gets a third, less the gaps.
        assertEquals(44f, WidgetWords.rowHeight(3, 3, 136f, 32, gapDp = 2), 0.01f)
        // Never more than 56 dp: one row in a tall card.
        assertEquals(56f, WidgetWords.rowHeight(1, 1, 300f, 32, gapDp = 2), 0f)
        // Rows left out: the room is for "+3 more", and each row stays as it is.
        assertEquals(44f, WidgetWords.rowHeight(8, 3, 138f, 44, gapDp = 2), 0f)
        // Never under the least height, as for a tap row in too little room.
        assertEquals(44f, WidgetWords.rowHeight(1, 1, 30f, 44), 0f)
        assertEquals(32f, WidgetWords.rowHeight(0, 0, 138f, 32), 0f)
    }

    /** A large card with no icon has the first letter of its title as its badge. */
    @Test
    fun aBadgeLetterIsTheTitlesFirstLetterAsACapital() {
        assertEquals("P", WidgetWords.badgeLetter("pune weather"))
        assertEquals("H", WidgetWords.badgeLetter("  #hn top"))
        assertEquals("2", WidgetWords.badgeLetter("2fa codes"))
        assertEquals("É", WidgetWords.badgeLetter("été"))
        assertNull(WidgetWords.badgeLetter("☀ · ·"))
        assertNull(WidgetWords.badgeLetter(""))
    }
}
