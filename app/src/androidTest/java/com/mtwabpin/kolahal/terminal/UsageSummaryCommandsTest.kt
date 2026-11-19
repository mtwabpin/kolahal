package com.mtwabpin.kolahal.terminal

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.CommandRunner
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.shell
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** `usage-summary`: Android's weeks, months and years, read on a real device. */
@RunWith(AndroidJUnit4::class)
class UsageSummaryCommandsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val commands = CommandRunner(context)

    private fun withUsageAccess(mode: String, block: () -> Unit) {
        val before = Regex("GET_USAGE_STATS: (\\w+)").find(shell("appops get ${context.packageName} GET_USAGE_STATS"))?.groupValues?.get(1) ?: "default"
        try {
            shell("appops set ${context.packageName} GET_USAGE_STATS $mode")
            block()
        } finally {
            shell("appops set ${context.packageName} GET_USAGE_STATS $before")
        }
    }

    private fun texts(line: String) = commands.ownLines(line).map { StyledText.plain(it.text) }

    /** Without usage access it says to allow it, with a tap that opens the page. */
    @Test fun itNeedsUsageAccess() = withUsageAccess("ignore") {
        val denied = commands.ownLines("usage-summary").single()
        assertEquals("Usage summary needs usage access — tap to allow it", denied.text)
        assertEquals("usage-access", denied.ctx.values[LogContext.RUN])
    }

    /** Alone: a line for each level that has data, each a tap that runs more; or that there is none yet. */
    @Test fun theOverview() = withUsageAccess("allow") {
        val shown = commands.ownLines("usage-summary")
        val first = StyledText.plain(shown.first().text)
        if (first == "No usage recorded yet") return@withUsageAccess
        assertEquals("What Android keeps, and the screen time a day in each", first)
        val rows = shown.drop(1).dropLast(1)
        assertTrue(rows.isNotEmpty())
        rows.forEach { row ->
            assertTrue(row.text, Regex("\\d+ (days?|weeks?|months?|years?) +from \\d+ \\w+( \\d{4})? +(\\d+h )?\\d+m").matches(row.text))
            assertTrue(row.ctx.values[LogContext.RUN].orEmpty(), Regex("usage \\d+|usage-summary (week|month|year)").matches(row.ctx.values[LogContext.RUN].orEmpty()))
        }
    }

    /** `week` and `month` start with big numbers and end with the note on Android's own edges. */
    @Test fun weeksAndMonths() = withUsageAccess("allow") {
        for ((word, note) in listOf("week" to "not on Monday", "month" to "not on the 1st")) {
            val shown = texts("usage-summary $word")
            if (shown.size == 1 && shown.single().startsWith("No usage recorded")) continue
            assertTrue(shown.first(), shown.first().contains("a day on average"))
            assertTrue(shown.last(), shown.last().endsWith(note))
        }
    }

    /** `full` holds the overview and a part for each of weeks, months and years, in that order. */
    @Test fun full() = withUsageAccess("allow") {
        val shown = texts("usage-summary full")
        val parts = listOf("Weeks", "Months", "Years").map { shown.indexOf(it) }
        assertTrue(shown.toString(), parts.all { it > 0 } && parts == parts.sorted())
    }

    /** Another word says how to use it. */
    @Test fun anUnknownWord() = withUsageAccess("allow") {
        assertEquals(listOf("Usage: usage-summary [week | month | year | full]"), texts("usage-summary decade"))
    }
}
