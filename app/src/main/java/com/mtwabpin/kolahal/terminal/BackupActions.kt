package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.data.Backup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Handles backup files and the folder for automatic backups. The view model holds one as `backupActions`. */
class BackupActions(
    private val context: Context,
    private val scripts: ScriptActions,
    private val host: ScriptActions.Host,
) {
    private fun fileOp(label: String, block: suspend () -> Unit) = host.fileOp(label, block)
    private fun notice(text: String, error: Boolean = false) = host.notice(text, error)

    fun exportBackup(uri: Uri?) {
        if (uri == null) return
        fileOp("backup") { notice("Backup saved: settings, apps, ${Backup(context).export(uri)} scripts, todos, notes, reminders, feeds and the log (without notifications and calls)") }
    }

    fun importBackup(uri: Uri?) {
        if (uri == null) return
        fileOp("import") {
            val r = Backup(context).import(uri)
            AppRepository.get(context).refresh()
            scripts.refreshScriptCaches()
            fun count(n: Int, one: String, many: String = one + "s") = "$n ${if (n == 1) one else many}"
            notice(
                "Imported ${count(r.settings, "setting")}, ${count(r.aliases, "alias", "aliases")}, ${count(r.scripts, "script")}" +
                    (if (r.full) ", ${r.restored} saved items (todos, notes, reminders, feeds, log…)" else "") +
                    (if (r.disabled == 1) " (1 new script is off — turn it on in Scripts)" else "") +
                    (if (r.disabled > 1) " (${r.disabled} new scripts are off — turn them on in Scripts)" else "") +
                    (if (r.skipped.isNotEmpty()) " · skipped ${r.skipped.joinToString()}" else "") +
                    (if (r.review.isNotEmpty()) " · check these settings from the file: ${r.review.joinToString()}" else "") +
                    if (r.full) " — restarting to load it all" else ""
            )
            // A full restore replaced the preferences and tables, so every in-memory cache is now stale.
            // Restart the app to load everything again.
            if (r.full) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    context.startActivity(
                        Intent(context, com.mtwabpin.kolahal.MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    android.os.Process.killProcess(android.os.Process.myPid())
                }, 2_000)
            }
        }
    }

    private val _backupVersion = MutableStateFlow(0)
    /** Goes up by one when the backup folder changes or a backup is written. Settings uses it to refresh its backup line. */
    val backupVersion: StateFlow<Int> = _backupVersion.asStateFlow()

    @Volatile private var backUpOncePicked = false

    fun askBackupFolder(thenBackUp: Boolean) {
        backUpOncePicked = thenBackUp
        host.emit(UiEvent.PickBackupFolder)
    }

    /** Returns the backup folder and the time of the last backup, for Settings. Returns null when no folder is set. */
    fun backupFolderLine(): String? {
        if (com.mtwabpin.kolahal.data.AutoBackup.folder(context) == null) return null
        // The folder is saved, but Kolahal may not be able to open it any more. For example, the backup was
        // restored on another phone, or the folder was deleted. Backups fail until the user picks it again.
        val name = com.mtwabpin.kolahal.data.AutoBackup.folderName(context) ?: return "Can't be written: tap to pick it again"
        val last = com.mtwabpin.kolahal.data.AutoBackup.last(context)?.let {
            "last backup " + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT).format(java.util.Date(it))
        }
        return listOfNotNull(name, last ?: "no backup yet").joinToString(" · ")
    }

    fun onBackupFolderPicked(uri: Uri?) {
        val thenBackUp = backUpOncePicked
        backUpOncePicked = false
        if (uri == null) return
        fileOp("backup folder") {
            com.mtwabpin.kolahal.data.AutoBackup.setFolder(context, uri)
            _backupVersion.value++
            notice("Backups now save to ${com.mtwabpin.kolahal.data.AutoBackup.folderName(context) ?: "that folder"}")
            if (thenBackUp) backUp()
        }
    }

    /** Settings' "Back up now": writes today's backup to the folder. If no folder is set, asks for one first. */
    fun backUpNow() {
        if (com.mtwabpin.kolahal.data.AutoBackup.folder(context) == null) return askBackupFolder(true)
        fileOp("backup") { backUp() }
    }

    private fun backUp() {
        val name = com.mtwabpin.kolahal.data.AutoBackup.backUp(context)
        _backupVersion.value++
        notice("Backed up: $name in ${com.mtwabpin.kolahal.data.AutoBackup.folderName(context) ?: "the backup folder"}")
    }
}
