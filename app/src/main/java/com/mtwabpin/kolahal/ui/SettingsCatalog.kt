package com.mtwabpin.kolahal.ui

import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen

/** Readable titles for settings. The description comes from the key's help text. */
internal val TITLES = mapOf(
    "theme.name" to "Theme",
    "theme.always-dark" to "Always dark",
    "screen.bars" to "Full screen",
    "font.family" to "Font",
    "font.sans" to "System font",
    "font.size" to "Text size",
    "header.show" to "Show the header",
    "header.below-bar" to "Below the status bar area",
    "header.padding" to "Padding",
    "header.left" to "Left side",
    "header.right" to "Right side",
    "header.pinned-seconds" to "Seconds per item",
    "header.pinned-dots" to "Dots up to",
    "header.pinned-bar-above" to "Space above the bar",
    "header.pinned-bar-below" to "Space below the bar",
    "clock.24h" to "24-hour clock",
    "user.name" to "Your name",
    "widgets.page" to "Widgets page (swipe sideways)",
    "widgets.start" to "Start on the widgets page",
    "widgets.wallpaper" to "Transparency (%)",
    "log.time" to "Times",
    "log.dim" to "Dim older output",
    "log.spacing" to "Space between output groups (dp)",
    "log.gutter" to "Colour bar beside lines",
    "log.line-height" to "Line height",
    "log.taller-taps" to "Taller tappable lines",
    "log.tables" to "Tables",
    "log.fold" to "Fold long lines after (lines)",
    "log.wallpaper" to "Transparency (%)",
    "log.keep-days" to "Keep log for (days)",
    "log.fresh" to "Clean screen after (minutes away)",
    "suggestions.auto" to "Only while typing",
    "suggestions.idle" to "Favourites & most-used on empty prompt",
    "suggestions.max" to "Maximum suggestions",
    "suggestions.style" to "Style while typing",
    "suggestions.icons" to "Show app icons",
    "suggestions.wrap" to "Wrap descriptions",
    "selector.rail" to "Show the app selector",
    "selector.browse" to "Slide through",
    "selector.filter" to "Only that letter's apps",
    "selector.side" to "Edge",
    "selector.position" to "Position on the edge",
    "selector.handle" to "Show the A–Z handle",
    "selector.letters" to "Show letters on the edge",
    "selector.height" to "Length on the edge (dp)",
    "selector.row-height" to "Row height (dp)",
    "selector.icons" to "Show app icons",
    "selector.system-font" to "System font",
    "suggestions.rows" to "Chip rows",
    "chips.height" to "Chip height (dp)",
    "keybar.show" to "Show the key bar",
    "keybar.auto" to "Only while typing",
    "keybar.height" to "Row height (dp)",
    "keybar.row1" to "Row 1 keys",
    "keybar.row2" to "Row 2 keys",
    "prompt.height" to "Prompt height (dp)",
    "keyboard.suggestions" to "Keyboard autocorrect & suggestions",
    "keyboard.on-home" to "Open the keyboard on Home",
    "backup.auto" to "Automatic backups",
    "log.tips" to "Daily tips",
    "backup.keep" to "Backups to keep",
    "launch.instant" to "Instant launch",
    "launch.instant-letters" to "Letters before instant launch",
    "launch.top" to "Enter opens the best match",
    "launch.single" to "Enter opens the only match",
    "search.engine" to "Search engine",
    "search.url" to "Custom search URL",
    "gesture.double-tap" to "Double-tap empty space",
    "gesture.swipe-down" to "Swipe down on the header",
    "gesture.swipe-up" to "Swipe up on the prompt",
    "feeds.log" to "Show new items in the log",
    "feeds.log-lines" to "Items per feed per refresh",
    "feeds.read-shown" to "Items shown in the log count as read",
    "feeds.backlog-read" to "Mark existing posts read when subscribing",
    "feeds.interval" to "Refresh interval for new feeds (min)",
    "feeds.notify" to "Notify for new feeds",
    "feeds.wifi" to "Refresh on Wi-Fi only",
)

