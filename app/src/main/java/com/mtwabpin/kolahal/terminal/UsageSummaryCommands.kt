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
 * - `week`, `month`: the time a day, unlocks a day and the total; a column for each of Android's
 *   weeks or months; the last one that is over against those before; the top apps.
 * - `year`: a line for each year, the top apps of each, and the apps left behind or new.
 * - `full`: all of these, one after the other.
 * Android's buckets start at its own times, so every date is the real start of one, and the note
 * says the numbers are rough.
 */
class UsageSummaryCommands(context: Context) : CommandGroup(context) {
    private val apps = AppRepository.get(context)
    private val unlocks = UnlockCounter.get(context)
    private val zone get() = ZoneId.systemDefault()

    fun run(args: List<String>) {
        if (!unlocks.hasUsageAccess()) return fix("Usage summary needs usage access — tap to allow it", "usage-access")
        val levels = UsageLevels(context)
        // Each level is read once, even by `full`.
        val read = HashMap<Level, List<Bucket>>()
        val buckets = { level: Level -> read.getOrPut(level) { levels.read(level) } }
        when (args.firstOrNull()?.lowercase()) {
            null -> overview(buckets)
            "week" -> period(buckets(Level.WEEKS), Level.WEEKS)
            "month" -> period(buckets(Level.MONTHS), Level.MONTHS)
            "year" -> years(buckets(Level.YEARS))
            "full" -> {
                overview(buckets)
                for ((level, title) in listOf(Level.WEEKS to "Weeks", Level.MONTHS to "Months", Level.YEARS to "Years")) {
                    out(StyledText.wrap(StyledText.Style.BOLD, title))
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

    /** An app's name: the app list's, or Android's; null for a package with no name of its own (the in-call screen). */
    private fun name(pkg: String, byPkg: Map<String, AppEntry>): String? {
        byPkg[pkg]?.let { return it.label }
        val pm = context.packageManager
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrNull()
        return label?.takeIf { it.isNotBlank() && it != pkg }
    }

    /** The line of an app: its name, and a tap opens it when it is in the app list. */
    private fun appLine(pkg: String, byPkg: Map<String, AppEntry>, desc: String, fraction: Double? = null) {
        val label = name(pkg, byPkg) ?: return
        val ctx = (byPkg[pkg]?.let { appContext(it) } ?: LogContext.EMPTY) + mapOf(LogContext.DESC to desc) +
            (fraction?.let { LogContext.bar(it) } ?: emptyMap())
        out(label, ctx)
    }

    // ---- the overview ---------------------------------------------------------------------

    /**
     * `usage-summary` alone: a line for each level, how many buckets it has, where it starts, and the
     * time a day in it. A tap on a line runs that level's summary; the days run `usage`, which
     * works each day out from events.
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
            listOf("${all.size} ${unit(level, all.size)}", "from ${date(all.first().start)}", duration(perDay), run)
        }
        if (rows.isEmpty()) return info("No usage recorded yet")
        info("What Android keeps, and the screen time a day in each")
        // Padded, so the dates and times line up. The log uses a monospaced font.
        val w0 = rows.maxOf { it[0].length }
        val w1 = rows.maxOf { it[1].length }
        val w2 = rows.maxOf { it[2].length }
        rows.forEach { (count, from, time, run) ->
            out("${count.padEnd(w0)}  ${from.padEnd(w1)}  ${time.padStart(w2)}", LogContext(LogContext.ALIGNED + mapOf(LogContext.RUN to run)))
        }
        info("Tap a line for more. Android's own totals: about right, not exact")
    }

    // ---- weeks and months -----------------------------------------------------------------

    /**
     * `usage-summary week` and `month`: the time a day, unlocks a day and the total; a column for
     * each bucket, the current one standing out; the last one that is over against those before; the
     * top apps over all of them; and a note on where Android starts them.
     */
    private fun period(buckets: List<Bucket>, level: Level) {
        if (buckets.isEmpty()) return info("No usage recorded in Android's ${unit(level, 2)} yet")
        val counted = UsageLevels.counted(buckets)
        val big = mutableListOf<Pair<String, String>>()
        UsageLevels.perDay(counted)?.let { big += duration(it) to "a day on average" }
        UsageLevels.unlocksPerDay(counted)?.let { big += it.toString() to if (it == 1) "unlock a day" else "unlocks a day" }
        big += hours(buckets.sumOf { it.total }) to "in ${buckets.size} ${unit(level, buckets.size)}"
        out(StyledText.stats(big))
        if (buckets.size > 1) {
            val said = "Screen time a day: " + buckets.joinToString(", ") { "from ${date(it.start)} ${duration(it.perDay)}" }
            val last = if (buckets.last().current) "Now" else date(buckets.last().start)
            out(StyledText.columns(buckets.map { it.perDay / 60_000.0 }, listOf(date(buckets.first().start), last), said, newest = buckets.last().current))
        }
        UsageLevels.change(buckets)?.let { percent ->
            val last = buckets.last { !it.current }
            val before = buckets.count { !it.current } - 1
            info("The ${unit(level)} from ${date(last.start)}: ${duration(last.perDay)} a day, ${change(percent)} against the $before ${unit(level, before)} before")
        }
        val byPkg = byPkg()
        val top = UsageLevels.top(buckets, 8).filter { it.second >= 60_000 && name(it.first, byPkg) != null }.take(5)
        if (top.isNotEmpty()) {
            info("Top apps since ${date(buckets.first().start)}")
            val most = top.first().second.toDouble()
            top.forEach { (pkg, ms) -> appLine(pkg, byPkg, hours(ms), ms / most) }
        }
        info(when (level) {
            Level.WEEKS -> "Android's weeks start where the one before ended, not on Monday"
            else -> "Android's months start where the one before ended, not on the 1st"
        })
    }

    // ---- years ----------------------------------------------------------------------------

    /**
     * `usage-summary year`: a line for each of Android's years with its time a day and a bar; the
     * newest against the one before; the top 3 apps of each; and the apps left behind or new.
     */
    private fun years(buckets: List<Bucket>) {
        if (buckets.isEmpty()) return info("No usage recorded in Android's years yet")
        val label = { b: Bucket ->
            val start = Instant.ofEpochMilli(b.start).atZone(zone)
            if (b.current) "${start.format(monthYear)} – now" else "${start.format(monthYear)} – ${Instant.ofEpochMilli(b.end).atZone(zone).format(monthYear)}"
        }
        val most = buckets.maxOf { it.perDay }.coerceAtLeast(1).toDouble()
        info("Screen time a day")
        val width = buckets.maxOf { label(it).length }
        buckets.forEach { b ->
            out(label(b).padEnd(width), LogContext(LogContext.ALIGNED + mapOf(LogContext.DESC to duration(b.perDay)) + LogContext.bar(b.perDay / most)))
        }
        val counted = UsageLevels.counted(buckets)
        if (counted.size >= 2) {
            val (before, now) = counted.takeLast(2)
            val diff = now.perDay - before.perDay
            info(when {
                diff <= -60_000 -> "▼ ${duration(-diff)} a day less than the year before"
                diff >= 60_000 -> "▲ ${duration(diff)} a day more than the year before"
                else -> "About the same a day as the year before"
            })
        }
        val byPkg = byPkg()
        buckets.forEach { b ->
            val top = UsageLevels.top(listOf(b), 8).filter { it.second >= 60_000 }.mapNotNull { name(it.first, byPkg) }.take(3)
            if (top.isNotEmpty()) info("${label(b)}: ${top.joinToString(", ")}")
        }
        if (counted.size >= 2) {
            val (before, now) = counted.takeLast(2)
            val left = UsageLevels.leftBehind(before, now).filter { name(it.pkg, byPkg) != null }
            if (left.isNotEmpty()) {
                info("Left behind")
                left.forEach { appLine(it.pkg, byPkg, "${duration(it.before)} → ${duration(it.now)} a day") }
            }
            val new = UsageLevels.newApps(before, now).filter { name(it.pkg, byPkg) != null }
            if (new.isNotEmpty()) {
                info("New this year")
                new.forEach { appLine(it.pkg, byPkg, "${duration(it.now)} a day") }
            }
        } else info("Android has one year so far: the next one compares the two")
    }

    companion object {
        /** Commands handled by [run]. */
        val NAMES = setOf("usage-summary")
    }
}
