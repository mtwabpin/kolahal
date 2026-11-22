package com.mtwabpin.kolahal.terminal

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Home's coming and going: when the log's page is read, what arrived while away, and log.fresh's clean screen. */
class HomeSessionTest {
    private class FakeHost : HomeSession.Host {
        override var newest: Long? = null
        override var storedSeenUpTo: Long = Long.MAX_VALUE
        override var freshMinutes: Long = 10
        var max = 0L
        var maxFails = false
        var hidden: Long? = null
        var reloads = 0
        override var rowsMarks: Pair<Long, Long>? = null

        /** What the session asked for, in order. */
        val events = ArrayList<String>()

        override fun dropScreen(seen: Long) {
            events += "drop $seen"
        }

        override fun hideScreen(seen: Long) {
            events += "hide $seen"
            hidden = seen
        }

        override fun maxId(): Long = if (maxFails) error("no database") else max
        override fun requestReload() {
            reloads++
        }
    }

    private var now = 1_000_000L
    private val host = FakeHost()

    // Unconfined: what onResume launches has run when it returns.
    private val session = HomeSession(host, Dispatchers.Unconfined) { now }
    private val scope = CoroutineScope(Dispatchers.Unconfined)

    private fun away(minutes: Long) {
        session.onPause()
        session.onStarted(false)
        now += minutes * 60_000L
        session.onStarted(true)
        session.onResume(scope)
    }

    @Test
    fun backAfterAStopTheLogIsReadOnceAtOnce() {
        session.onStarted(false)
        assertFalse(session.reading.value)
        session.onStarted(true)
        session.onResume(scope)
        assertTrue(session.reading.value)
        assertTrue(session.back)
        assertEquals(0, host.reloads)
    }

    @Test
    fun aResumeWithoutAStopReadsThePageAgain() {
        session.onResume(scope)
        assertEquals(1, host.reloads)
        assertFalse(session.back)
    }

    /** At start, the first read may already have built the rows with the marks of this resume: it isn't read twice. */
    @Test
    fun rowsWithTheNewMarksAreNotReadAgain() {
        host.storedSeenUpTo = 42
        host.max = 50
        host.rowsMarks = 42L to 50L
        session.onResume(scope)
        assertEquals(0, host.reloads)
        // Built before the marks were known: read again, so the lines that came while away are marked.
        host.rowsMarks = Long.MAX_VALUE to Long.MAX_VALUE
        session.onResume(scope)
        assertEquals(1, host.reloads)
    }

    @Test
    fun aResumeWhileStoppedReadsNothing() {
        session.onStarted(false)
        session.onResume(scope)
        assertFalse(session.reading.value)
        assertEquals(0, host.reloads)
    }

    @Test
    fun leavingKeepsTheNewestLineAsSeen() {
        host.newest = 42
        host.max = 50
        away(1)
        assertEquals(42L, host.storedSeenUpTo)
        assertEquals(42L, session.seenUpTo)
        assertEquals(50L, session.unreadUntil)
    }

    @Test
    fun afterLongEnoughAwayOnlyTheSeenLinesAreHidden() {
        host.newest = 42
        away(10)
        assertEquals(42L, host.hidden)
    }

    /**
     * The seen lines leave the screen before onResume returns, so no frame of the return shows them.
     * The hide is written and the page read after, on another thread.
     */
    @Test
    fun theSeenLinesLeaveTheScreenBeforeTheHideIsWritten() {
        host.newest = 42
        host.max = 50
        val queued = ArrayList<Runnable>()
        val later = CoroutineScope(java.util.concurrent.Executor { queued += it }.asCoroutineDispatcher())
        session.onPause()
        session.onStarted(false)
        now += 10 * 60_000L
        session.onStarted(true)
        session.onResume(later)
        assertEquals(listOf("drop 42"), host.events)
        assertNull(host.hidden)
        while (queued.isNotEmpty()) queued.removeAt(0).run()
        assertEquals(listOf("drop 42", "hide 42"), host.events)
        assertTrue(session.reading.value)
    }

    @Test
    fun aShortTimeAwayHidesNothing() {
        host.newest = 42
        away(9)
        assertNull(host.hidden)
        assertTrue(host.events.isEmpty())
    }

    @Test
    fun freshOffHidesNothing() {
        host.newest = 42
        host.freshMinutes = 0
        away(60)
        assertNull(host.hidden)
    }

    @Test
    fun nothingSeenYetHidesNothing() {
        away(60)
        assertNull(host.hidden)
    }

    @Test
    fun theFirstResumeHidesNothing() {
        host.storedSeenUpTo = 42
        session.onResume(scope)
        assertNull(host.hidden)
    }

    @Test
    fun aFailedReadKeepsTheLastUnreadMark() {
        host.newest = 42
        host.max = 50
        away(1)
        host.maxFails = true
        away(1)
        assertEquals(50L, session.unreadUntil)
    }
}
