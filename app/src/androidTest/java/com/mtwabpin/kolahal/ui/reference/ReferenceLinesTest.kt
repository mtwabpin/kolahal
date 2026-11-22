package com.mtwabpin.kolahal.ui.reference

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.ScriptEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The editor reference draws each log example and fmt sample from lines made in Kotlin
 * ([ReferenceExamples.Log.lines]), not by running the example. Here each example's code runs as
 * the reference inserts it, in Lua and in JavaScript, as a saved script, and must log those same
 * lines: the same text, and the same context for what a line shows (its body, link, gap, Copy text
 * and buttons).
 */
@RunWith(AndroidJUnit4::class)
class ReferenceLinesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val logs = LogRepository.get(context)
    private val name = "reflines${System.nanoTime()}"
    private val id = 930_000_000L + System.nanoTime() % 1_000_000

    private fun lines(): List<LogEntry> =
        logs.recent(limit = 200).filter { it.source == name }.sortedBy { it.id }

    @After
    fun tearDown() = lines().forEach { logs.delete(it.id) }

    /** What [ReferenceExamples.Line] holds of a line's context. */
    private val shown = setOf(
        LogContext.TITLE,
        LogContext.BODY,
        LogContext.URL,
        LogContext.GAP,
        LogContext.COPY,
        LogContext.ACTIONS
    )

    @Test
    fun eachExampleLogsTheLinesTheReferenceDraws() {
        for (ex in ReferenceExamples.logs + ReferenceExamples.fmt) for (lang in ScriptLang.entries) {
            lines().forEach { logs.delete(it.id) }
            // A saved script: only its lines may have buttons.
            val host = HostApi(context, name, scriptId = id, notifyEnabled = false)
            runBlocking {
                ScriptEngine.forLang(lang)
                    .run(ex.code(lang), name, host, 10_000, false, emptyList(), null)
            }
            host.flush()
            assertEquals(
                "${ex.name} in $lang",
                ex.lines.map { it.text to it.ctx },
                lines().map { it.text to it.ctx.values.filterKeys { k -> k in shown } },
            )
        }
    }
}
