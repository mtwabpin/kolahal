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

/** Find matches to draw: a window of start offsets around the current one. Equal when they mark the same. */
internal class MatchHighlights(val starts: IntArray, val length: Int, val current: Int) {
    override fun equals(other: Any?) =
        other is MatchHighlights && other.length == length && other.current == current && other.starts.contentEquals(starts)

    override fun hashCode() = 31 * (31 * starts.contentHashCode() + length) + current
}

/**
 * Tokens per line, by the line's text: typing re-matches only the line being edited, not the
 * whole script. Each token is (start, end, kind) within its line. Kept by the editor for its
 * language, so a new [SyntaxHighlighter] (a find match, a flashed line) starts with them.
 */
internal class LineTokens(lang: ScriptLang) {
    val keywords = ApiDocs.keywords(lang).toSet()
    // Every token here stays on one line; JS template strings (`…`), which may span lines, are found separately.
    private val token = Regex(
        (if (lang == ScriptLang.LUA) "--[^\n]*" else "//[^\n]*") +
            "|\"(?:\\\\.|[^\"\\\\\n])*\"|'(?:\\\\.|[^'\\\\\n])*'|\\b\\d+(?:\\.\\d+)?\\b|\\b[A-Za-z_][A-Za-z0-9_]*\\b"
    )

    private val lineTokens = object : LinkedHashMap<String, IntArray>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntArray>?) = size > 8000
    }

    /**
     * Very long lines (minified code, data) are kept apart, a few at a time: typing on one makes a new
     * one on every key, and among the 8,000 kept lines each would stay, a copy of the line each.
     */
    private val longLines = object : LinkedHashMap<String, IntArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, IntArray>?) = size > 8
    }

    fun of(line: String): IntArray = (if (line.length > LONG_LINE) longLines else lineTokens).getOrPut(line) { tokenize(line) }

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
        /** Lines longer than this are kept apart ([longLines]). */
        const val LONG_LINE = 2_000
        const val COMMENT = 0
        const val STRING = 1
        const val NUMBER = 2
        const val KEYWORD = 3
    }
}

/** Colours comments, strings, numbers and keywords; flashes one line (error jump); marks find matches. */
internal class SyntaxHighlighter(
    lang: ScriptLang,
    private val flashLine: Int?,
    private val matches: MatchHighlights?,
    private val tokens: LineTokens = LineTokens(lang),
) : VisualTransformation {
    private val keywords = tokens.keywords
    // The theme it was made for: a text field colours again only for a highlighter that isn't equal to the last.
    private val palette = Term.palette
    private val js = lang == ScriptLang.JS

    private fun tokensOf(line: String): IntArray = tokens.of(line)

    // The text field asks again for the same text when only the cursor or selection moved, twice
    // per change: the last answer stands.
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
        // A big script is coloured without italic comments and bold keywords: laying out a script
        // with them costs more the more of them there are (on the emulator, 25 KB took 396 ms with
        // them and 73 ms without, at every key).
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
            while (lineStart <= src.length) {
                val lineEnd = src.indexOf('\n', lineStart).let { if (it < 0) src.length else it }
                val t = tokensOf(src.substring(lineStart, lineEnd))
                for (i in t.indices step 3) addStyle(styles[t[i + 2]], lineStart + t[i], lineStart + t[i + 1])
                lineStart = lineEnd + 1
            }
            if (js) {
                // Template strings: pair up backticks with a plain scan (no escapes inside are honoured, as before).
                var open = src.indexOf('`')
                while (open >= 0) {
                    val close = src.indexOf('`', open + 1)
                    if (close < 0) break
                    addStyle(styles[STRING], open, close + 1)
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
                // Offsets can be a beat stale while matches are recomputed after an edit.
                for (s in matches.starts) {
                    val e = s + matches.length
                    if (e <= src.length) addStyle(if (s == matches.current) cur else other, s, e)
                }
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    private companion object {
        const val STRING = LineTokens.STRING

        /** Longer than this (about 550 lines), code is coloured only. */
        const val PLAIN_FROM = 20_000
    }

    override fun equals(other: Any?) = other is SyntaxHighlighter && other.flashLine == flashLine &&
        other.keywords == keywords && other.matches == matches && other.palette == palette

    override fun hashCode() = 31 * (31 * (31 * keywords.hashCode() + (flashLine ?: 0)) + matches.hashCode()) + palette.hashCode()
}
