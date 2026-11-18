package com.mtwabpin.kolahal.usage

import android.app.usage.StorageStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import android.os.storage.StorageManager

/**
 * The phone's storage, and each app's size, from Android's storage statistics; and when each app
 * was last used. Another app's size needs Usage Access, as screen time does (`storage`).
 */
class StorageUsage(private val context: Context) {
    private val storage = context.getSystemService(StorageStatsManager::class.java)

    /** The phone's own storage, in bytes. */
    data class Space(val total: Long, val free: Long) {
        val used get() = total - free
    }

    /**
     * An app's size, in bytes: the app itself, and its data. [data] includes [cache], as Android
     * counts it; its size is [app] + [data].
     */
    data class AppSize(val pkg: String, val app: Long, val data: Long, val cache: Long) {
        val size get() = app + data
    }

    fun space(): Space? = runCatching {
        Space(storage.getTotalBytes(StorageManager.UUID_DEFAULT), storage.getFreeBytes(StorageManager.UUID_DEFAULT))
    }.getOrNull()

    /** The sizes of [packages] in the personal profile. One Android can't tell is left out. */
    fun sizes(packages: Collection<String>): List<AppSize> = packages.mapNotNull { pkg ->
        runCatching {
            val s = storage.queryStatsForPackage(StorageManager.UUID_DEFAULT, pkg, Process.myUserHandle())
            AppSize(pkg, s.appBytes, s.dataBytes, s.cacheBytes)
        }.getOrNull()
    }

    /**
     * When each app was last in front, by package, as far back as Android keeps its yearly totals
     * (about 2 years). An app with no record there is not in the map: it was not used in that time,
     * or Android doesn't know.
     */
    fun lastUsed(now: Long = System.currentTimeMillis()): Map<String, Long> = runCatching {
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val out = HashMap<String, Long>()
        // Each kind of total: the yearly ones reach furthest back, the daily ones have the last days.
        val day = 86_400_000L
        val kinds = listOf(
            UsageStatsManager.INTERVAL_DAILY to 14 * day, UsageStatsManager.INTERVAL_WEEKLY to 8 * 7 * day,
            UsageStatsManager.INTERVAL_MONTHLY to 7 * 31 * day, UsageStatsManager.INTERVAL_YEARLY to 2 * 366 * day,
        )
        for ((kind, back) in kinds) for (s in usage.queryUsageStats(kind, now - back, now).orEmpty()) {
            if (s.lastTimeUsed > 0) out[s.packageName] = maxOf(out[s.packageName] ?: 0L, s.lastTimeUsed)
        }
        out
    }.getOrDefault(emptyMap())
}
