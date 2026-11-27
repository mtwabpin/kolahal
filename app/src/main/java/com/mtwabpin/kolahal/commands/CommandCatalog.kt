/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.commands

/**
 * Every built-in command, with:
 * - its usage, its help, and what the `help` list and `help <command>` show ([COMMANDS], [CommandSpec]);
 * - its place in `help` ([HELP_SECTIONS]);
 * - which build has it ([FEATURE_COMMANDS]);
 * - whether it is offered at all ([HIDDEN]);
 * - which home style offers it ([TtyCommands]).
 *
 * `CommandProcessor` (in terminal) runs the commands. Each one's other names and whether it is sensitive are in its entry ([CommandSpec]).
 * Each list has one entry per line, so that two new commands added at once don't conflict.
 */
object CommandCatalog {
    val COMMANDS = listOf(
        CommandSpec(
            "help",
            "help [<command>]",
            "Show this list, or just one command",
            short = "This list",
            args = "<command>",
            aka = listOf("?")
        ),
        // Offered to nobody ([HIDDEN]): it runs only when typed in full. `help skill.md` typed in full shows this.
        CommandSpec(
            "skill.md",
            "skill.md [shaders|widgets]",
            "Copy a guide for an AI assistant on how to write scripts and shaders for Kolahal: paste it into the assistant, then say what to build. With shaders or widgets, a shorter guide for just that",
            short = "Copy the guide for an AI assistant",
            args = "shaders · widgets",
            uses = SkillText.Guide.entries.map { it.command to SkillText.about(it) },
            sensitive = true,
        ),
        *ControlSpecs.SPECS.toTypedArray(),
        CommandSpec(
            "apps",
            "apps [work|personal|private|hidden] [-l|-a]",
            "App grid (-l: one per line, -a: hidden apps too)",
            short = "Your apps as a grid",
            args = "work · personal · private · hidden · -l · -a",
            uses = listOf(
                "apps" to "your apps as a grid",
                "apps work" to "only the work profile's apps",
                "apps personal" to "only the personal apps",
                "apps private" to "only the private space's apps",
                "apps hidden" to "only the hidden apps",
                "apps -l" to "one app per line",
                "apps -a" to "hidden apps too",
            ),
            aka = listOf("ls"),
        ),
        CommandSpec(
            "open",
            "open <app>[:shortcut] [-w|-p|-s]",
            "Launch an app or one of its shortcuts",
            short = "Open an app or shortcut",
            args = "<app>[:shortcut] · -w · -p · -s",
            aka = listOf("launch")
        ),
        CommandSpec(
            "shortcuts",
            "shortcuts <app>",
            "An app's launcher shortcuts",
            short = "An app's shortcuts",
            args = "<app>"
        ),
        CommandSpec(
            "activity",
            "activity <app> [<name>]",
            "List an app's activities, or open one: a screen without an icon",
            short = "Any screen of an app",
            args = "<app> [<name>]",
            uses = listOf(
                "activity <app>" to "the screens other apps may open; tap one to open it",
                "activity <app> <name>" to "open one by its name or class, for an alias",
            ),
            sensitive = true,
        ),
        CommandSpec(
            "info",
            "info <app> [-w]",
            "Open app info",
            short = "An app's info page",
            args = "<app> · -w",
            sensitive = true
        ),
        CommandSpec(
            "uninstall",
            "uninstall <app> [-w]",
            "Uninstall an app",
            short = "Uninstall an app",
            args = "<app> · -w",
            sensitive = true
        ),
        CommandSpec(
            "work", "work [on|off|status]", "Pause / resume the work profile",
            short = "Pause the work profile", args = "on · off · status",
            uses = listOf(
                "work" to "whether it is on or paused",
                "work on" to "resume the work profile",
                "work off" to "pause it",
            ),
            sensitive = true,
        ),
        CommandSpec(
            "private", "private [on|off|status]", "Lock / unlock private space",
            short = "Lock private space", args = "on · off · status",
            uses = listOf(
                "private" to "whether it is unlocked or locked",
                "private on" to "unlock private space",
                "private off" to "lock it",
            ),
            sensitive = true,
        ),
        CommandSpec(
            "unlocks",
            "unlocks [<days> | week | month]",
            "Unlock counts; week and month as a chart",
            short = "Unlock counts",
            args = "<days> · week · month"
        ),
        CommandSpec(
            "fetch",
            "fetch",
            "This phone and Kolahal at a glance, fastfetch style",
            short = "This phone at a glance",
            aka = listOf("neofetch", "fastfetch")
        ),
        CommandSpec(
            "source",
            "source",
            "Open Kolahal's source code on GitHub",
            short = "Source code on GitHub",
            sensitive = true
        ),
        CommandSpec(
            "wallpaper",
            "wallpaper [theme|shader <name>|live]",
            "What your wallpaper is, and what it can become. theme: a plain wallpaper in your theme's color, so your wallpaper doesn't flash when you swipe home. shader: a shader as your live wallpaper. live: Android's list of live wallpapers",
            short = "Your wallpaper",
            args = "theme · shader <name> · live",
            uses = listOf(
                "wallpaper" to "what your wallpaper is: your own, the theme's color or a shader; then what it can become, to tap",
                "wallpaper theme" to "a plain wallpaper in your theme's color, which follows the theme, so your wallpaper doesn't flash when you swipe home",
                "wallpaper shader <name>" to "a shader as your live wallpaper: Android's preview opens, for the home screen, the lock screen or both",
                "wallpaper live" to "Android's own list of live wallpapers, for a phone whose wallpaper settings don't link to it",
            ),
            sensitive = true
        ),
        CommandSpec(
            "script",
            "script <sub> <name>",
            "Subcommands: ls lib install get sources source new edit run every at notify on off access allow deny pin unpin secret set triggers mv rm cat diff dir export import",
            short = "Manage scripts",
            args = "ls · lib · install · get · sources · source · new · edit · run · every · at · notify · on · off · access · allow · deny · pin · unpin · secret · set · triggers · mv · rm · cat · diff · dir · export · import",
            uses = listOf(
                "script ls" to "your scripts, with timers and last results",
                "script lib" to "the bundled library",
                "script install <name>" to "install or update a library script",
                "script get [<name>]" to "shared scripts, or install one",
                "script sources" to "where Get more scripts reads from",
                "script source add <owner/name>" to "add a scripts repository",
                "script source rm <name>" to "remove a source",
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
            sensitive = true,
        ),
        CommandSpec(
            "=",
            "= <expr>",
            "Calculator · = 15% of 80 · = 5 km in mi · = 100 usd in eur",
            short = "Calculator",
            args = "15% of 80 · 5 km in mi · 100 usd in eur",
            sensitive = true
        ),
        CommandSpec(
            "lua",
            "lua <code>",
            "Evaluate Lua 5.4",
            short = "Run Lua",
            args = "<code>",
            sensitive = true
        ),
        CommandSpec(
            "js",
            "js <code>",
            "Evaluate JavaScript (QuickJS)",
            short = "Run JavaScript",
            args = "<code>",
            sensitive = true
        ),
        CommandSpec(
            "rss",
            "rss <url>",
            "Fetch a feed into the log once",
            short = "Read a feed once",
            args = "<url>",
            sensitive = true
        ),
        CommandSpec(
            "feed",
            "feed [add <url>|url <id> <url>|ls|rm|fetch|read|all|open|notify|every|on|off] [<id>]",
            "RSS/Atom subscriptions with unread counts (no args: text list)",
            short = "Your feeds, with unread",
            args = "add <url> · ls · rm · fetch · read · all · open · notify · every · on · off · url <id> <url>",
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
            sensitive = true,
        ),
        CommandSpec(
            "feeds",
            "feeds",
            "Feed manager screen (add, intervals, notifications, OPML)",
            short = "Feed manager",
            sensitive = true
        ),
        CommandSpec(
            "widgets",
            "widgets",
            "Settings → Widgets → Arrange widgets: add, resize and arrange them",
            short = "Arrange widgets",
        ),
        CommandSpec(
            "widget",
            "widget [add|ls|page [<page>]]",
            "Add a widget, list the placed ones, or turn home to a widgets page (no args: text list)",
            short = "Add or list widgets",
            args = "add · ls · page",
            uses = listOf(
                "widget add" to "pick a widget to add to the widgets page shown last",
                "widget ls" to "the placed widgets, as text, by page",
                "widget page" to "turn home to the widgets page, page 1",
                "widget page 2" to "turn home to page 2, or to a page by its name: widget page work",
            ),
            sensitive = true
        ),
        CommandSpec(
            "notifs",
            "notifs [all|mute <app>|unmute <app>|muted|log [on|off]|clear]",
            "Your notifications in the terminal",
            short = "Your notifications",
            args = "all · mute <app> · unmute <app> · muted · log [on|off] · clear",
            uses = listOf(
                "notifs" to "the notifications showing now",
                "notifs all" to "dismissed ones too",
                "notifs mute <app>" to "keep an app's notifications out of the log",
                "notifs unmute <app>" to "let them in again",
                "notifs muted" to "the muted apps",
                "notifs log on|off" to "notifications in the log, or not",
                "notifs clear" to "dismiss them all",
            ),
            aka = listOf("notifications"),
            sensitive = true,
        ),
        CommandSpec(
            "search",
            "search <query>",
            "Web search (engine: config search.engine)",
            short = "Search the web",
            args = "<query>",
            aka = listOf("g")
        ),
        CommandSpec(
            "yt",
            "yt <query>",
            "Search YouTube",
            short = "Search YouTube",
            args = "<query>",
            aka = listOf("youtube")
        ),
        CommandSpec(
            "wiki",
            "wiki <query>",
            "Search Wikipedia",
            short = "Search Wikipedia",
            args = "<query>"
        ),
        CommandSpec(
            "maps",
            "maps <place>",
            "Find a place in your maps app",
            short = "Find a place",
            args = "<place>",
            aka = listOf("map")
        ),
        CommandSpec(
            "play",
            "play <query>",
            "Search the Play Store",
            short = "Search the Play Store",
            args = "<query>",
            aka = listOf("store")
        ),
        CommandSpec(
            "storage",
            "storage",
            "Free space, the largest apps, and apps not opened for 30 days",
            short = "Storage and unused apps"
        ),
        CommandSpec(
            "usage",
            "usage [<days> | <app> | summary | week | month | year | full] [<app>]",
            "Screen time per app, or one app in detail; over Android's weeks, months and years with a word. ≈ marks Android's own totals: about right, not exact. Its weeks and months start where the one before ended, not on Monday or the 1st. Tap a line of the summary to see that level.",
            short = "Screen time per app",
            args = "<days> · <app> · summary · week · month · year · full",
            uses = listOf(
                "usage" to "time per app today",
                "usage <days>" to "time per app over 1 to 30 days",
                "usage <app>" to "one app: today by hour, and its last 7 days",
                "usage summary" to "each level: how far back, and the time a day",
                "usage week" to "a row a week: its total and its time a day; the change; the top apps",
                "usage month" to "the same, a row a month",
                "usage year" to "a row a year, its top apps, and apps used less or new",
                "usage month <app>" to "one app: a row a month, its share of screen time, when last used",
                "usage full" to "the summary and every level",
            ),
            aka = listOf("screentime")
        ),
        CommandSpec(
            "installs",
            "installs [<n>]",
            "Recent app installs, updates and uninstalls",
            short = "Recent installs",
            args = "<n>",
            aka = listOf("updates")
        ),
        // The Play build has no READ_CONTACTS. There, a command without a name opens Android's contact picker.
        if (com.mtwabpin.kolahal.BuildConfig.CONTACTS) CommandSpec(
            "call",
            "call <name|number>",
            "Dial a contact or number",
            short = "Call someone",
            args = "<name|number>",
            aka = listOf("dial"),
            sensitive = true
        )
        else CommandSpec(
            "call",
            "call [<name|number>]",
            "Dial a contact or number",
            short = "Call someone",
            args = "[<name|number>]",
            aka = listOf("dial"),
            sensitive = true
        ),
        if (com.mtwabpin.kolahal.BuildConfig.CONTACTS) CommandSpec(
            "sms",
            "sms <name|number> [<text>]",
            "Start a text message",
            short = "Text someone",
            args = "<name|number> [<text>]",
            aka = listOf("text"),
            sensitive = true
        )
        else CommandSpec(
            "sms",
            "sms [<name|number> [<text>]]",
            "Start a text message",
            short = "Text someone",
            args = "[<name|number> [<text>]]",
            aka = listOf("text"),
            sensitive = true
        ),
        if (com.mtwabpin.kolahal.BuildConfig.CONTACTS) CommandSpec(
            "contacts",
            "contacts [<query>]",
            "Find contacts (favorites when empty)",
            short = "Find a contact",
            args = "<query>",
            aka = listOf("contact")
        )
        else CommandSpec(
            "contacts",
            "contacts [<query>|--forget <name>]",
            "Pick a contact, or find one picked before",
            short = "Pick a contact",
            args = "<query> · --forget <name>",
            aka = listOf("contact")
        ),
        CommandSpec(
            "calls",
            "calls [<n>]",
            "Recent calls: incoming, outgoing and missed",
            short = "Recent calls",
            args = "<n>"
        ),
        CommandSpec(
            "url",
            "url <address>",
            "Open a link",
            short = "Open a link",
            args = "<address>",
            aka = listOf("web"),
            sensitive = true
        ),
        CommandSpec(
            "history",
            "history",
            "Recent commands (tap to reuse)",
            short = "Recent commands"
        ),
        CommandSpec(
            "clear", "clear [history|all]", "Clear the screen, the ↑ history, or both",
            short = "Clear the screen", args = "history · all",
            uses = listOf(
                "clear" to "clear the screen",
                "clear history" to "clear the ↑ history",
                "clear all" to "clear both",
            ),
            aka = listOf("cls"), sensitive = true,
        ),
        CommandSpec(
            "backup",
            "backup [now|every <daily|weekly|off>|dir]",
            "Back up everything to a folder now, or every day or week",
            short = "Back up everything",
            args = "now · every <daily|weekly|off> · dir",
            uses = listOf(
                "backup" to "back up now; the first time, pick a folder",
                "backup every daily" to "back up every day",
                "backup every weekly" to "back up every week",
                "backup every off" to "stop automatic backups",
                "backup dir" to "pick the backup folder",
            ),
            sensitive = true,
        ),
        CommandSpec(
            "log",
            "log [only <name|kind>|all]",
            "Show only one script's, feed's or app's lines, one kind of line, or the starred ones",
            short = "Show only some lines",
            args = "only <name|kind> · all",
            uses = listOf(
                "log only <name>" to "only one script's, feed's or app's lines",
                "log only <kind>" to "only commands, scripts, feeds, notifications, apps, calls, reminders or starred lines",
                "log all" to "every line again",
            ),
        ),
        CommandSpec(
            "fresh",
            "fresh",
            "Start a clean screen now, like log.fresh; earlier lines are one tap away (not logged)",
            short = "A clean screen"
        ),
        CommandSpec(
            "scramble",
            "scramble [on|off]",
            "Scramble home for a screen recording: every line, its pictures, the pinned line and the sheets show no real text until scramble off; nothing stored changes",
            short = "Hide the log for a recording",
            args = "on · off",
            uses = listOf(
                "scramble" to "scramble home: lines keep their shape and colors, not their text",
                "scramble off" to "show the real text again",
            ),
            sensitive = true,
        ),
        CommandSpec(
            "restart",
            "restart [scripts]",
            "Restart the launcher, or stop running scripts",
            short = "Restart Kolahal",
            args = "scripts",
            aka = listOf("reboot"),
            sensitive = true
        ),
        CommandSpec(
            "update",
            "update [install]",
            "Check for updates (install: download and install the latest)",
            short = "Check for updates",
            args = "install",
            sensitive = true
        ),
        CommandSpec(
            "status", "status [clear <name|all>]", "Scripts' status lines, the pinned ones marked",
            short = "Scripts' status lines", args = "clear <name|all>",
            uses = listOf(
                "status" to "scripts' status lines, the pinned ones marked",
                "status clear <name>" to "remove one script's line",
                "status clear" to "remove them all",
            ),
            sensitive = true,
        ),
        CommandSpec(
            "setup",
            "setup [text]",
            "Setup checklist page (text: print it in the log)",
            short = "Setup checklist",
            args = "text"
        ),
        CommandSpec(
            "tour",
            "tour",
            "How Kolahal works, a page at a time, with a few initial settings",
            short = "How Kolahal works"
        ),
        // The words after it come from Accesses; the Play build leaves some out.
        CommandSpec(
            "allow",
            "allow [<what>]",
            "The Android permissions Kolahal can use, and whether each is allowed; allow <what> asks for one, or opens its page in Android's settings",
            short = "Android permissions",
            args = Accesses.OFFERED.joinToString(" · ") { it.word },
            uses = listOf("allow" to "every permission and whether it is allowed; tap one to allow it") +
                    Accesses.OFFERED.map { "allow ${it.word}" to "${it.title}: ${it.short}" },
            sensitive = true,
        ),
        CommandSpec(
            "crashes",
            "crashes [share|clear] [<n>]",
            "Crash reports kept on this phone; share one to report a bug",
            short = "Crash reports",
            args = "share <n> · clear",
            uses = listOf(
                "crashes" to "crash reports, newest first",
                "crashes share [<n>]" to "share one; the newest by default",
                "crashes clear" to "delete them all",
            ),
            sensitive = true,
        ),
        CommandSpec(
            "home",
            "home",
            "Choose the default launcher",
            short = "Choose the home app",
            sensitive = true
        ),
        CommandSpec(
            "lock",
            "lock",
            "Lock the screen (needs the accessibility service: allow lock)",
            short = "Lock the screen",
            sensitive = true
        ),
        CommandSpec(
            "shade",
            "shade",
            "Pull down the notification shade",
            short = "Pull down notifications",
            sensitive = true
        ),
        CommandSpec(
            "settings",
            "settings",
            "Kolahal settings screen (+ backup import/export)",
            short = "Kolahal settings",
            aka = listOf("prefs")
        ),
        CommandSpec(
            "scripts",
            "scripts",
            "Script manager screen (import files, timers)",
            short = "Script manager",
            sensitive = true
        ),
        CommandSpec(
            "system",
            "system",
            "Android system settings",
            short = "Android settings",
            sensitive = true
        ),
        CommandSpec(
            "config",
            "config [ls|set|get|reset] <key> <v>",
            "Settings (no args opens the settings page)",
            short = "Settings by name",
            args = "ls · set · get · reset <key> [<value>]",
            uses = listOf(
                "config" to "open the settings page",
                "config ls" to "every setting; tap one to change it",
                "config get <key>" to "one setting's value",
                "config set <key> <value>" to "change a setting",
                "config reset <key>" to "put one back to its default",
                "config reset all" to "put them all back",
            ),
            aka = listOf("set"),
            sensitive = true,
        ),
        CommandSpec(
            "theme",
            "theme <name>",
            "Themes: ${com.mtwabpin.kolahal.data.Keys.BUILT_IN_THEMES.joinToString(" ")}",
            short = "Colors",
            args = "<name>",
            sensitive = true
        ),
        CommandSpec(
            "font",
            "font [<name>|import|sans [off|prompt|all]]",
            "Terminal font: system jetbrains-mono fira-code ibm-plex-mono source-code-pro iosevka custom (no args: list); sans: the system font for the prompt or everything",
            short = "Terminal font",
            args = "<name> · import · sans [off|prompt|all]",
            uses = listOf(
                "font" to "the fonts; tap one to use it",
                "font <name>" to "use a font",
                "font import" to "add your own .ttf or .otf",
                "font sans prompt" to "the system font for the prompt",
                "font sans all" to "the system font for everything",
                "font sans off" to "the terminal font everywhere",
            ),
            sensitive = true,
        ),
        CommandSpec(
            "date",
            "date [<place>...]",
            "Current date and time, or the time in a city, a zone, utc or an offset",
            short = "Date and time",
            args = "<place>…",
            aka = listOf("time")
        ),
        CommandSpec(
            "echo",
            "echo <text>",
            "Print text",
            short = "Print text",
            args = "<text>",
            sensitive = true
        ),
        CommandSpec(
            "cal",
            "cal [<month>] [<year>]",
            "The month as a calendar, today in bold · cal dec · cal dec 2027 · cal 2027-12",
            short = "A month's calendar",
            args = "<month> [<year>]"
        ),
        // The screens take the whole screen until a key or a tap. Each is sensitive: typing `cl`
        // or `ma` on the way to an app must not open one, and a script can't. They are commands only
        // in the tty home style ([TtyCommands]): in the gui style `clock` opens the Clock app, and
        // `screen <name>` runs only when typed in full.
        CommandSpec(
            "screen",
            "screen <name>",
            "A full-screen screen until a key or a tap: matrix, clock, pipes, decode, sl, top or boot",
            short = "A full-screen screen",
            args = "<name>",
            sensitive = true
        ),
        CommandSpec(
            "matrix",
            "matrix",
            "Characters fall down the whole screen, as in the film, until a key or a tap",
            short = "Matrix rain",
            sensitive = true
        ),
        CommandSpec(
            "pipes",
            "pipes",
            "Pipes grow across the whole screen, until a key or a tap",
            short = "Growing pipes",
            sensitive = true
        ),
        CommandSpec(
            "sl",
            "sl",
            "A steam locomotive crosses the screen",
            short = "A steam locomotive",
            sensitive = true
        ),
        CommandSpec(
            "clock",
            "clock",
            "The time in big digits, with the date, until a key or a tap",
            short = "The time in big digits",
            sensitive = true
        ),
        CommandSpec(
            "top",
            "top",
            "This phone and Kolahal at a glance, updated each second, as top shows a computer, until a key or a tap",
            short = "This phone, live",
            sensitive = true
        ),
        CommandSpec(
            "decode",
            "decode",
            "Home's log lines scramble and settle again, until a key or a tap",
            short = "The log scrambles and settles",
            sensitive = true
        ),
        CommandSpec(
            "boot",
            "boot",
            "A startup sequence with this phone's counts, then home comes back",
            short = "A startup sequence",
            sensitive = true
        ),
        CommandSpec(
            "shaders",
            "shaders",
            "Shaders screen (use one, add your own, get more)",
            short = "Shaders screen",
            sensitive = true
        ),
        CommandSpec(
            "shader",
            "shader [<name>|ls|use|on|off|set|reset|follow|values|play|words|get|check|reload|edit|save|rm] [<name>]",
            "A shader over home and the full-screen screens: crt, paper, aurora and others, or your own .agsl file (screen.shader). Needs Android 13 or newer",
            short = "A shader over home and the screens",
            args = "<name> · ls · use · on · off · set · reset · follow · values · play · words · get · check · reload · edit · save · rm",
            uses = listOf(
                "shader" to "the shaders: tap one to use it",
                "shader <name>" to "use a shader",
                "shader off" to "no shader; this always works, even when the screen can't be read",
                "shader on" to "the last one again, also after it was turned off for being slow",
                "shader set <name> <parameter> <value>" to "change a shader, such as the amount of snow; without a value, what it can be",
                "shader reset <name>" to "reset its parameters to their defaults",
                "shader follow <name> <parameter> <value>" to "make a parameter follow a value, such as battery or one a script gives, or a formula, such as battery / 20; off ends it",
                "shader values" to "the values there are to follow",
                "shader play <name>" to "play a one-off shader, such as hearts, once over home",
                "shader words <name> <words>" to "play it when a notification has one of the words, with commas between; off ends it",
                "shader get" to "shaders people share, marked when one you installed has a newer version; tap one to install it",
                "shader get <name>" to "install or update a shared shader; it stays off until you use it",
                "shader get --update" to "update every shader you installed with shader get",
                "shader check <name>" to "whether it compiles, and what Kolahal sets for it",
                "shader reload" to "read the .agsl files in the script folder again",
                "shader edit <name>" to "open the editor; one that comes with Kolahal becomes yours when you save it",
                "shader save <name>" to "save a shader's file in the script folder's shaders folder, to edit it there",
                "shader save template-tint" to "a shader of your own to edit, from a template: template-tint, -scene, -moving, -touch, -value or -once",
                "shader rm <name>" to "remove your own shader; delete does the same",
            ),
            sensitive = true,
        ),
        CommandSpec(
            "alias", "alias [name = command]", "List or define aliases",
            short = "Your aliases", args = "<name> = <command>",
            uses = listOf(
                "alias" to "your aliases",
                "alias <name>" to "what one stands for",
                "alias <name> = <command>" to "make one: <name> and a space at the start of the line becomes <command>",
                "unalias <name>" to "remove one",
            ),
            sensitive = true,
        ),
        CommandSpec(
            "unalias",
            "unalias <name>",
            "Remove an alias",
            short = "Remove an alias",
            args = "<name>",
            sensitive = true
        ),
        CommandSpec(
            "pin",
            "pin <app>",
            "Favorite: first on an empty prompt (suggestions.idle) and starred in the app selector (pins lists them)",
            short = "Pin an app",
            args = "<app>",
            aka = listOf("fav"),
            sensitive = true
        ),
        CommandSpec(
            "unpin",
            "unpin <app>",
            "Remove a favorite",
            short = "Unpin an app",
            args = "<app>",
            aka = listOf("unfav"),
            sensitive = true
        ),
        CommandSpec(
            "pins",
            "pins",
            "Grid of pinned apps",
            short = "Your pinned apps",
            aka = listOf("favs")
        ),
        CommandSpec(
            "hide",
            "hide <app> [-w]",
            "Hide an app from lists and search",
            short = "Hide an app",
            args = "<app> · -w",
            sensitive = true
        ),
        CommandSpec(
            "unhide",
            "unhide <app>",
            "Show a hidden app again ('apps hidden')",
            short = "Show a hidden app",
            args = "<app>",
            sensitive = true
        ),
        CommandSpec(
            "rename",
            "rename <app> = <name>",
            "Custom label (empty restores)",
            short = "Rename an app",
            args = "<app> = <name>",
            sensitive = true
        ),
        CommandSpec(
            "todo",
            "todo [<text>|add|done|undo|edit|rm <n>|all|clear]",
            "Todo list (tap an item to tick it)",
            short = "Your todo list",
            args = "<text> · add · done · undo · edit · rm <n> · all · clear",
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
            sensitive = true,
        ),
        CommandSpec(
            "note",
            "note <text>",
            "Save a quick note",
            short = "Save a quick note",
            args = "<text>",
            sensitive = true
        ),
        CommandSpec(
            "notes", "notes [grep <text>|rm <n>]", "Your notes, newest first (tap to copy)",
            short = "Your notes", args = "grep <text> · rm <n>",
            uses = listOf(
                "notes" to "your notes, newest first; tap one to copy it",
                "notes grep <text>" to "only the notes with this text",
                "notes rm <n>" to "remove one",
            ),
            sensitive = true,
        ),
        CommandSpec(
            "edit",
            "edit [<file>|new [<name>]|recent]",
            "Open a text file in the text editor, a window of its own that stays open when you go home",
            short = "Edit a text file",
            args = "<file> · new [<name>] · recent",
            uses = listOf(
                "edit" to "pick a file to open",
                "edit new [<name>]" to "make a new file, and open it",
                "edit recent" to "the files opened lately, and those with unsaved text; tap one to open it",
                "edit <file>" to "open one of those by its name",
            ),
            sensitive = true,
        ),
        CommandSpec(
            "remind",
            "remind <when> <text>",
            "Notify later · 10m · 18:30 · tomorrow 9:00 · fri 8am · dec 3 10:00 · every mon 19:00 · rm <n>",
            short = "Remind you later",
            args = "<when> <text> · rm <n>",
            sensitive = true
        ),
        CommandSpec("reminders", "reminders", "Pending reminders", short = "Pending reminders"),
        CommandSpec(
            "timer",
            "timer <5m|1h30m> [<label>]",
            "Start a timer in the clock app (no args: show timers)",
            short = "A timer in the clock app",
            args = "<5m|1h30m> [<label>]",
            sensitive = true
        ),
        CommandSpec(
            "alarm",
            "alarm <HH:MM> [<label>]",
            "Set an alarm in the clock app",
            short = "An alarm in the clock app",
            args = "<HH:MM> [<label>]",
            sensitive = true
        ),
        CommandSpec(
            "alarms",
            "alarms",
            "Show the clock app's alarms",
            short = "The clock app's alarms",
            sensitive = true
        ),
        CommandSpec(
            "stopwatch",
            "stopwatch",
            "Open the clock app",
            short = "Open the clock app",
            sensitive = true
        ),
        CommandSpec(
            "agenda",
            "agenda [<days>]",
            "Upcoming calendar events (tap to open one)",
            short = "Upcoming events",
            args = "<days>"
        ),
    )

    /** Every built-in command name. Used to check aliases, script names and typos. */
    val COMMAND_NAMES: Set<String> = COMMANDS.map { it.name }.toSet()

    /** Each other name ([CommandSpec.aka]) and the command it stands for. */
    val SYNONYMS: Map<String, String> =
        COMMANDS.flatMap { spec -> spec.aka.map { it to spec.name } }.toMap()

    /** The commands that never run without Enter, and that scripts can't send ([CommandSpec.sensitive]), and the device controls. */
    val SENSITIVE: Set<String> = COMMANDS.filter { it.sensitive }
        .mapTo(HashSet()) { it.name } + ControlSpecs.SENSITIVE

    /** Names that a script or an alias can't use: the command names and their synonyms. */
    val RESERVED_NAMES: Set<String> = COMMAND_NAMES + SYNONYMS.keys

    /**
     * For each feature that a build can leave out (screen lock, call log, self-update):
     * whether this build has it, and its commands.
     */
    internal val FEATURE_COMMANDS = listOf(
        com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE to setOf("lock"),
        com.mtwabpin.kolahal.BuildConfig.CALL_LOG to setOf("calls"),
        com.mtwabpin.kolahal.BuildConfig.SELF_UPDATE to setOf("update"),
    )

    /**
     * Commands whose feature this build leaves out. They are not suggested or listed in help,
     * so the Play build doesn't offer what it can't do. If the user types one anyway, it explains why it is missing.
     */
    val UNAVAILABLE: Set<String> =
        FEATURE_COMMANDS.filterNot { it.first }.flatMap { it.second }.toSet()

    /**
     * Commands offered to nobody, in any build: not suggested, not listed in `help` or Settings →
     * Commands, and not run by instant launch. Typed in full, they run.
     * - `skill.md` copies a guide for an AI assistant ([SkillText]).
     */
    val HIDDEN: Set<String> = setOf(
        "skill.md",
    )

    /** The commands this build offers in the tty home style. Help lists them and completion suggests them; [TtyCommands.offered] gives a style's. */
    val OFFERED: List<CommandSpec> = COMMANDS.filter { it.name !in UNAVAILABLE && it.name !in HIDDEN }
    val OFFERED_NAMES: Set<String> = OFFERED.mapTo(LinkedHashSet()) { it.name }

    /** The usage line for each command name, for option completion and instant runs. */
    val COMMAND_USAGES: Map<String, String> = COMMANDS.associate { it.name to it.usage }

    /** Commands handled by `ProductivityCommands` (in terminal). */
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
            "activity",
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
            "storage",
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
        "control" to ControlSpecs.SPECS.map { it.name },
        "profiles" to listOf(
            "work",
            "private",
        ),
        "productivity" to listOf(
            "todo",
            "note",
            "notes",
            "edit",
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
            "scramble",
            "restart",
            "update",
            "alias",
            "unalias",
            "config",
            "backup",
            "echo",
            "date",
            "cal",
        ),
        // How home looks: its colors, its font, what is drawn over it and what is behind it.
        "look" to listOf(
            "theme",
            "font",
            "shader",
            "shaders",
            "wallpaper",
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
            "unlocks",
            "allow",
            "crashes",
            "home",
            "lock",
            "shade",
            "system",
        ),
        "screens" to listOf(
            "screen",
            "matrix",
            "pipes",
            "sl",
            "clock",
            "top",
            "decode",
            "boot",
        ),
    )
}
