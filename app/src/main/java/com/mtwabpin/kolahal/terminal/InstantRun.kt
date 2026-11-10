package com.mtwabpin.kolahal.terminal

/**
 * `launch.instant`: runs what the user is typing, without Enter, as soon as it can mean only one thing.
 * - `apps`: only app names.
 * - `all`: also commands, aliases, and whole lines whose arguments leave nothing to choose (`torch on`).
 *
 * Sensitive commands (calls, messages, uninstall, profile switches, clearing, …) never run this way.
 * One exception: device and media state commands do run as such a whole line ([wholeLine]).
 */
object InstantRun {
    const val OFF = "off"
    const val APPS = "apps"
    const val ALL = "all"

    /**
     * Commands that never run without Enter, except those in [WHOLE_LINE_OK]. They cost money,
     * change state, or are hard to undo.
     */
    val SENSITIVE = setOf(
        "call",
        "sms",
        "uninstall",
        "work",
        "private",
        "clear",
        "restart",
        "update",
        "lock",
        "script",
        "alias",
        "unalias",
        "config",
        "hide",
        "unhide",
        "rename",
        "feed",
        "feeds",
        "notifs",
        "lua",
        "js",
        "=",
        "url",
        "echo",
        "todo",
        "note",
        "notes",
        "remind",
        "timer",
        "alarm",
        "widget",
        "backup",
        // `rss <url>` fetches without any script's internet switch. `crashes clear` deletes the reports.
        "rss",
        "crashes",
        // These leave the launcher or change the phone. Typing `sy` on the way to another word
        // must not open Android's settings.
        "battery",
        "home",
        "system",
        "alarms",
        "stopwatch",
        "accessibility",
        "usage-access",
        "shade",
        "notifications",
        "source",
        // Replaces the wallpaper, and the old one can't be restored.
        "wallpaper",
        // `scripts rm|off|import …` goes to the same handler as `script`. `status clear` deletes lines.
        "scripts",
        "status",
        // These change how the launcher looks or what it shows first.
        "theme",
        "font",
        "pin",
        "unpin",
        // These open Android's panels and pages. Typing `wi` on the way to another word must not
        // open Wi-Fi, and a script must not open it either.
        "wifi",
        "bt",
        "nfc",
        "data",
        "info",
        // Opens another app's screen. Typing `activity settings w` must not open the first match.
        "activity",
    ) + com.mtwabpin.kolahal.control.ControlCommands.SENSITIVE

    /** Other names that the dispatcher accepts for a command. Checks always use the canonical name. */
    val SYNONYMS = mapOf(
        "fav" to "pin",
        "unfav" to "unpin",
        "favs" to "pins",
        "?" to "help",
        "ls" to "apps",
        "launch" to "open",
        "cls" to "clear",
        "reboot" to "restart",
        "notifications-feed" to "notifs",
        "web" to "url",
        "g" to "search",
        "youtube" to "yt",
        "map" to "maps",
        "store" to "play",
        "screentime" to "usage",
        "updates" to "installs",
        "dial" to "call",
        "text" to "sms",
        "contact" to "contacts",
        "prefs" to "settings",
        "time" to "date",
        "set" to "config",
        "vol" to "volume",
        "bluetooth" to "bt",
        "neofetch" to "fetch",
        "fastfetch" to "fetch",
    )

    fun canonical(head: String): String = head.lowercase().let { SYNONYMS[it] ?: it }

    /**
     * True when [line] is a sensitive command, after synonyms are resolved. This is decided by:
     * - its first word; or
     * - what its arguments make it do:
     *   - `open phone:mom` starts a shortcut, which may place a call;
     *   - `open x -w --resume` turns a paused profile on;
     *   - `--allow` asks for a permission;
     *   - `--settings` opens Android's settings.
     */
    fun isSensitive(line: String): Boolean {
        // Add spaces around pipes first: `torch|head` runs `torch`, and a script may not send that.
        val t = Pipes.spaced(CommandParsing.spaces(line)).trim()
        // `=…` runs code in the calculator. (`=notify(…)` has no space after the `=`.)
        if (t.startsWith("=")) return true
        val head = canonical(t.substringBefore(' '))
        if (head in SENSITIVE) return true
        val args = t.substringAfter(' ', "").split(' ')
        return (head == "open" && (args.any { ':' in it } || "--resume" in args)) || args.any { it == "--allow" || it == "--settings" }
    }

    /**
     * Like [isSensitive], but also expands [aliases] the way the dispatcher does (up to 5 levels).
     * So an alias such as `c = clear` can't hide a sensitive command from the check.
     */
    fun isSensitive(line: String, aliases: Map<String, String>): Boolean {
        var current = Pipes.spaced(CommandParsing.spaces(line))
        repeat(6) {
            if (isSensitive(current)) return true
            val head = current.substringBefore(' ')
            val body = aliases[head] ?: aliases[head.lowercase()] ?: return false
            current = Pipes.spaced(body + " " + current.substringAfter(' ', ""))
        }
        return true // deeper than the dispatcher expands: refuse rather than guess
    }

