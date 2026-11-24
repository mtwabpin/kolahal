/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import com.mtwabpin.kolahal.apps.ProfileKind

/**
 * How `apps` lists a profile's apps: chips in [COLUMNS] even columns, a few rows of them, and
 * "+N more" for the rest, so the command's own line stays on screen on a phone of usual size.
 * The lines that count them: a profile's divider, or one line when `apps` lists one profile.
 */
internal object AppListing {
    /** The columns of an apps grid on a phone. A wider screen fits more. */
    const val COLUMNS = 3

    /**
     * The rows of chips a profile's apps fill before "+N more", by how many profiles `apps` lists:
     * about nine in all, so the command's line stays on screen above them. One profile gets six,
     * two get four each, three get three, more get two.
     */
    fun rows(profiles: Int): Int = when {
        profiles <= 1 -> 6
        profiles == 2 -> 4
        profiles == 3 -> 3
        else -> 2
    }

    /**
     * The places in [apps] (in their order) of the apps a capped grid shows until it is opened:
     * one cell less than [cells], for "+N more". Null when all of them fit in [cells], so a grid
     * never ends in "+1 more".
     * - Pinned apps first, in the order they were pinned ([pinOrder]: an app's place among them,
     *   null when it isn't pinned);
     * - then the apps used most ([use]: the score the suggestions rank by);
     * - then the first of the rest, in their order.
     * They show in the order of [apps] (by name), the order the whole grid has when it is opened.
     */
    fun <T> firstShown(apps: List<T>, cells: Int, pinOrder: (T) -> Int?, use: (T) -> Double): List<Int>? {
        if (apps.size <= cells) return null
        val pins = IntArray(apps.size) { pinOrder(apps[it]) ?: Int.MAX_VALUE }
        val scores = DoubleArray(apps.size) { use(apps[it]) }
        return apps.indices
            .sortedWith(compareBy<Int> { pins[it] }.thenByDescending { scores[it] }.thenBy { it })
            .take((cells - 1).coerceAtLeast(0))
            .sorted()
    }

    /** A profile's divider above its apps, with how many it has: `── work · 18 ──`. */
    fun divider(kind: ProfileKind, count: Int): String = "── ${kind.label} · $count ──"

    /**
     * The line that counts the apps when `apps` lists one profile: `40 apps`, `18 work apps`,
     * `3 hidden apps`. [kind] is the profile asked for, null for none; [hidden]: only hidden apps.
     */
    fun count(count: Int, kind: ProfileKind?, hidden: Boolean): String =
        listOfNotNull(
            count.toString(),
            "hidden".takeIf { hidden },
            kind?.label,
            if (count == 1) "app" else "apps",
        ).joinToString(" ")
}
