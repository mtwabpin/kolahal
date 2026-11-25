package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.terminal.Screen
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Arrange widgets keeps an empty page it has just added while it gives way to a screen that returns
 * to it ([keepsEmptyPages]): the picker, and a card's settings page. Any other screen leaves it, and
 * the empty pages go.
 */
class EmptyPagesKeptTest {
    @Test
    fun thePickerAndACardsSettingsPageKeepThem() {
        listOf(Screen.WIDGETS, Screen.WIDGET_PICKER, Screen.WIDGET_SETTINGS).forEach {
            assertTrue(it.name, keepsEmptyPages(it))
        }
    }

    @Test
    fun everyOtherScreenLeavesArrangeWidgets() {
        val kept = setOf(Screen.WIDGETS, Screen.WIDGET_PICKER, Screen.WIDGET_SETTINGS)
        (Screen.entries - kept).forEach { assertFalse(it.name, keepsEmptyPages(it)) }
    }
}
