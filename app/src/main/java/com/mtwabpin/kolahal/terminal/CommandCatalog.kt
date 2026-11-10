package com.mtwabpin.kolahal.terminal

/**
 * Every built-in command, with:
 * - its usage, its help, and what the `help` list and `help <command>` show ([COMMANDS], [CommandSpec]);
 * - its place in `help` ([HELP_SECTIONS]);
 * - which build has it ([FEATURE_COMMANDS]).
 *
 * [CommandProcessor] runs the commands. Sensitive ones are listed in [InstantRun.SENSITIVE].
 * Each list has one entry per line, so that two new commands added at once don't conflict.
 */
object CommandCatalog {
    val COMMANDS = listOf(
        CommandSpec("help", "help [<command>]", "Show this list, or just one command", short = "This list", args = "<command>"),
        *com.mtwabpin.kolahal.control.ControlCommands.SPECS.toTypedArray(),
        CommandSpec(
            "apps", "apps [work|personal|private|hidden] [-l|-a]", "App grid (-l: one per line, -a: hidden apps too)",
            short = "Your apps as a grid", args = "work · personal · private · hidden · -l · -a",
            uses = listOf(
                "apps" to "your apps as a grid",
                "apps work" to "only the work profile's apps",
                "apps personal" to "only the personal apps",
                "apps private" to "only the private space's apps",
                "apps hidden" to "only the hidden apps",
                "apps -l" to "one app per line",
                "apps -a" to "hidden apps too",
            ),
        ),
        CommandSpec("open", "open <app>[:shortcut] [-w|-p|-s]", "Launch an app or one of its shortcuts", short = "Open an app or shortcut", args = "<app>[:shortcut] · -w · -p · -s"),
        CommandSpec("shortcuts", "shortcuts <app>", "An app's launcher shortcuts", short = "An app's shortcuts", args = "<app>"),
        CommandSpec("info", "info <app> [-w]", "Open app info", short = "An app's info page", args = "<app> · -w"),
        CommandSpec("uninstall", "uninstall <app> [-w]", "Uninstall an app", short = "Uninstall an app", args = "<app> · -w"),
        CommandSpec(
            "work", "work [on|off|status]", "Pause / resume the work profile",
            short = "Pause the work profile", args = "on · off · status",
            uses = listOf(
                "work" to "whether it is on or paused",
                "work on" to "resume the work profile",
                "work off" to "pause it",
            ),
        ),
        CommandSpec(
            "private", "private [on|off|status]", "Lock / unlock private space",
            short = "Lock private space", args = "on · off · status",
            uses = listOf(
                "private" to "whether it is unlocked or locked",
                "private on" to "unlock private space",
                "private off" to "lock it",
            ),
        ),
        CommandSpec("unlocks", "unlocks [<days>]", "Unlock counts", short = "Unlock counts", args = "<days>"),
        CommandSpec("fetch", "fetch", "This phone and Kolahal at a glance, fastfetch style", short = "This phone at a glance"),
        CommandSpec("source", "source", "Open Kolahal's source code on GitHub", short = "Source code on GitHub"),
        CommandSpec("wallpaper", "wallpaper [match]", "match: set the home screen wallpaper to this theme's colour, following the theme, so it doesn't flash when you swipe home", short = "Wallpaper in theme colour", args = "match"),
        CommandSpec(
            "script", "script <sub> <name>", "Subcommands: ls lib install get new edit run every at notify on off access allow deny pin unpin secret set triggers mv rm cat diff dir export import",
            short = "Manage scripts",
            args = "ls · lib · install · get · new · edit · run · every · at · notify · on · off · access · allow · deny · pin · unpin · secret · set · triggers · mv · rm · cat · diff · dir · export · import",
            uses = listOf(
                "script ls" to "your scripts, with timers and last results",
                "script lib" to "the bundled library",
                "script install <name>" to "install or update a library script",
                "script get [<name>]" to "shared scripts, or install one",
                "script new <name>" to "a new script from a template",
                "script edit <name>" to "open the editor",
                "script run <name> [<words>]" to "run it now",
                "script cat <name>" to "print its code",
                "script diff <name>" to "compare it with the library's copy",
                "script every <name> <15m|off>" to "run it on a timer",
                "script at <name> <HH:mm|off>" to "run it at times each day",
                "script notify <name> on|off" to "let it notify you, or not",
                "script on|off <name>" to "turn it on or off",
                "script triggers" to "scripts that run on events",
                "script access <name>" to "what it may use",
                "script allow|deny <name> <what>" to "turn one access on or off",
                "script pin|unpin <name>" to "its status line at the top, or not",
                "script secret <name> [<NAME>]" to "its secrets, or set one",
                "script set <name> [<option> <value>]" to "its options, or set one",
                "script mv <name> <new-name>" to "rename it",
                "script rm <name>" to "delete it, with its data",
                "script dir" to "pick the sync folder",
                "script export" to "write every script to the folder",
                "script import" to "read the scripts back from the folder",
            ),
        ),
        CommandSpec("=", "= <expr>", "Calculator · = 15% of 80 · = 5 km in mi · = 100 usd in eur", short = "Calculator", args = "15% of 80 · 5 km in mi · 100 usd in eur"),
        CommandSpec("lua", "lua <code>", "Evaluate Lua 5.4", short = "Run Lua", args = "<code>"),
        CommandSpec("js", "js <code>", "Evaluate JavaScript (QuickJS)", short = "Run JavaScript", args = "<code>"),
        CommandSpec("rss", "rss <url>", "Fetch a feed into the log once", short = "Read a feed once", args = "<url>"),
        CommandSpec(
            "feed", "feed [add <url>|url <id> <url>|ls|rm|fetch|read|all|open|notify|every|on|off] [<id>]", "RSS/Atom subscriptions with unread counts (no args: text list)",
            short = "Your feeds, with unread", args = "add <url> · ls · rm · fetch · read · all · open · notify · every · on · off · url <id> <url>",
            uses = listOf(
                "feed" to "your feeds, with unread counts",
                "feed add <url>" to "subscribe, and fetch it now",
                "feed url <id> <url>" to "change a feed's address",
                "feed rm <id>" to "unsubscribe, and delete its items",
                "feed fetch [<id>]" to "check for new items now",
                "feed read [<id>]" to "unread items, then mark them read",
                "feed all [<id>]" to "the newest items, read or not",
                "feed open [<id>]" to "open up to 5 unread items in the browser",
                "feed notify <id> on|off" to "notify you of new items, or not",
                "feed every <id> <2h>" to "how often to check (15m at least)",
                "feed on|off <id>" to "turn a feed on, or pause it",
            ),
        ),
        CommandSpec("feeds", "feeds", "Feed manager screen (add, intervals, notifications, OPML)", short = "Feed manager"),
        CommandSpec("widgets", "widgets [ls]", "Settings → Widgets → Arrange widgets: add, resize and arrange them (ls: text list)", short = "Arrange widgets", args = "ls"),
        CommandSpec("widget", "widget add", "Pick a widget to add to the widgets page", short = "Add a widget", args = "add"),
        CommandSpec(
            "notifs", "notifs [all|enable|mute <app>|unmute <app>|muted|log [on|off]|clear]", "Your notifications in the terminal",
            short = "Your notifications", args = "all · enable · mute <app> · unmute <app> · muted · log [on|off] · clear",
            uses = listOf(
                "notifs" to "the notifications showing now",
                "notifs all" to "dismissed ones too",
                "notifs enable" to "allow notification access",
                "notifs mute <app>" to "keep an app's notifications out of the log",
                "notifs unmute <app>" to "let them in again",
                "notifs muted" to "the muted apps",
                "notifs log on|off" to "notifications in the log, or not",
                "notifs clear" to "dismiss them all",
            ),
        ),
        CommandSpec("search", "search <query>", "Web search (engine: config search.engine)", short = "Search the web", args = "<query>"),
        CommandSpec("yt", "yt <query>", "Search YouTube", short = "Search YouTube", args = "<query>"),
        CommandSpec("wiki", "wiki <query>", "Search Wikipedia", short = "Search Wikipedia", args = "<query>"),
        CommandSpec("maps", "maps <place>", "Find a place in your maps app", short = "Find a place", args = "<place>"),
        CommandSpec("play", "play <query>", "Search the Play Store", short = "Search the Play Store", args = "<query>"),
        CommandSpec("usage", "usage [<days>]", "Screen time per app", short = "Screen time per app", args = "<days>"),
        CommandSpec("installs", "installs [<n>]", "Recent app installs, updates and uninstalls", short = "Recent installs", args = "<n>"),
        // The Play build has no READ_CONTACTS. There, a command without a name opens Android's contact picker.
        if (com.mtwabpin.kolahal.BuildConfig.CONTACTS) CommandSpec("call", "call <name|number>", "Dial a contact or number", short = "Call someone", args = "<name|number>")
        else CommandSpec("call", "call [<name|number>]", "Dial a contact or number", short = "Call someone", args = "[<name|number>]"),
        if (com.mtwabpin.kolahal.BuildConfig.CONTACTS) CommandSpec("sms", "sms <name|number> [<text>]", "Start a text message", short = "Text someone", args = "<name|number> [<text>]")
        else CommandSpec("sms", "sms [<name|number> [<text>]]", "Start a text message", short = "Text someone", args = "[<name|number> [<text>]]"),
        if (com.mtwabpin.kolahal.BuildConfig.CONTACTS) CommandSpec("contacts", "contacts [<query>|--allow]", "Find contacts (favourites when empty)", short = "Find a contact", args = "<query> · --allow")
        else CommandSpec("contacts", "contacts [<query>|--forget <name>]", "Pick a contact, or find one picked before", short = "Pick a contact", args = "<query> · --forget <name>"),
        CommandSpec("calls", "calls [<n>|--allow|--settings]", "Recent calls: incoming, outgoing and missed", short = "Recent calls", args = "<n> · --allow · --settings"),
        CommandSpec("url", "url <address>", "Open a link", short = "Open a link", args = "<address>"),
        CommandSpec("history", "history", "Recent commands (tap to reuse)", short = "Recent commands"),
        CommandSpec(
            "clear", "clear [history|all]", "Clear the screen, the ↑ history, or both",
            short = "Clear the screen", args = "history · all",
            uses = listOf(
                "clear" to "clear the screen",
                "clear history" to "clear the ↑ history",
                "clear all" to "clear both",
            ),
        ),
        CommandSpec(
            "backup", "backup [now|every <daily|weekly|off>|dir]", "Back up everything to a folder now, or every day or week",
            short = "Back up everything", args = "now · every <daily|weekly|off> · dir",
            uses = listOf(
                "backup" to "back up now; the first time, pick a folder",
                "backup every daily" to "back up every day",
                "backup every weekly" to "back up every week",
                "backup every off" to "stop automatic backups",
                "backup dir" to "pick the backup folder",
            ),
        ),
        CommandSpec(
            "log", "log [only <name|kind>|all]", "Show only one script's, feed's or app's lines, one kind of line, or the starred ones",
            short = "Show only some lines", args = "only <name|kind> · all",
            uses = listOf(
                "log only <name>" to "only one script's, feed's or app's lines",
                "log only <kind>" to "only commands, scripts, feeds, notifications, apps, calls, reminders or starred lines",
                "log all" to "every line again",
            ),
        ),
        CommandSpec("fresh", "fresh", "Start a clean screen now, like log.fresh; earlier lines are one tap away (not logged)", short = "A clean screen"),
        CommandSpec("restart", "restart [scripts]", "Restart the launcher, or stop running scripts", short = "Restart Kolahal", args = "scripts"),
        CommandSpec("update", "update [install]", "Check for updates (install: download and install the latest)", short = "Check for updates", args = "install"),
        CommandSpec(
            "status", "status [clear <name|all>]", "Scripts' status lines, the pinned ones marked",
            short = "Scripts' status lines", args = "clear <name|all>",
            uses = listOf(
                "status" to "scripts' status lines, the pinned ones marked",
                "status clear <name>" to "remove one script's line",
                "status clear" to "remove them all",
            ),
        ),
        CommandSpec("setup", "setup [text]", "Setup checklist page (text: print it in the log)", short = "Setup checklist", args = "text"),
        CommandSpec("tour", "tour", "How Kolahal works, a page at a time, with a few initial settings", short = "How Kolahal works"),
        CommandSpec("notifications", "notifications [settings]", "Allow script notifications", short = "Allow script notifications", args = "settings"),
        CommandSpec("usage-access", "usage-access", "Grant usage access (unlock count)", short = "Allow usage access"),
        CommandSpec(
            "crashes", "crashes [share|clear] [<n>]", "Crash reports kept on this phone; share one to report a bug",
            short = "Crash reports", args = "share <n> · clear",
            uses = listOf(
                "crashes" to "crash reports, newest first",
                "crashes share [<n>]" to "share one; the newest by default",
                "crashes clear" to "delete them all",
            ),
        ),
        CommandSpec("battery", "battery", "Battery optimization settings", short = "Battery optimization"),
        CommandSpec("home", "home", "Choose the default launcher", short = "Choose the home app"),
        CommandSpec("lock", "lock", "Lock the screen (needs the accessibility service)", short = "Lock the screen"),
        CommandSpec("shade", "shade", "Pull down the notification shade", short = "Pull down notifications"),
        CommandSpec("accessibility", "accessibility", "Enable the screen-lock service for double-tap", short = "Turn on screen lock"),
        CommandSpec("settings", "settings", "Kolahal settings screen (+ backup import/export)", short = "Kolahal settings"),
        CommandSpec("scripts", "scripts", "Script manager screen (import files, timers)", short = "Script manager"),
        CommandSpec("system", "system", "Android system settings", short = "Android settings"),
        CommandSpec(
            "config", "config [ls|set|get|reset] <key> <v>", "Settings (no args opens the settings page)",
            short = "Settings by name", args = "ls · set · get · reset <key> [<value>]",
            uses = listOf(
                "config" to "open the settings page",
                "config ls" to "every setting; tap one to change it",
                "config get <key>" to "one setting's value",
                "config set <key> <value>" to "change a setting",
                "config reset <key>" to "put one back to its default",
                "config reset all" to "put them all back",
            ),
        ),
        CommandSpec("theme", "theme <name>", "Themes: green amber mono dracula solarized gruvbox gruvbox-material", short = "Colours", args = "<name>"),
        CommandSpec(
            "font", "font [<name>|import|sans [off|prompt|all]]", "Terminal font: system jetbrains-mono fira-code ibm-plex-mono source-code-pro custom (no args: list); sans: the system font for the prompt or everything",
            short = "Terminal font", args = "<name> · import · sans [off|prompt|all]",
            uses = listOf(
                "font" to "the fonts; tap one to use it",
                "font <name>" to "use a font",
                "font import" to "add your own .ttf or .otf",
                "font sans prompt" to "the system font for the prompt",
                "font sans all" to "the system font for everything",
                "font sans off" to "the terminal font everywhere",
            ),
        ),
        CommandSpec("date", "date [<place>...]", "Current date and time, or the time in a city, a zone, utc or an offset", short = "Date and time", args = "<place>…"),
        CommandSpec("echo", "echo <text>", "Print text", short = "Print text", args = "<text>"),
        CommandSpec("cal", "cal [<month>] [<year>]", "The month as a calendar, today in bold · cal dec · cal dec 2027 · cal 2027-12", short = "A month's calendar", args = "<month> [<year>]"),
        CommandSpec(
            "alias", "alias [name = command]", "List or define aliases",
            short = "Your aliases", args = "<name> = <command>",
            uses = listOf(
                "alias" to "your aliases",
                "alias <name>" to "what one stands for",
                "alias <name> = <command>" to "make one",
                "unalias <name>" to "remove one",
            ),
        ),
        CommandSpec("unalias", "unalias <name>", "Remove an alias", short = "Remove an alias", args = "<name>"),
        CommandSpec("pin", "pin <app>", "Favourite: first on an empty prompt (suggestions.idle) and starred in the app selector (pins lists them)", short = "Pin an app", args = "<app>"),
        CommandSpec("unpin", "unpin <app>", "Remove a favourite", short = "Unpin an app", args = "<app>"),
        CommandSpec("pins", "pins", "Grid of pinned apps", short = "Your pinned apps"),
        CommandSpec("hide", "hide <app> [-w]", "Hide an app from lists and search", short = "Hide an app", args = "<app> · -w"),
        CommandSpec("unhide", "unhide <app>", "Show a hidden app again ('apps hidden')", short = "Show a hidden app", args = "<app>"),
        CommandSpec("rename", "rename <app> = <name>", "Custom label (empty restores)", short = "Rename an app", args = "<app> = <name>"),
        CommandSpec(
            "todo", "todo [<text>|add|done|undo|edit|rm <n>|all|clear]", "Todo list (tap an item to tick it)",
            short = "Your todo list", args = "<text> · add · done · undo · edit · rm <n> · all · clear",
            uses = listOf(
                "todo" to "the list",
                "todo <text>" to "add an item",
                "todo done <n>" to "tick one",
                "todo undo <n>" to "untick one",
                "todo edit <n> <text>" to "change one",
                "todo rm <n>" to "remove one",
                "todo all" to "with the ticked ones",
                "todo clear" to "remove the ticked ones",
            ),
        ),
        CommandSpec("note", "note <text>", "Save a quick note", short = "Save a quick note", args = "<text>"),
        CommandSpec(
            "notes", "notes [grep <text>|rm <n>]", "Your notes, newest first (tap to copy)",
            short = "Your notes", args = "grep <text> · rm <n>",
            uses = listOf(
                "notes" to "your notes, newest first; tap one to copy it",
                "notes grep <text>" to "only the notes with this text",
                "notes rm <n>" to "remove one",
            ),
        ),
        CommandSpec("remind", "remind <when> <text>", "Notify later · 10m · 18:30 · tomorrow 9:00 · fri 8am · dec 3 10:00 · every mon 19:00 · rm <n>", short = "Remind you later", args = "<when> <text> · rm <n>"),
        CommandSpec("reminders", "reminders", "Pending reminders", short = "Pending reminders"),
        CommandSpec("timer", "timer <5m|1h30m> [<label>]", "Start a timer in the clock app (no args: show timers)", short = "A timer in the clock app", args = "<5m|1h30m> [<label>]"),
        CommandSpec("alarm", "alarm <HH:MM> [<label>]", "Set an alarm in the clock app", short = "An alarm in the clock app", args = "<HH:MM> [<label>]"),
        CommandSpec("alarms", "alarms", "Show the clock app's alarms", short = "The clock app's alarms"),
        CommandSpec("stopwatch", "stopwatch", "Open the clock app", short = "Open the clock app"),
        CommandSpec("agenda", "agenda [<days>|--allow|--settings]", "Upcoming calendar events (tap to open one)", short = "Upcoming events", args = "<days> · --allow · --settings"),
    )

