/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.data

import com.mtwabpin.kolahal.assistant.ConversationStore
import com.mtwabpin.kolahal.assistant.Services
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.Shaders


/**
 * A user-tunable setting, edited with `config set <key> <value>`.
 * [help] is its description, shown in Settings, in `config` and in the prompt's suggestions:
 * - A line break goes between its parts: what it does, what its values mean, when it doesn't apply.
 * - Every line but the last ends with a full stop, so it still reads well where a line break
 *   shows as a space (a suggestion cut to one line).
 */
sealed class ConfigKey<T>(val key: String, val default: T, val help: String) {
    abstract fun parse(raw: String): T?
    open fun format(value: T): String = value.toString()

    /** This key's value in [config], as the text it is stored as. */
    fun valueText(config: Config): String = format(config[this])

    class Bool(key: String, default: Boolean, help: String) :
        ConfigKey<Boolean>(key, default, help) {
        override fun parse(raw: String) = when (raw.lowercase()) {
            "on", "true", "yes", "1" -> true
            "off", "false", "no", "0" -> false
            else -> null
        }

        override fun format(value: Boolean) = if (value) "on" else "off"
    }

    class Num(key: String, default: Int, val range: IntRange, help: String) :
        ConfigKey<Int>(key, default, help) {
        override fun parse(raw: String) = raw.toIntOrNull()?.takeIf { it in range }
    }

    /**
     * One of [fixed], or one of [added]: values that come and go while the app runs (custom themes).
     * [notes] says what a value does, in a few words; the prompt's suggestions show it beside the value.
     */
    class Choice(
        key: String, default: String, private val fixed: List<String>, help: String,
        private val added: () -> List<String> = { emptyList() },
        val notes: Map<String, String> = emptyMap(),
    ) : ConfigKey<String>(key, default, help) {
        val choices: List<String> get() = fixed + added()
        override fun parse(raw: String) = raw.lowercase().takeIf { it in choices }
    }

    /** Free text. [accept] turns what was typed into the value, or returns null when it can't be one. */
    class Text(
        key: String, default: String, help: String,
        private val accept: (String) -> String? = { it },
    ) : ConfigKey<String>(key, default, help) {
        override fun parse(raw: String) = accept(raw)
    }
}

object Keys {
    /** What the header can show. Each part is on one side at most ([HEADER_LEFT], [HEADER_RIGHT]). */
    val HEADER_PARTS = listOf(
        "time",
        "date",
        "battery",
        "alarm",
        "todo",
        "network",
        "unlocks",
        "profiles",
        "settings"
    )
    val HEADER_SHOW = ConfigKey.Bool(
        "header.show",
        false,
        "Show the header, the line at the top of home: the time, the date, the battery and the parts you chose.\nOff: only the pinned header shows, while music plays or a script is pinned. Open Settings with the settings command"
    )
    val HEADER_BELOW_BAR = ConfigKey.Bool(
        "header.below-bar",
        true,
        "With the status bar hidden (screen.hide-bars), put the header below the status bar's area, not beside the camera.\nWith the status bar shown, the header is always below it"
    )
    val HEADER_PADDING = ConfigKey.Num(
        "header.padding",
        6,
        0..24,
        "Space (dp) above and below the header's one line of text"
    )

    // The header's two sides. Each is a list of parts in the order they show; none leaves a side empty.
    val HEADER_LEFT = ConfigKey.Text(
        "header.left",
        "time,date",
        "The parts on the header's left side, from the left.\n$HEADER_PARTS_HELP",
        ::headerParts
    )
    val HEADER_RIGHT = ConfigKey.Text(
        "header.right",
        "alarm,todo,battery",
        "The parts on the header's right side, from the left.\n$HEADER_PARTS_HELP",
        ::headerParts
    )
    val HEADER_PINNED_SECONDS = ConfigKey.Num(
        "header.pinned-seconds",
        5,
        2..60,
        "How many seconds each item in the pinned header shows before the next one"
    )
    val HEADER_PINNED_DOTS = ConfigKey.Num(
        "header.pinned-dots",
        3,
        0..10,
        "Dots at the end of the pinned header show which item is on screen, for up to this many items. A tap on them shows the next one.\n0 turns them off"
    )
    val HEADER_PINNED_BAR_ABOVE = ConfigKey.Num(
        "header.pinned-bar-above",
        5,
        0..16,
        "Space (dp) between the pinned header's text and its progress bar"
    )
    val HEADER_PINNED_BAR_BELOW = ConfigKey.Num(
        "header.pinned-bar-below",
        5,
        0..16,
        "Space (dp) under the pinned header's progress bar, before the log"
    )

    // The name in name@device: no @ of its own and no spaces, like usernames.
    val USER_NAME = ConfigKey.Text(
        "user.name", "kolahal",
        "Your name in name@device, in fetch (up to 32 characters, no spaces or @)",
    ) { raw ->
        raw.trim().takeIf { n -> n.length in 1..32 && n.none { it.isWhitespace() || it == '@' } }
    }
    val CLOCK_24H = ConfigKey.Bool("clock.24h", false, "Use 24-hour time in the header and log")
    val KEYBAR = ConfigKey.Text(
        "keybar.row1", DEFAULT_KEYBAR,
        "Touch keys, comma separated: ESC TAB UP DOWN LEFT RIGHT HOME END BKSP ENTER SPACE KBD or literal text",
    )
    val KEYBAR2 = ConfigKey.Text(
        "keybar.row2",
        DEFAULT_KEYBAR2,
        "Second key bar row, in the same form as keybar.row1.\nEmpty by default"
    )
    val KEYBAR_SHOW = ConfigKey.Bool("keybar.show", false, "Show the touch key bar")
    val KEYBAR_AUTO = ConfigKey.Bool(
        "keybar.auto",
        true,
        "Show the key bar only while the on-screen keyboard is up for the prompt (off: always)"
    )
    val KEYBAR_HEIGHT = ConfigKey.Num(
        "keybar.height",
        40,
        20..64,
        "Key bar row height (dp).\nSmaller leaves more room for the log"
    )
    val PROMPT_HEIGHT_TYPING = ConfigKey.Num(
        "prompt.height",
        40,
        20..72,
        "Prompt line height (dp).\nIt never gets shorter than its text"
    )
    val PROMPT_BOTTOM_PADDING = ConfigKey.Num(
        "prompt.bottom-padding",
        0,
        0..48,
        "Space under the prompt (dp) while the keyboard is closed, for screens whose rounded corners cut the $"
    )