/**
 * Sliders that show home through Settings while the user drags them, like the brightness slider in
 * quick settings. The value says whether home shows in typing mode (key bar, prompt, suggestions).
 */
internal val PEEK_SLIDERS: Map<String, Boolean> = mapOf(
    Keys.FONT_SIZE.key to false,
    Keys.LOG_LINE_HEIGHT.key to false,
    Keys.LOG_SPACING.key to false,
    Keys.SELECTOR_HEIGHT.key to false,
    // While this slider moves, home shows the selector's list open.
    Keys.SELECTOR_ROW_HEIGHT.key to false,
    // The phone's wallpaper behind the log, and behind the widgets page (that page shows instead of the log).
    Keys.LOG_WALLPAPER.key to false,
    Keys.WIDGETS_WALLPAPER.key to false,
    Keys.KEYBAR_HEIGHT.key to true,
    Keys.PROMPT_HEIGHT_TYPING.key to true,
    Keys.SUGGESTIONS.key to true,
    Keys.SUGGESTION_ROWS.key to true,
    Keys.CHIP_HEIGHT.key to true,
)

/**
 * Settings that only matter when another setting allows them. For example, the key bar's rows do
 * nothing while the key bar is off. Settings hides them until then, but `config` can still change every setting.
 */
private val SHOWN_WHEN: Map<ConfigKey<*>, (Config) -> Boolean> = run {
    fun on(switch: ConfigKey.Bool): (Config) -> Boolean = { it[switch] }
    fun choice(key: ConfigKey.Choice, test: (String) -> Boolean): (Config) -> Boolean = { test(it[key]) }
    mapOf(
        Keys.SEARCH_URL to choice(Keys.SEARCH_ENGINE) { it == "custom" },
        Keys.LAUNCH_INSTANT_MIN to choice(Keys.LAUNCH_INSTANT) { it != "off" },
        Keys.SUGGESTION_ROWS to choice(Keys.SUGGESTIONS_STYLE) { it == "chips" },
        // Only the list style shows descriptions.
        Keys.SUGGESTIONS_WRAP to choice(Keys.SUGGESTIONS_STYLE) { it != "chips" },
        Keys.KEYBAR_AUTO to on(Keys.KEYBAR_SHOW),
        Keys.KEYBAR_HEIGHT to on(Keys.KEYBAR_SHOW),
        Keys.KEYBAR to on(Keys.KEYBAR_SHOW),
        Keys.KEYBAR2 to on(Keys.KEYBAR_SHOW),
        Keys.SELECTOR_BROWSE to on(Keys.SELECTOR_RAIL),
        Keys.SELECTOR_FILTER to { c: Config -> c[Keys.SELECTOR_RAIL] && c[Keys.SELECTOR_BROWSE] == "letters" },
        Keys.WIDGETS_WALLPAPER to { c: Config -> c[Keys.WIDGETS_PAGE] != "off" },
        Keys.WIDGETS_START to { c: Config -> c[Keys.WIDGETS_PAGE] != "off" },
        Keys.SELECTOR_SIDE to on(Keys.SELECTOR_RAIL),
        Keys.SELECTOR_POSITION to on(Keys.SELECTOR_RAIL),
        Keys.SELECTOR_HANDLE to on(Keys.SELECTOR_RAIL),
        Keys.SELECTOR_LETTERS to on(Keys.SELECTOR_RAIL),
        Keys.SELECTOR_HEIGHT to on(Keys.SELECTOR_RAIL),
        Keys.SELECTOR_ICONS to on(Keys.SELECTOR_RAIL),
        Keys.SELECTOR_ROW_HEIGHT to on(Keys.SELECTOR_RAIL),
        Keys.SELECTOR_SYSTEM_FONT to on(Keys.SELECTOR_RAIL),
        Keys.FEEDS_LOG_LINES to on(Keys.FEEDS_LOG),
        Keys.FEEDS_READ_SHOWN to on(Keys.FEEDS_LOG),
        // Without the header, its placement and its swipe do not apply. The pinned header, the 24-hour
        // clock (used for the log's times) and your name (used by fetch) still apply.
        Keys.HEADER_BELOW_BAR to on(Keys.HEADER_SHOW),
        Keys.GESTURE_SWIPE_DOWN to on(Keys.HEADER_SHOW),
    )
}

