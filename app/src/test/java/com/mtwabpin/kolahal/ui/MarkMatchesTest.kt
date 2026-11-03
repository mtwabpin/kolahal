package com.mtwabpin.kolahal.ui

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

/** A log search marks each place its words are found in a line. */
class MarkMatchesTest {
    @Test fun eachPlaceIsMarkedInAnyCase() {
        val marked = markMatches(AnnotatedString("Tea, then tea again"), listOf("tea"))
        assertEquals(listOf(0 to 3, 10 to 13), marked.spanStyles.map { it.start to it.end })
    }

    /** An empty word marks nothing, rather than being found at the same place for ever. */
    @Test fun anEmptyWordMarksNothing() {
        assertEquals(0, markMatches(AnnotatedString("abc"), listOf("")).spanStyles.size)
        assertEquals(listOf(1 to 2), markMatches(AnnotatedString("abc"), listOf("", "b")).spanStyles.map { it.start to it.end })
    }
}
