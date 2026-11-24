/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import androidx.compose.runtime.Immutable
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.Json
import kotlin.math.roundToInt

/** Which lines show their time, from `log.time`. With `groups`, a group's first line shows it, and so does a line where the minute changes. */
enum class TimeMode {
    OFF, COMMANDS, GROUPS, ALL;

    companion object {
        /** The mode `log.time`'s [value] names: off, commands or groups (anything else is commands). */
        fun of(value: String): TimeMode = when (value) {
            "groups" -> GROUPS
            "off" -> OFF
            else -> COMMANDS
        }
    }
}

/**
 * A piece of a row's text with its look. [ink] is null for the paragraph's own ink; bold text
 * already has the ink it shows in. [url] makes the piece a link.
 */
@Immutable
data class Piece(
    val text: String,
    val ink: Ink? = null,
    val styles: Set<StyledText.Style> = emptySet(),
    val url: String? = null,
)

/**
 * One line of a row's words, or all of a text with no styles, line breaks included (the GUI lays
 * such a text out in one piece). [level] is 1 or 2 for a list item, with its [prefix] ("•", "◦",
 * "3."); 0 for none.
 */
@Immutable
data class Para(
    val role: Role,
    val pieces: List<Piece>,
    val level: Int = 0,
    val prefix: String = "",
) {
    /**
     * Where the line is in its row:
     * - [TITLE]: the row's own line (a command, an output's first line, a script line's title);
     * - [DETAIL]: lines under an output or info line, muted;
     * - [BODY]: a script line's body, muted, under its title.
     */
    enum class Role { TITLE, DETAIL, BODY }
}

/**
 * What one log row shows and what can be tapped on it, decided once ([lineModel]) for any
 * renderer. The renderer decides only where things go.
 */
@Immutable
data class LineModel(
    /** The line itself, for what a tap, a swipe or a button does with it. */
    val entry: LogEntry,
    val gutter: Gutter,
    val dimmed: Boolean,
    val unread: Boolean,
    val gapAbove: Gap,
    /** When the line was logged, if it shows its time (`log.time`); null for no time on this row. */
    val time: Long?,
    /** The line aligns columns with spaces, so it is drawn in the terminal font. */
    val mono: Boolean,
    /** Where a script's line came from, after its words: a name, or a notification's icon. */
    val source: Source?,
    /** A person's picture beside the line (a call, a contact). */
    val person: Person?,
    val body: Body,
    /** What follows the words: tables, bars, big numbers, charts, and the text between them. */
    val blocks: List<Block>,
    /** A built-in command's bar, after the words or in the second column. */
    val bar: StyledText.Meter?,
    /** A picture under the words: its address. */
    val image: String?,
    /** A script's buttons under the line (`ctx.actions`), the ones that show. */
    val buttons: List<Button>,
    /** The line does something when tapped, so it gets a taller touch target (`log.taller-taps`). */
    val tappable: Boolean,
    /** The line may fold (`log.fold`) when it is tall: not a command, and only its words. */
    val mayFold: Boolean,
) {
    val id: Long get() = entry.id
    val kind: LogKind get() = entry.kind

    /** The space above the row: none, an item's gap inside a group (`log.item-spacing`), or a group's gap (`log.spacing`). */
    enum class Gap { NONE, ITEM, GROUP }

    /** A script line's source. [Icon] is a notification's saved icon ([key]); [name] is what it stands for. */
    sealed interface Source {
        val name: String

        data class Name(override val name: String) : Source
        data class Icon(val key: String, override val name: String) : Source
    }

    /** A person's picture: their name (or number), their contact photo, and whether it is the big one (a call with details). */
    data class Person(val name: String, val photo: String?, val big: Boolean)

    /** A button under a script's line: its place in `ctx.actions`, its label and arguments, and whether it was the one tapped. */
    data class Button(val index: Int, val label: String, val args: String, val done: Boolean)

    /** An app in an apps grid, as the line saved it. The renderer looks it up among the installed apps. */
    data class AppRef(val pkg: String, val cls: String?, val user: Long?)

    /** How far wrapped lines are indented: not at all, past "• ", or past "$ " in the log's font or in the terminal font. */
    enum class Indent { NONE, BULLET, PROMPT, CODE }

    /** The form of the row. */
    sealed interface Body {
        /**
         * Words: the title, then details or a body. [ink] and [heading] are the title's own ink
         * and weight. [bullet] starts an unread script line. [blank]: the words show nothing above
         * the blocks (a command's line can be its big numbers alone).
         */
        data class Words(
            val paras: List<Para>,
            val ink: Ink,
            val heading: Boolean,
            val indent: Indent,
            val bullet: Boolean,
            val blank: Boolean,
        ) : Body

        /** Two columns (config, usage, scripts). The texts keep their [StyledText] marks: [name] is styled, [desc] shows as it is. */
        data class TwoColumns(val name: String, val desc: String) : Body

        /**
         * `help`'s rows: a name in a column as wide as [nameWidthOf] (the list's longest name), its
         * description beside it and [args] muted under that. [desc] keeps its [StyledText] marks.
         */
        data class NameColumns(
            val name: String,
            val desc: String,
            val args: String?,
            val nameWidthOf: String,
        ) : Body

        /** `apps` output: a chip for each app. */
        data class AppGrid(val apps: List<AppRef>) : Body
    }

    /** What follows a row's words, in order. */
    sealed interface Block {
        data class Text(val paras: List<Para>) : Block
        data class Table(val rows: List<StyledText.Row>) : Block

        /** Bars on lines that follow each other, lined up as one. */
        data class Meters(val rows: List<StyledText.Meter>) : Block
        data class Stats(val items: List<StyledText.Stat>) : Block
        data class Chart(val columns: StyledText.Columns) : Block
    }
}

