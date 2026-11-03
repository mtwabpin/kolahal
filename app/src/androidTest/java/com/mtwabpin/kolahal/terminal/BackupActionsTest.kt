package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.data.AutoBackup
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.ScriptScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Settings' Backup page: a file saved or imported says what it holds, and Back up now asks for a folder or says what is wrong with it. */
@RunWith(AndroidJUnit4::class)
class BackupActionsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val config = Config.get(context)
    private val prefs = AppPrefs.get(context)
    private val repo = ScriptRepository.get(context)
    private val logs = LogRepository.get(context)
    private val before = logs.maxId()
    private val host = RecordingHost()
    private val scope = CoroutineScope(SupervisorJob())
    private val scripts = ScriptActions(context, SavedStateHandle(), scope, MutableStateFlow<List<Script>>(emptyList()), host)
    private val actions = BackupActions(context, scripts, host)
    private val files = File(context.cacheDir, "backup-actions-test")

    private val autoBackup = context.getSharedPreferences("auto_backup", Context.MODE_PRIVATE)
    private val folderWas = autoBackup.getString("folder", null)
    private val lastWas = autoBackup.getLong("last", 0L)

    @Before fun setUp() {
        // No folder chosen, whatever this phone had.
        autoBackup.edit(commit = true) { remove("folder") }
    }

    @After fun tearDown() {
        scope.cancel()
        Keys.ALL.forEach(config::reset)
        listOf("ba-al", "ba-al2", "help").forEach { prefs.setAlias(it, null) }
        listOf("ba-bk", "ba-bk2").forEach { n -> repo.byName(n)?.let { ScriptScheduler.cancel(context, it.id); repo.delete(it.id) } }
        logs.recent(400).filter { it.id > before }.forEach { logs.delete(it.id) }
        files.deleteRecursively()
        autoBackup.edit(commit = true) {
            if (folderWas != null) putString("folder", folderWas) else remove("folder")
            if (lastWas > 0) putLong("last", lastWas) else remove("last")
        }
    }

    private fun file(name: String, text: String? = null) =
        File(files.apply { mkdirs() }, name).apply { if (text != null) writeText(text) }

    /** A folder Kolahal was never given (or has lost): what a picked folder is without its grant. */
    private val ungranted = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AKolahalTestBackups")

    /** Export everything writes the backup to the file picked and says what it holds; nothing picked, nothing happens. */
    @Test fun exportSavesTheFileAndSaysWhatItHolds() {
        actions.exportBackup(null)
        assertTrue(host.notices.isEmpty())

        val out = file("kolahal-backup.json")
        actions.exportBackup(Uri.fromFile(out))
        assertTrue(out.readText(), out.readText().startsWith("{") && "\"kolahal\":3" in out.readText())
        assertEquals(
            RecordingHost.Notice(
                "Backup saved: settings, apps, ${repo.all().size} scripts, todos, notes, reminders, feeds and the log (without notifications and calls)",
                false,
            ),
            host.lastNotice(),
        )
    }

    /** Importing a settings file says how much came back, and that a new script is off until turned on. */
    @Test fun importSaysWhatCameBack() {
        actions.importBackup(null)
        assertTrue(host.notices.isEmpty())

        val backup = file(
            "settings.json",
            """{"kolahal":3,"config":{"theme.name":"amber"},"aliases":{"ba-al":"echo a","ba-al2":"echo b"},""" +
                """"scripts":[{"name":"ba-bk","lang":"lua","code":"return 1","enabled":true}]}""",
        )
        actions.importBackup(Uri.fromFile(backup))
        assertEquals(
            RecordingHost.Notice("Imported 1 setting, 2 aliases, 1 script (1 new script is off — turn it on in Scripts)", false),
            host.lastNotice(),
        )
        assertEquals("amber", config[Keys.THEME])
        assertEquals("echo b", prefs.alias("ba-al2"))
        assertFalse(repo.byName("ba-bk")!!.enabled)
        // The scripts list and the command names are read again.
        assertTrue(host.commandList!!.any { it.name == "ba-bk" })
    }

    /** What an import skipped and the settings from the file worth a look (where searches go) are named. */
    @Test fun importNamesWhatItSkippedAndWhatToCheck() {
        val backup = file(
            "settings.json",
            """{"kolahal":3,"config":{"search.url":"https://example.com/?q=%s","no.such.key":"x"},"aliases":{"help":"clear all"},""" +
                """"scripts":[{"name":"ba-bk","lang":"lua","code":"return 1"},{"name":"ba-bk2","lang":"js","code":"log(1)"}]}""",
        )
        actions.importBackup(Uri.fromFile(backup))
        assertEquals(
            RecordingHost.Notice(
                "Imported 1 setting, 0 aliases, 2 scripts (2 new scripts are off — turn them on in Scripts)" +
                    " · skipped config no.such.key, alias help (built-in command)" +
                    " · check these settings from the file: search.url = https://example.com/?q=%s",
                false,
            ),
            host.lastNotice(),
        )
        assertNull(prefs.alias("help"))
    }

    /** A file that isn't a Kolahal backup fails, saying so, and changes nothing. */
    @Test fun aFileThatIsntABackupSaysSo() {
        actions.importBackup(Uri.fromFile(file("list.json", "[1, 2]")))
        actions.importBackup(Uri.fromFile(file("other.json", """{"theme.name":"amber"}""")))
        assertEquals(
            listOf("import" to "not a Kolahal backup", "import" to "not a Kolahal backup (missing \"kolahal\")"),
            host.failures.toList(),
        )
        assertTrue(host.notices.isEmpty())
        assertTrue(config.isDefault(Keys.THEME))
    }

    /** Back up now with no folder chosen asks for one first; a picker closed with nothing picked changes nothing. */
    @Test fun backUpNowWithNoFolderAsksForOne() {
        assertNull(actions.backupFolderLine())
        actions.backUpNow()
        assertEquals(listOf<UiEvent>(UiEvent.PickBackupFolder), host.events.toList())

        actions.onBackupFolderPicked(null)
        assertNull(AutoBackup.folder(context))
        assertTrue(host.notices.isEmpty())
        assertTrue(host.failures.isEmpty())
    }

    /** A folder picked that Kolahal can't keep: the pick fails, saying why, and no folder is set or backup written. */
    @Test fun aFolderThatCantBeKeptChangesNothing() {
        val version = actions.backupVersion.value
        actions.askBackupFolder(thenBackUp = true)
        assertEquals(listOf<UiEvent>(UiEvent.PickBackupFolder), host.events.toList())

        actions.onBackupFolderPicked(ungranted)
        val (label, why) = host.failures.single()
        assertEquals("backup folder", label)
        assertTrue(why, "permission" in why.lowercase())
        assertNull(AutoBackup.folder(context))
        assertNull(AutoBackup.last(context))
        assertEquals(version, actions.backupVersion.value)
        assertTrue(host.notices.isEmpty())
    }

    /** A backup folder Kolahal can no longer write: Settings and Back up now say to pick it again, without asking. */
    @Test fun aFolderThatCantBeWrittenSaysToPickItAgain() {
        autoBackup.edit(commit = true) { putString("folder", ungranted.toString()); remove("last") }
        assertEquals("Can't be written: tap to pick it again", actions.backupFolderLine())
        val version = actions.backupVersion.value
        actions.backUpNow()
        assertEquals(listOf("backup" to "the backup folder can't be written: run 'backup dir'"), host.failures.toList())
        assertTrue(host.events.isEmpty())
        assertNull(AutoBackup.last(context))
        assertEquals(version, actions.backupVersion.value)
    }
}
