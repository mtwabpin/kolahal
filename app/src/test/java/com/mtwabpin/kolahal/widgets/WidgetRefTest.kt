package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** One widget of a script ([WidgetRef]), and the copy of the placed list that the database keeps ([PlacedIds]). */
class WidgetRefTest {
    /** The first widget's key is the script's name, so nothing stored for it before had to change. */
    @Test
    fun theFirstWidgetsKeyIsTheScriptsName() {
        assertEquals("weather", WidgetRef("weather").key)
        assertTrue(WidgetRef("weather").first)
        assertEquals("weather#2", WidgetRef("weather", "2").key)
        assertFalse(WidgetRef("weather", "2").first)
        assertEquals("Weather", WidgetRef("weather").titled("Weather"))
        assertEquals("Weather 2", WidgetRef("weather", "2").titled("Weather"))
    }

    @Test
    fun aKeyIsReadBack() {
        for (ref in listOf(
            WidgetRef("weather"),
            WidgetRef("weather", "2"),
            WidgetRef("a_b-c", "12"),
            WidgetRef("x", "abc12345")
        )) {
            assertEquals(ref, WidgetRef.parse(ref.key))
        }
        // Without an id the whole text is the name, as before: a name from an old version still reads.
        assertEquals(WidgetRef("Old Name"), WidgetRef.parse("Old Name"))
        assertNull(WidgetRef.parse(null))
        assertNull(WidgetRef.parse(""))
        // An id that can't be one, or no name before it.
        for (bad in listOf(
            "weather#",
            "weather#Two",
            "weather#2 ",
            "weather#123456789",
            "weather#2#3",
            "#2",
            "Old Name#2",
            "a b#2"
        )) {
            assertNull(bad, WidgetRef.parse(bad))
        }
    }

    /** Every key of a script: its name, or its name and the separator at the start. A longer name is another script. */
    @Test
    fun aKeyBelongsToOneScript() {
        assertEquals("weather#", WidgetRef.further("weather"))
        fun isOf(key: String, script: String) = WidgetRef.parse(key)?.script == script
        assertTrue(isOf("weather", "weather"))
        assertTrue(isOf("weather#2", "weather"))
        assertFalse(isOf("weather2", "weather"))
        assertFalse(isOf("weather_2#2", "weather"))
        assertFalse(isOf("weathe", "weather"))
    }

    @Test
    fun theNextIdIsOneTheScriptNeverHad() {
        assertEquals("2", WidgetRef.nextId(listOf(""), 0))
        assertEquals("3", WidgetRef.nextId(listOf("", "2"), 0))
        // An id that was given up is not used again: the number goes on from the highest given.
        assertEquals("5", WidgetRef.nextId(listOf("", "3", "4"), 0))
        assertEquals("4", WidgetRef.nextId(listOf(""), 3))
        assertEquals("10", WidgetRef.nextId(listOf("", "2"), 9))
        // The first widget may be gone while further ones stay.
        assertEquals("3", WidgetRef.nextId(listOf("2"), 0))
        assertEquals("2", WidgetRef.nextId(emptyList(), 0))
        // An id that is no number (a list written by hand) doesn't count.
        assertEquals("2", WidgetRef.nextId(listOf("", "abc"), 0))
        // The number must still be an id.
        assertEquals("99999999", WidgetRef.nextId(listOf(""), 99_999_998))
        assertNull(WidgetRef.nextId(listOf(""), 99_999_999))
        // No more than six widgets of one script.
        assertEquals("6", WidgetRef.nextId(listOf("", "2", "3", "4", "5"), 0))
        assertEquals("12", WidgetRef.nextId(listOf("", "2", "3", "4", "5"), 11))
        assertNull(WidgetRef.nextId(listOf("", "2", "3", "4", "5", "6"), 0))
        assertNull(WidgetRef.nextId(listOf("2", "3", "4", "5", "6", "7"), 0))
        assertEquals(6, WidgetRef.MAX_PER_SCRIPT)
        // The limit is on how many there are, the first widget included.
        assertTrue(WidgetRef.roomFor(listOf("", "2", "3", "4", "5")))
        assertFalse(WidgetRef.roomFor(listOf("", "2", "3", "4", "5", "6")))
        assertFalse(WidgetRef.roomFor(listOf("2", "3", "4", "5", "6", "7")))
    }

