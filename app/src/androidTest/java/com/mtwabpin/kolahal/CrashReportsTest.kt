package com.mtwabpin.kolahal

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogRepository
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Start-up and every return to home both look for new reports: each is announced once. */
@RunWith(AndroidJUnit4::class)
class CrashReportsTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val dir = File(context.filesDir, "crash")
    // The phone's own reports aside, and what was announced of them: put back after.
    @get:Rule val crashReports = CrashReportsAside()
    // The notices the test's reports get.
    @get:Rule val logLines = LogLinesSince()

    @Before fun setUp() {
        context.getSharedPreferences("crash", 0).edit(commit = true) { clear() }
        // The first look only notes where Android's own record of exits ends.
        CrashReports.announce(context)
    }

    private fun report(id: String, error: String) {
        dir.mkdirs()
        File(dir, "$id-crash.txt").writeText("Kolahal crash report\nKolahal\nAndroid\nWhen: x\nWhat: crash in test, thread main\n\n$error\n\tat a.b(c:1)\n")
    }

    /** The crash notices logged since [after], as their tap commands. */
    private fun notices(after: Long) = logs.recent(100)
        .filter { it.id > after && it.text.startsWith("Kolahal stopped") }
        .map { it.ctx.values[LogContext.RUN] }

    @Test fun eachReportIsAnnouncedOnce() {
        val start = logs.recent(1).lastOrNull()?.id ?: 0
        report("20260101-120000", "java.lang.IllegalStateException: one")
        CrashReports.announce(context)
        CrashReports.announce(context)
        assertEquals(listOf("crashes share 20260101-120000"), notices(start))

        val second = logs.recent(1).last().id
        report("20260102-120000", "java.lang.IllegalStateException: two")
        CrashReports.announce(context)
        CrashReports.announce(context)
        assertEquals(listOf("crashes share 20260102-120000"), notices(second))
    }

    @Test fun listIsNewestFirstAndClearDeletesAll() {
        report("20260101-120000", "java.lang.IllegalStateException: old")
        report("20260102-120000", "java.lang.IllegalStateException: new")
        File(dir, "20260101-120000-crash.txt").setLastModified(1_000_000L)
        File(dir, "20260102-120000-crash.txt").setLastModified(2_000_000L)
        val list = CrashReports.list(context)
        assertEquals(listOf("20260102-120000", "20260101-120000"), list.map { it.id })
        assertEquals("IllegalStateException: new", list.first().summary)
        assertEquals(2, CrashReports.clear(context))
        assertEquals(emptyList<Any>(), CrashReports.list(context))
    }
}
