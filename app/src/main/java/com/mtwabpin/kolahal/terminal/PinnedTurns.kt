package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.StatusLine

/**
 * The pinned header's rotation and refresh logic. The header (ui/PinnedHeader) takes turns with it,
 * and the view model runs the lines that are [due].
 */
internal object PinnedTurns {
    /** Whether the dots show for [count] items, with `header.pinned-dots` set to [upTo]. One item needs no dots. */
    fun showsDots(count: Int, upTo: Int) = count in 2..upTo

    /** The index to show for [current]: its own index, or the first one if it is gone. */
    fun index(keys: List<String>, current: String?) = keys.indexOf(current).coerceAtLeast(0)

    /** The key after [current]. After the last one, it wraps to the first. */
    fun next(keys: List<String>, current: String?): String? =
        if (keys.isEmpty()) null else keys[(index(keys, current) + 1) % keys.size]

    /**
     * Returns the names of the lines to run again at [now] (ms). A line is due if it asks for a
     * refresh and either has not run yet, or its last run ([lastRun], by name, same clock) was at
     * least that many seconds ago.
     */
    fun due(lines: List<StatusLine>, lastRun: Map<String, Long>, now: Long): List<String> = lines.mapNotNull { p ->
        val every = p.refresh ?: return@mapNotNull null
        val last = lastRun[p.name]
        p.name.takeIf { last == null || now - last >= every * 1000L }
    }
}
