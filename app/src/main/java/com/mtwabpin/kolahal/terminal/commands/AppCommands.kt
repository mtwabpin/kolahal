/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import android.os.Build
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
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
        val list = apps.apps.value.filter {
            (kind == null || it.profile.kind == kind) && (if ("hidden" in args) it.hidden else showHidden || !it.hidden)
        }
        if (list.isEmpty()) {
            info("No apps" + (kind?.let { " in ${it.label} profile" } ?: ""))
            return
        }
        val kinds = list.map { it.profile.kind }.distinct().sortedBy { it.ordinal }
        when {
            "-l" in args -> list.forEach { out(appLine(it), appContext(it)) }
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
        apps.search(CommandParsing.withoutFlags(args), ProfileKind.fromFlags(args), includeHidden)

    /** Runs [action] on the best match for [args] (an app name and profile flags). If nothing matches, prints an error. */
    fun withApp(args: List<String>, includeHidden: Boolean = false, action: (AppEntry) -> Unit) {
        val matches = resolveApp(args, includeHidden)
        when {
            matches.isEmpty() -> error("No app matches '${CommandParsing.withoutFlags(args)}'")
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
        if (matches.isEmpty()) return error("No app matches '${CommandParsing.withoutFlags(args)}'")
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
            error("No app matches '${CommandParsing.withoutFlags(args)}'")
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
                "${app.profile.kind.title} profile is paused\nTap to resume it and open ${app.label}",
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
    fun resumeAndLaunch(app: AppEntry) {
        val result = apps.setQuietMode(app.profile, false)
        if (result == com.mtwabpin.kolahal.apps.ProfileQuiet.Result.FAILED) {
            error("Couldn't resume the ${app.profile.kind.label} profile\n$NOT_HOME", copying("home"))
            return
        }
        // With a PIN, Android asks for it first: there is more time to type it.
        val pin = result == com.mtwabpin.kolahal.apps.ProfileQuiet.Result.NEEDS_PIN
        info(if (pin) "Enter the ${app.profile.kind.label} profile's PIN to resume it…" else "Resuming ${app.profile.kind.label} profile…")
        background("resume") {
            val deadline = System.currentTimeMillis() + if (pin) 60_000 else 20_000
            while (System.currentTimeMillis() < deadline) {
                apps.refresh()
                Thread.sleep(500)
                val profile = apps.profileBySerial(app.profile.serial)
                if (profile != null && !profile.quiet) {
                    val fresh =
                        apps.find(app.pkg, app.component.className, app.profile.serial) ?: app
                    launch(fresh)
                    return@background
                }
            }
            error("${app.profile.kind.title} profile didn't resume in time\nTry again")
        }
    }

    /** Prints one log line shown as tappable app chips. Long-press a chip for its actions. */
    fun appGrid(list: List<AppEntry>) {
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
                LogContext.COPY to list.joinToString("\n") { appLine(it) }),
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
            null, "status" -> info(
                "${kind.title} profile: " + if (profile.quiet) com.mtwabpin.kolahal.data.StyledText.tag(
                    com.mtwabpin.kolahal.data.StyledText.ColorRole.MUTED,
                    "paused"
                ) else com.mtwabpin.kolahal.data.StyledText.tag(
                    com.mtwabpin.kolahal.data.StyledText.ColorRole.GOOD,
                    "on"
                )
            )

            "on", "off" -> {
                val quiet = args[0] == "off"
                if (Build.VERSION.SDK_INT < 29) {
                    error("Toggling profiles needs Android 10+")
                } else when (apps.setQuietMode(profile, quiet)) {
                    com.mtwabpin.kolahal.apps.ProfileQuiet.Result.DONE -> info("${kind.title} profile turning ${args[0]}…")
                    com.mtwabpin.kolahal.apps.ProfileQuiet.Result.NEEDS_PIN -> info("Enter the ${kind.label} profile's PIN to turn it on")
                    com.mtwabpin.kolahal.apps.ProfileQuiet.Result.FAILED -> error("Couldn't change ${kind.label} profile\n$NOT_HOME", copying("home"))
                }
            }

            else -> error("Usage: ${kind.name.lowercase()} [on|off|status]")
        }
    }

    private fun listShortcuts(app: AppEntry) {
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

    /** The profile's name with a capital first letter, for the start of a sentence. */
    private val ProfileKind.title get() = label.replaceFirstChar { it.uppercase() }

    companion object {
        /** Where pinned apps show when no setting shows them. */
        private const val PINNED_NOWHERE =
            "Pinned apps come first on an empty prompt with 'config set suggestions.idle on'"

        /** Why Android may refuse to pause or resume a profile, and the command that fixes it. */
        private const val NOT_HOME = "Is Kolahal the default launcher?\n'home' chooses the default launcher"

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
