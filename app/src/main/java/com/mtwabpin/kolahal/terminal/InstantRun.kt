package com.mtwabpin.kolahal.terminal

/**
 * `launch.instant`: run what the user is typing as soon as it can only mean one thing, without
 * Enter. `apps` limits this to app names; `all` also covers commands, aliases, and whole lines
 * whose arguments leave nothing to choose (`torch on`). Sensitive commands (calls, messages,
 * uninstall, profile switches, clearing, …) never run this way.
 */
object InstantRun {
    const val OFF = "off"
    const val APPS = "apps"
    const val ALL = "all"

    /** Never run without Enter: they cost money, change state, or are hard to undo. */
    val SENSITIVE = setOf(
        "call", "sms", "uninstall", "work", "private", "clear", "restart", "lock", "script", "alias", "unalias",
        "config", "hide", "unhide", "rename", "feed", "feeds", "notifs", "lua", "js", "=", "url", "echo",
        "todo", "note", "notes", "remind", "timer", "alarm", "widget", "backup",
        // `rss <url>` fetches with no script's internet switch; `crashes clear` deletes the reports.
        "rss", "crashes",
        // These leave the launcher or change the phone: typing `sy` on the way to something else
        // mustn't open Android's settings.
        "battery", "home", "system", "accessibility", "usage-access", "shade", "local-network", "notifications", "source",
        // Replaces the wallpaper, which can't be brought back.
        "wallpaper",
        // `scripts rm|off|import …` reaches the same handler as `script`; `status clear` wipes lines.
        "scripts", "status",
        // Change how the launcher looks or what it puts first.
        "theme", "font", "pin", "unpin",
        // Open Android's panels and pages: typing `wi` on the way to something else mustn't open
        // Wi-Fi, update and a script mustn't either.
        "wifi", "bt", "nfc", "data", "info", "update",
    ) + com.mtwabpin.kolahal.control.ControlCommands.SENSITIVE

    /** Other names the dispatcher accepts for a command; checks always use the canonical name. */
    val SYNONYMS = mapOf(
        "fav" to "pin", "unfav" to "unpin", "favs" to "pins", "?" to "help", "ls" to "apps", "launch" to "open",
        "cls" to "clear", "reboot" to "restart", "notifications-feed" to "notifs", "web" to "url", "g" to "search",
        "youtube" to "yt", "map" to "maps", "store" to "play", "screentime" to "usage", "dial" to "call",
        "text" to "sms", "contact" to "contacts", "prefs" to "settings", "time" to "date", "set" to "config",
        "vol" to "volume", "bluetooth" to "bt", "neofetch" to "fetch", "fastfetch" to "fetch",
    )

    fun canonical(head: String): String = head.lowercase().let { SYNONYMS[it] ?: it }

    /**
     * True when [line] is a sensitive command, after synonyms: by its first word, or by what its
     * arguments make it do (`open phone:mom` starts a shortcut, which may place a call; `open x -w
     * --resume` turns a paused profile on; `--allow` asks for a permission and `--settings` opens
     * Android's settings).
     */
    fun isSensitive(line: String): Boolean {
        val t = CommandParsing.spaces(line).trim()
        // `=…` runs code in the calculator (`=notify(…)` has no space after the `=`).
        if (t.startsWith("=")) return true
        val head = canonical(t.substringBefore(' '))
        if (head in SENSITIVE) return true
        val args = t.substringAfter(' ', "").split(' ')
        if (head == "open" && (args.any { ':' in it } || "--resume" in args)) return true
        return args.any { it == "--allow" || it == "--settings" }
    }

    /**
     * Like [isSensitive], but follows [aliases] the way the dispatcher expands them (up to 5
     * levels), so an alias such as `c = clear` can't carry a sensitive command past the check.
     */
    fun isSensitive(line: String, aliases: Map<String, String>): Boolean {
        var current = CommandParsing.spaces(line).trim()
        repeat(6) {
            if (isSensitive(current)) return true
            val head = current.substringBefore(' ')
            val body = aliases[head] ?: aliases[head.lowercase()] ?: return false
            current = (body + " " + current.substringAfter(' ', "")).trim()
        }
        return true // deeper than the dispatcher expands: refuse rather than guess
    }

    sealed interface Target<out A> {
        data class App<A>(val app: A) : Target<A>
        data class Command(val line: String) : Target<Nothing>
    }

    /**
     * What [text] uniquely refers to, or null. [rankedApps] is the app search (tier 0 exact,
     * 1 prefix, 2 word prefix, 3+ looser); only prefix tiers count, so "grov" picks Grove even
     * though looser matches exist. A command and an app with the same name resolve to the command
     * in `all` mode (`system` opens Android settings). [isOn]: the app's profile is on (personal
     * always is; a paused work profile or private space isn't).
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
        // With arguments: the whole line runs once there is nothing left to choose (`torch on`).
        if (q.any { it.isWhitespace() }) return if (mode == ALL && wholeLine(q, commands, aliases)) Target.Command(q.trim()) else null
        if (q.length < minLength) return null
        val names = commands.keys + aliases.keys + scriptCommands
        val nameMatches = names.filter { it.startsWith(q) }.distinct()
        // The same app in several profiles counts once, the copy ranked first (frecency), as Enter
        // would launch it. But copies in two profiles that are on (personal and a running work
        // profile) are a choice: nothing launches, and the suggestions show both.
        val apps = rankedApps(q).filter { it.second <= 2 }.map { it.first }
            .groupBy { appLabel(it).lowercase() }.values
            .flatMap { copies -> copies.filter(isOn).takeIf { it.size > 1 } ?: listOf(copies.first()) }
        if (mode == APPS) {
            // Typing a command must never launch an app that happens to share its prefix.
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
     * Device and media state (`torch`, `dnd`, `media`, …) is sensitive: never run by a prefix or from
     * a script. A line with nothing left to choose (`torch on`), typed, picked or tapped in the log, does run.
     */
    private val WHOLE_LINE_OK = com.mtwabpin.kolahal.control.ControlCommands.SENSITIVE

    /** A [line] with arguments that fills its command's usage line completely ([UsageLine.complete]). */
    fun wholeLine(line: String, commands: Map<String, String>, aliases: Map<String, String>): Boolean {
        val word = line.trim().lowercase().substringBefore(' ')
        // An alias may stand for anything: it waits for Enter.
        if (word in aliases) return false
        val head = canonical(word)
        if (head in SENSITIVE && head !in WHOLE_LINE_OK) return false
        return commands[head]?.let { UsageLine.complete(line, it) } == true
    }

    /**
     * Whether a whole command line (e.g. tapped in the history) may run without Enter. Aliases
     * are expanded the way the dispatcher does (chained, cycle-safe); a word that isn't a command
     * runs only if [isApp] says it names an app.
     */
    fun canRun(
        line: String,
        commands: Map<String, String>,
        aliases: Map<String, String>,
        scriptCommands: Collection<String>,
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
        if (head.isEmpty() || head in SENSITIVE) return false
        // Script commands are in [commands] only when their usage is known (ScriptMeta.usage).
        val usage = commands[head] ?: return head !in scriptCommands && isApp(current)
        // Only commands that take no arguments at all: `contacts` must still accept `contacts bob`.
        return parts.size > 1 || usage.trim() == head
    }
}
