/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

/** What a shader can be over: a dark theme, a light one, or a page that the wallpaper shows through (`log.wallpaper`). */
enum class ShaderSurface(val word: String, val label: String) {
    DARK("dark", "Dark theme"), LIGHT("light", "Light theme"), WALLPAPER("wallpaper", "Wallpaper");

    companion object {
        fun named(word: String): ShaderSurface? = entries.firstOrNull { it.word == word }
    }
}

/** How well it works there. */
enum class ShaderFit(val word: String, val label: String) {
    GOOD("good", "Good"), FAIR("fair", "Fair"), POOR("poor", "Poor");

    companion object {
        fun named(word: String): ShaderFit? = entries.firstOrNull { it.word == word }
    }
}

/** `// @suits dark good, light fair, wallpaper poor`: how well a shader works over each, as its author says. What isn't there isn't rated. */
object ShaderSuits {
    /** The header's word for it. */
    const val DIRECTIVE = "suits"

    /** How much of the wallpaper (`log.wallpaper`, percent) may show before a shader that is poor over one is worth a warning. */
    const val WALLPAPER_SHOWS = 20

    /** Reads what follows `@suits`. What can't be read is left out. */
    fun read(line: String): Map<ShaderSurface, ShaderFit> = line.split(',').mapNotNull { part ->
        val words = part.trim().lowercase().split(Regex("\\s+"))
        val surface = ShaderSurface.named(words[0])
        val fit = words.getOrNull(1)?.let(ShaderFit::named)
        if (surface != null && fit != null && words.size == 2) surface to fit else null
    }.toMap()
}
