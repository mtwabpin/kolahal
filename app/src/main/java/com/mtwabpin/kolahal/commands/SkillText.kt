/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.commands

import java.util.Locale

/**
 * `skill.md`: guides for an AI assistant on how to write scripts and shaders for Kolahal. The
 * command copies one whole, so the user can paste it into an assistant and ask for a script or a
 * shader. It is offered to nobody ([CommandCatalog.HIDDEN]).
 *
 * - `skill.md`: the whole guide, scripts and shaders ([Guide.ALL]).
 * - `skill.md shaders`: shaders only, with more examples and rules ([Guide.SHADERS]).
 * - `skill.md widgets`: scripts whose point is a widget, with the whole script API ([Guide.WIDGETS]).
 *
 * The guides are written by hand, in pieces kept in the app's assets ([FOLDER]): each fact is in one
 * piece, and each guide is its title and a list of pieces ([Guide.assemble]). SkillTextTest keeps
 * every guide in step with the code: every function of the script API, every uniform a shader gets,
 * every header line, and examples that pass the checks.
 */
object SkillText {
    /** The command's name. */
    const val NAME = "skill.md"

    /** The assets folder that holds the guides' pieces, one `.md` file each. */
    const val FOLDER = "skill"

    /** The most characters a guide may have, so an assistant reads it in one go. */
    const val MAX_CHARS = 40_000

    /**
     * A guide: the word after `skill.md` that gives it ([word]; none for the whole guide), its
     * [title], and its [pieces], the names of files in [FOLDER] without `.md`, in order.
     */
    enum class Guide(val word: String?, val title: String, val pieces: List<String>) {
        ALL(
            null,
            "Writing scripts and shaders for Kolahal",
            listOf(
                "about",
                "answer",
                "install",
                "install-script",
                "install-shader",
                "scripts",
                "widgets",
                "screens",
                "shaders",
                "examples",
                "examples-scripts",
                "examples-shaders",
            ),
        ),
        SHADERS(
            "shaders",
            "Writing shaders for Kolahal",
            listOf(
                "about",
                "about-shaders",
                "answer-shaders",
                "install",
                "install-shader",
                "shaders",
                "shaders-more",
                "examples",
                "examples-shaders",
                "examples-shaders-more",
            ),
        ),
        WIDGETS(
            "widgets",
            "Writing widget scripts for Kolahal",
            listOf(
                "about",
                "about-widgets",
                "answer",
                "install",
                "install-script",
                "scripts",
                "widgets",
                "widgets-more",
                "screens",
                "examples",
                "examples-scripts",
                "examples-widgets",
            ),
        );

        /** What the user types for it: `skill.md`, `skill.md shaders`. */
        val command: String get() = if (word == null) NAME else "$NAME $word"

        /** The guide's text: its title, then its pieces, a blank line between. [read] gives a file of the assets by its path. */
        fun assemble(read: (String) -> String): String =
            "# $title\n\n" + pieces.joinToString("\n") { read(path(it)) }

        companion object {
            /** The guide that the words after `skill.md` ask for ("" for the whole one), or null for a word that is none. */
            fun of(words: String): Guide? = entries.firstOrNull { (it.word ?: "") == words.trim().lowercase() }
        }
    }

    /** The asset path of a piece: `skill/about.md`. */
    fun path(piece: String): String = "$FOLDER/$piece.md"

    /** The log line after a copy of [guide], [chars] characters long: which guide, its length, what to do next. */
    fun copied(guide: Guide, chars: Int): String {
        val which = when (guide) {
            Guide.ALL -> NAME
            Guide.SHADERS -> "${guide.command}, the guide for shaders"
            Guide.WIDGETS -> "${guide.command}, the guide for widget scripts"
        }
        return "Copied $which (${String.format(Locale.ROOT, "%,d", chars)} characters)\nPaste it into an AI assistant and say what to build"
    }

    /** The log line for words after `skill.md` that are no guide: what was wrong, then each guide, one to a line. */
    fun unknown(words: String): String =
        "No guide called '${words.trim()}'\n" + Guide.entries.joinToString("\n") { "${it.command}: ${about(it)}" }

    /** A guide in a few words, as [unknown] and the command's help list them. */
    fun about(guide: Guide): String = when (guide) {
        Guide.ALL -> "the whole guide, scripts and shaders"
        Guide.SHADERS -> "shaders only"
        Guide.WIDGETS -> "scripts with a widget, with the whole script API"
    }
}
