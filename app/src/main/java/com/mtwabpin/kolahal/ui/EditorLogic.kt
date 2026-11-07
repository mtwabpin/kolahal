package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ApiDoc
import com.mtwabpin.kolahal.script.ApiDocs

/** Pure helpers for the script editor, with no Compose code: find, completion, inline docs and lines. */
object EditorLogic {

    /** Start offsets of all non-overlapping matches of a query, up to [MAX_MATCHES]. */
    class Matches(val starts: IntArray, val length: Int, val capped: Boolean) {
        val size get() = starts.size

        /** Index of the first match that starts at or after [offset]. If there is none, it wraps to 0. */
        fun indexAtOrAfter(offset: Int): Int {
            if (starts.isEmpty()) return -1
            var lo = 0
            var hi = starts.size
            while (lo < hi) {
                val mid = (lo + hi) ushr 1
                if (starts[mid] < offset) lo = mid + 1 else hi = mid
            }
            return if (lo == starts.size) 0 else lo
        }

        companion object {
            val EMPTY = Matches(IntArray(0), 0, false)
        }
    }

    const val MAX_MATCHES = 100_000

    /** How many matches are highlighted around the current one. The others are only counted, but you can still move to them. */
    const val MAX_HIGHLIGHTS = 2_000

    fun findMatches(text: String, query: String, ignoreCase: Boolean, limit: Int = MAX_MATCHES): Matches {
        if (query.isEmpty() || query.length > text.length) return Matches.EMPTY
        var out = IntArray(16)
        var n = 0
        var i = text.indexOf(query, 0, ignoreCase)
        while (i >= 0) {
            if (n == limit) return Matches(out.copyOf(n), query.length, capped = true)
            if (n == out.size) out = out.copyOf(n * 2)
            out[n++] = i
            i = text.indexOf(query, i + query.length, ignoreCase)
        }
        return Matches(out.copyOf(n), query.length, capped = false)
    }

    /** Replaces every match of [query] in [text]. Returns the new text and the number of replacements. */
    fun replaceAll(text: String, query: String, replacement: String, ignoreCase: Boolean): Pair<String, Int> {
        val m = findMatches(text, query, ignoreCase, Int.MAX_VALUE)
        if (m.size == 0) return text to 0
        val sb = StringBuilder(text.length + (replacement.length - query.length).coerceAtLeast(0) * m.size)
        var last = 0
        for (s in m.starts) {
            sb.append(text, last, s).append(replacement)
            last = s + query.length
        }
        sb.append(text, last, text.length)
        return sb.toString() to m.size
    }

    /** The identifier being typed at the cursor. For `http.ge|`: qualifier `http`, partial `ge`, start at `g`. */
    data class Prefix(val qualifier: String?, val partial: String, val start: Int)

    private fun Char.isIdent() = isLetterOrDigit() || this == '_' || this == '$'

    fun prefixAt(text: String, cursor: Int): Prefix? {
        if (cursor < 0 || cursor > text.length) return null
        var s = cursor
        while (s > 0 && text[s - 1].isIdent() && cursor - s < 64) s--
        val partial = text.substring(s, cursor)
        if (partial.isNotEmpty() && partial[0].isDigit()) return null
        var qualifier: String? = null
        if (s > 0 && text[s - 1] == '.') {
            var q = s - 1
            while (q > 0 && text[q - 1].isIdent() && s - q < 64) q--
            qualifier = text.substring(q, s - 1)
            if (qualifier.isEmpty() || qualifier[0].isDigit()) return null
            // Chains like `a.b.c` and `x:method` are not API namespaces.
            if (q > 0 && (text[q - 1] == '.' || text[q - 1] == ':')) return null
        }
        return Prefix(qualifier, partial, s)
    }

    /** Whether [cursor] is inside a line comment or a string literal. This is a rough check of the current line only. */
    fun inCommentOrString(text: String, cursor: Int, lang: ScriptLang): Boolean {
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        if (cursor - lineStart > 2_000) return false
        var quote = 0.toChar()
        var i = lineStart
        while (i < cursor) {
            val c = text[i]
            if (quote != 0.toChar()) {
                if (c == '\\') i++ else if (c == quote) quote = 0.toChar()
            } else if (c == '"' || c == '\'' || c == '`') {
                quote = c
            } else if (lang == ScriptLang.LUA && c == '-' && i + 1 < cursor && text[i + 1] == '-') {
                return true
            } else if (lang == ScriptLang.JS && c == '/' && i + 1 < cursor && text[i + 1] == '/') {
                return true
            }
            i++
        }
        return quote != 0.toChar()
    }

