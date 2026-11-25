package com.mtwabpin.kolahal.ui.shaders

import org.junit.Assert.assertEquals
import org.junit.Test

/** How a parameter's choice of words is laid out in a shader's details ([choiceRows]): how many buttons each row has. */
class ParamChoiceRowsTest {
    private val gap = 2f

    /** The width of a parameter's buttons on a phone, and a button that holds the widest word at the usual font size. */
    private val width = 347f
    private val need = 56f

    /** Up to four words are one row, whatever the font size: as today. */
    @Test
    fun upToFourWordsAreOneRow() {
        for (count in 2..4) assertEquals(listOf(count), choiceRows(count, width, need, gap))
        assertEquals(listOf(4), choiceRows(4, width, needDp = 200f, gapDp = gap))
    }

    /** Five to eight words are two rows, as even as they go, the longer first. */
    @Test
    fun fiveToEightAreTwoRows() {
        assertEquals(listOf(3, 2), choiceRows(5, width, need, gap))
        assertEquals(listOf(3, 3), choiceRows(6, width, need, gap))
        assertEquals(listOf(4, 3), choiceRows(7, width, need, gap))
        assertEquals(listOf(4, 4), choiceRows(8, width, need, gap))
        // Short words that would all fit in one row still are two.
        assertEquals(listOf(3, 3), choiceRows(6, width, needDp = 30f, gapDp = gap))
    }

    /** At a large font, where a word can't fit four to a row even at its smallest, the rows break further, as even as they go. */
    @Test
    fun aLargeFontBreaksTheRowsFurther() {
        // Three fit in a row.
        assertEquals(listOf(3, 3, 2), choiceRows(8, width, needDp = 100f, gapDp = gap))
        assertEquals(listOf(3, 2, 2), choiceRows(7, width, needDp = 100f, gapDp = gap))
        assertEquals(listOf(3, 2), choiceRows(5, width, needDp = 100f, gapDp = gap))
        // Two fit in a row.
        assertEquals(listOf(2, 2, 2, 2), choiceRows(8, width, needDp = 150f, gapDp = gap))
        assertEquals(listOf(2, 2, 1), choiceRows(5, width, needDp = 150f, gapDp = gap))
    }
}
