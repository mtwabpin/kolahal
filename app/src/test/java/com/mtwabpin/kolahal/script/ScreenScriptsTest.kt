package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.ui.editor.EditorLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A script with a screen of its own (`@uses screen`) is a command, and nothing else. Every other script is as it was. */
class ScreenScriptsTest {
    private val lines = """
        -- @every 1h
        -- @at 07:00, 18:30
        -- @on unlock, boot
        -- @widget stat 2x1
        tty.screen{ start = function(g) end }
    """.trimIndent()
    private val screen = "-- @uses screen\n$lines"

    @Test
    fun itIsACommandOnly() {
        assertTrue(ScriptMeta.isScreen(screen))
        assertTrue(ScriptMeta.isCommand(screen))
        assertTrue(ScriptMeta.parse(screen).isCommand)
        assertNull(ScriptMeta.every(screen))
        assertEquals(emptyList<String>(), ScriptMeta.at(screen))
        assertEquals(emptyList<String>(), ScriptMeta.events(screen))
        assertNull(ScriptMeta.widget(screen))
        assertEquals(listOf("screen"), ScriptAccess.items(screen))
        assertTrue(EditorLogic.offersRunWith(screen))
        assertEquals(setOf(LibraryTag.COMMAND, LibraryTag.OFFLINE), LibraryTags.of("life", screen))
        assertEquals(setOf(LibraryTag.COMMAND, LibraryTag.OFFLINE), LibraryTags.of("life", "// @uses clipboard, screen\n"))
    }

    /** The same lines without `@uses screen` mean what they always did. */
    @Test
    fun everyOtherScriptIsAsItWas() {
        assertFalse(ScriptMeta.isScreen(lines))
        assertFalse(ScriptMeta.isCommand(lines))
        assertEquals(60, ScriptMeta.every(lines))
        assertEquals(listOf("07:00", "18:30"), ScriptMeta.at(lines))
        assertEquals(listOf("unlock", "boot"), ScriptMeta.events(lines))
        assertTrue(ScriptMeta.widget(lines) != null)
        assertTrue(ScriptCheck.check(lines, ScriptLang.LUA).none { "doesn't work with" in it.text })
    }

    /** A timer it had goes when it gets a screen, and a timer line doesn't set one. */
    @Test
    fun itsTimersGo() {
        assertEquals(0, ScriptMeta.intervalAfter(lines, screen, 60))
        assertEquals(0, ScriptMeta.intervalAfter("", screen, 180))
        assertEquals(emptyList<String>(), ScriptMeta.timesAfter(lines, screen, listOf("07:00", "18:30")))
        // And come back with the code, as any new line sets them.
        assertEquals(60, ScriptMeta.intervalAfter(screen, lines, 0))
        assertEquals(listOf("07:00", "18:30"), ScriptMeta.timesAfter(screen, lines, emptyList()))
    }

    /** The editor says which lines don't work, each on its line. */
    @Test
    fun theEditorSaysWhichLinesDontWork() {
        val issues = ScriptCheck.check(screen, ScriptLang.LUA).filter { "doesn't work with @uses screen" in it.text }
        assertEquals(listOf(2, 3, 4, 5), issues.map { it.line })
        assertEquals(
            "@every doesn't work with @uses screen: a script that draws a full screen runs only when you start it",
            issues.first().text
        )
        assertEquals(listOf("@every", "@at", "@on", "@widget"), issues.map { it.text.substringBefore(' ') })
        assertEquals(setOf("every", "at", "on", "widget"), ScriptCheck.SCREEN_REFUSES)
        // JavaScript's comments too. A timer switched off is no timer, and @command says what is so anyway.
        assertTrue(ScriptCheck.check("// @uses screen\n// @at 07:00", ScriptLang.JS).any { "@at doesn't work" in it.text })
        assertEquals(
            emptyList<ScriptCheck.Issue>(),
            ScriptCheck.check("-- @command\n-- @uses screen\n-- @every off\ntty.screen{ start = function(g) end }", ScriptLang.LUA)
        )
    }

    /** A call without the line is a hint in the editor, and it doesn't make a script a command. */
    @Test
    fun theCallAloneIsNoScreen() {
        val code = "tty.screen{ start = function(g) end }"
        assertFalse(ScriptMeta.isScreen(code))
        assertFalse(ScriptMeta.isCommand(code))
        assertTrue(ScriptMeta.opensScreen(code))
        assertEquals(listOf("tty.screen needs a `-- @uses screen` line"), ScriptCheck.check(code, ScriptLang.LUA).map { it.text })
    }
}
