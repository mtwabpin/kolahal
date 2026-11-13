package com.mtwabpin.kolahal.unlock

import com.mtwabpin.kolahal.data.Stores
import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.KvStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Without Usage Access, unlocks are counted as they happen. The counts are kept in the database, so
 * the :script process reads today's count too (`device.unlocks()`), not what it read when it started.
 */
@RunWith(AndroidJUnit4::class)
class UnlockCounterTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val counter = UnlockCounter.get(context)
    private val kv = KvStore(context, Stores.UNLOCKS)
    private val days = listOf("d:2001-02-03", "d:2001-02-04")

    @After fun tearDown() {
        days.forEach { kv.set(it, null) }
        context.getSharedPreferences("unlocks", Context.MODE_PRIVATE).edit(commit = true) { days.forEach(::remove) }
    }

    /** Counts from before 0.5.7, in the "unlocks" prefs file, move to the database once; a count there already stays. */
    @Test fun countsFromBeforeMoveToTheDatabase() {
        kv.set(days[1], "7")
        val prefs = context.getSharedPreferences("unlocks", Context.MODE_PRIVATE)
        prefs.edit(commit = true) {
            putInt(days[0], 3)
            putInt(days[1], 1)
        }
        counter.moveFromPrefs()
        assertEquals("3", kv.get(days[0]))
        assertEquals("7", kv.get(days[1]))
        assertTrue(prefs.all.toString(), prefs.all.isEmpty())
    }

    @Test fun withoutUsageAccessTheCountComesFromTheDatabase() {
        assumeFalse("Usage Access is on: the count comes from Android", counter.hasUsageAccess())
        kv.set(days[0], "5")
        assertEquals(5, counter.countFor(LocalDate.of(2001, 2, 3)))
        assertEquals(0, counter.countFor(LocalDate.of(2001, 2, 4)))
    }
}
