/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import android.content.Context
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
    private fun notice(text: String) = host.notice(text)

    fun exportBackup(uri: Uri?) {
        if (uri == null) return
        fileOp("backup") {
            notice("Backup saved\nSettings, apps, ${Backup(context).export(uri)} scripts, todos, notes, reminders, feeds and the log\nWithout notifications and calls")
        }
    }

    fun importBackup(uri: Uri?) {
        if (uri == null) return
        fileOp("import") {
            val r = Backup(context).import(uri)
            AppRepository.get(context).refresh()
            scripts.refreshScriptCaches()
            fun count(n: Int, one: String, many: String = one + "s") =
                "$n ${if (n == 1) one else many}"
            // What happened first, then one line for each count or note.
            notice(
                listOfNotNull(
                    if (r.full) "Backup imported, restarting to load it all" else "Backup imported",
                    "${count(r.settings, "setting")}, ${count(r.aliases, "alias", "aliases")}, ${count(r.scripts, "script")}" +
                            (if (r.shaders > 0) ", ${count(r.shaders, "shader")}" else "") +
                            if (r.full) ", ${r.restored} saved items (todos, notes, reminders, feeds, log…)" else "",
                    when {
                        r.disabled == 1 -> "1 new script is off: turn it on in Scripts"
                        r.disabled > 1 -> "${r.disabled} new scripts are off: turn them on in Scripts"
                        else -> null
                    },
                    r.skipped.takeIf { it.isNotEmpty() }?.let { "Skipped ${it.joinToString()}" },
                    r.review.takeIf { it.isNotEmpty() }?.let { "Check these from the file: ${it.joinToString()}" },
                    if (r.newer) "This backup is from a newer Kolahal, so what this version doesn't know was skipped" else null,
                ).joinToString("\n")
            )
            // A full restore replaced the preferences and tables, so every in-memory cache is now stale.
            // Restart the app to load everything again.
            if (r.full) {
                com.mtwabpin.kolahal.LauncherApp.restart(context, afterMs = 2_000)
            }
        }
    }

    private val _backupVersion = MutableStateFlow(0)

    /** Goes up by one when the backup folder changes or a backup is written. Settings uses it to refresh its backup line. */
    val backupVersion: StateFlow<Int> = _backupVersion.asStateFlow()

    @Volatile
    private var backUpOncePicked = false

    fun askBackupFolder(thenBackUp: Boolean) {
        backUpOncePicked = thenBackUp
        host.emit(UiEvent.PickBackupFolder)
    }

    /** Returns the backup folder and the time of the last backup, for Settings. Returns null when no folder is set. */
    fun backupFolderLine(): String? {
        if (com.mtwabpin.kolahal.data.AutoBackup.folder(context) == null) return null
        // The folder is saved, but Kolahal may not be able to open it any more. For example, the backup was
        // restored on another phone, or the folder was deleted. Backups fail until the user picks it again.
        val name = com.mtwabpin.kolahal.data.AutoBackup.folderName(context)
            ?: return "Can't be written: tap to pick it again"
        val last = com.mtwabpin.kolahal.data.AutoBackup.last(context)?.let {
            "last backup " + java.text.DateFormat.getDateTimeInstance(
                java.text.DateFormat.MEDIUM,
                java.text.DateFormat.SHORT
            ).format(java.util.Date(it))
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
        if (com.mtwabpin.kolahal.data.AutoBackup.folder(context) == null) return askBackupFolder(
            true
        )
        fileOp("backup") { backUp() }
    }

    private fun backUp() {
        val name = com.mtwabpin.kolahal.data.AutoBackup.backUp(context)
        _backupVersion.value++
        notice("Backed up: $name\nFolder: ${com.mtwabpin.kolahal.data.AutoBackup.folderName(context) ?: "the backup folder"}")
    }
}
