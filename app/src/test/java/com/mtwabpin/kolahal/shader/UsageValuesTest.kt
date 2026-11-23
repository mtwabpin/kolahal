package com.mtwabpin.kolahal.shader

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.usage.AppSessions
import com.mtwabpin.kolahal.usage.AppSessions.Companion.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Kolahal's values from the phone's use ([UsageValues]): their ids, and the arithmetic. */
class UsageValuesTest {
    private val hour = 3_600_000L
    private val minute = 60_000L

    @Test
    fun theIds() {
        assertEquals(27, UsageValues.IDS.size)
        assertEquals(UsageValues.IDS.size, UsageValues.IDS.toSet().size)
        assertEquals(listOf("unlocks", "unlocks.usual", "unlocks.d1"), UsageValues.IDS.take(3))
        assertEquals("unlocks.d7", UsageValues.IDS[8])
        assertEquals("screen.h8", UsageValues.IDS[UsageValues.IDS.size - 2])
        assertEquals("away", UsageValues.IDS.last())
        // The menu lists five; the rest are rows of days and blocks.
        assertEquals(listOf("unlocks", "unlocks.usual", "screen", "screen.usual", "away"), UsageValues.LISTED.keys.toList())
        assertEquals(22, UsageValues.IDS.count { UsageValues.series(it) != null })
        assertTrue(UsageValues.LISTED.keys.all { UsageValues.series(it) == null })
        for (bad in listOf("unlocks.d0", "unlocks.d8", "screen.h9", "screen.d", "unlocks.dx", "weather.d1")) {
            assertNull(bad, UsageValues.series(bad))
            assertFalse(bad, UsageValues.has(bad))
        }
        assertEquals(UsageValues.SCREEN_BLOCKS, UsageValues.series("screen.h3"))
    }

    /** A script under one of these names would give values that pass for Kolahal's: both are commands, which no script can be called. */
    @Test
    fun noScriptHasTheirNames() {
        assertTrue(UsageValues.GROUPS.all { it in CommandCatalog.RESERVED_NAMES })
        // Every id with a second word has one of these first.
        assertTrue(UsageValues.IDS.filter { '.' in it }.all { it.substringBefore('.') in UsageValues.GROUPS })
        // The one-word ones can't be a script's, which always has two.
        assertTrue(UsageValues.IDS.filter { '.' !in it }.none { it in ShaderValues.BUILT_IN })
    }

    @Test
    fun whatTheyAreCalled() {
        assertEquals("Unlocks today", UsageValues.label("unlocks"))
        assertEquals("Time away before the last unlock", UsageValues.label("away"))
        assertEquals("Unlocks, day 7 of the last 7", UsageValues.label("unlocks.d7"))
        assertEquals("Screen time, day 1 of the last 7", UsageValues.label("screen.d1"))
        assertEquals("Screen time from 00:00 to 03:00", UsageValues.label("screen.h1"))
        assertEquals("Screen time from 09:00 to 12:00", UsageValues.label("screen.h4"))
        assertEquals("Screen time from 21:00 to midnight", UsageValues.label("screen.h8"))
        assertNull(UsageValues.label("weather.rain"))
    }

    @Test
    fun whatTheyNeedRead() {
        assertTrue(UsageValues.needsUnlocks(listOf("unlocks.d3")))
        assertFalse(UsageValues.needsUnlocks(listOf("screen", "away")))
        assertTrue(UsageValues.needsScreen(listOf("screen.h2")))
        assertFalse(UsageValues.needsScreen(listOf("away", "unlocks")))
    }

    @Test
    fun againstAUsualDay() {
        assertEquals(1f, UsageValues.usual(40.0, listOf(30.0, 50.0))!!, 0.001f)
        assertEquals(0.5f, UsageValues.usual(20.0, listOf(40.0))!!, 0.001f)
        // Twice as many or more is the most.
        assertEquals(2f, UsageValues.usual(100.0, listOf(40.0))!!, 0.001f)
        // None today is 0, also after days of none.
        assertEquals(0f, UsageValues.usual(0.0, listOf(40.0))!!, 0.001f)
        assertEquals(0f, UsageValues.usual(0.0, listOf(0.0, 0.0))!!, 0.001f)
        // Some after a week of none: the most.
        assertEquals(2f, UsageValues.usual(3.0, List(6) { 0.0 })!!, 0.001f)
        // The first day has nothing to compare with.
        assertNull(UsageValues.usual(12.0, emptyList()))
    }

    @Test
    fun aDayAsAPartOfTheWeeksMost() {
        assertEquals(listOf(0.5f, 1f, 0f, 0.25f), UsageValues.parts(listOf(20.0, 40.0, 0.0, 10.0)))
        assertEquals(List(7) { 0f }, UsageValues.parts(List(7) { 0.0 }))
        assertEquals(emptyList<Float>(), UsageValues.parts(emptyList()))
    }

