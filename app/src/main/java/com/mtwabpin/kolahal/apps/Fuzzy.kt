package com.mtwabpin.kolahal.apps

import java.text.Normalizer

/** The forgiving part of app search. It ignores accents and allows small typing mistakes. */
internal object Fuzzy {
    private val MARKS = Regex("\\p{Mn}+")

    /**
     * Lowercases [s] and removes accents, so "Café" and "cafe" match. Some letters are not a plain
     * letter plus an accent (ł, ø, ß, æ…). These are replaced by plain letters too: "Straße" becomes "strasse".
     */
    fun fold(s: String): String {
        val plain = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(MARKS, "")
        if (plain.none { it in LETTERS }) return plain
        return buildString(plain.length + 4) { for (c in plain) append(LETTERS[c] ?: c) }
    }

    /** Latin letters that have no accent to remove, mapped to their plain-letter spelling. */
    private val LETTERS = mapOf(
        'ł' to "l", 'ø' to "o", 'ß' to "ss", 'đ' to "d", 'ð' to "d", 'æ' to "ae", 'œ' to "oe", 'þ' to "th", 'ı' to "i", 'ħ' to "h",
    )

    /**
     * True when [q] is a typing mistake for the start of one of [names] (the label, then its words).
     * - A mistake is one letter wrong, missing or extra, or two letters swapped (`mpas` for Maps).
     * - From eight letters on, two mistakes are allowed.
     * - Below four letters, it is always false: almost anything would match something.
     */
    fun slip(q: String, names: List<String>): Boolean {
        if (q.length < 4) return false
        val max = if (q.length >= 8) 2 else 1
        return names.any { name ->
            // One table covers every length. Its last row holds the edit count to each start of the name.
            val longest = minOf(name.length, q.length + max)
            longest >= 1 && lastRow(q, name, longest).let { last -> (maxOf(1, q.length - max)..longest).any { n -> last[n] <= max } }
        }
    }

    /**
     * Number of edits from [a] to the first [bLength] letters of [b]. A swap of two neighbours counts
     * as one edit (optimal string alignment). Only for tests: [slip] reads every length from one table.
     */
    fun distance(a: String, b: String, bLength: Int = b.length): Int = lastRow(a, b, bLength)[bLength]

    /**
     * Returns the table's last row for [a] against the first [bLength] letters of [b]. Index n holds
     * the edits from [a] to the first n letters. It keeps only three rows in memory, because app
     * search checks every name when a key press matches nothing.
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
