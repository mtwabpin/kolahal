package com.mtwabpin.kolahal.terminal

import android.content.Context
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.notifications.NotifPrefs
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.ui.TermFonts

/**
 * Builds the suggestion strip for the typed text:
 * - for the first word: commands, aliases and apps;
 * - after it: each command's arguments, the options its usage line lists, and earlier commands
 *   that continue the line.
 *
 * A synonym (`ls`, `cls`, `launch`) completes like the command it stands for, but keeps its own spelling.
 */
class Completer(private val context: Context, private val data: Data) {
    /** The data that suggestions read from the view model. It is read fresh on each keystroke. */
    interface Data {
        /** Earlier commands, newest first. */
        val history: List<String>
        val scripts: List<Script>
        /** Names of enabled `@command` scripts. */
        val commandNames: List<String>
        /** Usage lines of the command scripts that declare one. */
        val scriptUsages: Map<String, String>
        /** The `@desc` of each command script that has one. It is shown beside the name. */
        val scriptDescriptions: Map<String, String> get() = emptyMap()
        /** Scripts that have a status line. */
        val statusNames: Set<String>
        val feeds: List<com.mtwabpin.kolahal.feeds.FeedInfo>
        val todos: List<com.mtwabpin.kolahal.productivity.Todo>
        val notes: List<com.mtwabpin.kolahal.productivity.Note>
        val reminders: List<com.mtwabpin.kolahal.productivity.Reminder>
        /** The sources of the log's lines, newest first ([LogFilter]). */
        val logSources: List<String> get() = emptyList()
        /** The script names in the bundled library (`script install`). */
        val libraryNames: List<String> get() = emptyList()
        /** The names of the shared scripts, as far as they are known (`script get`). */
        val sharedNames: List<String> get() = emptyList()
    }

    private val config = Config.get(context)
    private val appsRepo = AppRepository.get(context)
    private val appPrefs = AppPrefs.get(context)

    /** The most rows of one kind: what the strip shows (`suggestions.max`), but never fewer than 12. */
    private val cap: Int get() = maxOf(12, config[Keys.SUGGESTIONS])

    /** How many numbers each contact name has. Computed once per contact list, not on every keystroke. */
    private var countedContacts: List<Any>? = null
    private var namesCounted: Map<String, Int> = emptyMap()

    private fun numbersPerName(all: List<com.mtwabpin.kolahal.contacts.Contact>): Map<String, Int> {
        if (countedContacts !== all) {
            namesCounted = all.groupingBy { it.name.lowercase() }.eachCount()
            countedContacts = all
        }
        return namesCounted
    }

