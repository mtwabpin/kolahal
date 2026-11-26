package com.mtwabpin.kolahal.widgets

import com.mtwabpin.kolahal.data.Keys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    /** Split's tiles and Shape's shape keep their outline; every other look follows the setting. */
    @Test
    fun whichLooksFollowIt() {
        assertEquals(
            WidgetLook.entries - WidgetLook.SPLIT - WidgetLook.SHAPE,
            WidgetLook.entries.filter(CardCorners::takenIn)
        )
    }

    /**
     * Every look of every built-in card either follows the setting or is named in its description as
     * keeping its own: a new look can't fall out of it unseen.
     */
    @Test
    fun everyBuiltInLookFollowsOrIsNamed() {
        val builtins = listOf(
            WidgetSource.BATTERY, WidgetSource.SHORTCUTS, WidgetSource.MEDIA, WidgetSource.TODOS,
            WidgetSource.AGENDA, WidgetSource.REMINDERS, WidgetSource.NOTES,
        )
        for (id in builtins) for (look in WidgetLook.offeredFor(WidgetSource.builtinKind(id), id)) {
            val named = Regex("\\b${look.label}\\b").containsMatchIn(Keys.WIDGET_CORNERS.help)
            assertEquals("$id ${look.id}", !CardCorners.takenIn(look), named)
        }
    }

    /** A surface that fills the card (Stack's sheets) keeps its own corner, or takes the card's less the room around it, never below 0. */
    @Test
    fun anInnerSurfaceFitsTheCard() {
        assertEquals(18, CardCorners.inner(null, inset = 16, own = 18))
        assertEquals(listOf(0, 0, 0, 24), CardCorners.entries.map { CardCorners.inner(it, inset = 16, own = 18) })
    }
}
