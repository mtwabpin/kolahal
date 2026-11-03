package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The ids a backup's log saved, against the lines that came along: what keeps new lines in view after a restore. */
class FullBackupTest {
    /** Every id the log keeps, so one added later is tested too. */
    private val ids = LogRepository.ID_KEYS.toList()

    private fun saved(vararg entries: Pair<String, Long>) = entries.associate { (key, id) -> key to mapOf("l" to id) }

    private fun id(entries: Map<*, *>, key: String) = ((entries[key] as Map<*, *>)["l"] as Number).toLong()

    /** The lines up to them stayed behind or are gone: each comes down to the newest line there is. */
    @Test fun idsPastTheNewestLineComeDownToIt() {
        val back = FullBackup.logIdsWithin(saved(*ids.map { it to 1_000L }.toTypedArray()), newest = 994)
        for (key in ids) assertEquals(key, 994L, id(back, key))
    }

    @Test fun idsWithinTheLogStayAsTheyWere() {
        val within = saved("hidden_up_to" to 300, "cleared_up_to" to 994, "seen_up_to" to 0)
        assertEquals(within, FullBackup.logIdsWithin(within, newest = 994))
    }

    /** All of them stayed behind or were cleared: nothing restored, and nothing to hide. */
    @Test fun anEmptyLogHidesNothing() {
        val back = FullBackup.logIdsWithin(saved(*ids.map { it to 1_000L }.toTypedArray()), newest = 0)
        for (key in ids) assertEquals(key, 0L, id(back, key))
    }

    /** JSON reads a small number as an Int. */
    @Test fun aSmallIdFromAFileCountsToo() {
        val back = FullBackup.logIdsWithin(mapOf("hidden_up_to" to mapOf("l" to 1_000)), newest = 994)
        assertEquals(994L, id(back, "hidden_up_to"))
    }

    /** An edited file can give an id as text, or as anything: the log reads only numbers, so it gets one or none. */
    @Test fun anIdThatIsntANumberIsReadAsOneOrLeftOut() {
        val back = FullBackup.logIdsWithin(
            mapOf("hidden_up_to" to mapOf("s" to "300"), "cleared_up_to" to mapOf("s" to "lots"), "seen_up_to" to mapOf("b" to true), "purge_up_to" to "5"),
            newest = 994,
        )
        assertEquals(mapOf("hidden_up_to" to mapOf("l" to 300L)), back)
    }

    /** A time and a filter aren't ids; an id never saved stays unsaved (none seen yet means nothing is unread). */
    @Test fun whatIsntASavedIdIsLeftAlone() {
        val others = mapOf("trimmed_at" to mapOf("l" to 1_790_000_000_000L), "filter" to mapOf("s" to "only feeds"))
        assertEquals(others, FullBackup.logIdsWithin(others, newest = 994))
    }

    /** What a restore promises: each restored line is hidden or shown as before, and the next line logged shows. */
    @Test fun restoredLinesStayAsTheyWereAndTheNextLineShows() {
        val restored = listOf(3L, 7L, 9L)
        val newest = restored.max()
        for (hiddenUpTo in listOf(0L, 3L, 5L, 7L, 9L, 10L, 1_000L)) {
            val back = id(FullBackup.logIdsWithin(saved("hidden_up_to" to hiddenUpTo), newest), "hidden_up_to")
            for (line in restored) assertEquals("line $line, hidden up to $hiddenUpTo", line <= hiddenUpTo, line <= back)
            assertTrue("hidden up to $hiddenUpTo", newest + 1 > back)
        }
    }
}
