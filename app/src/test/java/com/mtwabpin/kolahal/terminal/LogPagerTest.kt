package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.logEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The log's page kept up to date from what changed ([mergePage]), and the same for `log only …`
 * ([patchFilterPage]): each gives what reading the log again would give.
 */
class LogPagerTest {
    /** A log to read from, as [com.mtwabpin.kolahal.data.LogRepository.recent] reads the real one. */
    private class FakeLog {
        val lines = ArrayList<LogEntry>()
        private var last = 0L

        fun add(text: String = "x", kind: LogKind = LogKind.OUTPUT, ctx: LogContext = LogContext.EMPTY): LogEntry =
            logEntry(kind, text, ctx = ctx, id = ++last).also { lines += it }

        fun remove(id: Long) = lines.removeAll { it.id == id }

        /** Changes the line [id] in place, as a script's `line.set` does. */
        fun set(id: Long, text: String) {
            val at = lines.indexOfFirst { it.id == id }
            if (at >= 0) lines[at] = lines[at].copy(text = text)
        }

        fun recent(limit: Int, afterId: Long = 0, beforeId: Long = Long.MAX_VALUE) =
            lines.filter { it.id > afterId && it.id < beforeId }.takeLast(limit)

        fun byIds(ids: Collection<Long>) = lines.filter { it.id in ids }.associateBy { it.id }

        /** The whole page, read as a reload reads it when it can't build on the last one. */
        fun page(limit: Int) = recent(limit).let { LogPage(it, atStart = it.size < limit) }
    }

    private fun log(lines: Int) = FakeLog().apply { repeat(lines) { add() } }

    private fun ids(page: LogPage) = page.lines.map { it.id }

    /** No older line is read: the test fails if one is asked for. */
    private val nothingOlder = { _: Int, _: Long -> throw AssertionError("no older line is needed") }

    private fun merge(log: FakeLog, page: LogPage, limit: Int, gone: Set<Long> = emptySet(), changed: Set<Long> = emptySet()) =
        mergePage(page, gone, log.byIds(changed), log.recent(limit, afterId = page.lines.last().id), limit) { count, beforeId -> log.recent(count, beforeId = beforeId) }

    // ---- the log's page -------------------------------------------------------------------

    @Test fun newLinesComeAtTheEnd() {
        val log = log(3)
        val page = log.page(5)
        assertTrue(page.atStart)
        val fresh = listOf(log.add(), log.add())
        assertEquals(LogPage(log.lines, atStart = true), mergePage(page, emptySet(), emptyMap(), fresh, 5, nothingOlder))
    }

    /** More lines than the page holds: the oldest leave it, and they are before the page from then on. */
    @Test fun thePageKeepsItsNewestLines() {
        val log = log(5)
        val page = log.page(5)
        val fresh = listOf(log.add(), log.add())
        val merged = mergePage(page.copy(atStart = true), emptySet(), emptyMap(), fresh, 5, nothingOlder)
        assertEquals(listOf(3L, 4L, 5L, 6L, 7L), ids(merged))
        assertFalse(merged.atStart)
    }

    @Test fun olderLinesTakeThePlaceOfRemovedOnes() {
        val log = log(10)
        val page = log.page(5)
        log.remove(7)
        log.remove(8)
        val merged = merge(log, page, 5, gone = setOf(7, 8))
        assertEquals(listOf(4L, 5L, 6L, 9L, 10L), ids(merged))
        assertFalse(merged.atStart)
    }

    /** Fewer older lines than asked for: that is the log's start, and nothing older is read after it. */
    @Test fun theLogsStartIsFoundOnce() {
        val log = log(6)
        val page = log.page(5)
        assertFalse(page.atStart)
        log.remove(3)
        log.remove(4)
        val merged = merge(log, page, 5, gone = setOf(3, 4))
        assertEquals(LogPage(log.lines, atStart = true), merged)
        log.remove(5)
        assertEquals(listOf(1L, 2L, 6L), ids(mergePage(merged, setOf(5), emptyMap(), emptyList(), 5, nothingOlder)))
    }

    /** Scrolling back raises the limit. The lines before the page are read, up to it. */
    @Test fun scrollingBackReadsTheOlderLines() {
        val log = log(10)
        val page = log.page(5)
        assertEquals(listOf(3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L), ids(merge(log, page, 8)))
        val all = merge(log, page, 20)
        assertEquals(LogPage(log.lines, atStart = true), all)
        // Back at the newest lines, the page is short again, and no longer starts where the log does.
        assertEquals(LogPage(log.recent(5), atStart = false), merge(log, all, 5))
    }

