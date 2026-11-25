package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.StyledText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * The progress styles that are more than a ring or bars, as `widget.show` reads them ([WidgetParser])
 * and as they are stored ([WidgetJson]): a gauge, nested rings and stages.
 */
class ProgressStylesParserTest {
    private fun progress(vararg fields: Pair<String, Any?>) =
        WidgetParser.parse(mapOf(*fields), WidgetKind.PROGRESS, { it }, { it }) as WidgetSpec.Progress

    private fun no(vararg fields: Pair<String, Any?>): String? =
        assertThrows(IllegalArgumentException::class.java) {
            WidgetParser.parse(mapOf(*fields), WidgetKind.PROGRESS, { it }, { it })
        }.message

    /** What is stored comes back the same. */
    private fun storedAndRead(spec: WidgetSpec.Progress) =
        assertEquals(spec, WidgetJson.decode(WidgetJson.encode(spec)))

    private val air = listOf(
        mapOf("from" to 0, "color" to "good"),
        mapOf("from" to 51, "color" to "warn"),
        mapOf("from" to 151, "color" to "bad"),
    )

    // ---- gauge -------------------------------------------------------------------------------

    @Test
    fun aGaugeWithZones() {
        assertEquals(
            WidgetSpec.Progress(
                WidgetBase(title = "Air · Nairobi"),
                value = 118f,
                max = 300f,
                label = "Unhealthy for some",
                style = "gauge",
                unit = "US AQI",
                zones = listOf(
                    WidgetSpec.GaugeZone(0f, StyledText.ColorRole.GOOD),
                    WidgetSpec.GaugeZone(51f, StyledText.ColorRole.WARN),
                    WidgetSpec.GaugeZone(151f, StyledText.ColorRole.BAD),
                ),
            ),
            progress(
                "title" to "Air · Nairobi",
                "value" to 118,
                "max" to 300,
                "label" to "unhealthy for some",
                "style" to "gauge",
                "unit" to "US AQI",
                "zones" to air,
            ),
        )
    }

    /** A gauge's value is held between its min and its max; a zone's start too. */
    @Test
    fun aGaugeHoldsItsValueOnItsScale() {
        val wind = progress("value" to -12, "min" to -10, "max" to 40, "style" to "gauge")
        assertEquals(-10f, wind.value)
        assertEquals(-10f, wind.min)
        assertEquals(40f, progress("value" to 99, "min" to -10, "max" to 40, "style" to "gauge").value)
        val held = progress(
            "value" to 5, "max" to 10, "style" to "gauge",
            "zones" to listOf(mapOf("from" to -3, "color" to "good"), mapOf("from" to 30, "color" to "bad")),
        )
        assertEquals(listOf(0f, 10f), held.zones.map { it.from })
        // A gauge keeps its first 5 zones.
        val many = progress(
            "value" to 1, "max" to 10, "style" to "gauge",
            "zones" to (0..7).map { mapOf("from" to it, "color" to "info") }
        )
        assertEquals(5, many.zones.size)
    }

