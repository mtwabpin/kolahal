/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.widgets.WidgetParser
import com.mtwabpin.kolahal.widgets.WidgetSpec
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The words and the places of a `sky` card ([WidgetSpec.Sky]): where the sun or the moon is at a
 * moment, what comes next, and what a screen reader says. Pure, so it is unit tested. Times are in
 * seconds, as a script gives them.
 */
internal object SkyWords {
    /**
     * Where the sun or the moon is at a moment ([place]).
     * - [up]: above the horizon, on the day arc. [along] is how far along it is, from 0 at its rise
     *   to 1 at its set.
     * - Not [up]: under the horizon, on the night arc. [along] runs from 0 at its last set to 1 at
     *   its next rise.
     * - [rises] and [sets] are the times at the arc's two ends: the rise and the set of this day,
     *   or at night the next rise and the last set. [next] is the next of the two to come.
     */
    data class Place(
        val up: Boolean,
        val along: Float,
        val rises: Long,
        val sets: Long,
        val next: Long,
    )

    /**
     * [t] moved by [days] days of the sun, or of the [moon]. A day of the sun is a day on the
     * phone's clock in [zone], so a sunrise keeps its time of day where the clocks change. A day
     * of the moon is [WidgetParser.MOON_DAY].
     */
    fun shifted(t: Long, days: Long, moon: Boolean, zone: ZoneId): Long =
        if (moon) t + days * WidgetParser.MOON_DAY
        else Instant.ofEpochSecond(t).atZone(zone).plusDays(days).toEpochSecond()

    /**
     * Where the body that rises at [rises] and sets at [sets] is at [now]. The times may be of
     * another day: they are moved by whole days ([shifted]) to the day that holds [now]. So the
     * card stays right with no run of the script, a little off as the times drift from day to day.
     */
    fun place(rises: Long, sets: Long, now: Long, moon: Boolean, zone: ZoneId): Place {
        val day = WidgetParser.dayOf(moon)
        var days = Math.floorDiv(now - rises, day)
        // A day on the clock is 23 or 25 hours where the clocks change: step to the day that holds now.
        while (shifted(rises, days, moon, zone) > now) days--
        while (shifted(rises, days + 1, moon, zone) <= now) days++
        val r = shifted(rises, days, moon, zone)
        val s = shifted(sets, days, moon, zone)
        if (now < s) return Place(up = true, along = part(now - r, s - r), rises = r, sets = s, next = s)
        val again = shifted(rises, days + 1, moon, zone)
        return Place(up = false, along = part(now - s, again - s), rises = again, sets = s, next = again)
    }

    /** [done] of [whole], from 0 to 1. A whole of nothing is done. */
    private fun part(done: Long, whole: Long): Float =
        if (whole <= 0) 1f else (done.toDouble() / whole).toFloat().coerceIn(0f, 1f)

    /**
     * Where a body [along] its arc is, as a step from the arc's middle on the horizon: x from -1
     * (the arc's start, where it rises) to 1 (where it sets), and y from -1 (the day arc's top) to
     * 1 (the night arc's bottom), as a screen counts y. A card multiplies them by the arc's width
     * and height.
     * - [up]: the day arc, from the rise on the left over the top to the set on the right.
     * - Not [up]: the night arc, from the set on the right under the horizon back to the rise.
     */
    fun onArc(along: Float, up: Boolean): Pair<Float, Float> {
        val a = if (up) PI * (1 + along) else PI * along
        return cos(a).toFloat() to sin(a).toFloat()
    }

    /**
     * The arcs of a sky in a box [width] by [height], with [unit] for 1 dp: all in the box's units,
     * from its top left.
     * - [body] is the radius of the sun or the moon: about a seventh of the box's smaller side,
     *   from 7 dp to 16 dp.
     * - [horizon] is where the horizon is. Under it is room for the night arc, [depth] deep, and
     *   for the body on it, so the night arc keeps inside the box.
     * - The day arc is [rx] wide and [ry] high on each side of [cx], and keeps inside the box with
     *   the body on it. It is at most a little higher than wide, as the mockup draws it.
     */
    data class Arcs(val cx: Float, val horizon: Float, val rx: Float, val ry: Float, val depth: Float, val body: Float)

