package com.mtwabpin.kolahal.ui.scripts

import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ScriptLibrary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The groups of the library page's list ([libraryGroups]) and the templates last in Get more scripts ([templatesLast]). */
class LibraryGroupsTest {
    private fun isTemplate(name: String) = name.startsWith("template-")

    /** Each group as its heading and its rows, for one comparison. */
    private fun shown(differ: List<String>, rest: List<String>) =
        libraryGroups(differ, rest) { isTemplate(it) }.map { Triple(it.part, it.heading, it.rows) }

    /**
     * In the gui style a library script with a screen of its own says that it is for the tty style. In the
     * tty style, and for every other script, the line above its title is as it was.
     */
    @Test
    fun aScreenScriptSaysItIsForTheTtyStyle() {
        fun row(code: String) =
            LibRow(ScriptLibrary.Entry.of("tty-matrix.lua", ScriptLang.LUA, code), ScriptLibrary.State.NOT_INSTALLED)
        val screen = row("-- @uses screen\ntty.screen{ frame = function() end }")
        val plain = row("-- @command\nlog(\"hi\")")
        assertTrue(screen.entry.screen)
        assertFalse(plain.entry.screen)
        assertEquals("tty-matrix.lua · command · TTY style only", overline(screen, tty = false))
        assertEquals("tty-matrix.lua · command", overline(screen, tty = true))
        assertEquals("tty-matrix.lua · command", overline(plain, tty = false))
    }

    /** The scripts that do something come first, in their order; the templates after them, under "Templates". */
    @Test
    fun theTemplatesComeLastUnderTheirHeading() {
        assertEquals(
            listOf(
                Triple(LibraryPart.SCRIPTS, null, listOf("aqi", "countdown", "weather")),
                Triple(LibraryPart.TEMPLATES, "Templates", listOf("template-command", "template-js")),
            ),
            shown(
                emptyList(),
                listOf("aqi", "countdown", "template-command", "template-js", "weather")
            )
        )
    }

    /** With only templates (the Template chip on), they show without a heading. */
    @Test
    fun templatesAloneHaveNoHeading() {
        assertEquals(
            listOf(Triple(LibraryPart.TEMPLATES, null, listOf("template-event", "template-js"))),
            shown(emptyList(), listOf("template-event", "template-js"))
        )
        assertEquals(
            listOf(Triple(LibraryPart.SCRIPTS, null, listOf("countdown"))),
            shown(emptyList(), listOf("countdown"))
        )
    }

    /** Your copies that differ come first, and the groups below them each get a heading. */
    @Test
    fun theCopiesThatDifferComeFirst() {
        assertEquals(
            listOf(
                Triple(LibraryPart.DIFFERS, "Your copy differs · 1", listOf("countdown")),
                Triple(LibraryPart.SCRIPTS, "All scripts", listOf("aqi")),
                Triple(LibraryPart.TEMPLATES, "Templates", listOf("template-js")),
            ),
            shown(listOf("countdown"), listOf("template-js", "aqi"))
        )
        // Only templates under them: no "All scripts" with nothing under it.
        assertEquals(
            listOf(
                Triple(LibraryPart.DIFFERS, "Your copy differs · 1", listOf("countdown")),
                Triple(LibraryPart.TEMPLATES, "Templates", listOf("template-js")),
            ),
            shown(listOf("countdown"), listOf("template-js"))
        )
        assertEquals(
            listOf(Triple(LibraryPart.DIFFERS, "Your copy differs · 1", listOf("countdown"))),
            shown(listOf("countdown"), emptyList())
        )
    }

    /** Nothing kept: no group at all. */
    @Test
    fun noRowsNoGroups() {
        assertEquals(emptyList<Any>(), shown(emptyList(), emptyList()))
    }

    /** Get more scripts: the templates go last, and each part keeps its order (here, two sources one after the other). */
    @Test
    fun templatesLastKeepsEachPartsOrder() {
        val listed = listOf(
            "hn" to "kolahal-scripts",
            "template-timer" to "kolahal-scripts",
            "weather" to "kolahal-scripts",
            "template-fetch" to "mine",
            "clock" to "mine",
        )
        assertEquals(
            listOf("hn", "weather", "clock", "template-timer", "template-fetch"),
            templatesLast(listed) { it.first }.map { it.first }
        )
        // A name that has "template" further in is no template.
        assertEquals(listOf("my-template", "template-x"), templatesLast(listOf("template-x", "my-template")) { it })
    }
}
