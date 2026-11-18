package com.mtwabpin.kolahal.usage

/**
 * One app's sessions in a window of time (a day, so far), worked out from Android's usage events:
 * how often it was opened, its longest session, when it was last used, and its time in each hour.
 * - [total] is the time its sessions add up to. `usage` shows Android's own total for the app,
 *   which can differ a little; `usage <app>` shows Android's total too, and these for the rest.
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

        /**
         * [pkg]'s sessions in [from, until), from [events] in time order.
         * - An app opens when its screen comes to the front after another app's, or after the
         *   screen was off. A second screen of the same app is not an open.
         * - A session ends when another app comes to the front, the screen goes off, the phone locks
         *   or shuts down. It ends when the app's screen went away, if that was first.
         * - An app already in front at [from] (its first event is going away) has a session from
         *   [from]. One still in front at [until] has one up to [until].
         * - [hours] has a slot for each hour from [from], [hourCount] in all.
         */
        fun of(events: List<Event>, pkg: String, from: Long, until: Long, hourCount: Int = 24): AppSessions {
            val sessions = ArrayList<LongArray>()
            var front: String? = null
            var frontSince = from
            // When the app in front went away, while nothing else has come to the front yet.
            var awayAt: Long? = null
            // Whether anything has happened yet: before that, an app that goes away was in front at [from].
            var started = false
            var opens = 0
            fun close(at: Long) {
                if (front == pkg && at > frontSince) sessions += longArrayOf(frontSince, at)
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
                            if (e.pkg == pkg) opens++
                        }
                        started = true
                    }
                    PAUSED, STOPPED -> {
                        if (!started && front == null && e.pkg == pkg) {
                            // In front since before [from].
                            front = pkg
                            frontSince = from
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
            val inFront = front == pkg && awayAt == null
            close(awayAt ?: until)
            val hours = LongArray(hourCount)
            for ((start, end) in sessions.map { it[0] to it[1] }) {
                var t = start
                while (t < end) {
                    val slot = ((t - from) / HOUR_MS).toInt()
                    val slotEnd = from + (slot + 1) * HOUR_MS
                    val upTo = minOf(end, slotEnd)
                    if (slot in 0 until hourCount) hours[slot] += upTo - t
                    t = upTo
                }
            }
            val longest = sessions.maxByOrNull { it[1] - it[0] }
            return AppSessions(
                opens = opens,
                total = sessions.sumOf { it[1] - it[0] },
                longest = longest?.let { it[1] - it[0] } ?: 0L,
                longestAt = longest?.get(0),
                lastUsed = sessions.lastOrNull()?.get(1),
                inFront = inFront,
                hours = hours,
            )
        }
    }
}
