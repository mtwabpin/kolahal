package com.mtwabpin.kolahal.script

/**
 * A line diff of two texts, as Compare shows a library script against your copy: the lines both
 * share, then a longest common run of lines between their common start and end, with removed lines
 * before added ones where a line changed. Grouped in hunks with a few unchanged lines around each
 * change.
 */
object LineDiff {
    enum class Kind { SAME, REMOVED, ADDED }

    data class Line(val kind: Kind, val text: String)

    /** [skipped] unchanged lines left out, then [lines]; a last hunk without lines counts the ones after the last change. */
    data class Hunk(val skipped: Int, val lines: List<Line>)

    /** Past this many cells (lines × lines between the common start and end), the middle is all removed, then all added. */
    private const val MAX_CELLS = 1_000_000

    /**
     * Every line of [old] and [new], each the same, only in [old] (removed) or only in [new] (added).
     * A text's last newline ends its last line rather than starting an empty one, an empty text has
     * no lines, and Windows line endings are line endings.
     */
    fun lines(old: String, new: String): List<Line> {
        fun split(text: String) = text.replace("\r\n", "\n").removeSuffix("\n").let { if (it.isEmpty()) emptyList() else it.lines() }
        val a = split(old)
        val b = split(new)
        var start = 0
        while (start < a.size && start < b.size && a[start] == b[start]) start++
        var endA = a.size
        var endB = b.size
        while (endA > start && endB > start && a[endA - 1] == b[endB - 1]) {
            endA--
            endB--
        }
        val out = ArrayList<Line>(a.size + b.size)
        for (i in 0 until start) out += Line(Kind.SAME, a[i])
        val n = endA - start
        val m = endB - start
        if (n.toLong() * m > MAX_CELLS) {
            for (i in start until endA) out += Line(Kind.REMOVED, a[i])
            for (j in start until endB) out += Line(Kind.ADDED, b[j])
        } else {
            // common[i][j]: the longest common run of the middle's lines from a's i and b's j on.
            val common = Array(n + 1) { IntArray(m + 1) }
            for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
                common[i][j] = if (a[start + i] == b[start + j]) common[i + 1][j + 1] + 1 else maxOf(common[i + 1][j], common[i][j + 1])
            }
            var i = 0
            var j = 0
            while (i < n || j < m) when {
                i < n && j < m && a[start + i] == b[start + j] -> { out += Line(Kind.SAME, a[start + i]); i++; j++ }
                i < n && (j == m || common[i + 1][j] >= common[i][j + 1]) -> { out += Line(Kind.REMOVED, a[start + i]); i++ }
                else -> { out += Line(Kind.ADDED, b[start + j]); j++ }
            }
        }
        for (i in endA until a.size) out += Line(Kind.SAME, a[i])
        return out
    }

    /** The changed [lines] with three unchanged ones around each; none when nothing changed. */
    fun hunks(lines: List<Line>): List<Hunk> {
        val context = 3
        val near = BooleanArray(lines.size)
        for ((i, line) in lines.withIndex()) {
            if (line.kind != Kind.SAME) for (k in maxOf(0, i - context)..minOf(lines.lastIndex, i + context)) near[k] = true
        }
        val hunks = ArrayList<Hunk>()
        var skipped = 0
        var current: MutableList<Line>? = null
        for ((i, line) in lines.withIndex()) {
            if (near[i]) {
                val hunk = current ?: mutableListOf<Line>().also { hunks += Hunk(skipped, it); skipped = 0 }
                hunk += line
                current = hunk
            } else {
                current = null
                skipped++
            }
        }
        if (skipped > 0 && hunks.isNotEmpty()) hunks += Hunk(skipped, emptyList())
        return hunks
    }
}
