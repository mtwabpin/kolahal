package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.workDataOf
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.widgets.TestScriptWidgets
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetJson
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetState
import com.mtwabpin.kolahal.widgets.WidgetStates
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `widget.show` and `widget.clear`, from the script's side: what is kept for its widget and when,
 * a wrong description, code typed at the prompt, and both languages.
 */
@RunWith(AndroidJUnit4::class)
class WidgetShowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scripts = ScriptRepository.get(context)
    private val logs = LogRepository.get(context)
    private val t = TestScriptWidgets("wshow")
    @get:Rule val logLines = LogLinesSince()

    @After fun tearDown() {
        t.close()
        // Code typed at the prompt is named by its language.
        for (lang in ScriptLang.entries) WidgetStates.forget(context, lang.ext)
    }

    private val sunny = "-- @widget stat\nwidget.show{ value = 24, unit = '°C', label = 'Sunny' }"
    private val sunnySpec = WidgetSpec.Stat(WidgetBase(), "24", label = "Sunny", unit = "°C")
    private val nothing = WidgetState(null, false)

    private fun script(code: String = sunny, suffix: String = "", lang: ScriptLang = ScriptLang.LUA) = t.create(suffix, code, lang)

    /** A run's host for [s], as a run makes it: with its access as stored now, and its `@widget` line. */
    private fun host(s: Script): HostApi {
        val now = scripts.byId(s.id)!!
        return HostApi(context, now.name, scriptId = now.id, notifyEnabled = false, grants = now.grants, widget = ScriptMeta.widget(now.code))
    }

    private fun stored(s: Script) = t.stored(s.name)

    private fun stat(s: Script) = stored(s).spec as WidgetSpec.Stat

    /** The errors logged since the test began. */
    private fun errors() = logs.recent(400, afterId = logLines.start).filter { it.kind == LogKind.ERROR }.map { it.text }

    private fun refused(what: String, block: () -> Unit) {
        try {
            block()
            fail("expected a refusal: $what")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().startsWith("widget") && e.message.orEmpty().contains(what))
        }
    }

    /** Until its widget is on the widgets page, a run's description is checked and not kept, and the call says false. */
    @Test fun nothingIsKeptUntilTheWidgetIsPlaced() {
        val s = script()
        assertFalse(s.grants.allows(ScriptAccess.WIDGET))
        t.run(s)
        assertEquals(nothing, stored(s))
        assertTrue(scripts.byId(s.id)!!.lastStatus.orEmpty(), scripts.byId(s.id)!!.lastStatus.orEmpty().startsWith("ok"))
        assertFalse(host(s).widgetShow(mapOf("value" to 24)))
        host(s).widgetClear()
        assertEquals(nothing, stored(s))
        assertNull(KvStore(context, Stores.WIDGET).get(s.name))
    }

    /** Placed, the run that placing asks for fills it, and so does every run after. */
    @Test fun placedItKeepsWhatTheScriptShows() {
        val began = System.currentTimeMillis()
        val s = t.place(script())
        assertTrue(s.grants.allows(ScriptAccess.WIDGET))
        // The run in the :script process found the access on, and noted that it ended.
        val first = stored(s)
        assertEquals(sunnySpec, first.spec)
        assertTrue(first.hasContent)
        assertTrue("set at ${first.setAt}", first.setAt >= began)
        assertTrue("tried at ${first.triedAt}, set at ${first.setAt}", first.triedAt >= first.setAt)
        assertNull(first.error)

        // The call says true, and the new description takes the old one's place.
        assertTrue(host(s).widgetShow(mapOf("value" to "25", "label" to "Cloudy")))
        val cloudy = WidgetSpec.Stat(WidgetBase(), "25", label = "Cloudy")
        assertEquals(cloudy, stored(s).spec)
        assertEquals(WidgetJson.encode(cloudy), KvStore(context, Stores.WIDGET).get(s.name))
        // A run on its timer shows the script's own again.
        t.run(s)
        assertEquals(sunnySpec, stored(s).spec)
    }

    /** `widget.clear` takes the content away. Its time stays, so the card says "Nothing to show" and not that it loads. */
    @Test fun clearEmptiesIt() {
        val s = t.place(script())
        val h = host(s)
        assertTrue(h.widgetShow(mapOf("value" to 7)))
        val shown = stored(s)
        assertEquals("7", stat(s).value)
        h.widgetClear()
        val cleared = stored(s)
        assertFalse(cleared.hasContent)
        assertNull(cleared.spec)
        assertNull(KvStore(context, Stores.WIDGET).get(s.name))
        assertEquals(shown.setAt, cleared.setAt)
    }

    /** A description that is wrong is the script's own error, and names the field. Nothing of it is kept. */
    @Test fun aWrongDescriptionIsTheScriptsError() {
        // Not placed: it is checked all the same.
        val s = script("-- @widget stat\nwidget.show{ value = 24, colour = 'red' }")
        t.run(s)
        val error = errors().single()
        assertTrue(error, error.startsWith("${s.name}: ") && error.contains("widget: a stat widget has no field 'colour'; use kind, title, icon, color"))
        val status = scripts.byId(s.id)!!.lastStatus.orEmpty()
        assertTrue(status, status.startsWith("error: ") && status.contains("a stat widget has no field 'colour'"))
        assertEquals(nothing, stored(s))

        val h = host(s)
        refused("value is needed: the big value") { h.widgetShow(mapOf("label" to "Sunny")) }
        refused("value must be text, not a table") { h.widgetShow(mapOf("value" to mapOf("now" to 24))) }
        refused("trend must be up, down or flat, not 'sideways'") { h.widgetShow(mapOf("value" to 24, "trend" to "sideways")) }
        refused("no colour 'red'") { h.widgetShow(mapOf("value" to 24, "color" to "red")) }
        refused("item 2 of more: value is needed") { h.widgetShow(mapOf("value" to 24, "more" to listOf(mapOf("value" to 1), mapOf("label" to "humidity")))) }
        refused("tap: give args or url, not both") { h.widgetShow(mapOf("value" to 24, "tap" to mapOf("args" to "more", "url" to "https://example.com"))) }
        refused("no kind 'gauge'") { h.widgetShow(mapOf("kind" to "gauge", "value" to 24)) }
        refused("widget.show expects a description") { h.widgetShow(null) }
        assertEquals(nothing, stored(s))
    }

    /** Placed, a wrong description leaves what the widget showed, and the page's run leaves its error for the card. */
    @Test fun aWrongDescriptionKeepsWhatWasShown() {
        val s = t.place(script("// @widget stat\nwidget.show({ value: 5 });", lang = ScriptLang.JS))
        assertEquals("5", stat(s).value)
        scripts.saveCode(s.id, "// @widget stat\nwidget.show({ value: 6, trend: \"sideways\" });")
        t.run(s)
        val error = errors().single()
        assertTrue(error, error.startsWith("${s.name}: ") && error.contains("widget: trend must be up, down or flat, not 'sideways'"))
        assertEquals("5", stat(s).value)
        assertNull(stored(s).error)

        // The widgets page asks for a run: the card then has the error and when it came.
        val before = System.currentTimeMillis()
        t.run(s, widget = true)
        val failed = stored(s)
        assertEquals("5", stat(s).value)
        assertTrue(failed.error.orEmpty(), failed.error.orEmpty().contains("trend must be up, down or flat"))
        assertTrue(failed.triedAt >= before)
        // A run that works forgets it.
        scripts.saveCode(s.id, "// @widget stat\nwidget.show({ value: 6 });")
        t.run(s, widget = true)
        assertEquals("6", stat(s).value)
        assertNull(stored(s).error)
    }

    /** Code typed at the prompt has no script to keep a widget for: it gets a line that says so. */
    @Test fun codeTypedAtThePromptGetsALine() {
        val typed = mapOf(ScriptLang.LUA to "widget.show{ value = 1 }\nwidget.clear()", ScriptLang.JS to "widget.show({ value: 1 });\nwidget.clear();")
        for ((lang, code) in typed) {
            val from = logs.maxId()
            runBlocking { ScriptRun.run(context, workDataOf(ScriptWorker.KEY_LANG to lang.ext, ScriptWorker.KEY_CODE to code)) }
            val said = logs.recent(400, afterId = from).map { it.text }
            for (call in listOf("widget.show", "widget.clear")) {
                assertTrue("$lang $call: $said", said.any { it.contains("$call() only works in saved scripts") })
            }
            assertTrue("$lang: $said", logs.recent(400, afterId = from).none { it.kind == LogKind.ERROR })
            assertEquals(nothing, t.stored(lang.ext))
        }
    }

    /** Lua's table and JavaScript's object both arrive as the description, lists inside them too, and both clear it. */
    @Test fun bothLanguagesFillIt() {
        val spec = sunnySpec.copy(more = listOf(WidgetSpec.Small("68%", "humidity"), WidgetSpec.Small("12", "km/h")), progress = 0.5f)
        val lua = t.place(
            script("-- @widget stat\nwidget.show{ value = 24, unit = '°C', label = 'Sunny', progress = 0.5,\n  more = { { value = '68%', label = 'humidity' }, { value = 12, label = 'km/h' } } }", "-lua"),
        )
        t.run(lua)
        assertEquals(spec, stored(lua).spec)

        val js = t.place(
            script(
                "// @widget stat\nwidget.show({ value: 24, unit: \"°C\", label: \"Sunny\", progress: 0.5,\n  more: [{ value: \"68%\", label: \"humidity\" }, { value: 12, label: \"km/h\" }] });",
                "-js", ScriptLang.JS,
            ),
        )
        t.run(js)
        assertEquals(spec, stored(js).spec)

        scripts.saveCode(lua.id, "-- @widget stat\nwidget.clear()")
        t.run(lua)
        assertFalse(stored(lua).hasContent)
        scripts.saveCode(js.id, "// @widget stat\nwidget.clear();")
        t.run(js)
        assertFalse(stored(js).hasContent)
        assertTrue(errors().toString(), errors().isEmpty())
    }

    /** The `@widget` line gives the kind, a description's own `kind` wins, and without the line the call is an error that says what to add. */
    @Test fun theWidgetLineGivesTheKind() {
        val s = t.place(script("-- @widget text 4x2\nwidget.show{ title = 'Quote', text = 'Simple is better.', caption = 'Tim Peters' }"))
        t.run(s)
        assertEquals(WidgetSpec.Text(WidgetBase(title = "Quote"), "Simple is better.", "Tim Peters"), stored(s).spec)
        assertTrue(host(s).widgetShow(mapOf("kind" to "progress", "value" to 0.5)))
        assertEquals(WidgetSpec.Progress(WidgetBase(), 0.5f), stored(s).spec)

        val plain = script("widget.show{ value = 1 }", "-plain")
        for (call in listOf<(HostApi) -> Unit>({ it.widgetShow(mapOf("value" to 1)) }, { it.widgetClear() })) {
            try {
                call(host(plain))
                fail("expected an error: the script has no @widget line")
            } catch (e: IllegalStateException) {
                assertTrue(e.message.orEmpty(), e.message.orEmpty().endsWith("needs `@widget <kind>` at the top of the script"))
            }
        }
        assertEquals(nothing, stored(plain))
    }

    /** One run's first twenty calls count. A loop of them changes nothing after that. */
    @Test fun aRunsCallsAreCapped() {
        val s = t.place(script())
        val h = host(s)
        for (i in 1..HostApi.MAX_WIDGET_PER_RUN + 5) assertTrue(h.widgetShow(mapOf("value" to i)))
        assertEquals("${HostApi.MAX_WIDGET_PER_RUN}", stat(s).value)
        h.widgetClear()
        assertTrue(stored(s).hasContent)
    }

    /**
     * A run tells the cards once, when it ends, whatever it changed: not once for each call and once
     * more for its end. A run that changes nothing of the widget tells nobody.
     */
    @Test fun aRunTellsTheCardsOnce() {
        val calls = "widget.show{ value = 1 }\nwidget.clear()\nwidget.show{ value = 2 }"
        val s = script("-- @widget stat\n$calls", "-once")
        // Its access goes on without placing the widget. Placing queues a run in the other process,
        // and that run would tell the cards too, at a moment of its own.
        scripts.setAccess(s.id, ScriptAccess.WIDGET, true)
        fun told() = WidgetStates.versions.value[s.name] ?: 0

        // A widget's run: three calls and the note of its end.
        var before = told()
        t.run(s, widget = true)
        assertEquals(before + 1, told())
        assertEquals("2", stat(s).value)
        assertTrue(stored(s).triedAt > 0)

        // A run on its timer: the calls only.
        before = told()
        t.run(s)
        assertEquals(before + 1, told())

        // Nothing stored, nothing told.
        scripts.saveCode(s.id, "-- @widget stat\nreturn 1", byUser = true)
        before = told()
        t.run(s)
        assertEquals(before, told())
        // A widget's run that stores nothing still notes that it ended.
        t.run(s, widget = true)
        assertEquals(before + 1, told())

        // A run that fails after it showed something: once, with its error.
        scripts.saveCode(s.id, "-- @widget stat\nwidget.show{ value = 3 }\nerror('no network')", byUser = true)
        before = told()
        t.run(s, widget = true)
        assertEquals(before + 1, told())
        assertEquals("3", stat(s).value)
        assertTrue(stored(s).error.orEmpty(), stored(s).error.orEmpty().contains("no network"))
    }
}
