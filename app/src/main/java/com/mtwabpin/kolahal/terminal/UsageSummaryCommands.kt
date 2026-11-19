package com.mtwabpin.kolahal.terminal

import android.content.Context
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.TimeFormats.duration
import com.mtwabpin.kolahal.unlock.UnlockCounter
import com.mtwabpin.kolahal.usage.UsageLevels
import com.mtwabpin.kolahal.usage.UsageLevels.Companion.Bucket
import com.mtwabpin.kolahal.usage.UsageLevels.Level
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * `usage-summary [week | month | year | full]`: the long view of screen time, from Android's
 * weeks, months and years ([UsageLevels]).
 * - Alone: a line for each level, with how far back it goes and the time a day in it.
 * - `week`, `month`, `year`: the time a day, unlocks a day and the total; a table with a row for
 *   each of Android's weeks, months or years, its total and its time a day; the change of the last
 *   one that is over; the top apps. `year` also has the apps used less or new.
 * - `full`: all of these, one after the other, parted by `── weeks ──` rules.
 * Few words: numbers lead, labels are short, `≈` marks Android's own (rough) totals, and the
 * explanations are in `help usage-summary`. Every date is the real start of one of Android's buckets.
 */
class UsageSummaryCommands(context: Context) : CommandGroup(context) {
    private val apps = AppRepository.get(context)
    private val unlocks = UnlockCounter.get(context)
    private val zone get() = ZoneId.systemDefault()

    fun run(args: List<String>) {
        if (!unlocks.hasUsageAccess()) return fix("Usage summary needs usage access — tap to allow it", "usage-access")
        val levels = UsageLevels(context)
        // Each level is read once, even by `full`. A year is filled from the months inside it, and a
        // month from its weeks: Android deletes a removed app's history from some levels only.
        val read = HashMap<Level, List<Bucket>>()
        fun buckets(level: Level): List<Bucket> = read.getOrPut(level) {
            val own = levels.read(level)
            when (level) {
                Level.YEARS -> UsageLevels.fill(own, buckets(Level.MONTHS))
                Level.MONTHS -> UsageLevels.fill(own, buckets(Level.WEEKS))
                else -> own
            }
        }
        when (args.firstOrNull()?.lowercase()) {
            null -> overview(::buckets)
            "week" -> period(buckets(Level.WEEKS), Level.WEEKS)
            "month" -> period(buckets(Level.MONTHS), Level.MONTHS)
            "year" -> years(buckets(Level.YEARS))
            "full" -> {
                overview(::buckets)
                for (level in listOf(Level.WEEKS, Level.MONTHS, Level.YEARS)) {
                    rule(unit(level, 2))
                    if (level == Level.YEARS) years(buckets(level)) else period(buckets(level), level)
                }
            }
            else -> usage("usage-summary")
        }
    }

    // ---- words ----------------------------------------------------------------------------

    /** A level's unit: "week", and with [n] the plural when it needs one. */
    private fun unit(level: Level, n: Int = 1): String {
        val one = when (level) { Level.DAYS -> "day"; Level.WEEKS -> "week"; Level.MONTHS -> "month"; Level.YEARS -> "year" }
        return if (n == 1) one else one + "s"
    }

    private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
    private val longDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
    private val monthYear = DateTimeFormatter.ofPattern("MMM yyyy", Locale.getDefault())

    /** A bucket's start: "13 Jun", with the year when it isn't this year: "17 Apr 2025". */
    private fun date(ms: Long): String {
        val day = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
        return day.format(if (day.year == java.time.LocalDate.now(zone).year) shortDate else longDate)
    }

    /** Long times in hours: "172h"; short ones as "4h 12m". */
    private fun hours(ms: Long): String = if (ms >= 10 * 3_600_000L) "${ms / 3_600_000}h" else duration(ms)

    /** "▼ 13%" or "▲ 8%", or "the same". */
    private fun change(percent: Long): String = when {
        percent < 0 -> "▼ ${-percent}%"
        percent > 0 -> "▲ $percent%"
        else -> "the same"
    }

