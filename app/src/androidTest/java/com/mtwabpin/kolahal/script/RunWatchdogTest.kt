package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The watchdog on its own: past a run's timeout and its grace, or with native memory grown past
 * its limit, it says why and would stop the :script process. Here, in the main process, it only says so.
 */
@RunWith(AndroidJUnit4::class)
class RunWatchdogTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val before = logs.maxId()
    private val host = HostApi(context, "wdtest", scriptId = null, notifyEnabled = false)

    @After fun tearDown() = logs.recent(50).filter { it.id > before }.forEach { logs.delete(it.id) }

    private fun said() = logs.recent(50).filter { it.id > before && it.source == "wdtest" && it.kind == LogKind.ERROR }.map { it.text }

    private fun waitFor(what: String, ms: Long, check: () -> Boolean) {
        val until = System.currentTimeMillis() + ms
        while (!check() && System.currentTimeMillis() < until) Thread.sleep(50)
        assertTrue(what, check())
    }

    @Test fun aRunPastItsTimeAndTheGraceIsStopped() {
        val watchdog = RunWatchdog(host, timeoutMs = 100, nativeLimitMb = null)
        try {
            waitFor("the watchdog's line", ms = 10_000) { said().isNotEmpty() }
            assertEquals(listOf("wdtest: still running after timeout, killed script process"), said())
        } finally {
            watchdog.close()
        }
    }

    /** A run that ends in time closes it: nothing is said past the grace. */
    @Test fun aRunThatEndsInTimeIsLeftAlone() {
        RunWatchdog(host, timeoutMs = 100, nativeLimitMb = null).close()
        Thread.sleep(5_500)
        assertEquals(emptyList<String>(), said())
    }

    /** Native memory grown past the limit stops the run before its time: a picture's pixels are native memory. */
    @Test fun nativeMemoryPastTheLimitIsStopped() {
        val watchdog = RunWatchdog(host, timeoutMs = 60_000, nativeLimitMb = 4)
        val big = android.graphics.Bitmap.createBitmap(2_000, 2_000, android.graphics.Bitmap.Config.ARGB_8888)
        try {
            waitFor("the watchdog's line", ms = 5_000) { said().isNotEmpty() }
            assertEquals(listOf("wdtest: used over 4 MB, killed script process"), said())
        } finally {
            watchdog.close()
            big.recycle()
        }
    }
}
