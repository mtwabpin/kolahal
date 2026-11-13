package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ApiDoc
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorLogicTest {

    // ---- find ----

    @Test fun findsNonOverlappingMatches() {
        val m = EditorLogic.findMatches("aaaa", "aa", ignoreCase = false)
        assertEquals(listOf(0, 2), m.starts.toList())
        assertEquals(2, m.length)
    }

    @Test fun findRespectsCase() {
        val text = "Log log LOG"
        assertEquals(listOf(4), EditorLogic.findMatches(text, "log", false).starts.toList())
        assertEquals(listOf(0, 4, 8), EditorLogic.findMatches(text, "log", true).starts.toList())
    }

    @Test fun emptyQueryFindsNothing() {
        assertEquals(0, EditorLogic.findMatches("abc", "", true).size)
        assertEquals(0, EditorLogic.findMatches("ab", "abc", true).size)
    }

    @Test fun findIsCapped() {
        val m = EditorLogic.findMatches("x".repeat(50), "x", false, limit = 10)
        assertEquals(10, m.size)
        assertTrue(m.capped)
        assertFalse(EditorLogic.findMatches("xx", "x", false).capped)
    }

    @Test fun findHandlesLargeText() {
        val text = "local x = http.get(url)\n".repeat(90_000) // ~2 MB
        val m = EditorLogic.findMatches(text, "HTTP", true)
        assertEquals(90_000, m.size)
    }

    @Test fun indexAtOrAfterWraps() {
        val m = EditorLogic.findMatches("a.a.a", "a", false) // 0, 2, 4
        assertEquals(0, m.indexAtOrAfter(0))
        assertEquals(1, m.indexAtOrAfter(1))
        assertEquals(2, m.indexAtOrAfter(4))
        assertEquals(0, m.indexAtOrAfter(5))
        assertEquals(-1, EditorLogic.Matches.EMPTY.indexAtOrAfter(0))
    }

    /** Next and previous go around: after the last match comes the first, before the first the last. */
    @Test fun nextAndPreviousMatchWrapAround() {
        val m = EditorLogic.findMatches("a.a.a", "a", false) // 0, 2, 4
        // Next, from a cursor on a match or between two.
        assertEquals(1, m.indexFrom(0, forward = true))
        assertEquals(1, m.indexFrom(1, forward = true))
        assertEquals(2, m.indexFrom(2, forward = true))
        assertEquals(0, m.indexFrom(4, forward = true))
        assertEquals(0, m.indexFrom(5, forward = true))
        // Previous.
        assertEquals(1, m.indexFrom(4, forward = false))
        assertEquals(1, m.indexFrom(3, forward = false))
        assertEquals(0, m.indexFrom(2, forward = false))
        assertEquals(2, m.indexFrom(0, forward = false))
        // Past the last match, the one before the cursor is the last.
        assertEquals(2, m.indexFrom(5, forward = false))
        // A single match is both the next and the previous one.
        val one = EditorLogic.findMatches("xax", "a", false)
        assertEquals(listOf(0, 0), listOf(one.indexFrom(1, forward = true), one.indexFrom(1, forward = false)))
        assertEquals(-1, EditorLogic.Matches.EMPTY.indexFrom(0, forward = true))
        assertEquals(-1, EditorLogic.Matches.EMPTY.indexFrom(0, forward = false))
    }

    /** Only the matches around the current one are drawn, and the current one is marked by where it starts. */
    @Test fun highlightsAreTheMatchesAroundTheCurrentOne() {
        val m = EditorLogic.findMatches("x".repeat(10), "x", false) // starts 0 to 9
        // They all fit.
        assertEquals(MatchHighlights(IntArray(10) { it }, 1, 3), EditorLogic.highlights(m, 3))
        // Room for 4: two before the current one, itself, and one after.
        assertEquals(MatchHighlights(intArrayOf(3, 4, 5, 6), 1, 5), EditorLogic.highlights(m, 5, max = 4))
        // Near the first match they start at the first. Near the last they end at the last, and are fewer.
        assertEquals(MatchHighlights(intArrayOf(0, 1, 2, 3), 1, 1), EditorLogic.highlights(m, 1, max = 4))
        assertEquals(MatchHighlights(intArrayOf(7, 8, 9), 1, 9), EditorLogic.highlights(m, 9, max = 4))
        // No current match yet (-1): the first ones, and none is marked.
        assertEquals(MatchHighlights(intArrayOf(0, 1, 2, 3), 1, -1), EditorLogic.highlights(m, -1, max = 4))
        // An index past the end (the text got shorter) counts as the last match.
        assertEquals(MatchHighlights(intArrayOf(7, 8, 9), 1, 9), EditorLogic.highlights(m, 50, max = 4))
        // The length is the query's.
        assertEquals(MatchHighlights(intArrayOf(0, 4), 2, 4), EditorLogic.highlights(EditorLogic.findMatches("ab  ab", "ab", false), 1))
        assertNull(EditorLogic.highlights(EditorLogic.Matches.EMPTY, -1))
        assertNull(EditorLogic.highlights(EditorLogic.Matches.EMPTY, 0))
    }

    @Test fun replaceAllCountsAndReplaces() {
        val (out, n) = EditorLogic.replaceAll("Foo foo FOO", "foo", "bar", ignoreCase = true)
        assertEquals("bar bar bar", out)
        assertEquals(3, n)
        assertEquals("Foo bar FOO" to 1, EditorLogic.replaceAll("Foo foo FOO", "foo", "bar", ignoreCase = false))
        assertEquals("abc" to 0, EditorLogic.replaceAll("abc", "z", "y", ignoreCase = true))
    }

    // ---- prefix ----

    @Test fun prefixPlainIdentifier() =
        assertEquals(EditorLogic.Prefix(null, "lo", 6), EditorLogic.prefixAt("x = 1 lo", 8))

    @Test fun prefixAfterNamespace() {
        assertEquals(EditorLogic.Prefix("http", "ge", 5), EditorLogic.prefixAt("http.ge", 7))
        assertEquals(EditorLogic.Prefix("http", "", 5), EditorLogic.prefixAt("http.", 5))
    }

    @Test fun prefixRejectsChainsAndNumbers() {
        assertNull(EditorLogic.prefixAt("a.http.ge", 9))
        assertNull(EditorLogic.prefixAt("s:http.ge", 9))
        assertNull(EditorLogic.prefixAt("x = 12", 6))
    }

    // ---- completion ----

    private fun names(code: String, lang: ScriptLang = ScriptLang.LUA) =
        EditorLogic.completions(code, code.length, lang).map { it.name }

    @Test fun completesNamespaceMembers() {
        assertEquals(listOf("http.get", "http.cached", "http.request"), names("local r = http."))
        assertEquals(listOf("http.get"), names("http.g"))
    }

    @Test fun needsTwoCharsWithoutQualifier() {
        assertTrue(names("l").isEmpty())
        assertTrue("log" in names("lo"))
        assertTrue("local" in names("lo"))
    }

    @Test fun apiRanksBeforeKeywords() {
        // `log` and `logs` (API) before `local` (keyword) for the same prefix.
        assertEquals(listOf("log", "logs", "local"), names("lo"))
    }

    @Test fun caseSensitivePrefixRanksFirst() {
        val docs = listOf(
            ApiDoc("Store", "", ""),
            ApiDoc("store", "", "", kind = ApiDoc.Kind.NAMESPACE),
            ApiDoc("storeAll", "", ""),
        )
        val ranked = EditorLogic.rank(docs, EditorLogic.Prefix(null, "st", 0)).map { it.name }
        assertEquals(listOf("store", "storeAll", "Store"), ranked)
    }

    @Test fun completionIsLanguageSpecific() {
        assertTrue(names("json.").isNotEmpty())
        assertTrue(names("json.", ScriptLang.JS).isEmpty())
        assertEquals(listOf("console.log", "console.error"), names("console.", ScriptLang.JS))
        assertTrue("const" in names("co", ScriptLang.JS))
        assertFalse("const" in names("co", ScriptLang.LUA))
    }

    @Test fun unknownQualifierCompletesNothing() = assertTrue(names("foo.ge").isEmpty())

    @Test fun noCompletionInCommentsOrStrings() {
        assertTrue(names("-- http.").isEmpty())
        assertTrue(names("log(\"http.").isEmpty())
        assertTrue(names("// http.", ScriptLang.JS).isEmpty())
        assertTrue(names("log(\"a\") http.").isNotEmpty())
    }

    @Test fun finishedKeywordIsNotOffered() = assertFalse("end" in names("end"))

    @Test fun insertionAddsParenOrDot() {
        val get = EditorLogic.completions("http.g", 6, ScriptLang.LUA).first()
        assertEquals("get(", EditorLogic.insertion(get, null))
        assertEquals("get", EditorLogic.insertion(get, '('))
        val ns = EditorLogic.completions("ht", 2, ScriptLang.LUA).first { it.kind == ApiDoc.Kind.NAMESPACE }
        assertEquals("http.", EditorLogic.insertion(ns, null))
    }

    /** Accepting a completion replaces what was typed of the name, and puts the cursor where typing goes on. */
    @Test fun acceptingACompletionReplacesWhatWasTyped() {
        val get = ApiDoc("http.get", "", "")
        val http = ApiDoc("http", "", "", kind = ApiDoc.Kind.NAMESPACE)
        val local = ApiDoc("local", "", "", kind = ApiDoc.Kind.KEYWORD)
        // A function gets its bracket, and the cursor is after it.
        assertEquals(EditorLogic.Edited("x = http.get(", 13), EditorLogic.accept("x = http.g", 10, get))
        // A bracket that is already there is kept, and the cursor steps over it.
        assertEquals(EditorLogic.Edited("http.get(url)", 9), EditorLogic.accept("http.g(url)", 6, get))
        // The same for a namespace and its dot.
        assertEquals(EditorLogic.Edited("http.", 5), EditorLogic.accept("ht", 2, http))
        assertEquals(EditorLogic.Edited("http.get", 5), EditorLogic.accept("ht.get", 2, http))
        // A keyword gets nothing after it.
        assertEquals(EditorLogic.Edited("local x", 5), EditorLogic.accept("lo x", 2, local))
        // Only the text before the cursor is replaced.
        assertEquals(EditorLogic.Edited("http.get(XY", 9), EditorLogic.accept("http.gXY", 6, get))
        // Nothing to complete: a number is being typed, or the cursor is outside the text.
        assertNull(EditorLogic.accept("12", 2, get))
        assertNull(EditorLogic.accept("ht", 9, http))
    }

    /** A directive from the reference goes on a line of its own, above the cursor's line. */
    @Test fun aDirectiveGoesAboveTheCursorsLine() {
        val text = "local a = 1\nlocal b = 2"
        // One that takes a value: the cursor waits after it, on its line.
        assertEquals(EditorLogic.Edited("local a = 1\n-- @every \nlocal b = 2", 22), EditorLogic.insertDirective(text, 15, "-- @every "))
        // One that takes nothing: the cursor goes to the line below, where it was.
        assertEquals(EditorLogic.Edited("local a = 1\n-- @command\nlocal b = 2", 24), EditorLogic.insertDirective(text, 15, "-- @command\n"))
        // The cursor at the start of its line, or at the end of the text: the same line.
        assertEquals(EditorLogic.Edited("local a = 1\n-- @command\nlocal b = 2", 24), EditorLogic.insertDirective(text, 12, "-- @command\n"))
        assertEquals(EditorLogic.Edited("local a = 1\n-- @command\nlocal b = 2", 24), EditorLogic.insertDirective(text, text.length, "-- @command\n"))
        // On the first line, at its start or its end: above everything.
        assertEquals(EditorLogic.Edited("-- @command\nlocal a = 1\nlocal b = 2", 12), EditorLogic.insertDirective(text, 0, "-- @command\n"))
        assertEquals(EditorLogic.Edited("-- @every \nlocal a = 1\nlocal b = 2", 10), EditorLogic.insertDirective(text, 11, "-- @every "))
        // An empty script.
        assertEquals(EditorLogic.Edited("// @command\n", 12), EditorLogic.insertDirective("", 0, "// @command\n"))
        assertEquals(EditorLogic.Edited("// @every \n", 10), EditorLogic.insertDirective("", 0, "// @every "))
    }

    // ---- inline docs ----

    @Test fun docInsideCall() {
        val code = "local r = http.get(\"https://x\", { a = 1 }"
        assertEquals("http.get", EditorLogic.docAt(code, code.length, ScriptLang.LUA)?.name)
    }

    @Test fun docOnIdentifier() {
        assertEquals("rss.fresh", EditorLogic.docAt("rss.fresh", 5, ScriptLang.LUA)?.name)
        assertEquals("rss", EditorLogic.docAt("rss.", 4, ScriptLang.LUA)?.name)
    }

    @Test fun docAfterClosedCallIsNone() {
        assertNull(EditorLogic.docAt("log(\"x\") ", 9, ScriptLang.LUA))
        assertNull(EditorLogic.docAt("foo(", 4, ScriptLang.LUA))
    }

    @Test fun docUsesLanguageSignature() {
        assertTrue(EditorLogic.docAt("http.get(", 9, ScriptLang.JS)!!.signature.startsWith("await"))
        assertFalse(EditorLogic.docAt("http.get(", 9, ScriptLang.LUA)!!.signature.startsWith("await"))
    }

    // ---- lines ----

    @Test fun lineStartOffsets() {
        val t = "a\nbb\nccc"
        assertEquals(0, EditorLogic.lineStart(t, 1))
        assertEquals(2, EditorLogic.lineStart(t, 2))
        assertEquals(5, EditorLogic.lineStart(t, 3))
        assertEquals(t.length, EditorLogic.lineStart(t, 9))
    }

    @Test fun runResultReadsTheRecordedStatus() {
        assertEquals(EditorLogic.RunResult("Ran in 812ms · its output is in the log", failed = false), EditorLogic.runResult("ok in 812ms", 60))
        assertEquals(EditorLogic.RunResult("Stopped after 60s (the time limit)", failed = true), EditorLogic.runResult("timeout", 60))
        assertEquals(
            EditorLogic.RunResult("Line 7: attempt to call a nil value · tap to go there", failed = true, line = 7),
            EditorLogic.runResult("error: attempt to call a nil value (line 7)", 60),
        )
        assertEquals(
            EditorLogic.RunResult("Line 13: attempt to perform arithmetic on a nil value · tap to go there", failed = true, line = 13),
            EditorLogic.runResult("error: etest:13: attempt to perform arithmetic on a nil value (line 13)", 60),
        )
        assertEquals(EditorLogic.RunResult("Error: boom", failed = true), EditorLogic.runResult("error: boom", 60))
        assertEquals(EditorLogic.RunResult("Stopped: internet off", failed = true), EditorLogic.runResult("stopped: internet off", 60))
        assertEquals("Line 7: boom", EditorLogic.runResult("error: boom (line 7)", 60, tapHint = false).text)
        // A status recorded before lines were kept, or one whose message mentions a line itself.
        assertEquals(EditorLogic.RunResult("Error: x (line 2) y", failed = true), EditorLogic.runResult("error: x (line 2) y", 60))
    }

    // ---- the field's width ----

    /** A made-up font: ASCII 6 to 10 px, a tab 12 at most, anything else up to 20; [cellPx] 10. */
    private fun width(line: String) = line.sumOf { c ->
        when {
            c == '\t' -> 12
            c in ' '..'~' -> 6 + c.code % 5
            else -> 11 + c.code % 10
        }
    }.toFloat()

    @Test fun theWidestLineIsTheWidestMeasured() {
        val r = kotlin.random.Random(3)
        val letters = "ab WM\t日é🙂"
        repeat(2_000) {
            val text = (0 until r.nextInt(0, 12)).joinToString("\n") { String(CharArray(r.nextInt(0, 30)) { letters[r.nextInt(letters.length)] }) }
            val widest = text.split('\n').maxOf(::width)
            assertEquals(text, widest, EditorLogic.widestLine(text, cellPx = 10f, tabPx = 12f, measure = ::width), 0f)
        }
    }

    /** Most lines needn't be measured: none could be wider than the widest found. */
    @Test fun fewLinesAreMeasured() {
        val text = (List(500) { "short line $it" } + "a much longer line than the rest of them, by far" + List(500) { "x = $it" }).joinToString("\n")
        var measured = 0
        EditorLogic.widestLine(text, cellPx = 10f, tabPx = 12f) { measured++; width(it) }
        assertTrue("$measured lines measured", measured <= 3)
        assertEquals(0f, EditorLogic.widestLine("", 10f, 12f, ::width), 0f)
        // An empty last line, or only line breaks.
        assertEquals(width("abc"), EditorLogic.widestLine("abc\n", 10f, 12f, ::width), 0f)
        assertEquals(0f, EditorLogic.widestLine("\n\n", 10f, 12f, ::width), 0f)
    }
}
