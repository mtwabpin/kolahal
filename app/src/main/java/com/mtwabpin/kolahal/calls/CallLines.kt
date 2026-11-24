/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.calls

/**
 * Which rows of Android's call log get a line in Kolahal's log: one line for each call, however many
 * rows the phone writes for it.
 *
 * A phone app may write a call more than once: a row when the call starts and another, with a new
 * id, when it ends or while it goes on. Going by the row's id alone, each of those was a new line,
 * so one call showed several times while it was going on. A call is told by when it began and with
 * whom ([key]); a later row for a call that has its line changes that line.
 *
 * Pure: the store and the log are [CallEvents]'s.
 */
internal object CallLines {
    /** How many of the newest calls keep their place, so a later row can find its line. */
    const val KEPT = 30

    /**
     * What tells one call from another: when it began and the number, as a hash. The number itself
     * isn't kept, only whether it is the same one.
     */
    fun key(c: CallEvents.Call): String = "${c.date}|${c.number.hashCode()}"

    /**
     * What to do with new [rows] of the call log, given the calls that have a line ([logged], by [key]).
     * - [fresh]: calls with no line yet, oldest first, each as its newest row.
     * - [again]: calls that have a line, as their newest row: the line takes what that row says.
     * - [last]: the highest row id among [rows], whatever order they came in. The next look starts
     *   after it, so no row is read twice.
     */
    class Plan(val fresh: List<CallEvents.Call>, val again: List<CallEvents.Call>, val last: Long)

    fun plan(rows: List<CallEvents.Call>, logged: Set<String>): Plan {
        // The newest row of a call says the most: how long it was, how it ended.
        val newest = rows.groupBy(::key).values.map { same -> same.maxBy { it.id } }.sortedBy { it.id }
        val (again, fresh) = newest.partition { key(it) in logged }
        return Plan(fresh, again, rows.maxOf { it.id })
    }

    /** The calls that have a line, as the store keeps them: `key<tab>line id`, one to a line, oldest first. */
    fun decode(kept: String?): LinkedHashMap<String, Long> {
        val out = LinkedHashMap<String, Long>()
        kept.orEmpty().lineSequence().forEach { row ->
            val id = row.substringAfter('\t', "").toLongOrNull() ?: return@forEach
            out[row.substringBefore('\t')] = id
        }
        return out
    }

    /** [lines] for the store, the newest [KEPT] of them. */
    fun encode(lines: Map<String, Long>): String =
        lines.entries.toList().takeLast(KEPT).joinToString("\n") { "${it.key}\t${it.value}" }
}