    /**
     * Two widgets of a habit placed, the second removed, one more placed: it is #3, not #2, so it
     * doesn't find the days the script stored for #2 (`days2`). As [WidgetHost] does it: every list
     * it saves counts in [GivenIds], and the next id goes on from there.
     */
    @Test
    fun aRemovedWidgetsIdDoesNotComeBack() {
        var all = listOf(placed("script:habit"))
        var given = GivenIds.noted(emptyMap(), all)
        fun placeAnother(): String {
            val id = WidgetRef.nextId(PlacedIds.of(all)["habit"].orEmpty(), given["habit"] ?: 0)!!
            all = all + placed(WidgetSource.script(WidgetRef("habit", id)))
            given = GivenIds.noted(given, all)
            return id
        }
        fun remove(id: String) {
            all = all.filterNot { it.source == WidgetSource.script(WidgetRef("habit", id)) }
            given = GivenIds.noted(given, all)
        }
        assertEquals("2", placeAnother())
        remove("2")
        assertEquals("3", placeAnother())
        assertEquals("4", placeAnother())
        remove("3")
        remove("4")
        assertEquals("5", placeAnother())
        assertEquals(mapOf("habit" to 5), given)
    }

    /**
     * The ids given are kept as a JSON object and come back; a backup's are added to these, the higher
     * one for each name; a rename takes them to the new name. A name or a number that can't be one is skipped.
     */
    @Test
    fun theIdsGivenAreKept() {
        val given = mapOf("habit" to 3, "football" to 2)
        assertEquals(given, GivenIds.parse(GivenIds.encode(given)))
        assertEquals(emptyMap<String, Int>(), GivenIds.parse(null))
        assertEquals(emptyMap<String, Int>(), GivenIds.parse("[1, 2]"))
        assertEquals(
            mapOf("habit" to 4),
            GivenIds.of(mapOf("habit" to 4.0, "a b" to 3, "cricket" to "5", "chess" to 1, "sun" to 2.5)),
        )
        assertEquals(mapOf("habit" to 5, "football" to 2, "chess" to 2), GivenIds.merged(given, mapOf("habit" to 5, "chess" to 2)))
        assertEquals(given, GivenIds.merged(given, mapOf("habit" to 2)))
        assertEquals(mapOf("habits" to 3, "football" to 2), GivenIds.renamed(given, "habit", "habits"))
        // Onto a name that had ids of its own: the higher stays.
        assertEquals(mapOf("football" to 3), GivenIds.renamed(given, "habit", "football"))
        assertEquals(given, GivenIds.renamed(given, "sun", "moon"))
        // A widget that came in another way counts as given, a first widget and an id that is no number don't.
        assertEquals(
            mapOf("habit" to 7, "football" to 2, "sun" to 4),
            GivenIds.noted(given, listOf(placed("script:habit#7"), placed("script:sun#4"), placed("script:moon"), placed("script:sky#ab"))),
        )
    }

    private fun placed(source: String) = PlacedWidget(null, source, 0L, 96, 0)

    /** The ids by script name, from the placed list. Only a script's own widgets count. */
    @Test
    fun thePlacedListSaysWhichWidgetsAScriptHas() {
        val all = listOf(
            placed("script:weather"),
            placed("script:weather#2"),
            placed("script:hn#3"),
            placed("builtin:todos"),
            placed("script:bad#No"),
            PlacedWidget(7, "com.a/.W", 0L, 96, 1),
        )
        assertEquals(mapOf("weather" to setOf("", "2"), "hn" to setOf("3")), PlacedIds.of(all))
        assertEquals(emptyMap<String, Set<String>>(), PlacedIds.of(emptyList()))
    }

    @Test
    fun aRowIsWrittenAndReadBack() {
        assertEquals("[\"\",\"2\",\"10\"]", PlacedIds.encode(setOf("10", "2", "")))
        for (ids in listOf(
            setOf(""),
            setOf("2"),
            setOf("", "2", "3"),
            emptySet()
        )) assertEquals(ids, PlacedIds.parse(PlacedIds.encode(ids)))
        // No row, or a text that is not a list of ids: nothing is known.
        assertNull(PlacedIds.parse(null))
        for (bad in listOf("", "{}", "true", "[2]", "[\"Two\"]", "[\"2\", null]", "[")) assertNull(
            bad,
            PlacedIds.parse(bad)
        )
    }

    /** With a row, the row decides. With none, the widget was placed before rows were kept: it is the first. */
    @Test
    fun aRowSaysWhetherAWidgetIsPlaced() {
        assertTrue(PlacedIds.holds(setOf("", "2"), ""))
        assertTrue(PlacedIds.holds(setOf("", "2"), "2"))
        assertFalse(PlacedIds.holds(setOf("2"), ""))
        assertFalse(PlacedIds.holds(setOf(""), "2"))
        assertFalse(PlacedIds.holds(emptySet(), ""))
        assertTrue(PlacedIds.holds(null, ""))
        assertFalse(PlacedIds.holds(null, "2"))
    }
}
