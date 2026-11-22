package com.mtwabpin.kolahal.shader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** The `tilt` uniform ([ShaderTilt]): which way it goes, what counts as level, how it glides, and that it comes to rest. */
class ShaderTiltTest {
    private val tilt = ShaderTilt()
    private var now = 5_000_000_000L

    /** Gravity on the phone's own axes, held with its top raised by [pitch] degrees and its right side lowered by [right]. */
    private fun held(pitch: Double, right: Double = 0.0): FloatArray {
        val p = pitch * PI / 180
        val r = right * PI / 180
        return floatArrayOf(
            (-G * sin(r)).toFloat(), (G * cos(r) * sin(p)).toFloat(), (G * cos(r) * cos(p)).toFloat(),
        )
    }

    /** Feeds it for [seconds], [hz] readings a second, each what [read] gives for its number. How often it changed. */
    private fun feed(
        seconds: Double, hz: Int = 50, rotation: Int = 0, shaky: Boolean = false, read: (Int) -> FloatArray,
    ): Int {
        var changes = 0
        repeat((seconds * hz).toInt()) { i ->
            now += 1_000_000_000L / hz
            val g = read(i)
            if (tilt.gravity(g[0], g[1], g[2], shaky, rotation, now)) changes++
        }
        return changes
    }

    /** However the phone is held when it starts, that is level, and it changes nothing while it is still. */
    @Test
    fun howItIsHeldIsLevel() {
        for (pitch in listOf(0.0, 50.0, 90.0, 130.0, 180.0, -60.0)) {
            tilt.restart()
            assertEquals("$pitch", 0, feed(10.0) { held(pitch, right = 10.0) })
            assertEquals(0f, tilt.x, 0f)
            assertEquals(0f, tilt.y, 0f)
        }
    }

    /** x is positive when the right side is lower, and y when the bottom edge is: flat, in a hand, upright and overhead. */
    @Test
    fun itLeansToTheLowerSide() {
        for (pitch in listOf(0.0, 50.0, 90.0, 130.0)) {
            for (by in listOf(15.0, -15.0)) {
                val sign = if (by > 0) 1f else -1f
                tilt.restart()
                feed(1.0) { held(pitch) }
                feed(0.6) { held(pitch, right = by) }
                assertEquals("$pitch right $by", sign * 0.47f, tilt.x, 0.05f)
                assertTrue("$pitch right $by: ${tilt.y}", abs(tilt.y) < 0.06f)
                // Back, and the other way about.
                feed(60.0) { held(pitch) }
                feed(0.6) { held(pitch + by) }
                assertEquals("$pitch bottom $by", sign * 0.47f, tilt.y, 0.05f)
                assertTrue("$pitch bottom $by: ${tilt.x}", abs(tilt.x) < 0.06f)
                while (tilt.ease(0.033f)) Unit
            }
        }
    }

    /** 30 degrees is 1, and more than that is 1 too. */
    @Test
    fun itNeverLeavesOne() {
        feed(1.0) { held(0.0) }
        feed(1.0) { held(0.0, right = 80.0) }
        assertTrue("${tilt.x}", tilt.x in 0.95f..1f)
        feed(1.0) { held(0.0, right = -80.0) }
        assertTrue("${tilt.x}", tilt.x in -1f..-0.95f)
        feed(1.0) { held(89.0) }
        assertTrue("${tilt.y}", tilt.y in 0.9f..1f)
    }

    /** A jump of the phone isn't one on the screen: it gets there within half a second, in small steps. */
    @Test
    fun itGlides() {
        feed(1.0) { held(40.0) }
        var before = tilt.x
        feed(0.5) {
            assertTrue("from $before to ${tilt.x}", tilt.x - before < 0.3f)
            before = tilt.x
            held(40.0, right = 30.0)
        }
        assertTrue("${tilt.x}", tilt.x > 0.85f)
    }

