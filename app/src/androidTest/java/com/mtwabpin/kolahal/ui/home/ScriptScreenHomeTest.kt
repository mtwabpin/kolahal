package com.mtwabpin.kolahal.ui.home

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performTouchInput
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.ConfigReset
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TestViewModels
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.terminal.ScriptScreenRequests
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A script's own screen (`tty.screen`) over home, all of it real: the run is in the `:script` process,
 * and home reaches it there through its service.
 */
@RunWith(AndroidJUnit4::class)
class ScriptScreenHomeTest {
    @get:Rule
    val logLines = LogLinesSince()

    @get:Rule
    val vms = TestViewModels()

    @get:Rule
    val configReset = ConfigReset()

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val app =
        InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val scripts = ScriptRepository.get(app)
    private val logs = LogRepository.get(app)
    private val made = mutableListOf<Long>()

    @After
    fun tearDown() = made.forEach {
        ScriptScheduler.cancel(app, it)
        scripts.delete(it)
    }

    /** A saved script, with everything it declares allowed. */
    private fun script(suffix: String, lang: ScriptLang, code: String): Script =
        scripts.create("sstest$suffix", lang, code).also { made += it.id }

    /** Home as MainActivity shows it: the terminal, with the screens over it. */
    private fun show(): TerminalViewModel {
        val vm = vms.terminal(app)
        compose.setContent {
            KolahalTheme {
                Box(Modifier.fillMaxSize()) {
                    TerminalScreen(vm)
                    HomeScreenOverlay(vm)
                }
            }
        }
        compose.waitForIdle()
        return vm
    }

    private fun TerminalViewModel.onScriptScreen() = ttyScreen.value?.startsWith(ScriptScreenRequests.PREFIX) == true