/** Everything a row can do, in reading order. Renderers build pointer handling and semantics from it. */
sealed interface Target {
    /** A tap or long press on the line. */
    data object Line : Target
    data class Link(val url: String, val text: String) : Target

    /** A script's button that can still be tapped. */
    data class Button(val index: Int, val label: String) : Target

    /** The chip of the [index]th app in an apps grid. */
    data class App(val index: Int) : Target
    data object Fold : Target
}

/** The settings [lineModel] reads: `log.time` and `log.fold`. */
@Immutable
data class LineOptions(val timeMode: TimeMode, val foldLines: Int)

/**
 * What [row] shows and what can be tapped on it. [hasIcon] says whether a notification's icon was
 * saved under a key; without it, the line ends with its app's name.
 */
fun lineModel(row: LogRow.Entry, o: LineOptions, hasIcon: (String) -> Boolean = { false }): LineModel {
    val e = row.entry
    val ctx = e.ctx.values
    val desc = ctx[LogContext.DESC]
    val apps = ctx[LogContext.APPS]
    val body = when {
        apps != null -> LineModel.Body.AppGrid(appRefs(apps))
        desc != null && ctx[LogContext.NAME_COL] != null -> LineModel.Body.NameColumns(
            e.text,
            desc,
            ctx[LogContext.ARGS],
            ctx.getValue(LogContext.NAME_COL)
        )

        desc != null -> LineModel.Body.TwoColumns(e.text, desc)
        else -> wordsOf(e, row.unread)
    }
    val words = body is LineModel.Body.Words
    // A script's tables and bars, and what follows them. A built-in command's big numbers, tables
    // and bars are in its text, after any words.
    val rest = if (!words) null else when (e.kind) {
        LogKind.SCRIPT -> e.ctx.body?.let { StyledText.splitAtBlocks(it).second }
        LogKind.OUTPUT, LogKind.INFO -> StyledText.splitAtBlocks(e.text).second
        else -> null
    }
    // On a notification line, the saved status-bar icon replaces the app name at the end.
    val source = e.source?.takeIf { words && row.showSource && e.kind == LogKind.SCRIPT }
        ?.let { name ->
            ctx[LogContext.ICON]?.takeIf(hasIcon)?.let { LineModel.Source.Icon(it, name) }
                ?: LineModel.Source.Name(name)
        }
    return LineModel(
        entry = e,
        gutter = row.gutter,
        dimmed = row.dimmed,
        unread = row.unread,
        gapAbove = when {
            row.blockStart -> LineModel.Gap.GROUP
            row.itemGap -> LineModel.Gap.ITEM
            else -> LineModel.Gap.NONE
        },
        time = when (o.timeMode) {
            TimeMode.ALL -> e.ts
            TimeMode.COMMANDS -> e.ts.takeIf { e.kind == LogKind.COMMAND }
            TimeMode.GROUPS -> e.ts.takeIf { row.showTime }
            TimeMode.OFF -> null
        },
        mono = ctx.containsKey(LogContext.MONO),
        source = source,
        person = ctx[LogContext.PERSON]?.let {
            LineModel.Person(it, ctx[LogContext.PHOTO], big = '\n' in e.text)
        },
        body = body,
        blocks = rest?.let(::blocksOf).orEmpty(),
        bar = if (e.kind == LogKind.OUTPUT || e.kind == LogKind.INFO) lineBar(ctx[LogContext.BAR]) else null,
        image = if (words) e.ctx.image else null,
        buttons = if (words && e.kind == LogKind.SCRIPT) buttonsOf(e.ctx) else emptyList(),
        tappable = e.kind == LogKind.COMMAND || ctx.keys.any { it in ACTIONABLE_KEYS },
        mayFold = words && e.kind != LogKind.COMMAND && o.foldLines > 0,
    )
}

