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
        // The setting is called "Hide system bars": None, Status bar, Both.
        Keys.SCREEN_HIDE_BARS.key to mapOf("status" to "Status bar"),
        Keys.FONT_SANS.key to mapOf("prompt" to "Prompt only"),
        Keys.SEARCH_ENGINE.key to mapOf("duckduckgo" to "DuckDuckGo"),
        Keys.WIDGET_CORNERS.key to mapOf("own" to "Its own", "slight" to "Slightly rounded", "very" to "Very rounded"),
        // The services by the names the Assistant shows (Claude, Gemini, ChatGPT). Only in a build that has it.
        *(if (com.mtwabpin.kolahal.BuildConfig.ASSISTANT) {
            arrayOf(Keys.ASSISTANT_SERVICE.key to com.mtwabpin.kolahal.assistant.Services.ALL.associate { it.id to it.name })
        } else emptyArray()),
    )

    /**
     * A number that a setting shows as something else: the screensaver's 0 minutes is half a minute,
     * and a daily limit of 0 is none.
     */
    private val NUMBERS: Map<String, Map<Int, String>> = mapOf(
        Keys.SCREEN_SAVER_AFTER.key to mapOf(0 to "30 s"),
        Keys.USAGE_SCREEN_TIME_LIMIT.key to mapOf(0 to "Off"),
        Keys.USAGE_UNLOCK_LIMIT.key to mapOf(0 to "Off"),
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
