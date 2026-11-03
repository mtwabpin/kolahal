package com.mtwabpin.kolahal.terminal

/** Pure helpers for reading and printing command arguments. */
object CommandParsing {
    /**
     * Words, with "…" or '…' around one holding spaces. A quote opens only at the start of a word,
     * so an apostrophe inside one (`I'm`, `Domino's`) is just a letter.
     */
    fun tokenize(s: String): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quote: Char? = null
        for (ch in s) {
            when {
                quote != null && ch == quote -> quote = null
                quote != null -> cur.append(ch)
                (ch == '"' || ch == '\'') && cur.isEmpty() -> quote = ch
                ch.isWhitespace() -> if (cur.isNotEmpty()) { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
        }
        if (cur.isNotEmpty()) out += cur.toString()
        return out
    }

    /**
     * [line] with every kind of space (tab, no-break space, a line break) as a plain one, so the
     * command name splits off the same way everywhere: running it, and checking whether it is sensitive.
     */
    fun spaces(line: String): String = if (line.none { it.isWhitespace() && it != ' ' }) line
        else buildString(line.length) { line.forEach { append(if (it.isWhitespace()) ' ' else it) } }

    /** Longest timer or feed interval: a week, like backup import allows. */
    const val MAX_INTERVAL_MIN = 7L * 24 * 60

    /** "30m", "2h", "1d", "45" (minutes), "off" -> minutes (0 = off). */
    fun parseInterval(s: String?): Int? {
        if (s == null) return null
        if (s == "off" || s == "0" || s == "manual") return 0
        val m = Regex("^(\\d+)([mhd]?)$").find(s.lowercase()) ?: return null
        // Capped before it's multiplied: `1500000d` must be rejected, not wrap to a negative
        // "timer off", nor a number so big it wraps round to a few hours.
        val n = m.groupValues[1].toLongOrNull()?.takeIf { it <= MAX_INTERVAL_MIN } ?: return null
        val minutes = when (m.groupValues[2]) {
            "h" -> n * 60
            "d" -> n * 60 * 24
            else -> n
        }
        return minutes.takeIf { it <= MAX_INTERVAL_MIN }?.toInt()
    }

    /**
     * The daily times in [args], the words after `script at <name>`: commas, spaces or both between
     * them, as on an `@at` line, so "8:00,18:30", "8:00, 18:30" and "8:00 18:30" each hold two.
     */
    fun clockWords(args: List<String>): List<String> =
        args.flatMap { it.split(',', ' ') }.filter { it.isNotBlank() }

    /** Levenshtein distance, for "did you mean". */
    fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    fun formatInterval(min: Int): String = when {
        min % (60 * 24) == 0 -> "${min / (60 * 24)}d"
        min % 60 == 0 -> "${min / 60}h"
        else -> "${min}m"
    }
}
