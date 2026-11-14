package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.terminal.WidgetScriptReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Widgets screen names a widget Kolahal draws itself by its script ([sourceTitle]). */
class WidgetTitlesTest {
    private fun script(name: String, code: String) = Script(1, name, ScriptLang.LUA, code, 0, enabled = true, notify = false, lastRun = null, lastStatus = null)

    /** The scripts as the view model reads them from their code, by name. */
    private val scripts = WidgetScriptReader().read(listOf(script("weather", "-- @title Weather in Paris\n-- @widget stat"), script("hn", "-- @widget list")))

    @Test fun aScriptsWidgetHasTheScriptsTitle() {
        assertEquals("Weather in Paris", sourceTitle("script:weather", scripts))
        // No @title: the script's name.
        assertEquals("hn", sourceTitle("script:hn", scripts))
        // The script is gone: the name the widget was placed with.
        assertEquals("old", sourceTitle("script:old", scripts))
        assertEquals("old", sourceTitle("script:old", emptyMap()))
    }

    /** A script can have several widgets. A further one has its id after the title, so they can be told apart. */
    @Test fun aFurtherWidgetHasItsIdAfterTheTitle() {
        assertEquals("Weather in Paris 2", sourceTitle("script:weather#2", scripts))
        assertEquals("hn 3", sourceTitle("script:hn#3", scripts))
        assertEquals("old 2", sourceTitle("script:old#2", emptyMap()))
        // An id that can't be one names no script's widget: the text as it is stored.
        assertEquals("weather#Two", sourceTitle("script:weather#Two", scripts))
        // A Status card has no id.
        assertEquals("weather#2 status", sourceTitle("status:weather#2", scripts))
    }

    /** The options sheet says whose options it shows: the script's own, or one further widget's. */
    @Test fun theOptionsSheetSaysWhoseOptionsTheyAre() {
        val first = com.mtwabpin.kolahal.widgets.WidgetRef("weather")
        val second = com.mtwabpin.kolahal.widgets.WidgetRef("weather", "2")
        assertEquals(WidgetOptionWords.SCRIPT, WidgetOptionWords.note(first, found = true, options = 2))
        assertEquals(WidgetOptionWords.WIDGET, WidgetOptionWords.note(second, found = true, options = 2))
        assertEquals(WidgetOptionWords.NONE, WidgetOptionWords.note(second, found = true, options = 0))
        assertEquals(WidgetOptionWords.GONE, WidgetOptionWords.note(first, found = false, options = 0))
    }

    @Test fun aStatusCardAndABuiltInWidgetHaveNamesToo() {
        assertEquals("Weather in Paris status", sourceTitle("status:weather", scripts))
        assertEquals("hn status", sourceTitle("status:hn", scripts))
        assertEquals("Battery and network", sourceTitle("builtin:battery", scripts))
        assertEquals("Todos", sourceTitle("builtin:todos", scripts))
        // A script named like a built-in widget does not name it. One this version does not know has the name in its source.
        assertEquals("hn", sourceTitle("builtin:hn", scripts))
    }

    /**
     * The picker's Kolahal section: a row for each script with a `@widget` line, and a Status card for each that sets a status line.
     * The rows are made from the scripts as the view model reads them ([WidgetScriptReader]), off the main thread.
     */
    @Test fun thePickerListsTheScriptsThatHaveAWidget() {
        val rows = pickerScriptRows(
            WidgetScriptReader().read(
                listOf(
                    script("zeta", "-- @title Air\n-- @widget progress 1x1\nwidget.show{}"),
                    script("plain", "log('hi')"),
                    script("weather", "-- @title Weather\n-- @widget stat\nstatus('23°')\nwidget.show{}"),
                    script("cricket", "status('NZ 136/8')"),
                    script("odd", "-- @widget hologram"),
                )
            ).values
        )
        // A row knows its script by name. The picker looks the script up when the row is tapped.
        assertEquals(listOf("zeta", "cricket", "weather", "weather"), rows.map { it.script })
        // By title. A script with both has two rows, its own widget first.
        assertEquals(listOf("script:zeta", "status:cricket", "script:weather", "status:weather"), rows.map { it.source })
        assertEquals(listOf("Air", "cricket", "Weather", "Weather"), rows.map { it.title })
        assertEquals(listOf("Progress", "Status card", "Stat", "Status card"), rows.map { it.what })
        assertEquals(listOf("1x1", "2x1", "2x1", "2x1"), rows.map { it.size.id })
        assertEquals(listOf(false, true, false, true), rows.map { it.isStatus })
        assertEquals(listOf("Air", "cricket status", "Weather", "Weather status"), rows.map { it.name })
        // The search finds a row by its title, its script's name or its kind.
        assertEquals(listOf("script:zeta"), rows.filter { it.search.contains("zeta") }.map { it.source })
        assertEquals(listOf("script:weather"), rows.filter { it.search.contains("stat ") || it.search.endsWith("stat") }.map { it.source })
        assertEquals(listOf("status:cricket", "status:weather"), rows.filter { it.search.contains("status card") }.map { it.source })
        assertEquals(emptyList<String>(), pickerScriptRows(emptyList()).map { it.source })
    }

    /** An app's widget gets its label from its app, not from here. */
    @Test fun anAppsWidgetHasNoTitleHere() {
        assertNull(sourceTitle("com.example.clock/.ClockWidget", scripts))
        assertNull(sourceTitle("script:", scripts))
        assertNull(sourceTitle("weather", scripts))
    }
}
