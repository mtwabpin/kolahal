package com.mtwabpin.kolahal.data

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoBackupTest {
    /** Plain digits, as [java.time.LocalDate] writes them, whatever the language: `%02d` would write Persian or Bengali ones. */
    private fun day(n: Int) = "kolahal-backup-2026-10-${n.toString().padStart(2, '0')}.json"

    /** A folder Kolahal can't use gets a tap to pick one; a backup too big doesn't, as no folder fixes that. */
    @Test fun onlyAFolderProblemOffersTheFolderPicker() {
        val (folder, folderTap) = AutoBackup.failure(IllegalStateException("the backup folder can't be written: run 'backup dir'"))
        assertEquals("Automatic backup failed: the backup folder can't be written: run 'backup dir' · tap to pick the folder", folder)
        assertEquals("backup dir", folderTap.values[LogContext.RUN])
        val (big, bigTap) = AutoBackup.failure(Backup.TooBig("the backup would be 70 MB, more than the 64 MB import limit"))
        assertEquals("Automatic backup failed: the backup would be 70 MB, more than the 64 MB import limit", big)
        assertEquals(null, bigTap.values[LogContext.RUN])
    }

    /** The oldest automatic backups go past the newest few; a backup you exported by hand, or anything else, stays. */
    @Test fun onlyOldAutomaticBackupsGo() {
        val sizes = (listOf(day(25), day(27), day(26), "kolahal-backup.json", "notes.txt", day(24), "kolahal-backup-2026-10-28.tmp.json")).associateWith { 100L }
        assertEquals(listOf(day(25), day(24)), AutoBackup.expired(sizes, day(27), 2))
        assertEquals(emptyList<String>(), AutoBackup.expired(sizes, day(27), 7))
    }

    /** Android lets a provider report no size, and a file then reads as empty: every backup must not go for that. */
    @Test fun readingAsEmptyCostsNothing() {
        val sizes = (20..27).associate { day(it) to 0L }
        assertEquals(emptyList<String>(), AutoBackup.expired(sizes, day(27), 3))
    }

    /** One that reads as empty doesn't push a real one out, and goes itself once it's past those kept. */
    @Test fun anEmptyFileDoesntCount() {
        val sizes = mapOf(day(1) to 100L, day(2) to 0L, day(3) to 100L, day(4) to 100L, day(5) to 0L, day(6) to 100L, day(7) to 0L)
        assertEquals(listOf(day(3), day(2), day(1)), AutoBackup.expired(sizes, day(7), 3))
        assertEquals(listOf(day(2), day(1)), AutoBackup.expired(sizes, day(7), 4))
    }

    /** With the clock set back, today's isn't the newest by name: it still stays. */
    @Test fun theOneJustWrittenStays() {
        val sizes = mapOf(day(27) to 100L, day(26) to 100L, day(25) to 100L, day(20) to 100L)
        assertEquals(listOf(day(25)), AutoBackup.expired(sizes, day(20), 2))
    }
}
