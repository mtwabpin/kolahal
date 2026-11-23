package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/** The start-up and the boot receiver set the alarms once in a process, and the second waits for the first. */
class RunOnceTest {
    @Test
    fun theSecondCallerWaitsForTheFirstRunAndDoesNotRunIt() {
        val gate = RunOnce()
        val runs = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CountDownLatch(1)
        val firstEnded = AtomicBoolean(false)
        val first = Thread {
            gate.once {
                started.countDown()
                release.await()
                runs.incrementAndGet()
                firstEnded.set(true)
                true
            }
        }
        first.start()
        assertTrue(started.await(5, TimeUnit.SECONDS))

        val secondReturned = CountDownLatch(1)
        val endedWhenSecondReturned = AtomicBoolean(false)
        val second = Thread {
            gate.once { runs.incrementAndGet(); true }
            endedWhenSecondReturned.set(firstEnded.get())
            secondReturned.countDown()
        }
        second.start()
        // While the first runs, the second doesn't return.
        assertFalse(secondReturned.await(300, TimeUnit.MILLISECONDS))

        release.countDown()
        assertTrue(secondReturned.await(5, TimeUnit.SECONDS))
        first.join(5_000)
        second.join(5_000)
        assertTrue("the second returns once the first run has ended", endedWhenSecondReturned.get())
        assertEquals("the second doesn't run it again", 1, runs.get())

        // A later call that must always run (the exact-alarm permission changed) runs it again.
        gate.again { runs.incrementAndGet(); true }
        assertEquals(2, runs.get())
        gate.once { runs.incrementAndGet(); true }
        assertEquals(2, runs.get())
    }

    @Test
    fun aRunThatFailedIsTriedByTheNextCaller() {
        val gate = RunOnce()
        val runs = AtomicInteger()
        gate.once { runs.incrementAndGet(); false }
        gate.once { runs.incrementAndGet(); true }
        assertEquals(2, runs.get())
        gate.once { runs.incrementAndGet(); true }
        assertEquals(2, runs.get())
    }
}
