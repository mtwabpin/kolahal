package com.mtwabpin.kolahal.data


/** A user-tunable setting, edited with `config set <key> <value>`. */
sealed class ConfigKey<T>(val key: String, val default: T, val help: String) {
    abstract fun parse(raw: String): T?
    open fun format(value: T): String = value.toString()

    class Bool(key: String, default: Boolean, help: String) : ConfigKey<Boolean>(key, default, help) {
        override fun parse(raw: String) = when (raw.lowercase()) {
            "on", "true", "yes", "1" -> true
            "off", "false", "no", "0" -> false
            else -> null
        }
        override fun format(value: Boolean) = if (value) "on" else "off"
    }

    class Num(key: String, default: Int, val range: IntRange, help: String) : ConfigKey<Int>(key, default, help) {
        override fun parse(raw: String) = raw.toIntOrNull()?.takeIf { it in range }
    }

    /** One of [fixed], or of [added]: values that come and go while the app runs (custom themes). */
    class Choice(
        key: String, default: String, private val fixed: List<String>, help: String,
        private val added: () -> List<String> = { emptyList() },
    ) : ConfigKey<String>(key, default, help) {
        val choices: List<String> get() = fixed + added()
        override fun parse(raw: String) = raw.lowercase().takeIf { it in choices }
    }

    /** Free text; [accept] makes what was typed the value, or null when it can't be one. */
    class Text(
        key: String, default: String, help: String,
        private val accept: (String) -> String? = { it },
    ) : ConfigKey<String>(key, default, help) {
        override fun parse(raw: String) = accept(raw)
    }
}

