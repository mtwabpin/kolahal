package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.workDataOf
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Pins
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/** `pin(text, opts)`: what an item keeps, its bounds, where it goes with its script, and refresh runs. */
@RunWith(AndroidJUnit4::class)
class PinTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scripts = ScriptRepository.get(context)
    private val store = KvStore(context, Pins.SCOPE)
    private val logs = LogRepository.get(context)
    private val before = logs.maxId()

    @After fun tearDown() {
        for (name in listOf("pinner", "pinner2")) {
            scripts.byName(name)?.let { s ->
                KvStore(context, "worker").set("refresh-error:${s.id}", null)
                scripts.delete(s.id)
            }
            Pins.clearLines(context, name)
        }
        logs.recent(200).filter { it.id > before }.forEach { logs.delete(it.id) }
    }

    private fun script(code: String = "pin('x')", lang: ScriptLang = ScriptLang.LUA) = scripts.create("pinner", lang, code)
    private fun host(s: Script) = HostApi(context, s.name, scriptId = s.id, notifyEnabled = false, grants = scripts.byId(s.id)!!.grants)
    private fun stored(name: String = "pinner") = store.get(name)?.let { Pins.decode(name, it) }

    private fun refused(what: String, block: () -> Unit) {
        try {
            block()
            fail("expected a refusal: $what")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains(what))
        }
    }

    @Test fun anItemKeepsItsFirstLineBarAndRefresh() {
        host(script()).pin("NZ 136/8 (33.2 ov) v IND\nsecond line", mapOf("progress" to 0.66, "refresh" to 30))
        val p = stored()!!
        assertEquals("NZ 136/8 (33.2 ov) v IND", p.text)
        assertEquals(0.66f, p.progress!!, 0.001f)
        assertEquals(30, p.refresh)
    }

    /** A new text keeps the item's place among the others; nil takes it out. */
    @Test fun aNewTextKeepsItsPlace() {
        val h = host(script())
        h.pin("first")
        val since = stored()!!.since
        Thread.sleep(5)
        h.pin("second")
        assertEquals("second", stored()!!.text)
        assertEquals(since, stored()!!.since)
        assertNull(stored()!!.progress)
        h.pin(null)
        assertNull(store.get("pinner"))
    }

    @Test fun progressAndRefreshStayInBounds() {
        val h = host(script())
        h.pin("x", mapOf("progress" to 1.7, "refresh" to 2))
        assertEquals(1f, stored()!!.progress)
        assertEquals(Pins.MIN_REFRESH, stored()!!.refresh)
        h.pin("x", mapOf("progress" to -1, "refresh" to 99_999))
        assertEquals(0f, stored()!!.progress)
        assertEquals(Pins.MAX_REFRESH, stored()!!.refresh)
        h.pin("x", mapOf("refresh" to 0))
        assertNull(stored()!!.refresh)
    }

    @Test fun wrongOptionsAreRefused() {
        val h = host(script())
        refused("no option colour") { h.pin("x", mapOf("colour" to "red")) }
        refused("progress is a number") { h.pin("x", mapOf("progress" to "half")) }
        refused("refresh is a number") { h.pin("x", mapOf("refresh" to "10s")) }
        assertNull(store.get("pinner"))
    }

    /** Only what changed is written, and a loop of it stops after a few. */
    @Test fun writesAreCapped() {
        val h = host(script())
        repeat(5) { h.pin("same") }
        for (i in 1..40) h.pin("n$i")
        assertEquals("n19", stored()!!.text)
    }

    @Test fun withItsHeaderAccessOffNothingIsPinned() {
        val s = script()
        scripts.setAccess(s.id, ScriptAccess.HEADER, false)
        host(s).pin("x")
        assertNull(store.get("pinner"))
    }

    @Test fun codeTypedAtThePromptCantPin() {
        HostApi(context, "lua", scriptId = null, notifyEnabled = false).pin("x")
        assertNull(store.get("lua"))
    }

    /** Lua's table and JavaScript's object both arrive as its options. */
    @Test fun bothLanguagesPin() {
        val lua = script("pin('from lua', { progress = 0.5, refresh = 10 })")
        runBlocking { ScriptRun.run(context, workDataOf(ScriptWorker.KEY_SCRIPT_ID to lua.id)) }
        assertEquals("from lua", stored()!!.text)
        assertEquals(0.5f, stored()!!.progress!!, 0.001f)
        assertEquals(10, stored()!!.refresh)
        scripts.delete(lua.id)
        Pins.clearLines(context, "pinner")

        val js = script("pin(\"from js\", { progress: 0.25 });", ScriptLang.JS)
        runBlocking { ScriptRun.run(context, workDataOf(ScriptWorker.KEY_SCRIPT_ID to js.id)) }
        assertEquals("from js", stored()!!.text)
        assertEquals(0.25f, stored()!!.progress!!, 0.001f)
        assertNull(stored()!!.refresh)
    }

    /** Renamed, the item goes with it; switched off, it goes. */
    @Test fun itGoesWithItsScript() {
        val s = script()
        host(s).pin("x")
        scripts.rename(s.id, "pinner2")
        assertNull(store.get("pinner"))
        assertNotNull(stored("pinner2"))
        scripts.setEnabled(s.id, false)
        assertNull(store.get("pinner2"))
    }

    @Test fun whatIsntAPinIsLeftOut() {
        assertNull(Pins.decode("x", "not json"))
        assertNull(Pins.decode("x", "{\"progress\":1}"))
        assertEquals(Pins.MIN_REFRESH, Pins.decode("x", "{\"text\":\"t\",\"refresh\":5}")!!.refresh)
    }

    /**
     * A refresh, every few seconds while home shows the item: an error once until a run works, no
     * line for what a run returns, and the script's row only when how it goes changes.
     */
    @Test fun aRefreshSaysWhatChangesOnce() {
        val s = script("if store.get('ok') then return 'result' end\nerror('offline', 0)")
        val refresh = workDataOf(
            ScriptWorker.KEY_SCRIPT_ID to s.id, ScriptWorker.KEY_SCHEDULED to true,
            ScriptWorker.KEY_EVENT to "{\"name\":\"${ScriptScheduler.REFRESH}\"}",
        )
        fun lines(text: String) = logs.recent(100).count { it.id > before && it.text.contains(text) }
        repeat(3) { runBlocking { ScriptRun.run(context, refresh) } }
        assertEquals(1, lines("pinner: offline"))
        assertTrue(scripts.byId(s.id)!!.lastStatus.orEmpty().startsWith("error: offline"))

        KvStore(context, ScriptRepository.scopeOf(s.id)).set("ok", "1")
        repeat(2) { runBlocking { ScriptRun.run(context, refresh) } }
        assertEquals(0, lines("result"))
        assertTrue(scripts.byId(s.id)!!.lastStatus.orEmpty().startsWith("ok"))

        // Failing again is said again.
        KvStore(context, ScriptRepository.scopeOf(s.id)).set("ok", null)
        runBlocking { ScriptRun.run(context, refresh) }
        assertEquals(2, lines("pinner: offline"))
    }
}
