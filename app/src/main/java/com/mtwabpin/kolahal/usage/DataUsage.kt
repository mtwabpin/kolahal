/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.usage

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.ConnectivityManager

/**
 * Mobile and Wi-Fi data, for the phone and per app, from Android's network statistics. They need
 * Usage Access, as screen time does (`data`).
 * - The phone's totals are all its users' (the work profile too). Android gives the per-app numbers
 *   of the personal profile only: the rest is "Other".
 * - Android 9 tells an app the mobile data only with the SIM's subscriber id, which Kolahal does not
 *   read. There [Summary.mobile] is null, and only Wi-Fi shows.
 */
class DataUsage(context: Context) {
    private val stats = context.getSystemService(NetworkStatsManager::class.java)

    /** What one app (Android's user id for it, [uid]) sent and received, in bytes. */
    data class App(val uid: Int, val mobile: Long, val wifi: Long) {
        val total get() = mobile + wifi
    }

    /** The phone's totals, in bytes, and each app's. [mobile] is null when Android doesn't tell. */
    data class Summary(val mobile: Long?, val wifi: Long, val apps: List<App>)

    /** The data used in [from, until), biggest app first. */
    fun between(from: Long, until: Long): Summary {
        @Suppress("DEPRECATION") // the network types are what this API takes
        val mobileType = ConnectivityManager.TYPE_MOBILE
        @Suppress("DEPRECATION")
        val wifiType = ConnectivityManager.TYPE_WIFI
        // Android 9 tells the mobile data only with the SIM's subscriber id: unknown, not 0.
        val mobile = if (android.os.Build.VERSION.SDK_INT < 29) null else device(mobileType, from, until)
        val wifi = device(wifiType, from, until) ?: 0L
        val byUid = HashMap<Int, LongArray>()
        if (mobile != null) perApp(mobileType, from, until)?.forEach { (uid, bytes) -> byUid.getOrPut(uid) { LongArray(2) }[0] += bytes }
        perApp(wifiType, from, until)?.forEach { (uid, bytes) -> byUid.getOrPut(uid) { LongArray(2) }[1] += bytes }
        val apps = byUid.map { (uid, b) -> App(uid, b[0], b[1]) }.filter { it.total > 0 }.sortedByDescending { it.total }
        return Summary(mobile, wifi, apps)
    }

    /** The phone's bytes on [type] in [from, until), or null when Android doesn't tell. */
    private fun device(type: Int, from: Long, until: Long): Long? = runCatching {
        @Suppress("DEPRECATION")
        val bucket = stats.querySummaryForDevice(type, null, from, until)
        bucket.rxBytes + bucket.txBytes
    }.getOrNull()

    /** Each app's bytes on [type] in [from, until), by its user id; null when Android doesn't tell. */
    private fun perApp(type: Int, from: Long, until: Long): Map<Int, Long>? = runCatching {
        val out = HashMap<Int, Long>()
        stats.querySummary(type, null, from, until).use { list ->
            val bucket = NetworkStats.Bucket()
            while (list.hasNextBucket()) {
                list.getNextBucket(bucket)
                out[bucket.uid] = (out[bucket.uid] ?: 0L) + bucket.rxBytes + bucket.txBytes
            }
        }
        out
    }.getOrNull()
}