object Keys {
    val HEADER_SHOW = ConfigKey.Bool("header.show", true, "Show the header: clock, header items and script status lines (off: the log fills the screen, under the pinned header while music plays or a script pins an item; open settings with the settings command)")
    val HEADER_PINNED_SECONDS = ConfigKey.Num("header.pinned-seconds", 5, 2..60, "With the header off, how many seconds each item in the pinned header shows before the next one")
    val HEADER_PINNED_DOTS = ConfigKey.Num("header.pinned-dots", 3, 0..10, "Dots at the end of the pinned header showing which item is on screen, for up to this many items (0: never)")
    val HEADER_CLOCK = ConfigKey.Bool("header.clock", true, "Show the clock")
    val HEADER_SETTINGS = ConfigKey.Bool("header.settings", true, "Show the settings button (the command settings opens Settings too)")
    val HEADER_DEVICE = ConfigKey.Bool("header.device", false, "Show name@device in the header (the name is user.name)")
    // A name in name@device: no @ of its own, and no spaces, as usernames go.
    val USER_NAME = ConfigKey.Text(
        "user.name", "kolahal",
        "Your name in name@device, in the header and in fetch (up to 32 characters, no spaces or @)",
    ) { raw -> raw.trim().takeIf { n -> n.length in 1..32 && n.none { it.isWhitespace() || it == '@' } } }
    val HEADER_DATE = ConfigKey.Bool("header.date", true, "Show the date next to the clock")
    val HEADER_UNLOCKS = ConfigKey.Bool("header.unlocks", true, "Show today's unlock count")
    val HEADER_BATTERY = ConfigKey.Bool("header.battery", true, "Show battery level")
    val HEADER_NETWORK = ConfigKey.Bool("header.network", false, "Show network type")
    val HEADER_ALARM = ConfigKey.Bool("header.alarm", true, "Show the next alarm")
    val HEADER_PROFILES = ConfigKey.Bool("header.profiles", true, "Show work/private profile state")
    val HEADER_STATUS = ConfigKey.Bool("header.status", true, "Show status lines pinned by scripts")
    val HEADER_MEDIA = ConfigKey.Bool("header.media", true, "Show what's playing under the clock, with a progress bar (tap: play/pause, long-press: next; needs notification access)")
    val HEADER_TODO = ConfigKey.Bool("header.todo", true, "Show the open todo count (tap to run todo)")
    val HEADER_AUTOHIDE = ConfigKey.Bool("header.autohide", true, "Scrolling back hides the header, scrolling forward or a command shows it as one line; Home or a tap restores it")
    val HEADER_ICONS = ConfigKey.Bool("header.icons", true, "Icons for battery, network, unlocks, alarm and profiles in the header (off: text labels)")
    // What the one-line header shows: while typing, and with header.autohide while using the log.
    val COMPACT_CLOCK = ConfigKey.Bool("header.compact.clock", true, "Show the clock in the one-line header")
    val COMPACT_DATE = ConfigKey.Bool("header.compact.date", false, "Show the date in the one-line header")
    val COMPACT_DEVICE = ConfigKey.Bool("header.compact.device", false, "Show name@device in the one-line header")
    val COMPACT_UNLOCKS = ConfigKey.Bool("header.compact.unlocks", false, "Show today's unlock count in the one-line header")
    val COMPACT_BATTERY = ConfigKey.Bool("header.compact.battery", true, "Show battery level in the one-line header")
    val COMPACT_NETWORK = ConfigKey.Bool("header.compact.network", false, "Show network type in the one-line header")
    val COMPACT_ALARM = ConfigKey.Bool("header.compact.alarm", false, "Show the next alarm in the one-line header")
    val COMPACT_MEDIA = ConfigKey.Bool("header.compact.media", true, "Show a music note in the one-line header while something plays")
    val COMPACT_TODO = ConfigKey.Bool("header.compact.todo", false, "Show the open todo count in the one-line header")
    val COMPACT_STATUS = ConfigKey.Bool("header.compact.status", true, "Show how many status lines scripts have pinned in the one-line header")
    val COMPACT_SETTINGS = ConfigKey.Bool("header.compact.settings", false, "Show the settings button in the one-line header")
    val COMPACT_PROFILES = ConfigKey.Bool("header.compact.profiles", false, "Show work/private profile state in the one-line header")
    val CLOCK_24H = ConfigKey.Bool("clock.24h", false, "Use 24-hour time in the header and log")
    val KEYBAR = ConfigKey.Text(
        "keybar.row1", DEFAULT_KEYBAR,
        "Touch keys, comma separated: ESC TAB UP DOWN LEFT RIGHT HOME END BKSP ENTER SPACE KBD or literal text",
    )
    val KEYBAR2 = ConfigKey.Text("keybar.row2", DEFAULT_KEYBAR2, "Second key bar row (same syntax; empty by default)")
    val KEYBAR_SHOW = ConfigKey.Bool("keybar.show", false, "Show the touch key bar")
    val KEYBAR_AUTO = ConfigKey.Bool("keybar.auto", true, "Show the key bar only while the on-screen keyboard is up for the prompt (off: always)")
    val KEYBAR_HEIGHT = ConfigKey.Num("keybar.height", 40, 20..64, "Key bar row height (dp); smaller leaves more room for the log")
    val PROMPT_HEIGHT_TYPING = ConfigKey.Num("prompt.height", 40, 20..72, "Prompt line height (dp); it never gets shorter than its text")
    /** The themes that come with Kolahal; [CustomThemes] holds the ones people make. */
    val BUILT_IN_THEMES = listOf("green", "amber", "mono", "dracula", "solarized", "gruvbox", "gruvbox-material")
    val THEME = ConfigKey.Choice(
        "theme.name", "green", BUILT_IN_THEMES,
        "Colour theme: a built-in one or one you made; each has a dark and a light version, chosen by the phone's dark mode",
        added = { CustomThemes.names },
    )
    val THEME_ALWAYS_DARK = ConfigKey.Bool("theme.always-dark", false, "Always use the theme's dark version, even in light mode")
    val STATUS_BAR = ConfigKey.Choice("screen.bars", "immersive", listOf("show", "hide", "immersive"), "Full screen: hide the status bar, or both system bars (immersive); swipe from the edge to show them")
    val FONT = ConfigKey.Choice(
        "font.family", "jetbrains-mono",
        listOf("system", "jetbrains-mono", "fira-code", "ibm-plex-mono", "source-code-pro", "custom"),
        "Terminal font (custom: a .ttf/.otf imported with 'font import')",
    )
    val FONT_SANS = ConfigKey.Choice(
        "font.sans", "off", listOf("off", "prompt", "all"),
        "Use the system font instead of the terminal font: off, the prompt only, or all of the terminal (lists with aligned columns and the script editor stay monospace)",
    )
    val FONT_SIZE = ConfigKey.Num("font.size", 14, 10..24, "Terminal font size (sp)")
    val SUGGESTIONS = ConfigKey.Num("suggestions.max", 30, 0..30, "Max suggestions shown while typing")
    val AUTO_LAUNCH = ConfigKey.Bool("launch.single", true, "Launch automatically when only one app matches")
    val LAUNCH_INSTANT = ConfigKey.Choice(
        "launch.instant", "all", listOf("off", "apps", "all"),
        "Run without Enter once only one match is left: apps, or apps and commands (calls, uninstall and other sensitive commands always need Enter)",
    )
    val LAUNCH_INSTANT_MIN = ConfigKey.Num("launch.instant-letters", 2, 1..6, "Letters to type before an instant launch")
    val LAUNCH_TOP = ConfigKey.Bool("launch.top", true, "Enter launches the best match when it starts with what you typed")
    val SUGGEST_ICONS = ConfigKey.Bool("suggestions.icons", true, "Show app icons next to app suggestions, in the list and on chips")
    val SUGGESTIONS_WRAP = ConfigKey.Bool("suggestions.wrap", false, "Show a command's or setting's whole description in the suggestion list, over more lines (off: one line, cut short)")
    val SUGGEST_IDLE = ConfigKey.Bool("suggestions.idle", false, "Show most-used apps when the prompt is empty")
    val SUGGESTIONS_AUTO = ConfigKey.Bool("suggestions.auto", true, "Show suggestions only while typing (keyboard up or text in the prompt)")
    val SUGGESTION_ROWS = ConfigKey.Num("suggestions.rows", 2, 1..2, "Rows of suggestion chips while typing (suggestions.style chips)")
    val SUGGESTIONS_STYLE = ConfigKey.Choice("suggestions.style", "list", listOf("list", "chips"), "While typing: a list above the prompt (best match at the bottom, nearest the thumb) or a strip of chips")
    val SELECTOR_RAIL = ConfigKey.Bool("selector.rail", false, "Slide along the screen's edge to a letter, then onto the list to pick an app; let go on one to launch")
    val SELECTOR_BROWSE = ConfigKey.Choice("selector.browse", "letters", listOf("letters", "apps"), "Sliding along the edge: letters moves a letter at a time, with its apps beside your thumb; apps moves through the whole list, an app at a time when you slide slowly")
    val SELECTOR_FILTER = ConfigKey.Bool("selector.filter", false, "With letters: the list shows only the letter's apps, not the whole list scrolled to it")
    val SELECTOR_SIDE = ConfigKey.Choice("selector.side", "right", listOf("right", "left"), "The edge you slide on; the list of apps opens on the other side")
    val SELECTOR_POSITION = ConfigKey.Choice("selector.position", "bottom", listOf("bottom", "middle", "top"), "Position of the slide area on the edge: keep it away from the back gesture")
    val SELECTOR_HANDLE = ConfigKey.Bool("selector.handle", true, "Show the slim A–Z handle on the edge; off hides it while the selector keeps working")
    val SELECTOR_LETTERS = ConfigKey.Bool("selector.letters", false, "Show the letters along the edge, so you can see where to touch")
    val SELECTOR_HEIGHT = ConfigKey.Num("selector.height", 350, 200..900, "Length (dp) of the part of the edge you slide on: shorter means less thumb travel; the list uses the whole height")
    val SELECTOR_ICONS = ConfigKey.Bool("selector.icons", false, "Show each app's icon in the app selector's list")
    val SELECTOR_ROW_HEIGHT = ConfigKey.Num("selector.row-height", 38, 32..64, "Height (dp) of each app in the app selector's list: taller spaces the names out; it grows with a large text size")
    val SELECTOR_AIM = ConfigKey.Num("selector.aim", 10, 0..40, "With selector.browse apps, a slow slide along the edge moves one app per this many dp (0: a slide always scrolls the whole list)")
    val SELECTOR_AIM_SPEED = ConfigKey.Num("selector.aim-speed", 100, 20..400, "Slides slower than this (dp/s) move one app at a time; from 2.5 times this speed they scroll the whole list again")
    val SELECTOR_SYSTEM_FONT = ConfigKey.Bool("selector.system-font", false, "Use the system font in the app selector instead of the terminal font: its list, the letters on the edge and the one beside your thumb")
    val SELECTOR_ACTIONS = ConfigKey.Choice("selector.actions", "off", listOf("far-side", "off"), "App actions while picking: far-side opens them when you lift on the far side of the list; off: long-press in the open list only")
    val SHOW_TIMESTAMPS = ConfigKey.Choice("log.time", "groups", listOf("off", "commands", "groups"), "Right-aligned times on log lines: groups shows one on each group's first line and when the minute changes, commands on the lines you type")
    val LOG_DIM = ConfigKey.Bool("log.dim", true, "Dim output older than the latest command")
    val LOG_SPACING = ConfigKey.Num("log.spacing", 5, 0..40, "Gap (dp) above each output group: a command and its output, or a background batch (feeds, scripts)")
    val LOG_GUTTER = ConfigKey.Bool("log.gutter", true, "Thin colour bar at the left of each log line: green for commands, script colour for background output, red for errors")
    val LOG_LINE_HEIGHT = ConfigKey.Num("log.line-height", 22, 10..56, "Minimum height of a log line and of script status lines in the header (22 is 22 dp at the default text size, and scales with it); a line is never shorter than its text")
    val LOG_WALLPAPER = ConfigKey.Num("log.wallpaper", 0, 0..100, "How much of the phone's wallpaper shows behind the log (%): 0 is none; higher shows less of the theme's colour")
    val LOG_TALLER_TAPS = ConfigKey.Bool("log.taller-taps", false, "Make tappable lines slightly taller, so they're easier to hit; off: every line is the same height")
    val CHIP_HEIGHT = ConfigKey.Num("chips.height", 48, 20..64, "Height (dp) of suggestion chips and app-grid chips; list-style suggestion rows shrink with it below 44")
    val LOG_KEEP_DAYS = ConfigKey.Num("log.keep-days", 60, 1..365, "Delete log lines older than this many days")
    val LOG_KEEP_LINES = ConfigKey.Num("log.keep-lines", 5000, 500..50_000, "Delete the oldest log lines past this many; typed commands are counted separately, so ↑ history is kept")
    val LOG_FRESH = ConfigKey.Num("log.fresh", 10, 0..240, "Show a clean home screen after this many minutes away (0: never); earlier output is one tap away")
    val LOG_TIPS = ConfigKey.Bool("log.tips", true, "A tip a day in the log for your first two weeks: something you haven't used yet")
    val FEEDS_LOG = ConfigKey.Bool("feeds.log", true, "Post new feed items in the log")
    val FEEDS_LOG_LINES = ConfigKey.Num("feeds.log-lines", 10, 1..50, "New items per feed and refresh shown in the log; the rest is one '+N more' line")
    val FEEDS_READ_SHOWN = ConfigKey.Bool("feeds.read-shown", false, "Items posted in the log count as read (otherwise only opened or listed ones do)")
    val FEEDS_BACKLOG_READ = ConfigKey.Bool("feeds.backlog-read", false, "When you subscribe, the posts already in the feed count as read; only newer ones are unread")
    val FEEDS_INTERVAL = ConfigKey.Num("feeds.interval", 60, 15..1440, "Refresh interval for newly added feeds (minutes)")
    val FEEDS_NOTIFY = ConfigKey.Bool("feeds.notify", false, "Newly added feeds notify about new items")
    val FEEDS_WIFI = ConfigKey.Bool("feeds.wifi", false, "Scheduled refreshes only on Wi-Fi (unmetered networks); 'feed fetch' always runs")
    val SEARCH_ENGINE = ConfigKey.Choice(
        "search.engine", "duckduckgo",
        listOf("duckduckgo", "google", "bing", "brave", "startpage", "kagi", "custom"),
        "Web search engine for search / g",
    )
    val SEARCH_URL = ConfigKey.Text("search.url", "https://duckduckgo.com/?q=%s", "Custom search URL (%s = query), used when search.engine = custom")
    val FOCUS_ON_HOME = ConfigKey.Bool("keyboard.on-home", false, "Open the on-screen keyboard whenever the home screen is shown")
    val BACKUP_AUTO = ConfigKey.Choice("backup.auto", "off", listOf("off", "daily", "weekly"), "Write a backup file to a folder you pick, every day or every week")
    val BACKUP_KEEP = ConfigKey.Num("backup.keep", 7, 1..60, "How many automatic backups to keep in the folder; older ones are deleted")
    val IME_SUGGESTIONS = ConfigKey.Bool(
        "keyboard.suggestions", false,
        "Prompt keyboard suggestions (off uses a password-type field: no autocorrect, but Latin-only on some keyboards)",
    )