    @Test
    fun whatAGaugeRefuses() {
        assertEquals("widget: min goes with style 'gauge'", no("value" to 1, "min" to 0))
        assertEquals(
            "widget: zones go with style 'gauge'",
            no("value" to 1, "zones" to air)
        )
        assertEquals(
            "widget: min must be below max",
            no("value" to 1, "min" to 5, "max" to 5, "style" to "gauge")
        )
        // Below as numbers, the same as floats: the stored card would read back as nothing.
        assertEquals(
            "widget: min must be below max",
            no("value" to 1, "min" to 0.99999999, "max" to 1, "style" to "gauge")
        )
        // A unit is written only by a gauge, so on any other style it is an error, as min is.
        assertEquals("widget: unit goes with style 'gauge'", no("value" to 1, "unit" to "km/h"))
        assertEquals("widget: unit goes with style 'gauge'", no("value" to 1, "style" to "ring", "unit" to "km/h"))
        assertEquals(
            "widget: style 'gauge' goes with value, not with items",
            no("style" to "gauge", "items" to listOf(mapOf("value" to 1)))
        )
        assertEquals(
            "widget: item 2 of zones: from must be above 50, where the zone before starts",
            no(
                "value" to 1, "max" to 100, "style" to "gauge",
                "zones" to listOf(mapOf("from" to 50, "color" to "good"), mapOf("from" to 50, "color" to "bad"))
            )
        )
        assertEquals(
            "widget: item 1 of zones: from is needed: the number where this zone starts",
            no("value" to 1, "style" to "gauge", "zones" to listOf(mapOf("color" to "good")))
        )
        assertEquals(
            "widget: item 1 of zones: color is needed: the zone's role, such as good, warn or bad",
            no("value" to 1, "style" to "gauge", "zones" to listOf(mapOf("from" to 0)))
        )
        assertEquals(
            "widget: item 1 of zones: no color 'red'; use accent, link, info, muted, text, good, warn, bad",
            no("value" to 1, "style" to "gauge", "zones" to listOf(mapOf("from" to 0, "color" to "red")))
        )
    }

    // ---- rings -------------------------------------------------------------------------------

    @Test
    fun ringsAreItemsWithTheirUnitAndTheOneThatMoves() {
        val spec = progress(
            "style" to "rings",
            "items" to listOf(
                mapOf("label" to "Steps", "value" to 6240, "max" to 8000, "text" to "6,240", "moving" to true),
                mapOf("label" to "Water", "value" to 5, "max" to 8, "unit" to "glasses", "color" to "link"),
            ),
        )
        assertEquals("rings", spec.style)
        assertEquals(
            listOf(
                WidgetSpec.Bar("Steps", 6240f, 8000f, "6,240", null, moving = true),
                WidgetSpec.Bar("Water", 5f, 8f, null, StyledText.ColorRole.LINK, unit = "glasses"),
            ),
            spec.items,
        )
        assertEquals(
            "widget: style 'rings' needs items: up to 4 rings, each { label, value, max, text, color, unit, moving }",
            no("value" to 1, "style" to "rings")
        )
    }

    /** `ring` is one letter from `rings`: with items it is an error, not bars. */
    @Test
    fun ringWithItemsNamesRings() {
        assertEquals(
            "widget: style 'ring' goes with value, not with items: 'rings' draws items as rings",
            no("style" to "ring", "items" to listOf(mapOf("value" to 1)))
        )
    }

    /** An item's unit and moving are drawn only by rings: on bars they are an error. */
    @Test
    fun anItemsUnitAndMovingGoWithRings() {
        assertEquals(
            "widget: item 2 of items: unit goes with style 'rings'",
            no("items" to listOf(mapOf("value" to 1), mapOf("value" to 5, "unit" to "glasses")))
        )
        assertEquals(
            "widget: item 1 of items: moving goes with style 'rings'",
            no("style" to "bar", "items" to listOf(mapOf("value" to 1, "moving" to true)))
        )
        // The card's own moving makes every bar a wave: that one goes with bars too.
        assertEquals(true, progress("moving" to true, "items" to listOf(mapOf("value" to 1))).moving)
    }

    // ---- stages ------------------------------------------------------------------------------

    @Test
    fun stagesWithTheOneItIsAt() {
        val spec = progress(
            "title" to "Parcel",
            "stages" to listOf("ordered", "Shipped", "On its way", "Delivered"),
            "stage" to 3,
            "value" to 0.4,
            "label" to "arrives Thursday",
            "moving" to true,
        )
        assertEquals(
            WidgetSpec.Progress(
                WidgetBase(title = "Parcel"),
                value = 0.4f,
                label = "Arrives Thursday",
                moving = true,
                stages = listOf("Ordered", "Shipped", "On its way", "Delivered"),
                stage = 3,
            ),
            spec,
        )
        // Stages need no value: the way to the next one is then just begun.
        assertEquals(0f, progress("stages" to listOf("A", "B"), "stage" to 1).value)
        // Up to 6, each cut at 24 characters.
        val long = progress("stages" to (1..6).map { "Stage $it of a long and winding road" }, "stage" to 6)
        assertEquals(6, long.stages.size)
        assertEquals(24, long.stages[0].length)
    }

