package com.mtwabpin.kolahal.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ApiDocs

/**
 * Find matches to draw: the start offsets in a window around the current match. Two instances are
 * equal when they mark the same text.
 */
internal class MatchHighlights(val starts: IntArray, val length: Int, val current: Int) {
    override fun equals(other: Any?) =
        other is MatchHighlights && other.length == length && other.current == current && other.starts.contentEquals(starts)

    override fun hashCode() = 31 * (31 * starts.contentHashCode() + length) + current
}

/**
 * A cache of tokens per line, keyed by the line's text. When the user types, only the edited line
 * is matched again, not the whole script.
 * - Each token is (start, end, kind), with offsets inside its line.
 * - The editor keeps one per language, so a new [SyntaxHighlighter] (for a find match or a
 *   flashed line) starts with the tokens already found.
 */
internal class LineTokens(lang: ScriptLang) {
    val keywords = ApiDocs.keywords(lang).toSet()
    // Every token here is on one line. JS template strings (`…`) can span lines, so they are found separately.
    private val token = Regex(
        (if (lang == ScriptLang.LUA) "--[^\n]*" else "//[^\n]*") +
            "|\"(?:\\\\.|[^\"\\\\\n])*\"|'(?:\\\\.|[^'\\\\\n])*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z0-9_]*\\b"
    )

    /**
     * The cache limit is the number of characters, not the number of lines. Why:
     * - Typing on a line stores a new copy of it at every key. The oldest copies are removed first,
     *   and the script's own lines are used at every key, so they stay.
     * - With a line count limit, a script with more lines than the limit had every line tokenised
     *   again at each key.
     */
    private val lineTokens = Kept(KEPT_CHARS)

    /**
     * Very long lines (minified code, data) have their own cache. Otherwise, copies of a long line
     * being edited would push the rest of the script out of the cache.
     */
    private val longLines = Kept(KEPT_LONG_CHARS)

    /** Total characters of the cached lines. Used by tests. */
    internal val keptChars: Long get() = lineTokens.chars + longLines.chars

    /** Returns the tokens of [line], from the cache or tokenised now. */
    fun of(line: String): IntArray = (if (line.length > LONG_LINE) longLines else lineTokens).getOrPut(line) { tokenize(line) }

    /** Tokens by line, least recently used first. The cached lines hold at most [maxChars] characters in total. */
    private class Kept(private val maxChars: Long) {
        private val map = LinkedHashMap<String, IntArray>(512, 0.75f, true)
        var chars = 0L
            private set

        fun getOrPut(line: String, make: () -> IntArray): IntArray {
            map[line]?.let { return it }
            val tokens = make()
            map[line] = tokens
            chars += line.length
            val oldest = map.entries.iterator()
            while (chars > maxChars && oldest.hasNext()) {
                val e = oldest.next()
                if (e.key === line) continue
                chars -= e.key.length
                oldest.remove()
            }
            return tokens
        }
    }

    private fun tokenize(line: String): IntArray {
        val out = ArrayList<Int>()
        for (m in token.findAll(line)) {
            val t = m.value
            val kind = when {
                t.startsWith("--") || t.startsWith("//") -> COMMENT
                t.startsWith("\"") || t.startsWith("'") -> STRING
                t[0].isDigit() -> NUMBER
                t in keywords -> KEYWORD
                else -> continue
            }
            out += m.range.first; out += m.range.last + 1; out += kind
        }
        return out.toIntArray()
    }

    companion object {
        /** Lines longer than this go to the separate cache ([longLines]). */
        const val LONG_LINE = 2_000
        /** Cache size in characters: a script of about 2 MB, or 4 MB of memory. */
        const val KEPT_CHARS = 2_000_000L
        /** Cache size in characters for the separate cache of [LONG_LINE]s. */
        const val KEPT_LONG_CHARS = 1_000_000L
        const val COMMENT = 0
        const val STRING = 1
        const val NUMBER = 2
        const val KEYWORD = 3
    }
}

/**
 * Highlights code in the editor:
 * - colours comments, strings, numbers and keywords;
 * - flashes one line (after a jump to an error);
 * - marks find matches.
 *
 * If [window] is set, only those lines (counted from 0) are coloured. It is used for the lines
 * around the visible part of a big script, because the colours cost several times the text itself
 * to lay out at every key. The flash and the matches show wherever they are.
 */