    /** Runs [s] and waits for its screen over home. */
    private fun open(vm: TerminalViewModel, s: Script) {
        ScriptScheduler.runNow(app, s)
        compose.waitUntil(30_000) { vm.onScriptScreen() }
        compose.waitUntil(10_000) {
            compose.onAllNodes(hasContentDescription("Test screen")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /** The script's own lines in the log, once its run has ended (and wrote them). */
    private fun lines(wanted: Int): List<String> {
        val until = System.currentTimeMillis() + 20_000
        while (true) {
            val lines = logs.recent(200, afterId = logLines.start).map { StyledText.plain(it.text) }
                .filter { it.startsWith("ss ") }
            if (lines.size >= wanted || System.currentTimeMillis() > until) return lines
            Thread.sleep(100)
        }
    }

    private fun tapScreen() = compose.onNode(hasContentDescription("Test screen")).performTouchInput { click(center) }

    /** It opens over home with its title, runs its start function in the right size, and a tap closes it: home sees the same tap. */
    @Test
    fun itOpensAndATapClosesIt() {
        val vm = show()
        val s = script(
            "tap", ScriptLang.LUA,
            """
                -- @uses screen
                tty.screen{
                  title = "Test screen",
                  start = function(g) log("ss started " .. (g.cols > 5 and g.rows > 5 and "big" or "small")) end,
                  frame = function(g, t) g.clear(); g.put(0, 0, "frame " .. math.floor(t / 100)) end,
                  stop = function() log("ss stopped") end,
                }
            """.trimIndent()
        )
        open(vm, s)
        tapScreen()
        compose.waitUntil(10_000) { vm.ttyScreen.value == null }
        // The screen leaves the composition, and tells the script, in the next frame: the test clock
        // moves only when asked.
        compose.waitForIdle()
        assertEquals(listOf("ss started big", "ss stopped"), lines(2))
        // Nothing of it was typed, and home is as it was.
        assertEquals("", vm.input.text)
    }

    /** With a tap function the tap goes to the script, with the cell it was on; it decides when to close. */
    @Test
    fun theScriptGetsTheTapAndCloses() {
        val vm = show()
        val s = script(
            "own", ScriptLang.JS,
            """
                // @uses screen
                let taps = 0;
                tty.screen({
                  title: "Test screen",
                  fps: 5,
                  start(g) { g.box(0, 0, g.cols, g.rows); log("ss start " + g.cols + "x" + g.rows); },
                  frame(g) { g.put(2, 2, "taps " + taps); },
                  tap(col, row) { taps++; log("ss tap " + (col >= 0 && row >= 0)); return taps < 2; },
                });
            """.trimIndent()
        )
        open(vm, s)
        tapScreen()
        compose.waitForIdle()
        Thread.sleep(500)
        assertTrue("one tap closed it", vm.onScriptScreen())
        tapScreen()
        compose.waitUntil(10_000) { vm.ttyScreen.value == null }
        val said = lines(3)
        assertTrue(said.toString(), said.first().startsWith("ss start "))
        assertEquals(listOf("ss tap true", "ss tap true"), said.drop(1))
    }

    /** Back always closes it, whatever the script does with keys and taps. */
    @Test
    fun backClosesIt() {
        val vm = show()
        val s = script(
            "back", ScriptLang.JS,
            """
                // @uses screen
                tty.screen({
                  title: "Test screen",
                  start(g) {},
                  key(k) { return true; },
                  tap() { return true; },
                  stop() { log("ss stopped"); },
                });
            """.trimIndent()
        )
        open(vm, s)
        compose.activity.runOnUiThread { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(10_000) { vm.ttyScreen.value == null }
        assertEquals(listOf("ss stopped"), lines(1))
    }

    /** An error in the script closes the screen and shows the error line. The log is as before otherwise. */
    @Test
    fun anErrorClosesItAndIsSaid() {
        val vm = show()
        val s = script(
            "bad", ScriptLang.LUA,
            """
                -- @uses screen
                tty.screen{
                  title = "Test screen",
                  start = function(g) end,
                  frame = function(g) error("broken frame") end,
                }
            """.trimIndent()
        )
        ScriptScheduler.runNow(app, s)
        compose.waitUntil(30_000) { vm.onScriptScreen() }
        compose.waitUntil(20_000) { vm.ttyScreen.value == null }
        val until = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < until) {
            if (logs.recent(200, afterId = logLines.start).any { "broken frame" in it.text }) return
            Thread.sleep(100)
        }
        throw AssertionError("the error was never said")
    }

    /** The screens a script may not open: each run ends with a line that says why, and home shows none. */
    private fun refuses(vm: TerminalViewModel, says: String, start: () -> Unit) {
        val from = logs.maxId()
        start()
        val until = System.currentTimeMillis() + 30_000
        while (logs.recent(200, afterId = from).none { says in it.text }) {
            if (System.currentTimeMillis() > until) throw AssertionError(
                "no line with “$says”: ${logs.recent(200, afterId = from).map { it.text }}"
            )
            Thread.sleep(100)
        }
        compose.waitForIdle()
        assertTrue("a screen opened", vm.ttyScreen.value == null)
    }

    private val life = """
        -- @uses screen
        tty.screen{ title = "Test screen", start = function(g) end }
    """.trimIndent()

    /** What runs a script by itself runs none with a screen: an event, a timer's run, a late daily time. */
    @Test
    fun nothingByItselfOpensIt() {
        val vm = show()
        val s = script("auto", ScriptLang.LUA, life)
        val why = "${s.name} ${com.mtwabpin.kolahal.script.ScriptMeta.SCREEN_ONLY}"
        refuses(vm, why) { ScriptScheduler.runForEvent(app, s.id, mapOf("name" to "unlock")) }
        refuses(vm, why) { ScriptScheduler.runForEvent(app, s.id, mapOf("name" to "boot")) }
        refuses(vm, why) { ScriptScheduler.runScheduled(app, s, "07:00") }
    }

    /** A pinned line's and a widget's refresh aren't even queued for it. */
    @Test
    fun aRefreshRunsNothing() {
        val s = script("fresh", ScriptLang.LUA, life)
        ScriptScheduler.refreshPinned(app, s)
        ScriptScheduler.refreshWidget(app, s)
        val queued = androidx.work.WorkManager.getInstance(app)
            .getWorkInfosForUniqueWork(ScriptScheduler.refreshQueue(s.id)).get()
        assertTrue(queued.toString(), queued.isEmpty())
    }

    /** Another script sends its name, its run or the code itself: no screen. */
    @Test
    fun anotherScriptOpensNone() {
        val vm = show()
        val s = script("sent", ScriptLang.LUA, life)
        refuses(vm, "not when another script starts it") { vm.runCommand(s.name, byScript = true) }
        refuses(vm, "not when another script starts it") {
            ScriptScheduler.sentByScript { ScriptScheduler.eval(app, ScriptLang.LUA, "tty.screen{ start = function(g) end }") }
        }
    }

    /** Without its `@uses` line the call is an error that says what to add, as any other item's is. */
    @Test
    fun withoutItsLineItIsAnError() {
        val vm = show()
        val s = script("bare", ScriptLang.LUA, "tty.screen{ start = function(g) end }")
        refuses(vm, "needs `@uses screen`") { ScriptScheduler.runNow(app, s) }
    }

    /** You may: by its name (it is a command without saying so), with a tap on a button, and with typed code. */
    @Test
    fun youMayOpenIt() {
        val vm = show()
        val s = script("yours", ScriptLang.LUA, life)
        for (start in listOf<() -> Unit>(
            { vm.runCommand(s.name) },
            { ScriptScheduler.runNow(app, s, event = mapOf("name" to "action", "label" to "Play")) },
            { ScriptScheduler.eval(app, ScriptLang.LUA, "tty.screen{ title = 'Test screen', start = function(g) end }") },
        )) {
            start()
            compose.waitUntil(30_000) { vm.onScriptScreen() }
            compose.waitUntil(10_000) {
                compose.onAllNodes(hasContentDescription("Test screen")).fetchSemanticsNodes().isNotEmpty()
            }
            tapScreen()
            compose.waitUntil(10_000) { vm.ttyScreen.value == null }
            compose.waitForIdle()
        }
    }

    /** A timer it had before counts for nothing, isn't set again, and is forgotten at the next sync. */
    @Test
    fun itsTimersAreGone() {
        val s = script("timed", ScriptLang.LUA, "-- @uses clipboard\nlog('x')")
        scripts.setInterval(s.id, 60)
        scripts.setAtTimes(s.id, listOf("07:00"))
        assertEquals(60, scripts.byId(s.id)!!.intervalMin)
        // As an update brings it.
        scripts.saveCode(s.id, life)
        assertEquals(0, scripts.byId(s.id)!!.intervalMin)
        assertEquals(emptyList<String>(), scripts.byId(s.id)!!.atTimes)
        // Undone, the timer is back with the code.
        scripts.saveCode(s.id, s.code)
        assertEquals(60, scripts.byId(s.id)!!.intervalMin)
        // Synced meanwhile (at Kolahal's next start), it isn't.
        scripts.saveCode(s.id, life)
        scripts.dropScreenTimers()
        scripts.saveCode(s.id, s.code)
        assertEquals(0, scripts.byId(s.id)!!.intervalMin)
        assertEquals(emptyList<String>(), scripts.byId(s.id)!!.atTimes)
        // And none can be set while it has the screen.
        scripts.saveCode(s.id, life)
        scripts.setInterval(s.id, 60)
        scripts.setAtTimes(s.id, listOf("08:00"))
        scripts.saveCode(s.id, s.code)
        assertEquals(0, scripts.byId(s.id)!!.intervalMin)
        assertEquals(emptyList<String>(), scripts.byId(s.id)!!.atTimes)
    }

    /** Its worker is done while the screen is open: the screen no longer needs it. */
    @Test
    fun theWorkerIsDoneWhileTheScreenIsOpen() {
        val vm = show()
        val s = script("long", ScriptLang.LUA, life)
        open(vm, s)
        val work = androidx.work.WorkManager.getInstance(app)
        fun running() = work.getWorkInfosByTag(com.mtwabpin.kolahal.script.ScriptWorker::class.java.name).get()
            .count { it.state == androidx.work.WorkInfo.State.RUNNING }
        compose.waitUntil(20_000) { running() == 0 }
        Thread.sleep(2_000)
        assertTrue("the screen closed with its worker", vm.onScriptScreen())
        tapScreen()
        compose.waitUntil(10_000) { vm.ttyScreen.value == null }
        compose.waitForIdle()
        // Its run ended as any run does: its result is kept.
        val until = System.currentTimeMillis() + 20_000
        while (scripts.byId(s.id)!!.lastStatus?.startsWith("ok") != true && System.currentTimeMillis() < until) Thread.sleep(100)
        assertTrue("${scripts.byId(s.id)!!.lastStatus}", scripts.byId(s.id)!!.lastStatus.orEmpty().startsWith("ok"))
    }

    /** If the script process is stopped, the screen closes with a line that says so. */
    @Test
    fun aStoppedProcessIsSaid() {
        val vm = show()
        val s = script("killed", ScriptLang.LUA, life)
        open(vm, s)
        compose.waitForIdle()
        val manager = app.getSystemService(android.app.ActivityManager::class.java)
        val process = manager.runningAppProcesses.first { it.processName.endsWith(":script") }
        android.os.Process.killProcess(process.pid)
        compose.waitUntil(20_000) { vm.ttyScreen.value == null }
        val says = "${s.name}: its screen closed, because the script process stopped"
        val until = System.currentTimeMillis() + 10_000
        while (logs.recent(200, afterId = logLines.start).none { it.text == says }) {
            if (System.currentTimeMillis() > until) throw AssertionError("the line was never logged")
            Thread.sleep(100)
        }
    }
}
