package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.script.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Shape look's shape of a card ([WidgetShape]): kept per placed card beside its look, in the
 * saved list and in backups, and named by a script as the card's own (`shape`).
 */
class WidgetShapeTest {
    private fun script(name: String, shape: WidgetShape? = null) =
        PlacedWidget(null, WidgetSource.script(name), 0L, 88, 0, span = 6, column = 0, topDp = 0, shape = shape)

    @Test
    fun aShapeIsNamedByItsIdInAnyCase() {
        assertEquals(
            listOf(
                "cookie9", "cookie12", "clover4", "clover8", "sun", "burst", "circle", "cookie4",
                "cookie6", "gem", "pixel", "slanted", "arch", "ghost", "pill"
            ),
            WidgetShape.ids
        )
        // Every id is a short lowercase word, as a script writes it; every short name fits under a shape.
        WidgetShape.entries.forEach {
            assertTrue(it.id, Regex("[a-z][a-z0-9]*").matches(it.id))
            assertTrue(it.short, it.short.length <= 9)
        }
        WidgetShape.entries.forEach { assertEquals(it, WidgetShape.named(it.id)) }
        assertEquals(WidgetShape.CLOVER8, WidgetShape.named(" Clover8 "))
        assertNull(WidgetShape.named("star"))
        assertNull(WidgetShape.named(""))
        assertNull(WidgetShape.named(null))
    }

    /** The card's own shape comes first, so one tap gives it back, then the others it may take in their order. */
    @Test
    fun theOwnShapeIsOfferedFirst() {
        assertEquals(WidgetShape.entries, WidgetShape.choices(WidgetShape.COOKIE9))
        assertEquals(
            listOf(WidgetShape.CLOVER4) + (WidgetShape.entries - WidgetShape.CLOVER4),
            WidgetShape.choices(WidgetShape.CLOVER4)
        )
        WidgetShape.entries.forEach { own ->
            val offered = WidgetShape.choices(own)
            assertEquals(15, offered.size)
            assertEquals(WidgetShape.entries.toSet(), offered.toSet())
        }
        // Of fewer: its own first, then those.
        val round = listOf(WidgetShape.CIRCLE, WidgetShape.GEM)
        assertEquals(listOf(WidgetShape.COOKIE9) + round, WidgetShape.choices(WidgetShape.COOKIE9, round))
    }

    /** A chosen shape is the line's last field. Without one the line is as it was, so older lines and tests read the same. */
    @Test
    fun theSavedListKeepsTheShape() {
        val all = listOf(script("a", WidgetShape.SUN), script("b").copy(column = 6))
        val text = WidgetCodec.encode(all)
        assertEquals(
            listOf("-|script:a|0|88|0|panel|6|plain|0|0|sun", "-|script:b|0|88|0|panel|6|plain|6|0"),
            text.lines()
        )
        assertEquals(all, WidgetCodec.decode(text))
        assertFalse(WidgetCodec.start(text).changed)
    }

    /** A line without a shape, or with one this version doesn't know, has the card's own. An app's widget has none. */
    @Test
    fun oldOddAndAppLinesHaveNoShape() {
        assertNull(WidgetCodec.decode("-|script:a|0|88|0|panel|6|shape|0|0").single().shape)
        assertNull(WidgetCodec.decode("-|script:a|0|88|0|panel|6").single().shape)
        assertNull(WidgetCodec.decode("-|script:a|0|88|0|panel|6|shape|0|0|heart").single().shape)
        assertEquals(
            WidgetShape.BURST,
            WidgetCodec.decode("-|builtin:notes|0|184|0|panel|6|shape|0|0|BURST").single().shape
        )
        assertNull(WidgetCodec.decode("5|com.a/.W|0|88|0|panel|6|shape|0|0|sun").single().shape)
    }

    /** A backup carries the shape where one was chosen, and an import reads it back as the saved list does. */
    @Test
    fun backupsCarryTheShape() {
        val all = listOf(script("a", WidgetShape.CLOVER8), script("b").copy(column = 6))
        val exported = WidgetCodec.export(all)
        assertEquals("clover8", exported[0]["shape"])
        assertFalse(exported[1].containsKey("shape"))
        assertEquals(all, WidgetCodec.fromExport(exported))
        // Through JSON, as a backup file is written and read.
        @Suppress("UNCHECKED_CAST")
        val read = Json.parse(Json.stringify(exported)) as List<Map<String, Any?>>
        assertEquals(all, WidgetCodec.fromExport(read))
        val imported = WidgetCodec.fromExport(
            listOf(
                mapOf("provider" to "script:c", "shape" to "star"),
                mapOf("provider" to "script:d", "shape" to 3),
                mapOf("provider" to "com.a/.W", "shape" to "sun"),
                mapOf("provider" to "script:e"),
            )
        )
        assertEquals(List(4) { null }, imported.map { it.shape })
    }

    /** A script names its card's own shape with `shape`, one of six words. */
    @Test
    fun aScriptNamesTheCardsShape() {
        fun parse(raw: Map<String, Any?>, kind: WidgetKind = WidgetKind.STAT) =
            WidgetParser.parse(raw, kind, { it }, { it })
        assertEquals(WidgetShape.CLOVER8, parse(mapOf("value" to "5", "shape" to "clover8")).base.shape)
        assertEquals(WidgetShape.SUN, parse(mapOf("value" to "5", "shape" to " Sun")).base.shape)
        assertNull(parse(mapOf("value" to "5")).base.shape)
        assertNull(parse(mapOf("value" to "5", "shape" to null)).base.shape)
        // Every kind takes it, as it takes a color: a card without one shape draws none.
        assertEquals(
            WidgetShape.BURST,
            parse(mapOf("text" to "Hi", "shape" to "burst"), WidgetKind.TEXT).base.shape
        )
        assertEquals(
            "widget: shape must be cookie9, cookie12, clover4, clover8, sun, burst, circle, cookie4, cookie6, gem, pixel, slanted, arch, ghost or pill, not 'star'",
            assertThrows(IllegalArgumentException::class.java) {
                parse(mapOf("value" to "5", "shape" to "star"))
            }.message
        )
        assertThrows(IllegalArgumentException::class.java) { parse(mapOf("value" to "5", "shape" to 9)) }
        assertTrue("shape" in WidgetParser.FIELDS)
    }

    /** The stored description keeps the shape; a stored word that is no shape makes it unreadable, as a wrong color does. */
    @Test
    fun theStoredDescriptionKeepsTheShape() {
        val spec = WidgetSpec.Stat(WidgetBase(title = "Air", shape = WidgetShape.COOKIE9), value = "118")
        val json = WidgetJson.encode(spec)
        assertEquals(spec, WidgetJson.decode(json))
        @Suppress("UNCHECKED_CAST")
        val map = (Json.parse(json) as Map<String, Any?>).toMutableMap()
        assertEquals("cookie9", map["shape"])
        map["shape"] = "star"
        assertNull(WidgetJson.decode(Json.stringify(map)))
        // Without a shape the stored text has no such field.
        assertFalse(WidgetJson.encode(WidgetSpec.Stat(WidgetBase(), value = "1")).contains("shape"))
    }
}