    /**
     * The themes that come with Kolahal. [CustomThemes] holds the ones people make.
     * A name added here can't be a saved theme's name any more: [CustomThemes.load] renames that theme.
     * The default ([THEME]) comes first.
     */
    val BUILT_IN_THEMES = listOf(
        "rose-pine",
        "green",
        "amber",
        "mono",
        "dracula",
        "solarized",
        "gruvbox",
        "gruvbox-material",
        "nord"
    ) + MaterialYou.VARIANTS
    val THEME = ConfigKey.Choice(
        "theme.name", "rose-pine", BUILT_IN_THEMES,
        "Color theme: a built-in one or one you made.\nEach has a dark and a light version, chosen by the phone's dark mode.\n" +
                "With ${MaterialYou.NAME}, the colors come from your wallpaper, on Android 12 and newer.\nWith ${MaterialYou.SOFT}, ${MaterialYou.TINTED} or ${MaterialYou.BLACK}, they're on a lighter, a tinted or a black background",
        added = { CustomThemes.names },
        notes = mapOf(
            MaterialYou.NAME to "Colors from your wallpaper, on Android 12 and newer",
            MaterialYou.SOFT to "Your wallpaper's colors on a lighter background",
            MaterialYou.TINTED to "Your wallpaper's colors on a tinted background",
            MaterialYou.BLACK to "Your wallpaper's colors on black",
        ),
    )
    val THEME_ALWAYS_DARK = ConfigKey.Bool(
        "theme.always-dark",
        false,
        "Always use the theme's dark version, even in light mode"
    )
    // Which of Android's own bars Kolahal hides. The name and the values say it: `screen.hide-bars both`.
    // Until 0.5.17 it was `screen.bars` with show, hide and immersive ([RENAMED], [OLD_BARS]).
    val SCREEN_HIDE_BARS = ConfigKey.Choice(
        "screen.hide-bars",
        "none",
        listOf("none", "status", "both"),
        "Which of Android's bars Kolahal hides: none, the status bar at the top (time, battery, notifications), or both that and the navigation bar at the bottom. A swipe from its edge shows a hidden bar for a moment",
        notes = mapOf(
            "none" to "The status bar and the navigation bar both show",
            "status" to "The top bar hides; swipe down from the top to see it",
            "both" to "Top and bottom bars hide; swipe from an edge to see them",
        ),
    )
    val SCREEN_SAVER = ConfigKey.Choice(
        "screen.saver",
        "off",
        listOf("off", "matrix", "clock", "pipes", "decode"),
        "Opens a screen over home after a while without a touch (screen.saver-after). Any touch or key closes it.\nMatrix, clock and pipes need the tty home style. Decode runs in both styles.\nNever runs with the screen off, in battery saver or with Remove animations on",
        notes = mapOf(
            "off" to "No screensaver",
            "matrix" to "Characters fall down the screen",
            "clock" to "The time in big digits, with the date",
            "pipes" to "Pipes grow across the screen",
            "decode" to "The log's lines scramble and settle again",
        ),
    )
    val SCREEN_SAVER_AFTER = ConfigKey.Num(
        "screen.saver-after",
        5,
        0..60,
        "Minutes without a touch on home before the screensaver (screen.saver) starts.\n0 is 30 seconds"
    )
    val FONT = ConfigKey.Choice(
        "font.family", "jetbrains-mono",
        listOf(
            "system",
            "jetbrains-mono",
            "fira-code",
            "ibm-plex-mono",
            "source-code-pro",
            "iosevka",
            "custom"
        ),
        "Terminal font (custom: a .ttf/.otf imported with 'font import')",
        notes = mapOf(
            "system" to "The phone's own font",
            "jetbrains-mono" to "JetBrains Mono, the default",
            "iosevka" to "Narrow, so more fits on a line",
            "custom" to "A .ttf or .otf you imported with font import",
        ),
    )
    val FONT_SANS = ConfigKey.Choice(
        "font.sans", "off", listOf("off", "prompt", "all"),
        "Use the system font instead of the terminal font: off, in the prompt only, or in all of the terminal.\nLists with aligned columns and the script editor stay monospace",
        notes = mapOf(
            "off" to "The terminal font everywhere",
            "prompt" to "The system font in the prompt only",
            "all" to "The system font in the log and the prompt, with columns in monospace",
        ),
    )
    val FONT_SIZE = ConfigKey.Num("font.size", 14, 10..24, "Terminal font size (sp)")
    val SUGGESTIONS =
        ConfigKey.Num("suggestions.max", 30, 0..30, "Max suggestions shown while typing")
    val AUTO_LAUNCH =
        ConfigKey.Bool("launch.single", true, "Launch automatically when only one app matches")
    val LAUNCH_INSTANT = ConfigKey.Choice(
        "launch.instant", "all", listOf("off", "apps", "all"),
        "Run without Enter once only one match is left: apps, or apps and commands.\nCalls, uninstall and other sensitive commands always need Enter",
        notes = mapOf(
            "off" to "Always wait for Enter",
            "apps" to "An app opens without Enter once it's the only match",
            "all" to "Apps and commands run without Enter once only one is left",
        ),
    )
    val LAUNCH_INSTANT_MIN =
        ConfigKey.Num("launch.instant-letters", 2, 1..6, "Letters to type before an instant launch")
    val LAUNCH_TOP = ConfigKey.Bool(
        "launch.top",
        true,
        "Enter launches the best match when it starts with what you typed"
    )
    val SUGGEST_ICONS = ConfigKey.Bool(
        "suggestions.icons",
        true,
        "Show app icons next to app suggestions, in the list and on chips, and on the apps the log lists"
    )
    val SUGGESTIONS_WRAP = ConfigKey.Bool(
        "suggestions.wrap",
        false,
        "Show a command's or setting's whole description in the suggestion list, over more lines.\nOff: one line, cut short"
    )
    val SUGGEST_IDLE =
        ConfigKey.Bool("suggestions.idle", false, "Show most-used apps when the prompt is empty")
    val SUGGESTIONS_AUTO = ConfigKey.Bool(
        "suggestions.auto",
        true,
        "Show suggestions only while typing (keyboard up or text in the prompt)"
    )
    val SUGGESTION_ROWS = ConfigKey.Num(
        "suggestions.rows",
        2,
        1..2,
        "Rows of suggestion chips while typing (suggestions.style chips)"
    )
    val SUGGESTIONS_STYLE = ConfigKey.Choice(
        "suggestions.style",
        "list",
        listOf("list", "chips"),
        "While typing: a list above the prompt (best match at the bottom, nearest the thumb) or a strip of chips",
        notes = mapOf(
            "list" to "A list above the prompt, the best match nearest your thumb",
            "chips" to "A strip of chips above the prompt",
        ),
    )
    val SELECTOR_RAIL = ConfigKey.Bool(
        "selector.rail",
        false,
        "Slide along the screen's edge to a letter, then onto the list to pick an app. Let go on one to launch it"
    )
    val SELECTOR_BROWSE = ConfigKey.Choice(
        "selector.browse",
        "apps",
        listOf("letters", "apps"),
        "What a slide along the edge moves through.\nWith letters, a letter at a time, with its apps beside your thumb.\nWith apps, the whole list, an app at a time when you slide slowly",
        notes = mapOf(
            "letters" to "A letter at a time, with its apps beside your thumb",
            "apps" to "The whole list, an app at a time when you slide slowly",
        ),
    )
    val SELECTOR_FILTER = ConfigKey.Bool(
        "selector.filter",
        false,
        "With letters: the list shows only the letter's apps, not the whole list scrolled to it"
    )
    val SELECTOR_SIDE = ConfigKey.Choice(
        "selector.side",
        "right",
        listOf("right", "left"),
        "The edge you slide on. The list of apps opens on the other side",
        notes = mapOf(
            "right" to "Slide on the right edge, with the list on the left",
            "left" to "Slide on the left edge, with the list on the right",
        ),
    )
    val SELECTOR_POSITION = ConfigKey.Choice(
        "selector.position",
        "bottom",
        listOf("bottom", "middle", "top"),
        "Position of the slide area on the edge: keep it away from the back gesture",
        notes = mapOf(
            "bottom" to "The slide area at the bottom of the edge",
            "middle" to "The slide area in the middle of the edge",
            "top" to "The slide area at the top of the edge",
        ),
    )
    val SELECTOR_HANDLE = ConfigKey.Bool(
        "selector.handle",
        true,
        "Show the slim A–Z handle on the edge.\nIt shows only while the letters along the edge are off.\nOff hides it, and the selector keeps working"
    )
    val SELECTOR_LETTERS = ConfigKey.Bool(
        "selector.letters",
        true,
        "Show the letters along the edge, so you can see where to touch"
    )
    val SELECTOR_HEIGHT = ConfigKey.Num(
        "selector.height",
        400,
        200..900,
        "Length (dp) of the part of the edge you slide on. Shorter means less thumb travel.\nThe list uses the whole height"
    )
    val SELECTOR_ICONS =
        ConfigKey.Bool("selector.icons", true, "Show each app's icon in the app selector's list")
    val SELECTOR_ROW_HEIGHT = ConfigKey.Num(
        "selector.row-height",
        50,
        32..64,
        "Height (dp) of each app in the app selector's list. Taller spaces the names out.\nIt grows with a large text size"
    )
    val SELECTOR_AIM = ConfigKey.Num(
        "selector.aim",
        10,
        0..40,
        "With selector.browse apps, a slow slide along the edge moves one app per this many dp.\nAt 0, a slide always scrolls the whole list"
    )
    val SELECTOR_AIM_SPEED = ConfigKey.Num(
        "selector.aim-speed",
        100,
        20..400,
        "Slides slower than this (dp/s) move one app at a time.\nFrom 2.5 times this speed, they scroll the whole list again"
    )
    val SELECTOR_SYSTEM_FONT = ConfigKey.Bool(
        "selector.system-font",
        true,
        "Use the system font in the app selector instead of the terminal font: its list, the letters on the edge and the one beside your thumb"
    )
    val SELECTOR_ACTIONS = ConfigKey.Choice(
        "selector.actions",
        "off",
        listOf("far-side", "off"),
        "How an app's actions open while you pick an app.\nWith far-side, lift on the far side of the list.\nWith off, only a long press in the open list opens them",
        notes = mapOf(
            "far-side" to "Lift on the far side of the list for an app's actions",
            "off" to "Actions only by long-press in the open list",
        ),
    )
    val SHOW_TIMESTAMPS = ConfigKey.Choice(
        "log.time",
        "groups",
        listOf("off", "commands", "groups"),
        "Right-aligned times on log lines.\nWith groups, a time shows on each group's first line and when the minute changes.\nWith commands, a time shows on each line you type",
        notes = mapOf(
            "off" to "No times",
            "commands" to "A time on each command you type",
            "groups" to "A time on each group's first line and when the minute changes",
        ),
    )
    val LOG_DIM = ConfigKey.Bool("log.dim", true, "Dim output older than the latest command")

