package com.mtwabpin.kolahal.documents

import com.mtwabpin.kolahal.documents.Drafts.Draft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Unsaved text kept in Kolahal's storage: it comes back as written, for its own file only, until it is deleted. */
class DraftsTest {
    @get:Rule val folder = TemporaryFolder()

    private val dir by lazy { java.io.File(folder.root, "editor-drafts") }
    private val drafts by lazy { Drafts(dir) }
    private val notes = "content://files/document/notes.txt"
    private val todo = "content://files/document/todo.md"

    /** The text comes back as written: line breaks, an empty first line, other scripts, and a name with a quote in it. */
    @Test fun aDraftComesBackAsWritten() {
        for (text in listOf("one\ntwo\n", "", "\n\nstarts with empty lines", "日本語 🙂 \"quoted\" {json: like}\n", "x".repeat(100_000))) {
            val draft = Draft(notes, "my \"notes\".txt", text, "abc123", 42)
            drafts.write(draft)
            assertEquals(draft, drafts.read(notes))
            // A new store on the same folder, as after the app was killed.
            assertEquals(draft, Drafts(dir).read(notes))
        }
    }

    /** A draft for a file that was never read has no base. */
    @Test fun aDraftWithoutABase() {
        drafts.write(Draft(notes, "notes.txt", "one", null, 1))
        assertNull(drafts.read(notes)!!.base)
    }

    @Test fun eachFileHasItsOwnDraft() {
        assertNull(drafts.read(notes))
        assertFalse(drafts.has(notes))
        drafts.write(Draft(notes, "notes.txt", "one", null, 1))
        drafts.write(Draft(todo, "todo.md", "two", null, 2))
        // A later draft takes the earlier one's place.
        drafts.write(Draft(notes, "notes.txt", "one more", null, 3))
        assertEquals("one more", drafts.read(notes)?.text)
        assertEquals("two", drafts.read(todo)?.text)
        drafts.delete(notes)
        assertFalse(drafts.has(notes))
        assertTrue(drafts.has(todo))
        // Deleting what isn't there is fine.
        drafts.delete(notes)
    }

    /** The list is newest first, and needs only each draft's first line. */
    @Test fun theListIsNewestFirst() {
        assertEquals(emptyList<Drafts.Entry>(), drafts.list())
        drafts.write(Draft(notes, "notes.txt", "one\ntwo", null, 10))
        drafts.write(Draft(todo, "todo.md", "", "base", 20))
        assertEquals(listOf(Drafts.Entry(todo, "todo.md", 20), Drafts.Entry(notes, "notes.txt", 10)), drafts.list())
    }

    /** A file that isn't a draft, or a damaged one, reads as no draft and doesn't break the list. */
    @Test fun aDamagedDraftIsNoDraft() {
        drafts.write(Draft(notes, "notes.txt", "one", null, 1))
        val file = dir.listFiles()!!.single()
        file.writeText("not json\nthe text")
        assertNull(drafts.read(notes))
        assertEquals(emptyList<Drafts.Entry>(), drafts.list())
        // Only whole drafts are in the folder: nothing half written is left behind.
        drafts.write(Draft(notes, "notes.txt", "one", null, 1))
        assertEquals(1, dir.listFiles()!!.size)
    }
}
