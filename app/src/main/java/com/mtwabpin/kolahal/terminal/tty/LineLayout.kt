/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.tty

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.terminal.Gutter
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.LineModel
import com.mtwabpin.kolahal.terminal.Para
import com.mtwabpin.kolahal.terminal.Piece
import com.mtwabpin.kolahal.terminal.Target
import com.mtwabpin.kolahal.terminal.linksPerPiece

/**
 * The settings a log row's layout reads, besides its model: the grid's width in [cols], the
 * gutter (`log.gutter`), dimmed old lines (`log.dim`), boxed tables (`log.tables`), folding
 * (`log.fold`), and the rows of an item's gap (`log.item-spacing` in whole rows).
 * [rtl]: the frame runs right to left (an Arabic, Hebrew or Persian layout): the gutter is on the
 * right, the time on the left, a table's first column on the right, bars fill from the right.
 */
data class LogLook(
    val cols: Int,
    val gutter: Boolean = true,
    val dim: Boolean = true,
    val boxedTables: Boolean = true,
    val foldLines: Int = 8,
    val itemGapRows: Int = 0,
    val rtl: Boolean = false,
)

/**
 * An app of an apps grid, as the renderer found it among the installed apps: its name, and its
 * profile's [tag] (W, S…, none for the personal profile) in [tagInk]. [quiet]: its profile is
 * paused, so it is faint. [shownFolded]: it shows while a capped grid is folded
 * ([LineModel.Body.AppGrid.showsFolded]).
 */
data class GridApp(
    val label: String,
    val tag: String? = null,
    val tagInk: Ink = Ink.FG,
    val quiet: Boolean = false,
    val shownFolded: Boolean = true,
)

/**
 * Lays out log rows on the grid: a [LineModel] becomes a [CellBlock] of [LogLook.cols] columns.
 * Pure, so the same row gives the same cells on every phone and in JVM tests.
 *
 * A row, left to right: the gutter (`│` in column 0, then a space), a person's initials, the
 * text, and the time at the right edge of its first row. Under the words come the blocks
 * (tables, bars, big numbers, charts), then a picture's name, the buttons, and a search's
 * "found in" line. One font size: emphasis is bold, inverse or a color, never a bigger size.
 *
 * Right-to-left text: each row of a paragraph shows its letters in the order the Unicode bidi
 * algorithm gives ([BidiText]), and words of joining scripts are whole pieces the painter shapes.
 * Rows start at the frame's start, as the GUI's short lines do. In a right-to-left frame
 * ([LogLook.rtl]) all of it is mirrored, from the gutter to the bars, while words read as they are.
 */
object LineLayout {
    /** Narrower than this, a row is laid out this wide anyway; the screen shows its left part. */
    const val MIN_COLS = 16

    /** Rows past `log.fold` fold only when this many more would be hidden, as in the GUI. */
    const val FOLD_SLACK = 2

    /** The least room a row keeps for its text beside the time. */
    private const val MIN_TEXT = 8

    /** The least cells a bar gets after a line's words. */
    private const val MIN_BAR = 8

