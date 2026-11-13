package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.Stores
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.notify.Notifier
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.Net
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.terminal.CommandCatalog.HELP_SECTIONS
import com.mtwabpin.kolahal.terminal.CommandCatalog.PRODUCTIVITY
import com.mtwabpin.kolahal.terminal.CommandCatalog.RESERVED_NAMES
import com.mtwabpin.kolahal.unlock.UnlockCounter
import com.mtwabpin.kolahal.widgets.listing
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Parses and runs commands typed at the prompt. Everything it prints goes into the persistent log
 * with a [LogContext], so the lines can still be tapped or long-pressed later.
 */
class CommandProcessor(context: Context, private val ui: TerminalUi) : CommandGroup(context) {
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

    /** Runs [block] outside the command's thread. What it prints still counts as the command's output. */
    private fun background(label: String, block: () -> Unit) {
        val cause = logs.currentCause
        backgroundScope.launch {
            logs.withCause(cause) {
                runCatching(block).onFailure { error("$label: ${it.message ?: it.javaClass.simpleName}") }
            }
        }
    }

    /**
     * Runs [line] as typed.
     * - [expand] = false runs it exactly as given. Aliases must not rewrite tap-to-run lines.
     * - [fromApp] = true: Kolahal itself sent it (a button, a header tap, a setup fix, a tap-to-run
     *   line). A command turned off ([CommandsOff]) still runs then. Typed lines, gestures and
     *   scripts keep the check.
     */
    fun run(line: String, expand: Boolean = true, fromApp: Boolean = false) {
        val typed = CommandParsing.spaces(line).trim()
        if (typed.isEmpty()) return
        // Which command lines are logged:
        // - The same command again with nothing printed in between (settings, settings…) stays one line.
        // - `fresh` is not logged at all, because it would put a line straight back on the clean screen.
        // - A line that contains a script secret's value is not logged. So the log, and the history
        //   built from it, never see the value.
        val secret = ScriptCommands.carriesSecret(typed) || (expand && ScriptCommands.carriesSecret(expandAliases(typed)))
        val last = logs.last()
        val commandId = when {
            typed.lowercase() == "fresh" || secret -> null
            // The output joins the existing line.
            last != null && last.second == LogKind.COMMAND && last.third == typed -> last.first
            else -> logs.add(LogKind.COMMAND, typed, ctx = LogContext.of(LogContext.COMMAND to typed))
        }
        // Running a command ends `log only`, because what you typed and what it prints must be visible.
        if (!ScriptScheduler.runningSentCommand && typed.substringBefore(' ').lowercase() != "log") ui.filterLog(null)
        // Everything it prints, now or later (a script, a feed fetch, a download), is marked as its output.
        logs.withCause(commandId) { runLogged(typed, expand, fromApp) }
    }

    /** Writes the command's output together ([LogRepository.batched]): one write for a page of output, not one per line. */
    private fun runLogged(typed: String, expand: Boolean, fromApp: Boolean) = logs.batched {
        // Expand aliases first, so `alias hf = help | grep feed` pipes too. The `alias` command itself keeps the pipe in its definition.
        val expanded = if (expand) expandAliases(typed) else typed
        val stages = if (expanded.substringBefore(' ').lowercase() == "alias") listOf(expanded) else Pipes.split(expanded)
        if (stages.size > 1) {
            // `cmd | grep x | head 5`: capture what the command prints right away, then filter it.
            val (_, captured) = logs.capture { execute(stages[0], fromApp) }
            Pipes.apply(captured, stages.drop(1)).fold(
                onSuccess = { logs.addAll(it) },
                onFailure = { logs.addAll(captured); error(it.message ?: "Invalid pipe") },
            )
            return@batched
        }
        execute(expanded, fromApp)
    }

