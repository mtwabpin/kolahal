package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.withDefaultZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

/** What the header's parts say, in which ink, and what a tap on each opens ([headerParts]). */
class HeaderModelTest {
    /** Friday 21 November 2025, 14:02 UTC. */
    private val at = 1_763_733_720_000L

    private fun parts(
        left: String,
        right: String,
        status: Status = Status(),
        unlocks: Int = 0,
        todos: Int = 0,
        unlockLimit: Int = 0,
    ) = withDefaultZone("UTC") {
        headerParts(
            left.split(','),
            right.split(','),
            clock24 = true,
            now = at,
            status = status,
            unlocks = unlocks,
            profiles = emptyList(),
            todoCount = todos,
            unlockLimit = unlockLimit,
            locale = Locale.UK,
        )
    }

    /** By default: the time and the date on the left; the alarm, todos and battery on the right. */
    @Test
    fun theDefaultSides() {
        val parts = parts("time,date", "alarm,todo,battery", Status(batteryPct = 82, network = "wifi"))
        assertEquals(listOf("14:02", "Fri 21 Nov"), parts.left.map { it.text })
        assertEquals(listOf(HeaderPart.Kind.TIME, HeaderPart.Kind.DATE), parts.left.map { it.kind })
        assertEquals(listOf(Ink.FG, Ink.DIM), parts.left.map { it.ink })
        // No alarm set and no todos: only the battery.
        assertEquals(listOf("82%"), parts.right.map { it.text })
    }

    /** A part with nothing to show is left out: no alarm, no todos, no battery level. */
    @Test
    fun aPartWithNothingToShowIsLeftOut() {
        assertEquals(emptyList<HeaderPart>(), parts("alarm,todo,battery", "").left)
        assertEquals(listOf("3"), parts("todo", "", todos = 3).left.map { it.text })
    }

    /** An alarm a day or more ahead shows its weekday; one today shows its time alone. */
    @Test
    fun anAlarmsDay() {
        val sunday = 1_763_881_200_000L // Sunday 23 November, 07:00
        val tonight = 1_763_767_800_000L // Friday 21 November, 23:30
        assertEquals("Sun 07:00", parts("alarm", "", Status(nextAlarm = sunday)).left.single().text)
        assertEquals("23:30", parts("alarm", "", Status(nextAlarm = tonight)).left.single().text)
    }

    /** The battery is in the prompt ink while charging, in the error ink at 15% or less. */
    @Test
    fun theBatterysInk() {
        fun battery(status: Status) = parts("battery", "", status).left.single()
        assertEquals(Ink.PROMPT, battery(Status(batteryPct = 40, charging = true)).ink)
        assertEquals(Ink.ERROR, battery(Status(batteryPct = 12)).ink)
        assertEquals(Ink.FG, battery(Status(batteryPct = 82)).ink)
        assertEquals(82, battery(Status(batteryPct = 82)).battery)
    }

    /** Offline is an error, and a screen reader hears the network's name. */
    @Test
    fun theNetwork() {
        val offline = parts("network", "", Status(network = "offline")).left.single()
        assertEquals(Ink.ERROR, offline.ink)
        assertEquals("network offline", offline.description)
        assertEquals(Ink.FG, parts("network", "", Status(network = "wifi")).left.single().ink)
    }

    /**
     * The unlocks against the daily limit: their usual ink under it or with none, the warning ink
     * at it, the error ink from one and a half times it; and a screen reader hears how far over.
     */
    @Test
    fun theUnlocksAgainstTheLimit() {
        fun unlocks(count: Int, limit: Int) = parts("unlocks", "", unlocks = count, unlockLimit = limit).left.single()
        assertEquals(Ink.FG, unlocks(41, 0).ink)
        assertNull(unlocks(41, 0).description)
        assertEquals(Ink.FG, unlocks(29, 30).ink)
        assertNull(unlocks(29, 30).description)
        assertEquals(Ink.WARN, unlocks(30, 30).ink)
        assertEquals("30 unlocks today, at your limit of 30", unlocks(30, 30).description)
        assertEquals(Ink.WARN, unlocks(41, 30).ink)
        assertEquals("41 unlocks today, 11 over your limit of 30", unlocks(41, 30).description)
        assertEquals(Ink.ERROR, unlocks(45, 30).ink)
        assertEquals("1 unlock today, at your limit of 1", unlocks(1, 1).description)
        // The text stays the count, and a tap still opens the unlocks.
        assertEquals("45", unlocks(45, 30).text)
        assertEquals(HeaderTarget.UNLOCKS, unlocks(45, 30).target)
    }

    /** A part listed on both sides shows on the left only. */
    @Test
    fun aPartOnBothSidesShowsOnTheLeft() {
        val parts = parts("time", "time,battery", Status(batteryPct = 50))
        assertEquals(listOf(HeaderPart.Kind.TIME), parts.left.map { it.kind })
        assertEquals(listOf(HeaderPart.Kind.BATTERY), parts.right.map { it.kind })
    }

    /** A tap on a part opens what it shows; each part keeps its test tag. */
    @Test
    fun whatATapOpens() {
        val parts = parts("time,date,unlocks,todo,settings", "", unlocks = 41, todos = 2).left
        assertEquals(
            listOf(
                HeaderTarget.CLOCK,
                HeaderTarget.CLOCK,
                HeaderTarget.UNLOCKS,
                HeaderTarget.TODO,
                HeaderTarget.SETTINGS
            ),
            parts.map { it.target },
        )
        assertEquals(listOf("time", "date", "unlocks", "todo", "settings"), parts.map { it.tag })
        assertEquals("41", parts[2].text)
        assertEquals("Settings", parts[4].description)
        assertNull(parts[0].description)
    }
}
