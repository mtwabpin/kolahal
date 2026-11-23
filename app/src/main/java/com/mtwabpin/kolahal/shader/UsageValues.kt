/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import com.mtwabpin.kolahal.usage.AppSessions
import java.util.Locale

/**
 * Kolahal's values from the phone's use, which shaders' parameters can follow ([ShaderValues]) as they follow
 * `battery`: no script gives them. [UsageWatch] works them out; this is what they are, and the arithmetic. Pure.
 * - `unlocks`: today's unlocks. `unlocks.usual`: today so far against the average full day of the six days
 *   before ([usual]). `unlocks.d1` … `unlocks.d7`: each of the last seven days, `d7` today, as a part of the
 *   week's most ([parts]).
 * - `screen`, `screen.usual`, `screen.d1` … `screen.d7`: the same for screen time, in minutes. `screen.h1` …
 *   `screen.h8`: the part of each three hours of today that the screen was on, `h1` from midnight ([blocks]).
 * - `away`: how many minutes the phone was locked before its last unlock ([away]).
 * - Screen time and `away` need usage access; the unlocks are counted without it while Kolahal runs.
 */
object UsageValues {
    const val UNLOCKS = "unlocks"
    const val SCREEN = "screen"
    const val AWAY = "away"

    /**
     * The first word of those that have a second. Both are commands, so no script has either name ([ShaderValues.load]
     * drops what one gave all the same), and a script's value, named `<script>.<name>`, is never one of these.
     */
    val GROUPS: Set<String> = setOf(UNLOCKS, SCREEN)

    /** The days the `d` values cover, today the last. */
    const val DAYS = 7

    /** The blocks of three hours the `h` values cover. */
    const val BLOCKS = 8

    private const val BLOCK_MS = 3 * 3_600_000L
    private const val MINUTE_MS = 60_000.0

    /** The most that a `usual` value is: twice a usual day, or more. */
    const val MOST_USUAL = 2f

    /** The values that the Follow menu lists, and what they are called. */
    val LISTED: Map<String, String> = linkedMapOf(
        UNLOCKS to "Unlocks today",
        "$UNLOCKS.usual" to "Unlocks against a usual day",
        SCREEN to "Screen time today",
        "$SCREEN.usual" to "Screen time against a usual day",
        AWAY to "Time away before the last unlock",
    )

    /** A row of values, one for each day or block: the ids are [prefix] and 1 to [count]. */
    class Series(val prefix: String, val count: Int, val label: String) {
        val ids: List<String> get() = (1..count).map { "$prefix$it" }
    }

    val UNLOCK_DAYS = Series("$UNLOCKS.d", DAYS, "Unlocks on each of the last 7 days, d7 today")
    val SCREEN_DAYS = Series("$SCREEN.d", DAYS, "Screen time on each of the last 7 days, d7 today")
    val SCREEN_BLOCKS = Series("$SCREEN.h", BLOCKS, "Screen time in each 3 hours of today, h1 from midnight")

    /** Every id, in the order that `shader values` lists them. */
    val IDS: List<String> = listOf(UNLOCKS, "$UNLOCKS.usual") + UNLOCK_DAYS.ids +
            listOf(SCREEN, "$SCREEN.usual") + SCREEN_DAYS.ids + SCREEN_BLOCKS.ids + AWAY

    private val ALL = IDS.toHashSet()

    /** Whether [id] is one of these. */
    fun has(id: String): Boolean = id in ALL

    /** The row that [id] is in, or null for one of [LISTED] or another id. */
    fun series(id: String): Series? = listOf(UNLOCK_DAYS, SCREEN_DAYS, SCREEN_BLOCKS).firstOrNull { s ->
        id.startsWith(s.prefix) && id.removePrefix(s.prefix).toIntOrNull()?.let { it in 1..s.count } == true
    }

    /** What [id] is called; null for an id that isn't one of these. */
    fun label(id: String): String? {
        LISTED[id]?.let { return it }
        val s = series(id) ?: return null
        val n = id.removePrefix(s.prefix).toInt()
        return when (s) {
            SCREEN_BLOCKS -> if (n == BLOCKS) "Screen time from 21:00 to midnight"
            else String.format(Locale.ROOT, "Screen time from %02d:00 to %02d:00", (n - 1) * 3, n * 3)
            UNLOCK_DAYS -> "Unlocks, day $n of the last 7"
            else -> "Screen time, day $n of the last 7"
        }
    }

    /** Whether any of [ids] needs the unlocks counted. */
    fun needsUnlocks(ids: Collection<String>): Boolean = ids.any { it == UNLOCKS || it.startsWith("$UNLOCKS.") }

