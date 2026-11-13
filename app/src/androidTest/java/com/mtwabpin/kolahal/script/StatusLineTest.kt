package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.Stores
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.workDataOf
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StatusLine
import com.mtwabpin.kolahal.data.StatusLines
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
 * `status(text, opts)`: what a line keeps, its bounds, where it goes with its script, pinning, and
 * the runs a pinned line's refresh asks for.
 */
@RunWith(AndroidJUnit4::class)
class StatusLineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scripts = ScriptRepository.get(context)
    private val logs = LogRepository.get(context)
    @get:Rule val logLines = LogLinesSince()

    @After fun tearDown() {
        for (name in listOf("liner", "liner2")) {
            scripts.byName(name)?.let { s ->
                KvStore(context, "worker").set("refresh-error:${s.id}", null)
                scripts.delete(s.id)
            }
            StatusLines.clear(context, name)
        }
    }

    private fun script(code: String = "status('x')", lang: ScriptLang = ScriptLang.LUA) = scripts.create("liner", lang, code)
    private fun host(s: Script) = HostApi(context, s.name, scriptId = s.id, notifyEnabled = false, grants = scripts.byId(s.id)!!.grants)
    private fun stored(name: String = "liner"): StatusLine? = StatusLines.all(context).firstOrNull { it.name == name }

    private fun refused(what: String, block: () -> Unit) {
        try {
            block()
            fail("expected a refusal: $what")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains(what))
        }
    }

    @Test fun aLineKeepsItsFirstLineBarAndRefresh() {
        host(script()).status("NZ 136/8 (33.2 ov) v IND\nsecond line", mapOf("progress" to 0.66, "refresh" to 30))
        val l = stored()!!
        assertEquals("NZ 136/8 (33.2 ov) v IND", l.text)
        assertEquals(0.66f, l.progress!!, 0.001f)
        assertEquals(30, l.refresh)
        // The text is kept as it always was, for backups and older versions.
        assertEquals("NZ 136/8 (33.2 ov) v IND", KvStore(context, Stores.STATUS).get("liner"))
    }

    /** A new text without options drops what the last one added; nil takes it all away. */
    @Test fun eachCallSaysItAll() {
        val h = host(script())
        h.status("first", mapOf("progress" to 0.5))
        h.status("second")
        assertEquals(StatusLine("liner", "second"), stored())
        h.status(null)
        assertNull(stored())
        assertNull(KvStore(context, Stores.STATUS_EXTRAS).get("liner"))
    }

    @Test fun progressAndRefreshStayInBounds() {
        val h = host(script())
        h.status("x", mapOf("progress" to 1.7, "refresh" to 2))
        assertEquals(1f, stored()!!.progress)
        assertEquals(StatusLines.MIN_REFRESH, stored()!!.refresh)
        h.status("x", mapOf("progress" to -1, "refresh" to 99_999))
        assertEquals(0f, stored()!!.progress)
        assertEquals(StatusLines.MAX_REFRESH, stored()!!.refresh)
        h.status("x", mapOf("refresh" to 0))
        assertNull(stored()!!.refresh)
    }

    @Test fun wrongOptionsAreRefused() {
        val h = host(script())
        refused("no option colour") { h.status("x", mapOf("colour" to "red")) }
        refused("progress is a number") { h.status("x", mapOf("progress" to "half")) }
        refused("refresh is a number") { h.status("x", mapOf("refresh" to "10s")) }
        assertNull(stored())
    }

    /** Only what changed is written, the options too, and a loop of it stops after a few. */
    @Test fun writesAreCapped() {
        val h = host(script())
        repeat(5) { h.status("same", mapOf("progress" to 0.5)) }
        h.status("same", mapOf("progress" to 0.6))
        assertEquals(0.6f, stored()!!.progress!!, 0.001f)
        for (i in 1..40) h.status("n$i")
        assertEquals("n18", stored()!!.text)
    }

    /** Unpinned, a script's line is still kept: its card and `status` show it. */
    @Test fun anUnpinnedLineIsKept() {
        val s = script()
        assertFalse(s.grants.allows(ScriptAccess.HEADER))
        host(s).status("kept")
        assertEquals("kept", stored()!!.text)
    }

    /** `allowed("header")` says whether you pinned it. */
    @Test fun allowedHeaderSaysWhetherPinned() {
        val s = script("status(allowed('header') and 'pinned' or 'not')")
        runBlocking { ScriptRun.run(context, workDataOf(ScriptWorker.KEY_SCRIPT_ID to s.id)) }
        assertEquals("not", stored()!!.text)
        scripts.setAccess(s.id, ScriptAccess.HEADER, true)
        runBlocking { ScriptRun.run(context, workDataOf(ScriptWorker.KEY_SCRIPT_ID to s.id)) }
        assertEquals("pinned", stored()!!.text)
    }

    @Test fun codeTypedAtThePromptHasNoLine() {
        HostApi(context, "lua", scriptId = null, notifyEnabled = false).status("x")
        assertNull(stored("lua"))
    }

    /** Lua's table and JavaScript's object both arrive as its options. */
    @Test fun bothLanguagesSetOptions() {
        val lua = script("status('from lua', { progress = 0.5, refresh = 10 })")
        runBlocking { ScriptRun.run(context, workDataOf(ScriptWorker.KEY_SCRIPT_ID to lua.id)) }
        assertEquals(StatusLine("liner", "from lua", 0.5f, 10), stored())
        scripts.delete(lua.id)
        StatusLines.clear(context, "liner")

        val js = script("status(\"from js\", { progress: 0.25 });", ScriptLang.JS)
        runBlocking { ScriptRun.run(context, workDataOf(ScriptWorker.KEY_SCRIPT_ID to js.id)) }
        assertEquals(StatusLine("liner", "from js", 0.25f, null), stored())
    }

    /** Renamed, the line and its options go with it; switched off, they go. */
    @Test fun itGoesWithItsScript() {
        val s = script()
        host(s).status("x", mapOf("progress" to 0.5))
        scripts.rename(s.id, "liner2")
        assertNull(stored("liner"))
        assertEquals(StatusLine("liner2", "x", 0.5f), stored("liner2"))
        scripts.setEnabled(s.id, false)
        assertNull(stored("liner2"))
        assertNull(KvStore(context, Stores.STATUS_EXTRAS).get("liner2"))
    }

    /** The bar's colour is a role, as fmt.color takes it; a name that isn't one is an error. */
    @Test fun theBarTakesAColourByRole() {
        val s = script()
        host(s).status("AQI 168", mapOf("progress" to 0.56, "color" to "bad"))
        assertEquals(StatusLine("liner", "AQI 168", 0.56f, null, com.mtwabpin.kolahal.data.StyledText.Colour.BAD), stored())
        assertTrue(runCatching { host(s).status("AQI 168", mapOf("color" to "red")) }.exceptionOrNull() is IllegalArgumentException)
        assertNull(StatusLines.decodeExtras("{\"color\":\"red\"}").colour)
        assertNull(StatusLines.decodeExtras("not json").colour)
    }

    /** Code that no longer sets a status line leaves none behind: its card and the pinned header have nothing old to show. */
    @Test fun codeWithoutAStatusLineClearsTheOldOne() {
        val s = script("status('HN: 3 new')")
        host(s).status("HN: 3 new")
        scripts.saveCode(s.id, "log('no line now')")
        assertNull(stored())
    }

    @Test fun whatIsntAnOptionIsLeftOut() {
        assertEquals(StatusLines.Extras(), StatusLines.decodeExtras("not json"))
        assertEquals(StatusLines.Extras(refresh = StatusLines.MIN_REFRESH), StatusLines.decodeExtras("{\"refresh\":5}"))
        assertEquals(StatusLines.Extras(progress = 1f), StatusLines.decodeExtras("{\"progress\":3}"))
        assertNull(StatusLines.encodeExtras(null, null))
    }

    /**
     * A pinned line's refresh, every few seconds while home shows it: an error once until a run
     * works, no line for what a run returns, and the script's row only when how it goes changes.
     */
    @Test fun aRefreshSaysWhatChangesOnce() {
        val s = script("if store.get('ok') then return 'result' end\nerror('offline', 0)")
        val refresh = workDataOf(
            ScriptWorker.KEY_SCRIPT_ID to s.id, ScriptWorker.KEY_SCHEDULED to true,
            ScriptWorker.KEY_EVENT to "{\"name\":\"${ScriptScheduler.REFRESH}\"}",
        )
        fun lines(text: String) = logs.recent(100).count { it.id > logLines.start && it.text.contains(text) }
        repeat(3) { runBlocking { ScriptRun.run(context, refresh) } }
        assertEquals(1, lines("liner: offline"))
        assertTrue(scripts.byId(s.id)!!.lastStatus.orEmpty().startsWith("error: offline"))

        KvStore(context, ScriptRepository.scopeOf(s.id)).set("ok", "1")
        repeat(2) { runBlocking { ScriptRun.run(context, refresh) } }
        assertEquals(0, lines("result"))
        assertTrue(scripts.byId(s.id)!!.lastStatus.orEmpty().startsWith("ok"))

        // Failing again is said again.
        KvStore(context, ScriptRepository.scopeOf(s.id)).set("ok", null)
        runBlocking { ScriptRun.run(context, refresh) }
        assertEquals(2, lines("liner: offline"))
    }

    /** Once, on the first start of a version with pinning: the scripts whose line the header showed are pinned. */
    @Test fun theLinesTheHeaderShowedArePinnedOnce() {
        val prefs = context.getSharedPreferences("launcher", android.content.Context.MODE_PRIVATE)
        val had = prefs.getBoolean("pins_carried", false)
        try {
            val shown = script()
            val hidden = scripts.create("liner2", ScriptLang.LUA, "status('y')")
            scripts.setAccess(hidden.id, ScriptAccess.HEADER, false)
            host(shown).status("a line")
            host(hidden).status("hidden line")
            prefs.edit().putBoolean("pins_carried", false).commit()
            StatusLines.pinShownOnce(context)
            assertTrue(scripts.byId(shown.id)!!.grants.allows(ScriptAccess.HEADER))
            assertFalse(scripts.byId(hidden.id)!!.grants.allows(ScriptAccess.HEADER))
            // Once: unpinned after, it stays unpinned.
            scripts.setAccess(shown.id, ScriptAccess.HEADER, false)
            StatusLines.pinShownOnce(context)
            assertFalse(scripts.byId(shown.id)!!.grants.allows(ScriptAccess.HEADER))
        } finally {
            prefs.edit().putBoolean("pins_carried", had).commit()
        }
    }
}
