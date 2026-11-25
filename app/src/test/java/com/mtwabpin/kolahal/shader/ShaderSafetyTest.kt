package com.mtwabpin.kolahal.shader

import com.mtwabpin.kolahal.shader.ShaderSafety.Exit
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

    /**
     * A one-off that plays over another app has a mark of its own, on disk before it is compiled: home's mark
     * stays whose it is, and each goes without the other.
     */
    @Test
    fun aShaderOverAnAppHasAMarkOfItsOwn() {
        safety.arm(id)
        safety.overBegin(other)
        assertEquals(id, armed)
        assertEquals(other, store.values["shader.over"])
        assertEquals(listOf("shader.armed", "shader.over"), store.synced)
        // Another one's end leaves it, its own takes it away, and home's mark is as it was.
        safety.overEnd(id)
        assertEquals(other, store.values["shader.over"])
        safety.overEnd(other)
        assertNull(store.values["shader.over"])
        assertEquals(id, armed)
        safety.disarm(id)
        assertNull(armed)
    }

    /**
     * One that was playing over an app when Kolahal crashed or hung is found at the next start and kept from
     * other apps, also after another shader trips. Home's own mark is found apart from it.
     */
    @Test
    fun aCrashOverAnAppKeepsItFromOtherApps() {
        safety.arm(id)
        safety.overBegin(other)
        val next = ShaderSafety(store)
        assertFalse(next.overBarred(other))
        assertEquals(other, next.overRecover(Exit.CRASH))
        assertTrue(next.overBarred(other))
        assertFalse(next.overBarred(id))
        // The mark is gone: a second look finds nothing. Home's is still there to be found.
        assertNull(next.overRecover(Exit.CRASH))
        assertEquals(id, next.recover(Exit.CRASH))
        // Tripped as home's is, it stays kept from apps when another shader takes the trip's place.
        next.trip(other)
        next.trip(id)
        assertFalse(next.isTripped(other))
        assertTrue(next.overBarred(other))
        // A hang and an end nobody knows count as well, and several are kept.
        next.overBegin(id)
        assertEquals(id, ShaderSafety(store).overRecover(Exit.HANG))
        assertTrue(next.overBarred(id) && next.overBarred(other))
        next.overBegin(id)
        assertEquals(id, ShaderSafety(store).overRecover(Exit.UNKNOWN))
        assertEquals("$other\n$id", store.values["shader.over-barred"])
    }

    /** An update, a low memory or a force-stop while one plays over an app isn't its fault. */
    @Test
    fun anotherEndOverAnAppIsNotACrash() {
        safety.overBegin(id)
        val next = ShaderSafety(store)
        assertNull(next.overRecover(Exit.OTHER))
        assertNull(next.overRecover(Exit.CRASH))
        assertFalse(next.overBarred(id))
        assertNull(store.values["shader.over"])
    }

    /** It plays over apps again when the user turns a shader on, as a tripped one is drawn again; a changed file is another shader. */
    @Test
    fun turningAShaderOnLetsItPlayOverAppsAgain() {
        safety.overBegin(id)
        assertEquals(id, safety.overRecover(Exit.CRASH))
        assertTrue(safety.overBarred(id))
        assertFalse(safety.overBarred(other))
        // Not tripped, so nothing else changes: the screens aren't told.
        val seen = safety.version.value
        safety.turnedOn()
        assertFalse(safety.overBarred(id))
        assertEquals(seen, safety.version.value)
    }

    /** Only the last few are kept. */
    @Test
    fun theOldestKeptFromAppsMakesRoom() {
        val ids = (0..ShaderSafety.MAX_BARRED).map { ShaderSafety.id("s$it", "half4 main() {}") }
        for (one in ids) {
            safety.overBegin(one)
            assertEquals(one, safety.overRecover(Exit.CRASH))
        }
        assertFalse(safety.overBarred(ids.first()))
        assertTrue(ids.drop(1).all(safety::overBarred))
    }

    @Test
    fun theShaderThatWasOnIsRemembered() {
        assertNull(safety.last)
        safety.last = "lcd"
        assertEquals("lcd", ShaderSafety(store).last)
    }
}
