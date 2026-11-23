package com.mtwabpin.kolahal.terminal.tty.screens

import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** `decode` in the gui style: the waves home's texts draw, and when they run. */
class LogDecodeTest {
    private class At(private val top: Float?) : LogDecode.Part {
        override fun place() = top?.let { LogDecode.Place(20f, it, 900f, it + 40f) }
    }

    private val grid = CellBuffer(1, 1)
    private fun env() = ScreenEnv(40, 40, Random(5), { 0L }, true, cellH = 40)

    private fun wave(kind: DecodeWave.Kind, seed: Int = 3, fromRight: Boolean = false) =
        DecodeWave(kind, seed, LogDecode.Place(0f, 0f, 1000f, 2000f), 40, 620f, fromRight)

    @Test
    fun everyCharacterSettlesBeforeItsWaveEnds() {
        for (kind in DecodeWave.Kind.entries) for (seed in 0 until 20) {
            val w = wave(kind, seed)
            for (i in 0 until 400) {
                val x = (i % 40) * 25f
                val y = (i / 8) * 40f
                val start = w.start(i, x, y)
                if (start == DecodeWave.NEVER) {
                    assertEquals(DecodeWave.Kind.SCATTERED, kind)
                    continue
                }
                assertTrue("$kind", start >= 0 && w.settle(i, x, y) > start)
                assertTrue("$kind ends at ${w.lengthMs}, not ${w.settle(i, x, y)}", w.settle(i, x, y) <= w.lengthMs)
            }
        }
    }

    @Test
    fun wavesGoTheirWay() {
        val down = wave(DecodeWave.Kind.BAND)
        assertTrue(down.start(0, 0f, 100f) < down.start(0, 0f, 1_800f))
        val up = wave(DecodeWave.Kind.UP)
        assertTrue(up.start(0, 0f, 100f) > up.start(0, 0f, 1_800f))
        val columns = wave(DecodeWave.Kind.COLUMNS, fromRight = true)
        assertTrue(columns.start(0, 100f, 100f) > columns.start(0, 900f, 100f))
        // The row picked for it takes part, whatever its chance.
        for (seed in 0 until 50) assertTrue(wave(DecodeWave.Kind.SCATTERED, seed).start(0, 0f, 620f) != DecodeWave.NEVER)
    }

    /** One wave after another for as long as it is open, never the same kind twice in a row. */
    @Test
    fun wavesFollowEachOther() {
        val decode = LogDecode()
        val screen = LogDecodeScreen(decode)
        for (i in 0 until 12) decode.add(At(100f + i * 50))
        decode.add(At(null))
        assertFalse(decode.active)
        screen.start(env())
        assertTrue(decode.active)
        val kinds = ArrayList<DecodeWave.Kind>()
        var last: DecodeWave? = null
        var end = 0L
        var t = 0L
        while (t < 120_000) {
            t += 42
            assertTrue(screen.frame(grid, t, 42))
            val wave = decode.wave
            if (wave != null && wave !== last) {
                if (last != null) {
                    val rest = decode.waveAt - end
                    assertTrue("$rest ms", rest in DecodeScreen.REST_MS..DecodeScreen.REST_MS + DecodeScreen.REST_MORE_MS + 42)
                }
                kinds += wave.kind
                end = decode.waveAt + wave.lengthMs
                last = wave
            }
        }
        assertEquals(DecodeWave.Kind.ALL, kinds.first())
        assertTrue("${kinds.size} waves", kinds.size > 25)
        assertFalse(kinds.drop(1).contains(DecodeWave.Kind.ALL))
        for ((a, b) in kinds.zipWithNext()) assertTrue(a != b)
        screen.stop()
        assertFalse(decode.active)
        assertNull(decode.wave)
    }

    /** With no text on screen yet it waits; a screen that closes late doesn't end the next one. */
    @Test
    fun itWaitsForTextAndForItsOwnEnd() {
        val decode = LogDecode()
        val first = LogDecodeScreen(decode)
        first.start(env())
        first.frame(grid, 1_000, 42)
        assertNull(decode.wave)
        val part = At(10f)
        decode.add(part)
        first.frame(grid, 1_042, 42)
        assertNotNull(decode.wave)
        val second = LogDecodeScreen(decode)
        second.start(env())
        first.stop()
        assertTrue(decode.active)
        second.stop()
        assertFalse(decode.active)
    }

    /**
     * A text that came during a wave decodes in from when it came, in `decode`'s time. `scramble` starts
     * each wave again from a time of 0: a text that came during an earlier wave follows the new one.
     */
    @Test
    fun aTextFromAnEarlierWaveFollowsTheNext() {
        val decode = LogDecode()
        decode.add(At(10f))
        val owner = Any()
        decode.start(owner, Random(1), 40)
        decode.frame(DecodeScreen.FIRST_MS)
        decode.frame(3_000)
        val born = decode.now
        val bornIn = decode.runs
        decode.frame(3_500)
        assertEquals(500L, decode.age(born, bornIn))
        // A text that was there before the first wave follows it.
        assertNull(decode.age(-1, bornIn))
        // The next wave of `scramble`: its time is under 3000 again.
        decode.stop(owner)
        decode.start(owner, Random(1), 40)
        decode.frame(DecodeScreen.FIRST_MS)
        assertNotNull(decode.wave)
        assertNull(decode.age(born, bornIn))
        assertEquals(0L, decode.age(decode.now, decode.runs))
    }

    /** With animations off nothing scrambles. */
    @Test
    fun theStillFrameIsHomeAsItIs() {
        val decode = LogDecode()
        decode.add(At(10f))
        val screen = LogDecodeScreen(decode)
        screen.start(env())
        screen.still(grid)
        assertTrue(decode.active)
        assertNull(decode.wave)
    }
}
