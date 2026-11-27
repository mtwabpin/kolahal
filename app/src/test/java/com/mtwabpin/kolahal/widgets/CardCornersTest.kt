package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How round Kolahal's cards are: the setting `widgets.corners` ([CardCorners]), its steps and which looks follow it. */
class CardCornersTest {
    /** The setting's choices are its own and each step, own first and by default. */
    @Test
    fun theSettingsChoices() {
        assertEquals("widgets.corners", Keys.WIDGET_CORNERS.key)
        assertEquals("own", Keys.WIDGET_CORNERS.default)
        assertEquals(listOf("own") + CardCorners.entries.map { it.id }, Keys.WIDGET_CORNERS.choices)
        assertNull(CardCorners.named("own"))
        assertNull(CardCorners.named("oval"))
        assertEquals(CardCorners.VERY, CardCorners.named(" Very "))
    }

    /** Its own is today's corners: 24 dp small, 28 dp larger. The steps go from square to very round. */
    @Test
    fun theSteps() {
        assertEquals(24, CardCorners.dp(null, small = true))
        assertEquals(28, CardCorners.dp(null, small = false))
        assertEquals(listOf(0, 8, 16, 40), CardCorners.entries.map { CardCorners.dp(it, small = true) })
        assertEquals(
            listOf(0, 8, 16, 40),
            Keys.WIDGET_CORNERS.choices.mapNotNull(CardCorners::named).map { CardCorners.dp(it, small = false) }
        )
    }

    /** Every look follows the setting, Split's tiles too; only a card in Shape that is one shape keeps it. */
    @Test
    fun whichLooksFollowIt() {
        assertEquals(WidgetLook.entries, WidgetLook.entries.filter { CardCorners.takenIn(it, oneShape = false) })
        assertEquals(
            WidgetLook.entries - WidgetLook.SHAPE,
            WidgetLook.entries.filter { CardCorners.takenIn(it, oneShape = true) }
        )
    }

    /**
     * Every look of every built-in card follows the setting, but a card that can be one shape in
     * Shape ([CardCorners.ONE_SHAPE]): the description names that one, and no other look.
     */
    @Test
    fun everyBuiltInLookFollowsOrIsNamed() {
        val builtins = listOf(
            WidgetSource.BATTERY, WidgetSource.SHORTCUTS, WidgetSource.MEDIA, WidgetSource.TODOS,
            WidgetSource.AGENDA, WidgetSource.REMINDERS, WidgetSource.NOTES,
        )
        val help = Keys.WIDGET_CORNERS.help
        assertFalse(help, "Split" in help)
        for (id in builtins) {
            val kind = WidgetSource.builtinKind(id)
            for (look in WidgetLook.offeredFor(kind, id)) {
                val oneShape = look == WidgetLook.SHAPE && kind in CardCorners.ONE_SHAPE
                val named = Regex("\\b${look.label}\\b").containsMatchIn(help)
                assertTrue("$id ${look.id}", CardCorners.takenIn(look, oneShape = false))
                if (oneShape) assertTrue("$id ${look.id} is one shape: the description names it", named)
                if (look != WidgetLook.SHAPE) assertFalse("$id ${look.id} is named", named)
            }
        }
    }

    /**
     * Split's tiles: with the cards' own, 28 dp on the card's corners and 8 dp where tiles meet, as
     * always. A chosen corner goes on the card's corners, and less the 4 dp gap where tiles meet,
     * never below 0 or above 8: square everywhere at Square, one group at Very rounded.
     */
    @Test
    fun splitsTilesFollowIt() {
        assertEquals(28, CardCorners.splitOuter(null))
        assertEquals(8, CardCorners.splitInner(null))
        assertEquals(listOf(0, 8, 16, 40), CardCorners.entries.map(CardCorners::splitOuter))
        assertEquals(listOf(0, 4, 8, 8), CardCorners.entries.map(CardCorners::splitInner))
    }

    /** Arrange widgets' outline is the card's corner: 24 dp on a small card with its own, Split's outer corner on a script's Split card. */
    @Test
    fun theOutlineIsTheCardsCorner() {
        assertEquals(24, CardCorners.outline(null, split = false, small = true))
        assertEquals(28, CardCorners.outline(null, split = false, small = false))
        assertEquals(28, CardCorners.outline(null, split = true, small = true))
        assertEquals(16, CardCorners.outline(CardCorners.ROUNDED, split = false, small = true))
        assertEquals(0, CardCorners.outline(CardCorners.SQUARE, split = true, small = true))
    }

    /** A chosen corner is never more than half the tile's shorter side, so it can't swallow a small tile. */
    @Test
    fun aCornerFitsTheTile() {
        assertEquals(40f, CardCorners.capped(40f, side = 120f), 0f)
        assertEquals(30f, CardCorners.capped(40f, side = 60f), 0f)
        assertEquals(0f, CardCorners.capped(0f, side = 60f), 0f)
    }

    /** A surface that fills the card (Stack's sheets) keeps its own corner, or takes the card's less the room around it, never below 0. */
    @Test
    fun anInnerSurfaceFitsTheCard() {
        assertEquals(18, CardCorners.inner(null, inset = 16, own = 18))
        assertEquals(listOf(0, 0, 0, 24), CardCorners.entries.map { CardCorners.inner(it, inset = 16, own = 18) })
    }
}
