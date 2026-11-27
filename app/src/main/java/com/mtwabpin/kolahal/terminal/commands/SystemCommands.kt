/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import android.content.Intent
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.terminal.MonthCalendar
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.SetupChecks
import com.mtwabpin.kolahal.terminal.TerminalUi
import com.mtwabpin.kolahal.terminal.WorldClock
import com.mtwabpin.kolahal.widgets.listing
import java.text.DateFormat
import java.util.Date

/**
 * Commands about the phone and home: `date` and `cal`, `widget` and `widgets`, `wallpaper`,
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
            ?: return error("Unknown month '$rest'\nUse cal, cal dec, cal dec 2027 or cal 2027-12")
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
            ?: return error("Unknown place '${words.joinToString(" ")}'\nUse a city (tokyo, new york), a zone (Asia/Tokyo), utc, or an offset like +5:30")
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
     * `widget` and `widgets`, [name] as typed ([WidgetWords]):
     * - `widgets` alone opens Arrange widgets.
     * - `widget page` turns home to page 1 of the widgets pages beside the log; `widget page 2` and
     *   `widget page work` to the page in that place, or with that name or the start of it
     *   ([WidgetWords.page]).
     * - `widget ls`, or `widget` alone, prints the placed widgets, under each page's name when there
     *   is more than one page. A tap on a widget opens Arrange widgets, one on a page's name turns
     *   home to that page.
     * - `widget add` opens the picker. The widget goes on the page home showed last.
     * `widgets page`, `widgets ls` and `widgets add` work the same.
     */
    fun widgets(name: String, args: List<String>) {
        val host = com.mtwabpin.kolahal.widgets.WidgetHost.get(context)
        when (WidgetWords.of(name, args)) {
            WidgetWords.Action.ARRANGE -> ui.openScreen(Screen.WIDGETS)
            WidgetWords.Action.PAGE -> if (!conf[Keys.WIDGETS_SHOW]) fix(
                "The widgets page is off\nTap to turn it on",
                "config set widgets.show on"
            ) else {
                val words = args.drop(1).joinToString(" ")
                if (words.isBlank()) return ui.showWidgetsPage()
                when (val to = WidgetWords.page(host.pages.value, words)) {
                    is WidgetWords.PageTarget.Found -> ui.showWidgetsPage(to.page)
                    is WidgetWords.PageTarget.Missing -> error(
                        to.message,
                        LogContext.of(LogContext.COPY to to.copy)
                    )
                }
            }

            WidgetWords.Action.LIST -> {
                val lines = host.listing(context)
                if (lines.isEmpty()) return info(
                    "No widgets yet\nTap to open the widget picker",
                    LogContext.of(LogContext.RUN to "widget add")
                )
                lines.forEachIndexed { i, line ->
                    val page = line.page
                    if (page != null) {
                        // A page's name over its widgets: a tap turns home to it. A gap sets the pages apart.
                        val run = if (page > 0) "widget page $page" else "widgets"
                        out(
                            line.text,
                            LogContext(mapOf(LogContext.RUN to run) + if (i > 0) LogContext.ITEM else emptyMap())
                        )
                    } else out(
                        line.text,
                        LogContext.of(LogContext.RUN to "widgets", LogContext.DESC to line.desc)
                    )
                }
            }

            WidgetWords.Action.ADD -> ui.openScreen(Screen.WIDGET_PICKER)
            null -> usage("widget")
        }
    }

    /**
     * - `wallpaper` says what the home screen wallpaper is: the user's own, the theme's color, or a shader
     *   ([WallpaperStatus]); and the lock screen's, when it has one of another kind. Then it lists what
     *   can become the wallpaper, each as a line to tap ([WallpaperStatus.choices]).
     * - `wallpaper theme` sets Kolahal's wallpaper ([com.mtwabpin.kolahal.control.WallpaperMatch]), in
     *   the theme's color as home last showed it.
     *   It asks first, because Android doesn't let an app read the wallpaper it replaces, so the
     *   old one can't be restored.
     * - The Material You themes take their colors from the wallpaper. So the wallpaper can't follow
     *   one, and both forms say so.
     * - `wallpaper shader <name>` makes a shader Kolahal's live wallpaper
     *   ([com.mtwabpin.kolahal.shader.WallpaperChoice]), always in Android's own preview, where people
     *   set it for the home screen, the lock screen or both.
     * - `wallpaper live` opens Android's own list of live wallpapers, for a phone whose wallpaper settings don't
     *   link to it.
     */
    fun wallpaper(args: List<String>) {
        val match = com.mtwabpin.kolahal.control.WallpaperMatch
        // The theme in use, when it is a Material You one: its name is in the messages.
        val theme = conf[Keys.THEME].takeIf(com.mtwabpin.kolahal.data.MaterialYou::isOne)
            ?: com.mtwabpin.kolahal.data.MaterialYou.NAME
        when (args.firstOrNull()?.lowercase()) {
            null -> {
                val shown = com.mtwabpin.kolahal.shader.WallpaperChoice.shown(context)
                val home = WallpaperStatus.home(live = shown.home, matched = match.isMatched(context))
                WallpaperStatus.lines(
                    home = home,
                    lock = WallpaperStatus.lock(own = shown.lockOwn, live = shown.lock),
                    shader = ShaderCommands.wallpaperDrawn(com.mtwabpin.kolahal.shader.WallpaperChoice.name.value),
                    theme = theme,
                    themeFromWallpaper = match.themeFromWallpaper,
                ).forEach { info(it) }
                // What can become the wallpaper, each as a line to tap: its word, what it is, and a few words more.
                val choices = WallpaperStatus.choices(home)
                val column = choices.map { it.word }.maxBy { it.length }
                choices.forEach { c ->
                    out(
                        c.word,
                        LogContext.of(
                            (if (c.run != null) LogContext.RUN else LogContext.COMMAND) to (c.run ?: c.fill.orEmpty()),
                            LogContext.DESC to c.title,
                            LogContext.NAME_COL to column,
                            LogContext.ARGS to c.note,
                        ),
                    )
                }
            }

            "theme" -> when {
                match.themeFromWallpaper -> error(WallpaperStatus.said(com.mtwabpin.kolahal.control.WallpaperMatch.Result.FROM_WALLPAPER, theme))
                match.isMatched(context) -> info(WallpaperStatus.said(com.mtwabpin.kolahal.control.WallpaperMatch.Result.ALREADY, theme))
                // It asks first: Android doesn't let an app read the wallpaper it replaces, so the old one is lost.
                args.getOrNull(1) != "--yes" -> {
                    info(WallpaperStatus.REPLACES)
                    info("Tap to replace it", LogContext.of(LogContext.RUN to "wallpaper theme --yes"))
                }

                else -> match.useTheme(context).let { result ->
                    val words = WallpaperStatus.said(result, theme)
                    if (WallpaperStatus.failed(result)) error(words) else info(words)
                }
            }

            "shader" -> {
                val name = args.getOrNull(1)?.lowercase() ?: return usage("wallpaper")
                val shaders = com.mtwabpin.kolahal.shader.Shaders
                val choice = com.mtwabpin.kolahal.shader.WallpaperChoice
                if (!shaders.exists(name)) return error("No shader '$name'\n'shader' lists them", LogContext.of(LogContext.COPY to "shader"))
                val meta = shaders.source(context, name)?.let(com.mtwabpin.kolahal.shader.ShaderMeta::parse)
                    ?: return error("Shader $name can't be read")
                (choice.problem(name, meta) ?: shaders.problem(context, name))?.let { return error(shaders.problemLine(name, it)) }
                choice.set(context, name)
                // Always Android's own preview, also when Kolahal's wallpaper is the phone's already: there the
                // person sees the shader and says where it goes, the home screen, the lock screen or both.
                // Changed without it, a wallpaper that is only on the lock screen would seem not to have been set.
                if (runCatching { startActivity(choice.preview(context)) }.isFailure) error("This phone has no live wallpaper preview")
            }

            "live" -> if (runCatching { startActivity(Intent(android.app.WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }.isFailure) {
                error(WallpaperStatus.NO_LIST)
            }

            else -> usage("wallpaper")
        }
    }

    /**
     * `screen <name>`: one of the full-screen screens over home
     * ([com.mtwabpin.kolahal.terminal.tty.screens.TtyScreens]). Without a known name it lists them.
     * It runs in both home styles; the gui style doesn't offer it ([com.mtwabpin.kolahal.commands.TtyCommands]).
     */
    /**
     * `scramble [on|off]`: home shows its log scrambled, for a screen recording, or as it is again
     * ([com.mtwabpin.kolahal.data.HomeScramble]). Its line in the log is scrambled like the others.
     */
    fun scramble(args: List<String>) {
        val scramble = com.mtwabpin.kolahal.data.HomeScramble
        val line = scramble.get(context).run(args) ?: return usage("scramble")
        // While it is on, the line names the way off: that command is what Copy gives.
        val on = line == scramble.ON || line == scramble.ALREADY_ON
        if (line == scramble.NOT_KEPT) error(line)
        else info(line, if (on) LogContext.of(LogContext.COPY to scramble.COPY) else LogContext.EMPTY)
    }

    fun screen(args: List<String>) {
        val names = com.mtwabpin.kolahal.terminal.tty.screens.TtyScreens.names
        val name = args.firstOrNull()?.lowercase() ?: return usage("screen")
        if (name !in names) return error("No screen called $name\nScreens: ${names.joinToString(", ")}")
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
                info("Pick a folder for backups\nThe backup is written there")
                ui.askBackupFolder(thenBackUp = true)
            } else background("backup") {
                val name = auto.backUp(context)
                val saved = "Backed up: $name\nFolder: ${auto.folderName(context) ?: "the backup folder"}"
                when (val every = conf[Keys.BACKUP_AUTO].takeIf { it != "off" }) {
                    null -> info(
                        "$saved\n'backup every daily' automates it",
                        LogContext.of(LogContext.COPY to "backup every daily"),
                    )

                    else -> info("$saved\nAutomatic: $every")
                }
            }

            "every", "auto" -> {
                val value = when (val v = args.getOrNull(1)?.lowercase()) {
                    "day" -> "daily"; "week" -> "weekly"; else -> v
                }
                if (value == null || value !in Keys.BACKUP_AUTO.choices) return error("Usage: backup every <daily|weekly|off>")
                conf.set(Keys.BACKUP_AUTO, value)
                if (value == "off") return info("Automatic backups off")
                info("Backing up $value to ${auto.folderName(context) ?: "a folder you pick"}\nKeeping the newest ${conf[Keys.BACKUP_KEEP]} (backup.keep)")
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
                    info("Script process stopped\nScheduled scripts will start it again")
                }
            }

            else -> usage("restart")
        }
    }

    /** The text form of the setup checklist (`setup text`). Tapping an unchecked line runs its fix. */
    fun setupText() {
        info(
            "Setup checklist\nTap an unchecked item to fix it\nRun 'setup text' again to check again",
            LogContext.of(LogContext.COPY to "setup text"),
        )
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
