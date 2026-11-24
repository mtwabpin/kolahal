package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `screen.hide-bars` hides the same bars on home and on every sheet and dialog over it: a sheet or a
 * dialog is a window of its own, which shows the bars unless it hides them too ([DialogBars]).
 */
class HiddenBarsTest {
    private val root = sourceFile("src/main/java/com/mtwabpin/kolahal")

    @Test
    fun eachChoiceHidesItsBars() {
        assertEquals(
            listOf(HiddenBars.NONE, HiddenBars.STATUS, HiddenBars.BOTH),
            Keys.SCREEN_HIDE_BARS.choices.map { HiddenBars.of(it) },
        )
        assertEquals(listOf(false, false), HiddenBars.NONE.let { listOf(it.status, it.navigation) })
        assertEquals(listOf(true, false), HiddenBars.STATUS.let { listOf(it.status, it.navigation) })
        assertEquals(listOf(true, true), HiddenBars.BOTH.let { listOf(it.status, it.navigation) })
        assertEquals(HiddenBars.NONE, HiddenBars.of("other"))
    }

    /** A Material sheet or dialog, or Compose's own dialog, called bare or written out. */
    private val window = Regex(
        """(?<![A-Za-z.])(?:androidx\.compose\.(?:material3|ui\.window)\.)?""" +
                """(ModalBottomSheet|AlertDialog|BasicAlertDialog|Dialog|TimePickerDialog|VibrantTimePickerDialog|DatePickerDialog)\(""",
    )

    /** From [start], just after a call's `(`: its arguments, and the lambda after them if it has one. */
    private fun call(text: String, start: Int): String {
        fun close(from: Int, open: Char, shut: Char): Int {
            var depth = 1
            var i = from
            while (i < text.length && depth > 0) {
                if (text[i] == open) depth++ else if (text[i] == shut) depth--
                i++
            }
            return i
        }
        val args = close(start, '(', ')')
        val after = text.substring(args).trimStart(' ')
        if (!after.startsWith("{")) return text.substring(start, args)
        val brace = text.indexOf('{', args)
        return text.substring(start, close(brace + 1, '{', '}'))
    }

    @Test
    fun everySheetAndDialogHidesTheBarsHomeHides() {
        var calls = 0
        val bare = root.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            val text = f.readText()
            window.findAll(text).mapNotNull { m ->
                calls++
                val line = text.substring(0, m.range.first).count { it == '\n' } + 1
                if ("DialogBars()" in call(text, m.range.last + 1)) null else "${f.name}:$line ${m.groupValues[1]}"
            }.toList()
        }.toList()
        assertTrue("$calls", calls > 20)
        assertEquals("Call DialogBars() in its content", emptyList<String>(), bare)
    }
}