    @Test
    fun whatStagesRefuse() {
        assertEquals(
            "widget: stages needs 2 names at least, the first stage first",
            no("stages" to listOf("Only"), "stage" to 1)
        )
        assertEquals(
            "widget: stages needs 2 names at least, the first stage first",
            no("stages" to emptyList<String>())
        )
        assertEquals(
            "widget: give items or stages, not both",
            no("stages" to listOf("A", "B"), "stage" to 1, "items" to listOf(mapOf("value" to 1)))
        )
        assertEquals(
            "widget: style goes with value or items, not with stages",
            no("stages" to listOf("A", "B"), "stage" to 1, "style" to "bar")
        )
        assertEquals(
            "widget: stage is needed: the one it is at, from 1 to 3",
            no("stages" to listOf("A", "B", "C"))
        )
        assertEquals(
            "widget: stage must be a whole number from 1 to 3, not 4",
            no("stages" to listOf("A", "B", "C"), "stage" to 4)
        )
        assertEquals(
            "widget: stage must be a whole number from 1 to 3, not 1.5",
            no("stages" to listOf("A", "B", "C"), "stage" to 1.5)
        )
        assertEquals(
            "widget: stage goes with stages: the names of the stages",
            no("value" to 1, "stage" to 2)
        )
        assertEquals(
            "widget: stages must be a list of texts, not 'A, B'",
            no("stages" to "A, B", "stage" to 1)
        )
        // More than 6 is an error, not a cut: the last stage is the goal, such as Delivered.
        assertEquals(
            "widget: stages has 7 names: give 6 at most",
            no("stages" to (1..7).map { "Stage $it" }, "stage" to 1)
        )
    }

    // ---- stored ------------------------------------------------------------------------------

    @Test
    fun theStylesAreStoredAndReadBack() {
        storedAndRead(
            progress(
                "value" to 118, "max" to 300, "min" to 10, "style" to "gauge", "unit" to "US AQI",
                "label" to "Unhealthy for some", "zones" to air,
            )
        )
        storedAndRead(
            progress(
                "style" to "rings", "moving" to true,
                "items" to listOf(
                    mapOf("label" to "Steps", "value" to 6240, "max" to 8000, "moving" to true),
                    mapOf("label" to "Sleep", "value" to 7.2, "max" to 8, "text" to "7:10", "unit" to "h"),
                ),
            )
        )
        storedAndRead(
            progress(
                "stages" to listOf("Ordered", "Shipped", "On its way", "Delivered"),
                "stage" to 4, "label" to "Left at the door",
            )
        )
    }

    /** A stored description whose stage is not one of its stages, or with a single stage, reads as nothing. */
    @Test
    fun oddStoredStagesReadAsNothing() {
        assertNull(WidgetJson.decode("""{"v":1,"kind":"progress","value":0,"stages":["A","B"],"stage":3}"""))
        assertNull(WidgetJson.decode("""{"v":1,"kind":"progress","value":0,"stages":["A","B"]}"""))
        assertNull(WidgetJson.decode("""{"v":1,"kind":"progress","value":0,"stages":["A"],"stage":1}"""))
        assertNull(WidgetJson.decode("""{"v":1,"kind":"progress","value":1,"style":"gauge","zones":[{"from":0}]}"""))
        assertNull(WidgetJson.decode("""{"v":1,"kind":"progress","value":1,"min":2,"max":1}"""))
    }
}
