package com.mtwabpin.kolahal.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogKept
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** What the log keeps: `log.keep-lines`, typed commands counted apart, and each line's context whole. */
@RunWith(AndroidJUnit4::class)
class LogTrimTest {
    // A trim here deletes the phone's older lines too: the whole log comes back after each test.
    @get:Rule val logKept = LogKept()
    private val logs = LogRepository.get(InstrumentationRegistry.getInstrumentation().targetContext)
    private val token = "trim${System.nanoTime()}"
    private val keepBefore = logs.keepLines

    private fun mine() = logs.recent(limit = 2_000).filter { it.text.startsWith(token) }

    @After fun tearDown() {
        logs.keepLines = keepBefore
        mine().forEach { logs.delete(it.id) }
    }

    @Test fun typedCommandsAreCountedApart() {
        repeat(20) { logs.add(LogKind.COMMAND, "$token command $it") }
        repeat(600) { logs.add(LogKind.OUTPUT, "$token output $it") }
        logs.trim(keep = 500)
        val left = mine()
        assertEquals(20, left.count { it.kind == LogKind.COMMAND })
        val outputs = left.filter { it.kind == LogKind.OUTPUT }.map { it.text }
        assertEquals(500, outputs.size)
        assertEquals("$token output 100", outputs.first())
    }

    /** Both kinds past the cap, mixed: each keeps its newest, and a second trim takes nothing more. */
    @Test fun eachKindKeepsItsNewestHoweverMixed() {
        repeat(600) {
            logs.add(LogKind.OUTPUT, "$token output $it")
            if (it < 510) logs.add(LogKind.COMMAND, "$token command $it")
        }
        logs.trim(keep = 500)
        val left = mine()
        val commands = left.filter { it.kind == LogKind.COMMAND }.map { it.text }
        val outputs = left.filter { it.kind == LogKind.OUTPUT }.map { it.text }
        assertEquals((10 until 510).map { "$token command $it" }, commands)
        assertEquals((100 until 600).map { "$token output $it" }, outputs)
        val reached = logs.trimmedUpTo
        logs.trim(keep = 500)
        assertEquals(1_000, mine().size)
        assertEquals(reached, logs.trimmedUpTo)
    }

    /** A trim says how far it reached, so home keeps a page the trim didn't touch. */
    @Test fun aTrimReportsHowFarItReached() {
        val ids = (0 until 510).map { logs.add(LogKind.OUTPUT, "$token output $it") }
        val structure = logs.structure
        logs.trim(keep = 500)
        assertEquals(structure, logs.structure)
        assertTrue(logs.trimmedUpTo >= ids[9])
        assertTrue(logs.trimmedUpTo < ids[10])
        assertEquals(500, mine().size)
    }

    /** Home trims on its own as the log grows, so notifications can't pile up between script runs. */
    @Test fun homeTrimsOnItsOwnAfterEnoughLines() {
        logs.keepLines = 500
        repeat(600) { logs.add(LogKind.OUTPUT, "$token output $it") }
        logs.trimIfOverdue()
        assertEquals(500, mine().size)
        // Not before enough lines came since: a trim at every line would cost a query each.
        repeat(LogRepository.TRIM_EVERY - 1) { logs.add(LogKind.OUTPUT, "$token more $it") }
        logs.trimIfOverdue()
        assertEquals(500 + LogRepository.TRIM_EVERY - 1, mine().size)
        logs.add(LogKind.OUTPUT, "$token last")
        logs.trimIfOverdue()
        assertEquals(500, mine().size)
    }

    @Test fun aContextTooBigKeepsItsFirstValuesAsWholeJson() {
        val many = (1..2_000).associate { "k$it" to "y".repeat(100) }
        val id = logs.add(LogKind.OUTPUT, "$token many", ctx = LogContext(mapOf(LogContext.SCRIPT to token) + many))
        val ctx = logs.recent(limit = 50).single { it.id == id }.ctx
        assertEquals(token, ctx.script)
        assertTrue(ctx.values.size.toString(), ctx.values.size in 2 until 2_001)
        assertTrue(LogRepository.jsonLength(ctx.values) <= LogRepository.MAX_FIELD)
    }

    @Test fun aValueThatWouldOverflowIsLeftOut() {
        val ctx = LogContext(
            linkedMapOf(LogContext.SCRIPT to token, "junk" to "\u0001".repeat(16_000), LogContext.URL to "https://example.com/$token"),
        )
        val id = logs.add(LogKind.OUTPUT, "$token junk", ctx = ctx)
        val stored = logs.recent(limit = 50).single { it.id == id }.ctx
        assertEquals(token, stored.script)
        assertEquals("https://example.com/$token", stored.url)
        assertFalse("junk" in stored.values)
    }

    @Test fun jsonLengthIsNeverShortOfOrgJson() {
        val samples = listOf(
            "", "plain", "a/b\"c\\d", "\t\b\n\r\u000c", "\u0001\u001f\u007f", "é😀",
            String(charArrayOf(Char(0x2028), Char(0x2029))),
        )
        for (s in samples) {
            val values = mapOf("k$s" to s, "url" to "https://example.com/?q=$s")
            val written = JSONObject(values).toString().length
            assertTrue("$s: $written", LogRepository.jsonLength(values) >= written)
        }
    }
}
