/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.commands

import com.mtwabpin.kolahal.data.Lab

/**
 * What the Lab features ([Lab.Feature]) add to the built-in commands while they are on: a word after a command,
 * its place in `help` and in `help <command>`, and its completion. While Lab is off, none of it shows, and the
 * commands are exactly as [CommandCatalog] has them.
 */
object LabCommands {
    /** `wallpaper picture <name>`: the composer for a still picture of a scene ([Lab.Feature.PICTURE]). */
    const val PICTURE = "picture"

    /** What a feature adds to [command]: the [word] after it, its [args] in `help`'s list, and its [use] in `help <command>`. */
    private class Adds(val command: String, val word: String, val args: String, val use: Pair<String, String>)

    private fun adds(feature: Lab.Feature): Adds = when (feature) {
        Lab.Feature.PICTURE -> Adds(
            "wallpaper",
            PICTURE,
            "$PICTURE <name>",
            "wallpaper $PICTURE <name>" to "a still picture of a scene, to save to the gallery or set as your wallpaper (Lab)",
        )
    }

    /** [spec] as `help` shows it while [features] are on: with their words and uses added. */
    fun spec(spec: CommandSpec, features: List<Lab.Feature>): CommandSpec {
        val more = features.map(::adds).filter { it.command == spec.name }
        if (more.isEmpty()) return spec
        val uses = spec.uses.ifEmpty { listOf(spec.usage to spec.short) }
        return spec.copy(
            args = (listOf(spec.args).filter { it.isNotEmpty() } + more.map { it.args }).joinToString(" · "),
            uses = uses + more.map { it.use },
        )
    }

    /** The words after [command] that [features] add, for completion: `wallpaper p` offers `picture`. */
    fun words(command: String, features: List<Lab.Feature>): List<String> =
        features.map(::adds).filter { it.command == command }.map { it.word }
}
