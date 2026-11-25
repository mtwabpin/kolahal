package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.sourceFile
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetJson
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetShape
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSizes
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
 * their own ([ownShape]), which shapes they may take ([shapesFor], [takenShape]), what the
 * Shape group of the settings page offers and keeps ([ShapeChoices]), and that the card draws the
 * shape the page and the toolbar show ([cardShapeOf]).
 */
class ShapeChoiceTest {
    private val base = WidgetBase()
    private val bar = WidgetSpec.Bar("Disk", 1f, 2f, null, null)

    /** The page the tests' cards are on, in dp: a 1×1 is 94 dp across, a 2×2 196. */
    private val pageDp = 400

    /** The own shape of a card of [kind] showing [spec] at [size] on [pageDp] ([ownShape]). */
    private fun own(kind: WidgetKind?, spec: WidgetSpec?, size: WidgetSize = WidgetSize.S2X2) =
        ownShape(kind, spec, WidgetLayout.widthDp(pageDp, size.span), WidgetSizes.heightDp(size, pageDp))

    /** A card of every kind, in each of its styles, one named by its script's `shape` among them. */
    private val everyCard: List<WidgetSpec> = listOf(
        WidgetSpec.Stat(base, value = "118"),
        WidgetSpec.Stat(WidgetBase(shape = WidgetShape.PILL), value = "118"),
        WidgetSpec.Text(base, "Carry an umbrella"),
        WidgetSpec.Rows(base, listOf(WidgetSpec.Row("Milk"))),
        WidgetSpec.Progress(base, value = 0.4f),
        WidgetSpec.Progress(base, value = 0.4f, style = "ring"),
        WidgetSpec.Progress(base, value = 0.4f, style = "bar"),
        WidgetSpec.Progress(base, value = 40f, max = 100f, style = "gauge"),
        WidgetSpec.Progress(WidgetBase(shape = WidgetShape.ARCH), value = 40f, max = 100f, style = "gauge"),
        WidgetSpec.Progress(base, value = 0.5f, stages = listOf("Packed", "Sent", "Here"), stage = 2),
        WidgetSpec.Progress(WidgetBase(shape = WidgetShape.GEM), value = 0.5f, stages = listOf("Packed", "Here"), stage = 1),
        WidgetSpec.Progress(base, value = 0f, style = "rings", items = listOf(bar, bar)),
        WidgetSpec.Progress(base, value = 0f, items = listOf(bar, bar)),
        WidgetSpec.Chart(base, listOf(3f, 5f, 4f)),
        WidgetSpec.Chart(base, listOf(3f, 5f, 4f), style = "bars"),
        WidgetSpec.Chart(base, List(10) { 1f }, style = "dots"),
        WidgetSpec.Chart(base, listOf(1f, 0f, 1f, 1f), style = "dots"),
        WidgetSpec.Countdown(base, to = 2_000_000_000L),
        WidgetSpec.Countdown(base, to = 2_000_000_000L, style = "days"),
        WidgetSpec.Countdown(base, to = 2_000_000_000L, style = "clock"),
        WidgetSpec.Countdown(base, to = 2_000_000_000L, style = "dots"),
        WidgetSpec.Countdown(base, to = 2_000_000_000L, style = "page"),
        WidgetSpec.Buttons(base, listOf(WidgetSpec.Button("Torch", WidgetTap(args = "torch")))),
        WidgetSpec.Toggles(base, listOf(WidgetSpec.Toggle("Wi-Fi", true, WidgetTap(args = "wifi")))),
        WidgetSpec.Clock(base, listOf(WidgetSpec.Zone("Asia/Tokyo", "Tokyo"))),
        WidgetSpec.Agenda(base, emptyList()),
        WidgetSpec.Image(base, "https://example.com/a.png", "Hills"),
        WidgetSpec.Versus(base, listOf(WidgetSpec.Side("Lisbon"), WidgetSpec.Side("Paris")), "2–1"),
        WidgetSpec.Sky(base, rises = 1_000L, sets = 40_000L),
        WidgetSpec.Sky(base, rises = 1_000L, sets = 40_000L, phase = 0.5f),
        WidgetSpec.Media(base, "Song", "Band", "com.a", true, 0L, 0L, 1f, 1000L),
    )

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

