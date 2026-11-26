/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.commands.CommandCatalog.PRODUCTIVITY
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.commands.CommandsOff
import com.mtwabpin.kolahal.commands.InstantRun
import com.mtwabpin.kolahal.commands.Pipes
import com.mtwabpin.kolahal.commands.TtyCommands
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.HomeStyles
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalUi
import com.mtwabpin.kolahal.unlock.UnlockCounter
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
    private val controls by lazy { ControlCommands(context) }
    private val productivity = ProductivityCommands(context, ui)
    private val scriptCommands = ScriptCommands(context, ui, ::background)
    private val configCommands = ConfigCommands(context, ui)
    private val people = PeopleCommands(context, ui)
    private val appCommands = AppCommands(context, ::background)
    private val usageCommands by lazy { UsageCommands(context) }
    private val feedCommands = FeedCommands(context, appCommands, ui)
    private val editCommands = EditCommands(context, ui)
    private val shaderCommands by lazy { ShaderCommands(context, ui, ::background) }
    private val aliasCommands = AliasCommands(context)
    private val helpCommands = HelpCommands(context, ::off)
    private val calcCommands = CalcCommands(context, ::background)
    private val webCommands = WebCommands(context)
    private val logCommands = LogCommands(context, ui)
    private val systemCommands = SystemCommands(context, ui, ::background)
    private val allowCommands = AllowCommands(context, ui)

    /** Runs [block] outside the command's thread. What it prints still counts as the command's output. */
    private fun background(label: String, block: () -> Unit) {
        val cause = logs.currentCause
        // The command's lines wait in its batch until it ends: written now, they stay above the block's.
        logs.flushBatch()
        backgroundScope.launch {
            logs.withCause(cause) {
                runCatching(block).onFailure { error("$label: ${it.reason()}") }
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
        val secret = ScriptCommands.carriesSecret(typed) || (expand && ScriptCommands.carriesSecret(
            expandAliases(typed)
        ))
        val last = logs.last()
        val commandId = when {
            typed.lowercase() == "fresh" || secret -> null
            // The output joins the existing line.
            last != null && last.second == LogKind.COMMAND && last.third == typed -> last.first
            else -> logs.add(
                LogKind.COMMAND,
                typed,
                ctx = LogContext.of(LogContext.COMMAND to typed)
            )
        }
        // Running a command ends `log only`, because what you typed and what it prints must be visible.
        if (!ScriptScheduler.runningSentCommand && typed.substringBefore(' ')
                .lowercase() != "log"
        ) ui.filterLog(null)
        // Everything it prints, now or later (a script, a feed fetch, a download), is marked as its output.
        logs.withCause(commandId) { runLogged(typed, expand, fromApp) }
    }

    /** Writes the command's output together ([LogRepository.batched]): one write for a page of output, not one per line. */
    private fun runLogged(typed: String, expand: Boolean, fromApp: Boolean) = logs.batched {
        // Expand aliases first, so `alias hf = help | grep feed` pipes too. The `alias` command itself keeps the pipe in its definition.
        val expanded = if (expand) expandAliases(typed) else typed
        val stages = if (expanded.substringBefore(' ')
                .lowercase() == "alias"
        ) listOf(expanded) else Pipes.split(expanded)
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
        if (trimmed.startsWith("=")) return calcCommands.calc(trimmed.drop(1).trim())
        val name = trimmed.substringBefore(' ').lowercase()
        val rest = trimmed.substringAfter(' ', "").trim()
        val args = CommandParsing.tokenize(rest)
        runCatching {
            // A command turned off (commands.off) is as if it didn't exist: its name opens the app it matches.
            // Kolahal's own buttons and tap-to-run lines still run it.
            if (!fromApp && CommandsOff.isOff(name, off())) return@runCatching fallbackLaunch(
                trimmed
            )
            when (name) {
                "alias" -> aliasCommands.alias(rest)
                "unalias" -> aliasCommands.unalias(args)
                in AppCommands.NAMES -> appCommands.run(name, args)
                in UsageCommands.NAMES -> usageCommands.run(name, args)
                // `data` alone opens the mobile data panel; `data today` and `data month` show the data each app used.
                "data" if args.isNotEmpty() -> usageCommands.run(name, args)
                "help", "?" -> if (args.isEmpty()) helpCommands.help() else helpCommands.commandHelp(args.first())
                // Offered to nobody (CommandCatalog.HIDDEN): it runs only when typed in full.
                "skill.md" -> helpCommands.skill()
                "fetch", "neofetch", "fastfetch" -> systemCommands.fetch()
                // Full-screen screens over home, until a key or a tap (terminal/tty/screens). Commands only
                // in the tty style (TtyCommands.SCREENS): in the gui style the name is a plain word, as below.
                "matrix", "pipes", "sl", "clock", "top", "decode", "boot" -> if (tty()) ui.showTtyScreen(name)
                else scriptCommands.runCommand(name, args) ?: fallbackLaunch(trimmed)
                "screen" -> systemCommands.screen(args)
                "shader" -> shaderCommands.run(args)
                "shaders" -> if (args.isEmpty()) ui.openScreen(Screen.SHADERS) else shaderCommands.run(args)
                "clear", "cls" -> logCommands.clear(args)
                "backup" -> systemCommands.backup(args)
                "log" -> logCommands.logFilter(args)
                "fresh" -> ui.freshScreen()
                "scramble" -> systemCommands.scramble(args)
                "restart", "reboot" -> systemCommands.restart(args)
                "history" -> logCommands.history()
                "scripts" -> if (args.isEmpty()) ui.openScreen(Screen.SCRIPTS) else scriptCommands.script(
                    args
                )

                "script" -> scriptCommands.script(args)
                "edit" -> editCommands.run(args)
                "lua" -> calcCommands.eval(ScriptLang.LUA, rest)
                "js" -> calcCommands.eval(ScriptLang.JS, rest)
                // Created only behind the build switch, so R8 removes the updater from the Play build.
                // Play allows no updates except its own (tools/check-play-build.sh checks this).
                "update" -> if (com.mtwabpin.kolahal.BuildConfig.SELF_UPDATE) {
                    UpdateCommands(context, ::background).run(args)
                } else {
                    error("'update' isn't available in this build\nIt updates through Google Play")
                }

                "rss" -> feedCommands.rss(rest)
                "feeds" -> if (args.isEmpty()) ui.openScreen(Screen.FEEDS) else feedCommands.feeds(
                    args
                )

                "feed" -> feedCommands.feeds(args)
                "widgets", "widget" -> systemCommands.widgets(name, args)
                "notifs", "notifications" -> feedCommands.notifs(args)
                "url", "web" -> webCommands.openUrl(rest)
                "source" -> webCommands.openUrl(com.mtwabpin.kolahal.Links.SOURCE)
                "wallpaper" -> systemCommands.wallpaper(args)
                "search", "g" -> webCommands.search(rest)
                "yt", "youtube" -> webCommands.search(rest, "youtube")
                "wiki" -> webCommands.search(rest, "wikipedia")
                "maps", "map" -> if (rest.isBlank()) fallbackLaunch(trimmed) else webCommands.openUri(
                    "geo:0,0?q=" + Uri.encode(
                        rest
                    ), "Maps: $rest"
                )

                "play", "store" -> if (rest.isBlank()) fallbackLaunch(trimmed) else webCommands.openUri(
                    "market://search?q=" + Uri.encode(
                        rest
                    ), "Play Store: $rest"
                )

                "calls" -> people.calls(args)
                "call", "dial" -> people.call(rest)
                "sms", "text" -> people.sms(args)
                "contacts", "contact" -> people.listContacts(rest)
                "crashes" -> logCommands.crashes(args)
                "setup" -> if (args.firstOrNull() == "text") systemCommands.setupText() else ui.openScreen(Screen.SETUP)
                "tour" -> ui.openScreen(Screen.ONBOARDING)
                "status" -> scriptCommands.status(args)
                "allow" -> allowCommands.run(args)
                "settings", "prefs" -> ui.openScreen(Screen.SETTINGS)
                "system" -> startSettings(Settings.ACTION_SETTINGS)
                "home" -> startSettings(Settings.ACTION_HOME_SETTINGS)
                // The Play build locks through Kolahal Extras: this runs off the main thread, so it can ask.
                "lock" -> if (!com.mtwabpin.kolahal.gesture.ScreenLock.lockNow(context)) {
                    if (com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE) fix(
                        "Locking needs an accessibility service\nTap to allow Kolahal screen lock",
                        "allow lock"
                    ) else extrasNeeded("Can't lock the screen", com.mtwabpin.kolahal.commands.Accesses.LOCK)
                }

                "shade" -> com.mtwabpin.kolahal.gesture.Gestures.expandShade(context)
                "date", "time" -> if (args.isEmpty()) systemCommands.now() else systemCommands.date(args)
                "echo" -> out(rest)
                "cal" -> systemCommands.cal(rest)
                "config", "set" -> if (name == "config" && args.isEmpty()) ui.openScreen(Screen.SETTINGS) else configCommands.config(
                    args,
                    rest
                )

                "theme" -> configCommands.config(listOf("set", Keys.THEME.key) + args)
                "font" -> configCommands.font(args)
                in com.mtwabpin.kolahal.commands.ControlSpecs.NAMES -> controls.run(name, args)
                in PRODUCTIVITY -> productivity.run(name, rest)
                else -> scriptCommands.runCommand(name, args) ?: fallbackLaunch(trimmed)
            }
        }.onFailure { error("$name: ${it.reason()}") }
    }

    // ---- aliases --------------------------------------------------------------------------

    /** Expands the first word through your aliases ([Aliases.expand]). */
    private fun expandAliases(line: String): String =
        Aliases.expand(line, TtyCommands.reserved(tty())) { prefs.alias(it) }

    /**
     * What the alias [word] stands for, for the prompt to show in its place ([com.mtwabpin.kolahal.terminal.Abbreviations]),
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

    /**
     * Launches [app] for a tap on it, its suggestion or its line. When its profile is paused (work)
     * or locked (private space), it asks first whether to resume it ([TerminalUi.askResume]): a line
     * in the log is easy to miss after a tap in the app selector.
     */
    fun launch(app: AppEntry) =
        if (app.profile.quiet) ui.askResume(app) else appCommands.launch(app)

    /** The answer Resume to [TerminalUi.askResume]: resumes [app]'s profile, waits for it, then opens [app]. */
    fun resumeAndLaunch(app: AppEntry) = appCommands.resumeAndLaunch(app)

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
                info("${matches.size} matches\nTap one to launch it")
            }
        }
    }

    /** The built-in commands turned off (commands.off), as far as they are commands in the home style ([TtyCommands.off]). */
    private fun off() = TtyCommands.off(CommandsOff.names(conf[Keys.COMMANDS_OFF]), tty())

    /** Whether home is in the tty style now: the screens are commands only there ([TtyCommands]). */
    private fun tty() = HomeStyles.ttyNow(conf)

    /** Unknown input: suggests the closest command, in case the first word has a typo. */
    private fun notFound(text: String) {
        val head = text.substringBefore(' ').lowercase()
        val rest = text.substringAfter(' ', "")
        val off = off()
        // A command turned off, and no app of that name: say why it didn't run.
        if (CommandsOff.isOff(
                head,
                off
            )
        ) return error("Command not found: $text\n${InstantRun.canonical(head)} is turned off in Settings → Commands")
        val tty = tty()
        val candidates = TtyCommands.offeredNames(tty) - off + prefs.aliases().keys +
                scripts.all().filter { ScriptMeta.isCommand(it.code) && TtyCommands.offersScript(it.code, tty) }.map { it.name }
        val best = CommandParsing.nearest(head, candidates)
        if (best == null) {
            error("Command not found: $text\n'help' lists the commands", LogContext.of(LogContext.COPY to "help"))
        } else {
            val corrected = "$best $rest".trim()
            logs.add(
                LogKind.ERROR,
                "Command not found: $head\nDid you mean '$corrected'?\nTap to put it in the prompt",
                // The trailing space marks it as a line to fill in, not to run, whatever launch.instant says.
                ctx = LogContext.of(LogContext.COMMAND to "$corrected ", LogContext.COPY to corrected),
            )
        }
    }

    private fun startSettings(action: String) = startActivity(Intent(action))
}
