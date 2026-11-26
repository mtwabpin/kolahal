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
}
