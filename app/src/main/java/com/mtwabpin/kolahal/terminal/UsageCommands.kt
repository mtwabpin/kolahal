package com.mtwabpin.kolahal.terminal

import android.content.Context
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.unlock.UnlockCounter
import com.mtwabpin.kolahal.usage.DataUsage
import com.mtwabpin.kolahal.usage.StorageUsage
import java.time.LocalDate
import java.time.ZoneId

/**
^ * `data today`, `data month` and `storage`: what Android's usage data tells of the phone's mobile and Wi-Fi data, and of
 * its storage. Both need Usage Access, as `usage` does.
 */
class UsageCommands(context: Context) : CommandGroup(context) {
    private val apps = AppRepository.get(context)
    private val unlocks = UnlockCounter.get(context)
    private val config = Config.get(context)

    fun run(name: String, args: List<String>) {
        if (!unlocks.hasUsageAccess()) return fix("${if (name == "data") "Data use" else "Storage per app"} needs usage access — tap to allow it", "usage-access")
        when (name) {
            "data" -> when (args.firstOrNull()?.lowercase()) {
                "today" -> dataToday()
                "month" -> dataMonth()
                else -> usage("data")
            }
            "storage" -> storage()
        }
    }

    // ---- data -----------------------------------------------------------------------------

    private val zone get() = ZoneId.systemDefault()

    private fun startOf(day: LocalDate) = day.atStartOfDay(zone).toInstant().toEpochMilli()

    /** `data`: today's mobile and Wi-Fi data, then each app's, biggest first. */
    private fun dataToday() {
        val s = DataUsage(context).between(startOf(LocalDate.now()), System.currentTimeMillis())
        val big = listOfNotNull(s.mobile?.let { bytes(it) to "mobile today" }, bytes(s.wifi) to "Wi-Fi today")
        out(StyledText.stats(big))
        appTable(s)
    }

    /**
     * `data month`: the data since the plan's day this month ([Keys.DATA_START_DAY]), then each
     * app's. With a plan ([Keys.DATA_PLAN]): a bar of how much of it is used, the pace for the whole
     * month, and the days left.
     */
    private fun dataMonth() {
        val today = LocalDate.now()
        val day = config[Keys.DATA_START_DAY]
        val thisMonth = today.withDayOfMonth(day)
        val start = if (today.dayOfMonth >= day) thisMonth else thisMonth.minusMonths(1)
        val next = start.plusMonths(1)
        val s = DataUsage(context).between(startOf(start), System.currentTimeMillis())
        val since = com.mtwabpin.kolahal.data.TimeFormats.dayLabel(start, today, capital = false)
        val big = listOfNotNull(s.mobile?.let { bytes(it) to "mobile since $since" }, bytes(s.wifi) to "Wi-Fi")
        out(StyledText.stats(big))
        val plan = config[Keys.DATA_PLAN].toLong() * GB
        val mobile = s.mobile
        if (plan > 0 && mobile != null) {
            val daysIn = java.time.temporal.ChronoUnit.DAYS.between(start, next).coerceAtLeast(1)
            val gone = java.time.temporal.ChronoUnit.DAYS.between(start, today) + 1
            val pace = mobile * daysIn / gone.coerceAtLeast(1)
            val left = java.time.temporal.ChronoUnit.DAYS.between(today, next)
            out(StyledText.bar(mobile.toDouble(), plan.toDouble(), if (pace > plan) StyledText.Colour.BAD else null))
            info("${(mobile * 100 / plan)}% of ${bytes(plan)} · on pace for ${bytes(pace)} · $left ${if (left == 1L) "day" else "days"} left")
        } else if (mobile != null) {
            info("With your plan in GB, this shows how much is left: config set data.plan 75")
        }
        appTable(s)
    }

    /**
     * A table of the apps that used the most, mobile and Wi-Fi, with "Other" for the rest so the
     * columns add up to the totals. Android gives only the personal profile's apps: the work
     * profile's data is in "Other".
     */
    private fun appTable(s: DataUsage.Summary) {
        if (s.apps.isEmpty() && (s.mobile ?: 0L) + s.wifi == 0L) return info("No data used")
        val shown = s.apps.groupBy { nameOf(it.uid) }
            .map { (name, list) -> DataUsage.App(list.first().uid, list.sumOf { it.mobile }, list.sumOf { it.wifi }) to name }
            .sortedByDescending { it.first.total }
            .take(SHOWN)
        val mobileOther = (s.mobile ?: s.apps.sumOf { it.mobile }) - shown.sumOf { it.first.mobile }
        val wifiOther = s.wifi - shown.sumOf { it.first.wifi }
        val rows = mutableListOf(listOf("App", "Mobile", "Wi-Fi"))
        shown.forEach { (a, name) -> rows += listOf(name, if (s.mobile == null) "–" else bytesOrDash(a.mobile), bytesOrDash(a.wifi)) }
        if (mobileOther > MB || wifiOther > MB) rows += listOf("Other", if (s.mobile == null) "–" else bytesOrDash(mobileOther), bytesOrDash(wifiOther))
        out(StyledText.table(rows, header = true, align = "lrr"))
        if (s.mobile == null) info("Mobile data per app needs Android 10 or newer")
    }

