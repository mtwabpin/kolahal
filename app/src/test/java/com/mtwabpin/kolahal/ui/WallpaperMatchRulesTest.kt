package com.mtwabpin.kolahal.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * A wallpaper's id means something only on this phone. Carried to another, the same number could be
 * someone's own wallpaper, which the next theme change would replace.
 */
class WallpaperMatchRulesTest {
    /** Left out, not let in: the same path in an include would mean the opposite. */
    private val line = """<exclude domain="sharedpref" path="wallpaper_match.xml"""

    private fun count(file: String) = File("src/main/res/xml/$file").readText().windowed(line.length).count { it == line }

    @Test fun itsIdStaysOnThePhone() {
        assertEquals("Android 11 and lower", 1, count("backup_rules.xml"))
        assertEquals("the cloud and a transfer", 2, count("data_extraction_rules.xml"))
    }
}
