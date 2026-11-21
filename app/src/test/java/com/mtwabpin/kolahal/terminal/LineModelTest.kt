package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.sourceFile
import com.mtwabpin.kolahal.terminal.LogCorpus.row
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What each kind of log row is made of ([lineModel]), over the whole [LogCorpus]. */
class LineModelTest {
    private val options = LineOptions(TimeMode.OFF, foldLines = 8)

    private fun model(
        line: LogCorpus.Line,
        o: LineOptions = options,
        hasIcon: (String) -> Boolean = { false }
    ) = lineModel(row(line), o, hasIcon)

    private fun words(line: LogCorpus.Line, unread: Boolean = false) =
        lineModel(row(line, unread = unread), options).body as LineModel.Body.Words

    private fun LineModel.Body.Words.piece(text: String) =
        paras.flatMap { it.pieces }.single { it.text == text }

    private fun LineModel.Body.Words.texts(role: Para.Role) =
        paras.filter { it.role == role }.map { p -> p.pieces.joinToString("") { it.text } }

    /** Every line of the corpus has a model, and a tap on the line is the first thing it can do. */
    @Test
    fun everyLineHasAModel() {
        for (line in LogCorpus.all) {
            val m = model(line)
            assertEquals(line.name, line.entry.id, m.id)
            assertEquals(line.name, Target.Line, m.targets().first())
            m.spoken(null)
        }
    }

    /** Two columns, help's name column and the apps grid come from the line's context; the rest is words. */
    @Test
    fun eachLineHasTheFormItsContextSays() {
        for (line in LogCorpus.all) {
            val expected = when (line) {
                LogCorpus.twoColumns, LogCorpus.twoColumnsBar, LogCorpus.contact -> LineModel.Body.TwoColumns::class
                LogCorpus.nameColumns -> LineModel.Body.NameColumns::class
                LogCorpus.apps -> LineModel.Body.AppGrid::class
                else -> LineModel.Body.Words::class
            }
            assertEquals(line.name, expected, model(line).body::class)
        }
        assertEquals(
            LineModel.Body.AppGrid(
                listOf(
                    LineModel.AppRef("com.example.maps", "com.example.maps.Main", 0),
                    LineModel.AppRef("com.example.clock", null, null),
                )
            ),
            model(LogCorpus.apps).body,
        )
        val help = model(LogCorpus.nameColumns).body as LineModel.Body.NameColumns
        assertEquals("torch", help.name)
        assertEquals("notifications", help.nameWidthOf)
        assertEquals("on · off", help.args)
    }

    /** A command is its prompt, then the line in bold, in the text color. */
    @Test
    fun aCommandIsItsPromptThenItsLineInBold() {
        val w = words(LogCorpus.command)
        assertEquals(
            listOf(
                Para(
                    Para.Role.TITLE,
                    listOf(Piece("$ "), Piece("torch on", Ink.FG, setOf(StyledText.Style.BOLD)))
                )
            ),
            w.paras,
        )
        assertEquals(Ink.PROMPT, w.ink)
        assertFalse(w.heading)
        assertEquals(LineModel.Indent.PROMPT, w.indent)
    }

    /** An output's lines after the first are details under it; an info line is in the info ink. */
    @Test
    fun anOutputsLinesAfterTheFirstAreDetails() {
        val w = words(LogCorpus.details)
        assertEquals(listOf("Battery 82%"), w.texts(Para.Role.TITLE))
        assertEquals(listOf("charging · 40 min to full"), w.texts(Para.Role.DETAIL))
        assertEquals(Ink.FG, w.ink)
        assertEquals(Ink.INFO, words(LogCorpus.info).ink)
        assertEquals(Ink.LINK, words(LogCorpus.linked).ink)
    }

    /** A profile tag at the end of a line is bold, in its profile's ink. */
    @Test
    fun aProfileTagIsInItsProfilesInk() {
        val w = words(LogCorpus.profileTag)
        assertEquals(Piece(" [W]", Ink.WORK, setOf(StyledText.Style.BOLD)), w.piece(" [W]"))
        assertNull(w.piece("Launched Maps").ink)
    }

    /** An error is plain, in the error ink: its styles are taken off. */
    @Test
    fun anErrorIsPlain() {
        val w = words(LogCorpus.error)
        assertEquals(listOf(Para(Para.Role.TITLE, listOf(Piece("Unknown command torh")))), w.paras)
        assertEquals(Ink.ERROR, w.ink)
    }

