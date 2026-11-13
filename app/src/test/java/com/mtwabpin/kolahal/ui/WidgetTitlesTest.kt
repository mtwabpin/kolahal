package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Widgets screen names a widget Kolahal draws itself by its script ([sourceTitle]). */
class WidgetTitlesTest {
    private fun script(name: String, code: String) = Script(1, name, ScriptLang.LUA, code, 0, enabled = true, notify = false, lastRun = null, lastStatus = null)

    private val scripts = listOf(script("weather", "-- @title Weather in Paris\n-- @widget stat"), script("hn", "-- @widget list"))

    @Test fun aScriptsWidgetHasTheScriptsTitle() {
        assertEquals("Weather in Paris", sourceTitle("script:weather", scripts))
        // No @title: the script's name.
        assertEquals("hn", sourceTitle("script:hn", scripts))
        // The script is gone: the name the widget was placed with.
        assertEquals("old", sourceTitle("script:old", scripts))
        assertEquals("old", sourceTitle("script:old", emptyList()))
    }

    @Test fun aStatusCardAndABuiltInWidgetHaveNamesToo() {
        assertEquals("Weather in Paris status", sourceTitle("status:weather", scripts))
        assertEquals("hn status", sourceTitle("status:hn", scripts))
        assertEquals("battery", sourceTitle("builtin:battery", scripts))
        // A script named like a built-in widget does not name it.
        assertEquals("hn", sourceTitle("builtin:hn", scripts))
    }

    /** The picker's Kolahal section: a row for each script with a `@widget` line, and a Status card for each that sets a status line. */
    @Test fun thePickerListsTheScriptsThatHaveAWidget() {
        val rows = pickerScriptRows(
            listOf(
                script("zeta", "-- @title Air\n-- @widget progress 1x1\nwidget.show{}"),
                script("plain", "log('hi')"),
                script("weather", "-- @title Weather\n-- @widget stat\nstatus('23°')\nwidget.show{}"),
                script("cricket", "status('NZ 136/8')"),
                script("odd", "-- @widget hologram"),
            )
        )
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
