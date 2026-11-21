package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * What a script may reach is what its `@uses` line declares: private data (notifications, the
 * clipboard, the app list) and this phone or the local network are refused otherwise.
 */
@RunWith(AndroidJUnit4::class)
class ScriptPermissionsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    /** A saved script's host; [uses] as its `@uses` line declares them (none: an empty line). */
    private fun script(vararg uses: String) = HostApi(
        context,
        "permtest",
        scriptId = null,
        notifyEnabled = false,
        grants = com.mtwabpin.kolahal.script.ScriptAccess.Grants(
            uses.toSet(),
            uses.associateWith { true })
    )

    /** Code typed at the prompt: may use everything. */
    private fun prompt() = HostApi(context, "repl", scriptId = null, notifyEnabled = false)

    private fun luaError(code: String, host: HostApi): String? =
        runCatching {
            runBlocking {
                LuaEngine().run(
                    code,
                    "t",
                    host,
                    10_000,
                    true
                )
            }
        }.exceptionOrNull()?.let { it.message ?: it.toString() }

    private fun jsError(code: String, host: HostApi): String? =
        runCatching {
            runBlocking {
                JsEngine().run(
                    code,
                    "t",
                    host,
                    10_000,
                    true
                )
            }
        }.exceptionOrNull()?.let { it.message ?: it.toString() }

    @Test
    fun privateDataNeedsItsUsesLine() {
        for ((call, what) in listOf(
            "notifications.recent(5)" to "notifications",
            "clipboard.get()" to "clipboard",
            "apps.list()" to "apps"
        )) {
            val error = luaError(call, script())
            assertTrue("$call: $error", error.orEmpty().contains("needs `@uses $what`"))
            // Declaring something else doesn't help.
            assertTrue(
                call,
                luaError(call, script("dnd")).orEmpty().contains("needs `@uses $what`")
            )
        }
        assertTrue(
            jsError("clipboard.set('x')", script()).orEmpty().contains("needs `@uses clipboard`")
        )
        assertTrue(
            jsError("notifications.recent(5)", script()).orEmpty()
                .contains("needs `@uses notifications`")
        )
    }

    @Test
    fun declaredUsesAreAllowed() {
        assertNull(luaError("clipboard.get()", script("clipboard")))
        assertNull(luaError("apps.list()", script("apps")))
        assertNull(luaError("apps.list()", prompt()))
    }

    /** A web server on this phone that counts the connections it gets, and keeps the Content-Type of each. */
    private class TinyServer : AutoCloseable {
        private val socket = ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))
        val accepted = AtomicInteger()
        val contentTypes = java.util.concurrent.CopyOnWriteArrayList<String?>()
        val url = "http://127.0.0.1:${socket.localPort}/"

        init {
            thread(isDaemon = true) {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    accepted.incrementAndGet()
                    client.use { c ->
                        val reader = c.getInputStream().bufferedReader()
                        val head = generateSequence {
                            reader.readLine()?.takeIf { it.isNotEmpty() }
                        }.toList()

                        fun header(name: String) =
                            head.firstOrNull { it.startsWith("$name:", ignoreCase = true) }
                                ?.substringAfter(':')?.trim()
                        contentTypes += header("Content-Type")
                        // Its body too: closing with one unread would reset the connection under the answer.
                        repeat(header("Content-Length")?.toIntOrNull() ?: 0) { reader.read() }
                        c.getOutputStream()
                            .write("HTTP/1.1 200 OK\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok".toByteArray())
                    }
                }
            }
        }

        override fun close() = socket.close()
    }

    /** A Content-Type header gets through as given: on a body, or by itself when there's no body to carry it. */
    @Test
    fun aContentTypeHeaderIsSentWithOrWithoutABody() {
        TinyServer().use { server ->
            val json = "headers = { [\"Content-Type\"] = \"application/json\" }"
            val cases = listOf(
                "http.get('${server.url}', { [\"content-type\"] = \"application/json\" })",
                "http.request{ url = '${server.url}', method = 'POST', $json }",
                "http.request{ url = '${server.url}', method = 'POST', body = 'x', $json }",
                "http.request{ url = '${server.url}', method = 'POST', body = 'x', contentType = 'text/csv', $json }",
            )
            for (call in cases) assertNull(call, luaError(call, script()))
            assertEquals(
                listOf(
                    "application/json",
                    "application/json",
                    "application/json",
                    "text/csv"
                ), server.contentTypes.map { it?.substringBefore(';') })
        }
    }

    /** Every address is a request like any other, with nothing to declare, and an old `@uses lan` changes nothing. */
    @Test
    fun aRequestGoesWhereItIsSent() {
        TinyServer().use { server ->
            for (host in listOf(
                script(),
                script("lan"),
                prompt()
            )) assertNull(luaError("http.get('${server.url}')", host))
            assertEquals(3, server.accepted.get())
        }
    }
}