    val WIDGETS_PAGE = ConfigKey.Choice("widgets.page", "left", listOf("left", "right", "off"), "A widgets page beside the terminal: swipe sideways on the home screen to reach it")
    val WIDGETS_START = ConfigKey.Bool("widgets.start", false, "Start on the widgets page: home opens on it, and Home and Back return to it; swipe sideways for the log")
    val WIDGETS_WALLPAPER = ConfigKey.Num("widgets.wallpaper", 0, 0..100, "How much of the phone's wallpaper shows behind the widgets page (%): 0 is none; higher shows less of the theme's colour")

    val GESTURE_DOUBLE_TAP = ConfigKey.Text("gesture.double-tap", if (com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE) "lock" else "none", "Double-tap empty log space: $GESTURE_ACTIONS, quick (quick actions) or none")
    val GESTURE_SWIPE_DOWN = ConfigKey.Text("gesture.swipe-down", "shade", "Swipe down on the header: $GESTURE_ACTIONS, quick or none")
    val GESTURE_SWIPE_UP = ConfigKey.Text("gesture.swipe-up", "none", "Swipe up on the prompt: $GESTURE_ACTIONS, quick or none (none by default: it's easy to trigger by accident; apps opens the app grid)")

    val ALL: List<ConfigKey<*>> = listOf(
        HEADER_SHOW,
        HEADER_PINNED_SECONDS,
        HEADER_PINNED_DOTS,
        HEADER_CLOCK,
        HEADER_SETTINGS,
        HEADER_DEVICE,
        USER_NAME,
        HEADER_DATE,
        HEADER_UNLOCKS,
        HEADER_BATTERY,
        HEADER_NETWORK,
        HEADER_ALARM,
        HEADER_PROFILES,
        HEADER_STATUS,
        HEADER_MEDIA,
        HEADER_AUTOHIDE,
        HEADER_ICONS,
        COMPACT_CLOCK,
        COMPACT_DATE,
        COMPACT_DEVICE,
        COMPACT_UNLOCKS,
        COMPACT_BATTERY,
        COMPACT_NETWORK,
        COMPACT_ALARM,
        COMPACT_MEDIA,
        COMPACT_TODO,
        COMPACT_STATUS,
        COMPACT_SETTINGS,
        COMPACT_PROFILES,
        CLOCK_24H,
        KEYBAR_SHOW,
        KEYBAR_AUTO,
        KEYBAR_HEIGHT,
        PROMPT_HEIGHT_TYPING,
        KEYBAR,
        KEYBAR2,
        THEME,
        THEME_ALWAYS_DARK,
        STATUS_BAR,
        FONT,
        FONT_SANS,
        FONT_SIZE,
        SUGGESTIONS,
        SUGGEST_IDLE,
        SUGGEST_ICONS,
        SUGGESTIONS_WRAP,
        SUGGESTIONS_AUTO,
        SUGGESTION_ROWS,
        SUGGESTIONS_STYLE,
        SELECTOR_RAIL,
        SELECTOR_BROWSE,
        SELECTOR_FILTER,
        SELECTOR_SIDE,
        SELECTOR_POSITION,
        SELECTOR_HANDLE,
        SELECTOR_LETTERS,
        SELECTOR_HEIGHT,
        SELECTOR_ACTIONS,
        SELECTOR_ICONS,
        SELECTOR_ROW_HEIGHT,
        SELECTOR_SYSTEM_FONT,
        SELECTOR_AIM,
        SELECTOR_AIM_SPEED,
        AUTO_LAUNCH,
        LAUNCH_TOP,
        LAUNCH_INSTANT,
        LAUNCH_INSTANT_MIN,
        SHOW_TIMESTAMPS,
        LOG_DIM,
        LOG_SPACING,
        LOG_GUTTER,
        LOG_LINE_HEIGHT,
        LOG_TALLER_TAPS,
        LOG_WALLPAPER,
        CHIP_HEIGHT,
        LOG_KEEP_DAYS,
        LOG_KEEP_LINES,
        LOG_FRESH,
        LOG_TIPS,
        SEARCH_ENGINE,
        SEARCH_URL,
        FOCUS_ON_HOME,
        IME_SUGGESTIONS,
        BACKUP_AUTO,
        BACKUP_KEEP,
        GESTURE_DOUBLE_TAP,
        GESTURE_SWIPE_DOWN,
        GESTURE_SWIPE_UP,
        FEEDS_LOG,
        FEEDS_LOG_LINES,
        FEEDS_READ_SHOWN,
        FEEDS_BACKLOG_READ,
        FEEDS_INTERVAL,
        FEEDS_NOTIFY,
        FEEDS_WIFI,
        HEADER_TODO,
        WIDGETS_PAGE,
        WIDGETS_START,
        WIDGETS_WALLPAPER,
    )

