package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A list's lead: the big line over its rows, read from a script and kept as the card's content. */
class ListLeadTest {
    private fun parse(raw: Map<String, Any?>) =
        WidgetParser.parse(raw, WidgetKind.LIST, { it }, { it }) as WidgetSpec.Rows

    private fun refused(raw: Map<String, Any?>): String? =
        runCatching { parse(raw) }.exceptionOrNull()?.message

    private val row = mapOf("title" to "Water plants", "trailing" to "07:00")

    @Test
    fun aListCanHaveALead() {
        val spec = parse(
            mapOf(
                "items" to listOf(row),
                "lead" to mapOf("value" to 42, "unit" to "min", "label" to "Call the bank · 22:22")
            )
        )
        assertEquals(WidgetSpec.Lead("42", "min", "Call the bank · 22:22"), spec.lead)
        assertNull(parse(mapOf("items" to listOf(row))).lead)
        // A lead with no rows is a card all the same.
        assertEquals(
            WidgetSpec.Lead("3"),
            parse(mapOf("items" to emptyList<Any>(), "lead" to mapOf("value" to "3"))).lead
        )
    }

    @Test
    fun aWrongLeadIsAnError() {
        assertEquals(
            "widget: lead: value is needed: the big value, as text or a number",
            refused(mapOf("items" to listOf(row), "lead" to mapOf("label" to "x")))
        )
        assertEquals(
            "widget: lead: no field 'colour'; use value, unit, label",
            refused(mapOf("items" to listOf(row), "lead" to mapOf("value" to 1, "colour" to "bad")))
        )
        assertEquals(
            "widget: lead must be { value, unit, label }, not '42'",
            refused(mapOf("items" to listOf(row), "lead" to "42"))
        )
    }

    /** The lead is kept with the card's content and read back the same. */
    @Test
    fun theLeadIsStored() {
        val spec = parse(
            mapOf(
                "items" to listOf(row),
                "lead" to mapOf("value" to "42", "unit" to "min", "label" to "Call the bank")
            )
        )
        assertEquals(spec, WidgetJson.decode(WidgetJson.encode(spec)))
    }
}
