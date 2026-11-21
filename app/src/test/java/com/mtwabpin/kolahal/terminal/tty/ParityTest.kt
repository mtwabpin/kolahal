package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.terminal.LineModel
import com.mtwabpin.kolahal.terminal.LineOptions
import com.mtwabpin.kolahal.terminal.LogCorpus
import com.mtwabpin.kolahal.terminal.Target
import com.mtwabpin.kolahal.terminal.TimeMode
import com.mtwabpin.kolahal.terminal.lineModel
import com.mtwabpin.kolahal.terminal.spoken
import com.mtwabpin.kolahal.terminal.targets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two ways of drawing the log can't drift (design §8.2): both draw from one [LineModel], and
 * for every row of [LogCorpus] the grid ([LineLayout]) shows what the model says a screen reader
 * reads, and has every target the model lists, where its words are, with the same TalkBack
 * actions. A new kind of body or block doesn't compile here until it is named, and then fails
 * until the corpus has a row of it that the grid lays out.
 */
class ParityTest {
    private val options = LineOptions(TimeMode.ALL, foldLines = 8)
    private val widths = listOf(32, 40, 80)

    private fun model(line: LogCorpus.Line) = lineModel(LogCorpus.row(line), options)

    private fun appsOf(m: LineModel) =
        (m.body as? LineModel.Body.AppGrid)?.apps?.map { GridApp(it.pkg.substringAfterLast('.')) }.orEmpty()

    private fun layout(m: LineModel, cols: Int, rtl: Boolean): CellBlock =
        LineLayout.layout(m, LogLook(cols, rtl = rtl), "14:02", foldOpen = true, apps = appsOf(m), initials = m.person?.let { "AL" })

    /** Each kind of body by name. No `else`: a new kind must be named here. */
    private fun kindOf(b: LineModel.Body): String = when (b) {
        is LineModel.Body.Words -> "words"
        is LineModel.Body.TwoColumns -> "two columns"
        is LineModel.Body.NameColumns -> "name columns"
        is LineModel.Body.AppGrid -> "apps"
    }

    /** Each kind of block by name. No `else`: a new kind must be named here. */
    private fun kindOf(b: LineModel.Block): String = when (b) {
        is LineModel.Block.Text -> "text"
        is LineModel.Block.Table -> "table"
        is LineModel.Block.Meters -> "bars"
        is LineModel.Block.Stats -> "big numbers"
        is LineModel.Block.Chart -> "chart"
    }

    private val bodies = setOf("words", "two columns", "name columns", "apps")
    private val blocks = setOf("text", "table", "bars", "big numbers", "chart")

    /** The corpus has a row of every kind, so the tests below see each one laid out. */
    @Test
    fun theCorpusHasEveryKind() {
        val models = LogCorpus.all.map(::model)
        assertEquals(bodies, models.map { kindOf(it.body) }.toSet())
        assertEquals(blocks, models.flatMap { m -> m.blocks.map { kindOf(it) } }.toSet())
        assertTrue("a built-in bar", models.any { it.bar != null })
        assertTrue("a picture", models.any { it.image != null })
        assertTrue("buttons", models.any { it.buttons.isNotEmpty() })
        assertTrue("a person", models.any { it.person != null })
        assertTrue("a source", models.any { it.source != null })
        assertTrue("a link", models.any { m -> m.targets().any { it is Target.Link } })
    }

    /** [s] without spaces or the grid's box drawing and blocks, for comparing what shows across wrapped rows. */
    private fun bare(s: String) = s.filterNot { it.isWhitespace() || it.code in 0x2500..0x259F }

