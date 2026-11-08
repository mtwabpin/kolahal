package com.mtwabpin.kolahal.terminal

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.LuaEngine
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.waitUntil
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Buttons under a script's line (ctx.actions): stored only as they may be, and a tap runs that script again, once. */
@RunWith(AndroidJUnit4::class)
class ScriptButtonsTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    @get:Rule val logLines = LogLinesSince()
    private val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
    private val repo = ScriptRepository.get(app)
    private val logs = LogRepository.get(app)
    private val name = "sb${System.nanoTime() % 100_000}"

    @After fun tearDown() {
        repo.byName(name)?.let { ScriptScheduler.cancel(app, it.id); repo.delete(it.id) }
    }

    private fun newLines(): List<LogEntry> = logs.recent(400).filter { it.id > logLines.start }

    /** Runs [code] as the saved script [name] (or as code typed at the prompt), and returns what it logged. */
    private fun run(code: String, scriptId: Long? = null): Result<String?> {
        val host = HostApi(app, name, scriptId = scriptId, notifyEnabled = false)
        return runCatching { runBlocking { LuaEngine().run(code, name, host, 5_000, false) } }.also { host.flush() }
    }

    private val code = """
        if event and event.name == "action" then
          log("pressed " .. table.concat(args, " ") .. " / " .. event.label)
          return
        end
        log("Battery at 92%", "Unplug the charger", { actions = { { "Snooze 30 min", "snooze 30" }, { "Not today", "off today" } } })
    """.trimIndent()

    @Test fun aTapRunsTheScriptAgainOnce() {
        val script = repo.create(name, ScriptLang.LUA, code, true)
        run(code, script.id).getOrThrow()
        val line = newLines().single { it.text == "Battery at 92%" }
        assertEquals(listOf("Snooze 30 min" to "snooze 30", "Not today" to "off today"), LogContext.actionsOf(line.ctx))
        val vm = vms.terminal(app)
        vm.runLineAction(line, 0)
        waitUntil("the script ran with the button's arguments", 20_000) { newLines().any { it.text == "pressed snooze 30 / Snooze 30 min" } }
        // The line shows the button as done, and a second tap runs nothing.
        assertEquals("0", logs.byIds(listOf(line.id)).single().ctx.values[LogContext.ACTION_DONE])
        vm.runLineAction(logs.byIds(listOf(line.id)).single(), 1)
        Thread.sleep(3_000)
        assertEquals(1, newLines().count { it.text.startsWith("pressed ") })
    }

    @Test fun onlyASavedScriptsLineGetsButtons() {
        // Typed at the prompt, there is no script for a tap to run: the buttons go.
        run(code).getOrThrow()
        assertNull(newLines().single { it.text == "Battery at 92%" }.ctx.values[LogContext.ACTIONS])
        // A label is one line, cut at 24 characters; a number as arguments reads as written.
        run("""log("x", { actions = { { "A very long label that goes on and on", 30 } } })""", scriptId = 1L).getOrThrow()
        assertEquals(listOf("A very long label that g" to "30"), LogContext.actionsOf(newLines().single { it.text == "x" }.ctx))
        // More than 3, or one without a label: an error that says so.
        val many = run("""log("y", { actions = { {"a","1"}, {"b","2"}, {"c","3"}, {"d","4"} } })""", scriptId = 1L).exceptionOrNull()?.message.orEmpty()
        assertTrue(many, many.contains("at most 3"))
        val unnamed = run("""log("z", { actions = { { "", "1" } } })""", scriptId = 1L).exceptionOrNull()?.message.orEmpty()
        assertTrue(unnamed, unnamed.contains("needs a label"))
    }
}
