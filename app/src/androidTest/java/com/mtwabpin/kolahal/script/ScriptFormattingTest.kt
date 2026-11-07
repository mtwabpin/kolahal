package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.shownText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** `md.log` and `fmt.*`: styles in the log, plain text everywhere else. */
@RunWith(AndroidJUnit4::class)
class ScriptFormattingTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val name = "fmt${System.nanoTime()}"

    private fun host(scriptId: Long? = null) = HostApi(context, name, scriptId = scriptId, notifyEnabled = false)
    private fun lua(code: String, host: HostApi = host()) = runBlocking { LuaEngine().run(code, name, host, 5_000, true) }.also { host.flush() }
    private fun js(code: String) = host().let { h -> runBlocking { JsEngine().run(code, name, h, 5_000, true) }.also { h.flush() } }

    private fun lines(): List<LogEntry> = logs.recent(limit = 200).filter { it.source == name }

    @After fun tearDown() {
        lines().forEach { logs.delete(it.id) }
        KvStore(context, HostApi.STATUS_SCOPE).set(name, null)
    }


    /** A table that holds itself: logging or encoding it ends with an error for the script, not a crash. */
    @Test fun aTableThatHoldsItselfIsAnErrorNotACrash() {
        val logged = runCatching { lua("local t = {} t.a = t log('loop', t)") }
        val encoded = runCatching { lua("local t = {} t.a = t log(json.encode(t))") }
        // The run fails, and the process (other scripts) carries on: this test would crash with it otherwise.
        assertTrue("$logged", logged.isFailure)
        assertTrue("$encoded", encoded.isFailure)
    }

    /** fmt.table in Lua and JavaScript: a body of rows, the header marked, numbers as written, cells on their side. */
    @Test fun fmtTableMakesABodyOfRows() {
        lua("log('Prices', fmt.table({ { 'Coin', 'Price' }, { 'BTC', 64210 }, { 'ETH', fmt.color('good', '3,118') } }, { header = true, align = 'lr' }))")
        js("log('Prices', fmt.table([['Coin', 'Price'], ['BTC', 64210], ['ETH', fmt.color('good', '3,118')]], { header: true, align: 'lr' }))")
        for (line in lines()) {
            val rows = StyledText.lines(line.ctx.body!!).map { it.row!! }
            assertEquals(listOf(true, false, false), rows.map { it.header })
            assertEquals("Coin\tPrice\nBTC\t64210\nETH\t3,118", StyledText.plain(line.ctx.body!!))
            assertEquals(listOf(false, true), rows[1].cells.map { it.right })
        }
        assertEquals(2, lines().size)
        // Not a list of rows: an error for the script.
        assertTrue(runCatching { lua("log('x', fmt.table('nope'))") }.isFailure)
        assertTrue(runCatching { js("log('x', fmt.table('nope'))") }.isFailure)
    }

    @Test fun mdLogReadsMarkdownInTheTitleAndBody() {
        lua("""md.log("**3** new releases", "- neovim `v0.11.4`\n- termux ~~v0.118~~ v0.119", { url = "https://example.com/r" })""")
        val e = lines().single()
        assertTrue(StyledText.has(e.text))
        assertEquals("3 new releases\n• neovim v0.11.4\n• termux v0.118 v0.119", e.shownText)
        assertEquals("https://example.com/r", e.ctx.url)
    }

    @Test fun logLeavesMarkdownAlone() {
        lua("""log("**not bold**", "- not a list")""")
        val e = lines().single()
        assertFalse(StyledText.has(e.text))
        assertEquals("**not bold**\n- not a list", e.shownText)
    }

    @Test fun styleFunctionsWorkInAnyLine() {
        lua("""log(fmt.bold("42") .. " points", fmt.list({ "one", "two" }, true)) log(fmt.code("x") .. fmt.italic("y") .. fmt.underline("z") .. fmt.strike("w"))""")
        val (first, second) = lines()
        assertEquals("42 points\n1. one\n2. two", first.shownText)
        assertEquals("xyzw", second.shownText)
        assertEquals(
            listOf(setOf(StyledText.Style.CODE), setOf(StyledText.Style.ITALIC), setOf(StyledText.Style.UNDERLINE), setOf(StyledText.Style.STRIKE)),
            StyledText.lines(second.text).single().spans.map { it.styles },
        )
    }

    @Test fun escapedWebTextShowsAsWritten() {
        lua("""md.log("**HN** " .. md.escape("Show HN: *nix tools in __50__ lines"))""")
        assertEquals("HN Show HN: *nix tools in __50__ lines", lines().single().shownText)
    }

    @Test fun plainStripsStyles() {
        assertEquals("a b", lua("""fmt.plain(fmt.bold("a") .. " " .. fmt.code("b"))"""))
    }

    @Test fun coloursByTheThemesRoles() {
        lua("""log(fmt.color("good", "▲ 2%") .. " " .. fmt.color("bad", "▼ 1%"))""")
        val spans = StyledText.lines(lines().single().text).single().spans
        assertEquals(listOf(StyledText.Colour.GOOD, null, StyledText.Colour.BAD), spans.map { it.colour })
        js("""log(fmt.color("warn", "low"))""")
        assertEquals(StyledText.Colour.WARN, StyledText.lines(lines().last().text).single().spans.single().colour)
        // No colour by value: the theme picks every colour.
        val error = runCatching { lua("""fmt.color("#ff0000", "x")""") }.exceptionOrNull()?.message.orEmpty()
        assertTrue(error, error.contains("no colour"))
    }

    @Test fun javaScriptHasTheSame() {
        js("""md.log("*hi*", "1. a\n2. b"); log(fmt.bold("x"), fmt.list(["p", "q"])); log(fmt.plain(fmt.strike("s")))""")
        assertEquals(listOf("hi\n1. a\n2. b", "x\n• p\n• q", "s"), lines().map { it.shownText })
    }

    @Test fun aStatusLineIsPlain() {
        lua("""status(fmt.bold("12") .. " new")""", host(scriptId = 1L))
        assertEquals("12 new", KvStore(context, HostApi.STATUS_SCOPE).get(name))
    }

    @Test fun searchFindsAPhraseAcrossAStyle() {
        lua("""md.log("**${name}3** new releases")""")
        assertEquals(1, logs.search(listOf("${name}3 new")).total)
    }
}
