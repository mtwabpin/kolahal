/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.data

import android.content.Context
import android.os.Build
import androidx.annotation.ChecksSdkIntAtLeast
import com.mtwabpin.kolahal.data.CustomThemes.LATER
import com.mtwabpin.kolahal.data.CustomThemes.whole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * The built-in theme `material-you`. It takes its colors from the phone's wallpaper.
 * - Android has wallpaper colors from version 12 ([supported]). There the theme changes when
 *   the wallpaper does.
 * - Android 9 to 11 has none. There the theme shows Material's standard colors. The theme's card
 *   in Settings says so, and so does the `theme` command ([note]).
 * The screen's code reads the colors ([com.mtwabpin.kolahal.ui.MaterialYouPalettes]).
 */
object MaterialYou {
    const val NAME = "material-you"

    /** The variations: the same wallpaper colors, with a lighter, a tinted or a black background (`MaterialYouPalettes`). */
    const val SOFT = "$NAME-soft"
    const val TINTED = "$NAME-tinted"
    const val BLACK = "$NAME-black"

    /** Every Material You theme, the plain one first. */
    val VARIANTS = listOf(NAME, SOFT, TINTED, BLACK)

    /** Whether the theme [name] takes its colors from the wallpaper. */
    fun isOne(name: String?): Boolean = name in VARIANTS

    /** True where the phone has wallpaper colors: Android 12 and newer. */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.S)
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    /** What the `theme` command says after it sets the Material You theme [name] on a phone that isn't [supported]. */
    fun note(name: String) =
        "$name takes its colors from the wallpaper on Android 12 and newer. This phone has an older Android, so it shows Material's standard colors"
}

/**
 * Themes people make in Settings (Look & feel → Your themes).
 * - Each theme is one file, `files/themes/<name>.json`.
 * - The file has `"role": "#RRGGBB"` for every role in [ROLES] (the dark version), and
 *   `"light-role"` for each role of the light version ([LIGHT]).
 * - A theme without a light version (made before themes had one) looks the same in both modes.
 * Themes are loaded once at start ([load]). `theme.name` accepts their names next to the built-in ones.
 */
object CustomThemes {
    /** Color roles, in the builder's order. */
    val ROLES = listOf(
        "background",
        "surface",
        "text",
        "muted",
        "accent",
        "link",
        "info",
        "script",
        "error",
        "good",
        "warn",
        "personal",
        "work",
        "private"
    )

    /** Roles added later, and the role that an older theme takes each one from. */
    private val LATER = mapOf("good" to "personal", "warn" to "work")

    /** A theme's name: lowercase letters, digits and hyphens, like the built-in ones. */
    val NAME = Regex("[a-z0-9][a-z0-9-]{0,23}")

    /** The prefix of the light version's roles: `light-background`. */
    const val LIGHT = "light-"

    private const val DIR = "themes"
    private val ROLE_COLOR = Regex("\"([a-z-]+)\"\\s*:\\s*\"#([0-9a-fA-F]{6})\"")

    /** The light version of [colors] by role, or null when it has none (or only part of one). */
    fun light(colors: Map<String, Int>): Map<String, Int>? =
        ROLES.associateWith { colors[LIGHT + it] ?: return null }

    /**
     * [colors], keeping the light version only if it is complete, because half of one would be read
     * as none. Roles added later ([LATER]) that a theme doesn't have yet are copied from their nearest
     * role, in both versions.
     */
    private fun whole(colors: Map<String, Int>): Map<String, Int> {
        val filled = HashMap(colors)
        for ((role, from) in LATER) for (prefix in listOf("", LIGHT)) {
            if (prefix + role !in filled) filled[prefix + from]?.let { filled[prefix + role] = it }
        }
        return filled.filterKeys { it in ROLES } + (light(filled)?.mapKeys { LIGHT + it.key }
            ?: emptyMap())
    }

    /** [colors] as a complete theme ([whole]), or null when a role of its dark version is missing. */
    fun complete(colors: Map<String, Int>): Map<String, Int>? =
        whole(colors).takeIf { c -> ROLES.all { it in c } }

    /** Whether a theme's file may hold [key]: a role of either version. */
    fun isKey(key: String) =
        key in ROLES || (key.startsWith(LIGHT) && key.removePrefix(LIGHT) in ROLES)

    private val _themes = MutableStateFlow<Map<String, Map<String, Int>>>(emptyMap())

    /** Every custom theme: name to colors (ARGB, by role). */
    val themes: StateFlow<Map<String, Map<String, Int>>> = _themes.asStateFlow()

    val names: List<String> get() = _themes.value.keys.sorted()

    fun colors(name: String): Map<String, Int>? = _themes.value[name]

    private fun dir(context: Context) = File(context.filesDir, DIR)

    /**
     * Reads the saved themes. Called at start, before the theme is applied.
     * - A saved theme may have the name of a built-in theme that came in a later version (`nord`,
     *   in 0.5.9). The saved theme moves to a free name (`nord-2`, [movedNames]), so both can be picked.
     * - If `theme.name` named the saved theme, it follows the theme to its new name.
     */
    fun load(context: Context) {
        val moved = read(dir(context))
        if (moved.isEmpty()) return
        val config = Config.get(context)
        moved[config[Keys.THEME]]?.let { config.set(Keys.THEME, it) }
    }