/** Whether Settings shows [key], given the current values of the other settings ([SHOWN_WHEN]). */
internal fun applies(key: ConfigKey<*>, config: Config): Boolean = SHOWN_WHEN[key]?.invoke(config) ?: true

/** Pages with an eye button that shows home while held. The value is the same typing flag as in [PEEK_SLIDERS]. */
internal val PEEK_PAGES: Map<String, Boolean> = mapOf("look" to false, "header" to false, "selector" to false, "log" to false, "suggestions" to true, "keyboard" to true)

/** A settings page with grouped keys. A null group title means the group has no sub-header. */
internal class Category(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: Int,
    val groups: List<Pair<String?, List<ConfigKey<*>>>>,
    /** A manager screen this page links to, shown at the top: title, subtitle, screen. */
    val manage: Triple<String, String, Screen>? = null,
) {
    val keys get() = groups.flatMap { it.second }
}

private val CATEGORIES = listOf(
    Category(
        "look", "Look & feel", "Theme, font, text size, full screen", R.drawable.ic_palette,
        listOf(
            "Terminal" to listOf(
                Keys.THEME,
                Keys.THEME_ALWAYS_DARK,
                Keys.FONT,
                Keys.FONT_SANS,
                Keys.FONT_SIZE,
            ),
            "Screen" to listOf(Keys.STATUS_BAR),
        ),
    ),
    Category(
        HEADER_CATEGORY, "Header", "Its parts on each side, clock, your name", R.drawable.ic_home,
        listOf(
            null to listOf(
                Keys.HEADER_SHOW,
                Keys.HEADER_BELOW_BAR,
            ),
            "Clock and name" to listOf(
                Keys.CLOCK_24H,
                Keys.USER_NAME,
            ),
        ),
        manage = Triple("Arrange the header", "The parts on each side and their order", Screen.HEADER_PARTS),
    ),
    Category(
        "widgets", "Widgets", "Add, resize and arrange widgets", R.drawable.ic_widgets,
        listOf(
            null to listOf(
                Keys.WIDGETS_PAGE,
                Keys.WIDGETS_START,
                Keys.WIDGETS_WALLPAPER,
            ),
        ),
        manage = Triple("Arrange widgets", "Add, resize and reorder widgets", Screen.WIDGETS),
    ),
    Category(
        "log", "Log", "Times, spacing, line height, history", R.drawable.ic_stat_terminal,
        listOf(
            "Display" to listOf(
                Keys.SHOW_TIMESTAMPS,
                Keys.LOG_DIM,
                Keys.LOG_GUTTER,
                Keys.LOG_SPACING,
                Keys.LOG_LINE_HEIGHT,
                Keys.LOG_TALLER_TAPS,
                Keys.LOG_TABLES,
                Keys.LOG_FOLD,
                Keys.LOG_WALLPAPER,
            ),
            "History" to listOf(
                Keys.LOG_FRESH,
                Keys.LOG_KEEP_DAYS,
            ),
            "Tips" to listOf(Keys.LOG_TIPS),
        ),
    ),
    Category(
        FEEDS_CATEGORY, "Feeds", "Refresh, log, read / unread, notifications", R.drawable.ic_rss,
        listOf(
            "In the log" to listOf(
                Keys.FEEDS_LOG,
                Keys.FEEDS_LOG_LINES,
            ),
            "Read / unread" to listOf(
                Keys.FEEDS_READ_SHOWN,
                Keys.FEEDS_BACKLOG_READ,
            ),
            "Refreshing" to listOf(Keys.FEEDS_WIFI),
            "New subscriptions" to listOf(
                Keys.FEEDS_INTERVAL,
                Keys.FEEDS_NOTIFY,
            ),
        ),
    ),
    Category(
        "suggestions", "Suggestions", "Style, icons, when they show", R.drawable.ic_keyboard_tab,
        listOf(
            null to listOf(
                Keys.SUGGESTIONS_STYLE,
                Keys.SUGGEST_ICONS,
                Keys.SUGGESTIONS_WRAP,
                Keys.SUGGESTIONS_AUTO,
                Keys.SUGGESTION_ROWS,
                Keys.SUGGEST_IDLE,
                Keys.SUGGESTIONS,
                Keys.CHIP_HEIGHT,
            ),
        ),
    ),
    Category(
        "keyboard", "Keyboard", "Key bar, prompt, autocorrect", R.drawable.ic_keyboard,
        listOf(
            "Key bar" to listOf(
                Keys.KEYBAR_SHOW,
                Keys.KEYBAR_AUTO,
                Keys.KEYBAR_HEIGHT,
                Keys.KEYBAR,
                Keys.KEYBAR2,
            ),
            "Prompt & keyboard" to listOf(
                Keys.PROMPT_HEIGHT_TYPING,
                Keys.IME_SUGGESTIONS,
                Keys.FOCUS_ON_HOME,
            ),
        ),
    ),
    // The app selector is separate from typing: it works while the keyboard is hidden.
    Category(
        "selector", "App selector", "Edge, letters, icons", R.drawable.ic_apps,
        listOf(
            null to listOf(Keys.SELECTOR_RAIL),
            "On the edge" to listOf(
                Keys.SELECTOR_BROWSE,
                Keys.SELECTOR_SIDE,
                Keys.SELECTOR_POSITION,
                Keys.SELECTOR_HEIGHT,
                Keys.SELECTOR_HANDLE,
                Keys.SELECTOR_LETTERS,
            ),
            "The list" to listOf(
                Keys.SELECTOR_FILTER,
                Keys.SELECTOR_ROW_HEIGHT,
                Keys.SELECTOR_ICONS,
                Keys.SELECTOR_SYSTEM_FONT,
            ),
        ),
    ),
    Category(
        "launch", "Launching & search", "Instant launch, Enter, web search", R.drawable.ic_search,
        listOf(
            "Launching" to listOf(
                Keys.LAUNCH_INSTANT,
                Keys.LAUNCH_INSTANT_MIN,
                Keys.LAUNCH_TOP,
                Keys.AUTO_LAUNCH,
            ),
            "Web search" to listOf(
                Keys.SEARCH_ENGINE,
                Keys.SEARCH_URL,
            ),
        ),
    ),
    Category(
        "gestures", "Gestures", "Double-tap and swipes", R.drawable.ic_touch,
        listOf(null to listOf(Keys.GESTURE_DOUBLE_TAP, Keys.GESTURE_SWIPE_DOWN, Keys.GESTURE_SWIPE_UP)),
    ),
)