    /** Only `config` sets it: TTY is opt-in, and never a switch in Settings (docs/tty.org, Decided). */
    val HOME_STYLE = ConfigKey.Choice(
        "home.style",
        "gui",
        listOf("gui", "tty"),
        "How home is drawn: gui (text, chips and cards) or tty (a strict character grid, like a terminal)",
        notes = mapOf(
            "gui" to "Text, chips and cards, with pictures and sizes",
            "tty" to "A character grid, like a terminal: one font size, no pictures",
        ),
    )

    // The parts of the tty home style that can be drawn as the gui style draws them, one key each:
    // gui (the default: these parts work better that way) or tty (on the character grid). They matter
    // only with home.style tty, and only `config` sets them: TTY stays a config setting.
    val TTY_SUGGESTIONS = ConfigKey.Choice(
        "tty.suggestions",
        "gui",
        listOf("gui", "tty"),
        "How the tty home style draws the prompt's suggestions: gui (as the gui style does) or tty (on the character grid)",
        notes = mapOf(
            "gui" to "As in the gui style, with app icons",
            "tty" to "On the character grid, without icons",
        ),
    )
    val TTY_SELECTOR = ConfigKey.Choice(
        "tty.selector",
        "gui",
        listOf("gui", "tty"),
        "How the tty home style draws the app selector on the edge: gui (as the gui style does) or tty (on the character grid)",
        notes = mapOf(
            "gui" to "As in the gui style: icons, magnified rows and a letter bubble",
            "tty" to "On the character grid: one font size, no icons",
        ),
    )
    val TTY_KEYBAR = ConfigKey.Choice(
        "tty.keybar",
        "gui",
        listOf("gui", "tty"),
        "How the tty home style draws the key bar: gui (as the gui style does) or tty (on the character grid)",
        notes = mapOf(
            "gui" to "As in the gui style, with icons for the arrows and Enter",
            "tty" to "On the character grid, the keys as text",
        ),
    )
    val TTY_QUICK_ACTIONS = ConfigKey.Choice(
        "tty.quick-actions",
        "gui",
        listOf("gui", "tty"),
        "How the tty home style shows the quick actions (\$): gui (a bottom sheet, as the gui style does) or tty (a box on the character grid)",
        notes = mapOf(
            "gui" to "A bottom sheet with icons, as in the gui style",
            "tty" to "A box on the character grid, without icons",
        ),
    )
    val TTY_MENU = ConfigKey.Choice(
        "tty.menu",
        "gui",
        listOf("gui", "tty"),
        "How the tty home style shows the long-press menu (on a line, an app or the pinned header) and the script secret box.\nWith gui, a bottom sheet, as the gui style does. With tty, a box on the character grid",
        notes = mapOf(
            "gui" to "A bottom sheet with icons, as in the gui style",
            "tty" to "A box on the character grid, without icons",
        ),
    )
    /** Over home in both styles and over the full-screen screens, drawn by the GPU on Android 13 and newer (ui/tty/TtyShader.kt). The user's own files come and go ([Shaders.userNames]). The Shaders tab sets it. */
    val SCREEN_SHADER = ConfigKey.Choice(
        "screen.shader",
        ShaderCatalog.OFF,
        listOf(ShaderCatalog.OFF) + ShaderCatalog.BUNDLED,
        "A shader over home and the full-screen screens, such as matrix and clock.\nIt can be one that comes with Kolahal, or your own .agsl file. The shader command lists them.\nNeeds Android 13 or newer",
        added = { Shaders.userNames - ShaderCatalog.BUNDLED.toSet() },
        notes = mapOf(
            ShaderCatalog.OFF to "No shader",
            "crt" to "An old CRT monitor: curved glass, scanlines and a glow",
            "lcd" to "An old handheld display: square pixels, and text casts a shadow",
            "eink" to "An e-reader page: matte, nearly without color, dithered",
            "paper" to "Paper with a fine grain and fibres, and ink that soaks in",
            "riso" to "A print with a second impression in the accent color",
            "graph" to "Squared paper behind the text",
            "foil" to "Text in foil: bands of color follow your finger and the phone",
            "lamp" to "A pool of light where you last touched, the rest dimmer",
            "dusk" to "A sky behind the text that follows the time of day",
            "frost" to "Ice grows in from the edges and melts under your finger",
            "mountains" to "Misty ridges that breathe, under the hour's sun or moon",
            "pond" to "A still pond under far hills, with rings of rain",
            "dunes" to "One long dune in a desert, under the hour's sky",
            "rain" to "A calm rain out of the window: short, slow streaks, and mist",
            "snowfall" to "Snow falls behind the text and drifts as the phone leans",
            "grove-snow" to "A few crisp flakes that fall and wander slowly",
            "grove-autumn" to "A few autumn leaves that turn as they drift down",
            "aurora" to "Northern lights in the top of the screen, behind the text",
            "night" to "Stars that twinkle and a meteor, or a star chart on a light theme",
            "fireflies" to "Small lights wander behind the text, or soft motes on a light theme",
            "contour" to "A map's contour lines, slowly changing, and a touch raises a hill",
            "ink" to "A touch drops a faint cloud of ink that spreads and fades",
            "aura" to "Soft light around the edges of the screen",
            "day-ring" to "Today as a dial around the screen, glowing where the phone was used",
            "lanterns" to "Paper lanterns over water, as high as each day's unlocks",
            "kintsugi" to "A bowl mended with gold, a seam for each day's unlocks",
            "record" to "A record on a turntable, a band of grooves for each day's screen time",
            "candle" to "A candle that burns down as today's screen time is spent",
            "hourglass" to "An hourglass that runs down as today's screen time is spent",
            "windows" to "An apartment block, a floor of lit windows for each day's screen time",
            "seismograph" to "A pen draws today's screen time on a strip of paper",
            "hearts" to "A one-off: hearts rise over home once",
            "hearts-fountain" to "A one-off: a fountain of hearts from the bottom, once",
            "hearts-falling" to "A one-off: hearts drift down like petals, once",
            "hearts-stream" to "A one-off: small hearts rise along the right edge, once",
            "hearts-poppers" to "A one-off: two bursts of hearts from the bottom corners, once",
            "hearts-fireworks" to "A one-off: three fireworks open into hearts of sparks, once",
            "hearts-bounce" to "A one-off: hearts drop, bounce, settle in a row and hop away, once",
            "pulse" to "A one-off: light runs in around the screen's edges once",
            "comet" to "A one-off: a comet of light runs once around the screen's edges",
            "breath" to "A one-off: the screen's edges glow in and out twice",
            "scan-line" to "A one-off: a line of light runs down the screen once",
            "spill" to "A one-off: light runs down both sides from the top, once",
            "trace" to "A one-off: a heartbeat line runs across the bottom once",
            "northern-lights" to "A one-off: a curtain of colored light falls from the top, once",
        ),
    )
    /** A one-off shader that a notification's word plays is drawn over the app in front (shader/ShaderOverApps.kt). Only in a build that has it ([ALL]). The Shaders tab sets it. */
    val SHADER_OVER_APPS = ConfigKey.Bool(
        "screen.shader-over-apps",
        false,
        "Play a one-off shader over the app in front when a notification has one of its words, if the shader is rated for other apps.\nNeeds Android's Display over other apps access, and Android 13 or newer"
    )
    val LOG_SPACING = ConfigKey.Num(
        "log.spacing",
        5,
        0..40,
        "Gap (dp) above each output group: a command and its output, or a background batch such as feeds or scripts.\nIn the tty home style, any value but 0 is one empty line"
    )
    val LOG_ITEM_SPACING = ConfigKey.Num(
        "log.item-spacing",
        5,
        0..40,
        "Gap (dp) between the items of a list in one output group, such as stories, contacts or feed items. The color bar runs through it.\nOnly in the gui home style. The tty style has no gap there"
    )
    val LOG_GUTTER = ConfigKey.Bool(
        "log.gutter",
        true,
        "Thin color bar at the left of each log line: the accent color for commands, script color for background output, red for errors"
    )
    val DATA_PLAN = ConfigKey.Num(
        "data.plan",
        0,
        0..2000,
        "Your mobile data plan for a month, in GB. `data month` shows how much is left, and the pace.\n0 means no plan"
    )
    val DATA_START_DAY = ConfigKey.Num(
        "data.start-day",
        1,
        1..28,
        "The day of the month your mobile data plan starts again, for `data month`"
    )