/** The context keys that make a line do something when tapped. */
private val ACTIONABLE_KEYS = setOf(
    LogContext.URL,
    LogContext.PKG,
    LogContext.COMMAND,
    LogContext.RUN,
    LogContext.SCRIPT,
    LogContext.APPS,
    LogContext.LINE,
    LogContext.PHONE,
    LogContext.SHORTCUT,
    "notif",
    LogContext.NOTE,
    LogContext.EVENT,
    LogContext.REMINDER,
)

/**
 * A line's words. Wrapped lines are indented past the prefix (`$ `, `• `), so each item reads as
 * one block:
 * - a command: its prompt, then the line in bold;
 * - an error: plain, its first line in the error color, and the lines after it as muted details: what to do about it;
 * - an info or output line: its first line, with a profile tag at the end in its profile's color,
 *   and the lines after it as muted details;
 * - a script line: its title (bold when it has a body), then the body, muted.
 * A built-in command's tables and bars, and a script body's, are not words: they are [LineModel.blocks].
 */
private fun wordsOf(e: LogEntry, unread: Boolean): LineModel.Body.Words {
    val text =
        if (e.kind == LogKind.OUTPUT || e.kind == LogKind.INFO) StyledText.splitAtBlocks(e.text).first else e.text
    val paras = ArrayList<Para>()
    when (e.kind) {
        LogKind.COMMAND -> paras += Para(
            Para.Role.TITLE,
            listOf(Piece("$ "), Piece(e.text, Ink.FG, setOf(StyledText.Style.BOLD)))
        )

        LogKind.ERROR -> {
            // Plain: an error's styles are taken off. Its first line says what went wrong; the lines after it, what to do.
            val plain = StyledText.plain(e.text)
            paras += Para(Para.Role.TITLE, listOf(Piece(plain.substringBefore('\n'))))
            plain.substringAfter('\n', "").split('\n').filter { it.isNotEmpty() }.forEach {
                paras += Para(Para.Role.DETAIL, listOf(Piece(it)))
            }
        }
        LogKind.INFO, LogKind.OUTPUT -> {
            val first = text.substringBefore('\n')
            paras += if (StyledText.has(first)) styledParas(first, Para.Role.TITLE, null)
            else listOf(Para(Para.Role.TITLE, tagged(first)))
        }

        LogKind.SCRIPT -> paras += styledParas(e.text, Para.Role.TITLE, Ink.FG)
    }
    // Lines after the first are details, such as a package name under an app event.
    if ((e.kind == LogKind.INFO || e.kind == LogKind.OUTPUT) && '\n' in text) {
        paras += styledParas(text.substringAfter('\n'), Para.Role.DETAIL, Ink.FG)
    }
    val body = e.ctx.body?.let { StyledText.splitAtBlocks(it).first }
    if (e.kind == LogKind.SCRIPT && !body.isNullOrEmpty()) paras += styledParas(
        body,
        Para.Role.BODY,
        Ink.FG
    )
    val script = e.kind == LogKind.SCRIPT
    return LineModel.Body.Words(
        paras = paras,
        ink = when (e.kind) {
            LogKind.COMMAND -> Ink.PROMPT
            LogKind.ERROR -> Ink.ERROR
            LogKind.INFO -> Ink.INFO
            LogKind.SCRIPT -> if (e.ctx.url != null) Ink.LINK else Ink.SCRIPT
            LogKind.OUTPUT -> if (e.ctx.url != null) Ink.LINK else Ink.FG
        },
        // A title with a body is a heading: bold, with the body muted below it. A title on its own
        // (one HN story, one price) is in normal weight, so a list of them isn't a wall of bold.
        heading = script && e.ctx.values.containsKey(LogContext.TITLE) && e.ctx.values.containsKey(
            LogContext.BODY
        ),
        indent = when {
            script -> if (unread) LineModel.Indent.BULLET else LineModel.Indent.NONE
            e.ctx.values.containsKey(LogContext.MONO) -> LineModel.Indent.CODE
            else -> LineModel.Indent.PROMPT
        },
        bullet = script && unread,
        // A script line without a title shows only its blocks.
        blank = if (script) e.text.isEmpty() else paras.groupBy { it.role }.values.all { shown(it) == 0 },
    )
}

