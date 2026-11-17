package com.mtwabpin.kolahal.documents

import com.mtwabpin.kolahal.data.sha256Hex

/**
 * One file open in the editor. It reads the file, keeps a draft of unsaved text, and saves.
 * - It has no screen. The editor's view model calls it off the main thread and shows what it returns.
 * - It never loses text: a draft is deleted only after its text is written to a file, or when the
 *   user discards it.
 * - It never overwrites what it has not seen: [save] reads the file again first, and writes only
 *   if it is still the one the text is based on, unless told to overwrite.
 *
 * [uri] is the file's address. [source] is where files are, and [drafts] where drafts are kept.
 */
class TextDocument(
    val uri: String,
    private val source: DocumentSource,
    private val drafts: Drafts,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The file as it was last read or written: what its app said about it, and the hash of its bytes. */
    private data class Version(val size: Long?, val modified: Long?, val hash: String)

    /** The file's name. Before the file is read, it is the last part of its address. */
    var name: String = nameIn(uri)
        private set

    /** How the file is written. A file that was never read is written as a new one: UTF-8, `\n`, a line break at the end. */
    var format = TextFormat()
        private set

    /** Whether Kolahal may write the file, as far as Android says. */
    var writable = true
        private set

    /** The text the file holds, as last read or written. Null: the file was never read. */
    var fileText: String? = null
        private set

    private var version: Version? = null

    /**
     * The hash of the file's bytes that the editor's text is based on.
     * - Usually it is the file as last read or written.
     * - After a draft came back whose file had changed since, it stays the older one. Then [save]
     *   asks before it overwrites, and a new draft still says which file it was written for.
     * - Null: the text is based on no file that was read.
     */
    private var base: String? = null

    sealed interface Opened {
        /**
         * The editor can open.
         * - [text]: what to show.
         * - [saved]: what the file holds. It differs from [text] when a draft came back.
         * - [restored]: [text] is a draft, with changes that were never saved.
         * - [changedSince]: the file changed after the draft's changes were made. Saving asks first.
         */
        data class Ready(val text: String, val saved: String, val restored: Boolean = false, val changedSince: Boolean = false) : Opened

        /** The file can't be edited (it is not text, or too big). [why] is a sentence for the user. */
        data class Refused(val why: String) : Opened

        /**
         * The file can't be read. [why] says so, without a full stop.
         * [draft] is the unsaved text kept from before, if there is any: the editor opens with it,
         * and it can be saved as another file.
         */
        data class Unavailable(val why: String, val draft: String?) : Opened
    }

    /** Reads the file, and brings back the draft if there is one. */
    fun open(): Opened {
        val draft = drafts.read(uri)
        draft?.let { name = it.name }
        base = draft?.base
        version = null
        fileText = null
        val bytes = try {
            val info = source.info(uri)
            name = info.name
            writable = info.writable
            source.read(uri, TextFiles.MAX_BYTES).also { version = Version(info.size, info.modified, sha256Hex(it)) }
        } catch (e: Exception) {
            return Opened.Unavailable(reason(e), draft?.text)
        }
        return when (val decoded = TextFiles.decode(bytes)) {
            // The file is something else now. The unsaved text was written for what it was, so it is kept.
            is TextFiles.Decoded.Refused -> if (draft != null) Opened.Unavailable(decoded.why.removeSuffix("."), draft.text) else Opened.Refused(decoded.why)
            is TextFiles.Decoded.Text -> {
                format = decoded.format
                fileText = decoded.text
                // Nothing unsaved is left in a draft that says what the file says.
                if (draft != null && draft.text == decoded.text) drafts.delete(uri)
                if (draft == null || draft.text == decoded.text) {
                    base = version?.hash
                    Opened.Ready(decoded.text, decoded.text)
                } else {
                    Opened.Ready(draft.text, decoded.text, restored = true, changedSince = base != version?.hash)
                }
            }
        }
    }

    /** Keeps [text] as the draft, or deletes the draft when [text] is what the file holds. */
    fun keepDraft(text: String) {
        if (text == fileText) drafts.delete(uri) else drafts.write(Drafts.Draft(uri, name, text, base, clock()))
    }

    /** Deletes the draft: the user chose to lose the unsaved text. What the editor shows next is based on the file as it is. */
    fun discardDraft() {
        drafts.delete(uri)
        base = version?.hash
    }

    sealed interface Saved {
        /** Written. [text] is what the file now holds: a line break may have been added at the end. */
        data class Done(val text: String) : Saved

        /** The file is not the one the text is based on, so nothing was written. Saving again with `overwrite` writes it. */
        data object Changed : Saved

        /** Nothing was written, or not all of it. [why] is a sentence for the user, without a full stop. The draft is kept. */
        data class Failed(val why: String) : Saved
    }

    /**
     * Writes [text] to the file, in the file's own format.
     * - If the file is not the one the text is based on, nothing is written and the answer is
     *   [Saved.Changed], unless [overwrite] is set.
     * - The draft is deleted only after the file is written.
     */
    fun save(text: String, overwrite: Boolean = false): Saved {
        // Either the file changed, or it is as last read but the text is a draft based on an older one.
        if (!overwrite && (changedOnDisk() || (version != null && base != version?.hash))) return Saved.Changed
        val complete = format.complete(text)
        val bytes = format.encode(complete)
        try {
            source.write(uri, bytes)
        } catch (e: Exception) {
            return Saved.Failed(reason(e))
        }
        val info = source.info(uri)
        version = Version(info.size, info.modified, sha256Hex(bytes)).also { base = it.hash }
        fileText = complete
        drafts.delete(uri)
        return Saved.Done(complete)
    }

    /**
     * Writes [text] to another file, [to], in this file's format. This file stays as it is. Its
     * draft is deleted once the text is in the other file: the changes now live there.
     */
    fun saveCopy(to: String, text: String): Saved {
        val complete = format.complete(text)
        try {
            source.write(to, format.encode(complete))
        } catch (e: Exception) {
            return Saved.Failed(reason(e))
        }
        discardDraft()
        return Saved.Done(complete)
    }

    /**
     * Whether the file is no longer what was read or written last. The file is read again and its
     * bytes are compared, so the answer doesn't depend on what the file's app says about it.
     * - With [quick], a file whose app gives the same size and time as before counts as unchanged,
     *   and is not read. Use it to look often (each time the editor comes back on screen). It can
     *   miss a change: some apps update the size and time a moment after the file. [save] never
     *   looks this way.
     * - A file that can't be read now counts as unchanged: there is nothing to lose there, and a
     *   write will say what is wrong.
     * - A file that was never read counts as changed if it holds anything.
     */
    fun changedOnDisk(quick: Boolean = false): Boolean {
        val last = version
        val info = source.info(uri)
        if (quick && last != null && last.modified != null && info.modified == last.modified && info.size == last.size) return false
        val bytes = runCatching { source.read(uri, TextFiles.MAX_BYTES) }.getOrNull() ?: return false
        if (last == null) return bytes.isNotEmpty()
        val same = sha256Hex(bytes) == last.hash
        // Remember the new size and time, so the next quick look doesn't read the file again.
        if (same) version = last.copy(size = info.size, modified = info.modified)
        return !same
    }

    private fun reason(e: Throwable): String = e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName

    companion object {
        /** The last part of an address, as a name to show until the file's own name is known. */
        fun nameIn(uri: String): String =
            uri.substringBefore('?').trimEnd('/').substringAfterLast('/').substringAfterLast("%2F").substringAfterLast("%3A").ifEmpty { "file" }
    }
}
