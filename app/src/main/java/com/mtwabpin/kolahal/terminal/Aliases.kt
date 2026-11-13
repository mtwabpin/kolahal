package com.mtwabpin.kolahal.terminal

/**
 * Aliases (`alias m = open maps`): how a line expands, and what an `alias …` line asks for.
 * [CommandProcessor] keeps them in [com.mtwabpin.kolahal.apps.AppPrefs]. These functions take
 * what they need as arguments, so JVM tests check them without a phone.
 */
object Aliases {
    /** How many times one line may expand: an alias may name another, this deep. */
    const val LEVELS = 5

    /**
     * [line] with its first word expanded through the aliases, at most [LEVELS] times and never in
     * a circle. [lookup] says what a word stands for (null: it isn't an alias).
     * - A built-in name ([reserved]) is never expanded, even if an alias of it got in some other
     *   way (an old backup).
     * - Each `|` gets a space on both sides, so `hf|head 5` is the alias `hf` and a filter.
     */
    fun expand(line: String, reserved: Set<String>, lookup: (String) -> String?): String {
        var current = Pipes.spaced(line)
        val seen = HashSet<String>()
        repeat(LEVELS) {
            val head = current.substringBefore(' ')
            if (head.lowercase() in reserved) return current
            if (!seen.add(head)) return current
            val expansion = lookup(head) ?: return current
            current = Pipes.spaced(expansion + " " + current.substringAfter(' ', ""))
        }
        return current
    }

    /** What an `alias …` line asks for. */
    sealed interface Request {
        /** `alias`: list them all. */
        data object ShowAll : Request

        /** `alias m`: show one. */
        data class Show(val name: String) : Request

        /** `alias m = open maps`. */
        data class Define(val name: String, val expansion: String) : Request

        /** A name or an expansion is missing. */
        data object Usage : Request

        /** The name is a built-in command's. */
        data class BuiltIn(val name: String) : Request
    }

    /**
     * Reads the words after `alias`.
     * - The name is lowercased, because typed lines are matched in lowercase. So `alias Help = …`
     *   can't hide behind the built-in.
     * - Quotes around the whole expansion are dropped.
     */
    fun parse(rest: String, reserved: Set<String>): Request {
        if (rest.isBlank()) return Request.ShowAll
        val name = rest.substringBefore('=').trim().substringBefore(' ').lowercase()
        val expansion = rest.substringAfter('=', "").trim().removeSurrounding("\"").removeSurrounding("'")
        return when {
            '=' !in rest -> Request.Show(name)
            name.isEmpty() || expansion.isEmpty() -> Request.Usage
            name in reserved -> Request.BuiltIn(name)
            else -> Request.Define(name, expansion)
        }
    }
}
