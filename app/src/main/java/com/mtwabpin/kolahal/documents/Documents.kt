/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.documents

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.core.net.toUri
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.data.Stores
import java.io.File

/**
 * The editor's files on this phone, in one place: where they are read and written, where drafts
 * and the recent list are kept, and the permissions that let a recent file be opened again.
 */
object Documents {
    /** The folder of drafts under Kolahal's files. Android's cloud backup leaves it out (res/xml): a draft is private text. */
    const val DRAFTS_DIR = "editor-drafts"
    private const val KEY_RECENT = "recent"
    private const val PERMISSIONS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

    fun source(context: Context) = ContentDocuments(context)

    // One of each in the process: the launcher (`edit recent`) and every open editor share them.
    private object DraftStore :
        Singleton<Drafts>({ Drafts(File(it.filesDir, DRAFTS_DIR)) }, mainOnly = true)

    private object RecentStore : Singleton<RecentFiles>({ context ->
        val prefs = Stores.EDITOR.open(context)
        RecentFiles(
            { prefs.getString(KEY_RECENT, null) },
            { prefs.edit { putString(KEY_RECENT, it) } })
    }, mainOnly = true)

    fun drafts(context: Context): Drafts = DraftStore.get(context)

    fun recent(context: Context): RecentFiles = RecentStore.get(context)

    /** One file as the editor works with it. */
    fun open(context: Context, uri: Uri) =
        TextDocument(uri.toString(), source(context), drafts(context))

    /**
     * Keeps the permission to [uri] past this run, if Android offers that: to read and write, or
     * else to read. The file picker offers it; an app that shares a file ("Open with") usually
     * doesn't. Returns whether Kolahal may open the file again later.
     */
    fun keepPermission(context: Context, uri: Uri): Boolean {
        val resolver = context.contentResolver
        runCatching { resolver.takePersistableUriPermission(uri, PERMISSIONS) }
            .onFailure {
                runCatching {
                    resolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }
            }
        return runCatching { resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission } }.getOrDefault(
            false
        )
    }

    /**
     * Call when the editor has opened [uri]. If Kolahal may open the file again later, it goes first
     * in the recent list. A file that falls off the list loses its kept permission, because Android
     * keeps only so many; a file with a draft keeps it, so the draft can still be saved.
     */
    fun remember(context: Context, uri: Uri, name: String) {
        if (!keepPermission(context, uri)) return
        val drafts = drafts(context)
        recent(context).add(uri.toString(), name, System.currentTimeMillis())
            .filterNot { drafts.has(it.uri) }
            .forEach { release(context, it.uri) }
    }

    /** Takes [uri] off the recent list and gives its permission back: the file is gone, or can't be opened any more. */
    fun forget(context: Context, uri: String) {
        if (recent(context).remove(uri)) release(context, uri)
    }

    private fun release(context: Context, uri: String) {
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                uri.toUri(),
                PERMISSIONS
            )
        }
    }

    /** A file that `edit recent` lists. [unsaved]: it has a draft. */
    data class Listed(val uri: String, val name: String, val time: Long, val unsaved: Boolean)

    /**
     * The recent files, and every file with unsaved text even if it is not a recent one: no draft
     * is out of reach. Newest first.
     */
    fun listed(context: Context): List<Listed> =
        listed(recent(context).list(), drafts(context).list())

    internal fun listed(recent: List<RecentFiles.Entry>, drafts: List<Drafts.Entry>): List<Listed> {
        val draftOf = drafts.associateBy { it.uri }
        val known = recent.map { r ->
            Listed(
                r.uri,
                r.name,
                maxOf(r.opened, draftOf[r.uri]?.time ?: 0),
                r.uri in draftOf
            )
        }
        val others = drafts.filter { d -> recent.none { it.uri == d.uri } }
            .map { Listed(it.uri, it.name, it.time, true) }
        return (known + others).sortedByDescending { it.time }
    }
}
