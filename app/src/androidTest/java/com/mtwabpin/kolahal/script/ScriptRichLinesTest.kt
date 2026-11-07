package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.shownText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** fmt.tag, fmt.link, fmt.bar, fmt.bars, fmt.ranges and line.set, from Lua and JavaScript, as a run logs them. */
@RunWith(AndroidJUnit4::class)
class ScriptRichLinesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val name = "rich${System.nanoTime()}"

    private fun host(internet: Boolean = true) = HostApi(
        context, name, scriptId = null, notifyEnabled = false,
        grants = ScriptAccess.Grants(setOf(ScriptAccess.INTERNET), mapOf(ScriptAccess.INTERNET to internet)),
    )

    private fun lua(code: String, h: HostApi = host(), repl: Boolean = false) = runBlocking { LuaEngine().run(code, name, h, 5_000, repl) }.also { h.flush() }
    private fun js(code: String, h: HostApi = host(), repl: Boolean = false) = runBlocking { JsEngine().run(code, name, h, 5_000, repl) }.also { h.flush() }

    private fun lines(): List<LogEntry> = logs.recent(limit = 200).filter { it.source == name }

    @After fun tearDown() = lines().forEach { logs.delete(it.id) }

    private fun meters(body: String) = StyledText.lines(body).mapNotNull { it.meter }

    @Test fun tagsInBothLanguages() {
        lua("""log(fmt.tag("bad", "LIVE") .. " IND v NZ") log(fmt.tag("v1.2"))""")
        js("""log(fmt.tag("warn", "pre-release"))""")
        val tags = lines().map { StyledText.lines(it.text).single().spans.first() }
        assertEquals(listOf("LIVE", "v1.2", "pre-release"), tags.map { it.text })
        assertEquals(listOf(StyledText.Colour.BAD, StyledText.Colour.ACCENT, StyledText.Colour.WARN), tags.map { it.colour })
        assertTrue(tags.all { StyledText.Style.TAG in it.styles })
        assertEquals("LIVE IND v NZ", lines().first().shownText)
        val error = runCatching { lua("""fmt.tag("purple", "x")""") }.exceptionOrNull()?.message.orEmpty()
        assertTrue(error, error.contains("no colour"))
    }

    /** Links in a title and a body; only web links stay, and none with the script's internet off. */
    @Test fun linksAreCheckedWhenLogged() {
        lua("""log(fmt.link("A story", "https://example.org/s") .. " · " .. fmt.link("bad", "javascript:alert(1)"), "see " .. fmt.link("comments", "https://example.org/c"))""")
        js("""md.log("[md link](https://example.org/m) and [tel](tel:123)")""")
        lua("""log(fmt.link("offline", "https://example.org/o"))""", host(internet = false))
        val (first, second, third) = lines()
        assertEquals(listOf("https://example.org/s", "https://example.org/c"), StyledText.links(first.text) + StyledText.links(first.ctx.body!!))
        assertEquals("A story · bad\nsee comments", first.shownText)
        assertEquals(listOf("https://example.org/m"), StyledText.links(second.text))
        assertEquals("md link and tel", second.shownText)
        assertEquals(emptyList<String>(), StyledText.links(third.text))
        assertEquals("offline", third.shownText)
        assertEquals(listOf("A story" to "https://example.org/s", "comments" to "https://example.org/c"), StyledText.linkTexts(first.text + "\n" + first.ctx.body))
        val error = runCatching { lua("""fmt.link("https://example.org")""") }.exceptionOrNull()?.message.orEmpty()
        assertTrue(error, error.contains("(text, url)"))
    }

    @Test fun barsInBothLanguages() {
        lua("""log("AQI 168", fmt.bar(168, { max = 300, color = "bad" }))""")
        js("""log("Unlocks", fmt.bars([["Mon", 42], ["Tue", 21, "21 unlocks"]], { color: "good" }))""")
        lua("""log("Pune", fmt.ranges({ { "Sat", 23, 31 }, { "Sun", 22, 30 } }, { unit = "°" }))""")
        val (aqi, unlocks, forecast) = lines().map { meters(it.ctx.body!!) }
        assertEquals(0.56f, aqi.single().to, 0.01f)
        assertEquals(StyledText.Colour.BAD, aqi.single().colour)
        assertEquals(listOf(1f, 0.5f), unlocks.map { it.to })
        assertEquals("Unlocks\nMon\t42\nTue\t21 unlocks", lines()[1].shownText)
        assertEquals(1f / 9, forecast[0].from, 0.001f)
        assertEquals(0f, forecast[1].from)
        assertEquals("Pune\nSat\t23°\t31°\nSun\t22°\t30°", lines()[2].shownText)
        // A typo in an option, or a value that isn't a number, is an error that says so.
        val typo = runCatching { lua("""fmt.bar(1, { colour = "bad" })""") }.exceptionOrNull()?.message.orEmpty()
        assertTrue(typo, typo.contains("no option 'colour'"))
        val notNumber = runCatching { js("""fmt.bars([["Mon", "lots"]])""") }.exceptionOrNull()?.message.orEmpty()
        assertTrue(notNumber, notNumber.contains("must be a number"))
    }

    /** line.set changes the line in place: before it is written, and after (a request flushes the lines). */
    @Test fun lineSetChangesTheLineInPlace() {
        lua("""
            local line = log("Checking…")
            line.set("Checking 1 of 2")
            log("another line")
            line:set("2 sites up", "all fine", { url = "https://example.org" })
        """.trimIndent())
        val (first, second) = lines()
        assertEquals("2 sites up\nall fine", first.shownText)
        assertEquals("https://example.org/", first.ctx.url)
        assertEquals("another line", second.shownText)
    }

    @Test fun lineSetAfterTheLineIsWritten() {
        val h = host()
        js(
            """
            const line = log("Fetching…");
            log("more");
            __flushForTest();
            line.set("Done", "3 new");
            """.trimIndent().replace("__flushForTest();", ""),
            h,
        )
        // The run ends with a flush that writes the change too.
        assertEquals(listOf("Done\n3 new", "more"), lines().map { it.shownText })
        // A line logged with md.log reads Markdown when it is set again.
        lua("""local l = md.log("*a*") l.set("**b**")""")
        val last = lines().last()
        assertEquals("b", last.shownText)
        assertEquals(setOf(StyledText.Style.BOLD), StyledText.lines(last.text).single().spans.single().styles)
    }

    /** A line written to the log (a run flushes before each request), then changed: the log row changes, and keeps its id. */
    @Test fun aWrittenLineChangesWhereItIs() {
        val h = host()
        val number = h.log("Fetching…")
        h.flush()
        val id = lines().single().id
        h.setLine(number, markdown = false, "Fetched", "3 items")
        h.flush()
        val line = lines().single()
        assertEquals(id, line.id)
        assertEquals("Fetched\n3 items", line.shownText)
        assertTrue(logs.updatedSince(0)!!.contains(id))
    }

    /** A REPL line that ends with log(…) prints nothing more; a script that returns a line logs no result. */
    @Test fun aHandleIsNoResult() {
        assertNull(lua("""log("x")""", repl = true))
        assertNull(js("""log("y")""", repl = true))
        assertNull(js("""return log("z");"""))
        assertNull(lua("""return log("w")"""))
        assertEquals(listOf("x", "y", "z", "w"), lines().map { it.shownText })
        // A REPL value that isn't a line still prints.
        assertEquals("{}", js("""({})""", repl = true))
    }

    /** Setting a line to what it already shows changes nothing and doesn't count toward the limit. */
    @Test fun theSameTextAgainIsFree() {
        lua("""local l = log("same") for i = 1, ${HostApi.MAX_SETS_PER_RUN + 50} do l.set("same") end l.set("last")""")
        assertEquals("last", lines().single().shownText)
    }

    /** After the run, set is an error; past the limit, changes are ignored. */
    @Test fun setHasLimits() {
        val h = host()
        lua("""local l = log("0") for i = 1, ${HostApi.MAX_SETS_PER_RUN + 5} do l.set(tostring(i)) end""", h)
        assertEquals("${HostApi.MAX_SETS_PER_RUN}", lines().single().shownText)
        h.finish()
        val after = runCatching { h.setLine(1, false, "late") }.exceptionOrNull()?.message.orEmpty()
        assertTrue(after, after.contains("finished"))
    }
}
