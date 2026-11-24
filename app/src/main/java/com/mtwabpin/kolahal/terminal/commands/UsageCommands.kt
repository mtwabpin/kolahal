/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.unlock.UnlockCounter
import com.mtwabpin.kolahal.usage.DataUsage
import com.mtwabpin.kolahal.usage.DayCounts
import com.mtwabpin.kolahal.usage.StorageUsage
import java.time.LocalDate
import java.time.ZoneId

/**
 * `usage`, `unlocks`, `data today`, `data month` and `storage`: what Android's usage data tells of
 * the phone's screen time, unlocks, mobile and Wi-Fi data, and storage. All but `unlocks` need
 * Usage Access; `unlocks` counts without it while Kolahal runs. `usage` with a word of
 * [UsageWords.WORDS] shows Android's weeks, months and years ([UsageSummaryCommands]).
 */
class UsageCommands(context: Context) : CommandGroup(context) {
    private val apps = AppRepository.get(context)
    private val unlocks = UnlockCounter.get(context)
    private val config = Config.get(context)
    private val summary by lazy { UsageSummaryCommands(context) }

    fun run(name: String, args: List<String>) {
        // Unlocks are counted without usage access too: while Kolahal runs.
        if (name == "unlocks") return unlocks(args)
        // Screen time says itself how to allow usage access.
        if (name == "usage" || name == "screentime") return if (UsageWords.of(args) is UsageWords.Asked.Summary) summary.run(
            args
        ) else usage(args)
        if (!com.mtwabpin.kolahal.usage.UsageAccess.granted(context)) return fix(
            "${if (name == "data") "Data use" else "Storage per app"} needs usage access\nTap to allow it",
            "allow usage"
        )
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
        val big = listOfNotNull(
            s.mobile?.let { bytes(it) to "mobile today" },
            bytes(s.wifi) to "Wi-Fi today"
        )
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
        val now = System.currentTimeMillis()
        // The plan's month, as the data-month widget and usage.data("month") count it ([DataCycle]).
        val cycle = com.mtwabpin.kolahal.usage.DataCycle.of(today, config[Keys.DATA_START_DAY])
        val s = DataUsage(context).between(cycle.startMs(zone), now)
        val since = TimeFormats.dayLabel(cycle.start, today, capital = false)
        val big = listOfNotNull(
            s.mobile?.let { bytes(it) to "mobile since $since" },
            bytes(s.wifi) to if (s.mobile == null) "Wi-Fi since $since" else "Wi-Fi"
        )
        out(StyledText.stats(big))
        val plan = com.mtwabpin.kolahal.usage.DataCycle.planBytes(config[Keys.DATA_PLAN])
        val mobile = s.mobile
        if (plan != null && mobile != null) {
            // The pace by the time gone, not by whole days: just after midnight a new day is not a whole one.
            val pace = cycle.pace(mobile, now, zone)
            val left = cycle.daysLeft(today)
            out(
                StyledText.bar(
                    mobile.toDouble(),
                    plan.toDouble(),
                    if (pace > plan) StyledText.ColorRole.BAD else null
                )
            )
            info("${(mobile * 100 / plan)}% of ${bytes(plan)}\nOn pace for ${bytes(pace)}, $left ${if (left == 1L) "day" else "days"} left")
        } else if (mobile != null) {
            info("No data plan set\n'$SET_PLAN' sets a 75 GB plan", LogContext.of(LogContext.COPY to SET_PLAN))
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
            .map { (name, list) ->
                DataUsage.App(
                    list.first().uid,
                    list.sumOf { it.mobile },
                    list.sumOf { it.wifi }) to name
            }
            .sortedByDescending { it.first.total }
            // An app that used less than 1 MB would show "–" twice: it counts toward "Other" instead.
            .filter { it.first.total >= MB }
            .take(SHOWN)
        val mobileOther = (s.mobile ?: s.apps.sumOf { it.mobile }) - shown.sumOf { it.first.mobile }
        val wifiOther = s.wifi - shown.sumOf { it.first.wifi }
        val rows = mutableListOf(listOf("App", "Mobile", "Wi-Fi"))
        shown.forEach { (a, name) ->
            rows += listOf(
                name,
                if (s.mobile == null) "–" else bytesOrDash(a.mobile),
                bytesOrDash(a.wifi)
            )
        }
        if (mobileOther > MB || wifiOther > MB) rows += listOf(
            "Other",
            if (s.mobile == null) "–" else bytesOrDash(mobileOther),
            bytesOrDash(wifiOther)
        )
        // Only the header: every app used under 1 MB so far (just after midnight).
        if (rows.size == 1) info("Every app used under 1 MB so far") else out(
            StyledText.table(
                rows,
                header = true,
                align = "lrr"
            )
        )
        if (s.mobile == null) info("Mobile data per app needs Android 10+")
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
        return packages.firstNotNullOfOrNull { pkg ->
            com.mtwabpin.kolahal.apps.AppNames.android(
                context,
                pkg
            )
        } ?: "Android system"
    }

    private fun personal(pkg: String): AppEntry? =
        apps.apps.value.firstOrNull { it.pkg == pkg && it.profile.kind == ProfileKind.PERSONAL }

    // ---- screen time ----------------------------------------------------------------------

    /** `usage [days]`: the foreground time per app from UsageStats. `usage <app>`: one app in detail ([appUsage]). Needs Usage Access. The words go to [UsageSummaryCommands] before this. */
    private fun usage(args: List<String>) {
        if (!com.mtwabpin.kolahal.usage.UsageAccess.granted(context)) {
            fix("Screen time needs usage access\nTap to allow it", "allow usage")
            return
        }
        val words = UsageWords.of(args)
        if (words is UsageWords.Asked.App) {
            // Without a profile flag, the personal app first: Android gives Kolahal only its use.
            val matches =
                apps.search(CommandParsing.withoutFlags(args), ProfileKind.fromFlags(args))
            val app = matches.firstOrNull { it.profile.kind == ProfileKind.PERSONAL }
                ?: matches.firstOrNull()
                ?: return error("No app matches '${CommandParsing.withoutFlags(args)}'")
            return appUsage(app)
        }
        val asked = (words as? UsageWords.Asked.Days)?.days ?: 1
        val reader = com.mtwabpin.kolahal.usage.AppUsage(context)
        // Only the days Android has data for: a day before its usage history begins is left out
        // ([com.mtwabpin.kolahal.usage.UsageHistory]). The events of the whole range are read once.
        val dataDays = com.mtwabpin.kolahal.usage.UsageHistory.days(context, asked)
        val days = dataDays.size
        val today = LocalDate.now()
        val start = reader.startOf(dataDays.first())
        val read = reader.read(start)
        // Each app by its name in the app list, the personal app first; a package with no name counts toward "Other apps".
        val byPkg = apps.byPackage()
        val times = reader.times(read.sessions) { byPkg[it]?.label }
        val sum = times.total
        if (sum < 60_000) return info("No app usage recorded ${if (days == 1) "today" else "in $days days"}")
        // Big numbers: the screen time today; over several days, a day's average (over the finished
        // days: today has only its hours so far) and the total.
        val finished = dataDays.filter { it != today }
        val big = if (days == 1) listOf(duration(sum) to "screen time today")
        else listOf(
            duration(
                reader.total(
                    read.between(
                        start,
                        reader.startOf(today)
                    )
                ) / finished.size.coerceAtLeast(1)
            ) to "a day",
            duration(sum) to "in $days days",
        )
        out(StyledText.stats(big))
        if (days < asked) info("$days of $asked days")
        val named = times.apps.map { Triple(it.label, byPkg[it.pkg], it.ms) }
        val shown = named.take(12)
        val other = sum - shown.sumOf { it.third }
        val max = maxOf(shown.firstOrNull()?.third ?: 0L, other)
        shown.forEach { (label, app, ms) ->
            out(
                label,
                // A bar on one scale before the time: the most-used app fills its bar.
                (if (app != null) appContext(app) else LogContext.EMPTY) + mapOf(
                    LogContext.DESC to duration(
                        ms
                    )
                ) + LogContext.bar(ms.toDouble() / max),
            )
        }
        // The rest, so the times add up to the total: apps past the first 12, and those without a name or under a minute.
        if (other >= 60_000) info(
            "Other apps",
            LogContext.of(LogContext.DESC to duration(other)) + LogContext.bar(other.toDouble() / max)
        )
    }

    /**
     * `usage <app>`: one app today and in the last days.
     * - Big numbers: its time today, how often it was opened, and its longest session, all from
     *   Android's usage events since midnight ([com.mtwabpin.kolahal.usage.AppUsage]), read once.
     * - When it was last used, a column for each hour today (or of the last day it was used), then
     *   a line for each of the last 7 days with data, with a bar, and the average of the finished days.
     * - A work or private app has no data here: Android gives Kolahal only its own profile's.
     */
    private fun appUsage(app: AppEntry) {
        if (app.profile.kind != ProfileKind.PERSONAL) {
            return info("No screen time for ${app.profile.kind.label} apps\nAndroid gives Kolahal only the personal profile's")
        }
        val reader = com.mtwabpin.kolahal.usage.AppUsage(context)
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val dataDays = com.mtwabpin.kolahal.usage.UsageHistory.days(context, 7, today)
        val read = reader.read(reader.startOf(dataDays.first()))
        fun day(d: LocalDate) =
            read.between(reader.startOf(d), reader.startOf(d.plusDays(1)))

        val days = dataDays.map { d ->
            d to (com.mtwabpin.kolahal.usage.AppSessions.totals(day(d))[app.pkg] ?: 0L)
        }
        val s = com.mtwabpin.kolahal.usage.AppSessions.of(day(today), app.pkg, read.inFront, zone)
        out(
            StyledText.stats(
                listOf(
                    duration(s.total) to "${app.label} today",
                    s.opens.toString() to if (s.opens == 1) "open" else "opens",
                    duration(s.longest) to "longest",
                )
            ), appContext(app)
        )
        val clock = TimeFormats.clockFormat(
            Config.get(context)[Keys.CLOCK_24H]
        )

        fun at(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).format(clock)
        when {
            s.inFront -> info("In use now")
            s.lastUsed != null -> info("Last used ${at(s.lastUsed)}" + (s.longestAt?.let {
                " · longest at ${
                    at(
                        it
                    )
                }"
            } ?: ""))

            else -> info("Not used today")
        }
        // A column for each hour: today's, or, when it wasn't used today, the last day it was used of these.
        val chartDay = if (s.total > 0) today else days.lastOrNull { it.second > 0 }?.first
        if (chartDay != null) {
            val hours =
                if (chartDay == today) s.hours else com.mtwabpin.kolahal.usage.AppSessions.of(
                    day(chartDay), app.pkg, null, zone
                ).hours
            // Today has the hours so far, in a chart of the whole day: the hours still to come keep
            // their places, and the hour now stands out. A past day has all 24, none standing out.
            val upTo = if (chartDay == today) java.time.LocalTime.now().hour + 1 else 24
            val minutes = hours.take(upTo).map { it / 60_000.0 }
            val dayName =
                TimeFormats.dayLabel(chartDay, today, capital = false)
            if (chartDay != today) info("By hour, $dayName")
            val busy = minutes.withIndex().filter { it.value >= 1 }
            val said = "${app.label} by hour, $dayName: " +
                    if (busy.isEmpty()) "under a minute in every hour" else busy.joinToString(", ") {
                        "%02d:00 %dm".format(
                            it.index,
                            it.value.toInt()
                        )
                    }
            out(
                StyledText.columns(
                    minutes,
                    listOf("00", "06", "12", "18", "24"),
                    said,
                    slots = 24,
                    newest = chartDay == today
                )
            )
        }
        if (days.size <= 1) return
        val labels = days.map { (d, _) ->
            TimeFormats.dayLabel(
                d,
                today,
                capital = true
            )
        }
        val width = labels.maxOf { it.length }
        // The times padded to one width, so every bar starts in the same place.
        val timeWidth = days.maxOf { duration(it.second).length }
        val most = days.maxOf { it.second }.coerceAtLeast(1L)
        days.forEachIndexed { i, (_, ms) ->
            out(
                "${labels[i].padEnd(width)}  ${duration(ms).padStart(timeWidth)}",
                LogContext(LogContext.ALIGNED + LogContext.bar(ms.toDouble() / most))
            )
        }
        // The average of the finished days: today has only its hours so far.
        val finished = days.filter { it.first != today }
        if (finished.isNotEmpty()) info("${duration(finished.sumOf { it.second } / finished.size)} a day · ${finished.size} ${if (finished.size == 1) "day" else "days"}")
    }

