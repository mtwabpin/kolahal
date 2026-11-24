package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZoneId

/** A versus card's marks, colors and bar, and what a screen reader hears ([VersusWords]). */
class VersusWordsTest {
    private val chess = WidgetSpec.Versus(
        WidgetBase(title = "Berlin"),
        listOf(
            WidgetSpec.Side("Carlsen", value = 72f, text = "1:12"),
            WidgetSpec.Side("Caruana", mark = "U", color = StyledText.ColorRole.GOOD, value = 3490f, text = "58:10"),
        ),
        score = "1½–½",
        turn = 1,
        moving = true,
        line = "17. b3",
    )

    @Test
    fun aSideHasItsMarkAndItsColor() {
        assertEquals("C", VersusWords.mark(chess.sides[0]))
        assertEquals("U", VersusWords.mark(chess.sides[1]))
        assertEquals("", VersusWords.mark(WidgetSpec.Side("…")))
        assertEquals(StyledText.ColorRole.ACCENT, VersusWords.role(chess.sides[0], 0))
        assertEquals(StyledText.ColorRole.GOOD, VersusWords.role(chess.sides[1], 1))
        assertEquals(StyledText.ColorRole.LINK, VersusWords.role(WidgetSpec.Side("B"), 1))
    }

    /** The bar's two parts are each side's share of the two values. */
    @Test
    fun theBarSharesTheTwoValues() {
        val possession = chess.copy(
            sides = listOf(WidgetSpec.Side("Lisbon", value = 58f), WidgetSpec.Side("Paris", value = 42f))
        )
        val (a, b) = VersusWords.shares(possession)!!
        assertEquals(0.58f, a, 0.0001f)
        assertEquals(0.42f, b, 0.0001f)
        assertEquals("58", VersusWords.figure(possession.sides[0]))
        assertEquals("58:10", VersusWords.figure(chess.sides[1]))
    }

    /** No bar without a value on both sides, or with two values of 0. */
    @Test
    fun noBarWithoutBothValues() {
        assertNull(VersusWords.shares(chess.copy(sides = listOf(WidgetSpec.Side("A", value = 1f), WidgetSpec.Side("B")))))
        assertNull(VersusWords.shares(chess.copy(sides = listOf(WidgetSpec.Side("A", value = 0f), WidgetSpec.Side("B", value = 0f)))))
        assertEquals(1f to 0f, VersusWords.shares(chess.copy(sides = listOf(WidgetSpec.Side("A", value = 3f), WidgetSpec.Side("B", value = 0f)))))
        assertNull(VersusWords.figure(WidgetSpec.Side("A")))
    }

    @Test
    fun theTurnIsASidesPlace() {
        assertEquals(0, VersusWords.turnIndex(chess))
        assertEquals(1, VersusWords.turnIndex(chess.copy(turn = 2)))
        assertNull(VersusWords.turnIndex(chess.copy(turn = null)))
    }

    @Test
    fun aScreenReaderHearsTheWholeMatch() {
        assertEquals(
            listOf("Carlsen 1½–½ Caruana", "Carlsen 1:12", "Caruana 58:10", "Carlsen's turn", "17. b3"),
            VersusWords.said(chess)
        )
        val football = WidgetSpec.Versus(
            WidgetBase(title = "League"),
            listOf(WidgetSpec.Side("Lisbon"), WidgetSpec.Side("Paris")),
            score = "2–1",
            line = StyledText.color(StyledText.ColorRole.ACCENT, "64' Goal"),
            badge = "67'",
        )
        assertEquals(
            "League: 67', Lisbon 2–1 Paris, 64' Goal",
            WidgetWords.description("League", football, 0, ZoneId.of("UTC"))
        )
    }

    /**
     * A 4×1 card, 68 dp inside: the bar with its figures (18 dp) always has its room. The score
     * takes the largest size that fits over it with the last line (18 dp); else alone, and the line
     * gives way; at a large font size, the smallest.
     */
    @Test
    fun aOneRowCardKeepsItsBarAndLetsTheLineGo() {
        val plain = listOf(36, 32, 28)
        assertEquals(VersusWords.RowPlan(1, line = true), VersusWords.rowPlan(68, plain, 18, 18))
        // At a larger font size the line goes first, then the score gets smaller.
        assertEquals(VersusWords.RowPlan(1, line = false), VersusWords.rowPlan(68, listOf(46, 41, 36), 23, 23))
        assertEquals(VersusWords.RowPlan(1, line = false), VersusWords.rowPlan(72, listOf(52, 45, 40), 26, 26))
        assertEquals(VersusWords.RowPlan(2, line = false), VersusWords.rowPlan(60, listOf(52, 45, 40), 26, 26))
        // With no bar and no line, the largest score.
        assertEquals(VersusWords.RowPlan(0, line = false), VersusWords.rowPlan(68, plain, 0, 0))
        assertEquals(VersusWords.RowPlan(0, line = true), VersusWords.rowPlan(68, plain, 18, 0))
    }

    /**
     * The names of a 4×1 card get the room they need, and the score what is left: a live score's
     * "New Zealand" fits whole beside "237/4 · 299/8", which gets narrower. A name is cut only
     * past the score's least width.
     */
    @Test
    fun theNamesFitBeforeTheScoreKeepsItsWidth() {
        // Everything fits: the score has its own width, the sides share the rest.
        assertEquals(111f, VersusWords.middleDp(363f, 111f, 104f, 125f))
        // The live score: sides of 131 dp leave the score 101 dp, under its least 125 dp.
        assertEquals(125f, VersusWords.middleDp(363f, 266f, 131f, 125f))
        // Sides that leave more than the least: the score gets that.
        assertEquals(163f, VersusWords.middleDp(363f, 266f, 100f, 125f))
        // Never more than the middle wants, never more than the row.
        assertEquals(90f, VersusWords.middleDp(363f, 90f, 200f, 125f))
        assertEquals(100f, VersusWords.middleDp(100f, 300f, 200f, 125f))
    }

    /** Versus has no Shape and no Poster: a card with either stored draws as Plain. */
    @Test
    fun aLookVersusLacksDrawsPlain() {
        assertEquals(WidgetLook.PLAIN, WidgetLook.SHAPE.drawnFor(WidgetKind.VERSUS))
        assertEquals(WidgetLook.PLAIN, WidgetLook.POSTER.drawnFor(WidgetKind.VERSUS))
        assertEquals(WidgetLook.BOLD, WidgetLook.BOLD.drawnFor(WidgetKind.VERSUS))
        assertEquals(WidgetLook.SPLIT, WidgetLook.SPLIT.drawnFor(WidgetKind.VERSUS))
    }
}
