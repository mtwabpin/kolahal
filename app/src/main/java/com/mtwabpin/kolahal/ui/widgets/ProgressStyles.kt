/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetSpec
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * The three styles of a progress card that are more than a ring or bars, as plain numbers and
 * words, so JVM tests cover them. The cards draw them in WidgetGauge.kt, WidgetRings.kt and
 * WidgetStages.kt.
 * - A gauge (`style = "gauge"`): a thick arc from `min` to `max`, in its zones' colors, filled to
 *   the value.
 * - Nested rings (`style = "rings"`): the bars of `items` as rings inside each other.
 * - Stages (`stages`): a path of named steps, with the one it is at.
 */
internal object ProgressStyles {
    const val GAUGE = "gauge"
    const val RINGS = "rings"

    /** A gauge: one value on a scale, with no bars and no stages. */
    fun isGauge(spec: WidgetSpec.Progress): Boolean =
        spec.style == GAUGE && spec.items.isEmpty() && spec.stages.isEmpty()

    /** Bars drawn as rings inside each other. */
    fun isRings(spec: WidgetSpec.Progress): Boolean = spec.style == RINGS && spec.items.isNotEmpty()

    /** A path of stages. */
    fun isStages(spec: WidgetSpec.Progress): Boolean = spec.stages.isNotEmpty()

    /** Where [value] sits from [min] (0) to [max] (1). A scale of no length is empty. */
    fun gaugeFraction(value: Float, min: Float, max: Float): Float {
        // In doubles: the length of a scale of very large numbers is more than a float holds.
        val length = max.toDouble() - min.toDouble()
        if (value.isNaN() || length.isNaN() || length <= 0) return 0f
        return ((value.toDouble() - min) / length).coerceIn(0.0, 1.0).toFloat()
    }

    /** The zone [value] is in: the last one that starts at or below it. Null below the first zone, or with none. */
    fun zoneOf(value: Float, zones: List<WidgetSpec.GaugeZone>): WidgetSpec.GaugeZone? =
        zones.lastOrNull { it.from <= value }

    /** The color role of a gauge's word and of its Bold fill: its value's zone, or the card's own. */
    fun gaugeRole(spec: WidgetSpec.Progress): StyledText.ColorRole? =
        zoneOf(spec.value, spec.zones)?.color ?: spec.base.color

    /**
     * How far along a path of [count] stages it is, from 0 (at the first) to 1 (at the last): the
     * stages done, and [along] (0 to 1) of the way from stage [stage] (1 to [count]) to the next.
     */
    fun stagesFraction(stage: Int, count: Int, along: Float): Float {
        if (count < 2) return if (count == 1) 1f else 0f
        val at = stage.coerceIn(1, count) - 1
        val way = if (at < count - 1 && !along.isNaN()) along.coerceIn(0f, 1f) else 0f
        return ((at + way) / (count - 1)).coerceIn(0f, 1f)
    }

    /** How far along stages are on the way to the next one, from 0 to 1: `value` of `max`. */
    fun along(spec: WidgetSpec.Progress): Float = WidgetWords.fraction(spec.value, spec.max)

    /**
     * The level a card of [spec] fills to in Bold, from 0 to 1: how far along its stages, where its
     * gauge's value sits, or else its value of its max.
     */
    fun level(spec: WidgetSpec.Progress): Float = when {
        isStages(spec) -> stagesFraction(spec.stage, spec.stages.size, along(spec))
        isGauge(spec) -> gaugeFraction(spec.value, spec.min, spec.max)
        else -> WidgetWords.fraction(spec.value, spec.max)
    }

    /**
     * One arc of a gauge, in degrees as Compose's `drawArc` takes them: 0 is at 3 o'clock, and
     * [sweep] goes clockwise. [color] is its zone's role, or null for the card's color. A [filled]
     * arc is the part up to the value; the others are the track under it.
     */
    data class Arc(
        val start: Float,
        val sweep: Float,
        val color: StyledText.ColorRole?,
        val filled: Boolean
    )

    /** A part this short, in degrees, is not drawn: its round ends would make it a dot. */
    private const val SHORTEST = 0.5f

    /**
     * The arcs of a gauge from [min] to [max], drawn from [start] over [sweep] degrees, at [value].
     * - Each zone runs from its `from` to the next zone's, the last one to [max]. Below the first
     *   zone the arc is in the card's color. Without zones it is all in the card's color.
     * - Each part has its track, and then its filled arc up to the value, if the value reaches it.
     * - Parts are [gap] degrees apart, so their round ends don't run into each other.
     */
    fun gaugeArcs(
        min: Float,
        max: Float,
        zones: List<WidgetSpec.GaugeZone>,
        value: Float,
        start: Float,
        sweep: Float,
        gap: Float
    ): List<Arc> {
        fun at(v: Float) = start + sweep * gaugeFraction(v, min, max)
        // The parts as (from, to, role), in order along the scale.
        val bounds = zones.map { it.from.coerceIn(min, max) }
        val parts = buildList<Triple<Float, Float, StyledText.ColorRole?>> {
            if (zones.isEmpty() || bounds[0] > min) add(Triple(min, bounds.firstOrNull() ?: max, null))
            zones.forEachIndexed { i, z -> add(Triple(bounds[i], bounds.getOrNull(i + 1) ?: max, z.color)) }
        }.filter { (from, to) -> to > from }
        return buildList {
            parts.forEachIndexed { i, (from, to, role) ->
                val a = at(from) + if (i > 0) gap / 2 else 0f
                val b = at(to) - if (i < parts.size - 1) gap / 2 else 0f
                if (b - a < SHORTEST) return@forEachIndexed
                add(Arc(a, b - a, role, filled = false))
                if (value > from) {
                    val end = minOf(b, at(minOf(value, to)))
                    if (end - a >= SHORTEST) add(Arc(a, end - a, role, filled = true))
                }
            }
        }
    }