    /** Bold in a script's line is in the text ink; a tag and a color keep their own; a link is one target. */
    @Test
    fun aScriptLinesStyles() {
        val w = words(LogCorpus.styles)
        assertEquals(Ink.FG, w.piece("big").ink)
        assertEquals(setOf(StyledText.Style.ITALIC), w.piece("aside").styles)
        assertNull(w.piece("aside").ink)
        assertEquals(Ink.ERROR, w.piece("LIVE").ink)
        assertTrue(StyledText.Style.TAG in w.piece("LIVE").styles)
        assertEquals(Ink.WARN, w.piece("warm").ink)
        assertEquals("https://kolahal.example/a", w.piece(" page").url)
        assertEquals(
            listOf(Target.Line, Target.Link("https://kolahal.example/a", "the page")),
            model(LogCorpus.styles).targets(),
        )
    }

    /** A title with a body is a bold heading, with the body muted under it. A title alone isn't bold. */
    @Test
    fun aTitleWithABodyIsAHeading() {
        val w = words(LogCorpus.heading)
        assertTrue(w.heading)
        assertEquals(Ink.SCRIPT, w.ink)
        assertEquals(listOf("Top stories"), w.texts(Para.Role.TITLE))
        assertEquals(listOf("Three new stories today"), w.texts(Para.Role.BODY))
        assertFalse(words(LogCorpus.scriptLine).heading)
    }

    /** A body's list items keep their level and their bullet or number. */
    @Test
    fun aBodysListItems() {
        val body = words(LogCorpus.listBody).paras.filter { it.role == Para.Role.BODY }
        assertEquals(listOf(0, 1, 1, 1, 2), body.map { it.level })
        assertEquals(listOf("", "•", "•", "1.", "◦"), body.map { it.prefix })
        assertEquals(
            listOf("Read these", "one", "two", "first", "nested"),
            body.map { p -> p.pieces.joinToString("") { it.text } })
    }

    /** An unread script line starts with a bullet, and its wrapped lines hang past it. Other lines don't. */
    @Test
    fun anUnreadScriptLineStartsWithItsBullet() {
        val unread = words(LogCorpus.scriptLine, unread = true)
        assertTrue(unread.bullet)
        assertEquals(LineModel.Indent.BULLET, unread.indent)
        val read = words(LogCorpus.scriptLine)
        assertFalse(read.bullet)
        assertEquals(LineModel.Indent.NONE, read.indent)
        assertFalse(words(LogCorpus.output, unread = true).bullet)
        assertEquals(LineModel.Indent.CODE, words(LogCorpus.aligned).indent)
        assertTrue(model(LogCorpus.aligned).mono)
    }

    /** A script's line ends with its source: a name, or a notification's saved icon. */
    @Test
    fun aScriptLineEndsWithItsSource() {
        assertEquals(LineModel.Source.Name("weather"), model(LogCorpus.scriptLine).source)
        assertNull(lineModel(row(LogCorpus.scriptLine, showSource = false), options).source)
        assertEquals(
            LineModel.Source.Icon("chat-icon", "Chat"),
            model(LogCorpus.notification) { it == "chat-icon" }.source,
        )
        assertEquals(LineModel.Source.Name("Chat"), model(LogCorpus.notification).source)
        assertNull(model(LogCorpus.command).source)
    }

    /** Tables, bars, big numbers and charts follow the words, in order, with the text between them. */
    @Test
    fun blocksFollowTheWords() {
        fun kinds(line: LogCorpus.Line) = model(line).blocks.map { it::class.simpleName }
        assertEquals(listOf("Table", "Text"), kinds(LogCorpus.table))
        assertEquals(listOf("Today"), words(LogCorpus.table).texts(Para.Role.BODY))
        assertEquals(3, (model(LogCorpus.table).blocks[0] as LineModel.Block.Table).rows.size)
        assertEquals(listOf("Table"), kinds(LogCorpus.outputTable))
        assertEquals(listOf("Usage today"), words(LogCorpus.outputTable).texts(Para.Role.TITLE))
        assertEquals(listOf("Meters"), kinds(LogCorpus.bars))
        assertEquals(2, (model(LogCorpus.bars).blocks[0] as LineModel.Block.Meters).rows.size)
        assertEquals(listOf("Meters"), kinds(LogCorpus.ranges))
        assertEquals(listOf("Stats"), kinds(LogCorpus.stats))
        assertEquals(listOf("Chart"), kinds(LogCorpus.chart))
        assertEquals(
            listOf(1f / 3, 1f, 2f / 3, null, null).map { it?.let { h -> Math.round(h * 1000) } },
            (model(LogCorpus.chart).blocks[0] as LineModel.Block.Chart).columns.heights.map {
                it?.let { h -> Math.round(h * 1000) }
            },
        )
        assertTrue(model(LogCorpus.scriptLine).blocks.isEmpty())
    }