    /** Runs one command whose aliases are already expanded. [fromApp]: see [run]. */
    private fun execute(trimmed: String, fromApp: Boolean) {
        // "= 6*7" is a quick calculator. It accepts any Lua expression.
        if (trimmed.startsWith("=")) return calc(trimmed.drop(1).trim())
        val name = trimmed.substringBefore(' ').lowercase()
        val rest = trimmed.substringAfter(' ', "").trim()
        val args = CommandParsing.tokenize(rest)
        runCatching {
            // A command turned off (commands.off) is as if it didn't exist: its name opens the app it matches.
            // Kolahal's own buttons and tap-to-run lines still run it.
            if (!fromApp && CommandsOff.isOff(name, off())) return@runCatching fallbackLaunch(trimmed)
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
                // Created only behind the build switch, so R8 removes the updater from the Play build.
                // Play allows no updates except its own (tools/check-play-build.sh checks this).
                "update" -> if (com.mtwabpin.kolahal.BuildConfig.SELF_UPDATE) {
                    UpdateCommands(context, ::background).run(args)
                } else {
                    error("This build updates through Google Play, so update isn't available")
                }
                "rss" -> feedCommands.rss(rest)
                "feeds" -> if (args.isEmpty()) ui.openScreen(Screen.FEEDS) else feedCommands.feeds(args)
                "feed" -> feedCommands.feeds(args)
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
                    logs.add(LogKind.ERROR, "Locking needs the Kolahal screen lock accessibility service — tap to allow it", ctx = LogContext.of(LogContext.RUN to "accessibility"))
                }
                "shade" -> com.mtwabpin.kolahal.gesture.Gestures.expandShade(context)
                "date", "time" -> if (args.isEmpty()) now() else date(args)
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

    /** Expands the first word through your aliases ([Aliases.expand]). */
    private fun expandAliases(line: String): String = Aliases.expand(line, RESERVED_NAMES) { prefs.alias(it) }

    /**
     * What the alias [word] stands for, for the prompt to show in its place ([Abbreviations]),
     * expanded as Enter would expand it. Null when:
     * - [word] isn't an alias (or is a built-in name);
     * - the line would carry a script secret, which the prompt must not show;
     * - Enter would expand the line again (an alias that leads back to itself).
     */
    fun abbreviation(word: String): String? {
        if (prefs.alias(word) == null) return null
        val line = expandAliases(word).trim()
        if (line == word || ScriptCommands.carriesSecret(line) || expandAliases(line).trim() != line) return null
        return line
    }

    private fun alias(rest: String) {
        when (val request = Aliases.parse(rest, RESERVED_NAMES)) {
            Aliases.Request.ShowAll -> {
                val all = prefs.aliases()
                if (all.isEmpty()) info("No aliases — e.g. alias m = open maps")
                all.forEach { (k, v) -> out("$k = $v", LogContext.of(LogContext.COMMAND to k)) }
            }
            is Aliases.Request.Show -> prefs.alias(request.name)?.let { out("${request.name} = $it") } ?: error("No alias '${request.name}'")
            Aliases.Request.Usage -> error("Usage: alias <name> = <command>")
            is Aliases.Request.BuiltIn -> error("'${request.name}' is a built-in command and can't be an alias")
            is Aliases.Request.Define -> {
                prefs.setAlias(request.name, request.expansion)
                info("Alias ${request.name} = ${request.expansion}")
            }
        }
    }

    private fun unalias(args: List<String>) {
        val name = args.firstOrNull() ?: return usage("unalias")
        if (prefs.alias(name) == null) return error("No alias '$name'")
        prefs.setAlias(name, null)
        info("Removed alias $name")
    }

    // ---- output helpers -------------------------------------------------------------------


    /** `cal [dec [2027]]`: prints the month as a calendar, with today in bold. Weeks start on the locale's first day. */
    private fun cal(rest: String) {
        val today = java.time.LocalDate.now()
        val month = MonthCalendar.parse(rest, today)
            ?: return error("Unknown month '$rest'. Use cal, cal dec, cal dec 2027 or cal 2027-12")
        val lines = MonthCalendar.lines(month, today) { com.mtwabpin.kolahal.data.StyledText.wrap(com.mtwabpin.kolahal.data.StyledText.Style.BOLD, it) }
        // One log line per row, in the terminal font, so the columns line up.
        lines.forEach { out(it, LogContext(LogContext.ALIGNED)) }
    }

    /** `date`: the time large, with today's date under it. Copy gives the date and time in full. */
    private fun now() {
        val now = java.time.ZonedDateTime.now()
        val clock24 = Config.get(context)[Keys.CLOCK_24H]
        val time = now.format(java.time.format.DateTimeFormatter.ofPattern(if (clock24) "HH:mm" else "h:mm a"))
        val day = now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy"))
        out(com.mtwabpin.kolahal.data.StyledText.stats(listOf(time to day)), LogContext.of(LogContext.COPY to DateFormat.getDateTimeInstance().format(Date())))
    }

    /**
     * `date tokyo new york`: prints the time in each place, one line each, and how far ahead or behind
     * it is. One place shows its time large, with the place under it.
     */
    private fun date(words: List<String>) {
        val places = WorldClock.parse(words)
            ?: return error("Unknown place '${words.joinToString(" ")}'. Use a city (tokyo, new york), a zone (Asia/Tokyo), utc, or an offset like +5:30")
        val now = java.time.Instant.now()
        val clock24 = Config.get(context)[Keys.CLOCK_24H]
        if (places.size == 1) {
            val line = WorldClock.line(places[0], now, java.time.ZoneId.systemDefault(), clock24)
            val under = listOf(line.place, line.day, line.relative).filter { it.isNotEmpty() }.joinToString(" · ")
            return out(com.mtwabpin.kolahal.data.StyledText.stats(listOf(line.time to under)), LogContext.of(LogContext.COPY to "${line.place} ${line.detail}"))
        }
        for (place in places) {
            val line = WorldClock.line(place, now, java.time.ZoneId.systemDefault(), clock24)
            out(line.place, LogContext.of(LogContext.DESC to line.detail))
        }
    }

    /** Launches [app], the same way as a tap on it or on its suggestion. */
    fun launch(app: AppEntry) = appCommands.launch(app)

    /** Treats any input that isn't a command as an app name. */
    private fun fallbackLaunch(text: String) {
        // Just after a start, with no saved list, the apps may not be read yet: wait a little rather than "not found".
        if (apps.apps.value.isEmpty()) apps.awaitLoaded(3_000)
        val ranked = apps.searchRanked(text)
        val matches = ranked.map { it.first }
        when {
            matches.isEmpty() -> notFound(text)
            matches[0].label.equals(text, ignoreCase = true) -> appCommands.launch(matches[0])
            matches.size == 1 && conf[Keys.AUTO_LAUNCH] -> appCommands.launch(matches[0])
            // Enter launches the top suggestion chip, as long as it starts with the input.
            conf[Keys.LAUNCH_TOP] && ranked[0].second <= 2 -> appCommands.launch(matches[0])
            else -> {
                appCommands.appGrid(matches.take(12))
                info("${matches.size} matches — tap one to launch")
            }
        }
    }

    /** The built-in commands turned off (commands.off). */
    private fun off() = CommandsOff.names(conf[Keys.COMMANDS_OFF])

    /** Unknown input: suggests the closest command, in case the first word has a typo. */
    private fun notFound(text: String) {
        val head = text.substringBefore(' ').lowercase()
        val rest = text.substringAfter(' ', "")
        val off = off()
        // A command turned off, and no app of that name: say why it didn't run.
        if (CommandsOff.isOff(head, off)) return error("Command not found: $text  (${InstantRun.canonical(head)} is turned off · Settings → Commands)")
        val candidates = CommandCatalog.OFFERED_NAMES - off + prefs.aliases().keys +
            scripts.all().filter { ScriptMeta.isCommand(it.code) }.map { it.name }
        val best = CommandParsing.nearest(head, candidates)
        if (best == null) {
            error("Command not found: $text  (try 'help')")
        } else {
            val corrected = "$best $rest".trim()
            logs.add(
                LogKind.ERROR,
                "Command not found: $head — did you mean '$corrected'? (tap to put it in the prompt)",
                // The trailing space marks it as a line to fill in, not to run, whatever launch.instant says.
                ctx = LogContext.of(LogContext.COMMAND to "$corrected "),
            )
        }
    }

    // ---- misc -----------------------------------------------------------------------------

    private fun unlocks(args: List<String>) {
        val days = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 30) ?: 1
        if (!unlocks.hasUsageAccess()) {
            info("Counting unlocks only while the launcher is running")
            info("Tap here to allow usage access for accurate counts", LogContext.of(LogContext.RUN to "usage-access"))
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
        // Big numbers: today's count; over several days, the average and the total too.
        val todayCount = rows.firstOrNull { it.first == "Today" }?.second ?: 0
        val big = mutableListOf(todayCount.toString() to if (todayCount == 1) "unlock today" else "unlocks today")
        if (days > 1) {
            val total = rows.sumOf { it.second }
            big += (total / rows.size.coerceAtLeast(1)).toString() to "a day on average"
            big += total.toString() to "in $days days"
        }
        out(com.mtwabpin.kolahal.data.StyledText.stats(big))
        // One day: the big number says it all. Over several, a line a day with a bar.
        if (days == 1) return
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

    private fun history() {
        val cmds = logs.commandHistory(30).reversed()
        cmds.forEach { out(it, LogContext.of(LogContext.COMMAND to it)) }
        if (cmds.isEmpty()) info("History is empty")
    }

    private fun eval(lang: ScriptLang, code: String) {
        if (code.isBlank()) {
            usage(lang.ext)
            return
        }
        if (!ScriptScheduler.eval(context, lang, code)) error("Snippet too long — use 'script new'")
    }

    private fun openUrl(raw: String) {
        if (raw.isBlank()) return usage("url")
        val url = if (raw.contains("://")) raw else "https://$raw"
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
        info("Opened $url", LogContext.of(LogContext.URL to url))
    }

    private fun search(query: String, engine: String? = null) {
        if (query.isBlank()) return usage("search")
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

    /** Opens [uri] in the app that handles it (maps, store, ...). */
    private fun openUri(uri: String, what: String) {
        startActivity(Intent(Intent.ACTION_VIEW, uri.toUri()))
        info(what, LogContext.of(LogContext.URL to uri))
    }

    // ---- calculator & conversions --------------------------------------------------------

    /** `= 2^10`, `= 5 km in mi`, `= 72 f to c`, `= 100 usd in inr`. Currency uses live rates, cached for 6h. */
    private fun calc(expr: String) {
        val conversion = Units.parse(expr)
        if (conversion != null) {
            val (amount, from, to) = conversion
            Units.convert(amount, from, to)?.let { return answer(it.substringAfter("= "), it.substringBefore(" = "), fallback = it) }
            if (Units.looksLikeCurrency(from) && Units.looksLikeCurrency(to)) {
                return background("convert") { currency(amount, from.uppercase(), to.uppercase()) }
            }
        }
        // Arithmetic is answered here at once. Anything else (Lua code) goes to Lua.
        val answer = try {
            Calc.answer(expr)
        } catch (e: ArithmeticException) {
            return error("$expr: ${e.message}")
        }
        if (answer != null) return answer(answer, expr.trim().takeIf { it.length <= ANSWER_LABEL_MAX }.orEmpty(), fallback = answer)
        eval(ScriptLang.LUA, expr)
    }

    /**
     * A calculator's [result], large, with [of] (what it answers) under it. Copy gives [copy].
     * A result too long for big type stays a line of text, [fallback], so it doesn't wrap.
     */
    private fun answer(result: String, of: String, fallback: String, copy: String = result) {
        val ctx = LogContext.of(LogContext.COPY to copy)
        if (result.length > ANSWER_MAX) out(fallback, ctx) else out(com.mtwabpin.kolahal.data.StyledText.stats(listOf(result to of)), ctx)
    }

    private fun currency(amount: Double, from: String, to: String) {
        val cache = KvStore(context, Stores.FX)
        val cached = cache.get(from)?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() }
        val fresh = (cached?.get("at") as? Number)?.toLong()?.let { System.currentTimeMillis() - it < 6 * 3_600_000L } == true
        val rates = if (fresh) {
            cached["rates"] as? Map<*, *>
        } else {
            val body = Json.parse(Net.get("https://open.er-api.com/v6/latest/$from")) as? Map<*, *>
                ?: throw IllegalStateException("invalid response")
            if (body["result"] != "success") throw IllegalStateException("unknown currency $from")
            (body["rates"] as? Map<*, *>)?.also {
                cache.set(from, Json.stringify(mapOf("at" to System.currentTimeMillis(), "rates" to it)))
            }
        }
        val rate = (rates?.get(to) as? Number)?.toDouble() ?: throw IllegalStateException("unknown currency $to")
        val result = "${Units.format(amount * rate)} $to"
        answer(result, "${Units.format(amount)} $from", fallback = "${Units.format(amount)} $from = $result", copy = Units.format(amount * rate))
    }

    // ---- widgets ----------------------------------------------------------------------------

    /**
     * - `widgets ls` prints the placed widgets. A tap opens the widgets page.
     * - `widget add` opens the picker.
     */
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
            else -> usage("widgets", "widget")
        }
    }

