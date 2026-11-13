package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.CommandCatalog
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.ui.breaksAtSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `help`: a row per command (name, what it does, its arguments), sections with counts; `help <command>` in full. */
@RunWith(AndroidJUnit4::class)
class HelpTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule val logLines = LogLinesSince()
    private val commands = CommandRunner(context)

    private fun help(): List<LogEntry> = commands.lines("help")

    /** The left column holds only the name; what it does and its arguments are beside it, the arguments muted under. */
    @Test fun eachRowIsTheNameWithItsDescriptionAndArguments() {
        val rows = help().filter { it.ctx.values[LogContext.NAME_COL] != null }
        val column = rows.first().ctx.values.getValue(LogContext.NAME_COL)
        assertTrue("one column for all", rows.all { it.ctx.values[LogContext.NAME_COL] == column })
        assertTrue(column, column.length >= "screen-timeout".length)
        for (spec in CommandCatalog.OFFERED) {
            val row = rows.singleOrNull { it.text == spec.name && it.ctx.script == null } ?: error("${spec.name} has no row")
            assertEquals(spec.short, row.ctx.values[LogContext.DESC])
            assertEquals(spec.name + " ", row.ctx.command)
            if (spec.args.isEmpty()) assertNull(row.ctx.values[LogContext.ARGS]) else assertEquals(spec.args, row.ctx.values[LogContext.ARGS])
        }
        val todo = rows.single { it.text == "todo" }
        assertEquals("<text> · add · done · undo · edit · rm <n> · all · clear", todo.ctx.values[LogContext.ARGS])
    }

    /** Each section's heading is its name and how many commands it has. */
    @Test fun sectionHeadingsHaveACount() {
        val shown = help().map { StyledText.plain(it.text) }
        for ((section, names) in CommandCatalog.HELP_SECTIONS) {
            val count = names.count { it in CommandCatalog.OFFERED_NAMES }
            if (count == 0) continue
            val heading = section.replaceFirstChar { it.uppercase() } + " · $count"
            assertTrue("$heading not in $shown", heading in shown)
        }
        // The heading is bold, in the accent colour.
        val apps = help().first { StyledText.plain(it.text).startsWith("Apps · ") }
        val first = StyledText.lines(apps.text).single().spans.first()
        assertEquals("Apps", first.text)
        assertEquals(StyledText.Colour.ACCENT, first.colour)
        assertTrue(StyledText.Style.BOLD in first.styles)
    }

    /** Each command shows under its section: fetch is a device command, log a terminal one. (HelpCatalogTest checks each has one.) */
    @Test fun commandsShowUnderTheirSection() {
        val lines = help()
        fun sectionOf(name: String): String {
            val at = lines.indexOfFirst { it.text == name && it.ctx.values[LogContext.NAME_COL] != null }
            val heading = lines.subList(0, at).last { it.ctx.values[LogContext.NAME_COL] == null }
            return StyledText.plain(heading.text).substringBefore(" · ")
        }
        assertEquals("Device", sectionOf("fetch"))
        assertEquals("Terminal", sectionOf("log"))
    }

    /**
     * Rows wrap only at a space or after "|", never inside a word: every place Android may break a
     * line of a name, a description, the arguments or a use follows a space or "|".
     */
    @Test fun rowsBreakOnlyAtSpacesOrBars() {
        val texts = CommandCatalog.COMMANDS.flatMap { spec ->
            listOf(spec.name, spec.short, spec.args, spec.usage) + spec.uses.flatMap { listOf(it.first, it.second) }
        }.filter { it.isNotEmpty() }
        val breaker = android.icu.text.BreakIterator.getLineInstance()
        for (raw in texts) {
            val text = breaksAtSpaces(raw)
            breaker.setText(text)
            var at = breaker.next()
            while (at != android.icu.text.BreakIterator.DONE && at < text.length) {
                val before = text[at - 1]
                assertTrue("'$raw' may break inside a word, at ${text.substring(0, at)}|", before == ' ' || before == '​')
                at = breaker.next()
            }
        }
        // The usage that used to break as "--s" / "ettings".
        val agenda = breaksAtSpaces("[<days>|--allow|--settings]")
        assertEquals("[<days>|​-⁠-⁠allow|​-⁠-⁠settings]", agenda)
    }

    /** `help todo`: its name, what it does, then a "Use" list: each use with what it does; a tap puts the use's words in the prompt. */
    @Test fun oneCommandShowsItsUses() {
        val todo = CommandCatalog.COMMANDS.first { it.name == "todo" }
        val lines = commands.lines("help todo")
        assertEquals("todo", StyledText.plain(lines[0].text))
        assertEquals("todo ", lines[0].ctx.command)
        assertEquals(todo.help, lines[1].text)
        assertEquals("Use", StyledText.plain(lines[2].text))
        val uses = lines.drop(3).take(todo.uses.size)
        assertEquals(todo.uses.map { it.first }, uses.map { it.text })
        assertEquals(todo.uses.map { it.second }, uses.map { StyledText.plain(it.ctx.values.getValue(LogContext.DESC)) })
        assertEquals("todo edit <n> <text>", uses.first().ctx.values[LogContext.NAME_COL])
        assertEquals(listOf("todo ", "todo ", "todo done ", "todo undo ", "todo edit ", "todo rm ", "todo all ", "todo clear "), uses.map { it.ctx.command })
        assertTrue(lines.any { it.text == "More in the guide's command list" && it.ctx.url != null })
        // A command with no uses listed shows its usage line.
        val mp = commands.lines("help mp").map { StyledText.plain(it.text) }
        assertEquals(listOf("mp", "Play / pause media", "Use", "mp"), mp.take(4))
    }
}
