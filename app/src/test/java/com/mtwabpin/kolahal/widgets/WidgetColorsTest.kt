package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.script.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A card's color chosen in Arrange widgets ([PlacedWidget.color]): kept per placed card beside its
 * look and shape, in the saved list and in backups, and drawn in place of the card's own
 * ([WidgetColors]).
 */
class WidgetColorsTest {
    private fun script(name: String, column: Int = 0, shape: WidgetShape? = null, color: ColorRole? = null) =
        PlacedWidget(
            null, WidgetSource.script(name), 0L, 88, 0,
            span = 6, column = column, topDp = 0, shape = shape, color = color
        )

    /** A chosen color is the line's last field, after the shape's, which stays empty when no shape was chosen. Neither: the line as it was. */
    @Test
    fun theSavedListKeepsTheColor() {
        val all = listOf(
            script("a", color = ColorRole.WARN),
            script("b", column = 6, shape = WidgetShape.SUN, color = ColorRole.LINK),
            script("c", column = 0).copy(topDp = 96, shape = WidgetShape.BURST),
            script("d", column = 6).copy(topDp = 96),
        )
        val text = WidgetCodec.encode(all)
        assertEquals(
            listOf(
                "-|script:a|0|88|0|panel|6|plain|0|0||warn",
                "-|script:b|0|88|0|panel|6|plain|6|0|sun|link",
                "-|script:c|0|88|0|panel|6|plain|0|96|burst",
                "-|script:d|0|88|0|panel|6|plain|6|96",
            ),
            text.lines()
        )
        assertEquals(all, WidgetCodec.decode(text))
        assertFalse(WidgetCodec.start(text).changed)
    }

    /** A line without a color, or with one this version doesn't know, has the card's own. An app's widget has none. */
    @Test
    fun oldOddAndAppLinesHaveNoColor() {
        assertNull(WidgetCodec.decode("-|script:a|0|88|0|panel|6|plain|0|0").single().color)
        assertNull(WidgetCodec.decode("-|script:a|0|88|0|panel|6|plain|0|0|sun").single().color)
        assertNull(WidgetCodec.decode("-|script:a|0|88|0|panel|6|plain|0|0||pink").single().color)
        assertEquals(
            ColorRole.GOOD,
            WidgetCodec.decode("-|builtin:battery|0|88|0|panel|6|plain|0|0||GOOD").single().color
        )
        assertNull(WidgetCodec.decode("5|com.a/.W|0|88|0|panel|6|plain|0|0||warn").single().color)
    }

    /** A backup carries the color where one was chosen, and an import reads it back as the saved list does. */
    @Test
    fun backupsCarryTheColor() {
        val all = listOf(script("a", color = ColorRole.BAD), script("b", column = 6))
        val exported = WidgetCodec.export(all)
        assertEquals("bad", exported[0]["color"])
        assertFalse(exported[1].containsKey("color"))
        assertEquals(all, WidgetCodec.fromExport(exported))
        @Suppress("UNCHECKED_CAST")
        val read = Json.parse(Json.stringify(exported)) as List<Map<String, Any?>>
        assertEquals(all, WidgetCodec.fromExport(read))
        val imported = WidgetCodec.fromExport(
            listOf(
                mapOf("provider" to "script:c", "color" to "pink"),
                mapOf("provider" to "script:d", "color" to 3),
                mapOf("provider" to "com.a/.W", "color" to "warn"),
                mapOf("provider" to "script:e"),
            )
        )
        assertEquals(List(4) { null }, imported.map { it.color })
    }

