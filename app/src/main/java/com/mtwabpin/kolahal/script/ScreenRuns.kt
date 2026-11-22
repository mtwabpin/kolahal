/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/** What a run tells its worker about its screen (`tty.screen`). */
internal class ScreenHandover {
    /** The script's code has run to its end, and its screen comes next: the limit of a run is over. */
    val codeDone = CompletableDeferred<Unit>()

    /** Home shows the screen, and is bound to the process: the run no longer needs its worker. */
    val shown = CompletableDeferred<Unit>()
}

/**
 * Runs with a screen of their own, in a [scope] that outlives their worker. Android stops a worker after
 * about ten minutes, and a screen may stay open for hours.
 *
 * The run is the same run from its first line to its last: only who waits for it changes. Its worker
 * does until home shows the screen; from then on home's binding to
 * [ScriptScreenService] keeps the process, and the worker is done. A run that never shows a screen
 * stays the worker's to its end, and ends with it. [failed] is told of a failure nobody waits for.
 */
internal class ScreenRuns(private val scope: CoroutineScope, private val failed: (Throwable) -> Unit) {
    /**
     * Runs [block] and returns its result, or true as soon as its screen shows. A worker that is stopped
     * before that stops the run; one stopped after it changes nothing.
     */
    suspend fun run(block: suspend (ScreenHandover) -> Boolean): Boolean {
        val handover = ScreenHandover()
        val run = scope.async { block(handover) }
        run.invokeOnCompletion { e ->
            if (e != null && e !is CancellationException && handover.shown.isCompleted) failed(e)
        }
        try {
            return select {
                run.onAwait { it }
                handover.shown.onJoin { true }
            }
        } catch (e: CancellationException) {
            if (!handover.shown.isCompleted) {
                run.cancel(e)
                // As a worker waits for what it started, so that the run has written its last lines.
                withContext(NonCancellable) { run.join() }
            }
            throw e
        }
    }

    companion object {
        /**
         * Runs [block] with at most [ms] for the script's code, up to [ScreenHandover.codeDone]. The
         * screen that follows has no limit. Past it, this throws what withTimeout throws.
         */
        suspend fun <T> limited(ms: Long, handover: ScreenHandover, block: suspend () -> T): T = coroutineScope {
            val run = async { block() }
            withTimeout(ms) {
                select<Unit> {
                    run.onJoin {}
                    handover.codeDone.onJoin {}
                }
            }
            run.await()
        }
    }
}