    /** Settings only `config` changes: the Settings screen leaves them out. */
    val CONFIG_ONLY: Set<ConfigKey<*>> = setOf(LOG_KEEP_LINES, SELECTOR_AIM, SELECTOR_AIM_SPEED, SELECTOR_ACTIONS, BACKUP_KEEP)

    /** A setting by name; an old name ([RENAMED]) finds the setting under its new one. */
    fun byName(name: String): ConfigKey<*>? {
        val n = name.lowercase()
        return ALL.firstOrNull { it.key == (RENAMED[n] ?: n) }
    }

    /**
     * Names settings had before 0.3.0, old to new. Saved values move over once ([Config]); `config`
     * and backups still take the old names.
     */
    val RENAMED: Map<String, String> = mapOf(
        "theme" to "theme.name",
        "font" to "font.family",
        "statusbar" to "screen.bars",
        "suggestions" to "suggestions.max",
        "keybar" to "keybar.row1",
        "keybar2" to "keybar.row2",
        "launch.instant.min" to "launch.instant-letters",
        "feeds.log.lines" to "feeds.log-lines",
        "feeds.backlog.read" to "feeds.backlog-read",
        "feeds.read.shown" to "feeds.read-shown",
        "gesture.doubletap" to "gesture.double-tap",
        "gesture.swipedown" to "gesture.swipe-down",
        "gesture.swipeup" to "gesture.swipe-up",
        "log.lineheight" to "log.line-height",
        "log.keepdays" to "log.keep-days",
        "keyboard.onhome" to "keyboard.on-home",
    )

