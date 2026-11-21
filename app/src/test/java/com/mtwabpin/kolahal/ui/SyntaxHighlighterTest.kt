package com.mtwabpin.kolahal.ui

import androidx.compose.ui.text.AnnotatedString
import com.mtwabpin.kolahal.withValue
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

    private val js =
        "// @every 1h\nconst x = `a\nb`;\nif (x) { http.get(\"https://example.com\"); }\nreturn 42.5;"

    @Test
    fun keptTokensColourAsAFreshHighlighter() {
        for ((lang, code) in listOf(Syntax.LUA to lua, Syntax.JS to js)) {
            val tokens = LineTokens(lang)
            // Warm the shared tokens with other text first, as typing does.
            SyntaxHighlighter(lang, tokens).filter(AnnotatedString(code.replace("1", "2")))
            val fresh = SyntaxHighlighter(lang).filter(AnnotatedString(code)).text
            val kept = SyntaxHighlighter(lang, tokens).filter(AnnotatedString(code)).text
            assertEquals("$lang", fresh, kept)
        }
    }

    @Test
    fun theSameTextIsntColouredAgain() {
        val h = SyntaxHighlighter(Syntax.LUA)
        val first = h.filter(AnnotatedString(lua))
        assertSame(first, h.filter(AnnotatedString(lua)))
        // A changed text is coloured for what it now says.
        val changed = h.filter(AnnotatedString("$lua\nprint(1)"))
        assertEquals(
            SyntaxHighlighter(Syntax.LUA).filter(AnnotatedString("$lua\nprint(1)")).text,
            changed.text
        )
    }

    /** The last answer stands only for the theme it was coloured in. */
    @Test
    fun aNewThemeColoursAgain() {
        withValue(Term::palette, Palettes.green) {
            val h = SyntaxHighlighter(Syntax.LUA)
            val green = h.filter(AnnotatedString(lua))
            withValue(Term::palette, Palettes.dracula) {
                val dracula = h.filter(AnnotatedString(lua))
                assertNotSame(green, dracula)
                assertEquals(
                    SyntaxHighlighter(Syntax.LUA).filter(AnnotatedString(lua)).text,
                    dracula.text
                )
            }
        }
    }

    /** A big script is coloured without bold and italic, which make its layout far slower; a usual one keeps them. */
    @Test
    fun aBigScriptIsColouredOnly() {
        fun fonts(code: String) =
            SyntaxHighlighter(Syntax.LUA).filter(AnnotatedString(code)).text.spanStyles
                .count { it.item.fontWeight != null || it.item.fontStyle != null }
        assertTrue(fonts(lua) > 0)
        val big = buildString { while (length <= 8_000) append(lua).append('\n') }
        assertEquals(0, fonts(big))
        // The colours stay.
        assertTrue(SyntaxHighlighter(Syntax.LUA).filter(AnnotatedString(big)).text.spanStyles.size > 100)
    }

    /** The kept tokens hold a bounded amount of text, not a number of lines: a long script stays whole. */
    @Test
    fun keptTokensAreBoundedByText() {
        val tokens = LineTokens(Syntax.LUA)
        // More lines than the 8,000 once kept, all of them kept.
        val short = (0 until 9_000).map { "local x$it = $it" }
        short.forEach { tokens.of(it) }
        assertEquals(short.sumOf { it.length }.toLong(), tokens.keptChars)
        // Copies of a long line typed on, and very long lines: no more than the cap.
        repeat(3_000) { tokens.of("local s = '" + "a".repeat(1_500) + it + "'") }
        repeat(500) { tokens.of("-- " + "b".repeat(5_000) + it) }
        assertTrue(tokens.keptChars <= LineTokens.KEPT_CHARS + LineTokens.KEPT_LONG_CHARS)
        // A line let go is tokenised again alike.
        assertTrue(LineTokens(Syntax.LUA).of(short[0]).contentEquals(tokens.of(short[0])))
    }

    /** A window colours its lines as the whole script would, and no others. */
    @Test
    fun aWindowColoursOnlyItsLines() {
        for ((lang, code) in listOf(
            Syntax.LUA to lua,
            Syntax.JS to "$js\nconst y = `x\ny`;\n// end"
        )) {
            val starts = listOf(0) + code.indices.filter { code[it] == '\n' }.map { it + 1 }
            fun lineOf(offset: Int) = starts.indexOfLast { it <= offset }
            val whole = SyntaxHighlighter(lang).filter(AnnotatedString(code)).text.spanStyles
            for (window in listOf(0..0, 1..2, 2..100, 50..60)) {
                val part = SyntaxHighlighter(
                    lang,
                    window = window
                ).filter(AnnotatedString(code)).text.spanStyles
                // Every colour in the window, as coloured whole; none outside it.
                val expected =
                    whole.filter { r -> (lineOf(r.start)..lineOf((r.end - 1).coerceAtLeast(r.start))).any { it in window } }
                assertEquals(
                    "$lang $window",
                    expected.map { Triple(it.start, it.end, it.item) }.toSet(),
                    part.map { Triple(it.start, it.end, it.item) }.toSet()
                )
            }
        }
        assertNotEquals(
            SyntaxHighlighter(Syntax.LUA, window = 0..10),
            SyntaxHighlighter(Syntax.LUA, window = 5..15)
        )
    }

    @Test
    fun findMatchesAreEqualByWhatTheyMark() {
        assertEquals(
            MatchHighlights(intArrayOf(1, 5), 2, 5),
            MatchHighlights(intArrayOf(1, 5), 2, 5)
        )
        assertNotEquals(
            MatchHighlights(intArrayOf(1, 5), 2, 5),
            MatchHighlights(intArrayOf(1, 5), 2, 1)
        )
    }

    /** Two languages' highlighters are not the same. */
    @Test
    fun eachLanguageHasItsOwnHighlighter() {
        assertNotEquals(SyntaxHighlighter(Syntax.LUA), SyntaxHighlighter(Syntax.JS))
        assertEquals(SyntaxHighlighter(Syntax.LUA), SyntaxHighlighter(Syntax.LUA))
    }

    /** A very long line (minified JSON) has no colours; the lines around it keep theirs. */
    @Test
    fun aVeryLongLineIsNotColoured() {
        val long = "\"b\": [" + (1..2_000).joinToString(",") { "\"k$it\"" } + "],"
        assertTrue(long.length > SyntaxHighlighter.MAX_COLOURED_LINE)
        val code = "{\"a\": 1,\n$long\n\"c\": 2}"
        val spans = SyntaxHighlighter(Syntax.JSON).filter(AnnotatedString(code)).text.spanStyles
        val from = code.indexOf(long)
        val to = from + long.length
        assertTrue("the first line is coloured", spans.any { it.end <= from })
        assertTrue("the last line is coloured", spans.any { it.start > to })
        assertTrue("the long line is not", spans.none { it.start in from until to })
    }

    /** JSON: keys in the keyword colour, string values in the string colour, other values in the number colour. */
    @Test
    fun jsonColoursKeysAndValues() {
        val line =
            """  "name" : "Tea \"hot\"", "n": -1.5e3, "ok": true, "none": null, "list": ["a", 2]"""
        val t = LineTokens(Syntax.JSON).of(line)
        val got = (t.indices step 3).map { line.substring(t[it], t[it + 1]) to t[it + 2] }
        assertEquals(
            listOf(
                "\"name\"" to LineTokens.KEYWORD,
                "\"Tea \\\"hot\\\"\"" to LineTokens.STRING,
                "\"n\"" to LineTokens.KEYWORD,
                "-1.5e3" to LineTokens.NUMBER,
                "\"ok\"" to LineTokens.KEYWORD,
                "true" to LineTokens.NUMBER,
                "\"none\"" to LineTokens.KEYWORD,
                "null" to LineTokens.NUMBER,
                "\"list\"" to LineTokens.KEYWORD,
                "\"a\"" to LineTokens.STRING,
                "2" to LineTokens.NUMBER,
            ),
            got,
        )
        // Backticks mean nothing in JSON: no template string is coloured across lines.
        val code = "{\n  \"a\": \"`x\",\n  \"b\": 1,\n  \"c\": \"y`\"\n}"
        val spans = SyntaxHighlighter(Syntax.JSON).filter(AnnotatedString(code)).text.spanStyles
        assertTrue(spans.none { it.end - it.start > 6 })
    }
}