    /** Asks for the runtime permission when possible. Otherwise (before Android 13, or turned off) opens settings. */
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

    /**
     * - `wallpaper` says whether the home screen wallpaper is Kolahal's.
     * - `wallpaper match` sets Kolahal's wallpaper ([com.mtwabpin.kolahal.ui.WallpaperMatch]).
     *   It asks first, because Android doesn't let an app read the wallpaper it replaces, so the
     *   old one can't be restored.
     */
    private fun wallpaper(args: List<String>) {
        val match = com.mtwabpin.kolahal.ui.WallpaperMatch
        when (args.firstOrNull()?.lowercase()) {
            null -> info(
                if (match.isMatched(context)) "Your home screen wallpaper is in the theme's colour, and follows the theme"
                else "Your home screen wallpaper is your own; Kolahal doesn't change it (wallpaper match sets it to the theme's colour)",
            )
            "match" -> when {
                match.isMatched(context) -> info("Your home screen wallpaper already follows the theme")
                args.getOrNull(1) != "--yes" -> {
                    info("This replaces your home screen wallpaper with this theme's colour, so it doesn't flash when you swipe home. It follows the theme until you set another wallpaper. Kolahal can't restore the old one, and log.wallpaper and widgets.wallpaper will show only this colour.")
                    info("Tap to replace it", LogContext.of(LogContext.RUN to "wallpaper match --yes"))
                }
                match.set(context, match.themeColour()) -> info("Home screen wallpaper set to this theme's colour. It follows the theme until you set another wallpaper; the lock screen wallpaper isn't changed")
                else -> error("Couldn't change the wallpaper on this phone")
            }
            else -> usage("wallpaper")
        }
    }

