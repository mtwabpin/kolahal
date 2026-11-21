/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.documents

import com.mtwabpin.kolahal.data.sha256Hex

/**
 * One file open in the editor. It reads the file, keeps a draft of unsaved text, and saves.
 * - It has no screen. The editor's view model calls it off the main thread and shows what it returns.
 * - It never loses text: a draft is written before a save touches the file, and is deleted only
 *   after its text is in a file, or when the user discards it.
 * - It never overwrites what the editor has not shown: [save] reads the file again first, and
 *   writes only if it is still the one the text is based on, unless told to overwrite.
 *
 * [uri] is the file's address. [source] is where files are, and [drafts] where drafts are kept.
 */
class TextDocument(
    val uri: String,
    private val source: DocumentSource,
    private val drafts: Drafts,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** The file as it was read or written: what its app said about it, and the hash of its bytes. */
    internal data class Version(val size: Long?, val modified: Long?, val hash: String)

    /** The file's name. Before the file is read, it is the last part of its address. */
    var name: String = nameIn(uri)
        private set

    /** How the file is written. A file that was never read is written as a new one: UTF-8, `\n`, a line break at the end. */
    var format = TextFormat()
        private set

    /** Whether Kolahal may write the file, as far as Android says. */
    var writable = true
        private set

    /** The text the file holds, as the editor last got it or wrote it. Null: the file was never read. */
    var fileText: String? = null
        private set

    /** The file as the editor last got it or wrote it. */
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
        data class Ready(
            val text: String,
            val saved: String,
            val restored: Boolean = false,
            val changedSince: Boolean = false
        ) : Opened

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
        return when (val read = reread()) {
            is Reread.Unavailable -> {
                // A file out of reach has no name of its own to give: the draft's is the one it had.
                if (draft == null) name = read.info.name
                Opened.Unavailable(read.why, draft?.text)
            }
            // The file is something else now. The unsaved text was written for what it was, so it is kept.
            is Reread.Refused -> {
                name = read.info.name
                version = read.version
                if (draft != null) Opened.Unavailable(
                    read.why.removeSuffix("."),
                    draft.text
                ) else Opened.Refused(read.why)
            }

            is Reread.Text -> {
                val fresh = read.fresh
                // Nothing unsaved is left in a draft that says what the file says.
                if (draft != null && draft.text == fresh.text) drafts.delete(uri)
                if (draft == null || draft.text == fresh.text) {
                    adopt(fresh)
                    Opened.Ready(fresh.text, fresh.text)
                } else {
                    // The editor shows the draft: the file is what it is saved over, but not what it is based on.
                    val wasBasedOn = base
                    adopt(fresh)
                    base = wasBasedOn
                    Opened.Ready(
                        draft.text,
                        fresh.text,
                        restored = true,
                        changedSince = wasBasedOn != fresh.version.hash
                    )
                }
            }
        }
    }

    /** The file as [reread] found it. Until [adopt], the editor's text is not based on it. */
    class Fresh internal constructor(
        val text: String,
        internal val info: DocumentInfo,
        internal val format: TextFormat,
        internal val version: Version,
    )

    sealed interface Reread {
        class Text internal constructor(val fresh: Fresh) : Reread

        /** The file is not text now, or is too big. [why] is a sentence for the user. */
        class Refused internal constructor(
            val why: String,
            internal val info: DocumentInfo,
            internal val version: Version
        ) : Reread

        /** The file can't be read now. [why] says so, without a full stop. */
        class Unavailable internal constructor(val why: String, internal val info: DocumentInfo) :
            Reread
    }

    /**
     * Reads the file again, and changes nothing: the editor's text is still based on the file as it
     * was. Call [adopt] once the editor shows what this read. If the editor can't show it (the user
     * typed meanwhile), don't: then [save] still finds that the file changed, and asks.
     */
    fun reread(): Reread {
        val info = source.info(uri)
        val bytes = try {
            source.read(uri, TextFiles.MAX_BYTES)
        } catch (e: Exception) {
            return Reread.Unavailable(reason(e), info)
        }
        val version = Version(info.size, info.modified, sha256Hex(bytes))
        return when (val decoded = TextFiles.decode(bytes)) {
            is TextFiles.Decoded.Refused -> Reread.Refused(decoded.why, info, version)
            is TextFiles.Decoded.Text -> Reread.Text(
                Fresh(
                    decoded.text,
                    info,
                    decoded.format,
                    version
                )
            )
        }
    }

    /** The editor now shows [fresh]: it is the file that the text is based on, and that a save compares with. */
    fun adopt(fresh: Fresh) {
        name = fresh.info.name
        writable = fresh.info.writable
        format = fresh.format
        fileText = fresh.text
        version = fresh.version
        base = fresh.version.hash
    }

    /**
     * Keeps [text] as the draft. A text that a save would write as what the file already holds
     * needs none, and the draft is deleted.
     */
    fun keepDraft(text: String) {
        if (format.complete(text) == fileText) drafts.delete(uri) else writeDraft(text)
    }

    private fun writeDraft(text: String) =
        drafts.write(Drafts.Draft(uri, name, text, base, clock()))

    /**
     * The same file was opened again at another address, [to]: from its folder, where Kolahal may
     * write it. The unsaved [text] moves there:
     * - It is written as [to]'s draft first, based on the same version of the file as now. Then this
     *   address's draft is deleted.
     * - When [to] opens, the text comes back. If [to] is not the file the text is based on (another
     *   file was picked), saving asks first, as for any file that changed.
     * - Unsaved text that [to] already has is never overwritten: then nothing changes, and the answer is false.
     */
    fun moveDraftTo(to: String, text: String): Boolean {
        drafts.read(to)?.let { if (it.text != text) return false }
        drafts.write(Drafts.Draft(to, name, text, base, clock()))
        drafts.delete(uri)
        return true
    }

    /** Deletes the draft: the user chose to lose the unsaved text. */
    fun discardDraft() = drafts.delete(uri)

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
     * - The text is kept as a draft first. A write that stops half way (no space, the app killed)
     *   can leave the file cut short, and the draft is then the only whole copy.
     * - The draft is deleted only after the file is written.
     */
    fun save(text: String, overwrite: Boolean = false): Saved {
        // Either the file changed, or it is as last read but the text is a draft based on an older one.
        if (!overwrite && (changedOnDisk() || (version != null && base != version?.hash))) return Saved.Changed
        val complete = format.complete(text)
        val bytes = format.encode(complete)
        try {
            writeDraft(text)
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
     * Writes [text] to another file, [to], in this file's format. This file stays as it is. The
     * text is kept as this file's draft first, and the draft is deleted once the text is in the
     * other file: the changes now live there.
     */
    fun saveCopy(to: String, text: String): Saved {
        val complete = format.complete(text)
        try {
            writeDraft(text)
            source.write(to, format.encode(complete))
        } catch (e: Exception) {
            return Saved.Failed(reason(e))
        }
        discardDraft()
        return Saved.Done(complete)
    }

    /**
     * Whether the file is no longer what the editor last got or wrote. The file is read again and
     * its bytes are compared, so the answer doesn't depend on what the file's app says about it.
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
        val bytes =
            runCatching { source.read(uri, TextFiles.MAX_BYTES) }.getOrNull() ?: return false
        if (last == null) return bytes.isNotEmpty()
        val same = sha256Hex(bytes) == last.hash
        // Remember the new size and time, so the next quick look doesn't read the file again.
        if (same) version = last.copy(size = info.size, modified = info.modified)
        return !same
    }

    private fun reason(e: Throwable): String =
        e.message?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName

    companion object {
        /** The last part of an address, as a name to show until the file's own name is known. */
        fun nameIn(uri: String): String =
            uri.substringBefore('?').trimEnd('/').substringAfterLast('/').substringAfterLast("%2F")
                .substringAfterLast("%3A").ifEmpty { "file" }
    }
}
