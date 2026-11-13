package com.mtwabpin.kolahal.script

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * One run's secrets: a script holds only a mark, the value goes only to its sites over https, and
 * nothing the run shows or stores holds it. (The device's ScriptSecretsTest checks the same through
 * HostApi and real requests.)
 */
class RunSecretsTest {
    private val key = ScriptMeta.Secret("KEY", listOf("api.example.com"), "")
    private val value = "s3cr3t/value 1"

    /** Stands in for Android's Uri.encode, which the JVM doesn't have: a space is %20 there, not +. */
    private val uriEncode: (String) -> String = { java.net.URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun run(values: Map<String, String> = mapOf("KEY" to value)) = RunSecrets(HostApi.Secrets(listOf(key), values), uriEncode)

    private fun refused(part: String, block: () -> Unit) {
        val e = assertThrows(IOException::class.java) { block() }
        assertTrue(e.message, e.message!!.contains(part))
        assertFalse("the error must not hold the value", e.message!!.contains(value))
    }

    @Test fun aMarkStandsForAValueThatIsSet() {
        val mark = run().mark("KEY")!!
        assertTrue(mark, Regex("__kolahal_secret_KEY_[0-9a-f]{12}__").matches(mark))
        assertNull("no value set yet", run(emptyMap()).mark("KEY"))
    }

    @Test fun onlyADeclaredNameHasAMark() {
        val undeclared = assertThrows(IllegalStateException::class.java) { run().mark("OTHER") }
        assertEquals("secrets.get(\"OTHER\") needs `@secret OTHER <site> <description>` at the top of the script", undeclared.message)
        // Code typed at the prompt has no secrets at all.
        val prompt = assertThrows(IllegalStateException::class.java) { RunSecrets(null, uriEncode).mark("KEY") }
        assertEquals("secrets.get only works in saved scripts", prompt.message)
    }

    /** A mark holds its run's own number, so another run neither fills it nor takes it for one of its own. */
    @Test fun aMarkMeansNothingInAnotherRun() {
        val first = run()
        val second = run()
        val mark = first.mark("KEY")!!
        assertNotEquals(mark, second.mark("KEY"))
        assertEquals("k=$mark", second.fill("k=$mark", "https://api.example.com/".toHttpUrl()))
        assertEquals(mark, second.redact(mark))
    }

    @Test fun aMarkBecomesItsValueForItsSitesOverHttps() {
        val s = run()
        val mark = s.mark("KEY")!!
        assertEquals("Bearer $value", s.fill("Bearer $mark", "https://api.example.com/user".toHttpUrl()))
        assertEquals("k=$value&again=$value", s.fill("k=$mark&again=$mark", "https://eu.api.example.com/".toHttpUrl()))
        refused("KEY may only be sent to api.example.com, not evil.example") { s.fill("k=$mark", "https://evil.example/".toHttpUrl()) }
        refused("KEY may only be sent over https, not to http://api.example.com") { s.fill("k=$mark", "http://api.example.com/".toHttpUrl()) }
        // Text without a mark goes anywhere as it is.
        assertEquals("plain", s.fill("plain", "http://evil.example/".toHttpUrl()))
    }

    /** `https://<mark>.api.example.com` ends in an allowed site, but the address lookup would carry the value. */
    @Test fun aMarkCantBePartOfASitesName() {
        val s = run()
        val mark = s.mark("KEY")!!
        refused("KEY can't be part of a site's name") { s.fill("k=$mark", "https://$mark.api.example.com/".toHttpUrl()) }
        refused("KEY can't be part of a site's name") { s.fill("k=$mark", "https://$mark@api.example.com/".toHttpUrl()) }
    }

    /** A mark of this run for a name with no value, or a name the script doesn't declare, is not filled. It is still masked. */
    @Test fun aMarkWithNoValueBehindItIsLeftAlone() {
        val unset = ScriptMeta.Secret("UNSET", listOf("api.example.com"), "")
        val s = RunSecrets(HostApi.Secrets(listOf(key, unset), mapOf("KEY" to value)), uriEncode)
        assertNull(s.mark("UNSET"))
        val mark = s.mark("KEY")!!
        for (name in listOf("UNSET", "NOPE")) {
            val made = mark.replace("KEY", name)
            assertEquals(name, "k=$made", s.fill("k=$made", "https://api.example.com/".toHttpUrl()))
            assertEquals(name, "k=••••", s.redact("k=$made"))
        }
    }

    @Test fun aValueIsMaskedInEveryFormItTakes() {
        val s = run()
        assertEquals("••••", s.redact(value))
        assertEquals("Bearer ••••", s.redact("Bearer ${s.mark("KEY")}"))
        assertEquals("k=••••&x=1", s.redact("k=${java.net.URLEncoder.encode(value, "UTF-8")}&x=1"))
        assertEquals("k=••••", s.redact("k=${uriEncode(value)}"))
        assertEquals("{\"k\":\"••••\"}", s.redact("{\"k\":\"${value.replace("/", "\\/")}\"}"))
        assertEquals("•••• and ••••", s.redact("$value and $value"))
        assertEquals("nothing here", s.redact("nothing here"))
    }

    /** Values shorter than [SecretStore.MIN_LENGTH] can't be set: they would mask ordinary text. */
    @Test fun aValueTooShortToTellApartIsNotMasked() {
        val s = run(mapOf("KEY" to "abc"))
        assertEquals("abcdef", s.redact("abcdef"))
        s.check("evil.example", listOf("http://evil.example/?k=abc"), https = false)
    }

    @Test fun anErrorIsMaskedAndKeepsItsKind() {
        val s = run()
        val io = s.masked(IOException("bad header: $value", RuntimeException(value)))
        assertTrue(io is IOException)
        assertEquals("bad header: ••••", io.message)
        assertNull("the cause would carry the value on", io.cause)
        assertTrue(s.masked(IllegalArgumentException("x $value")) is IllegalArgumentException)
        val other = s.masked(UnsupportedOperationException("x $value"))
        assertEquals(IllegalStateException::class.java, other.javaClass)
        assertEquals("x ••••", other.message)
    }

    @Test fun anErrorWithoutASecretIsItself() {
        val s = run()
        val plain = IOException("timeout")
        assertSame(plain, s.masked(plain))
        val silent = IllegalStateException()
        assertSame(silent, s.masked(silent))
        // A cancelled run stays cancelled, whatever its message.
        val cancelled = kotlinx.coroutines.CancellationException("stopped $value")
        assertSame(cancelled, s.masked(cancelled))
    }

    @Test fun aRequestThatCarriesAValueElsewhereStops() {
        val s = run()
        s.check("api.example.com", listOf("https://api.example.com/?k=x", value), https = true)
        s.check("eu.api.example.com", listOf(value), https = true)
        refused("KEY may only be sent to api.example.com, not evil.example") { s.check("evil.example", listOf("https://evil.example/", value), https = true) }
        refused("KEY may only be sent over https, not to http://api.example.com") { s.check("api.example.com", listOf(value), https = false) }
        // Encoded for a URL or escaped for JSON, it is still the value.
        refused("may only be sent to") { s.check("evil.example", listOf("https://evil.example/?k=${uriEncode(value)}"), https = true) }
        refused("may only be sent to") { s.check("evil.example", listOf("{\"k\":\"${value.replace("/", "\\/")}\"}"), https = true) }
    }

    @Test fun aRequestWithoutAValueGoesAnywhere() {
        run().check("evil.example", listOf("http://evil.example/?q=weather", "body"), https = false)
        run(emptyMap()).check("evil.example", listOf(value), https = false)
        RunSecrets(null, uriEncode).check("evil.example", listOf(value), https = false)
    }

    /** A store goes into backups, so a key made from a URL never holds a value. */
    @Test fun aStoreKeyForAUrlNeverHoldsAValue() {
        val s = run()
        assertEquals("http.cache:https://example.com/feed", s.urlKey("http.cache:", "https://example.com/feed"))
        val hashed = s.urlKey("http.cache:", "https://api.example.com/?k=$value")
        assertTrue(hashed, Regex("http\\.cache:#[0-9a-f]{64}").matches(hashed))
        assertNotEquals(hashed, s.urlKey("http.cache:", "https://api.example.com/?k=$value&page=2"))
    }

    /** A mark counts by its name, not by its run's number: the next run finds what this one stored. */
    @Test fun aStoreKeyIsTheSameInEveryRun() {
        val first = run()
        val second = run()
        val a = first.urlKey("rss.seen:", "https://api.example.com/feed?k=${first.mark("KEY")}")
        val b = second.urlKey("rss.seen:", "https://api.example.com/feed?k=${second.mark("KEY")}")
        assertTrue(a, Regex("rss\\.seen:#[0-9a-f]{64}").matches(a))
        assertEquals(a, b)
    }
}
