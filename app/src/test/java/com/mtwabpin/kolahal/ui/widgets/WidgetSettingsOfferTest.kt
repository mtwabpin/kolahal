package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arrange widgets offers Widget settings ([offersWidgetSettings]) for a placed app widget whose setup
 * can run again, and only while its profile is listed and running: not while it is paused, its private
 * space locked, or the profile isn't listed (a private space that Android hides while it is locked).
 */
class WidgetSettingsOfferTest {
    private val weather = PlacedWidget(7, "com.example.weather/.Widget", 0L, 160, 0)

    @Test
    fun aWidgetThatCanBeSetUpAgainIsOffered() {
        assertTrue(offersWidgetSettings(weather, running = true, setUpAgain = true))
        assertFalse(offersWidgetSettings(weather, running = true, setUpAgain = false))
    }

    @Test
    fun notWhileItsProfileIsPausedLockedOrNotListed() {
        assertFalse(offersWidgetSettings(weather, running = false, setUpAgain = true))
    }

    @Test
    fun notForAWidgetToAddAgainOrOneKolahalDraws() {
        // Restored from a backup: no id until it is added again.
        assertFalse(offersWidgetSettings(weather.copy(id = null), running = true, setUpAgain = true))
        val card = PlacedWidget(null, WidgetSource.builtin(WidgetSource.BATTERY), 0L, 160, 0)
        assertFalse(offersWidgetSettings(card, running = true, setUpAgain = true))
    }
}
