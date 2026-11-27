/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.settings

import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.commands.Accesses
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.HomeStyles
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.SearchWords
import com.mtwabpin.kolahal.data.SettingTitles
import com.mtwabpin.kolahal.terminal.LineSwipes
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.ScreenSaver

/**
 * Sliders that show home through Settings while the user drags them, like the brightness slider in
 * quick settings. The value says whether home shows in typing mode (key bar, prompt, suggestions).
 */
internal val PEEK_SLIDERS: Map<String, Boolean> = mapOf(
    Keys.FONT_SIZE.key to false,
    Keys.LOG_SPACING.key to false,
    Keys.LOG_ITEM_SPACING.key to false,
    Keys.SELECTOR_HEIGHT.key to false,
    // While this slider moves, home shows the selector's list open.
    Keys.SELECTOR_ROW_HEIGHT.key to false,
    // The phone's wallpaper behind the log, and behind the widgets page (that page shows instead of the log).
    Keys.LOG_WALLPAPER.key to false,
    Keys.WIDGETS_WALLPAPER.key to false,
    Keys.KEYBAR_HEIGHT.key to true,
    Keys.PROMPT_HEIGHT_TYPING.key to true,
    // The space shows with the keyboard closed: the keyboard covers it.
    Keys.PROMPT_BOTTOM_PADDING.key to false,
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
    fun choice(key: ConfigKey.Choice, test: (String) -> Boolean): (Config) -> Boolean =
        { test(it[key]) }
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
        Keys.WIDGETS_WALLPAPER to { c: Config -> c[Keys.WIDGETS_SHOW] },
        // The widgets page as home's first page needs the app selector, its way to open apps.
        Keys.WIDGETS_START to { c: Config -> c[Keys.WIDGETS_SHOW] && c[Keys.SELECTOR_RAIL] },
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
        // A screensaver that the gui style doesn't run (matrix, clock, pipes) is off there.
        Keys.SCREEN_SAVER_AFTER to { c: Config -> ScreenSaver.inEffect(c[Keys.SCREEN_SAVER], HomeStyles.ttyNow(c)) != ScreenSaver.OFF },
        // The tty style has no gap between a list's items, and no taller lines (HomeStyles.GUI_ONLY).
        Keys.LOG_ITEM_SPACING to { c: Config -> HomeStyles.applies(Keys.LOG_ITEM_SPACING, HomeStyles.ttyNow(c)) },
        Keys.LOG_TALLER_TAPS to { c: Config -> HomeStyles.applies(Keys.LOG_TALLER_TAPS, HomeStyles.ttyNow(c)) },
    )
}

/**
 * The values Settings offers for a choice: every value, but for `screen.saver`, whose values follow
 * the home style ([ScreenSaver.choices]): off and decode in the gui style.
 */
internal fun shownChoices(key: ConfigKey.Choice, config: Config): List<String> =
    if (key == Keys.SCREEN_SAVER) ScreenSaver.choices(HomeStyles.ttyNow(config)) else key.choices

/**
 * The value Settings marks as chosen for a choice: the stored one, but
 * - for a screensaver the gui style doesn't run, which shows as off there ([ScreenSaver.inEffect]);
 * - for a line's swipe, what that side does now: the defaults trade sides with the page order
 *   ([LineSwipes]), in a layout that is right-to-left when [rtl] is.
 * The stored value stays.
 */
internal fun shownChoice(key: ConfigKey.Choice, value: String, config: Config, rtl: Boolean = false): String = when (key) {
    Keys.SCREEN_SAVER -> ScreenSaver.inEffect(value, HomeStyles.ttyNow(config))
    Keys.LOG_SWIPE_LEFT -> LineSwipes.of(config, rtl).left.id
    Keys.LOG_SWIPE_RIGHT -> LineSwipes.of(config, rtl).right.id
    else -> value
}

/**
 * What Settings writes when [key] is set to [value]: that value, and for a line's swipe the other side's
 * action as it shows ([shownChoice], from the swipes in effect [now]) too. A side the defaults traded then
 * keeps what it does, instead of going back to its own default.
 */
internal fun choiceWrites(key: ConfigKey.Choice, value: String, now: LineSwipes): List<Pair<ConfigKey.Choice, String>> =
    when (key) {
        Keys.LOG_SWIPE_LEFT -> listOf(key to value, Keys.LOG_SWIPE_RIGHT to now.right.id)
        Keys.LOG_SWIPE_RIGHT -> listOf(Keys.LOG_SWIPE_LEFT to now.left.id, key to value)
        else -> listOf(key to value)
    }

/** Whether Settings shows [key], given the current values of the other settings ([SHOWN_WHEN]). */
internal fun applies(key: ConfigKey<*>, config: Config): Boolean =
    SHOWN_WHEN[key]?.invoke(config) ?: true

/**
 * Settings that can't work without the accesses Android gives Kolahal ([Accesses], by their words). Without one,
 * Settings hides them, and their group shows a line and a button that asks for it ([groupShows]). The stored
 * value stays, and `config` can still change every setting.
 */
