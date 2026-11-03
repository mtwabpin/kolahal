package com.mtwabpin.kolahal.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.random.Random

/** The editor's undo keeps what each step changed, and must undo and redo exactly as whole copies did. */
class UndoHistoryTest {
    /** The history as it was, a whole copy per step: what the new one is held to. */
    private class Copies(initial: String, private val clock: () -> Long) {
        private val undo = ArrayDeque<String>().apply { addLast(initial) }
        private val redo = ArrayDeque<String>()
        private var lastRecord = 0L

        fun record(text: String) {
            val now = clock()
            val newline = text.length > undo.last().length && text.count { it == '\n' } != undo.last().count { it == '\n' }
            if (now - lastRecord > 600 || newline || undo.size == 1) {
                undo.addLast(text)
                if (undo.size > 200) undo.removeFirst()
            } else {
                undo[undo.lastIndex] = text
            }
            lastRecord = now
            redo.clear()
        }

        fun checkpoint() {
            lastRecord = 0
        }

        val canUndo get() = undo.size > 1
        val canRedo get() = redo.isNotEmpty()

        fun undo(): String? {
            if (undo.size <= 1) return null
            redo.addLast(undo.removeLast())
            lastRecord = 0
            return undo.last()
        }

        fun redo(): String? {
            val text = redo.removeLastOrNull() ?: return null
            undo.addLast(text)
            lastRecord = 0
            return text
        }
    }

    /** A change as typing or the editor's tools make one: a few letters, a newline, a deletion, a paste, a replace-all. */
    private fun change(r: Random, text: String): String {
        val at = r.nextInt(text.length + 1)
        return when (r.nextInt(6)) {
            0 -> text.substring(0, at) + "abcdefg".substring(0, 1 + r.nextInt(3)) + text.substring(at)
            1 -> text.substring(0, at) + "\n" + text.substring(at)
            2 -> if (text.isEmpty()) "x" else text.substring(0, at.coerceAtMost(text.length - 1)) + text.substring((at + 1 + r.nextInt(5)).coerceAtMost(text.length))
            3 -> text.substring(0, at) + "local x = 1\nprint(x)\n" + text.substring(at)
            4 -> text.replace('a', 'b')
            else -> text.substring(0, at) + "aa" + text.substring(at)
        }.let { if (it == text) it + "z" else it }
    }

    @Test fun undoAndRedoGiveWhatWholeCopiesGave() {
        repeat(200) { seed ->
            val r = Random(seed)
            var now = 1_000L
            val initial = "-- @every 1h\nprint('hello')\n".repeat(r.nextInt(4))
            val copies = Copies(initial) { now }
            val steps = UndoHistory(initial) { now }
            var text = initial
            repeat(400) { i ->
                // Typing quickly, a pause, a long pause.
                now += listOf(50L, 300L, 700L, 5_000L)[r.nextInt(4)]
                when (r.nextInt(10)) {
                    in 0..5 -> {
                        text = change(r, text)
                        copies.record(text)
                        steps.record(text)
                    }
                    6 -> {
                        copies.checkpoint()
                        steps.checkpoint()
                    }
                    7, 8 -> {
                        val back = copies.undo()
                        assertEquals("seed $seed, step $i: undo", back, steps.undo())
                        if (back != null) text = back
                    }
                    else -> {
                        val again = copies.redo()
                        assertEquals("seed $seed, step $i: redo", again, steps.redo())
                        if (again != null) text = again
                    }
                }
                assertEquals("seed $seed, step $i", copies.canUndo, steps.canUndo)
                assertEquals("seed $seed, step $i", copies.canRedo, steps.canRedo)
            }
            // Back to the start, as far as the 200 steps kept go, and forward again.
            while (true) {
                val back = copies.undo()
                assertEquals("seed $seed: undoing all", back, steps.undo())
                back ?: break
            }
            while (true) {
                val again = copies.redo()
                assertEquals("seed $seed: redoing all", again, steps.redo())
                again ?: break
            }
        }
    }

    /** More than 200 steps: the oldest go, as before. */
    @Test fun onlyTheLast200StepsAreKept() {
        var now = 0L
        val steps = UndoHistory("") { now }
        var text = ""
        repeat(250) {
            now += 1_000
            text += "x"
            steps.record(text)
        }
        var undone = 0
        while (steps.undo() != null) undone++
        assertEquals(199, undone)
    }
}
