package com.mtwabpin.kolahal.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

/** The rows of a toolbar's choices in Arrange widgets ([choicesPerRow]): one row while every word fits, else rows of the same count. */
class ChoiceRowsTest {
    private val gap = 2f

    /** Six looks in the toolbar's width at the usual font size: one row. */
    @Test
    fun oneRowWhileTheWidestWordFits() {
        assertEquals(6, choicesPerRow(6, widthDp = 379f, needDp = 40f, gapDp = gap))
        // Just fits: six buttons and five gaps.
        assertEquals(6, choicesPerRow(6, widthDp = 6 * 50f + 5 * gap, needDp = 50f, gapDp = gap))
        assertEquals(2, choicesPerRow(2, widthDp = 331f, needDp = 120f, gapDp = gap))
    }

    /** Where only five of six fit in a row, they are three and three, not five and one. */
    @Test
    fun rowsOfTheSameCount() {
        assertEquals(3, choicesPerRow(6, widthDp = 379f, needDp = 70f, gapDp = gap))
        assertEquals(3, choicesPerRow(6, widthDp = 6 * 50f + 5 * gap - 1f, needDp = 50f, gapDp = gap))
        // Four fit: still two rows of three.
        assertEquals(3, choicesPerRow(6, widthDp = 331f, needDp = 80f, gapDp = gap))
        // Two fit: three rows of two.
        assertEquals(2, choicesPerRow(6, widthDp = 331f, needDp = 120f, gapDp = gap))
    }

    /** A count that doesn't divide: the rows are as even as they can be, the last one shorter. */
    @Test
    fun aCountThatDoesNotDivide() {
        assertEquals(3, choicesPerRow(5, widthDp = 331f, needDp = 80f, gapDp = gap))
        assertEquals(4, choicesPerRow(7, widthDp = 379f, needDp = 70f, gapDp = gap))
    }

    /** A word wider than the space has a row to itself, and one choice or none is one row. */
    @Test
    fun atLeastOneToARow() {
        assertEquals(1, choicesPerRow(3, widthDp = 100f, needDp = 400f, gapDp = gap))
        assertEquals(1, choicesPerRow(1, widthDp = 100f, needDp = 40f, gapDp = gap))
        assertEquals(1, choicesPerRow(0, widthDp = 100f, needDp = 40f, gapDp = gap))
    }
}