    fun suggestions(text: String): List<Suggestion> {
        if (text.isBlank()) {
            return if (config[Keys.SUGGEST_IDLE]) appsRepo.top(config[Keys.SUGGESTIONS]).map { Suggestion.App(it) } else emptyList()
        }
        if (text.trimStart().startsWith("=")) return calcSuggestions(text)
        val parts = text.trimStart().split(' ')
        val head = parts.first().lowercase()
        // The command that a synonym stands for. It decides the completions. [head] is what goes back into the prompt.
        val cmd = InstantRun.canonical(head)
        val scriptList = data.scripts
        if (parts.size == 1) {
            val names = CommandCatalog.OFFERED_NAMES +
                data.commandNames +
                appPrefs.aliases().keys
            // Commands used lately come first, then the rest in their usual order.
            val recent = data.history.asSequence().map { InstantRun.canonical(it.substringBefore(' ')) }.distinct()
                .withIndex().associate { (i, name) -> name to i }
            val cmds = names.distinct()
                .filter { it.startsWith(head) && it != head }
                .sortedBy { recent[InstantRun.canonical(it)] ?: Int.MAX_VALUE }
                .map { Suggestion.Completion(it, "$it ", icon = if (it in data.commandNames) R.drawable.ic_code else null, help = helpFor(it)) }
            val ranked = appsRepo.searchRanked(head).take(cap)
            // Enter may launch an app, as the prompt's fallback does: on its whole name, on the only
            // match, or with launch.top on a prefix match. Then that app is the first, highlighted row,
            // so Enter, TAB and the highlight agree.
            val top = ranked.firstOrNull()
            val enterLaunches = top != null && names.none { it == head } && head !in InstantRun.SYNONYMS &&
                (top.first.label.equals(head, ignoreCase = true) || (ranked.size == 1 && config[Keys.AUTO_LAUNCH]) ||
                    (config[Keys.LAUNCH_TOP] && top.second <= 2))
            val apps = ranked.map { Suggestion.App(it.first) }
            // The text is already a whole command name. Enter runs it, so it is the first row. Then come
            // its options, then longer commands (`feed` offers `feed add` before `feeds`). TAB adds the space.
            val whole = if (cmd in names) {
                listOf(Suggestion.Completion(head, "$head ", icon = if (head in data.commandNames) R.drawable.ic_code else null,
                    help = helpFor(head) ?: CommandCatalog.COMMANDS.firstOrNull { it.name == cmd }?.help, enter = true))
            } else emptyList()
            val options = if (cmd in names) usageOptions(cmd, listOf(head, "")).map { it.copy(label = "$head ${it.label}") } else emptyList()
            // Not a command name: after the apps, show commands and settings whose description matches it.
            val about = if (cmd !in names) described(head, shown = cmds.take(3).mapTo(HashSet()) { it.label }) else emptyList()
            return (if (enterLaunches) listOf(apps.first().copy(enter = true)) + cmds.take(3) + apps.drop(1) + about else whole + options + cmds.take(3) + apps + about).distinctBy { it.key }
        }
        // Commands that this build leaves out (Play build: the call log, the screen lock, `update`) get no suggestions.
        if (cmd in CommandCatalog.UNAVAILABLE) return emptyList()
        val specific = argSuggestions(cmd, head, parts, scriptList)
        // Commands without their own completer still get the options that their usage line lists.
        val options = if (specific.isEmpty()) usageOptions(cmd, parts) else emptyList()
        val typed = text.trim()
        // Leave out an earlier line when a specific row already stands for it (`open maps` beside the Maps app).
        val covered = specific.mapTo(HashSet()) { s ->
            when (s) {
                is Suggestion.Completion -> s.replacement
                is Suggestion.App -> "$head ${s.app.label}"
            }.trim().lowercase()
        }
        val rows = (specific + options + historySuggestions(text).filterNot { it.replacement.trim().lowercase() in covered })
            .filter { it !is Suggestion.Completion || it.replacement.trim() != typed }
            .distinctBy { it.key }
        // Usually Enter runs the line as typed, and no row matches that (`torch ` toggles, it isn't `torch on`).
        // But `open ma`, `info ma`, … act on the best match for the name. If the first row is that app,
        // mark it as the Enter row.
        val arg = parts.drop(1).joinToString(" ").trim()
        val best = if (cmd in PICK_BEST_APP && arg.isNotEmpty() && FLAGS.find(" $arg") == null && !(cmd == "open" && ':' in arg)) {
            appsRepo.search(arg).firstOrNull()
        } else null
        val first = rows.firstOrNull() ?: return rows
        val picksBest = best != null && when (first) {
            is Suggestion.App -> first.app == best
            is Suggestion.Completion -> first.app == best
        }
        if (!picksBest) return rows
        return listOf(if (first is Suggestion.App) first.copy(enter = true) else (first as Suggestion.Completion).copy(enter = true)) + rows.drop(1)
    }

    /**
     * For `= 2^10`: shows the answer while you type (also for a conversion). A tap puts the answer
     * in the prompt, so you can continue from it. Then come earlier calculations that continue the line.
     */
    private fun calcSuggestions(text: String): List<Suggestion> {
        val expr = text.trimStart().drop(1).trim()
        val converted = Units.parse(expr)?.let { (amount, from, to) -> Units.convert(amount, from, to) }
        val answer = converted?.let { it to it.substringAfter("= ").substringBefore(' ').replace(",", "") }
            ?: runCatching { Calc.answer(expr) }.getOrNull()?.let { "= $it" to it }
        val row = answer?.takeIf { it.second != expr }?.let { (label, value) ->
            Suggestion.Completion(label, "= $value", help = "the answer · tap to continue from it")
        }
        return (listOfNotNull(row) + historySuggestions(text)).distinctBy { it.key }
    }

