/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.data

import java.text.BreakIterator

/**
 * The first [n] characters, or one fewer if the [n]th starts an emoji or another character written
 * as two. Cutting there would end the line in half a character, which shows as a box.
 */
fun String.takeWhole(n: Int): String {
    val cut = take(n)
    return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}

/**
 * The first [n] characters as they are seen, and no more than [units] UTF-16 units in all. An emoji
 * made of several (a flag, a skin tone, people joined by a zero-width joiner) counts as one, and is
 * kept whole or left out. No "…" is added.
 */
fun String.takeSeen(n: Int, units: Int): String {
    if (n <= 0 || units <= 0 || isEmpty()) return ""
    val breaks = BreakIterator.getCharacterInstance()
    breaks.setText(this)
    var end = 0
    var count = 0
    while (count < n) {
        val next = breaks.next()
        if (next == BreakIterator.DONE || next > units) break
        end = next
        count++
    }
    return substring(0, end)
}
