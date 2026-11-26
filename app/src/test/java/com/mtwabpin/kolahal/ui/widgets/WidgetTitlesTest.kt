package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.terminal.WidgetScriptReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The Widgets screen names a widget Kolahal draws itself by its script ([sourceTitle]). */
class WidgetTitlesTest {
    private fun script(name: String, code: String) = Script(
        1,
        name,
        ScriptLang.LUA,
        code,
        0,
        enabled = true,
        notify = false,
        lastRun = null,
        lastStatus = null
    )

    /** The scripts as the view model reads them from their code, by name. */
    private val scripts = WidgetScriptReader().read(
        listOf(
            script(
                "weather",
                "-- @title Weather in Paris\n-- @widget stat"
            ), script("hn", "-- @widget list")
        )
    )

    @Test
    fun aScriptsWidgetHasTheScriptsTitle() {
        assertEquals("Weather in Paris", sourceTitle("script:weather", scripts))
        // No @title: the script's name.
        assertEquals("hn", sourceTitle("script:hn", scripts))
        // The script is gone: the name the widget was placed with.
        assertEquals("old", sourceTitle("script:old", scripts))
        assertEquals("old", sourceTitle("script:old", emptyMap()))
    }

    /** A script can have several widgets. A further one has its id after the title, so they can be told apart. */
    @Test
    fun aFurtherWidgetHasItsIdAfterTheTitle() {
        assertEquals("Weather in Paris 2", sourceTitle("script:weather#2", scripts))
        assertEquals("hn 3", sourceTitle("script:hn#3", scripts))
        assertEquals("old 2", sourceTitle("script:old#2", emptyMap()))
        // An id that can't be one names no script's widget: the text as it is stored.
        assertEquals("weather#Two", sourceTitle("script:weather#Two", scripts))
    }

    /** The settings page says whose options it shows: the script's own, or one further widget's. */
    @Test
    fun theSettingsPageSaysWhoseOptionsItShows() {
        val first = com.mtwabpin.kolahal.widgets.WidgetRef("weather")
        val second = com.mtwabpin.kolahal.widgets.WidgetRef("weather", "2")
        assertEquals(
            WidgetOptionWords.SCRIPT,
            WidgetOptionWords.note(first, found = true, options = 2)
        )
        assertEquals(
            WidgetOptionWords.WIDGET,
            WidgetOptionWords.note(second, found = true, options = 2)
        )
        assertEquals(
            WidgetOptionWords.NONE,
            WidgetOptionWords.note(second, found = true, options = 0)
        )
        assertEquals(
            WidgetOptionWords.GONE,
            WidgetOptionWords.note(first, found = false, options = 0)
        )
    }

    /**
     * What a picker row says of what is placed. A built-in widget is placed once. A script's
     * widget can be placed again, up to six times.
     */
    @Test
    fun aPickerRowSaysWhatIsAdded() {
        assertNull(pickerAdded(0))
        assertEquals("Added", pickerAdded(1))
        val most = com.mtwabpin.kolahal.widgets.WidgetRef.MAX_PER_SCRIPT
        assertNull(pickerAdded(0, most))
        assertEquals("Added · tap to add another", pickerAdded(1, most))
        assertEquals("Added · tap to add another", pickerAdded(5, most))
        assertEquals("Added · 6 of 6", pickerAdded(6, most))
    }

    @Test
    fun aBuiltInWidgetHasANameToo() {
        assertEquals("Battery and network", sourceTitle("builtin:battery", scripts))
        assertEquals("Todos", sourceTitle("builtin:todos", scripts))
        // A script named like a built-in widget does not name it. One this version does not know has the name in its source.
        assertEquals("hn", sourceTitle("builtin:hn", scripts))
    }

    /**
     * The picker's Kolahal section: a row for each script with a `@widget` line.
     * The rows are made from the scripts as the view model reads them ([WidgetScriptReader]), off the main thread.
     */
    @Test
    fun thePickerListsTheScriptsThatHaveAWidget() {
        val rows = pickerScriptRows(
            WidgetScriptReader().read(
                listOf(
                    script("zeta", "-- @title Air\n-- @widget progress 1x1\nwidget.show{}"),
                    script("plain", "log('hi')"),
                    script(
                        "weather",
                        "-- @title Weather\n-- @widget stat\nstatus('23°')\nwidget.show{}"
                    ),
                    script("cricket", "status('NZ 136/8')"),
                    script("odd", "-- @widget hologram"),
                )
            ).values
        )
        // A row knows its script by name. The picker looks the script up when the row is tapped.
        assertEquals(listOf("zeta", "weather"), rows.map { it.script })
        // By title.
        assertEquals(listOf("script:zeta", "script:weather"), rows.map { it.source })
        assertEquals(listOf("Air", "Weather"), rows.map { it.title })
        assertEquals(listOf("Progress", "Stat"), rows.map { it.what })
        assertEquals(listOf("1x1", "2x1"), rows.map { it.size.id })
        // The search finds a row by its title, its script's name or its kind.
        assertEquals(
            listOf("script:zeta"),
            rows.filter { it.search.contains("zeta") }.map { it.source })
        assertEquals(
            listOf("script:weather"),
            rows.filter { it.search.contains("stat ") || it.search.endsWith("stat") }
                .map { it.source })
        assertEquals(emptyList<String>(), pickerScriptRows(emptyList()).map { it.source })
    }

    /**
     * A status line is no widget: it shows at the top of home. So a script that sets one has no row
     * for it, and one with only a status line has no row at all. Status cards are gone since 0.5.9.
     */
    @Test
    fun thePickerOffersNoStatusCard() {
        val rows = pickerScriptRows(
            WidgetScriptReader().read(
                listOf(
                    script("cricket", "status('NZ 136/8', { progress = 0.6, refresh = 30 })"),
                    script(
                        "weather",
                        "-- @title Weather\n-- @widget stat\nstatus('23°')\nwidget.show{}"
                    ),
                )
            ).values
        )
        assertEquals(listOf("script:weather"), rows.map { it.source })
        assertEquals(
            emptyList<String>(),
            rows.filter { it.search.contains("status") }.map { it.source })
    }

    /** An app's widget gets its label from its app, not from here. */
    @Test
    fun anAppsWidgetHasNoTitleHere() {
        assertNull(sourceTitle("com.example.clock/.ClockWidget", scripts))
        assertNull(sourceTitle("script:", scripts))
        assertNull(sourceTitle("weather", scripts))
        // A Status card, from before 0.5.9, is no widget Kolahal draws.
        assertNull(sourceTitle("status:weather", scripts))
    }

    /** The Agenda's days on its settings page: each of widgets.agenda-days, large, with a line under it and words for TalkBack. */
    @Test
    fun theAgendasDaysSayWhatTheyShow() {
        val choices = com.mtwabpin.kolahal.data.Keys.AGENDA_WIDGET_DAYS.choices
        assertEquals(listOf("Auto", "1", "3", "7", "14"), choices.map(WidgetSettingsWords::daysMark))
        assertEquals(listOf("As many as fit", "Today", "3 days", "A week", "Two weeks"), choices.map(WidgetSettingsWords::daysLine))
        assertEquals(
            listOf("Auto: as many days as fit", "1 day: today", "3 days", "7 days: a week", "14 days: two weeks"),
            choices.map(WidgetSettingsWords::daysSaid)
        )
    }
}