    /** A phone that keeps leaning is level again after a while, and has leaned the other way when it comes back. */
    @Test
    fun whatIsLevelFollowsThePhone() {
        feed(1.0) { held(40.0) }
        feed(1.0) { held(40.0, right = 12.0) }
        assertTrue("${tilt.x}", tilt.x > 0.3f)
        feed(6.0) { held(40.0, right = 12.0) }
        assertTrue("${tilt.x}", tilt.x in 0.08f..0.25f)
        feed(40.0) { held(40.0, right = 12.0) }
        assertEquals(0f, tilt.x, 2 * ShaderTilt.DEADBAND)
        feed(0.6) { held(40.0) }
        assertTrue("${tilt.x}", tilt.x < -0.3f)
    }

    /** On a table it comes to rest: what the sensor's noise does never shows, so nothing is drawn. */
    @Test
    fun onATableItSettles() {
        val noise = { i: Int, at: Int -> 0.02f * ((i * 7 + at * 5) % 13 - 6) / 6f }
        fun table(i: Int) = floatArrayOf(noise(i, 0), noise(i, 1), G.toFloat() + noise(i, 2))
        feed(1.0) { held(50.0, right = 20.0) }
        val settling = feed(60.0) { table(it) }
        assertTrue("$settling", settling in 10..400)
        assertEquals(0, feed(120.0) { table(it) })
        assertEquals(0f, tilt.x, 2 * ShaderTilt.DEADBAND)
        assertEquals(0f, tilt.y, 2 * ShaderTilt.DEADBAND)
    }

    /** A lean smaller than the deadband is none, and a steady one draws nothing once it is there. */
    @Test
    fun aSmallLeanIsNone() {
        feed(1.0) { held(0.0) }
        // A quarter of a degree: 0.0087.
        assertEquals(0, feed(10.0) { held(0.0, right = 0.25) })
        assertTrue(feed(0.5) { held(0.0, right = 3.0) } > 0)
    }

    /** However many readings come, it changes 30 times a second at most. */
    @Test
    fun itChangesAtMostThirtyTimesASecond() {
        for (hz in listOf(50, 100, 208, 400)) {
            tilt.restart()
            feed(1.0, hz) { held(0.0) }
            val changes = feed(2.0, hz) { held(0.0, right = 25 * sin(2 * PI * it / hz)) }
            assertTrue("$hz readings: $changes", changes in 36..60)
        }
    }

    /** Right and down are the screen's, however it is turned. */
    @Test
    fun itFollowsTheTurnOfTheScreen() {
        for (rotation in 0..3) {
            // What is right and up on the screen, on the phone's own axes.
            fun turned(right: Float, top: Float, z: Float) = when (rotation) {
                1 -> floatArrayOf(top, -right, z)
                2 -> floatArrayOf(-right, -top, z)
                3 -> floatArrayOf(-top, right, z)
                else -> floatArrayOf(right, top, z)
            }

            fun screen(pitch: Double, right: Double = 0.0) = held(pitch, right).let { turned(it[0], it[1], it[2]) }
            tilt.restart()
            feed(1.0, rotation = rotation) { screen(45.0) }
            feed(0.6, rotation = rotation) { screen(45.0, right = 15.0) }
            assertEquals("$rotation", 0.47f, tilt.x, 0.05f)
            assertTrue("$rotation: ${tilt.y}", abs(tilt.y) < 0.06f)
            feed(60.0, rotation = rotation) { screen(45.0) }
            feed(0.6, rotation = rotation) { screen(60.0) }
            assertEquals("$rotation", 0.47f, tilt.y, 0.05f)
            assertTrue("$rotation: ${tilt.x}", abs(tilt.x) < 0.06f)
            while (tilt.ease(0.033f)) Unit
        }
    }

    /** Turning the screen starts over: the phone as it is then is level, and the lean glides back to 0. */
    @Test
    fun turningTheScreenStartsOver() {
        feed(1.0) { held(45.0) }
        feed(0.5) { held(0.0, right = 60.0) }
        assertTrue(tilt.x > 0.9f)
        feed(0.02, rotation = 1) { held(0.0, right = 60.0) }
        assertTrue("it jumped to ${tilt.x}", tilt.x > 0.8f)
        feed(1.0, rotation = 1) { held(0.0, right = 60.0) }
        assertEquals(0f, tilt.x, 2 * ShaderTilt.DEADBAND)
        assertEquals(0f, tilt.y, 2 * ShaderTilt.DEADBAND)
    }