    /** Every built-in command name. Used to check aliases, script names and typos. */
    val COMMAND_NAMES: Set<String> = COMMANDS.map { it.name }.toSet()

    /** Names that a script or an alias can't use: the command names and their synonyms. */
    val RESERVED_NAMES: Set<String> = COMMAND_NAMES + InstantRun.SYNONYMS.keys

    /**
     * For each feature that the Play build leaves out (screen lock, call log, self-update):
     * whether this build has it, and its commands.
     */
    internal val FEATURE_COMMANDS = listOf(
        com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE to setOf("lock", "accessibility"),
        com.mtwabpin.kolahal.BuildConfig.CALL_LOG to setOf("calls"),
        com.mtwabpin.kolahal.BuildConfig.SELF_UPDATE to setOf("update"),
    )

    /**
     * Commands whose feature this build leaves out. They are not suggested or listed in help,
     * so the Play build doesn't offer what it can't do. If the user types one anyway, it explains why it is missing.
     */
    val UNAVAILABLE: Set<String> = FEATURE_COMMANDS.filterNot { it.first }.flatMap { it.second }.toSet()

    /** The commands this build offers. Help lists them and completion suggests them. */
    val OFFERED: List<CommandSpec> = COMMANDS.filter { it.name !in UNAVAILABLE }
    val OFFERED_NAMES: Set<String> = OFFERED.mapTo(LinkedHashSet()) { it.name }

    /** The usage line for each command name, for option completion and instant runs. */
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

    /** The groups in `help`. Every offered command is in one (a test checks this). Any that is not shows after the groups. */
    val HELP_SECTIONS = linkedMapOf(
        "apps" to listOf(
            "open",
            "shortcuts",
            "apps",
            "pin",
            "unpin",
            "pins",
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
            "log",
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
            "fetch",
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