    sealed interface Target<out A> {
        data class App<A>(val app: A) : Target<A>
        data class Command(val line: String) : Target<Nothing>
    }

    /**
     * Returns the one thing that [text] refers to, or null if it is not unique.
     * - [rankedApps] is the app search, with tiers: 0 exact, 1 prefix, 2 word prefix, 3+ looser.
     *   Only the prefix tiers count, so "grov" picks Grove even when looser matches exist.
     * - In `all` mode, if a command and an app have the same name, the command wins
     *   (`settings` opens Kolahal's settings, not an app called Settings).
     * - [isOn] says whether the app's profile is on. Personal always is. A paused work profile
     *   or private space is not.
     */
    fun <A> target(
        text: String,
        mode: String,
        minLength: Int,
        rankedApps: (String) -> List<Pair<A, Int>>,
        appLabel: (A) -> String,
        commands: Map<String, String>,
        aliases: Map<String, String>,
        scriptCommands: Collection<String>,
        isOn: (A) -> Boolean = { false },
    ): Target<A>? {
        if (mode == OFF) return null
        val q = text.trimStart().lowercase()
        // With arguments, the whole line runs once there is nothing left to choose (`torch on`).
        if (q.any { it.isWhitespace() }) return if (mode == ALL && wholeLine(q, commands, aliases)) Target.Command(q.trim()) else null
        if (q.length < minLength) return null
        val names = commands.keys + aliases.keys + scriptCommands
        val nameMatches = names.filter { it.startsWith(q) }.distinct()
        // The same app in several profiles counts once: the copy ranked first (frecency), which Enter
        // would launch. But if copies are in two profiles that are on (personal and a running work
        // profile), the user must choose. Nothing launches, and the suggestions show both.
        val apps = rankedApps(q).filter { it.second <= 2 }.map { it.first }
            .groupBy { appLabel(it).lowercase() }.values
            .flatMap { copies -> copies.filter(isOn).takeIf { it.size > 1 } ?: listOf(copies.first()) }
        if (mode == APPS) {
            // Typing a command must never launch an app that has the same prefix.
            if (nameMatches.isNotEmpty()) return null
            return apps.singleOrNull()?.let { Target.App(it) }
        }
        val otherApps = apps.filter { appLabel(it).lowercase() !in nameMatches }
        return when {
            nameMatches.isEmpty() -> otherApps.singleOrNull()?.let { Target.App(it) }
            nameMatches.size == 1 && otherApps.isEmpty() -> {
                val name = nameMatches[0]
                if (canRun(name, commands, aliases, scriptCommands)) Target.Command(name) else null
            }
            else -> null
        }
    }

    /**
     * Device and media state commands (`torch`, `dnd`, `media`, …) are sensitive. They never run
     * from a prefix or from a script. But a line with nothing left to choose (`torch on`) does run,
     * whether it is typed, picked, or tapped in the log.
     */
    private val WHOLE_LINE_OK = com.mtwabpin.kolahal.control.ControlCommands.SENSITIVE

    /**
     * Commands that are sensitive when typed or sent by a script, but run when tapped in the log.
     * `call` only opens the dialer with the number, and you still press Call there. So a tapped
     * contact doesn't need to wait for Enter.
     */
    private val TAP_OK = setOf("call")

    /** True when [line] has arguments and fills its command's usage line completely ([UsageLine.complete]). */
    fun wholeLine(line: String, commands: Map<String, String>, aliases: Map<String, String>): Boolean {
        val word = line.trim().lowercase().substringBefore(' ')
        // An alias may stand for anything, so it waits for Enter.
        if (word in aliases) return false
        val head = canonical(word)
        return (head !in SENSITIVE || head in WHOLE_LINE_OK) && commands[head]?.let { UsageLine.complete(line, it) } == true
    }

    /**
     * Returns whether a whole command line (for example, one tapped in the history) may run without Enter.
     * - Aliases are expanded the way the dispatcher does: chained, and safe against cycles.
     * - A word that is not a command runs only if [isApp] says it names an app.
     */
    fun canRun(
        line: String,
        commands: Map<String, String>,
        aliases: Map<String, String>,
        scriptCommands: Collection<String>,
        tapped: Boolean = false,
        isApp: (String) -> Boolean = { false },
    ): Boolean {
        var current = line.trim()
        val seen = HashSet<String>()
        while (true) {
            val head = current.substringBefore(' ').lowercase()
            val expansion = aliases[head] ?: break
            if (!seen.add(head) || seen.size > 5) return false
            current = (expansion + " " + current.substringAfter(' ', "")).trim()
        }
        val parts = current.split(Regex("\\s+"))
        val head = canonical(parts.firstOrNull() ?: return false)
        if (head.isEmpty() || (head in SENSITIVE && !(tapped && head in TAP_OK))) return false
        // Script commands are in [commands] only when their usage is known (ScriptMeta.usage).
        val usage = commands[head] ?: return head !in scriptCommands && isApp(current)
        // A bare command runs only if it takes no arguments at all. `contacts` must still accept `contacts bob`.
        return parts.size > 1 || usage.trim() == head
    }
}
