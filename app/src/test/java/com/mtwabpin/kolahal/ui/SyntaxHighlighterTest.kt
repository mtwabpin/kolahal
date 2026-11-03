package com.mtwabpin.kolahal.ui

import androidx.compose.ui.text.AnnotatedString
import com.mtwabpin.kolahal.data.ScriptLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
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

    @Test fun findMatchesAreEqualByWhatTheyMark() {
        assertEquals(MatchHighlights(intArrayOf(1, 5), 2, 5), MatchHighlights(intArrayOf(1, 5), 2, 5))
        assertEquals(
            SyntaxHighlighter(ScriptLang.LUA, null, MatchHighlights(intArrayOf(1, 5), 2, 5)),
            SyntaxHighlighter(ScriptLang.LUA, null, MatchHighlights(intArrayOf(1, 5), 2, 5)),
        )
        assert(MatchHighlights(intArrayOf(1, 5), 2, 5) != MatchHighlights(intArrayOf(1, 5), 2, 1))
    }
}
