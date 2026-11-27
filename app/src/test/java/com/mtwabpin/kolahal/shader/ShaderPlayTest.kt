package com.mtwabpin.kolahal.shader

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

    /** `ShaderActions.play` turns a shader that was turned off on again in [ShaderPlay.play]'s first: only when it plays. */
    @Test
    fun firstRunsOnlyWhenItStarts() {
        var ran = 0
        assertTrue(ShaderPlay.play("hearts", null, at = 1_000) { ran++ })
        assertEquals(1, ran)
        assertFalse(ShaderPlay.play("confetti", null, at = 2_000) { ran++ })
        assertEquals(1, ran)
        assertEquals("hearts", ShaderPlay.current.value?.name)
    }

    /** How far it has played goes with the play, for the next screen to draw it to go on from; it isn't part of what it equals. */
    @Test
    fun itKeepsHowFarItHasPlayed() {
        assertTrue(ShaderPlay.play("hearts", 4f, at = 1_000))
        val playing = ShaderPlay.current.value!!
        assertEquals(0f, playing.elapsed)
        playing.elapsed = 2.5f
        assertEquals(2.5f, ShaderPlay.current.value?.elapsed)
        assertEquals(ShaderPlay.Playing("hearts", 4f, 1_000), playing)
        // Done with an equal one ends it all the same.
        ShaderPlay.done(ShaderPlay.Playing("hearts", 4f, 1_000))
        assertNull(ShaderPlay.current.value)
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
