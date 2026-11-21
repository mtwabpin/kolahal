/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.usage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Android's usage at its four levels, for `usage-summary`. Android keeps each app's time in front in
 * buckets a day, a week, a month and a year long: about 10 days, 4 weeks, 6 months and 2 years.
 * - A bucket starts when Android rolled the one before over, not at midnight or on the 1st: on one
 *   phone the days start near 4 AM and the months on the 12th. So each bucket keeps its real start
 *   and end, and the output says them.
 * - The newest bucket of a level is the current one, still filling ([Bucket.current]).
 * - A bucket with no time is left out, never counted as 0. So is the current one before it has run a
 *   day, and one that is over but under an hour long: Android ends a bucket at its last event.
 * - These are Android's own totals, rough at the edges. `usage` works days out from events instead.
 */
class UsageLevels(context: Context) {
    private val usage = context.getSystemService(UsageStatsManager::class.java)

    /** A level, its Android interval, and how far back to ask for it: a little more than Android keeps. */
    enum class Level(val interval: Int, val lookBackDays: Long) {
        DAYS(UsageStatsManager.INTERVAL_DAILY, 14),
        WEEKS(UsageStatsManager.INTERVAL_WEEKLY, 7 * 6),
        MONTHS(UsageStatsManager.INTERVAL_MONTHLY, 31 * 8),
        YEARS(UsageStatsManager.INTERVAL_YEARLY, 366 * 4),
    }

    /** The buckets of [level], the oldest first, each with its apps' time and its unlocks. */
    fun read(level: Level): List<Bucket> {
        val now = System.currentTimeMillis()
        val from = now - level.lookBackDays * DAY_MS
        val stats =
            runCatching { usage.queryUsageStats(level.interval, from, now) }.getOrNull().orEmpty()
                .map {
                    Stat(
                        it.packageName,
                        it.firstTimeStamp,
                        it.lastTimeStamp,
                        it.totalTimeInForeground,
                        it.lastTimeUsed
                    )
                }
        // Unlocks: the same count as `unlocks`, Android's "keyguard hidden", per bucket.
        val unlocks =
            runCatching { usage.queryEventStats(level.interval, from, now) }.getOrNull().orEmpty()
                .filter { it.eventType == UsageEvents.Event.KEYGUARD_HIDDEN }
                .associate { it.firstTimeStamp to it.count }
        return buckets(stats, unlocks)
    }

    /**
     * One look at Android's levels: each level is read once, however often it is asked for. A year is
     * filled from the months inside it, and a month from its weeks ([fill]): Android deletes a
     * removed app's history from some levels only.
     */
    inner class Reading {
        private val seen = HashMap<Level, List<Bucket>>()

        fun buckets(level: Level): List<Bucket> = seen.getOrPut(level) {
            val own = this@UsageLevels.read(level)
            when (level) {
                Level.YEARS -> fill(own, buckets(Level.MONTHS))
                Level.MONTHS -> fill(own, buckets(Level.WEEKS))
                else -> own
            }
        }
    }