    /** The old switch for swiping up on the prompt: `gesture.swipe-up` is now `none` while it is off. */
    const val OLD_SWIPE_UP_ON = "gesture.swipeup.on"

    /**
     * Settings that are gone, with where their job went (what `config` says when given one). Saved
     * values are dropped ([migrate]); backups that still have them import without them.
     */
    val REMOVED: Map<String, String> = mapOf(
        OLD_SWIPE_UP_ON to "set gesture.swipe-up to an action to turn swiping up on, or none to turn it off",
        "theme.settings-colors" to "Settings always use the theme's colours",
        "gui.colors" to "Settings always use the theme's colours",
        "widgets.home" to "widgets are on the widgets page (widgets.page)",
        "widgets.height" to "widgets are on the widgets page (widgets.page)",
        "theme.paper" to "the home screen's background is the theme's colour; the phone's wallpaper can show behind it (log.wallpaper)",
        "header.media-always" to "with the header off, what's playing always shows in the pinned header at the top of home",
    )

    /**
     * Saved settings ([saved]: name to raw value) under today's names. A value already saved under
     * the new name wins, and settings that are gone ([REMOVED]) are left out. The swipe-up switch (off
     * by default, with `apps` as its action) folds into `gesture.swipe-up`.
     */
    fun migrate(saved: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((name, value) in saved) {
            if (name in REMOVED) continue
            val new = RENAMED[name] ?: name
            if (new != name && new in saved) continue
            out[new] = value
        }
        val swipeUp = GESTURE_SWIPE_UP.key
        if (swipeUp !in saved) {
            if (saved[OLD_SWIPE_UP_ON] == "on") out[swipeUp] = saved["gesture.swipeup"] ?: "apps" else out.remove(swipeUp)
        }
        return out
    }

    /** Saved once settings hold the one-line header's choices: [carryCompact] has run on this phone. */
    const val COMPACT_CARRIED = "header.compact-carried"

    /**
     * Saved settings from before the one-line header had its own choices (up to 0.4.10), with what
     * was off there still off. It showed the battery whatever `header.battery` said; that counts
     * as off too, as whoever turned it off didn't want it.
     */
    fun carryCompact(saved: Map<String, String>): Map<String, String> =
        saved + listOf(HEADER_BATTERY to COMPACT_BATTERY, HEADER_MEDIA to COMPACT_MEDIA, HEADER_STATUS to COMPACT_STATUS)
            .filter { (full, compact) -> compact.key !in saved && saved[full.key]?.let(full::parse) == false }
            .associate { (_, compact) -> compact.key to compact.format(false) }
}

// What a keyboard's letters page doesn't have: the rest (`.`, backspace, Enter) sits right below the bar.
private const val DEFAULT_KEYBAR = "/,|,=,LEFT,UP,DOWN,RIGHT"
private const val DEFAULT_KEYBAR2 = ""

// The Play build has no screen lock to offer.
private val GESTURE_ACTIONS = if (com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE) "a command, lock, shade" else "a command, shade"