    /**
     * [model]'s row. [time] is the time as the row shows it, null for none. [foldOpen]: a tall line
     * shows all of it. [marks]: a search's words, marked (and then the line never folds).
     * [apps]: an apps grid's apps, as found. [initials]: a person's initials. [footer]: a search
     * result's "found in" line.
     */
    fun layout(
        model: LineModel,
        look: LogLook,
        time: String? = null,
        foldOpen: Boolean = false,
        marks: List<String> = emptyList(),
        apps: List<GridApp> = emptyList(),
        /** What an apps grid says when [apps] is empty. */
        gone: String = com.mtwabpin.kolahal.apps.PrivateSpace.GRID_GONE,
        initials: String? = null,
        footer: String? = null,
    ): CellBlock {
        val w = BlockWriter(look.cols.coerceAtLeast(MIN_COLS))
        val cols = w.cols
        val left = if (look.gutter) 2 else 0
        val top = if (model.gapAbove == LineModel.Gap.ITEM) look.itemGapRows else 0
        if (top > 0) w.reach(top - 1)
        // The time: right-aligned and muted on the first row; text on that row stops a cell short
        // of it. It is written last, so where the words end is where they end.
        val timeWidth = time?.let(CellWidth::total) ?: 0
        val showTime = time != null && left + MIN_TEXT + 1 + timeWidth <= cols
        if (showTime) w.reserve(top, cols - timeWidth - 1)
        var textLeft = left
        val person = model.person
        if (person != null && !initials.isNullOrEmpty()) {
            val shown = if (CellWidth.total(initials) >= 2) initials else "$initials "
            w.put(textLeft, top, shown, CellStyle.of(avatarInk(person.name), flags = CellStyle.INVERSE or CellStyle.BOLD))
            textLeft += CellWidth.total(shown) + 1
        }
        // While folded, only the rows that show (and the few that decide whether it folds) are kept.
        val mayFold = model.mayFold && marks.isEmpty() && look.foldLines > 0
        if (mayFold && !foldOpen) w.keepRows = top + look.foldLines + FOLD_SLACK + 1
        // A capped apps grid hides these many apps behind its "+N more" row; a search's result shows them all.
        val appsOpen = foldOpen || marks.isNotEmpty()
        val foldedApps = if (model.body is LineModel.Body.AppGrid && !appsOpen) apps.count { !it.shownFolded } else 0
        var row = when (val body = model.body) {
            is LineModel.Body.Words -> words(w, model, body, top, textLeft, marks)
            is LineModel.Body.TwoColumns -> twoColumns(w, model, body, top, textLeft, marks)
            is LineModel.Body.NameColumns -> nameColumns(w, body, top, textLeft, marks)
            is LineModel.Body.AppGrid -> appGrid(w, apps, top, textLeft, appsOpen, gone)
        }
        val blockLeft = textLeft + if (model.unread) 2 else 0
        for (block in model.blocks) row = Blocks.layout(w, block, row, blockLeft, cols, look.boxedTables, marks)
        w.keepRows = Int.MAX_VALUE
        // A tall line folds: its first rows, then "▾ N more lines". Open, "▴ Show less" under it.
        var folded = 0
        if (mayFold && row - top > look.foldLines + FOLD_SLACK) {
            if (!foldOpen) {
                folded = row - top - look.foldLines
                row = top + look.foldLines
                w.cut(row)
            }
            val label = if (foldOpen) "▴ Show less" else "▾ $folded more ${if (folded == 1) "line" else "lines"}"
            val end = w.line(listOf(Seg(label, CellStyle.of(Ink.LINK))), row, textLeft, cols)
            w.mark(Target.Fold, textLeft, row, end - textLeft)
            row++
        }
        // No pictures on the grid: a picture shows as its name, in the link's look when the line opens a link.
        model.image?.let { address ->
            val name = address.substringBefore('?').substringBefore('#').trimEnd('/').substringAfterLast('/')
            val style = if (model.entry.ctx.url != null) CellStyle.of(Ink.LINK, flags = CellStyle.UNDERLINE) else DIM
            row = w.wrap(listOf(Seg("[image: ${name.ifEmpty { "picture" }}]", style)), row, blockLeft, blockLeft, cols)
        }
        if (model.buttons.isNotEmpty()) row = buttons(w, model.buttons, row, textLeft)
        if (footer != null) {
            w.line(listOf(Seg(footer, DIM)), row, textLeft, cols, marks)
            row++
        }
        w.reach(row - 1)
        if (showTime) w.put(cols - timeWidth, top, time!!, DIM)
        // The gutter runs down the whole row, through an item's gap above it.
        if (look.gutter) for (r in 0 until w.rows) w.put(0, r, "│", gutterStyle(model.gutter))
        return w.build(if (look.dim && model.dimmed) CellStyle.FAINT else 0, folded, mirrored = look.rtl, foldedApps = foldedApps)
    }

    /**
     * The actions a screen reader offers on a laid-out row besides its tap and long press, in
     * reading order: "Open …" for each link, each button by its label, "Show more" or "Fold" for
     * the fold, then Copy and Delete (a swipe's two). Each with the target it acts on; null for
     * the line itself. Built from [block]'s targets, so it can't offer what a finger can't hit.
     */
    fun actions(block: CellBlock): List<Pair<Target?, String>> = buildList {
        for (t in block.targets) when (val target = t.target) {
            is Target.Link -> add(target to "Open ${target.text}")
            is Target.Button -> add(target to target.label)
            Target.Fold -> add(target to if (block.foldedRows > 0 || block.foldedApps > 0) "Show more" else "Fold")
            is Target.App, Target.Line -> Unit
        }
        add(null to "Copy")
        add(null to "Delete")
    }

    /** `── Tue 17 Nov ──────`, muted, under a blank row. [label] is the day as the log names it. */
    fun day(label: String, cols: Int, rtl: Boolean = false): CellBlock = divider("── $label ", DIM, cols, rtl)

