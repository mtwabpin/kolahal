package com.mtwabpin.kolahal.shader

import org.junit.Assert.assertFalse
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
}
