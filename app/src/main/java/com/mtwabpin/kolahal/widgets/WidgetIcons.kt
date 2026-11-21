/**
 * Manual Review - Reviewed
 */
package com.mtwabpin.kolahal.widgets

/**
 * The icons a widget can name: `icon = "sun"` draws a line icon in the card's color, where an
 * emoji keeps its own colors. Pure: the drawables are in the ui layer (`widgetIconDrawable`).
 * - A name is lower case. An `icon` that is a name from [NAMES] is that icon. Any other text of up
 *   to 4 characters is drawn as it is: an emoji or a letter.
 * - The icons are Material Symbols Rounded (Apache License 2.0), in `res/drawable/wi_<name>.xml`.
 * - A name, once here, stays: a script may use it. New names can be added.
 */
object WidgetIcons {
    /** The names in groups, as the script editor's reference and the scripting guide show them. */
    val GROUPS: List<Pair<String, List<String>>> = listOf(
        "Weather" to listOf(
            "sun",
            "moon",
            "cloud",
            "rain",
            "snow",
            "storm",
            "wind",
            "fog",
            "temp",
            "drop"
        ),
        "Time" to listOf("alarm", "timer", "clock", "calendar", "bell"),
        "Home and device" to listOf(
            "home",
            "work",
            "lock",
            "unlock",
            "wifi",
            "battery",
            "bolt",
            "torch",
            "phone",
            "dnd"
        ),
        "People and talk" to listOf("person", "people", "mail", "chat", "quote"),
        "Media" to listOf("music", "play", "pause", "news", "book", "feed"),
        "Signs" to listOf(
            "heart",
            "star",
            "check",
            "flag",
            "fire",
            "leaf",
            "trophy",
            "ball",
            "cricket"
        ),
        "Money and charts" to listOf("money", "cart", "chart", "up", "down", "parcel", "gift"),
        "Going places" to listOf("car", "bus", "train", "plane", "walk", "pin", "globe", "coffee"),
        "Work" to listOf("code", "bug", "link"),
    )

    /** Every icon name: the names of all [GROUPS]. */
    val NAMES: List<String> = GROUPS.flatMap { it.second }

    private val SET = NAMES.toHashSet()

    /** Whether [icon] names one of the icons. */
    fun isName(icon: String): Boolean = icon in SET

    /** A word that looks like an icon name: lower-case letters, perhaps with a hyphen. */
    private val NAME_LIKE = Regex("[a-z]+(-[a-z]+)*")

    /** Whether [icon] looks like a name but is none: a typo, such as "sunn". Such an icon is an error. */
    fun looksLikeName(icon: String): Boolean =
        icon.length > 4 && NAME_LIKE.matches(icon) && !isName(icon)
}