    /**
     * Returns commands (built-in and your script commands) and settings whose description has a word
     * that starts with [word]. Examples: `flash` finds `torch` (Flashlight), `silent` finds `ring`,
     * `24` finds the 24-hour clock.
     * Returns at most three commands and three settings. Commands already [shown] by name are left out.
     */
    private fun described(word: String, shown: Set<String>): List<Suggestion> {
        if (word.length < (if (word.all(Char::isDigit)) 2 else 3)) return emptyList()
        fun says(text: String) = words(text).any { it.startsWith(word, ignoreCase = true) }
        val builtIn = CommandCatalog.COMMANDS.asSequence()
            .filter { it.name !in CommandCatalog.UNAVAILABLE && it.name !in shown && it.name != word }
            // The options in its usage line count too (`ring … silent`), but not short ones such as `on`.
            .filter { spec -> says(spec.help) || says(KEYWORDS[spec.name].orEmpty()) || words(spec.usage).asSequence().drop(1).any { it.length >= 4 && it.startsWith(word, ignoreCase = true) } }
            .map { Suggestion.Completion(it.name, "${it.name} ", help = it.help) }
        val scripted = data.scriptDescriptions.asSequence().filter { (name, desc) -> name !in shown && says(desc) }
            .map { (name, desc) -> Suggestion.Completion(name, "$name ", icon = R.drawable.ic_code, help = desc) }
        val settings = Keys.ALL.asSequence().filter { says(com.mtwabpin.kolahal.ui.TITLES[it.key].orEmpty()) || says(it.help) }
            .map { Suggestion.Completion(it.key, "config set ${it.key} ", icon = R.drawable.ic_tune, help = com.mtwabpin.kolahal.ui.TITLES[it.key] ?: it.help) }
        return (builtIn + scripted).take(3).toList() + settings.take(3).toList()
    }

    /** The text beside a command's name in its row: a command script's `@desc`, or what an alias stands for. */
    private fun helpFor(name: String): String? = data.scriptDescriptions[name] ?: appPrefs.aliases()[name]?.let { "= $it" }

    /**
     * Returns earlier commands that continue the typed text. This is the completer for free text
     * (search, note, …).
     * A line that named an item by its number (`todo done 3`) is left out. The numbers may have
     * changed since, and the live rows show the current ones.
     */
    private fun historySuggestions(text: String): List<Suggestion.Completion> {
        val typed = text.trimStart()
        return data.history.asSequence()
            .filter { it.length > typed.length && it.startsWith(typed, ignoreCase = true) && !STALE_NUMBERED.matches(it) }
            .take(4)
            .map { Suggestion.Completion(it, it) }
            .toList()
    }

    /** Returns the literal options at this argument position in the command's usage line (`apps [work|personal|…]`). */
    private fun usageOptions(cmd: String, parts: List<String>): List<Suggestion.Completion> {
        val usage = CommandCatalog.COMMAND_USAGES[cmd] ?: data.scriptUsages[cmd] ?: return emptyList()
        val token = UsageLine.tokens(usage).drop(1).getOrNull(parts.size - 2) ?: return emptyList()
        if (token.startsWith("<")) return emptyList() // a placeholder: <app>, <query>, …
        val inner = token.removeSurrounding("[", "]")
        val last = parts.last()
        val before = parts.dropLast(1).joinToString(" ")
        return UsageLine.split(inner, '|').mapNotNull { alt ->
            val word = alt.trim().substringBefore(' ')
            val rest = alt.trim().substringAfter(' ', "")
            // `add <url>` and `mute <app>` take a value after them. `name = command` is not an option.
            word.takeIf { it.matches(UsageLine.OPTION) && (rest.isEmpty() || rest.startsWith("<") || rest.startsWith("[")) }
        }.filter { it.starts(last) }
            .map { Suggestion.Completion(it, "$before $it ") }
    }

    /** True when this word starts with [prefix] (any case) and is longer than it. */
    private fun String.starts(prefix: String) = startsWith(prefix, ignoreCase = true) && !equals(prefix, ignoreCase = true)

