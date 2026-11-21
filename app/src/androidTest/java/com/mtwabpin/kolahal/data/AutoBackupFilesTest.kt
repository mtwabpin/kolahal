package com.mtwabpin.kolahal.data

import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

/** A failed automatic backup must not cost a real one. */
@RunWith(AndroidJUnit4::class)
class AutoBackupFilesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var folder: File
    private lateinit var dir: DocumentFile

    @Before
    fun setUp() {
        folder = File(context.cacheDir, "autobackup-test").apply { deleteRecursively(); mkdirs() }
        dir = DocumentFile.fromFile(folder)
    }

    @After
    fun tearDown() {
        folder.deleteRecursively()
    }

    private fun backup(day: Int, text: String = "backup") =
        File(folder, "kolahal-backup-2026-10-%02d.json".format(day)).apply { writeText(text) }

    private fun names() = folder.list().orEmpty().sorted()

    /** What a backup does: writes to the file it's handed. */
    private fun writes(text: String): (Uri) -> Unit = { File(it.path!!).writeText(text) }

    /** Six empty files from failed days, then one good day: the real backups all stay. */
    @Test
    fun emptyFilesDontPushRealBackupsOut() {
        val real = (1..3).map { backup(it) }
        val empty = (10..15).map { backup(it, "") }
        val today = backup(20, "")
        val saved = AutoBackup.saveTo(dir, today.name, keep = 7, writes("today"))
        assertEquals(today.name, saved)
        assertEquals("today", today.readText())
        assertEquals((real + empty + today).map { it.name }, names())
    }

    /** A provider that reports no size reads as if every file were empty: none goes for that. */
    @Test
    fun filesThatReadAsEmptyStay() {
        val old = (1..5).map { backup(it, "") }
        val name = "kolahal-backup-2026-10-20.json"
        AutoBackup.saveTo(dir, name, keep = 3, writes("today"))
        assertEquals(old.map { it.name } + name, names())
    }

    @Test
    fun onlyRealBackupsCountTowardsKeep() {
        (1..5).forEach { backup(it) }
        val today = backup(20)
        AutoBackup.saveTo(dir, today.name, keep = 3, writes("today"))
        assertEquals(listOf(backup(4).name, backup(5).name, today.name), names())
    }

    /** Written aside, then in place of the day's earlier one, with nothing else left in the folder. */
    @Test
    fun aSecondBackupThatDayReplacesTheFirst() {
        val today = backup(20, "earlier today")
        AutoBackup.saveTo(dir, today.name, keep = 7, writes("later"))
        assertEquals("later", today.readText())
        assertEquals(listOf(today.name), names())
    }

    /** The write fails: no file left behind, whatever was there stays, and the failure still shows. */
    @Test
    fun aFailedWriteLeavesNothingNew() {
        val old = backup(1)
        try {
            AutoBackup.saveTo(
                dir,
                "kolahal-backup-2026-10-20.json",
                keep = 7
            ) { throw IOException("disk full") }
            fail("the failure must reach the caller, to be logged")
        } catch (e: IOException) {
            assertEquals("disk full", e.message)
        }
        assertEquals(listOf(old.name), names())
    }

    /** Today's backup, already written, stays when writing it again fails partway. */
    @Test
    fun aFailedWriteKeepsTodaysExistingFile() {
        val today = backup(20, "earlier today")
        runCatching {
            AutoBackup.saveTo(dir, today.name, keep = 7) { uri ->
                File(uri.path!!).writeText("part of")
                throw IOException("disk full")
            }
        }
        assertEquals(listOf(today.name), names())
        assertTrue(today.exists())
        assertEquals("earlier today", today.readText())
        assertFalse(names().isEmpty())
    }
}
