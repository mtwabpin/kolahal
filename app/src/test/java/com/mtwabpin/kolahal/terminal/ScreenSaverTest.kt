package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.ScreenSaver.Next
import com.mtwabpin.kolahal.terminal.tty.screens.TtyScreens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When the idle screensaver opens: after `screen.saver-after` quiet minutes, with home idle and the phone willing. */
class ScreenSaverTest {
    private val minute = 60_000L
    private val idle = ScreenSaver.Home(inFront = true, logPage = true)
    private val willing = ScreenSaver.Phone(screenOn = true, batterySaver = false, animationsOff = false)

    private fun next(
        saver: String = "matrix",
        after: Int = 5,
        quietSince: Long = 1_000L,
        now: Long,
        home: ScreenSaver.Home = idle,
        phone: ScreenSaver.Phone = willing,
    ) = ScreenSaver.next(saver, after, quietSince, now, home, phone)

    @Test
    fun itWaitsThenOpens() {
        assertEquals(Next.Wait(5 * minute), next(now = 1_000L))
        assertEquals(Next.Wait(minute), next(now = 1_000L + 4 * minute))
        assertEquals(Next.Open("matrix"), next(now = 1_000L + 5 * minute))
        assertEquals(Next.Open("clock"), next(saver = "clock", now = 1_000L + 9 * minute))
        assertEquals(Next.Open("pipes"), next(saver = "pipes", after = 1, now = 1_000L + minute))
        assertEquals(Next.Open("decode"), next(saver = "decode", after = 1, now = 1_000L + minute))
    }

    /** A touch moves the start of the quiet: the wait is from the last touch. */
    @Test
    fun aTouchStartsTheWaitAgain() {
        val touched = 1_000L + 3 * minute
        assertEquals(Next.Wait(5 * minute), next(quietSince = touched, now = touched))
        assertEquals(Next.Wait(2 * minute), next(quietSince = touched, now = touched + 3 * minute))
    }

    @Test
    fun offNeverOpens() {
        assertEquals(Next.Never, next(saver = "off", now = 99 * minute))
        // Only the screens it can be; not sl, which ends by itself, nor top.
        assertEquals(Next.Never, next(saver = "sl", now = 99 * minute))
        assertEquals(Next.Never, next(saver = "top", now = 99 * minute))
    }

    /** Not over another app, the widgets page, a sheet, the keyboard, typed text or another screen. */
    @Test
    fun onlyOverAnIdleHome() {
        val late = 99 * minute
        for (busy in listOf(
            idle.copy(inFront = false),
            idle.copy(logPage = false),
            idle.copy(sheetOpen = true),
            idle.copy(typing = true),
            idle.copy(screenOpen = true),
        )) {
            assertFalse(busy.toString(), busy.ready)
            assertEquals(busy.toString(), Next.Never, next(now = late, home = busy))
        }
        assertTrue(idle.ready)
    }

    /** Never with the screen off, in battery saver, or with "Remove animations" on: nothing opens, and it asks again later. */
    @Test
    fun thePhoneCanSayNo() {
        val late = 99 * minute
        for (no in listOf(
            willing.copy(screenOn = false),
            willing.copy(batterySaver = true),
            willing.copy(animationsOff = true),
        )) {
            assertFalse(no.toString(), no.allows)
            assertEquals(no.toString(), Next.Wait(minute), next(now = late, phone = no))
        }
        // Before the time is up, it only waits, whatever the phone says.
        assertEquals(Next.Wait(5 * minute), next(now = 1_000L, phone = willing.copy(batterySaver = true)))
    }

    /** The setting's least, 0, is half a minute: 30 seconds. */
    @Test
    fun theLeastIsHalfAMinute() {
        assertEquals(30_000L, ScreenSaver.waitMs(0))
        assertEquals(60_000L, ScreenSaver.waitMs(1))
        assertEquals(Next.Wait(20_000L), ScreenSaver.next("matrix", 0, 0L, 10_000L, idle, willing))
        assertEquals(Next.Open("matrix"), ScreenSaver.next("matrix", 0, 0L, 30_000L, idle, willing))
        assertEquals(0, com.mtwabpin.kolahal.data.Keys.SCREEN_SAVER_AFTER.range.first)
    }

    /** A test's short minute scales every wait. */
    @Test
    fun aShortMinute() {
        val n = ScreenSaver.next("matrix", 2, 0L, 100L, idle, willing, minuteMs = 300L)
        assertEquals(Next.Wait(500L), n)
        assertEquals(Next.Open("matrix"), ScreenSaver.next("matrix", 2, 0L, 600L, idle, willing, minuteMs = 300L))
        assertEquals(Next.Wait(300L), ScreenSaver.next("matrix", 2, 0L, 600L, idle, willing.copy(animationsOff = true), minuteMs = 300L))
    }

    /** The setting offers off and each screen the saver can be; each is a real screen, and each value says what it is. */
    @Test
    fun theSettingsMatch() {
        assertEquals(listOf("off") + ScreenSaver.SCREENS, Keys.SCREEN_SAVER.choices)
        assertEquals("off", Keys.SCREEN_SAVER.default)
        for (name in ScreenSaver.SCREENS) assertTrue(name, name in TtyScreens.names)
        for (value in Keys.SCREEN_SAVER.choices) {
            val note = Keys.SCREEN_SAVER.notes[value]
            assertTrue("$value has no note", !note.isNullOrBlank())
            assertTrue("$value: '$note' is long", note!!.length <= 70)
            assertFalse("$value: '$note' ends with a full stop", note.endsWith("."))
        }
        assertEquals(5, Keys.SCREEN_SAVER_AFTER.default)
        assertEquals(0..60, Keys.SCREEN_SAVER_AFTER.range)
        for (key in listOf(Keys.SCREEN_SAVER, Keys.SCREEN_SAVER_AFTER)) {
            assertEquals(key, Keys.byName(key.key))
            assertFalse(key.key, key in Keys.CONFIG_ONLY)
        }
    }

    /**
     * The gui style offers off and decode, which scrambles home's own log there; matrix, clock and
     * pipes draw the grid, so only the tty style offers them. One kept from the tty style is off in
     * the gui style, and `config` says so beside the stored value.
     */
    @Test
    fun theChoicesFollowTheHomeStyle() {
        assertEquals(Keys.SCREEN_SAVER.choices, ScreenSaver.choices(tty = true))
        assertEquals(listOf("off", "decode"), ScreenSaver.choices(tty = false))
        for (saver in ScreenSaver.SCREENS) assertEquals(saver, ScreenSaver.inEffect(saver, tty = true))
        assertEquals("decode", ScreenSaver.inEffect("decode", tty = false))
        for (saver in listOf("matrix", "clock", "pipes")) {
            assertEquals(saver, "off", ScreenSaver.inEffect(saver, tty = false))
            assertTrue(saver, ScreenSaver.note(saver, tty = false).orEmpty().contains("home.style tty"))
            assertEquals(saver, null, ScreenSaver.note(saver, tty = true))
        }
        assertEquals(null, ScreenSaver.note("decode", tty = false))
        assertEquals(null, ScreenSaver.note("off", tty = false))
    }
}
