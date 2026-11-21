package com.mtwabpin.kolahal.terminal

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
internal class ScriptListReads<T>(
    private val fingerprint: () -> String,
    private val all: () -> T,
    private val write: (T) -> Unit,
    private val lane: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    /** The [fingerprint] at the last [readIfChanged] that read. Only touched on [lane]. */
    private var readAt: String? = null

    /** Reads the list and writes it. [readIfChanged] still reads once after it, if the scripts changed since its last read. */
    suspend fun read() = withContext(lane) { write(all()) }

    /**
     * Reads the list and writes it if [fingerprint] changed since this was last called. True if it read.
     * The fingerprint is taken before the list: a change in between makes the next call read again.
     */
    suspend fun readIfChanged(): Boolean = withContext(lane) {
        val print = fingerprint()
        if (print == readAt) return@withContext false
        write(all())
        readAt = print
        true
    }
}
