package com.mtwabpin.kolahal.script

import android.app.Application
import android.os.Debug
import android.os.Process
import kotlin.concurrent.thread

/**
 * The last guard on a run. It handles what an engine can't stop by itself:
 * - a long C function in Lua (a pathological `string.find`),
 * - a QuickJS built-in that never checks the interrupt (`Array.prototype.includes` over 2^53 slots),
 * - promise jobs that keep queuing one another,
 * - memory that grows faster than the engine checks it.
 * It acts after the timeout plus [GRACE_MS], or when native memory has grown more than
 * [nativeLimitMb] (null means memory is not watched). Then it logs why and kills the isolated
 * :script process. In other places (code typed at the prompt in tests, the main process) it only
 * logs, because it must never kill the launcher. Close it when the run ends.
 */
internal class RunWatchdog(host: HostApi, timeoutMs: Long, nativeLimitMb: Long?) : AutoCloseable {
    private val baseline = Debug.getNativeHeapAllocatedSize()
    private val thread = thread(isDaemon = true, name = "script-watchdog") {
        val killAt = System.currentTimeMillis() + timeoutMs + GRACE_MS
        try {
            while (true) {
                // With no memory to watch (JavaScript has its own limit), only the clock matters. Its end time
                // is known, so one sleep is enough instead of a hundred wake-ups a second.
                Thread.sleep(if (nativeLimitMb == null) maxOf(WATCH_EVERY_MS, killAt - System.currentTimeMillis()) else WATCH_EVERY_MS)
                val reason = when {
                    nativeLimitMb != null && Debug.getNativeHeapAllocatedSize() - baseline > nativeLimitMb * 1024L * 1024 -> "used over $nativeLimitMb MB"
                    System.currentTimeMillis() > killAt -> "still running after timeout"
                    else -> null
                } ?: continue
                runCatching { host.logStopped("${host.scriptName}: $reason, killed script process") }
                if (Application.getProcessName().endsWith(":script")) Process.killProcess(Process.myPid())
                return@thread
            }
        } catch (_: InterruptedException) {
            return@thread
        }
    }

    override fun close() = thread.interrupt()

    private companion object {
        const val GRACE_MS = 5_000L

        /** Often enough that, if memory doubles every few instructions, it overshoots by about one doubling only. */
        const val WATCH_EVERY_MS = 10L
    }
}
