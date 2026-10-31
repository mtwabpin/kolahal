package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.Net
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.notify.Notifier
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.unlock.UnlockCounter
import com.mtwabpin.kolahal.widgets.listing
import java.text.DateFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date

/**
 * Parses and runs commands typed at the prompt. Everything it prints goes into the
 * persistent log with a [LogContext] so lines stay actionable later (tap / long-press).
 */
class CommandProcessor(private val context: Context, private val ui: TerminalUi) {
    private val logs = LogRepository.get(context)
    private val apps = AppRepository.get(context)
    private val scripts = ScriptRepository.get(context)
    private val unlocks = UnlockCounter.get(context)
    private val conf = Config.get(context)
    private val prefs = AppPrefs.get(context)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val controls by lazy { com.mtwabpin.kolahal.control.ControlCommands(context) }
    private val productivity = ProductivityCommands(context, ui)
    private val scriptCommands = ScriptCommands(context, ui, ::background)
    private val configCommands = ConfigCommands(context, ui)
    private val people = PeopleCommands(context, ui)
    private val appCommands = AppCommands(context, ::background)
    private val feedCommands = FeedCommands(context, appCommands, ui)
    private val updateCommands = UpdateCommands(
    context,
    ::background,
    { text, ctx -> out(text, ctx) },
    { text, ctx -> info(text, ctx) },
) { message, url ->
    logs.add(
        LogKind.ERROR,
        message,
        ctx = LogContext.of(
            LogContext.URL to (url ?: "https://github.com/mtwabpin/kolahal/releases/latest")
        ),
    )
    }

    private fun background(label: String, block: () -> Unit) {
        backgroundScope.launch {
            runCatching(block).onFailure { error("$label: ${it.message ?: it.javaClass.simpleName}") }
        }
    }

    /** [expand] = false runs [line] literally (tap-to-run lines must not be rewritten by aliases). */
    fun run(line: String, expand: Boolean = true) {
        val typed = CommandParsing.spaces(line).trim()
        if (typed.isEmpty()) return
        // The same command again with nothing printed in between (settings, settings…) is one line.
        // `fresh` isn't logged at all: it would put a line straight back on the clean screen.
        // Nor is a line holding a script secret's value: the log, and the history built from it, never see it.
        val secret = ScriptCommands.carriesSecret(typed) || (expand && ScriptCommands.carriesSecret(expandAliases(typed)))
        val last = logs.last()
        val commandId = when {
            typed.lowercase() == "fresh" || secret -> null
            // Its output joins the line already there.
            last != null && last.second == LogKind.COMMAND && last.third == typed -> last.first
            else -> logs.add(LogKind.COMMAND, typed, ctx = LogContext.of(LogContext.COMMAND to typed))
        }
        // A command you run ends `log only`: what you typed, and what it prints, must show.
        if (!ScriptScheduler.runningSentCommand && typed.substringBefore(' ').lowercase() != "log") ui.filterLog(null)
        // What it prints, now or later (a script, a feed fetch), is marked as its output.
        logs.withCause(commandId) { runLogged(typed, expand) }
    }

    /** What the command prints is written together ([LogRepository.batched]): one write for a page of output, not one per line. */
    private fun runLogged(typed: String, expand: Boolean) = logs.batched {
        // Aliases first, so `alias hf = help | grep feed` pipes too; its definition keeps the pipe.
        val expanded = if (expand) expandAliases(typed) else typed
        val stages = if (expanded.substringBefore(' ').lowercase() == "alias") listOf(expanded) else Pipes.split(expanded)
        if (stages.size > 1) {
            // `cmd | grep x | head 5`: capture what the command prints right away, then filter it.
            val (_, captured) = logs.capture { execute(stages[0], expand = false) }
            Pipes.apply(captured, stages.drop(1)).fold(
                onSuccess = { logs.addAll(it) },
                onFailure = { logs.addAll(captured); error(it.message ?: "Bad pipe") },
            )
            return@batched
        }
        execute(typed, expand)
    }

