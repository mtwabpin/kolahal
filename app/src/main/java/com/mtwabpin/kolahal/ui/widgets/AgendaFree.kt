/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.widgets.WidgetSpec
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/**
 * What an agenda's Now look shows ([com.mtwabpin.kolahal.widgets.WidgetLook.NOW]): what is on now
 * and how far along it is, the free time until the next event, and that event; and the day with
 * the free time between its events. Pure, so it is unit tested. Times are in seconds.
 * - The events are the ones the agenda shows ([WidgetWords.agenda]): an event that has ended is
 *   left out, in the days the agenda has.
 * - An all-day event is never "now", and has no free time around it.
 * - An event is on now when it has begun and has an end still to come. An event with no end has
 *   no length: the free time after it counts from its start.
 */
internal object AgendaFree {
    /** Less free time than this between two events is not said: they follow each other. */
    const val LEAST_FREE = 15 * 60L

    /** [seconds] in whole minutes, to the nearest: under a minute is 1. */
    private fun minutesOf(seconds: Long): Int =
        maxOf(1L, (seconds + 30) / 60).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    /**
     * A length of time as the card writes it, as every card does ([WidgetWords.hoursAndMinutes]):
     * "45 min", "1 h", "1 h 30 min", "12 h". Under a minute is "1 min".
     */
    fun span(seconds: Long): String = WidgetWords.hoursAndMinutes(minutesOf(seconds))

    /** The same for a screen reader ([WidgetWords.hoursAndMinutesSaid]): "45 minutes", "1 hour", "1 hour 30 minutes". */
    fun spanSaid(seconds: Long): String = WidgetWords.hoursAndMinutesSaid(minutesOf(seconds))

    /** The event on now: [event], from 0 to 1 of the way through ([fraction]), and the time it ends ([until], "11:30"). */
    data class Now(val event: WidgetSpec.Event, val fraction: Float, val until: String)

    /**
     * What comes next: [event] at [time] as the card writes it ("13:00", "all day"), on [day]
     * ("Tomorrow", "Tue 20 Oct"), or null for today.
     */
    data class Next(val event: WidgetSpec.Event, val time: String, val day: String?)

    /**
     * The agenda at a moment.
     * - [now]: the event on now.
     * - [next]: the next event to start. With none, the first event of a later day: an all-day one.
     * - [free]: the time until the next event when it is today: from now, or from the end of what
     *   is on now. Null when there is none, or the next event is on a later day.
     * - [first]: today's first event as the card writes it, for a card with neither an event on now
     *   nor free time: an all-day event, or one that began with no end. Null when it is [next].
     */
    data class Moment(
        val now: Now?,
        val next: Next?,
        val free: Long?,
        val first: WidgetWords.AgendaLine?,
    )

    /** What the Now look's wide card shows at [now], in [zone], with times as [clock24] says. */
    fun moment(
        spec: WidgetSpec.Agenda,
        now: Long,
        zone: ZoneId,
        clock24: Boolean,
        locale: Locale = Locale.getDefault()
    ): Moment {
        val days = WidgetWords.agenda(spec, now, zone, clock24, locale)
        val clock = TimeFormats.clockFormat(clock24, locale)
        fun at(seconds: Long) = Instant.ofEpochSecond(seconds).atZone(zone)
        val today = at(now).toLocalDate()
        val todays = days.firstOrNull { it.label == WidgetWords.TODAY }?.lines.orEmpty()
        // What goes on now; of two, the one that began last.
        val going = todays.filter { going(it.event, now) }
        val on = going.maxByOrNull { it.event.starts }?.event?.let { e ->
            val ends = e.ends ?: return@let null
            val end = at(ends)
            val until = if (end.toLocalDate() == today) end.format(clock)
            else end.format(TimeFormats.dayFormat(end.year, today.year, locale))
            Now(e, ((now - e.starts).toFloat() / (ends - e.starts)).coerceIn(0f, 1f), until)
        }
        val starts = days.firstNotNullOfOrNull { day ->
            day.lines.firstOrNull { !it.event.allDay && it.event.starts > now }
                ?.let { Next(it.event, it.time, day.label.takeIf { label -> label != WidgetWords.TODAY }) }
        }
        // Nothing starts later: the first event of a later day, if any.
        val next = starts ?: days.firstOrNull { it.label != WidgetWords.TODAY }?.let { day ->
            day.lines.first().let { Next(it.event, it.time, day.label) }
        }
        // Free from now, or from the latest end of what goes on now.
        val busy = going.mapNotNull { it.event.ends }.maxOrNull() ?: now
        val free = next?.takeIf { it.day == null }?.let { it.event.starts - maxOf(now, busy) }?.takeIf { it > 0 }
        val first = todays.firstOrNull()?.takeIf { it.event != next?.event }
        return Moment(on, next, free, first)
    }

