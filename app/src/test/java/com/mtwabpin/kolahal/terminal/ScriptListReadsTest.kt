package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.ScriptRepository.Fingerprint
import com.mtwabpin.kolahal.data.ScriptRepository.Run
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * The script list's one writer: the list written last is the one read last, the log's reload reads
 * only after a change, and a recorded run reads no code.
 */
class ScriptListReadsTest {
    private fun script(id: Long, lastRun: Long? = null, status: String? = null) =
        Script(id, "s$id", ScriptLang.LUA, "-- code $id", 0, true, false, lastRun, status)

    /** The scripts as stored: what [ScriptListReads] reads whole. */
    @Volatile
    private var stored = listOf(script(1), script(2))

    @Volatile
    private var set = "a"
    private val reads = AtomicInteger()
    private val written = java.util.Collections.synchronizedList(mutableListOf<List<Script>>())

    private fun print() = Fingerprint(set, stored.associate { it.id to Run(it.lastRun, it.lastStatus) })

    private val lists = ScriptListReads(
        fingerprint = ::print,
        all = { reads.incrementAndGet(); stored },
        write = { written += it })

    @Test
    fun theReloadReadsOnlyAfterAChange() = runBlocking {
        assertTrue(lists.readIfChanged())
        assertFalse(lists.readIfChanged())
        set = "b"
        assertTrue(lists.readIfChanged())
        assertFalse(lists.readIfChanged())
        assertEquals(2, reads.get())
        assertEquals(2, written.size)
    }

    /** A read after a change on the Scripts screen leaves the reload's own check be: it still sees the change (and reads the status lines again). */
    @Test
    fun aReadDoesNotHideAChangeFromTheReload() = runBlocking {
        assertTrue(lists.readIfChanged())
        set = "b"
        lists.read()
        assertTrue(lists.readIfChanged())
        assertEquals(3, reads.get())
    }

    /** A run recorded in the script process: the list gets its time and result, and no script is read again. */
    @Test
    fun aRunReadsNoCode() = runBlocking {
        assertTrue(lists.readIfChanged())
        val first = written.last()
        stored = listOf(script(1, lastRun = 5, status = "ok"), script(2))
        assertFalse(lists.readIfChanged())
        assertEquals(1, reads.get())
        val now = written.last()
        assertEquals(stored, now)
        // The script that didn't run is the same as before: what follows from it needn't look again.
        assertSame(first[1], now[1])
        assertFalse(lists.readIfChanged())
        assertEquals(2, written.size)
    }

    /** A run with a change beside it (a script added, edited or switched) reads every script. */
    @Test
    fun aRunWithAChangeReadsEverything() = runBlocking {
        assertTrue(lists.readIfChanged())
        stored = listOf(script(1, lastRun = 5, status = "ok"), script(2))
        set = "b"
        assertTrue(lists.readIfChanged())
        assertEquals(2, reads.get())
        assertEquals(stored, written.last())
    }

    /** A script screen read the list after a run: the next run patches that newer list. */
    @Test
    fun aRunPatchesTheListReadLast() = runBlocking {
        assertTrue(lists.readIfChanged())
        stored = listOf(script(1, lastRun = 9, status = "error"), script(2))
        lists.read()
        stored = listOf(script(1, lastRun = 9, status = "error"), script(2, lastRun = 10, status = "ok"))
        assertFalse(lists.readIfChanged())
        assertEquals(stored, written.last())
        assertEquals(2, reads.get())
    }

    @Test
    fun aScriptMissingFromTheRunsNeedsAWholeRead() {
        val list = listOf(script(1), script(2))
        assertNull(ScriptRepository.withRuns(list, mapOf(1L to Run(null, null))))
        assertEquals(
            listOf(script(1, 3, "ok"), script(2)),
            ScriptRepository.withRuns(list, mapOf(1L to Run(3, "ok"), 2L to Run(null, null))),
        )
    }

    /** Reads from many threads at once: each is written in the order it was read, so the newest read is written last. */
    @Test
    fun anOlderReadIsNeverWrittenLast() = runBlocking {
        val order = java.util.Collections.synchronizedList(mutableListOf<Int>())
        val slow = ScriptListReads(
            fingerprint = ::print,
            all = {
                val n = reads.incrementAndGet().also { if (it % 2 == 1) Thread.sleep(2) }
                listOf(script(n.toLong()))
            },
            write = { order += it.single().id.toInt() },
        )
        (1..200).map { i ->
            async(Dispatchers.Default) {
                if (i % 3 == 0) slow.readIfChanged().also { set = "p$i" } else slow.read()
            }
        }.awaitAll()
        val seen = order.toList()
        assertEquals(seen.sorted(), seen)
        assertEquals(reads.get(), seen.last())
    }
}
