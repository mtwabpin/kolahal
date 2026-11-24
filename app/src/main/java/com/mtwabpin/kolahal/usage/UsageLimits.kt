/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.usage

import com.mtwabpin.kolahal.data.StyledText

/**
 * The daily limits for screen time and unlocks (`usage.screen-time-limit`, `usage.unlock-limit`), and
 * how a figure of today stands against one. Every place that shows today's screen time or unlock
 * count colors it by [level]: the header's unlocks, `usage`, `unlocks`, `top`, `fetch` and the
 * scripts' `usage.today`. Nothing else happens at a limit: no notification, nothing blocked.
 */
object UsageLimits {
    /** How a figure stands against its limit: under it (or no limit), at or over it, or far over it. */
    enum class Level { NONE, WARN, BAD }

    /**
     * [figure] against [limit]: [Level.WARN] at or over the limit, [Level.BAD] at one and a half
     * times it or more, [Level.NONE] under it. A limit of 0 or less is no limit.
     */
    fun level(figure: Long, limit: Long): Level = when {
        limit <= 0 -> Level.NONE
        figure * 2 >= limit * 3 -> Level.BAD
        figure >= limit -> Level.WARN
        else -> Level.NONE
    }

    /** Today's screen time [ms] against a limit of [limitMinutes], in whole minutes, as the figure shows them. */
    fun screenTime(ms: Long, limitMinutes: Int): Level = level(ms / 60_000, limitMinutes.toLong())

    /** Today's unlock [count] against [limit]. */
    fun unlocks(count: Int, limit: Int): Level = level(count.toLong(), limit.toLong())

    /** The theme's color for [level]: warn or bad, or null for a figure in its usual color. */
    fun role(level: Level): StyledText.ColorRole? = when (level) {
        Level.NONE -> null
        Level.WARN -> StyledText.ColorRole.WARN
        Level.BAD -> StyledText.ColorRole.BAD
    }

    /** [figure] in [level]'s color ([role]), or as it is under the limit. */
    fun colored(figure: String, level: Level): String =
        role(level)?.let { StyledText.color(it, figure) } ?: figure

    /**
     * Today's screen time against its limit in words, from a small letter: "30m over your limit of
     * 3h", "at your limit of 3h". Null under the limit or with none.
     */
    fun screenTimeWords(ms: Long, limitMinutes: Int): String? {
        if (screenTime(ms, limitMinutes) == Level.NONE) return null
        val over = ms / 60_000 - limitMinutes
        val limit = minutes(limitMinutes.toLong())
        return if (over == 0L) "at your limit of $limit" else "${minutes(over)} over your limit of $limit"
    }

    /** Today's unlocks against their limit in words, from a small letter: "11 over your limit of 30", "at your limit of 30". Null under it or with none. */
    fun unlockWords(count: Int, limit: Int): String? {
        if (unlocks(count, limit) == Level.NONE) return null
        val over = count - limit
        return if (over == 0) "at your limit of $limit" else "$over over your limit of $limit"
    }

    /** A limit or an amount over it, as people say it: "3h", "2h 30m", "45m". */
    fun minutes(minutes: Long): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h == 0L -> "${m}m"
            m == 0L -> "${h}h"
            else -> "${h}h ${m}m"
        }
    }
}
