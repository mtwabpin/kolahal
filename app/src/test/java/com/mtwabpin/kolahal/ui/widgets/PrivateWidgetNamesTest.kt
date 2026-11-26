package com.mtwabpin.kolahal.ui.widgets

import com.mtwabpin.kolahal.apps.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What stands for an app's widget names its app unless its profile is a private space ([namesApp]):
 * one listed as such, or one missing from the list that Android says is a locked private space it
 * hides ([kindOf]). A profile that can't be found at all, such as a work profile not on this phone,
 * keeps the name. The widgets page's line ([placeholderLine]) and a row under "Restored from backup"
 * ([pendingTitle]) follow it.
 */
class PrivateWidgetNamesTest {
    @Test
    fun namedInPersonalWorkAndCloneProfiles() {
        assertTrue(namesApp(ProfileKind.PERSONAL))
        assertTrue(namesApp(ProfileKind.WORK))
        assertTrue(namesApp(ProfileKind.CLONE))
    }

    @Test
    fun notInAPrivateSpace() {
        assertFalse(namesApp(ProfileKind.PRIVATE))
        // Not found: a work profile not on this phone, say. Only a sign of a private space hides it.
        assertTrue(namesApp(null))
    }

    @Test
    fun theWidgetsPagesLine() {
        assertEquals(
            "[Widget unavailable] com.example.clock",
            placeholderLine("Widget unavailable", "com.example.clock", names = true)
        )
        assertEquals("[Widget unavailable]", placeholderLine("Widget unavailable", "com.example.clock", names = namesApp(ProfileKind.PRIVATE)))
        assertEquals(
            "[Widget unavailable] com.example.clock",
            placeholderLine("Widget unavailable", "com.example.clock", names = namesApp(null))
        )
        assertEquals(
            "[Private space locked]",
            placeholderLine("Private space locked", "com.example.clock", names = namesApp(ProfileKind.PRIVATE))
        )
        assertEquals(
            "[Work profile paused] com.example.clock",
            placeholderLine("Work profile paused", "com.example.clock", names = namesApp(ProfileKind.WORK))
        )
    }

    @Test
    fun aRestoredRow() {
        assertEquals("Clock", pendingTitle("Clock", ProfileKind.PERSONAL))
        assertEquals("Clock", pendingTitle("Clock", ProfileKind.WORK))
        assertEquals("Private space widget", pendingTitle("Clock", ProfileKind.PRIVATE))
        assertEquals("Clock", pendingTitle("Clock", null))
    }
}
