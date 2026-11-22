/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.script.ShaderCatalog
import com.mtwabpin.kolahal.script.ShaderMeta
import com.mtwabpin.kolahal.script.SharedShader
import com.mtwabpin.kolahal.terminal.ShaderRow

/** What the Shaders screen says of a shader, as plain functions (ShaderPageTest). */
internal object ShaderPage {
    /** Under a shader's description: whether it is on or was turned off, where it is from and what differs. [inUse] is the one set. */
    fun notes(row: ShaderRow, inUse: String): List<String> = listOfNotNull(
        when {
            row.turnedOff -> turnedOff(row)
            row.name == inUse -> "In use"
            else -> null
        },
        from(row),
        if (row.origin?.edited == true) "Edited" else null,
        if (row.meta.animated) "Animated" else null,
        if (row.meta.tilt) USES_TILT else null,
        if (row.origin?.newer == true) update(row) else null,
    )

    /** Of a shader that follows how the phone leans. */
    const val USES_TILT = "Uses tilt"

    /** A shared shader's file, and what it does, in the list: `glow.agsl · animated · uses tilt`. */
    fun fileLine(e: SharedShader): String = listOfNotNull(
        "${e.name}.${ShaderCatalog.EXT}",
        if (e.animated) "animated" else null,
        if (e.tilt) USES_TILT.lowercase() else null,
    ).joinToString(" · ")

    /** Whether a shared shader follows how the phone leans: its list says so, or its [code], once that is here. */
    fun usesTilt(e: SharedShader, code: String?): Boolean =
        e.tilt || (code != null && ShaderMeta.parse(code).tilt)

    /** That the watchdog or the safe mode turned it off, with the reason when it is known. */
    fun turnedOff(row: ShaderRow): String = row.why?.let { "Turned off: $it" } ?: "Turned off"

    /** Where one of the user's own is from. Null for one that comes with Kolahal. */
    fun from(row: ShaderRow): String? = when {
        row.origin != null -> "From ${row.origin.source}"
        row.yours && row.bundled -> "Yours, used instead of the one that comes with Kolahal"
        row.yours -> "Yours"
        else -> null
    }

    /** "Update to 1.1", or without a version "Update available". */
    fun update(row: ShaderRow): String =
        row.latest?.version?.ifBlank { null }?.let { "Update to $it" } ?: "Update available"

    /** Its file, version and author: `glow.agsl · v1.0 · by ann`. */
    fun byLine(row: ShaderRow): String = listOfNotNull(
        "${row.name}.agsl",
        row.meta.version?.let { "v$it" },
        row.meta.author?.let { "by $it" },
    ).joinToString(" · ")
}
