package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.widgets.WidgetRef
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A script's option values, and those of one of its further widgets ([ScriptOptions]). A further
 * widget follows the script's values until you set one for that widget.
 */
class ScriptOptionsTest {
    private val city = ScriptMeta.Option("city", "London", emptyList(), "The city to show")
    private val units = ScriptMeta.Option("units", "metric", listOf("metric", "imperial"), "")
    private val declared = listOf(city, units)

    @Test fun anOptionNobodySetReadsAsItsDefault() {
        assertEquals(mapOf("city" to "London", "units" to "metric"), ScriptOptions.values(declared, emptyMap()))
        assertEquals(emptySet<String>(), ScriptOptions.changed(declared, emptyMap()))
    }

    @Test fun theScriptsValueWinsOverTheDefault() {
        val own = mapOf("city" to "Paris", "units" to "imperial", "gone" to "x")
        assertEquals(mapOf("city" to "Paris", "units" to "imperial"), ScriptOptions.values(declared, own))
        // Only what the code still declares counts as set.
        assertEquals(setOf("city", "units"), ScriptOptions.changed(declared, own))
        // A choice the code no longer offers counts as not set.
        assertEquals("metric", ScriptOptions.values(declared, mapOf("units" to "kelvin"))["units"])
        assertEquals(emptySet<String>(), ScriptOptions.changed(declared, mapOf("units" to "kelvin")))
        // An empty text is a value.
        assertEquals("", ScriptOptions.values(declared, mapOf("city" to ""))["city"])
    }

    /** The widget's value, else the script's, else the default. */
    @Test fun aWidgetsValueWinsOverTheScripts() {
        val own = mapOf("city" to "Paris")
        assertEquals(mapOf("city" to "Tokyo", "units" to "metric"), ScriptOptions.values(declared, own, mapOf("city" to "Tokyo")))
        // What the widget has not set follows the script.
        assertEquals(mapOf("city" to "Paris", "units" to "imperial"), ScriptOptions.values(declared, own, mapOf("units" to "imperial")))
        assertEquals(mapOf("city" to "Paris", "units" to "metric"), ScriptOptions.values(declared, own, emptyMap()))
        // A widget's choice that is gone falls back to the script's, not to the default.
        assertEquals("imperial", ScriptOptions.values(declared, mapOf("units" to "imperial"), mapOf("units" to "kelvin"))["units"])
        // The widget's own list of what you set has only its own values.
        assertEquals(setOf("city"), ScriptOptions.changed(declared, mapOf("city" to "Tokyo")))
    }

    /** The first widget has the script's scope, so its values are where they always were. */
    @Test fun eachWidgetHasItsOwnScope() {
        assertEquals("option:weather", ScriptOptions.scope("weather"))
        assertEquals("option:weather", ScriptOptions.scope("weather", ""))
        assertEquals("option:weather#2", ScriptOptions.scope("weather", "2"))
        assertEquals("option:weather#", ScriptOptions.furtherScopes("weather"))
    }

    /** After a value changed, the widgets that read it run again: the one it was set for, or all that follow the script. */
    @Test fun theWidgetsThatReadAChangedValueRunAgain() {
        val first = WidgetRef("weather")
        val second = WidgetRef("weather", "2")
        val third = WidgetRef("weather", "3")
        val placed = listOf(first, second, third)
        // The third widget has a value of its own for the option.
        val own = { ref: WidgetRef -> ref == third }
        // A further widget's own value: that widget only.
        assertEquals(listOf(second), ScriptOptions.readers(second, placed, own))
        assertEquals(listOf(third), ScriptOptions.readers(third, placed, own))
        // The script's own value: the first widget, and the second, which follows the script.
        assertEquals(listOf(first, second), ScriptOptions.readers(first, placed, own))
        // Only what is on the page runs.
        assertEquals(listOf(second), ScriptOptions.readers(first, listOf(second, third), own))
        assertEquals(emptyList<WidgetRef>(), ScriptOptions.readers(second, listOf(first), own))
    }
}
