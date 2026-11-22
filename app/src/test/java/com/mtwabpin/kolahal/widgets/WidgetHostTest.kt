package com.mtwabpin.kolahal.widgets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A widget's view is used again only for the same details from the same install ([ViewDetails]). */
class WidgetHostTest {
    private val clock = ViewDetails("com.example/.Clock", "user 0", 7, "/data/app/~~a/com.example-1@100")

    @Test
    fun equalDetailsShareTheView() {
        // Read again, as new objects with the same values (each check before Android 12 does).
        assertTrue(clock.sameAs(clock.copy()))
    }

    @Test
    fun anUpdateMakesTheViewAgain() {
        // A new folder, or the same folder with a later update time (Android 9 and 10 swap two folders).
        assertFalse(clock.sameAs(clock.copy(install = "/data/app/~~b/com.example-2@200")))
        assertFalse(clock.sameAs(clock.copy(install = "/data/app/~~a/com.example-1@300")))
    }

    @Test
    fun otherDetailsMakeTheViewAgain() {
        assertFalse(clock.sameAs(clock.copy(provider = "com.example/.Weather")))
        assertFalse(clock.sameAs(clock.copy(profile = "user 10")))
        assertFalse(clock.sameAs(clock.copy(initialLayout = 8)))
    }

    @Test
    fun anUnknownInstallIsNeverTheSame() {
        val unknown = clock.copy(install = null)
        assertFalse(unknown.sameAs(unknown.copy()))
        assertFalse(unknown.sameAs(clock))
        assertFalse(clock.sameAs(unknown))
    }
}