    /**
     * The words a screen reader reads for the row ([spoken]) that the grid must show too: all of
     * them but "new" (an unread line shows a bullet), a chart's sentence (it shows as columns),
     * a bar's "N percent" (it shows as a bar) and an apps line's own text (the apps show instead).
     */
    private fun shownWords(m: LineModel): List<String> {
        val skip = buildList {
            for (b in m.blocks) if (b is LineModel.Block.Chart) add(b.columns.said.joinToString("") { it.text })
            if (m.body is LineModel.Body.AppGrid) add(m.entry.text)
            // A line with no title shows only its blocks, in both styles: its source is read, not shown.
            val body = m.body
            if (body is LineModel.Body.Words && body.blank) m.source?.let { add(it.name) }
        }
        var text = m.spoken("14:02")
        for (s in skip) if (s.isNotEmpty()) text = text.replace(s, " ")
        // "unread" is spoken as "new"; a bar with no text as "82 percent".
        text = text.replace(Regex("^new, "), " ").replace(Regex("\\d+ percent"), " ")
        return text.split(Regex("[\\s,.;:]+")).filter { it.isNotBlank() }
    }

    /**
     * Every word a screen reader reads for a row shows on the grid, in both directions. At 80
     * columns: narrower, a bar's texts are cut with "…" to keep its track (the grid's one row).
     */
    @Test
    fun theGridShowsWhatIsSpoken() {
        for (line in LogCorpus.all) for (cols in listOf(80)) for (rtl in listOf(false, true)) {
            val m = model(line)
            val block = layout(m, cols, rtl)
            val shown = bare((0 until block.rows).joinToString("") { block.grid.rowText(it) })
            for (word in shownWords(m)) {
                assertTrue("${line.name} at $cols${if (rtl) ", right to left" else ""}: “$word” is not shown in\n${block.dump()}", bare(word) in shown)
            }
            for (app in appsOf(m)) assertTrue("${line.name}: ${app.label}", app.label in shown)
        }
    }

    /**
     * The grid has every target the model lists, and its cells hold the target's words: a link's
     * text, a button's label, an app's name, the fold's arrow.
     */
    @Test
    fun everyTargetShowsWhereItIs() {
        for (line in LogCorpus.all) for (cols in widths) for (rtl in listOf(false, true)) {
            val m = model(line)
            val block = layout(m, cols, rtl)
            val name = "${line.name} at $cols${if (rtl) ", right to left" else ""}"
            assertEquals(name, m.targets().toSet() - Target.Line, block.targets.map { it.target }.toSet() - Target.Fold)
            for (t in block.targets) {
                val cells = bare(t.rects.joinToString("") { r ->
                    (r.row until r.row + r.rows).joinToString("") { row ->
                        val text = block.grid.rowText(row)
                        // Columns to string indices: the corpus's targets hold one-cell characters.
                        text.substring(r.col.coerceAtMost(text.length), (r.col + r.cols).coerceAtMost(text.length))
                    }
                })
                val expected = when (val target = t.target) {
                    is Target.Link -> bare(target.text)
                    is Target.Button -> bare(target.label)
                    is Target.App -> bare(appsOf(m)[target.index].label)
                    Target.Fold -> "▴"
                    Target.Line -> ""
                }
                assertTrue("$name: $t holds “$cells”", expected.startsWith(cells) || cells.contains(expected))
            }
        }
    }

    /**
     * TalkBack offers the same actions on a row in both styles: an "Open …" for each of the model's
     * links, each of its buttons, Copy and Delete. Each action acts on a target the grid has.
     */
    @Test
    fun theActionsAreTheModels() {
        for (line in LogCorpus.all) {
            val m = model(line)
            val block = layout(m, 40, rtl = false)
            val actions = LineLayout.actions(block)
            val labels = actions.map { it.second }
            for (t in m.targets()) when (t) {
                is Target.Link -> assertTrue("${line.name}: $labels", "Open ${t.text}" in labels)
                is Target.Button -> assertTrue("${line.name}: $labels", t.label in labels)
                else -> Unit
            }
            assertEquals(listOf("Copy", "Delete"), labels.takeLast(2))
            for ((target, _) in actions) if (target != null) assertTrue(block.targets.any { it.target == target })
        }
    }
}
