package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.LineModel
import com.mtwabpin.kolahal.terminal.LineOptions
import com.mtwabpin.kolahal.terminal.LogCorpus
import com.mtwabpin.kolahal.terminal.LogCorpus.row
import com.mtwabpin.kolahal.terminal.Target
import com.mtwabpin.kolahal.terminal.TimeMode
import com.mtwabpin.kolahal.terminal.lineModel
import com.mtwabpin.kolahal.terminal.targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Each kind of log row on the grid ([LineLayout]), over the whole [LogCorpus]. */
class LineLayoutTest {
    private val options = LineOptions(TimeMode.ALL, foldLines = 8)
    private val widths = listOf(32, 40, 53, 80)

    private fun model(line: LogCorpus.Line, unread: Boolean = false, dimmed: Boolean = false, o: LineOptions = options) =
        lineModel(row(line, unread = unread, dimmed = dimmed), o)

    /** The apps an apps grid would find: one for each it saved. */
    private fun appsOf(m: LineModel) =
        (m.body as? LineModel.Body.AppGrid)?.apps?.map { GridApp(it.pkg.substringAfterLast('.')) }.orEmpty()

    private fun layout(
        line: LogCorpus.Line,
        cols: Int = 40,
        look: LogLook = LogLook(cols),
        time: String? = "14:02",
        foldOpen: Boolean = false,
        marks: List<String> = emptyList(),
        unread: Boolean = false,
        dimmed: Boolean = false,
    ): CellBlock {
        val m = model(line, unread, dimmed)
        return LineLayout.layout(m, look, time, foldOpen, marks, appsOf(m), initials = m.person?.let { "AL" })
    }

    private fun CellBlock.row(r: Int) = grid.rowText(r)

    /** Every cell of every target is on the block. */
    private fun CellBlock.assertTargetsInside(name: String) {
        for (t in targets) for (r in t.rects) {
            assertTrue("$name: $t starts left of the block", r.col >= 0 && r.row >= 0)
            assertTrue("$name: $t is empty", r.cols > 0 && r.rows > 0)
            assertTrue("$name: $t ends right of the block ($cols)", r.col + r.cols <= cols)
            assertTrue("$name: $t ends below the block ($rows)", r.row + r.rows <= rows)
        }
    }

    /** Every row of the corpus lays out at each width, with and without the gutter, and its targets are on it. */
    @Test
    fun everyRowLaysOutWithinTheWidth() {
        for (line in LogCorpus.all) for (cols in widths) for (gutter in listOf(true, false)) for (open in listOf(false, true)) {
            val block = layout(line, look = LogLook(cols, gutter = gutter), foldOpen = open)
            val name = "${line.name} at $cols${if (gutter) "" else " without the gutter"}"
            assertEquals(name, cols, block.cols)
            assertTrue(name, block.rows >= 1)
            for (r in 0 until block.rows) assertTrue(name, CellWidth.total(block.row(r)) <= cols)
            block.assertTargetsInside(name)
            if (gutter) for (r in 0 until block.rows) assertEquals("$name, row $r", '│', block.row(r)[0])
        }
    }

    /**
     * What a finger can hit is what the row can do: every target of the model is on the block,
     * and the block has no target the model doesn't list (but the fold's).
     */
    @Test
    fun theTargetsAreTheModels() {
        for (line in LogCorpus.all) for (cols in widths) {
            val m = model(line)
            val block = LineLayout.layout(m, LogLook(cols), "14:02", foldOpen = true, apps = appsOf(m))
            val shown = block.targets.map { it.target }.toSet() - Target.Fold
            assertEquals("${line.name} at $cols", m.targets().toSet() - Target.Line, shown)
            // A tap in a target's cell hits it.
            for (t in block.targets) {
                val r = t.rects.first()
                assertEquals("${line.name}: $t", t.target, block.hitAt(r.col, r.row))
            }
        }
    }

