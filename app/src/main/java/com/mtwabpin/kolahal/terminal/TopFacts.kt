/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import android.app.ActivityManager
import android.content.Context
import android.os.Process
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.terminal.tty.screens.TopData
import com.mtwabpin.kolahal.terminal.tty.screens.TopScript
import com.mtwabpin.kolahal.terminal.tty.screens.TopSource
import com.mtwabpin.kolahal.usage.AppUsage
import com.mtwabpin.kolahal.usage.UsageAccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

/**
 * The facts `top` shows ([TopSource]), read each second on the main thread while it is open. No
 * new permission is needed, and each read is quick:
 * - Kolahal's time up, its Java heap and its memory in RAM (/proc/self/statm, a few bytes);
 * - the phone's memory (ActivityManager.MemoryInfo) and the battery, as the header has it;
 * - today's unlocks, and every script with its last run and timers, which home already holds.
 *
 * Today's screen time takes a read of Android's usage events, so it is worked out in [scope] off
 * the main thread, at most once a minute, and only with usage access.
 */
internal class TopFacts(
    private val context: Context,
    private val scope: CoroutineScope,
    private val scripts: () -> List<Script>,
    private val status: () -> Status,
    private val unlocks: () -> Int,
) : TopSource {
    private val activities = context.getSystemService(ActivityManager::class.java)
    private val memory = ActivityManager.MemoryInfo()
    private val pageSize = runCatching { Os.sysconf(OsConstants._SC_PAGESIZE) }.getOrDefault(4_096L)

    // Today's screen time: the last result, and when it was asked for (uptime ms).
    @Volatile
    private var access: Boolean? = null

    @Volatile
    private var screenTime = -1L
    private var askedAt = 0L
    private var reading: Job? = null

    override fun read(): TopData {
        refreshUsage()
        activities?.getMemoryInfo(memory)
        val runtime = Runtime.getRuntime()
        val s = status()
        // The first read checks usage access at once, so the line doesn't say "no access" for a second.
        val granted = access ?: UsageAccess.granted(context).also { access = it }
        return TopData(
            uptimeMs = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime(),
            batteryPercent = s.batteryPct ?: -1,
            charging = s.charging,
            memTotal = if (activities != null) memory.totalMem else -1,
            memAvailable = if (activities != null) memory.availMem else -1,
            lowMemory = activities != null && memory.lowMemory,
            ownResident = resident(),
            heapUsed = runtime.totalMemory() - runtime.freeMemory(),
            heapMax = runtime.maxMemory(),
            usageAccess = granted,
            screenTimeMs = if (granted) screenTime else -1,
            unlocks = unlocks(),
            scripts = scripts().map { TopScript(it.name, it.enabled, it.lastRun, it.lastStatus, it.intervalMin, it.atTimes) },
        )
    }

    /** Asks for today's screen time again when the last answer is a minute old. */
    private fun refreshUsage() {
        val now = SystemClock.uptimeMillis()
        if (reading?.isActive == true || (askedAt != 0L && now - askedAt < USAGE_EVERY_MS)) return
        askedAt = now
        reading = scope.launch(Dispatchers.IO) {
            val granted = UsageAccess.granted(context)
            screenTime = if (!granted) -1L else runCatching {
                AppUsage(context).perDay(listOf(LocalDate.now())).first()
            }.getOrDefault(-1L)
            access = granted
        }
    }

    /** Kolahal's memory in RAM: the second number in /proc/self/statm, in pages. -1 if it can't be read. */
    private fun resident(): Long = runCatching {
        File("/proc/self/statm").readText().trim().split(' ')[1].toLong() * pageSize
    }.getOrDefault(-1L)

    private companion object {
        const val USAGE_EVERY_MS = 60_000L
    }
}