    /** `crashes [share|clear] [n]`: manages the crash reports kept on this phone ([com.mtwabpin.kolahal.CrashReports]). */
    private fun crashes(args: List<String>) {
        val reports = com.mtwabpin.kolahal.CrashReports.list(context)
        when (args.firstOrNull()?.lowercase()) {
            null, "ls" -> {
                if (reports.isEmpty()) return info("No crash reports. If Kolahal stops unexpectedly, a report is kept here until you share or clear it")
                reports.forEachIndexed { i, r ->
                    out(
                        "#${i + 1} ${r.kind.label} ${com.mtwabpin.kolahal.CrashReports.whenText(context, r.time)}\n${r.summary.take(160)}",
                        LogContext(mapOf(LogContext.RUN to "crashes share ${r.id}") + LogContext.ITEM),
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
            else -> usage("crashes")
        }
    }

    /**
     * `log only xkcd` · `log only feeds`: the log shows only those lines. This lasts until `log all`,
     * ✕, Back, or another command.
     */
    private fun logFilter(args: List<String>) {
        when (args.firstOrNull()) {
            "only" -> ui.filterLog(LogFilter.parse(args.drop(1).joinToString(" ")) ?: return error("Usage: log only <script, feed or app | commands, scripts, feeds, notifications, apps, calls, reminders, starred>"))
            "all" -> ui.filterLog(null)
            else -> usage("log")
        }
    }

    /** `fetch`: a summary of this phone and Kolahal ([Fetch]), aligned in the terminal font. */
    private fun fetch() {
        val (device, rows) = Fetch.facts(context)
        val at = System.currentTimeMillis()
        logs.addAll(Fetch.layout(device, rows, Fetch.columns(context, ui.logWidthPx()), conf[Keys.USER_NAME]).map { LogRepository.Pending(LogKind.OUTPUT, it, null, LogContext(LogContext.ALIGNED), at) })
    }

    /**
     * - `clear` clears the screen.
     * - `clear history` clears the ↑ recall history.
     * - `clear all` clears everything (log and history).
     */
    private fun clear(args: List<String>) {
        when (args.firstOrNull()) {
            null, "screen" -> ui.clearScreen()
            "history" -> {
                logs.clearHistory()
                info("Command history cleared")
            }
            "all", "log" -> logs.clearAll()
            else -> usage("clear")
        }
    }

    /**
     * - `backup` writes today's backup file to the backup folder. If no folder is set, it asks for one first.
     * - `backup every daily|weekly|off` sets automatic backups.
     * - `backup dir` picks the folder.
     */
    private fun backup(args: List<String>) {
        val auto = com.mtwabpin.kolahal.data.AutoBackup
        when (args.firstOrNull()?.lowercase()) {
            null, "now" -> if (auto.folder(context) == null) {
                info("Pick a folder for backups; the backup is written there")
                ui.askBackupFolder(thenBackUp = true)
            } else background("backup") {
                val name = auto.backUp(context)
                val every = conf[Keys.BACKUP_AUTO].takeIf { it != "off" }?.let { " · automatic: $it" } ?: " · 'backup every daily' automates it"
                info("Backed up: $name in ${auto.folderName(context) ?: "the backup folder"}$every")
            }
            "every", "auto" -> {
                val value = when (val v = args.getOrNull(1)?.lowercase()) { "day" -> "daily"; "week" -> "weekly"; else -> v }
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
    private fun restart(args: List<String>) {
        when (args.firstOrNull()) {
            null, "launcher" -> {
                info("Restarting…")
                com.mtwabpin.kolahal.LauncherApp.restart(context, afterMs = 300)
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
            out("$mark ${c.title} — ${c.detail}", LogContext(LogContext.ITEM + if (c.ok || c.fix == null) emptyMap() else mapOf(LogContext.RUN to c.fix)))
        }
    }

    private fun startSettings(action: String) = startActivity(Intent(action))

    /**
     * Prints a command's row in `help`: its name, in a column as wide as [column] (the longest name
     * in the list), what it does, and its arguments muted under that. A tap puts its name in the prompt.
     */
    private fun helpLine(spec: CommandSpec, column: String = spec.name) = out(
        spec.name,
        LogContext.of(
            LogContext.COMMAND to spec.name + " ",
            LogContext.DESC to spec.short,
            LogContext.NAME_COL to column,
            LogContext.ARGS to spec.args.ifEmpty { null },
        ),
    )

    /** Prints a script command's row in `help`, in the same form as a built-in's: its @desc, and its @usage under it. */
    private fun helpLine(s: Script, column: String = s.name) {
        val desc = ScriptMeta.description(s.code) ?: "${s.lang.ext} script"
        val args = ScriptMeta.usage(s.code, s.name)?.removePrefix(s.name)?.trim()?.ifEmpty { null }
        out(
            s.name,
            LogContext.of(
                LogContext.COMMAND to s.name + " ",
                LogContext.SCRIPT to s.name,
                LogContext.DESC to desc,
                LogContext.NAME_COL to column,
                LogContext.ARGS to args,
            ),
        )
    }

    /** A section's heading in `help`: its name, bold in the accent colour, and how many commands it has. */
    private fun helpHeading(name: String, count: Int) = info(
        StyledText.colour(StyledText.Colour.ACCENT, StyledText.wrap(StyledText.Style.BOLD, name)) + StyledText.colour(StyledText.Colour.MUTED, " · $count"),
        LogContext(LogContext.ITEM),
    )

    /**
     * `help`: every command, in sections, one row each (name, what it does, its arguments).
     * Each built-in command is in a section of [HELP_SECTIONS]; the script commands come last.
     */
    private fun help() {
        val off = off()
        val offered = CommandCatalog.OFFERED.filter { it.name !in off }
        val byName = offered.associateBy { it.name }
        val commandScripts = scripts.all().filter { ScriptMeta.isCommand(it.code) }
        // One column for the whole list, so the descriptions line up.
        val column = (offered.map { it.name } + commandScripts.map { it.name }).maxBy { it.length }
        val placed = HashSet<String>()
        for ((section, names) in HELP_SECTIONS) {
            val specs = names.mapNotNull(byName::get)
            if (specs.isEmpty()) continue
            helpHeading(section.replaceFirstChar { it.uppercase() }, specs.size)
            specs.forEach { spec ->
                placed += spec.name
                helpLine(spec, column)
            }
        }
        // None should be left, but a command missing from HELP_SECTIONS still shows.
        offered.filter { it.name !in placed }.forEach { helpLine(it, column) }
        if (commandScripts.isNotEmpty()) helpHeading("Your script commands", commandScripts.size)
        commandScripts.forEach { helpLine(it, column) }
        val muted = { text: String -> StyledText.colour(StyledText.Colour.MUTED, text) }
        val key = { text: String -> StyledText.tag(StyledText.Colour.TEXT, text) }
        info(muted("Anything else opens the app it matches"), LogContext(LogContext.ITEM))
        if (off.isNotEmpty()) info(muted("Turned off: ") + CommandsOff.CHOICES.filter { it in off }.joinToString(", ") + muted(" · Settings → Commands"), LogContext(LogContext.ITEM))
        info(key("tap") + muted(" a line to use it · ") + key("long-press") + muted(" for more"))
        // No-break spaces keep the example on one line. A line may still break after "|" there; the word joiner stops it.
        info(muted("Pipes: ") + Pipes.SHOWN.joinToString(" ") { key(it) } + muted(" · help\u00A0|\u2060\u00A0grep\u00A0feed"))
    }

    /**
     * `help todo`: that command in full. Its name large, what it does, then each way to use it
     * ([CommandSpec.uses], or its usage line), its other names, and a link to more in the guide.
     */
    private fun commandHelp(word: String) {
        val typed = word.lowercase()
        val off = off()
        if (CommandsOff.isOff(typed, off)) return info("${InstantRun.canonical(typed)} is turned off: its name opens the app it matches · Settings → Commands turns it on")
        val offered = CommandCatalog.OFFERED.filter { it.name !in off }
        offered.firstOrNull { it.name == InstantRun.canonical(typed) }?.let { spec ->
            out(StyledText.stats(listOf(spec.name to "")), LogContext.of(LogContext.COMMAND to spec.name + " "))
            out(spec.help)
            info(StyledText.colour(StyledText.Colour.MUTED, "Use"), LogContext(LogContext.ITEM))
            if (spec.uses.isEmpty()) {
                out(StyledText.wrap(StyledText.Style.BOLD, spec.usage), LogContext.of(LogContext.COMMAND to spec.name + " "))
            } else {
                val column = spec.uses.maxBy { it.first.length }.first
                spec.uses.forEach { (use, does) ->
                    out(
                        use,
                        LogContext.of(
                            LogContext.COMMAND to useCommand(use),
                            LogContext.DESC to StyledText.colour(StyledText.Colour.MUTED, does),
                            LogContext.NAME_COL to column,
                        ),
                    )
                }
            }
            val also = spec.aka
            if (also.isNotEmpty()) info("Also known as ${also.joinToString(", ")}", LogContext(LogContext.ITEM))
            info("More in the guide's command list", LogContext.of(LogContext.URL to com.mtwabpin.kolahal.ui.GUIDE_URL + "reference/commands/") + if (also.isEmpty()) LogContext.ITEM else emptyMap())
            return
        }
        prefs.alias(typed)?.let { return info("$typed is your alias for: $it", LogContext.of(LogContext.COMMAND to "$typed ")) }
        scripts.all().firstOrNull { it.name == typed && ScriptMeta.isCommand(it.code) }?.let { return helpLine(it) }
        // Not a command. Show the commands it may be the start of, or a typo of.
        val near = offered.filter { it.name.startsWith(typed) || CommandParsing.isNear(typed, it.name) }
        if (near.isEmpty()) return error("No command called '$word' · help lists them all")
        info("No command called '$word'; nearest:")
        near.take(8).let { shown -> shown.forEach { helpLine(it, shown.maxBy { s -> s.name.length }.name) } }
    }
}

/**
 * What a tap on a use in `help <command>` puts in the prompt: its words up to the first one to fill
 * in or choose. `todo done <n>` gives "todo done ", `script on|off <name>` gives "script ".
 */
internal fun useCommand(use: String): String =
    use.split(" ").takeWhile { w -> w.none { it in "<[|" } }.joinToString(" ") + " "

/** A calculator's result longer than this stays a line of text: in big type it would wrap. */
internal const val ANSWER_MAX = 16

/** An expression longer than this isn't shown under its result: it's on the command line above. */
private const val ANSWER_LABEL_MAX = 32