    /** The card's own shape is its kind's at its size, or the one its script names. */
    @Test
    fun eachCardHasAShapeOfItsOwn() {
        assertEquals(WidgetShape.COOKIE12, own(null, WidgetSpec.Stat(base, value = "1")))
        assertEquals(WidgetShape.CLOVER4, own(null, WidgetSpec.Progress(base, value = 0.4f)))
        assertEquals(
            WidgetShape.COOKIE12,
            own(null, WidgetSpec.Progress(base, value = 0f, style = "rings", items = listOf(bar)))
        )
        assertEquals(WidgetShape.CLOVER4, own(WidgetKind.TEXT, null))
        assertEquals(WidgetShape.COOKIE9, own(WidgetKind.CLOCK, null))
        assertEquals(WidgetShape.COOKIE12, own(WidgetKind.IMAGE, null))
        assertEquals(
            WidgetShape.SUN,
            own(WidgetKind.STAT, WidgetSpec.Stat(WidgetBase(shape = WidgetShape.SUN), value = "1"))
        )
        // A countdown's sun carries its count: a shape its script names is no choice of its own.
        assertEquals(
            WidgetShape.SUN,
            own(null, WidgetSpec.Countdown(WidgetBase(shape = WidgetShape.PILL), to = 2_000_000_000L))
        )
    }

    /**
     * Stages are the clover where the card is the shape, and a 9-sided cookie beside the words in
     * any other box. A picture is a clover in a 1-row card and a 12-sided cookie in the others.
     */
    @Test
    fun stagesAndAPictureHaveAnotherShapeBesideTheirWords() {
        val stages = WidgetSpec.Progress(base, value = 0.5f, stages = listOf("Packed", "Sent", "Here"), stage = 2)
        val picture = WidgetSpec.Image(base, "https://example.com/a.png", "Hills")
        assertEquals(WidgetShape.CLOVER4, own(null, stages, WidgetSize.S1X1))
        assertEquals(WidgetShape.CLOVER4, own(null, stages, WidgetSize.S2X2))
        listOf(WidgetSize.S2X1, WidgetSize.S4X1, WidgetSize.S4X2, WidgetSize.S4X4).forEach {
            assertEquals(it.id, if (it == WidgetSize.S4X4) WidgetShape.CLOVER4 else WidgetShape.COOKIE9, own(null, stages, it))
        }
        assertEquals(WidgetShape.CLOVER4, own(null, picture, WidgetSize.S2X1))
        assertEquals(WidgetShape.CLOVER4, own(null, picture, WidgetSize.S4X1))
        listOf(WidgetSize.S1X1, WidgetSize.S2X2, WidgetSize.S4X2, WidgetSize.S4X4).forEach {
            assertEquals(it.id, WidgetShape.COOKIE12, own(null, picture, it))
        }
    }

