package com.mtwabpin.kolahal.terminal

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/**
 * Aliases work like fish's abbreviations: an alias typed as the line's first word, then a space,
 * becomes what it stands for, right in the prompt. With `alias a = Authy`, typing `a` and a space
 * leaves `Authy ` in the prompt.
 *
 * - Only the first word expands, and only from the start of the line.
 * - Enter still expands an alias that wasn't expanded yet (`a` then Enter).
 */
object Abbreviations {
    /**
     * [value] with its first word expanded, when [value] is [old] with one space typed right after
     * that word. [expand] says what a word stands for (null: it isn't an alias). Null when nothing
     * expands: a paste, a deletion, a space typed after a later word, or no alias.
     */
    fun onSpace(old: String, value: TextFieldValue, expand: (String) -> String?): TextFieldValue? {
        val at = value.selection.end - 1
        if (!value.selection.collapsed || at < 1 || value.text[at] != ' ') return null
        // Exactly one space typed, right before the cursor.
        if (value.text.removeRange(at, at + 1) != old) return null
        val word = value.text.substring(0, at)
        if (word.any { it.isWhitespace() }) return null
        val body = expand(word)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return TextFieldValue(body + " " + value.text.substring(at + 1), TextRange(body.length + 1))
    }
}
