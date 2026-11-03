package com.mtwabpin.kolahal.data

/**
 * The first [n] characters, or one fewer where the [n]th starts an emoji or another character
 * written as two: cut there, the line would end in half a character, which shows as a box.
 */
fun String.takeWhole(n: Int): String {
    val cut = take(n)
    return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}