    private fun duration(ms: Long) = TimeFormats.duration(ms)

    // ---- unlocks --------------------------------------------------------------------------

    /** Says how unlocks are counted without usage access: only while Kolahal runs. */
    private fun noAccessNote() {
        info("Counting unlocks only while Kolahal runs")
        info(
            "Tap to allow usage access for exact counts",
            LogContext.of(LogContext.RUN to "allow usage")
        )
    }

    /**
     * Big numbers for [history]: today's count, and over several days the average of the finished
     * days ([DayCounts.finishedAverage]: today has only its hours so far) and the total.
     */
    private fun unlockStats(history: List<Pair<LocalDate, Int>>, today: LocalDate) {
        val todayCount = history.lastOrNull { it.first == today }?.second ?: 0
        val big =
            mutableListOf(todayCount.toString() to if (todayCount == 1) "unlock today" else "unlocks today")
        if (history.size > 1) {
            DayCounts.finishedAverage(history, today)?.let { big += it.toString() to "a day" }
            big += "%,d".format(history.sumOf { it.second }) to "in ${history.size} days"
        }
        out(StyledText.stats(big))
    }

    /** With fewer days than [asked], a line says why. */
    private fun fewerDaysNote(count: Int, asked: Int) {
        if (count >= asked) return
        info("$count of $asked days")
    }

