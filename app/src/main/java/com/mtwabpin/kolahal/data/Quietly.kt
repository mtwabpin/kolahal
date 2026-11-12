package com.mtwabpin.kolahal.data

import android.content.Context

/**
 * Work whose failure must not stop what comes after it (setting reminders again at start, scheduling
 * scripts and feeds), but must not go unseen either. Before, such a failure left no trace: reminders
 * that weren't set again after a reboot just didn't fire.
 *
 * [run] does the work. On failure, it logs one error line naming [what] failed and why, at most once
 * a day for each [what], so a step that fails at every start doesn't fill the log.
 */
object Quietly {
    /** Where the day of the last report is kept: a scope about this phone, never in a backup. */
    private const val SCOPE = "worker"

    fun run(context: Context, what: String, block: () -> Unit) {
        try {
            block()
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            report(context, what, e)
        }
    }

    /** Logs that [what] failed with [e], once a day for each [what]. Reporting never throws. */
    fun report(context: Context, what: String, e: Throwable) {
        runCatching {
            val store = KvStore(context, SCOPE)
            val key = "failed:$what"
            val today = java.time.LocalDate.now().toString()
            if (store.get(key) == today) return
            store.set(key, today)
            val reason = e.message?.lines()?.firstOrNull { it.isNotBlank() }?.take(160) ?: e.javaClass.simpleName
            LogRepository.get(context).add(LogKind.ERROR, "$what failed: $reason")
        }
    }
}
