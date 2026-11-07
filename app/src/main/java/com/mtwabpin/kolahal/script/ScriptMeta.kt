package com.mtwabpin.kolahal.script

/**
 * Directives read from a script's comments:
 *
 *   -- @command            (or `// @command` in JS) — the script can be run from the prompt by name
 *   -- @desc <text>        — a one-line description shown in `help` and suggestions
 *   -- @usage <args>       — the arguments, written like a built-in usage line (`[random|<number>]`);
 *                            empty when there are none. Used for suggestions and `launch.instant`
 *   -- @on <event>[, ...]  — run when a device event happens (see [EVENTS]); the payload is `event`
 *   -- @uses <what>[, ...] — what it needs (see [USES]); without it, those calls fail
 *   -- @secret <NAME> <site> <description> — a key you set, read with secrets.get, sent only to <site> ([Secret])
 *   -- @option <name> <default> <description> — a setting you change without editing the code, read as options.<name> ([Option])
 *
 * Directives that describe the script, so a script file is complete on its own (the library reads these too):
 *
 *   -- @title <text>       — its name for people: Hacker News
 *   -- @author <name>, @version <x.y>, @tags <word>[, ...]
 *   -- @every <15m|1h|1d>, @at <HH:mm>[, ...], @notify — the timers and notifications it suggests,
 *                            set when it is added from a file or the library
 *   -- @requires <x.y.z>   — the oldest Kolahal it works with; older versions don't import it
 */
object ScriptMeta {
    private val command = Regex("""^\s*(--|//)\s*@command\b""", RegexOption.MULTILINE)
    private val desc = Regex("""^\s*(--|//)[ \t]*@desc[ \t]+(.+)$""", RegexOption.MULTILINE)
    private val usageLine = Regex("""^\s*(--|//)\s*@usage\b[ \t]*(.*)$""", RegexOption.MULTILINE)
    private val argRef = Regex("""\b(args|argline)\b""")
    private val on = Regex("""^\s*(--|//)\s*@on\b(.*)$""", RegexOption.MULTILINE)
    private val usesLine = Regex("""^\s*(--|//)\s*@uses\b(.*)$""", RegexOption.MULTILINE)

    /**
     * What a script may ask for with `@uses`:
     * - `notifications`: notifications.recent and the `@on notification` trigger
     * - `clipboard`
     * - `apps`: apps.list
     * - `commands`: launcher.run (terminal commands and other scripts)
     * - `dnd`: device.dnd and device.setDnd (Do Not Disturb)
     * - `work`: device.work and device.setWork (the work profile)
     * The user allows each one ([ScriptAccess]).
     */
    val USES = listOf("notifications", "clipboard", "apps", "commands", "dnd", "work")

    /** Known names from every `@uses` line. Unknown names are ignored. */
    fun uses(code: String): Set<String> {
        val named = usesLine.findAll(code).flatMap { m -> usesNames(m.groupValues[2]) }.toSet()
        return USES.filterTo(LinkedHashSet()) { it in named }
    }

    /** The names on one `@uses` line, after `@uses`. They can be separated by commas, spaces or tabs, in any case. */
    fun usesNames(rest: String): List<String> = rest.split(',', ' ', '\t').map { it.trim().lowercase() }.filter { it.isNotEmpty() }

    /** Events a script can subscribe to with `@on`. */
    val EVENTS = listOf("unlock", "charger", "unplug", "wifi", "offline", "notification", "boot", "headset")

    fun isCommand(code: String) = command.containsMatchIn(code)

    /**
     * An `event` like the one that [name] brings (or that a daily [time] brings, `at`), with made-up
     * values. Used to try a script from the editor.
     */
    fun sampleEvent(name: String, time: String? = null): Map<String, Any?> = when (name) {
        "charger" -> mapOf("name" to name, "level" to 64, "plug" to "ac")
        "unplug" -> mapOf("name" to name, "level" to 64)
        "notification" -> mapOf(
            "name" to name, "app" to "Messages", "pkg" to "com.google.android.apps.messaging",
            "title" to "Alice", "text" to "Running late, there in 10", "time" to System.currentTimeMillis(),
        )
        "headset" -> mapOf("name" to name, "type" to "wired", "device" to "Headset", "mic" to true)
        "wifi" -> mapOf("name" to name, "network" to "wifi")
        "boot" -> mapOf("name" to name, "uptime" to 42)
        "at" -> mapOf("name" to name, "time" to (time ?: "09:00"))
        else -> mapOf("name" to name)
    }

