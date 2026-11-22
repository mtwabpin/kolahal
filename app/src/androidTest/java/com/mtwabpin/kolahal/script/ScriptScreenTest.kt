package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.screens.FramePlayer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/**
 * A script's own screen (`tty.screen`) in both engines. Home is made up: [Home] answers when the
 * script opens its screen, with the events a test gives, and keeps what the script drew.
 */
@RunWith(AndroidJUnit4::class)
class ScriptScreenTest {
    @get:Rule
    val logLines = LogLinesSince()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)

    private val frames = CopyOnWriteArrayList<ScreenFrame>()
    private val ended = AtomicInteger()

    /** The newest line before the run: the lines after it are the run's. */
    @Volatile
    private var before = 0L

    /** The run's lines in the log when its last tick was answered: while its screen was open. */
    @Volatile
    private var linesAtTick = emptyList<String>()
    private var readsLines = false

    private val out = object : ScreenSession.Out {
        override fun draw(frame: ScreenFrame): Boolean {
            frames.add(frame)
            if (frame.tick && readsLines) linesAtTick = lines()
            return true
        }

        override fun end() {
            ended.incrementAndGet()
        }
    }

    /** Home opens the screen at 20×6, and [events] come after it. */
    private inner class Home(private val events: List<ScreenSession.Event>) : ScreenSession.Home {
        override fun open(session: ScreenSession): Boolean {
            session.post(ScreenSession.Event.Attach(20, 6, out))
            events.forEach(session::post)
            return true
        }

        override fun closed(session: ScreenSession) = Unit
    }

    private fun host(
        interactive: Boolean = true,
        byScript: Boolean = false,
        grants: ScriptAccess.Grants? = null,
        events: List<ScreenSession.Event> = emptyList(),
    ): HostApi = HostApi(
        context, "screentest", scriptId = null, notifyEnabled = false,
        interactive = interactive, byScript = byScript, grants = grants,
    ).apply { screenHome = Home(events) }

    private fun run(lang: ScriptLang, code: String, host: HostApi): String? {
        frames.clear()
        ended.set(0)
        before = logs.maxId()
        try {
            return runBlocking { ScriptEngine.forLang(lang).run(code, "screentest", host, 10_000, repl = false) }
        } finally {
            host.flush()
        }
    }

    /** Runs [lua], then [js], each on its own, and gives the test each one's error (null: none). */
    private fun inBoth(
        lua: String,
        js: String,
        events: List<ScreenSession.Event> = emptyList(),
        check: (lang: ScriptLang, error: Throwable?) -> Unit,
    ) {
        for ((lang, code) in listOf(ScriptLang.LUA to lua, ScriptLang.JS to js)) {
            val error = runCatching { run(lang, code, host(events = events)) }.exceptionOrNull()
            check(lang, error)
        }
    }

    /** The grid after the frames of the run, as home shows it. */
    private fun grid(): CellBuffer = CellBuffer(20, 6).also { g -> frames.forEach { FramePlayer.play(it, g) } }

    private fun tick(t: Long) = ScreenSession.Event.Tick(t, 100)
    private val close = ScreenSession.Event.Close

    /** The lines the run logged. */
    private fun lines() = logs.recent(200, afterId = before).map { StyledText.plain(it.text) }

    // ---- drawing -----------------------------------------------------------------------------

    @Test
    fun itDrawsWhatItsFunctionsDraw() = inBoth(
        lua = """
            tty.screen{
              title = "Life",
              start = function(g) g.put(0, 0, "size " .. g.cols .. "x" .. g.rows) end,
              frame = function(g, t, dt)
                g.put(0, 1, "t " .. t .. " " .. dt, "accent")
                g:put(0, 2, "colon style", { color = "good", bold = true })
                g.box(12, 3, 5, 2, "link")
                g.bar(0, 5, 10, 0.5)
              end,
            }
        """.trimIndent(),
        js = """
            tty.screen({
              title: "Life",
              start(g) { g.put(0, 0, "size " + g.cols + "x" + g.rows); },
              frame(g, t, dt) {
                g.put(0, 1, "t " + t + " " + dt, "accent");
                g.put(0, 2, "colon style", { color: "good", bold: true });
                g.box(12, 3, 5, 2, "link");
                g.bar(0, 5, 10, 0.5);
              },
            });
        """.trimIndent(),
        events = listOf(tick(100), tick(200), close),
    ) { lang, error ->
        assertNull("$lang: $error", error)
        val g = grid()
        assertEquals("$lang", "size 20x6", g.rowText(0).trimEnd())
        assertEquals("$lang", "t 200 100", g.rowText(1).trimEnd())
        assertEquals("$lang", "colon style", g.rowText(2).trimEnd())
        assertEquals("$lang", "            ┌───┐", g.rowText(3).trimEnd())
        assertEquals("$lang", "            └───┘", g.rowText(4).trimEnd())
        assertEquals("$lang", "█████", g.rowText(5).trimEnd())
        assertEquals("$lang", 1, ended.get())
        // start draws once, then each tick is answered by a frame of its own.
        assertEquals("$lang", listOf(false, true, true), frames.map { it.tick })
    }

    /** What a script keeps lasts from call to call: in JavaScript on the object too. */
    @Test
    fun itsStateLastsAcrossCalls() = inBoth(
        lua = """
            local n = 0
            tty.screen{ frame = function(g) n = n + 1; g.put(0, 0, "n " .. n) end }
        """.trimIndent(),
        js = """
            tty.screen({ n: 0, frame(g) { this.n++; g.put(0, 0, "n " + this.n); } });
        """.trimIndent(),
        events = listOf(tick(1), tick(2), tick(3), close),
    ) { lang, error ->
        assertNull("$lang: $error", error)
        assertEquals("$lang", "n 3", grid().rowText(0).trimEnd())
    }

    // ---- input --------------------------------------------------------------------------------

    @Test
    fun itGetsKeysAndTaps() = inBoth(
        lua = """
            tty.screen{
              start = function(g) end,
              key = function(k) log("key " .. k) end,
              tap = function(c, r) log("tap " .. c .. "," .. r) end,
            }
        """.trimIndent(),
        js = """
            tty.screen({
              start(g) {},
              key(k) { log("key " + k); },
              tap(c, r) { log("tap " + c + "," + r); },
            });
        """.trimIndent(),
        events = listOf(ScreenSession.Event.Key("up"), ScreenSession.Event.Key("q"), ScreenSession.Event.Tap(3, 4), close),
    ) { lang, error ->
        assertNull("$lang: $error", error)
        assertEquals("$lang", listOf("key up", "key q", "tap 3,4"), lines())
    }

    /** Returning false closes it: the rest of the events are not for it any more, and stop has the last word. */
    @Test
    fun returningFalseClosesIt() = inBoth(
        lua = """
            tty.screen{
              frame = function(g, t) return t < 300 end,
              stop = function() log("stopped") end,
            }
        """.trimIndent(),
        js = """
            tty.screen({
              frame(g, t) { return t < 300; },
              stop() { log("stopped"); },
            });
        """.trimIndent(),
        events = listOf(tick(100), tick(200), tick(300), tick(400)),
    ) { lang, error ->
        assertNull("$lang: $error", error)
        assertEquals("$lang", listOf(true, true, true), frames.map { it.tick })
        assertEquals("$lang", 1, ended.get())
        assertEquals("$lang", listOf("stopped"), lines())
    }

    /** A function that returns nothing keeps the screen open. */
    @Test
    fun returningNothingKeepsItOpen() = inBoth(
        lua = "tty.screen{ frame = function(g) end, key = function(k) end }",
        js = "tty.screen({ frame(g) {}, key(k) {} });",
        events = listOf(tick(1), ScreenSession.Event.Key("x"), tick(2), close),
    ) { lang, error ->
        assertNull("$lang: $error", error)
        assertEquals("$lang", 2, frames.count { it.tick })
    }

    /** An async function in JavaScript is waited for, and its result counts. */
    @Test
    fun anAsyncJavaScriptFunctionIsWaitedFor() {
        val code = """
            let n = 0;
            tty.screen({ async frame(g) { await sleep(20); n++; g.put(0, 0, "n " + n); return n < 2; } });
        """.trimIndent()
        run(ScriptLang.JS, code, host(events = listOf(tick(1), tick(2), tick(3))))
        assertEquals("n 2", grid().rowText(0).trimEnd())
        assertEquals(2, frames.count { it.tick })
    }

    // ---- what goes wrong ------------------------------------------------------------------------

    /** An error in a function ends the run with the script's own error, and closes the screen. */
    @Test
    fun anErrorInAFunctionEndsTheRun() = inBoth(
        lua = """tty.screen{ frame = function(g) error("oops") end }""",
        js = """tty.screen({ frame(g) { throw new Error("oops"); } });""",
        events = listOf(tick(1), tick(2)),
    ) { lang, error ->
        assertTrue("$lang: $error", error != null && "oops" in error.toString())
        assertEquals("$lang", 1, ended.get())
        assertTrue("$lang", frames.isEmpty())
    }

    /** A wrong drawing call is an error for the script, as the host says it. */
    @Test
    fun aWrongDrawingCallIsAnError() = inBoth(
        lua = """tty.screen{ start = function(g) g.put(0, 0, "x", "red") end }""",
        js = """tty.screen({ start(g) { g.put(0, 0, "x", "red"); } });""",
    ) { lang, error ->
        assertTrue("$lang: $error", error != null && "no color 'red'" in error.toString())
        assertEquals("$lang", 1, ended.get())
    }

    /** A function that runs too long is stopped, and the run ends with a clear error. */
    @Test
    fun aFunctionThatRunsTooLongIsStopped() = inBoth(
        lua = "tty.screen{ frame = function(g) while true do end end }",
        js = "tty.screen({ frame(g) { while (true) {} } });",
        events = listOf(tick(1)),
    ) { lang, error ->
        assertTrue("$lang: $error", error != null && "frame took over 1 s" in error.toString())
        assertEquals("$lang", 1, ended.get())
    }

    /** Each call has its time, however long the screen is open: many calls that each wait a while run on. */
    @Test
    fun theTimeIsForEachCall() = inBoth(
        lua = "tty.screen{ frame = function(g, t) sleep(300) end }",
        js = "tty.screen({ async frame(g, t) { await sleep(300); } });",
        events = List(5) { tick(it * 100L) } + close,
    ) { lang, error ->
        assertNull("$lang: $error", error)
        assertEquals("$lang", 5, frames.count { it.tick })
    }

    /** A bad spec is refused where it is, with the reason, before any screen opens. */
    @Test
    fun aSpecThatCannotWorkSaysSo() = inBoth(
        lua = "tty.screen{ frame = function(g) end, frames = function(g) end }",
        js = "tty.screen({ frame(g) {}, frames(g) {} });",
    ) { lang, error ->
        assertTrue("$lang: $error", error != null && "no function frames" in error.toString())
        assertEquals("$lang", 0, ended.get())
    }

    /** A screen runs when the code ends: it comes after the script's last line. */
    @Test
    fun theScreenRunsAfterTheCode() = inBoth(
        lua = """
            tty.screen{ start = function(g) log("screen") end }
            log("code")
        """.trimIndent(),
        js = """
            tty.screen({ start(g) { log("screen"); } });
            log("code");
        """.trimIndent(),
        events = listOf(close),
    ) { lang, error ->
        assertNull("$lang: $error", error)
        assertEquals("$lang", listOf("code", "screen"), lines())
    }

    /** One run, one screen. */
    @Test
    fun aRunOpensOneScreen() = inBoth(
        lua = "tty.screen{ start = function(g) end }; tty.screen{ start = function(g) end }",
        js = "tty.screen({ start(g) {} }); tty.screen({ start(g) {} });",
    ) { lang, error ->
        assertTrue("$lang: $error", error != null && "one screen" in error.toString())
    }

    /** The grid is only for the functions of the screen: drawing anywhere else is an error. */
    @Test
    fun drawingOutsideItsFunctionsIsAnError() {
        val error = runCatching { run(ScriptLang.JS, "tty.screen({ start(g) {} }); __kolahal_grid.put(0, 0, 'x');", host(events = listOf(close))) }
            .exceptionOrNull()
        assertTrue("$error", error != null && "only inside the functions of tty.screen" in error.toString())
        assertTrue(frames.isEmpty())
        // In Lua there is no grid to reach outside them: it is only the function's own argument.
        assertEquals("nil", run(ScriptLang.LUA, "return tostring(__kolahal_grid)", host()))
    }

    // ---- what it may do -------------------------------------------------------------------------

    private val opens = mapOf(
        ScriptLang.LUA to "tty.screen{ start = function(g) end }",
        ScriptLang.JS to "tty.screen({ start(g) {} });",
    )

    /** A script without its `@uses screen` line is told to add it, and opens nothing. */
    @Test
    fun itNeedsItsUsesLine() {
        for ((lang, code) in opens) {
            val host = host(grants = ScriptAccess.Grants(emptySet(), emptyMap()))
            val error = runCatching { run(lang, code, host) }.exceptionOrNull()
            assertTrue("$lang: $error", error != null && "needs `@uses screen`" in error.toString())
            assertFalse(host.hasScreen)
        }
    }

    /** While it is off the screen doesn't open, and the run says so. One nobody decided on asks, and goes on without. */
    @Test
    fun itIsOffUntilAllowed() {
        for ((lang, code) in opens) {
            val undecided = host(grants = ScriptAccess.Grants(setOf("screen"), emptyMap()))
            run(lang, "$code\nlog('went on')", undecided)
            assertEquals("$lang", setOf("screen"), undecided.undecided.toSet())
            assertEquals("$lang", listOf("went on"), lines())
            assertFalse("$lang", undecided.hasScreen)

            val off = host(grants = ScriptAccess.Grants(setOf("screen"), mapOf("screen" to false)))
            val error = runCatching { run(lang, code, off) }.exceptionOrNull()
            assertTrue("$lang: $error", error != null && "full screen is off" in error.toString())
            assertEquals("$lang", RunStop.Denied("screen", "tty.screen: full screen is off for this script"), off.stoppedBy)
        }
    }

    /** Only a run the user started opens a screen. */
    @Test
    fun aTimerOrAnEventOpensNoScreen() {
        for ((lang, code) in opens) {
            val host = host(interactive = false, grants = ScriptAccess.Grants(setOf("screen"), mapOf("screen" to true)))
            val error = runCatching { run(lang, code, host) }.exceptionOrNull()
            assertTrue("$lang: $error", error != null && "only opens when you start the script" in error.toString())
            assertTrue("$lang", frames.isEmpty())
        }
    }

    /** A run without a screen ends as it always did. */
    @Test
    fun aRunWithoutAScreenIsAsItWas() {
        for (lang in ScriptLang.entries) {
            val host = host()
            assertEquals("$lang", "42", run(lang, "return 42", host))
            assertFalse("$lang", host.hasScreen)
            assertTrue("$lang", frames.isEmpty())
        }
    }

    /** A run another script started (launcher.run) opens none either, whoever started that script. */
    @Test
    fun anotherScriptOpensNoScreen() {
        for ((lang, code) in opens) {
            val host = host(byScript = true, grants = ScriptAccess.Grants(setOf("screen"), mapOf("screen" to true)))
            val error = runCatching { run(lang, code, host) }.exceptionOrNull()
            assertTrue("$lang: $error", error != null && "not when another script starts it" in error.toString())
            assertFalse("$lang", host.hasScreen)
        }
    }

    /** A line a function logs is in the log while the screen is open, not when it closes. */
    @Test
    fun linesShowWhileTheScreenIsOpen() {
        readsLines = true
        inBoth(
            lua = """
                tty.screen{
                  key = function(k) log("key " .. k) end,
                  frame = function(g) return true end,
                }
            """.trimIndent(),
            js = """
                tty.screen({
                  key(k) { log("key " + k); },
                  frame(g) { return true; },
                });
            """.trimIndent(),
            events = listOf(ScreenSession.Event.Key("a"), ScreenSession.Event.Key("b"), tick(1), close),
        ) { lang, error ->
            assertNull("$lang: $error", error)
            assertEquals("$lang", listOf("key a", "key b"), linesAtTick)
        }
    }

    /** The watchdog's thread goes with its run, and a long screen starts no more of them. */
    @Test
    fun theWatchdogIsGoneAfterTheRun() {
        fun watchdogs() = Thread.getAllStackTraces().keys.count { it.name == "script-watchdog" && it.isAlive }
        for ((lang, code) in opens) {
            run(lang, code, host(events = List(500) { tick(it.toLong()) } + close))
            com.mtwabpin.kolahal.waitUntil("$lang: a watchdog is still there") { watchdogs() == 0 }
        }
    }
}
