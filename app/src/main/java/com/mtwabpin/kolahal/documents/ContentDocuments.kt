/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.documents

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.util.Locale

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
        val said = flags
        val supported = when {
            said == null || said and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0 -> true
            // Such a file says it can't be written, but its media store address can be ([write]).
            else -> isMediaDocument(address) && Build.VERSION.SDK_INT >= 29
        }
        return DocumentInfo(name?.takeIf { it.isNotBlank() } ?: TextDocument.nameIn(uri), size, modified?.takeIf { it > 0 }, !readOnlyGrant(address) && supported)
    }

    /**
     * True when Kolahal was given this file to read, but not to write: what "Open with" from another
     * app often gives. Android answers only for permissions given for this one address. A file that
     * Kolahal can reach without one (its own file in the media store) has neither, and is not read-only.
     */
    private fun readOnlyGrant(address: Uri): Boolean {
        fun given(mode: Int) = context.checkUriPermission(address, Process.myPid(), Process.myUid(), mode) == PackageManager.PERMISSION_GRANTED
        return given(Intent.FLAG_GRANT_READ_URI_PERMISSION) && !given(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
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
     *
     * A media document ([isMediaDocument]) may refuse to be written. Then its media store address
     * takes the write ([mediaAddress]). It is the same file, and the editor still knows the file by
     * [uri]: its draft, the recent list and its window. The media store address is asked for at each
     * write and never kept: the permission to it ends when the phone restarts, and asking renews it.
     */
    override fun write(uri: String, bytes: ByteArray) {
        val address = Uri.parse(uri)
        val file = try {
            firstOpen(sequence<() -> ParcelFileDescriptor> {
                yield { reaching("write") { openToWrite(address) } }
                if (isMediaDocument(address)) mediaAddress(address)?.let { media -> yield { reaching("write") { openToWrite(media) } } }
            })
        } catch (e: DocumentUnavailable) {
            // A media document that can still be read was refused to write: say so, not the app's own words.
            if (isMediaDocument(address) && readable(address)) throw DocumentUnavailable(MEDIA_READ_ONLY, e) else throw e
        }
        ParcelFileDescriptor.AutoCloseOutputStream(file).use { out ->
            out.write(bytes)
            // Neither works on a pipe, which is what a cloud app may give. Then the write alone must do.
            runCatching { out.channel.truncate(bytes.size.toLong()) }
            runCatching { out.fd.sync() }
        }
    }

    private fun readable(address: Uri) = runCatching { resolver.openInputStream(address)?.use { true } ?: false }.getOrDefault(false)

    private fun openToWrite(address: Uri): ParcelFileDescriptor? =
        runCatching { resolver.openFileDescriptor(address, "wt") }.getOrNull() ?: resolver.openFileDescriptor(address, "w")

    /**
     * The media store address of a media document, or null when Android gives none (before Android 10).
     * Android also gives Kolahal the same permission there as it has to [address] (see
     * [MediaStore.getMediaUri]). Without asking, Kolahal has no permission there.
     */
    private fun mediaAddress(address: Uri): Uri? =
        if (Build.VERSION.SDK_INT >= 29) runCatching { MediaStore.getMediaUri(context, address) }.getOrNull() else null

    /**
     * Whether opening the file again from its folder may let Kolahal save it in place:
     * - a media document, which Android may give read-only;
     * - a file that another app shared to read only ("Open with").
     */
    fun reopenHelps(uri: Uri): Boolean = isMediaDocument(uri) || readOnlyGrant(uri)

    /**
     * The folder that holds the file, as an address for the file picker to start in, or null when
     * it can't be known. Only files in the phone's shared storage have a known folder: a media
     * document, or a media store address. Call it off the main thread.
     */
    fun folderOf(uri: Uri): Uri? {
        if (Build.VERSION.SDK_INT < 29) return null
        val media = when {
            uri.authority == MediaStore.AUTHORITY -> uri
            isMediaDocument(uri) -> mediaAddress(uri)
            else -> null
        } ?: return null
        return runCatching {
            resolver.query(media, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH, MediaStore.MediaColumns.VOLUME_NAME), null, null, null)?.use { c ->
                if (!c.moveToFirst()) return null
                val path = c.getString(0)?.trimEnd('/') ?: return null
                val volume = c.getString(1) ?: return null
                // The storage app names the phone's own storage "primary", and a memory card by its ID in capitals.
                val root = if (volume == MediaStore.VOLUME_EXTERNAL_PRIMARY) "primary" else volume.uppercase(Locale.ROOT)
                DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE, "$root:$path")
            }
        }.getOrNull()
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

    companion object {
        /** Android's media documents: what the file picker shows under Recent, Images, Videos, Audio and Documents. */
        const val MEDIA_DOCUMENTS = "com.android.providers.media.documents"
        private const val EXTERNAL_STORAGE = "com.android.externalstorage.documents"
        const val MEDIA_READ_ONLY = "Android opened this file read-only (from Recent)"

        /**
         * A file picked under Recent, Images, Videos, Audio or Documents in the file picker.
         * - Its app says it can't be written.
         * - Some Android versions do write it; on others, only its media store address can be written.
         * - Before Android 10, neither can: open it from its folder, or use Save as.
         */
        fun isMediaDocument(uri: Uri) = uri.authority == MEDIA_DOCUMENTS
    }
}