/** How many characters [paras] of one role show, with the line breaks and list prefixes between them. */
private fun shown(paras: List<Para>): Int = paras.withIndex().sumOf { (i, p) ->
    (if (i > 0) 1 else 0) + (if (p.level > 0) 2 * (p.level - 1) + p.prefix.length + 1 else 0) +
            p.pieces.sumOf { it.text.length }
}

/**
 * [text] as paragraphs of [role]: one per line when it has [StyledText] marks, else all of it in
 * one. Bold text shows in [strong], if given, so bold in a muted body or a bold title stands out.
 */
private fun styledParas(text: String, role: Para.Role, strong: Ink?): List<Para> =
    if (!StyledText.has(text)) listOf(Para(role, listOf(Piece(text))))
    else StyledText.lines(text).map { Para(role, piecesOf(it.spans, strong), it.level, it.prefix) }

/** [spans] as pieces. A tag has its color, the accent color without one. */
private fun piecesOf(spans: List<StyledText.Span>, strong: Ink?): List<Piece> =
    spans.map { s ->
        val ink = when {
            StyledText.Style.TAG in s.styles -> Ink.of(s.color ?: StyledText.ColorRole.ACCENT)
            s.color != null -> Ink.of(s.color)
            StyledText.Style.BOLD in s.styles -> strong
            else -> null
        }
        Piece(s.text, ink, s.styles, s.url)
    }

/** A profile tag at the end of a line's first row, as in `Launched Maps [W]` or `Updated Maps [W] ↑`. */
private val profileTag = Regex(" \\[([PWSC])](?= \\S*$|$)")

/** [text] as pieces. A profile tag at the end is bold, in its profile's color. */
private fun tagged(text: String): List<Piece> {
    val tag = profileTag.find(text) ?: return listOf(Piece(text))
    val kind = ProfileKind.entries.first { it.tag == tag.groupValues[1] }
    return listOf(
        Piece(text.substring(0, tag.range.first)),
        Piece(tag.value, Ink.of(kind), setOf(StyledText.Style.BOLD)),
        Piece(text.substring(tag.range.last + 1)),
    )
}

