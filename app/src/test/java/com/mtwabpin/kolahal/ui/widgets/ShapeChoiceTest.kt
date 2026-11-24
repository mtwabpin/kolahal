package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetShape
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which cards have a choice of shape in the Shape look ([hasShapeChoice]), the one they have of
 * their own ([ownShape]), and what the Shape row offers and keeps ([ShapeChoices]).
 */
class ShapeChoiceTest {
    private val base = WidgetBase()
    private val bar = WidgetSpec.Bar("Disk", 1f, 2f, null, null)

    /** A card with one shape for itself or its main value has the choice; one whose shape carries the value, or whose parts each take a shape, has none. */
    @Test
    fun theCardsWithOneShapeHaveTheChoice() {
        val with = listOf(
            WidgetSpec.Stat(base, value = "118"),
            WidgetSpec.Text(base, "Carry an umbrella"),
            WidgetSpec.Progress(base, value = 0.4f),
            WidgetSpec.Progress(base, value = 40f, max = 100f, style = "gauge"),
            WidgetSpec.Progress(base, value = 0.5f, stages = listOf("Packed", "Sent", "Here"), stage = 2),
            WidgetSpec.Progress(base, value = 0f, style = "rings", items = listOf(bar, bar)),
            WidgetSpec.Chart(base, listOf(3f, 5f, 4f)),
            // Dots for longer than a week are a calendar in a shape, not a flower.
            WidgetSpec.Chart(base, List(10) { 1f }, style = "dots"),
            WidgetSpec.Clock(base, listOf(WidgetSpec.Zone("Asia/Tokyo", "Tokyo"))),
            WidgetSpec.Image(base, "https://example.com/a.png", "Hills"),
        )
        with.forEach { assertTrue(it.toString(), hasShapeChoice(null, it)) }

        val without = listOf(
            WidgetSpec.Countdown(base, to = 2_000_000_000L),
            WidgetSpec.Chart(base, listOf(1f, 0f, 1f, 1f), style = "dots"),
            WidgetSpec.Progress(base, value = 0f, items = listOf(bar, bar)),
            WidgetSpec.Rows(base, listOf(WidgetSpec.Row("Milk"))),
            WidgetSpec.Buttons(base, listOf(WidgetSpec.Button("Torch", WidgetTap(args = "torch")))),
            WidgetSpec.Toggles(base, listOf(WidgetSpec.Toggle("Wi-Fi", true, WidgetTap(args = "wifi")))),
            WidgetSpec.Agenda(base, emptyList()),
            WidgetSpec.Sky(base, rises = 1_000L, sets = 40_000L),
        )
        without.forEach { assertFalse(it.toString(), hasShapeChoice(null, it)) }

        // The battery's shape shows its level.
        assertFalse(hasShapeChoice(WidgetKind.PROGRESS, WidgetSpec.Progress(base, value = 0.4f), battery = true))
    }

    /** Before a card has shown anything, its kind decides. */
    @Test
    fun withoutContentTheKindDecides() {
        val with = setOf(
            WidgetKind.STAT,
            WidgetKind.TEXT,
            WidgetKind.PROGRESS,
            WidgetKind.CHART,
            WidgetKind.CLOCK,
            WidgetKind.IMAGE
        )
        WidgetKind.entries.forEach { assertEquals(it.id, it in with, hasShapeChoice(it, null)) }
        assertFalse(hasShapeChoice(null, null))
    }

    /** The card's own shape is the one a square card of its kind draws, or the one its script names. */
    @Test
    fun eachCardHasAShapeOfItsOwn() {
        assertEquals(WidgetShape.COOKIE12, ownShape(null, WidgetSpec.Stat(base, value = "1")))
        assertEquals(WidgetShape.CLOVER4, ownShape(null, WidgetSpec.Progress(base, value = 0.4f)))
        assertEquals(
            WidgetShape.COOKIE12,
            ownShape(null, WidgetSpec.Progress(base, value = 0f, style = "rings", items = listOf(bar)))
        )
        assertEquals(WidgetShape.CLOVER4, ownShape(WidgetKind.TEXT, null))
        assertEquals(WidgetShape.COOKIE9, ownShape(WidgetKind.CLOCK, null))
        assertEquals(WidgetShape.COOKIE12, ownShape(WidgetKind.IMAGE, null))
        assertEquals(
            WidgetShape.SUN,
            ownShape(WidgetKind.STAT, WidgetSpec.Stat(WidgetBase(shape = WidgetShape.SUN), value = "1"))
        )
    }

    /** The first chip is the card's own: a tap on it keeps nothing, so the card follows its own shape. */
    @Test
    fun theRowKeepsOnlyAShapeThatIsNotTheCardsOwn() {
        val row = ShapeChoices(WidgetShape.CLOVER4, null)
        assertEquals(WidgetShape.CLOVER4, row.shapes.first())
        assertEquals(WidgetShape.CLOVER4, row.shown)
        assertNull(row.pick(WidgetShape.CLOVER4))
        assertEquals(WidgetShape.SUN, row.pick(WidgetShape.SUN))
        assertEquals(WidgetShape.SUN, row.copy(chosen = WidgetShape.SUN).shown)
    }

    @Test
    fun aPlacedWidgetsRow() {
        fun placed(source: String, shape: WidgetShape? = null) =
            PlacedWidget(null, source, 0L, 184, 0, shape = shape)
        // Notes is a text: its mark is a clover.
        assertEquals(
            ShapeChoices(WidgetShape.CLOVER4, WidgetShape.BURST),
            shapeChoicesFor(
                placed(WidgetSource.builtin(WidgetSource.NOTES), WidgetShape.BURST),
                WidgetKind.TEXT,
                null
            )
        )
        assertNull(shapeChoicesFor(placed(WidgetSource.builtin(WidgetSource.BATTERY)), WidgetKind.PROGRESS, null))
        assertNull(shapeChoicesFor(placed(WidgetSource.builtin(WidgetSource.TODOS)), WidgetKind.LIST, null))
        assertNull(shapeChoicesFor(placed("com.a/.W"), WidgetKind.STAT, null))
        // A script's card by what it shows: a chart of a habit's week has its flower.
        val weather = placed(WidgetSource.script("weather"))
        assertEquals(
            ShapeChoices(WidgetShape.COOKIE12, null),
            shapeChoicesFor(weather, WidgetKind.CHART, WidgetSpec.Chart(base, listOf(3f, 4f)))
        )
        assertNull(shapeChoicesFor(weather, WidgetKind.CHART, WidgetSpec.Chart(base, listOf(1f, 0f), style = "dots")))
    }

    /** Only the burst's points cut deep enough for the words to need more room. */
    @Test
    fun onlyTheBurstNeedsMoreRoom() {
        WidgetShape.entries.forEach {
            assertEquals(it.id, if (it == WidgetShape.BURST) 0.04f else 0f, extraInset(it), 0f)
        }
        assertEquals(0f, extraInset(null), 0f)
    }
}