    // Daily limits: a figure of today at or over one shows in the warning color, from one and a half
    // times it in the error color (usage/UsageLimits). 0 is no limit. Settings → Header has them.
    val USAGE_SCREEN_TIME_LIMIT = ConfigKey.Num(
        "usage.screen-time-limit",
        0,
        0..1440,
        "Daily limit for screen time, in minutes: 180 is 3 hours. From the limit, today's screen time shows in the warning color, and from one and a half times it in the error color.\n0 turns it off"
    )
    val USAGE_UNLOCK_LIMIT = ConfigKey.Num(
        "usage.unlock-limit",
        0,
        0..500,
        "Daily limit for unlocks. From the limit, today's unlock count shows in the warning color, and from one and a half times it in the error color.\n0 turns it off"
    )
    val LOG_LINE_HEIGHT = ConfigKey.Num(
        "log.line-height",
        22,
        10..56,
        "Minimum height of a log line. A line is never shorter than its text.\n22 is 22 dp at the default text size, and it scales with the text size"
    )
    val LOG_WALLPAPER = ConfigKey.Num(
        "log.wallpaper",
        0,
        0..100,
        "How much of the phone's wallpaper shows behind the log (%).\n0 is none. Higher shows less of the theme's color. A scene shader hides it while it is on"
    )
    val LOG_TALLER_TAPS = ConfigKey.Bool(
        "log.taller-taps",
        false,
        "Make tappable lines slightly taller, so they're easier to hit.\nOff: every line is the same height.\nOnly in the gui home style. On the tty style's grid, every line is one row"
    )
    val LOG_TABLES = ConfigKey.Choice(
        "log.tables",
        "boxed",
        listOf("boxed", "plain"),
        "How tables in script lines look: boxed (on a shaded box, with lines between rows) or plain (columns only)",
        notes = mapOf(
            "boxed" to "On a shaded box, with lines between rows",
            "plain" to "Columns only",
        ),
    )
    val LOG_FOLD = ConfigKey.Num(
        "log.fold",
        8,
        0..60,
        "Fold a log line taller than this many lines of text. A tap shows the rest.\n0 turns folding off"
    )
    val CHIP_HEIGHT = ConfigKey.Num(
        "chips.height",
        44,
        20..64,
        "Height (dp) of suggestion chips, the suggestion list's rows and app chips in the log"
    )
    val LOG_KEEP_DAYS = ConfigKey.Num(
        "log.keep-days",
        60,
        1..365,
        "Delete log lines older than this many days.\nStarred lines are kept"
    )
    val LOG_KEEP_LINES = ConfigKey.Num(
        "log.keep-lines",
        5000,
        500..50_000,
        "Delete the oldest log lines past this many.\nTyped commands are counted separately, so ↑ history is kept.\nStarred lines are kept and not counted"
    )
    val LOG_FRESH = ConfigKey.Num(
        "log.fresh",
        10,
        0..240,
        "Home starts clean when you come back after this many minutes away. One tap shows the earlier lines again.\n0 turns it off"
    )
    val LOG_TIPS = ConfigKey.Bool(
        "log.tips",
        true,
        "A tip a day in the log for your first two weeks: something you haven't used yet"
    )

