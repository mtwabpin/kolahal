package com.mtwabpin.kolahal.productivity

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** A calendar instance as [Agenda.query] reads it. [where] is its place, and [calendar] the id of its calendar. */
data class AgendaEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val where: String? = null,
    val calendar: Long = 0,
)

/**
 * A calendar event as a card or a script shows it ([Agenda.shown]).
 * - [starts] and [ends] are in seconds since 1970, as a widget takes them. An all-day event starts
 *   and ends at midnights on the phone.
 * - [where] and [calendar] come from [AgendaEvent]. A script's `calendar.events` has neither.
 * - [begin] is the start as the calendar keeps it, in milliseconds. A tap opens the event at that
 *   time, so the right day of a repeating event opens.
 */
data class ShownEvent(
    val id: Long,
    val title: String,
    val starts: Long,
    val ends: Long,
    val allDay: Boolean,
    val begin: Long,
    val where: String? = null,
    val calendar: Long = 0,
)

/** `agenda`: upcoming calendar instances. Needs READ_CALENDAR. */
object Agenda {
    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /**
     * Returns instances that overlap [from, from + days) in the local zone. It skips hidden calendars
     * and events the user declined.
     */
    fun query(context: Context, from: LocalDate, days: Int, zone: ZoneId = ZoneId.systemDefault()): List<AgendaEvent> {
        val start = from.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = from.plusDays(days.toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        // All-day instances are stored at UTC midnight. So we widen the range by one day on each side,
        // and group() removes the extra ones.
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, start - 86_400_000L)
            ContentUris.appendId(it, end + 86_400_000L)
        }.build()
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.CALENDAR_ID,
        )
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Instances.SELF_ATTENDEE_STATUS} != ${CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED}"
        val out = ArrayList<AgendaEvent>()
        context.contentResolver.query(uri, projection, selection, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { c ->
            while (c.moveToNext()) {
                out += AgendaEvent(
                    id = c.getLong(0),
                    title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(no title)",
                    begin = c.getLong(2),
                    end = c.getLong(3),
                    allDay = c.getInt(4) != 0,
                    where = c.getString(5)?.trim()?.takeIf { it.isNotEmpty() },
                    calendar = c.getLong(6),
                )
            }
        }
        return out
    }

    /**
     * Pure. Groups events by day for [days] days from [from]. Days without events are left out.
     * - All-day events (UTC dates, end exclusive) come first. They repeat on every day they cover.
     * - A timed event shows on the day it starts. If it started before [from] and is still running,
     *   it shows on [from].
     * - Timed events that ended before [now] are dropped.
     */
    fun group(events: List<AgendaEvent>, zone: ZoneId, from: LocalDate, days: Int, now: Long): List<Pair<LocalDate, List<AgendaEvent>>> {
        val last = from.plusDays(days.toLong() - 1)
        val byDay = sortedMapOf<LocalDate, MutableList<AgendaEvent>>()
        for (e in events) {
            if (e.allDay) {
                val first = Instant.ofEpochMilli(e.begin).atZone(ZoneOffset.UTC).toLocalDate()
                val endExclusive = Instant.ofEpochMilli(e.end).atZone(ZoneOffset.UTC).toLocalDate()
                var d = maxOf(first, from)
                val stop = minOf(maxOf(endExclusive, first.plusDays(1)), last.plusDays(1))
                while (d.isBefore(stop)) {
                    byDay.getOrPut(d) { ArrayList() } += e
                    d = d.plusDays(1)
                }
            } else {
                if (maxOf(e.begin, e.end) < now) continue
                val day = maxOf(Instant.ofEpochMilli(e.begin).atZone(zone).toLocalDate(), from)
                if (day.isAfter(last)) continue
                byDay.getOrPut(day) { ArrayList() } += e
            }
        }
        return byDay.map { (day, list) ->
            day to list.sortedWith(compareBy({ !it.allDay }, { it.begin }, { it.title }))
        }
    }

    /**
     * Pure. The events of [days] days from today, as [query] gave them at [now] (in milliseconds),
     * in the order they are shown: by day, and all-day events first in a day. The Agenda card and
     * a script's `calendar.events` both show this list.
     * - [group] says which events count. The read is a day wider on each side than what is shown,
     *   and an event that has ended is left out.
     * - An all-day event of several days is in the group of each of its days. It is one event here.
     * - The calendar keeps an all-day event at midnight UTC. Here it starts at midnight in [zone]
     *   and ends at the midnight after its last day. Left at UTC it would show a day early west of
     *   UTC, and stay for hours east of it. One that started before today starts today.
     * - Every event has its end, also one with no length: its end is its start. Whoever shows the
     *   list can then drop an event when its time has come, as [group] does.
     */
    fun shown(events: List<AgendaEvent>, zone: ZoneId, now: Long, days: Int): List<ShownEvent> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        return group(events, zone, today, days, now).flatMap { it.second }.distinct().map { e ->
            if (e.allDay) {
                val first = Instant.ofEpochMilli(e.begin).atZone(ZoneOffset.UTC).toLocalDate()
                val after = maxOf(Instant.ofEpochMilli(e.end).atZone(ZoneOffset.UTC).toLocalDate(), first.plusDays(1))
                ShownEvent(
                    id = e.id,
                    title = e.title,
                    starts = maxOf(first, today).atStartOfDay(zone).toEpochSecond(),
                    ends = after.atStartOfDay(zone).toEpochSecond(),
                    allDay = true,
                    begin = e.begin,
                    where = e.where,
                    calendar = e.calendar,
                )
            } else {
                ShownEvent(e.id, e.title, starts = e.begin / 1000, ends = maxOf(e.begin, e.end) / 1000, allDay = false, begin = e.begin, where = e.where, calendar = e.calendar)
            }
        }
    }

    fun eventUri(id: Long) = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
}
