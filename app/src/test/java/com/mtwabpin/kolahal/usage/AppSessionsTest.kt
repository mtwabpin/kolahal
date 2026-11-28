package com.mtwabpin.kolahal.usage

import android.app.usage.UsageEvents
import com.mtwabpin.kolahal.usage.AppSessions.Companion.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One app's opens, sessions and hours, from Android's usage events ([AppSessions.of]). */
class AppSessionsTest {
    private val min = 60_000L
    private val from = 0L
    private val until = 24 * 60 * min
    private val app = "com.whatsapp"
    private val other = "com.android.chrome"

    private fun front(pkg: String, at: Long) = Event(AppSessions.RESUMED, pkg, at)
    private fun away(pkg: String, at: Long) = Event(AppSessions.PAUSED, pkg, at)

    /** The app's use in [from, until), worked out as `usage` does it: every app's sessions, then the app's. */
    private fun of(events: List<Event>, until: Long = this.until): AppSessions {
        val (all, inFront) = AppSessions.sessions(events, from, until)
        return AppSessions.of(all, app, inFront, java.time.ZoneOffset.UTC)
    }

    private fun of(vararg e: Event) = of(e.toList())

    @Test
    fun theKindsAreAndroids() {
        assertEquals(UsageEvents.Event.ACTIVITY_RESUMED, AppSessions.RESUMED)
        assertEquals(UsageEvents.Event.ACTIVITY_PAUSED, AppSessions.PAUSED)
        assertEquals(UsageEvents.Event.SCREEN_NON_INTERACTIVE, AppSessions.SCREEN_OFF)
        assertEquals(UsageEvents.Event.KEYGUARD_SHOWN, AppSessions.LOCKED)
        assertEquals(UsageEvents.Event.DEVICE_SHUTDOWN, AppSessions.SHUTDOWN)
    }

    /** Opened twice, with another app between: two opens, the longer session is the longest. */
    @Test
    fun eachTimeItComesToTheFrontIsAnOpen() {
        val s = of(
            front(app, 10 * min),
            away(app, 15 * min),
            front(other, 15 * min),
            away(other, 20 * min),
            front(app, 20 * min),
            away(app, 32 * min),
            front(other, 32 * min)
        )
        assertEquals(2, s.opens)
        assertEquals(17 * min, s.total)
        assertEquals(12 * min, s.longest)
        assertEquals(20 * min, s.longestAt)
        assertEquals(32 * min, s.lastUsed)
        assertFalse(s.inFront)
    }

    /** Another screen of the same app, at once, is the same session and no open. */
    @Test
    fun anotherScreenOfTheAppIsNoOpen() {
        val s = of(
            front(app, 10 * min),
            away(app, 12 * min),
            front(app, 12 * min + 300),
            away(app, 20 * min),
            front(other, 20 * min)
        )
        assertEquals(1, s.opens)
        assertEquals(10 * min, s.total)
    }

    /**
     * From one screen of an app to another: the first pauses, then the next comes to the front.
     * One session, one open. (Android then says the first screen stopped; Kolahal doesn't read that kind.)
     */
    @Test
    fun anotherScreenAfterAPauseIsTheSameSession() {
        fun screen(type: Int, name: String, at: Long) = Event(type, app, at, name)
        val s = of(
            screen(AppSessions.RESUMED, "Chats", 10 * min),
            screen(AppSessions.PAUSED, "Chats", 15 * min),
            screen(AppSessions.RESUMED, "Chat", 15 * min + 200),
            screen(AppSessions.PAUSED, "Chat", 30 * min),
            screen(AppSessions.RESUMED, "Chats", 30 * min + 200),
            screen(AppSessions.PAUSED, "Chats", 50 * min),
            front(other, 50 * min),
        )
        assertEquals(1, s.opens)
        assertEquals(40 * min, s.total)
    }

    /** A session over the window's start keeps its part inside, and was opened before: no open in the window. */
    @Test
    fun aSessionOverMidnightIsCut() {
        val night = 24 * 60 * min
        val end = night + 100 * min
        val (all, _) = AppSessions.sessions(
            listOf(
                front(app, night - 30 * min),
                away(app, night + 40 * min),
                front(other, night + 40 * min)
            ), 0L, end
        )
        val today = AppSessions.clip(all, night, end)
        assertEquals(
            listOf(
                AppSessions.Companion.Session(
                    app,
                    night,
                    night + 40 * min,
                    opened = false
                ), AppSessions.Companion.Session(other, night + 40 * min, end, opened = true)
            ), today
        )
        assertEquals(30 * min, AppSessions.totals(AppSessions.clip(all, 0L, night))[app])
    }

    /** Back to the app a while later, with nothing between (the screen of a call): a second open. */
    @Test
    fun aLateReturnIsAnotherOpen() {
        val s = of(
            front(app, 10 * min),
            away(app, 12 * min),
            front(app, 15 * min),
            away(app, 18 * min),
            front(other, 18 * min)
        )
        assertEquals(2, s.opens)
        assertEquals(5 * min, s.total)
    }