    /** `── 3 new since you left ──────`, in the accent color, under a blank row. */
    fun unread(count: Int, cols: Int, rtl: Boolean = false): CellBlock =
        divider("── $count new since you left ", CellStyle.of(Ink.PROMPT, flags = CellStyle.BOLD), cols, rtl)

    /** Lines of text, each in its look, wrapped to [cols]: the empty log's hints, "▴ 12 earlier lines". */
    fun lines(cols: Int, lines: List<Pair<String, CellStyle>>, rtl: Boolean = false): CellBlock {
        val w = BlockWriter(cols.coerceAtLeast(MIN_COLS))
        var row = 0
        for ((text, style) in lines) row = w.wrap(listOf(Seg(text, style)), row, 0, 0, w.cols)
        return w.build(mirrored = rtl)
    }

    private fun divider(text: String, style: CellStyle, cols: Int, rtl: Boolean): CellBlock {
        val w = BlockWriter(cols.coerceAtLeast(MIN_COLS))
        w.reach(0)
        val end = w.line(listOf(Seg(text, style)), 1, 0, w.cols)
        w.repeat(end, 1, '─', w.cols - end, style)
        return w.build(mirrored = rtl)
    }

    /**
     * A line's words: the title (a command's `$ `, an unread script line's `•`), the source's name
     * after it, then the details or the body, muted. Wrapped rows are indented past the prefix, so
     * each item reads as one block. A built-in command's bar goes after the words on the first row.
     */
    private fun words(w: BlockWriter, model: LineModel, b: LineModel.Body.Words, top: Int, textLeft: Int, marks: List<String>): Int {
        if (b.blank) return top
        val bar = model.bar
        val right = if (bar != null) w.cols - MIN_BAR - 1 else w.cols
        val restIndent = if (b.indent == LineModel.Indent.NONE) 0 else 2
        val title = CellStyle.of(b.ink, flags = if (b.heading) CellStyle.BOLD else 0)
        val links = linksPerPiece(b.paras.flatMap { p -> p.pieces.map { it.text to it.url } })
        val lastTitle = b.paras.indexOfLast { it.role == Para.Role.TITLE }
        var k = 0
        var row = top
        for ((i, p) in b.paras.withIndex()) {
            val segs = ArrayList<Seg>()
            val base = if (p.role == Para.Role.TITLE) title else DIM
            var first = textLeft + restIndent
            if (p.role == Para.Role.TITLE && i == 0) {
                first = textLeft
                if (b.bullet) segs += Seg("• ", CellStyle.of(Ink.PROMPT, flags = CellStyle.BOLD))
            }
            var rest = textLeft + restIndent
            if (p.level > 0) {
                first += 2 * (p.level - 1)
                val prefix = "${p.prefix} "
                segs += Seg(prefix, base.ink(Ink.PROMPT))
                rest = first + CellWidth.total(prefix) + if (b.bullet && i == 0) 2 else 0
            } else if (b.bullet && i == 0) rest = maxOf(rest, textLeft + 2)
            for (piece in p.pieces) addPiece(segs, piece, base, links[k++])
            // The source goes at the end of the title, muted: `… machine. · wikipedia`.
            if (i == lastTitle) model.source?.let { segs += Seg(" · ${it.name}", DIM) }
            row = w.wrap(segs, row, first, rest, right, marks)
        }
        if (bar != null && row > top) {
            // After the widest row of the words, so rows of words as wide have their bars line up.
            val from = (top until row).maxOf { w.end(it) } + 1
            Blocks.track(w, bar, top, from.coerceAtLeast(textLeft), w.limit(top))
        }
        return row
    }

    /**
     * Two columns (config, usage, scripts): the name, bold, in 46 % of the width; the description,
     * muted, in the rest. A bar goes at the start of the second column, so bars of rows line up.
     */
    private fun twoColumns(w: BlockWriter, model: LineModel, b: LineModel.Body.TwoColumns, top: Int, textLeft: Int, marks: List<String>): Int {
        val width = w.cols - textLeft
        val nameWidth = (width * 0.46f).toInt().coerceAtLeast(1)
        val descLeft = textLeft + nameWidth + 1
        var nameEnd = top + 1
        for ((i, segs) in styledLines(b.name, CellStyle.of(Ink.FG, flags = CellStyle.BOLD), Ink.FG).withIndex()) {
            nameEnd = w.wrap(segs, if (i == 0) top else nameEnd, textLeft, textLeft, descLeft - 1, marks)
        }
        var textFrom = descLeft
        val bar = model.bar
        if (bar != null) {
            val trackEnd = descLeft + (w.limit(top) - descLeft) / 2
            Blocks.track(w, bar, top, descLeft, trackEnd)
            textFrom = trackEnd + 1
        }
        val descEnd = w.wrap(listOf(Seg(StyledText.plain(b.desc), DIM)), top, textFrom, textFrom, w.cols, marks)
        return maxOf(nameEnd, descEnd)
    }

