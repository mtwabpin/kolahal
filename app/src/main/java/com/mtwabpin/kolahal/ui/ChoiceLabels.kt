package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys

/**
 * The words Settings shows for a choice's values. Only the screen changes: the value stays what
 * `config` takes and what a backup holds.
 * - A value listed here shows as its label.
 * - Any other value shows with a capital letter, dashes as spaces: `far-side` is "Far side".
 */
internal object ChoiceLabels {
    private val LABELS: Map<String, Map<String, String>> = mapOf(
        // The setting is called "Full screen", so "show" (the bars) means it is off.
        Keys.STATUS_BAR.key to mapOf("show" to "Off", "hide" to "Status bar", "immersive" to "Immersive"),
        Keys.FONT_SANS.key to mapOf("prompt" to "Prompt only"),
        Keys.SEARCH_ENGINE.key to mapOf("duckduckgo" to "DuckDuckGo"),
    )

    fun of(key: ConfigKey.Choice, value: String): String = LABELS[key.key]?.get(value) ?: readable(value)

    /** A built-in theme's name with capitals ("Gruvbox Material"). A theme you made keeps its name as you wrote it. */
    fun theme(name: String): String =
        if (name in Keys.BUILT_IN_THEMES) name.split('-').joinToString(" ") { it.replaceFirstChar(Char::uppercase) } else name

    private fun readable(value: String) = value.replace('-', ' ').replaceFirstChar(Char::uppercase)
}
