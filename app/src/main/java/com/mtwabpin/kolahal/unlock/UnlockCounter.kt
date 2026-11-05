package com.mtwabpin.kolahal.unlock

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
 * Primary source: UsageStats KEYGUARD_HIDDEN events (accurate, includes unlocks while the
 * launcher process was dead; needs the user to grant Usage Access).
 * Fallback: an ACTION_USER_PRESENT receiver registered while the launcher process is alive.
 */
class UnlockCounter private constructor(private val context: Context) {
    private val prefs = context.getSharedPreferences("unlocks", Context.MODE_PRIVATE)
    private val _today = MutableStateFlow(0)
    val today: StateFlow<Int> = _today.asStateFlow()

    private var registered = false
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    fun start() {
        if (registered) return
        registered = true
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                val key = dayKey(LocalDate.now())
                prefs.edit { putInt(key, prefs.getInt(key, 0) + 1) }
                com.mtwabpin.kolahal.script.TriggerDispatcher.get(context).fire("unlock")
                refresh()
            }
        // USER_PRESENT comes from SystemUI, not the system's own uid: a receiver that isn't exported
        // never gets it. It is a protected broadcast, so only the system can send it anyway.
        }, IntentFilter(Intent.ACTION_USER_PRESENT), ContextCompat.RECEIVER_EXPORTED)
        refresh()
    }

    /** Recounts off the main thread; a day of UsageStats events can be thousands of entries. */
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

    /** Finished days don't change: counted once. */
    private val pastDays = java.util.concurrent.ConcurrentHashMap<LocalDate, Int>()

    /** Today so far: the day, how far its events were read, and the count up to there. */
    private var todayScan: Triple<LocalDate, Long, Int>? = null

    /**
     * Unlocks on [day]. With usage access, today's count reads only the events since the last
     * call (it runs on every unlock and return home), and past days are read once.
     */
    @Synchronized
    fun countFor(day: LocalDate): Int {
        if (!hasUsageAccess()) return prefs.getInt(dayKey(day), 0)
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
        // The next scan starts after the last unlock seen, not now: one the system writes a moment
        // later, with a time before now, is still found.
        todayScan = Triple(today, last?.let { it + 1 } ?: upTo, total)
        return total
    }

    /** How many unlocks in [from, until), and the time of the last. */
    private fun keyguardHidden(from: Long, until: Long): Pair<Int, Long?> {
        val usage = context.getSystemService(UsageStatsManager::class.java)
        // From Android 15 Android hands over the unlocks alone, not every event of the day to skip.
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

    companion object : Singleton<UnlockCounter>(::UnlockCounter)
}