    /** Whether any of [ids] needs screen time. */
    fun needsScreen(ids: Collection<String>): Boolean = ids.any { it == SCREEN || it.startsWith("$SCREEN.") }

    /**
     * [today] so far against the average of the full days [before] it: 0 for none, 1 for as much as a usual day,
     * [MOST_USUAL] at most (also for some on a day after days of none). Null with no day before.
     */
    fun usual(today: Double, before: List<Double>): Float? {
        if (before.isEmpty()) return null
        val average = before.average()
        return when {
            today <= 0.0 -> 0f
            average <= 0.0 -> MOST_USUAL
            else -> (today / average).toFloat().coerceAtMost(MOST_USUAL)
        }
    }

    /** Each of [days] as a part of the most of them, from 0 to 1: all 0 when the most is 0. */
    fun parts(days: List<Double>): List<Float> {
        val most = days.maxOrNull() ?: 0.0
        return days.map { if (most <= 0.0) 0f else (it / most).toFloat().coerceIn(0f, 1f) }
    }

    /**
     * The part of each three hours of a day that the screen was on, from 0 to 1: [on] are the times it was (start
     * and end, in ms), [dayStart] the day's midnight and [dayEnd] the next. The last block ends at [dayEnd], so on a
     * day the clock changes it has two or four hours. A block the day hasn't reached yet is 0.
     */
    fun blocks(on: List<Pair<Long, Long>>, dayStart: Long, dayEnd: Long): List<Float> = (0 until BLOCKS).map { i ->
        val from = dayStart + i * BLOCK_MS
        val until = if (i == BLOCKS - 1) dayEnd else minOf(dayEnd, from + BLOCK_MS)
        if (until <= from) 0f
        else {
            val ms = on.sumOf { (start, end) -> (minOf(end, until) - maxOf(start, from)).coerceAtLeast(0L) }
            (ms.toDouble() / (until - from)).toFloat().coerceIn(0f, 1f)
        }
    }

    /**
     * How many minutes the phone was locked before its last unlock, from [events] in time order (only
     * [AppSessions.LOCKED] and [AppSessions.UNLOCKED] count): from the first lock after the unlock before it.
     * Null without an unlock, or without a lock before it.
     */
    fun away(events: List<AppSessions.Companion.Event>): Float? {
        val last = events.indexOfLast { it.type == AppSessions.UNLOCKED }
        if (last < 0) return null
        val before = events.subList(0, last)
        val previous = before.indexOfLast { it.type == AppSessions.UNLOCKED }
        val lock = before.drop(previous + 1).firstOrNull { it.type == AppSessions.LOCKED } ?: return null
        return ((events[last].time - lock.time) / MINUTE_MS).toFloat().coerceAtLeast(0f)
    }

    /**
     * The unlocks' values from each day's count, oldest first and today last, from the first day that has
     * one ([com.mtwabpin.kolahal.unlock.UnlockCounter.history]). None without today.
     */
    fun unlocks(days: List<Int>): Map<String, String> =
        if (days.isEmpty()) emptyMap() else of(UNLOCKS, UNLOCK_DAYS, days.map { it.toDouble() })

    /**
     * Screen time's values from each day's time in ms, oldest first and today last, the days with data only
     * ([com.mtwabpin.kolahal.usage.UsageHistory]), and today's [blocks]. None without today.
     */
    fun screen(days: List<Long>, blocks: List<Float>): Map<String, String> {
        if (days.isEmpty()) return emptyMap()
        return of(SCREEN, SCREEN_DAYS, days.map { it / MINUTE_MS }) +
                SCREEN_BLOCKS.ids.zip(blocks) { id, part -> id to number(part.toDouble()) }
    }

    /** [name], its `usual` and its days, from each day's amount, oldest first and today last. A day before the first is 0. */
    private fun of(name: String, days: Series, amounts: List<Double>): Map<String, String> = buildMap {
        val today = amounts.last()
        put(name, number(today))
        usual(today, amounts.dropLast(1).takeLast(DAYS - 1))?.let { put("$name.usual", number(it.toDouble())) }
        val week = List((DAYS - amounts.size).coerceAtLeast(0)) { 0.0 } + amounts.takeLast(DAYS)
        days.ids.zip(parts(week)) { id, part -> put(id, number(part.toDouble())) }
    }

    /** [x] as a value is kept: two decimals at most, and none that are 0. `37`, `0.42`. */
    fun number(x: Double): String = String.format(Locale.ROOT, "%.2f", x).trimEnd('0').trimEnd('.').let { if (it == "-0") "0" else it }
}
