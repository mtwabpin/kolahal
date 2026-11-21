package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.shownText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** `log(title, body?, ctx?)` and `logs(…)`: every line a script logs is a title, with a body under it. */
@RunWith(AndroidJUnit4::class)
class ScriptTitlesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val name = "titles${System.nanoTime()}"

    private fun host(secrets: HostApi.Secrets? = null) =
        HostApi(context, name, scriptId = null, notifyEnabled = false, secrets = secrets)

    private fun lua(code: String, host: HostApi = host()) =
        runBlocking { LuaEngine().run(code, name, host, 5_000, true) }.also { host.flush() }

    private fun js(code: String, host: HostApi = host()) =
        runBlocking { JsEngine().run(code, name, host, 5_000, true) }.also { host.flush() }

    private fun lines(): List<LogEntry> = logs.recent(limit = 200).filter { it.source == name }

    @After
    fun tearDown() = lines().forEach { logs.delete(it.id) }

    @Test
    fun aTitleWithABodyAndALinkInLua() {
        lua("""log("neovim/neovim", "v0.11.4\nbug fixes", { url = "https://example.com/r" })""")
        val e = lines().single()
        assertEquals("neovim/neovim", e.text)
        assertEquals("v0.11.4\nbug fixes", e.ctx.body)
        assertEquals("https://example.com/r", e.ctx.url)
        assertTrue(LogContext.TITLE in e.ctx.values)
        assertEquals("neovim/neovim\nv0.11.4\nbug fixes", e.shownText)
    }

    @Test
    fun aTableSecondIsStillTheContext() {
        lua("""log("plain") log("linked", { url = "https://example.com/x" })""")
        val (plain, linked) = lines()
        assertNull(plain.ctx.body)
        assertTrue(LogContext.TITLE in plain.ctx.values)
        assertNull(linked.ctx.body)
        assertEquals("https://example.com/x", linked.ctx.url)
    }

    @Test
    fun logsTakesAListOrSeveralTitles() {
        lua("""logs({ "a", "b" }) logs("c", "d")""")
        assertEquals(listOf("a", "b", "c", "d"), lines().map { it.text })
        assertTrue(lines().all { it.ctx.body == null && LogContext.TITLE in it.ctx.values })
    }

    @Test
    fun javaScriptTakesTheSameForms() {
        js("""log("t", "body", { url: "https://example.com" }); log("u", { url: "https://example.com/u" }); logs(["v", "w"]); logs("x")""")
        val out = lines()
        assertEquals(listOf("t", "u", "v", "w", "x"), out.map { it.text })
        assertEquals("body", out[0].ctx.body)
        assertEquals("https://example.com/u", out[1].ctx.url)
        assertNull(out[1].ctx.body)
    }

    @Test
    fun aScriptCantSetTheTitleOrBodyKeysItself() {
        lua("""log("t", { body = "planted", title = "0" })""")
        val e = lines().single()
        assertNull(e.ctx.body)
        assertEquals("1", e.ctx.values[LogContext.TITLE])
    }

    @Test
    fun copyMayTakeTheBody() {
        lua("""log("t", "the body", { copy = "the body" })""")
        assertEquals("the body", lines().single().ctx.copy)
    }

    @Test
    fun aSecretInTheBodyIsHidden() {
        val secret = ScriptMeta.Secret("KEY", listOf("api.example.com"), "")
        lua(
            """log("token", "it is s3cretvalue1")""",
            host(HostApi.Secrets(listOf(secret), mapOf("KEY" to "s3cretvalue1")))
        )
        assertTrue(lines().single().ctx.body.orEmpty().contains("••••"))
        assertTrue("s3cretvalue1" !in lines().single().ctx.body.orEmpty())
    }
}
