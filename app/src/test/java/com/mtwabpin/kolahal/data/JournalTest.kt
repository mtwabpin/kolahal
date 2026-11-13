package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The log's lines removed, and its lines changed in place, as home asks for them: "what came after the last I applied?" */
class JournalTest {
    @Test fun beforeAnyChangeThereIsNothing() {
        val journal = Journal(kept = 3)
        assertEquals(0L, journal.seq)
        assertEquals(emptySet<Long>(), journal.since(0))
    }

    @Test fun everyChangeHasANumber() {
        val journal = Journal(kept = 10)
        journal.add(listOf(7L))
        assertEquals(1L, journal.seq)
        journal.add(listOf(8L, 9L))
        assertEquals(3L, journal.seq)
        journal.add(emptyList())
        assertEquals(3L, journal.seq)
    }

    @Test fun onlyWhatCameAfterIsReturned() {
        val journal = Journal(kept = 10)
        journal.add(listOf(7L))
        val seen = journal.seq
        journal.add(listOf(8L, 9L))
        assertEquals(setOf(8L, 9L), journal.since(seen))
        assertEquals(setOf(7L, 8L, 9L), journal.since(0))
        assertEquals(setOf(9L), journal.since(2))
        assertEquals(emptySet<Long>(), journal.since(journal.seq))
    }

    /** A line changed twice is one line to read again. */
    @Test fun aLineChangedTwiceIsThereOnce() {
        val journal = Journal(kept = 10)
        journal.add(listOf(5L, 5L, 6L))
        assertEquals(setOf(5L, 6L), journal.since(0))
        assertEquals(3L, journal.seq)
    }

    /** Only the last few are remembered. Asked about what it forgot, it says it can't know: the page is read again. */
    @Test fun whatIsTooLongAgoIsNotKnown() {
        val journal = Journal(kept = 3)
        journal.add(listOf(1L, 2L, 3L, 4L, 5L))
        assertEquals(5L, journal.seq)
        // Changes 3, 4 and 5 are remembered: after change 2, they are all of it.
        assertEquals(setOf(3L, 4L, 5L), journal.since(2))
        assertEquals(setOf(5L), journal.since(4))
        // After change 1 there was change 2 as well, which is forgotten.
        assertNull(journal.since(1))
        assertNull(journal.since(0))
    }

    /** A number from before the first change (a page of another start) is never known. */
    @Test fun aNumberBeforeTheFirstIsNotKnown() {
        val journal = Journal(kept = 3)
        assertNull(journal.since(-1))
        journal.add(listOf(1L))
        assertNull(journal.since(-1_000))
        assertEquals(setOf(1L), journal.since(0))
    }

    /** Home can be ahead only if it asked another journal; it gets nothing, not an error. */
    @Test fun aNumberPastTheLatestHasNothing() {
        val journal = Journal(kept = 3)
        journal.add(listOf(1L))
        assertEquals(emptySet<Long>(), journal.since(99))
    }

    /** Writes come from several threads (commands, the broadcast from scripts): every change is counted once. */
    @Test fun changesFromSeveralThreadsAreAllCounted() {
        val journal = Journal(kept = 10_000)
        val threads = (0 until 8).map { t -> Thread { repeat(500) { journal.add(listOf(t * 1_000L + it)) } } }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
        assertEquals(4_000L, journal.seq)
        assertEquals(4_000, journal.since(0)!!.size)
    }
}
