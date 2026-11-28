/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.usage

import android.annotation.SuppressLint
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

    /**
     * The sessions of a range ([read]): cut to it, with the app still in front at its end. [locks]: when the phone
     * locked and unlocked ([AppSessions.LOCKED], [AppSessions.UNLOCKED]) in time order, when they were asked for.
     */
    class Read(val sessions: List<Session>, val inFront: String?, val locks: List<AppSessions.Companion.Event> = emptyList()) {
        /** The sessions in [from, until), cut to it. */
        fun between(from: Long, until: Long): List<Session> =
            AppSessions.clip(sessions, from, until)
    }

    /**
     * The sessions of [from, until), from one read of the events. The read starts
     * [AppSessions.LOOK_BEFORE_MS] earlier: an app in front at [from] (at midnight) has its time from
     * there, not lost because its screen came to the front the day before. With [locks], the same read has
     * the unlocks too ([Read.locks]), which the sessions don't use.
     */
    fun read(from: Long, until: Long = System.currentTimeMillis(), locks: Boolean = false): Read {
        val readFrom = from - AppSessions.LOOK_BEFORE_MS
        val events = events(readFrom, until, locks)
        val (all, inFront) = AppSessions.sessions(
            if (locks) events.filter { it.type != AppSessions.UNLOCKED } else events, readFrom, until,
        )
        val locked = if (locks) events.filter { it.type == AppSessions.LOCKED || it.type == AppSessions.UNLOCKED } else emptyList()
        return Read(AppSessions.clip(all, from, until), inFront, locked)
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
     * - Each app is named as Android names it, not by a name the user gave it with `rename`. A package
     *   with no name at all (the phone's in-call screen) counts in the total, not in the list.
     */
    fun times(sessions: List<Session>): Times {
        val counted = AppSessions.totals(sessions)
        val named = counted.filter { it.value >= 60_000 }.mapNotNull { (pkg, ms) ->
            val name = com.mtwabpin.kolahal.apps.AppNames.android(context, pkg)
            if (name.isNullOrBlank() || name == pkg) null else AppTime(pkg, name, ms)
        }.sortedByDescending { it.ms }
        return Times(named, counted.values.sum())
    }

    /** Today's screen time since local midnight: each app's time and the total, as `usage` and `usage.today` show it. */
    fun today(): Times =
        times(read(startOf(LocalDate.now(zone))).sessions)

    /** The screen time of each of [days], in milliseconds, from one read of the events: what `usage N` and `usage.days` add up. */
    fun perDay(days: List<LocalDate>): List<Long> {
        if (days.isEmpty()) return emptyList()
        val read = read(startOf(days.first()))
        return days.map { d -> total(read.between(startOf(d), startOf(d.plusDays(1)))) }
    }

    /** Android's usage events in [from, until), only of the kinds [AppSessions] reads, and the unlocks with [unlocks]. */
    @SuppressLint("WrongConstant")
    private fun events(from: Long, until: Long, unlocks: Boolean = false): List<AppSessions.Companion.Event> {
        val sessionKinds = intArrayOf(
            AppSessions.RESUMED,
            AppSessions.PAUSED,
            AppSessions.SCREEN_OFF,
            AppSessions.LOCKED,
            AppSessions.SHUTDOWN,
            AppSessions.STARTUP
        )
        val kinds = if (unlocks) sessionKinds + AppSessions.UNLOCKED else sessionKinds
        val read = runCatching {
            if (Build.VERSION.SDK_INT >= 35) usage.queryEvents(
                UsageEventsQuery.Builder(from, until).setEventTypes(*kinds).build()
            )
            else usage.queryEvents(from, until)
        }.getOrNull() ?: return emptyList()
        val out = ArrayList<AppSessions.Companion.Event>()
        val e = UsageEvents.Event()
        while (read.hasNextEvent()) {
            read.getNextEvent(e)
            if (e.eventType in kinds) out += AppSessions.Companion.Event(
                e.eventType,
                e.packageName ?: "",
                e.timeStamp,
                e.className ?: ""
            )
        }
        return out
    }
}