    /** Where a gauge's arc sits in its box ([gaugeFit]): its center and its radius, in the box's unit. */
    data class Fit(val cx: Float, val cy: Float, val radius: Float)

    /**
     * The largest arc of [sweep] degrees, open at the bottom, that fits a box [width] by [height]
     * with [margin] kept clear on each side (half the line and the knob). It is centered across,
     * and down the middle of the room the arc takes.
     */
    fun gaugeFit(width: Float, height: Float, sweep: Float, margin: Float): Fit {
        val half = (sweep / 2).coerceIn(0f, 180f) * PI.toFloat() / 180f
        // Across: the whole circle once the arc passes the sides; else as far as its ends reach.
        val across = if (half >= PI.toFloat() / 2) 1f else sin(half)
        // Down from the center: as far as its ends reach below it.
        val below = maxOf(0f, -cos(half))
        val room = (width - 2 * margin).coerceAtLeast(0f)
        val tall = (height - 2 * margin).coerceAtLeast(0f)
        val radius = minOf(room / (2 * across), tall / (1 + below)).coerceAtLeast(0f)
        val cy = (height - radius * (1 + below)) / 2 + radius
        return Fit(width / 2, cy, radius)
    }

    /**
     * The sides and line widths of nested rings, from the outside in: [count] rings in a square
     * [outer] across. A line is about a twelfth of the side, from 5 to 13, and rings are a third of
     * a line apart (at least 2). Units are the caller's (dp).
     */
    fun ringSides(outer: Float, count: Int): List<Pair<Float, Float>> {
        if (count <= 0 || outer <= 0f) return emptyList()
        val line = (outer * 0.085f).coerceIn(5f, 13f)
        val step = 2 * (line + maxOf(2f, line / 3))
        return List(count) { i -> (outer - i * step) to line }.filter { (side) -> side >= 2 * line }
    }

    /** A number as a card writes a scale: grouped by thousands for [locale], at most two decimals: "8,000", "2.5". */
    fun grouped(v: Float, locale: Locale = Locale.getDefault()): String {
        if (v.isNaN() || v.isInfinite() || abs(v) >= 1e15f) return WidgetWords.num(v)
        val r = (v * 100.0).roundToLong() / 100.0
        val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
        return if (r == floor(r)) format.format(r.toLong()) else format.format(r)
    }

    /** What a ring's full scale is, after its value: "of 8,000", "of 8 glasses". */
    fun ringOf(bar: WidgetSpec.Bar, locale: Locale = Locale.getDefault()): String =
        listOfNotNull("of", grouped(bar.max, locale), bar.unit).joinToString(" ")

    /** A ring's value as its row writes it: its text, or the number. */
    fun ringValue(bar: WidgetSpec.Bar, locale: Locale = Locale.getDefault()): String =
        bar.text ?: grouped(bar.value, locale)

    /** A gauge's value as it is written: its text, or the number. */
    fun gaugeValue(spec: WidgetSpec.Progress): String = spec.text ?: WidgetWords.num(spec.value)

    /** "3 of 4": the stage it is at, of how many. */
    fun stageCount(spec: WidgetSpec.Progress): String = "${spec.stage} of ${spec.stages.size}"

    /**
     * What a screen reader says for a progress card, part by part ([WidgetWords.description]). It
     * has every value, also those a look or a small card leaves out.
     * - Stages: "On its way, stage 3 of 4, 40% of the way to Delivered", then the label.
     * - A gauge: its value with its unit, "118 US AQI", then the label.
     * - Rings: each one's label, value and scale, "Steps 6,240 of 8,000", then the label.
     * - A ring or bars: the text or the percent of each, then the label.
     */
    fun said(spec: WidgetSpec.Progress, locale: Locale = Locale.getDefault()): List<String?> {
        fun words(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(" ")
        return buildList {
            when {
                isStages(spec) -> {
                    val at = spec.stage.coerceIn(1, spec.stages.size)
                    add(spec.stages[at - 1].ifBlank { null })
                    add("stage ${stageCount(spec)}")
                    val along = along(spec)
                    if (at < spec.stages.size && along > 0f) {
                        val next = spec.stages[at].ifBlank { "the next stage" }
                        add("${WidgetWords.percent(along, 1f)} of the way to $next")
                    }
                }

                isGauge(spec) -> add(WidgetWords.joined(gaugeValue(spec), spec.unit))
                isRings(spec) -> spec.items.forEach {
                    add(words(it.label, ringValue(it, locale), ringOf(it, locale)))
                }

                else -> {
                    if (spec.items.isEmpty()) add(spec.text ?: WidgetWords.percent(spec.value, spec.max))
                    spec.items.forEach { b -> add(words(b.label, b.text ?: WidgetWords.percent(b.value, b.max))) }
                }
            }
            add(spec.label)
        }
    }
}