    /**
     * The settings page's first shape ("Its own"), the toolbar's Settings button and the card show
     * the same shape: for every kind, style and box, with no shape chosen and with each one chosen.
     */
    @Test
    fun thePageTheToolbarAndTheCardShowOneShape() {
        assertEquals(WidgetKind.entries.toSet(), everyCard.map(WidgetJson::kindOf).toSet())
        val pages = listOf(320, 400, 600)
        val heights = (WidgetSizes.MIN_HEIGHT_DP..640 step 8).toList()
        val chosen = listOf<WidgetShape?>(null) + WidgetShape.entries
        var compared = 0
        for (page in pages) for (span in WidgetSizes.MIN_SPAN..WidgetLayout.COLUMNS) for (heightDp in heights) {
            val widthDp = WidgetLayout.widthDp(page, span)
            for (spec in everyCard) for (shape in chosen) {
                val kind = WidgetJson.kindOf(spec)
                val w = PlacedWidget(
                    null, WidgetSource.script("cards"), 0L, heightDp, 0,
                    span = span, look = WidgetLook.SHAPE, shape = shape
                )
                val card = cardShapeOf(WidgetLook.SHAPE, spec, shape, battery = false, widthDp, heightDp)
                val choices = shapeChoicesFor(w, kind, spec, page)
                // The message is written only for a failure: a million of them would be slow.
                fun same(expected: WidgetShape?, actual: WidgetShape?) {
                    if (expected != actual) assertEquals("$spec, chosen $shape, $widthDp × $heightDp dp", expected, actual)
                }
                if (choices == null) {
                    same(ownShape(kind, spec, widthDp, heightDp), card)
                } else {
                    // The toolbar's Settings button and the chosen cell.
                    same(choices.shown, card)
                    // The "Its own" cell is what the card draws with nothing chosen.
                    same(cardShapeOf(WidgetLook.SHAPE, spec, null, battery = false, widthDp, heightDp), choices.own)
                    same(choices.own, choices.shapes.first())
                    compared++
                }
            }
        }
        assertTrue(compared > 0)
        // The battery's shape shows its level: no group, and the card keeps its own.
        val level = WidgetSpec.Progress(base, value = 0.4f)
        assertEquals(
            WidgetShape.CLOVER4,
            cardShapeOf(WidgetLook.SHAPE, level, WidgetShape.SUN, battery = true, 196, 196)
        )
        WidgetLook.entries.filter { it != WidgetLook.SHAPE }.forEach {
            assertNull(cardShapeOf(it, level, null, battery = false, 196, 196))
        }
    }

