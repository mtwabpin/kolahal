package com.mtwabpin.kolahal.script

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Scripts' `json.decode` and `json.encode`, and what a Lua table becomes. Only what Android's
 * org.json and the one these tests run on do alike: not the order of an object's keys, nor the
 * type a decimal is read as.
 */
class JsonTest {
    private fun refused(text: String) = assertThrows(JSONException::class.java) { Json.decode(text) }.message

    /** A page or a word instead of JSON is an error that says what came, without quoting it. */
    @Test fun notJsonSaysWhatItGot() {
        assertEquals("Not JSON: an HTML or XML page", refused("<html>"))
        assertEquals("Not JSON: an HTML or XML page", refused("  <?xml version=\"1.0\"?><rss></rss>"))
        assertEquals("Not JSON: 5 characters of text", refused("hello"))
        assertEquals("Not JSON: 5 characters of text", refused(" hello\n"))
        assertEquals("Unexpected text after the JSON value", refused("{} x"))
        assertEquals("Unexpected text after the JSON value", refused("[1] [2]"))
    }

    /** A quoted string is JSON, and so is what follows a value only as spaces. */
    @Test fun aStringOrAValueWithSpacesAfterIt() {
        assertEquals("hello", Json.decode("\"hello\""))
        assertEquals(listOf(1, 2), Json.decode("[1, 2]\n"))
        assertEquals(mapOf("a" to null), Json.decode(" {\"a\": null} "))
    }

    /** Lua's numbers are all doubles: a whole one is written without its `.0`. Not a number is `null`, as in JavaScript. */
    @Test fun numbers() {
        assertEquals("1", Json.stringify(1.0))
        assertEquals("-3", Json.stringify(-3.0))
        assertEquals("2.5", Json.stringify(2.5))
        assertEquals("null", Json.stringify(Double.NaN))
        assertEquals("null", Json.stringify(Double.POSITIVE_INFINITY))
        assertEquals("[1,null]", Json.stringify(listOf(1.0, Double.NaN)))
    }

    @Test fun nestedValuesRoundTrip() {
        val value = mapOf(
            "name" to "Kolahal",
            "tags" to listOf("a", 1, true, null),
            "inner" to mapOf("n" to 2, "list" to listOf(mapOf("deep" to false))),
            "none" to null,
        )
        assertEquals(value, Json.decode(Json.stringify(value)))
        // One key each, so the order keys come in doesn't matter.
        assertEquals("""{"a":[1,"x",null,true,{"b":"c"}]}""", Json.stringify(mapOf("a" to listOf(1.0, "x", null, true, mapOf("b" to "c")))))
        assertEquals("\"say \\\"hi\\\"\"", Json.stringify("say \"hi\""))
    }

    /** A table numbered 1 to n is a list; any other keys make it an object, a whole number's key written without `.0`. */
    @Test fun luaTables() {
        assertEquals(listOf("a", "b"), Json.normalizeLua(mapOf(1.0 to "a", 2.0 to "b")))
        assertEquals(listOf("a", "b"), Json.normalizeLua(mapOf(2.0 to "b", 1.0 to "a")))
        assertEquals(mapOf("1" to "a", "3" to "b"), Json.normalizeLua(mapOf(1.0 to "a", 3.0 to "b")))
        assertEquals(mapOf("1" to "a", "x" to "b"), Json.normalizeLua(mapOf(1.0 to "a", "x" to "b")))
        assertEquals(listOf(listOf(7L), mapOf("k" to 1.5)), Json.normalizeLua(mapOf(1.0 to mapOf(1.0 to 7.0), 2.0 to mapOf("k" to 1.5))))
        assertEquals(7L, Json.normalizeLua(7.0))
        assertEquals(emptyMap<String, Any?>(), Json.normalizeLua(emptyMap<Any, Any>()))
    }

    /** A line's context holds text only: a table is written as JSON, a whole number without `.0`, and nil is left out. */
    @Test fun aContextIsText() {
        val flat = Json.toStringMap(
            mapOf("n" to 7.0, "x" to 1.5, "nested" to mapOf("a" to listOf(1.0, 2.0)), "s" to "text", "yes" to true, "gone" to null),
        )
        assertEquals(mapOf("n" to "7", "x" to "1.5", "nested" to """{"a":[1,2]}""", "s" to "text", "yes" to "true"), flat)
        assertEquals(emptyMap<String, String>(), Json.toStringMap(listOf("a")))
        assertEquals(emptyMap<String, String>(), Json.toStringMap(null))
    }

    /** A number as a script shows it: a whole one without its `.0`, as far as a double holds whole numbers exactly. */
    @Test fun aWholeNumberIsShownWithoutItsFraction() {
        assertEquals("3", Json.numberText(3.0))
        assertEquals("-3", Json.numberText(-3.0))
        assertEquals("0", Json.numberText(-0.0))
        assertEquals("2.5", Json.numberText(2.5))
        assertEquals("9000000000000000", Json.numberText(9.0e15))
        assertEquals("-9000000000000000", Json.numberText(-9.0e15))
        assertEquals("1.0E16", Json.numberText(1.0e16))
        assertEquals("NaN", Json.numberText(Double.NaN))
        assertEquals("Infinity", Json.numberText(Double.POSITIVE_INFINITY))
    }

    /** An empty Lua table has no keys to tell a list from a map, so it arrives as an empty map. It is an empty list too. */
    @Test fun anEmptyTableIsAnEmptyList() {
        assertEquals(listOf("a", 1), Json.listOrNull(listOf("a", 1)))
        assertEquals(emptyList<Any?>(), Json.listOrNull(emptyList<Any?>()))
        assertEquals(emptyList<Any?>(), Json.listOrNull(emptyMap<String, Any?>()))
        assertEquals(emptyList<Any?>(), Json.listOrNull(Json.normalizeLua(emptyMap<Any, Any>())))
        assertNull(Json.listOrNull(mapOf("a" to 1)))
        assertNull(Json.listOrNull("text"))
        assertNull(Json.listOrNull(null))
    }
}