    private val online = Regex("""\b(http|rss)\s*\.\s*\w""")

    /** Whether the code makes web requests or reads feeds (`http.`, `rss.`). A timed run of it waits for a network. */
    fun goesOnline(code: String) = online.containsMatchIn(code)

    private val internetRef = Regex("""\b(http|rss|url|image)\b|\blauncher\s*\.\s*run\b""")
    private val statusRef = Regex("""(?<![.:\w])status\b""")
    private val notifyRef = Regex("""(?<![.:\w@])notify\b""")

    /**
     * What a script uses beyond `@uses`, read from the names in its code. [ScriptAccess] switches and
     * allows only these:
     * - the internet, for `http` and `rss`, for the links and pictures its lines carry (`url`,
     *   `image`), and for `launcher.run` (a search sends its words to a site),
     * - its header line, for `status`,
     * - posting notifications, for `notify`.
     * Any mention of the name counts (a comment, `local status`), so a script that calls one by name
     * always has it. A field such as `res.status`, or the `@notify` line, doesn't count.
     */
    fun usesInternet(code: String) = internetRef.containsMatchIn(code)

    fun setsStatus(code: String) = statusRef.containsMatchIn(code)

    fun postsNotifications(code: String) = notifyRef.containsMatchIn(code)

    fun description(code: String): String? = desc.find(code)?.groupValues?.get(2)?.trim()?.ifEmpty { null }

    /** The text after a `@<name>` directive, or null when there is none or it is empty. */
    private fun value(code: String, name: String): String? =
        Regex("""^\s*(--|//)\s*@$name\b[ \t]*(.*)$""", RegexOption.MULTILINE).find(code)
            ?.groupValues?.get(2)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * `@secret OWM_KEY api.openweathermap.org Your OpenWeather API key`: a value you set in the
     * script's sheet or with `script secret`. The script reads it with `secrets.get("OWM_KEY")`.
     * It is sent only to [hosts] or their subdomains (`*` means any host).
     */
    data class Secret(val name: String, val hosts: List<String>, val desc: String) {
        fun allows(host: String): Boolean = hosts.any { it == "*" || host.equals(it, ignoreCase = true) || host.endsWith(".$it", ignoreCase = true) }
    }

    private val secretLine = Regex("""^\s*(--|//)\s*@secret\b[ \t]*(.*)$""", RegexOption.MULTILINE)
    private val secretName = Regex("^[A-Za-z][A-Za-z0-9_]{0,39}$")

    // After the name come the sites, joined by commas (spaces around them allowed), then the description.
    private val secretRest = Regex("""^([^\s,]+(?:\s*,\s*[^\s,]+)*)\s*(.*)$""")

    /** Every well-formed `@secret` line (a name, then its hosts). If a name appears twice, the first line wins. */
    fun secrets(code: String): List<Secret> = secretLine.findAll(code).mapNotNull { m ->
        val parts = m.groupValues[2].trim().split(Regex("\\s+"), limit = 2)
        val name = parts.getOrNull(0)?.takeIf(secretName::matches) ?: return@mapNotNull null
        val rest = secretRest.find(parts.getOrNull(1).orEmpty()) ?: return@mapNotNull null
        val hosts = rest.groupValues[1].split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        Secret(name, hosts, rest.groupValues[2].trim())
    }.distinctBy { it.name }.toList()

    /**
     * `@option city London The city to show`: a setting you change in the script's sheet or with
     * `script set`, without editing its code. The script reads it as `options.city`.
     * - The default is one word, `"quoted words"` or `""`.
     * - `metric|imperial` makes a choice. Its first value is the default.
     */
    data class Option(val name: String, val default: String, val choices: List<String>, val desc: String)

    private val optionLine = Regex("""^\s*(--|//)\s*@option\b[ \t]*(.*)$""", RegexOption.MULTILINE)
    private val optionName = Regex("^[A-Za-z][A-Za-z0-9_]{0,31}$")
    private val optionRest = Regex("^(\"[^\"]*\"|\\S+)\\s*(.*)$")

    /** Every well-formed `@option` line. If a name appears twice, the first line wins. */
    fun options(code: String): List<Option> = optionLine.findAll(code).mapNotNull { m ->
        val parts = m.groupValues[2].trim().split(Regex("\\s+"), limit = 2)
        val name = parts.getOrNull(0)?.takeIf(optionName::matches) ?: return@mapNotNull null
        val rest = optionRest.find(parts.getOrNull(1).orEmpty().trim()) ?: return@mapNotNull Option(name, "", emptyList(), "")
        val raw = rest.groupValues[1]
        val choices = if (!raw.startsWith('"') && '|' in raw) raw.split('|').filter { it.isNotEmpty() }.distinct() else emptyList()
        Option(name, choices.firstOrNull() ?: raw.removeSurrounding("\""), choices, rest.groupValues[2].trim())
    }.distinctBy { it.name }.toList()

