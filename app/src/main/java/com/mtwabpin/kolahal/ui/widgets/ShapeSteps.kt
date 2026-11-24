/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * The numbers behind the Shape look's shapes that change with what they show, and behind the motion
 * of shapes and buttons. Pure, so it is unit tested. The drawing is in WidgetShapeValues.kt and
 * WidgetShapeMotion.kt.
 */
internal object ShapeSteps {
    // ---- a countdown's sun -------------------------------------------------------------------

    /** The most rays a countdown's sun has. */
    const val MAX_RAYS = 12

    private const val HOUR = 3600L
    private const val DAY = 86_400L
    private const val FIVE_MINUTES = 300L

    /**
     * The rays of a countdown's sun at [now], for a moment [to] with a start [from] (all in
     * seconds). Each ray is a step of the wait, and a ray goes as each step passes:
     * - The step is a day for a wait of a day or more, an hour for one of an hour or more, and five
     *   minutes for a shorter one. With a start, the wait is the whole of it; without one, the time
     *   left, so the sun has its rays again when the time left goes under a day or an hour.
     * - The rays are the whole steps left, at most [MAX_RAYS]: 12 days and more is a sun with 12
     *   rays, 7 days has 7, the last day (or the last five minutes) is a plain circle.
     * - Once the moment has come, the sun is a plain circle.
     */
    fun rays(to: Long, from: Long?, now: Long): Int {
        val left = to - now
        if (left <= 0) return 0
        val wait = if (from != null && from < to) to - from else left
        val step = when {
            wait >= DAY -> DAY
            wait >= HOUR -> HOUR
            else -> FIVE_MINUTES
        }
        return (left / step).coerceAtMost(MAX_RAYS.toLong()).toInt()
    }

    /** The outside of a shape that has a point out, in a box 1 wide: its edge. */
    const val OUTER = 0.5f

    /**
     * The sun's body, from its middle, in a box 1 wide: where a ray that has gone was. A ray is a
     * fifth of the radius, so it shows on a 1×1 card, and a wait's thin ring (0.38 out) stays inside
     * the body.
     */
    const val SUN_BODY = 0.40f

    /** How pointed a ray is: a little more than a wave, as the mockup draws it. */
    const val SUN_SHARPNESS = 1.25

    /** Corners for each ray: enough for a smooth ray at any card's size. */
    const val SUN_STEPS = 6

    /** The flower's middle, from its middle, in a box 1 wide: where its leaves meet. */
    const val FLOWER_BODY = 0.35f

    /** How round a leaf is: under 1, so it stays full to near where it meets the next and is a round petal. */
    const val FLOWER_SHARPNESS = 0.55

    /** Corners for each leaf. */
    const val FLOWER_STEPS = 8

    /**
     * The corners of a shape with a point for each of [out], in a box 1 wide around (0.5, 0.5), as
     * x and y one after the other: [steps] corners for each point, the first of them where it meets
     * the point before. A point that is out rises from [body] to the edge ([OUTER]) and back as a
     * wave, as round as [sharpness] says (1 a plain wave, more a narrower point, less a fuller one).
     * A point that is not out lies on the body's circle, so a shape with none out is that circle.
     * The first point is at the top, and the others follow clockwise.
     */
    fun lobeCorners(out: List<Boolean>, body: Float, sharpness: Double, steps: Int): FloatArray {
        val n = out.size
        val corners = FloatArray(n * steps * 2)
        for (i in 0 until n) for (s in 0 until steps) {
            // Where on its point, from -0.5 (where it meets the one before) to 0 (its top).
            val f = s.toDouble() / steps - 0.5
            val rise = if (out[i]) ((1 + cos(2 * PI * f)) / 2).pow(sharpness) else 0.0
            val radius = body + (OUTER - body) * rise
            val angle = 2 * PI * (i + f) / n
            val at = 2 * (i * steps + s)
            corners[at] = (0.5 + radius * sin(angle)).toFloat()
            corners[at + 1] = (0.5 - radius * cos(angle)).toFloat()
        }
        return corners
    }