    fun arcs(width: Float, height: Float, unit: Float): Arcs {
        val body = (minOf(width, height) * 0.14f).coerceIn(7 * unit, 16 * unit)
        val depth = minOf(body * 1.2f, height * 0.2f).coerceAtLeast(0f)
        val horizon = height - depth - body - unit
        val rx = (width / 2 - body - 2 * unit).coerceAtLeast(unit)
        val ry = (horizon - body - 2 * unit).coerceIn(unit, maxOf(unit, rx * 1.05f))
        return Arcs(cx = width / 2, horizon = horizon, rx = rx, ry = ry, depth = depth, body = body)
    }

    /** The name of a rise or a set: "Sunrise", "Sunset", "Moonrise", "Moonset". */
    fun eventName(rise: Boolean, moon: Boolean): String = when {
        moon && rise -> "Moonrise"
        moon -> "Moonset"
        rise -> "Sunrise"
        else -> "Sunset"
    }

    /** [seconds] to go, in whole minutes, rounded up: what is still to come is never "0 min". */
    private fun minutesLeft(seconds: Long): Int =
        ((seconds.coerceAtLeast(1) + 59) / 60).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /** A time to go, as a card writes it ([WidgetWords.hoursAndMinutes]): "2 h 10 min", "3 h", "45 min". */
    fun span(seconds: Long): String = WidgetWords.hoursAndMinutes(minutesLeft(seconds))

    /** [span] for a screen reader ([WidgetWords.hoursAndMinutesSaid]): "2 hours 10 minutes", "1 hour", "1 minute". */
    fun spanSaid(seconds: Long): String = WidgetWords.hoursAndMinutesSaid(minutesLeft(seconds))

    /**
     * What comes next at [now], from the longest to the shortest, for a card to write the first
     * that fits: "Sunset in 2 h 10 min", then "Sunset".
     */
    fun nextLines(place: Place, now: Long, moon: Boolean): List<String> {
        val name = eventName(rise = !place.up, moon = moon)
        return listOf("$name in ${span(place.next - now)}", name)
    }

    /** The words a card writes in its room ([fitLine]): [text] at [size] sp, on two lines when [twoLines]. */
    data class LineFit(val text: String, val size: Float, val twoLines: Boolean)

    /**
     * Which of [lines] a card writes in [room], and how, where [width] gives a text's width at a
     * size in sp (in the room's unit).
     * - The first, the longest, that fits whole at [size]: "Sunset in 2 h 10 min", else "Sunset".
     * - When none does, the last one at a smaller size, down to [least] sp: "Waning crescent".
     * - When even that is too wide, the last one on two lines, at [least] or 11 sp, whichever is
     *   larger and no larger than [size]: it breaks between its words, never inside one.
     */
    fun fitLine(
        lines: List<String>,
        size: Float,
        least: Float,
        room: Float,
        width: (String, Float) -> Float
    ): LineFit {
        lines.firstOrNull { width(it, size) <= room }?.let { return LineFit(it, size, twoLines = false) }
        val last = lines.lastOrNull() ?: return LineFit("", size, twoLines = false)
        var sp = size - 1f
        while (sp >= least) {
            if (width(last, sp) <= room) return LineFit(last, sp, twoLines = false)
            sp -= 1f
        }
        return LineFit(last, minOf(size, maxOf(least, 11f)), twoLines = true)
    }

    /** The time of day of [t] as Kolahal writes it ([TimeFormats.clock]): "17:20" or "5:20 PM". */
    fun clock(t: Long, zone: ZoneId, clock24: Boolean, locale: Locale = Locale.getDefault()): String =
        Instant.ofEpochSecond(t).atZone(zone).format(TimeFormats.clockFormat(clock24, locale))

