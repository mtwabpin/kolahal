package com.mtwabpin.kolahal.apps

import java.text.Normalizer

/** The forgiving half of app search: accents that don't matter, and slips of the thumb. */
internal object Fuzzy {
    private val MARKS = Regex("\\p{Mn}+")

    /** "Café" and "cafe" read alike: lowercase, without accents. */
    fun fold(s: String): String = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")

    /**
     * Whether [q] is a slip for the start of one of [names] (the label, then its words): a letter
     * wrong, missing, extra, or two swapped (`mpas` for Maps), two of those from eight letters on.
     * Shorter than four letters, almost anything is a slip of something: never.
     */
    fun slip(q: String, names: List<String>): Boolean {
        if (q.length < 4) return false
        val max = if (q.length >= 8) 2 else 1
        return names.any { name ->
            // One table for every length: its last row holds the edits to each start of the name.
            val longest = minOf(name.length, q.length + max)
            longest >= 1 && lastRow(q, name, longest).let { last -> (maxOf(1, q.length - max)..longest).any { n -> last[n] <= max } }
        }
    }

    /**
     * Edits from [a] to the first [bLength] letters of [b], a swap of two neighbours counting as one
     * (optimal string alignment).
     */
    fun distance(a: String, b: String, bLength: Int = b.length): Int = lastRow(a, b, bLength)[bLength]

    /**
     * The table's last row for [a] against the first [bLength] letters of [b]: at n, the edits from [a]
     * to the first n letters. Three rows at a time: app search asks for a slip against every name on a
     * key that matches nothing.
     */
    private fun lastRow(a: String, b: String, bLength: Int): IntArray {
        var older = IntArray(bLength + 1)
        var prev = IntArray(bLength + 1) { it }
        var row = IntArray(bLength + 1)
        for (i in 1..a.length) {
            row[0] = i
            for (j in 1..bLength) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                row[j] = minOf(prev[j] + 1, row[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) row[j] = minOf(row[j], older[j - 2] + 1)
            }
            val spare = older
            older = prev
            prev = row
            row = spare
        }
        return prev
    }
}
