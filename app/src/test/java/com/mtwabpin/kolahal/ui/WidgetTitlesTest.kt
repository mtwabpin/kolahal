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

    /** An app's widget gets its label from its app, not from here. */
    @Test fun anAppsWidgetHasNoTitleHere() {
        assertNull(sourceTitle("com.example.clock/.ClockWidget", scripts))
        assertNull(sourceTitle("script:", scripts))
        assertNull(sourceTitle("weather", scripts))
    }
}