    /** The app list by package, the personal app first, so a tap on its line opens that one. */
    private fun byPkg(): Map<String, AppEntry> =
        apps.apps.value.groupBy { it.pkg }.mapValues { (_, list) -> list.firstOrNull { it.profile.kind == ProfileKind.PERSONAL } ?: list.first() }

    /**
     * An app's name: the app list's, or Android's; else its package. Android names no app that was
     * removed, none it hides from Kolahal (since Android 11 an app sees only the apps it declares),
     * and none without a label of its own. Kolahal can't tell those apart, so it says only the
     * package, not why: an app's use counts, named or not.
     */
    private fun name(pkg: String, byPkg: Map<String, AppEntry>): String {
        byPkg[pkg]?.let { return it.label }
        return com.mtwabpin.kolahal.apps.AppNames.android(context, pkg) ?: pkg
    }

    /** The line of an app: [prefix] (a chip) and its name, and a tap opens it when it is in the app list. */
    private fun appLine(pkg: String, byPkg: Map<String, AppEntry>, desc: String, fraction: Double? = null, prefix: String = "") {
        val ctx = (byPkg[pkg]?.let { appContext(it) } ?: LogContext.EMPTY) + mapOf(LogContext.DESC to desc) +
            (fraction?.let { LogContext.bar(it) } ?: emptyMap())
        out(prefix + name(pkg, byPkg), ctx)
    }

    /** A thin rule with a label, `── top apps ──`: it parts the output where a heading or a sentence would. */
    private fun rule(label: String) = info(StyledText.colour(StyledText.Colour.MUTED, "── $label ──"))

    /** A change of time a day, coloured: less is good. */
    private fun coloured(text: String, less: Boolean) = StyledText.colour(if (less) StyledText.Colour.GOOD else StyledText.Colour.WARN, text)

    // ---- the overview ---------------------------------------------------------------------

    /**
     * `usage-summary` alone: a line for each level, how many buckets it has, where it starts, and the
     * time a day in it, under a dim header. A tap on a line runs that level's
     * summary; the days run `usage`, which works each day out from events.
     */
    private fun overview(buckets: (Level) -> List<Bucket>) {
        val rows = Level.entries.mapNotNull { level ->
            val all = buckets(level)
            val perDay = UsageLevels.perDay(UsageLevels.counted(all)) ?: return@mapNotNull null
            val run = when (level) {
                Level.DAYS -> "usage ${all.size.coerceIn(1, 30)}"
                Level.WEEKS -> "usage-summary week"
                Level.MONTHS -> "usage-summary month"
                Level.YEARS -> "usage-summary year"
            }
            listOf("${all.size} ${unit(level, all.size)}", date(all.first().start), duration(perDay), run)
        }
        if (rows.isEmpty()) return info("No usage recorded yet")
        // Padded, so the dates and times line up under the header. The log uses a monospaced font, and
        // keeps no-break spaces at a line's start.
        val w0 = rows.maxOf { it[0].length }
        val w1 = maxOf(rows.maxOf { it[1].length }, SINCE.length)
        val w2 = maxOf(rows.maxOf { it[2].length }, A_DAY.length)
        info("${"".padEnd(w0, NBSP)}  ${SINCE.padEnd(w1)}  ${A_DAY.padStart(w2)}", LogContext(LogContext.ALIGNED))
        rows.forEach { (count, from, time, run) ->
            out("${count.padEnd(w0)}  ${from.padEnd(w1)}  ${time.padStart(w2)}", LogContext(LogContext.ALIGNED + mapOf(LogContext.RUN to run)))
        }
    }

    // ---- weeks, months and years ----------------------------------------------------------

    /** Big numbers over [buckets]: the time a day (≈: Android's own totals), unlocks a day, and the total. */
    private fun bigNumbers(buckets: List<Bucket>) {
        val counted = UsageLevels.counted(buckets)
        val big = mutableListOf<Pair<String, String>>()
        UsageLevels.perDay(counted)?.let { big += "≈${duration(it)}" to "a day" }
        UsageLevels.unlocksPerDay(counted)?.let { big += it.toString() to if (it == 1) "unlock a day" else "unlocks a day" }
        big += hours(buckets.sumOf { it.total }) to "total"
        out(StyledText.stats(big))
    }

