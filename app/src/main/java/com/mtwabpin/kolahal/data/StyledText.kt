package com.mtwabpin.kolahal.data

/**
 * Styled text in a log line. Invisible markers surround its styled parts. A script's `fmt.*`
 * functions put them there, or `md.log` does from Markdown.
 * - The markers are private-use characters from Unicode's plane 16.
 * - Text from outside (feeds, other apps' notifications, app names) has them removed as it comes
 *   in ([strip]), so it can't turn styling on.
 * - The log draws them. Everywhere else (notifications, status lines, Copy text, search) they are
 *   removed ([plain]).
 */
object StyledText {
    /** [TAG]: a short label on a shaded chip, in its colour (`fmt.tag`). New styles go at the end: stored lines keep their markers. */
    enum class Style { BOLD, ITALIC, UNDERLINE, STRIKE, CODE, TAG }

    /**
     * Colours by role, never by value. The theme picks each one, in its dark and light version,
     * so a script's colours are readable on every theme.
     */
    enum class Colour(val role: String) {
        ACCENT("accent"), LINK("link"), INFO("info"), MUTED("muted"), TEXT("text"), GOOD("good"), WARN("warn"), BAD("bad");

        companion object {
            fun named(role: String): Colour? = entries.firstOrNull { it.role == role.trim().lowercase() }
        }
    }

    /** A piece of a line, with its styles, its colour (null means the line's own colour) and the link it opens ([link]). */
    data class Span(val text: String, val styles: Set<Style>, val colour: Colour? = null, val url: String? = null)

    /**
     * One line. [level] is 1 or 2 for a list item (0 for none). [prefix] is its bullet or number as
     * it shows ("•", "◦", "3."). [spans] are its pieces. A table row has [row], a bar has [meter],
     * and then no spans.
     */
    data class Line(val level: Int, val prefix: String, val spans: List<Span>, val row: Row? = null, val meter: Meter? = null)

    /** One cell of a table row: its pieces, and whether it lines up on the right (numbers). */
    data class Cell(val spans: List<Span>, val right: Boolean)

    /** A table row ([table]). [header] is true for a table's first row when it names the columns. */
    data class Row(val header: Boolean, val cells: List<Cell>)

    /**
     * A bar ([bar], [bars], [ranges]). It fills its track from [from] to [to] (0 to 1), in [colour]
     * (null: the accent colour). Bars on lines that follow each other line up in columns: [label]
     * on the left, then [start], the track, and [end].
     */
    data class Meter(val from: Float, val to: Float, val colour: Colour?, val label: List<Span>, val start: List<Span>, val end: List<Span>)

    private const val BASE = 0x10FB00
    private const val ITEM = BASE + 0x10
    // A table: a marker at the start of a line makes it a row (or the header row), and one before
    // each cell starts it, lined up on the left or the right.
    private const val ROW = BASE + 0x12
    private const val HEAD = BASE + 0x13
    private const val CELL = BASE + 0x14
    private const val CELL_RIGHT = BASE + 0x15
    // A link: LINK, the address, LINK_TEXT, the text it shows, LINK_END.
    private const val LINK = BASE + 0x16
    private const val LINK_TEXT = BASE + 0x17
    private const val LINK_END = BASE + 0x18
    // A bar: a marker at the start of a line, then its numbers, then three parts before each of which
    // a CELL marker goes: the label, the text before the track and the text after it.
    private const val METER = BASE + 0x19
    private const val COLOUR = BASE + 0x20
    private const val COLOUR_END = BASE + 0x2F
    private const val LAST = COLOUR_END

    /** Opens [style]. [close] ends it. */
    fun open(style: Style): String = char(BASE + style.ordinal * 2)
    fun close(style: Style): String = char(BASE + style.ordinal * 2 + 1)

    /** Put at the start of a line, it makes the line a list item of [level] 1 or 2. A numbered item's text starts with "3. ". */
    fun item(level: Int): String = char(ITEM + level.coerceIn(1, 2) - 1)

    fun wrap(style: Style, text: String): String = open(style) + text + close(style)

    fun colour(colour: Colour, text: String): String = char(COLOUR + colour.ordinal) + text + char(COLOUR_END)

    /** A tag (`fmt.tag`): [text] on one line, on a chip shaded in [colour]. */
    fun tag(colour: Colour, text: String): String = colour(colour, wrap(Style.TAG, text.replace('\n', ' ')))

