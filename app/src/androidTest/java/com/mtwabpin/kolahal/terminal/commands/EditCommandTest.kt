package com.mtwabpin.kolahal.terminal.commands

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.LogLinesSince
import com.mtwabpin.kolahal.TerminalUiStub
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.documents.Documents
import com.mtwabpin.kolahal.documents.Drafts
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `edit` at the prompt: it asks for the picker, a new file or one of the listed files, and the editor's window does the rest. */
@RunWith(AndroidJUnit4::class)
class EditCommandTest {
    @get:Rule
    val lines = LogLinesSince()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val recent = Documents.recent(context)
    private val drafts = Documents.drafts(context)

    /** What the command asked the screen for. */
    private val asked = mutableListOf<String>()
    private val commands = CommandRunner(context, object : TerminalUiStub() {
        override fun pickTextFile() {
            asked += "pick"
        }

        override fun newTextFile(name: String) {
            asked += "new $name"
        }

        override fun openTextFile(uri: String) {
            asked += "open $uri"
        }
    })

    // Names no real file on the phone has.
    private val notes = "content://com.mtwabpin.kolahal.test.files/ktest-notes.txt"
    private val todo = "content://com.mtwabpin.kolahal.test.files/ktest%20todo.md"
    private val scratch = "content://com.mtwabpin.kolahal.test.files/ktest-scratch.txt"

    /** The phone's own recent files, set aside for the test. */
    private var before = emptyList<com.mtwabpin.kolahal.documents.RecentFiles.Entry>()

    @Before
    fun setUp() {
        before = recent.list()
        before.forEach { recent.remove(it.uri) }
    }

    @After
    fun tearDown() {
        listOf(notes, todo, scratch).forEach { recent.remove(it); drafts.delete(it) }
        before.reversed().forEach { recent.add(it.uri, it.name, it.opened) }
    }

    @Test
    fun editAsksForThePickerOrANewFile() {
        commands.run("edit")
        commands.run("edit new")
        commands.run("edit new shopping list.md")
        assertEquals(listOf("pick", "new untitled.txt", "new shopping list.md"), asked)
    }

    /** The recent files and those with unsaved text, newest first. A tap opens the line's file, and so does its name. */
    @Test
    fun recentListsWhatCanBeOpenedAgain() {
        // The phone may hold real drafts: only this test's lines are looked at.
        recent.add(notes, "ktest-notes.txt", 1_000)
        recent.add(todo, "ktest todo.md", 2_000)
        drafts.write(Drafts.Draft(scratch, "ktest-scratch.txt", "unsaved", null, 3_000))
        val mine = commands.lines("edit recent").filter { it.text.startsWith("ktest") }
        assertEquals(
            listOf("ktest-scratch.txt", "ktest todo.md", "ktest-notes.txt"),
            mine.map { it.text })
        assertEquals(
            listOf("edit ktest-scratch.txt", "edit ktest todo.md", "edit ktest-notes.txt"),
            mine.map { it.ctx.values[LogContext.RUN] })
        assertTrue(mine[0].ctx.values.getValue(LogContext.DESC).startsWith("unsaved changes · "))
        // By its name, in another case, and by the start of its name.
        commands.run("edit ktest todo.md")
        commands.run("edit KTEST-NOTES.TXT")
        commands.run("edit ktest-scr")
        assertEquals(listOf("open $todo", "open $notes", "open $scratch"), asked)
    }

    @Test
    fun aNameThatIsntListedSaysSo() {
        val said = commands.lines("edit kolahal-test-no-such-file.txt").single()
        assertEquals(LogKind.ERROR, said.kind)
        assertEquals(
            "No recent file 'kolahal-test-no-such-file.txt'\nTap to pick a file",
            said.text
        )
        assertEquals("edit", said.ctx.values[LogContext.RUN])
        assertEquals(emptyList<String>(), asked)
    }

    /** An address opens as it is, listed or not. */
    @Test
    fun anAddressOpensAsItIs() {
        commands.run("edit content://some.app/files/a.txt")
        assertEquals(listOf("open content://some.app/files/a.txt"), asked)
    }
}
