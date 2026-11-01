package com.mtwabpin.kolahal.terminal

/**
 * Every built-in command: its usage and help line ([COMMANDS]), its place in `help` ([HELP_SECTIONS]),
 * and which build has it ([FEATURE_COMMANDS]). [CommandProcessor] runs them; sensitive ones are
 * listed in [InstantRun.SENSITIVE]. Each list is one entry per line, so two new commands don't collide.
 */
object CommandCatalog {
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
        CommandSpec("wallpaper", "wallpaper [match]", "match: set the home screen wallpaper to this theme's colour, following the theme, so it doesn't flash when you swipe home"),
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
        CommandSpec("log", "log [only <name|kind>|all]", "Show only one script's, feed's or app's lines, or one kind of line"),
        CommandSpec("fresh", "fresh", "Start a clean screen now, like log.fresh; earlier lines are one tap away (not logged)"),
        CommandSpec("restart", "restart [scripts]", "Restart the launcher, or stop running scripts"),
        CommandSpec("update", "update [install]", "Check for updates (install: download and install the latest)"),
        CommandSpec("status", "status [clear <name|all>]", "Header lines pinned by scripts"),
        CommandSpec("setup", "setup [text]", "Setup checklist page (text: print it in the log)"),
        CommandSpec("tour", "tour", "How Kolahal works, a page at a time, with a few initial settings"),
        CommandSpec("notifications", "notifications [settings]", "Allow script notifications"),
        CommandSpec("usage-access", "usage-access", "Grant usage access (unlock count)"),
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
        CommandSpec("font", "font [<name>|import|sans [off|prompt|all]]", "Terminal font: system jetbrains-mono fira-code ibm-plex-mono source-code-pro custom (no args: list); sans: the system font for the prompt or everything"),
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
        CommandSpec("notes", "notes [grep <text>|rm <n>]", "Your notes, newest first (tap to copy)"),
        CommandSpec("remind", "remind <when> <text>", "Notify later · 10m · 18:30 · tomorrow 9:00 · fri 8am · dec 3 10:00 · every mon 19:00 · rm <n>"),
        CommandSpec("reminders", "reminders", "Pending reminders"),
        CommandSpec("timer", "timer <5m|1h30m> [<label>]", "Start a timer in the clock app (no args: show timers)"),
        CommandSpec("alarm", "alarm <HH:MM> [<label>]", "Set an alarm in the clock app"),
        CommandSpec("alarms", "alarms", "Show the clock app's alarms"),
        CommandSpec("stopwatch", "stopwatch", "Open the clock app"),
        CommandSpec("agenda", "agenda [<days>|--allow|--settings]", "Upcoming calendar events (tap to open one)"),
    )

    /** Every built-in command name, for alias, script-name and typo checks. */
    val COMMAND_NAMES: Set<String> = COMMANDS.map { it.name }.toSet()

    /** Names a script or an alias can't take: the commands and the other names they answer to. */
    val RESERVED_NAMES: Set<String> = COMMAND_NAMES + InstantRun.SYNONYMS.keys

    /**
     * The commands of each feature the Play build leaves out (screen lock, call log, self-update), with whether
     * this build has it.
     */
    internal val FEATURE_COMMANDS = listOf(
        com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE to setOf("lock", "accessibility"),
        com.mtwabpin.kolahal.BuildConfig.CALL_LOG to setOf("calls"),
        com.mtwabpin.kolahal.BuildConfig.SELF_UPDATE to setOf("update"),
    )

    /**
     * Commands whose feature this build leaves out: not suggested or listed in help, so the Play
     * build doesn't offer what it can't do. Typed anyway, each says why it isn't there.
     */
    val UNAVAILABLE: Set<String> = FEATURE_COMMANDS.filterNot { it.first }.flatMap { it.second }.toSet()

    /** Usage line per command name, for option completion and instant runs. */
    val COMMAND_USAGES: Map<String, String> = COMMANDS.associate { it.name to it.usage }

    /** Commands handled by [ProductivityCommands]. */
    val PRODUCTIVITY = setOf(
        "todo",
        "note",
        "notes",
        "remind",
        "reminders",
        "timer",
        "alarm",
        "alarms",
        "stopwatch",
        "agenda",
    )

    /** `help` groups; commands not listed here are shown after the groups. */
    val HELP_SECTIONS = linkedMapOf(
        "apps" to listOf(
            "open",
            "shortcuts",
            "apps",
            "pin",
            "unpin",
            "info",
            "uninstall",
            "hide",
            "unhide",
            "rename",
            "usage",
            "installs",
        ),
        "people" to listOf(
            "call",
            "sms",
            "contacts",
            "calls",
        ),
        "web" to listOf(
            "search",
            "yt",
            "wiki",
            "maps",
            "play",
            "url",
        ),
        "feeds" to listOf(
            "feeds",
            "feed",
            "rss",
            "notifs",
        ),
        "control" to com.mtwabpin.kolahal.control.ControlCommands.SPECS.map { it.name },
        "profiles" to listOf(
            "work",
            "private",
        ),
        "productivity" to listOf(
            "todo",
            "note",
            "notes",
            "remind",
            "reminders",
            "timer",
            "alarm",
            "alarms",
            "stopwatch",
            "agenda",
        ),
        "scripts" to listOf(
            "scripts",
            "script",
            "lua",
            "js",
            "=",
            "status",
        ),
        "terminal" to listOf(
            "help",
            "history",
            "clear",
            "fresh",
            "restart",
            "update",
            "alias",
            "unalias",
            "config",
            "theme",
            "font",
            "backup",
            "echo",
            "date",
            "cal",
        ),
        "widgets" to listOf(
            "widgets",
            "widget",
        ),
        "device" to listOf(
            "settings",
            "setup",
            "tour",
            "source",
            "wallpaper",
            "unlocks",
            "notifications",
            "usage-access",
            "crashes",
            "battery",
            "home",
            "lock",
            "shade",
            "accessibility",
            "system",
        ),
    )
}