    /** A tap near a small target counts; a tap far from any is the line's. */
    @Test
    fun aTapNearATargetHitsIt() {
        val block = layout(LogCorpus.buttons)
        val button = block.targets.first { it.target is Target.Button }
        val r = button.rects.first()
        val cellW = 10
        val cellH = 20
        // Just under the button's row, in the middle of it.
        val x = (r.col + r.cols / 2f) * cellW
        val y = (r.row + r.rows) * cellH + 5f
        assertEquals(button.target, block.hit(x, y, cellW, cellH, slop = 24f))
        assertEquals(Target.Line, block.hit(x, y + 60f, cellW, cellH, slop = 24f))
        assertEquals(Target.Line, block.hit(0f, 0f, cellW, cellH, slop = 24f))
    }

    @Test
    fun aCommandShowsItsPromptAndTheTime() {
        val block = layout(LogCorpus.command)
        assertEquals(1, block.rows)
        assertTrue(block.row(0), block.row(0).startsWith("│ $ torch on"))
        assertTrue(block.row(0), block.row(0).endsWith("14:02"))
        // The prompt in its color, the command bold, the time muted.
        assertEquals(Ink.PROMPT, block.grid.styleAt(2, 0).fg)
        assertTrue(block.grid.styleAt(4, 0).has(CellStyle.BOLD))
        assertEquals(Ink.DIM, block.grid.styleAt(39, 0).fg)
        // Without the gutter, the text starts in the first column.
        assertTrue(layout(LogCorpus.command, look = LogLook(40, gutter = false)).row(0).startsWith("$ torch on"))
    }

    /** Text wraps short of the time on the first row only, and wrapped rows are indented past the prompt. */
    @Test
    fun wordsWrapShortOfTheTime() {
        val e = LogEntry(9001, LogCorpus.AT, LogKind.COMMAND, null, "one two three four five six seven", LogContext(emptyMap()))
        val m = lineModel(LogCorpus.row(LogCorpus.Line("long command", e)), options)
        val block = LineLayout.layout(m, LogLook(24), "14:02")
        assertEquals(
            listOf("│ $ one two three  14:02", "│   four five six seven"),
            (0 until block.rows).map { block.row(it).trimEnd() },
        )
    }

    /** Details and a script's body are muted, under the title. */
    @Test
    fun detailsAndBodiesAreMuted() {
        val block = layout(LogCorpus.details)
        assertEquals("│ Battery 82%", block.row(0).substring(0, 13))
        assertTrue(block.row(1), block.row(1).startsWith("│   charging · 40 min to full"))
        assertEquals(Ink.DIM, block.grid.styleAt(4, 1).fg)
        val heading = layout(LogCorpus.heading)
        assertTrue(heading.row(0), heading.row(0).startsWith("│ Top stories · hn"))
        assertTrue(heading.grid.styleAt(2, 0).has(CellStyle.BOLD))
        assertTrue(heading.row(1), heading.row(1).startsWith("│ Three new stories today"))
        assertEquals(Ink.DIM, heading.grid.styleAt(2, 1).fg)
    }

    @Test
    fun listsHangTheirItems() {
        val block = layout(LogCorpus.listBody, cols = 40)
        val rows = (0 until block.rows).map { block.row(it).trimEnd() }
        assertTrue(rows.toString(), "│ • one" in rows && "│ • two" in rows && "│ 1. first" in rows)
        assertTrue(rows.toString(), "│   ◦ nested" in rows)
    }

