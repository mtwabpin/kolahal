/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.shaders

import com.mtwabpin.kolahal.shader.ShaderValues
import com.mtwabpin.kolahal.shader.UsageValues
import java.util.Locale

/**
 * Parameters that follow a row of values, such as `screen.h1` … `screen.h8`, which a shader's details show as one
 * row ([ShaderParamRows]). Pure (ShaderSeriesTest).
 */
internal object ShaderSeries {
    /** The fewest parameters that make a row of their own. */
    const val LEAST = 3

    /** Parameters [first] to [last] of a shader, in its order, which follow [ids]. */
    data class Run(val first: Int, val last: Int, val ids: List<String>) {
        val indices: IntRange get() = first..last
    }

    /** A value's name with a number at its end: what is before the number, and the number. */
    private val NUMBERED = Regex("(.*\\D)(\\d{1,6})")

    private fun numbered(id: String?): Pair<String, Int>? =
        id?.let { NUMBERED.matchEntire(it) }?.let { it.groupValues[1] to it.groupValues[2].toInt() }

    /**
     * The runs in [follows], what each parameter of a shader follows as its header says (null: nothing): next to
     * each other, [LEAST] or more, the same name with the numbers counting up by one at its end.
     * `screen.h1`, `screen.h2`, `screen.h3` are one; `d1`, `d3`, `d4` aren't.
     */
    fun runs(follows: List<String?>): List<Run> {
        val out = ArrayList<Run>()
        var i = 0
        while (i < follows.size) {
            val start = numbered(follows[i])
            var end = i + 1
            if (start != null) {
                while (end < follows.size && numbered(follows[end]) == (start.first to start.second + (end - i))) end++
            }
            if (start != null && end - i >= LEAST) {
                out += Run(i, end - 1, follows.subList(i, end).map { it.orEmpty() })
                i = end
            } else i++
        }
        return out
    }

    /** What the values of a run are, in a few words: "Each of the last 7 days", "Today, in blocks of 3 hours". */
    fun title(ids: List<String>): String {
        val series = usage(ids)
        val first = ids.first().removePrefix(series?.prefix.orEmpty()).toIntOrNull()
        val last = ids.last().removePrefix(series?.prefix.orEmpty()).toIntOrNull()
        if (series == null || first == null || last == null) return "${ShaderValues.label(ids.first())} to ${ShaderValues.label(ids.last())}"
        return when (series) {
            UsageValues.SCREEN_BLOCKS ->
                if (first == 1 && last == UsageValues.BLOCKS) "Today, in blocks of 3 hours"
                else "Today from ${hour((first - 1) * 3)} to ${hour(last * 3)}, in blocks of 3 hours"
            else -> when {
                first == 1 && last == UsageValues.DAYS -> "Each of the last ${UsageValues.DAYS} days"
                last == UsageValues.DAYS -> "Each of the last ${ids.size} days"
                else -> "Days $first to $last of the last ${UsageValues.DAYS}"
            }
        }
    }

    /** What a run follows: "Follows your screen time", "Follows values from weather". */
    fun follows(ids: List<String>): String = when (usage(ids)) {
        UsageValues.SCREEN_BLOCKS, UsageValues.SCREEN_DAYS -> "Follows your screen time"
        UsageValues.UNLOCK_DAYS -> "Follows your unlocks"
        else -> if ('.' in ids.first()) "Follows values from ${ids.first().substringBefore('.')}" else "Follows ${ids.size} values"
    }

    /** The row of [UsageValues] that all of [ids] are in, or null. */
    private fun usage(ids: List<String>): UsageValues.Series? =
        UsageValues.series(ids.first())?.takeIf { s -> ids.all { UsageValues.series(it) == s } }

    /** An hour as the values' own words have it: `06:00`, and `midnight` at the end of a day. */
    private fun hour(h: Int): String = if (h >= 24) "midnight" else String.format(Locale.ROOT, "%02d:00", h)
}