    /**
     * [text] as a link to [url] (`fmt.link`). The log draws it in the link colour, underlined, and a
     * tap on it opens [url]. Everywhere else only [text] shows. The text is one line, and a link
     * inside it is dropped: links don't nest. [url] is checked when the line is logged ([mapLinks]).
     */
    fun link(url: String, text: String): String =
        char(LINK) + without(url, BASE..LAST).filterNot { it.isWhitespace() } + char(LINK_TEXT) +
            mapLinks(text) { null }.replace('\n', ' ') + char(LINK_END)

    /** The addresses of the links in [text], in order. */
    fun links(text: String): List<String> {
        if (!has(text)) return emptyList()
        val out = ArrayList<String>()
        mapLinks(text) { out += it; it }
        return out
    }

    /**
     * The links in [text] as they show: each one's text and address, once for each address, in
     * order. In table cells and the texts of bars too. A line's long-press menu offers them.
     */
    fun linkTexts(text: String): List<Pair<String, String>> {
        if (!has(text)) return emptyList()
        val out = LinkedHashMap<String, StringBuilder>()
        var last: String? = null
        for (line in lines(text)) {
            val spans = line.spans + line.row?.cells.orEmpty().flatMap { it.spans } +
                line.meter?.let { it.label + it.start + it.end }.orEmpty()
            for (s in spans) {
                val url = s.url
                if (url == null) { last = null; continue }
                val shown = out.getOrPut(url) { StringBuilder() }
                // The pieces of one link (its bold part, then the rest) read as one text.
                if (url == last || shown.isEmpty()) shown.append(s.text)
                last = url
            }
            last = null
        }
        return out.map { (url, shown) -> shown.toString().trim() to url }
    }

