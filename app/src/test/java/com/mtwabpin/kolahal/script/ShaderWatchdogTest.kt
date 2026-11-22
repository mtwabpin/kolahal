package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.script.ShaderWatchdog.Verdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a shader is too slow, and when it has proven itself ([ShaderWatchdog]), and when its time moves ([ShaderClock]). */
class ShaderWatchdogTest {
    private val ms = 1_000_000L
    private val budget = 33 * ms

    /** Gives [count] frames of [cost] ns on the GPU and [total] ns in all; returns the last verdict. */
    private fun ShaderWatchdog.frames(count: Int, cost: Long, total: Long = cost): List<Verdict> =
        (1..count).map { frame(cost, total) }

    @Test
    fun aFastShaderIsProvenOnceAndNeverStopped() {
        val dog = ShaderWatchdog(budget)
        val verdicts = dog.frames(ShaderWatchdog.WARMUP + ShaderWatchdog.PROVEN + 100, 8 * ms)
        assertEquals(1, verdicts.count { it == Verdict.Proven })
        assertTrue(verdicts.none { it is Verdict.Slow || it is Verdict.Stalled })
        // Proven at the PROVEN-th good frame after the warm-up.
        assertEquals(ShaderWatchdog.WARMUP + ShaderWatchdog.PROVEN - 1, verdicts.indexOf(Verdict.Proven))
    }

    /** The GPU builds the shader's program in the first frames: they don't count, however slow. */
    @Test
    fun theFirstFramesAreNotCounted() {
        val dog = ShaderWatchdog(budget)
        assertTrue(dog.frames(ShaderWatchdog.WARMUP, 400 * ms, 900 * ms).all { it == Verdict.Fine })
        assertTrue(dog.frames(ShaderWatchdog.WINDOW + 60, 8 * ms).none { it is Verdict.Slow || it is Verdict.Stalled })
    }

    @Test
    fun aSlowShaderIsStoppedAfterAWindow() {
        val dog = ShaderWatchdog(budget)
        dog.frames(ShaderWatchdog.WARMUP, 8 * ms)
        val verdicts = dog.frames(ShaderWatchdog.WINDOW, 60 * ms)
        // Not before the window is full, and then at once.
        assertTrue(verdicts.dropLast(1).none { it is Verdict.Slow })
        val slow = verdicts.last() as Verdict.Slow
        assertEquals(60 * ms, slow.frameNs)
    }

    /** One slow frame in twenty is noise; one in five isn't. */
    @Test
    fun aFewSlowFramesAreForgiven() {
        val dog = ShaderWatchdog(budget)
        dog.frames(ShaderWatchdog.WARMUP, 8 * ms)
        repeat(6) { dog.frames(19, 8 * ms); dog.frames(1, 100 * ms, 100 * ms) }
        assertEquals(Verdict.Fine, dog.frame(8 * ms, 8 * ms).let { if (it == Verdict.Proven) Verdict.Fine else it })
        val heavy = ShaderWatchdog(budget)
        heavy.frames(ShaderWatchdog.WARMUP, 8 * ms)
        var last: Verdict = Verdict.Fine
        repeat(20) { last = heavy.frames(4, 8 * ms).last(); last = heavy.frame(100 * ms, 100 * ms) }
        assertTrue("$last", last is Verdict.Slow)
    }

    @Test
    fun threeStallsTurnItOff() {
        val dog = ShaderWatchdog(budget)
        dog.frames(ShaderWatchdog.WARMUP, 8 * ms)
        assertEquals(Verdict.Fine, dog.frame(8 * ms, 300 * ms))
        assertEquals(Verdict.Fine, dog.frame(8 * ms, 400 * ms))
        assertEquals(Verdict.Stalled(500 * ms), dog.frame(8 * ms, 500 * ms))
    }

    /** Stalls are forgotten as they leave the window. */
    @Test
    fun oldStallsAreForgotten() {
        val dog = ShaderWatchdog(budget)
        dog.frames(ShaderWatchdog.WARMUP, 8 * ms)
        dog.frame(8 * ms, 300 * ms)
        dog.frames(ShaderWatchdog.WINDOW, 8 * ms)
        dog.frame(8 * ms, 300 * ms)
        assertEquals(Verdict.Fine, dog.frame(8 * ms, 300 * ms).let { if (it == Verdict.Proven) Verdict.Fine else it })
    }

    @Test
    fun theBudgetIsTwoFrames() {
        assertEquals(33_333_333L, ShaderWatchdog.budgetFor(60f))
        assertEquals(16_666_666L, ShaderWatchdog.budgetFor(120f))
        // A nonsense rate doesn't make the budget endless or nothing.
        assertEquals(ShaderWatchdog.budgetFor(30f), ShaderWatchdog.budgetFor(0f))
        assertEquals(ShaderWatchdog.budgetFor(240f), ShaderWatchdog.budgetFor(1000f))
    }

    private val calm = ShaderClock.Phone(
        batterySaver = false,
        animationsOff = false,
        hot = false,
        charging = false,
        batteryPercent = 80,
    )

    @Test
    fun timeMovesWhileHomeIsUsed() {
        assertTrue(ShaderClock.moves(calm, 0))
        assertTrue(ShaderClock.moves(calm, ShaderClock.IDLE_MS - 1))
    }

    /** It stops after a minute without a touch, and where the phone should rest, but a charger keeps it going. */
    @Test
    fun timeStopsWhenTheScreenIsLeftAloneOrThePhoneShouldRest() {
        assertFalse(ShaderClock.moves(calm, ShaderClock.IDLE_MS))
        assertFalse(ShaderClock.moves(calm.copy(batteryPercent = ShaderClock.LOW_BATTERY - 1), 0))
        val charging = calm.copy(charging = true, batteryPercent = 3)
        assertTrue(ShaderClock.moves(charging, 10 * ShaderClock.IDLE_MS))
        // But nothing beats battery saver, the accessibility setting or a hot phone.
        assertFalse(ShaderClock.moves(charging.copy(batterySaver = true), 0))
        assertFalse(ShaderClock.moves(charging.copy(animationsOff = true), 0))
        assertFalse(ShaderClock.moves(charging.copy(hot = true), 0))
    }
}