internal class SyntaxHighlighter(
    lang: ScriptLang,
    private val flashLine: Int?,
    private val matches: MatchHighlights?,
    private val tokens: LineTokens = LineTokens(lang),
    private val window: IntRange? = null,
) : VisualTransformation {
    private val keywords = tokens.keywords
    // The theme this was made for. A text field colours again only when the new highlighter is not equal to the last one.
    private val palette = Term.palette
    private val js = lang == ScriptLang.JS

    private fun tokensOf(line: String): IntArray = tokens.of(line)

    // The text field asks again for the same text when only the cursor or selection moves, twice
    // per change. Return the last result in that case.
    private var lastText: AnnotatedString? = null
    private var lastPalette: Palette? = null
    private var last: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        last?.let { if (text == lastText && Term.palette == lastPalette) return it }
        return colour(text).also {
            lastText = text
            lastPalette = Term.palette
            last = it
        }
    }

    private fun colour(text: AnnotatedString): TransformedText {
        val src = text.text
        // A big script is coloured without italic comments and bold keywords. Their layout cost grows
        // with their number. On the emulator, per key: 8 KB took 48 ms with them and 13 ms without;
        // 20 KB took 162 ms and 32 ms.
        val plain = src.length > PLAIN_FROM
        val styles = arrayOf(
            SpanStyle(color = Term.dim, fontStyle = if (plain) null else FontStyle.Italic),
            SpanStyle(color = Term.script),
            SpanStyle(color = Term.palette.work),
            SpanStyle(color = Term.prompt, fontWeight = if (plain) null else FontWeight.Bold),
        )
        val styled = buildAnnotatedString {
            append(src)
            var lineStart = 0
            var line = 0
            // Start and end of the window's lines in the text. Used for the template strings.
            var from = if (window == null) 0 else -1
            var to = src.length
            while (lineStart <= src.length) {
                val lineEnd = src.indexOf('\n', lineStart).let { if (it < 0) src.length else it }
                if (window == null || line in window) {
                    if (from < 0) from = lineStart
                    val t = tokensOf(src.substring(lineStart, lineEnd))
                    for (i in t.indices step 3) addStyle(styles[t[i + 2]], lineStart + t[i], lineStart + t[i + 1])
                    to = lineEnd
                } else if (line > window.last) {
                    break
                }
                lineStart = lineEnd + 1
                line++
            }
            if (js && from >= 0) {
                // Template strings: pair up backticks with a simple scan. Escapes inside are ignored, as before.
                var open = src.indexOf('`')
                while (open in 0..to) {
                    val close = src.indexOf('`', open + 1)
                    if (close < 0) break
                    if (close >= from) addStyle(styles[LineTokens.STRING], open, close + 1)
                    open = src.indexOf('`', close + 1)
                }
            }
            if (flashLine != null) {
                val start = selectionAtLine(src, flashLine).start
                val end = src.indexOf('\n', start).let { if (it < 0) src.length else it }
                if (end > start) addStyle(SpanStyle(background = Term.error.copy(alpha = 0.25f)), start, end)
            }
            if (matches != null) {
                val other = SpanStyle(background = Term.info.copy(alpha = 0.3f))
                val cur = SpanStyle(background = Term.prompt.copy(alpha = 0.55f), color = Term.bg)
                // Offsets can be briefly out of date while matches are recomputed after an edit.
                for (s in matches.starts) {
                    val e = s + matches.length
                    if (e <= src.length) addStyle(if (s == matches.current) cur else other, s, e)
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    private companion object {
        /** Above this length (about 200 lines), code gets colours only, with no italic or bold. */
        const val PLAIN_FROM = 8_000
    }

    override fun equals(other: Any?) = other is SyntaxHighlighter && other.flashLine == flashLine &&
        other.keywords == keywords && other.matches == matches && other.palette == palette && other.window == window

    override fun hashCode() = 31 * (31 * (31 * (31 * keywords.hashCode() + (flashLine ?: 0)) + matches.hashCode()) + palette.hashCode()) + window.hashCode()
}
