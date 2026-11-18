package com.mtwabpin.kolahal.terminal

import android.content.Context
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.TimeFormats
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
        // Unlocks are counted without usage access too: while Kolahal runs.
        if (name == "unlocks") return unlocks(args)
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
            // An app that used less than 1 MB would show "–" twice: it counts toward "Other" instead.
            .filter { it.first.total >= MB }
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

    // ---- unlocks --------------------------------------------------------------------------

    /** `unlocks [<days>]`: today's count, or a line a day with a bar. `unlocks week` and `unlocks month`: a chart ([unlocksChart]). */
    private fun unlocks(args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            "week" -> return unlocksChart(7)
            "month" -> return unlocksChart(30)
        }
        val days = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 30) ?: 1
        if (!unlocks.hasUsageAccess()) {
            info("Counting unlocks only while the launcher is running")
            info("Tap here to allow usage access for accurate counts", LogContext.of(LogContext.RUN to "usage-access"))
        }
        val today = java.time.LocalDate.now()
        // Only the days with a count: a day before Android's usage history begins is left out, and the
        // average is over the days that are here ([UnlockCounter.countOrNull]).
        val history = unlocks.history(days)
        val rows = history.map { (day, count) -> TimeFormats.dayLabel(day, today, capital = true) to count }
        // Big numbers: today's count; over several days, the average and the total too.
        val todayCount = history.firstOrNull { it.first == today }?.second ?: 0
        val big = mutableListOf(todayCount.toString() to if (todayCount == 1) "unlock today" else "unlocks today")
        if (rows.size > 1) {
            val total = rows.sumOf { it.second }
            big += (total / rows.size).toString() to "a day on average"
            big += total.toString() to "in ${rows.size} days"
        }
        out(com.mtwabpin.kolahal.data.StyledText.stats(big))
        // One day: the big number says it all. Over several, a line a day with a bar.
        if (rows.size <= 1) return
        // Pad the labels so the counts line up. The log uses a monospaced font.
        val labelWidth = rows.maxOf { it.first.length }
        val countWidth = rows.maxOf { it.second.toString().length }
        // A bar after each count, on one scale: the busiest day fills its bar. "unlock" gets a no-break
        // space, which a line keeps at its end, so its bar starts where the others do.
        val most = rows.maxOf { it.second }.coerceAtLeast(1)
        rows.forEach { (label, count) ->
            out("${label.padEnd(labelWidth)}  ${count.toString().padStart(countWidth)} ${if (count == 1) "unlock\u00A0" else "unlocks"}", LogContext(LogContext.ALIGNED + LogContext.bar(count.toDouble() / most)))
        }
    }

    /**
     * `unlocks week` and `unlocks month`: the last [days] days as a chart, with only the days that
     * have a count ([UnlockCounter.countOrNull]): a day with no data is left out, not drawn as 0.
     * - Big numbers: today, a day's average and the total over those days.
     * - Over a month: how the last 7 days compare with the 7 before, when both have 3 days or more.
     * - A column a day, the oldest first, then (over a month) a line for each week, Monday to
     *   Sunday, with its average over its days that have a count.
     * - With fewer days than asked, a line says why: Android keeps about 10, and Kolahal saves each
     *   day from 0.5.12 on.
     */
    private fun unlocksChart(days: Int) {
        if (!unlocks.hasUsageAccess()) {
            info("Counting unlocks only while the launcher is running")
            info("Tap here to allow usage access for accurate counts", LogContext.of(LogContext.RUN to "usage-access"))
        }
        val today = LocalDate.now()
        val history = unlocks.history(days)
        val todayCount = history.lastOrNull { it.first == today }?.second ?: 0
        val total = history.sumOf { it.second }
        val big = mutableListOf(todayCount.toString() to if (todayCount == 1) "unlock today" else "unlocks today")
        if (history.size > 1) {
            big += (total / history.size).toString() to "a day on average"
            big += "%,d".format(total) to "in ${history.size} days"
        }
        out(StyledText.stats(big))
        if (days == 30) trend(history, today)?.let { info(it) }
        if (history.size > 1) {
            val label = { d: LocalDate -> com.mtwabpin.kolahal.data.TimeFormats.dayLabel(d, today, capital = true) }
            val said = "Unlocks a day: " + history.joinToString(", ") { (d, n) -> "${label(d)} $n" }
            out(StyledText.columns(history.map { it.second.toDouble() }, listOf(label(history.first().first), "Today"), said))
        }
        if (days == 30) weeks(history, today)
        if (history.size < days) info("${history.size} ${if (history.size == 1) "day" else "days"} so far: Android keeps about 10, and Kolahal saves each day's count from now on")
    }

    /** "▼ 6% a day against the 7 days before", or null when either 7 days has fewer than 3 days with a count. */
    private fun trend(history: List<Pair<LocalDate, Int>>, today: LocalDate): String? {
        fun average(from: Long, to: Long): Double? {
            val inRange = history.filter { (d, _) -> d in today.minusDays(to)..today.minusDays(from) }
            return if (inRange.size < 3) null else inRange.sumOf { it.second }.toDouble() / inRange.size
        }
        val last = average(0, 6) ?: return null
        val before = average(7, 13)?.takeIf { it > 0 } ?: return null
        val change = Math.round((last - before) / before * 100)
        return when {
            change < 0 -> "▼ ${-change}% a day, the last 7 days against the 7 before"
            change > 0 -> "▲ $change% a day, the last 7 days against the 7 before"
            else -> "The same a day as the 7 days before"
        }
    }

    /** A line for each week, Monday to Sunday, the newest first, with its average over its days that have a count, and a bar. */
    private fun weeks(history: List<Pair<LocalDate, Int>>, today: LocalDate) {
        val monday = { d: LocalDate -> d.minusDays((d.dayOfWeek.value - 1).toLong()) }
        val byWeek = history.groupBy { monday(it.first) }.toSortedMap(compareByDescending { it })
        if (byWeek.size < 2) return
        val fmt = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())
        val rows = byWeek.map { (start, list) ->
            val end = start.plusDays(6)
            val name = when {
                start == monday(today) -> "This week"
                start.month == end.month -> "${start.dayOfMonth}–${end.format(fmt)}"
                else -> "${start.format(fmt)}–${end.format(fmt)}"
            }
            Triple(name, list.sumOf { it.second } / list.size, list.size)
        }
        info("By week, a day on average")
        val width = rows.maxOf { it.first.length }
        val countWidth = rows.maxOf { it.second.toString().length }
        val most = rows.maxOf { it.second }.coerceAtLeast(1)
        rows.forEach { (name, average, n) ->
            val partial = if (n < 7) " · $n ${if (n == 1) "day" else "days"}" else ""
            out("${name.padEnd(width)}  ${average.toString().padStart(countWidth)}$partial", LogContext(LogContext.ALIGNED + LogContext.bar(average.toDouble() / most)))
        }
    }

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
        val unused = reader.unused(byPkg.keys, UNUSED_DAYS, sizes.associate { it.pkg to it.size }) { byPkg[it]?.lower ?: it }
        if (unused.isEmpty()) return info("Every app was opened in the last $UNUSED_DAYS days")
        info("Not opened for $UNUSED_DAYS days or more")
        unused.take(UNUSED_SHOWN).forEach { u ->
            val app = byPkg.getValue(u.pkg)
            val words = listOfNotNull(if (u.days == null) "no use recorded" else "${u.days} days", u.bytes?.let(::bytes)).joinToString(" · ")
            out(app.label, appContext(app) + mapOf(LogContext.DESC to words))
        }
        val total = unused.sumOf { it.bytes ?: 0L }
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
        val NAMES = setOf("storage", "unlocks")

        private const val MB = 1_000_000L
        private const val GB = 1_000_000_000L

        /** The apps a table shows; the rest are "Other". */
        private const val SHOWN = 8

        /** How long an app has not been opened to count as unused, in days. */
        const val UNUSED_DAYS = 30L
        private const val UNUSED_SHOWN = 20
    }
}
