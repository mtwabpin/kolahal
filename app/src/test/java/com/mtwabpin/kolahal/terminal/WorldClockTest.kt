package com.mtwabpin.kolahal.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

class WorldClockTest {
    private val adelaide = ZoneId.of("Australia/Adelaide")

    // 2026-10-24 08:30 in Adelaide (UTC+10:30, summer time): 22:00 UTC the day before.
    private val now = Instant.parse("2026-10-23T22:00:00Z")

    @Test
    fun citiesInAnyCaseWithSpacesOrUnderscores() {
        assertEquals(ZoneId.of("Asia/Tokyo"), WorldClock.find("tokyo")!!.zone)
        assertEquals("New York", WorldClock.find("new york")!!.name)
        assertEquals(ZoneId.of("America/New_York"), WorldClock.find("New_York")!!.zone)
        assertEquals(ZoneId.of("America/Sao_Paulo"), WorldClock.find("SAO PAULO")!!.zone)
    }

    @Test
    fun zoneIdsUtcAndOffsets() {
        assertEquals(ZoneId.of("Australia/Adelaide"), WorldClock.find("australia/adelaide")!!.zone)
        assertEquals(ZoneOffset.UTC, WorldClock.find("UTC")!!.zone)
        assertEquals(ZoneOffset.ofHoursMinutes(5, 30), WorldClock.find("+5:30")!!.zone)
        assertEquals(ZoneOffset.ofHours(-8), WorldClock.find("-8")!!.zone)
        assertNull(WorldClock.find("+25"))
        assertNull(WorldClock.find("nowhere at all"))
    }

    @Test
    fun suggestionsStartWithWhatWasTyped() {
        val names = WorldClock.suggest("to").map { it.name }
        assertTrue(names.toString(), "Tokyo" in names && "Toronto" in names)
        assertTrue(WorldClock.suggest("new y").map { it.name } == listOf("New York"))
        assertTrue(WorldClock.suggest("tokyo").isEmpty())
        assertTrue(WorldClock.suggest("").isEmpty())
    }

    @Test
    fun aheadBehindAndSameTime() {
        val tokyo = WorldClock.line(
            WorldClock.find("tokyo")!!,
            now,
            adelaide,
            clock24 = true,
            locale = Locale.UK
        )
        assertEquals("Tokyo", tokyo.place)
        assertEquals("07:00 · 1h30 behind", tokyo.detail)
        // London is on summer time in October: UTC+1, 9h30 behind Adelaide, and still the day before.
        assertEquals(
            "23:00 Fri 23 Oct · 9h30 behind",
            WorldClock.line(WorldClock.find("london")!!, now, adelaide, true, Locale.UK).detail
        )
        assertEquals(
            "8:30 AM · same time",
            WorldClock.line(
                WorldClock.find("+10:30")!!,
                now,
                adelaide,
                false,
                Locale.US
            ).detail.replace(' ', ' ')
        )
    }

    @Test
    fun severalPlacesEachTakeTheWordsTheyNeed() {
        val places = WorldClock.parse(listOf("new", "york", "london", "+2"))!!
        assertEquals(listOf("New York", "London", "UTC+2"), places.map { it.name })
        assertNull(WorldClock.parse(listOf("london", "nowhere")))
    }

    /** Offsets go to ±18:00: one past it is no place, not an error from Java. */
    @Test
    fun offsetsStopAtEighteenHours() {
        assertEquals(ZoneOffset.ofHours(18), WorldClock.find("+18")!!.zone)
        assertEquals(ZoneOffset.ofHours(-18), WorldClock.find("-18:00")!!.zone)
        assertNull(WorldClock.find("+18:30"))
        assertNull(WorldClock.find("-18:01"))
    }
}