    /** Links are underlined in the link color; tags are inverse, with room on each side; code is on the surface color. */
    @Test
    fun stylesShowOnTheGrid() {
        val block = layout(LogCorpus.styles, cols = 80)
        val text = block.row(0)
        fun styleOf(word: String) = block.grid.styleAt(text.indexOf(word), 0)
        assertTrue(styleOf("big").has(CellStyle.BOLD))
        assertTrue(styleOf("aside").has(CellStyle.ITALIC))
        assertTrue(styleOf("under").has(CellStyle.UNDERLINE))
        assertTrue(styleOf("gone").has(CellStyle.STRIKE))
        assertEquals(Ink.SURFACE, styleOf("v1.2").bg)
        assertTrue(styleOf("LIVE").has(CellStyle.INVERSE))
        assertEquals(Ink.ERROR, styleOf("LIVE").fg)
        // The cell before the tag's text is part of the tag.
        assertTrue(block.grid.styleAt(text.indexOf("LIVE") - 1, 0).has(CellStyle.INVERSE))
        assertEquals(Ink.WARN, styleOf("warm").fg)
        assertEquals(Ink.LINK, styleOf("page").fg)
        assertTrue(styleOf("page").has(CellStyle.UNDERLINE))
        val link = block.targets.single { it.target is Target.Link }
        assertEquals(Target.Link("https://kolahal.example/a", "the page"), link.target)
        assertEquals(text.indexOf("the page"), link.rects.single().col)
        assertEquals("the page".length, link.rects.single().cols)
    }

    /** A link that wraps is one target with a rectangle on each row. */
    @Test
    fun aWrappedLinkHasARectanglePerRow() {
        val text = "see " + StyledText.link("https://kolahal.example/x", "a rather long link text that wraps")
        val e = LogEntry(9002, LogCorpus.AT, LogKind.SCRIPT, "s", text, LogContext(emptyMap()))
        val m = lineModel(LogCorpus.row(LogCorpus.Line("wrapped link", e)), options)
        val block = LineLayout.layout(m, LogLook(24))
        val link = block.targets.single()
        assertTrue(link.rects.toString(), link.rects.size >= 2)
        assertEquals(link.rects.map { it.row }, link.rects.map { it.row }.distinct())
    }

    /** The profile tag at the end of a line is inverse, in its profile's color. */
    @Test
    fun aProfileTagIsInverse() {
        val block = layout(LogCorpus.profileTag)
        val at = block.row(0).indexOf("[W]")
        assertTrue(block.row(0), at > 0)
        assertEquals(Ink.WORK, block.grid.styleAt(at, 0).fg)
        assertTrue(block.grid.styleAt(at, 0).has(CellStyle.INVERSE))
        assertFalse(block.grid.styleAt(at - 1, 0).has(CellStyle.INVERSE))
    }

    /** CJK and emoji take two cells each and never split across rows. */
    @Test
    fun wideCharactersTakeTwoCells() {
        val e = LogEntry(9003, LogCorpus.AT, LogKind.SCRIPT, null, "中文字幕的测试😀😀😀😀", LogContext(emptyMap()))
        val m = lineModel(LogCorpus.row(LogCorpus.Line("wide", e)), options)
        val block = LineLayout.layout(m, LogLook(MIN, gutter = false))
        // 11 wide characters in 16 columns: 8 on the first row, 3 on the next.
        assertEquals("中文字幕的测试😀", block.row(0).trimEnd())
        assertEquals("😀😀😀", block.row(1).trimEnd())
        assertEquals(CellBuffer.WIDE_TAIL, block.grid.glyphAt(1, 0))
        // With a cell left over, a wide character goes on to the next row whole.
        val odd = LineLayout.layout(m, LogLook(MIN + 1, gutter = false))
        assertEquals(CellBuffer.EMPTY, odd.grid.glyphAt(MIN, 0))
    }

