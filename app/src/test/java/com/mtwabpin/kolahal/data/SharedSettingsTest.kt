package com.mtwabpin.kolahal.data

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The :script process reads only the settings home copies for it ([SharedSettings.KEYS]). A read of
 * any other setting would quietly give its default, so every read in the code must name a key in
 * that list.
 */
class SharedSettingsTest {
    private val read =
        Regex("""SharedSettings\.get\(\s*[^,]+,\s*(?:com\.mtwabpin\.kolahal\.data\.)?Keys\.([A-Z_0-9]+)\s*\)""")

    @Test
    fun everyReadNamesASharedKey() {
        val shared = SharedSettings.KEYS.map { it.key }.toSet()
        val reads = sourceFile("src/main/java/com/mtwabpin/kolahal").walkTopDown()
            .filter { it.extension == "kt" }.flatMap { f ->
            read.findAll(f.readText()).map { f.name to it.groupValues[1] }
        }.toList()
        assertTrue("found no reads: the pattern is out of date", reads.isNotEmpty())
        for ((file, name) in reads) {
            val key = Keys::class.java.getMethod("get$name").invoke(Keys) as ConfigKey<*>
            assertTrue(
                "$file reads Keys.$name, which isn't in SharedSettings.KEYS",
                key.key in shared
            )
        }
    }
}
