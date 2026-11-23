package com.mtwabpin.kolahal.shader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** When a moving shader's time moves ([ShaderClock]). */
class ShaderClockTest {
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

    private fun pace(vararg lines: String, uniforms: String = "uniform float time;") =
        ShaderClock.Pace.of(ShaderMeta.parse(lines.joinToString("\n") + "\nuniform shader content; $uniforms"))

    private val full = 1_000L / ShaderClock.FPS

    /** Without `@fps` and `@settles`, a shader that moves is drawn 30 times a second, touched or not, as it always was. */
    @Test
    fun withoutTheLinesNothingChanges() {
        for (uniforms in listOf("uniform float time;", "uniform float time; uniform float2 touch;", "uniform float touchAge;", "uniform float time; uniform float pressed;")) {
            val p = pace("// @animated", uniforms = uniforms)
            assertEquals(uniforms, ShaderClock.FPS, p.fps)
            assertEquals(uniforms, ShaderClock.IDLE_MS, p.settlesMs)
            assertFalse(uniforms, p.onlyAfterTouch)
            for (since in listOf(null, 0L, 2_000L, ShaderClock.IDLE_MS - 1, 10 * ShaderClock.IDLE_MS)) {
                assertEquals("$uniforms $since", full, ShaderClock.frameMs(p, since))
            }
            assertFalse(ShaderClock.touchHurries(p, full))
        }
        assertEquals(ShaderClock.Pace.DEFAULT, pace("// @animated"))
        // 33 ms, the frame it always had.
        assertEquals(33L, full)
    }

    /** A slow scene is drawn as often as it says; one that doesn't read the touch stays so when touched. */
    @Test
    fun aSlowSceneIsDrawnAsOftenAsItSays() {
        val mist = pace("// @animated", "// @fps 10")
        assertFalse(mist.readsTouch)
        for (since in listOf(null, 0L, 2_000L, 8_000L)) assertEquals(100L, ShaderClock.frameMs(mist, since))
        assertFalse(ShaderClock.touchHurries(mist, 100L))
        assertEquals(1_000L, ShaderClock.frameMs(pace("// @animated", "// @fps 1"), null))
    }

    /** One with `@fps 10` that reads the touch: 30 a second while a touch settles (5 s), then 10 again. */
    @Test
    fun aTouchIsFollowedSmoothlyUntilItSettles() {
        val sand = pace("// @animated", "// @fps 10", "// @settles 5", uniforms = "uniform float time; uniform float2 touch; uniform float touchAge;")
        assertTrue(sand.readsTouch)
        assertFalse(sand.onlyAfterTouch)
        assertEquals(5_000L, sand.settlesMs)
        assertEquals(100L, ShaderClock.frameMs(sand, null))
        assertEquals(full, ShaderClock.frameMs(sand, 0L))
        assertEquals(full, ShaderClock.frameMs(sand, 2_000L))
        assertEquals(full, ShaderClock.frameMs(sand, 4_999L))
        assertEquals(100L, ShaderClock.frameMs(sand, 5_000L))
        assertEquals(100L, ShaderClock.frameMs(sand, 8_000L))
        // A loop that sleeps 100 ms wakes at a touch; one at 30 a second needn't.
        assertTrue(ShaderClock.touchHurries(sand, 100L))
        assertFalse(ShaderClock.touchHurries(sand, full))
        // `pressed` is reading the touch too.
        assertTrue(pace("// @fps 10", uniforms = "uniform float time; uniform float pressed;").readsTouch)
        // Without `@settles`, a touch is followed for the idle time.
        val noSettles = pace("// @animated", "// @fps 10", uniforms = "uniform float time; uniform float2 touch;")
        assertEquals(full, ShaderClock.frameMs(noSettles, ShaderClock.IDLE_MS - 1))
        assertEquals(100L, ShaderClock.frameMs(noSettles, null))
    }

    /** A shader that moves only with the touch (`ink`), with `@settles`: drawn while a touch settles, not before the first, not after. */
    @Test
    fun aShaderThatMovesOnlyWithTheTouchStops() {
        val ink = pace("// @animated", "// @settles 4", uniforms = "uniform float2 touch; uniform float touchAge;")
        assertTrue(ink.onlyAfterTouch)
        assertNull(ShaderClock.frameMs(ink, null))
        assertEquals(full, ShaderClock.frameMs(ink, 0L))
        assertEquals(full, ShaderClock.frameMs(ink, 3_999L))
        assertNull(ShaderClock.frameMs(ink, 4_000L))
        assertNull(ShaderClock.frameMs(ink, 60_000L))
        // Its `@fps` matters only for the frames it has, which are all after a touch.
        val slow = pace("// @animated", "// @fps 5", "// @settles 4", uniforms = "uniform float touchAge;")
        assertEquals(full, ShaderClock.frameMs(slow, 1_000L))
        assertNull(ShaderClock.frameMs(slow, 5_000L))
        // Without `@settles`, as it always was: the idle time stops it, and turning it on counts as a touch there.
        val plain = pace("// @animated", uniforms = "uniform float touchAge;")
        assertFalse(plain.onlyAfterTouch)
        assertEquals(full, ShaderClock.frameMs(plain, null))
        // With `time` or `progress` it has a clock of its own, and doesn't stop.
        for (other in listOf("uniform float time;", "uniform float progress;")) {
            val p = pace("// @animated", "// @settles 4", uniforms = "uniform float touchAge; $other")
            assertFalse(other, p.onlyAfterTouch)
            assertEquals(other, full, ShaderClock.frameMs(p, 10_000L))
        }
    }

    /** A one-off shader keeps the default, whatever its header says. */
    @Test
    fun aOneOffShaderIsNotPaced() {
        assertEquals(ShaderClock.Pace.DEFAULT, pace("// @once 6", "// @fps 5", "// @settles 1", uniforms = "uniform float touchAge;"))
    }

    /** A step of the clock is the time between frames, but a pause is one frame, also at a few frames a second. */
    @Test
    fun aPauseIsOneStep() {
        assertEquals(full, ShaderClock.step(full, full))
        assertEquals(ShaderClock.PAUSE_MS, ShaderClock.step(5_000L, full))
        assertEquals(0L, ShaderClock.step(-10L, full))
        assertEquals(1_000L, ShaderClock.step(1_000L, 1_000L))
        assertEquals(2_000L, ShaderClock.step(9_000L, 1_000L))
    }
}
