package com.mtwabpin.kolahal.script

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns

/** Storage Access Framework helpers shared by backup, script import/export and folder sync. */
object Files {
    /** Large enough for any script; guards against picking a huge file by mistake. */
    const val MAX_BYTES = 2 * 1024 * 1024

    /** What a full backup may weigh: the log, feed items, script stores and an imported font. */
    const val MAX_BACKUP_BYTES = 64 * 1024 * 1024

    fun readText(context: Context, uri: Uri, maxBytes: Int = MAX_BYTES): String {
        val stream = context.contentResolver.openInputStream(uri) ?: throw IllegalStateException("can't open the file")
        val out = java.io.ByteArrayOutputStream()
        if (!stream.use { copyLimited(it, out, maxBytes.toLong()) }) throw IllegalStateException("File is larger than ${maxBytes / (1024 * 1024)} MB")
        return out.toString(Charsets.UTF_8.name())
    }

    /**
     * Copies [input] to [out] while it holds at most [max] bytes: false, with the copy stopped, when
     * there's more. For files, feeds and pictures, whose stated length can be missing or wrong.
     * A loop of its own: InputStream.readNBytes needs API 33.
     */
    fun copyLimited(input: java.io.InputStream, out: java.io.OutputStream, max: Long): Boolean {
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) return true
            total += n
            if (total > max) return false
            out.write(buffer, 0, n)
        }
    }

    /** The picked file's name, as its provider gives it; null when it gives none. */
    fun displayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** Some providers reject truncate mode "wt"; fall back to "w". */
    fun writeText(context: Context, uri: Uri, text: String) {
        val resolver = context.contentResolver
        val stream = runCatching { resolver.openOutputStream(uri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(uri, "w")
            ?: throw IllegalStateException("can't write the file")
        stream.use { it.write(text.toByteArray()) }
    }

    /**
     * Gives up the grant to [old], a folder picked before, unless the backup or the script folder
     * still uses it: one folder can be both, with one grant between them. Persisted grants are
     * capped per app. Call it once the new folder is saved.
     */
    fun releaseFolder(context: Context, old: Uri) {
        if (inUse(context, old)) return
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        runCatching { context.contentResolver.releasePersistableUriPermission(old, flags) }
    }

    /** Whether [folder] is the backup folder or the script folder. */
    internal fun inUse(context: Context, folder: Uri): Boolean =
        folder == com.mtwabpin.kolahal.data.AutoBackup.folder(context) || folder == ScriptFolder(context).uri
}
