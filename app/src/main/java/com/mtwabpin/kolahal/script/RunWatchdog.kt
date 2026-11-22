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
internal class RunWatchdog(private val host: HostApi, timeoutMs: Long, nativeLimitMb: Long?) :
    AutoCloseable {
    init {
        running += host
    }

    private val baseline = Debug.getNativeHeapAllocatedSize()

    /** When the run is past its time, for the thread below. [extend] moves it. */
    @Volatile
    private var killAt = System.currentTimeMillis() + timeoutMs + GRACE_MS

    @Volatile
    private var closed = false

    private val thread = thread(isDaemon = true, name = "script-watchdog") {
        // While memory stays put (a run waiting on the network), the checks slow down to every
        // [IDLE_EVERY_MS]. As soon as it grows, they are every [WATCH_EVERY_MS] again.
        var every = WATCH_EVERY_MS
        var last = baseline
        while (!closed) {
            // With no memory to watch (JavaScript has its own limit), only the clock matters. Its end time
            // is known, so one sleep is enough instead of a hundred wake-ups a second.
            val wait = if (nativeLimitMb == null) maxOf(WATCH_EVERY_MS, killAt - System.currentTimeMillis()) else every
            LockSupport.parkNanos(wait * 1_000_000L)
            if (closed) return@thread
            val heap = Debug.getNativeHeapAllocatedSize()
            every = if (heap - last > GROWING_BYTES) WATCH_EVERY_MS else minOf(
                IDLE_EVERY_MS,
                every * 2
            )
            last = heap
            val reason = when {
                nativeLimitMb != null && heap - baseline > nativeLimitMb * 1024L * 1024 -> "used over $nativeLimitMb MB"
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
     * The run has [ms] more from now. The calls of a script's screen (`tty.screen`) get it one by one:
     * such a run goes on as long as its screen is open.
     */
    fun extend(ms: Long) {
        killAt = System.currentTimeMillis() + ms + GRACE_MS
        LockSupport.unpark(thread)
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