    /** `unlocks [<days>]`: today's count, or a line a day with a bar. `unlocks week` and `unlocks month`: a chart ([unlocksChart]). */
    private fun unlocks(args: List<String>) {
        when (args.firstOrNull()?.lowercase()) {
            "week" -> return unlocksChart(7)
            "month" -> return unlocksChart(30)
        }
        val days = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 30) ?: 1
        if (!com.mtwabpin.kolahal.usage.UsageAccess.granted(context)) noAccessNote()
        val today = LocalDate.now()
        // Only the days with a count: the days before Android's usage history begins are left out
        // ([UnlockCounter.history]).
        val history = unlocks.history(days)
        unlockStats(history, today)
        // One day: the big number says it all. Over several, a line a day with a bar.
        if (history.size <= 1) return fewerDaysNote(history.size, days)
        val rows = history.map { (day, count) ->
            TimeFormats.dayLabel(
                day,
                today,
                capital = true
            ) to count
        }
        // Pad the labels so the counts line up. The log uses a monospaced font.
        val labelWidth = rows.maxOf { it.first.length }
        val countWidth = rows.maxOf { it.second.toString().length }
        // A bar after each count, on one scale: the busiest day fills its bar. "unlock" gets a no-break
        // space, which a line keeps at its end, so its bar starts where the others do.
        val most = rows.maxOf { it.second }.coerceAtLeast(1)
        rows.forEach { (label, count) ->
            out(
                "${label.padEnd(labelWidth)}  ${
                    count.toString().padStart(countWidth)
                } ${if (count == 1) "unlock " else "unlocks"}",
                LogContext(LogContext.ALIGNED + LogContext.bar(count.toDouble() / most))
            )
        }
        fewerDaysNote(history.size, days)
    }

    /**
     * `unlocks week` and `unlocks month`: the last [days] days as a chart, from the first day that
     * has a count ([UnlockCounter.history]).
     * - Big numbers: today, a day's average over the finished days, and the total.
     * - Over a month: how the last 7 finished days compare with the 7 before ([DayCounts.trend]).
     * - A column a day, the oldest first, then (over a month) a line for each week, Monday to
     *   Sunday, with its average over its finished days.
     * - With fewer days than asked, a line says why.
     */
    private fun unlocksChart(days: Int) {
        if (!com.mtwabpin.kolahal.usage.UsageAccess.granted(context)) noAccessNote()
        val today = LocalDate.now()
        val history = unlocks.history(days)
        unlockStats(history, today)
        if (days == 30) DayCounts.trend(history, today)?.let { change ->
            info(
                when {
                    change < 0 -> "▼ ${-change}% · last 7 days"
                    change > 0 -> "▲ $change% · last 7 days"
                    else -> "0% · last 7 days"
                }
            )
        }
        if (history.size > 1) {
            val label = { d: LocalDate -> TimeFormats.dayLabel(d, today, capital = true) }
            val said = "Unlocks a day: " + history.joinToString(", ") { (d, n) -> "${label(d)} $n" }
            out(
                StyledText.columns(
                    history.map { it.second.toDouble() },
                    listOf(label(history.first().first), "Today"),
                    said
                )
            )
        }
        if (days == 30) weeks(history, today)
        fewerDaysNote(history.size, days)
    }

    /** A line for each week, Monday to Sunday, the newest first, with its average over its finished days and a bar ([DayCounts.weeks]). */
    private fun weeks(history: List<Pair<LocalDate, Int>>, today: LocalDate) {
        val weeks = DayCounts.weeks(history, today)
        if (weeks.size < 2) return
        val fmt =
            java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.getDefault())
        val rows = weeks.map { w ->
            val end = w.monday.plusDays(6)
            val name = when {
                w.monday == DayCounts.monday(today) -> "This week"
                w.monday.month == end.month -> "${w.monday.dayOfMonth}–${end.format(fmt)}"
                else -> "${w.monday.format(fmt)}–${end.format(fmt)}"
            }
            // A week with fewer days says how many, padded to one width, so every bar starts in the same place.
            Triple(
                name,
                w.average,
                if (w.days < 7) "${w.days} ${if (w.days == 1) "day" else "days"}" else ""
            )
        }
        info(StyledText.color(StyledText.ColorRole.MUTED, "── by week ──"))
        val width = rows.maxOf { it.first.length }
        val countWidth = rows.maxOf { it.second.toString().length }
        val noteWidth = rows.maxOf { it.third.length }
        val most = rows.maxOf { it.second }.coerceAtLeast(1)
        rows.forEach { (name, average, note) ->
            val tail = if (noteWidth == 0) "" else "  " + note.padEnd(noteWidth, ' ')
            out(
                "${name.padEnd(width)}  ${average.toString().padStart(countWidth)}$tail",
                LogContext(LogContext.ALIGNED + LogContext.bar(average.toDouble() / most))
            )
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
            out(
                StyledText.stats(
                    listOf(
                        bytes(sp.free) to "free",
                        bytes(sp.used) to "used of ${bytes(sp.total)}"
                    )
                )
            )
            out(StyledText.bar(sp.used.toDouble(), sp.total.toDouble()))
        }
        val launchable =
            apps.apps.value.filter { it.profile.kind == ProfileKind.PERSONAL }.distinctBy { it.pkg }
        val byPkg = launchable.associateBy { it.pkg }
        val sizes = reader.sizes(byPkg.keys).sortedByDescending { it.size }
        if (sizes.isNotEmpty()) {
            info("Largest apps")
            val rows = mutableListOf(listOf("App", "Size", "Cache"))
            sizes.take(SHOWN).forEach { s ->
                rows += listOf(
                    byPkg[s.pkg]?.label ?: s.pkg,
                    bytes(s.size),
                    bytes(s.cache)
                )
            }
            out(StyledText.table(rows, header = true, align = "lrr"))
            info("Size is the app, its data and its cache")
        }
        val unused = reader.unused(
            byPkg.keys,
            UNUSED_DAYS,
            sizes.associate { it.pkg to it.size }) { byPkg[it]?.lower ?: it }
        if (unused.isEmpty()) return info("No unused apps\nEvery app you can uninstall was opened in the last $UNUSED_DAYS days")
        info("Not opened for $UNUSED_DAYS days or more")
        unused.take(UNUSED_SHOWN).forEach { u ->
            val app = byPkg.getValue(u.pkg)
            val words = listOfNotNull(
                if (u.days == null) "no use recorded" else "${u.days} days",
                u.bytes?.let(::bytes)
            ).joinToString(" · ")
            out(app.label, appContext(app) + mapOf(LogContext.DESC to words))
        }
        val total = unused.sumOf { it.bytes ?: 0L }
        val count =
            if (unused.size > UNUSED_SHOWN) "$UNUSED_SHOWN of ${unused.size} apps" else "${unused.size} ${if (unused.size == 1) "app" else "apps"}"
        info("$count${if (total > 0) ", ${bytes(total)} in all" else ""}\nLong-press one for its info or to uninstall")
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
        val NAMES = setOf("storage", "unlocks", "usage", "screentime")

        private const val MB = 1_000_000L
        private const val GB = 1_000_000_000L

        /** The command that `data month` offers with no data plan: a plan of 75 GB, as an example. */
        private const val SET_PLAN = "config set data.plan 75"

        /** The apps a table shows; the rest are "Other". */
        private const val SHOWN = 8

        /** How long an app has not been opened to count as unused, in days. */
        const val UNUSED_DAYS = 30L
        private const val UNUSED_SHOWN = 20
    }
}