    private fun execute(typed: String, expand: Boolean) {
        val trimmed = if (expand) expandAliases(typed) else typed
        // "= 6*7" is a quick calculator (any Lua expression).
        if (trimmed.startsWith("=")) return calc(trimmed.drop(1).trim())
        val name = trimmed.substringBefore(' ').lowercase()
        val rest = trimmed.substringAfter(' ', "").trim()
        val args = CommandParsing.tokenize(rest)
        runCatching {
            when (name) {
                "alias" -> alias(rest)
                "unalias" -> unalias(args)
                in AppCommands.NAMES -> appCommands.run(name, args)
                "help", "?" -> if (args.isEmpty()) help() else commandHelp(args.first())
                "unlocks" -> unlocks(args)
                "fetch", "neofetch", "fastfetch" -> fetch()
                "clear", "cls" -> clear(args)
                "backup" -> backup(args)
                "log" -> logFilter(args)
                "fresh" -> ui.freshScreen()
                "restart", "reboot" -> restart(args)
                "history" -> history()
                "scripts" -> if (args.isEmpty()) ui.openScreen(Screen.SCRIPTS) else scriptCommands.script(args)
                "script" -> scriptCommands.script(args)
                "lua" -> eval(ScriptLang.LUA, rest)
                "js" -> eval(ScriptLang.JS, rest)
                "update" -> if (com.mtwabpin.kolahal.BuildConfig.SELF_UPDATE) {
    updateCommands.run(args)
} else {
    error("Self-updates aren't available in the Play build")
                }
                "rss" -> feedCommands.rss(rest)
                "feeds" -> if (args.isEmpty()) ui.openScreen(Screen.FEEDS) else feedCommands.feeds(args).also { runCatching { com.mtwabpin.kolahal.feeds.FeedScheduler.sync(context) } }
                // A feed added, removed, switched or given a new interval changes the schedule.
                "feed" -> feedCommands.feeds(args).also { runCatching { com.mtwabpin.kolahal.feeds.FeedScheduler.sync(context) } }
                "widgets" -> if (args.isEmpty()) ui.openScreen(Screen.WIDGETS) else widgets(args)
                "widget" -> widgets(args)
                "notifs", "notifications-feed" -> feedCommands.notifs(args)
                "url", "web" -> openUrl(rest)
                "source" -> openUrl(com.mtwabpin.kolahal.ui.SOURCE_URL)
                "wallpaper" -> wallpaper(args)
                "search", "g" -> search(rest)
                "yt", "youtube" -> search(rest, "youtube")
                "wiki" -> search(rest, "wikipedia")
                "maps", "map" -> if (rest.isBlank()) fallbackLaunch(trimmed) else openUri("geo:0,0?q=" + Uri.encode(rest), "Maps: $rest")
                "play", "store" -> if (rest.isBlank()) fallbackLaunch(trimmed) else openUri("market://search?q=" + Uri.encode(rest), "Play Store: $rest")
                "calls" -> people.calls(args)
                "call", "dial" -> people.call(rest)
                "sms", "text" -> people.sms(args)
                "contacts", "contact" -> people.listContacts(rest)
                "notifications" -> notifications(args)
                "local-network" -> localNetwork(args)
                "crashes" -> crashes(args)
                "setup" -> if (args.firstOrNull() == "text") setupText() else ui.openScreen(Screen.SETUP)
                "tour" -> ui.openScreen(Screen.ONBOARDING)
                "status" -> scriptCommands.status(args)
                "usage-access" -> startActivity(unlocks.usageAccessIntent())
                "battery" -> startSettings(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                "settings", "prefs" -> ui.openScreen(Screen.SETTINGS)
                "system" -> startSettings(Settings.ACTION_SETTINGS)
                "home" -> startSettings(Settings.ACTION_HOME_SETTINGS)
                "lock", "accessibility" -> if (!com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE) {
                    error("This build has no accessibility service, so it can't lock the screen")
                } else if (name == "accessibility") {
                    startActivity(com.mtwabpin.kolahal.gesture.LockService.settingsIntent())
                } else if (!com.mtwabpin.kolahal.gesture.LockService.lock()) {
                    logs.add(LogKind.ERROR, "Locking needs the Kolahal screen lock accessibility service — tap to enable it", ctx = LogContext.of(LogContext.RUN to "accessibility"))
                }
                "shade" -> com.mtwabpin.kolahal.gesture.Gestures.expandShade(context)
                "date", "time" -> if (args.isEmpty()) out(DateFormat.getDateTimeInstance().format(Date())) else date(args)
                "echo" -> out(rest)
                "cal" -> cal(rest)
                "config", "set" -> if (name == "config" && args.isEmpty()) ui.openScreen(Screen.SETTINGS) else configCommands.config(args, rest)
                "theme" -> configCommands.config(listOf("set", Keys.THEME.key) + args)
                "font" -> configCommands.font(args)
                in com.mtwabpin.kolahal.control.ControlCommands.NAMES -> controls.run(name, args)
                in PRODUCTIVITY -> productivity.run(name, rest)
                else -> scriptCommands.runCommand(name, args) ?: fallbackLaunch(trimmed)
            }
        }.onFailure { error("$name: ${it.message ?: it.javaClass.simpleName}") }
    }

    // ---- aliases --------------------------------------------------------------------------

    /** Expands the first word through user aliases (up to a few levels, no cycles). */
    private fun expandAliases(line: String): String {
        var current = line
        val seen = HashSet<String>()
        repeat(5) {
            val head = current.substringBefore(' ')
            // A built-in always wins, even over an alias that slipped in (an old backup).
            if (head.lowercase() in RESERVED_NAMES) return current
            if (!seen.add(head)) return current
            val expansion = prefs.alias(head) ?: return current
            current = (expansion + " " + current.substringAfter(' ', "")).trim()
        }
        return current
    }

    private fun alias(rest: String) {
        if (rest.isBlank()) {
            val all = prefs.aliases()
            if (all.isEmpty()) info("No aliases — e.g. alias m = open maps")
            all.forEach { (k, v) -> out("$k = $v", LogContext.of(LogContext.COMMAND to k)) }
            return
        }
        // Lowercase, as typed lines are matched: `alias Help = …` can't hide behind the built-in.
        val name = rest.substringBefore('=').trim().substringBefore(' ').lowercase()
        val expansion = rest.substringAfter('=', "").trim().removeSurrounding("\"").removeSurrounding("'")
        when {
            !rest.contains('=') -> prefs.alias(name)?.let { out("$name = $it") } ?: error("No alias '$name'")
            name.isEmpty() || expansion.isEmpty() -> error("Usage: alias <name> = <command>")
            name in RESERVED_NAMES -> error("'$name' is a built-in command and can't be an alias")
            else -> {
                prefs.setAlias(name, expansion)
                info("Alias $name = $expansion")
            }
        }
    }

    private fun unalias(args: List<String>) {
        val name = args.firstOrNull() ?: return error("Usage: unalias <name>")
        if (prefs.alias(name) == null) return error("No alias '$name'")
        prefs.setAlias(name, null)
        info("Removed alias $name")
    }

    // ---- output helpers -------------------------------------------------------------------

    private fun out(text: String, ctx: LogContext = LogContext.EMPTY) { logs.add(LogKind.OUTPUT, text, ctx = ctx) }
    private fun info(text: String, ctx: LogContext = LogContext.EMPTY) { logs.add(LogKind.INFO, text, ctx = ctx) }
    private fun error(text: String) { logs.add(LogKind.ERROR, text) }

    /** `cal [dec [2027]]`: the month as a calendar, today in bold, weeks starting on the locale's day. */
    private fun cal(rest: String) {
        val today = java.time.LocalDate.now()
        val month = MonthCalendar.parse(rest, today)
            ?: return error("No month called '$rest'. cal, cal dec, cal dec 2027 or cal 2027-12")
        val lines = MonthCalendar.lines(month, today) { com.mtwabpin.kolahal.data.StyledText.wrap(com.mtwabpin.kolahal.data.StyledText.Style.BOLD, it) }
        // One line each, in the terminal font, so the columns hold.
        lines.forEach { out(it, LogContext(LogContext.ALIGNED)) }
    }

    /** `date tokyo new york`: the time in each place, one line each, and how far ahead or behind it is. */
    private fun date(words: List<String>) {
        val places = WorldClock.parse(words)
            ?: return error("No place called '${words.joinToString(" ")}'. A city (tokyo, new york), a zone (Asia/Tokyo), utc, or an offset like +5:30")
        val now = java.time.Instant.now()
        val clock24 = Config.get(context)[Keys.CLOCK_24H]
        for (place in places) {
            val line = WorldClock.line(place, now, java.time.ZoneId.systemDefault(), clock24)
            out(line.place, LogContext.of(LogContext.DESC to line.detail))
        }
    }

    /** Launches [app], as a tap on it or on its suggestion does. */
    fun launch(app: AppEntry) = appCommands.launch(app)

    /** Anything that isn't a command is treated as an app name. */
    private fun fallbackLaunch(text: String) {
        val ranked = apps.searchRanked(text)
        val matches = ranked.map { it.first }
        when {
            matches.isEmpty() -> notFound(text)
            matches[0].label.equals(text, ignoreCase = true) -> appCommands.launch(matches[0])
            matches.size == 1 && conf[Keys.AUTO_LAUNCH] -> appCommands.launch(matches[0])
            // The top suggestion chip is what Enter launches, as long as it starts with the input.
            conf[Keys.LAUNCH_TOP] && ranked[0].second <= 2 -> appCommands.launch(matches[0])
            else -> {
                appCommands.appGrid(matches.take(12))
                info("${matches.size} matches — tap one to launch")
            }
        }
    }

    /** Unknown input: suggest the closest command (typo in the first word). */
    private fun notFound(text: String) {
        val head = text.substringBefore(' ').lowercase()
        val rest = text.substringAfter(' ', "")
        val candidates = COMMAND_NAMES - UNAVAILABLE + prefs.aliases().keys +
            scripts.all().filter { ScriptMeta.isCommand(it.code) }.map { it.name }
        val best = candidates.filter { it.length > 1 }
            .map { it to CommandParsing.editDistance(head, it) }
            .filter { (c, d) -> d <= (if (c.length <= 4) 1 else 2) }
            .minByOrNull { it.second }?.first
        if (best == null) {
            error("Command not found: $text  (try 'help')")
        } else {
            val corrected = (best + " " + rest).trim()
            logs.add(
                LogKind.ERROR,
                "Command not found: $head — did you mean '$corrected'? (tap to put it in the prompt)",
                // The trailing space marks it as one to fill in, not run, whatever launch.instant says.
                ctx = LogContext.of(LogContext.COMMAND to "$corrected "),
            )
        }
    }

    // ---- misc -----------------------------------------------------------------------------

    private fun unlocks(args: List<String>) {
        val days = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 30) ?: 1
        if (!unlocks.hasUsageAccess()) {
            info("Counting via unlock receiver (misses unlocks while launcher isn't running)")
            info("Tap here to grant usage access for accurate counts", LogContext.of(LogContext.RUN to "usage-access"))
        }
        val today = java.time.LocalDate.now()
        val fmt = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM", java.util.Locale.getDefault())
        val rows = unlocks.history(days).map { (day, count) ->
            val label = when (day) {
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> day.format(fmt)
            }
            label to count
        }
        // Padded so the counts line up (the log is monospaced).
        val labelWidth = rows.maxOf { it.first.length }
        val countWidth = rows.maxOf { it.second.toString().length }
        rows.forEach { (label, count) ->
            out("${label.padEnd(labelWidth)}  ${count.toString().padStart(countWidth)} ${if (count == 1) "unlock" else "unlocks"}", LogContext(LogContext.ALIGNED))
        }
    }

