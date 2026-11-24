package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetBase
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
}