    private fun argSuggestions(cmd: String, head: String, parts: List<String>, scriptList: List<Script>): List<Suggestion> {
        val arg = parts.drop(1).joinToString(" ")
        val last = parts.last()
        fun words(options: List<String>, icon: Int? = null) = options.filter { it.starts(last) }
            .map { Suggestion.Completion(it, (parts.dropLast(1) + it).joinToString(" "), icon = icon) }
        return when (cmd) {
            "activity" -> activitySuggestions(head, parts)
            "open" -> if (arg.contains(':')) shortcutSuggestions(head, arg)
                else appsRepo.search(arg.replace(FLAGS, "").trim()).take(cap).map { Suggestion.App(it) }
            // `unhide` and `unpin` offer only the apps they can act on, not every app.
            "info", "uninstall", "hide", "unhide", "rename", "shortcuts", "pin", "unpin" -> when (cmd) {
                "rename" if arg.contains('=') -> emptyList()
                "unhide" -> appCompletions(head, arg, includeHidden = true) { appPrefs.isHidden(it.key) }
                "unpin" -> appPrefs.pinned().toSet().let { pinned -> appCompletions(head, arg) { it.key in pinned } }
                else -> appCompletions(head, arg)
            }
            "notifs" -> when {
                parts.size >= 3 && parts[1] == "mute" -> appCompletions("$head mute", parts.drop(2).joinToString(" "))
                parts.size >= 3 && parts[1] == "unmute" -> NotifPrefs.get(context).let { prefs -> appCompletions("$head unmute", parts.drop(2).joinToString(" ")) { prefs.isMuted(it.pkg) } }
                parts.size == 3 && parts[1] == "log" -> words(listOf("on", "off"))
                else -> emptyList()
            }
            "alias", "unalias" -> if (parts.size == 2) appPrefs.aliases().filterKeys { it.startsWith(last, ignoreCase = true) }.map { (name, command) ->
                if (cmd == "unalias") Suggestion.Completion("$name = $command", "$head $name")
                else Suggestion.Completion("$name = $command", "$head $name = $command")
            } else emptyList()
            // `help to`: offer the command names.
            "help" -> if (parts.size == 2) CommandCatalog.OFFERED_NAMES.filter { it.starts(last) }
                .map { Suggestion.Completion(it, "$head $it", help = CommandCatalog.COMMANDS.firstOrNull { c -> c.name == it }?.help) } else emptyList()
            "status" -> if (parts.size == 3 && parts[1] == "clear") words(data.statusNames.toList() + "all", R.drawable.ic_code) else emptyList()
            "feed" -> when (parts.size) {
                3 if parts[1] in FEED_ID_SUBCOMMANDS -> data.feeds
                    .filter { last.isEmpty() || it.id.toString().startsWith(last) || (it.title ?: it.url).contains(last, ignoreCase = true) }
                    .take(cap).map { Suggestion.Completion("#${it.id} ${it.title ?: it.url}", "$head ${parts[1]} ${it.id}", icon = R.drawable.ic_rss) }
                4 if parts[1] == "notify" -> words(listOf("on", "off"))
                // `feed off` switches a feed off, so `every` takes only an interval.
                4 if parts[1] == "every" -> words(INTERVALS - "off")
                else -> emptyList()
            }
            "usage", "unlocks" -> if (parts.size == 2) listOf("1", "7", "14", "30")
                .filter { it.starts(last) }.map { Suggestion.Completion(if (it == "1") "today" else "$it days", "$head $it") } else emptyList()
            "installs", "calls" -> if (parts.size == 2) listOf("20", "50", "200")
                .filter { it.starts(last) }.map { Suggestion.Completion("last $it", "$head $it") } else emptyList()
            "script" -> {
                if (parts.size == 2) {
                    ScriptCommands.SUBCOMMANDS.filter { it.starts(parts[1]) }
                        .map { Suggestion.Completion(it, "$head $it ") }
                } else {
                    val sub = parts[1]
                    val prefix = parts.getOrNull(2).orEmpty()
                    if (parts.size == 3) {
                        // The script names that the subcommand takes:
                        // - a library or shared script to install;
                        // - a script to switch on or off, or to pin or unpin;
                        // - otherwise any script. Some subcommands take no name at all.
                        val names = when (sub) {
                            "install" -> data.libraryNames
                            // Only a library script has a library version to compare with.
                            "diff" -> scriptList.map { it.name }.filter { it in data.libraryNames }
                            "get" -> data.sharedNames
                            "on" -> scriptList.filter { !it.enabled }.map { it.name }
                            "off" -> scriptList.filter { it.enabled }.map { it.name }
                            // Only what the command can act on: a script with a status line that isn't
                            // pinned yet, or one that is pinned.
                            "pin" -> scriptList.filter { !it.grants.allows(ScriptAccess.HEADER) && ScriptMeta.setsStatus(it.code) }.map { it.name }
                            "unpin" -> scriptList.filter { it.grants.allows(ScriptAccess.HEADER) }.map { it.name }
                            in SCRIPT_NO_NAME -> emptyList()
                            else -> scriptList.map { it.name }
                        }
                        names.filter { it.starts(prefix) }.map { Suggestion.Completion(it, "$head $sub $it ", icon = R.drawable.ic_code) }
                    } else if (parts.size == 4 && sub == "every") {
                        words(INTERVALS)
                    } else if (parts.size == 4 && sub == "notify") {
                        words(listOf("on", "off"))
                    } else if (parts.size == 4 && sub == "set") {
                        // The script's @option names. The next word gets the option's choices.
                        val s = scriptList.firstOrNull { it.name == parts[2] }
                        words((s?.let { ScriptMeta.options(it.code).map { o -> o.name } }).orEmpty()).map { it.copy(replacement = it.replacement + " ") }
                    } else if (parts.size == 5 && sub == "set") {
                        val o = scriptList.firstOrNull { it.name == parts[2] }?.let { ScriptMeta.options(it.code) }?.firstOrNull { it.name == parts[3] }
                        words(o?.choices.orEmpty() + listOf("--reset"))
                    } else if (parts.size == 4 && (sub == "secret" || sub == "secrets")) {
                        // The script's @secret names.
                        val s = scriptList.firstOrNull { it.name == parts[2] }
                        words((s?.let { ScriptMeta.secrets(it.code).map { d -> d.name } }).orEmpty())
                    } else if (parts.size >= 4 && (sub == "allow" || sub == "deny")) {
                        // What can be switched: its access items, and `notify` if it posts notifications. Each is offered once.
                        val s = scriptList.firstOrNull { it.name == parts[2] }
                        val given = parts.subList(3, parts.size - 1)
                        (s?.let { ScriptAccess.items(it.code) + listOfNotNull("notify".takeIf { _ -> ScriptMeta.postsNotifications(it.code) }) }).orEmpty()
                            .filter { it.starts(last) && it !in given }
                            .map { Suggestion.Completion(it, (parts.dropLast(1) + it).joinToString(" ") + " ") }
                    } else emptyList()
                }
            }
            "config" -> {
                val keyIndex = if (parts.getOrNull(1) in listOf("set", "get", "reset")) 2 else 1
                when (parts.size) {
                    2 if keyIndex == 2 -> emptyList()
                    keyIndex + 1 -> {
                        val prefix = parts.take(keyIndex).joinToString(" ")
                        val subs = if (keyIndex == 1) listOf("ls", "set", "get", "reset").filter { it.startsWith(last, ignoreCase = true) } else emptyList()
                        subs.map { Suggestion.Completion(it, "$prefix $it ") } +
                            Keys.ALL.filter { it.key.starts(last) }
                                .map { Suggestion.Completion(it.key, "$prefix ${it.key} ", icon = R.drawable.ic_tune) }
                    }
                    keyIndex + 2 -> {
                        val key = Keys.byName(parts[keyIndex]) ?: return emptyList()
                        val prefix = parts.take(keyIndex + 1).joinToString(" ")
                        val values = when {
                            key is ConfigKey.Bool -> listOf("on", "off")
                            key is ConfigKey.Choice -> key.choices
                            // A gesture's action is one of these words, or any command.
                            key.key.startsWith("gesture.") -> listOf("lock", "shade", "quick", "none") - CommandCatalog.UNAVAILABLE
                            else -> emptyList()
                        }
                        values.filter { it.starts(last) }.map { Suggestion.Completion(it, "$prefix $it") }
                    }
                    else -> emptyList()
                }
            }
            "theme" -> Keys.THEME.choices.filter { it.starts(arg) }
                .map { Suggestion.Completion(it, "$head $it", icon = R.drawable.ic_palette) }
            // `cal dec`: offer the month names.
            "cal" -> if (parts.size == 2) MonthCalendar.suggest(last).map { Suggestion.Completion(it, "$head $it") } else emptyList()
            // The last words typed may be the start of one place (`new y` → New York), after other places.
            // `time` is a synonym, so [cmd] is `date` for both. The line keeps what was typed.
            "date" -> (minOf(3, parts.size - 1) downTo 1).firstNotNullOfOrNull { n ->
                WorldClock.suggest(parts.takeLast(n).joinToString(" ")).takeIf { it.isNotEmpty() }
                    ?.map { Suggestion.Completion(it.name, (parts.dropLast(n) + it.name).joinToString(" ")) }
            }.orEmpty()
            "font" -> if (parts.size == 3 && parts[1] == "sans") {
                Keys.FONT_SANS.choices.filter { it.starts(last) }.map { Suggestion.Completion(it, "$head sans $it") }
            } else {
                (TermFonts.names + "import" + "sans").filter { it.starts(arg) }
                    .map { Suggestion.Completion(it, if (it == "sans") "$head sans " else "$head $it") }
            }
            "call", "sms", "contacts" -> contactSuggestions(cmd, head, parts)
            in CommandCatalog.PRODUCTIVITY -> productivitySuggestions(cmd, head, parts)
            // `log only …`: offer the kinds, then the sources of the log's lines.
            "log" -> if (parts.size == 3 && parts[1] == "only") {
                LogFilter.suggestions(parts[2], data.logSources).take(cap)
                    .map { Suggestion.Completion(it.label, "$head only ${it.word}", icon = R.drawable.ic_visibility) }
            } else emptyList()
            else -> com.mtwabpin.kolahal.control.ControlCommands.suggest(cmd, arg)
                .map { (label, _) -> Suggestion.Completion(label, "$head $label") }
        }
    }

