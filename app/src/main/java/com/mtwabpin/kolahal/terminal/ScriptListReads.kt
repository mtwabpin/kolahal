/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The one writer of the script list ([ScriptActions] holds it). Every read of the scripts that is
 * written to the list runs on [lane], one at a time, so the list written last is always the one read
 * last: an older read can't overwrite a newer one.
 * - [read]: after a change on the Scripts screen, an import or an install, or when a script screen opens.
 * - [readIfChanged]: the log's reload, only when [fingerprint] changed since its last read.
 *
 * [all] reads the list and [write] sets it, with what follows from it (the command scripts).
 */
internal class ScriptListReads(
    private val fingerprint: () -> ScriptRepository.Fingerprint,
    private val all: () -> List<Script>,
    private val write: (List<Script>) -> Unit,
    private val lane: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    /** The [fingerprint] at the last [readIfChanged] that read or patched. Only touched on [lane]. */
    private var readAt: ScriptRepository.Fingerprint? = null

    /** The list written last, by [read] or [readIfChanged]. Only touched on [lane]. */
    private var written: List<Script>? = null

    private fun writeList(list: List<Script>) {
        write(list)
        written = list
    }

    /** Reads the list and writes it. [readIfChanged] still reads once after it, if the scripts changed since its last read. */
    suspend fun read() = withContext(lane) { writeList(all()) }

    /**
     * Reads the list and writes it if [fingerprint] changed since this was last called. The
     * fingerprint is taken before the list: a change in between makes the next call read again.
     * - When only runs moved (no script added, removed, renamed, edited or switched), the list
     *   written last gets the new runs ([ScriptRepository.withRuns]): no code is read.
     * - Otherwise every script is read again.
     * True only when every script was read again.
     */
    suspend fun readIfChanged(): Boolean = withContext(lane) {
        val print = fingerprint()
        val before = readAt
        if (print == before) return@withContext false
        val list = written
        val patched = if (before != null && list != null && print.scripts == before.scripts) {
            ScriptRepository.withRuns(list, print.runs)
        } else null
        writeList(patched ?: all())
        readAt = print
        patched == null
    }
}