/** The Backup page. It shows automatic backups, then the backup folder and the file actions ([SettingsScreen]). */
internal const val BACKUP_CATEGORY = "backup"

/** The Header page. It shows the top of home above its settings (HeaderPreview). */
internal const val HEADER_CATEGORY = "header"

private val BACKUP = Category(BACKUP_CATEGORY, "Backup", "Automatic backups, export, import", R.drawable.ic_download, listOf(null to listOf(Keys.BACKUP_AUTO)))

/** Keys that no page lists (for example, keys added later) still show under "More". [Keys.CONFIG_ONLY] keys do not. */
private val OTHER: Category by lazy {
    val listed = (CATEGORIES + BACKUP).flatMap { it.keys }.toSet()
    Category("other", "More", "Everything else", R.drawable.ic_tune, listOf(null to Keys.ALL.filter { it !in listed && it !in Keys.CONFIG_ONLY }))
}

/** The feeds category. It shows in the Feeds tab ([categorySettings]), not in the Settings list. */
internal const val FEEDS_CATEGORY = "feeds"

internal fun allCategories() = CATEGORIES + BACKUP + listOfNotNull(OTHER.takeIf { it.keys.isNotEmpty() })

internal fun matches(key: ConfigKey<*>, q: String): Boolean {
    val title = TITLES[key.key] ?: key.key
    return listOf(title, key.key, key.help).any { it.contains(q, ignoreCase = true) }
}
