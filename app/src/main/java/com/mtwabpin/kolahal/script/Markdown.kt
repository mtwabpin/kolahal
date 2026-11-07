package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.StyledText.Style

/**
 * Reads the Markdown that `md.log` uses and turns it into [StyledText].
 * - Styles: `**bold**` (or `__bold__`), `*italic*` (or `_italic_`, not inside a word),
 *   `~~strike~~`, `++underline++` and `` `code` ``.
 * - Links: `[text](https://…)`. The log checks the address as it checks every link ([StyledText.link]).
 * - In a body, list items too: `- `, `* `, `+ ` or `1. `. Two leading spaces make a second-level item.
 * - A backslash before a punctuation mark shows the mark as it is. [escape] adds these backslashes.
 */
object Markdown {
    private val DOUBLE = listOf("**" to Style.BOLD, "__" to Style.BOLD, "~~" to Style.STRIKE, "++" to Style.UNDERLINE)
    private val ITEM = Regex("^( *)([-*+]|\\d{1,4}[.)]) +(.*)$")
    private const val PUNCTUATION = "!\"#$%&'()*+,-./:;<=>?@[\\]^_`{|}~"

    /** For a title: styles within each line, but no list items. */
    fun inline(text: String): String = text.split('\n').joinToString("\n") { span(it) }

    /** For a body: styles, and lines that are list items. */
    fun block(text: String): String = text.split('\n').joinToString("\n") { line ->
        val m = ITEM.find(line) ?: return@joinToString span(line)
        val level = if (m.groupValues[1].length >= 2) 2 else 1
        val marker = m.groupValues[2]
        val number = if (marker[0].isDigit()) "${marker.dropLast(1)}. " else ""
        StyledText.item(level) + number + span(m.groupValues[3])
    }

    /** Escapes everything in [text] that Markdown would read as a style or a list item, so it shows as written. */
    fun escape(text: String): String = text.split('\n').joinToString("\n") { line ->
        // `[` too, so text from the web can't make a link ([text](address)).
        val escaped = buildString { line.forEach { c -> if (c in "\\*_~+`[") append('\\'); append(c) } }
        // A line that would read as a list item: "- milk", "1. milk".
        Regex("^( *)(-|\\d{1,4})([.)]?)( +)").find(escaped)?.let { m ->
            val (spaces, mark, dot, gap) = m.destructured
            when {
                mark == "-" && dot.isEmpty() -> "$spaces\\-$gap" + escaped.substring(m.range.last + 1)
                mark != "-" && dot.isNotEmpty() -> "$spaces$mark\\$dot$gap" + escaped.substring(m.range.last + 1)
                else -> null
            }
        } ?: escaped
    }

    private fun span(s: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length && s[i + 1] in PUNCTUATION) {
                out.append(s[i + 1])
                i += 2
                continue
            }
            if (c == '[') {
                val link = link(s, i)
                if (link != null) {
                    val (close, end) = link
                    out.append(StyledText.link(s.substring(close + 2, end), span(s.substring(i + 1, close))))
                    i = end + 1
                    continue
                }
            }
            if (c == '`') {
                val end = s.indexOf('`', i + 1)
                if (end > i + 1) {
                    out.append(StyledText.wrap(Style.CODE, s.substring(i + 1, end)))
                    i = end + 1
                    continue
                }
            }
            val double = DOUBLE.firstOrNull { (d, _) -> s.startsWith(d, i) }
            if (double != null) {
                val (d, style) = double
                val end = closing(s, d, i + 2)
                if (end > i + 2 && !s[i + 2].isWhitespace() && !s[end - 1].isWhitespace()) {
                    out.append(StyledText.wrap(style, span(s.substring(i + 2, end))))
                    i = end + 2
                    continue
                }
            }
            if (c == '*' || c == '_') {
                val end = closing(s, c.toString(), i + 1)
                // `_` works only around words, not inside one, so snake_case stays as it is.
                val opens = i + 1 < s.length && !s[i + 1].isWhitespace() && (c == '*' || i == 0 || !s[i - 1].isLetterOrDigit())
                val closes = end > i + 1 && !s[end - 1].isWhitespace() && (c == '*' || end + 1 >= s.length || !s[end + 1].isLetterOrDigit())
                if (opens && closes) {
                    out.append(StyledText.wrap(Style.ITALIC, span(s.substring(i + 1, end))))
                    i = end + 1
                    continue
                }
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    /**
     * A link `[text](address)` that starts at [open] in [s]: where its `]` is, and where its `)` is.
     * Null if there is none there. The address has no spaces; brackets in it must pair up, as in
     * `https://en.wikipedia.org/wiki/Lua_(programming_language)`.
     */
    private fun link(s: String, open: Int): Pair<Int, Int>? {
        var i = open + 1
        var close = -1
        while (i < s.length && close < 0) {
            when (s[i]) {
                '\\' -> i += 2
                '`' -> s.indexOf('`', i + 1).let { e -> i = if (e < 0) i + 1 else e + 1 }
                '[' -> return null
                ']' -> close = i
                else -> i++
            }
        }
        if (close <= open + 1 || !s.startsWith("(", close + 1)) return null
        var depth = 1
        i = close + 2
        while (i < s.length) {
            when {
                s[i].isWhitespace() -> return null
                s[i] == '(' -> depth++
                s[i] == ')' && --depth == 0 -> return if (i > close + 2) close to i else null
            }
            i++
        }
        return null
    }

    /** The next place after [from] where [d] closes in [s], skipping escaped marks and `code`. Returns -1 if there is none. */
    private fun closing(s: String, d: String, from: Int): Int {
        var i = from
        while (i < s.length) {
            when {
                s[i] == '\\' -> i += 2
                s[i] == '`' -> s.indexOf('`', i + 1).let { e -> i = if (e < 0) i + 1 else e + 1 }
                // Half of a double mark does not close a single * or _ (`*a **b** c*`).
                d.length == 1 && s.startsWith(d + d, i) -> i += 2
                s.startsWith(d, i) -> return i
                else -> i++
            }
        }
        return -1
    }
}