    /** A boxed table's lines join: every column's line runs from the top border to the bottom one. */
    @Test
    fun aBoxedTablesLinesJoin() {
        for (line in listOf(LogCorpus.table, LogCorpus.outputTable)) for (cols in widths) {
            val block = layout(line, cols = cols, time = null)
            val rows = (0 until block.rows).map { block.row(it) }
            val top = rows.indexOfFirst { '┌' in it }
            val bottom = rows.indexOfFirst { '└' in it }
            assertTrue("${line.name}: $rows", top >= 0 && bottom > top)
            val joints = rows[top].indices.filter { rows[top][it] in "┌┬┐" }
            assertTrue(joints.size >= 3)
            for (r in top + 1 until bottom) for (c in joints) {
                assertTrue("${line.name} at $cols, row $r column $c:\n${rows.joinToString("\n")}", rows[r][c] in "│├┼┤╞╪╡")
            }
            for (c in joints) assertTrue(rows[bottom][c] in "└┴┘")
            // The header is bold, a double line under it.
            assertTrue(rows.any { '╪' in it })
        }
        val block = layout(LogCorpus.table, cols = 40, time = null)
        assertTrue(block.dump(), block.dump().contains("│ BTC  │ 64,210 │"))
    }

    /** Plain tables have columns only, two cells apart, and an underlined header. */
    @Test
    fun aPlainTableHasColumnsOnly() {
        val block = layout(LogCorpus.table, look = LogLook(40, boxedTables = false), time = null)
        val dump = block.dump()
        assertFalse(dump, dump.contains('┌'))
        // Prices line up on the right.
        assertTrue(dump, dump.contains("│ Coin   Price"))
        assertTrue(dump, dump.contains("│ BTC   64,210"))
        assertTrue(dump, dump.contains("│ ETH    3,100"))
        val r = (0 until block.rows).first { block.row(it).contains("Coin") }
        assertTrue(block.grid.styleAt(block.row(r).indexOf("Coin"), r).has(CellStyle.UNDERLINE))
    }

    /** A table too wide for its columns turns into lines of "name: value". */
    @Test
    fun aTooWideTableBecomesRecords() {
        val wide = StyledText.table(listOf(listOf("A", "B", "C", "D", "E", "F"), listOf("1", "2", "3", "4", "5", "6")), header = true)
        val e = LogEntry(9004, LogCorpus.AT, LogKind.OUTPUT, null, "Wide\n$wide", LogContext(emptyMap()))
        val m = lineModel(LogCorpus.row(LogCorpus.Line("wide table", e)), options)
        val dump = LineLayout.layout(m, LogLook(MIN)).dump()
        assertTrue(dump, dump.contains("A: 1") && dump.contains("F: 6"))
    }

    /** Bars that follow each other line up: their tracks start and end in the same columns. */
    @Test
    fun barsLineUp() {
        for (line in listOf(LogCorpus.bars, LogCorpus.ranges)) {
            val block = layout(line, cols = 40, time = null)
            val rows = (0 until block.rows).map { block.row(it) }.filter { '▕' in it }
            assertEquals(line.name, 2, rows.size)
            assertEquals(rows[0].indexOf('▕'), rows[1].indexOf('▕'))
            assertEquals(rows[0].lastIndexOf('▏'), rows[1].lastIndexOf('▏'))
        }
        // Tue's 8,000 steps fill the track; Mon's 4,000 half of it.
        val steps = layout(LogCorpus.bars, cols = 40, time = null)
        val tue = (0 until steps.rows).map { steps.row(it) }.single { it.contains("Tue") }
        val track = tue.substring(tue.indexOf('▕') + 1, tue.lastIndexOf('▏'))
        assertTrue(track, track.all { it == '█' })
        // A built-in bar goes after the words, short of the time.
        val volume = layout(LogCorpus.builtInBar)
        assertTrue(volume.row(0), volume.row(0).contains("Volume 60% ▕"))
        assertTrue(volume.row(0), volume.row(0).contains("▏ 14:02"))
    }