    /** terminal/DismissOnDelete: a swipe dismisses once its Undo is gone, Delete line at once. Settings → Log shows it with notification access. */
    val NOTIFS_DISMISS_ON_DELETE = ConfigKey.Bool(
        "notifs.dismiss-on-delete",
        true,
        "Deleting a notification's line also dismisses the notification, once Undo is gone. Ongoing ones, such as music or a call, stay.\nOff: deleting a line only removes it from the log.\nClearing the log dismisses nothing. 'notifs clear' dismisses them all"
    )
    val FEEDS_LOG = ConfigKey.Bool("feeds.log", true, "Post new feed items in the log")
    val FEEDS_LOG_LINES = ConfigKey.Num(
        "feeds.log-lines",
        10,
        1..50,
        "New items per feed and refresh shown in the log.\nThe rest is one '+N more' line"
    )
    val FEEDS_READ_SHOWN = ConfigKey.Bool(
        "feeds.read-shown",
        false,
        "Items posted in the log count as read.\nOff: only opened or listed items do"
    )
    val FEEDS_BACKLOG_READ = ConfigKey.Bool(
        "feeds.backlog-read",
        false,
        "When you subscribe, the posts already in the feed count as read. Only newer ones are unread"
    )
    val FEEDS_INTERVAL = ConfigKey.Num(
        "feeds.interval",
        60,
        15..1440,
        "Refresh interval for newly added feeds (minutes)"
    )
    val FEEDS_NOTIFY =
        ConfigKey.Bool("feeds.notify", false, "Newly added feeds notify about new items")
    val FEEDS_WIFI = ConfigKey.Bool(
        "feeds.wifi",
        false,
        "Scheduled refreshes only on Wi-Fi (unmetered networks).\n'feed fetch' always runs"
    )
    val SEARCH_ENGINE = ConfigKey.Choice(
        "search.engine", "duckduckgo",
        listOf("duckduckgo", "google", "bing", "brave", "startpage", "kagi", "custom"),
        "Web search engine for search / g",
        notes = mapOf(
            "custom" to "The address in search.url",
        ),
    )
    val SEARCH_URL = ConfigKey.Text(
        "search.url",
        "https://duckduckgo.com/?q=%s",
        "Custom search URL (%s = query), used when search.engine = custom"
    )

