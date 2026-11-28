package com.mtwabpin.kolahal.script

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** How a run with a screen leaves its worker ([ScreenRuns]): the worker is the one that calls [ScreenRuns.run]. */
class ScreenRunsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val failures = java.util.concurrent.CopyOnWriteArrayList<Throwable>()
    private val runs = ScreenRuns(scope) { failures += it }

    @After
    fun tearDown() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
    }

    private fun <T> within(ms: Long = 5_000, block: suspend () -> T): T = runBlocking { withTimeout(ms) { block() } }

    /** A run that shows no screen is the worker's to its end, with its result and its error. */
    @Test
    fun aRunWithoutAScreenIsTheWorkersToItsEnd() {
        assertTrue(within { runs.run { true } })
        assertFalse(within { runs.run { delay(50); false } })
        try {
            within { runs.run { throw IllegalArgumentException("boom") } }
            fail("expected an error")
        } catch (e: IllegalArgumentException) {
            assertEquals("boom", e.message)
        }
        assertEquals(emptyList<Throwable>(), failures.toList())
    }

    /** Once its screen shows, the worker is done and the run goes on. */
    @Test
    fun theWorkerIsDoneWhenTheScreenShows() {
        val close = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<Unit>()
        assertTrue(within {
            runs.run { handover ->
                handover.shown.complete(Unit)
                close.await()
                ended.complete(Unit)
                false
            }
        })
        assertFalse(ended.isCompleted)
        close.complete(Unit)
        within { ended.await() }
    }

    /** A worker stopped before the screen shows stops the run, and waits until it has ended. */
    @Test
    fun aWorkerStoppedBeforeTheScreenStopsTheRun() {
        val started = CompletableDeferred<Unit>()
        var ended = false
        within {
            val worker = scope.launch {
                runs.run {
                    started.complete(Unit)
                    try {
                        awaitCancellation()
                    } finally {
                        ended = true
                    }
                }
            }
            started.await()
            worker.cancelAndJoin()
        }
        assertTrue(ended)
    }

    /** One stopped after it changes nothing: the run is not the worker's any more. */
    @Test
    fun aWorkerStoppedAfterTheScreenShowsChangesNothing() {
        val close = CompletableDeferred<Unit>()
        val ended = CompletableDeferred<Unit>()
        within {
            val worker = scope.launch {
                runs.run { handover ->
                    handover.shown.complete(Unit)
                    close.await()
                    ended.complete(Unit)
                    true
                }
            }
            worker.join()
            worker.cancelAndJoin()
            close.complete(Unit)
            ended.await()
        }
    }

    /** A failure after the worker has gone isn't lost. */
    @Test
    fun aFailureNobodyWaitsForIsTold() {
        val close = CompletableDeferred<Unit>()
        within {
            runs.run { handover ->
                handover.shown.complete(Unit)
                close.await()
                throw IllegalStateException("late")
            }
        }
        close.complete(Unit)
        within { while (failures.isEmpty()) delay(10) }
        assertEquals("late", failures.single().message)
    }

    /** The code before the screen has the limit of a run. */
    @Test
    fun theCodeHasItsLimit() {
        try {
            within { ScreenRuns.limited<Unit>(50, ScreenHandover()) { awaitCancellation() } }
            fail("expected a timeout")
        } catch (_: TimeoutCancellationException) {
        }
    }

    /** A request that asked to wait longer moves the limit ([HostApi.stretchedTo]), with a screen or without. */
    @Test
    fun theLimitMovesWhenARunIsStretched() {
        // Each run's limit moves to 400 ms from its own start.
        fun moved() = System.currentTimeMillis() + 400
        val a = moved()
        assertEquals("late", within { ScreenRuns.limited(50, null, { a }) { delay(250); "late" } })
        val b = moved()
        assertEquals("late", within { ScreenRuns.limited(50, ScreenHandover(), { b }) { delay(250); "late" } })
        // Past the moved limit, it ends as at any other.
        val c = moved()
        try {
            within { ScreenRuns.limited<Unit>(50, null, { c }) { awaitCancellation() } }
            fail("expected a timeout")
        } catch (_: TimeoutCancellationException) {
        }
    }

    /** The screen has none, and a run's own error and result come out as they are. */
    @Test
    fun theScreenHasNone() {
        val handover = ScreenHandover()
        assertEquals("done", within {
            ScreenRuns.limited(50, handover) {
                handover.codeDone.complete(Unit)
                delay(300)
                "done"
            }
        })
        assertEquals("quick", within { ScreenRuns.limited(5_000, ScreenHandover()) { "quick" } })
        try {
            within { ScreenRuns.limited<Unit>(5_000, ScreenHandover()) { throw IllegalArgumentException("boom") } }
            fail("expected an error")
        } catch (e: IllegalArgumentException) {
            assertEquals("boom", e.message)
        }
    }
}