internal val NEEDS_ACCESS: Map<ConfigKey<*>, List<String>> = buildMap {
    // Screen time isn't known without usage access, and unlocks are then counted only while Kolahal runs.
    put(Keys.USAGE_SCREEN_TIME_LIMIT, listOf(Accesses.USAGE))
    put(Keys.USAGE_UNLOCK_LIMIT, listOf(Accesses.USAGE))
    // Without notification access there are no notification lines, and nothing to dismiss.
    put(Keys.NOTIFS_DISMISS_ON_DELETE, listOf(Accesses.READ_NOTIFICATIONS))
    // No word plays a one-off shader without notification access, and none plays over an app without
    // Display over other apps. The Shaders tab shows its switch. A build without it (BuildConfig.SHADER_OVER_APPS) has neither the setting nor that access.
    if (BuildConfig.SHADER_OVER_APPS) put(Keys.SHADER_OVER_APPS, listOf(Accesses.READ_NOTIFICATIONS, Accesses.OVERLAY))
}

/** What a group of settings shows: the settings it draws ([keys]), then a line and a button for each access in [asks]. */
internal data class GroupShows(val keys: List<ConfigKey<*>>, val asks: List<String>) {
    val isEmpty: Boolean get() = keys.isEmpty() && asks.isEmpty()
}

/**
 * What a group shows of [keys] (those that apply, [applies]) while the accesses [missing] aren't given: each
 * setting that has every access it [needs], and each missing access that one of the others needs, once.
 */
internal fun groupShows(
    keys: List<ConfigKey<*>>,
    missing: Set<String>,
    needs: Map<ConfigKey<*>, List<String>> = NEEDS_ACCESS,
): GroupShows {
    val lacks = keys.map { key -> needs[key].orEmpty().filter { it in missing } }
    return GroupShows(keys.filterIndexed { i, _ -> lacks[i].isEmpty() }, lacks.flatten().distinct())
}

/** Pages with an eye button that shows home while held. The value is the same typing flag as in [PEEK_SLIDERS]. */
internal val PEEK_PAGES: Map<String, Boolean> = mapOf(
    "look" to false,
    "header" to false,
    "selector" to false,
    "log" to false,
    "suggestions" to true,
    "keyboard" to true
)

/** A settings page with grouped keys. A null group title means the group has no sub-header. */
internal class Category(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: Int,
    val groups: List<Pair<String?, List<ConfigKey<*>>>>,
    /** The manager screens this page links to, shown at the top. */
    val manage: List<Manage> = emptyList(),
) {
    val keys get() = groups.flatMap { it.second }
}

/** A manager screen a Settings page links to: its row's title, subtitle and icon (null: the page's own). */
internal class Manage(val title: String, val subtitle: String, val screen: Screen, val icon: Int? = null)

