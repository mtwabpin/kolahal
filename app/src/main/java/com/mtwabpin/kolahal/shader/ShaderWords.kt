/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import android.content.Context
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.Stores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * The words that play a one-off shader ([ShaderPlay]) when a notification has one, by shader. People set them
 * in its details, or with `shader words`. Looking at a notification needs no script: Kolahal's own listener
 * asks here ([onNotification]).
 */
object ShaderWords {
    /** The most characters of words a shader can have. */
    const val MAX_CHARS = 200

    private val _words = MutableStateFlow<Map<String, String>>(emptyMap())

    /** The words, as they were written: with commas between. */
    val words: StateFlow<Map<String, String>> = _words.asStateFlow()

    fun load(context: Context) {
        _words.value = Stores.SHADER_WORDS.open(context).all.mapNotNull { (k, v) -> (v as? String)?.let { k to it } }.toMap()
    }

    /** [shader] plays on [text], words with commas between; blank for none. */
    fun set(context: Context, shader: String, text: String) {
        val kept = text.take(MAX_CHARS)
        _words.update { if (kept.isBlank()) it - shader else it + (shader to kept) }
        Stores.SHADER_WORDS.open(context).edit { if (kept.isBlank()) remove(shader) else putString(shader, kept) }
    }

    /** A notification came: the first shader with a word of it plays. From any thread of the main process. */
    fun onNotification(title: String, text: String) {
        val all = _words.value
        if (all.isEmpty()) return
        val said = "$title\n$text"
        all.entries.firstOrNull { (_, words) -> has(said, words) }?.let { ShaderPlay.play(it.key, null) }
    }

    /** Whether [text] has one of [words], whole and in any case. */
    fun has(text: String, words: String): Boolean =
        words.split(',').map { it.trim() }.filter { it.isNotEmpty() }.any { hasWord(text, it) }

    private fun hasWord(text: String, word: String): Boolean {
        var at = text.indexOf(word, ignoreCase = true)
        while (at >= 0) {
            val end = at + word.length
            if (!joins(text, at, at - 1) && !joins(text, end - 1, end)) return true
            at = text.indexOf(word, at + 1, ignoreCase = true)
        }
        return false
    }

    /** Whether the characters at [a] and [b] are both of a word, so it goes on. Nothing is beyond the text. */
    private fun joins(text: String, a: Int, b: Int): Boolean =
        b in text.indices && part(text[a]) && part(text[b])

    private fun part(c: Char): Boolean = c.isLetterOrDigit() || Character.getType(c).toByte() in MARKS

    /** Accents and vowel signs are of the word they are on, as in Hindi. */
    private val MARKS = setOf(Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK)
}
