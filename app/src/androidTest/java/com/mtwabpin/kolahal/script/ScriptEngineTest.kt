package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScriptEngineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private fun host() = HostApi(context, "test", scriptId = null, notifyEnabled = false)

    private fun lua(code: String, repl: Boolean = true, timeout: Long = 5_000) =
        runBlocking { LuaEngine().run(code, "t", host(), timeout, repl) }

    private fun js(code: String, repl: Boolean = true, timeout: Long = 5_000) =
        runBlocking { JsEngine().run(code, "t", host(), timeout, repl) }

    @Test fun luaIsVersion54() = assertEquals("Lua 5.4", lua("_VERSION"))

    @Test fun luaExpressionAndTables() {
        assertEquals("3", lua("1 + 2"))
        val out = lua("json.decode('{\"a\":1,\"b\":[1,2,3]}')")!!
        assertEquals(mapOf("a" to 1, "b" to listOf(1, 2, 3)), Json.parse(out))
    }

    @Test fun luaSandboxRemovesDangerousGlobals() {
        assertEquals("nil\tnil\tnil\tnil\tnil", lua("java, io, debug, require, os.execute"))
    }

    @Test fun luaInfiniteLoopTimesOut() {
        val started = System.currentTimeMillis()
        try {
            lua("while true do end", repl = false, timeout = 1_000)
            fail("expected timeout")
        } catch (e: Exception) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("timed out"))
        }
        assertTrue(System.currentTimeMillis() - started < 5_000)
    }

    @Test fun luaPcallCannotSwallowTimeout() {
        val started = System.currentTimeMillis()
        try {
            lua("while true do pcall(function() while true do end end) end", repl = false, timeout = 1_000)
            fail("expected timeout")
        } catch (e: Exception) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("timed out"))
        }
        assertTrue(System.currentTimeMillis() - started < 5_000)
    }

    @Test fun luaXpcallAndResumeCannotSwallowTimeout() {
        for (code in listOf(
            "while true do xpcall(function() while true do end end, function(e) return e end) end",
            "while true do local co = coroutine.create(function() while true do end end); coroutine.resume(co) end",
        )) {
            try {
                lua(code, repl = false, timeout = 1_000)
                fail("expected timeout for: $code")
            } catch (e: Exception) {
                assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("timed out"))
            }
        }
    }

    @Test fun luaPcallStillCatchesOrdinaryErrors() {
        assertEquals("false\tboom", lua("pcall(error, 'boom', 0)"))
    }

    @Test fun luaLoadKeepsGlobals() {
        assertEquals("2", lua("load('x = 1; return x + 1')()"))
        assertEquals("nil", lua("tostring(load('\\27Lua'))"))  // binary chunks are rejected
    }

    @Test fun luaMemoryCap() {
        try {
            lua("local t = {} for i = 1, 1e8 do t[i] = ('x'):rep(64) .. i end", repl = false, timeout = 20_000)
            fail("expected memory limit")
        } catch (e: Exception) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("memory limit", ignoreCase = true))
        }
    }

    /** string.rep can't ask for gigabytes in one call. */
    @Test fun luaRepIsCapped() {
        val out = lua("pcall(string.rep, 'x', 2^30)")!!
        assertTrue(out, out.startsWith("false") && out.contains("too large"))
        assertEquals("true\t3", lua("local ok, s = pcall(string.rep, 'ab', 2, '-') return ok, #s - 2"))
    }

    /** The guards keep their own copies: a script that replaces the functions they use still can't swallow its timeout. */
    @Test fun luaShadowedGlobalsKeepTheGuards() {
        val code = """
            error = function() end
            type = function() return "table" end
            tostring = function() return "" end
            string.find = function() return nil end
            string.lower = function(s) return "" end
            table.pack = function() return { n = 0 } end
            table.unpack = function() end
            while true do pcall(function() while true do end end) end
        """.trimIndent()
        try {
            lua(code, repl = false, timeout = 1_000)
            fail("expected timeout")
        } catch (e: Exception) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("timed out"))
        }
    }

    /**
     * Waits until no script run is queued or running: a run stopped by its process going down that
     * would run again is queued for it, after WorkManager's back-off (10 s at least).
     */
    private fun scriptRunsSettle() {
        val wm = androidx.work.WorkManager.getInstance(context)
        val until = System.currentTimeMillis() + 60_000
        while (System.currentTimeMillis() < until && wm.getWorkInfosByTag(ScriptWorker::class.java.name).get().any { !it.state.isFinished }) Thread.sleep(250)
    }

    /**
     * A string doubling itself outruns any instruction count: in the :script process its memory cap or
     * the watchdog stops it, once (it doesn't run again), and the app lives on.
     */
    @Test fun aRunawayStringIsStoppedInTheScriptProcess() {
        val logs = LogRepository.get(context)
        val marker = "runaway-" + System.nanoTime()
        ScriptScheduler.eval(context, ScriptLang.LUA, "log('$marker') local s = ('x'):rep(1024 * 1024) while true do s = s .. s end")
        fun stops(): List<String> {
            val recent = logs.recent(200)
            val at = recent.indexOfFirst { it.text == marker }
            if (at < 0) return emptyList()
            // recent() is oldest first: what the run logged after the marker.
            return recent.drop(at + 1).map { it.text }.filter { "killed script process" in it || "memory" in it.lowercase() }
        }
        val deadline = System.currentTimeMillis() + 30_000
        while (System.currentTimeMillis() < deadline && stops().isEmpty()) Thread.sleep(250)
        assertTrue("never stopped", stops().isNotEmpty())
        scriptRunsSettle()
        assertEquals("ran again: ${stops()}", 1, logs.recent(200).count { it.text == marker })
    }

    /** sleep and requests wait no longer than the run has left: a loop of them still ends on time. */
    @Test fun aSleepLoopEndsAtTheTimeout() {
        for (run in listOf<() -> Unit>(
            { lua("while true do sleep(5000) end", repl = false, timeout = 1_000) },
            { js("while (true) await sleep(5000);", repl = false, timeout = 1_000) },
        )) {
            val started = System.currentTimeMillis()
            try {
                run()
                fail("expected timeout")
            } catch (e: Exception) {
                // Lua says so; QuickJS raises its interrupt.
                assertTrue(e.toString(), e.message.orEmpty().contains("timed out") || e is com.dokar.quickjs.QuickJsInterruptedException)
            }
            assertTrue("took ${System.currentTimeMillis() - started} ms", System.currentTimeMillis() - started < 3_000)
        }
    }

    /** A value nested deeper than the host's conversion can take is refused in JavaScript, before it gets there. */
    @Test fun jsDeepValuesAreRefused() {
        val nested = "let a = []; for (let i = 0; i < 100000; i++) a = [a];"
        for (call in listOf("log(a)", "log('x', {deep: a})", "store.set('k', a)", "return a")) {
            try {
                js("$nested $call", repl = false)
                fail("expected a refusal for $call")
            } catch (e: Exception) {
                assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("64 levels"))
            }
        }
        assertEquals("[[1]]", js("return [[1]]", repl = false))
    }

    /** The guard keeps what it uses: a script replacing Array.prototype, Reflect.apply or an index setter can't switch it off. */
    @Test fun jsGuardSurvivesTampering() {
        val code = """
            Array.prototype.map = function () { return this; };
            Reflect.apply = (f, t, a) => f(...a);
            Object.defineProperty(Array.prototype, "0", { set() {}, configurable: true });
            Object.keys = () => [];
            let a = []; for (let i = 0; i < 100000; i++) a = [a];
            log(a);
        """.trimIndent()
        try {
            js(code, repl = false)
            fail("expected a refusal")
        } catch (e: Exception) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("64 levels"))
        }
    }

    /** At most 64 requests or sleeps wait at once. */
    @Test fun jsWaitingCallsAreCapped() {
        try {
            js("await Promise.all(Array.from({ length: 100 }, () => sleep(10)));", repl = false)
            fail("expected a cap")
        } catch (e: Exception) {
            assertTrue(e.message.orEmpty(), e.message.orEmpty().contains("more than 64"))
        }
        assertEquals("64", js("await Promise.all(Array.from({ length: 64 }, () => sleep(1))); return 64", repl = false))
    }

    /**
     * Promise jobs that queue one another and loop: QuickJS turns its interrupt into a rejection and
     * runs the next job. In the :script process the watchdog stops the run past its timeout, once.
     */
    @Test fun aRunawayJsJobLoopIsStoppedInTheScriptProcess() {
        val logs = LogRepository.get(context)
        val marker = "jobloop-" + System.nanoTime()
        ScriptScheduler.eval(context, ScriptLang.JS, "log('$marker'); function spin() { Promise.resolve().then(spin); for (;;) {} } spin();")
        fun stops(): List<String> {
            val recent = logs.recent(200)
            val at = recent.indexOfFirst { it.text == marker }
            if (at < 0) return emptyList()
            return recent.drop(at + 1).map { it.text }.filter { "killed script process" in it }
        }
        val deadline = System.currentTimeMillis() + ScriptWorker.TIMEOUT_MS + 30_000
        while (System.currentTimeMillis() < deadline && stops().isEmpty()) Thread.sleep(500)
        assertTrue("never stopped", stops().isNotEmpty())
        scriptRunsSettle()
        assertEquals("ran again", 1, logs.recent(200).count { it.text == marker })
    }

    @Test fun luaArgsAreExposed() {
        val out = runBlocking { LuaEngine().run("return argline .. '|' .. #args .. '|' .. args[2]", "t", host(), 5_000, false, listOf("a", "b c")) }
        assertEquals("a b c|2|b c", out)
    }

    @Test fun jsArgsAreExposed() {
        val out = runBlocking { JsEngine().run("return argline + '|' + args.length + '|' + args[1]", "t", host(), 5_000, false, listOf("a", "b c")) }
        assertEquals("a b c|2|b c", out)
    }

    @Test fun luaRssParseAndStore() {
        val out = lua(
            """
            local feed = rss.parse([[<rss><channel><title>T</title>
              <item><title>One</title><link>https://a/1</link><guid>1</guid></item>
              <item><title>Two</title><link>https://a/2</link></item>
            </channel></rss>]])
            store.set("k", "v")
            return feed.title .. ":" .. #feed.items .. ":" .. feed.items[2].link .. ":" .. store.get("k")
            """.trimIndent(),
            repl = false,
        )
        assertEquals("T:2:https://a/2:v", out)
    }

    @Test fun luaLogWritesContext() {
        val logs = LogRepository.get(context)
        val h = host()
        runBlocking { LuaEngine().run("log('hello from lua', { url = 'https://example.com' })", "t", h, 5_000, false) }
        h.flush() // the worker flushes buffered lines at the end of a run
        val last = logs.recent(5).last { it.kind == LogKind.SCRIPT }
        assertEquals("hello from lua", last.text)
        assertEquals("https://example.com/", last.ctx.url)  // links are normalised
    }

    @Test fun jsExpression() = assertEquals("42", js("6 * 7"))

    @Test fun jsAsyncScriptReturnsValue() {
        val out = js(
            """
            const feed = rss.parse('<feed><title>A</title><entry><title>E</title><link href="https://e"/></entry></feed>');
            store.set("x", "y");
            return feed.title + ":" + feed.items[0].link + ":" + store.get("x");
            """.trimIndent(),
            repl = false,
        )
        assertEquals("A:https://e:y", out)
    }

    @Test fun jsInfiniteLoopTimesOut() {
        val started = System.currentTimeMillis()
        try {
            js("while (true) {}", repl = false, timeout = 1_000)
            fail("expected timeout")
        } catch (e: Exception) {
            // Its interrupt or a cancellation, not an error in the script.
            assertTrue(e.toString(), e is com.dokar.quickjs.QuickJsInterruptedException || e is kotlinx.coroutines.TimeoutCancellationException)
        }
        // It ran until the limit, and no longer.
        val took = System.currentTimeMillis() - started
        assertTrue("took $took ms", took in 900 until 5_000)
    }

    /** End to end: snippet goes through WorkManager into the :script process and back via the DB. */
    @Test fun replEvalRunsInScriptProcess() {
        val logs = LogRepository.get(context)
        val marker = "pid-check-" + System.nanoTime()
        ScriptScheduler.eval(context, ScriptLang.LUA, "log('$marker')")
        ScriptScheduler.eval(context, ScriptLang.JS, "log('$marker-js')")
        val deadline = System.currentTimeMillis() + 30_000
        var luaSeen = false
        var jsSeen = false
        while (System.currentTimeMillis() < deadline && !(luaSeen && jsSeen)) {
            val recent = logs.recent(50)
            luaSeen = recent.any { it.text == marker }
            jsSeen = recent.any { it.text == "$marker-js" }
            Thread.sleep(250)
        }
        assertTrue("lua snippet never logged", luaSeen)
        assertTrue("js snippet never logged", jsSeen)
        assertNotNull(logs.recent(50).firstOrNull { it.text == marker }?.source)
        val manager = context.getSystemService(android.app.ActivityManager::class.java)
        assertTrue("no :script process", manager.runningAppProcesses.any { it.processName == "${context.packageName}:script" })
    }
}
