package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.script.ShaderSafety.Exit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How a shader that crashes, hangs or is slow is kept off home ([ShaderSafety]). */
class ShaderSafetyTest {
    /** The store, with the keys written before anything else could happen ([ShaderSafety.Store.set] with `now`). */
    private class Memory : ShaderSafety.Store {
        val values = HashMap<String, String>()
        val synced = ArrayList<String>()

        override fun get(key: String) = values[key]
        override fun set(key: String, value: String?, now: Boolean) {
            if (value == null) values.remove(key) else values[key] = value
            if (now) synced += key
        }
    }

    private val store = Memory()
    private val safety = ShaderSafety(store)
    private val id = ShaderSafety.id("crt", "half4 main() {}")
    private val other = ShaderSafety.id("crt", "half4 main() { return half4(1); }")

    private val armed get() = store.values["shader.armed"]

    @Test
    fun anIdIsTheNameAndTheCode() {
        assertTrue(id, id.startsWith("crt@"))
        assertEquals("crt", ShaderSafety.nameOf(id))
        assertEquals(id, ShaderSafety.id("crt", "half4 main() {}"))
        assertNotEquals(id, other)
        assertNotEquals(id, ShaderSafety.id("lcd", "half4 main() {}"))
    }

    /** The mark is on disk before the first frame, so a crash in it is found at the next start. */
    @Test
    fun aShaderIsMarkedBeforeItIsDrawn() {
        safety.arm(id)
        assertEquals(id, armed)
        assertEquals(listOf("shader.armed"), store.synced)
        // Only once: the second surface is no second write.
        safety.arm(id)
        assertEquals(1, store.synced.size)
    }

    @Test
    fun theMarkGoesWithTheLastSurface() {
        safety.arm(id)
        safety.arm(id)
        safety.disarm(id)
        assertEquals(id, armed)
        safety.disarm(id)
        assertNull(armed)
        // One more than it was given is no harm.
        safety.disarm(id)
        assertNull(armed)
    }

    @Test
    fun aProvenShaderIsNeitherMarkedNorCounted() {
        safety.arm(id)
        assertEquals(id, armed)
        safety.trip(other)
        safety.proven(id)
        assertNull(armed)
        safety.arm(id)
        assertNull(armed)
        assertEquals(1, safety.trip(id))
    }

    /** A changed file is a new shader, so it must prove itself again. */
    @Test
    fun aChangedFileIsMarkedAgain() {
        safety.proven(id)
        safety.arm(other)
        assertEquals(other, armed)
    }

    @Test
    fun theMarkFromACompileThatFailsIsTakenBack() {
        safety.begin(id)
        assertEquals(id, armed)
        assertEquals(listOf("shader.armed"), store.synced)
        safety.cancel(id)
        assertNull(armed)
        // While a surface draws it, it stays.
        safety.arm(id)
        safety.cancel(id)
        assertEquals(id, armed)
        safety.disarm(id)
        assertNull(armed)
    }

    @Test
    fun aCrashOrAHangWhileMarkedIsFoundAtTheNextStart() {
        safety.arm(id)
        // A new process reads the same store.
        val next = ShaderSafety(store)
        assertEquals(id, next.recover(Exit.CRASH))
        // The mark is gone: a second look finds nothing.
        assertNull(next.recover(Exit.CRASH))

        safety.arm(id)
        assertEquals(id, ShaderSafety(store).recover(Exit.HANG))
        safety.arm(id)
        assertEquals(id, ShaderSafety(store).recover(Exit.UNKNOWN))
    }

    /** Kolahal also stops for an update, a low memory or a force-stop: that isn't the shader's fault. */
    @Test
    fun anotherEndIsNotACrash() {
        safety.arm(id)
        val next = ShaderSafety(store)
        assertNull(next.recover(Exit.OTHER))
        assertNull(next.recover(Exit.CRASH))
        assertNull(armed)
    }

    @Test
    fun aTrippedShaderStaysOffUntilItsFileChanges() {
        var seen = safety.version.value
        assertEquals(1, safety.trip(id))
        assertTrue(safety.isTripped(id))
        assertFalse(safety.isTripped(other))
        assertEquals(id, safety.tripped())
        assertTrue(safety.version.value > seen)
        // Told once: the second slow verdict in a row changes nothing.
        seen = safety.version.value
        assertEquals(0, safety.trip(id))
        assertEquals(seen, safety.version.value)
    }

    @Test
    fun turningItOnAgainClearsTheTrip() {
        safety.trip(id)
        safety.turnedOn()
        assertNull(safety.tripped())
        assertFalse(safety.isTripped(id))
        // Nothing to clear, nothing changes.
        val seen = safety.version.value
        safety.turnedOn()
        assertEquals(seen, safety.version.value)
    }

    /** The Shaders screen says why one was turned off, until it is turned on again. */
    @Test
    fun theReasonIsKeptWhileItIsOff() {
        assertNull(safety.why())
        safety.trip(id, "the GPU takes 60 ms")
        assertEquals("the GPU takes 60 ms", ShaderSafety(store).why())
        safety.turnedOn()
        assertNull(safety.why())
        // One turned off without a reason doesn't keep the last one's.
        safety.trip(id, "slow")
        safety.trip(other)
        assertNull(safety.why())
    }

    /** Turned on again after each trip, it gives up at the third, unless it ran well in between. */
    @Test
    fun theThirdTripInARowIsTheLast() {
        assertEquals(1, safety.trip(id))
        safety.turnedOn()
        assertEquals(2, safety.trip(id))
        safety.turnedOn()
        assertEquals(3, safety.trip(id))
        assertEquals(ShaderSafety.MAX_TRIPS, 3)

        safety.turnedOn()
        safety.proven(id)
        assertEquals(1, safety.trip(id))
    }

    @Test
    fun theShaderThatWasOnIsRemembered() {
        assertNull(safety.last)
        safety.last = "lcd"
        assertEquals("lcd", ShaderSafety(store).last)
    }
}
