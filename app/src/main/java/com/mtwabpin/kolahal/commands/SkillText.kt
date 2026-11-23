/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.commands

import java.util.Locale

/**
 * `skill.md`: a guide for an AI assistant on how to write scripts and shaders for Kolahal, kept in
 * the app's assets under the same name. The command copies it whole, so the user can paste it into
 * an assistant and ask for a script or a shader. It is offered to nobody ([CommandCatalog.HIDDEN]).
 *
 * The guide is written by hand. SkillTextTest keeps it in step with the code: every function of the
 * script API, every uniform a shader gets, every header line, and examples that pass the checks.
 */
object SkillText {
    /** The command's name, and the asset it copies. */
    const val NAME = "skill.md"

    /** The most characters the guide may have, so an assistant reads it in one go. */
    const val MAX_CHARS = 40_000

    /** The log line after a copy of [chars] characters: what was copied, its length, what to do next. */
    fun copied(chars: Int): String =
        "Copied $NAME (${String.format(Locale.ROOT, "%,d", chars)} characters) · paste it into an AI assistant and say what to build"
}
