package com.mtwabpin.kolahal

import android.content.Context
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogRepository
import org.junit.rules.ExternalResource

/*
 * Rules that put back what a test changed, so a later class starts from the same phone. Each acts
 * after the class's own `@After`, also when the test or that fails half way.
 */

private val target: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

/** Every setting back to its default after the test. */
class ConfigReset : ExternalResource() {
    override fun after() {
        val config = Config.get(target)
        Keys.ALL.forEach(config::reset)
    }
}

/**
 * The log as it was before the test: the lines written from its start on are deleted, after the
 * class's `@After` (and so also when that fails half way), and a clear, a fresh screen or an
 * emptied ↑ recall the test did is undone. Lines already there stay, even those the test hid.
 */
class LogLinesSince : ExternalResource() {
    private val logs = LogRepository.get(target)
    // Where LogRepository keeps what the screen and ↑ recall leave out.
    private val view = target.getSharedPreferences("logs", Context.MODE_PRIVATE)
    private val pointers = listOf("cleared_up_to", "hidden_up_to", "purge_up_to", "history_from")
    private var saved = emptyMap<String, Long>()

    /** The newest line before the test: every line after it is the test's. */
    var start = 0L
        private set

    override fun before() {
        start = logs.maxId()
        saved = pointers.filter(view::contains).associateWith { view.getLong(it, 0L) }
    }

    override fun after() {
        view.edit(commit = true) { pointers.forEach { k -> saved[k]?.let { putLong(k, it) } ?: remove(k) } }
        logs.recent(100_000, afterId = start).forEach { logs.delete(it.id) }
    }
}
