/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

/**
 * The shaders `screen.shader` can name: the ones that come with Kolahal and the user's own `.agsl`
 * files ([Shaders] reads both from the phone). Their names and which file wins, as plain Kotlin.
 */
object ShaderCatalog {
    /** `screen.shader`'s value for no shader. */
    const val OFF = "off"

    /** A shader file's extension. */
    const val EXT = "agsl"

    /** The shaders that come with Kolahal, as assets (`shaders/<name>.agsl`). */
    val BUNDLED = listOf(
        "crt", "lcd", "eink", "paper", "riso", "graph", "foil", "lamp", "dusk", "frost",
        "rain", "snowfall", "aurora", "night", "fireflies", "contour", "ink",
    )

    /** What the `shader` command reads as its own words: no shader has one of these names. */
    val WORDS =
        listOf("list", "ls", "use", "on", OFF, "get", "update", "check", "reload", "copy", "rm", "remove")

    /** A shader's name: one word at the prompt, like a script's. */
    private val NAME = Regex("[a-z0-9][a-z0-9_-]{0,39}")
    private val NOT_NAME = Regex("[^a-z0-9_-]+")

    /** Whether a shader can be called [name]. */
    fun valid(name: String): Boolean = NAME.matches(name) && name !in WORDS

    /**
     * The shader a file gives: `My Glow.agsl` is `my-glow`. Null when [fileName] isn't an `.agsl`
     * file, or leaves no name a shader can have.
     */
    fun nameOf(fileName: String): String? {
        if (!fileName.endsWith(".$EXT", ignoreCase = true)) return null
        return fileName.dropLast(EXT.length + 1).lowercase().replace(NOT_NAME, "-").trim('-', '_')
            .take(40).takeIf(::valid)
    }

    /**
     * One shader. [bundled]: Kolahal has one of this name; [yours]: the user has a file of it, which
     * is then the one used.
     */
    data class Entry(val name: String, val bundled: Boolean, val yours: Boolean)

    /** Every shader, the bundled ones first, then the user's own by name. */
    fun list(yours: Collection<String>): List<Entry> {
        val mine = yours.toSet()
        return BUNDLED.map { Entry(it, bundled = true, yours = it in mine) } +
                (mine - BUNDLED.toSet()).sorted().map { Entry(it, bundled = false, yours = true) }
    }
}
