package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository.Pending
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch

class PipesTest {
    private fun lines(vararg t: String) = t.mapIndexed { i, s -> Pending(LogKind.OUTPUT, s, null, LogContext.EMPTY, i.toLong()) }
    private fun run(stages: String, vararg t: String) = Pipes.apply(lines(*t), Pipes.split("x | $stages").drop(1)).getOrThrow().map { it.text }

    @Test fun splitOnlyOnKnownFilters() {
        assertEquals(listOf("help", "grep app", "head 3"), Pipes.split("help | grep app | head 3"))
        assertEquals(listOf("js a || b"), Pipes.split("js a || b"))
        assertEquals(listOf("lua return 1 | 2"), Pipes.split("lua return 1 | 2"))
    }

    /** The key bar's `|` adds no spaces. */
    @Test fun spacesAroundThePipeAreOptional() {
        for (line in listOf("help|grep feed", "help |grep feed", "help| grep feed", "help | grep feed")) {
            assertEquals(line, listOf("help", "grep feed"), Pipes.split(line))
        }
        // Still not a pipe: `||` is code, whatever follows it.
        assertEquals(listOf("js a ||grep"), Pipes.split("js a ||grep"))
        assertEquals(listOf("js a || grep"), Pipes.split("js a || grep"))
    }

    /** What worked before the spaces were optional means what it meant. */
    @Test fun aPipeWithNoSpaceBeforeItStaysInWordsKeptAsTyped() {
        assertEquals(listOf("help", "grep head|tail"), Pipes.split("help | grep head|tail"))
        assertEquals(listOf("help", "grep sort|count", "head 3"), Pipes.split("help | grep sort|count | head 3"))
        for (line in listOf(
            "lua print(x|count)", "js a|head", "=1|count", "echo \"a|head\"", "echo 'a|head'", "todo buy milk|sort stuff", "note a|head",
            "url https://x.test/?q=a|sort", "web https://x.test/?q=a|sort", "alias x = lua a|count", "config set keybar.row1 a|head",
        )) {
            assertEquals(line, listOf(line), Pipes.split(line))
        }
        // With a space before it, it's a filter, as it always was.
        assertEquals(listOf("help", "grep head", "tail"), Pipes.split("help | grep head |tail"))
        assertEquals(listOf("todo", "sort"), Pipes.split("todo |sort"))
    }

    @Test fun spacedPutsASpaceAroundEachFiltersPipe() {
        assertEquals("hf | head 5", Pipes.spaced("hf|head 5"))
        assertEquals("help | grep head|tail | sort", Pipes.spaced("help|grep head|tail | sort"))
        assertEquals("lua a|count", Pipes.spaced("lua a|count"))
        assertEquals("help", Pipes.spaced(" help "))
    }

    @Test fun grepIsCaseInsensitiveAndInvertible() {
        assertEquals(listOf("Apple", "pineapple"), run("grep apple", "Apple", "pear", "pineapple"))
        assertEquals(listOf("pear"), run("grep -v apple", "Apple", "pear", "pineapple"))
        assertEquals("bad regex falls back to a literal", listOf("a(b"), run("grep a(", "a(b", "ab"))
    }

    /** A quick match gives its result; one that fails says why. */
    @Test fun aMatchWithinTheLimit() {
        val limit = MatchLimit(ms = 5_000, maxThreads = 2)
        assertEquals(2, limit.run { 1 + 1 })
        val failure = assertThrows(IllegalStateException::class.java) { limit.run<Int> { error("broken") } }
        assertEquals("broken", failure.message)
    }

    /** The real regex engine is native, so `GrepOnDeviceTest` runs a real pattern: this one just takes too long. */
    @Test fun aSlowMatchStopsBeingWaitedFor() {
        val release = CountDownLatch(1)
        val limit = MatchLimit(ms = 50, maxThreads = 2)
        try {
            val started = System.currentTimeMillis()
            val failure = assertThrows(IllegalArgumentException::class.java) { limit.run { release.await() } }
            assertTrue(failure.message!!, "too slow" in failure.message!!)
            assertTrue("not left waiting", System.currentTimeMillis() - started < 5_000)
        } finally {
            release.countDown()
        }
    }

    /** A native match can't be stopped, so slow ones can't pile up; as they end, there's room again. */
    @Test fun slowMatchesCantPileUp() {
        val release = CountDownLatch(1)
        val limit = MatchLimit(ms = 50, maxThreads = 2)
        try {
            repeat(2) { assertThrows(IllegalArgumentException::class.java) { limit.run { release.await() } } }
            val refused = assertThrows(IllegalArgumentException::class.java) { limit.run { 1 } }
            assertTrue(refused.message!!, "still running" in refused.message!!)
        } finally {
            release.countDown()
        }
        var result: Result<Int>
        val until = System.currentTimeMillis() + 5_000
        do {
            result = runCatching { limit.run { 1 } }
        } while (result.isFailure && System.currentTimeMillis() < until)
        assertEquals(1, result.getOrThrow())
    }

    @Test fun headTailCountSortUniq() {
        assertEquals(listOf("1", "2"), run("head 2", "1", "2", "3"))
        assertEquals(listOf("3"), run("tail 1", "1", "2", "3"))
        assertEquals(listOf("2"), run("grep a | count", "a", "b", "ca"))
        assertEquals(listOf("a", "b", "c"), run("sort", "c", "a", "b"))
        assertEquals(listOf("a", "b"), run("uniq", "a", "a", "b"))
        // Every repeat, not only one next to its first, as Unix's uniq would.
        assertEquals(listOf("a", "b"), run("uniq", "a", "b", "a"))
    }

    @Test fun errorsPassThrough() {
        val input = lines("x") + Pending(LogKind.ERROR, "boom", null, LogContext.EMPTY, 5)
        val out = Pipes.apply(input, listOf("grep nothing")).getOrThrow()
        assertEquals(listOf("boom"), out.map { it.text })
        // Ahead of what the filters leave, so it isn't cut away by head or tail.
        val mixed = lines("a") + Pending(LogKind.ERROR, "boom", null, LogContext.EMPTY, 5) + lines("b", "c")
        assertEquals(listOf("boom", "c"), Pipes.apply(mixed, listOf("tail 1")).getOrThrow().map { it.text })
    }

    @Test fun unknownStageFails() {
        assertTrue(Pipes.apply(lines("a"), listOf("grepx")).isFailure)
    }

    @Test fun headAndTailAcceptDashN() {
        val t = (1..12).map { "l$it" }.toTypedArray()
        assertEquals(listOf("l1", "l2", "l3"), run("head -n 3", *t))
        assertEquals(listOf("l11", "l12"), run("tail -n 2", *t))
        assertEquals(listOf("l1", "l2"), run("head -2", *t))
    }
}
