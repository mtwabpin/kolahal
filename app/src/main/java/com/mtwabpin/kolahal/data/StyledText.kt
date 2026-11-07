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
    enum class Style { BOLD, ITALIC, UNDERLINE, STRIKE, CODE }

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

    /** A piece of a line, with its styles and its colour (null means the line's own colour). */
    data class Span(val text: String, val styles: Set<Style>, val colour: Colour? = null)

    /**
     * One line. [level] is 1 or 2 for a list item (0 for none). [prefix] is its bullet or number as
     * it shows ("•", "◦", "3."). [spans] are its pieces.
     */
    data class Line(val level: Int, val prefix: String, val spans: List<Span>, val row: Row? = null)

    /** One cell of a table row: its pieces, and whether it lines up on the right (numbers). */
    data class Cell(val spans: List<Span>, val right: Boolean)

    /** A table row ([table]). [header] is true for a table's first row when it names the columns. */
    data class Row(val header: Boolean, val cells: List<Cell>)

    private const val BASE = 0x10FB00
    private const val ITEM = BASE + 0x10
    // A table: a marker at the start of a line makes it a row (or the header row), and one before
    // each cell starts it, lined up on the left or the right.
    private const val ROW = BASE + 0x12
    private const val HEAD = BASE + 0x13
    private const val CELL = BASE + 0x14
    private const val CELL_RIGHT = BASE + 0x15
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

    /** One line per item, numbered 1., 2., … if [numbered], or else bulleted. */
    fun list(items: List<String>, numbered: Boolean, level: Int = 1): String =
        items.mapIndexed { i, s -> item(level) + (if (numbered) "${i + 1}. " else "") + s }.joinToString("\n")

    /** The most columns and rows a table keeps ([table]). More rows end with a "+N more" line. */
    const val TABLE_COLUMNS = 8
    const val TABLE_ROWS = 50

    /**
     * A table, one line per row, for a body (`fmt.table`).
     * - [header]: the first row names the columns.
     * - [align]: one letter for each column, `l` or `r` (for numbers). Columns after the last letter are on the left.
     * - A cell is one line: a line break in it becomes a space. Markers of other tables in a cell are dropped.
     * - Up to [TABLE_COLUMNS] columns and [TABLE_ROWS] rows (besides the header).
     */
    fun table(rows: List<List<String>>, header: Boolean, align: String = ""): String {
        if (rows.isEmpty()) return ""
        val columns = rows.maxOf { it.size }.coerceIn(1, TABLE_COLUMNS)
        val kept = rows.take(TABLE_ROWS + if (header) 1 else 0)
        val lines = kept.mapIndexed { i, row ->
            char(if (header && i == 0) HEAD else ROW) + (0 until columns).joinToString("") { c ->
                val right = align.getOrNull(c)?.lowercaseChar() == 'r'
                char(if (right) CELL_RIGHT else CELL) + withoutTableMarkers(row.getOrNull(c).orEmpty()).replace('\n', ' ')
            }
        }
        val more = rows.size - kept.size
        return (if (more > 0) lines + "+$more more" else lines).joinToString("\n")
    }

    private fun withoutTableMarkers(text: String): String {
        if (!has(text)) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp !in ROW..CELL_RIGHT) out.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /** Whether [text] holds a table row. */
    fun hasTable(text: String): Boolean = has(text) && text.split('\n').any { isRow(it) }

    private fun isRow(line: String): Boolean = line.length >= 2 && line.codePointAt(0).let { it == ROW || it == HEAD }

    /**
     * [body] split before its first table row: the text above the table, and the rest (the table and
     * anything after it), or null without a table. The log draws the first part as text and the rest
     * below it.
     */
    fun splitAtTable(body: String): Pair<String, String?> {
        val lines = body.split('\n')
        val first = lines.indexOfFirst { isRow(it) }
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
            var level = 0
            var spans = ArrayList<Span>()
            val run = StringBuilder()
            fun flush() {
                if (run.isNotEmpty()) spans += Span(run.toString(), on.toSet(), colours.lastOrNull())
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
                // A style or colour doesn't carry from one cell into the next.
                on.clear()
                colours.clear()
            }
            var i = 0
            while (i < raw.length) {
                val cp = raw.codePointAt(i)
                val size = Character.charCount(cp)
                when {
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
                out += Line(level, number.groupValues[1], listOfNotNull(Span(rest, first.styles, first.colour).takeIf { rest.isNotEmpty() }) + spans.drop(1))
            } else {
                out += Line(level, if (level == 1) "•" else "◦", spans)
            }
        }
        return out
    }

    /** [text] with every marker removed and nothing put in its place. Used for text from outside. */
    fun strip(text: String): String {
        if (!has(text)) return text
        val out = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            if (cp !in BASE..LAST) out.appendCodePoint(cp)
            i += Character.charCount(cp)
        }
        return out.toString()
    }

    /** [text] as plain text, as it reads: no styles, list items with their bullet or number, and table cells separated by tabs. */
    fun plain(text: String): String {
        if (!has(text)) return text
        return lines(text).joinToString("\n") { line ->
            // A table row: its cells, separated by tabs, so a copied table pastes into a spreadsheet.
            line.row?.let { row -> return@joinToString row.cells.joinToString("\t") { c -> c.spans.joinToString("") { it.text } } }
            val words = line.spans.joinToString("") { it.text }
            when (line.level) {
                0 -> words
                1 -> "${line.prefix} $words"
                else -> "  ${line.prefix} $words"
            }
        }
    }
}