    /** Big numbers are bold at the grid's size, their labels muted under them. */
    @Test
    fun bigNumbersAreBold() {
        val block = layout(LogCorpus.stats, time = null)
        val values = (0 until block.rows).first { block.row(it).contains("8,204") }
        val row = block.row(values)
        assertTrue(row, row.contains("8,204") && row.contains("6.1 km"))
        assertTrue(block.grid.styleAt(row.indexOf("8,204"), values).has(CellStyle.BOLD))
        val labels = block.row(values + 1)
        assertEquals(row.indexOf("8,204"), labels.indexOf("Steps"))
        assertEquals(Ink.DIM, block.grid.styleAt(labels.indexOf("Steps"), values + 1).fg)
        // With no words above them, a command's big numbers start on the first row.
        assertTrue(layout(LogCorpus.statsAlone, time = null).row(0).contains("41"))
    }

    /** The weather script's big numbers: the sky's words are the long label. */
    private val weather: LineModel
        get() {
            val items = listOf("17°C" to "🌦 Patchy light rain", "70%" to "humidity", "→ 54 km/h" to "wind", "0" to "UV")
            val body = StyledText.stats(items)
            val ctx = LogContext(mapOf(LogContext.TITLE to "1", LogContext.BODY to body))
            val e = LogEntry(9002, LogCorpus.AT, LogKind.SCRIPT, "weather", "Tokyo", ctx)
            return lineModel(LogCorpus.row(LogCorpus.Line("weather", e)), options)
        }

    /**
     * Big numbers that don't fit on a row, even with the longest label cut, break between items:
     * each value stays over its label, in the same column, and an item that doesn't fit moves whole
     * to the next pair of rows (the weather script's UV on a narrow grid).
     */
    @Test
    fun bigNumbersBreakBetweenItems() {
        val m = weather
        // The sky's words may be cut ("🌦 Patchy li…"): found by their start.
        val drawn = listOf("17°C" to "🌦 Patchy", "70%" to "Humidity", "→ 54 km/h" to "Wind", "0" to "UV")
        for (cols in listOf(32, 40, 43, 47, 53, 80)) {
            val block = LineLayout.layout(m, LogLook(cols), null)
            val rows = (0 until block.rows).map { block.row(it) }
            val name = "At $cols:\n" + rows.joinToString("\n")

            /** Where [text] starts in [row], in cells, or -1. */
            fun cellOf(row: String, text: String) =
                row.indexOf(text).let { if (it < 0) -1 else CellWidth.total(row.substring(0, it)) }

            /** The value's row and column: just over its label, in the same column. */
            fun item(value: String, label: String): Pair<Int, Int> {
                val r = rows.indexOfFirst { label in it }
                assertTrue(name, r > 0)
                val col = cellOf(rows[r], label)
                val above = rows[r - 1]
                val at = above.indices.firstOrNull { CellWidth.total(above.substring(0, it)) == col }
                assertTrue("$value over $label. $name", at != null && above.startsWith(value, at))
                return r - 1 to col
            }
            val placed = drawn.map { (value, label) -> item(value, label) }
            // Each item that moves starts its pair of rows at the left, where the first one is.
            for ((row, col) in placed) if (col == placed[0].second) assertTrue(name, row == placed[0].first || row > placed[0].first + 1)
            if (cols == 32) assertTrue(name, placed[3].first > placed[0].first)
            if (cols >= 43) assertEquals(name, setOf(placed[0].first), placed.map { it.first }.toSet())
        }
    }

