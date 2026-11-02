package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** What the engines make of values that aren't strings, and a few log details, from the 0.4.3 review. */
@RunWith(AndroidJUnit4::class)
class ScriptReviewTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val name = "rv${System.nanoTime()}"
    private val id = 900_000_000L + System.nanoTime() % 1_000_000
    private val store = KvStore(context, ScriptRepository.scopeOf(id))
    private val status = KvStore(context, HostApi.STATUS_SCOPE)

    private fun host() = HostApi(context, name, scriptId = id, notifyEnabled = false)
    private fun lua(code: String) = host().let { h -> runBlocking { LuaEngine().run(code, name, h, 5_000, true) }.also { h.flush() } }
    private fun js(code: String) = host().let { h -> runBlocking { JsEngine().run(code, name, h, 5_000, true) }.also { h.flush() } }
    private fun lines(): List<LogEntry> = logs.recent(limit = 100).filter { it.source == name }

    @After fun tearDown() {
        lines().forEach { logs.delete(it.id) }
        store.all().keys.forEach { store.set(it, null) }
        status.set(name, null)
    }

    /** Lua booleans and tables read as text, as `tostring` shows them, not as nil. */
    @Test fun luaBooleansAndTablesAreText() {
        lua("""log(true) log("t", false) log(fmt.bold(false) .. fmt.plain(true)) status(false) log({ a = 1 })""")
        val (first, second, third, fourth) = lines()
        assertEquals("true", first.text)
        assertEquals("false", second.ctx.body)
        assertEquals("falsetrue", com.mtwabpin.kolahal.data.StyledText.plain(third.text))
        assertEquals("""{"a":1}""", fourth.text)
        assertEquals("false", status.get(name))
    }

    /** `store.set("notified", true)` keeps the key (it deleted it, so a script notified on every run). */
    @Test fun luaStoreKeepsABoolean() {
        assertEquals("true", lua("""store.set("k", true) return store.get("k")"""))
        assertEquals("""[1,2]""", lua("""store.set("k", { 1, 2 }) return store.get("k")"""))
        lua("""store.set("k", nil)""")
        assertEquals(null, store.get("k"))
    }

    @Test fun logsWithAnEmptyTableLogsNothing() {
        lua("""logs({})""")
        assertTrue(lines().isEmpty())
        lua("""logs({ "a" })""")
        assertEquals(listOf("a"), lines().map { it.text })
    }

    /** NaN is null in JSON, as JS writes it; a huge whole number isn't clamped to Long. */
    @Test fun jsNanAndHugeNumbersRender() {
        js("""console.log({ avg: 0 / 0 }); log(2 ** 70)""")
        val (first, second) = lines()
        assertTrue(first.text, "\"avg\":null" in first.text)
        assertFalse(second.text, "9223372036854775807" in second.text)
    }

    /** A copy equal to the line's link stays, though the link was normalised (a / added). */
    @Test fun copyEqualToTheLinkStays() {
        lua("""log("Open", { url = "https://example.com", copy = "https://example.com" })""")
        assertEquals("https://example.com", lines().single().ctx.copy)
    }

    /** A quoted word is found in a line's body, where the raw JSON escapes the quotes. */
    @Test fun searchFindsAQuotedWordInABody() {
        val line = logs.add(LogKind.SCRIPT, "said$name", name, LogContext.of(LogContext.BODY to "He said \"ok\" and left"))
        try {
            assertTrue(logs.search(listOf("\"ok\"")).entries.any { it.id == line })
            assertTrue(logs.search(listOf("said$name", "\"ok\"")).entries.any { it.id == line })
        } finally {
            logs.delete(line)
        }
    }
}
