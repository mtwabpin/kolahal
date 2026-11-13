package com.mtwabpin.kolahal.data

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A script's store has limits: [KvStore.MAX_KEYS] keys and [KvStore.MAX_SCOPE] bytes. Its size is
 * counted once and then kept up to date by each write, so these hold as a loop of store.set goes on.
 */
@RunWith(AndroidJUnit4::class)
class KvStoreLimitsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = "script:kvlimits-${System.nanoTime()}"
    private val kv = KvStore(context, scope)

    @After fun tearDown() {
        kv.deleteStartingWith("")
    }

    @Test fun aFullStoreTakesNoNewKeyUntilOneGoes() {
        for (i in 0 until KvStore.MAX_KEYS) kv.set("k$i", "v")
        val full = assertThrows(IllegalArgumentException::class.java) { kv.set("one-more", "v") }
        assertEquals("store full (over ${KvStore.MAX_KEYS} keys)", full.message)
        // A key it has can still change.
        kv.set("k1", "changed")
        assertEquals("changed", kv.get("k1"))
        // One gone makes room at once.
        kv.set("k2", null)
        kv.set("one-more", "v")
        assertEquals("v", kv.get("one-more"))
        assertEquals(KvStore.MAX_KEYS, kv.all().size)
    }

    @Test fun theBytesCountAsValuesChange() {
        val big = "x".repeat(KvStore.MAX_VALUE - 16)
        val fits = (KvStore.MAX_SCOPE / KvStore.MAX_VALUE).toInt()
        for (i in 0 until fits) kv.set("b$i", big)
        assertThrows(IllegalArgumentException::class.java) { kv.set("b-more", big) }
        // A value made smaller frees its bytes.
        kv.set("b0", "small")
        kv.set("b-more", big)
        assertEquals(big.length, kv.get("b-more")?.length)
    }
}