    /**
     * Big numbers that would fit on a row with the longest label shorter: that label is cut with
     * "…" to just what fits, and the values stay whole (the weather script's sky on a phone, about
     * 43 cells). Below 10 cells the label isn't cut, and the row breaks instead.
     */
    @Test
    fun bigNumbersCutTheLongestLabelToFitARow() {
        val m = weather
        for (cols in listOf(40, 43, 47)) {
            val block = LineLayout.layout(m, LogLook(cols), null)
            val rows = (0 until block.rows).map { block.row(it) }
            val name = "At $cols:\n" + rows.joinToString("\n")
            val values = rows.indexOfFirst { "17°C" in it }
            assertTrue(name, values >= 0)
            for (v in listOf("17°C", "70%", "→ 54 km/h")) assertTrue(name, v in rows[values])
            val labels = rows[values + 1]
            assertTrue(name, "🌦 Patchy" in labels && "…" in labels && "Patchy light rain" !in labels)
            // Just what fits: UV ends at the grid's right edge, on the row of the others.
            assertEquals(name, cols, CellWidth.total(labels.substring(0, labels.indexOf("UV"))) + 2)
        }
        val wide = LineLayout.layout(m, LogLook(53), null)
        assertTrue((0 until wide.rows).any { "🌦 Patchy light rain" in wide.row(it) })
        // 9 cells for the label would be too few: whole, with UV on the next pair of rows.
        val narrow = LineLayout.layout(m, LogLook(39), null)
        val rows = (0 until narrow.rows).map { narrow.row(it) }
        assertTrue(rows.joinToString("\n"), rows.any { "🌦 Patchy light rain" in it } && rows.none { "…" in it })
    }

    /** A column chart: two rows of eighths, the newest column in the accent color, slots to come as dots, labels under. */
    @Test
    fun aColumnChartIsBlocks() {
        val block = layout(LogCorpus.chart, time = null)
        val rows = (0 until block.rows).map { block.row(it) }
        val bottom = rows.indexOfFirst { it.contains('▅') }
        assertTrue(rows.toString(), bottom > 0)
        // 1, 3 and 2 on one scale: a third, all and two thirds of 16 eighths; two cells a column.
        assertEquals("▅▅████··", rows[bottom].substring(2).replace(" ", ""))
        assertEquals("██▃▃", rows[bottom - 1].substring(2).replace(" ", ""))
        // The newest column (the 3rd) is the accent, the others faint.
        val col = 2 + 4
        assertFalse(block.grid.styleAt(col, bottom).has(CellStyle.FAINT))
        assertTrue(block.grid.styleAt(2, bottom).has(CellStyle.FAINT))
        assertEquals("00      12", rows[bottom + 1].substring(2).trimEnd())
    }

    /** Buttons are `[ label ]` in the accent color; once one was tapped only it shows, muted, with a ✓. */
    @Test
    fun buttonsAreBracketed() {
        val block = layout(LogCorpus.buttons)
        assertTrue(block.dump(), block.dump().contains("[ Snooze 30 min ]  [ Done ]"))
        assertEquals(
            setOf(Target.Button(0, "Snooze 30 min"), Target.Button(1, "Done")),
            block.targets.map { it.target }.filterIsInstance<Target.Button>().toSet()
        )
        val done = layout(LogCorpus.buttonDone)
        assertTrue(done.dump(), done.dump().contains("[✓ Done ]"))
        assertFalse(done.dump(), done.dump().contains("Snooze"))
        assertTrue(done.targets.none { it.target is Target.Button })
    }

    /** No pictures: a picture is its name, people their initials, a notification's icon its app's name. */
    @Test
    fun noPicturesOnTheGrid() {
        val picture = layout(LogCorpus.picture)
        assertTrue(picture.dump(), picture.dump().contains("[image: comic.png]"))
        val call = layout(LogCorpus.call)
        assertTrue(call.row(0), call.row(0).startsWith("│ AL Ada Lovelace"))
        assertTrue(call.grid.styleAt(2, 0).has(CellStyle.INVERSE))
        val notification = LineLayout.layout(lineModel(row(LogCorpus.notification), options) { true }, LogLook(60))
        assertTrue(notification.row(0), notification.row(0).contains("New message from Ada · Chat"))
    }

