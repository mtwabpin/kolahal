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

    /** Gives [count] frames that take the GPU [gpu] ns (null: not measured) and [total] ns in all. */
    private fun ShaderWatchdog.frames(count: Int, gpu: Long?, total: Long = gpu ?: 0L): List<Verdict> =
        (1..count).map { frame(gpu, total) }

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

    /** It is turned off when it stays slow, not for a moment of it. */
    @Test
    fun aShaderThatStaysSlowIsStopped() {
        val dog = ShaderWatchdog(budget)
        dog.frames(ShaderWatchdog.WARMUP, 8 * ms)
        // The window fills, and it must then be over the budget for SLOW_FOR frames in a row.
        val verdicts = dog.frames(ShaderWatchdog.WINDOW + ShaderWatchdog.SLOW_FOR - 1, 60 * ms)
        assertTrue(verdicts.dropLast(1).none { it is Verdict.Slow })
        assertEquals(60 * ms, (verdicts.last() as Verdict.Slow).frameNs)
    }

    /** A slow moment isn't enough: what came before and after it is fast, and the count starts again. */
    @Test
    fun aSlowMomentIsForgiven() {
        val dog = ShaderWatchdog(budget)
        dog.frames(ShaderWatchdog.WARMUP, 8 * ms)
        dog.frames(ShaderWatchdog.WINDOW, 8 * ms)
        // 40 slow frames, then fast ones: the window remembers them for a while, but not for long enough.
        val verdicts = dog.frames(40, 60 * ms) + dog.frames(ShaderWatchdog.WINDOW * 3, 8 * ms)
        assertTrue(verdicts.none { it is Verdict.Slow })
    }

    /** One slow frame in twenty is noise; one in five, for long, isn't. */
    @Test
    fun aFewSlowFramesAreForgiven() {
        val dog = ShaderWatchdog(budget)
        dog.frames(ShaderWatchdog.WARMUP, 8 * ms)
        val noise = (1..40).flatMap { dog.frames(19, 8 * ms) + dog.frames(1, 100 * ms) }
        assertTrue(noise.none { it is Verdict.Slow })
        val heavy = ShaderWatchdog(budget)
        heavy.frames(ShaderWatchdog.WARMUP, 8 * ms)
        val often = (1..80).flatMap { heavy.frames(4, 8 * ms) + heavy.frames(1, 100 * ms) }
        assertTrue(often.any { it is Verdict.Slow })
    }

    /**
     * Where the phone doesn't measure the GPU, a frame's whole time says nothing of the shader (it has
     * the wait for the screen in it): a phone that waits 26 ms a frame must not turn a shader off.
     */
    @Test
    fun aFramesWholeTimeIsNotTheShadersWhenTheGpuIsntMeasured() {
        val dog = ShaderWatchdog(ShaderWatchdog.budgetFor(120f))
        val verdicts = dog.frames(1000, null, 26 * ms)
        assertTrue(verdicts.none { it is Verdict.Slow || it is Verdict.Stalled })
        assertEquals(1, verdicts.count { it == Verdict.Proven })
    }

    @Test
    fun threeStallsTurnItOff() {
        val dog = ShaderWatchdog(budget)
        dog.frames(ShaderWatchdog.WARMUP, 8 * ms)
        assertEquals(Verdict.Fine, dog.frame(300 * ms, 310 * ms))
        assertEquals(Verdict.Fine, dog.frame(400 * ms, 410 * ms))
        assertEquals(Verdict.Stalled(500 * ms), dog.frame(500 * ms, 510 * ms))
    }

    /** A slow frame that the GPU didn't take part in is another's fault: it is no stall of the shader's. */
    @Test
    fun aSlowFrameThatTheGpuDidntTakeIsNoStall() {
        val dog = ShaderWatchdog(budget)
        dog.frames(ShaderWatchdog.WARMUP, 8 * ms)
        assertTrue(dog.frames(20, 8 * ms, 900 * ms).none { it is Verdict.Stalled })
        val unmeasured = ShaderWatchdog(budget)
        unmeasured.frames(ShaderWatchdog.WARMUP, null, 8 * ms)
        assertTrue(unmeasured.frames(20, null, 900 * ms).none { it is Verdict.Stalled })
        // A second and more, three times, is one all the same: nothing else says that it hangs.
        assertEquals(Verdict.Fine, unmeasured.frame(null, 1_100 * ms))
        assertEquals(Verdict.Fine, unmeasured.frame(null, 1_100 * ms))
        assertEquals(Verdict.Stalled(1_100 * ms), unmeasured.frame(null, 1_100 * ms))
    }

    /** Stalls are forgotten as they leave the window. */
    @Test
    fun oldStallsAreForgotten() {
        val dog = ShaderWatchdog(budget)
        dog.frames(ShaderWatchdog.WARMUP, 8 * ms)
        dog.frame(300 * ms, 300 * ms)
        dog.frames(ShaderWatchdog.WINDOW, 8 * ms)
        dog.frame(300 * ms, 300 * ms)
        assertEquals(Verdict.Fine, dog.frame(300 * ms, 300 * ms).let { if (it == Verdict.Proven) Verdict.Fine else it })
    }

    @Test
    fun theBudgetIsTwoFramesAndNeverUnderThirtyFramesASecond() {
        assertEquals(33_333_333L, ShaderWatchdog.budgetFor(60f))
        // A fast screen doesn't make it stricter.
        assertEquals(ShaderWatchdog.LEAST_BUDGET_NS, ShaderWatchdog.budgetFor(120f))
        assertEquals(ShaderWatchdog.LEAST_BUDGET_NS, ShaderWatchdog.budgetFor(240f))
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