/** [rest] as tables, runs of bars, big numbers, charts and runs of text, in order. */
private fun blocksOf(rest: String): List<LineModel.Block> {
    val out = ArrayList<LineModel.Block>()
    val text = ArrayList<String>()
    val rows = ArrayList<StyledText.Row>()
    val meters = ArrayList<StyledText.Meter>()
    fun endText() {
        if (text.isNotEmpty()) out += LineModel.Block.Text(
            styledParas(text.joinToString("\n"), Para.Role.BODY, Ink.FG)
        )
        text.clear()
    }

    fun endTable() {
        if (rows.isNotEmpty()) out += LineModel.Block.Table(rows.toList()); rows.clear()
    }

    fun endBars() {
        if (meters.isNotEmpty()) out += LineModel.Block.Meters(meters.toList()); meters.clear()
    }
    for (raw in rest.split('\n')) {
        val line = if (StyledText.hasBlock(raw)) StyledText.lines(raw).single() else null
        when {
            line?.row != null -> {
                endText(); endBars(); rows += line.row
            }

            line?.meter != null -> {
                endText(); endTable(); meters += line.meter
            }

            line?.stats != null -> {
                endText(); endTable(); endBars(); out += LineModel.Block.Stats(line.stats)
            }

            line?.columns != null -> {
                endText(); endTable(); endBars(); out += LineModel.Block.Chart(line.columns)
            }

            else -> {
                endTable(); endBars(); text += raw
            }
        }
    }
    endText()
    endTable()
    endBars()
    return out
}

/**
 * A line's own bar ([LogContext.BAR]): built-in commands use it, as `from,to,role`, to draw a bar
 * after a line's text (`unlocks`, `usage`, `volume`).
 */
internal fun lineBar(value: String?): StyledText.Meter? {
    val parts = value?.split(',') ?: return null
    val from = parts.getOrNull(0)?.toFloatOrNull() ?: return null
    val to = parts.getOrNull(1)?.toFloatOrNull() ?: return null
    if (from.isNaN() || to.isNaN()) return null
    val a = from.coerceIn(0f, 1f)
    return StyledText.Meter(
        a,
        to.coerceIn(a, 1f),
        parts.getOrNull(2)?.let(StyledText.ColorRole::named),
        emptyList(),
        emptyList(),
        emptyList()
    )
}

/** The buttons that show: all of them, or once one was tapped ([LogContext.ACTION_DONE]), only it. */
private fun buttonsOf(ctx: LogContext): List<LineModel.Button> {
    val done = ctx.values[LogContext.ACTION_DONE]?.toIntOrNull()
    return LogContext.actionsOf(ctx).mapIndexedNotNull { i, (label, args) ->
        if (done != null && i != done) null else LineModel.Button(i, label, args, done != null)
    }
}

/** The apps an apps grid saved ([LogContext.APPS]): a JSON list of {pkg, cls, user}. */
private fun appRefs(raw: String): List<LineModel.AppRef> =
    runCatching { Json.parse(raw) as? List<*> }.getOrNull().orEmpty()
        .filterIsInstance<Map<*, *>>().mapNotNull { m ->
            val pkg = m["pkg"]?.toString() ?: return@mapNotNull null
            LineModel.AppRef(pkg, m["cls"]?.toString(), m["user"]?.toString()?.toLongOrNull())
        }

/**
 * Everything [this] row can do, in reading order: the line, its links, its apps, the links in its
 * blocks, its buttons. The fold's link ([Target.Fold]) is not listed: only the renderer knows
 * whether the row is tall enough to fold ([LineModel.mayFold]).
 */