    /**
     * Completions for the identifier at [cursor].
     * - After a known namespace (`http.`), it offers that namespace's members.
     * - Otherwise it offers globals, namespaces and keywords, once at least 2 characters are typed.
     *
     * Order of results:
     * - case-sensitive prefix matches before case-insensitive ones,
     * - API names before keywords,
     * - shorter names first,
     * - then alphabetical.
     */
    fun completions(text: String, cursor: Int, lang: ScriptLang): List<ApiDoc> {
        val p = prefixAt(text, cursor) ?: return emptyList()
        if (p.qualifier == null && p.partial.length < 2) return emptyList()
        if (p.qualifier != null && !ApiDocs.isNamespace(p.qualifier, lang)) return emptyList()
        if (inCommentOrString(text, cursor, lang)) return emptyList()
        return rank(ApiDocs.forLang(lang), p)
    }

    fun rank(docs: List<ApiDoc>, p: Prefix, limit: Int = 30): List<ApiDoc> {
        val candidates = docs.filter { d ->
            d.qualifier == p.qualifier && d.member.startsWith(p.partial, ignoreCase = true) &&
                // A keyword or value that is already complete has nothing left to insert.
                !(d.member == p.partial && (d.kind == ApiDoc.Kind.KEYWORD || d.kind == ApiDoc.Kind.VALUE))
        }
        return candidates.sortedWith(
            compareBy(
                { if (it.member.startsWith(p.partial)) 0 else 1 },
                { if (it.kind == ApiDoc.Kind.KEYWORD) 1 else 0 },
                { it.member.length },
                { it.member },
            )
        ).take(limit)
    }

    /** The text that replaces the partial identifier when the user accepts [doc]. */
    fun insertion(doc: ApiDoc, nextChar: Char?): String = when (doc.kind) {
        ApiDoc.Kind.FUNCTION -> if (nextChar == '(') doc.member else doc.member + "("
        ApiDoc.Kind.NAMESPACE -> if (nextChar == '.') doc.member else doc.member + "."
        else -> doc.member
    }

    /**
     * The API entry to show docs for at [cursor]:
     * - the dotted name under or just before the cursor, if it is known;
     * - otherwise the innermost unclosed call `name(` before the cursor. It scans back at most 1000 chars.
     */
    fun docAt(text: String, cursor: Int, lang: ScriptLang): ApiDoc? {
        if (cursor < 0 || cursor > text.length) return null
        var end = cursor
        while (end < text.length && text[end].isIdent() && end - cursor < 64) end++
        dottedNameBefore(text, end)?.let { name -> ApiDocs.find(name, lang)?.let { return it } }

        var depth = 0
        var i = cursor - 1
        val stop = (cursor - 1_000).coerceAtLeast(0)
        while (i >= stop) {
            when (text[i]) {
                ')', ']', '}' -> depth++
                '[', '{' -> if (depth == 0) return null else depth--
                '(' -> if (depth == 0) {
                    var j = i
                    while (j > 0 && text[j - 1] == ' ') j--
                    return dottedNameBefore(text, j)?.let { ApiDocs.find(it, lang) }
                } else depth--
            }
            i--
        }
        return null
    }

    /** Returns the dotted name (like `http.get`) that ends at [end] (exclusive). */
    private fun dottedNameBefore(text: String, end: Int): String? {
        var s = end
        while (s > 0 && (text[s - 1].isIdent() || text[s - 1] == '.') && end - s < 64) s--
        val name = text.substring(s, end).trim('.')
        return name.ifEmpty { null }
    }

    /** Offset where [line] (1-based) starts. Returns the text length if the line is past the end. */
    fun lineStart(text: String, line: Int): Int {
        if (line <= 1) return 0
        var offset = 0
        repeat(line - 1) {
            val next = text.indexOf('\n', offset)
            if (next < 0) return text.length
            offset = next + 1
        }
        return offset
    }

    /**
     * The width of the widest line in [text], using [measure] for one line's width. The editor field
     * gets this width, so it does not need to measure the whole text before layout.
     * - [cellPx] is at least the width of any ASCII character. [tabPx] is the width of a tab.
     * - Any other character counts as two cells, because no glyph is wider.
     * - Lines are measured from the widest guess down. It stops when no other line can be wider.
     */
    fun widestLine(text: String, cellPx: Float, tabPx: Float, measure: (String) -> Float): Float {
        if (text.isEmpty()) return 0f
        val starts = ArrayList<Int>()
        val guesses = ArrayList<Float>()
        var start = 0
        var guess = 0f
        for (i in 0..text.length) {
            val c = if (i < text.length) text[i] else '\n'
            if (c == '\n') {
                starts += start
                guesses += guess
                start = i + 1
                guess = 0f
            } else {
                guess += when {
                    c == '\t' -> tabPx
                    c in ' '..'~' -> cellPx
                    else -> 2 * cellPx
                }
            }
        }
        var widest = 0f
        for (line in guesses.indices.sortedByDescending { guesses[it] }) {
            if (guesses[line] <= widest) break
            val end = text.indexOf('\n', starts[line]).let { if (it < 0) text.length else it }
            widest = maxOf(widest, measure(text.substring(starts[line], end)))
        }
        return widest
    }

