package com.mtwabpin.kolahal.documents

import com.mtwabpin.kolahal.documents.RecentFiles.Entry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The editor's recent files: newest first, a short list, and what falls off it is handed back. */
class RecentFilesTest {
    private var stored: String? = null
    private val recent = RecentFiles({ stored }, { stored = it })

    private fun uri(n: Int) = "content://files/document/$n.txt"

    @Test fun theNewestIsFirst() {
        assertEquals(emptyList<Entry>(), recent.list())
        recent.add(uri(1), "1.txt", 10)
        recent.add(uri(2), "2.txt", 20)
        assertEquals(listOf(Entry(uri(2), "2.txt", 20), Entry(uri(1), "1.txt", 10)), recent.list())
        // Opened again: it moves to the front, once, with its name as it is now.
        recent.add(uri(1), "renamed.txt", 30)
        assertEquals(listOf(Entry(uri(1), "renamed.txt", 30), Entry(uri(2), "2.txt", 20)), recent.list())
        // Another list on what was stored reads the same.
        assertEquals(recent.list(), RecentFiles({ stored }, {}).list())
    }

    /** The list stays short. What falls off the end is returned, so its permission can be given back. */
    @Test fun theListStaysShort() {
        for (n in 1..RecentFiles.MAX) assertEquals(emptyList<Entry>(), recent.add(uri(n), "$n.txt", n.toLong()))
        assertEquals(listOf(Entry(uri(1), "1.txt", 1)), recent.add(uri(99), "99.txt", 99))
        assertEquals(RecentFiles.MAX, recent.list().size)
        assertEquals(uri(99), recent.list().first().uri)
        assertEquals(uri(2), recent.list().last().uri)
        // One already on the list pushes nothing off.
        assertEquals(emptyList<Entry>(), recent.add(uri(5), "5.txt", 100))
    }

    @Test fun aFileCanBeTakenOff() {
        recent.add(uri(1), "1.txt", 10)
        recent.add(uri(2), "2.txt", 20)
        assertTrue(recent.remove(uri(1)))
        assertFalse(recent.remove(uri(1)))
        assertEquals(listOf(uri(2)), recent.list().map { it.uri })
    }

    /** A damaged list reads as empty, and the next file starts it again. */
    @Test fun aDamagedListIsEmpty() {
        stored = "{not a list"
        assertEquals(emptyList<Entry>(), recent.list())
        recent.add(uri(1), "1.txt", 10)
        assertEquals(listOf(uri(1)), recent.list().map { it.uri })
    }

    /** `edit recent` lists the recent files and every file with unsaved text, newest first. */
    @Test fun unsavedFilesAreListedToo() {
        val listed = Documents.listed(
            recent = listOf(Entry(uri(2), "2.txt", 20), Entry(uri(1), "1.txt", 10)),
            drafts = listOf(Drafts.Entry(uri(3), "3.txt", 15), Drafts.Entry(uri(1), "1.txt", 30)),
        )
        assertEquals(
            listOf(
                // Its draft is newer than its last opening.
                Documents.Listed(uri(1), "1.txt", 30, unsaved = true),
                Documents.Listed(uri(2), "2.txt", 20, unsaved = false),
                // Not a recent file, but it has unsaved text.
                Documents.Listed(uri(3), "3.txt", 15, unsaved = true),
            ),
            listed,
        )
    }
}
