package com.mtwabpin.kolahal.ui.shaders

import com.mtwabpin.kolahal.shader.UsageValues
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which parameters a shader's details show as one row ([ShaderSeries]), and what that row says. */
class ShaderSeriesTest {
    private fun runs(vararg follows: String?) = ShaderSeries.runs(follows.toList()).map { it.indices }

    /** Seismograph's eight blocks are one row, and a scene's seven days are one, after a parameter of its own. */
    @Test
    fun aWholeSeriesIsOneRun() {
        val blocks = UsageValues.SCREEN_BLOCKS.ids
        assertEquals(listOf(0..7), runs(*blocks.toTypedArray()))
        assertEquals(listOf(1..7), runs("battery", *UsageValues.UNLOCK_DAYS.ids.toTypedArray()))
        assertEquals(blocks, ShaderSeries.runs(blocks).single().ids)
    }

    /** Three are enough; two aren't, nor numbers with a gap, another name between, or two names. */
    @Test
    fun onlyThreeOrMoreNextToEachOtherCountingUp() {
        assertEquals(listOf(0..2), runs("weather.t1", "weather.t2", "weather.t3"))
        assertEquals(emptyList<IntRange>(), runs("screen.d1", "screen.d2", null, "screen.d3"))
        assertEquals(emptyList<IntRange>(), runs("screen.d1", "screen.d3", "screen.d4"))
        assertEquals(emptyList<IntRange>(), runs("screen.d1", "unlocks.d2", "screen.d3"))
        assertEquals(emptyList<IntRange>(), runs("battery", "day", "night", null))
        // A run starts where the one before it breaks off.
        assertEquals(listOf(1..3), runs("screen.h1", "screen.h5", "screen.h6", "screen.h7"))
        assertEquals(listOf(0..2, 3..5), runs("a.x1", "a.x2", "a.x3", "a.y1", "a.y2", "a.y3"))
    }

    /** The words come from what the values are: the days, the blocks of today, or a script's values. */
    @Test
    fun itSaysWhatTheValuesAre() {
        assertEquals("Today, in blocks of 3 hours", ShaderSeries.title(UsageValues.SCREEN_BLOCKS.ids))
        assertEquals("Today from 06:00 to midnight, in blocks of 3 hours", ShaderSeries.title(UsageValues.SCREEN_BLOCKS.ids.drop(2)))
        assertEquals("Each of the last 7 days", ShaderSeries.title(UsageValues.SCREEN_DAYS.ids))
        assertEquals("Each of the last 3 days", ShaderSeries.title(UsageValues.UNLOCK_DAYS.ids.takeLast(3)))
        assertEquals("Days 1 to 3 of the last 7", ShaderSeries.title(UsageValues.UNLOCK_DAYS.ids.take(3)))
        assertEquals("weather · t1 to weather · t3", ShaderSeries.title(listOf("weather.t1", "weather.t2", "weather.t3")))

        assertEquals("Follows your screen time", ShaderSeries.follows(UsageValues.SCREEN_BLOCKS.ids))
        assertEquals("Follows your screen time", ShaderSeries.follows(UsageValues.SCREEN_DAYS.ids))
        assertEquals("Follows your unlocks", ShaderSeries.follows(UsageValues.UNLOCK_DAYS.ids))
        assertEquals("Follows values from weather", ShaderSeries.follows(listOf("weather.t1", "weather.t2", "weather.t3")))
    }

    /** One parameter's Follow button names the value in the fewest true words, beside its link icon: no "Follows". */
    @Test
    fun oneParameterSaysItsValueShortly() {
        assertEquals("Battery", ShaderSeries.followsOne("battery"))
        assertEquals("Time of day", ShaderSeries.followsOne("day"))
        assertEquals("Unlocks", ShaderSeries.followsOne(UsageValues.UNLOCKS))
        assertEquals("Screen time", ShaderSeries.followsOne(UsageValues.SCREEN))
        assertEquals("Time away", ShaderSeries.followsOne(UsageValues.AWAY))
        assertEquals("Screen time from 06:00 to 09:00", ShaderSeries.followsOne("screen.h3"))
        assertEquals("weather · rain", ShaderSeries.followsOne("weather.rain"))
    }
}
