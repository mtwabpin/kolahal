package com.mtwabpin.kolahal.terminal

import android.app.Application
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.LogKept
import com.mtwabpin.kolahal.data.Db
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.waitUntil
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** How home's page of the log follows the log: older pages, moved and deleted lines. */
@RunWith(AndroidJUnit4::class)
class LogPagingTest {
    @get:Rule val vms = com.mtwabpin.kolahal.TestViewModels()
    // The fresh screen a test here hides and shows again: the log's marks come back as they were.
    @get:Rule val logKept = LogKept()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val app = instrumentation.targetContext.applicationContext as Application
    private val logs = LogRepository.get(app)
    private val token = "pg${System.nanoTime()}"
    private var first = Long.MAX_VALUE

    @After fun tearDown() {
        // By hand, not line by line: a few hundred lines were added.
        if (first != Long.MAX_VALUE) Db.get(app).writableDatabase.delete("logs", "id >= ?", arrayOf(first.toString()))
        if (hid) logs.unhide()
    }

    private var hid = false

    private fun viewModel() = vms.terminal(app)

    private fun add(text: String): Long = logs.add(LogKind.OUTPUT, text).also { first = minOf(first, it) }

    @Test fun scrollingBackAddsOlderLinesAndTheNewestStays() {
        repeat(450) { add("$token $it") }
        val vm = viewModel()
        waitUntil("the newest page") { vm.entries.value.lastOrNull()?.text == "$token 449" }
        val page = vm.entries.value
        assertEquals(400, page.size)
        assertEquals("$token 50", page.first().text)
        vm.loadOlder()
        waitUntil("the older page") { vm.entries.value.any { it.text == "$token 0" } }
        assertEquals("$token 449", vm.entries.value.last().text)
        assertTrue(vm.entries.value.size >= 450)
        // A line arriving meanwhile joins the longer page.
        add("$token 450")
        waitUntil("the new line") { vm.entries.value.last().text == "$token 450" }
        assertTrue(vm.entries.value.any { it.text == "$token 0" })
        // Back at the newest lines, the page shrinks again.
        vm.atNewest()
        waitUntil("the short page") { vm.entries.value.size == 400 }
        assertEquals("$token 450", vm.entries.value.last().text)
    }

    /** Scrolled back to the log's start, then down again: the page shrinks, and scrolling back reads older lines again. */
    @Test fun olderLinesLoadAgainAfterThePageShrinks() {
        // Only this test's lines show, however long the log is: the start is reached within the cap.
        logs.hideScreen(logs.maxId())
        hid = true
        repeat(450) { add("$token $it") }
        val total = logs.recent(5_000).size
        assertEquals(450, total)
        val vm = viewModel()
        waitUntil("the newest page") { vm.entries.value.lastOrNull()?.text == "$token 449" }
        while (vm.entries.value.size < total) {
            val had = vm.entries.value.size
            vm.loadOlder()
            waitUntil("an older page") { vm.entries.value.size > had }
        }
        vm.atNewest()
        waitUntil("the short page") { vm.entries.value.size == 400 }
        vm.loadOlder()
        waitUntil("older lines again") { vm.entries.value.any { it.text == "$token 0" } }
    }

    @Test fun aMovedLaunchLineShowsOnceAtTheBottom() {
        val vm = viewModel()
        first = minOf(first, logs.addReplacing(LogKind.INFO, "$token launched"))
        waitUntil("the launch line") { vm.entries.value.lastOrNull()?.text == "$token launched" }
        add("$token after")
        waitUntil("the next line") { vm.entries.value.lastOrNull()?.text == "$token after" }
        logs.addReplacing(LogKind.INFO, "$token launched")
        waitUntil("moved down") { vm.entries.value.lastOrNull()?.text == "$token launched" }
        assertEquals(1, vm.entries.value.count { it.text == "$token launched" })
        assertEquals("$token after", vm.entries.value[vm.entries.value.size - 2].text)
    }

    @Test fun aDeletedLineLeavesThePage() {
        val vm = viewModel()
        val id = add("$token stays")
        val going = add("$token goes")
        waitUntil("both") { vm.entries.value.lastOrNull()?.id == going }
        logs.delete(going)
        waitUntil("one gone") { vm.entries.value.lastOrNull()?.id == id }
        assertTrue(vm.entries.value.none { it.id == going })
    }
}
