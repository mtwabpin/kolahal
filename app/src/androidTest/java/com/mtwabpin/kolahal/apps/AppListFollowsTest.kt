package com.mtwabpin.kolahal.apps

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.waitUntil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The app list, once read, follows an app turned off and on as it happens, with nothing asking to read it again. */
@RunWith(AndroidJUnit4::class)
class AppListFollowsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val apps = AppRepository.get(instrumentation.targetContext)

    /** A system app nothing else here needs: the camera. */
    private val pkg = "com.android.camera2"

    private fun listed() = apps.apps.value.any { it.pkg == pkg }

    @Test fun anAppTurnedOffAndOnLeavesAndComesBack() {
        apps.refresh()
        waitUntil("the app list") { apps.apps.value.isNotEmpty() }
        assumeTrue("no $pkg here", listed())
        try {
            shell("pm disable-user --user 0 $pkg")
            waitUntil("gone once turned off") { !listed() }
        } finally {
            shell("pm enable --user 0 $pkg")
        }
        waitUntil("back once turned on") { listed() }
    }

    /**
     * The list as last loaded is kept for the next start, which shows it at once instead of nothing:
     * the same apps, with their names, renames and hidden state.
     */
    @Test fun theListIsKeptForTheNextStart() {
        val prefs = AppPrefs.get(instrumentation.targetContext)
        apps.refresh()
        waitUntil("the app list") { apps.apps.value.isNotEmpty() }
        val app = apps.apps.value.first { it.profile.kind == ProfileKind.PERSONAL }
        try {
            prefs.setLabel(app.key, "Zz Kept")
            apps.refresh()
            waitUntil("the rename in the list") { apps.apps.value.any { it.key == app.key && it.label == "Zz Kept" } }
            fun keys(list: List<AppEntry>) = list.filter { it.profile.kind != ProfileKind.PRIVATE }.map { it.key to it.label }
            waitUntil("the saved list matches") { keys(apps.saved()) == keys(apps.apps.value) }
            val kept = apps.saved().single { it.key == app.key }
            assertEquals(app.originalLabel, kept.originalLabel)
            assertTrue(apps.awaitLoaded(0))
        } finally {
            prefs.setLabel(app.key, null)
            apps.refresh()
            waitUntil("the old name back") { apps.apps.value.any { it.key == app.key && it.label == app.originalLabel } }
        }
    }
}
