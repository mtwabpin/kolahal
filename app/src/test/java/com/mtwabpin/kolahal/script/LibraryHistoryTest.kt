package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * assets/library-history.txt lists every version of each library script, so a copy whose installed
 * text wasn't recorded is still known as unedited when the app updates ([ScriptLibrary.shipped]).
 */
class LibraryHistoryTest {
    private val library = sourceFile("src/main/assets/library").listFiles().orEmpty().filter { it.extension == "lua" || it.extension == "js" }
    private val lines = sourceFile("src/main/assets/library-history.txt").readLines().filter { it.isNotBlank() && !it.startsWith("#") }

    private fun fingerprint(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).take(8).joinToString("") { "%02x".format(it) }

    /** A script changed in the library must be listed, or the copies of this version won't be known after the next change. */
    @Test fun everyScriptsCurrentVersionIsListed() {
        val listed = lines.map { it.split(' ') }.groupBy({ it[0] }, { it[1] })
        val missing = library.filter { fingerprint(it.readBytes()) !in listed[it.name].orEmpty() }.map { it.name }
        assertEquals("Run tools/library-history.sh: these changed and aren't listed", emptyList<String>(), missing)
    }

    @Test fun eachLineIsAFileAndAFingerprint() {
        val line = Regex("^[a-z0-9-]+\\.(lua|js) [0-9a-f]{16}$")
        assertTrue(lines.filterNot(line::matches).toString(), lines.all(line::matches))
        assertEquals("no line twice", lines.size, lines.toSet().size)
    }
}
