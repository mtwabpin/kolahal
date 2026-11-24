/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.shader.ShaderCatalog

/**
 * The shaders a stat may name as its `scene`: a picture behind the text, as a wallpaper is. The card
 * draws one still frame of it as its ground, with the figures over it on a scrim.
 * - A shader that works on the text itself (`crt`, `lcd` …) or plays once (`hearts`, `pulse`) is no
 *   scene: those are the ones that can't be a wallpaper ([com.mtwabpin.kolahal.shader.WallpaperChoice.suits]).
 * - `widget.show` refuses the bundled ones of them by name ([NOT_SCENES]). A user's own shader is only
 *   known on the phone: the card reads its header when it draws, and draws the plain stat for one
 *   that is no scene, or that is missing or doesn't compile.
 * Pure: the parser and the stored form ask here.
 */
object StatScenes {
    /** The bundled shaders that are no scene: they work on text, or play once. A test reads their headers. */
    val NOT_SCENES = listOf("crt", "lcd", "eink", "riso", "foil", "hearts", "pulse")

    /** The scenes the guide names for a weather card, as the words for what the sky does. */
    val WEATHER = listOf("pond", "rain", "snowfall", "dunes", "mountains")

    /** The longest name read for a scene: longer than any shader's name may be, so a long one is an error, never cut. */
    const val MAX_NAME = 64

    /**
     * Why [name] can't be a stat's scene, as the end of an error that starts with "scene ", or null
     * when it can be. [name] is as the script wrote it, after a trim.
     */
    fun problem(name: String): String? = when {
        !ShaderCatalog.valid(name) -> "must be a shader's name, such as ${WEATHER.first()}, not '${name.take(24)}'"
        name in NOT_SCENES -> "$name is no scene: it works on the text or plays once; use a scene such as ${suggested()}"
        else -> null
    }

    /** The weather scenes, for an error: "pond, rain, snowfall, dunes or mountains". */
    private fun suggested(): String = WEATHER.dropLast(1).joinToString() + " or " + WEATHER.last()
}
