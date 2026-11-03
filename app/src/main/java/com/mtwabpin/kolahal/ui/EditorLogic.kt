package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ApiDoc
import com.mtwabpin.kolahal.script.ApiDocs

/** Pure (Compose-free) helpers behind the script editor: find, completion, inline docs, lines. */
object EditorLogic {

    /** Start offsets of every occurrence of a query (non-overlapping), capped at [MAX_MATCHES]. */
    class Matches(val starts: IntArray, val length: Int, val capped: Boolean) {
        val size get() = starts.size

        /** Index of the first match starting at or after [offset], wrapping to 0. */
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

    /** Matches drawn around the current one; the rest are only counted and navigable. */
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

    /** [text] with every occurrence of [query] replaced; returns the text and the count. */
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

    /** Identifier being typed at the cursor: `http.ge|` -> qualifier `http`, partial `ge`, start at `g`. */
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
            // `a.b.c` and `x:method` chains are not API namespaces.
            if (q > 0 && (text[q - 1] == '.' || text[q - 1] == ':')) return null
        }
        return Prefix(qualifier, partial, s)
    }

    /** Whether [cursor] sits in a line comment or a string literal (rough, current line only). */
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
     * Completions for the identifier at [cursor]: after a known namespace (`http.`) its members, else
     * globals, namespaces and keywords once at least 2 characters are typed. Ranked: case-sensitive
     * prefix before case-insensitive, API before keywords, then shorter names, then alphabetical.
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
                // A finished keyword or value has nothing left to insert.
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

    /** Text that replaces the partial identifier when [doc] is accepted. */
    fun insertion(doc: ApiDoc, nextChar: Char?): String = when (doc.kind) {
        ApiDoc.Kind.FUNCTION -> if (nextChar == '(') doc.member else doc.member + "("
        ApiDoc.Kind.NAMESPACE -> if (nextChar == '.') doc.member else doc.member + "."
        else -> doc.member
    }

    /**
     * The API entry to document at [cursor]: the dotted name under/just before the cursor if it is
     * known, else the innermost unclosed call `name(` before the cursor (scanning back ≤ 1000 chars).
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

    /** `http.get` for the identifier chain ending at [end] (exclusive). */
    private fun dottedNameBefore(text: String, end: Int): String? {
        var s = end
        while (s > 0 && (text[s - 1].isIdent() || text[s - 1] == '.') && end - s < 64) s--
        val name = text.substring(s, end).trim('.')
        return name.ifEmpty { null }
    }

    /** Offset of the start of [line] (1-based), or the text length if it is past the end. */
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

    /** A finished run as the editor shows it: what happened, whether it failed, and the failing line. */
    data class RunResult(val text: String, val failed: Boolean, val line: Int? = null)

    private val LINE_SUFFIX = Regex(""" \(line (\d+)\)$""")

    /**
     * Reads a run's recorded status: `ok in 812ms`, `timeout`, `stopped: <why>`, or
     * `error: <message> (line 7)`. [tapHint]: the editor's line, where a tap goes to the line.
     */
    fun runResult(status: String?, timeoutSeconds: Long, tapHint: Boolean = true): RunResult = when {
        status == null -> RunResult("Ran · its output is in the log", failed = false)
        status.startsWith("ok in ") -> RunResult("Ran in ${status.removePrefix("ok in ")} · its output is in the log", failed = false)
        status == "timeout" -> RunResult("Stopped after ${timeoutSeconds}s (the time limit)", failed = true)
        status.startsWith("stopped: ") -> RunResult("Stopped: ${status.removePrefix("stopped: ")}", failed = true)
        else -> {
            val line = LINE_SUFFIX.find(status)?.groupValues?.get(1)?.toIntOrNull()
            val message = status.removePrefix("error: ").replace(LINE_SUFFIX, "")
            // Lua puts the place first (`etest:13: attempt to …`); the line is already said.
            if (line != null) RunResult("Line $line: ${message.replace(Regex("""^\S+:$line:\s*"""), "")}" + if (tapHint) " · tap to go there" else "", failed = true, line = line)
            else RunResult("Error: $message", failed = true)
        }
    }
}

/**
 * Undo by steps: a new step after a pause in typing or at a newline, up to 200. Only the text now is
 * kept whole; each step keeps what changed from the one before ([Edit]), so a big script's history
 * isn't 200 copies of it.
 */
internal class UndoHistory(initial: String, private val clock: () -> Long = System::currentTimeMillis) {
    /** At [at], [removed] became [inserted]; the rest of the text was the same before and after. */
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

    /** The text after the last step: what [undo] steps back from. */
    private var top = initial
    private var topNewlines = initial.count { it == '\n' }
    /** The steps that led to [top], oldest first. */
    private val undo = ArrayDeque<Edit>()
    /** Steps undone, the last undone last. */
    private val redo = ArrayDeque<Edit>()
    private var lastRecord = 0L
    /** The text before the step still being typed into: what it leads from, kept rather than rebuilt at every key. */
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
            // The same step goes on: it now leads from the text before it to this one.
            val last = undo.removeLast()
            undo.addLast(Edit.between(stepStart ?: last.undo(top), text))
        }
        top = text
        topNewlines = newlines
        lastRecord = now
        redo.clear()
    }

    /** The next [record] starts a new undo step (used around replace operations). */
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
