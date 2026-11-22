package com.mtwabpin.kolahal.script

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One-off shaders ([ShaderPlay]): one at a time, for a bounded time. */
class ShaderPlayTest {
    @After
    fun tearDown() {
        ShaderPlay.current.value?.let(ShaderPlay::done)
    }

    @Test
    fun oneAtATime() {
        assertTrue(ShaderPlay.play("hearts", null, at = 1_000))
        assertFalse(ShaderPlay.play("confetti", null, at = 2_000))
        val playing = ShaderPlay.current.value!!
        assertEquals("hearts", playing.name)
        ShaderPlay.done(playing)
        assertNull(ShaderPlay.current.value)
        assertTrue(ShaderPlay.play("confetti", 4f, at = 3_000))
        // Done with the one before doesn't end this one.
        ShaderPlay.done(playing)
        assertEquals("confetti", ShaderPlay.current.value?.name)
        assertEquals(4f, ShaderPlay.current.value?.seconds)
    }

    /** One that home never showed doesn't keep others out for ever. */
    @Test
    fun oneThatWaitedTooLongMakesRoom() {
        assertTrue(ShaderPlay.play("hearts", null, at = 0))
        assertFalse(ShaderPlay.play("confetti", null, at = ShaderPlay.WAIT_MS))
        assertTrue(ShaderPlay.play("confetti", null, at = ShaderPlay.WAIT_MS + 1))
    }

    @Test
    fun itsTimeIsBounded() {
        ShaderPlay.play("hearts", 600f, at = 0)
        assertEquals(ShaderPlay.MAX_SECONDS, ShaderPlay.current.value?.seconds)
        ShaderPlay.done(ShaderPlay.current.value!!)
        ShaderPlay.play("hearts", 0.1f, at = 0)
        assertEquals(ShaderPlay.MIN_SECONDS, ShaderPlay.current.value?.seconds)
    }
}
