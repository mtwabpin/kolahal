package com.mtwabpin.kolahal.terminal

/**
 * Named keys that the touch key bar understands. Any other text in the spec is inserted as it is.
 * The view model acts on them ([TerminalViewModel.onKeyBar]); the key bar (ui/KeyBar) draws them.
 */
enum class KeyBarKey(val label: String) {
    ESC("ESC"), TAB("TAB"), UP("↑"), DOWN("↓"), LEFT("←"), RIGHT("→"),
    HOME("⇤"), END("⇥"), ENTER("⏎"), KBD("⌨"), SPACE("␣"), BKSP("⌫");

    companion object {
        fun of(token: String) = entries.firstOrNull { it.name.equals(token, ignoreCase = true) }
    }
}
