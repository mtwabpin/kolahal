package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The log's marks after a restore, and as they come from the old prefs: what keeps new lines in view. */
class LogMarksTest {
    /** Every id the log keeps, so one added later is tested too. */
    private val ids = LogMarks.ID_KEYS.toList()

    private fun marks(vararg entries: Pair<String, Long>) = entries.associate { (key, id) -> key to id.toString() }

    /** The lines up to them stayed behind or are gone: each comes down to the newest line there is. */
    @Test fun idsPastTheNewestLineComeDownToIt() {
        val back = LogMarks.clamped(marks(*ids.map { it to 1_000L }.toTypedArray()), newest = 994)
        assertEquals(ids.associateWith { "994" }, back)
    }

    @Test fun idsWithinTheLogStayAsTheyWere() {
        assertEquals(emptyMap<String, String?>(), LogMarks.clamped(marks("hidden_up_to" to 300, "cleared_up_to" to 994, "seen_up_to" to 0), newest = 994))
    }

    /** All of them stayed behind or were cleared: nothing restored, and nothing to hide. */
    @Test fun anEmptyLogHidesNothing() {
        val back = LogMarks.clamped(marks(*ids.map { it to 1_000L }.toTypedArray()), newest = 0)
        assertEquals(ids.associateWith { "0" }, back)
    }

    /** The log reads these as numbers: one that isn't goes, rather than break it. */
    @Test fun anIdThatIsntANumberGoes() {
        assertEquals(mapOf("cleared_up_to" to null), LogMarks.clamped(mapOf("cleared_up_to" to "lots", "hidden_up_to" to "5"), newest = 994))
    }

    /** A time and the filter aren't ids; an id never saved stays unsaved (none seen yet means nothing is unread). */
    @Test fun whatIsntAnIdIsLeftAlone() {
        val others = mapOf("trimmed_at" to "1790000000000", "filter" to "only feeds")
        assertEquals(emptyMap<String, String?>(), LogMarks.clamped(others, newest = 994))
    }

    /** What a restore promises: each restored line is hidden or shown as before, and the next line logged shows. */
    @Test fun restoredLinesStayAsTheyWereAndTheNextLineShows() {
        val restored = listOf(3L, 7L, 9L)
        val newest = restored.max()
        for (hiddenUpTo in listOf(0L, 3L, 5L, 7L, 9L, 10L, 1_000L)) {
            val back = LogMarks.clamped(marks("hidden_up_to" to hiddenUpTo), newest)["hidden_up_to"]?.toLong() ?: hiddenUpTo
            for (line in restored) assertEquals("line $line, hidden up to $hiddenUpTo", line <= hiddenUpTo, line <= back)
            assertTrue("hidden up to $hiddenUpTo", newest + 1 > back)
        }
    }

    /**
     * The old prefs held numbers, and an edited backup could give one as text. Both come over as
     * numbers; text that isn't one, a switch, or a key the log never had doesn't. The filter is text.
     */
    @Test fun oldPrefsBecomeMarks() {
        assertEquals(
            mapOf("hidden_up_to" to "300", "cleared_up_to" to "5", "trimmed_at" to "1790000000000", "filter" to "only feeds"),
            LogMarks.fromPrefs(
                mapOf(
                    "hidden_up_to" to " 300 ", "cleared_up_to" to 5, "seen_up_to" to true, "purge_up_to" to "lots",
                    "trimmed_at" to 1_790_000_000_000L, "filter" to "only feeds", "other" to 1L,
                ),
            ),
        )
    }
}