    @Test fun aChangedLineIsReadAgainWhereItIs() {
        val log = log(10)
        val page = log.page(5)
        log.set(7, "seven")
        log.set(2, "two")
        val merged = merge(log, page, 5, changed = setOf(2, 7))
        assertEquals(listOf("x", "seven", "x", "x", "x"), merged.lines.map { it.text })
        assertEquals(listOf(6L, 7L, 8L, 9L, 10L), ids(merged))
    }

    /** Every line of the page went and none came: the page is empty, and nothing tells where to read from. */
    @Test fun withNoLineLeftNothingOlderIsRead() {
        val log = log(10)
        val page = log.page(2)
        assertEquals(LogPage(emptyList(), atStart = false), mergePage(page, setOf(9, 10), emptyMap(), emptyList(), 2, nothingOlder))
    }

    /** Against reading the page again: lines come, go and change, and the user scrolls back and returns. */
    @Test fun itIsThePageThatReadingAgainGives() {
        val random = Random(7)
        repeat(300) { round ->
            val log = log(random.nextInt(0, 30))
            var limit = listOf(3, 5, 8).random(random)
            var page = log.page(limit)
            repeat(25) { step ->
                val gone = HashSet<Long>()
                val changed = HashSet<Long>()
                repeat(random.nextInt(0, 4)) { log.add() }
                repeat(random.nextInt(0, 3)) { log.lines.randomOrNull(random)?.let { gone += it.id; log.remove(it.id) } }
                repeat(random.nextInt(0, 3)) { log.lines.randomOrNull(random)?.let { changed += it.id; log.set(it.id, "changed $step") } }
                if (random.nextInt(4) == 0) limit = listOf(3, 5, 8, 20).random(random)
                val where = "round $round, step $step, limit $limit"
                if (page.lines.isEmpty()) {
                    // The pager reads an empty page whole.
                    page = log.page(limit)
                    return@repeat
                }
                val fresh = log.recent(limit, afterId = page.lines.last().id)
                val merged = mergePage(page, gone, log.byIds(changed), fresh, limit) { count, beforeId -> log.recent(count, beforeId = beforeId) }
                if (merged.lines.isEmpty()) {
                    assertTrue(where, fresh.isEmpty() && page.lines.all { it.id in gone })
                } else {
                    assertEquals(where, log.recent(limit), merged.lines)
                    // It says it starts where the log does only when it does, and a short page always knows.
                    if (merged.atStart) assertEquals(where, log.lines, merged.lines)
                    assertTrue(where, merged.atStart || merged.lines.size == limit)
                }
                page = merged
            }
        }
    }

    // ---- a removed command ----------------------------------------------------------------

    @Test fun aRemovedLineMayHaveBeenACommand() {
        val log = FakeLog()
        val typed = log.add("ls", LogKind.COMMAND)
        val printed = log.add("a file")
        assertFalse(mayRemoveCommand(log.lines, emptySet()))
        assertFalse(mayRemoveCommand(log.lines, setOf(printed.id)))
        assertTrue(mayRemoveCommand(log.lines, setOf(typed.id)))
        assertTrue(mayRemoveCommand(log.lines, setOf(printed.id, typed.id)))
        // A line that isn't on the page: nothing tells what it was.
        assertTrue(mayRemoveCommand(log.lines, setOf(printed.id, 99)))
    }

    // ---- `log only …` ---------------------------------------------------------------------

    /** `log only …` here lets through the lines about cats. */
    private val cats = { e: LogEntry -> "cat" in e.text }
    private val inPrivateSpace = LogContext.of(LogContext.USER to "10")
    private val locked = setOf(10L)

    /** The whole log searched, as a reload searches it when it can't build on what it showed. */
    private fun search(log: FakeLog, limit: Int, locked: Set<Long> = emptySet()) =
        log.lines.filter { it.ctx.userSerial !in locked && cats(it) }.let { FilterPage(it.takeLast(limit), it.size) }

    @Test fun newLinesTheFilterAcceptsAreAdded() {
        val log = FakeLog()
        val first = log.add("a cat")
        val shown = search(log, 5)
        val fresh = listOf(log.add("a dog"), log.add("two cats"))
        assertEquals(FilterPage(listOf(first, fresh[1]), 2), patchFilterPage(shown, emptyMap(), fresh, 5, emptySet(), cats))
    }

