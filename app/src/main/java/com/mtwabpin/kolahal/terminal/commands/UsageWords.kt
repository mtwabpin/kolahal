/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

/**
 * What `usage` reads in the words after it ([of]):
 * - nothing, or a number: screen time for each app over that many days, 1 to 30;
 * - `summary`, `full`, `week`, `month` or `year`: Android's weeks, months and years
 *   ([UsageSummaryCommands]); `week`, `month` and `year` also with an app after them;
 * - anything else: one app, by its name.
 *
 * A word is the word only when it stands alone, or (week, month, year) before an app. So an app whose
 * name starts like a word keeps its name: `usage weekly` is the app Weekly, and `usage summary pro`
 * the app Summary Pro. `usage week` alone is Android's weeks.
 */
object UsageWords {
    /** The words before an app's name, with a few words on what each shows, in the order suggestions offer them. */
    val WORDS: Map<String, String> = linkedMapOf(
        "summary" to "each level: how far back, and the time a day",
        "week" to "a row a week",
        "month" to "a row a month",
        "year" to "a row a year",
        "full" to "the summary and every level",
    )

    /** The words that take an app after them: `usage month youtube`. */
    val LEVELS: Set<String> = setOf("week", "month", "year")

    /** What `usage` was asked for. */
    sealed interface Asked {
        /** Screen time for each app over [days] days. */
        data class Days(val days: Int) : Asked

        /** One of [WORDS], in lower case; with [args] after a level, one app's. [args] are the words as typed. */
        data class Summary(val word: String, val args: List<String>) : Asked

        /** One app, named by [words] (flags such as `-w` among them). */
        data class App(val words: List<String>) : Asked
    }

    /** What [args], the words after `usage`, ask for. */
    fun of(args: List<String>): Asked {
        val first = args.firstOrNull() ?: return Asked.Days(1)
        first.toIntOrNull()?.let { return Asked.Days(it.coerceIn(1, 30)) }
        val word = first.lowercase()
        return when {
            word in WORDS && args.size == 1 -> Asked.Summary(word, args)
            word in LEVELS -> Asked.Summary(word, args)
            else -> Asked.App(args)
        }
    }
}
