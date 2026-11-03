package com.mtwabpin.kolahal.terminal

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldClockTest {
    private val kolkata = ZoneId.of("Asia/Kolkata")
    // 2026-10-24 00:30 in Kolkata (UTC+5:30): 19:00 UTC the day before.
    private val now = Instant.parse("2026-10-23T19:00:00Z")

    @Test fun citiesInAnyCaseWithSpacesOrUnderscores() {
        assertEquals(ZoneId.of("Asia/Tokyo"), WorldClock.find("tokyo")!!.zone)
        assertEquals("New York", WorldClock.find("new york")!!.name)
        assertEquals(ZoneId.of("America/New_York"), WorldClock.find("New_York")!!.zone)
        assertEquals(ZoneId.of("America/Sao_Paulo"), WorldClock.find("SAO PAULO")!!.zone)
    }

    @Test fun zoneIdsUtcAndOffsets() {
        assertEquals(ZoneId.of("Asia/Kolkata"), WorldClock.find("asia/kolkata")!!.zone)
        assertEquals(ZoneOffset.UTC, WorldClock.find("UTC")!!.zone)
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), WorldClock.find("+5:30")!!.zone)
        assertEquals(ZoneOffset.ofHours(-8), WorldClock.find("-8")!!.zone)
        assertNull(WorldClock.find("+25"))
        assertNull(WorldClock.find("nowhere at all"))
    }

    @Test fun suggestionsStartWithWhatWasTyped() {
        val names = WorldClock.suggest("to").map { it.name }
        assertTrue(names.toString(), "Tokyo" in names && "Toronto" in names)
        assertTrue(WorldClock.suggest("new y").map { it.name } == listOf("New York"))
        assertTrue(WorldClock.suggest("tokyo").isEmpty())
        assertTrue(WorldClock.suggest("").isEmpty())
    }

    @Test fun aheadBehindAndSameTime() {
        val tokyo = WorldClock.line(WorldClock.find("tokyo")!!, now, kolkata, clock24 = true, locale = Locale.UK)
        assertEquals("Tokyo", tokyo.place)
        assertEquals("04:00 · 3h30 ahead", tokyo.detail)
        // London is on summer time in October: UTC+1, 4h30 behind Kolkata, and still the day before.
        assertEquals("20:00 Fri 23 Oct · 4h30 behind", WorldClock.line(WorldClock.find("london")!!, now, kolkata, true, Locale.UK).detail)
        assertEquals("12:30 AM · same time", WorldClock.line(WorldClock.find("+5:30")!!, now, kolkata, false, Locale.US).detail.replace(' ', ' '))
    }

    @Test fun severalPlacesEachTakeTheWordsTheyNeed() {
        val places = WorldClock.parse(listOf("new", "york", "london", "+2"))!!
        assertEquals(listOf("New York", "London", "UTC+2"), places.map { it.name })
        assertNull(WorldClock.parse(listOf("london", "nowhere")))
    }

    /** Offsets go to ±18:00: one past it is no place, not an error from Java. */
    @Test fun offsetsStopAtEighteenHours() {
        assertEquals(ZoneOffset.ofHours(18), WorldClock.find("+18")!!.zone)
        assertEquals(ZoneOffset.ofHours(-18), WorldClock.find("-18:00")!!.zone)
        assertNull(WorldClock.find("+18:30"))
        assertNull(WorldClock.find("-18:01"))
    }
}
