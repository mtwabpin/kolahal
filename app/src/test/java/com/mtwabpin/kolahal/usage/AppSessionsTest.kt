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
    private fun of(vararg e: Event) = AppSessions.of(e.toList(), app, from, until)

    @Test fun theKindsAreAndroids() {
        assertEquals(UsageEvents.Event.ACTIVITY_RESUMED, AppSessions.RESUMED)
        assertEquals(UsageEvents.Event.ACTIVITY_PAUSED, AppSessions.PAUSED)
        assertEquals(UsageEvents.Event.ACTIVITY_STOPPED, AppSessions.STOPPED)
        assertEquals(UsageEvents.Event.SCREEN_NON_INTERACTIVE, AppSessions.SCREEN_OFF)
        assertEquals(UsageEvents.Event.KEYGUARD_SHOWN, AppSessions.LOCKED)
        assertEquals(UsageEvents.Event.DEVICE_SHUTDOWN, AppSessions.SHUTDOWN)
    }

    /** Opened twice, with another app between: two opens, the longer session is the longest. */
    @Test fun eachTimeItComesToTheFrontIsAnOpen() {
        val s = of(front(app, 10 * min), away(app, 15 * min), front(other, 15 * min), away(other, 20 * min), front(app, 20 * min), away(app, 32 * min), front(other, 32 * min))
        assertEquals(2, s.opens)
        assertEquals(17 * min, s.total)
        assertEquals(12 * min, s.longest)
        assertEquals(20 * min, s.longestAt)
        assertEquals(32 * min, s.lastUsed)
        assertFalse(s.inFront)
    }

    /** Another screen of the same app, at once, is the same session and no open. */
    @Test fun anotherScreenOfTheAppIsNoOpen() {
        val s = of(front(app, 10 * min), away(app, 12 * min), front(app, 12 * min + 300), away(app, 20 * min), front(other, 20 * min))
        assertEquals(1, s.opens)
        assertEquals(10 * min, s.total)
    }

    /** Back to the app a while later, with nothing between (the screen of a call): a second open. */
    @Test fun aLateReturnIsAnotherOpen() {
        val s = of(front(app, 10 * min), away(app, 12 * min), front(app, 15 * min), away(app, 18 * min), front(other, 18 * min))
        assertEquals(2, s.opens)
        assertEquals(5 * min, s.total)
    }

    /** In front before the day began: its session starts at the day's start, and it is no open today. */
    @Test fun inFrontAtTheStart() {
        val s = of(away(app, 7 * min), front(other, 7 * min))
        assertEquals(0, s.opens)
        assertEquals(7 * min, s.total)
    }

    /** Still in front at the end: its session runs to the end, and it is in use. */
    @Test fun inFrontAtTheEnd() {
        val s = of(front(app, until - 30 * min))
        assertEquals(30 * min, s.total)
        assertTrue(s.inFront)
        assertEquals(until, s.lastUsed)
    }

    /** The screen goes off, the phone locks or shuts down: the session ends there, with no pause seen. */
    @Test fun theScreenOffEndsASession() {
        for (end in listOf(AppSessions.SCREEN_OFF, AppSessions.LOCKED, AppSessions.SHUTDOWN)) {
            val s = of(front(app, 10 * min), Event(end, "android", 25 * min), front(app, 40 * min), away(app, 41 * min), front(other, 41 * min))
            assertEquals("$end", 2, s.opens)
            assertEquals("$end", 16 * min, s.total)
        }
    }

    /** A session over an hour's end counts in both hours. */
    @Test fun hoursSplitASession() {
        val s = of(front(app, 50 * min), away(app, 70 * min), front(other, 70 * min))
        assertEquals(10 * min, s.hours[0])
        assertEquals(10 * min, s.hours[1])
        assertEquals(0L, s.hours[2])
    }

    @Test fun noSessionsNoTimes() {
        val s = of(front(other, 10 * min), away(other, 20 * min))
        assertEquals(0, s.opens)
        assertEquals(0L, s.total)
        assertNull(s.lastUsed)
        assertNull(s.longestAt)
    }
}