    /**
     * The chosen color wins over the card's own, also over a color the script sets by state. Nothing
     * chosen: the card's own. The battery keeps its warning color while it has one.
     */
    @Test
    fun theChosenColorWinsExceptABatterysWarning() {
        assertEquals(ColorRole.LINK, WidgetColors.drawn(null, ColorRole.LINK))
        assertEquals(ColorRole.LINK, WidgetColors.drawn(ColorRole.BAD, ColorRole.LINK))
        assertEquals(ColorRole.BAD, WidgetColors.drawn(ColorRole.BAD, null))
        assertNull(WidgetColors.drawn(null, null))
        // The battery: bad when low, good on power, muted when unknown; else the chosen one.
        assertEquals(ColorRole.BAD, WidgetColors.drawn(ColorRole.BAD, ColorRole.LINK, keepsState = true))
        assertEquals(ColorRole.GOOD, WidgetColors.drawn(ColorRole.GOOD, ColorRole.LINK, keepsState = true))
        assertEquals(ColorRole.LINK, WidgetColors.drawn(null, ColorRole.LINK, keepsState = true))
    }

    /** Only the card-wide color changes: a part's own color stays, and a card that already has the color is the same. */
    @Test
    fun recoloringKeepsThePartsColors() {
        val gauge = WidgetSpec.Progress(
            WidgetBase(title = "Air", color = ColorRole.WARN), value = 118f, max = 300f, style = "gauge",
            zones = listOf(WidgetSpec.GaugeZone(0f, ColorRole.GOOD), WidgetSpec.GaugeZone(100f, ColorRole.BAD)),
        )
        val linked = WidgetColors.recolored(gauge, ColorRole.LINK) as WidgetSpec.Progress
        assertEquals(ColorRole.LINK, linked.base.color)
        assertEquals("Air", linked.base.title)
        assertEquals(gauge.zones, linked.zones)
        assertSame(gauge, WidgetColors.recolored(gauge, ColorRole.WARN))
        val rows = WidgetSpec.Rows(WidgetBase(), listOf(WidgetSpec.Row("Milk", color = ColorRole.GOOD)))
        val muted = WidgetColors.recolored(rows, ColorRole.MUTED) as WidgetSpec.Rows
        assertEquals(ColorRole.MUTED, muted.base.color)
        assertEquals(ColorRole.GOOD, muted.items.single().color)
        val sides = listOf(WidgetSpec.Side("Lisbon", color = ColorRole.BAD), WidgetSpec.Side("Paris"))
        val versus = WidgetSpec.Versus(WidgetBase(), sides, "2–1")
        assertEquals(sides, (WidgetColors.recolored(versus, ColorRole.TEXT) as WidgetSpec.Versus).sides)
    }

    /** Seven roles, the card's own first; info only when it is the card's own. */
    @Test
    fun theOwnColorIsOfferedFirst() {
        assertEquals(
            listOf("accent", "link", "good", "warn", "bad", "muted", "text"),
            WidgetColors.choices(null).map { it.role }
        )
        assertEquals(
            listOf("warn", "accent", "link", "good", "bad", "muted", "text"),
            WidgetColors.choices(ColorRole.WARN).map { it.role }
        )
        assertEquals(ColorRole.INFO, WidgetColors.choices(ColorRole.INFO).first())
        assertEquals(8, WidgetColors.choices(ColorRole.INFO).size)
        assertEquals(
            listOf("Accent", "Link", "Good", "Warning", "Bad", "Muted", "Text"),
            WidgetColors.OFFERED.map(WidgetColors::label)
        )
    }

    /** Every card Kolahal draws has the row; an app's widget, which draws itself, has none. */
    @Test
    fun everyCardKolahalDrawsHasTheRow() {
        assertTrue(WidgetColors.offeredFor(WidgetSource.script("weather")))
        listOf(
            WidgetSource.BATTERY, WidgetSource.SHORTCUTS, WidgetSource.MEDIA, WidgetSource.TODOS,
            WidgetSource.REMINDERS, WidgetSource.AGENDA, WidgetSource.NOTES
        ).forEach { assertTrue(it, WidgetColors.offeredFor(WidgetSource.builtin(it))) }
        assertFalse(WidgetColors.offeredFor("com.a/.W"))
        assertFalse(WidgetColors.offeredFor(null))
    }
}