    /** The name for Android's user id [uid]: its app's name, or what the system calls the rest. */
    private fun nameOf(uid: Int): String {
        when (uid) {
            android.app.usage.NetworkStats.Bucket.UID_TETHERING -> return "Hotspot"
            android.app.usage.NetworkStats.Bucket.UID_REMOVED -> return "Removed apps"
        }
        if (uid % 100_000 < android.os.Process.FIRST_APPLICATION_UID) return "Android system"
        val packages = context.packageManager.getPackagesForUid(uid).orEmpty()
        // Several packages can share one id: the one with a name in the app list, if any.
        val app = packages.firstNotNullOfOrNull { pkg -> personal(pkg) }
        if (app != null) return app.label
        val pm = context.packageManager
        return packages.firstNotNullOfOrNull { pkg ->
            runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()?.takeIf { it.isNotBlank() && it != pkg }
        } ?: "Android system"
    }

    private fun personal(pkg: String): AppEntry? = apps.apps.value.firstOrNull { it.pkg == pkg && it.profile.kind == ProfileKind.PERSONAL }

    // ---- storage --------------------------------------------------------------------------

    /**
     * `storage`: the phone's free and used space with a bar, the largest apps of the personal
     * profile with their cache, then the apps not opened for [UNUSED_DAYS] days or more: each a line
     * of its own, so a tap opens it and a long-press offers its info and uninstall.
     */
    private fun storage() {
        val reader = StorageUsage(context)
        reader.space()?.let { sp ->
            out(StyledText.stats(listOf(bytes(sp.free) to "free", bytes(sp.used) to "used of ${bytes(sp.total)}")))
            out(StyledText.bar(sp.used.toDouble(), sp.total.toDouble()))
        }
        val launchable = apps.apps.value.filter { it.profile.kind == ProfileKind.PERSONAL }.distinctBy { it.pkg }
        val byPkg = launchable.associateBy { it.pkg }
        val sizes = reader.sizes(byPkg.keys).sortedByDescending { it.size }
        if (sizes.isNotEmpty()) {
            info("Largest apps")
            val rows = mutableListOf(listOf("App", "Size", "Cache"))
            sizes.take(SHOWN).forEach { s -> rows += listOf(byPkg[s.pkg]?.label ?: s.pkg, bytes(s.size), bytes(s.cache)) }
            out(StyledText.table(rows, header = true, align = "lrr"))
            info("Size is the app and its data, cache included")
        }
        // Not opened for a while: by the last time Android saw it in front. An app it has no record of
        // has no day count: only how long ago it was installed, if that is long enough.
        val now = System.currentTimeMillis()
        val last = reader.lastUsed(now)
        val sizeOf = sizes.associate { it.pkg to it.size }
        val pm = context.packageManager
        val unused = launchable.mapNotNull { app ->
            if (app.pkg == context.packageName) return@mapNotNull null
            val installed = runCatching { pm.getPackageInfo(app.pkg, 0).firstInstallTime }.getOrDefault(now)
            if (now - installed < UNUSED_DAYS * DAY) return@mapNotNull null
            val seen = last[app.pkg]
            when {
                seen == null -> Triple(app, null, sizeOf[app.pkg])
                now - seen >= UNUSED_DAYS * DAY -> Triple(app, (now - seen) / DAY, sizeOf[app.pkg])
                else -> null
            }
        }.sortedWith(compareByDescending<Triple<AppEntry, Long?, Long?>> { it.second ?: Long.MAX_VALUE }.thenBy { it.first.lower })
        if (unused.isEmpty()) return info("Every app was opened in the last $UNUSED_DAYS days")
        info("Not opened for $UNUSED_DAYS days or more")
        unused.take(UNUSED_SHOWN).forEach { (app, days, size) ->
            val words = listOfNotNull(if (days == null) "no use recorded" else "$days days", size?.let(::bytes)).joinToString(" · ")
            out(app.label, appContext(app) + mapOf(LogContext.DESC to words))
        }
        val total = unused.sumOf { it.third ?: 0L }
        info("${unused.size} ${if (unused.size == 1) "app" else "apps"}${if (total > 0) ", ${bytes(total)}" else ""} · long-press one for its info or to uninstall")
    }

    /** [b] bytes as Android's Settings write them: 1000-based, GB with one decimal. */
    private fun bytes(b: Long): String = when {
        b >= GB -> "%.1f GB".format(java.util.Locale.ROOT, b.toDouble() / GB)
        b >= MB -> "${b / MB} MB"
        b > 0 -> "<1 MB"
        else -> "0 MB"
    }

    private fun bytesOrDash(b: Long) = if (b < MB) "–" else bytes(b)

    companion object {
        /** Commands handled by [run]. `data today` and `data month` come here too; `data` alone opens the data panel. */
        val NAMES = setOf("storage")

        private const val MB = 1_000_000L
        private const val GB = 1_000_000_000L
        private const val DAY = 86_400_000L

        /** The apps a table shows; the rest are "Other". */
        private const val SHOWN = 8

        /** How long an app has not been opened to count as unused, in days. */
        const val UNUSED_DAYS = 30L
        private const val UNUSED_SHOWN = 20
    }
}
