package com.mtwabpin.kolahal.apps

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.shell
import com.mtwabpin.kolahal.waitUntil
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
}
