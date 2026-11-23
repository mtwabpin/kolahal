/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.wallpaper

import android.content.Context
import androidx.core.util.AtomicFile
import com.mtwabpin.kolahal.script.Json
import java.io.File

/**
 * All that the live wallpaper draws with, as home hands it over in a file: the wallpaper has a process of its
 * own, so a shader that crashes it doesn't take home along, and that process can't see home's settings change.
 */
data class WallpaperState(
    val name: String,
    val code: String,
    /** What its parameters are set to, what they follow and the formulas, and the values that scripts gave. */
    val set: Map<String, String>,
    val follows: Map<String, String>,
    val formulas: Map<String, String>,
    val given: Map<String, String>,
    /** The theme's colors by role, for dark and for light. */
    val dark: Map<String, Int>,
    val light: Map<String, Int>,
    val alwaysDark: Boolean,
    val cellW: Float,
    val cellH: Float,
) {
    fun encode(): String = Json.stringify(
        mapOf(
            "name" to name, "code" to code, "set" to set, "follows" to follows, "formulas" to formulas, "given" to given,
            "dark" to dark.mapValues { it.value.toString() }, "light" to light.mapValues { it.value.toString() },
            "alwaysDark" to alwaysDark, "cellW" to cellW.toDouble(), "cellH" to cellH.toDouble(),
        )
    )

    companion object {
        /** Null when [text] isn't one. */
        fun decode(text: String): WallpaperState? = runCatching {
            val all = Json.parse(text) as Map<*, *>
            fun texts(key: String) = (all[key] as? Map<*, *>).orEmpty().entries.associate { it.key.toString() to it.value.toString() }
            fun colors(key: String) = texts(key).mapValues { it.value.toInt() }
            WallpaperState(
                name = all["name"] as String, code = all["code"] as String,
                set = texts("set"), follows = texts("follows"), formulas = texts("formulas"), given = texts("given"),
                dark = colors("dark"), light = colors("light"), alwaysDark = all["alwaysDark"] == true,
                cellW = (all["cellW"] as Number).toFloat(), cellH = (all["cellH"] as Number).toFloat(),
            )
        }.getOrNull()

        /** A character's width as a part of the text's size, in the terminal's fonts. */
        private const val WIDE = 0.6f

        /** A line of text's own height as a part of its size, about: a cell is never shorter than its text. */
        private const val TALL = 1.2f

        /**
         * The text cell's size in pixels, near to what home's grid measures with the font
         * ([com.mtwabpin.kolahal.ui.tty.GridMetrics]): a character wide and a line tall. [textPx] is the text's
         * size (`font.size`) and [linePx] a line's height (`log.line-height`), which is a height of its own and
         * not a part of the text's size.
         */
        fun cell(textPx: Float, linePx: Float): Pair<Float, Float> = textPx * WIDE to maxOf(linePx, textPx * TALL)

        /** The folder it is in, which the wallpaper watches. */
        fun dir(context: Context) = File(context.filesDir, "wallpaper")

        /** The state's file in [dir]. */
        const val FILE = "state.json"

        private fun file(context: Context) = File(dir(context), FILE)

        fun read(context: Context): WallpaperState? =
            runCatching { AtomicFile(file(context)).readFully().toString(Charsets.UTF_8) }.getOrNull()?.let(::decode)

        /** Not on the main thread. */
        fun write(context: Context, state: WallpaperState) {
            dir(context).mkdirs()
            val file = AtomicFile(file(context))
            val out = file.startWrite()
            try {
                out.write(state.encode().toByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
                throw e
            }
        }
    }
}