    /**
     * Returns `<head> <app>` completions.
     * - If [only] is given, only apps that pass it are included.
     * - The profile flag is added when the app exists in several profiles.
     * - Apps outside the personal profile show their profile tag.
     */
    private fun appCompletions(head: String, arg: String, includeHidden: Boolean = false, only: ((AppEntry) -> Boolean)? = null): List<Suggestion> {
        val found = appsRepo.search(arg.replace(FLAGS, "").trim(), includeHidden = includeHidden)
            .filter { only == null || only(it) }
            .take(cap)
        // Two apps with the same name in one profile would complete to the same line, and the command
        // could reach only the first. So each one is named by its package (or package/activity) instead.
        val twins = found.groupBy { it.label.lowercase() to it.profile.kind }.values.filter { it.size > 1 }
        val ids = twins.flatMap { group ->
            val packages = group.groupingBy { it.pkg }.eachCount()
            group.map { it to if (packages.getValue(it.pkg) > 1) it.component.flattenToShortString() else it.pkg }
        }.toMap()
        return found.map { app ->
            val tag = if (app.profile.kind == ProfileKind.PERSONAL) "" else " [${app.profile.kind.tag}]"
            val id = ids[app]
            Suggestion.Completion(app.label + tag + (id?.let { " · $it" } ?: ""), "$head ${id ?: app.label}" + flagFor(app), app = app)
        }
    }