private val CATEGORIES = listOf(
    Category(
        "look", "Look & feel", "Theme, font, text size, system bars, screensaver", R.drawable.ic_palette,
        listOf(
            "Terminal" to listOf(
                Keys.THEME,
                Keys.THEME_ALWAYS_DARK,
                Keys.FONT,
                Keys.FONT_SANS,
                Keys.FONT_SIZE,
            ),
            "Screen" to listOf(Keys.SCREEN_HIDE_BARS),
            "Screensaver" to listOf(Keys.SCREEN_SAVER, Keys.SCREEN_SAVER_AFTER),
        ),
    ),
    Category(
        HEADER_CATEGORY, "Header", "Its parts on each side, clock, your name, daily limits", R.drawable.ic_home,
        listOf(
            null to listOf(
                Keys.HEADER_SHOW,
                Keys.HEADER_BELOW_BAR,
            ),
            "Clock and name" to listOf(
                Keys.CLOCK_24H,
                Keys.USER_NAME,
            ),
            // The header's unlocks show them first; usage, unlocks and top color their figures too.
            "Daily limits" to listOf(
                Keys.USAGE_SCREEN_TIME_LIMIT,
                Keys.USAGE_UNLOCK_LIMIT,
            ),
        ),
        manage = listOf(
            Manage(
                "Arrange the header",
                "The parts on each side and their order",
                Screen.HEADER_PARTS
            )
        ),
    ),
    Category(
        "widgets", "Widgets", "Add, resize and arrange widgets", R.drawable.ic_widgets,
        listOf(
            null to listOf(
                Keys.WIDGETS_SHOW,
                Keys.WIDGETS_START,
                Keys.WIDGETS_WALLPAPER,
                Keys.AGENDA_WIDGET_DAYS,
                Keys.WIDGET_CORNERS,
            ),
        ),
        manage = listOf(
            Manage("Arrange widgets", "Add, resize and reorder widgets", Screen.WIDGETS),
            // home.pages: the order of the log, the widgets pages and the Assistant.
            Manage("Page order", "The order of home's pages", Screen.HOME_PAGES, R.drawable.ic_sort),
        ),
    ),
    Category(
        "log", "Log", "Times, spacing, line height, history", R.drawable.ic_stat_terminal,
        listOf(
            "Display" to listOf(
                Keys.SHOW_TIMESTAMPS,
                Keys.LOG_DIM,
                Keys.LOG_GUTTER,
                Keys.LOG_SPACING,
                Keys.LOG_ITEM_SPACING,
                Keys.LOG_TALLER_TAPS,
                Keys.LOG_TABLES,
                Keys.LOG_FOLD,
                Keys.LOG_WALLPAPER,
            ),
            "Swipes" to listOf(
                Keys.LOG_SWIPE_LEFT,
                Keys.LOG_SWIPE_RIGHT,
            ),
            "History" to listOf(
                Keys.LOG_FRESH,
                Keys.LOG_KEEP_DAYS,
            ),
            "Tips" to listOf(Keys.LOG_TIPS),
            "Notifications" to listOf(Keys.NOTIFS_DISMISS_ON_DELETE),
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
                Keys.PROMPT_BOTTOM_PADDING,
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
    // Its switches set commands.off (CommandsPage).
    Category(
        COMMANDS_CATEGORY,
        "Commands",
        "Turn off the ones you don't use",
        R.drawable.ic_terminal,
        listOf(null to emptyList())
    ),
    Category(
        "gestures", "Gestures", "Double-tap and swipes", R.drawable.ic_touch,
        listOf(
            null to listOf(
                Keys.GESTURE_DOUBLE_TAP,
                Keys.GESTURE_SWIPE_DOWN,
                Keys.GESTURE_SWIPE_UP
            )
        ),
    ),
)

/**
 * A screen that Settings search finds by its [title], its [subtitle] and [words]. With [after], it also
 * has a row of its own on Settings' main page, after that category. A tab has none: the tab row shows it.
 */
internal class ScreenRow(
    val title: String,
    val subtitle: String,
    val icon: Int,
    val screen: Screen,
    val after: String?,
    val words: String,
) {
    /** Whether the search for [q] finds it: each word of [q], in any order ([SearchWords.allIn]). */
    fun matches(q: String): Boolean =
        SearchWords.allIn(SearchWords.of(q), listOf(title, subtitle, words))
}

/** The screens Settings search finds besides the settings. The Shaders row is found by the names of its settings too. */
internal val SCREEN_ROWS = listOf(
    ScreenRow(
        "Shaders",
        "Effects over home: CRT, paper, your own",
        R.drawable.ic_shader,
        Screen.SHADERS,
        after = null,
        words = "${Keys.SCREEN_SHADER.key} ${Keys.SHADER_OVER_APPS.key} shader effect crt rain paper scanlines glow agsl glsl one-off play over other apps",
    ),
)

/** The Backup page. It shows automatic backups, then the backup folder and the file actions ([SettingsScreen]). */
internal const val BACKUP_CATEGORY = "backup"

/** The Header page. It shows the top of home above its settings (HeaderPreview). */
internal const val HEADER_CATEGORY = "header"

private val BACKUP = Category(
    BACKUP_CATEGORY,
    "Backup",
    "Automatic backups, export, import",
    R.drawable.ic_download,
    listOf(null to listOf(Keys.BACKUP_AUTO))
)

/** The feeds category. It shows in the Feeds tab ([categorySettings]), not in the Settings list. */
internal const val FEEDS_CATEGORY = "feeds"

/** The Assistant's page: its rows are drawn by ui/assistant (assistantSettings), these keys are what search finds. */
const val ASSISTANT_CATEGORY = "assistant"

// A function, not a value: only called behind BuildConfig.ASSISTANT, so a build without it names none of its keys.
private fun assistantCategory() = Category(
    ASSISTANT_CATEGORY,
    "Assistant",
    "Service, model, API keys, history",
    R.drawable.ic_forum,
    listOf(
        null to listOf(Keys.ASSISTANT_PAGE),
        "Service" to listOf(Keys.ASSISTANT_SERVICE, Keys.ASSISTANT_MODEL),
        "Answers" to listOf(Keys.ASSISTANT_RICH, Keys.ASSISTANT_INSTRUCTIONS),
        "History" to listOf(Keys.ASSISTANT_HISTORY),
    ),
)

/**
 * Every settings page. Each setting is on one, except the [Keys.CONFIG_ONLY] ones: a test checks this.
 * A build without the Assistant (BuildConfig.ASSISTANT) has no page for it.
 */
internal fun allCategories() = CATEGORIES + (if (BuildConfig.ASSISTANT) listOf(assistantCategory()) else emptyList()) + BACKUP

/**
 * Whether Settings search for [q] finds [key]: each word of [q], in any order, is in the setting's
 * title, its config name, its help or one of its choices' labels ([SearchWords.allIn], as the other
 * pages search). "size font" finds Font size; "solarized" finds the theme.
 */
internal fun matches(key: ConfigKey<*>, q: String): Boolean {
    val title = SettingTitles[key.key] ?: key.key
    val choices =
        if (key is ConfigKey.Choice) key.choices.map { ChoiceLabels.of(key, it) } else emptyList()
    return SearchWords.allIn(SearchWords.of(q), listOf(title, key.key, key.help) + choices)
}