    /**
     * `help`'s rows: the name, bold, in a column as wide as the list's longest name (at most half
     * the width); the description beside it, and its arguments muted under that.
     */
    private fun nameColumns(w: BlockWriter, b: LineModel.Body.NameColumns, top: Int, textLeft: Int, marks: List<String>): Int {
        val width = w.cols - textLeft
        val nameWidth = minOf(CellWidth.total(b.nameWidthOf) + 1, width / 2).coerceAtLeast(2)
        val descLeft = textLeft + nameWidth
        val nameEnd = w.wrap(listOf(Seg(b.name, CellStyle.of(Ink.FG, flags = CellStyle.BOLD))), top, textLeft, textLeft, descLeft - 1, marks)
        var row = top
        for ((i, segs) in styledLines(b.desc, CellStyle.of(Ink.FG), Ink.FG).withIndex()) {
            row = w.wrap(segs, if (i == 0) top else row, descLeft, descLeft, w.cols, marks)
        }
        if (!b.args.isNullOrEmpty()) row = w.wrap(listOf(Seg(b.args, DIM)), row, descLeft, descLeft, w.cols, marks)
        return maxOf(nameEnd, row)
    }

    /**
     * `apps` output as `ls` lists files: names in columns as wide as the longest name, down each
     * column first, as many columns as fit. A profile's tag goes before the name.
     * A capped grid (some apps not [GridApp.shownFolded]) shows its first apps, then a row
     * "+N more" that opens it in place; [open], it shows them all, then "▴ Show less".
     */
    private fun appGrid(w: BlockWriter, apps: List<GridApp>, top: Int, textLeft: Int, open: Boolean, gone: String): Int {
        if (apps.isEmpty()) {
            w.put(textLeft, top, gone, DIM)
            return top + 1
        }
        val capped = apps.any { !it.shownFolded }
        // Places in [apps], so each chip's target is its app's, folded or not.
        val shown = if (capped && !open) apps.indices.filter { apps[it].shownFolded } else apps.indices.toList()
        var end = top
        if (shown.isNotEmpty()) {
            val width = w.cols - textLeft
            fun cells(a: GridApp) = (a.tag?.let { CellWidth.total(it) + 1 } ?: 0) + CellWidth.total(a.label)
            val column = (shown.maxOf { cells(apps[it]) } + 2).coerceAtMost(width)
            val perRow = ((width + 2) / column).coerceIn(1, shown.size)
            val rows = (shown.size + perRow - 1) / perRow
            for ((k, i) in shown.withIndex()) {
                val app = apps[i]
                val x = textLeft + (k / rows) * column
                val row = top + k % rows
                val faint = if (app.quiet) CellStyle.FAINT else 0
                var col = x
                app.tag?.let { col = w.put(col, row, it, CellStyle.of(app.tagInk, flags = CellStyle.BOLD or faint)) + 1 }
                val last = w.line(listOf(Seg(app.label, CellStyle.of(Ink.FG, flags = faint))), row, col, minOf(x + column - 2, w.cols).coerceAtLeast(col + 1))
                w.mark(Target.App(i), x, row, last - x)
            }
            end = top + rows
        }
        if (capped) {
            val label = if (open) "▴ Show less" else "+${apps.size - shown.size} more"
            val last = w.line(listOf(Seg(label, CellStyle.of(Ink.LINK))), end, textLeft, w.cols)
            w.mark(Target.Fold, textLeft, end, last - textLeft)
            end++
        }
        return end
    }

    /** A script's buttons, `[ Snooze 30 min ]  [ Done ]`, as many on a row as fit. A tapped one shows `[✓ Done ]`, muted. */
    private fun buttons(w: BlockWriter, buttons: List<LineModel.Button>, top: Int, textLeft: Int): Int {
        var row = top
        var col = textLeft
        for (b in buttons) {
            val text = if (b.done) "[✓ ${b.label} ]" else "[ ${b.label} ]"
            val width = CellWidth.total(text)
            if (col > textLeft && col + width > w.cols) {
                row++
                col = textLeft
            }
            val end = w.line(listOf(Seg(text, if (b.done) DIM else CellStyle.of(Ink.PROMPT, flags = CellStyle.BOLD))), row, col, w.cols)
            if (!b.done) w.mark(Target.Button(b.index, b.label), col, row, end - col)
            col = end + 2
        }
        return row + 1
    }