    /** The activities of the last app, so typing after `activity settings ` doesn't ask the system on every key. */
    private var activityCache: Pair<String, List<ActivityItem>>? = null

    /**
     * `activity set` offers apps (personal profile only). After the app's name and a space,
     * `activity settings wi` offers the app's activities that match.
     */
    private fun activitySuggestions(head: String, parts: List<String>): List<Suggestion> {
        val words = parts.drop(1).filter { it.isNotEmpty() && !it.startsWith("-") }
        if (words.isEmpty()) return emptyList()
        fun appRows() = appsRepo.search(words.joinToString(" "), ProfileKind.PERSONAL).take(cap)
            .map { Suggestion.Completion(it.label, "$head ${ActivityCommands.appToken(context, it)} ", app = it) }
        val (appName, name) = ActivityMatch.split(words) { appsRepo.searchRanked(it, ProfileKind.PERSONAL).firstOrNull()?.second }
            ?: return appRows()
        // Still typing the app's name.
        if (name.isEmpty() && parts.last().isNotEmpty()) return appRows()
        val app = appsRepo.search(appName, ProfileKind.PERSONAL).firstOrNull() ?: return emptyList()
        val items = activityCache?.takeIf { it.first == app.key }?.second
            ?: ActivityCommands.activities(context, app).also { activityCache = app.key to it }
        val shown = if (name.isEmpty()) items else when (val found = ActivityMatch.find(items, name)) {
            is ActivityMatch.Found.One -> listOf(found.item)
            is ActivityMatch.Found.Many -> found.items
            ActivityMatch.Found.None -> emptyList()
        }
        return shown.take(cap).map { item ->
            val itemName = ActivityMatch.nameFor(item, items)
            Suggestion.Completion("${app.label} › ${item.label}", "$head ${ActivityCommands.appToken(context, app, itemName)} $itemName", app = app)
        }
    }

    /** The shortcut labels of the last app, so typing after `open maps:` doesn't ask the system on every key. */
    private var shortcutCache: Pair<String, List<String>>? = null

    private var contactsLoadRequested = false

