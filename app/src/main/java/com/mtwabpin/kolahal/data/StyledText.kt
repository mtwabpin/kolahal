package com.mtwabpin.kolahal.data

/**
 * Styled text in a log line: invisible markers around its styled parts, put there by a script's
 * `fmt.*` functions, or by `md.log` from Markdown. The markers are private-use characters from
 * Unicode's plane 16, which text from the web doesn't use, so a feed title can't turn styling on.
 * The log draws them; everywhere else (notifications, status lines, Copy text, search) they are
 * stripped ([plain]).
 */
object StyledText {
    enum class Style { BOLD, ITALIC, UNDERLINE, STRIKE, CODE }

    /**
     * Colours by role, never by value: the theme picks each one, in its dark and its light version,
     * so a script's colours read on every theme.
     */
    enum class Colour(val role: String) {
        ACCENT("accent"), LINK("link"), INFO("info"), MUTED("muted"), TEXT("text"), GOOD("good"), WARN("warn"), BAD("bad");

        companion object {
            fun named(role: String): Colour? = entries.firstOrNull { it.role == role.trim().lowercase() }
        }
    }

    /** A piece of a line, the styles on it and its colour (null: the line's own). */
    data class Span(val text: String, val styles: Set<Style>, val colour: Colour? = null)

    /**
     * One line: [level] 1 or 2 for a list item (0 for none), [prefix] its bullet or number as it
     * shows ("•", "◦", "3."), and its pieces.
     */
    data class Line(val level: Int, val prefix: String, val spans: List<Span>)

    private const val BASE = 0x10FB00
    private const val ITEM = BASE + 0x10
    private const val COLOUR = BASE + 0x20
    private const val COLOUR_END = BASE + 0x2F
    private const val LAST = COLOUR_END

    /** Opens [style]; [close] ends it. */
    fun open(style: Style): String = char(BASE + style.ordinal * 2)
    fun close(style: Style): String = char(BASE + style.ordinal * 2 + 1)

    /** At the start of a line: the line is a list item, [level] 1 or 2. A numbered one's text starts with "3. ". */
    fun item(level: Int): String = char(ITEM + level.coerceIn(1, 2) - 1)

    fun wrap(style: Style, text: String): String = open(style) + text + close(style)

    fun colour(colour: Colour, text: String): String = char(COLOUR + colour.ordinal) + text + char(COLOUR_END)

    /** One line per item, [numbered] 1., 2., … or bulleted. */
    fun list(items: List<String>, numbered: Boolean, level: Int = 1): String =
        items.mapIndexed { i, s -> item(level) + (if (numbered) "${i + 1}. " else "") + s }.joinToString("\n")

    private fun char(codePoint: Int) = String(Character.toChars(codePoint))

    /** Whether [text] may hold a marker: every one is a surrogate pair starting with this. */
    fun has(text: String): Boolean = text.indexOf(HIGH) >= 0

    private val HIGH = Character.highSurrogate(BASE)

    /** An SQLite GLOB pattern that any text with a marker in it matches. */
    val GLOB: String = "*[${char(BASE)}-${char(LAST)}]*"

    private val NUMBER = Regex("^(\\d{1,4}[.)]) ")

    /**
     * [text] as lines of styled pieces. A style left open (a string cut short) ends with the text;
     * a stray marker, or half of one, is dropped.
     */
    fun lines(text: String): List<Line> {
        val out = ArrayList<Line>()
        val on = LinkedHashSet<Style>()
        // Colours nest: the innermost shows, and closing it brings back the one around it.
        val colours = ArrayDeque<Colour>()
        for (raw in text.split('\n')) {
            var level = 0
            val spans = ArrayList<Span>()
            val run = StringBuilder()
            fun flush() {
                if (run.isNotEmpty()) spans += Span(run.toString(), on.toSet(), colours.lastOrNull())
                run.clear()
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
            flush()
            if (level == 0) {
                out += Line(0, "", spans)
                continue
            }
            // A numbered item's "3. " is its prefix; a bulleted one gets a bullet.
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

    /** [text] as plain text, as it reads: no styles, list items with their bullet or number. */
    fun plain(text: String): String {
        if (!has(text)) return text
        return lines(text).joinToString("\n") { line ->
            val words = line.spans.joinToString("") { it.text }
            when (line.level) {
                0 -> words
                1 -> "${line.prefix} $words"
                else -> "  ${line.prefix} $words"
            }
        }
    }
}
