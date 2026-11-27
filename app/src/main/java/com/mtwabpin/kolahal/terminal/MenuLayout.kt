/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.R

/**
 * A sheet's actions as the GUI sheet (ActionSheet) lays them out, each in
 * exactly one place:
 * - [row]: the row of icons, [ActionPlace.SLOTS] long, null where a slot is empty; null when no
 *   action goes in the row.
 * - [groups]: each group's heading and chips, in the order their first action comes in the list.
 *   A group's destructive actions come last in it, in red.
 * - [words]: the actions without a place, as a list of words (quick actions, the editor's menu), or
 *   one whose slot an earlier action already took.
 */
class MenuLayout(
    val row: List<LogAction?>?,
    val groups: List<Pair<ActionPlace.Group, List<LogAction>>>,
    val words: List<LogAction>,
) {
    /** Every action, in the order the sheet shows them. */
    val all: List<LogAction> get() = row.orEmpty().filterNotNull() + groups.flatMap { it.second } + words

    companion object {
        fun of(actions: List<LogAction>): MenuLayout {
            val slots = arrayOfNulls<LogAction>(ActionPlace.SLOTS)
            val groups = LinkedHashMap<String, Pair<ActionPlace.Group, MutableList<LogAction>>>()
            val words = ArrayList<LogAction>()
            for (a in actions) when (val p = a.place) {
                is ActionPlace.Row ->
                    if (p.slot in slots.indices && slots[p.slot] == null) slots[p.slot] = a else words += a

                is ActionPlace.Group -> groups.getOrPut(p.key) { p to ArrayList() }.second += a
                null -> words += a
            }
            return MenuLayout(
                row = slots.toList().takeIf { r -> r.any { it != null } },
                groups = groups.values.map { (g, list) -> g to list.sortedBy { it.destructive } },
                words = words,
            )
        }

        /** The full words of "Fresh from here", which hides every line above the line. */
        const val FRESH = "Fresh from here"

        /**
         * [a] with its short words, icon and place when it is one of the actions every line has but
         * was added as plain words ([FRESH]): it goes in the row, where Copy output was before it
         * joined its command's group. Any other action is returned as it is.
         */
        fun placeKnown(a: LogAction): LogAction = when {
            a.place != null -> a
            a.label == FRESH -> a.copy(
                short = "Fresh",
                icon = a.icon ?: ActionIcon.Drawable(R.drawable.ic_clear_all),
                place = ActionPlace.FRESH,
            )

            else -> a
        }

        /**
         * A link's address, short enough for a heading: without `https://`, `www.` and a last `/`,
         * and cut to [max] characters with "…".
         */
        fun shortAddress(url: String, max: Int = 36): String {
            val bare = url.trim().replaceFirst(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://"), "")
                .removePrefix("www.").removeSuffix("/")
            return if (bare.length > max) bare.take(max - 1) + "…" else bare
        }
    }
}