fun LineModel.targets(): List<Target> = buildList {
    add(Target.Line)
    when (val b = body) {
        is LineModel.Body.Words -> addAll(linksOf(b.paras.flatMap { p -> p.pieces.map { it.text to it.url } }))
        is LineModel.Body.TwoColumns -> addAll(linksIn(b.name))
        is LineModel.Body.NameColumns -> addAll(linksIn(b.desc))
        is LineModel.Body.AppGrid -> b.apps.indices.forEach { add(Target.App(it)) }
    }
    for (block in blocks) addAll(linksOf(spansOf(block).map { it.text to it.url }))
    for (b in buttons) if (!b.done) add(Target.Button(b.index, b.label))
}

/** The links among [pieces] (text and address): pieces of one link that follow each other are one. */
private fun linksOf(pieces: List<Pair<String, String?>>): List<Target.Link> {
    val out = ArrayList<Target.Link>()
    for (link in linksPerPiece(pieces)) if (link != null && link !== out.lastOrNull()) out += link
    return out
}

/**
 * The link each of [pieces] (text and address) belongs to, or null: pieces of one link that follow
 * each other share one [Target.Link], with all their text. [targets] lists the same links, so a
 * renderer that marks where each piece goes can't tap a link the row doesn't have.
 */
internal fun linksPerPiece(pieces: List<Pair<String, String?>>): List<Target.Link?> {
    val out = arrayOfNulls<Target.Link>(pieces.size)
    var i = 0
    while (i < pieces.size) {
        val url = pieces[i].second
        if (url == null) {
            i++
            continue
        }
        var j = i
        val text = StringBuilder()
        while (j < pieces.size && pieces[j].second == url) text.append(pieces[j++].first)
        val link = Target.Link(url, text.toString())
        for (k in i until j) out[k] = link
        i = j
    }
    return out.asList()
}

/** The links in [text], a column's text with its [StyledText] marks. */
private fun linksIn(text: String): List<Target.Link> =
    if (!StyledText.has(text)) emptyList()
    else StyledText.lines(text).flatMap { line -> linksOf(line.spans.map { it.text to it.url }) }

/**
 * The pieces of [block] that can hold a link, in reading order: text, table cells, the texts of
 * bars and big numbers.
 */
internal fun spansOf(block: LineModel.Block): List<StyledText.Span> = when (block) {
    is LineModel.Block.Text -> block.paras.flatMap { p ->
        p.pieces.map { StyledText.Span(it.text, it.styles, null, it.url) }
    }

    is LineModel.Block.Table -> block.rows.flatMap { r -> r.cells.flatMap { it.spans } }
    is LineModel.Block.Meters -> block.rows.flatMap { it.label + it.start + it.end }
    is LineModel.Block.Stats -> block.items.flatMap { it.value + it.label }
    is LineModel.Block.Chart -> emptyList()
}

/**
 * What a screen reader says for [this] row: "new" for an unread line, its words, its source,
 * tables and bars as words, its buttons, and [timeText] (the time as the row shows it).
 */
fun LineModel.spoken(timeText: String?): String {
    val parts = ArrayList<String>()
    if (unread) parts += "new"
    when (val b = body) {
        is LineModel.Body.Words -> parts += wordsText(b.paras)
        is LineModel.Body.TwoColumns -> {
            parts += StyledText.plain(b.name); parts += StyledText.plain(b.desc)
        }

        is LineModel.Body.NameColumns -> {
            parts += StyledText.plain(b.name); parts += StyledText.plain(b.desc)
            b.args?.let { parts += it }
        }

        is LineModel.Body.AppGrid -> parts += StyledText.plain(entry.text)
    }
    source?.let { parts += it.name }
    bar?.let { parts += spokenMeter(it) }
    for (block in blocks) parts += when (block) {
        is LineModel.Block.Text -> wordsText(block.paras)
        is LineModel.Block.Table -> spokenTable(block.rows)
        is LineModel.Block.Meters -> spokenMeters(block.rows)
        is LineModel.Block.Stats -> spokenStats(block.items)
        is LineModel.Block.Chart -> words(block.columns.said)
    }
    for (b in buttons) parts += b.label
    timeText?.let { parts += it }
    return parts.filter { it.isNotBlank() }.joinToString(", ")
}

