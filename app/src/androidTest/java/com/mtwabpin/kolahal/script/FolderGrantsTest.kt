package com.mtwabpin.kolahal.script

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** One folder for backups and scripts has one grant: changing either keeps it while the other uses it. */
@RunWith(AndroidJUnit4::class)
class FolderGrantsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val backup = context.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)
    private val scripts = context.getSharedPreferences("script_folder", Context.MODE_PRIVATE)
    private val backupWas = backup.getString("folder", null)
    private val scriptsWas = scripts.getString("uri", null)

    private fun tree(name: String) = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3A$name")

    @After fun tearDown() {
        backup.edit(commit = true) { putString("folder", backupWas) }
        scripts.edit(commit = true) { putString("uri", scriptsWas) }
    }

    @Test fun aFolderTheOtherUsesIsKept() {
        backup.edit(commit = true) { putString("folder", tree("Kolahal").toString()) }
        scripts.edit(commit = true) { putString("uri", tree("Kolahal").toString()) }
        assertTrue(Files.inUse(context, tree("Kolahal")))

        // Backups moved on: the script folder is still there.
        backup.edit(commit = true) { putString("folder", tree("Backups").toString()) }
        assertTrue(Files.inUse(context, tree("Kolahal")))
        // Then the scripts too: nothing uses it.
        scripts.edit(commit = true) { putString("uri", tree("Scripts").toString()) }
        assertFalse(Files.inUse(context, tree("Kolahal")))
    }
}
