/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

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
        "mountains", "pond", "dunes", "rain", "snowfall", "grove-snow", "grove-autumn", "aurora", "night", "fireflies",
        "contour", "ink", "aura", "day-ring", "lanterns", "kintsugi", "record", "candle", "hourglass", "windows",
        "seismograph", "hearts", "pulse",
    )

    /**
     * Shaders to start your own from, as assets beside the bundled ones (`shaders/template-<idea>.agsl`),
     * in the order Add shader → Start from a template lists them. They aren't in [BUNDLED]: they are
     * never listed or used, only copied as a shader of the user's ([yourName]).
     */
    val TEMPLATES = listOf(
        "template-tint", "template-scene", "template-moving", "template-touch", "template-value", "template-once",
    )

    /** What the `shader` command reads as its own words: no shader has one of these names. */
    val WORDS =
        listOf("ls", "use", "on", OFF, "set", "reset", "follow", "values", "play", "words", "get", "check", "reload", "save", "edit", "rm", "delete")

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
     * The name of a copy of the template [template]: `template-tint` gives `my-tint`, then `my-tint-2`,
     * `my-tint-3` and on while [taken] says a name is taken. Never a bundled shader's name.
     */
    fun yourName(template: String, taken: (String) -> Boolean): String {
        val base = "my-" + template.removePrefix("template-")
        return generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }
            .first { valid(it) && it !in BUNDLED && !taken(it) }
    }

    /**
     * What Edit opens for shader [name], when the user's own are [yours]: their file ([Edit.YOURS]), or the code of
     * the one that comes with Kolahal ([Edit.COPY]). Null when there is no shader [name]: a template isn't one.
     */
    fun edit(name: String, yours: Collection<String>): Edit? = when {
        name in yours -> Edit.YOURS
        name in BUNDLED -> Edit.COPY
        else -> null
    }

    /**
     * What the editor has open. [YOURS]: the user's file, saved in place. [COPY]: one that comes with Kolahal; the
     * first save makes the user's file of the same name, which is used instead of it, as after `shader save` and
     * `shader reload`. Removing that file brings Kolahal's back.
     */
    enum class Edit { YOURS, COPY }

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
