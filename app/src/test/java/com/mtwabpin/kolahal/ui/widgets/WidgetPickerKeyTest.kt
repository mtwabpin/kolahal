package com.mtwabpin.kolahal.ui.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/** The picker reads a widget's pictures again only when the widget changed ([pickerPicturesKey]). */
class WidgetPickerKeyTest {
    private fun key(
        previewImage: Int = 1,
        previewLayout: Int = 2,
        icon: Int = 3,
        install: String? = "/data/app/~~a/com.example-1",
        serial: Long = 0,
        maxPx: Int = 770,
    ) = pickerPicturesKey("com.example/.Clock", serial, previewImage, previewLayout, icon, install, maxPx)

    @Test
    fun theSameWidgetReadAgainHasTheSameKey() {
        // The list is read again at each visit and when providers change: new objects, the same values.
        assertEquals(key(), key())
    }

    @Test
    fun anUpdateOrOtherPicturesGiveANewKey() {
        assertNotEquals(key(), key(install = "/data/app/~~b/com.example-2"))
        assertNotEquals(key(), key(previewImage = 4))
        assertNotEquals(key(), key(previewLayout = 4))
        assertNotEquals(key(), key(icon = 4))
    }

    @Test
    fun anotherProfileOrSizeGivesANewKey() {
        assertNotEquals(key(), key(serial = 10))
        assertNotEquals(key(), key(maxPx = 560))
    }
}
