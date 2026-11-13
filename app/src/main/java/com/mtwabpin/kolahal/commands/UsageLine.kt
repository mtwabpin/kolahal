package com.mtwabpin.kolahal.commands

/**
 * Reads a command's usage line, the one that `help` shows (`feed [add <url>|rm] [id]`).
 * It is used for:
 * - completing options;
 * - running whole lines instantly (`torch on`).
 */
object UsageLine {
    /** A literal word in a usage line that can be completed: `work`, `ls`, `-l`, `--vs`. Not `<app>`, `0-100` or `30s`. */
    val OPTION = Regex("-{0,2}[a-z][a-z-]*")

    /**
     * One way to fill an argument place.
     * - [word]: the literal word. It is null for a value such as `<app>` or `0-100`.
     * - [takesValue]: true when a value follows the word (`add <url>`).
     */
    data class Choice(val word: String?, val takesValue: Boolean)

    /** Splits a usage line on spaces outside brackets: `feed [add <url>|rm] [id]` → `feed`, `[add <url>|rm]`, `[id]`. */
    fun tokens(usage: String): List<String> = split(usage, ' ').filter { it.isNotBlank() }

    /** Returns the ways to fill the argument place [token]. `[add <url>|rm|<id>]` → `add` with a value, `rm`, a value. */
    fun choices(token: String): List<Choice> =
        split(token.removeSurrounding("[", "]"), '|').map { alt ->
            val word = alt.trim().substringBefore(' ')
            if (word.matches(OPTION)) Choice(word, alt.trim().contains(' ')) else Choice(null, true)
        }

    /**
     * Returns true when [line] fills every argument place of [usage] and nothing is left to choose.
     * For each word of [line]:
     * - the word is an option of its place, and that option takes no value;
     * - no other option of that place starts with the word;
     * - a value could not go in that place instead.
     * Also, the usage line must have no more places after the last word.
     *
     * True: `torch on` for `torch [on|off]`.
     * False: `torch o`, `apps work` (`-l` may follow), `volume up` (a number could go there).
     */
    fun complete(line: String, usage: String): Boolean {
        val words = line.trim().lowercase().split(Regex("\\s+"))
        val places = tokens(usage).drop(1)
        return words.size >= 2 && words.size - 1 == places.size && words.drop(1).zip(places).all { (word, place) ->
            val choices = choices(place)
            choices.all { it.word != null } &&
                choices.any { it.word == word && !it.takesValue } &&
                choices.none { it.word != word && it.word!!.startsWith(word) }
        }
    }

    /** Splits on [sep] where it is not inside `[]` or `<>`. */
    fun split(s: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        var depth = 0
        val cur = StringBuilder()
        for (ch in s) {
            when (ch) {
                '[', '<' -> { depth++; cur.append(ch) }
                ']', '>' -> { depth--; cur.append(ch) }
                sep if depth == 0 -> { out += cur.toString(); cur.clear() }
                else -> cur.append(ch)
            }
        }
        out += cur.toString()
        return out
    }
}