    /** Two columns and help's name column put the description beside the name. */
    @Test
    fun columnsSitSideBySide() {
        val two = layout(LogCorpus.twoColumns, time = null)
        assertTrue(two.row(0), two.row(0).startsWith("│ log.dim"))
        assertTrue(two.dump(), two.dump().contains("dims"))
        val help = layout(LogCorpus.nameColumns, cols = 60, time = null)
        val name = "notifications".length + 1
        assertEquals("│ torch" + " ".repeat(name - 5) + "Turns", help.row(0).substring(0, 2 + name + 5))
        assertTrue(help.dump(), help.dump().contains("on · off"))
        // With a bar, it starts the second column.
        val bar = layout(LogCorpus.twoColumnsBar, time = null)
        assertTrue(bar.row(0), bar.row(0).contains("Maps"))
        assertTrue(bar.row(0), bar.row(0).indexOf('▕') > bar.row(0).indexOf("Maps"))
    }

    @Test
    fun anAppsGridListsNamesInColumns() {
        val block = layout(LogCorpus.apps, time = null)
        // Each column as wide as the longest name and two cells.
        assertTrue(block.row(0), block.row(0).startsWith("│ maps   clock"))
        assertEquals(listOf(Target.App(0), Target.App(1)), block.targets.map { it.target })
        val none = LineLayout.layout(model(LogCorpus.apps), LogLook(40))
        assertTrue(none.dump().contains("(apps no longer installed)"))
    }

    /**
     * A capped apps grid shows its first apps, then "+N more" (the fold's target), with each chip
     * still its own app's; open, or as a search's result, all of them and "▴ Show less".
     */
    @Test
    fun aCappedAppsGridShowsItsFirstAppsThenHowManyMore() {
        val m = model(LogCorpus.apps)
        val apps = listOf(
            GridApp("maps"),
            GridApp("clock", shownFolded = false),
            GridApp("mail", tag = "W", tagInk = Ink.WORK),
            GridApp("notes", shownFolded = false),
        )
        val folded = LineLayout.layout(m, LogLook(40), apps = apps)
        assertEquals("│ maps    W mail\n│ +2 more", folded.dump())
        assertEquals(listOf(Target.App(0), Target.App(2), Target.Fold), folded.targets.map { it.target })
        assertEquals(2, folded.foldedApps)
        assertEquals(Target.Fold, folded.hitAt(3, 1))
        assertTrue(LineLayout.actions(folded).contains(Target.Fold to "Show more"))

        val open = LineLayout.layout(m, LogLook(40), foldOpen = true, apps = apps)
        assertEquals("│ maps    clock   W mail  notes\n│ ▴ Show less", open.dump())
        assertEquals((0..3).map { Target.App(it) } + Target.Fold, open.targets.map { it.target })
        assertEquals(0, open.foldedApps)
        assertTrue(LineLayout.actions(open).contains(Target.Fold to "Fold"))
        assertEquals(open.dump(), LineLayout.layout(m, LogLook(40), marks = listOf("mail"), apps = apps).dump())

        // Without a cap, no "+N more".
        val all = LineLayout.layout(m, LogLook(40), apps = apps.map { it.copy(shownFolded = true) })
        assertEquals("│ maps    clock   W mail  notes", all.dump())
        assertEquals(0, all.foldedApps)
    }

    /** A tall line folds after `log.fold` rows, with how many more; open, it shows all and "▴ Show less". */
    @Test
    fun aTallLineFolds() {
        val folded = layout(LogCorpus.long, time = null)
        // A title and 40 lines: 8 show, 33 are hidden.
        assertEquals(9, folded.rows)
        assertEquals(33, folded.foldedRows)
        assertTrue(folded.row(8), folded.row(8).startsWith("│ ▾ 33 more lines"))
        assertEquals(Target.Fold, folded.hitAt(4, 8))
        val open = layout(LogCorpus.long, time = null, foldOpen = true)
        assertEquals(42, open.rows)
        assertTrue(open.row(41), open.row(41).startsWith("│ ▴ Show less"))
        assertEquals(0, open.foldedRows)
        // A search result never folds: its words must show.
        assertEquals(41, layout(LogCorpus.long, time = null, marks = listOf("line")).rows)
        // Nor does a line with log.fold off.
        assertEquals(41, layout(LogCorpus.long, look = LogLook(40, foldLines = 0), time = null).rows)
    }