    private fun history() {
        val cmds = logs.commandHistory(30).reversed()
        cmds.forEach { out(it, LogContext.of(LogContext.COMMAND to it)) }
        if (cmds.isEmpty()) info("History is empty")
    }

    private fun eval(lang: ScriptLang, code: String) {
        if (code.isBlank()) {
            error("Usage: ${lang.ext} <code>")
            return
        }
        if (!ScriptScheduler.eval(context, lang, code)) error("Snippet too long — use 'script new'")
    }

    private fun openUrl(raw: String) {
        if (raw.isBlank()) return error("Usage: url <address>")
        val url = if (raw.contains("://")) raw else "https://$raw"
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        info("Opened $url", LogContext.of(LogContext.URL to url))
    }

    private fun search(query: String, engine: String? = null) {
        if (query.isBlank()) return error("Usage: search <query>")
        val template = when (engine ?: conf[Keys.SEARCH_ENGINE]) {
            "google" -> "https://www.google.com/search?q=%s"
            "bing" -> "https://www.bing.com/search?q=%s"
            "brave" -> "https://search.brave.com/search?q=%s"
            "startpage" -> "https://www.startpage.com/do/search?q=%s"
            "kagi" -> "https://kagi.com/search?q=%s"
            "youtube" -> "https://www.youtube.com/results?search_query=%s"
            "wikipedia" -> "https://en.wikipedia.org/w/index.php?search=%s"
            "custom" -> conf[Keys.SEARCH_URL].takeIf { it.contains("%s") } ?: "https://duckduckgo.com/?q=%s"
            else -> "https://duckduckgo.com/?q=%s"
        }
        val url = template.replace("%s", Uri.encode(query))
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        info("Search: $query", LogContext.of(LogContext.URL to url))
    }