/** [paras] as plain text: each with its list bullet or number, one per line. */
private fun wordsText(paras: List<Para>): String =
    paras.joinToString("\n") { p -> itemStart(p) + p.pieces.joinToString("") { it.text } }

/** What a list item starts with, as plain text: its bullet or number, further in at the second level. */
private fun itemStart(p: Para): String = when (p.level) {
    0 -> ""
    1 -> "${p.prefix} "
    else -> "  ${p.prefix} "
}

private fun words(spans: List<StyledText.Span>) = spans.joinToString("") { it.text }

/**
 * [blocks] as plain text, as [StyledText.plain] gives the text they come from: table cells
 * separated by tabs, a bar's texts, big numbers with their labels, a chart's words. A bar or a
 * line of numbers with no text is left out.
 */
fun plainOf(blocks: List<LineModel.Block>): String = blocks.flatMap { b ->
    when (b) {
        is LineModel.Block.Text -> b.paras.map { p -> itemStart(p) + p.pieces.joinToString("") { it.text } }
        is LineModel.Block.Table -> b.rows.map { r -> r.cells.joinToString("\t") { words(it.spans) } }
        is LineModel.Block.Meters -> b.rows.mapNotNull { m ->
            listOf(m.label, m.start, m.end).map(::words).filter { it.isNotEmpty() }
                .joinToString("\t").ifEmpty { null }
        }

        is LineModel.Block.Stats -> listOfNotNull(b.items.joinToString(" · ") {
            listOf(words(it.value), words(it.label)).filter(String::isNotEmpty).joinToString(" ")
        }.ifEmpty { null })

        is LineModel.Block.Chart -> listOfNotNull(words(b.columns.said).ifEmpty { null })
    }
}.joinToString("\n")

/** A table as a screen reader hears it: "Coin BTC, Price 64,210", each cell after its column's name, a row at a time. */
fun spokenTable(rows: List<StyledText.Row>): String {
    if (rows.isEmpty()) return ""
    val hasHeader = rows.first().header
    val names = if (hasHeader) rows.first().cells.map { c -> words(c.spans) } else emptyList()
    return rows.drop(if (hasHeader) 1 else 0).joinToString(". ") { row ->
        row.cells.mapIndexed { i, c ->
            listOfNotNull(names.getOrNull(i)?.takeIf { it.isNotBlank() }, words(c.spans)).joinToString(" ")
        }.joinToString(", ")
    }
}

/** Bars that follow each other, as a screen reader hears them. */
fun spokenMeters(meters: List<StyledText.Meter>): String =
    meters.joinToString(". ", transform = ::spokenMeter)

/** What a screen reader says for a bar: its texts, or how full it is. */
fun spokenMeter(m: StyledText.Meter): String {
    val parts = listOf(words(m.label), words(m.start), words(m.end)).filter { it.isNotBlank() }
    if (parts.isNotEmpty()) return parts.joinToString(" ")
    return "${((m.to - m.from) * 100).roundToInt()} percent"
}

/** A line of big numbers as a screen reader hears it: each value with its label. */
fun spokenStats(stats: List<StyledText.Stat>): String =
    stats.joinToString(", ") { s -> listOf(s.value, s.label).joinToString(" ") { words(it) }.trim() }

/**
 * Where each of [words] appears in [text], ignoring case, as ranges of characters: the first word's
 * places in order, then the next word's. Empty words are skipped: an empty word is found at the
 * same place forever.
 */
fun markRanges(text: String, words: List<String>): List<IntRange> {
    if (words.isEmpty()) return emptyList()
    val out = ArrayList<IntRange>()
    for (w in words) {
        if (w.isEmpty()) continue
        var at = text.indexOf(w, ignoreCase = true)
        while (at >= 0) {
            out += at until at + w.length
            at = text.indexOf(w, at + w.length, ignoreCase = true)
        }
    }
    return out
}
