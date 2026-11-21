/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.Suggestion

/**
 * The suggestions on the grid, in both styles (`suggestions.style`). No icons: a command starts
 * with the prompt's `$`, an app is its name, and a profile's tag is text (`[W]`, inverse in the
 * profile's color).
 * - [row] (`list`): `$ name  description`, the description muted, cut short or wrapped in its own
 *   column (`suggestions.wrap`). The row Enter acts on has its name inverse and `↵` at its end.
 *   The calculator's answer is bold.
 * - [strip] (`chips`): ` $ name ` chips on the surface color, a cell apart, in one or two rows.
 *   The chip Enter acts on is inverse.
 * An app whose profile is paused is faint.
 */
object SuggestionCells {
    /** The cells before a name: `$ ` for a command, blank for an app, so names line up. */
    const val LEAD = 2

    /** Narrower than this, a wrapped description goes under the name instead of beside it. */
    private const val MIN_HELP = 12

    /** A profile tag at the end of a completion's label (`Maps [W]`). */
    private val LABEL_TAG = Regex(" \\[([PWSC])]$")

    /** Enter's mark at the end of the row it acts on. */
    const val ENTER = "↵"

    /** [s]'s name, and its profile's tag and ink when it has one (an app of another profile, a completion about one). */
    private class Name(val text: String, val tag: String?, val tagInk: Ink)

    private fun name(s: Suggestion): Name = when (s) {
        is Suggestion.App -> {
            val kind = s.app.profile.kind
            Name(s.label, if (kind == ProfileKind.PERSONAL) null else "[${kind.tag}]", Ink.of(kind))
        }

        is Suggestion.Completion -> {
            val m = LABEL_TAG.find(s.label)
            if (m == null) Name(s.label, null, Ink.FG)
            else Name(s.label.substring(0, m.range.first), "[${m.groupValues[1]}]", Ink.of(ProfileKind.entries.first { it.tag == m.groupValues[1] }))
        }
    }

    /** The row's own ink: a profile's for an app, the accent for a command. */
    fun inkOf(s: Suggestion): Ink = when (s) {
        is Suggestion.App -> Ink.of(s.app.profile.kind)
        is Suggestion.Completion -> Ink.PROMPT
    }

    private fun quiet(s: Suggestion) = s is Suggestion.App && s.app.profile.quiet

    /**
     * One row of the list, [cols] wide, as many rows tall as it needs (more than one only when
     * [wrap] wraps a long description). [enter]: Enter acts on it. [help] is its description, if any.
     * The whole row is one spot, id 0.
     */
    fun row(s: Suggestion, help: String?, cols: Int, enter: Boolean, wrap: Boolean): HomeCells {
        val w = HomeWriter(cols)
        val ink = inkOf(s)
        val faint = if (quiet(s)) CellStyle.FAINT else 0
        val right = if (enter) cols - 2 else cols
        val n = name(s)
        val big = (s as? Suggestion.Completion)?.big
        val shownName = oneLine(big?.first ?: n.text)
        // The name: bold and inverse in the row's ink when Enter acts on it.
        val nameStyle = when {
            enter -> CellStyle.of(ink, flags = CellStyle.INVERSE or CellStyle.BOLD or faint)
            big != null -> CellStyle.of(Ink.FG, flags = CellStyle.BOLD or faint)
            else -> CellStyle.of(Ink.FG, flags = faint)
        }
        if (s is Suggestion.Completion) w.put(0, 0, "$ ", if (enter) nameStyle else CellStyle.of(Ink.PROMPT, flags = CellStyle.BOLD or faint))
        else if (enter) w.put(0, 0, "  ", nameStyle)
        val tagWidth = n.tag?.let { CellWidth.total(it) + 1 } ?: 0
        var end = w.line(shownName + if (enter) " " else "", nameStyle, 0, LEAD, (right - tagWidth).coerceAtLeast(LEAD + 1))
        n.tag?.let {
            end = w.put(end + if (enter) 0 else 1, 0, it, CellStyle.of(n.tagInk, flags = CellStyle.INVERSE or CellStyle.BOLD or faint))
        }
        val desc = big?.second?.takeIf { it.isNotBlank() } ?: help?.takeIf { it.isNotBlank() }
        var rows = 1
        if (desc != null) {
            val muted = CellStyle.of(Ink.DIM, flags = faint)
            val from = end + 2
            when {
                !wrap -> if (from < right - 1) w.line(oneLine(desc), muted, 0, from, right)
                from <= right - MIN_HELP -> rows = w.wrap(listOf(Seg(desc, muted)), 0, from, from, right)
                else -> rows = w.wrap(listOf(Seg(desc, muted)), 1, LEAD + 2, LEAD + 2, right)
            }
        }
        if (enter) w.put(cols - 1, 0, ENTER, CellStyle.of(Ink.DIM))
        w.spot(0, 0, 0, cols, rows.coerceAtLeast(1))
        return w.build()
    }

