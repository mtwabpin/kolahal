package com.mtwabpin.kolahal.terminal

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The APK `update install` kept goes once the new version is in, and only then. */
@RunWith(AndroidJUnit4::class)
class UpdateCleanupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = UpdateCommands.updatesDir(context)

    @After fun tearDown() {
        dir.deleteRecursively()
    }

    @Test fun theNewVersionsArrivalDeletesTheKeptApk() {
        val apk = File(dir.apply { mkdirs() }, "kolahal-9.9.9.apk").apply { writeText("x") }
        UpdateCleanupReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue("another broadcast leaves it", apk.exists())
        UpdateCleanupReceiver().onReceive(context, Intent(Intent.ACTION_MY_PACKAGE_REPLACED))
        assertFalse(dir.exists())
    }
}
