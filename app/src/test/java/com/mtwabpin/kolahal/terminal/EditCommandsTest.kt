package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.commands.InstantRun
import com.mtwabpin.kolahal.documents.Documents.Listed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** `edit <file>`: which of the listed files a name opens, and what a tap on a line of `edit recent` runs. */
class EditCommandsTest {
    private fun file(name: String, n: Int) = Listed("content://files/document/$n", name, 100L - n, unsaved = false)

    // Newest first, as `edit recent` lists them.
    private val files = listOf(file("notes.txt", 1), file("Todo.md", 2), file("notes.txt", 3), file("my notes.txt", 4), file("new", 5), file("a  b.txt", 6))

    @Test fun aNameOpensTheNewestFileOfThatName() {
        assertEquals(files[0], EditCommands.find(files, "notes.txt"))
        // In another case, when no file has it exactly.
        assertEquals(files[1], EditCommands.find(files, "todo.md"))
        // A start of the name, when only one file starts with it.
        assertEquals(files[1], EditCommands.find(files, "to"))
        assertEquals(files[3], EditCommands.find(files, "my"))
        assertNull(EditCommands.find(files, "n"))
        assertNull(EditCommands.find(files, "other.txt"))
        assertNull(EditCommands.find(emptyList(), "notes.txt"))
    }

    /** A tap runs `edit <name>` where that opens the line's own file, and `edit <address>` where it wouldn't. */
    @Test fun aTapOpensTheLinesOwnFile() {
        assertEquals("edit notes.txt", EditCommands.tapCommand(files, 0))
        assertEquals("edit Todo.md", EditCommands.tapCommand(files, 1))
        // An earlier line has the same name.
        assertEquals("edit content://files/document/3", EditCommands.tapCommand(files, 2))
        assertEquals("edit my notes.txt", EditCommands.tapCommand(files, 3))
        // `edit new` makes a new file.
        assertEquals("edit content://files/document/5", EditCommands.tapCommand(files, 4))
        // Two spaces don't come back as typed.
        assertEquals("edit content://files/document/6", EditCommands.tapCommand(files, 5))
    }

    /** What a tap runs reads back, as the command reads its words, as the name it was made from. */
    @Test fun aTapsNameReadsBackAsTheName() {
        for (i in files.indices) {
            val words = CommandParsing.tokenize(EditCommands.tapCommand(files, i).removePrefix("edit "))
            val target = words.joinToString(" ")
            val opened = if (target.startsWith("content://")) files.first { it.uri == target } else EditCommands.find(files, target)
            assertEquals(files[i], opened)
        }
    }

    /** `edit` leaves the launcher, so it never runs without Enter, and a script can't send it. */
    @Test fun editNeverRunsByItself() {
        assertTrue(InstantRun.isSensitive("edit"))
        assertTrue(InstantRun.isSensitive("edit notes.txt"))
    }
}
