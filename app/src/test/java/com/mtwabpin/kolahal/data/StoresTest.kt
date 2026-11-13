package com.mtwabpin.kolahal.data

import com.mtwabpin.kolahal.sourceFile
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [Stores] is the one list of preferences files and kv scopes. Nothing opens a file or names a scope
 * past it, and the backup rules in res/xml leave out exactly the files it says stay out.
 */
class StoresTest {
    private val sources = sourceFile("src/main/java").walkTopDown().filter { it.extension == "kt" && !it.path.endsWith("data/Stores.kt") }.toList()

    @Test fun preferencesFilesAreOpenedOnlyThroughStores() {
        val opened = sources.filter { "getSharedPreferences(" in it.readText() }.map { it.name }
        assertEquals("open them with Stores.<FILE>.open(context)", emptyList<String>(), opened)
    }

    @Test fun kvScopesAreNamedOnlyInStores() {
        val literal = Regex("""KvStore\([^,()]+, "[^"]*"\)""")
        val scoped = Regex("""const val \w*SCOPE\w* = """")
        val found = sources.flatMap { f -> f.readLines().filter { literal.containsMatchIn(it) || scoped.containsMatchIn(it) }.map { "${f.name}: ${it.trim()}" } }
        assertEquals("name the scope in Stores", emptyList<String>(), found)
    }

    @Test fun namesAreUnique() {
        assertEquals(Stores.PREFS.size, Stores.PREFS.map { it.name }.toSet().size)
        assertEquals(Stores.SCOPES.size, Stores.SCOPES.toSet().size)
    }

    /** The files each backup leaves out, as the XML says: `<exclude domain="sharedpref" path="logs.xml" />`. */
    private fun excluded(xml: String, section: String?): Set<String> {
        val text = sourceFile("src/main/res/xml/$xml").readText()
        val part = if (section == null) text else text.substringAfter("<$section>").substringBefore("</$section>")
        return Regex("""<exclude domain="sharedpref" path="([^"]+)\.xml"""").findAll(part).map { it.groupValues[1] }.toSet()
    }

    @Test fun theCloudBackupLeavesOutWhatStoresSays() {
        val notToCloud = Stores.PREFS.filter { !it.toCloud }.map { it.name }.toSet()
        assertEquals(notToCloud, excluded("data_extraction_rules.xml", "cloud-backup"))
        // Android 11 and older read backup_rules.xml instead.
        assertEquals(notToCloud, excluded("backup_rules.xml", null))
    }

    @Test fun aTransferToANewPhoneLeavesOutWhatStoresSays() {
        assertEquals(Stores.PREFS.filter { !it.toNewPhone }.map { it.name }.toSet(), excluded("data_extraction_rules.xml", "device-transfer"))
    }
}
