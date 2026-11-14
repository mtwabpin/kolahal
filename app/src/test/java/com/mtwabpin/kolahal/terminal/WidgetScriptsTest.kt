package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The scripts as the widget screens see them ([WidgetScriptReader]): read from the code once, and again only when the code changes. */
class WidgetScriptsTest {
    private fun script(name: String, code: String, enabled: Boolean = true, lastRun: Long? = null) =
        Script(1, name, ScriptLang.LUA, code, 0, enabled = enabled, notify = false, lastRun = lastRun, lastStatus = null)

    private val weather = script("weather", "-- @title Weather in Paris\n-- @widget stat 2x2\nstatus('23°')\nwidget.show{}")
    private val plain = script("plain", "log('hi')", enabled = false)

    @Test fun aScriptIsReadFromItsCode() {
        val read = WidgetScriptReader().read(listOf(weather, plain))
        assertEquals(listOf("weather", "plain"), read.keys.toList())
        assertEquals(WidgetScript("weather", "Weather in Paris", enabled = true, ScriptMeta.Widget(WidgetKind.STAT, WidgetSize.S2X2), setsStatus = true), read["weather"])
        assertEquals(WidgetScript("plain", null, enabled = false, widget = null, setsStatus = false), read["plain"])
        // A name with no script has no entry: its card says that the script was removed.
        assertNull(read["gone"])
        assertEquals(emptyMap<String, WidgetScript>(), WidgetScriptReader().read(emptyList()))
    }

    /** A script with an `@option` line has options to set for each of its widgets. The Widgets screen then offers them. */
    @Test fun aScriptWithAnOptionLineHasOptions() {
        val reader = WidgetScriptReader()
        val withOption = weather.copy(code = "-- @option city London The city to show\n" + weather.code)
        assertEquals(false, reader.read(listOf(weather))["weather"]!!.hasOptions)
        assertEquals(true, reader.read(listOf(withOption))["weather"]!!.hasOptions)
        // The line is gone again: so are the options.
        assertEquals(false, reader.read(listOf(weather))["weather"]!!.hasOptions)
    }

    /** The script list changes at every run. What the widget screens see does not, so nothing is drawn again. */
    @Test fun aRunChangesNothing() {
        val reader = WidgetScriptReader()
        val first = reader.read(listOf(weather, plain))
        // The list as it is read again after a run: new objects, with the same code.
        val second = reader.read(listOf(weather.copy(code = String(weather.code.toCharArray()), lastRun = 5L, lastStatus = "ok"), plain.copy(lastRun = 6L)))
        assertEquals(first, second)
        // The same objects: the code was not read again.
        assertSame(first["weather"], second["weather"])
        assertSame(first["plain"], second["plain"])
    }

    @Test fun aSwitchAndNewCodeAreSeen() {
        val reader = WidgetScriptReader()
        val first = reader.read(listOf(weather, plain))
        val off = reader.read(listOf(weather.copy(enabled = false), plain))
        assertEquals(first["weather"]!!.copy(enabled = false), off["weather"])
        assertSame(first["plain"], off["plain"])
        // The `@widget` line is gone from the code, and the title changed.
        val edited = reader.read(listOf(weather.copy(code = "-- @title Rain\nstatus('wet')"), plain))
        assertEquals(WidgetScript("weather", "Rain", enabled = true, widget = null, setsStatus = true), edited["weather"])
        assertNotSame(first["weather"], edited["weather"])
        // A deleted script leaves no entry, and one that takes its name later is read fresh.
        assertEquals(listOf("plain"), reader.read(listOf(plain)).keys.toList())
        assertEquals("Weather in Paris", reader.read(listOf(weather, plain))["weather"]!!.title)
    }
}
