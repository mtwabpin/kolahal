/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

/**
 * What a shader can be over: a dark theme, a light one, a page that the wallpaper shows through (`log.wallpaper`),
 * or another app, for a one-off shader ([ShaderOverApps]): there it draws on a clear ground, with nothing to read.
 */
enum class ShaderSurface(val word: String, val label: String) {
    DARK("dark", "Dark theme"), LIGHT("light", "Light theme"), WALLPAPER("wallpaper", "Wallpaper"), APPS("apps", "Other apps");

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

/**
 * `// @suits dark good, light fair, wallpaper poor`: how well a shader works over each, as its author says. A
 * one-off may add `apps good`. What isn't there isn't rated.
 */
object ShaderSuits {
    /** The header's word for it. */
    const val DIRECTIVE = "suits"

    /** The surfaces of home, which every shader that comes with Kolahal is rated for. [ShaderSurface.APPS] is for a one-off alone. */
    val HOME = listOf(ShaderSurface.DARK, ShaderSurface.LIGHT, ShaderSurface.WALLPAPER)

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