    /** Whether [event] goes on at [now]: it has begun and its end is still to come. Never an all-day event. */
    private fun going(event: WidgetSpec.Event, now: Long): Boolean {
        val ends = event.ends ?: return false
        return !event.allDay && event.starts <= now && now < ends
    }

    /** One line of the Now look's day ([day]): a day's heading, an event with its length, or free time between two events. */
    sealed interface Line {
        /** A day's heading: [label] ("Today") and its date ("Tue 24 Nov"). */
        data class Head(val label: String, val date: String) : Line

        /** An event as the card writes it, and its [length] ("1 h 30 min"), or null without one. */
        data class Event(val line: WidgetWords.AgendaLine, val length: String?) : Line

        /** Free time between two events, or from now to the first. */
        data class Free(val seconds: Long) : Line
    }

    /**
     * The days of the Now look's large card: each day's heading, then its events, with the free
     * time between two of them where it is [LEAST_FREE] or more. Today's first free time counts
     * from now. All-day events come first in their day, with no free time around them.
     */
    fun day(
        spec: WidgetSpec.Agenda,
        now: Long,
        zone: ZoneId,
        clock24: Boolean,
        locale: Locale = Locale.getDefault()
    ): List<Line> {
        val days = WidgetWords.agenda(spec, now, zone, clock24, locale)
        fun at(seconds: Long) = Instant.ofEpochSecond(seconds).atZone(zone)
        val today = at(now).toLocalDate()
        return buildList {
            for (day in days) {
                val date = at(day.lines.first().event.starts).toLocalDate().let { if (it.isBefore(today)) today else it }
                add(Line.Head(day.label, date.format(TimeFormats.dayFormat(date.year, today.year, locale))))
                // Where the free time starts: now for today, nothing before a later day's first event.
                var from: Long? = if (day.label == WidgetWords.TODAY) now else null
                for (line in day.lines) {
                    val e = line.event
                    if (e.allDay) {
                        add(Line.Event(line, null))
                        continue
                    }
                    // An event that has begun has no free time before it.
                    from?.let { if (e.starts - it >= LEAST_FREE) add(Line.Free(e.starts - it)) }
                    val ends = e.ends
                    add(Line.Event(line, ends?.takeIf { it > e.starts }?.let { span(it - e.starts) }))
                    // An event with no end has no length: the free time after it counts from its start.
                    val end = maxOf(ends ?: e.starts, e.starts)
                    from = maxOf(from ?: end, end)
                }
            }
        }
    }

    /** Today's free time between now and its last event: the [Line.Free] lines up to the next day's heading. */
    fun freeToday(lines: List<Line>): Long {
        val today = lines.firstOrNull() as? Line.Head ?: return 0
        if (today.label != WidgetWords.TODAY) return 0
        return lines.drop(1).takeWhile { it !is Line.Head }.filterIsInstance<Line.Free>().sumOf { it.seconds }
    }

    /**
     * How many of [lines] fit in [heightDp], from the first: a heading [headDp] tall, an event
     * [eventDp], free time [freeDp]. When events are left out, [moreDp] is kept for "+3 more".
     * - The first line is the card's heading and always shows, with the first event under it.
     * - The lines shown never end with a heading or with free time: nothing follows them.
     */
    fun shown(lines: List<Line>, heightDp: Int, headDp: Int, eventDp: Int, freeDp: Int, moreDp: Int): Int {
        val events = lines.count { it is Line.Event }
        var used = 0
        var seen = 0
        var fit = 0
        for ((i, line) in lines.withIndex()) {
            used += when (line) {
                is Line.Head -> headDp
                is Line.Event -> eventDp
                is Line.Free -> freeDp
            }
            if (line is Line.Event) seen++
            val more = if (seen < events) moreDp else 0
            if (used + more > heightDp && seen > 1) break
            if (line is Line.Event) fit = i + 1
        }
        return fit
    }

    /**
     * What a screen reader hears of the Now look, after the agenda's events: what is on now and how
     * far along, and the free time until the next event. "Now Team call, until 11:30, 60% done, free
     * 1 hour 30 minutes until Lunch". Null with neither.
     */
    fun said(moment: Moment): String? {
        val parts = buildList {
            moment.now?.let { add("now ${it.event.title}, until ${it.until}, ${(it.fraction * 100).roundToInt()}% done") }
            val next = moment.next
            if (moment.free != null && next != null) add("free ${spanSaid(moment.free)} until ${next.event.title}")
        }
        return parts.joinToString(", ").ifEmpty { null }?.replaceFirstChar { it.titlecase() }
    }
}
