package com.mtwabpin.kolahal.data

import com.mtwabpin.kolahal.withDefaultLocale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The clock and the day as every command, log line and worker writes them. */
class TimeFormatsTest {
    private val today = LocalDate.of(2026, 10, 20)
    private val evening = LocalDateTime.of(2026, 10, 20, 22, 40)
    private val morning = LocalDateTime.of(2026, 10, 20, 9, 5)

    @Test fun theClockFollowsThe24HourSetting() {
        assertEquals("22:40", TimeFormats.clockFormat(clock24 = true, Locale.US).format(evening))
        assertEquals("09:05", TimeFormats.clockFormat(clock24 = true, Locale.US).format(morning))
        assertEquals("10:40 PM", TimeFormats.clockFormat(clock24 = false, Locale.US).format(evening))
        assertEquals("9:05 AM", TimeFormats.clockFormat(clock24 = false, Locale.US).format(morning))
    }

    @Test fun theDayIsShort() {
        assertEquals("Tue 20 Oct", TimeFormats.dayFormat(Locale.UK).format(today))
        assertEquals("Tue 20 Oct", TimeFormats.dayFormat(Locale.UK).format(evening))
    }

    /** Workers and call lines use SimpleDateFormat, commands use java.time: one pattern, one text. */
    @Test fun bothKindsOfFormatterWriteTheSame() {
        val utc = TimeZone.getTimeZone("UTC")
        val at = Date(evening.toInstant(ZoneOffset.UTC).toEpochMilli())
        fun old(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply { timeZone = utc }.format(at)
        assertEquals(TimeFormats.clockFormat(clock24 = true, Locale.US).format(evening), old(TimeFormats.clock(true)))
        assertEquals(TimeFormats.clockFormat(clock24 = false, Locale.US).format(evening), old(TimeFormats.clock(false)))
        assertEquals(TimeFormats.dayFormat(Locale.US).format(evening), old(TimeFormats.DAY))
        // A longer pattern built on the day, as a call line has it.
        assertEquals("Tue 20 Oct, ", old("${TimeFormats.DAY}, "))
    }

    @Test fun aNearDayGoesByItsName() {
        assertEquals("today", TimeFormats.dayLabel(today, today, Locale.UK))
        assertEquals("yesterday", TimeFormats.dayLabel(today.minusDays(1), today, Locale.UK))
        assertEquals("Sun 18 Oct", TimeFormats.dayLabel(today.minusDays(2), today, Locale.UK))
        // Only days gone by have a name: tomorrow is a day like any other.
        assertEquals("Wed 21 Oct", TimeFormats.dayLabel(today.plusDays(1), today, Locale.UK))
    }

    /** `unlocks` starts its rows with a capital. Only the two words change: a day stays as its language writes it. */
    @Test fun aCapitalIsForTheTwoWordsOnly() {
        assertEquals("Today", TimeFormats.dayLabel(today, today, Locale.UK, capital = true))
        assertEquals("Yesterday", TimeFormats.dayLabel(today.minusDays(1), today, Locale.UK, capital = true))
        assertEquals("Sun 18 Oct", TimeFormats.dayLabel(today.minusDays(2), today, Locale.UK, capital = true))
        // French writes days and months in small letters.
        val french = TimeFormats.dayLabel(today.minusDays(2), today, Locale.FRANCE, capital = true)
        assertEquals(TimeFormats.dayFormat(Locale.FRANCE).format(today.minusDays(2)), french)
        assertTrue(french, french.first().isLowerCase())
    }

    /** With no locale given, the phone's language now is used, not the one at start-up. */
    @Test fun theLanguageIsReadWhenTheTextIsWritten() {
        val day = today.minusDays(2)
        withDefaultLocale(Locale.UK) {
            assertEquals("Sun 18 Oct", TimeFormats.dayLabel(day, today))
            assertEquals("Sun 18 Oct", TimeFormats.dayFormat().format(day))
        }
        withDefaultLocale(Locale.GERMANY) {
            assertEquals(TimeFormats.dayFormat(Locale.GERMANY).format(day), TimeFormats.dayLabel(day, today))
            assertEquals(TimeFormats.clockFormat(false, Locale.GERMANY).format(evening), TimeFormats.clockFormat(false).format(evening))
        }
    }
}