    companion object {
        const val DAY_MS = 86_400_000L
        const val HOUR_MS = 3_600_000L

        /** The current bucket counts in averages once it has run this long: a few days say little. */
        const val COUNTS_AFTER_MS = 7 * DAY_MS

        /** Years: an app left behind had this much a day, and now has a third of it or less. */
        const val LEFT_BEHIND_MS = 30 * 60_000L

        /** Years: a new app has this much a day now, and had no time the year before. */
        const val NEW_MS = 10 * 60_000L

        /** One app's time in front in one of Android's buckets, and when it was last used (0: not known). */
        data class Stat(
            val pkg: String,
            val start: Long,
            val end: Long,
            val ms: Long,
            val lastUsed: Long = 0
        )

        /**
         * One of Android's buckets: its real [start] and [end] (for the current one, the end of its data
         * so far), each app's time in front, the unlocks (null when Android has no count), and when
         * each app was last used, as far as this bucket knows.
         */
        data class Bucket(
            val start: Long,
            val end: Long,
            val apps: Map<String, Long>,
            val unlocks: Int?,
            val current: Boolean,
            val lastUsed: Map<String, Long> = emptyMap(),
        ) {
            val total: Long get() = apps.values.sum()

            /** All apps' time, or with [pkg] that app's. */
            fun total(pkg: String?): Long = if (pkg == null) total else apps[pkg] ?: 0
            val span: Long get() = end - start

            /** Its time in front a day, over its whole span. */
            val perDay: Long get() = if (span <= 0) 0 else total * DAY_MS / span

            /** One app's time a day in it. */
            fun perDay(pkg: String): Long = if (span <= 0) 0 else (apps[pkg] ?: 0) * DAY_MS / span
        }

        /**
         * [stats] as buckets, the oldest first, every app in them (Kolahal too, as in `usage`). The
         * newest start is the current bucket. Buckets with no time are left out, and so are the current
         * one under a day long and one that is over but under an hour long.
         */
        fun buckets(stats: List<Stat>, unlocks: Map<Long, Int>): List<Bucket> {
            val newest = stats.maxOfOrNull { it.start } ?: return emptyList()
            return stats.groupBy { it.start }.map { (start, list) ->
                val apps = list.filter { it.ms > 0 }.groupBy { it.pkg }
                    .mapValues { (_, s) -> s.sumOf { it.ms } }
                val lastUsed = list.filter { it.lastUsed > 0 }.groupBy { it.pkg }
                    .mapValues { (_, s) -> s.maxOf { it.lastUsed } }
                Bucket(
                    start,
                    list.maxOf { it.end },
                    apps,
                    unlocks[start],
                    current = start == newest,
                    lastUsed = lastUsed
                )
            }.filter { it.total > 0 && it.span >= if (it.current) DAY_MS else HOUR_MS }
                .sortedBy { it.start }
        }

        /**
         * [outer] with each app's time at least what the [inner] buckets inside it hold. Android deletes a
         * removed or reinstalled app's history from some levels and not others: on one phone the year
         * lost 48 hours of a reinstalled browser that the months kept. An outer bucket can't hold less
         * than the buckets inside it, so each app gets the larger of the two. An inner bucket counts
         * when it lies wholly inside, give or take an hour: the current ones end together, at now.
         */
        fun fill(outer: List<Bucket>, inner: List<Bucket>): List<Bucket> = outer.map { o ->
            val inside = inner.filter { it.start >= o.start && it.end <= o.end + HOUR_MS }
            if (inside.isEmpty()) return@map o
            val sums = inside.flatMap { it.apps.entries }.groupBy({ it.key }, { it.value })
                .mapValues { it.value.sum() }
            val used = inside.flatMap { it.lastUsed.entries }.groupBy({ it.key }, { it.value })
                .mapValues { it.value.max() }
            o.copy(
                apps = (o.apps.keys + sums.keys).associateWith {
                    maxOf(
                        o.apps[it] ?: 0,
                        sums[it] ?: 0
                    )
                },
                lastUsed = (o.lastUsed.keys + used.keys).associateWith {
                    maxOf(
                        o.lastUsed[it] ?: 0,
                        used[it] ?: 0
                    )
                },
            )
        }

        /** The buckets an average counts: those that are over, and the current one once it has run a week. */
        fun counted(buckets: List<Bucket>): List<Bucket> =
            buckets.filter { !it.current || it.span >= COUNTS_AFTER_MS }

        /** The time a day over [buckets] together (all apps', or with [pkg] that app's): their time over their spans. Null with none. */
        fun perDay(buckets: List<Bucket>, pkg: String? = null): Long? {
            val span = buckets.sumOf { it.span }
            return if (span <= 0) null else buckets.sumOf { it.total(pkg) } * DAY_MS / span
        }

        /** When [pkg] was last used, as far as [buckets] know; null when none says. */
        fun lastUsed(buckets: List<Bucket>, pkg: String): Long? =
            buckets.mapNotNull { it.lastUsed[pkg] }.maxOrNull()

        /** Unlocks a day over the [buckets] that have a count. Null with none. */
        fun unlocksPerDay(buckets: List<Bucket>): Int? {
            val with = buckets.filter { it.unlocks != null }
            val span = with.sumOf { it.span }
            return if (span <= 0) null else (with.sumOf { it.unlocks!!.toLong() } * DAY_MS.toDouble() / span).roundToInt()
        }

        /**
         * The newest bucket that is over, against those before it: the change of its time a day, in
         * percent: all apps', or with [pkg] that app's. Null with fewer than 2 buckets that are over,
         * or no time before.
         */
        fun change(buckets: List<Bucket>, pkg: String? = null): Long? {
            val over = buckets.filter { !it.current }
            if (over.size < 2) return null
            val before = perDay(over.dropLast(1), pkg) ?: return null
            if (before <= 0) return null
            val last = perDay(listOf(over.last()), pkg) ?: return null
            return ((last - before) * 100.0 / before).roundToLong()
        }

        /** The [n] apps with the most time over [buckets], biggest first. */
        fun top(buckets: List<Bucket>, n: Int): List<Pair<String, Long>> =
            buckets.flatMap { it.apps.entries }.groupBy({ it.key }, { it.value })
                .mapValues { it.value.sum() }
                .entries.sortedByDescending { it.value }.take(n).map { it.key to it.value }

        /** An app's time a day in two buckets. */
        data class Shift(val pkg: String, val before: Long, val now: Long)

        /** Apps big in [before] that are small in [now]: a third of their time a day or less. Biggest drop first. */
        fun leftBehind(before: Bucket, now: Bucket, n: Int = 3): List<Shift> =
            before.apps.keys.map { Shift(it, before.perDay(it), now.perDay(it)) }
                .filter { it.before >= LEFT_BEHIND_MS && it.now * 3 <= it.before }
                .sortedByDescending { it.before - it.now }.take(n)

        /** Apps with real time in [now] that had none in [before]. Biggest first. */
        fun newApps(before: Bucket, now: Bucket, n: Int = 3): List<Shift> =
            now.apps.keys.filter { (before.apps[it] ?: 0) == 0L }
                .map { Shift(it, 0, now.perDay(it)) }
                .filter { it.now >= NEW_MS }.sortedByDescending { it.now }.take(n)
    }
}
