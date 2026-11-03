package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.ui.TermFonts

/**
 * What the line in the prompt would change, shown on home before Enter: `theme dracula`,
 * `font fira-code`, `config set font.size 18`. Nothing is saved until the command runs.
 */
data class LivePreview(val theme: String? = null, val font: String? = null, val fontSize: Int? = null) {
    val label: String get() = theme ?: font?.let { "font $it" } ?: "font size $fontSize"

    companion object {
        private val SPACES = Regex("\\s+")

        /** The preview of [line]: only a whole value, and only one that isn't in use already. */
        fun of(line: String, config: Config, hasCustomFont: () -> Boolean): LivePreview? {
            val words = line.trim().lowercase().split(SPACES)
            val rest = words.drop(1)
            val (key, value) = when (words[0]) {
                "theme" -> rest.singleOrNull()?.let { Keys.THEME to it }
                "font" -> rest.singleOrNull()?.let { Keys.FONT to it }
                // As `config` takes them: `config set font.size 18`, `config font.size 18`, `set font.size 18`.
                "config", "set" -> (if (rest.firstOrNull() == "set") rest.drop(1) else rest).takeIf { it.size == 2 }?.let { Keys.byName(it[0]) to it[1] }
                else -> null
            } ?: return null
            return when (key) {
                Keys.THEME -> Keys.THEME.parse(value)?.takeIf { it != config[Keys.THEME] }?.let { LivePreview(theme = it) }
                Keys.FONT -> Keys.FONT.parse(value)?.takeIf { it != config[Keys.FONT] && (it != TermFonts.CUSTOM || hasCustomFont()) }?.let { LivePreview(font = it) }
                Keys.FONT_SIZE -> Keys.FONT_SIZE.parse(value)?.takeIf { it != config[Keys.FONT_SIZE] }?.let { LivePreview(fontSize = it) }
                else -> null
            }
        }
    }
}