    /**
     * Which of the sun's [MAX_RAYS] places have a ray for [rays] rays: spread as evenly as they can
     * be round the whole sun, so it still reads as a sun. The first is at the top, and ray j is at
     * the place nearest to j / [rays] of the way round. So the largest gap between two rays is as
     * small as it can be (12 / [rays] places, rounded up): 2 rays are opposite, 3 a third apart, 7
     * at most two places apart. As the count falls, the rays move to their new places in the morph.
     */
    fun sunRays(rays: Int): List<Boolean> {
        val n = rays.coerceIn(0, MAX_RAYS)
        val places = (0 until n).map { (2 * MAX_RAYS * it + n) / (2 * n) }.toSet()
        return List(MAX_RAYS) { it in places }
    }

    // ---- a habit's week as a flower ----------------------------------------------------------

    /**
     * Whether a chart is drawn as a flower in the Shape look's square card: a chart of dots whose
     * points are all 0 or 1 ([DotCalendar.yesNo]) and no more than a week of them. A longer run
     * keeps its calendar.
     */
    fun weekFlower(style: String?, points: List<Float>): Boolean =
        style == "dots" && points.size <= DotCalendar.DAYS && DotCalendar.yesNo(points)

    /**
     * The leaves of the week's flower, one for each day of the week in the order of its columns
     * ([DotCalendar.column]): true for a day done. The last point is the day in column [last], the
     * point before it the day before. A day after it is still to come, and a day of the week with
     * no point is not done.
     */
    fun weekLeaves(points: List<Float>, last: Int): List<Boolean> =
        List(DotCalendar.DAYS) { day ->
            val index = points.size - 1 - (last - day)
            day <= last && index >= 0 && points[index] == 1f
        }

    /**
     * The words under the days [done] in the middle of the week's flower: "this week", or "all week"
     * when all seven are done. Without [room] (a 1×1 card), "of 7": short enough for the small
     * flower's middle, and it still says out of what.
     */
    fun weekWords(done: Int, room: Boolean): String = when {
        !room -> "of ${DotCalendar.DAYS}"
        done >= DotCalendar.DAYS -> "all week"
        else -> "this week"
    }

    // ---- the battery's shape -----------------------------------------------------------------

    /** The battery's shapes in the Shape look, from full to low: round, then scalloped, then spiky. */
    enum class BatteryForm { ROUND, FINE_SCALLOPS, SCALLOPS, SOFT_SPIKES, SPIKES }

    /**
     * The battery's shape at [level] (0 to 100): round from 80%, then scalloped, then spiky; the
     * sharpest at [BatteryCards.LOW] and under, where the card is in the bad color. Null while the
     * phone is [charging] or doesn't say its level: the card keeps its clover then.
     */
    fun batteryForm(level: Int?, charging: Boolean): BatteryForm? = when {
        level == null || charging -> null
        level >= 80 -> BatteryForm.ROUND
        level >= 60 -> BatteryForm.FINE_SCALLOPS
        level >= 35 -> BatteryForm.SCALLOPS
        level > BatteryCards.LOW -> BatteryForm.SOFT_SPIKES
        else -> BatteryForm.SPIKES
    }

    // ---- motion ------------------------------------------------------------------------------

    /**
     * How much of a spring's swing past its end a settling shape grows by. The theme's fast spring
     * goes about 10% past its end and back, so the shape grows by about 5%, once.
     */
    const val SETTLE_GROWTH = 0.5f

    /** How far a settling shape has morphed when its spring is at [progress]: the spring's swing past either end is not drawn. */
    fun settleMorph(progress: Float): Float = progress.coerceIn(0f, 1f)

    /** The size of a settling shape when its spring is at [progress]: 1, and a little more while the spring swings past either end. */
    fun settleScale(progress: Float): Float =
        1f + SETTLE_GROWTH * maxOf(0f, progress - 1f, -progress)

    /** How much wider a pressed button gets, as a share of its width: Material's button group's own. */
    const val GIVE_WAY = 0.15f

    /**
     * The widths of [count] buttons in a row, as weights, while the one at [pressed] is held: it is
     * [ratio] wider, and the buttons beside it are narrower by as much, so the row keeps its width.
     * With two neighbours, each gives half. With none pressed (or one alone in its row) all are 1.
     */
    fun giveWay(count: Int, pressed: Int, ratio: Float = GIVE_WAY): List<Float> {
        if (count < 2 || pressed !in 0 until count) return List(count) { 1f }
        val neighbours = (if (pressed > 0) 1 else 0) + (if (pressed < count - 1) 1 else 0)
        val give = ratio / neighbours
        return List(count) {
            when (it) {
                pressed -> 1f + ratio
                pressed - 1, pressed + 1 -> 1f - give
                else -> 1f
            }
        }
    }
}