    /**
     * Reads the theme files in [dir]. Before that, each file with a built-in theme's name moves to
     * its new name ([movedNames]). Returns the names that moved, old to new.
     */
    internal fun read(dir: File): Map<String, String> {
        val saved = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .map { it.name.removeSuffix(".json") }
        // A file that can't be moved keeps its name. The built-in theme then hides it, until the builder renames it.
        val moved = movedNames(saved.toSet()).filter { (from, to) ->
            File(dir, "$from.json").renameTo(
                File(
                    dir,
                    "$to.json"
                )
            )
        }
        _themes.value = saved.map { moved[it] ?: it }.mapNotNull { name ->
            val colors =
                runCatching { File(dir, "$name.json").readText() }.getOrNull()?.let(::decode)
            if (NAME.matches(name) && colors != null) name to colors else null
        }.toMap()
        return moved
    }

    /**
     * New names for the saved themes in [saved] that have a built-in theme's name, old to new.
     * Each new name is free ([freeName]): no built-in theme, saved theme or other new name has it.
     */
    internal fun movedNames(saved: Set<String>): Map<String, String> {
        val taken = (Keys.BUILT_IN_THEMES + saved).toMutableSet()
        return saved.filter { it in Keys.BUILT_IN_THEMES }.sorted()
            .associateWith { name -> freeName(name, taken).also { taken += it } }
    }

    fun save(context: Context, name: String, colors: Map<String, Int>) {
        val kept = complete(colors)
        require(NAME.matches(name) && kept != null) { "not a theme: $name" }
        File(dir(context).apply { mkdirs() }, "$name.json").writeText(encode(kept))
        _themes.value += (name to kept)
    }

    fun delete(context: Context, name: String) {
        File(dir(context), "$name.json").delete()
        _themes.value -= name
    }

    /**
     * `{ "background": "#0B0D0B", …, "light-background": "#F3F7F1", … }`, one role per line. A file
     * to share starts with the theme's [name], which [import] reads.
     */
    fun encode(colors: Map<String, Int>, name: String? = null): String {
        val keys = ROLES + (light(colors)?.let { ROLES.map { LIGHT + it } } ?: emptyList())
        val lines = listOfNotNull(name?.let { "  \"name\": \"$it\"" }) + keys.map {
            "  \"$it\": \"${
                hex(colors.getValue(it))
            }\""
        }
        return lines.joinToString(",\n", "{\n", "\n}\n")
    }

    private val NAMED = Regex("\"name\"\\s*:\\s*\"([^\"]{1,64})\"")

    /**
     * Saves a shared theme's file ([text], named [fileName]) as one of your themes. It uses the name
     * inside the file, or else the file's name. It adds -2, -3 … when a built-in theme or one of yours
     * already has that name. Returns the name, and false when you already have the theme (the same
     * name and colors).
     */
    fun import(context: Context, text: String, fileName: String?): Pair<String, Boolean> {
        val colors = decode(text) ?: throw IllegalArgumentException("not a Kolahal theme")
        val wanted =
            listOfNotNull(NAMED.find(text)?.groupValues?.get(1), fileName?.substringBeforeLast('.'))
                .map(::tidy).firstOrNull { NAME.matches(it) } ?: "imported"
        if (colors(wanted) == colors) return wanted to false
        val name = freeName(wanted, Keys.BUILT_IN_THEMES.toSet() + names)
        save(context, name, colors)
        return name to true
    }

    /** [text] as a theme's name: lowercase, spaces and underscores become hyphens, and anything else is removed. */
    fun tidy(text: String): String =
        text.trim().lowercase().replace(Regex("[\\s_]+"), "-").replace(Regex("[^a-z0-9-]"), "")
            .trimStart('-').take(24)

    /** [wanted], or with -2, -3 … added when [taken] has it. The result is still no longer than a name may be. */
    fun freeName(wanted: String, taken: Set<String>): String = generateSequence(1) { it + 1 }
        .map { if (it == 1) wanted else wanted.take(24 - "-$it".length).trimEnd('-') + "-$it" }
        .first { it !in taken }

    /** The colors in [text] (in [encode]'s form). Null unless every role of the dark version is there. */
    fun decode(text: String): Map<String, Int>? {
        val found = ROLE_COLOR.findAll(text)
            .associate { it.groupValues[1] to (0xFF000000 or it.groupValues[2].toLong(16)).toInt() }
        return complete(found)
    }

    /** "#0B0D0B", with no alpha, because themes are opaque. */
    fun hex(argb: Int): String = "#%06X".format(argb and 0xFFFFFF)

    /** An opaque color from "#0B0D0B" or "0b0d0b". Null for anything else. */
    fun parseHex(text: String): Int? = text.trim().removePrefix("#")
        .takeIf { s -> s.length == 6 && s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' } }
        ?.let { (0xFF000000 or it.toLong(16)).toInt() }
}
