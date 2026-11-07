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

data class AgendaEvent(val id: Long, val title: String, val begin: Long, val end: Long, val allDay: Boolean)

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

    fun eventUri(id: Long) = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
}