    // Settings → Commands sets it with a switch per command.
    val COMMANDS_OFF = ConfigKey.Text(
        "commands.off", "",
        "Built-in commands turned off, separated by commas. With none, all are on.\nA command that is off isn't suggested or listed in help. Its name opens the app it matches, as maps opens Maps.\nThe help, config and settings commands stay on",
        com.mtwabpin.kolahal.commands.CommandsOff::parse,
    )
    val FOCUS_ON_HOME = ConfigKey.Bool(
        "keyboard.on-home",
        false,
        "Open the on-screen keyboard whenever the home screen is shown"
    )
    val BACKUP_AUTO = ConfigKey.Choice(
        "backup.auto",
        "off",
        listOf("off", "daily", "weekly"),
        "Write a backup file to a folder you pick, every day or every week",
        notes = mapOf(
            "off" to "No automatic backups",
            "daily" to "A backup file every day, in the folder you picked",
            "weekly" to "A backup file every week, in the folder you picked",
        ),
    )
    val BACKUP_KEEP = ConfigKey.Num(
        "backup.keep",
        7,
        1..60,
        "How many automatic backups to keep in the folder. Older ones are deleted"
    )
    val IME_SUGGESTIONS = ConfigKey.Bool(
        "keyboard.suggestions", false,
        "Keyboard suggestions in the prompt.\nOff: no autocorrect, because the prompt is then a password-type field. Some keyboards type only Latin letters there",
    )

    val WIDGETS_PAGE = ConfigKey.Choice(
        "widgets.page",
        "left",
        listOf("left", "right", "off"),
        "Widgets pages beside the terminal: swipe sideways on the home screen to reach them, a page at a time",
        notes = mapOf(
            "left" to "The widgets pages to the left of the log",
            "right" to "The widgets pages to the right of the log",
            "off" to "No widgets pages",
        ),
    )
    val WIDGETS_START = ConfigKey.Bool(
        "widgets.start",
        false,
        "Start on widgets page 1: home opens on it, and Home and Back return to it. Swipe sideways for the log.\nNeeds the app selector (selector.rail)"
    )

    /**
     * Whether home starts on the widgets page: widgets.start, with the widgets page on, and the app
     * selector on. Without the selector, that page would have no way to open an app, so home starts on
     * the log; widgets.start keeps its value for when the selector is back.
     */
    fun widgetsFirst(c: Config): Boolean =
        c[WIDGETS_START] && c[WIDGETS_PAGE] != "off" && c[SELECTOR_RAIL]

    val WIDGETS_WALLPAPER = ConfigKey.Num(
        "widgets.wallpaper",
        0,
        0..100,
        "How much of the phone's wallpaper shows behind the widgets page (%).\n0 is none. Higher shows less of the theme's color"
    )
    // The Assistant (assistant/, ui/assistant): only in a build that has it (BuildConfig.ASSISTANT, [ALL]).
    // Getters, not fields: the keys live in [AssistantConfig], built only when something reads them, so
    // a build without the Assistant names nothing of it here and R8 leaves it all out.
    val ASSISTANT_PAGE: ConfigKey.Bool get() = AssistantConfig.PAGE
    val ASSISTANT_SERVICE: ConfigKey.Choice get() = AssistantConfig.SERVICE
    val ASSISTANT_MODEL: ConfigKey.Text get() = AssistantConfig.MODEL
    val ASSISTANT_RICH: ConfigKey.Bool get() = AssistantConfig.RICH
    val ASSISTANT_INSTRUCTIONS: ConfigKey.Text get() = AssistantConfig.INSTRUCTIONS
    val ASSISTANT_HISTORY: ConfigKey.Bool get() = AssistantConfig.HISTORY
    val AGENDA_WIDGET_DAYS = ConfigKey.Choice(
        "widgets.agenda-days",
        "auto",
        listOf("auto", "1", "3", "7", "14"),
        "Days the Agenda widget shows from today.\nWith auto, it looks ahead up to 14 days, as many events as the card fits",
        notes = mapOf(
            "auto" to "Up to 14 days ahead, as many events as the card fits",
            "1" to "Today only",
            "3" to "Today and the next 2 days",
            "7" to "A week from today",
            "14" to "Two weeks from today",
        ),
    )

    val GESTURE_DOUBLE_TAP = ConfigKey.Text(
        "gesture.double-tap",
        if (com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE) "lock" else "none",
        "Double-tap empty log space: $GESTURE_ACTIONS, quick (quick actions) or none"
    )
    val GESTURE_SWIPE_DOWN = ConfigKey.Text(
        "gesture.swipe-down",
        "shade",
        "Swipe down on the header: $GESTURE_ACTIONS, quick or none"
    )
    val GESTURE_SWIPE_UP = ConfigKey.Text(
        "gesture.swipe-up",
        "none",
        "Swipe up on the prompt: $GESTURE_ACTIONS, quick or none.\nIt's none by default, because a swipe up is easy to make by accident. The apps command opens the app grid"
    )

