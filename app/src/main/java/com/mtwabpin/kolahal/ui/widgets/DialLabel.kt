/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * The hands of the Shape clock's dial ([ClockDial]) and where the place's name goes on it. Pure, so
 * it is unit tested. Sizes are in units of the dial's 100-unit square, from its middle, y down.
 * - The name sits [RISE] above the middle. While the hour hand passes through its box, with
 *   [MARGIN] around it, the name goes as far under the middle, drawn the same ([labelY]).
 * - The hour hand only moves forward, and the directions that pass through a box clear of the
 *   middle are one range: the name goes under once and back once in each pass.
 * - The minute hand never moves the name: it passes every hour, and the name would move twice an
 *   hour. The name has a ring of the card's color, so it reads over it.
 */
internal object DialLabel {
    /** The hour hand's length from the middle and its width. Its ends are round. */
    const val HOUR_LENGTH = 20f
    const val HOUR_WIDTH = 5f

    /** The minute hand's length from the middle and its width. */
    const val MINUTE_LENGTH = 30f
    const val MINUTE_WIDTH = 3.5f

    /** How far the middle of the name's box is from the dial's middle: above it, or under it. */
    const val RISE = 17f

    /** The room kept between the hour hand's edge and the name's box. */
    const val MARGIN = 1f

    /** The turns of the hour hand from 12 at [hour] (0 to 23) and [minute]: from 0 to less than 1. */
    fun hourTurns(hour: Int, minute: Int): Float = (hour % 12 + minute / 60f) / 12f

    /**
     * The middle of the name's box, [width] by [height], when the hour hand is at [turns]: -[RISE]
     * (above the middle), or [RISE] (under it) while the hand passes through the box above and not
     * the one under. A box that reaches the middle, with the margin, is in the way of every
     * direction of the hand: it stays above.
     */
    fun labelY(turns: Float, width: Float, height: Float): Float =
        if (covers(turns, -RISE, width, height) && !covers(turns, RISE, width, height)) RISE else -RISE

    /**
     * True when the hour hand at [turns] comes within [MARGIN] of the box [width] by [height] whose
     * middle is [y] under the dial's middle.
     */
    fun covers(turns: Float, y: Float, width: Float, height: Float): Boolean {
        val a = turns * 2 * PI
        val endX = (sin(a) * HOUR_LENGTH).toFloat()
        val endY = (-cos(a) * HOUR_LENGTH).toFloat()
        val left = -width / 2
        val right = width / 2
        val top = y - height / 2
        val bottom = y + height / 2

        // How far the point at [t] along the hand, from the middle (0) to its end (1), is from the box.
        fun away(t: Float): Float {
            val x = endX * t
            val py = endY * t
            val dx = maxOf(left - x, 0f, x - right)
            val dy = maxOf(top - py, 0f, py - bottom)
            return hypot(dx, dy)
        }
        // The distance to a box along a straight line has one low: narrow down to it.
        var lo = 0f
        var hi = 1f
        repeat(40) {
            val a1 = lo + (hi - lo) / 3
            val a2 = hi - (hi - lo) / 3
            if (away(a1) <= away(a2)) hi = a2 else lo = a1
        }
        return away((lo + hi) / 2) <= HOUR_WIDTH / 2 + MARGIN
    }
}
