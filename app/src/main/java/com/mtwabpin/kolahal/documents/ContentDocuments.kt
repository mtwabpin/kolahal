package com.mtwabpin.kolahal.documents

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException

/**
 * The editor's files on the phone: read and written through Android's content resolver, with the
 * permission that the file picker or the app that shared the file gave. Kolahal has no storage
 * permission of its own.
 */
class ContentDocuments(context: Context) : DocumentSource {
    private val context = context.applicationContext
    private val resolver get() = context.contentResolver

    /**
     * Name, size and time come from the file's app, which may leave any of them out.
     * - Document providers (the file picker's) give the time as `last_modified`, in milliseconds.
     * - The media store gives `date_modified`, in seconds.
     */
    override fun info(uri: String): DocumentInfo {
        val address = Uri.parse(uri)
        var name: String? = null
        var size: Long? = null
        var modified: Long? = null
        var flags: Int? = null
        runCatching {
            resolver.query(address, null, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use
                fun column(name: String) = c.getColumnIndex(name).takeIf { it >= 0 && !c.isNull(it) }
                name = column(OpenableColumns.DISPLAY_NAME)?.let(c::getString)
                size = column(OpenableColumns.SIZE)?.let(c::getLong)
                modified = column(DocumentsContract.Document.COLUMN_LAST_MODIFIED)?.let(c::getLong)
                    ?: column("date_modified")?.let { c.getLong(it) * 1000 }
                flags = column(DocumentsContract.Document.COLUMN_FLAGS)?.let(c::getInt)
            }
        }
        val granted = context.checkUriPermission(address, Process.myPid(), Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ==
            PackageManager.PERMISSION_GRANTED
        val supported = flags?.let { it and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0 } ?: true
        return DocumentInfo(name?.takeIf { it.isNotBlank() } ?: TextDocument.nameIn(uri), size, modified?.takeIf { it > 0 }, granted && supported)
    }

    override fun read(uri: String, max: Int): ByteArray {
        val stream = reaching("read") { resolver.openInputStream(Uri.parse(uri)) }
        val out = ByteArrayOutputStream()
        stream.use { input ->
            val buffer = ByteArray(16 * 1024)
            // One byte past the limit is enough to know that the file is bigger.
            while (out.size() <= max) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
            }
        }
        return out.toByteArray()
    }

    /**
     * Opens the file to write and truncate ("wt"). Some apps refuse that mode, so it falls back to "w".
     * After writing, the file is cut to the new length where Android allows: after a "w" that
     * didn't truncate, the end of a longer old text would otherwise stay.
     */
    override fun write(uri: String, bytes: ByteArray) {
        val address = Uri.parse(uri)
        val file = reaching("write") {
            runCatching { resolver.openFileDescriptor(address, "wt") }.getOrNull() ?: resolver.openFileDescriptor(address, "w")
        }
        ParcelFileDescriptor.AutoCloseOutputStream(file).use { out ->
            out.write(bytes)
            // Neither works on a pipe, which is what a cloud app may give. Then the write alone must do.
            runCatching { out.channel.truncate(bytes.size.toLong()) }
            runCatching { out.fd.sync() }
        }
    }

    /** Runs [open], and says in plain words why the file can't be reached when it can't. */
    private fun <T : Any> reaching(what: String, open: () -> T?): T = try {
        open() ?: throw DocumentUnavailable("The app that holds this file gave nothing to $what")
    } catch (e: SecurityException) {
        throw DocumentUnavailable("Kolahal has no permission to $what this file", e)
    } catch (e: FileNotFoundException) {
        throw DocumentUnavailable("This file can't be opened to $what: it was moved or deleted, or its app doesn't allow it", e)
    } catch (e: DocumentUnavailable) {
        throw e
    } catch (e: RuntimeException) {
        // An unknown address, or an app that failed: IllegalArgumentException, NullPointerException, …
        throw DocumentUnavailable("The app that holds this file is gone or didn't answer", e)
    }

    /**
     * Why Kolahal won't open [uri] in the editor, or null when it will.
     * - Only `content:` addresses: Kolahal has no storage permission to read a `file:` one, and such
     *   an address could point into Kolahal's own storage.
     * - Not Kolahal's own files. Another app could otherwise send an address of them, and have the
     *   editor show or change what only Kolahal may touch.
     */
    fun refusal(uri: Uri): String? {
        if (uri.scheme != "content") return "Kolahal opens files from a file picker or another app (content: addresses) only"
        val owner = uri.authority?.let { runCatching { context.packageManager.resolveContentProvider(it, 0) }.getOrNull() }
        return if (owner?.packageName == context.packageName) "That is one of Kolahal's own files" else null
    }
}
