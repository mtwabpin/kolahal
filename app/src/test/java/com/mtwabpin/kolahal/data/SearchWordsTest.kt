package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the search fields of the pages find their rows: every word typed must be found. */
class SearchWordsTest {
    private val weather = listOf("weather", "Weather", "Current weather: weather [city]")

    private fun finds(query: String, texts: List<String> = weather) = SearchWords.allIn(SearchWords.of(query), texts)

    @Test fun blanksSplitTheWords() {
        assertEquals(listOf("hacker", "news"), SearchWords.of("hacker news"))
        assertEquals(listOf("hacker", "news"), SearchWords.of("  hacker \t news\n"))
        assertEquals(listOf("3-day"), SearchWords.of("3-day"))
    }

    @Test fun aBlankSearchHasNoWords() {
        assertEquals(emptyList<String>(), SearchWords.of(""))
        assertEquals(emptyList<String>(), SearchWords.of(" \t "))
    }

    @Test fun noWordsFindEveryRow() {
        assertTrue(finds(""))
        assertTrue(finds("   "))
        assertTrue(finds("", emptyList()))
    }

    @Test fun aWordCanBeAPartOfALongerOne() {
        assertTrue(finds("wea"))
        assertTrue(finds("city"))
        assertFalse(finds("rain"))
    }

    @Test fun upperAndLowerCaseAreTheSame() {
        assertTrue(finds("WEATHER"))
        assertTrue(finds("current CITY"))
        assertTrue(finds("hn", listOf("HN")))
    }

    /** Each word can be in another text, but every word must be in one of them. */
    @Test fun everyWordMustBeFound() {
        assertTrue(finds("current weather"))
        assertTrue(finds("hacker ann", listOf("hn", "Hacker News", "ann")))
        assertFalse(finds("weather rain"))
        assertFalse(finds("weather", emptyList()))
    }

    /** A word is looked for in one text at a time: the end of one and the start of the next don't make a word. */
    @Test fun aWordDoesNotSpanTwoTexts() {
        assertFalse(finds("weatherweather", listOf("weather", "weather")))
    }
}
