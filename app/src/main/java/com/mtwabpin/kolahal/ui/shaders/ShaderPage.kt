/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.shaders

import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderFit
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderSurface
import com.mtwabpin.kolahal.shader.SharedShader
import com.mtwabpin.kolahal.terminal.ShaderRow
import com.mtwabpin.kolahal.ui.widgets.STILL

/** What the Shaders screen says of a shader, as plain functions (ShaderPageTest). */
internal object ShaderPage {
    /** Under a shader's description: whether it is on or was turned off, where it is from and what differs. [inUse] is the one set. */
    fun notes(row: ShaderRow, inUse: String, words: String? = null, wallpaper: Boolean = false): List<String> = listOfNotNull(
        when {
            row.turnedOff -> turnedOff(row)
            row.name == inUse -> "In use"
            else -> null
        },
        from(row),
        if (row.origin?.edited == true) "Edited" else null,
        if (row.meta.animated && row.meta.once == null) "Animated" else null,
        if (row.meta.tilt) USES_TILT else null,
        if (row.meta.params.isNotEmpty()) "Adjustable" else null,
        words?.let { "Plays on: $it" },
        if (wallpaper) "Your wallpaper" else null,
        if (row.origin?.newer == true) update(row) else null,
    )

    /** The chips under the search field: each keeps the shaders that [keeps] it. */
    enum class Filter(val label: String, val keeps: (ShaderRow) -> Boolean) {
        DARK("Dark theme", { it.meta.suits[ShaderSurface.DARK] == ShaderFit.GOOD }),
        LIGHT("Light theme", { it.meta.suits[ShaderSurface.LIGHT] == ShaderFit.GOOD }),
        WALLPAPER("Wallpaper", { it.meta.suits[ShaderSurface.WALLPAPER] == ShaderFit.GOOD }),
        STILL("Still", { !it.meta.animated }),
        ANIMATED("Animated", { it.meta.animated }),
        TILT("Tilt", { it.meta.tilt }),
        ADJUSTABLE("Adjustable", { it.meta.params.isNotEmpty() }),
        ONCE("One-off", { it.meta.once != null }),
        YOURS("Yours", { it.yours }),
    }

    /** Whether [row] has every word of [query], in its name, title, description or tags, and passes every one of [filters]. */
    fun matches(row: ShaderRow, query: String, filters: Set<Filter>): Boolean {
        if (filters.any { !it.keeps(row) }) return false
        val text = listOfNotNull(row.name, row.title, row.meta.description, row.meta.author, row.meta.tags.joinToString(" "))
            .joinToString(" ").lowercase()
        return query.lowercase().split(' ').all { it in text }
    }

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

    /**
     * Play, on a one-off's row and in its sheet: null where there is none (a shader that isn't a one-off, or a screen
     * reader is on, as nothing plays then), else whether it can be pressed. It can't where shaders aren't drawn
     * ([supported]), for one that was turned off, or one that couldn't be used ([problem]); the page says why.
     */
    fun play(row: ShaderRow, problem: String?, supported: Boolean, screenReader: Boolean): Boolean? = when {
        row.meta.once == null || screenReader -> null
        else -> supported && !row.turnedOff && problem == null
    }

    /**
     * Whether a tap on a one-off's row plays it, over the page: where its [play] can be pressed, and also for one that was
     * turned off, which plays and is on again, as a tap on any other turns it on again. Not with a screen reader on, where
     * shaders aren't drawn, or for one that couldn't be used: the page says why.
     */
    fun rowPlays(row: ShaderRow, play: Boolean?, problem: String?, supported: Boolean): Boolean =
        play == true || (play == false && row.turnedOff && supported && problem == null)

    /** That the safe mode turned it off, with the reason when it is known. */
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
