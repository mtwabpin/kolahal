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
        const val STOPPED = 23
        const val SHUTDOWN = 26

        /** An app that comes back within this long after it went away, with no other app between, did not close: it changed screens. */
        const val SAME_SESSION_MS = 5_000L

        private const val HOUR_MS = 3_600_000L

        /** One usage event: its kind ([RESUMED] and the others), the app's package, and its time. */
        data class Event(val type: Int, val pkg: String, val time: Long)

        /** One time an app was in front, from [start] to [end]. [opened]: it was opened then, not already in front at the window's start. */
        data class Session(val pkg: String, val start: Long, val end: Long, val opened: Boolean)

        /**
         * Every app's sessions in [from, until), from [events] in time order. One app is in front at
         * a time.
         * - An app opens when its screen comes to the front after another app's, or after the
         *   screen was off. A second screen of the same app is not an open.
         * - A session ends when another app comes to the front, the screen goes off, the phone locks
         *   or shuts down. It ends when the app's screen went away, if that was first.
         * - An app already in front at [from] (its first event is going away) has a session from
         *   [from]. One still in front at [until] has one up to [until]: the last session's end is
         *   [until] then, and [inFrontAtEnd] is that app.
         */
        fun sessions(events: List<Event>, from: Long, until: Long): Pair<List<Session>, String?> {
            val out = ArrayList<Session>()
            var front: String? = null
            var frontSince = from
            var opened = false
            // When the app in front went away, while nothing else has come to the front yet.
            var awayAt: Long? = null
            // Whether anything has happened yet: before that, an app that goes away was in front at [from].
            var started = false
            fun close(at: Long) {
                val app = front
                if (app != null && at > frontSince) out += Session(app, frontSince, at, opened)
                front = null
                awayAt = null
            }
            for (e in events) {
                if (e.time < from || e.time >= until) continue
                when (e.type) {
                    RESUMED -> {
                        val away = awayAt
                        if (front == e.pkg && (away == null || e.time - away <= SAME_SESSION_MS)) {
                            // Another screen of the app in front, or back to it at once.
                            awayAt = null
                        } else {
                            close(away ?: e.time)
                            front = e.pkg
                            frontSince = e.time
                            opened = true
                        }
                        started = true
                    }
                    PAUSED, STOPPED -> {
                        if (!started && front == null) {
                            // In front since before [from].
                            front = e.pkg
                            frontSince = from
                            opened = false
                        }
                        if (front == e.pkg && awayAt == null) awayAt = e.time
                        started = true
                    }
                    SCREEN_OFF, LOCKED, SHUTDOWN -> {
                        close(awayAt ?: e.time)
                        started = true
                    }
                }
            }
            val stillInFront = front.takeIf { awayAt == null }
            close(awayAt ?: until)
            return out to stillInFront
        }

        /** Each app's time in front in [from, until), from its sessions ([sessions]), by package. */
        fun totals(events: List<Event>, from: Long, until: Long): Map<String, Long> =
            sessions(events, from, until).first.groupingBy { it.pkg }.fold(0L) { sum, s -> sum + (s.end - s.start) }

        /** [pkg]'s sessions in [from, until) ([sessions]), with a slot in [hours] for each hour from [from], [hourCount] in all. */
        fun of(events: List<Event>, pkg: String, from: Long, until: Long, hourCount: Int = 24): AppSessions {
            val (all, inFront) = sessions(events, from, until)
            val mine = all.filter { it.pkg == pkg }
            val hours = LongArray(hourCount)
            for (s in mine) {
                var t = s.start
                while (t < s.end) {
                    val slot = ((t - from) / HOUR_MS).toInt()
                    val upTo = minOf(s.end, from + (slot + 1) * HOUR_MS)
                    if (slot in 0 until hourCount) hours[slot] += upTo - t
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
