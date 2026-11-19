package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.os.Build
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.unlock.UnlockCounter
import java.util.Date

/**
 * App commands:
 * - listing, opening and launching apps and their shortcuts;
 * - opening any activity of an app (`activity`, in [ActivityCommands]);
 * - pins, hiding and renaming;
 * - work and private profiles;
 * - screen time and install history.
 *
 * [CommandProcessor] also launches apps through this class when the typed text names an app.
 */
class AppCommands(
    context: Context,
    /** Runs slow work (such as waiting for a profile to resume) outside the serial command queue. */
    private val background: (label: String, block: () -> Unit) -> Unit,
) : CommandGroup(context) {
    private val apps = AppRepository.get(context)
    private val prefs = AppPrefs.get(context)
    private val unlocks = UnlockCounter.get(context)
    private val activities by lazy { ActivityCommands(context) }


    /** Describes where a pinned app shows, based on the current settings. */
    private fun pinnedWhere(): String {
        val conf = com.mtwabpin.kolahal.data.Config.get(context)
        return listOfNotNull(
            "it comes first in suggestions on an empty prompt".takeIf { conf[com.mtwabpin.kolahal.data.Keys.SUGGEST_IDLE] },
            "it is a ★ favourite in the app selector".takeIf { conf[com.mtwabpin.kolahal.data.Keys.SELECTOR_RAIL] },
        ).joinToString(", and ").ifEmpty { "pinned apps come first on an empty prompt with config set suggestions.idle on" }
    }

    /** Runs one of [NAMES]. */
    fun run(name: String, args: List<String>) {
        when (name) {
            // Each acts on the best match it can change: `unpin ma` unpins Maps, even when Mail matches better
            // but isn't pinned. When none can be changed, it says so. The suggestions offer the same apps.
            "hide" -> withAppItCanChange(args, can = { !it.hidden }) { app ->
                if (app.hidden) info("${app.label} is hidden already · unhide ${app.label}") else setHidden(app, true)
            }
            "pin", "fav" -> if (args.isEmpty()) listPinned() else prefs.pinned().toSet().let { pinned ->
                withAppItCanChange(args, can = { it.key !in pinned }) { app ->
                    if (app.key in pinned) info("${app.label} is pinned already")
                    else { prefs.setPinned(app.key, true); info("Pinned ${app.label} — ${pinnedWhere()}") }
                }
            }
            "unpin", "unfav" -> prefs.pinned().toSet().let { pinned ->
                withAppItCanChange(args, can = { it.key in pinned }) { app ->
                    if (app.key !in pinned) info("${app.label} isn't pinned · pins lists them")
                    else { prefs.setPinned(app.key, false); info("Unpinned ${app.label}") }
                }
            }
            "pins", "favs" -> listPinned()
            "unhide" -> withAppItCanChange(args, can = { prefs.isHidden(it.key) }) { app ->
                if (!prefs.isHidden(app.key)) info("${app.label} isn't hidden · apps hidden lists them") else setHidden(app, false)
            }
            "rename" -> rename(args)
            "apps", "ls" -> listApps(args)
            "open", "launch" -> open(args)
            "info" -> withApp(args) { apps.openAppInfo(it); info("App info: ${it.label}") }
            "uninstall" -> withApp(args) { apps.uninstall(it) }
            "work" -> profileToggle(ProfileKind.WORK, args)
            "private" -> profileToggle(ProfileKind.PRIVATE, args)
            "usage", "screentime" -> usage(args)
            "installs", "updates" -> installs(args)
            "shortcuts" -> withApp(args) { app -> listShortcuts(app) }
            "activity" -> activities.run(args)
        }
    }

    private fun installs(args: List<String>) {
        val n = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 200) ?: 20
        val list = com.mtwabpin.kolahal.apps.PackageEvents.get(context).history().take(n)
        if (list.isEmpty()) return info("No app installs, updates or uninstalls recorded yet")
        val fmt = java.text.SimpleDateFormat("EEE d MMM HH:mm", java.util.Locale.getDefault())
        // Each event is an item: its app, then the package and the versions under it.
        list.forEach { (t, text) -> out(text, LogContext.of(LogContext.DESC to fmt.format(Date(t))) + LogContext.ITEM) }
    }

    private fun listPinned() {
        val byKey = apps.apps.value.associateBy { it.key }
        val pinned = prefs.pinned().mapNotNull { byKey[it] }
        if (pinned.isEmpty()) return info("No pinned apps — pin <app>, or long-press an app → Pin")
        appGrid(pinned)
        info("${pinned.size} pinned · unpin <app> to remove")
    }

    private fun setHidden(app: AppEntry, hidden: Boolean) {
        prefs.setHidden(app.key, hidden)
        apps.refresh()
        info("${if (hidden) "Hidden" else "Unhidden"} ${appLine(app)}", appCtx(app))
    }

    /** `rename <app> = <new label>`, or `rename <app> =` to restore the original. */
    private fun rename(args: List<String>) {
        val eq = args.indexOf("=")
        if (eq <= 0) return error("Usage: rename <app> [-w] = <new name>")
        withApp(args.subList(0, eq), includeHidden = true) { app ->
            val label = args.drop(eq + 1).joinToString(" ").ifBlank { null }
            prefs.setLabel(app.key, label)
            apps.refresh()
            info("${app.originalLabel} → ${label ?: app.originalLabel}", appCtx(app))
        }
    }

    private fun appCtx(app: AppEntry) = appContext(app)

    /** Returns `Maps [W]`. A personal app gets a tag only when the same app also exists in another profile. */
    private fun appLine(app: AppEntry): String {
        val twin = app.profile.kind == ProfileKind.PERSONAL &&
            apps.apps.value.any { it.pkg == app.pkg && it.profile.serial != app.profile.serial }
        return if (app.profile.kind != ProfileKind.PERSONAL || twin) "${app.label} [${app.profile.kind.tag}]" else app.label
    }

    private fun profileFlag(args: List<String>): ProfileKind? = when {
        "-w" in args || "--work" in args -> ProfileKind.WORK
        "-p" in args || "--personal" in args -> ProfileKind.PERSONAL
        "-s" in args || "--private" in args -> ProfileKind.PRIVATE
        "-c" in args || "--clone" in args -> ProfileKind.CLONE
        else -> null
    }

    private fun stripFlags(args: List<String>) = args.filterNot { it.startsWith("-") }.joinToString(" ")

    private fun listApps(args: List<String>) {
        val kind = profileFlag(args) ?: when (args.firstOrNull()) {
            "work" -> ProfileKind.WORK
            "personal" -> ProfileKind.PERSONAL
            "private" -> ProfileKind.PRIVATE
            else -> null
        }
        val showHidden = "-a" in args || "hidden" in args
        val list = apps.apps.value.filter {
            (kind == null || it.profile.kind == kind) && (if ("hidden" in args) it.hidden else showHidden || !it.hidden)
        }
        if (list.isEmpty()) {
            info("No apps" + (kind?.let { " in ${it.label} profile" } ?: ""))
            return
        }
        val kinds = list.map { it.profile.kind }.distinct().sortedBy { it.ordinal }
        when {
            "-l" in args -> list.forEach { out(appLine(it), appCtx(it)) }
            // Show one grid per profile, so an app and its work copy are not mixed together.
            kinds.size > 1 -> kinds.forEach { k ->
                info("── ${k.label} ──", LogContext(LogContext.ITEM))
                appGrid(list.filter { it.profile.kind == k })
            }
            else -> appGrid(list)
        }
        info("${list.size} ${if (list.size == 1) "app" else "apps"} · " + kinds.joinToString("  ") { "${it.label} [${it.tag}]" })
    }

    private fun resolveApp(args: List<String>, includeHidden: Boolean = false): List<AppEntry> =
        apps.search(stripFlags(args), profileFlag(args), includeHidden)

    /** Runs [action] on the best match for [args] (an app name and profile flags). If nothing matches, prints an error. */
    fun withApp(args: List<String>, includeHidden: Boolean = false, action: (AppEntry) -> Unit) {
        val matches = resolveApp(args, includeHidden)
        when {
            matches.isEmpty() -> error("No app matches '${stripFlags(args)}'")
            else -> action(matches.first())
        }
    }

    /**
     * Runs [action] on the best match for [args] that [can] keeps: an app the command can change. If
     * none can be, on the best match of all (hidden ones too), so the command can say why it does nothing.
     */
    private fun withAppItCanChange(args: List<String>, can: (AppEntry) -> Boolean, action: (AppEntry) -> Unit) {
        val matches = resolveApp(args, includeHidden = true)
        if (matches.isEmpty()) return error("No app matches '${stripFlags(args)}'")
        action(matches.firstOrNull(can) ?: matches.first())
    }

    private fun open(args: List<String>) {
        // `open maps:navigate` starts the shortcut whose label best matches "navigate".
        val joined = stripFlags(args)
        // But "Duolingo: Language Lessons" is an app's name, not a Duolingo shortcut.
        if (joined.contains(':') && apps.search(joined, profileFlag(args)).none { it.label.equals(joined, ignoreCase = true) }) {
            val app = apps.search(joined.substringBefore(':'), profileFlag(args)).firstOrNull()
                ?: return error("No app matches '${joined.substringBefore(':')}'")
            val wanted = joined.substringAfter(':').trim().lowercase()
            val shortcut = apps.shortcuts(app).minByOrNull { s ->
                val label = (s.shortLabel ?: s.longLabel ?: "").toString().lowercase()
                when {
                    label == wanted -> 0
                    label.startsWith(wanted) -> 1
                    label.contains(wanted) -> 2
                    else -> 9
                }
            }?.takeIf { s -> (s.shortLabel ?: "").toString().lowercase().contains(wanted) || (s.longLabel ?: "").toString().lowercase().contains(wanted) }
                ?: return error("${app.label} has no shortcut matching '$wanted' — 'shortcuts ${app.label}'")
            apps.startShortcut(app, shortcut.id)
            return info("${app.label} › ${shortcut.shortLabel}", appCtx(app))
        }
        val matches = resolveApp(args)
        if (matches.isEmpty()) {
            error("No app matches '${stripFlags(args)}'")
            return
        }
        val app = matches.first()
        if ("--resume" in args && app.profile.quiet) resumeAndLaunch(app) else launch(app)
    }

    fun launch(app: AppEntry) {
        if (app.profile.quiet) {
            val flag = if (app.profile.kind == ProfileKind.PRIVATE) "-s" else "-w"
            logs.add(
                LogKind.INFO,
                "${app.profile.kind.title} profile is paused — tap to resume it and open ${app.label}",
                ctx = LogContext.of(LogContext.RUN to "open ${app.label} $flag --resume"),
            )
            return
        }
        runCatching { apps.launch(app) }
            // Launched by a tap (no typed command above it), it is an event of its own in the log.
            .onSuccess {
                val ctx = if (logs.currentCause == null) appCtx(app) + mapOf(LogContext.APART to "1") else appCtx(app)
                logs.addReplacing(LogKind.INFO, "Launched ${appLine(app)}", ctx)
            }
            .onFailure { error("Can't launch ${app.label}: ${it.reason()}") }
    }

    /** Turns the paused profile back on, waits until it is available, then opens [app]. */
    fun resumeAndLaunch(app: AppEntry) {
        if (!apps.setQuietMode(app.profile, false)) {
            error("Couldn't resume the ${app.profile.kind.label} profile (is Kolahal the default launcher? run 'home')")
            return
        }
        info("Resuming ${app.profile.kind.label} profile…")
        background("resume") {
            val deadline = System.currentTimeMillis() + 20_000
            while (System.currentTimeMillis() < deadline) {
                apps.refresh()
                Thread.sleep(500)
                val profile = apps.profileBySerial(app.profile.serial)
                if (profile != null && !profile.quiet) {
                    val fresh = apps.find(app.pkg, app.component.className, app.profile.serial) ?: app
                    launch(fresh)
                    return@background
                }
            }
            error("${app.profile.kind.title} profile didn't resume in time — try again")
        }
    }

    /** Prints one log line shown as tappable app chips. Long-press a chip for its actions. */
    fun appGrid(list: List<AppEntry>) {
        val refs = list.map { mapOf("pkg" to it.pkg, "cls" to it.component.className, "user" to it.profile.serial) }
        out(
            list.joinToString(", ") { it.label },
            LogContext.of(LogContext.APPS to Json.stringify(refs), LogContext.COPY to list.joinToString("\n") { appLine(it) }),
        )
    }

    private fun profileToggle(kind: ProfileKind, args: List<String>) {
        val profile = apps.profiles.value.firstOrNull { it.kind == kind }
        if (profile == null) {
            info("No ${kind.label} profile on this device")
            return
        }
        when (args.firstOrNull()) {
            // On or paused as a tag, as toggles shows on and off.
            null, "status" -> info("${kind.title} profile: " + if (profile.quiet) com.mtwabpin.kolahal.data.StyledText.tag(com.mtwabpin.kolahal.data.StyledText.Colour.MUTED, "paused") else com.mtwabpin.kolahal.data.StyledText.tag(com.mtwabpin.kolahal.data.StyledText.Colour.GOOD, "on"))
            "on", "off" -> {
                val quiet = args[0] == "off"
                if (Build.VERSION.SDK_INT < 29) {
                    error("Toggling profiles needs Android 10+")
                } else if (apps.setQuietMode(profile, quiet)) {
                    info("${kind.title} profile turning ${args[0]}…")
                } else {
                    error("Couldn't change ${kind.label} profile (is Kolahal the default launcher? run 'home')")
                }
            }
            else -> error("Usage: ${kind.name.lowercase()} [on|off|status]")
        }
    }

    private fun listShortcuts(app: AppEntry) {
        val list = apps.shortcuts(app)
        if (list.isEmpty()) {
            return info(
                if (SetupChecks.isDefaultLauncher(context)) "${app.label} has no shortcuts"
                else "Shortcuts need Kolahal as the default launcher — run 'home'"
            )
        }
        list.forEach { s ->
            out(
                "${app.label} › ${s.shortLabel}",
                appCtx(app) + mapOf(LogContext.SHORTCUT to s.id, LogContext.DESC to (s.longLabel ?: s.shortLabel ?: "").toString()),
            )
        }
    }

    /** `usage [days]`: the foreground time per app from UsageStats. `usage <app>`: one app in detail ([appUsage]). Needs Usage Access. */
    private fun usage(args: List<String>) {
        if (!unlocks.hasUsageAccess()) {
            fix("Screen time needs usage access — tap to allow it", "usage-access")
            return
        }
        if (args.isNotEmpty() && args.first().toIntOrNull() == null) {
            // Without a profile flag, the personal app first: Android gives Kolahal only its use.
            val matches = resolveApp(args)
            val app = matches.firstOrNull { it.profile.kind == ProfileKind.PERSONAL } ?: matches.firstOrNull()
                ?: return error("No app matches '${stripFlags(args)}'")
            return appUsage(app)
        }
        val asked = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 30) ?: 1
        val reader = com.mtwabpin.kolahal.usage.AppUsage(context)
        // Only the days Android has data for: a day before its usage history begins is left out
        // ([com.mtwabpin.kolahal.usage.UsageHistory]). The events of the whole range are read once.
        val dataDays = com.mtwabpin.kolahal.usage.UsageHistory.days(context, asked)
        val days = dataDays.size
        val today = java.time.LocalDate.now()
        val start = reader.startOf(dataDays.first())
        val read = reader.read(start)
        // Each app by its name in the app list, the personal app first; a package with no name counts toward "Other apps".
        val byPkg = personalFirst()
        val times = reader.times(read.sessions) { byPkg[it]?.label }
        val sum = times.total
        if (sum < 60_000) return info("No app usage recorded ${if (days == 1) "today" else "in $days days"}")
        // Big numbers: the screen time today; over several days, a day's average (over the finished
        // days: today has only its hours so far) and the total.
        val finished = dataDays.filter { it != today }
        val big = if (days == 1) listOf(duration(sum) to "screen time today")
        else listOf(
            duration(reader.total(read.between(start, reader.startOf(today))) / finished.size.coerceAtLeast(1)) to "a day on average",
            duration(sum) to "in $days days",
        )
        out(com.mtwabpin.kolahal.data.StyledText.stats(big))
        if (days < asked) info("$days ${if (days == 1) "day" else "days"} with data: Android keeps usage for the last days only")
        val named = times.apps.map { Triple(it.label, byPkg[it.pkg], it.ms) }
        val shown = named.take(12)
        val other = sum - shown.sumOf { it.third }
        val max = maxOf(shown.firstOrNull()?.third ?: 0L, other)
        shown.forEach { (label, app, ms) ->
            out(
                label,
                // A bar on one scale before the time: the most-used app fills its bar.
                (if (app != null) appCtx(app) else LogContext.EMPTY) + mapOf(LogContext.DESC to duration(ms)) + LogContext.bar(ms.toDouble() / max),
            )
        }
        // The rest, so the times add up to the total: apps past the first 12, and those without a name or under a minute.
        if (other >= 60_000) info("Other apps", LogContext.of(LogContext.DESC to duration(other)) + LogContext.bar(other.toDouble() / max))
    }

    /** The app list by package, the personal app where several profiles have it: a tap on its line opens that one. */
    private fun personalFirst(): Map<String, AppEntry> =
        apps.apps.value.groupBy { it.pkg }.mapValues { (_, list) -> list.firstOrNull { it.profile.kind == ProfileKind.PERSONAL } ?: list.first() }

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
            return info("Screen time of ${app.profile.kind.label} apps isn't available: Android gives Kolahal only the personal profile's")
        }
        val reader = com.mtwabpin.kolahal.usage.AppUsage(context)
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val dataDays = com.mtwabpin.kolahal.usage.UsageHistory.days(context, 7, today)
        val read = reader.read(reader.startOf(dataDays.first()))
        fun day(d: java.time.LocalDate) = read.between(reader.startOf(d), reader.startOf(d.plusDays(1)))
        val days = dataDays.map { d -> d to (com.mtwabpin.kolahal.usage.AppSessions.totals(day(d))[app.pkg] ?: 0L) }
        val s = com.mtwabpin.kolahal.usage.AppSessions.of(day(today), app.pkg, read.inFront, zone)
        out(com.mtwabpin.kolahal.data.StyledText.stats(listOf(
            duration(s.total) to "${app.label} today",
            s.opens.toString() to if (s.opens == 1) "open" else "opens",
            duration(s.longest) to "longest",
        )), appCtx(app))
        val clock = com.mtwabpin.kolahal.data.TimeFormats.clockFormat(com.mtwabpin.kolahal.data.Config.get(context)[com.mtwabpin.kolahal.data.Keys.CLOCK_24H])
        fun at(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(zone).format(clock)
        when {
            s.inFront -> info("In use now")
            s.lastUsed != null -> info("Last used ${at(s.lastUsed)}" + (s.longestAt?.let { " · longest at ${at(it)}" } ?: ""))
            else -> info("Not used today")
        }
        // A column for each hour: today's, or, when it wasn't used today, the last day it was used of these.
        val chartDay = if (s.total > 0) today else days.lastOrNull { it.second > 0 }?.first
        if (chartDay != null) {
            val hours = if (chartDay == today) s.hours else com.mtwabpin.kolahal.usage.AppSessions.of(day(chartDay), app.pkg, null, zone).hours
            // Today has the hours so far, in a chart of the whole day: the hours still to come keep
            // their places, and the hour now stands out. A past day has all 24, none standing out.
            val upTo = if (chartDay == today) java.time.LocalTime.now().hour + 1 else 24
            val minutes = hours.take(upTo).map { it / 60_000.0 }
            val dayName = com.mtwabpin.kolahal.data.TimeFormats.dayLabel(chartDay, today, capital = false)
            if (chartDay != today) info("By hour, $dayName")
            val busy = minutes.withIndex().filter { it.value >= 1 }
            val said = "${app.label} by hour, $dayName: " +
                if (busy.isEmpty()) "under a minute in every hour" else busy.joinToString(", ") { "%02d:00 %dm".format(it.index, it.value.toInt()) }
            out(com.mtwabpin.kolahal.data.StyledText.columns(minutes, listOf("00", "06", "12", "18", "24"), said, slots = 24, newest = chartDay == today))
        }
        if (days.size <= 1) return
        val labels = days.map { (d, _) -> com.mtwabpin.kolahal.data.TimeFormats.dayLabel(d, today, capital = true) }
        val width = labels.maxOf { it.length }
        // The times padded to one width, so every bar starts in the same place.
        val timeWidth = days.maxOf { duration(it.second).length }
        val most = days.maxOf { it.second }.coerceAtLeast(1L)
        days.forEachIndexed { i, (_, ms) ->
            out("${labels[i].padEnd(width)}  ${duration(ms).padStart(timeWidth)}", LogContext(LogContext.ALIGNED + LogContext.bar(ms.toDouble() / most)))
        }
        // The average of the finished days: today has only its hours so far.
        val finished = days.filter { it.first != today }
        if (finished.isNotEmpty()) info("${duration(finished.sumOf { it.second } / finished.size)} a day on average, over the ${finished.size} ${if (finished.size == 1) "day" else "days"} before today")
    }

    /** The profile's name with a capital first letter, for the start of a sentence. */
    private val ProfileKind.title get() = label.replaceFirstChar { it.uppercase() }

    private fun duration(ms: Long): String {
        val minutes = ms / 60_000
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }

    companion object {
        /** Commands handled by [run]. */
        val NAMES = setOf(
            "hide", "pin", "fav", "unpin", "unfav", "pins", "favs", "unhide", "rename", "apps", "ls", "open", "launch",
            "info", "uninstall", "work", "private", "usage", "screentime", "installs", "updates", "shortcuts",
            "activity",
        )
    }
}