    /** A line can be its blocks alone: a command's big numbers, a script's table with no title. */
    @Test
    fun blocksAloneHaveNoWords() {
        assertTrue(words(LogCorpus.statsAlone).blank)
        assertTrue(words(LogCorpus.untitled).blank)
        assertFalse(words(LogCorpus.output).blank)
        assertFalse(words(LogCorpus.bars).blank)
    }

    /** The blocks read as plain text as the text they come from does: Copy and the fold's estimate agree. */
    @Test
    fun theBlocksPlainTextIsTheirTexts() {
        var checked = 0
        for (line in LogCorpus.all) {
            val m = model(line)
            if (m.blocks.isEmpty()) continue
            val e = line.entry
            val rest = when (e.kind) {
                LogKind.SCRIPT -> StyledText.splitAtBlocks(e.ctx.body!!).second
                else -> StyledText.splitAtBlocks(e.text).second
            }
            assertEquals(line.name, StyledText.plain(rest!!), plainOf(m.blocks))
            checked++
        }
        assertTrue("$checked lines with blocks", checked >= 8)
    }

    /** A built-in command's bar is after its words or in its second column. A script's line has none. */
    @Test
    fun aCommandsBar() {
        assertEquals(
            StyledText.Meter(0f, 0.6f, StyledText.ColorRole.GOOD, emptyList(), emptyList(), emptyList()),
            model(LogCorpus.builtInBar).bar,
        )
        assertEquals(0.6f, model(LogCorpus.twoColumnsBar).bar?.to)
        assertNull(model(LogCorpus.scriptLine).bar)
    }

    /** A script's buttons show until one is tapped; then only it shows, done, and nothing can be tapped. */
    @Test
    fun aScriptsButtons() {
        assertEquals(
            listOf(
                LineModel.Button(0, "Snooze 30 min", "snooze 30", false),
                LineModel.Button(1, "Done", "done", false),
            ),
            model(LogCorpus.buttons).buttons,
        )
        assertEquals(
            listOf(Target.Button(0, "Snooze 30 min"), Target.Button(1, "Done")),
            model(LogCorpus.buttons).targets().filterIsInstance<Target.Button>(),
        )
        assertEquals(
            listOf(LineModel.Button(1, "Done", "done", true)),
            model(LogCorpus.buttonDone).buttons
        )
        assertTrue(model(LogCorpus.buttonDone).targets().none { it is Target.Button })
    }

    /** A picture shows under the words. */
    @Test
    fun aPicture() {
        assertEquals("https://kolahal.example/comic.png", model(LogCorpus.picture).image)
        assertNull(model(LogCorpus.scriptLine).image)
    }

    /** log.time: on every line, on commands, on a group's first line, or on none. */
    @Test
    fun theTimeShowsAsLogTimeSays() {
        val command = LogCorpus.command
        val output = LogCorpus.output
        fun time(line: LogCorpus.Line, mode: TimeMode, showTime: Boolean = true) =
            lineModel(row(line, showTime = showTime), LineOptions(mode, 8)).time
        assertEquals(output.entry.ts, time(output, TimeMode.ALL))
        assertEquals(command.entry.ts, time(command, TimeMode.COMMANDS))
        assertNull(time(output, TimeMode.COMMANDS))
        assertEquals(output.entry.ts, time(output, TimeMode.GROUPS))
        assertNull(time(output, TimeMode.GROUPS, showTime = false))
        assertNull(time(command, TimeMode.OFF))
    }

    /** A group's first line has the group's gap above it; an item of a list, the item's gap. */
    @Test
    fun theGapAbove() {
        assertEquals(
            LineModel.Gap.GROUP,
            lineModel(row(LogCorpus.output, blockStart = true), options).gapAbove
        )
        assertEquals(
            LineModel.Gap.ITEM,
            lineModel(row(LogCorpus.item, itemGap = true), options).gapAbove
        )
        assertEquals(LineModel.Gap.NONE, model(LogCorpus.output).gapAbove)
    }

