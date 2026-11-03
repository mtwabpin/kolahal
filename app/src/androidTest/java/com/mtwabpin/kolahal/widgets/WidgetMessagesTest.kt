package com.mtwabpin.kolahal.widgets

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Widget results are kept for a widget screen to show, and a widget to add again is said in the log. */
@RunWith(AndroidJUnit4::class)
class WidgetMessagesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val widgets = WidgetHost.get(context)
    private val logs = LogRepository.get(context)
    private val before = logs.maxId()
    private val had = widgets.widgets.value.map { it.key }.toSet()

    @After fun tearDown() {
        while (widgets.takeMessage() != null) Unit
        widgets.widgets.value.filter { it.key !in had }.forEach { widgets.remove(it.key) }
        logs.recent(50).filter { it.id > before }.forEach { logs.delete(it.id) }
    }

    /**
     * Adding from the picker moves to the Widgets screen just as "Added" comes: messages are kept, in
     * order, until a screen takes them. (Each screen showing them is checked in its own test.)
     */
    @Test fun messagesAreKeptInOrderUntilTaken() {
        while (widgets.takeMessage() != null) Unit
        widgets.message("Added Clock")
        widgets.message("Widget not added")
        assertEquals(listOf("Added Clock", "Widget not added"), widgets.messages.value)
        assertEquals("Added Clock", widgets.takeMessage())
        assertEquals("Widget not added", widgets.takeMessage())
        assertNull(widgets.takeMessage())
    }

    /** A widget whose id didn't come along (Android restored Kolahal on a new phone): the log says so, and a tap opens the page. */
    @Test fun aWidgetToAddAgainIsInTheLog() {
        val provider = widgets.providers().firstOrNull()
        assumeTrue("no widgets to place on this device", provider != null)
        // An id Android never gave out, as on a new phone.
        widgets.place(Int.MAX_VALUE - 7, provider!!.info)
        widgets.prune()
        val line = logs.recent(20).first { it.id > before && "adding again" in it.text }
        assertTrue(line.text, line.text.startsWith("1 widget needs adding again"))
        assertEquals("widgets", line.ctx.values[LogContext.RUN])
        assertTrue(widgets.widgets.value.any { it.key !in had && it.pending })
    }
}
