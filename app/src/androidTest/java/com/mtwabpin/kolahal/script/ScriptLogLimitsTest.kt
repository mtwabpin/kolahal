package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** What one script run may put in the log: lines, characters and commands. */
@RunWith(AndroidJUnit4::class)
class ScriptLogLimitsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val name = "limits${System.nanoTime()}"

    private fun host(interactive: Boolean = false, byScript: Boolean = false) =
        HostApi(context, name, scriptId = null, notifyEnabled = false, interactive = interactive, byScript = byScript)

    /** This test's lines, oldest first. */
    private fun lines(): List<LogEntry> = logs.recent(limit = 2_000).filter { it.source == name }

    @After fun tearDown() = lines().forEach { logs.delete(it.id) }

    @Test fun aRunLogsAtMost500Lines() {
        val host = host()
        repeat(600) { host.log("line $it") }
        host.flush()
        val out = lines()
        assertEquals(501, out.size)
        assertEquals("line 499", out[499].text)
        assertEquals("More than 500 lines in one run: the rest aren't logged", out.last().text)
    }

    @Test fun errorLinesCountToo() {
        val host = host()
        repeat(300) { host.log("line $it") }
        repeat(300) { host.logError("error $it") }
        host.flush()
        val out = lines()
        assertEquals(501, out.size)
        assertEquals(200, out.count { it.kind == LogKind.ERROR })
    }

    @Test fun consoleErrorInJavaScriptCountsToo() {
        val host = host()
        runBlocking { JsEngine().run("for (let i = 0; i < 600; i++) console.error('e' + i)", name, host, 5_000, true) }
        host.flush()
        val out = lines()
        assertEquals(501, out.size)
        assertEquals(500, out.count { it.kind == LogKind.ERROR })
    }

    @Test fun aRunLogsAtMost256KCharacters() {
        val host = host()
        repeat(40) { host.log("x".repeat(10_000)) }
        host.flush()
        val out = lines()
        assertEquals(27, out.size)
        assertEquals("More than 256K characters in one run: the rest aren't logged", out.last().text)
    }

    /**
     * line.set counts toward the run's characters too: growing lines past the limit stops there, with
     * the same line saying so. And a line keeps only what the log keeps, however long the text set.
     */
    @Test fun lineSetStopsAtTheRunsCharacters() {
        val host = host()
        val numbers = (1..10).map { host.log("short $it") }
        numbers.forEach { host.setLine(it, markdown = false, raw = "y".repeat(40_000)) }
        host.setLine(numbers.first(), markdown = false, raw = "z".repeat(5_000_000))
        host.flush()
        val out = lines()
        assertEquals("More than 256K characters in one run: the rest aren't logged", out.last().text)
        // 6 lines of 40,000 fit in 256K with the rest; the 7th set is refused, and so is everything after.
        assertEquals(6, out.count { it.text == "y".repeat(40_000) })
        assertEquals("short 7", out[6].text)
    }

    @Test fun aLinesLinkCountsTowardTheCharacters() {
        val host = host()
        val link = "https://example.com/" + "a".repeat(10_000)
        repeat(40) { host.log("x", ctx = mapOf("url" to link)) }
        host.flush()
        val out = lines()
        assertEquals(27, out.size)
        assertEquals(link, out.first().ctx.url)
        assertEquals("More than 256K characters in one run: the rest aren't logged", out.last().text)
    }

    @Test fun aRunSendsAtMostTenCommands() {
        val host = host(interactive = true)
        val sent = (1..15).map { host.runCommand("date") }
        host.flush()
        assertEquals(List(10) { true } + List(5) { false }, sent)
        assertEquals(listOf("launcher.run: more than 10 commands in one run; the rest don't run"), lines().map { it.text })
    }

    @Test fun aScriptAnotherScriptStartedSendsNoCommands() {
        val host = host(interactive = true, byScript = true)
        assertFalse(host.runCommand("date"))
        host.flush()
        assertEquals(listOf("launcher.run isn't available to a script another script started"), lines().map { it.text })
    }
}