    /** `open maps:` → offers that app's launcher shortcuts. */
    private fun shortcutSuggestions(head: String, arg: String): List<Suggestion> {
        val app = appsRepo.search(arg.substringBefore(':').trim()).firstOrNull() ?: return emptyList()
        val labels = shortcutCache?.takeIf { it.first == app.key }?.second
            ?: appsRepo.shortcuts(app).mapNotNull { (it.shortLabel ?: it.longLabel)?.toString() }.distinct()
                .also { shortcutCache = app.key to it }
        val wanted = arg.substringAfter(':').trim()
        return labels.filter { it.contains(wanted, ignoreCase = true) }.take(cap)
            .map { Suggestion.Completion("${app.label} › $it", "$head ${app.label}:$it" + flagFor(app), app = app) }
    }

    /**
     * Contact suggestions:
     * - `call ` lists favourites first, then everyone. Typing narrows the list by name or number.
     * - A name with several numbers gets one row per number, and completes to the number. So each
     *   row is different and dials the number it shows.
     * - `sms` always completes to the number, because its first word is the recipient.
     */
    private fun contactSuggestions(cmd: String, head: String, parts: List<String>): List<Suggestion> {
        val repo = com.mtwabpin.kolahal.contacts.ContactsRepository.get(context)
        if (!repo.picker && !repo.hasPermission()) return listOf(Suggestion.Completion("Allow contacts access", "contacts --allow", icon = R.drawable.ic_person))
        // `contacts --forget <name>`: offer the names picked before.
        if (repo.picker && cmd == "contacts" && parts.getOrNull(1) == "--forget") {
            val q = parts.drop(2).joinToString(" ")
            return (if (q.isBlank()) repo.contacts.value else repo.search(q, cap)).map { it.name }.distinct().take(cap)
                .map { Suggestion.Completion(it, "$head --forget $it", icon = R.drawable.ic_person) }
        }
        val sms = cmd == "sms"
        // `sms <number> hello…`: the recipient is complete, and the rest is the message.
        if (sms && parts.size > 2 && com.mtwabpin.kolahal.contacts.ContactsRepository.looksLikeNumber(parts[1])) return emptyList()
        val query = parts.drop(1).joinToString(" ")
        val all = repo.contacts.value
        // Access may be allowed after start (e.g. from system settings). Load now. [contacts] triggers a recomposition when done.
        if (all.isEmpty() && !contactsLoadRequested) { contactsLoadRequested = true; repo.refresh() }
        val list = if (query.isBlank()) all.take(cap) else repo.search(query, cap)
        val numbersPerName = numbersPerName(all)
        // The Play build knows only the contacts picked before. So the last row opens the picker.
        val pick = if (repo.picker) Suggestion.Completion("Pick a contact", cmd, icon = R.drawable.ic_person, help = "Enter opens Android's contact picker") else null
        return list.map { c ->
            val shared = (numbersPerName[c.name.lowercase()] ?: 1) > 1
            val label = (if (c.starred) "★ " else "") + c.name + if (shared) " · ${c.label} ${c.number}" else ""
            val target = when {
                cmd == "contacts" -> c.name
                sms -> c.number.filterNot { it.isWhitespace() }
                shared -> c.number
                else -> c.name
            }
            Suggestion.Completion(label, "$head $target" + if (sms) " " else "", icon = R.drawable.ic_person)
        } + listOfNotNull(pick)
    }

    private fun flagFor(app: AppEntry) = when (app.profile.kind) {
        ProfileKind.WORK -> " -w"
        ProfileKind.PRIVATE -> " -s"
        ProfileKind.CLONE -> " -c"
        ProfileKind.PERSONAL -> if (appsRepo.apps.value.any { it.pkg == app.pkg && it.profile.kind != ProfileKind.PERSONAL }) " -p" else ""
    }

