package com.mtwabpin.kolahal.assistant

import com.mtwabpin.kolahal.script.SecretStore
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * The asker that sends: a key no header can hold is refused, and the error never carries the key; a
 * redirect isn't followed, so the key never goes on to another address.
 */
class HttpAskerTest {
    private val turns = listOf(Turn(Role.USER, "Hello"))

    /** Keys as they may come from a paste: a zero-width space, a letter that isn't ASCII, a line break, a NUL. */
    private val unfit = listOf("sk-ant-secret-part​-1234", "sk-ant-secrét-part-1234", "sk-ant-secret-part\n1234", "sk-ant-secret\u0000part-1234")

    // No request is ever sent from these tests: one that tried would fail as offline.
    private fun noNetwork() = OkHttpClient.Builder().addInterceptor { throw IOException("not sent in tests") }.build()

    private fun asker(key: String) = HttpAsker({ SecretStore.Stored(key, listOf(it.host)) }, { noNetwork() })

    private fun assertKeyless(key: String, e: AssistantException) {
        val message = e.message.orEmpty()
        assertFalse("the key is in '$message'", key in message)
        // Nor a part of it, before or after the character that can't go.
        for (part in Regex("[A-Za-z0-9-]{6,}").findAll(key).map { it.value }) assertFalse("'$part' is in '$message'", part in message)
        assertEquals(Problem.BAD_KEY, e.problem)
    }

    @Test
    fun aKeyIsPrintableAscii() {
        assertTrue(KeyRule.fits("sk-ant-api03-AbC_123~xyz"))
        assertTrue(KeyRule.fits("AIzaSyD-0123456789"))
        for (key in unfit + listOf("", "two words", "tab\there")) assertFalse(key, KeyRule.fits(key))
    }

    /** A saved key that can't go makes a plain error: the key, or any part of it, is never in it. */
    @Test
    fun anErrorNeverCarriesTheKey() {
        for (s in Services.ALL) for (key in unfit) {
            try {
                asker(key).ask(s, s.default, "system", turns, false)
                fail("${s.id}: no error for an unfit key")
            } catch (e: AssistantException) {
                assertKeyless(key, e)
            }
            try {
                runBlocking { asker(key).send(s, s.default, "system", turns, false) }
                fail("${s.id}: no error for an unfit key")
            } catch (e: AssistantException) {
                assertKeyless(key, e)
            }
        }
    }

    /** OkHttp's own refusal of a header names its whole value: that message is replaced by a fixed one. */
    @Test
    fun okHttpsRefusalIsReplaced() {
        for (s in Services.ALL) for (key in unfit) {
            val call = s.call(key, s.default, "system", turns, false)
            try {
                HttpAsker.request(call, s)
                fail("${s.id}: OkHttp took the header")
            } catch (e: AssistantException) {
                assertKeyless(key, e)
            }
        }
        // A fitting key makes a request, with the key in its header.
        val ok = Anthropic.call("sk-ant-fine-1234", Anthropic.default, "system", turns, false)
        assertEquals("sk-ant-fine-1234", HttpAsker.request(ok, Anthropic).header("x-api-key"))
    }

    /** No key saved: the error says so, and no request goes. */
    @Test
    fun noKeyIsSaid() {
        try {
            HttpAsker({ null }, { noNetwork() }).ask(Anthropic, Anthropic.default, "system", turns, false)
            fail("no error")
        } catch (e: AssistantException) {
            assertEquals(Problem.NO_KEY, e.problem)
        }
    }

    /** The Assistant's client stops at a redirect: the key would go with it, to wherever it points. */
    @Test
    fun aRedirectIsntFollowed() {
        val client = HttpAsker.assistantClient(OkHttpClient())
        assertFalse(client.followRedirects)
        assertFalse(client.followSslRedirects)
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 10_000
            val serving = thread {
                server.accept().use { socket ->
                    val reader = socket.getInputStream().bufferedReader()
                    while (reader.readLine()?.isNotEmpty() == true) Unit
                    // Port 1: nothing listens there, so a client that followed would fail.
                    socket.getOutputStream().apply {
                        write("HTTP/1.1 307 Temporary Redirect\r\nLocation: http://127.0.0.1:1/elsewhere\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        flush()
                    }
                }
            }
            val request = okhttp3.Request.Builder().url("http://127.0.0.1:${server.localPort}/v1/messages").build()
            val code = client.newCall(request).execute().use { it.code }
            assertEquals(307, code)
            serving.join(10_000)
        }
    }
}