    /**
     * A bucket's dates: "13 Jun – 13 Jul", "11 Nov – now"; a year by its months: "Apr 2025 – Apr 2026".
     * Android's buckets start where the one before ended, so these are their real dates.
     */
    private fun span(b: Bucket, level: Level): String {
        val show = { ms: Long -> if (level == Level.YEARS) Instant.ofEpochMilli(ms).atZone(zone).format(monthYear) else date(ms) }
        return "${show(b.start)} – ${if (b.current) "now" else show(b.end)}"
    }

    /** A row for each bucket, the oldest first: its dates, its total, and its time a day. */
    private fun table(buckets: List<Bucket>, level: Level) {
        val head = when (level) { Level.WEEKS -> "Week"; Level.MONTHS -> "Month"; Level.YEARS -> "Year"; Level.DAYS -> "Day" }
        val rows = listOf(listOf(head, "Total", "A day")) + buckets.map { listOf(span(it, level), hours(it.total), duration(it.perDay)) }
        out(StyledText.table(rows, header = true, align = "lrr"))
    }

    /**
     * `usage-summary week` and `month`: the big numbers; a row for each of Android's weeks or months
     * with its total and its time a day; the last one that is over, its change against those before;
     * and the top apps over all of them.
     */
    private fun period(buckets: List<Bucket>, level: Level) {
        if (buckets.isEmpty()) return info("No usage recorded yet")
        bigNumbers(buckets)
        table(buckets, level)
        // The last one that is over against those before: its change, and which row it is.
        UsageLevels.change(buckets)?.let { percent ->
            info("${coloured(change(percent), percent <= 0)} · ${span(buckets.last { !it.current }, level)}")
        }
        val byPkg = byPkg()
        val top = UsageLevels.top(buckets, 5).filter { it.second >= 60_000 }
        if (top.isNotEmpty()) {
            rule("top apps")
            val most = top.first().second.toDouble()
            top.forEach { (pkg, ms) -> appLine(pkg, byPkg, hours(ms), ms / most) }
        }
    }

    /**
     * `usage-summary year`: the big numbers; a row for each of Android's years with its total and its
     * time a day; the newest one's change; the top 3 apps of each year; then the apps used less or new
     * since the year before, each with a chip.
     */
    private fun years(buckets: List<Bucket>) {
        if (buckets.isEmpty()) return info("No usage recorded yet")
        bigNumbers(buckets)
        table(buckets, Level.YEARS)
        val counted = UsageLevels.counted(buckets)
        val pair = if (counted.size >= 2) counted.takeLast(2) else null
        if (pair != null) {
            val diff = pair[1].perDay - pair[0].perDay
            when {
                diff <= -60_000 -> info(coloured("▼ ${duration(-diff)} a day", true))
                diff >= 60_000 -> info(coloured("▲ ${duration(diff)} a day", false))
            }
        }
        val byPkg = byPkg()
        // Each year by the month it starts: "Apr 2025".
        val label = { b: Bucket -> Instant.ofEpochMilli(b.start).atZone(zone).format(monthYear) }
        val tops = buckets.map { b -> b to UsageLevels.top(listOf(b), 3).filter { it.second >= 60_000 }.map { name(it.first, byPkg) } }
            .filter { it.second.isNotEmpty() }
        if (tops.isNotEmpty()) {
            rule("top apps")
            tops.forEach { (b, names) ->
                info("${label(b)}  ${names.joinToString(" · ")}", LogContext(LogContext.ALIGNED))
            }
        }
        if (pair != null) {
            val (before, now) = pair
            UsageLevels.leftBehind(before, now).forEach {
                appLine(it.pkg, byPkg, "${duration(it.before)} → ${duration(it.now)}", prefix = StyledText.tag(StyledText.Colour.WARN, "less") + " ")
            }
            UsageLevels.newApps(before, now).forEach {
                appLine(it.pkg, byPkg, duration(it.now), prefix = StyledText.tag(StyledText.Colour.GOOD, "new") + " ")
            }
        }
    }

    companion object {
        /** Commands handled by [run]. */
        val NAMES = setOf("usage-summary")

        /** The overview's header words. */
        private const val SINCE = "since"
        private const val A_DAY = "≈ a day"
        private const val NBSP = '\u00A0'
    }
}
