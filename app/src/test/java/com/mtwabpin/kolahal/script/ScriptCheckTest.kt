package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptCheckTest {
    private fun lua(code: String) = ScriptCheck.check(code.trimIndent(), ScriptLang.LUA)

    @Test fun aNameTheApiDoesntHave() {
        assertEquals(listOf(ScriptCheck.Issue(2, "http.gte isn't in the API: http.get?")), lua("""
            local ok = 1
            local r = http.gte("https://example.com")
        """))
    }

    @Test fun linesCountWithCommentsAboveThem() {
        assertEquals(listOf(3), lua("-- @every 1h\n-- a note\nlocal r = http.gte('x')").map { it.line })
    }

    @Test fun aCallThatNeedsUses() {
        assertEquals(listOf(ScriptCheck.Issue(1, "clipboard.get needs a `-- @uses clipboard` line")), lua("log(clipboard.get())"))
        assertTrue(lua("-- @uses clipboard\nlog(clipboard.get())").isEmpty())
        assertEquals("launcher.run needs a `// @uses commands` line", ScriptCheck.check("launcher.run('torch')", ScriptLang.JS).single().text)
    }

    /** A run reads `@uses` in any case, with tabs too: so does the check. */
    @Test fun usesInCapitalsOrAfterATab() {
        assertTrue(lua("-- @uses Clipboard\tapps\nlog(clipboard.get(), apps.list())").isEmpty())
        assertEquals(setOf("clipboard", "apps"), ScriptMeta.uses("-- @uses Clipboard\tapps"))
    }

    @Test fun statusLinesItCantRead() {
        val issues = lua("""
            -- @evrey 1h
            -- @every 5x
            -- @at 25:00
            -- @on unlok
            -- @uses clipbord
        """)
        assertEquals(listOf(1, 2, 3, 4, 5), issues.map { it.line })
        assertEquals("Unknown directive @evrey: @every?", issues[0].text)
        assertTrue(issues[1].text.startsWith("@every 5x isn't an interval"))
        assertTrue(issues[2].text.startsWith("@at 25:00 isn't a time"))
        assertTrue(issues[3].text.startsWith("@on unlok isn't an event"))
        assertTrue(issues[4].text.startsWith("@uses clipbord isn't valid"))
    }

    /** A comment isn't a call, and a table of the script's own by that name is its own. */
    @Test fun notEverythingIsAnIssue() {
        assertTrue(lua("-- http.gte is a typo of mine\nlog(1)").isEmpty())
        assertTrue(lua("local store = {}\nstore.thing = 1").isEmpty())
        assertTrue(lua("-- @every 1h\n-- @at 07:00, 18:30\n-- @on unlock\nlog(http.get('x').status)").isEmpty())
    }

    /** `@widget <kind> [<size>]`: a kind or a size that doesn't exist is pointed out. */
    @Test fun aWidgetLineItCantRead() {
        assertTrue(lua("-- @widget stat\n-- @widget list 4x2").isEmpty())
        assertTrue(lua("-- @widget buttons\n-- @widget toggles 2x2\n-- @widget clock 4x1\n-- @widget agenda").isEmpty())
        // `media` is a kind only Kolahal fills, so for a script it is no kind.
        assertEquals("@widget media isn't a kind: stat, text, list, progress, chart, countdown, buttons, toggles, clock, agenda, image", lua("-- @widget media").single().text)
        assertEquals(listOf(ScriptCheck.Issue(1, "@widget graph isn't a kind: stat, text, list, progress, chart, countdown, buttons, toggles, clock, agenda, image")), lua("-- @widget graph"))
        assertEquals(listOf(ScriptCheck.Issue(1, "@widget stat 3x3 isn't a size: 1x1, 2x1, 2x2, 4x1, 4x2, 4x4")), lua("-- @widget stat 3x3"))
        assertEquals("@widget needs a kind: stat, text, list, progress, chart, countdown, buttons, toggles, clock, agenda, image", ScriptCheck.check("// @widget", ScriptLang.JS).single().text)
    }

    @Test fun theWidgetCallsAreInTheApi() {
        assertTrue(lua("-- @widget stat\nif widget then widget.show{ value = 1 } end\nwidget.clear()").isEmpty())
        assertTrue(ScriptCheck.check("// @widget text\nif (typeof widget !== \"undefined\") widget.show({ text: \"x\" });", ScriptLang.JS).isEmpty())
        assertEquals("widget.shwo isn't in the API: widget.show?", lua("widget.shwo{}").single().text)
    }
}