    /** The part of the moon that is lit, from 0 to 1, as a card writes it: "78%". */
    fun lit(phase: Float): String = "${(phase.coerceIn(0f, 1f) * 100).roundToInt()}%"

    /** Below this lit part the moon is new, and above 1 less it is full. */
    private const val NEW = 0.03f

    /** A lit part this close to a half is a quarter. */
    private const val QUARTER = 0.03f

    /**
     * The name of the moon's phase with [phase] lit, [waxing] or not: "New moon", "Waxing crescent",
     * "First quarter", "Waxing gibbous", "Full moon", "Waning gibbous", "Last quarter",
     * "Waning crescent".
     */
    fun moonName(phase: Float, waxing: Boolean): String = when {
        phase < NEW -> "New moon"
        phase > 1f - NEW -> "Full moon"
        abs(phase - 0.5f) <= QUARTER -> if (waxing) "First quarter" else "Last quarter"
        phase < 0.5f -> if (waxing) "Waxing crescent" else "Waning crescent"
        else -> if (waxing) "Waxing gibbous" else "Waning gibbous"
    }

    /**
     * How a moon with [phase] lit is drawn on its disc.
     * - [litRight]: the lit side is the right one, as a waxing moon is seen from the north.
     * - [edge]: the half width of the line between light and dark (an ellipse), as a part of the
     *   disc's radius: 1 for a new or a full moon, 0 at a quarter.
     * - [crescent]: that line bows toward the lit side, so less than half is lit.
     */
    data class MoonShape(val litRight: Boolean, val edge: Float, val crescent: Boolean)

    fun moonShape(phase: Float, waxing: Boolean): MoonShape {
        val k = phase.coerceIn(0f, 1f)
        return MoonShape(litRight = waxing, edge = abs(1f - 2f * k), crescent = k < 0.5f)
    }

    /** The next phases at [now]: those still to come, the soonest first. */
    fun upcoming(phases: List<WidgetSpec.Phase>, now: Long): List<WidgetSpec.Phase> =
        phases.filter { it.at > now }.sortedBy { it.at }

    /** A phase's day as a card writes it under its disc: "28 Nov". */
    fun phaseDay(at: Long, zone: ZoneId, locale: Locale = Locale.getDefault()): String =
        Instant.ofEpochSecond(at).atZone(zone).format(DateTimeFormatter.ofPattern("d MMM", locale))

    /**
     * The card's big text: the time of the next rise or set ("17:20"), or for the moon the part
     * of it that is lit ("78%").
     */
    fun main(spec: WidgetSpec.Sky, place: Place, zone: ZoneId, clock24: Boolean, locale: Locale = Locale.getDefault()): String =
        spec.phase?.let(::lit) ?: clock(place.next, zone, clock24, locale)

    /**
     * What a screen reader says for [spec] at [now], in parts.
     * - The sun: "Sunset in 2 hours 10 minutes", "sunrise 07:38", "sunset 17:20".
     * - The moon: its phase's name, "78% lit", what comes next, its rise and its set, and each of
     *   its next phases with its day: "Full moon 28 Nov".
     */
    fun said(
        spec: WidgetSpec.Sky,
        now: Long,
        zone: ZoneId,
        clock24: Boolean,
        locale: Locale = Locale.getDefault()
    ): List<String> {
        val moon = spec.phase != null
        val place = place(spec.rises, spec.sets, now, moon, zone)
        val next = "${eventName(rise = !place.up, moon = moon)} in ${spanSaid(place.next - now)}"
        val ends = listOf(
            "${eventName(rise = true, moon = moon).lowercase()} ${clock(place.rises, zone, clock24, locale)}",
            "${eventName(rise = false, moon = moon).lowercase()} ${clock(place.sets, zone, clock24, locale)}",
        )
        val phase = spec.phase ?: return listOf(next) + ends
        return listOf(moonName(phase, spec.waxing), "${lit(phase)} lit", next) + ends +
                upcoming(spec.phases, now).map { "${moonName(it.phase, it.waxing)} ${phaseDay(it.at, zone, locale)}" }
    }
}
