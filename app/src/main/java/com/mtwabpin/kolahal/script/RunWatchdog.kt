/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import android.os.Debug
import android.os.Process
import com.mtwabpin.kolahal.data.Singleton
import com.mtwabpin.kolahal.script.RunWatchdog.Companion.GRACE_MS
import java.util.concurrent.locks.LockSupport
import kotlin.concurrent.thread

/**
 * The last guard on a run. It handles what an engine can't stop by itself:
 * - a long C function in Lua (a pathological `string.find`),
 * - a QuickJS built-in that never checks the interrupt (`Array.prototype.includes` over 2^53 slots),
 * - promise jobs that keep queuing one another (QuickJS stops these itself since quickjs-kt 1.0.17),
 * - memory that grows faster than the engine checks it.
 * It acts after the timeout plus [GRACE_MS], or when native memory has grown more than
 * [nativeLimitMb] (null means memory is not watched). Then it logs why and kills the isolated
 * :script process. In other places (code typed at the prompt in tests, the main process) it only
 * logs, because it must never kill the launcher. Close it when the run ends.
 *
 * Other runs in the process die with it. Each logs a line saying so first, with what it had waiting
 * ([stopOthers]); before, they vanished mid-run without a word.
 */
internal class RunWatchdog(
    private val host: HostApi,
    timeoutMs: Long,
    private val nativeLimitMb: Long?
) : AutoCloseable {
    init {
        running += host
    }

    /** The native memory the run started from; for a screen's call, what that call started from ([call]). */
    @Volatile
    private var baseline = Debug.getNativeHeapAllocatedSize()

    /** When the run is past its time, for the thread below. [call] and [rest] move it. */
    @Volatile
    private var killAt = System.currentTimeMillis() + timeoutMs + GRACE_MS

    /** A screen waits for home: nothing of the script runs, so nothing is watched but the clock ([rest]). */
    @Volatile
    private var resting = false

    @Volatile
    private var closed = false

    private val thread = thread(isDaemon = true, name = "script-watchdog") {
        // While memory stays put (a run waiting on the network), the checks slow down to every
        // [IDLE_EVERY_MS]. As soon as it grows, they are every [WATCH_EVERY_MS] again.
        var every = WATCH_EVERY_MS
        var last = baseline
        while (!closed) {
            // With no memory to watch (JavaScript has its own limit, and a screen between its calls runs
            // nothing), only the clock matters. Its end time is known, so one sleep is enough instead of a
            // hundred wake-ups a second. A call of a screen's ends that sleep.
            val idle = nativeLimitMb == null || resting
            if (idle) every = WATCH_EVERY_MS
            val wait = if (idle) maxOf(WATCH_EVERY_MS, killAt - System.currentTimeMillis()) else every
            LockSupport.parkNanos(wait * 1_000_000L)
            if (closed) return@thread
            val heap = Debug.getNativeHeapAllocatedSize()
            every = if (heap - last > GROWING_BYTES) WATCH_EVERY_MS else minOf(
                IDLE_EVERY_MS,
                every * 2
            )
            last = heap
            val reason = when {
                nativeLimitMb != null && !resting && heap - baseline > nativeLimitMb * 1024L * 1024 -> "used over $nativeLimitMb MB"
                System.currentTimeMillis() > killAt -> "still running after timeout"
                else -> null
            } ?: continue
            runCatching { host.logStopped("${host.scriptName}: $reason, killed script process") }
            if (Singleton.inScriptProcess) {
                stopOthers(host)
                Process.killProcess(Process.myPid())
            }
            return@thread
        }
    }

    /**
     * A call of a script's screen (`tty.screen`) starts, and has [ms]: a run with a screen goes on for as
     * long as its screen is open, so its time and its memory are watched call by call. Over hours, the
     * process's memory may grow with other runs, which is none of this one's.
     */
    fun call(ms: Long) {
        if (nativeLimitMb != null) baseline = Debug.getNativeHeapAllocatedSize()
        killAt = System.currentTimeMillis() + ms + GRACE_MS
        resting = false
        LockSupport.unpark(thread)
    }

    /** The screen waits for home now, up to [ms]. The thread sleeps until then, or until the next [call]. */
    fun rest(ms: Long) {
        killAt = System.currentTimeMillis() + ms + GRACE_MS
        resting = true
    }

    override fun close() {
        running -= host
        closed = true
        LockSupport.unpark(thread)
    }

    companion object {
        /** The runs in this process now, each while its watchdog is open. */
        private val running: MutableSet<HostApi> =
            java.util.concurrent.ConcurrentHashMap.newKeySet()

        /**
         * Before the process is killed for [culprit]: every other run logs what it had waiting and a line
         * saying it was stopped too. At most a second, as one of them may be stuck as well.
         */
        internal fun stopOthers(culprit: HostApi) {
            val others = running.filter { it !== culprit }
            if (others.isEmpty()) return
            val logging = thread(isDaemon = true, name = "script-watchdog-others") {
                for (other in others) runCatching {
                    other.logStopped("${other.scriptName}: stopped, because ${culprit.scriptName} had to be stopped (scripts run in one process)")
                }
            }
            logging.join(1_000)
        }

        private const val GRACE_MS = 5_000L

        /** Often enough that, if memory doubles every few instructions, it overshoots by about one doubling only. */
        private const val WATCH_EVERY_MS = 10L

        /** How often while memory doesn't grow: 20 checks a second instead of 100. */
        private const val IDLE_EVERY_MS = 50L

        /** Growth between two checks that counts as growing (native memory moves a little on its own). */
        private const val GROWING_BYTES = 256L * 1024
    }
}
