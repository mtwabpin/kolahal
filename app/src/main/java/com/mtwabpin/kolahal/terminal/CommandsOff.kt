package com.mtwabpin.kolahal.terminal

/**
 * `commands.off`: built-in commands turned off. Some people don't use a command, or want its name
 * to open an app instead (`maps` for Maps, `wiki` for a Wikipedia app).
 *
 * A command that is off works as if it didn't exist:
 * - Its name, or another name for it (`youtube` for `yt`), opens the app it matches.
 * - It isn't suggested, listed in `help`, or run by instant launch.
 * - Scripts, gestures and taps on log lines can't run it either.
 *
 * [ALWAYS_ON] can't be turned off, so there is always a way to turn the others back on.
 */
object CommandsOff {
    /** `help` finds commands; `config` and `settings` turn them back on. */
    val ALWAYS_ON = setOf("help", "config", "settings")

    /** The commands that can be turned off, in the catalog's order. The calculator (`=`) isn't a word, so it stays on. */
    val CHOICES: List<String> by lazy { CommandCatalog.OFFERED_NAMES.filter { it !in ALWAYS_ON && it != "=" } }

    /**
     * The value to store for [raw]: command names separated by commas or spaces, or `none`.
     * Another name for a command counts as that command (`youtube` is `yt`). Null when a name
     * isn't a command that can be turned off.
     */
    fun parse(raw: String): String? {
        val names = raw.split(',', ' ').map { it.trim().lowercase() }.filter { it.isNotEmpty() && it != "none" }.map(InstantRun::canonical)
        if (names.any { it !in CHOICES }) return null
        return CHOICES.filter { it in names }.joinToString(",")
    }

    /** The commands in a stored value. */
    fun names(value: String): Set<String> = value.split(',').map(String::trim).filterTo(HashSet()) { it.isNotEmpty() }

    /** Whether [word] (a command's name, or another name for it) names a command in [off]. */
    fun isOff(word: String, off: Set<String>): Boolean = off.isNotEmpty() && InstantRun.canonical(word) in off

    /** [value] with [name] turned off ([off] true) or on again. */
    fun toggled(value: String, name: String, off: Boolean): String {
        val now = names(value)
        return CHOICES.filter { if (it == name) off else it in now }.joinToString(",")
    }
}
