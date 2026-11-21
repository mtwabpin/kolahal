package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.script.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A row's range: a bar from its low to its high, read from a script and kept with the card's content. */
class ListRangeTest {
    private fun parse(raw: Map<String, Any?>) =
        WidgetParser.parse(raw, WidgetKind.LIST, { it }, { it }) as WidgetSpec.Rows

    private fun row(range: Any?) = mapOf("title" to "Sat", "trailing" to "22–31°", "range" to range)

    private fun range(vararg fields: Pair<String, Any?>): WidgetSpec.Range? =
        parse(mapOf("items" to listOf(row(mapOf(*fields))))).items.single().range

    private fun refused(range: Any?): String? =
        runCatching { parse(mapOf("items" to listOf(row(range)))) }.exceptionOrNull()?.message

    @Test
    fun aRowCanHaveARange() {
        assertEquals(WidgetSpec.Range(22f, 31f), range("low" to 22, "high" to 31))
        // Lua's numbers are doubles, JavaScript's whole ones longs.
        assertEquals(
            WidgetSpec.Range(94_100f, 97_900f, 97_420f),
            range("low" to 94_100.0, "high" to 97_900L, "value" to 97_420)
        )
        assertEquals(WidgetSpec.Range(-3.5f, 4f), range("low" to -3.5, "high" to 4))
        // A range of one number is a range all the same: the card draws it as a dot.
        assertEquals(WidgetSpec.Range(25f, 25f), range("low" to 25, "high" to 25))
        // Without one, or with nil, the row has none.
        assertNull(parse(mapOf("items" to listOf(mapOf("title" to "Sat")))).items.single().range)
        assertNull(parse(mapOf("items" to listOf(row(null)))).items.single().range)
    }

    @Test
    fun aValueOutsideTheRangeIsHeldAtItsNearerEnd() {
        assertEquals(31f, range("low" to 22, "high" to 31, "value" to 40)?.value)
        assertEquals(22f, range("low" to 22, "high" to 31, "value" to -5)?.value)
        assertEquals(27f, range("low" to 22, "high" to 31, "value" to 27)?.value)
    }

    @Test
    fun aWrongRangeIsAnError() {
        assertEquals(
            "widget: item 1 of items: range: low must not be above high",
            refused(mapOf("low" to 31, "high" to 22))
        )
        assertEquals(
            "widget: item 1 of items: range: value must be a number, not 'now'",
            refused(mapOf("low" to 22, "high" to 31, "value" to "now"))
        )
        assertEquals(
            "widget: item 1 of items: range: low must be a number, not '22°'",
            refused(mapOf("low" to "22°", "high" to 31))
        )
        assertEquals(
            "widget: item 1 of items: range: high must be a number, not true",
            refused(mapOf("low" to 22, "high" to true))
        )
        assertTrue(
            refused(
                mapOf(
                    "low" to 22,
                    "high" to Double.NaN
                )
            )!!.startsWith("widget: item 1 of items: range: high must be a number, not ")
        )
        assertEquals(
            "widget: item 1 of items: range: low is needed: the lowest number of the range",
            refused(mapOf("high" to 31))
        )
        assertEquals(
            "widget: item 1 of items: range: high is needed: the highest number of the range",
            refused(mapOf("low" to 22))
        )
        assertEquals(
            "widget: item 1 of items: range: no field 'mid'; use low, high, value",
            refused(mapOf("low" to 22, "high" to 31, "mid" to 26))
        )
        assertEquals(
            "widget: item 1 of items: range must be { low, high, value }, not '22–31'",
            refused("22–31")
        )
        // A list such as Lua's { 22, 31 } has no names: it is no range.
        assertEquals(
            "widget: item 1 of items: range must be { low, high, value }, not a list",
            refused(listOf(22, 31))
        )
    }

    /** The range is kept with the card's content and read back the same, its value too. */
    @Test
    fun theRangeIsStored() {
        val spec = parse(
            mapOf(
                "items" to listOf(
                    row(mapOf("low" to 22, "high" to 31)),
                    mapOf(
                        "title" to "BTC",
                        "range" to mapOf("low" to 94_100.5, "high" to 97_900, "value" to 97_420.25)
                    ),
                    mapOf("title" to "No range"),
                ),
            ),
        )
        assertEquals(spec, WidgetJson.decode(WidgetJson.encode(spec)))
        assertEquals(WidgetSpec.Range(94_100.5f, 97_900f, 97_420.25f), spec.items[1].range)
        // Stored under the script's names. A range without a value has none.
        val stored = (Json.parse(WidgetJson.encode(spec)) as Map<*, *>)["items"] as List<*>
        fun keys(i: Int) = ((stored[i] as Map<*, *>)["range"] as? Map<*, *>)?.keys
        assertEquals(setOf("low", "high"), keys(0))
        assertEquals(setOf("low", "high", "value"), keys(1))
        assertNull(keys(2))
    }

    /** What is stored is checked again as it is read: a low above its high reads as nothing, and a value is brought into its range. */
    @Test
    fun aStoredRangeIsCheckedAgain() {
        fun stored(range: String) =
            WidgetJson.decode("""{"v":1,"kind":"list","items":[{"title":"Sat","range":$range}]}""")

        fun rangeOf(range: String) = (stored(range) as WidgetSpec.Rows).items.single().range
        assertEquals(WidgetSpec.Range(22f, 31f), rangeOf("""{"low":22,"high":31}"""))
        assertEquals(31f, rangeOf("""{"low":22,"high":31,"value":99}""")?.value)
        assertNull(stored("""{"low":31,"high":22}"""))
        assertNull(stored("""{"high":31}"""))
        assertNull(stored("""{"low":"22","high":31}"""))
        assertNull(stored("""[22,31]"""))
    }
}
