package com.mtwabpin.kolahal.shader

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

/** The `date` uniform ([ShaderDate]): its four numbers, and when a still shader that reads it is drawn again. */
class ShaderDateTest {
    @Test
    fun itIsTheYearTheMonthTheDayAndTheSecondsOfTheDay() {
        val evening = LocalDateTime.of(2026, 11, 22, 18, 15, 30)
        assertArrayEquals(floatArrayOf(2026f, 11f, 22f, 18 * 3600f + 15 * 60f + 30f), ShaderDate.of(evening), 0f)
    }

    /** Months and days count from 1, and the seconds from midnight, up to the last of the day. */
    @Test
    fun theDayStartsAtZeroAndEndsBelowADaysSeconds() {
        assertArrayEquals(floatArrayOf(2027f, 1f, 1f, 0f), ShaderDate.of(LocalDateTime.of(2027, 1, 1, 0, 0, 0)), 0f)
        val last = ShaderDate.of(LocalDateTime.of(2026, 12, 31, 23, 59, 59, 900_000_000))
        assertEquals(12f, last[1], 0f)
        assertEquals(31f, last[2], 0f)
        assertEquals(86_399.9f, last[3], 0.01f)
        assertTrue(last[3] < 86_400f)
    }

    @Test
    fun aStillShaderIsDrawnAgainAtTheNextFullMinute() {
        val minute = ShaderDate.MINUTE_MS
        assertEquals(minute, ShaderDate.untilNextMinute(0))
        assertEquals(minute - 1_500, ShaderDate.untilNextMinute(7 * minute + 1_500))
        assertEquals(30_000L, ShaderDate.untilNextMinute(1_000_000 * minute + 30_000))
    }

    /** Never sooner than a second, and never later than a minute, whatever the clock says. */
    @Test
    fun theWaitIsNeverShortOrLong() {
        assertEquals(ShaderDate.LEAST_MS, ShaderDate.untilNextMinute(ShaderDate.MINUTE_MS - 1))
        assertEquals(ShaderDate.LEAST_MS, ShaderDate.untilNextMinute(ShaderDate.MINUTE_MS - 999))
        // A clock before 1970 counts the same way.
        assertEquals(1_500L, ShaderDate.untilNextMinute(-1_500))
        for (now in listOf(-123_456_789L, 0L, 59_999L, 60_000L, 1_763_000_000_123L)) {
            assertTrue("$now", ShaderDate.untilNextMinute(now) in ShaderDate.LEAST_MS..ShaderDate.MINUTE_MS)
        }
    }

    /** The host sets it, as four numbers and nothing else. */
    @Test
    fun theHostKnowsIt() {
        assertEquals(listOf(4), ShaderMeta.KNOWN[ShaderDate.NAME])
        val good = "uniform shader content; uniform float4 date; half4 main(float2 p) { return content.eval(p); }"
        assertEquals(emptyList<String>(), ShaderMeta.problems(good))
        assertEquals(emptyList<String>(), ShaderMeta.parse(good).unset())
        val bad = "uniform shader content; uniform float3 date; half4 main(float2 p) { return content.eval(p); }"
        assertEquals(listOf("'date' must be a float4, not float3"), ShaderMeta.problems(bad))
    }
}
