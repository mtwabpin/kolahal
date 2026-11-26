/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import android.os.Build
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.PrivateSpace
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.apps.ProfileWords
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.terminal.SetupChecks
import java.util.Date

/**
 * App commands:
 * - listing, opening and launching apps and their shortcuts;
 * - opening any activity of an app (`activity`, in [ActivityCommands]);
 * - pins, hiding and renaming;
 * - the work profile and the private space;
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
    private val activities by lazy { ActivityCommands(context) }


    /** Lines that say where a pinned app shows, based on the current settings. Empty when it shows nowhere. */
    private fun pinnedWhere(): List<String> {
        val conf = com.mtwabpin.kolahal.data.Config.get(context)
        return listOfNotNull(
            "It comes first in suggestions on an empty prompt".takeIf { conf[com.mtwabpin.kolahal.data.Keys.SUGGEST_IDLE] },
            "It is a ★ favorite in the app selector".takeIf { conf[com.mtwabpin.kolahal.data.Keys.SELECTOR_RAIL] },
        )
    }

    /** How a command names [app], as the user would type it: `maps`, `maps -w`. The flag as [appLine] tags it. */
    private fun appArg(app: AppEntry): String =
        app.label.lowercase() + if (appLine(app) != app.label) " " + app.profile.kind.flag else ""

    /** A line's context whose Copy gives [command]. */
    private fun copying(command: String) = LogContext.of(LogContext.COPY to command)

    /** Runs one of [NAMES]. */
    fun run(name: String, args: List<String>) {
        when (name) {
            // Each acts on the best match it can change: `unpin ma` unpins Maps, even when Mail matches better
            // but isn't pinned. When none can be changed, it says so. The suggestions offer the same apps.
            "hide" -> withAppItCanChange(args, can = { !it.hidden }) { app ->
                if (app.hidden) info(
                    "${app.label} is hidden already\n'unhide ${appArg(app)}' shows it again",
                    copying("unhide ${appArg(app)}")
                ) else setHidden(
                    app,
                    true
                )
            }

            "pin", "fav" -> if (args.isEmpty()) listPinned() else prefs.pinned().toSet()
                .let { pinned ->
                    withAppItCanChange(args, can = { it.key !in pinned }) { app ->
                        if (app.key in pinned) info("${app.label} is pinned already")
                        else {
                            prefs.setPinned(
                                app.key,
                                true
                            )
                            // Shown nowhere, the line names the setting's command too: then Copy keeps the line's words.
                            val where = pinnedWhere()
                            val unpin = "unpin ${appArg(app)}"
                            info(
                                (listOf("Pinned ${app.label}") + where.ifEmpty { listOf(PINNED_NOWHERE) } + "'$unpin' removes it")
                                    .joinToString("\n"),
                                if (where.isEmpty()) LogContext.EMPTY else copying(unpin)
                            )
                        }
                    }
                }

            "unpin", "unfav" -> prefs.pinned().toSet().let { pinned ->
                withAppItCanChange(args, can = { it.key in pinned }) { app ->
                    if (app.key !in pinned) info("${app.label} isn't pinned\n'pins' lists the pinned apps", copying("pins"))
                    else {
                        prefs.setPinned(app.key, false); info("Unpinned ${app.label}")
                    }
                }
            }

            "pins", "favs" -> listPinned()
            "unhide" -> withAppItCanChange(args, can = { prefs.isHidden(it.key) }) { app ->
                if (!prefs.isHidden(app.key)) info(
                    "${app.label} isn't hidden\n'apps hidden' lists the hidden apps",
                    copying("apps hidden")
                ) else setHidden(
                    app,
                    false
                )
            }

            "rename" -> rename(args)
            "apps", "ls" -> listApps(args)
            "open", "launch" -> open(args)
            "info" -> withApp(args) { apps.openAppInfo(it); info("App info: ${it.label}") }
            "uninstall" -> withApp(args) { apps.uninstall(it) }
            "work" -> profileToggle(ProfileKind.WORK, args)
            "private" -> profileToggle(ProfileKind.PRIVATE, args)
            "installs", "updates" -> installs(args)
            "shortcuts" -> withApp(args) { app -> listShortcuts(app) }
            "activity" -> activities.run(args)
        }
    }

    private fun installs(args: List<String>) {
        val n = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 200) ?: 20
        val list = com.mtwabpin.kolahal.apps.PackageEvents.get(context).history().take(n)
        if (list.isEmpty()) return info("No app installs, updates or uninstalls recorded yet")
        val clock24 =
            com.mtwabpin.kolahal.data.Config.get(context)[com.mtwabpin.kolahal.data.Keys.CLOCK_24H]
        val fmt = java.text.SimpleDateFormat(
            "${com.mtwabpin.kolahal.data.TimeFormats.DAY} ${
                com.mtwabpin.kolahal.data.TimeFormats.clock(clock24)
            }", java.util.Locale.getDefault()
        )
        // Each event is an item: its app, then the package and the versions under it.
        list.forEach { (t, text) ->
            out(
                text,
                LogContext.of(LogContext.DESC to fmt.format(Date(t))) + LogContext.ITEM
            )
        }
    }

    private fun listPinned() {
        val byKey = apps.apps.value.associateBy { it.key }
        val pinned = prefs.pinned().mapNotNull { byKey[it] }
        if (pinned.isEmpty()) return info("No pinned apps\n'pin <app>' pins one\nOr long-press an app → Pin")
        appGrid(pinned)
        info("${pinned.size} pinned\n'unpin <app>' removes one")
    }

    private fun setHidden(app: AppEntry, hidden: Boolean) {
        prefs.setHidden(app.key, hidden)
        apps.refresh()
        info("${if (hidden) "Hidden" else "Unhidden"} ${appLine(app)}", appContext(app))
    }

    /** `rename <app> = <new label>`, or `rename <app> =` to restore the original. */
    private fun rename(args: List<String>) {
        val eq = args.indexOf("=")
        if (eq <= 0) return error("Usage: rename <app> [-w] = <new name>")
        withApp(args.subList(0, eq), includeHidden = true) { app ->
            val label = args.drop(eq + 1).joinToString(" ").ifBlank { null }
            prefs.setLabel(app.key, label)
            apps.refresh()
            info("${app.originalLabel} → ${label ?: app.originalLabel}", appContext(app))
        }
    }

    /** Returns `Maps [W]`. A personal app gets a tag only when the same app also exists in another profile. */
    private fun appLine(app: AppEntry): String {
        val twin = app.profile.kind == ProfileKind.PERSONAL &&
                apps.apps.value.any { it.pkg == app.pkg && it.profile.serial != app.profile.serial }
        return if (app.profile.kind != ProfileKind.PERSONAL || twin) "${app.label} [${app.profile.kind.tag}]" else app.label
    }


    private fun listApps(args: List<String>) {
        val kind = ProfileKind.fromFlags(args) ?: when (args.firstOrNull()) {
            "work" -> ProfileKind.WORK
            "personal" -> ProfileKind.PERSONAL
            "private" -> ProfileKind.PRIVATE
            else -> null
        }
        val showHidden = "-a" in args || "hidden" in args
        // A locked private space lists none of its apps (PrivateSpace): asked for alone, it says it is
        // locked, or that Kolahal can't see it while it isn't the home app.
        if (kind != null) apps.profiles.value.firstOrNull { it.kind == kind }.let { profile ->
            if (profile == null && kind == ProfileKind.PRIVATE) return noProfile(kind)
            if (profile != null && PrivateSpace.locked(profile)) return offLine(profile)
        }
        val list = apps.apps.value.filter {
            (kind == null || it.profile.kind == kind) && (if ("hidden" in args) it.hidden else showHidden || !it.hidden)
        }
        // In the place of its heading and apps, one line: it is locked, and a tap unlocks it. None
        // while Android hides the space when it is locked.
        val locked = apps.profiles.value.firstOrNull { PrivateSpace.locked(it) && PrivateSpace.shows(it) }
            ?.takeIf { kind == null && "hidden" !in args }
        if (list.isEmpty() && locked == null) {
            info(kind?.let(ProfileMessages::noApps) ?: "No apps")
            return
        }
        val kinds = (list.map { it.profile.kind } + listOfNotNull(locked?.kind)).distinct().sortedBy { it.ordinal }
        // One profile: a line counts its apps. Several: each has a divider with its count, so an
        // app and its work copy are not mixed together. The private space's lines are marked with
        // it, so they hide while it is locked.
        if (kinds.size == 1 && list.isNotEmpty()) info(AppListing.count(list.size, kind, hidden = "hidden" in args), spaceOf(list))
        // A grid shows the pinned and most-used apps first, then "+N more" ([AppListing.firstShown]).
        val cells = AppListing.rows(kinds.size) * AppListing.COLUMNS
        val pins = prefs.pinned().withIndex().associate { (i, key) -> key to i }
        val now = System.currentTimeMillis()
        for (k in kinds) {
            if (k == locked?.kind) {
                offLine(locked)
                continue
            }
            val group = list.filter { it.profile.kind == k }
            if (kinds.size > 1) info(AppListing.divider(k, group.size), LogContext(LogContext.ITEM) + spaceOf(group).values)
            if ("-l" in args) group.forEach { out(appLine(it), appContext(it)) }
            else appGrid(group, AppListing.firstShown(group, cells, { pins[it.key] }, { prefs.frecency(it.key, now) }))
        }
    }

    /** A line's context that marks it as the private space's when all of [list] is in one, so it hides while that is locked. */
    private fun spaceOf(list: List<AppEntry>): LogContext =
        list.map { it.profile }.distinct().singleOrNull()?.takeIf { it.kind == ProfileKind.PRIVATE }
            ?.let { LogContext.of(LogContext.USER to it.serial.toString()) } ?: LogContext.EMPTY

    /** [profile] is paused or locked: the line says so, and its tap turns it on (`private on`). */
    private fun offLine(profile: com.mtwabpin.kolahal.apps.Profile) =
        info(ProfileMessages.off(profile.kind), LogContext.of(LogContext.RUN to ProfileMessages.onCommand(profile.kind)))

    /** No profile of [kind]: perhaps because Android hides the private space from Kolahal, which isn't the home app. */
    private fun noProfile(kind: ProfileKind) {
        val hidden = kind == ProfileKind.PRIVATE && apps.privateHidden()
        info(ProfileMessages.none(kind, hidden), if (hidden) copying("home") else LogContext.EMPTY)
    }

    private fun resolveApp(args: List<String>, includeHidden: Boolean = false): List<AppEntry> =
        apps.search(CommandParsing.withoutFlags(args), ProfileKind.fromFlags(args), includeHidden)

    /**
     * No app matches [name]. Asked for in the private space ([kind]), that may be why: it is locked
     * (its apps aren't searched), or hidden from Kolahal. Otherwise, as for any name: no app matches.
     */
    private fun noMatch(name: String, kind: ProfileKind?) {
        if (kind == ProfileKind.PRIVATE) {
            val space = apps.profiles.value.firstOrNull { it.kind == ProfileKind.PRIVATE }
            if (space == null && apps.privateHidden()) return noProfile(ProfileKind.PRIVATE)
            if (space != null && PrivateSpace.locked(space)) return offLine(space)
        }
        error("No app matches '$name'")
    }

    private fun noMatch(args: List<String>) = noMatch(CommandParsing.withoutFlags(args), ProfileKind.fromFlags(args))

    /** Runs [action] on the best match for [args] (an app name and profile flags). If nothing matches, prints an error. */
    fun withApp(args: List<String>, includeHidden: Boolean = false, action: (AppEntry) -> Unit) {
        val matches = resolveApp(args, includeHidden)
        when {
            matches.isEmpty() -> noMatch(args)
            else -> action(matches.first())
        }
    }

    /**
     * Runs [action] on the best match for [args] that [can] keeps: an app the command can change. If
     * none can be, on the best match of all (hidden ones too), so the command can say why it does nothing.
     */
    private fun withAppItCanChange(
        args: List<String>,
        can: (AppEntry) -> Boolean,
        action: (AppEntry) -> Unit
    ) {
        val matches = resolveApp(args, includeHidden = true)
        if (matches.isEmpty()) return noMatch(args)
        action(matches.firstOrNull(can) ?: matches.first())
    }

    private fun open(args: List<String>) {
        // `open maps:navigate` starts the shortcut whose label best matches "navigate".
        val joined = CommandParsing.withoutFlags(args)
        // But "Duolingo: Language Lessons" is an app's name, not a Duolingo shortcut.
        if (joined.contains(':') && apps.search(joined, ProfileKind.fromFlags(args))
                .none { it.label.equals(joined, ignoreCase = true) }
        ) {
            val app =
                apps.search(joined.substringBefore(':'), ProfileKind.fromFlags(args)).firstOrNull()
                    ?: return noMatch(joined.substringBefore(':'), ProfileKind.fromFlags(args))
            // A paused work profile's app has its shortcuts, but Android gives none while it is off.
            if (app.profile.quiet) return offLine(app.profile)
            val wanted = joined.substringAfter(':').trim().lowercase()
            val shortcut = apps.shortcuts(app).minByOrNull { s ->
                val label = (s.shortLabel ?: s.longLabel ?: "").toString().lowercase()
                when {
                    label == wanted -> 0
                    label.startsWith(wanted) -> 1
                    label.contains(wanted) -> 2
                    else -> 9
                }
            }?.takeIf { s ->
                (s.shortLabel ?: "").toString().lowercase().contains(wanted) || (s.longLabel
                    ?: "").toString().lowercase().contains(wanted)
            }
                ?: return error(
                    "${app.label} has no shortcut matching '$wanted'\n'shortcuts ${appArg(app)}' lists them",
                    copying("shortcuts ${appArg(app)}")
                )
            apps.startShortcut(app, shortcut.id)
            return info("${app.label} › ${shortcut.shortLabel}", appContext(app))
        }
        val matches = resolveApp(args)
        if (matches.isEmpty()) {
            // `open maps -s` while the private space is locked: its apps aren't searched, so the line
            // offers to unlock it first, then to open what was asked (an alias for a private app).
            val space = apps.profiles.value.firstOrNull { it.kind == ProfileKind.PRIVATE }
                ?.takeIf { PrivateSpace.locked(it) && ProfileKind.fromFlags(args) == ProfileKind.PRIVATE }
            when {
                space == null -> noMatch(args)
                "--resume" in args -> resumeThen(space) {
                    resolveApp(args).firstOrNull()?.let(::launch) ?: noMatch(args)
                }

                else -> info(
                    ProfileMessages.offOpen(space.kind, joined),
                    LogContext.of(LogContext.RUN to "open $joined ${space.kind.flag} --resume"),
                )
            }
            return
        }
        val app = matches.first()
        if ("--resume" in args && app.profile.quiet) resumeAndLaunch(app) else launch(app)
    }

    fun launch(app: AppEntry) {
        if (app.profile.quiet) {
            // The profile's own flag: a paused clone profile is not the work profile.
            val flag = app.profile.kind.flag
            logs.add(
                LogKind.INFO,
                ProfileMessages.offOpen(app.profile.kind, app.label),
                ctx = LogContext.of(LogContext.RUN to "open ${app.label} $flag --resume"),
            )
            return
        }
        runCatching { apps.launch(app) }
            // Launched by a tap (no typed command above it), it is an event of its own in the log.
            .onSuccess {
                val ctx =
                    if (logs.currentCause == null) appContext(app) + mapOf(LogContext.APART to "1") else appContext(
                        app
                    )
                logs.addReplacing(LogKind.INFO, "Launched ${appLine(app)}", ctx)
            }
            .onFailure { error("Can't launch ${app.label}\n${it.reason()}") }
    }

    /** Turns the paused profile back on, waits until it is available, then opens [app]. */
    fun resumeAndLaunch(app: AppEntry) = resumeThen(app.profile) {
        launch(apps.find(app.pkg, app.component.className, app.profile.serial) ?: app)
    }

    /** Turns [profile] back on (resumes or unlocks it), waits until it is available, then runs [then]. */
    private fun resumeThen(profile: com.mtwabpin.kolahal.apps.Profile, then: () -> Unit) {
        val kind = profile.kind
        val result = apps.setQuietMode(profile, false)
        if (result == com.mtwabpin.kolahal.apps.ProfileQuiet.Result.FAILED) {
            error(ProfileMessages.refused(kind, on = true), copying("home"))
            return
        }
        // With a PIN, Android asks for it first: there is more time to type it.
        val pin = result == com.mtwabpin.kolahal.apps.ProfileQuiet.Result.NEEDS_PIN
        info(if (pin) ProfileMessages.needsLock(kind) else ProfileMessages.changing(kind, on = true))
        background("resume") {
            val deadline = System.currentTimeMillis() + if (pin) 60_000 else 20_000
            while (System.currentTimeMillis() < deadline) {
                apps.refresh()
                Thread.sleep(500)
                val now = apps.profileBySerial(profile.serial)
                if (now != null && !now.quiet) {
                    then()
                    return@background
                }
            }
            error(ProfileMessages.late(kind))
        }
    }

    /**
     * Prints one log line shown as tappable app chips. Long-press a chip for its actions. [first]:
     * the places in [list] of the apps it shows until "+N more" opens it; null shows them all. A
     * grid of the private space's apps alone hides while it is locked.
     */
    fun appGrid(list: List<AppEntry>, first: List<Int>? = null) {
        val refs = list.map {
            mapOf(
                "pkg" to it.pkg,
                "cls" to it.component.className,
                "user" to it.profile.serial
            )
        }
        out(
            list.joinToString(", ") { it.label },
            LogContext.of(
                LogContext.APPS to Json.stringify(refs),
                LogContext.COPY to list.joinToString("\n") { appLine(it) }) +
                    (first?.let { mapOf(LogContext.APPS_FIRST to it.joinToString(",")) } ?: emptyMap()) +
                    spaceOf(list).values,
        )
    }

    private fun profileToggle(kind: ProfileKind, args: List<String>) {
        val profile = apps.profiles.value.firstOrNull { it.kind == kind } ?: return noProfile(kind)
        when (args.firstOrNull()) {
            // Locked or unlocked (paused or on) as a tag, as toggles shows on and off.
            null, "status" -> info(
                ProfileMessages.statusName(kind) + if (profile.quiet) com.mtwabpin.kolahal.data.StyledText.tag(
                    com.mtwabpin.kolahal.data.StyledText.ColorRole.MUTED,
                    ProfileWords.off(kind)
                ) else com.mtwabpin.kolahal.data.StyledText.tag(
                    com.mtwabpin.kolahal.data.StyledText.ColorRole.GOOD,
                    ProfileWords.on(kind)
                )
            )

            "on", "off" -> {
                val on = args[0] == "on"
                if (Build.VERSION.SDK_INT < 29) {
                    error("Toggling profiles needs Android 10+")
                } else when (apps.setQuietMode(profile, !on)) {
                    com.mtwabpin.kolahal.apps.ProfileQuiet.Result.DONE -> info(ProfileMessages.changing(kind, on))
                    com.mtwabpin.kolahal.apps.ProfileQuiet.Result.NEEDS_PIN -> info(ProfileMessages.needsLock(kind))
                    com.mtwabpin.kolahal.apps.ProfileQuiet.Result.FAILED -> error(ProfileMessages.refused(kind, on), copying("home"))
                }
            }

            else -> error("Usage: ${ProfileMessages.command(kind)} [on|off|status]")
        }
    }

    private fun listShortcuts(app: AppEntry) {
        // Android gives no shortcuts of a paused or locked profile's app: say so, not "no shortcuts".
        if (app.profile.quiet) return offLine(app.profile)
        val list = apps.shortcuts(app)
        if (list.isEmpty()) {
            return if (SetupChecks.isDefaultLauncher(context)) info("${app.label} has no shortcuts")
            else info("Shortcuts need Kolahal as the home app\n'home' chooses the default launcher", copying("home"))
        }
        list.forEach { s ->
            out(
                "${app.label} › ${s.shortLabel}",
                appContext(app) + mapOf(
                    LogContext.SHORTCUT to s.id,
                    LogContext.DESC to (s.longLabel ?: s.shortLabel ?: "").toString()
                ),
            )
        }
    }

    companion object {
        /** Where pinned apps show when no setting shows them. */
        private const val PINNED_NOWHERE =
            "Pinned apps come first on an empty prompt with 'config set suggestions.idle on'"

        /** Commands handled by [run]. */
        val NAMES = setOf(
            "hide",
            "pin",
            "fav",
            "unpin",
            "unfav",
            "pins",
            "favs",
            "unhide",
            "rename",
            "apps",
            "ls",
            "open",
            "launch",
            "info",
            "uninstall",
            "work",
            "private",
            "installs",
            "updates",
            "shortcuts",
            "activity",
        )
    }
}