    /** A finished run as the editor shows it: what happened, whether it failed, and the line that failed. */
    data class RunResult(val text: String, val failed: Boolean, val line: Int? = null)

    private val LINE_SUFFIX = Regex(""" \(line (\d+)\)$""")

    /**
     * Reads a run's recorded status. The status is one of:
     * - `ok in 812ms`
     * - `timeout`
     * - `stopped: <why>`
     * - `error: <message> (line 7)`
     *
     * Set [tapHint] for the editor's line, where a tap jumps to the failing line.
     */
    fun runResult(status: String?, timeoutSeconds: Long, tapHint: Boolean = true): RunResult = when {
        status == null -> RunResult("Ran · its output is in the log", failed = false)
        status.startsWith("ok in ") -> RunResult("Ran in ${status.removePrefix("ok in ")} · its output is in the log", failed = false)
        status == "timeout" -> RunResult("Stopped after ${timeoutSeconds}s (the time limit)", failed = true)
        status.startsWith("stopped: ") -> RunResult("Stopped: ${status.removePrefix("stopped: ")}", failed = true)
        else -> {
            val line = LINE_SUFFIX.find(status)?.groupValues?.get(1)?.toIntOrNull()
            val message = status.removePrefix("error: ").replace(LINE_SUFFIX, "")
            // Lua puts the location first (`etest:13: attempt to …`). Remove it, because the line number is already shown.
            if (line != null) RunResult("Line $line: ${message.replace(Regex("""^\S+:$line:\s*"""), "")}" + if (tapHint) " · tap to go there" else "", failed = true, line = line)
            else RunResult("Error: $message", failed = true)
        }
    }
}

/**
 * Undo history in steps, up to 200. A new step starts after a pause in typing or at a newline.
 * Only the current text is stored in full. Each step stores only what changed from the step before
 * ([Edit]), so the history of a big script is not 200 copies of it.
 */
internal class UndoHistory(initial: String, private val clock: () -> Long = System::currentTimeMillis) {
    /** At offset [at], [removed] was replaced by [inserted]. The rest of the text did not change. */
    private class Edit(val at: Int, val removed: String, val inserted: String) {
        fun redo(text: String) = text.substring(0, at) + inserted + text.substring(at + removed.length)
        fun undo(text: String) = text.substring(0, at) + removed + text.substring(at + inserted.length)

        companion object {
            fun between(before: String, after: String): Edit {
                val most = minOf(before.length, after.length)
                var start = 0
                while (start < most && before[start] == after[start]) start++
                var end = 0
                while (end < most - start && before[before.length - 1 - end] == after[after.length - 1 - end]) end++
                return Edit(start, before.substring(start, before.length - end), after.substring(start, after.length - end))
            }
        }
    }

    /** The text after the last step. [undo] steps back from this. */
    private var top = initial
    private var topNewlines = initial.count { it == '\n' }
    /** The steps that led to [top], oldest first. */
    private val undo = ArrayDeque<Edit>()
    /** Undone steps. The most recently undone step is last. */
    private val redo = ArrayDeque<Edit>()
    private var lastRecord = 0L
    /** The text before the step that is still being typed. It is kept so it is not rebuilt on every key press. */
    private var stepStart: String? = null

    private fun setTop(text: String) {
        top = text
        topNewlines = text.count { it == '\n' }
    }

    fun record(text: String) {
        val now = clock()
        val newlines = text.count { it == '\n' }
        val newline = text.length > top.length && newlines != topNewlines
        if (now - lastRecord > 600 || newline || undo.isEmpty()) {
            stepStart = top
            undo.addLast(Edit.between(top, text))
            if (undo.size + 1 > 200) undo.removeFirst()
        } else {
            // The same step continues: it now goes from the text before the step to this text.
            val last = undo.removeLast()
            undo.addLast(Edit.between(stepStart ?: last.undo(top), text))
        }
        top = text
        topNewlines = newlines
        lastRecord = now
        redo.clear()
    }

    /** Makes the next [record] start a new undo step. Used around replace operations. */
    fun checkpoint() {
        lastRecord = 0
        stepStart = null
    }

    val canUndo get() = undo.isNotEmpty()
    val canRedo get() = redo.isNotEmpty()

    fun undo(): String? {
        val step = undo.removeLastOrNull() ?: return null
        redo.addLast(step)
        lastRecord = 0
        stepStart = null
        setTop(step.undo(top))
        return top
    }

    fun redo(): String? {
        val step = redo.removeLastOrNull() ?: return null
        undo.addLast(step)
        lastRecord = 0
        stepStart = null
        setTop(step.redo(top))
        return top
    }
}
