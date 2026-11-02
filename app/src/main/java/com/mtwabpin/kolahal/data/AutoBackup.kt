package com.mtwabpin.kolahal.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Backups on their own (`backup.auto`): the [Backup] file, `kolahal-backup-2026-10-27.json`,
 * written every day or every week to a folder you pick, keeping the newest `backup.keep`. One
 * written the same day replaces that day's.
 */
object AutoBackup {
    private const val PREFS = "auto_backup"
    private const val KEY_FOLDER = "folder"
    private const val KEY_LAST = "last"
    private const val WORK = "auto-backup"
    private val NAME = Regex("""^kolahal-backup-\d{4}-\d{2}-\d{2}\.json$""")

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun folder(context: Context): Uri? = prefs(context).getString(KEY_FOLDER, null)?.let(Uri::parse)

    /** The folder's name, as the file picker showed it. */
    fun folderName(context: Context): String? = folder(context)?.let { runCatching { DocumentFile.fromTreeUri(context, it)?.name }.getOrNull() }

    fun setFolder(context: Context, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(tree, flags)
        val old = folder(context)
        prefs(context).edit { putString(KEY_FOLDER, tree.toString()) }
        // The previous folder's grant goes, unless the script folder is there too.
        old?.takeIf { it != tree }?.let { com.mtwabpin.kolahal.script.Files.releaseFolder(context, it) }
    }

    /** When the last backup was written, or null. */
    fun last(context: Context): Long? = prefs(context).getLong(KEY_LAST, 0L).takeIf { it > 0 }

    /** Schedules the backups `backup.auto` asks for, or cancels them. */
    fun sync(context: Context) {
        val wm = WorkManager.getInstance(context)
        val days = when (Config.get(context)[Keys.BACKUP_AUTO]) {
            "daily" -> 1L
            "weekly" -> 7L
            else -> return wm.cancelUniqueWork(WORK).let { }
        }
        wm.enqueueUniquePeriodicWork(
            WORK, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(days, TimeUnit.DAYS).build(),
        )
    }

    /** Writes today's backup to the folder and drops the oldest past `backup.keep`. Returns the file's name. */
    fun backUp(context: Context): String {
        val tree = folder(context) ?: throw IllegalStateException("no backup folder: run 'backup dir'")
        val dir = DocumentFile.fromTreeUri(context, tree)
        if (dir == null || !dir.canWrite()) throw IllegalStateException("the backup folder can't be written: run 'backup dir'")
        // Before any file exists: a backup too big to save leaves nothing behind.
        val text = Backup(context).text()
        val saved = saveTo(dir, "kolahal-backup-${LocalDate.now()}.json", Config.get(context)[Keys.BACKUP_KEEP]) { uri ->
            com.mtwabpin.kolahal.script.Files.writeText(context, uri, text)
        }
        prefs(context).edit { putLong(KEY_LAST, System.currentTimeMillis()) }
        return saved
    }

    /**
     * Writes [name] in [dir] with [write], then drops the oldest past [keep]. It goes to a file of
     * its own first, so a failure leaves nothing new and costs nothing: writing over the day's
     * earlier backup would empty that before the first byte.
     */
    internal fun saveTo(dir: DocumentFile, name: String, keep: Int, write: (Uri) -> Unit): String {
        val tempName = name.removeSuffix(".json") + ".tmp.json"
        dir.findFile(tempName)?.delete()
        val temp = dir.createFile("application/json", tempName) ?: throw IllegalStateException("can't create $name")
        try {
            write(temp.uri)
        } catch (e: Exception) {
            runCatching { temp.delete() }
            throw e
        }
        val earlier = dir.findFile(name)
        if ((earlier != null && !earlier.delete()) || !temp.renameTo(name)) {
            // A provider that can't delete or rename: written again under its name, the copy kept until that's done.
            val file = dir.findFile(name) ?: dir.createFile("application/json", name) ?: throw IllegalStateException("can't create $name")
            write(file.uri)
            temp.delete()
        }
        val files = dir.listFiles().filter { NAME.matches(it.name.orEmpty()) }.associateBy { it.name.orEmpty() }
        expired(files.mapValues { it.value.length() }, name, keep).forEach { old -> runCatching { files[old]?.delete() } }
        return name
    }

    /**
     * Of the automatic backups in a folder, by name with the size each reads as (their names sort
     * by date), those past the newest [keep], [written] being one of those kept. One that reads as
     * empty doesn't count, as an earlier failure could have left it and it would push a real one
     * out. It doesn't go for that alone: a provider that reports no size makes every file read so.
     */
    internal fun expired(sizes: Map<String, Long>, written: String, keep: Int): List<String> {
        var counted = 0
        return buildList {
            for (name in sizes.keys.filter(NAME::matches).sortedDescending()) {
                when {
                    name == written -> counted++
                    counted >= keep -> add(name)
                    sizes.getValue(name) > 0 -> counted++
                }
            }
        }
    }
}

/** Writes an automatic backup; a failure is a line in the log that says how to fix it. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { AutoBackup.backUp(applicationContext) }.onFailure {
            LogRepository.get(applicationContext).add(
                LogKind.ERROR, "Automatic backup failed: ${it.message ?: it.javaClass.simpleName} · tap to pick the folder",
                ctx = LogContext.of(LogContext.RUN to "backup dir"),
            )
        }
        return Result.success()
    }
}
