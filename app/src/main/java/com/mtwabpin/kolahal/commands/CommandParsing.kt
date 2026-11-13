package com.mtwabpin.kolahal.commands

/** Pure helpers for reading and printing command arguments. */
object CommandParsing {
    /**
     * Splits [s] into words. Put "…" or '…' around a word that contains spaces.
     * A quote opens only at the start of a word, so an apostrophe inside a word (`I'm`, `Domino's`) is just a letter.
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
     * Returns [line] with every kind of space (tab, no-break space, line break) replaced by a plain space.
     * This way the command name splits off the same way everywhere: when it runs, and when we check
     * whether it is sensitive.
     */
    fun spaces(line: String): String = if (line.none { it.isWhitespace() && it != ' ' }) line
        else buildString(line.length) { line.forEach { append(if (it.isWhitespace()) ' ' else it) } }

    /** The longest timer or feed interval: one week. Backup import allows the same. */
    const val MAX_INTERVAL_MIN = 7L * 24 * 60

    /** Parses "30m", "2h", "1d" or "45" (minutes), in any case. "off", "0" and "manual" give 0 (off). */
    fun parseInterval(s: String?): Int? {
        val w = s?.lowercase() ?: return null
        if (w == "off" || w == "0" || w == "manual") return 0
        val m = Regex("^(\\d+)([mhd]?)$").find(w) ?: return null
        // Check the limit before multiplying. `1500000d` must be rejected. It must not overflow to a
        // negative number ("timer off"), or to a huge number that wraps round to a few hours.
        val n = m.groupValues[1].toLongOrNull()?.takeIf { it <= MAX_INTERVAL_MIN } ?: return null
        val minutes = when (m.groupValues[2]) {
            "h" -> n * 60
            "d" -> n * 60 * 24
            else -> n
        }
        return minutes.takeIf { it <= MAX_INTERVAL_MIN }?.toInt()
    }

    /**
     * Returns the daily times in [args], the words after `script at <name>`.
     * Times can be separated by commas, spaces or both, as on an `@at` line.
     * So "8:00,18:30", "8:00, 18:30" and "8:00 18:30" each give two times.
     */
    fun clockWords(args: List<String>): List<String> =
        args.flatMap { it.split(',', ' ') }.filter { it.isNotBlank() }

    /**
     * What the words of `script at` ([clockWords]) set:
     * - [Times]: the times, sorted and without duplicates. The list is empty for `off`.
     * - [Bad]: the first word that is not a time.
     */
    sealed interface DailyTimes {
        data class Times(val times: List<String>) : DailyTimes
        data class Bad(val word: String) : DailyTimes
    }

    /** Reads [words] as `script at` does. Returns null when there are no words: that means "show usage", not `off`. */
    fun dailyTimes(words: List<String>): DailyTimes? {
        if (words.isEmpty()) return null
        if (words[0] == "off") return DailyTimes.Times(emptyList())
        val times = words.map { com.mtwabpin.kolahal.script.ScriptScheduler.normalizeTime(it) ?: return DailyTimes.Bad(it) }
        return DailyTimes.Times(times.distinct().sorted())
    }

    /**
     * Whether [typed] could be a typo of [name]: at most one letter off for a name of four letters
     * or fewer, two for a longer one.
     */
    fun isNear(typed: String, name: String): Boolean = editDistance(typed, name) <= (if (name.length <= 4) 1 else 2)

    /**
     * The name in [names] that [typed] is most likely a typo of ([isNear]), for "did you mean".
     * The closest wins; on a tie, the first in [names]. One-letter names are left out. Null when
     * none is near.
     */
    fun nearest(typed: String, names: Collection<String>): String? =
        names.filter { it.length > 1 && isNear(typed, it) }.minByOrNull { editDistance(typed, it) }

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
