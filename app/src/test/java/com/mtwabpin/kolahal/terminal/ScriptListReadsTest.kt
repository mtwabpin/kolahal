package com.mtwabpin.kolahal.terminal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/** The script list's one writer: the list written last is the one read last, and the log's reload reads only after a change. */
class ScriptListReadsTest {
    @Volatile
    private var print = "a"
    private val reads = AtomicInteger()
    private val written = java.util.Collections.synchronizedList(mutableListOf<Int>())
    private val lists = ScriptListReads<Int>(
        fingerprint = { print },
        all = { reads.incrementAndGet() },
        write = { written += it })

    @Test
    fun theReloadReadsOnlyAfterAChange() = runBlocking {
        assertTrue(lists.readIfChanged())
        assertFalse(lists.readIfChanged())
        print = "b"
        assertTrue(lists.readIfChanged())
        assertFalse(lists.readIfChanged())
        assertEquals(listOf(1, 2), written.toList())
    }

    /** A read after a change on the Scripts screen leaves the reload's own check be: it still sees the change (and reads the status lines again). */
    @Test
    fun aReadDoesNotHideAChangeFromTheReload() = runBlocking {
        assertTrue(lists.readIfChanged())
        print = "b"
        lists.read()
        assertTrue(lists.readIfChanged())
        assertEquals(listOf(1, 2, 3), written.toList())
    }

    /** Reads from many threads at once: each is written in the order it was read, so the newest read is written last. */
    @Test
    fun anOlderReadIsNeverWrittenLast() = runBlocking {
        val slow = ScriptListReads<Int>(
            fingerprint = { print },
            all = { reads.incrementAndGet().also { if (it % 2 == 1) Thread.sleep(2) } },
            write = { written += it },
        )
        (1..200).map { i ->
            async(Dispatchers.Default) {
                if (i % 3 == 0) slow.readIfChanged().also { print = "p$i" } else slow.read()
            }
        }.awaitAll()
        val order = written.toList()
        assertEquals(order.sorted(), order)
        assertEquals(reads.get(), order.last())
    }
}
