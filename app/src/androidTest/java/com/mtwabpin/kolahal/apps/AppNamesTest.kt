package com.mtwabpin.kolahal.apps

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.waitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** An app's name for a package, the same everywhere ([AppNames]): renames count, and unknown packages have none. */
@RunWith(AndroidJUnit4::class)
class AppNamesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val apps = AppRepository.get(context)

    /** A name set with `rename` is the name: the Now playing card and the notification log follow it too. */
    @Test
    fun aRenameIsTheName() {
        val prefs = AppPrefs.get(context)
        apps.refresh()
        waitUntil("the app list") { apps.apps.value.isNotEmpty() }
        val app = apps.apps.value.first { it.profile.kind == ProfileKind.PERSONAL }
        try {
            prefs.setLabel(app.key, "Zz Renamed")
            apps.refresh()
            waitUntil("the rename in the list") { apps.apps.value.any { it.key == app.key && it.label == "Zz Renamed" } }
            assertEquals("Zz Renamed", AppNames.label(context, app.pkg))
            assertEquals("Zz Renamed", AppNames.label(context, app.pkg, app.profile.serial))
            // Android's own name is not the rename.
            assertEquals(false, AppNames.android(context, app.pkg) == "Zz Renamed")
        } finally {
            prefs.setLabel(app.key, null)
            apps.refresh()
        }
    }

    /** `usage` names an app as Android does, not by its rename. */
    @Test
    fun usageShowsTheRealName() {
        val prefs = AppPrefs.get(context)
        apps.refresh()
        waitUntil("the app list") { apps.apps.value.isNotEmpty() }
        val app = apps.apps.value.first { it.profile.kind == ProfileKind.PERSONAL }
        val real = AppNames.real(context, app)
        try {
            prefs.setLabel(app.key, "Zz Renamed")
            apps.refresh()
            val renamed = apps.apps.value.first { it.key == app.key }
            waitUntil("the rename in the list") { apps.apps.value.any { it.key == app.key && it.label == "Zz Renamed" } }
            assertEquals(real, AppNames.real(context, apps.apps.value.first { it.key == app.key }))
            assertEquals(false, real == "Zz Renamed")
            assertEquals(real, AppNames.real(context, renamed))
        } finally {
            prefs.setLabel(app.key, null)
            apps.refresh()
        }
    }

    /** A package Android doesn't know has no name: the caller shows the package, or leaves it out. */
    @Test
    fun anUnknownPackageHasNoName() {
        assertNull(AppNames.android(context, "com.example.not.installed"))
        assertNull(AppNames.label(context, "com.example.not.installed"))
    }
}
