/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.notify.Notifier
import com.mtwabpin.kolahal.terminal.MonthCalendar
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.SetupChecks
import com.mtwabpin.kolahal.terminal.TerminalUi
import com.mtwabpin.kolahal.terminal.WorldClock
import com.mtwabpin.kolahal.widgets.listing
import java.text.DateFormat
import java.util.Date

/**
 * Commands about the phone and home: `date` and `cal`, `widgets`, `notifications`, `wallpaper`,
 * `screen`, `scramble`, `fetch`, `backup`, `restart` and `setup text`.
 */
class SystemCommands(
    context: Context,
    private val ui: TerminalUi,
    /** Runs the backup outside the command's thread. */
    private val background: (label: String, block: () -> Unit) -> Unit,
) : CommandGroup(context) {
    private val conf = Config.get(context)

    /** `cal [dec [2027]]`: prints the month as a calendar, with today in bold. Weeks start on the locale's first day. */
    fun cal(rest: String) {
        val today = java.time.LocalDate.now()
        val month = MonthCalendar.parse(rest, today)
            ?: return error("Unknown month '$rest'. Use cal, cal dec, cal dec 2027 or cal 2027-12")
        val lines = MonthCalendar.lines(
            month,
            today
        ) {
            StyledText.wrap(
                StyledText.Style.BOLD,
                it
            )
        }
        // One log line per row, in the terminal font, so the columns line up.
        lines.forEach { out(it, LogContext(LogContext.ALIGNED)) }
    }

    /** `date`: the time large, with today's date under it. Copy gives the date and time in full. */
    fun now() {
        val now = java.time.ZonedDateTime.now()
        val clock24 = Config.get(context)[Keys.CLOCK_24H]
        val time =
            now.format(java.time.format.DateTimeFormatter.ofPattern(TimeFormats.clock(clock24)))
        val day = now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))
        out(
            StyledText.stats(listOf(time to day)),
            LogContext.of(LogContext.COPY to DateFormat.getDateTimeInstance().format(Date()))
        )
    }

    /**
     * `date tokyo new york`: prints the time in each place, one line each, and how far ahead or behind
     * it is. One place shows its time large, with the place under it.
     */
    fun date(words: List<String>) {
        val places = WorldClock.parse(words)
            ?: return error("Unknown place '${words.joinToString(" ")}'. Use a city (tokyo, new york), a zone (Asia/Tokyo), utc, or an offset like +5:30")
        val now = java.time.Instant.now()
        val clock24 = Config.get(context)[Keys.CLOCK_24H]
        if (places.size == 1) {
            val line = WorldClock.line(places[0], now, java.time.ZoneId.systemDefault(), clock24)
            val under = listOf(line.place, line.day, line.relative).filter { it.isNotEmpty() }
                .joinToString(" · ")
            return out(
                StyledText.stats(listOf(line.time to under)),
                LogContext.of(LogContext.COPY to "${line.place} ${line.detail}")
            )
        }
        for (place in places) {
            val line = WorldClock.line(place, now, java.time.ZoneId.systemDefault(), clock24)
            out(line.place, LogContext.of(LogContext.DESC to line.detail))
        }
    }

    // ---- widgets ----------------------------------------------------------------------------

    /**
     * - `widgets` alone opens Arrange widgets.
     * - `widgets page` turns home to the widgets page beside the log.
     * - `widgets ls` prints the placed widgets. A tap opens Arrange widgets.
     * - `widget add` opens the picker.
     */
    fun widgets(args: List<String>) {
        val host = com.mtwabpin.kolahal.widgets.WidgetHost.get(context)
        when (args.firstOrNull()) {
            null -> ui.openScreen(Screen.WIDGETS)
            "page" -> if (conf[Keys.WIDGETS_PAGE] == "off") fix(
                "The widgets page is off — tap to turn it on",
                "config set widgets.page left"
            ) else ui.showWidgetsPage()

            "ls", "list" -> {
                val lines = host.listing(context)
                if (lines.isEmpty()) return info(
                    "No widgets yet — widget add opens the picker",
                    LogContext.of(LogContext.RUN to "widget add")
                )
                lines.forEach { (text, desc) ->
                    out(
                        text,
                        LogContext.of(LogContext.RUN to "widgets", LogContext.DESC to desc)
                    )
                }
            }

            "add" -> ui.openScreen(Screen.WIDGET_PICKER)
            else -> usage("widgets", "widget")
        }
    }

    /** Asks for the runtime permission when possible. Otherwise (before Android 13, or turned off) opens settings. */
    fun notifications(args: List<String>) {
        val permissionMissing = Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(
            android.Manifest.permission.POST_NOTIFICATIONS,
        ) != PackageManager.PERMISSION_GRANTED
        when {
            args.firstOrNull() == "settings" || (!permissionMissing && !Notifier.canPost(context)) ->
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                )

            permissionMissing -> ui.requestNotificationPermission()
            else -> info("Notifications are allowed")
        }
    }

    /**
     * - `wallpaper` says whether the home screen wallpaper is Kolahal's.
     * - `wallpaper match` sets Kolahal's wallpaper ([com.mtwabpin.kolahal.control.WallpaperMatch]), in
     *   the theme's color as home last showed it.
     *   It asks first, because Android doesn't let an app read the wallpaper it replaces, so the
     *   old one can't be restored.
     * - The Material You themes take their colors from the wallpaper. So the wallpaper can't follow
     *   one, and both forms say so.
     */
    fun wallpaper(args: List<String>) {
        val match = com.mtwabpin.kolahal.control.WallpaperMatch
        // The theme in use, when it is a Material You one: its name is in the messages.
        val theme = conf[Keys.THEME].takeIf(com.mtwabpin.kolahal.data.MaterialYou::isOne)
            ?: com.mtwabpin.kolahal.data.MaterialYou.NAME
        when (args.firstOrNull()?.lowercase()) {
            null -> info(
                when {
                    !match.isMatched(context) -> "Your home screen wallpaper is your own; Kolahal doesn't change it (wallpaper match sets it to the theme's color)"
                    match.themeFromWallpaper -> "Your home screen wallpaper is Kolahal's, but it doesn't follow the $theme theme, which takes its colors from the wallpaper. It follows again when you pick another theme"
                    else -> "Your home screen wallpaper is in the theme's color, and follows the theme"
                },
            )

            "match" -> {
                val color = match.themeColor
                when {
                    match.themeFromWallpaper -> error("The $theme theme takes its colors from the wallpaper, so the wallpaper can't follow it: pick another theme, then try again")
                    match.isMatched(context) -> info("Your home screen wallpaper already follows the theme")
                    args.getOrNull(1) != "--yes" -> {
                        info("This replaces your home screen wallpaper with this theme's color, so it doesn't flash when you swipe home. It follows the theme until you set another wallpaper. Kolahal can't restore the old one, and log.wallpaper and widgets.wallpaper will show only this color.")
                        info(
                            "Tap to replace it",
                            LogContext.of(LogContext.RUN to "wallpaper match --yes")
                        )
                    }

                    color == null -> error("Kolahal doesn't know the theme's color yet: go home, then try again")
                    match.set(
                        context,
                        color
                    ) -> info("Home screen wallpaper set to this theme's color. It follows the theme until you set another wallpaper; the lock screen wallpaper isn't changed")

                    else -> error("Couldn't change the wallpaper on this phone")
                }
            }

            else -> usage("wallpaper")
        }
    }

    /**
     * `screen <name>`: one of the full-screen screens over home
     * ([com.mtwabpin.kolahal.terminal.tty.screens.TtyScreens]). Without a known name it lists them.
     */
    /**
     * `scramble [on|off]`: home shows its log scrambled, for a screen recording, or as it is again
     * ([com.mtwabpin.kolahal.data.HomeScramble]). Its line in the log is scrambled like the others.
     */
    fun scramble(args: List<String>) {
        val line = com.mtwabpin.kolahal.data.HomeScramble.get(context).run(args) ?: return usage("scramble")
        if (line == com.mtwabpin.kolahal.data.HomeScramble.NOT_KEPT) error(line) else info(line)
    }

    fun screen(args: List<String>) {
        val names = com.mtwabpin.kolahal.terminal.tty.screens.TtyScreens.names
        val name = args.firstOrNull()?.lowercase() ?: return usage("screen")
        if (name !in names) return error("No screen called $name. Screens: ${names.joinToString(", ")}")
        ui.showTtyScreen(name)
    }

    /** `fetch`: a summary of this phone and Kolahal ([Fetch]), aligned in the terminal font. */
    fun fetch() {
        val (device, rows) = Fetch.facts(context)
        val at = System.currentTimeMillis()
        logs.addAll(
            Fetch.layout(
                device,
                rows,
                Fetch.columns(context, ui.logWidthPx()),
                conf[Keys.USER_NAME]
            ).map {
                LogRepository.Pending(
                    LogKind.OUTPUT,
                    it,
                    null,
                    LogContext(LogContext.ALIGNED),
                    at
                )
            })
    }

    /**
     * - `backup` writes today's backup file to the backup folder. If no folder is set, it asks for one first.
     * - `backup every daily|weekly|off` sets automatic backups.
     * - `backup dir` picks the folder.
     */
    fun backup(args: List<String>) {
        val auto = com.mtwabpin.kolahal.data.AutoBackup
        when (args.firstOrNull()?.lowercase()) {
            null, "now" -> if (auto.folder(context) == null) {
                info("Pick a folder for backups; the backup is written there")
                ui.askBackupFolder(thenBackUp = true)
            } else background("backup") {
                val name = auto.backUp(context)
                val every =
                    conf[Keys.BACKUP_AUTO].takeIf { it != "off" }?.let { " · automatic: $it" }
                        ?: " · 'backup every daily' automates it"
                info("Backed up: $name in ${auto.folderName(context) ?: "the backup folder"}$every")
            }

            "every", "auto" -> {
                val value = when (val v = args.getOrNull(1)?.lowercase()) {
                    "day" -> "daily"; "week" -> "weekly"; else -> v
                }
                if (value == null || value !in Keys.BACKUP_AUTO.choices) return error("Usage: backup every <daily|weekly|off>")
                conf.set(Keys.BACKUP_AUTO, value)
                if (value == "off") return info("Automatic backups off")
                info("Backing up $value to ${auto.folderName(context) ?: "a folder you pick"}, keeping the newest ${conf[Keys.BACKUP_KEEP]} (backup.keep)")
                if (auto.folder(context) == null) ui.askBackupFolder(thenBackUp = false)
            }

            "dir", "folder" -> ui.askBackupFolder(thenBackUp = false)
            else -> usage("backup")
        }
    }

    /**
     * - `restart` relaunches the launcher.
     * - `restart scripts` stops the :script process, for stuck scripts.
     */
    fun restart(args: List<String>) {
        when (args.firstOrNull()) {
            null, "launcher" -> {
                info("Restarting…")
                com.mtwabpin.kolahal.LauncherApp.restart(context, afterMs = 300)
            }

            "scripts", "script" -> {
                val am = context.getSystemService(android.app.ActivityManager::class.java)
                val scriptProcess = am.runningAppProcesses.orEmpty()
                    .firstOrNull { it.processName.endsWith(":script") }
                if (scriptProcess == null) {
                    info("Script process isn't running")
                } else {
                    android.os.Process.killProcess(scriptProcess.pid)
                    info("Script process stopped; scheduled scripts will start it again")
                }
            }

            else -> usage("restart")
        }
    }

    /** The text form of the setup checklist (`setup text`). Tapping an unchecked line runs its fix. */
    fun setupText() {
        info("Setup — tap an unchecked item to fix it, run 'setup text' again to re-check")
        SetupChecks.run(context).forEach { c ->
            val mark = when {
                c.ok -> "[x]"
                c.fix == null -> "[-]"
                else -> "[ ]"
            }
            out(
                "$mark ${c.title} — ${c.detail}",
                LogContext(
                    LogContext.ITEM + if (c.ok || c.fix == null) emptyMap() else mapOf(LogContext.RUN to c.fix)
                )
            )
        }
    }
}
