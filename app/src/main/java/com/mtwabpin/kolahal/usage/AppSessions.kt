/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.usage

/**
 * One app's sessions in a window of time (a day, so far), worked out from Android's usage events:
 * how often it was opened, its longest session, when it was last used, and its time in each hour.
 * - Every time `usage` shows comes from these sessions, counted from local midnight. Android's own
 *   daily totals start at another hour (midnight UTC on many phones), so they mix two days.
 * - [hours] has a slot for each hour from the window's start: the milliseconds the app was in front.
 */
class AppSessions(
    val opens: Int,
    val total: Long,
    val longest: Long,
    /** When the longest session started. Null with no session. */
    val longestAt: Long?,
    /** When the app was last in front: the end of its last session. Null with no session. */
    val lastUsed: Long?,
    /** True when the app is still in front at the window's end. */
    val inFront: Boolean,
    val hours: LongArray,
) {
    companion object {
        /** The kinds of event this reads (UsageEvents.Event): an app's screen came to the front or went away, the screen went off, the phone locked or shut down. */
        const val RESUMED = 1
        const val PAUSED = 2
        const val SCREEN_OFF = 16
        const val LOCKED = 17
        const val SHUTDOWN = 26
        const val STARTUP = 27

        /**
         * With no event of any kind for this long, the app in front was not in use: the phone was off
         * with no "shut down" written (a flat battery), or it slept. Its session ends at the last event.
         */
        const val SILENT_MS = 6 * 3_600_000L

        /** An app that comes back within this long after it went away, with no other app between, did not close: it changed screens. */
        const val SAME_SESSION_MS = 5_000L

        /**
         * How long before a window the events are read too ([AppUsage]): the app in front at the
         * window's start (at midnight) is known from its screen coming to the front before.
         */
        const val LOOK_BEFORE_MS = 6 * 3_600_000L

        /** One usage event: its kind ([RESUMED] and the others), the app's package, the screen's class, and its time. */
        data class Event(val type: Int, val pkg: String, val time: Long, val screen: String = "")

        /** One time an app was in front, from [start] to [end]. [opened]: it was opened then, not already in front before. */
        data class Session(val pkg: String, val start: Long, val end: Long, val opened: Boolean)

        /**
         * Every app's sessions in [from, until), from [events] in time order. One app is in front at
         * a time; the second [Pair] is the app still in front at [until], if any.
         * - An app's screens are followed by name: the app goes away only when none of them is in
         *   front. Moving from one screen of an app to another (its first screen pauses, the next
         *   comes to the front, the first stops) is one session and no open.
         * - "Stopped" says nothing new: a screen stops after it paused. It is not read.
         * - An app opens when its screen comes to the front after another app's, after the screen
         *   was off, or after it was away for more than [SAME_SESSION_MS].
         * - A session ends when another app comes to the front, the screen goes off, the phone locks
         *   or shuts down; it ends when the app went away, if that was first.
         * - An app whose first event is a screen going away was in front since before [from].
         */
        fun sessions(events: List<Event>, from: Long, until: Long): Pair<List<Session>, String?> {
            val out = ArrayList<Session>()
            var front: String? = null
            // The front app's screens that are in front now.
            val screens = HashSet<String>()
            var frontSince = from
            var opened = false
            // When the front app's last screen went away, while nothing else has come to the front yet.
            var awayAt: Long? = null
            var started = false
            fun close(at: Long) {
                val app = front
                if (app != null && at > frontSince) out += Session(app, frontSince, at, opened)
                front = null
                screens.clear()
                awayAt = null
            }

            fun open(pkg: String, screen: String, at: Long) {
                front = pkg
                screens += screen
                frontSince = at
                opened = true
            }
            // The time of the last event read: a session ends there after a long silence, or a start-up.
            var lastEvent = from
            for (e in events) {
                if (e.time !in from..<until) continue
                if (front != null && e.time - lastEvent > SILENT_MS) close(awayAt ?: lastEvent)
                val before = lastEvent
                lastEvent = e.time
                when (e.type) {
                    RESUMED -> {
                        val away = awayAt
                        when {
                            front != e.pkg -> {
                                close(away ?: e.time); open(e.pkg, e.screen, e.time)
                            }
                            // Back after a while, with nothing between: opened again.
                            away != null && e.time - away > SAME_SESSION_MS -> {
                                close(away); open(e.pkg, e.screen, e.time)
                            }
                            // Another screen of the app in front, or back to it at once.
                            else -> {
                                screens += e.screen; awayAt = null
                            }
                        }
                        started = true
                    }

                    PAUSED -> {
                        if (!started && front == null) {
                            // In front since before [from].
                            front = e.pkg
                            frontSince = from
                            opened = false
                            screens += e.screen
                        }
                        if (front == e.pkg) {
                            screens -= e.screen
                            if (screens.isEmpty() && awayAt == null) awayAt = e.time
                        }
                        started = true
                    }
                    // The phone started: whatever was in front ended with it, at the last event before.
                    STARTUP -> close(awayAt ?: before)
                    SCREEN_OFF, LOCKED, SHUTDOWN -> {
                        close(awayAt ?: e.time)
                        started = true
                    }
                }
            }
            // Silent up to the window's end: not in use since its last event.
            if (front != null && until - lastEvent > SILENT_MS) close(awayAt ?: lastEvent)
            val stillInFront = front.takeIf { awayAt == null }
            close(awayAt ?: until)
            return out to stillInFront
        }

        /**
         * [sessions] cut to [from, until): a session over the edge keeps only its part inside. A part
         * that starts at [from] was opened before, so it is no open in the window.
         */
        fun clip(sessions: List<Session>, from: Long, until: Long): List<Session> =
            sessions.mapNotNull { s ->
                val start = maxOf(s.start, from)
                val end = minOf(s.end, until)
                if (end <= start) null else Session(s.pkg, start, end, s.opened && s.start >= from)
            }

        /** Each app's time in front in [sessions], by package. */
        fun totals(sessions: List<Session>): Map<String, Long> =
            sessions.groupingBy { it.pkg }.fold(0L) { sum, s -> sum + (s.end - s.start) }

        /**
         * [pkg]'s use in [sessions] (already cut to one day), with a slot in [hours] for each hour of the
         * clock in [zone]: on a day the clock changes, an hour may get two hours' time, or none.
         * [inFront]: the app in front at the window's end, if any.
         */
        fun of(
            sessions: List<Session>,
            pkg: String,
            inFront: String?,
            zone: java.time.ZoneId = java.time.ZoneId.systemDefault()
        ): AppSessions {
            val mine = sessions.filter { it.pkg == pkg }
            val hours = LongArray(24)
            for (s in mine) {
                var t = s.start
                while (t < s.end) {
                    val at = java.time.Instant.ofEpochMilli(t).atZone(zone)
                    val next =
                        at.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusHours(1).toInstant()
                            .toEpochMilli()
                    val upTo = minOf(s.end, next)
                    hours[at.hour] += upTo - t
                    t = upTo
                }
            }
            val longest = mine.maxByOrNull { it.end - it.start }
            return AppSessions(
                opens = mine.count { it.opened },
                total = mine.sumOf { it.end - it.start },
                longest = longest?.let { it.end - it.start } ?: 0L,
                longestAt = longest?.start,
                lastUsed = mine.lastOrNull()?.end,
                inFront = inFront == pkg,
                hours = hours,
            )
        }
    }
}
