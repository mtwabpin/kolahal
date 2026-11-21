package com.mtwabpin.kolahal.terminal.tty

import org.junit.Assert.assertEquals
import org.junit.Test

/** How many cells text takes on the grid, cluster by cluster, whatever font a phone has. */
class CellWidthTest {
    private fun width(text: String) = CellWidth.total(text)
    private fun clusters(text: String) = CellWidth.clusters(text)

    @Test
    fun asciiIsOneCellEach() {
        assertEquals(5, width("hello"))
        assertEquals(listOf("a", "b", "c"), clusters("abc"))
        assertEquals(1, CellWidth.of(" "))
        assertEquals(1, CellWidth.of("~"))
    }

    @Test
    fun eastAsianWideIsTwo() {
        assertEquals(2, CellWidth.of("中"))
        assertEquals(4, width("中文"))
        assertEquals(2, CellWidth.of("あ"))
        assertEquals(2, CellWidth.of("한"))
        assertEquals(2, CellWidth.of("Ａ")) // fullwidth A
        assertEquals(1, CellWidth.of("ｱ")) // half-width katakana, as matrix uses
        assertEquals(7, width("a中b文c"))
    }

    @Test
    fun hangulJamoMakeOneSyllable() {
        val gak = "각"
        assertEquals(listOf(gak), clusters(gak))
        assertEquals(2, width(gak))
    }

    @Test
    fun emojiAreTwo() {
        assertEquals(2, CellWidth.of("😀"))
        assertEquals(2, CellWidth.of("🚀"))
    }

    @Test
    fun aZwjSequenceIsOneClusterOfTwoCells() {
        val family = "👨‍👩‍👧"
        assertEquals(listOf(family), clusters(family))
        assertEquals(2, width(family))
        val fire = "❤️‍🔥"
        assertEquals(1, clusters(fire).size)
        assertEquals(2, width(fire))
    }

    @Test
    fun skinTonesJoinTheirEmoji() {
        val thumb = "👍🏽"
        assertEquals(listOf(thumb), clusters(thumb))
        assertEquals(2, width(thumb))
        assertEquals(4, width("👍🏽👍🏿"))
        assertEquals(2, clusters("👍🏽👍🏿").size)
    }

    @Test
    fun flagsArePairs() {
        assertEquals(listOf("🇳🇱", "🇩🇪"), clusters("🇳🇱🇩🇪"))
        assertEquals(4, width("🇳🇱🇩🇪"))
        // A lone regional indicator is a letter, one cell.
        assertEquals(1, width("🇳"))
    }

    @Test
    fun variationSelectorsChooseTheLook() {
        assertEquals(1, CellWidth.of("❤")) // text by default
        assertEquals(2, CellWidth.of("❤️")) // as emoji
        assertEquals(1, clusters("❤️").size)
        assertEquals(1, CellWidth.of("⌚︎")) // an emoji by default, asked for as text
        assertEquals(2, CellWidth.of("1️⃣")) // keycap
    }

    @Test
    fun combiningMarksJoinTheirLetter() {
        assertEquals(listOf("é"), clusters("é"))
        assertEquals(4, width("Café"))
        assertEquals(1, width("ǟ"))
        // A mark with nothing before it takes no cell.
        assertEquals(0, width("́"))
    }

    @Test
    fun zeroWidthAndControlTakeNoCell() {
        assertEquals(listOf("a", "​", "b"), clusters("a​b"))
        assertEquals(2, width("a​b"))
        assertEquals(0, width("\n"))
        assertEquals(listOf("\r\n"), clusters("\r\n"))
        assertEquals(0, width("\u0007"))
    }

    @Test
    fun boxDrawingAndBlocksAreOneCellEach() {
        val box = "─│┌┐└┘├┤┬┴┼═║╔╗╚╝╭╮╯╰━┃"
        assertEquals(box.length, width(box))
        val blocks = "█▀▄▌▐░▒▓▁▂▃▄▅▆▇▕▏"
        assertEquals(blocks.length, width(blocks))
    }
}
