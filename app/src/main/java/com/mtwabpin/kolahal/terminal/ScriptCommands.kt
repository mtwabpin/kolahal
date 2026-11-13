package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.commands.CommandParsing
import android.content.Context
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.shortTime
import com.mtwabpin.kolahal.data.StatusLines
import com.mtwabpin.kolahal.notify.Notifier
import com.mtwabpin.kolahal.script.LineDiff
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptFolder
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptNames
import com.mtwabpin.kolahal.script.ScriptOptions
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.script.SecretStore

/**
 * The `script …` and `status` commands, and `@command` scripts run by their name.
 * They live outside [CommandProcessor], so the dispatcher needs only one line per command.
 */
class ScriptCommands(
    context: Context,
    private val ui: TerminalUi,
    /** Runs slow work (folder sync) outside the serial command queue. Failures are logged. */
    private val background: (label: String, block: () -> Unit) -> Unit,
) : CommandGroup(context) {
    private val scripts = ScriptRepository.get(context)
    private val folder = ScriptFolder(context)
    private val changes = ScriptChanges(context)


    /** Runs the `@command` script named [name]. Returns null if there is no such script. */
    fun runCommand(name: String, args: List<String>): Unit? {
        val script = scripts.byName(name)?.takeIf { ScriptMeta.isCommand(it.code) } ?: return null
        // A script that is off (for example, an import not reviewed yet) doesn't run by its name.
        if (!script.enabled) {
            fix("${script.name} is off — tap to turn it on", "script on ${script.name}")
            return Unit
        }
        ScriptScheduler.runNow(context, script, args)
        return Unit
    }

    /**
     * - `status` lists the scripts' status lines and marks the pinned ones.
     * - `status clear <name|all>` removes them.
     */
    fun status(args: List<String>) {
        val all = StatusLines.all(context)
        when (args.firstOrNull()) {
            null, "ls" -> {
                // Only lines of scripts that are on. The pinned ones show at the top of home.
                val on = scripts.all().filter { it.enabled }.associateBy { it.name }
                val lines = all.filter { it.name in on }
                if (lines.isEmpty()) return info("No status lines — scripts set them with status(text)")
                lines.forEach { l ->
                    val pinned = on.getValue(l.name).grants.allows(ScriptAccess.HEADER)
                    // Pinned ones get a tag, and a line with a bar (status(text, { progress })) shows it, in its colour.
                    val bar = l.progress?.let { LogContext.bar(it.toDouble(), l.colour?.role) }.orEmpty()
                    out("${l.name} ▸ ${l.text}" + if (pinned) " " + StyledText.tag(StyledText.Colour.ACCENT, "pinned") else "", LogContext.of(LogContext.SCRIPT to l.name) + bar)
                }
                if (lines.none { on.getValue(it.name).grants.allows(ScriptAccess.HEADER) }) info("None pinned — script pin <name>, or long-press one, shows it at the top")
            }
            "clear" -> {
                val target = args.getOrNull(1) ?: "all"
                (if (target == "all") all.map { it.name } else listOf(target)).forEach { StatusLines.clear(context, it) }
                logs.onStatusChanged()
                info("Cleared status: $target")
            }
            else -> usage("status")
        }
    }

    fun script(args: List<String>) {
        val sub = args.firstOrNull() ?: "ls"
        val target = args.getOrNull(1)
        when (sub) {
            "ls", "list" -> listScripts()
            "triggers", "events" -> listTriggers()
            "dir", "folder" -> {
                folder.uri?.let { info("Script folder: ${it.lastPathSegment}") }
                ui.pickScriptFolder()
            }
            "lib", "library" -> {
                val lib = ScriptLibrary(context)
                val installed = scripts.all()
                info("Script library — 'script install <name>', or browse it in 'scripts'")
                lib.entries.forEach { e ->
                    val state = when (lib.state(e, installed)) {
                        ScriptLibrary.State.NOT_INSTALLED -> ""
                        ScriptLibrary.State.INSTALLED -> " " + StyledText.tag(StyledText.Colour.GOOD, "installed")
                        ScriptLibrary.State.MODIFIED -> " " + StyledText.tag(StyledText.Colour.MUTED, "edited")
                        ScriptLibrary.State.UPDATE_AVAILABLE -> " " + StyledText.tag(StyledText.Colour.WARN, "update")
                    }
                    // An item each: the description beside it wraps.
                    out("${e.name}.${e.lang.ext}$state", LogContext.of(LogContext.COMMAND to "script install ${e.name}", LogContext.DESC to e.description) + LogContext.ITEM)
                }
            }
            "install" -> {
                val lib = ScriptLibrary(context)
                val entry = lib.entries.firstOrNull { it.name == target?.lowercase()?.substringBefore('.') }
                    ?: return error("No library script '${target.orEmpty()}' — 'script lib' lists them")
                // Updating an unedited copy is safe. A copy with your changes (or your own script with
                // that name) is replaced only when you ask for it.
                if ("--replace" !in args && lib.state(entry, scripts.all()) == ScriptLibrary.State.MODIFIED) {
                    return error(
                        "Your ${entry.name}.${entry.lang.ext} differs from the library version: " +
                            "'script install ${entry.name} --replace' replaces it (export it first to keep a copy)"
                    )
                }
                val s = changes.install(lib, entry)
                info("Installed ${s.name}.${s.lang.ext}" + (if (entry.command) " — run it as '${s.name}'" else "") +
                    (if (s.intervalMin > 0) " · every ${CommandParsing.formatInterval(s.intervalMin)}" else ""), scriptCtx(s))
            }
            // Folder sync can be slow on SAF providers, so run it outside the serial command queue.
            "export" -> background("export") { info("Exported ${folder.export()} scripts + kolahal.json") }
            "import" -> background("import") {
                val r = folder.import()
                info("Imported: ${r.created} new, ${r.updated} updated" + if (r.skipped.isNotEmpty()) ", skipped ${r.skipped.joinToString()}" else "")
            }
            "new" -> {
                val (name, lang) = parseName(target ?: return error("Usage: script new <name>[.lua|.js] [lua|js] [--cmd]"))
                if (!ScriptNames.NAME.matches(name)) return error("Script names: lowercase letters, digits, - and _ only")
                if (name in CommandCatalog.RESERVED_NAMES) return error("'$name' is a built-in command, pick another name")
                val asCommand = "--cmd" in args || "cmd" in args
                val chosen = args.drop(2).firstNotNullOfOrNull(ScriptLang::of) ?: lang ?: ScriptLang.LUA
                if (scripts.byName(name) != null) return error("Script '$name' already exists — 'script edit $name'")
                val script = changes.create(name, chosen, asCommand)
                info("Created ${script.name}.${script.lang.ext}", scriptCtx(script))
                ui.openEditor(script)
            }
            "edit" -> withScript(target) { ui.openEditor(it) }
            // The words after the script name are its args, as when it runs by its name.
            "run" -> withScript(target) { info(runNowWithText(context, it, args.drop(2)), scriptCtx(it)) }
            "every" -> withScript(target) { s ->
                val minutes = CommandParsing.parseInterval(args.getOrNull(2))
                    ?: return error("Usage: script every <name> <15m|30m|2h|1d|off>")
                val effective = changes.interval(s, minutes)
                info(
                    if (effective == 0) "${s.name}: timer off" else "${s.name}: runs every ${CommandParsing.formatInterval(effective)}" +
                        if (minutes in 1 until ScriptScheduler.MIN_INTERVAL_MIN) " (Android minimum is 15m)" else "",
                    scriptCtx(s),
                )
            }
            "at" -> withScript(target) { s ->
                val times = when (val read = CommandParsing.dailyTimes(CommandParsing.clockWords(args.drop(2)))) {
                    null -> return error("Usage: script at <name> <HH:mm[,HH:mm…]|off>")
                    is CommandParsing.DailyTimes.Bad -> return error("Invalid time '${read.word}' — use HH:mm")
                    is CommandParsing.DailyTimes.Times -> read.times
                }
                changes.times(s, times)
                info(if (times.isEmpty()) "${s.name}: no daily times" else "${s.name}: daily at ${times.joinToString(", ")}", scriptCtx(s))
            }
            "notify" -> withScript(target) { s ->
                val on = when (args.getOrNull(2)) {
                    "on" -> true
                    "off" -> false
                    else -> return error("Usage: script notify <name> on|off")
                }
                scripts.setNotify(s.id, on)
                info("${s.name}: notifications ${if (on) "on" else "off"}", scriptCtx(s))
                if (on && !Notifier.canPost(context)) ui.requestNotificationPermission()
            }
            "on", "off", "enable", "disable" -> withScript(target) { s ->
                val enabled = sub == "on" || sub == "enable"
                // You typed this, so it also allows what the script declares. The next line says what that is.
                changes.enabled(s, enabled)
                info("${s.name}: ${if (enabled) "turned on" else "turned off"}", scriptCtx(s))
                // Switching a script on allows what it declares that nobody has decided yet. Say what it may use.
                val grants = scripts.byId(s.id)?.grants
                val uses = grants?.declared.orEmpty().filter { grants?.allows(it) == true }
                if (enabled && uses.isNotEmpty()) {
                    info("${s.name} can use: ${uses.joinToString()} — 'script deny ${s.name} <what>' turns one off", scriptCtx(s))
                }
                if (enabled) scripts.byId(s.id)?.let { ScriptAccess.askForSecrets(context, it) }
            }
            // This uses the network, so run it outside the serial command queue.
            "get" -> background("script get") { getShared(args.drop(1)) }
            "secret", "secrets" -> withScript(target) { s -> secret(s, args.drop(2)) }
            "set", "option", "options" -> withScript(target) { s -> option(s, args.drop(2)) }
            "access" -> withScript(target) { listAccess(it) }
            "pin", "unpin" -> withScript(target) { s ->
                if (!ScriptMeta.setsStatus(s.code)) return error("${s.name} has no status line to pin: it never calls status()")
                changes.access(s, ScriptAccess.HEADER, sub == "pin")
                info(if (sub == "pin") "${s.name}: pinned to the top" else "${s.name}: unpinned", scriptCtx(s))
            }
            "allow", "deny" -> withScript(target) { s ->
                val items = args.drop(2).map { it.lowercase() }
                if (items.isEmpty()) return error("Usage: script $sub <name> <what…> — 'script access ${s.name}' lists them")
                setAccess(s, items, sub == "allow")
            }
            "rm", "delete" -> withScript(target) { s ->
                scripts.delete(s.id)
                logs.onStatusChanged()
                info("Deleted ${s.name}")
            }
            "mv", "rename" -> withScript(target) { s ->
                val to = args.getOrNull(2)?.lowercase()?.removeSuffix(".${s.lang.ext}")
                    ?: return error("Usage: script mv <name> <new-name>")
                if (to == s.name) return info("${s.name} already has that name", scriptCtx(s))
                changes.rename(s, to)?.let { return error(it) }
                val renamed = scripts.byId(s.id) ?: return
                info(
                    "Renamed ${s.name}.${s.lang.ext} to ${renamed.name}.${renamed.lang.ext}" +
                        if (ScriptMeta.isCommand(renamed.code)) " — run it as '${renamed.name}'" else "",
                    scriptCtx(renamed),
                )
            }
            "cat", "show" -> withScript(target) { s ->
                // "Copy text" on the first line copies the whole script. Only the first line has it:
                // on every line, each row would be as big as the code.
                val at = System.currentTimeMillis()
                logs.addAll(s.code.lines().mapIndexed { i, l ->
                    val ctx = if (i == 0) LogContext.of(LogContext.COPY to s.code) + LogContext.ALIGNED else LogContext(LogContext.ALIGNED)
                    LogRepository.Pending(LogKind.OUTPUT, "${(i + 1).toString().padStart(3)}  $l", null, ctx, at)
                })
            }
            "diff" -> withScript(target) { s -> diff(s) }
            // List every subcommand that completion offers, not the catalog's short `script <sub> <name>`.
            else -> error("Usage: script [${SUBCOMMANDS.joinToString("|")}] <name>")
        }
    }

    /**
     * `script get [<name>] [--refresh] [--replace]`:
     * - without a name, lists the scripts shared in kolahal-scripts;
     * - with a name, installs or updates that script. A new script that asks for nothing
     *   ([ScriptAccess.asksFor]: the internet, or anything with `@uses`) is switched on. One that
     *   asks is installed off, with a line naming what it asks for: a tap switches it on and allows
     *   that, as `script on` does. You see the access before anything is allowed.
     */
    private fun getShared(args: List<String>) {
        val catalog = ScriptCatalog(context)
        val refresh = "--refresh" in args
        val replace = "--replace" in args
        val name = args.firstOrNull { !it.startsWith("--") }?.lowercase()?.substringBefore('.')
        val entries = catalog.entries(refresh)
        if (name == null) {
            val installed = scripts.all()
            info("Shared scripts from ${ScriptCatalog.SOURCE} — 'script get <name>' installs one")
            if (entries.isEmpty()) info("None yet")
            entries.forEach { e ->
                val state = when (catalog.state(e, installed)) {
                    ScriptCatalog.State.NOT_INSTALLED -> ""
                    ScriptCatalog.State.INSTALLED -> " " + StyledText.tag(StyledText.Colour.GOOD, "installed")
                    ScriptCatalog.State.UPDATE_AVAILABLE -> " " + StyledText.tag(StyledText.Colour.WARN, "update")
                    ScriptCatalog.State.MODIFIED -> " " + StyledText.tag(StyledText.Colour.MUTED, "edited")
                    ScriptCatalog.State.NAME_TAKEN -> " " + StyledText.tag(StyledText.Colour.BAD, "name taken")
                    ScriptCatalog.State.NEEDS_NEWER -> " " + StyledText.tag(StyledText.Colour.BAD, "needs Kolahal ${e.requires}")
                }
                val desc = listOfNotNull(
                    e.title, e.version.ifBlank { null }?.let { "v$it" }, e.author.ifBlank { null }?.let { "by $it" }, e.desc.ifBlank { null },
                )
                out("${e.name}.${e.lang.ext}$state", LogContext.of(LogContext.COMMAND to "script get ${e.name}", LogContext.DESC to desc.joinToString(" · ")) + LogContext.ITEM)
            }
            return
        }
        val entry = entries.firstOrNull { it.name == name }
            ?: (if (refresh) null else catalog.entries(refresh = true).firstOrNull { it.name == name })
            ?: return error("No shared script '$name' — 'script get' lists them")
        when (catalog.state(entry, scripts.all())) {
            ScriptCatalog.State.NEEDS_NEWER -> return error("${entry.name} needs Kolahal ${entry.requires} or newer")
            ScriptCatalog.State.NAME_TAKEN -> return error(ScriptNames.clash(context, entry.name) ?: "You have your own script named ${entry.name}")
            ScriptCatalog.State.INSTALLED -> return info("${entry.name} is installed and up to date")
            ScriptCatalog.State.MODIFIED -> if (!replace) {
                return error("You edited ${entry.name}: 'script get ${entry.name} --replace' replaces it (export it first to keep a copy)")
            }
            else -> Unit
        }
        val update = scripts.byName(entry.name) != null
        val code = catalog.download(entry)
        val asks = ScriptAccess.asksFor(code)
        val s = catalog.install(entry, code, replace = replace, switchOn = asks.isEmpty())
        if (update) {
            info("Updated ${s.name}.${s.lang.ext} to ${entry.version}", scriptCtx(s))
            return
        }
        val timer = listOfNotNull(
            s.intervalMin.takeIf { it > 0 }?.let { "every ${CommandParsing.formatInterval(it)}" },
            s.atTimes.takeIf { it.isNotEmpty() }?.let { "daily at ${it.joinToString(", ")}" },
        )
        val installed = "Installed ${s.name}.${s.lang.ext} from ${ScriptCatalog.SOURCE}" + timer.joinToString("") { " · $it" }
        if (s.enabled) {
            info(installed, scriptCtx(s))
            return
        }
        info("$installed · off", scriptCtx(s))
        info(
            "${s.name} asks to use: ${asks.joinToString()} — tap to switch it on and allow that, or 'script on ${s.name}'",
            LogContext.of(LogContext.SCRIPT to s.name, LogContext.RUN to "script on ${s.name}"),
        )
    }

    /**
     * - `script secret <name>` lists the script's `@secret` names.
     * - `script secret <name> <NAME>` opens a masked box for the value.
     * - `--clear` forgets the value.
     *
     * A value typed on the line also works. That line isn't logged ([carriesSecret]), but the
     * keyboard may have seen it, so the box is the better way.
     */
    private fun secret(s: Script, args: List<String>) {
        val declared = ScriptMeta.secrets(s.code)
        val store = SecretStore(context)
        val name = args.firstOrNull()
        if (name == null) {
            if (declared.isEmpty()) return info("${s.name} declares no @secret", scriptCtx(s))
            info("${s.name} — 'script secret ${s.name} <NAME>' sets one", scriptCtx(s))
            declared.forEach { d ->
                out(
                    d.name.padEnd(16) + when {
                        store.isSet(s.name, d) -> StyledText.tag(StyledText.Colour.GOOD, "set")
                        store.withoutSites(s.name, d) -> StyledText.tag(StyledText.Colour.WARN, "set it again")
                        store.hostsChanged(s.name, d) -> StyledText.tag(StyledText.Colour.WARN, "set for other sites")
                        else -> StyledText.tag(StyledText.Colour.MUTED, "not set")
                    },
                    LogContext.of(
                        LogContext.DESC to listOf(d.desc, "sent only to ${d.hosts.joinToString()}").filter { it.isNotBlank() }.joinToString(" · "),
                        LogContext.COMMAND to "script secret ${s.name} ${d.name}",
                    ) + LogContext.ALIGNED,
                )
            }
            return
        }
        val d = declared.firstOrNull { it.name == name }
            ?: return error("${s.name} has no @secret $name" + if (declared.isEmpty()) "" else " — it has: ${declared.joinToString(" ") { it.name }}")
        val rest = args.drop(1)
        when {
            rest.isEmpty() -> ui.askSecret(s, d.name)
            rest == listOf("--clear") -> {
                store.set(s.name, d.name, null, d.hosts)
                info("${s.name}: ${d.name} cleared", scriptCtx(s))
            }
            rest.joinToString(" ").length < SecretStore.MIN_LENGTH -> error("${d.name} must be at least ${SecretStore.MIN_LENGTH} characters")
            else -> {
                store.set(s.name, d.name, rest.joinToString(" "), d.hosts)
                info("${s.name}: ${d.name} set (that line isn't logged; next time 'script secret ${s.name} ${d.name}' opens a box the keyboard doesn't learn from)", scriptCtx(s))
            }
        }
    }

    /** `script set <name> [<option> [<value…>|--reset]]`: lists the script's `@option`s and their values, or changes one. */
    private fun option(s: Script, args: List<String>) {
        val declared = ScriptMeta.options(s.code)
        val store = ScriptOptions(context)
        fun shown(value: String) = value.ifEmpty { "\"\"" }
        val name = args.firstOrNull()
        if (name == null) {
            if (declared.isEmpty()) return info("${s.name} has no @option", scriptCtx(s))
            info("${s.name} — 'script set ${s.name} <option> <value>' changes one", scriptCtx(s))
            val values = store.values(s.name, declared)
            val changed = store.changed(s.name, declared)
            declared.forEach { o ->
                out(
                    o.name.padEnd(16) + shown(values.getValue(o.name)) + if (o.name in changed) "" else " (default)",
                    LogContext.of(
                        LogContext.DESC to listOf(o.desc, o.choices.joinToString("|")).filter { it.isNotBlank() }.joinToString(" · "),
                        LogContext.COMMAND to "script set ${s.name} ${o.name} ",
                    ) + LogContext.ALIGNED,
                )
            }
            return
        }
        val o = declared.firstOrNull { it.name == name }
            ?: return error("${s.name} has no @option $name" + if (declared.isEmpty()) "" else " — it has: ${declared.joinToString(" ") { it.name }}")
        val rest = args.drop(1)
        when {
            rest.isEmpty() -> info("${s.name}: ${o.name} = ${shown(store.values(s.name, listOf(o)).getValue(o.name))}", scriptCtx(s))
            rest == listOf("--reset") -> {
                store.set(s.name, o.name, null)
                info("${s.name}: ${o.name} reset to default, ${shown(o.default)}", scriptCtx(s))
            }
            else -> {
                val value = rest.joinToString(" ")
                if (o.choices.isNotEmpty() && value !in o.choices) return error("${o.name} must be one of ${o.choices.joinToString(", ")}")
                store.set(s.name, o.name, value)
                info("${s.name}: ${o.name} = $value", scriptCtx(s))
            }
        }
    }

    /** `script access <name>`: lists each switch, whether it is on or off, and where it comes from. Tap one to change it. */
    private fun listAccess(s: Script) {
        val grants = s.grants
        val items = ScriptAccess.items(s.code)
        if (items.isEmpty() && !ScriptMeta.postsNotifications(s.code)) {
            return info("${s.name}.${s.lang.ext} has no access switches: no internet, status line to pin, notifications, @uses or @on", scriptCtx(s))
        }
        info("${s.name}.${s.lang.ext} — 'script allow|deny ${s.name} <what>'", scriptCtx(s))
        fun row(item: String) {
            val from = when (item) {
                in ScriptMeta.USES -> " · @uses $item"
                in ScriptMeta.EVENTS -> " · @on $item"
                else -> ""
            }
            val on = grants.allows(item)
            // On and off as tags, as in toggles. A permission not decided yet says so.
            val state = if (on) StyledText.tag(StyledText.Colour.GOOD, "on") else if (item in grants.undecided) StyledText.tag(StyledText.Colour.WARN, "not allowed yet") else StyledText.tag(StyledText.Colour.MUTED, "off")
            out(
                "${item.padEnd(14)}$state",
                LogContext.of(
                    LogContext.DESC to ScriptAccess.label(item) + from,
                    LogContext.COMMAND to "script ${if (on) "deny" else "allow"} ${s.name} $item",
                ) + LogContext.ALIGNED,
            )
        }
        // Same order as on the Scripts screen: internet and pinning, notifications, what the script
        // declares with @uses, and last the events it runs on.
        val (events, access) = items.partition { it in ScriptMeta.EVENTS }
        val (base, declared) = access.partition { it == ScriptAccess.INTERNET || it == ScriptAccess.HEADER }
        base.forEach(::row)
        if (ScriptMeta.postsNotifications(s.code)) out(
            "notify".padEnd(14) + if (s.notify) StyledText.tag(StyledText.Colour.GOOD, "on") else StyledText.tag(StyledText.Colour.MUTED, "off"),
            LogContext.of(LogContext.DESC to "Posting notifications", LogContext.COMMAND to "script notify ${s.name} ${if (s.notify) "off" else "on"}") +
                LogContext.ALIGNED,
        )
        declared.forEach(::row)
        events.forEach(::row)
    }

    /** `script allow|deny <name> <what…>`. `notify` is the notifications switch. */
    private fun setAccess(s: Script, items: List<String>, on: Boolean) {
        val all = ScriptAccess.items(s.code)
        items.firstOrNull { it != "notify" && it !in all }?.let { item ->
            val has = all + listOfNotNull("notify".takeIf { ScriptMeta.postsNotifications(s.code) })
            return error(
                if (item in ScriptMeta.USES) "${s.name} doesn't declare @uses $item"
                else "No '$item' for ${s.name} — " + if (has.isEmpty()) "it has no access switches" else "it has: ${has.joinToString(" ")}"
            )
        }
        for (item in items) {
            if (item == "notify") scripts.setNotify(s.id, on) else scripts.setAccess(s.id, item, on)
        }
        // After pinning or unpinning, update the top of home now.
        if (ScriptAccess.HEADER in items) logs.onStatusChanged()
        info("${s.name}: " + items.joinToString(", ") { "$it ${if (on) "on" else "off"}" }, scriptCtx(s))
        if (on && "notify" in items && !Notifier.canPost(context)) ui.requestNotificationPermission()
    }

    private fun listScripts() {
        val all = scripts.all()
        if (all.isEmpty()) {
            info("No scripts yet — try 'script new hn' or 'script new news.js'")
            return
        }
        val config = com.mtwabpin.kolahal.data.Config.get(context)
        all.forEach { s ->
            val timer = listOfNotNull(
                s.intervalMin.takeIf { it > 0 }?.let { "every ${CommandParsing.formatInterval(it)}" },
                s.atTimes.takeIf { it.isNotEmpty() }?.let { "at " + it.joinToString(",") },
            ).joinToString(" ").ifEmpty { "manual" }
            val last = s.lastRun?.let { "${config.shortTime(it)} ${s.lastStatus.orEmpty()}" } ?: "never run"
            val parts = buildList {
                add(timer)
                if (ScriptMeta.isCommand(s.code)) add("command")
                ScriptMeta.events(s.code).takeIf { it.isNotEmpty() }?.let { add("on " + it.joinToString(",")) }
                if (s.notify && ScriptMeta.postsNotifications(s.code)) add("notify")
                add(last)
            }
            // A script that is switched off says so in a tag after its name. An item each: the details beside it wrap.
            val off = if (s.enabled) "" else " " + StyledText.tag(StyledText.Colour.MUTED, "off")
            out("${s.name}.${s.lang.ext}$off", scriptCtx(s) + mapOf(LogContext.DESC to parts.joinToString(" · ")) + LogContext.ITEM)
        }
    }

    /** Lists the scripts with `@on` lines and the events they run on. */
    private fun listTriggers() {
        val subscribed = scripts.all().mapNotNull { s ->
            val events = ScriptMeta.events(s.code)
            val unknown = ScriptMeta.unknownEvents(s.code)
            if (events.isEmpty() && unknown.isEmpty()) null else Triple(s, events, unknown)
        }
        if (subscribed.isEmpty()) {
            info("No script has an @on line — add '-- @on unlock' (Lua) or '// @on charger' (JS)")
        }
        subscribed.forEach { (s, events, unknown) ->
            val parts = buildList {
                add(if (events.isEmpty()) "no known events" else "on " + events.joinToString(", "))
                if (unknown.isNotEmpty()) add("unknown: " + unknown.joinToString(", "))
                if (!s.enabled) add("off")
            }
            out("${s.name}.${s.lang.ext}", scriptCtx(s) + mapOf(LogContext.DESC to parts.joinToString(" · ")))
        }
        info("Events: ${ScriptMeta.EVENTS.joinToString(" ")}")
    }

    private inline fun withScript(name: String?, action: (Script) -> Unit) {
        if (name == null) return error("Script name required")
        val script = scripts.byName(parseName(name).first)
        if (script == null) error("No script named '$name' — 'script ls'") else action(script)
    }

    private fun scriptCtx(s: Script) = LogContext.of(LogContext.SCRIPT to s.name)

    /**
     * `script diff <name>`: compares a library script with the library version, like Compare in the
     * script manager.
     * - It shows the changed lines, with three unchanged lines around each.
     * - Lines only in the library version are red (-). Lines only in yours use the accent colour (+).
     * - Other unchanged lines are only counted.
     * - "Copy text" on the first line copies it as a plain diff.
     * - The last line says how to get the library version.
     */
    private fun diff(s: Script) {
        val library = ScriptLibrary(context)
        val file = "${s.name}.${s.lang.ext}"
        val entry = library.entries.firstOrNull { it.name == s.name && it.lang == s.lang }
            ?: return info("$file isn't from the library, so there is nothing to compare it with", scriptCtx(s))
        val hunks = LineDiff.hunks(LineDiff.lines(library.code(entry), s.code))
        if (hunks.isEmpty()) return info("$file matches the library version", scriptCtx(s))
        val changed = hunks.flatMap { it.lines }
        fun lines(n: Int) = if (n == 1) "1 line" else "$n lines"
        val removed = lines(changed.count { it.kind == LineDiff.Kind.REMOVED })
        val added = lines(changed.count { it.kind == LineDiff.Kind.ADDED })
        val sign = mapOf(LineDiff.Kind.REMOVED to "-", LineDiff.Kind.ADDED to "+", LineDiff.Kind.SAME to " ")
        val plain = buildString {
            append("--- library/$file\n+++ $file\n")
            hunks.forEach { h -> h.lines.forEach { append(sign.getValue(it.kind)).append(' ').append(it.text).append('\n') } }
        }
        val at = System.currentTimeMillis()
        val out = ArrayList<LogRepository.Pending>()
        fun add(text: String, ctx: LogContext = LogContext(LogContext.ALIGNED)) { out += LogRepository.Pending(LogKind.OUTPUT, text, null, ctx, at) }
        add(
            "$file against the library version: " + StyledText.colour(StyledText.Colour.BAD, "- $removed only in the library") +
                ", " + StyledText.colour(StyledText.Colour.ACCENT, "+ $added only in yours"),
            LogContext.of(LogContext.COPY to plain) + scriptCtx(s).values,
        )
        for ((skipped, hunkLines) in hunks) {
            if (skipped > 0) add(StyledText.colour(StyledText.Colour.MUTED, "⋯ ${lines(skipped)} unchanged"))
            for ((kind, line) in hunkLines) {
                val text = "${sign.getValue(kind)} ${line.replace("\t", "    ")}"
                add(when (kind) {
                    LineDiff.Kind.REMOVED -> StyledText.colour(StyledText.Colour.BAD, text)
                    LineDiff.Kind.ADDED -> StyledText.colour(StyledText.Colour.ACCENT, text)
                    LineDiff.Kind.SAME -> StyledText.colour(StyledText.Colour.MUTED, text)
                })
            }
        }
        logs.addAll(out)
        // How to go back to the library version. An edited copy is replaced only when asked, like Reset in the script manager.
        if (library.state(entry, scripts.all()) == ScriptLibrary.State.MODIFIED) {
            info("'script install ${s.name} --replace' restores the library version (export yours first to keep it)", scriptCtx(s))
        } else {
            info("'script install ${s.name}' updates it to the library version", scriptCtx(s))
        }
    }

    private fun parseName(raw: String): Pair<String, ScriptLang?> {
        val ext = raw.substringAfterLast('.', "")
        val lang = ScriptLang.of(ext)
        return (if (lang != null) raw.substringBeforeLast('.') else raw).lowercase() to lang
    }

    companion object {
        /** True for `script secret <name> <NAME> <value>`: a line that contains a secret value. Such a line is never logged. */
        fun carriesSecret(line: String): Boolean {
            // Split the line the same way the command will, so no kind of space can hide a fifth word.
            val words = CommandParsing.tokenize(line.trim())
            return words.size >= 5 && words[0].lowercase() in setOf("script", "scripts") &&
                words[1].lowercase() in setOf("secret", "secrets") && words[4] != "--clear"
        }

        /** The subcommands that can follow `script`, for completion. */
        val SUBCOMMANDS = listOf("ls", "lib", "install", "new", "edit", "run", "every", "at", "notify", "on", "off", "get", "access", "allow", "deny", "pin", "unpin", "secret", "set", "triggers", "mv", "rm", "cat", "diff", "dir", "export", "import")
    }
}