    /** In front before the day began: its session starts at the day's start, and it is no open today. */
    @Test
    fun inFrontAtTheStart() {
        val s = of(away(app, 7 * min), front(other, 7 * min))
        assertEquals(0, s.opens)
        assertEquals(7 * min, s.total)
    }

    /** Still in front at the end: its session runs to the end, and it is in use. */
    @Test
    fun inFrontAtTheEnd() {
        val s = of(front(app, until - 30 * min))
        assertEquals(30 * min, s.total)
        assertTrue(s.inFront)
        assertEquals(until, s.lastUsed)
    }

    /** The screen goes off, the phone locks or shuts down: the session ends there, with no pause seen. */
    @Test
    fun theScreenOffEndsASession() {
        for (end in listOf(AppSessions.SCREEN_OFF, AppSessions.LOCKED, AppSessions.SHUTDOWN)) {
            val s = of(
                front(app, 10 * min),
                Event(end, "android", 25 * min),
                front(app, 40 * min),
                away(app, 41 * min),
                front(other, 41 * min)
            )
            assertEquals("$end", 2, s.opens)
            assertEquals("$end", 16 * min, s.total)
        }
    }

    /** A session over an hour's end counts in both hours. */
    @Test
    fun hoursSplitASession() {
        val s = of(front(app, 50 * min), away(app, 70 * min), front(other, 70 * min))
        assertEquals(10 * min, s.hours[0])
        assertEquals(10 * min, s.hours[1])
        assertEquals(0L, s.hours[2])
    }

    /** Every app's time from one pass: one app in front at a time, so the times add up to the time in use. */
    @Test
    fun totalsForEveryApp() {
        val events = listOf(
            front(app, 10 * min),
            front(other, 25 * min),
            Event(AppSessions.SCREEN_OFF, "android", 40 * min),
            front(app, 50 * min)
        )
        val totals = AppSessions.totals(AppSessions.sessions(events, from, 60 * min).first)
        assertEquals(mapOf(app to 25 * min, other to 15 * min), totals)
    }

    /** Seven hours in front with nothing else happening: seven hours, still in front at the end. */
    @Test
    fun aLongSessionCountsInFull() {
        val s = of(listOf(front(app, 10 * min)), until = 10 * min + 7 * 60 * min)
        assertEquals(7 * 60 * min, s.total)
        assertEquals(1, s.opens)
        assertTrue(s.inFront)
    }

    /** Eight hours in front, ended by the screen going off: eight hours. */
    @Test
    fun theScreenGoingOffEndsALongSession() {
        val s = of(
            front(app, 10 * min),
            Event(AppSessions.SCREEN_OFF, "android", 10 * min + 8 * 60 * min)
        )
        assertEquals(8 * 60 * min, s.total)
        assertEquals(8 * 60 * min, s.longest)
        assertFalse(s.inFront)
    }

    /** In front from 6 hours before midnight with no event since, past midnight: cut at midnight, both days count. */
    @Test
    fun aSilentSessionOverMidnightCountsOnBothDays() {
        val day = 24 * 60 * min
        val (all, _) = AppSessions.sessions(
            listOf(
                front(app, day - 6 * 60 * min - 10 * min),
                Event(AppSessions.SCREEN_OFF, "android", day + 30 * min)
            ),
            from, 2 * day
        )
        assertEquals(6 * 60 * min + 10 * min, AppSessions.totals(AppSessions.clip(all, from, day))[app])
        val after = AppSessions.clip(all, day, 2 * day)
        assertEquals(30 * min, AppSessions.totals(after)[app])
        assertFalse(after.single().opened)
    }

    /** A shut-down ends the session where it is recorded; with none (a flat battery), the start-up after ends it at its last event. */
    @Test
    fun shutDownAndStartUpEndASession() {
        assertEquals(UsageEvents.Event.DEVICE_STARTUP, AppSessions.STARTUP)
        val off = of(
            front(app, 10 * min),
            Event(AppSessions.SHUTDOWN, "android", 40 * min),
            Event(AppSessions.STARTUP, "android", 600 * min),
            front(other, 601 * min)
        )
        assertEquals(30 * min, off.total)
        val flat = of(
            front(app, 10 * min),
            away(app, 20 * min),
            front(app, 20 * min + 100),
            Event(AppSessions.STARTUP, "android", 600 * min),
            front(other, 601 * min)
        )
        assertEquals(10 * min + 100, flat.total)
        assertFalse(flat.inFront)
    }

    @Test
    fun noSessionsNoTimes() {
        val s = of(front(other, 10 * min), away(other, 20 * min))
        assertEquals(0, s.opens)
        assertEquals(0L, s.total)
        assertNull(s.lastUsed)
        assertNull(s.longestAt)
    }
}