    /** A chip's text: ` $ name ` for a command, ` Name [W] ` for an app. */
    private fun chipSegs(s: Suggestion, enter: Boolean): List<Seg> {
        val ink = inkOf(s)
        val faint = if (quiet(s)) CellStyle.FAINT else 0
        val n = name(s)
        if (enter) {
            val style = CellStyle.of(ink, flags = CellStyle.INVERSE or CellStyle.BOLD or faint)
            val lead = if (s is Suggestion.Completion) " $ " else " "
            return listOf(Seg(lead + oneLine(n.text) + (n.tag?.let { " $it" } ?: "") + " ", style))
        }
        val bg = Ink.SURFACE
        val out = ArrayList<Seg>()
        out += Seg(" ", CellStyle.of(Ink.FG, bg, faint))
        if (s is Suggestion.Completion) out += Seg("$ ", CellStyle.of(Ink.PROMPT, bg, CellStyle.BOLD or faint))
        out += Seg(oneLine(n.text), CellStyle.of(if (s is Suggestion.App) Ink.FG else Ink.DIM, bg, faint))
        n.tag?.let { out += Seg(" $it", CellStyle.of(n.tagInk, bg, CellStyle.BOLD or faint)) }
        out += Seg(" ", CellStyle.of(Ink.FG, bg, faint))
        return out
    }

    /** The cells [s]'s chip takes. */
    fun chipWidth(s: Suggestion): Int = chipSegs(s, false).sumOf { CellWidth.total(it.text) }

    /**
     * Which chips go on which row: all on one, or with [rows] 2 and more than three chips, each on
     * the row that is shorter so far (as the GUI's staggered grid fills its rows). [widths] are the
     * chips' cells; [gap] the cells between two chips.
     */
    fun pack(widths: List<Int>, rows: Int, gap: Int = 1): List<List<Int>> {
        if (rows < 2 || widths.size <= 3) return listOf(widths.indices.toList())
        val lines = List(2) { ArrayList<Int>() }
        val used = IntArray(2)
        for ((i, width) in widths.withIndex()) {
            val r = if (used[1] < used[0]) 1 else 0
            lines[r] += i
            used[r] += width + gap
        }
        return lines
    }

    /**
     * The strip's rows: the chips of [items] that [pack] puts on each, a cell apart, from the
     * strip's start. A row is as wide as its chips (it scrolls when wider than the screen). A
     * chip's spot id is its index in [items]; only the first chip can be the one Enter acts on.
     */
    fun strip(items: List<Suggestion>, rows: Int, gap: Int = 1): List<HomeCells> =
        pack(items.map(::chipWidth), rows, gap).map { line ->
            val width = line.sumOf { chipWidth(items[it]) + gap } - gap
            val w = HomeWriter(width.coerceAtLeast(1))
            var x = 0
            for (i in line) {
                val s = items[i]
                var col = x
                for (seg in chipSegs(s, s.enter && i == 0)) col = w.put(col, 0, seg.text, seg.style)
                w.spot(i, x, 0, col - x)
                x = col + gap
            }
            w.build()
        }
}