    fun title(code: String): String? = value(code, "title")

    fun author(code: String): String? = value(code, "author")

    fun version(code: String): String? = value(code, "version")

    /** `@every 1h`: the interval it suggests, in minutes. Null if there is none (or it is `off`). */
    fun every(code: String): Int? =
        value(code, "every")?.let { com.mtwabpin.kolahal.terminal.CommandParsing.parseInterval(it) }?.takeIf { it > 0 }

    /** `@at 07:00, 18:30`: the daily times it suggests, as "HH:mm". */
    fun at(code: String): List<String> =
        value(code, "at")?.split(',', ' ')?.filter { it.isNotBlank() }?.mapNotNull(ScriptScheduler::normalizeTime)
            ?.distinct()?.sorted().orEmpty()

    /**
     * A script's interval after its code changes from [old] to [new]. [current] is the interval now.
     * - A new or changed `@every` line sets the interval.
     * - A removed `@every` line undoes the interval it had set. A timer set in the sheet stays.
     */
    fun intervalAfter(old: String, new: String, current: Int): Int {
        val was = every(old)?.coerceAtLeast(ScriptScheduler.MIN_INTERVAL_MIN)
        val now = every(new)?.coerceAtLeast(ScriptScheduler.MIN_INTERVAL_MIN)
        return when {
            now == was -> current
            now != null -> now
            current == was -> 0
            else -> current
        }
    }

    /** A script's daily times after its code changes from [old] to [new]. [current] is the value now. Same rule as [intervalAfter], for `@at` lines. */
    fun timesAfter(old: String, new: String, current: List<String>): List<String> {
        val was = at(old)
        val now = at(new)
        return when {
            now == was -> current
            now.isNotEmpty() -> now
            current.sorted() == was -> emptyList()
            else -> current
        }
    }

    /** `@notify`: the script suggests that it may post notifications. */
    fun notifies(code: String): Boolean = Regex("""^\s*(--|//)\s*@notify\b""", RegexOption.MULTILINE).containsMatchIn(code)

    fun requires(code: String): String? = value(code, "requires")

    /** True when [code]'s `@requires` names a newer Kolahal than [current] (a version name such as 0.4.0). */
    fun needsNewer(code: String, current: String): Boolean = requires(code)?.let { isNewer(it, current) } == true

    /** True when version [wanted] is newer than [current]. False when either one isn't a version. */
    fun isNewer(wanted: String, current: String): Boolean {
        val want = versionParts(wanted) ?: return false
        val have = versionParts(current) ?: return false
        for (i in 0 until maxOf(want.size, have.size)) {
            val w = want.getOrElse(i) { 0 }
            val h = have.getOrElse(i) { 0 }
            if (w != h) return w > h
        }
        return false
    }

    /** Turns "0.4.0" or "0.4.0-lite" into [0, 4, 0]. Null when it isn't a version. */
    private fun versionParts(v: String): List<Int>? =
        v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: return null }

    /**
     * The command's usage line, `name args…`, like a built-in one.
     * - From `@usage`, if there is one. The name in front is optional there.
     * - Just [name], when the code never reads `args` or `argline`, so it can't take any.
     * - Null when unknown. Then nothing is suggested after the name, and it never runs without Enter.
     */
    fun usage(code: String, name: String): String? {
        val m = usageLine.find(code) ?: return if (argRef.containsMatchIn(code)) null else name
        val rest = m.groupValues[2].trim()
        val args = if (rest == name || rest.startsWith("$name ")) rest.removePrefix(name).trim() else rest
        return if (args.isEmpty()) name else "$name $args"
    }

    /** Known events from every `@on` line, in [EVENTS] order. Unknown names are ignored. */
    fun events(code: String): List<String> {
        val named = words(code).toSet()
        return EVENTS.filter { it in named }
    }

    /** Names in `@on` lines that aren't events (for warnings). */
    fun unknownEvents(code: String): List<String> = words(code).filter { it !in EVENTS }.distinct()

    private fun words(code: String): List<String> =
        on.findAll(code).flatMap { m -> m.groupValues[2].split(',', ' ', '\t') }
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toList()
}