    /** Opens [uri] in whatever app handles it (maps, store, ...). */
    private fun openUri(uri: String, what: String) {
        startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
        info(what, LogContext.of(LogContext.URL to uri))
    }

    // ---- calculator & conversions --------------------------------------------------------

    /** `= 2^10`, `= 5 km in mi`, `= 72 f to c`, `= 100 usd in inr` (live rates, cached 6h). */
    private fun calc(expr: String) {
        val conversion = Units.parse(expr)
        if (conversion != null) {
            val (amount, from, to) = conversion
            Units.convert(amount, from, to)?.let { return out(it, LogContext.of(LogContext.COPY to it.substringAfter("= "))) }
            if (Units.looksLikeCurrency(from) && Units.looksLikeCurrency(to)) {
                return background("convert") { currency(amount, from.uppercase(), to.uppercase()) }
            }
        }
        // Arithmetic is answered here at once; anything else (Lua code) goes to Lua.
        val answer = try {
            Calc.answer(expr)
        } catch (e: ArithmeticException) {
            return error("$expr: ${e.message}")
        }
        if (answer != null) return out(answer, LogContext.of(LogContext.COPY to answer))
        eval(ScriptLang.LUA, expr)
    }

    private fun currency(amount: Double, from: String, to: String) {
        val cache = KvStore(context, "fx")
        val cached = cache.get(from)?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() }
        val fresh = (cached?.get("at") as? Number)?.toLong()?.let { System.currentTimeMillis() - it < 6 * 3_600_000L } == true
        val rates = if (fresh) {
            cached["rates"] as? Map<*, *>
        } else {
            val body = Json.parse(Net.get("https://open.er-api.com/v6/latest/$from")) as? Map<*, *>
                ?: throw IllegalStateException("bad response")
            if (body["result"] != "success") throw IllegalStateException("unknown currency $from")
            (body["rates"] as? Map<*, *>)?.also {
                cache.set(from, Json.stringify(mapOf("at" to System.currentTimeMillis(), "rates" to it)))
            }
        }
        val rate = (rates?.get(to) as? Number)?.toDouble() ?: throw IllegalStateException("unknown currency $to")
        val text = "${Units.format(amount)} $from = ${Units.format(amount * rate)} $to"
        out(text, LogContext.of(LogContext.COPY to Units.format(amount * rate)))
    }

    // ---- widgets ----------------------------------------------------------------------------

    /** `widgets ls` prints the placed widgets (tap opens the page); `widget add` opens the picker. */
    private fun widgets(args: List<String>) {
        val host = com.mtwabpin.kolahal.widgets.WidgetHost.get(context)
        when (args.firstOrNull()) {
            null -> ui.openScreen(Screen.WIDGETS)
            "ls", "list" -> {
                val lines = host.listing(context)
                if (lines.isEmpty()) return info("No widgets yet — widget add opens the picker", LogContext.of(LogContext.RUN to "widget add"))
                lines.forEach { (text, desc) -> out(text, LogContext.of(LogContext.RUN to "widgets", LogContext.DESC to desc)) }
            }
            "add" -> ui.openScreen(Screen.WIDGET_PICKER)
            else -> error("Usage: widgets [ls] · widget add")
        }
    }

    /** Asks for the runtime permission when it can; otherwise (pre-13, or turned off) opens settings. */
    private fun notifications(args: List<String>) {
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

    /** `local-network`: Android 17's permission for scripts with `@uses lan` and feeds on your network. */
    private fun localNetwork(args: List<String>) {
        when {
            Build.VERSION.SDK_INT < 37 -> info("Reaching your local network needs no permission before Android 17")
            args.firstOrNull() == "--settings" ->
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            Net.localNetworkAllowed() -> info("Local network access is allowed")
            else -> ui.requestLocalNetworkPermission()
        }
    }

    /**
     * `wallpaper`: whether the home screen's wallpaper is Kolahal's; `wallpaper match` makes it so
     * ([com.mtwabpin.kolahal.ui.WallpaperMatch]). It asks first: Android doesn't let an app read the
     * wallpaper it replaces, so the old one can't be brought back.
     */
    private fun wallpaper(args: List<String>) {
        val match = com.mtwabpin.kolahal.ui.WallpaperMatch
        when (args.firstOrNull()?.lowercase()) {
            null -> info(
                if (match.isMatched(context)) "Your home screen wallpaper is in the theme's colour, and follows the theme"
                else "Your home screen wallpaper is your own, and Kolahal leaves it alone (wallpaper match puts it in the theme's colour)",
            )
            "match" -> when {
                match.isMatched(context) -> info("Your home screen wallpaper already follows the theme")
                args.getOrNull(1) != "--yes" -> {
                    info("This replaces your home screen wallpaper with this theme's colour, so it doesn't flash on the way home by gesture. It follows the theme until you set another wallpaper. Kolahal can't bring the old one back, and log.wallpaper and widgets.wallpaper will show only this colour.")
                    info("Tap to replace it", LogContext.of(LogContext.RUN to "wallpaper match --yes"))
                }
                match.set(context, match.themeColour()) -> info("Home screen wallpaper set to this theme's colour. It follows the theme until you set another wallpaper; the lock screen keeps its own")
                else -> error("This phone didn't let Kolahal change the wallpaper")
            }
            else -> error("Usage: wallpaper [match]")
        }
    }

    /** `crashes [share|clear] [n]`: the crash reports kept on this phone ([CrashReports]). */
    private fun crashes(args: List<String>) {
        val reports = com.mtwabpin.kolahal.CrashReports.list(context)
        when (args.firstOrNull()?.lowercase()) {
            null, "ls" -> {
                if (reports.isEmpty()) return info("No crash reports. If Kolahal stops unexpectedly, its report waits here until you share or clear it")
                reports.forEachIndexed { i, r ->
                    out(
                        "#${i + 1} ${r.kind.label} ${com.mtwabpin.kolahal.CrashReports.whenText(context, r.time)}\n${r.summary.take(160)}",
                        LogContext.of(LogContext.RUN to "crashes share ${r.id}"),
                    )
                }
                info("Tap one to share it · crashes clear deletes them all")
            }
            "share" -> {
                val which = args.getOrNull(1)
                val r = when {
                    which == null -> reports.firstOrNull()
                    which.toIntOrNull() != null -> reports.getOrNull(which.toInt() - 1)
                    else -> reports.firstOrNull { it.id == which }
                } ?: return error(if (reports.isEmpty()) "No crash reports" else "No crash report $which (crashes lists them)")
                startActivity(com.mtwabpin.kolahal.ui.crashReportShare(r.text()))
            }
            "clear" -> com.mtwabpin.kolahal.CrashReports.clear(context).let { n ->
                info(if (n == 1) "Deleted 1 crash report" else "Deleted $n crash reports")
            }
            else -> error("Usage: crashes [share|clear] [<n>]")
        }
    }

    /** `log only xkcd` · `log only feeds`: the log shows those lines alone, until `log all` (or ✕, Back, another command). */
    private fun logFilter(args: List<String>) {
        when (args.firstOrNull()) {
            "only" -> ui.filterLog(LogFilter.parse(args.drop(1).joinToString(" ")) ?: return error("Usage: log only <script, feed or app | commands, scripts, feeds, notifications, apps, calls, reminders>"))
            "all" -> ui.filterLog(null)
            else -> error("Usage: log only <name|kind> · log all")
        }
    }

    /** `fetch`: this phone and Kolahal at a glance ([Fetch]), lined up in the terminal font. */
    private fun fetch() {
        val (device, rows) = Fetch.facts(context)
        val at = System.currentTimeMillis()
        logs.addAll(Fetch.layout(device, rows, Fetch.columns(context, ui.logWidthPx()), conf[Keys.USER_NAME]).map { LogRepository.Pending(LogKind.OUTPUT, it, null, LogContext(LogContext.ALIGNED), at) })
    }

    /** `clear` screen · `clear history` ↑ recall · `clear all` everything (log + history). */
    private fun clear(args: List<String>) {
        when (args.firstOrNull()) {
            null, "screen" -> ui.clearScreen()
            "history" -> {
                logs.clearHistory()
                info("Command history cleared")
            }
            "all", "log" -> logs.clearAll()
            else -> error("Usage: clear [screen|history|all]")
        }
    }

    /**
     * `backup` writes today's backup file to the backup folder (asking for one first), `backup
     * every daily|weekly|off` sets automatic backups, `backup dir` picks the folder.
     */
    private fun backup(args: List<String>) {
        val auto = com.mtwabpin.kolahal.data.AutoBackup
        when (args.firstOrNull()?.lowercase()) {
            null, "now" -> if (auto.folder(context) == null) {
                info("Pick a folder for backups; the backup is written there")
                ui.askBackupFolder(thenBackUp = true)
            } else background("backup") {
                val name = auto.backUp(context)
                val every = conf[Keys.BACKUP_AUTO].takeIf { it != "off" }?.let { " · automatic: $it" } ?: " · 'backup every daily' does it on its own"
                info("Backed up: $name in ${auto.folderName(context) ?: "the backup folder"}$every")
            }
            "every", "auto" -> {
                val value = when (val v = args.getOrNull(1)?.lowercase()) { "day" -> "daily"; "week" -> "weekly"; else -> v }
                if (value == null || value !in Keys.BACKUP_AUTO.choices) return error("Usage: backup every daily|weekly|off")
                conf.set(Keys.BACKUP_AUTO, value)
                if (value == "off") return info("Automatic backups off")
                info("Backups $value to ${auto.folderName(context) ?: "a folder you pick"}, the newest ${conf[Keys.BACKUP_KEEP]} kept (backup.keep)")
                if (auto.folder(context) == null) ui.askBackupFolder(thenBackUp = false)
            }
            "dir", "folder" -> ui.askBackupFolder(thenBackUp = false)
            else -> error("Usage: backup [now|every daily|weekly|off|dir]")
        }
    }

    /** `restart` relaunches the launcher; `restart scripts` stops the :script process (stuck scripts). */
    private fun restart(args: List<String>) {
        when (args.firstOrNull()) {
            null, "launcher" -> {
                info("Restarting…")
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    context.startActivity(
                        Intent(context, com.mtwabpin.kolahal.MainActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                    android.os.Process.killProcess(android.os.Process.myPid())
                }, 300)
            }
            "scripts", "script" -> {
                val am = context.getSystemService(android.app.ActivityManager::class.java)
                val scriptProcess = am.runningAppProcesses.orEmpty().firstOrNull { it.processName.endsWith(":script") }
                if (scriptProcess == null) {
                    info("Script process isn't running")
                } else {
                    android.os.Process.killProcess(scriptProcess.pid)
                    info("Script process stopped; scheduled scripts will start it again")
                }
            }
            else -> error("Usage: restart [scripts]")
        }
    }

    /** Text form of the setup checklist (`setup text`); unchecked lines run their fix when tapped. */
    fun setupText() {
        info("Setup — tap an unchecked item to fix it, run 'setup text' again to re-check")
        SetupChecks.run(context).forEach { c ->
            val mark = when {
                c.ok -> "[x]"
                c.fix == null -> "[-]"
                else -> "[ ]"
            }
            out("$mark ${c.title} — ${c.detail}", if (c.ok || c.fix == null) LogContext.EMPTY else LogContext.of(LogContext.RUN to c.fix))
        }
    }

    private fun startSettings(action: String) = startActivity(Intent(action))

    private fun startActivity(intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun help() {
        val offered = COMMANDS.filter { it.name !in UNAVAILABLE }
        val byName = offered.associateBy { it.name }
        val placed = HashSet<String>()
        for ((section, names) in HELP_SECTIONS) {
            info(section.replaceFirstChar { it.uppercase() })
            names.mapNotNull(byName::get).forEach { spec ->
                placed += spec.name
                out(spec.usage, LogContext.of(LogContext.COMMAND to spec.name + " ", LogContext.DESC to spec.help))
            }
        }
        offered.filter { it.name !in placed }.forEach { spec ->
            out(spec.usage, LogContext.of(LogContext.COMMAND to spec.name + " ", LogContext.DESC to spec.help))
        }
        val commandScripts = scripts.all().filter { ScriptMeta.isCommand(it.code) }
        if (commandScripts.isNotEmpty()) info("Your script commands")
        commandScripts.forEach { s ->
            val desc = ScriptMeta.description(s.code) ?: "${s.lang.ext} script"
            out(ScriptMeta.usage(s.code, s.name) ?: s.name, LogContext.of(LogContext.COMMAND to s.name + " ", LogContext.SCRIPT to s.name, LogContext.DESC to desc))
        }
        info("Anything else is matched against app names · tap a line to act · long-press for more")
        info("Pipes: help | grep feed · history | tail 5 · contacts | count  (grep [-v], head, tail, sort, uniq, count)")
    }

    /** `help todo`: that command's usage and what it does, its other names, and where the guide says more. */
    private fun commandHelp(word: String) {
        val typed = word.lowercase()
        val offered = COMMANDS.filter { it.name !in UNAVAILABLE }
        fun line(spec: CommandSpec) = out(spec.usage, LogContext.of(LogContext.COMMAND to spec.name + " ", LogContext.DESC to spec.help))
        offered.firstOrNull { it.name == InstantRun.canonical(typed) }?.let { spec ->
            line(spec)
            val also = InstantRun.SYNONYMS.filterValues { it == spec.name }.keys
            if (also.isNotEmpty()) info("Also typed as ${also.joinToString(", ")}")
            info("More in the guide's command list", LogContext.of(LogContext.URL to com.mtwabpin.kolahal.ui.GUIDE_URL + "reference/commands/"))
            return
        }
        prefs.alias(typed)?.let { return info("$typed is your alias for: $it", LogContext.of(LogContext.COMMAND to "$typed ")) }
        scripts.all().firstOrNull { it.name == typed && ScriptMeta.isCommand(it.code) }?.let { s ->
            val desc = ScriptMeta.description(s.code) ?: "${s.lang.ext} script"
            return out(ScriptMeta.usage(s.code, s.name) ?: s.name, LogContext.of(LogContext.COMMAND to s.name + " ", LogContext.SCRIPT to s.name, LogContext.DESC to desc))
        }
        // Not a command: those it may be the start of, or a typo of.
        val near = offered.filter { it.name.startsWith(typed) || CommandParsing.editDistance(typed, it.name) <= (if (it.name.length <= 4) 1 else 2) }
        if (near.isEmpty()) return error("No command called '$word' · help lists them all")
        info("No command called '$word'; nearest:")
        near.take(8).forEach(::line)
    }

    companion object {
        val COMMANDS = listOf(
            CommandSpec("help", "help [<command>]", "Show this list, or just one command"),
            *com.mtwabpin.kolahal.control.ControlCommands.SPECS.toTypedArray(),
            CommandSpec("apps", "apps [work|personal|private|hidden] [-l|-a]", "App grid (-l: one per line, -a: hidden apps too)"),
            CommandSpec("open", "open <app>[:shortcut] [-w|-p|-s]", "Launch an app or one of its shortcuts"),
            CommandSpec("shortcuts", "shortcuts <app>", "An app's launcher shortcuts"),
            CommandSpec("info", "info <app> [-w]", "Open app info"),
            CommandSpec("uninstall", "uninstall <app> [-w]", "Uninstall an app"),
            CommandSpec("work", "work [on|off|status]", "Pause / resume the work profile"),
            CommandSpec("private", "private [on|off|status]", "Lock / unlock private space"),
            CommandSpec("unlocks", "unlocks [<days>]", "Unlock counts"),
            CommandSpec("fetch", "fetch", "This phone and Kolahal at a glance, fastfetch style"),
            CommandSpec("source", "source", "Open Kolahal's source code on GitHub"),
            CommandSpec("wallpaper", "wallpaper [match]", "match: the home screen's wallpaper in this theme's colour, following the theme, so it doesn't flash on the way home by gesture"),
            CommandSpec("script", "script <sub> <name>", "Subcommands: ls lib install get new edit run every at notify on off access allow deny secret set triggers mv rm cat diff dir export import"),
            CommandSpec("=", "= <expr>", "Calculator · = 15% of 80 · = 5 km in mi · = 100 usd in eur"),
            CommandSpec("lua", "lua <code>", "Evaluate Lua 5.4"),
            CommandSpec("js", "js <code>", "Evaluate JavaScript (QuickJS)"),
            CommandSpec("rss", "rss <url>", "Fetch a feed into the log once"),
            CommandSpec("feed", "feed [add <url>|ls|rm|fetch|read|all|open|notify|every|on|off] [<id>]", "RSS/Atom subscriptions with unread counts (no args: text list)"),
            CommandSpec("feeds", "feeds", "Feed manager screen (add, intervals, notifications, OPML)"),
            CommandSpec("widgets", "widgets [ls]", "Settings → Widgets: add, resize and arrange them (ls: text list)"),
            CommandSpec("widget", "widget add", "Pick a widget to add to the widgets page"),
            CommandSpec("notifs", "notifs [all|enable|mute <app>|unmute <app>|muted|log [on|off]|clear]", "Your notifications in the terminal"),
            CommandSpec("search", "search <query>", "Web search (engine: config search.engine)"),
            CommandSpec("yt", "yt <query>", "Search YouTube"),
            CommandSpec("wiki", "wiki <query>", "Search Wikipedia"),
            CommandSpec("maps", "maps <place>", "Find a place in your maps app"),
            CommandSpec("play", "play <query>", "Search the Play Store"),
            CommandSpec("usage", "usage [<days>]", "Screen time per app"),
            CommandSpec("installs", "installs [<n>]", "Recent app installs, updates and uninstalls"),
            // The Play build has no READ_CONTACTS: no name opens Android's contact picker.
            CommandSpec("call", if (com.mtwabpin.kolahal.BuildConfig.CONTACTS) "call <name|number>" else "call [<name|number>]", "Dial a contact or number"),
            CommandSpec("sms", if (com.mtwabpin.kolahal.BuildConfig.CONTACTS) "sms <name|number> [<text>]" else "sms [<name|number> [<text>]]", "Start a text message"),
            if (com.mtwabpin.kolahal.BuildConfig.CONTACTS) CommandSpec("contacts", "contacts [<query>|--allow]", "Find contacts (favourites when empty)")
            else CommandSpec("contacts", "contacts [<query>|--forget <name>]", "Pick a contact, or find one picked before"),
            CommandSpec("calls", "calls [<n>|--allow|--settings]", "Recent calls: incoming, outgoing and missed"),
            CommandSpec("url", "url <address>", "Open a link"),
            CommandSpec("history", "history", "Recent commands (tap to reuse)"),
            CommandSpec("clear", "clear [history|all]", "Clear the screen, the ↑ history, or both"),
            CommandSpec("backup", "backup [now|every <daily|weekly|off>|dir]", "Back up everything to a folder now, or every day or week"),
            CommandSpec("log", "log [only <name|kind>|all]", "Only one script's, feed's or app's lines, or one kind's"),
            CommandSpec("fresh", "fresh", "Start a clean screen now, like log.fresh; earlier lines are one tap away (not logged)"),
            CommandSpec("restart", "restart [scripts]", "Restart the launcher, or stop running scripts"),
            CommandSpec("status", "status [clear <name|all>]", "Header lines pinned by scripts"),
            CommandSpec("setup", "setup [text]", "Setup checklist page (text: print it in the log)"),
            CommandSpec("tour", "tour", "How Kolahal works, a page at a time, with a few first settings"),
            CommandSpec("notifications", "notifications [settings]", "Allow script notifications"),
            CommandSpec("usage-access", "usage-access", "Grant usage access (unlock count)"),
            CommandSpec("local-network", "local-network [--settings]", "Let scripts (@uses lan) and feeds reach your local network (Android 17+)"),
            CommandSpec("crashes", "crashes [share|clear] [<n>]", "Crash reports kept on this phone; share one to report a bug"),
            CommandSpec("battery", "battery", "Battery optimization settings"),
            CommandSpec("home", "home", "Choose the default launcher"),
            CommandSpec("lock", "lock", "Lock the screen (needs the accessibility service)"),
            CommandSpec("shade", "shade", "Pull down the notification shade"),
            CommandSpec("accessibility", "accessibility", "Enable the screen-lock service for double-tap"),
            CommandSpec("settings", "settings", "Kolahal settings screen (+ backup import/export)"),
            CommandSpec("scripts", "scripts", "Script manager screen (import files, timers)"),
            CommandSpec("system", "system", "Android system settings"),
            CommandSpec("config", "config [ls|set|get|reset] <key> <v>", "Settings (no args opens the settings page)"),
            CommandSpec("theme", "theme <name>", "Themes: green amber mono dracula solarized gruvbox gruvbox-material"),
            CommandSpec("font", "font [<name>|import|sans [off|prompt|all]]", "Terminal font: system jetbrains-mono fira-code ibm-plex-mono source-code-pro custom (no args: list); sans: the phone's font for the prompt or everything"),
            CommandSpec("date", "date [<place>...]", "Current date and time, or the time in a city, a zone, utc or an offset"),
            CommandSpec("echo", "echo <text>", "Print text"),
            CommandSpec("cal", "cal [<month>] [<year>]", "The month as a calendar, today in bold · cal dec · cal dec 2027 · cal 2027-12"),
            CommandSpec("alias", "alias [name = command]", "List or define aliases"),
            CommandSpec("unalias", "unalias <name>", "Remove an alias"),
            CommandSpec("pin", "pin <app>", "Favourite: first on an empty prompt (suggestions.idle) and starred in the app selector (pins lists them)"),
            CommandSpec("unpin", "unpin <app>", "Remove a favourite"),
            CommandSpec("hide", "hide <app> [-w]", "Hide an app from lists and search"),
            CommandSpec("unhide", "unhide <app>", "Show a hidden app again ('apps hidden')"),
            CommandSpec("rename", "rename <app> = <name>", "Custom label (empty restores)"),
            CommandSpec("todo", "todo [<text>|add|done|undo|edit|rm <n>|all|clear]", "Todo list (tap an item to tick it)"),
            CommandSpec("note", "note <text>", "Save a quick note"),
            CommandSpec("notes", "notes [grep <text>|rm <n>]", "Your notes, newest first (tap copies)"),
            CommandSpec("remind", "remind <when> <text>", "Notify later · 10m · 18:30 · tomorrow 9:00 · fri 8am · dec 3 10:00 · every mon 19:00 · rm <n>"),
            CommandSpec("reminders", "reminders", "Pending reminders"),
            CommandSpec("timer", "timer <5m|1h30m> [<label>]", "Start a timer in the clock app (no args: show timers)"),
            CommandSpec("alarm", "alarm <HH:MM> [<label>]", "Set an alarm in the clock app"),
            CommandSpec("alarms", "alarms", "Show the clock app's alarms"),
            CommandSpec("stopwatch", "stopwatch", "Open the clock app"),
            CommandSpec("update", "update [install]", "Check for updates; install only when asked"),
            CommandSpec("agenda", "agenda [<days>|--allow|--settings]", "Upcoming calendar events (tap opens one)"),
        )

        /** Every built-in command name, for alias, script-name and typo checks. */
        val COMMAND_NAMES: Set<String> = COMMANDS.map { it.name }.toSet()

        /** Names a script or an alias can't take: the commands and the other names they answer to. */
        val RESERVED_NAMES: Set<String> = COMMAND_NAMES + InstantRun.SYNONYMS.keys

        /**
         * The commands of each feature the Play build leaves out (screen lock, call log), with whether
         * this build has it.
         */
        internal val FEATURE_COMMANDS = listOf(
            com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE to setOf("lock", "accessibility"),
            com.mtwabpin.kolahal.BuildConfig.CALL_LOG to setOf("calls"),
        )

        /**
         * Commands whose feature this build leaves out: not suggested or listed in help, so the Play
         * build doesn't offer what it can't do. Typed anyway, each says why it isn't there.
         */
        val UNAVAILABLE: Set<String> = FEATURE_COMMANDS.filterNot { it.first }.flatMap { it.second }.toSet()

        /** Usage line per command name, for option completion and instant runs. */
        val COMMAND_USAGES: Map<String, String> = COMMANDS.associate { it.name to it.usage }

        /** Commands handled by [ProductivityCommands]. */
        val PRODUCTIVITY = setOf("todo", "note", "notes", "remind", "reminders", "timer", "alarm", "alarms", "stopwatch", "agenda")

        /** `help` groups; commands not listed here are shown after the groups. */
        val HELP_SECTIONS = linkedMapOf(
            "apps" to listOf("open", "shortcuts", "apps", "pin", "unpin", "info", "uninstall", "hide", "unhide", "rename", "usage", "installs"),
            "people" to listOf("call", "sms", "contacts", "calls"),
            "web" to listOf("search", "yt", "wiki", "maps", "play", "url"),
            "feeds" to listOf("feeds", "feed", "rss", "notifs"),
            "control" to com.mtwabpin.kolahal.control.ControlCommands.SPECS.map { it.name },
            "profiles" to listOf("work", "private"),
            "productivity" to listOf("todo", "note", "notes", "remind", "reminders", "timer", "alarm", "alarms", "stopwatch", "agenda"),
            "scripts" to listOf("scripts", "script", "lua", "js", "=", "status"),
            "terminal" to listOf("help", "history", "clear", "fresh", "restart", "alias", "unalias", "config", "theme", "font", "backup", "echo", "date", "cal", "update"),
            "widgets" to listOf("widgets", "widget"),
            "device" to listOf("settings", "setup", "tour", "source", "wallpaper", "unlocks", "notifications", "usage-access", "local-network", "crashes", "battery", "home", "lock", "shade", "accessibility", "system"),
        )
    }
}
