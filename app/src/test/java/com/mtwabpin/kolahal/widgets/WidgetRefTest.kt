package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One widget of a script ([WidgetRef]), and the copy of the placed list that the database keeps ([PlacedIds]). */
class WidgetRefTest {
    /** The first widget's key is the script's name, so nothing stored for it before had to change. */
    @Test fun theFirstWidgetsKeyIsTheScriptsName() {
        assertEquals("weather", WidgetRef("weather").key)
        assertTrue(WidgetRef("weather").first)
        assertEquals("weather#2", WidgetRef("weather", "2").key)
        assertFalse(WidgetRef("weather", "2").first)
        assertEquals("Weather", WidgetRef("weather").titled("Weather"))
        assertEquals("Weather 2", WidgetRef("weather", "2").titled("Weather"))
    }

    @Test fun aKeyIsReadBack() {
        for (ref in listOf(WidgetRef("weather"), WidgetRef("weather", "2"), WidgetRef("a_b-c", "12"), WidgetRef("x", "abc12345"))) {
            assertEquals(ref, WidgetRef.parse(ref.key))
        }
        // Without an id the whole text is the name, as before: a name from an old version still reads.
        assertEquals(WidgetRef("Old Name"), WidgetRef.parse("Old Name"))
        assertNull(WidgetRef.parse(null))
        assertNull(WidgetRef.parse(""))
        // An id that can't be one, or no name before it.
        for (bad in listOf("weather#", "weather#Two", "weather#2 ", "weather#123456789", "weather#2#3", "#2", "Old Name#2", "a b#2")) {
            assertNull(bad, WidgetRef.parse(bad))
        }
    }

    /** Every key of a script: its name, or its name and the separator at the start. A longer name is another script. */
    @Test fun aKeyBelongsToOneScript() {
        assertEquals("weather#", WidgetRef.further("weather"))
        assertTrue(WidgetRef.isOf("weather", "weather"))
        assertTrue(WidgetRef.isOf("weather#2", "weather"))
        assertFalse(WidgetRef.isOf("weather2", "weather"))
        assertFalse(WidgetRef.isOf("weather_2#2", "weather"))
        assertFalse(WidgetRef.isOf("weathe", "weather"))
    }

    @Test fun theNextIdIsTheSmallestFreeOne() {
        assertEquals("2", WidgetRef.nextId(listOf("")))
        assertEquals("3", WidgetRef.nextId(listOf("", "2")))
        // An id that was given up is used again.
        assertEquals("2", WidgetRef.nextId(listOf("", "3", "4")))
        // The first widget may be gone while further ones stay.
        assertEquals("3", WidgetRef.nextId(listOf("2")))
        assertEquals("2", WidgetRef.nextId(emptyList()))
        // No more than six widgets of one script.
        assertEquals("6", WidgetRef.nextId(listOf("", "2", "3", "4", "5")))
        assertNull(WidgetRef.nextId(listOf("", "2", "3", "4", "5", "6")))
        assertNull(WidgetRef.nextId(listOf("2", "3", "4", "5", "6", "7")))
        assertEquals(6, WidgetRef.MAX_PER_SCRIPT)
    }

    private fun placed(source: String) = PlacedWidget(null, source, 0L, 96, 0)

    /** The ids by script name, from the placed list. Only a script's own widgets count. */
    @Test fun thePlacedListSaysWhichWidgetsAScriptHas() {
        val all = listOf(
            placed("script:weather"), placed("script:weather#2"), placed("status:weather"), placed("script:hn#3"),
            placed("builtin:todos"), placed("script:bad#No"), PlacedWidget(7, "com.a/.W", 0L, 96, 1),
        )
        assertEquals(mapOf("weather" to setOf("", "2"), "hn" to setOf("3")), PlacedIds.of(all))
        assertEquals(emptyMap<String, Set<String>>(), PlacedIds.of(emptyList()))
    }

    @Test fun aRowIsWrittenAndReadBack() {
        assertEquals("[\"\",\"2\",\"10\"]", PlacedIds.encode(setOf("10", "2", "")))
        for (ids in listOf(setOf(""), setOf("2"), setOf("", "2", "3"), emptySet())) assertEquals(ids, PlacedIds.parse(PlacedIds.encode(ids)))
        // No row, or a text that is not a list of ids: nothing is known.
        assertNull(PlacedIds.parse(null))
        for (bad in listOf("", "{}", "true", "[2]", "[\"Two\"]", "[\"2\", null]", "[")) assertNull(bad, PlacedIds.parse(bad))
    }

    /** With a row, the row decides. With none, the widget was placed before rows were kept: it is the first. */
    @Test fun aRowSaysWhetherAWidgetIsPlaced() {
        assertTrue(PlacedIds.holds(setOf("", "2"), ""))
        assertTrue(PlacedIds.holds(setOf("", "2"), "2"))
        assertFalse(PlacedIds.holds(setOf("2"), ""))
        assertFalse(PlacedIds.holds(setOf(""), "2"))
        assertFalse(PlacedIds.holds(emptySet(), ""))
        assertTrue(PlacedIds.holds(null, ""))
        assertFalse(PlacedIds.holds(null, "2"))
    }
}