    /**
     * [text] with each link's address replaced by what [keep] returns for it. When [keep] returns
     * null, the link goes and its text stays, as plain text. A link with no text part loses its
     * marker, so its address shows as plain text and opens nothing.
     */
    fun mapLinks(text: String, keep: (String) -> String?): String {
        if (!has(text)) return text
        val out = StringBuilder(text.length)
        val sep = char(LINK_TEXT)
        // The open link was taken away: its end marker goes too.
        var dropEnd = false
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val size = Character.charCount(cp)
            when (cp) {
                LINK -> {
                    val lineEnd = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    val at = text.indexOf(sep, i + size)
                    if (at in 0 until lineEnd) {
                        val kept = keep(text.substring(i + size, at))
                        if (kept != null) out.append(char(LINK)).append(without(kept, BASE..LAST)).append(sep) else dropEnd = true
                        i = at + sep.length
                        continue
                    }
                }
                LINK_END -> if (dropEnd) dropEnd = false else out.appendCodePoint(cp)
                else -> out.appendCodePoint(cp)
            }
            i += size
        }
        return out.toString()
    }

    /** One line per item, numbered 1., 2., … if [numbered], or else bulleted. */
    fun list(items: List<String>, numbered: Boolean, level: Int = 1): String =
        items.mapIndexed { i, s -> item(level) + (if (numbered) "${i + 1}. " else "") + s }.joinToString("\n")

    /** The most columns and rows a table keeps ([table]). More rows end with a "+N more" line. The same row limit holds for [bars] and [ranges]. */
    const val TABLE_COLUMNS = 8
    const val TABLE_ROWS = 50

    /**
     * A table, one line per row, for a body (`fmt.table`).
     * - [header]: the first row names the columns.
     * - [align]: one letter for each column, `l` or `r` (for numbers). Columns after the last letter are on the left.
     * - A cell is one line: a line break in it becomes a space. Markers of other tables and of bars in a cell are dropped.
     * - Up to [TABLE_COLUMNS] columns and [TABLE_ROWS] rows (besides the header).
     */
    fun table(rows: List<List<String>>, header: Boolean, align: String = ""): String {
        if (rows.isEmpty()) return ""
        val columns = rows.maxOf { it.size }.coerceIn(1, TABLE_COLUMNS)
        val kept = rows.take(TABLE_ROWS + if (header) 1 else 0)
        val lines = kept.mapIndexed { i, row ->
            char(if (header && i == 0) HEAD else ROW) + (0 until columns).joinToString("") { c ->
                val right = align.getOrNull(c)?.lowercaseChar() == 'r'
                char(if (right) CELL_RIGHT else CELL) + part(row.getOrNull(c).orEmpty())
            }
        }
        return withMore(lines, rows.size - kept.size)
    }

    private fun withMore(lines: List<String>, more: Int) = (if (more > 0) lines + "+$more more" else lines).joinToString("\n")

    /** A piece of a row or a bar: one line, without the markers that start rows, cells or bars. */
    private fun part(text: String): String = without(withoutBars(text), ROW..CELL_RIGHT).replace('\n', ' ')

    /** [text] without bars: each bar marker goes with its numbers (up to its first part or the line's end). */
    private fun withoutBars(text: String): String {
        if (!has(text)) return text
        val meter = char(METER)
        val cell = char(CELL)
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val at = text.indexOf(meter, i)
            if (at < 0) {
                out.append(text, i, text.length)
                break
            }
            out.append(text, i, at)
            val ends = listOf(text.indexOf(cell, at), text.indexOf('\n', at)).filter { it >= 0 }
            i = ends.minOrNull() ?: text.length
        }
        return out.toString()
    }

    private fun without(text: String, markers: IntRange): String {
        if (!has(text)) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp !in markers) out.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /**
     * A bar line, filled from [from] to [to] (each 0 to 1), with [label], [start] and [end] around
     * its track. Bars on lines that follow each other share their columns.
     */
    fun meter(from: Double, to: Double, colour: Colour?, label: String = "", start: String = "", end: String = ""): String {
        val a = from.fraction()
        val b = maxOf(a, to.fraction())
        return char(METER) + "${number(a)},${number(b)},${colour?.role.orEmpty()}" +
            listOf(label, start, end).joinToString("") { char(CELL) + part(it) }
    }

    /** `fmt.bar(value, { max, color })`: one bar, [value] out of [max], as wide as the line. */
    fun bar(value: Double, max: Double = 1.0, colour: Colour? = null): String = meter(0.0, share(value, 0.0, max), colour)

    /** A row of [bars]: its [label], its [value], and the text after its bar (by default the value with the unit). */
    data class BarRow(val label: String, val value: Double, val text: String? = null)

    /**
     * `fmt.bars(rows, opts)`: one bar for each row, all on one scale from 0 to [max] (by default the
     * largest value), so the rows compare at a glance. Up to [TABLE_ROWS] rows.
     */
    fun bars(rows: List<BarRow>, max: Double? = null, colour: Colour? = null, unit: String = ""): String {
        if (rows.isEmpty()) return ""
        val top = max ?: rows.maxOf { it.value.takeIf(Double::isFinite) ?: 0.0 }
        val kept = rows.take(TABLE_ROWS)
        return withMore(kept.map { r -> meter(0.0, share(r.value, 0.0, top), colour, r.label, end = r.text ?: (number(r.value) + unit)) }, rows.size - kept.size)
    }

    /** A row of [ranges]: its [label], from [low] to [high], and the texts on each side (by default the numbers with the unit). */
    data class RangeRow(val label: String, val low: Double, val high: Double, val lowText: String? = null, val highText: String? = null)

    /**
     * `fmt.ranges(rows, opts)`: for each row, a bar from its low to its high, all on one scale from
     * [min] to [max] (by default the lowest low and the highest high). For a forecast's days, each
     * day's low to high. Up to [TABLE_ROWS] rows.
     */
    fun ranges(rows: List<RangeRow>, min: Double? = null, max: Double? = null, colour: Colour? = null, unit: String = ""): String {
        if (rows.isEmpty()) return ""
        val finite = { d: Double -> d.takeIf(Double::isFinite) }
        val bottom = min ?: rows.mapNotNull { finite(minOf(it.low, it.high)) }.minOrNull() ?: 0.0
        val top = max ?: rows.mapNotNull { finite(maxOf(it.low, it.high)) }.maxOrNull() ?: 1.0
        val kept = rows.take(TABLE_ROWS)
        return withMore(kept.map { r ->
            val (lo, hi) = if (r.low <= r.high) r.low to r.high else r.high to r.low
            val (loText, hiText) = if (r.low <= r.high) r.lowText to r.highText else r.highText to r.lowText
            meter(share(lo, bottom, top), share(hi, bottom, top), colour, r.label, loText ?: (number(lo) + unit), hiText ?: (number(hi) + unit))
        }, rows.size - kept.size)
    }

    /** Where [value] falls between [low] and [high], from 0 to 1. Nothing to scale (no range, not a number) is 0. */
    private fun share(value: Double, low: Double, high: Double): Double =
        if (!(high > low) || !value.isFinite()) 0.0 else ((value - low) / (high - low)).fraction()

    private fun Double.fraction(): Double = if (isNaN()) 0.0 else coerceIn(0.0, 1.0)

    /** A number as people write it: 42, 3.5, 0.125. At most three decimals. */
    fun number(d: Double): String {
        if (!d.isFinite()) return d.toString()
        if (kotlin.math.abs(d) >= 1e12) return "%.0f".format(java.util.Locale.ROOT, d)
        val rounded = Math.round(d * 1000) / 1000.0
        return if (rounded % 1.0 == 0.0 && kotlin.math.abs(rounded) < 9e15) rounded.toLong().toString() else rounded.toString()
    }

    /** Whether [text] holds a table row or a bar: lines the log draws under the text ([splitAtBlocks]). */
    fun hasBlock(text: String): Boolean = has(text) && text.split('\n').any { isBlock(it) }

    /** Whether [text] holds a table row. */
    fun hasTable(text: String): Boolean = has(text) && text.split('\n').any { isRow(it) }

    private fun isRow(line: String): Boolean = line.length >= 2 && line.codePointAt(0).let { it == ROW || it == HEAD }

    private fun isMeter(line: String): Boolean = line.length >= 2 && line.codePointAt(0) == METER

    private fun isBlock(line: String): Boolean = isRow(line) || isMeter(line)

    /**
     * [body] split before its first table row or bar: the text above it, and the rest (the tables,
     * the bars and anything after them), or null with neither. The log draws the first part as text
     * and the rest below it.
     */
    fun splitAtBlocks(body: String): Pair<String, String?> {
        if (!has(body)) return body to null
        val lines = body.split('\n')
        val first = lines.indexOfFirst { isBlock(it) }
        if (first < 0) return body to null
        return lines.take(first).joinToString("\n") to lines.drop(first).joinToString("\n")
    }

    private fun char(codePoint: Int) = String(Character.toChars(codePoint))

    /** Whether [text] may hold a marker. Every marker is a surrogate pair that starts with this character. */
    fun has(text: String): Boolean = text.indexOf(HIGH) >= 0

    private val HIGH = Character.highSurrogate(BASE)

    /** An SQLite GLOB pattern that any text with a marker in it matches. */
    val GLOB: String = "*[${char(BASE)}-${char(LAST)}]*"

    private val NUMBER = Regex("^(\\d{1,4}[.)]) ")

    /**
     * [text] as lines of styled pieces. A style left open (in a string cut short) ends with the text.
     * A stray marker, or half of one, is dropped.
     */
    fun lines(text: String): List<Line> {
        val out = ArrayList<Line>()
        val on = LinkedHashSet<Style>()
        // Colours nest: the innermost one shows, and closing it brings back the one around it.
        val colours = ArrayDeque<Colour>()
        for (raw in text.split('\n')) {
            if (isMeter(raw)) {
                out += Line(0, "", emptyList(), meter = meterOf(raw))
                continue
            }
            var level = 0
            var spans = ArrayList<Span>()
            val run = StringBuilder()
            // The open link's address, and the address being read (from LINK up to LINK_TEXT).
            var url: String? = null
            var address: StringBuilder? = null
            fun flush() {
                if (run.isNotEmpty()) spans += Span(run.toString(), on.toSet(), colours.lastOrNull(), url)
                run.clear()
            }
            // A table row: its cells so far, and whether the open cell is on the right.
            val header = isRow(raw) && raw.codePointAt(0) == HEAD
            val cells = if (isRow(raw)) ArrayList<Cell>() else null
            var right: Boolean? = null
            fun endCell() {
                flush()
                right?.let { cells?.add(Cell(spans, it)) }
                spans = ArrayList()
                // A style, colour or link doesn't carry from one cell into the next.
                on.clear()
                colours.clear()
                url = null
            }
            var i = 0
            while (i < raw.length) {
                val cp = raw.codePointAt(i)
                val size = Character.charCount(cp)
                val reading = address
                when {
                    reading != null -> if (cp == LINK_TEXT) {
                        url = reading.toString()
                        address = null
                    } else reading.appendCodePoint(cp)
                    cp in BASE..LAST -> {
                        val n = cp - BASE
                        when {
                            n < Style.entries.size * 2 -> {
                                flush()
                                val style = Style.entries[n / 2]
                                if (n % 2 == 0) on += style else on -= style
                            }
                            cp == ITEM || cp == ITEM + 1 -> if (i == 0) level = cp - ITEM + 1
                            cp == CELL || cp == CELL_RIGHT -> if (cells != null) {
                                endCell()
                                right = cp == CELL_RIGHT
                            }
                            cp == LINK -> { flush(); url = null; address = StringBuilder() }
                            cp == LINK_END -> { flush(); url = null }
                            cp == COLOUR_END -> { flush(); colours.removeLastOrNull() }
                            cp >= COLOUR && cp < COLOUR + Colour.entries.size -> { flush(); colours.addLast(Colour.entries[cp - COLOUR]) }
                        }
                    }
                    // Half a marker, from a string cut in the middle of one.
                    size == 1 && Character.isSurrogate(raw[i]) -> Unit
                    else -> run.appendCodePoint(cp)
                }
                i += size
            }
            if (cells != null) {
                endCell()
                out += Line(0, "", emptyList(), Row(header, cells))
                continue
            }
            flush()
            if (level == 0) {
                out += Line(0, "", spans)
                continue
            }
            // A numbered item's "3. " becomes its prefix. A bulleted item gets a bullet.
            val first = spans.firstOrNull()
            val number = first?.let { NUMBER.find(it.text) }
            if (first != null && number != null) {
                val rest = first.text.substring(number.range.last + 1)
                out += Line(level, number.groupValues[1], listOfNotNull(first.copy(text = rest).takeIf { rest.isNotEmpty() }) + spans.drop(1))
            } else {
                out += Line(level, if (level == 1) "•" else "◦", spans)
            }
        }
        return out
    }

    /** A bar line ([meter]) read back. Numbers it can't read make an empty bar. */
    private fun meterOf(raw: String): Meter {
        val parts = raw.substring(Character.charCount(METER)).split(char(CELL))
        val numbers = parts[0].split(',')
        val from = numbers.getOrNull(0)?.toFloatOrNull()?.takeIf { !it.isNaN() }?.coerceIn(0f, 1f) ?: 0f
        val to = numbers.getOrNull(1)?.toFloatOrNull()?.takeIf { !it.isNaN() }?.coerceIn(from, 1f) ?: from
        val colour = numbers.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let(Colour::named)
        fun spans(i: Int) = parts.getOrNull(i)?.takeIf { it.isNotEmpty() }?.let { lines(it).firstOrNull()?.spans } ?: emptyList()
        return Meter(from, to, colour, spans(1), spans(2), spans(3))
    }

    /** [text] with every marker removed and nothing put in its place, and links without their addresses. Used for text from outside. */
    fun strip(text: String): String {
        if (!has(text)) return text
        val out = StringBuilder(text.length)
        var address = false
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            when {
                cp == LINK -> address = true
                cp == LINK_TEXT || cp == '\n'.code -> { address = false; if (cp == '\n'.code) out.append('\n') }
                address || cp in BASE..LAST -> Unit
                else -> out.appendCodePoint(cp)
            }
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /**
     * [text] as plain text, as it reads: no styles or link addresses, list items with their bullet
     * or number, and table cells and the texts of a bar separated by tabs. A bar with no text is left out.
     */
    fun plain(text: String): String {
        if (!has(text)) return text
        fun words(spans: List<Span>) = spans.joinToString("") { it.text }
        return lines(text).mapNotNull { line ->
            // A table row: its cells, separated by tabs, so a copied table pastes into a spreadsheet.
            line.row?.let { row -> return@mapNotNull row.cells.joinToString("\t") { c -> words(c.spans) } }
            line.meter?.let { m -> return@mapNotNull listOf(m.label, m.start, m.end).map(::words).filter { it.isNotEmpty() }.joinToString("\t").ifEmpty { null } }
            val words = words(line.spans)
            when (line.level) {
                0 -> words
                1 -> "${line.prefix} $words"
                else -> "  ${line.prefix} $words"
            }
        }.joinToString("\n")
    }
}