    @Test
    fun theUnlocks() {
        val week = UsageValues.unlocks(listOf(10, 20, 0, 40, 30, 20, 40))
        assertEquals("40", week["unlocks"])
        // The six days before: 120 / 6 = 20 a day.
        assertEquals("2", week["unlocks.usual"])
        assertEquals(listOf("0.25", "0.5", "0", "1", "0.75", "0.5", "1"), UsageValues.UNLOCK_DAYS.ids.map { week[it] })
        // The first day: no usual day, and the days before it are 0.
        val first = UsageValues.unlocks(listOf(5))
        assertEquals("5", first["unlocks"])
        assertNull(first["unlocks.usual"])
        assertEquals(listOf("0", "0", "0", "0", "0", "0", "1"), UsageValues.UNLOCK_DAYS.ids.map { first[it] })
        // A week of zeros.
        val none = UsageValues.unlocks(List(7) { 0 })
        assertEquals("0", none["unlocks"])
        assertEquals("0", none["unlocks.usual"])
        assertTrue(UsageValues.UNLOCK_DAYS.ids.all { none[it] == "0" })
        assertEquals(emptyMap<String, String>(), UsageValues.unlocks(emptyList()))
        // Only the last seven days count.
        assertEquals("1", UsageValues.unlocks(listOf(1000) + List(7) { 1 })["unlocks.usual"])
    }

    @Test
    fun screenTime() {
        val blocks = List(8) { if (it == 0) 0.5f else 0f }
        val values = UsageValues.screen(listOf(60 * minute, 120 * minute, 90 * minute), blocks)
        assertEquals("90", values["screen"])
        assertEquals("1", values["screen.usual"])
        assertEquals(listOf("0", "0", "0", "0", "0.5", "1", "0.75"), UsageValues.SCREEN_DAYS.ids.map { values[it] })
        assertEquals("0.5", values["screen.h1"])
        assertEquals("0", values["screen.h8"])
        assertEquals(UsageValues.IDS.filter { it.startsWith("screen") }.toSet(), values.keys)
        assertEquals("12.5", UsageValues.screen(listOf(750_000L), blocks)["screen"])
    }

    @Test
    fun threeHoursAtATime() {
        val day = 1_000_000_000L
        val end = day + 24 * hour
        // On from 1:00 to 2:30, and from 8:00 to 10:00; now is 10:00, so the blocks after are 0.
        val on = listOf(day + hour to day + hour + 90 * minute, day + 8 * hour to day + 10 * hour)
        val parts = UsageValues.blocks(on, day, end)
        assertEquals(8, parts.size)
        assertEquals(0.5f, parts[0], 0.001f)
        assertEquals(0f, parts[1], 0.001f)
        assertEquals(1f / 3, parts[2], 0.001f)
        assertEquals(1f / 3, parts[3], 0.001f)
        assertTrue(parts.drop(4).all { it == 0f })
        // A session over two blocks counts in each, and nothing goes over 1.
        val all = UsageValues.blocks(listOf(day - hour to end + hour), day, end)
        assertEquals(List(8) { 1f }, all)
        assertEquals(List(8) { 0f }, UsageValues.blocks(emptyList(), day, end))
        // A day of 23 hours: its last block has two.
        val short = UsageValues.blocks(listOf(day + 21 * hour to day + 22 * hour), day, day + 23 * hour)
        assertEquals(0.5f, short[7], 0.001f)
    }

    @Test
    fun timeAway() {
        fun lock(min: Int) = Event(AppSessions.LOCKED, "", min * minute)
        fun unlock(min: Int) = Event(AppSessions.UNLOCKED, "", min * minute)
        assertEquals(30f, UsageValues.away(listOf(unlock(0), lock(10), unlock(40)))!!, 0.001f)
        // The screen locked twice in between: from the first.
        assertEquals(50f, UsageValues.away(listOf(unlock(0), lock(10), lock(30), unlock(60)))!!, 0.001f)
        // Only the last unlock counts.
        assertEquals(2f, UsageValues.away(listOf(lock(0), unlock(100), lock(200), unlock(202)))!!, 0.001f)
        // With no unlock before it, the first lock that was read.
        assertEquals(90f, UsageValues.away(listOf(lock(10), unlock(100)))!!, 0.001f)
        // Locked now, or no lock to measure from.
        assertEquals(30f, UsageValues.away(listOf(lock(10), unlock(40), lock(50)))!!, 0.001f)
        assertNull(UsageValues.away(listOf(lock(10))))
        assertNull(UsageValues.away(listOf(unlock(40))))
        assertNull(UsageValues.away(listOf(unlock(10), unlock(40))))
        assertNull(UsageValues.away(emptyList()))
    }

    @Test
    fun asTheyAreKept() {
        assertEquals("37", UsageValues.number(37.0))
        assertEquals("0.42", UsageValues.number(0.4213))
        assertEquals("0.5", UsageValues.number(0.5))
        assertEquals("100", UsageValues.number(100.0))
        assertEquals("0", UsageValues.number(0.0001))
    }
}
