package com.mtwabpin.kolahal.shader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The first read of what people set and scripts gave ([Shaders.readSoon]): off the main thread, and
 * whatever needs it first ([Shaders.settingsRead]) has it, read once.
 */
class ShadersTest {
    @Test
    fun aReaderWaitsForTheReadUnderWay() {
        val reads = AtomicInteger()
        val started = CountDownLatch(1)
        val finish = CountDownLatch(1)
        var thread: Thread? = null
        Shaders.readSoon({
            started.countDown()
            finish.await(5, TimeUnit.SECONDS)
            reads.incrementAndGet()
        }) { run -> thread = Thread(run).also { it.start() } }
        assertTrue(started.await(5, TimeUnit.SECONDS))

        val seen = AtomicInteger(-1)
        val reader = Thread {
            Shaders.settingsRead()
            seen.set(reads.get())
        }
        reader.start()
        reader.join(100)
        assertTrue("it waits while the read goes on", reader.isAlive)

        finish.countDown()
        reader.join(5_000)
        thread?.join(5_000)
        assertEquals("it sees what was read", 1, seen.get())
        assertEquals("read once", 1, reads.get())
    }

    @Test
    fun aReaderBeforeTheThreadReadsItOnItsOwn() {
        val reads = AtomicInteger()
        var later: (() -> Unit)? = null
        Shaders.readSoon({ reads.incrementAndGet() }) { run -> later = run }

        Shaders.settingsRead()
        assertEquals(1, reads.get())
        // The thread's turn comes after: nothing is read again.
        later!!()
        Shaders.settingsRead()
        assertEquals(1, reads.get())
    }
}