    /** The gutter's ink for [g], faint: ui/tty also draws it through an item's gap above a line. */
    internal fun gutterStyle(g: Gutter) = CellStyle.of(
        when (g) {
            Gutter.COMMAND -> Ink.PROMPT
            Gutter.SCRIPT -> Ink.SCRIPT
            Gutter.ERROR -> Ink.ERROR
        },
        flags = CellStyle.FAINT,
    )

    /** A person's color, the same every time for a name: the GUI's avatars pick from the same roles, in the same order. */
    private fun avatarInk(name: String): Ink {
        val inks = arrayOf(Ink.PROMPT, Ink.LINK, Ink.INFO, Ink.WARN, Ink.WORK, Ink.PRIVATE, Ink.SCRIPT)
        return inks[Math.floorMod(name.trim().lowercase().hashCode(), inks.size)]
    }

    /** A profile tag at the end of a line (` [W]`), drawn inverse in its profile's color. */
    private val PROFILE_TAG = Regex("^ \\[[PWSC]]$")

    /** Adds [piece] to [segs] in its look on top of [base]. */
    private fun addPiece(segs: MutableList<Seg>, piece: Piece, base: CellStyle, link: Target.Link?) {
        if (piece.ink != null && link == null && PROFILE_TAG.matches(piece.text)) {
            segs += Seg(" ", base)
            segs += Seg(piece.text.substring(1), CellStyle.of(piece.ink, flags = CellStyle.INVERSE or CellStyle.BOLD))
            return
        }
        segs += seg(piece.text, piece.styles, piece.ink, base, link)
    }

    /** [text]'s lines, each as pieces in their looks on top of [base], with their links. Bold is in [strong]. */
    private fun styledLines(text: String, base: CellStyle, strong: Ink?): List<List<Seg>> {
        if (!StyledText.has(text)) return listOf(listOf(Seg(text, base)))
        return StyledText.lines(text).map { line ->
            val links = linksPerPiece(line.spans.map { it.text to it.url })
            val prefix = if (line.level > 0) listOf(Seg("  ".repeat(line.level - 1) + line.prefix + " ", base.ink(Ink.PROMPT))) else emptyList()
            prefix + line.spans.mapIndexed { i, s -> spanSeg(s, base, strong, links[i]) }
        }
    }
}

/** Muted text. */
internal val DIM = CellStyle.of(Ink.DIM)

/** The flag bits of a style, without its inks. */
private const val FLAGS = 0xFF.inv()

/**
 * [text] in its [styles] and [ink] on top of [base]: bold, italic, underline and strike as they
 * are; code on the surface color; a tag inverse, with a cell of room on each side; a link
 * underlined in the link color.
 */
internal fun seg(text: String, styles: Set<StyledText.Style>, ink: Ink?, base: CellStyle, link: Target.Link?): Seg {
    var fg = ink ?: base.fg
    var bg = base.bg
    var flags = base.bits and FLAGS
    var shown = text
    for (s in styles) when (s) {
        StyledText.Style.BOLD -> flags = flags or CellStyle.BOLD
        StyledText.Style.ITALIC -> flags = flags or CellStyle.ITALIC
        StyledText.Style.UNDERLINE -> flags = flags or CellStyle.UNDERLINE
        StyledText.Style.STRIKE -> flags = flags or CellStyle.STRIKE
        StyledText.Style.CODE -> bg = Ink.SURFACE
        StyledText.Style.TAG -> {
            flags = flags or CellStyle.INVERSE or CellStyle.BOLD
            shown = " $text "
        }
    }
    if (link != null) {
        fg = Ink.LINK
        flags = flags or CellStyle.UNDERLINE
    }
    return Seg(shown, CellStyle.of(fg, bg, flags), link)
}

/**
 * A [StyledText] span on top of [base], with the ink [LineModel]'s pieces get: a tag in its color
 * (the accent without one), a color role as its ink, bold text in [strong].
 */
internal fun spanSeg(s: StyledText.Span, base: CellStyle, strong: Ink?, link: Target.Link?): Seg {
    val ink = when {
        StyledText.Style.TAG in s.styles -> Ink.of(s.color ?: StyledText.ColorRole.ACCENT)
        s.color != null -> Ink.of(s.color)
        StyledText.Style.BOLD in s.styles -> strong
        else -> null
    }
    return seg(s.text, s.styles, ink, base, link)
}
