package com.mtwabpin.kolahal.usage

import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.SettingTitles
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.usage.UsageLimits.Level
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The daily limits: a figure under, at and far over one, the words for it, and the two settings. */
class UsageLimitsTest {
    private val minute = 60_000L

    /** Under the limit nothing; at it and over it the warning; from one and a half times it the error. */
    @Test
    fun theTwoStages() {
        assertEquals(Level.NONE, UsageLimits.level(29, 30))
        assertEquals(Level.WARN, UsageLimits.level(30, 30))
        assertEquals(Level.WARN, UsageLimits.level(44, 30))
        assertEquals(Level.BAD, UsageLimits.level(45, 30))
        assertEquals(Level.BAD, UsageLimits.level(300, 30))
        // An odd limit: 1.5 × 7 is 10.5, so 10 is still the warning and 11 the error.
        assertEquals(Level.WARN, UsageLimits.level(10, 7))
        assertEquals(Level.BAD, UsageLimits.level(11, 7))
        // A limit of 1: one is at it, two is far over it.
        assertEquals(Level.WARN, UsageLimits.level(1, 1))
        assertEquals(Level.BAD, UsageLimits.level(2, 1))
    }

    /** A limit of 0 is none: no figure is over it, however big. */
    @Test
    fun noLimitIsNeverPassed() {
        assertEquals(Level.NONE, UsageLimits.level(0, 0))
        assertEquals(Level.NONE, UsageLimits.level(10_000, 0))
        assertEquals(Level.NONE, UsageLimits.unlocks(500, 0))
        assertEquals(Level.NONE, UsageLimits.screenTime(20 * 60 * minute, 0))
        assertNull(UsageLimits.unlockWords(500, 0))
        assertNull(UsageLimits.screenTimeWords(20 * 60 * minute, 0))
    }

    /** Screen time counts whole minutes, as its figure shows them: 2h 59m and some seconds is under 3h. */
    @Test
    fun screenTimeInWholeMinutes() {
        assertEquals(Level.NONE, UsageLimits.screenTime(180 * minute - 1, 180))
        assertEquals(Level.WARN, UsageLimits.screenTime(180 * minute, 180))
        assertEquals(Level.WARN, UsageLimits.screenTime(269 * minute + 59_000, 180))
        assertEquals(Level.BAD, UsageLimits.screenTime(270 * minute, 180))
    }

    /** Each stage has its theme color, and a figure under the limit keeps its own. */
    @Test
    fun theColors() {
        assertNull(UsageLimits.role(Level.NONE))
        assertEquals(StyledText.ColorRole.WARN, UsageLimits.role(Level.WARN))
        assertEquals(StyledText.ColorRole.BAD, UsageLimits.role(Level.BAD))
        assertEquals("41", UsageLimits.colored("41", Level.NONE))
        assertEquals(StyledText.color(StyledText.ColorRole.WARN, "41"), UsageLimits.colored("41", Level.WARN))
        assertEquals(StyledText.color(StyledText.ColorRole.BAD, "4h 30m"), UsageLimits.colored("4h 30m", Level.BAD))
    }

    /** The words beside the color: how far over, or at the limit; none under it. */
    @Test
    fun theWords() {
        assertNull(UsageLimits.screenTimeWords(179 * minute, 180))
        assertEquals("at your limit of 3h", UsageLimits.screenTimeWords(180 * minute, 180))
        assertEquals("30m over your limit of 3h", UsageLimits.screenTimeWords(210 * minute, 180))
        assertEquals("2h 5m over your limit of 2h 30m", UsageLimits.screenTimeWords(275 * minute, 150))
        assertEquals("1h over your limit of 45m", UsageLimits.screenTimeWords(105 * minute, 45))
        assertNull(UsageLimits.unlockWords(29, 30))
        assertEquals("at your limit of 30", UsageLimits.unlockWords(30, 30))
        assertEquals("11 over your limit of 30", UsageLimits.unlockWords(41, 30))
        // Each fits a line of the log with room to spare.
        assertTrue(UsageLimits.screenTimeWords(1_440 * minute, 1_439)!!.length <= 43)
    }

    @Test
    fun minutesAsPeopleSayThem() {
        assertEquals("0m", UsageLimits.minutes(0))
        assertEquals("45m", UsageLimits.minutes(45))
        assertEquals("1h", UsageLimits.minutes(60))
        assertEquals("3h", UsageLimits.minutes(180))
        assertEquals("2h 30m", UsageLimits.minutes(150))
        assertEquals("24h", UsageLimits.minutes(1_440))
    }

    /**
     * The two settings are off by default, 0 turns them off, and they take whole numbers in their
     * ranges: minutes for screen time (up to a day), a count for unlocks.
     */
    @Test
    fun theSettings() {
        val time = Keys.USAGE_SCREEN_TIME_LIMIT
        val unlocks = Keys.USAGE_UNLOCK_LIMIT
        assertEquals("usage.screen-time-limit", time.key)
        assertEquals("usage.unlock-limit", unlocks.key)
        assertEquals(0, time.default)
        assertEquals(0, unlocks.default)
        assertEquals(180, time.parse("180"))
        assertEquals(0, time.parse("0"))
        assertEquals(1_440, time.parse("1440"))
        assertNull(time.parse("1441"))
        assertNull(time.parse("-1"))
        // Minutes, not a length with units.
        assertNull(time.parse("3h"))
        assertEquals(60, unlocks.parse("60"))
        assertEquals(0, unlocks.parse("0"))
        assertNull(unlocks.parse("501"))
        assertNull(unlocks.parse("many"))
        for (key in listOf(time, unlocks)) {
            assertTrue(key.key, key in Keys.ALL)
            assertEquals(key, Keys.byName(key.key))
            assertTrue(key.key, SettingTitles[key.key] != null)
            assertTrue(key.key, key.help.endsWith("0 turns it off"))
        }
    }
}