    val ALL: List<ConfigKey<*>> = listOf(
        HEADER_SHOW,
        HEADER_BELOW_BAR,
        HEADER_PADDING,
        HEADER_LEFT,
        HEADER_RIGHT,
        HEADER_PINNED_SECONDS,
        HEADER_PINNED_DOTS,
        HEADER_PINNED_BAR_ABOVE,
        HEADER_PINNED_BAR_BELOW,
        USER_NAME,
        CLOCK_24H,
        KEYBAR_SHOW,
        KEYBAR_AUTO,
        KEYBAR_HEIGHT,
        PROMPT_HEIGHT_TYPING,
        PROMPT_BOTTOM_PADDING,
        KEYBAR,
        KEYBAR2,
        THEME,
        THEME_ALWAYS_DARK,
        SCREEN_HIDE_BARS,
        SCREEN_SAVER,
        SCREEN_SAVER_AFTER,
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
        HOME_STYLE,
        SCREEN_SHADER,
        // A build without drawing over other apps (BuildConfig.SHADER_OVER_APPS) has no setting for it.
        *listOfNotNull(SHADER_OVER_APPS.takeIf { com.mtwabpin.kolahal.BuildConfig.SHADER_OVER_APPS }).toTypedArray(),
        LOG_SPACING,
        LOG_ITEM_SPACING,
        LOG_GUTTER,
        LOG_LINE_HEIGHT,
        DATA_PLAN,
        DATA_START_DAY,
        USAGE_SCREEN_TIME_LIMIT,
        USAGE_UNLOCK_LIMIT,
        LOG_TALLER_TAPS,
        LOG_TABLES,
        LOG_FOLD,
        LOG_WALLPAPER,
        CHIP_HEIGHT,
        LOG_KEEP_DAYS,
        LOG_KEEP_LINES,
        LOG_FRESH,
        LOG_TIPS,
        NOTIFS_DISMISS_ON_DELETE,
        SEARCH_ENGINE,
        SEARCH_URL,
        COMMANDS_OFF,
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
        WIDGETS_PAGE,
        WIDGETS_START,
        WIDGETS_WALLPAPER,
        AGENDA_WIDGET_DAYS,
        *(if (com.mtwabpin.kolahal.BuildConfig.ASSISTANT) AssistantConfig.all() else emptyArray()),
        // Last, so they never take the place of another setting when the prompt suggests settings by what
        // they do (Completer): these matter only with home.style tty.
        TTY_SUGGESTIONS,
        TTY_SELECTOR,
        TTY_KEYBAR,
        TTY_QUICK_ACTIONS,
        TTY_MENU,
    )

    /**
     * Settings that the pages of the Settings screen leave out. Only `config` changes them, or a screen
     * of their own (the header's sides: Settings → Header → Arrange the header; the shader and where a one-off
     * plays: the Shaders tab).
     */
    val CONFIG_ONLY: Set<ConfigKey<*>> = setOf(
        LOG_KEEP_LINES,
        LOG_LINE_HEIGHT,
        DATA_PLAN,
        DATA_START_DAY,
        SELECTOR_AIM,
        SELECTOR_AIM_SPEED,
        SELECTOR_ACTIONS,
        BACKUP_KEEP,
        HEADER_PADDING,
        HEADER_PINNED_SECONDS,
        HEADER_PINNED_DOTS,
        HEADER_PINNED_BAR_ABOVE,
        HEADER_PINNED_BAR_BELOW,
        HEADER_LEFT,
        HEADER_RIGHT,
        COMMANDS_OFF,
        HOME_STYLE,
        TTY_SUGGESTIONS,
        TTY_SELECTOR,
        TTY_KEYBAR,
        TTY_QUICK_ACTIONS,
        TTY_MENU,
        SCREEN_SHADER,
        SHADER_OVER_APPS,
    )

    /** A setting by name. An old name ([RENAMED]) finds the setting under its new name. */
    fun byName(name: String): ConfigKey<*>? {
        val n = name.lowercase()
        return ALL.firstOrNull { it.key == (RENAMED[n] ?: n) }
    }

    /**
     * Setting names from before 0.3.0, old to new. Saved values move over once ([Config]). `config`
     * and backups still accept the old names.
     */
    val RENAMED: Map<String, String> = mapOf(
        "theme" to "theme.name",
        "font" to "font.family",
        "statusbar" to "screen.hide-bars",
        "screen.bars" to "screen.hide-bars",
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
        "tty.shader" to "screen.shader",
    )

    /** The old switch for swiping up on the prompt. When it is off, `gesture.swipe-up` is now `none`. */
    const val OLD_SWIPE_UP_ON = "gesture.swipeup.on"

    /**
     * Settings that were removed, with where their job went (what `config` says when given one).
     * Saved values are dropped ([migrate]). Backups that still have them import without them.
     */
    val REMOVED: Map<String, String> = mapOf(
        OLD_SWIPE_UP_ON to "set gesture.swipe-up to an action to turn swiping up on, or none to turn it off",
        "theme.settings-colors" to "Settings always use the theme's colors",
        "gui.colors" to "Settings always use the theme's colors",
        "widgets.home" to "widgets are on the widgets page (widgets.page)",
        "widgets.height" to "widgets are on the widgets page (widgets.page)",
        "theme.paper" to "the home screen's background is the theme's color; the phone's wallpaper can show behind it (log.wallpaper)",
        "header.media-always" to "what's playing always shows in the pinned header, under the header",
        // 0.4.16: always on.
        "header.media" to "what's playing always shows in the pinned header, with notification access",
        // 0.4.14: the header is one line, next to the camera.
        *HEADER_GONE.map { it to "the header is one line: choose its parts with header.left and header.right" }
            .toTypedArray(),
        // 0.4.16: two lists of parts instead of two items.
        "header.item-1" to "choose the header's parts, on each side, with header.left and header.right (Settings → Header → Arrange the header)",
        "header.item-2" to "choose the header's parts, on each side, with header.left and header.right (Settings → Header → Arrange the header)",
        *COMPACT_GONE.map { it to "the header is one line, the same while typing" }.toTypedArray(),
        "header.autohide" to "the header is one line and stays as it is",
        "header.icons" to "the header shows icons",
        "header.status" to "a script's status line shows at the top once you pin it (script pin <name>)",
        "header.compact-carried" to "the header is one line",
        "a2ui.page" to "the trial page is now the Assistant (assistant.page)",
        "a2ui.model" to "the Assistant asks the model in assistant.model",
    )

    // These are getters because [REMOVED], which is created first, reads them.
    /** The full header's own items before it became one line (0.4.14), in the order it showed them. */
    private val OLD_ITEMS
        get() = listOf(
            "unlocks",
            "battery",
            "network",
            "alarm",
            "todo",
            "settings",
            "profiles",
            "device"
        )
    private val HEADER_GONE
        get() = listOf(
            "header.clock",
            "header.date"
        ) + OLD_ITEMS.map { "header.$it" }
    private val COMPACT_GONE
        get() = listOf(
            "clock",
            "date",
            "device",
            "unlocks",
            "battery",
            "network",
            "alarm",
            "media",
            "todo",
            "status",
            "settings",
            "profiles"
        ).map { "header.compact.$it" }

    /** Which old items were on by default, for items never changed. */
    private val OLD_ON get() = setOf("unlocks", "battery", "alarm", "todo", "settings", "profiles")

