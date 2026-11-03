package com.mtwabpin.kolahal.script

import android.app.Application
import android.os.Debug
import android.os.Process
import kotlin.concurrent.thread

/**
 * The last word on a run, for what an engine can't stop by itself: a long C function in Lua (a
 * pathological `string.find`), a QuickJS built-in that never looks for the interrupt
 * (`Array.prototype.includes` over 2^53 slots), promise jobs that keep queuing one another, or
 * memory that grows faster than the engine checks it. Past the timeout and [GRACE_MS], or with
 * native memory grown more than [nativeLimitMb] (null: not watched), it logs why and kills the
 * isolated :script process. Elsewhere (code typed at the prompt in tests, the main process) it only
 * logs: killing the launcher is never the answer. Close it when the run ends.
 */
internal class RunWatchdog(host: HostApi, timeoutMs: Long, nativeLimitMb: Long?) : AutoCloseable {
    private val baseline = Debug.getNativeHeapAllocatedSize()
    private val thread = thread(isDaemon = true, name = "script-watchdog") {
        val killAt = System.currentTimeMillis() + timeoutMs + GRACE_MS
        try {
            while (true) {
                // With no memory to watch (JavaScript has its own limit), only the clock is left, and
                // when it runs out is known: one sleep, not a hundred wake-ups a second.
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

        /** Often enough that memory doubling every few instructions overshoots by about one doubling. */
        const val WATCH_EVERY_MS = 10L
    }
}