    /**
     * A body draws the card's shape as the frame resolved it ([cardShape] takes no shape), and only
     * the frame provides it: a body that named a shape of its own would split the card from its
     * settings page again.
     */
    @Test
    fun theBodiesDrawTheShapeTheFrameResolved() {
        val dir = sourceFile("src/main/java/com/mtwabpin/kolahal/ui/widgets")
        val files = dir.listFiles().orEmpty().filter { it.extension == "kt" }
        assertTrue(files.size > 40)
        val calls = Regex("""\bcardShape\(([^)]*)\)""")
        val named = files.flatMap { f ->
            calls.findAll(f.readText()).filter { it.groupValues[1].isNotBlank() }.map { "${f.name}: ${it.value}" }.toList()
        }
        assertEquals(emptyList<String>(), named)
        val provided = files.flatMap { f ->
            Regex("""LocalCardShape provides (\w+)""").findAll(f.readText()).map { "${f.name}: ${it.groupValues[1]}" }.toList()
        }
        assertEquals(listOf("ScriptWidgets.kt: shape"), provided)
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
                null,
                pageDp
            )
        )
        assertNull(shapeChoicesFor(placed(WidgetSource.builtin(WidgetSource.BATTERY)), WidgetKind.PROGRESS, null, pageDp))
        assertNull(shapeChoicesFor(placed(WidgetSource.builtin(WidgetSource.TODOS)), WidgetKind.LIST, null, pageDp))
        assertNull(shapeChoicesFor(placed("com.a/.W"), WidgetKind.STAT, null, pageDp))
        // A script's card by what it shows: a chart of a habit's week has its flower.
        val weather = placed(WidgetSource.script("weather"))
        assertEquals(
            ShapeChoices(WidgetShape.COOKIE12, null),
            shapeChoicesFor(weather, WidgetKind.CHART, WidgetSpec.Chart(base, listOf(3f, 4f)), pageDp)
        )
        assertNull(shapeChoicesFor(weather, WidgetKind.CHART, WidgetSpec.Chart(base, listOf(1f, 0f), style = "dots"), pageDp))
    }

    /** Only the burst's points and the pill's ends come close enough to a long number to need more room. */
    @Test
    fun onlyTheBurstAndThePillNeedMoreRoom() {
        WidgetShape.entries.forEach {
            val more = it == WidgetShape.BURST || it == WidgetShape.PILL
            assertEquals(it.id, if (more) 0.04f else 0f, extraInset(it), 0f)
        }
        assertEquals(0f, extraInset(null), 0f)
    }

    /**
     * A clock, a gauge and rings draw a round dial in the shape: they leave out the four shapes that
     * are not the same turned a half turn. Every other card takes all fifteen.
     */
    @Test
    fun aDialTakesOnlyShapesThatAreTheSameTurned() {
        val lopsided = setOf(WidgetShape.SLANTED, WidgetShape.ARCH, WidgetShape.GHOST, WidgetShape.PILL)
        assertEquals(lopsided, WidgetShape.entries.filterNot { it.symmetric }.toSet())
        assertEquals(15, WidgetShape.entries.size)
        val round = WidgetShape.entries - lopsided
        val clock = WidgetSpec.Clock(base, listOf(WidgetSpec.Zone("Asia/Tokyo", "Tokyo")))
        val gauge = WidgetSpec.Progress(base, value = 40f, max = 100f, style = "gauge")
        val rings = WidgetSpec.Progress(base, value = 0f, style = "rings", items = listOf(bar, bar))
        listOf(clock, gauge, rings).forEach { assertEquals(it.toString(), round, shapesFor(null, it)) }
        assertEquals(round, shapesFor(WidgetKind.CLOCK, null))
        listOf(
            WidgetSpec.Stat(base, value = "118"),
            WidgetSpec.Text(base, "Carry an umbrella"),
            WidgetSpec.Progress(base, value = 0.4f),
            WidgetSpec.Progress(base, value = 0.5f, stages = listOf("Packed", "Sent", "Here"), stage = 2),
            WidgetSpec.Chart(base, listOf(3f, 5f, 4f)),
            WidgetSpec.Image(base, "https://example.com/a.png", "Hills"),
        ).forEach { assertEquals(it.toString(), WidgetShape.entries, shapesFor(null, it)) }
    }

    /** The shape a card takes: the chosen one, else its script's, only where it has the choice and may take it. */
    @Test
    fun theShapeACardTakes() {
        val stat = WidgetSpec.Stat(WidgetBase(shape = WidgetShape.SUN), value = "1")
        assertEquals(WidgetShape.SUN, takenShape(stat, null, battery = false))
        assertEquals(WidgetShape.PILL, takenShape(stat, WidgetShape.PILL, battery = false))
        assertNull(takenShape(WidgetSpec.Stat(base, value = "1"), null, battery = false))
        // A clock may not take a pill: it keeps its own, chosen or named by its script.
        val clock = WidgetSpec.Clock(WidgetBase(shape = WidgetShape.PILL), listOf(WidgetSpec.Zone("UTC")))
        assertNull(takenShape(clock, null, battery = false))
        assertNull(takenShape(clock, WidgetShape.ARCH, battery = false))
        assertEquals(WidgetShape.GEM, takenShape(clock, WidgetShape.GEM, battery = false))
        assertEquals(WidgetShape.COOKIE9, own(null, clock))
        // The battery's shape shows its level.
        assertNull(takenShape(WidgetSpec.Progress(base, value = 0.4f), WidgetShape.SUN, battery = true))
        // The settings page offers a clock the round ones only, its own first.
        val row = ShapeChoices(WidgetShape.COOKIE9, WidgetShape.ARCH, shapesFor(WidgetKind.CLOCK, null))
        assertEquals(WidgetShape.COOKIE9, row.shown)
        assertEquals(11, row.shapes.size)
        assertFalse(WidgetShape.ARCH in row.shapes)
    }
}