    @Test fun linesOfALockedSpaceAreLeftOut() {
        val log = FakeLog()
        val shown = search(log, 5, locked)
        val fresh = listOf(log.add("a cat", ctx = inPrivateSpace), log.add("my cat"))
        assertEquals(FilterPage(listOf(fresh[1]), 1), patchFilterPage(shown, emptyMap(), fresh, 5, locked, cats))
        assertEquals(FilterPage(fresh, 2), patchFilterPage(shown, emptyMap(), fresh, 5, emptySet(), cats))
    }

    /** More than the page holds: the newest stay, and the count is of them all. */
    @Test fun theFiltersPageKeepsItsNewestLines() {
        val log = FakeLog()
        repeat(3) { log.add("cat $it") }
        val shown = search(log, 3)
        val fresh = listOf(log.add("cat 3"), log.add("cat 4"))
        val patched = patchFilterPage(shown, emptyMap(), fresh, 3, emptySet(), cats)!!
        assertEquals(listOf("cat 2", "cat 3", "cat 4"), patched.lines.map { it.text })
        assertEquals(5, patched.count)
    }

    @Test fun aChangedLineIsPatchedOrGoes() {
        val log = FakeLog()
        val one = log.add("cat one")
        val two = log.add("cat two")
        val three = log.add("cat three")
        val shown = search(log, 5)
        log.set(one.id, "the cat sleeps")
        log.set(two.id, "a dog now")
        val patched = patchFilterPage(shown, log.byIds(setOf(one.id, two.id)), emptyList(), 5, emptySet(), cats)
        assertEquals(FilterPage(listOf(one.copy(text = "the cat sleeps"), three), 2), patched)
    }

    /** Where it belongs among the lines shown isn't known, so the whole log is searched. */
    @Test fun aChangedLineThatNowMatchesNeedsTheWholeLog() {
        val log = FakeLog()
        val dog = log.add("a dog")
        val hidden = log.add("a bird", ctx = inPrivateSpace)
        log.add("a cat")
        val shown = search(log, 5, locked)
        log.set(dog.id, "a cat now")
        assertNull(patchFilterPage(shown, log.byIds(setOf(dog.id)), emptyList(), 5, locked, cats))
        // A line of a locked space isn't shown, whatever it says now. Nor is one the filter still refuses.
        log.set(hidden.id, "a hidden cat")
        log.set(dog.id, "a bigger dog")
        assertEquals(shown, patchFilterPage(shown, log.byIds(setOf(dog.id, hidden.id)), emptyList(), 5, locked, cats))
    }

    /** Against searching the whole log again, while every line the filter accepts is on its page. */
    @Test fun itIsWhatSearchingAgainGives() {
        val random = Random(11)
        val words = listOf("a cat", "a dog", "two cats", "a bird")
        var patches = 0
        var searches = 0
        repeat(300) { round ->
            val log = FakeLog()
            repeat(random.nextInt(0, 10)) { log.add(words.random(random), ctx = if (random.nextInt(4) == 0) inPrivateSpace else LogContext.EMPTY) }
            val limit = 1_000
            var shown = search(log, limit, locked)
            repeat(20) { step ->
                val changed = HashSet<Long>()
                val newest = log.lines.lastOrNull()?.id ?: 0
                repeat(random.nextInt(0, 3)) { log.lines.randomOrNull(random)?.let { changed += it.id; log.set(it.id, words.random(random)) } }
                repeat(random.nextInt(0, 3)) { log.add(words.random(random), ctx = if (random.nextInt(4) == 0) inPrivateSpace else LogContext.EMPTY) }
                val patched = patchFilterPage(shown, log.byIds(changed), log.recent(limit, afterId = newest), limit, locked, cats)
                val expected = search(log, limit, locked)
                if (patched == null) searches++ else assertEquals("round $round, step $step", expected, patched).also { patches++ }
                shown = expected
            }
        }
        // Both happened: steps that were patched, and steps that needed the whole log.
        assertTrue("$patches patched, $searches searched", patches > 0 && searches > 0)
    }

    /** New lines only, and a page shorter than what the filter accepts: still what a search gives. */
    @Test fun aShortPageFollowsNewLinesAsASearchWould() {
        val random = Random(3)
        repeat(200) { round ->
            val log = FakeLog()
            val limit = random.nextInt(1, 6)
            var shown = search(log, limit)
            repeat(20) { step ->
                val newest = log.lines.lastOrNull()?.id ?: 0
                repeat(random.nextInt(0, 4)) { log.add(if (random.nextBoolean()) "a cat" else "a dog") }
                shown = patchFilterPage(shown, emptyMap(), log.recent(1_000, afterId = newest), limit, emptySet(), cats)!!
                assertEquals("round $round, step $step", search(log, limit), shown)
            }
        }
    }
}
