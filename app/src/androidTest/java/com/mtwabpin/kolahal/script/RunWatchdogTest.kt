package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** When one run takes the script process down, the others in it say so first, with what they had waiting. */
@RunWith(AndroidJUnit4::class)
class RunWatchdogTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val stuck = "wd-stuck${System.nanoTime() % 100_000}"
    private val other = "wd-other${System.nanoTime() % 100_000}"
    private val done = "wd-done${System.nanoTime() % 100_000}"

    private fun host(name: String) = HostApi(context, name, scriptId = null, notifyEnabled = false)
    private fun lines(name: String) = logs.recent(200).filter { it.source == name }.map { it.text }

    @After fun tearDown() = logs.recent(200).filter { it.source in setOf(stuck, other, done) }.forEach { logs.delete(it.id) }

    @Test fun otherRunsSayTheyWereStoppedToo() {
        val stuckRun = host(stuck)
        val otherRun = host(other)
        val doneRun = host(done)
        // A run that already ended isn't in the process any more.
        RunWatchdog(doneRun, 60_000, null).close()
        RunWatchdog(stuckRun, 60_000, null).use {
            RunWatchdog(otherRun, 60_000, null).use {
                otherRun.log("half way")
                RunWatchdog.stopOthers(stuckRun)
            }
        }
        assertEquals(listOf("half way", "$other: stopped, because $stuck had to be stopped (scripts run in one process)"), lines(other))
        assertEquals(emptyList<String>(), lines(stuck))
        assertEquals(emptyList<String>(), lines(done))
    }
}