    /** Starting over keeps what shows, so nothing jumps, and goes to 0 from there. */
    @Test
    fun startingOverDoesntJump() {
        feed(1.0) { held(45.0) }
        feed(0.5) { held(45.0, right = 20.0) }
        val was = tilt.x
        tilt.restart()
        assertEquals(was, tilt.x, 0f)
        feed(1.0) { held(45.0, right = 20.0) }
        assertEquals(0f, tilt.x, 2 * ShaderTilt.DEADBAND)
    }

    /** When nothing is read any more it goes back to 0 once, in small steps, and is then at rest. */
    @Test
    fun itEasesBackToZero() {
        feed(1.0) { held(45.0) }
        feed(0.5) { held(70.0, right = -30.0) }
        assertFalse(tilt.resting)
        var steps = 0
        var before = tilt.x
        do {
            val more = tilt.ease(1f / ShaderTilt.FPS)
            assertTrue("from $before to ${tilt.x}", tilt.x in before..0f)
            assertTrue("from $before to ${tilt.x}", tilt.x - before < 0.25f)
            before = tilt.x
            steps++
        } while (more)
        assertTrue("$steps", steps in 5..40)
        assertTrue(tilt.resting)
        assertEquals(0f, tilt.x, 0f)
        assertEquals(0f, tilt.y, 0f)
        assertFalse(tilt.ease(1f))
    }

    /** A phone that falls, and a sensor that gives no numbers, change nothing and spoil nothing. */
    @Test
    fun nonsenseIsLeftOut() {
        feed(1.0) { held(45.0) }
        assertEquals(0, feed(1.0) { floatArrayOf(0f, 0.1f, 0f) })
        assertEquals(0, feed(1.0) { floatArrayOf(Float.NaN, 0f, 9.8f) })
        assertEquals(0, feed(1.0) { floatArrayOf(0f, Float.POSITIVE_INFINITY, 9.8f) })
        assertEquals(0, feed(1.0) { held(45.0) })
        feed(0.6) { held(45.0, right = 15.0) }
        assertTrue("${tilt.x}", tilt.x > 0.3f)
    }

    /** The accelerometer has the hand's shakes in it: they are steadied, and the lean still shows. */
    @Test
    fun theAccelerometerIsSteadied() {
        fun shaken(i: Int, right: Double) = held(45.0, right).also { it[0] += if (i % 2 == 0) 3f else -3f }
        feed(2.0, shaky = true) { shaken(it, 0.0) }
        feed(2.0, shaky = true) { i ->
            assertTrue("${tilt.x}", abs(tilt.x) < 0.1f)
            shaken(i, 0.0)
        }
        feed(1.0, shaky = true) { shaken(it, 15.0) }
        assertTrue("${tilt.x}", tilt.x > 0.3f)
    }

    /** A pause between two readings isn't a leap of what counts as level. */
    @Test
    fun aPauseCountsAsAMoment() {
        feed(1.0) { held(0.0) }
        now += 10_000_000_000L
        feed(0.02) { held(0.0, right = 15.0) }
        assertTrue("${tilt.x}", tilt.x > 0.3f)
        // A clock that goes back changes nothing by itself.
        now -= 20_000_000_000L
        feed(0.02) { held(0.0, right = 15.0) }
        assertTrue("${tilt.x}", tilt.x > 0.3f)
    }

    /** The host sets it, as two numbers. */
    @Test
    fun theHostKnowsIt() {
        assertEquals(listOf(ShaderTilt.FLOATS), ShaderMeta.KNOWN[ShaderTilt.NAME])
        // A lean draws no more often than a shader that moves.
        assertEquals(ShaderClock.FPS, ShaderTilt.FPS)
    }

    private companion object {
        const val G = 9.81
    }
}
