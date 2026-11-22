/**
 * Manual Review - Pending
 */
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
 * The colors a text gets in the editor: a script language's, or JSON's. AGSL's are for reading a
 * shader's code ([ReadOnlyCode]).
 * - [script]: the script language whose keywords it uses. JSON and AGSL have none.
 */
enum class Syntax(val script: ScriptLang?) {
    LUA(ScriptLang.LUA),
    JS(ScriptLang.JS),
    JSON(null),
    AGSL(null);

    companion object {
        fun of(lang: ScriptLang): Syntax = when (lang) {
            ScriptLang.LUA -> LUA
            ScriptLang.JS -> JS
        }
    }
}

/**
 * A cache of tokens per line, keyed by the line's text. When the user types, only the edited line
 * is matched again, not the whole script.
 * - Each token is (start, end, kind), with offsets inside its line.
 * - The editor keeps one per language, so a new [SyntaxHighlighter] (for a new colored window)
 *   starts with the tokens already found.
 */
internal class LineTokens(val syntax: Syntax) {
    val keywords = if (syntax == Syntax.AGSL) AGSL_WORDS
    else syntax.script?.let { ApiDocs.keywords(it).toSet() } ?: emptySet()

    // Every token here is on one line. JS template strings (`…`) can span lines, so they are found separately.
    private val token = Regex(
        when (syntax) {
            // A key is a string followed by a colon (group 1). A value is a string, a number, true, false or null.
            Syntax.JSON -> "\"(?:\\\\.|[^\"\\\\\n])*\"(\\s*:)?|-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b|\\b(?:true|false|null)\\b"
            else -> (if (syntax == Syntax.LUA) "--[^\n]*" else "//[^\n]*") +
                    "|\"(?:\\\\.|[^\"\\\\\n])*\"|'(?:\\\\.|[^'\\\\\n])*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z0-9_]*\\b"
        }
    )

    /**
     * The cache limit is the number of characters, not the number of lines. Why:
     * - Typing on a line stores a new copy of it at every key. The oldest copies are removed first,
     *   and the script's own lines are used at every key, so they stay.
     * - With a line count limit, a script with more lines than the limit had every line tokenized
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

    /** Returns the tokens of [line], from the cache or tokenized now. */
    fun of(line: String): IntArray =
        (if (line.length > LONG_LINE) longLines else lineTokens).getOrPut(line) { tokenize(line) }

    /** Tokens by line, least recently used first. The cached lines hold at most [maxChars] characters in total. */
    private class Kept(private val maxChars: Long) {
        private val map = LinkedHashMap<String, IntArray>(512, 0.75f, true)
        var chars = 0L
            private set

        // `e.key === line` is deliberate: it is the very line just added, which must not be evicted.
        @Suppress("StringReferentialEquality")
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

    private fun tokenize(line: String): IntArray =
        if (syntax == Syntax.JSON) tokenizeJson(line) else tokenizeScript(line)

    /**
     * JSON has no keywords or comments, so its kinds mean other things:
     * - a key takes the keyword color, so the shape of the data stands out;
     * - a string value takes the string color;
     * - a number, true, false and null take the number color: they are all plain values.
     * The colon after a key is not colored.
     */
    private fun tokenizeJson(line: String): IntArray {
        val out = ArrayList<Int>()
        for (m in token.findAll(line)) {
            val t = m.value
            val colon = m.groups[1]
            val end = colon?.range?.first ?: (m.range.last + 1)
            val kind = when {
                colon != null -> KEYWORD
                t.startsWith("\"") -> STRING
                else -> NUMBER
            }
            out += m.range.first; out += end; out += kind
        }
        return out.toIntArray()
    }

    private fun tokenizeScript(line: String): IntArray {
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

        /** The words of AGSL that stand out: the types and the control flow. */
        private val AGSL_WORDS = setOf(
            "uniform", "shader", "layout", "const", "in", "out", "inout", "struct", "void", "bool", "int",
            "float", "float2", "float3", "float4", "half", "half2", "half3", "half4", "int2", "int3", "int4",
            "float2x2", "float3x3", "float4x4", "half2x2", "half3x3", "half4x4", "vec2", "vec3", "vec4",
            "mat2", "mat3", "mat4", "if", "else", "for", "while", "do", "return", "break", "continue",
            "true", "false",
        )

        const val COMMENT = 0
        const val STRING = 1
        const val NUMBER = 2
        const val KEYWORD = 3
    }
}

/**
 * Colors code in the editor: comments, strings, numbers and keywords.
 * - Find matches and a flashed line are not colors: the editor draws them behind the text
 *   (EditorMarks), so a jump to the next match does not lay the whole text out again.
 * - A line longer than [MAX_COLORED_LINE] (minified JSON or code) is not colored. It would be
 *   tokenized again at every key, and its thousands of colors make its layout slow.
 * - Plain text has no language and gets no highlighter: the editor uses no transformation then.
 *
 * If [window] is set, only those lines (counted from 0) are colored. It is used for the lines
 * around the visible part of a big script, because the colors cost several times the text itself
 * to lay out at every key.
 */
internal class SyntaxHighlighter(
    lang: Syntax,
    private val tokens: LineTokens = LineTokens(lang),
    private val window: IntRange? = null,
) : VisualTransformation {
    // The theme this was made for. A text field colors again only when the new highlighter is not equal to the last one.
    private val palette = Term.palette
    private val js = lang == Syntax.JS

    // The text field asks again for the same text when only the cursor or selection moves, twice
    // per change. Return the last result in that case.
    private var lastText: AnnotatedString? = null
    private var lastPalette: Palette? = null
    private var last: TransformedText? = null

    override fun filter(text: AnnotatedString): TransformedText {
        last?.let { if (text == lastText && Term.palette == lastPalette) return it }
        return color(text).also {
            lastText = text
            lastPalette = Term.palette
            last = it
        }
    }

    private fun color(text: AnnotatedString): TransformedText {
        val src = text.text
        // A big script is colored without italic comments and bold keywords. Their layout cost grows
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
                    if (lineEnd - lineStart <= MAX_COLORED_LINE) {
                        val t = tokens.of(src.substring(lineStart, lineEnd))
                        for (i in t.indices step 3) addStyle(
                            styles[t[i + 2]],
                            lineStart + t[i],
                            lineStart + t[i + 1]
                        )
                    }
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
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    companion object {
        /** Above this length (about 200 lines), code gets colors only, with no italic or bold. */
        private const val PLAIN_FROM = 8_000

        /** A line longer than this gets no colors. */
        const val MAX_COLORED_LINE = 5_000
    }

    override fun equals(other: Any?) =
        other is SyntaxHighlighter && other.tokens.syntax == tokens.syntax &&
                other.palette == palette && other.window == window

    override fun hashCode() =
        31 * (31 * tokens.syntax.hashCode() + palette.hashCode()) + window.hashCode()
}