    /** Lines that do something when tapped are told apart (they get a taller touch target). */
    @Test
    fun linesThatDoSomething() {
        val tappable = LogCorpus.all.filter { model(it).tappable }.toSet()
        for (line in listOf(
            LogCorpus.command, LogCorpus.linked, LogCorpus.runs, LogCorpus.notification,
            LogCorpus.apps, LogCorpus.profileTag, LogCorpus.buttons, LogCorpus.call,
        )) assertTrue(line.name, line in tappable)
        for (line in listOf(LogCorpus.output, LogCorpus.scriptLine, LogCorpus.heading, LogCorpus.error)) {
            assertFalse(line.name, line in tappable)
        }
    }

    /** A line may fold when log.fold is on, but never a command, and only its words. */
    @Test
    fun whatMayFold() {
        assertTrue(model(LogCorpus.long).mayFold)
        assertFalse(model(LogCorpus.long, LineOptions(TimeMode.OFF, foldLines = 0)).mayFold)
        assertFalse(model(LogCorpus.command).mayFold)
        assertFalse(model(LogCorpus.twoColumns).mayFold)
        assertFalse(model(LogCorpus.apps).mayFold)
    }

    /** A call's picture is the big one, beside its details; a contact's is the small one. */
    @Test
    fun aPersonsPicture() {
        assertEquals(
            LineModel.Person("Ada Lovelace", "content://contacts/1/photo", big = true),
            model(LogCorpus.call).person
        )
        assertEquals(LineModel.Person("Ada Lovelace", null, big = false), model(LogCorpus.contact).person)
        assertNull(model(LogCorpus.output).person)
    }

    /** What a row can do, in reading order: the line, its links, its apps, the links in its blocks, its buttons. */
    @Test
    fun targetsInReadingOrder() {
        assertEquals(
            listOf(Target.Line, Target.App(0), Target.App(1)),
            model(LogCorpus.apps).targets()
        )
        assertEquals(
            listOf(Target.Line, Target.Link("https://kolahal.example/torch", "see more")),
            model(LogCorpus.nameColumns).targets(),
        )
        assertEquals(
            listOf(Target.Line, Target.Link("https://kolahal.example/feed", "example feed")),
            model(LogCorpus.table).targets(),
        )
        assertEquals(listOf(Target.Line), model(LogCorpus.output).targets())
    }

    /** A screen reader hears the row as words: new, the text, the source, tables and bars, buttons, the time. */
    @Test
    fun whatAScreenReaderHears() {
        assertEquals(
            "new, 12°C, clear, weather, 14:02",
            lineModel(row(LogCorpus.scriptLine, unread = true), options).spoken("14:02"),
        )
        val table = model(LogCorpus.table).spoken(null)
        assertTrue(table, "Coin BTC, Price 64,210. Coin ETH, Price 3,100" in table)
        assertTrue(table, table.endsWith("from an example feed"))
        assertTrue(model(LogCorpus.bar).spoken(null).endsWith("82 percent"))
        val buttons = model(LogCorpus.buttons).spoken(null)
        assertTrue(buttons, buttons.endsWith("Snooze 30 min, Done"))
    }

    /** A search marks each place its words are found, in any case. An empty word marks nothing. */
    @Test
    fun markRangesFindEachPlace() {
        assertEquals(listOf(0..2, 10..12), markRanges("Tea, then tea again", listOf("tea")))
        assertEquals(listOf(1..1), markRanges("abc", listOf("", "b")))
        assertEquals(emptyList<IntRange>(), markRanges("abc", emptyList()))
    }

    /** The shared models draw nothing: no Android graphics and no Compose UI, so both renderers and the JVM can use them. */
    @Test
    fun theSharedModelsArePlainKotlin() {
        for (name in listOf("LineModel.kt", "HeaderModel.kt")) {
            val imports = sourceFile("src/main/java/com/mtwabpin/kolahal/terminal/$name").readLines()
                .filter { it.startsWith("import ") }
            assertEquals(
                name,
                emptyList<String>(),
                imports.filter { "android.graphics" in it || "androidx.compose.ui" in it })
        }
    }
}
