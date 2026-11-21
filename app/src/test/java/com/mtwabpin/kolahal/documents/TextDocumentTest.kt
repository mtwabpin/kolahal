package com.mtwabpin.kolahal.documents

import com.mtwabpin.kolahal.documents.TextDocument.Opened
import com.mtwabpin.kolahal.documents.TextDocument.Saved
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A file open in the editor, on files in memory: it opens, keeps unsaved text as a draft, saves in
 * the file's own format, and never writes over a file it has not seen.
 */
class TextDocumentTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val files = FakeFiles()
    private val drafts by lazy { Drafts(folder.newFolder("drafts")) }
    private val notes = "content://files/document/notes.txt"

    /** The file as a new editor opens it: after the editor was closed, or the app was killed. */
    private fun document(uri: String = notes) = TextDocument(uri, files, drafts)

    private fun ready(doc: TextDocument) = doc.open() as Opened.Ready

    @Test
    fun aFileOpensWithItsTextAndName() {
        files.put(notes, "one\r\ntwo\r\n")
        val doc = document()
        assertEquals("notes.txt", doc.name)
        assertEquals(Opened.Ready("one\ntwo\n", "one\ntwo\n"), doc.open())
        assertEquals(LineEnding.CRLF, doc.format.lineEnding)
        assertEquals("one\ntwo\n", doc.fileText)
        assertTrue(doc.writable)
    }

    /** Saving writes the file's own line endings, and keeps the line break at its end. */
    @Test
    fun savingKeepsTheFilesFormat() {
        files.put(notes, "one\r\ntwo\r\n")
        val doc = document()
        doc.open()
        assertEquals(Saved.Done("one\ntwo\nthree\n"), doc.save("one\ntwo\nthree"))
        assertEquals("one\r\ntwo\r\nthree\r\n", files.text(notes))
        assertEquals("one\ntwo\nthree\n", doc.fileText)
        // And again, with nothing changed in between: no alarm about its own write.
        assertEquals(Saved.Done("one\ntwo\n"), doc.save("one\ntwo\n"))
        assertEquals("one\r\ntwo\r\n", files.text(notes))
    }

    /** Unsaved text is kept as a draft, and comes back in a new editor over the file's text. */
    @Test
    fun unsavedTextComesBack() {
        files.put(notes, "one\n")
        val first = document()
        first.open()
        first.keepDraft("one\ntwo")
        assertTrue(drafts.has(notes))
        assertEquals(Opened.Ready("one\ntwo", "one\n", restored = true), document().open())
        // Saved: the draft is gone, and the next editor opens the file as it is.
        val again = document()
        again.open()
        assertEquals(Saved.Done("one\ntwo\n"), again.save("one\ntwo"))
        assertFalse(drafts.has(notes))
        assertEquals(Opened.Ready("one\ntwo\n", "one\ntwo\n"), document().open())
    }

    /** Text typed back to what the file says leaves no draft. */
    @Test
    fun textLikeTheFilesNeedsNoDraft() {
        files.put(notes, "one\n")
        val doc = document()
        doc.open()
        doc.keepDraft("one\ntwo")
        doc.keepDraft("one\n")
        assertFalse(drafts.has(notes))
        // A draft that says what the file now says (saved by another app) is dropped at open.
        doc.keepDraft("one\ntwo\n")
        files.change(notes, "one\ntwo\n")
        assertEquals(Opened.Ready("one\ntwo\n", "one\ntwo\n"), document().open())
        assertFalse(drafts.has(notes))
    }

    /** The user discards the changes: the draft goes, and the file opens as it is. */
    @Test
    fun discardedChangesDontComeBack() {
        files.put(notes, "one\n")
        val doc = document()
        doc.open()
        doc.keepDraft("changed")
        doc.discardDraft()
        assertEquals(Opened.Ready("one\n", "one\n"), document().open())
    }

    /** Another app changed the file while it was open: saving writes nothing until told to overwrite. */
    @Test
    fun aFileChangedSinceIsNotOverwritten() {
        files.put(notes, "one\n")
        val doc = document()
        doc.open()
        files.change(notes, "theirs\n")
        assertTrue(doc.changedOnDisk())
        assertEquals(Saved.Changed, doc.save("mine\n"))
        assertEquals("theirs\n", files.text(notes))
        assertEquals(Saved.Done("mine\n"), doc.save("mine\n", overwrite = true))
        assertEquals("mine\n", files.text(notes))
        assertFalse(doc.changedOnDisk())
    }

    /** A quick look trusts a file whose app gives the same size and time, and doesn't read it. Saving always reads it. */
    @Test
    fun aQuickLookDoesntReadAnUnchangedFile() {
        files.put(notes, "one\n")
        val doc = document()
        doc.open()
        val reads = files.reads
        assertFalse(doc.changedOnDisk(quick = true))
        assertEquals(reads, files.reads)
        assertEquals(Saved.Done("two\n"), doc.save("two\n"))
        assertEquals(reads + 1, files.reads)
    }

    /**
     * A file's app may still say the old size and time a moment after a change (the media store
     * does). A quick look misses that change. A save doesn't, as it reads the file.
     */
    @Test
    fun aChangeTheAppHasntReportedIsFoundAtSave() {
        val file = files.put(notes, "one\n")
        val doc = document()
        doc.open()
        // The same size, and the time not moved on.
        file.bytes = "two\n".toByteArray()
        assertFalse(doc.changedOnDisk(quick = true))
        assertTrue(doc.changedOnDisk())
        assertEquals(Saved.Changed, doc.save("mine\n"))
        assertEquals("two\n", files.text(notes))
    }

    /** A file whose app gives no time: even a quick look reads it, and finds a change of the same size. */
    @Test
    fun aChangeIsFoundWithoutATime() {
        files.put(notes, "one\n", timed = false)
        val doc = document()
        doc.open()
        assertFalse(doc.changedOnDisk(quick = true))
        files.change(notes, "two\n")
        assertTrue(doc.changedOnDisk(quick = true))
        assertEquals(Saved.Changed, doc.save("mine\n"))
        // Neither size nor time.
        files.files.getValue(notes).sized = false
        assertEquals(Saved.Changed, doc.save("mine\n"))
    }

    /** A new time with the same bytes (a sync app touched it) is no change, and isn't read twice. */
    @Test
    fun aNewTimeAloneIsNoChange() {
        val file = files.put(notes, "one\n")
        val doc = document()
        doc.open()
        file.modified = file.modified!! + 60_000
        assertFalse(doc.changedOnDisk(quick = true))
        val reads = files.reads
        assertFalse(doc.changedOnDisk(quick = true))
        assertEquals(reads, files.reads)
        assertEquals(Saved.Done("two\n"), doc.save("two\n"))
    }

    /**
     * A draft written for an older file: it comes back, the editor is told the file changed since,
     * and saving asks first. That holds after the editor is killed and opened once more.
     */
    @Test
    fun aDraftForAnOlderFileAsksBeforeItOverwrites() {
        files.put(notes, "one\n")
        val first = document()
        first.open()
        first.keepDraft("mine\n")
        files.change(notes, "theirs\n")
        val second = document()
        assertEquals(
            Opened.Ready("mine\n", "theirs\n", restored = true, changedSince = true),
            second.open()
        )
        // More typing, and killed again: the new draft is still one for the older file.
        second.keepDraft("mine too\n")
        val third = document()
        assertEquals(
            Opened.Ready("mine too\n", "theirs\n", restored = true, changedSince = true),
            third.open()
        )
        assertEquals(Saved.Changed, third.save("mine too\n"))
        assertEquals(Saved.Done("mine too\n"), third.save("mine too\n", overwrite = true))
        assertEquals(Opened.Ready("mine too\n", "mine too\n"), document().open())
    }

    /** A draft whose file is as it was comes back without that alarm, and saves at once. */
    @Test
    fun aDraftForTheSameFileSavesAtOnce() {
        files.put(notes, "one\n")
        val first = document()
        first.open()
        first.keepDraft("mine\n")
        val second = document()
        assertEquals(Opened.Ready("mine\n", "one\n", restored = true), second.open())
        assertEquals(Saved.Done("mine\n"), second.save("mine\n"))
    }

    /**
     * The file read again is not what the text is based on until the editor shows it. If the user
     * typed while it was read, it isn't shown: a save still asks, and a draft still says it is for
     * the older file.
     */
    @Test
    fun aFileReadAgainCountsOnlyOnceShown() {
        files.put(notes, "one\n")
        val doc = document()
        doc.open()
        files.change(notes, "theirs\n")
        val fresh = (doc.reread() as TextDocument.Reread.Text).fresh
        assertEquals("theirs\n", fresh.text)
        // Not shown.
        assertEquals("one\n", doc.fileText)
        assertEquals(Saved.Changed, doc.save("one\nmine\n"))
        assertEquals("theirs\n", files.text(notes))
        doc.keepDraft("one\nmine\n")
        assertEquals(
            Opened.Ready("one\nmine\n", "theirs\n", restored = true, changedSince = true),
            document().open()
        )
        // Shown: the text is based on it now, and saves at once.
        doc.adopt(fresh)
        assertEquals("theirs\n", doc.fileText)
        assertEquals(Saved.Done("theirs\nmine\n"), doc.save("theirs\nmine\n"))
    }

    /** Reading again says when the file can't be read, or is no longer text, and leaves the document as it was. */
    @Test
    fun readingAgainSaysWhatIsWrong() {
        files.put(notes, "one\n")
        val doc = document()
        doc.open()
        files.unreachable = "gone"
        assertEquals("gone", (doc.reread() as TextDocument.Reread.Unavailable).why)
        files.unreachable = null
        files.files.getValue(notes).bytes = byteArrayOf(1, 0, 2)
        assertEquals(
            "It is not a text file: it holds binary data.",
            (doc.reread() as TextDocument.Reread.Refused).why
        )
        assertEquals("one\n", doc.fileText)
        assertEquals(Saved.Changed, doc.save("two\n"))
    }

    /**
     * A save keeps the text as a draft before it touches the file. Typed and saved at once, with
     * no draft written yet: a write that fails leaves the text in a draft.
     */
    @Test
    fun aSaveKeepsADraftFirst() {
        files.put(notes, "one\n")
        val doc = document()
        doc.open()
        files.writeFails = "No space left on device"
        assertEquals(Saved.Failed("No space left on device"), doc.save("two\n"))
        assertEquals("two\n", drafts.read(notes)?.text)
        assertEquals(Opened.Ready("two\n", "one\n", restored = true), document().open())
        // Save as does the same.
        drafts.delete(notes)
        assertEquals(
            Saved.Failed("No space left on device"),
            doc.saveCopy("content://files/document/copy.txt", "three\n")
        )
        assertEquals("three\n", drafts.read(notes)?.text)
    }

    /** A text that a save would write as what the file holds needs no draft: only its last line break is missing. */
    @Test
    fun aMissingLastLineBreakNeedsNoDraft() {
        files.put(notes, "one\n")
        val doc = document()
        doc.open()
        doc.keepDraft("one")
        assertFalse(drafts.has(notes))
        // A file without one at its end: the text with one is a change.
        val bare = "content://files/document/bare.txt"
        files.put(bare, "one")
        val other = document(bare)
        other.open()
        other.keepDraft("one\n")
        assertTrue(drafts.has(bare))
    }

    /** A write that fails keeps the draft, and says why. */
    @Test
    fun aFailedSaveKeepsTheDraft() {
        files.put(notes, "one\n")
        val doc = document()
        doc.open()
        doc.keepDraft("two\n")
        files.writeFails = "No space left on device"
        assertEquals(Saved.Failed("No space left on device"), doc.save("two\n"))
        assertEquals("one\n", doc.fileText)
        assertEquals("two\n", drafts.read(notes)?.text)
    }

    /** Permission taken back, or the file's app gone: the editor is told why, and gets the draft. */
    @Test
    fun aFileOutOfReachKeepsItsDraft() {
        files.put(notes, "one\n")
        val first = document()
        first.open()
        first.keepDraft("two\n")
        files.unreachable = "Kolahal has no permission to read this file"
        val doc = document()
        assertEquals(
            Opened.Unavailable("Kolahal has no permission to read this file", "two\n"),
            doc.open()
        )
        assertEquals("notes.txt", doc.name)
        assertNull(doc.fileText)
        // Saving says why it can't, and the draft stays.
        assertEquals(Saved.Failed("Kolahal has no permission to read this file"), doc.save("two\n"))
        assertTrue(drafts.has(notes))
        // Without a draft there is nothing to show.
        assertEquals(
            Opened.Unavailable("Kolahal has no permission to read this file", null),
            document("content://files/document/other.txt").open()
        )
    }

    /**
     * The same file, opened again at an address Kolahal may write (from its folder): the unsaved
     * text moves there, and saves at once. Nothing is left at the old address.
     */
    @Test
    fun unsavedTextMovesToTheSameFileAtAnotherAddress() {
        val folder = "content://files/document/folder/notes.txt"
        files.put(notes, "one\n", writable = false)
        files.files[folder] = files.files.getValue(notes)
        val doc = document()
        doc.open()
        doc.keepDraft("two\n")
        assertTrue(doc.moveDraftTo(folder, "two\n"))
        assertFalse(drafts.has(notes))
        val moved = document(folder)
        assertEquals(Opened.Ready("two\n", "one\n", restored = true), moved.open())
        assertEquals(Saved.Done("two\n"), moved.save("two\n"))
        assertEquals("two\n", files.text(folder))
    }

    /** Another file was picked: its unsaved text is never overwritten, and a different file asks before a save. */
    @Test
    fun movedTextNeverReplacesOtherUnsavedText() {
        val other = "content://files/document/other.txt"
        files.put(notes, "one\n")
        files.put(other, "theirs\n")
        document(other).apply { open(); keepDraft("their draft\n") }
        val doc = document()
        doc.open()
        doc.keepDraft("two\n")
        assertFalse(doc.moveDraftTo(other, "two\n"))
        assertEquals("their draft\n", drafts.read(other)?.text)
        assertEquals("two\n", drafts.read(notes)?.text)
        // Without a draft there, the text moves, and the other file asks before it is overwritten.
        document(other).discardDraft()
        assertTrue(doc.moveDraftTo(other, "two\n"))
        val moved = document(other)
        assertEquals(
            Opened.Ready("two\n", "theirs\n", restored = true, changedSince = true),
            moved.open()
        )
        assertEquals(Saved.Changed, moved.save("two\n"))
    }

    /** The second way to write a file is tried only when the first can't open it, and a failure says the first one's reason. */
    @Test
    fun aSecondWayToWriteIsTriedOnlyAfterTheFirstFails() {
        val tried = mutableListOf<String>()
        fun way(name: String, fails: Boolean): () -> String = {
            tried += name
            if (fails) throw DocumentUnavailable("$name refused") else name
        }
        assertEquals("own", firstOpen(sequenceOf(way("own", false), way("media", false))))
        assertEquals(listOf("own"), tried)
        tried.clear()
        assertEquals("media", firstOpen(sequenceOf(way("own", true), way("media", false))))
        assertEquals(listOf("own", "media"), tried)
        val failed = runCatching {
            firstOpen(
                sequenceOf(
                    way("own", true),
                    way("media", true)
                )
            )
        }.exceptionOrNull()
        assertEquals("own refused", failed?.message)
    }

    /** Save as: the text goes to another file, in this file's format, and the draft goes once it is there. */
    @Test
    fun saveAsWritesAnotherFile() {
        files.put(notes, "one\r\n")
        val doc = document()
        doc.open()
        doc.keepDraft("one\ntwo")
        files.unreachable = "gone"
        val copy = "content://files/document/copy.txt"
        assertEquals(Saved.Failed("gone"), doc.saveCopy(copy, "one\ntwo"))
        assertTrue(drafts.has(notes))
        files.unreachable = null
        assertEquals(Saved.Done("one\ntwo\n"), doc.saveCopy(copy, "one\ntwo"))
        assertEquals("one\r\ntwo\r\n", files.text(copy))
        assertEquals("one\r\n", files.text(notes))
        assertFalse(drafts.has(notes))
    }

    /** The file came back within reach, with something in it that was never read: saving asks first. */
    @Test
    fun aFileNeverReadIsNotOverwrittenUnasked() {
        files.put(notes, "one\n")
        document().apply { open(); keepDraft("two\n") }
        files.unreachable = "gone"
        val doc = document()
        doc.open()
        files.unreachable = null
        files.change(notes, "theirs\n")
        assertTrue(doc.changedOnDisk())
        assertEquals(Saved.Changed, doc.save("two\n"))
        assertEquals(Saved.Done("two\n"), doc.save("two\n", overwrite = true))
    }

    /** A binary file, or one too big, doesn't open; with unsaved text from when it was text, that text does. */
    @Test
    fun aFileThatIsntTextIsRefused() {
        val picture = "content://files/document/picture.png"
        files.files[picture] = FakeFiles.File(byteArrayOf(1, 2, 0, 3), "picture.png", 1)
        assertEquals(
            Opened.Refused("It is not a text file: it holds binary data."),
            document(picture).open()
        )
        files.put(notes, "one\n")
        document().apply { open(); keepDraft("two\n") }
        files.files.getValue(notes).bytes = byteArrayOf(1, 2, 0, 3)
        val doc = document()
        assertEquals(
            Opened.Unavailable("It is not a text file: it holds binary data", "two\n"),
            doc.open()
        )
        assertEquals(Saved.Changed, doc.save("two\n"))
    }

    /** A new, empty file: what is typed is saved with a line break at its end. */
    @Test
    fun aNewFileIsSavedAsText() {
        files.put(notes, "")
        val doc = document()
        assertEquals(Opened.Ready("", ""), doc.open())
        assertEquals(Saved.Done("hello\n"), doc.save("hello"))
        assertEquals("hello\n", files.text(notes))
    }

    /** Whether Kolahal may write the file comes from the file's app. */
    @Test
    fun aReadOnlyFileSaysSo() {
        files.put(notes, "one\n", writable = false)
        val doc = document()
        doc.open()
        assertFalse(doc.writable)
    }

    @Test
    fun aNameFromTheAddress() {
        assertEquals(
            "notes.txt",
            TextDocument.nameIn("content://com.android.externalstorage.documents/document/primary%3ADocuments%2Fnotes.txt")
        )
        assertEquals(
            "todo.md",
            TextDocument.nameIn("content://com.android.externalstorage.documents/document/primary%3Atodo.md")
        )
        assertEquals("42", TextDocument.nameIn("content://media/external/downloads/42"))
        assertEquals("file", TextDocument.nameIn(""))
    }
}