    /** Older output is faint (a color, not a layer), cell by cell, the gutter too. */
    @Test
    fun dimmedRowsAreFaint() {
        val block = layout(LogCorpus.details, dimmed = true)
        for (r in 0 until block.rows) for (c in 0 until block.cols) {
            if (block.grid.glyphAt(c, r) != CellBuffer.EMPTY) assertTrue("$r,$c", block.grid.styleAt(c, r).has(CellStyle.FAINT))
        }
        // log.dim off: as bright as the newest output.
        val bright = layout(LogCorpus.details, look = LogLook(40, dim = false), dimmed = true)
        assertFalse(bright.grid.styleAt(2, 0).has(CellStyle.FAINT))
    }

    /** A search's words are marked wherever they show, whatever their case. */
    @Test
    fun searchWordsAreMarked() {
        val block = layout(LogCorpus.details, marks = listOf("CHARGING"))
        val r = 1
        val at = block.row(r).indexOf("charging")
        for (c in at until at + 8) assertEquals(MARK, block.grid.styleAt(c, r))
        assertFalse(block.grid.styleAt(at + 9, r) == MARK)
    }

    /** An unread script line starts with a bullet; an item's gap is rows above it that the gutter runs through. */
    @Test
    fun unreadLinesAndItemGaps() {
        val unread = layout(LogCorpus.scriptLine, unread = true)
        assertTrue(unread.row(0), unread.row(0).startsWith("│ • 12°C, clear · weather"))
        val m = lineModel(row(LogCorpus.item, itemGap = true), options)
        val block = LineLayout.layout(m, LogLook(40, itemGapRows = 1))
        assertEquals("│", block.row(0).trimEnd())
        assertTrue(block.row(1), block.row(1).startsWith("│ Second story"))
    }

    @Test
    fun dividersFillTheWidth() {
        val day = LineLayout.day("Tue 17 Nov", 30)
        assertEquals(2, day.rows)
        assertEquals("── Tue 17 Nov ────────────────", day.row(1))
        val unread = LineLayout.unread(3, 30)
        assertEquals("── 3 new since you left ──────", unread.row(1))
        assertEquals(Ink.PROMPT, unread.grid.styleAt(0, 1).fg)
    }

    /**
     * Wrapping, over random text: no row is wider than the grid, no wide character is split, and
     * the words read back in order.
     */
    @Test
    fun wrappingKeepsEveryWord() {
        val random = Random(7)
        val alphabet = listOf("a", "bb", "ccc", "dddddddddddddddddddddddddddd", "中", "😀", "é", " ", " ", "  ")
        repeat(200) {
            val text = (0 until random.nextInt(1, 60)).joinToString("") { alphabet[random.nextInt(alphabet.size)] }
            val cols = random.nextInt(MIN, 50)
            val w = BlockWriter(cols)
            val end = w.wrap(listOf(Seg(text, CellStyle.PLAIN)), 0, 0, 2, cols)
            val block = w.build()
            assertTrue(end >= 1)
            val shown = (0 until block.rows).joinToString(" ") { block.row(it) }
            assertEquals("“$text” at $cols", text.split(' ').filter { it.isNotEmpty() }.joinToString(""), shown.replace(" ", ""))
        }
    }

    /** The empty log's hints and the like: lines of text in their looks. */
    @Test
    fun linesOfText() {
        val block = LineLayout.lines(20, listOf("Type an app name or a command" to CellStyle.PLAIN, "help lists everything" to DIM))
        assertEquals(listOf("Type an app name or", "a command", "help lists", "everything"), (0 until block.rows).map { block.row(it).trimEnd() })
    }

    private companion object {
        const val MIN = LineLayout.MIN_COLS
    }
}
