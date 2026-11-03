package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import kotlin.random.Random

/** ↑ history kept up to date from the new lines, as reading it again from the log would give it. */
class HistoryMergeTest {
    private var id = 0L
    private fun line(kind: LogKind, text: String) = LogEntry(++id, 0, kind, null, text, LogContext.EMPTY)
    private fun command(text: String) = line(LogKind.COMMAND, text)

    /** What [com.mtwabpin.kolahal.data.LogRepository.commandHistory]'s query reads: each command once, by its newest line. */
    private fun read(lines: List<LogEntry>, limit: Int) = lines.filter { it.kind == LogKind.COMMAND }
        .groupBy { it.text }.entries.sortedByDescending { e -> e.value.maxOf { it.id } }.map { it.key }.take(limit)

    @Test fun aNewCommandComesFirst() {
        val old = listOf("ls", "apps")
        assertEquals(listOf("torch on", "ls", "apps"), withNewCommands(old, listOf(command("torch on"), line(LogKind.INFO, "Torch on"))))
    }

    /** Typed again, it moves up, and is there once. */
    @Test fun aRepeatedCommandMovesUp() {
        assertEquals(listOf("apps", "ls"), withNewCommands(listOf("ls", "apps"), listOf(command("apps"))))
    }

    /** The newest of several new ones first. */
    @Test fun severalNewOnesNewestFirst() {
        assertEquals(listOf("b", "a", "ls"), withNewCommands(listOf("ls"), listOf(command("a"), command("b"), command("a"), command("b"))))
    }

    /** Only output came: nothing to change, and the list is the one there was. */
    @Test fun outputAloneChangesNothing() {
        val old = listOf("ls")
        assertSame(old, withNewCommands(old, listOf(line(LogKind.OUTPUT, "x"), line(LogKind.SCRIPT, "y"))))
        assertSame(old, withNewCommands(old, emptyList()))
    }

    @Test fun itKeepsToTheLimit() {
        assertEquals(listOf("d", "c", "a"), withNewCommands(listOf("a", "b"), listOf(command("c"), command("d")), limit = 3))
    }

    /** Against reading the whole log again: any log, cut anywhere into what was read and what came after. */
    @Test fun itIsWhatReadingAgainGives() {
        val random = Random(4)
        repeat(500) {
            id = 0
            val words = listOf("ls", "apps", "torch", "date", "calc 1+1", "open maps", "notes", "x")
            val log = List(random.nextInt(0, 60)) {
                if (random.nextInt(3) == 0) line(LogKind.OUTPUT, "out") else command(words[random.nextInt(words.size)])
            }
            val limit = random.nextInt(1, 10)
            val cut = random.nextInt(0, log.size + 1)
            val history = read(log.take(cut), limit)
            assertEquals("cut at $cut, limit $limit", read(log, limit), withNewCommands(history, log.drop(cut), limit))
        }
    }
}
