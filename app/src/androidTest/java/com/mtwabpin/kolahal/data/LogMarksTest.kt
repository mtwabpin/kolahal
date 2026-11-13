package com.mtwabpin.kolahal.data

import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The log's marks in the database: those from before 0.5.8 move over from the "logs" prefs once, a
 * restore leaves none past the newest line, and the script process reads what home wrote.
 */
@RunWith(AndroidJUnit4::class)
class LogMarksTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val marks = logs.marks
    private val old = context.getSharedPreferences("logs", Context.MODE_PRIVATE)
    private var saved = emptyMap<String, String>()

    @Before fun setUp() {
        saved = marks.all()
    }

    @After fun tearDown() {
        old.edit(commit = true) { clear() }
        marks.replaceAll(saved)
    }

    /** After the update, the old file's marks are in the database, and they win: the file has some only then, or after an older backup. */
    @Test fun marksFromBeforeMoveToTheDatabaseOnce() {
        marks.set(LogMarks.SEEN_UP_TO to 40L, LogMarks.HIDDEN_UP_TO to 2L)
        marks.flush()
        old.edit(commit = true) {
            putLong(LogMarks.HIDDEN_UP_TO, 7L)
            putString(LogMarks.FILTER, "only feeds")
        }
        marks.moveFromPrefs()
        assertTrue(old.all.toString(), old.all.isEmpty())
        assertEquals(7L, marks.long(LogMarks.HIDDEN_UP_TO))
        assertEquals("only feeds", marks.string(LogMarks.FILTER))
        assertEquals("kept, as the file had none", 40L, marks.long(LogMarks.SEEN_UP_TO))
        assertEquals("only once", 7L, marks.also { it.moveFromPrefs() }.long(LogMarks.HIDDEN_UP_TO))
    }

    /** A restore's marks past the newest line come down to it, from the database or from an older backup's file. */
    @Test fun aRestoreLeavesNoMarkPastTheNewestLine() {
        val newest = logs.maxId()
        marks.set(LogMarks.HIDDEN_UP_TO to newest + 50, LogMarks.SEEN_UP_TO to newest, LogMarks.CLEARED_UP_TO to "lots")
        old.edit(commit = true) { putLong(LogMarks.HISTORY_FROM, newest + 9) }
        marks.restored(newest)
        assertEquals(newest, marks.long(LogMarks.HIDDEN_UP_TO))
        assertEquals(newest, marks.long(LogMarks.SEEN_UP_TO))
        assertEquals(newest, marks.long(LogMarks.HISTORY_FROM))
        assertNull("not a number: gone", marks.string(LogMarks.CLEARED_UP_TO))
    }

    /** The :script process keeps nothing in memory: it reads each mark as home last wrote it. */
    @Test fun theScriptProcessReadsWhatHomeWrote() {
        val elsewhere = LogMarks(context, inMemory = false)
        marks.set(LogMarks.HIDDEN_UP_TO to 11L)
        marks.flush()
        assertEquals(11L, elsewhere.long(LogMarks.HIDDEN_UP_TO))
        marks.set(LogMarks.HIDDEN_UP_TO to 12L)
        marks.flush()
        assertEquals(12L, elsewhere.long(LogMarks.HIDDEN_UP_TO))
        // It never moves the old file's marks: only the main process does.
        old.edit(commit = true) { putLong(LogMarks.HIDDEN_UP_TO, 3L) }
        elsewhere.moveFromPrefs()
        assertEquals(3L, old.getLong(LogMarks.HIDDEN_UP_TO, 0L))
    }

    /** Home changes a mark on the main thread: memory has it at once, and the database gets every change in order. */
    @Test fun changesReachTheDatabaseInOrder() {
        val elsewhere = LogMarks(context, inMemory = false)
        for (id in 1L..50L) marks.set(LogMarks.SEEN_UP_TO to id)
        assertEquals("in memory at once", 50L, marks.long(LogMarks.SEEN_UP_TO))
        marks.flush()
        assertEquals("the last one wins in the database", 50L, elsewhere.long(LogMarks.SEEN_UP_TO))
    }
}