    private fun productivitySuggestions(cmd: String, head: String, parts: List<String>): List<Suggestion> {
        val sub = parts.getOrNull(1).orEmpty()
        fun subs(list: List<String>) = if (parts.size == 2) {
            list.filter { it.starts(sub) }.map { Suggestion.Completion(it, "$head $it ") }
        } else emptyList()
        // `todo done ` → the open items, by their list number (the one that `todo` shows).
        fun items(labels: List<Pair<Int, String>>, icon: Int) = if (parts.size == 3) {
            labels.filter { (n, text) -> parts[2].isEmpty() || n.toString().startsWith(parts[2]) || text.contains(parts[2], ignoreCase = true) }
                .take(cap).map { (n, text) -> Suggestion.Completion("$n. $text", "$head $sub $n", icon = icon) }
        } else emptyList()
        val numberedTodos = data.todos.mapIndexed { i, t -> i + 1 to t }
        return when (cmd) {
            "todo" -> when (sub) {
                "done", "toggle" -> items(numberedTodos.filter { !it.second.done }.map { it.first to it.second.text }, R.drawable.ic_check)
                "undo", "undone" -> items(numberedTodos.filter { it.second.done }.map { it.first to it.second.text }, R.drawable.ic_check)
                "rm", "edit" -> items(numberedTodos.map { it.first to it.second.text }, R.drawable.ic_check)
                else -> subs(ProductivityCommands.TODO_SUBCOMMANDS)
            }
            "notes" -> if (sub == "rm") items(data.notes.mapIndexed { i, n -> i + 1 to n.text }, R.drawable.ic_edit) else subs(ProductivityCommands.NOTES_SUBCOMMANDS)
            "remind" -> when {
                sub == "rm" -> items(data.reminders.mapIndexed { i, r -> i + 1 to r.text }, R.drawable.ic_alarm)
                parts.size == 2 -> (listOf("rm", "exact", "every") + listOf("10m", "30m", "1h", "tomorrow 9:00"))
                    .filter { it.starts(sub) }.map { Suggestion.Completion(it, "$head $it ") }
                sub == "every" && parts.size == 3 -> listOf("day", "weekday", "weekend", "mon", "tue", "wed", "thu", "fri", "sat", "sun", "month", "year")
                    .filter { it.starts(parts[2]) }.map { Suggestion.Completion(it, "$head every $it ") }
                else -> emptyList()
            }
            "timer" -> if (parts.size == 2) listOf("5m", "10m", "15m", "25m", "1h")
                .filter { it.starts(sub) }.map { Suggestion.Completion(it, "$head $it ") } else emptyList()
            "agenda" -> if (parts.size == 2) listOf("1", "3", "7", "14", "31")
                .filter { it.starts(sub) }.map { Suggestion.Completion(if (it == "1") "today" else "$it days", "$head $it") } else emptyList()
            else -> emptyList()
        }
    }

    companion object {
        /** The separator that splits a description into words. */
        private val NOT_WORD = Regex("[^\\p{L}\\p{N}]+")

        /** Descriptions split into words, once each, because typing searches all of them on every key. */
        private val wordsOf = java.util.concurrent.ConcurrentHashMap<String, List<String>>()

        private fun words(text: String): List<String> {
            // Edited scripts leave old descriptions behind. Only a few hundred are in use at once.
            if (wordsOf.size > 2_000) wordsOf.clear()
            return wordsOf.getOrPut(text) { text.split(NOT_WORD) }
        }

        /** Everyday words for what a command does, which its description doesn't use. */
        private val KEYWORDS = mapOf(
            "media" to "music song player",
            "volume" to "sound loud quiet",
            "brightness" to "light dim",
            "data" to "mobile cellular",
            "wifi" to "wireless network internet",
            "bt" to "bluetooth headphones",
            "=" to "math sum",
            "date" to "clock",
            "theme" to "colours colors dark light",
            "battery" to "power",
            "toggles" to "quick",
        )

        /** Profile flags typed after an app name (`-w`, `-p`). They are removed before searching. */
        private val FLAGS = Regex("\\s-\\w+")

        /** Commands that act on the best match for the app name typed after them. Their first row shows that match. */
        private val PICK_BEST_APP = setOf("open", "info", "uninstall", "hide", "shortcuts", "pin")

        /** `feed <sub> <id>`: the subcommands that take a feed. */
        private val FEED_ID_SUBCOMMANDS = setOf("rm", "remove", "url", "read", "all", "open", "notify", "every", "fetch", "refresh", "on", "off")

        /** `script <sub>`: the subcommands that take no script name. */
        private val SCRIPT_NO_NAME = setOf("ls", "lib", "triggers", "dir", "export", "import", "new")

        /** Timer intervals offered for `script every`. `feed every` gets the same, without "off". */
        private val INTERVALS = listOf("15m", "30m", "1h", "2h", "6h", "12h", "1d", "off")

        /** Matches an earlier line that named an item by its list number. It is not offered again, because the numbers may have changed. */
        private val STALE_NUMBERED = Regex("^(todo (done|toggle|undo|undone|rm|edit)|notes rm|remind rm|feed (rm|remove|read|all|open|notify|every|fetch|refresh|on|off)) #?\\d+.*", RegexOption.IGNORE_CASE)
    }
}
