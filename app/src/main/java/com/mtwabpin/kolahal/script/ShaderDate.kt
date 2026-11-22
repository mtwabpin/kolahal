/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import java.time.LocalDateTime

/**
 * The `date` uniform: the phone's own date and time of day, for a shader that follows the day or the
 * season. Four numbers: the year, the month (1 to 12), the day of the month, and the seconds since
 * midnight, all in local time.
 *
 * A still shader that declares it is drawn again once a minute ([untilNextMinute]), which is often
 * enough for a sky and costs next to nothing.
 */
object ShaderDate {
    /** The uniform's name. */
    const val NAME = "date"

    /** How many numbers it holds. */
    const val FLOATS = 4

    /** The uniform's four numbers at [now]. The seconds have their fraction. */
    fun of(now: LocalDateTime): FloatArray = floatArrayOf(
        now.year.toFloat(),
        now.monthValue.toFloat(),
        now.dayOfMonth.toFloat(),
        now.toLocalTime().toSecondOfDay() + now.nano / 1_000_000_000f,
    )

    /**
     * How long to wait, in ms, from [nowMs] (a clock in ms, such as the wall clock's) until the next
     * full minute: at least [LEAST_MS], so a clock that steps back never makes a busy loop.
     */
    fun untilNextMinute(nowMs: Long): Long = (MINUTE_MS - Math.floorMod(nowMs, MINUTE_MS)).coerceAtLeast(LEAST_MS)

    const val MINUTE_MS = 60_000L
    const val LEAST_MS = 1_000L
}
