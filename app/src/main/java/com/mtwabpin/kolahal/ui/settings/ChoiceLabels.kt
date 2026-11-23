/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.settings

import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.MaterialYou

/**
 * The words Settings shows for a choice's values. Only the screen changes: the value stays what
 * `config` takes and what a backup holds.
 * - A value listed here shows as its label.
 * - Any other value shows with a capital letter, dashes as spaces: `far-side` is "Far side".
 */
internal object ChoiceLabels {
    private val LABELS: Map<String, Map<String, String>> = mapOf(
        // The setting is called "Full screen", so "show" (the bars) means it is off.
        Keys.STATUS_BAR.key to mapOf(
            "show" to "Off",
            "hide" to "Status bar",
            "immersive" to "Immersive"
        ),
        Keys.FONT_SANS.key to mapOf("prompt" to "Prompt only"),
        Keys.SEARCH_ENGINE.key to mapOf("duckduckgo" to "DuckDuckGo"),
    )

    /** A number that a setting shows as something else: the screensaver's 0 minutes is half a minute. */
    private val NUMBERS: Map<String, Map<Int, String>> = mapOf(
        Keys.SCREEN_SAVER_AFTER.key to mapOf(0 to "30 s"),
    )

    /** What a number setting's slider shows for [value]: the number, or its words where it has some. */
    fun of(key: ConfigKey.Num, value: Int): String = NUMBERS[key.key]?.get(value) ?: value.toString()

    fun of(key: ConfigKey.Choice, value: String): String =
        LABELS[key.key]?.get(value) ?: readable(value)

    /** Built-in themes whose names need more than capitals. */
    private val THEME_LABELS = mapOf("rose-pine" to "Rosé Pine")

    /**
     * A built-in theme's name with capitals ("Gruvbox Material"), or its label ("Rosé Pine").
     * A theme you made keeps its name as you wrote it.
     */
    fun theme(name: String): String = when (name) {
        in THEME_LABELS -> THEME_LABELS.getValue(name)
        in Keys.BUILT_IN_THEMES -> name.split('-')
            .joinToString(" ") { it.replaceFirstChar(Char::uppercase) }

        else -> name
    }

    /**
     * A note under a theme's name on its card, or null for no note.
     * The Material You themes have one on Android 9 to 11 ([supported] is false there): the phone has no
     * wallpaper colors, so the card shows Material's standard colors.
     */
    fun themeNote(name: String, supported: Boolean = MaterialYou.supported): String? =
        "Android 12+".takeIf { MaterialYou.isOne(name) && !supported }

    private fun readable(value: String) = value.replace('-', ' ').replaceFirstChar(Char::uppercase)
}
