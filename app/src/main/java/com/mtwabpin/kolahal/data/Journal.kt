package com.mtwabpin.kolahal.data

/**
 * The latest changes of one kind to the log's lines, by line id: lines removed, or lines changed in
 * place ([LogRepository] keeps one of each). Every change gets a number, and the numbers only grow.
 * Home remembers the number it last applied ([seq]) and asks for what came after it ([since]). So
 * it changes a few lines of its page instead of reading the page again.
 *
 * Only the last [kept] changes are remembered. Asked about an older number, [since] returns null,
 * and the page must be read again.
 */
internal class Journal(private val kept: Int) {
    /** Each change's number with its line's id, oldest first. */
    private val entries = ArrayDeque<Pair<Long, Long>>()

    /** The number of the latest change, or 0 before any. */
    @Volatile var seq = 0L
        private set

    /** Notes a change to each of the lines [ids], in order. */
    @Synchronized fun add(ids: Collection<Long>) {
        for (id in ids) {
            entries.addLast(++seq to id)
            if (entries.size > kept) entries.removeFirst()
        }
    }

    /** The ids changed after change number [since], or null when that is too long ago to know. */
    @Synchronized fun since(since: Long): Set<Long>? {
        if (since < seq - entries.size) return null
        return entries.filter { (number, _) -> number > since }.mapTo(HashSet()) { (_, id) -> id }
    }
}
