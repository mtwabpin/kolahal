package com.mtwabpin.kolahal.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import com.mtwabpin.kolahal.usage.AppSessions.Companion.Session
import java.time.LocalDate
import java.time.ZoneId

/**
 * Apps' use, from Android's usage events (Usage Access): `usage`, `usage <app>` and `usage.*`.
 * - Every time is worked out from the events, counted from local midnight ([AppSessions]): the
 *   totals, the days, the opens, the sessions and the hours. Android's own daily totals start at
 *   another hour on many phones (midnight UTC), so a day of theirs mixes two of yours.
 * - A command reads the events of its whole range once ([read]), and cuts each day from that.
 */
class AppUsage(private val context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)
    private val zone = ZoneId.systemDefault()

    fun startOf(day: LocalDate): Long = day.atStartOfDay(zone).toInstant().toEpochMilli()

    /** The sessions of a range ([read]): cut to it, with the app still in front at its end. */
    class Read(val sessions: List<Session>, val inFront: String?) {
        /** The sessions in [from, until), cut to it. */
        fun between(from: Long, until: Long): List<Session> = AppSessions.clip(sessions, from, until)
    }

    /**
     * The sessions of [from, until), from one read of the events. The read starts
     * [AppSessions.LOOK_BEFORE_MS] earlier: an app in front at [from] (at midnight) has its time from
     * there, not lost because its screen came to the front the day before.
     */
    fun read(from: Long, until: Long = System.currentTimeMillis()): Read {
        val readFrom = from - AppSessions.LOOK_BEFORE_MS
        val (all, inFront) = AppSessions.sessions(events(readFrom, until), readFrom, until)
        return Read(AppSessions.clip(all, from, until), inFront)
    }

    /** One app's time: its package, the name it shows as, and its milliseconds in front. */
    data class AppTime(val pkg: String, val label: String, val ms: Long)

    /** Each app's time ([apps], biggest first) and the time of all apps together ([total]). */
    class Times(val apps: List<AppTime>, val total: Long)

    /**
     * Screen time in [sessions]: every app, Kolahal too, as an app's use is its use. The time
     * `usage`, `usage N` and `usage.days` add up, so they agree.
     */
    fun total(sessions: List<Session>): Long = sessions.sumOf { it.end - it.start }

    /**
     * Each app's time in [sessions], biggest first, and the total ([total]), for `usage` and
     * `usage.today()`.
     * - An app with a minute or more is in the list; the rest count in the total only.
     * - [label] names a package (the app list's name); without one, Android's name for it. A package
     *   with no name at all (the phone's in-call screen) counts in the total, not in the list.
     */
    fun times(sessions: List<Session>, label: (String) -> String?): Times {
        val counted = AppSessions.totals(sessions)
        val pm = context.packageManager
        val named = counted.filter { it.value >= 60_000 }.mapNotNull { (pkg, ms) ->
            val name = label(pkg) ?: runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
            if (name.isNullOrBlank() || name == pkg) null else AppTime(pkg, name, ms)
        }.sortedByDescending { it.ms }
        return Times(named, counted.values.sum())
    }

    /** Android's usage events in [from, until), only of the kinds [AppSessions] reads. */
    private fun events(from: Long, until: Long): List<AppSessions.Companion.Event> {
        val kinds = intArrayOf(AppSessions.RESUMED, AppSessions.PAUSED, AppSessions.SCREEN_OFF, AppSessions.LOCKED, AppSessions.SHUTDOWN, AppSessions.STARTUP)
        val read = runCatching {
            if (Build.VERSION.SDK_INT >= 35) usage.queryEvents(UsageEventsQuery.Builder(from, until).setEventTypes(*kinds).build())
            else usage.queryEvents(from, until)
        }.getOrNull() ?: return emptyList()
        val out = ArrayList<AppSessions.Companion.Event>()
        val e = UsageEvents.Event()
        while (read.hasNextEvent()) {
            read.getNextEvent(e)
            if (e.eventType in kinds) out += AppSessions.Companion.Event(e.eventType, e.packageName ?: "", e.timeStamp, e.className ?: "")
        }
        return out
    }
}
