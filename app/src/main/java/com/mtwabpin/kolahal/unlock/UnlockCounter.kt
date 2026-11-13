package com.mtwabpin.kolahal.unlock

import com.mtwabpin.kolahal.data.Stores
import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageEventsQuery
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.ZoneId

/**
 * Counts device unlocks.
 *
 * - Main source: UsageStats KEYGUARD_HIDDEN events. They are accurate and include unlocks while the
 *   launcher process was not running. The user must grant Usage Access.
 * - Fallback: an ACTION_USER_PRESENT receiver, registered while the launcher process is alive.
 *   Its counts are kept in the database (kv scope [Stores.UNLOCKS]), so the :script process reads today's
 *   count too. A prefs file would stay at what that process read when it started.
 */
class UnlockCounter private constructor(private val context: Context) {
    private val counts = com.mtwabpin.kolahal.data.KvStore(context, Stores.UNLOCKS)
    private val _today = MutableStateFlow(0)
    val today: StateFlow<Int> = _today.asStateFlow()

    private var registered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun start() {
        if (registered) return
        registered = true
        scope.launch { moveFromPrefs() }
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val day = LocalDate.now()
                scope.launch { counts.set(dayKey(day), (fallbackCount(day) + 1).toString()) }
                com.mtwabpin.kolahal.script.TriggerDispatcher.get(context).fire("unlock")
                refresh()
            }
        // USER_PRESENT comes from SystemUI, not from the system's own uid, so a receiver that isn't
        // exported never gets it. It is a protected broadcast, so only the system can send it anyway.
        }, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    /** Counts again off the main thread, because one day of UsageStats events can have thousands of entries. */
    fun refresh() {
        scope.launch { _today.value = countFor(LocalDate.now()) }
    }

    fun hasUsageAccess(): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION") // deprecated in API 37, replacement not available on older releases
        val mode = if (Build.VERSION.SDK_INT >= 29) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Counts for finished days. They don't change, so each is counted once. */
    private val pastDays = java.util.concurrent.ConcurrentHashMap<LocalDate, Int>()

    /** Today's scan so far: the day, the time up to which events were read, and the count up to then. */
    private var todayScan: Triple<LocalDate, Long, Int>? = null

    /**
     * Returns the unlocks on [day]. With usage access:
     * - Today's count reads only the events since the last call. This call runs on every unlock and
     *   every return to home.
     * - Past days are read once.
     */
    @Synchronized
    fun countFor(day: LocalDate): Int {
        if (!hasUsageAccess()) return fallbackCount(day)
        val zone = ZoneId.systemDefault()
        val start = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val today = LocalDate.now(zone)
        if (day != today) {
            val end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            return pastDays.getOrPut(day) { keyguardHidden(start, end).first }
        }
        val (_, upTo, count) = todayScan?.takeIf { it.first == today } ?: Triple(today, start, 0)
        val now = System.currentTimeMillis()
        val (added, last) = if (now > upTo) keyguardHidden(upTo, now) else (0 to null)
        val total = count + added
        // The next scan starts after the last unlock seen, not at now. So we still find an unlock
        // that the system writes a moment later with a time before now.
        todayScan = Triple(today, last?.let { it + 1 } ?: upTo, total)
        return total
    }

    /** Returns the number of unlocks in [from, until), and the time of the last one. */
    private fun keyguardHidden(from: Long, until: Long): Pair<Int, Long?> {
        val usage = context.getSystemService(UsageStatsManager::class.java)
        // From Android 15, we can ask for unlock events only, instead of reading every event of the day.
        val events = if (Build.VERSION.SDK_INT >= 35) {
            usage.queryEvents(UsageEventsQuery.Builder(from, until).setEventTypes(UsageEvents.Event.KEYGUARD_HIDDEN).build())
        } else {
            usage.queryEvents(from, until)
        } ?: return 0 to null
        val event = UsageEvents.Event()
        var count = 0
        var last: Long? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) {
                count++
                last = maxOf(last ?: 0L, event.timeStamp)
            }
        }
        return count to last
    }

    /** Counts for the last [days] days, oldest first. */
    fun history(days: Int): List<Pair<LocalDate, Int>> {
        val today = LocalDate.now()
        return (days - 1 downTo 0).map { today.minusDays(it.toLong()) }.map { it to countFor(it) }
    }

    private fun dayKey(day: LocalDate) = "d:$day"

    private fun fallbackCount(day: LocalDate): Int = counts.get(dayKey(day))?.toIntOrNull() ?: 0

    /** Counts from before 0.5.7 were in the "unlocks" prefs file: moved here once. A count already here is kept. */
    @androidx.annotation.VisibleForTesting
    internal fun moveFromPrefs() {
        val prefs = Stores.OLD_UNLOCKS.open(context)
        val old = prefs.all.takeIf { it.isNotEmpty() } ?: return
        for ((k, v) in old) if (v is Int && counts.get(k) == null) counts.set(k, v.toString())
        prefs.edit(commit = true) { clear() }
    }

    companion object : Singleton<UnlockCounter>(::UnlockCounter) {
    }
}
