package com.mtwabpin.kolahal.documents

import com.mtwabpin.kolahal.data.sha256Hex
import java.io.File
import org.json.JSONObject

/**
 * Unsaved text of the editor, kept in Kolahal's own storage. The editor writes a draft while you
 * type and when you leave it. So the text is still there when Android has closed the editor, and
 * when the file itself can no longer be reached.
 *
 * One file per document in [dir], named by a hash of the document's address:
 * - the first line is a JSON header: the address, the file's name, what the text was based on, and the time;
 * - the rest is the text, as the editor holds it (`\n` line breaks).
 *
 * A draft is deleted only when its text is saved or the user discards it.
 */
class Drafts(private val dir: File) {
    /**
     * - [base]: the hash of the file's bytes when this text was last the same as the file
     *   ([TextDocument]). If the file's hash is different now, the file changed since. Null: the
     *   file was never read.
     */
    data class Draft(val uri: String, val name: String, val text: String, val base: String?, val time: Long)

    /** A draft without its text, for a list. */
    data class Entry(val uri: String, val name: String, val time: Long)

    private fun file(uri: String) = File(dir, sha256Hex(uri.toByteArray()).take(32) + ".draft")

    private fun header(d: Draft) = JSONObject().put("uri", d.uri).put("name", d.name).put("time", d.time).apply { d.base?.let { put("base", it) } }

    /** Writes [draft], replacing the document's earlier one. The file is written whole or not at all. */
    @Synchronized
    fun write(draft: Draft) {
        dir.mkdirs()
        val target = file(draft.uri)
        val tmp = File(dir, target.name + ".tmp")
        tmp.writeText(header(draft).toString() + "\n" + draft.text)
        if (!tmp.renameTo(target)) {
            tmp.delete()
            throw java.io.IOException("can't keep the draft")
        }
    }

    /** The document's draft, or null when it has none (or its file can't be read). */
    @Synchronized
    fun read(uri: String): Draft? = runCatching {
        val all = file(uri).takeIf { it.isFile }?.readText() ?: return null
        val h = JSONObject(all.substringBefore('\n'))
        // Two addresses with the same hash: not this document's draft.
        if (h.getString("uri") != uri) return null
        Draft(uri, h.getString("name"), all.substringAfter('\n', ""), h.optString("base").ifEmpty { null }, h.getLong("time"))
    }.getOrNull()

    @Synchronized
    fun has(uri: String): Boolean = file(uri).isFile

    @Synchronized
    fun delete(uri: String) {
        file(uri).delete()
    }

    /** Every draft, newest first. */
    @Synchronized
    fun list(): List<Entry> = dir.listFiles { f -> f.isFile && f.name.endsWith(".draft") }.orEmpty().mapNotNull { f ->
        runCatching {
            val h = JSONObject(f.bufferedReader().use { it.readLine() })
            Entry(h.getString("uri"), h.getString("name"), h.getLong("time"))
        }.getOrNull()
    }.sortedByDescending { it.time }
}
