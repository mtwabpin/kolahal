package com.mtwabpin.kolahal.data

/**
 * The first [n] characters, or one fewer if the [n]th starts an emoji or another character written
 * as two. Cutting there would end the line in half a character, which shows as a box.
 */
fun String.takeWhole(n: Int): String {
    val cut = take(n)
    return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}
