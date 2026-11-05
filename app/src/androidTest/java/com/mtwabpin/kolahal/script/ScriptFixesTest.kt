package com.mtwabpin.kolahal.script

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.SharedSettings
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Settings the script process reads, daily times, and room in a script's store. */
@RunWith(AndroidJUnit4::class)
class ScriptFixesTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val config = Config.get(app)
    private val scripts = ScriptRepository.get(app)

    @After fun tearDown() {
        config.reset(Keys.LOG_KEEP_LINES)
        // Its daily times are alarms: cancelled with it.
        scripts.byName("sftimes")?.let { com.mtwabpin.kolahal.script.ScriptScheduler.cancel(app, it.id); scripts.delete(it.id) }
    }

    /** The :script process trims the log too: it reads `log.keep-lines` where home copies it. */
    @Test fun theScriptProcessSeesKeepLines() {
        config.set(Keys.LOG_KEEP_LINES, "50000")
        waitUntil("log.keep-lines") { SharedSettings.get(app, Keys.LOG_KEEP_LINES) == 50_000 }
    }

    /** A run's lines come with no `── name · 22:40 ──` line before them: each shows its time and name. */
    @Test fun aRunLogsOnlyItsOwnLines() {
        val name = "sfrun${System.nanoTime()}"
        val host = HostApi(app, name, scriptId = 900_000_000L + System.nanoTime() % 1_000_000, notifyEnabled = false)
        kotlinx.coroutines.runBlocking { LuaEngine().run("log('a') log('b')", name, host, 5_000, false) }
        host.flush()
        val lines = com.mtwabpin.kolahal.data.LogRepository.get(app).recent(limit = 50).filter { it.source == name }
        lines.forEach { com.mtwabpin.kolahal.data.LogRepository.get(app).delete(it.id) }
        assertEquals(listOf("a", "b"), lines.map { it.text })
    }

    /** `options` holds each @option: yours where you set one (a choice it offers), else the default; it follows a rename. */
    @Test fun optionsReachTheScript() {
        val name = "sfopt${System.nanoTime() % 1_000_000}"
        val code = "-- @option city London The city\n-- @option units metric|imperial Units\nlog(options.city .. ' ' .. options.units)"
        val s = scripts.create(name, ScriptLang.LUA, code)
        val store = ScriptOptions(app)
        val declared = ScriptMeta.options(code)
        try {
            assertEquals(mapOf("city" to "London", "units" to "metric"), store.values(name, declared))
            store.set(name, "city", "New York")
            store.set(name, "units", "kelvin") // not one of its choices: the default
            assertEquals(mapOf("city" to "New York", "units" to "metric"), store.values(name, declared))
            assertEquals(setOf("city"), store.changed(name, declared))

            for (lang in listOf(ScriptLang.LUA, ScriptLang.JS)) {
                val run = "$name-${lang.ext}"
                val host = HostApi(app, run, scriptId = 900_000_000L + System.nanoTime() % 1_000_000, notifyEnabled = false, options = store.values(name, declared))
                val src = if (lang == ScriptLang.LUA) "log(options.city .. ' ' .. options.units)" else "log(options.city + ' ' + options.units)"
                kotlinx.coroutines.runBlocking { ScriptEngine.forLang(lang).run(src, run, host, 5_000, false) }
                host.flush()
                val lines = com.mtwabpin.kolahal.data.LogRepository.get(app).recent(limit = 50).filter { it.source == run }
                lines.forEach { com.mtwabpin.kolahal.data.LogRepository.get(app).delete(it.id) }
                assertEquals(lang.ext, listOf("New York metric"), lines.map { it.text })
            }

            scripts.rename(s.id, "$name-2")
            assertEquals("New York", store.values("$name-2", declared)["city"])
            assertEquals("London", store.values(name, declared)["city"])
        } finally {
            scripts.byName("$name-2")?.let { scripts.delete(it.id) }
            scripts.byName(name)?.let { scripts.delete(it.id) }
        }
        assertTrue(KvStore(app, ScriptOptions.scope("$name-2")).all().isEmpty())
    }

    /** `@every` and `@at` typed in the editor set its timers when saved; taken out, they take back what they set. */
    @Test fun timersTypedInTheEditorApply() {
        val name = "sfedit${System.nanoTime() % 1_000_000}"
        val s = scripts.create(name, ScriptLang.LUA, "log(1)")
        val vm = vms.terminal(app)
        fun save(code: String): String? {
            val done = java.util.concurrent.CompletableFuture<String?>()
            instrumentation.runOnMainSync { vm.saveScript(scripts.byId(s.id)!!, code) { _, changed -> done.complete(changed) } }
            return done.get(10, java.util.concurrent.TimeUnit.SECONDS)
        }
        try {
            assertEquals("runs every 1h · daily at 07:00", save("-- @every 1h\n-- @at 7:00\nlog(1)"))
            assertEquals(60, scripts.byId(s.id)!!.intervalMin)
            assertEquals(listOf("07:00"), scripts.byId(s.id)!!.atTimes)
            assertNull(save("-- @every 1h\n-- @at 7:00\nlog(2)"))
            assertEquals("timer off · no daily times", save("log(3)"))
            assertEquals(0, scripts.byId(s.id)!!.intervalMin)
        } finally {
            ScriptScheduler.cancel(app, s.id)
            scripts.delete(s.id)
        }
    }

    /** Closing the editor with unsaved text saves it as Save does, timers and all. */
    @Test fun closingTheEditorAppliesItsTimers() {
        val name = "sfclose${System.nanoTime() % 1_000_000}"
        val s = scripts.create(name, ScriptLang.LUA, "log(1)")
        val vm = vms.terminal(app)
        fun close(code: String) = instrumentation.runOnMainSync {
            vm.openEditorAt(scripts.byId(s.id)!!, null)
            vm.closeEditor(code)
        }
        try {
            close("-- @every 1h\n-- @at 7:00\nlog(1)")
            waitUntil("timers set") { scripts.byId(s.id)!!.intervalMin == 60 && scripts.byId(s.id)!!.atTimes == listOf("07:00") }
            waitUntil("says so") { vm.notice.value?.text == "Saved $name.lua · runs every 1h · daily at 07:00" }
            close("log(3)")
            waitUntil("timers off") { scripts.byId(s.id)!!.intervalMin == 0 && scripts.byId(s.id)!!.atTimes.isEmpty() }
        } finally {
            ScriptScheduler.cancel(app, s.id)
            scripts.delete(s.id)
        }
    }

    @Test fun dailyTimesSwitchAScriptOn() {
        val s = scripts.create("sftimes", ScriptLang.LUA, "return 1", enabled = false)
        val vm = vms.terminal(app)
        assertFalse(vm.scriptActions.setScriptTimes(s, "8:00, 25:00"))
        assertTrue(vm.scriptActions.setScriptTimes(s, "18:30, 8:00"))
        waitUntil("switched on") { scripts.byId(s.id)!!.enabled }
        assertEquals(listOf("08:00", "18:30"), scripts.byId(s.id)!!.atTimes)
    }

    /** `http.cached` makes room among its own responses when a script's store is full. */
    @Test fun cachedResponsesGoTogetherAndNothingElse() {
        val store = KvStore(app, ScriptRepository.scopeOf(900_000_000L + System.nanoTime() % 1_000_000))
        store.set("http.cache:a", "1")
        store.set("http.cache:b", "2")
        store.set("http.cachex", "3")
        store.set("seen", "4")
        store.deleteStartingWith("http.cache:", except = "http.cache:b")
        assertNull(store.get("http.cache:a"))
        assertEquals(mapOf("http.cache:b" to "2", "http.cachex" to "3", "seen" to "4"), store.all())
        store.all().keys.forEach { store.set(it, null) }
    }
}