    /** `screen.bars`'s values until 0.5.17, as `screen.hide-bars` says them. */
    private val OLD_BARS = mapOf("show" to "none", "hide" to "status", "immersive" to "both")

    /**
     * Saved settings ([saved]: name to raw value), moved to today's names.
     * - A value already saved under the new name wins.
     * - Settings that were removed ([REMOVED]) are left out.
     * - The old swipe-up switch (off by default, with `apps` as its action) is merged into `gesture.swipe-up`.
     */
    fun migrate(saved: Map<String, String>): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        for ((name, value) in saved) {
            if (name in REMOVED) continue
            val new = RENAMED[name] ?: name
            if (new != name && new in saved) continue
            // The bars' setting changed its values with its name: what was hidden stays hidden.
            out[new] = if (new == SCREEN_HIDE_BARS.key) OLD_BARS[value] ?: value else value
        }
        val swipeUp = GESTURE_SWIPE_UP.key
        if (swipeUp !in saved) {
            if (saved[OLD_SWIPE_UP_ON] == "on") out[swipeUp] =
                saved["gesture.swipeup"] ?: "apps" else out.remove(swipeUp)
        }
        carryRight(saved)?.let { if (HEADER_RIGHT.key !in saved) out[HEADER_RIGHT.key] = it }
        return out
    }

    /**
     * The right side, built from the two items you chose (0.4.14 to 0.4.15), or from the full
     * header's items carried over as those two. It lists those items, then the battery, as the line
     * showed them. Null if neither was changed: then the new default applies, unsaved.
     */
    private fun carryRight(saved: Map<String, String>): String? {
        val items =
            carryItems(saved) + saved.filterKeys { it == "header.item-1" || it == "header.item-2" }
        if (items.isEmpty()) return null
        val first = items["header.item-1"] ?: "alarm"
        val second = items["header.item-2"] ?: "todo"
        return (listOf(first, second).filter { it in HEADER_PARTS }
            .distinct() + "battery").joinToString(",")
    }

    /**
     * The full header's items, turned into the one-line header's two items (0.4.14): the first two
     * that were on, in the order it showed them. The battery is left aside, because the line always
     * shows it. Only used when a setting changed one of them. If none changed, the new defaults apply.
     */
    private fun carryItems(saved: Map<String, String>): Map<String, String> {
        val items = OLD_ITEMS.filter { it != "battery" && it != "device" }
        if (items.none { "header.$it" in saved }) return emptyMap()
        val on = items.filter { saved["header.$it"]?.let(HEADER_SHOW::parse) ?: (it in OLD_ON) }
        return listOf("header.item-1", "header.item-2").withIndex()
            .associate { (i, key) -> key to (on.getOrNull(i) ?: "none") }
    }
}

private const val HEADER_PARTS_HELP =
    "Any of time, date, battery, alarm (the next one), todo (open todos), network, unlocks (today's count), profiles (work and private) and settings (a button), separated by commas.\nA part shows on one side at most.\nFor an empty side, write none"

/** The parts in [raw] (separated by commas, spaces or both) as a header side's value. Returns none for an empty side, or null for an unknown or repeated part. */
internal fun headerParts(raw: String): String? {
    val parts = raw.lowercase().split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
    if (parts == listOf("none") || parts.isEmpty()) return "none"
    if (parts.any { it !in Keys.HEADER_PARTS } || parts.distinct().size != parts.size) return null
    return parts.joinToString(",")
}

/** A header side's parts, read from its value ([Keys.HEADER_LEFT], [Keys.HEADER_RIGHT]). */
internal fun headerSide(value: String): List<String> =
    value.split(',').filter { it in Keys.HEADER_PARTS }

// What a keyboard's letters page doesn't have. The rest (`.`, backspace, Enter) is right below the bar.
// From 0.4.16: the calculator's signs and @ instead of the left and right arrows (dragging across
// the bar moves the cursor).
private const val DEFAULT_KEYBAR = "/,@,|,=,+,-,*,UP,DOWN"
private const val DEFAULT_KEYBAR2 = ""

// A build without the screen lock (LOCK_SERVICE off) has no lock to offer.
private val GESTURE_ACTIONS =
    if (com.mtwabpin.kolahal.BuildConfig.LOCK_SERVICE) "a command, lock, shade" else "a command, shade"

/**
 * The Assistant's settings, as [Keys.ASSISTANT_PAGE] and the others name them. Its services and their
 * models come from Services.ALL. Read only behind BuildConfig.ASSISTANT (or through those getters): the
 * Play build never builds this object, and R8 drops it with the services and A2UI.
 */
internal object AssistantConfig {
    val PAGE = ConfigKey.Bool(
        "assistant.page",
        false,
        "Show the Assistant's page after the widgets pages, to chat with ${Services.ALL.joinToString { it.name }}.\nNeeds your own API key for the service"
    )
    val SERVICE = ConfigKey.Choice(
        "assistant.service",
        Services.ALL.first().id,
        Services.ALL.map { it.id },
        "The service the Assistant asks. Each needs its own API key",
        notes = Services.ALL.associate { it.id to "${it.name}, by ${it.company}" },
    )
    val MODEL = ConfigKey.Text(
        "assistant.model",
        "",
        "The model the Assistant asks: one the service offers, or another of its models by name.\nEmpty: the service's default",
        { it.trim().takeIf { m -> Regex("[A-Za-z0-9._:-]*").matches(m) } },
    )
    val RICH = ConfigKey.Bool(
        "assistant.rich",
        true,
        "Answers drawn as cards, lists and rows (A2UI).\nOff: answers in plain text"
    )
    val INSTRUCTIONS = ConfigKey.Text(
        "assistant.instructions",
        "",
        "Your own instructions, added to every conversation. For example: answer in Spanish",
        { it.trim().takeIf { t -> t.length <= 2000 } },
    )
    val HISTORY = ConfigKey.Bool(
        "assistant.history",
        true,
        "Keep conversations on this phone, the newest ${ConversationStore.CAP}, to open them again.\nOff: new ones aren't kept and the list is hidden. They're never in a backup"
    )

    fun all(): Array<ConfigKey<*>> = arrayOf(PAGE, SERVICE, MODEL, RICH, INSTRUCTIONS, HISTORY)
}
