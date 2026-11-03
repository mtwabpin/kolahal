package com.mtwabpin.kolahal.ui

import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.AnnotatedString
import com.mtwabpin.kolahal.data.ScriptLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor's colours: kept tokens and a kept answer give what colouring afresh gives. */
class SyntaxHighlighterTest {
    private val lua = """
        -- @every 1h
        local n = 0
        for i = 1, 10 do n = n + i end
        if n > 3 then print("sum " .. n) end
        return 'done'
    """.trimIndent()

    private val js = "// @every 1h\nconst x = `a\nb`;\nif (x) { http.get(\"https://example.com\"); }\nreturn 42.5;"

    @Test fun keptTokensColourAsAFreshHighlighter() {
        for ((lang, code) in listOf(ScriptLang.LUA to lua, ScriptLang.JS to js)) {
            val tokens = LineTokens(lang)
            // Warm the shared tokens with other text first, as typing does.
            SyntaxHighlighter(lang, null, null, tokens).filter(AnnotatedString(code.replace("1", "2")))
            val matches = MatchHighlights(intArrayOf(0, 10), 3, 10)
            for ((flash, find) in listOf(null to null, 2 to null, null to matches, 3 to matches)) {
                val fresh = SyntaxHighlighter(lang, flash, find).filter(AnnotatedString(code)).text
                val kept = SyntaxHighlighter(lang, flash, find, tokens).filter(AnnotatedString(code)).text
                assertEquals("$lang $flash $find", fresh, kept)
            }
        }
    }

    @Test fun theSameTextIsntColouredAgain() {
        val h = SyntaxHighlighter(ScriptLang.LUA, null, null)
        val first = h.filter(AnnotatedString(lua))
        assertSame(first, h.filter(AnnotatedString(lua)))
        // A changed text is coloured for what it now says.
        val changed = h.filter(AnnotatedString("$lua\nprint(1)"))
        assertEquals(SyntaxHighlighter(ScriptLang.LUA, null, null).filter(AnnotatedString("$lua\nprint(1)")).text, changed.text)
    }

    /** The last answer stands only for the theme it was coloured in. */
    @Test fun aNewThemeColoursAgain() {
        val before = Term.palette
        try {
            Term.palette = Palettes.green
            val h = SyntaxHighlighter(ScriptLang.LUA, null, null)
            val green = h.filter(AnnotatedString(lua))
            Term.palette = Palettes.dracula
            val dracula = h.filter(AnnotatedString(lua))
            assertNotSame(green, dracula)
            assertEquals(SyntaxHighlighter(ScriptLang.LUA, null, null).filter(AnnotatedString(lua)).text, dracula.text)
        } finally {
            Term.palette = before
        }
    }

    /** A big script is coloured without bold and italic, which make its layout far slower; a usual one keeps them. */
    @Test fun aBigScriptIsColouredOnly() {
        fun fonts(code: String) = SyntaxHighlighter(ScriptLang.LUA, null, null).filter(AnnotatedString(code)).text.spanStyles
            .count { it.item.fontWeight != null || it.item.fontStyle != null }
        assertTrue(fonts(lua) > 0)
        val big = buildString { while (length <= 8_000) append(lua).append('\n') }
        assertEquals(0, fonts(big))
        // The colours stay.
        assertTrue(SyntaxHighlighter(ScriptLang.LUA, null, null).filter(AnnotatedString(big)).text.spanStyles.size > 100)
    }

    /** The kept tokens hold a bounded amount of text, not a number of lines: a long script stays whole. */
    @Test fun keptTokensAreBoundedByText() {
        val tokens = LineTokens(ScriptLang.LUA)
        // More lines than the 8,000 once kept, all of them kept.
        val short = (0 until 9_000).map { "local x$it = $it" }
        short.forEach { tokens.of(it) }
        assertEquals(short.sumOf { it.length }.toLong(), tokens.keptChars)
        // Copies of a long line typed on, and very long lines: no more than the cap.
        repeat(3_000) { tokens.of("local s = '" + "a".repeat(1_500) + it + "'") }
        repeat(500) { tokens.of("-- " + "b".repeat(5_000) + it) }
        assertTrue(tokens.keptChars <= LineTokens.KEPT_CHARS + LineTokens.KEPT_LONG_CHARS)
        // A line let go is tokenised again alike.
        assertTrue(LineTokens(ScriptLang.LUA).of(short[0]).contentEquals(tokens.of(short[0])))
    }

    /** A window colours its lines as the whole script would, and no others; matches and the flash show anywhere. */
    @Test fun aWindowColoursOnlyItsLines() {
        for ((lang, code) in listOf(ScriptLang.LUA to lua, ScriptLang.JS to "$js\nconst y = `x\ny`;\n// end")) {
            val starts = listOf(0) + code.indices.filter { code[it] == '\n' }.map { it + 1 }
            fun lineOf(offset: Int) = starts.indexOfLast { it <= offset }
            val whole = SyntaxHighlighter(lang, null, null).filter(AnnotatedString(code)).text.spanStyles
            for (window in listOf(0..0, 1..2, 2..100, 50..60)) {
                val part = SyntaxHighlighter(lang, null, null, window = window).filter(AnnotatedString(code)).text.spanStyles
                // Every colour in the window, as coloured whole; none outside it.
                val expected = whole.filter { r -> (lineOf(r.start)..lineOf((r.end - 1).coerceAtLeast(r.start))).any { it in window } }
                assertEquals("$lang $window", expected.map { Triple(it.start, it.end, it.item) }.toSet(), part.map { Triple(it.start, it.end, it.item) }.toSet())
            }
            val found = MatchHighlights(intArrayOf(code.length - 3), 3, code.length - 3)
            val marked = SyntaxHighlighter(lang, 1, found, window = 0..0).filter(AnnotatedString(code)).text.spanStyles
            assertTrue("$lang: the match far below the window", marked.any { it.start == code.length - 3 && it.item.background.isSpecified })
            assertTrue("$lang: the flashed line", marked.any { it.start == 0 && it.item.background.isSpecified })
        }
        assertNotEquals(SyntaxHighlighter(ScriptLang.LUA, null, null, window = 0..10), SyntaxHighlighter(ScriptLang.LUA, null, null, window = 5..15))
    }

    @Test fun findMatchesAreEqualByWhatTheyMark() {
        assertEquals(MatchHighlights(intArrayOf(1, 5), 2, 5), MatchHighlights(intArrayOf(1, 5), 2, 5))
        assertEquals(
            SyntaxHighlighter(ScriptLang.LUA, null, MatchHighlights(intArrayOf(1, 5), 2, 5)),
            SyntaxHighlighter(ScriptLang.LUA, null, MatchHighlights(intArrayOf(1, 5), 2, 5)),
        )
        assertNotEquals(MatchHighlights(intArrayOf(1, 5), 2, 5), MatchHighlights(intArrayOf(1, 5), 2, 1))
    }
}
