/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal.commands

/**
 * What `wallpaper` says the phone's wallpaper is ([SystemCommands.wallpaper]), from what Android tells:
 * the user's own, the theme's color (`wallpaper theme`), or a shader as Kolahal's live wallpaper.
 */
object WallpaperStatus {
    /** What one screen's wallpaper is. */
    enum class Kind { YOURS, THEME, SHADER }

    /** The home screen's: Kolahal's live wallpaper ([live]) first, then the theme's color ([matched]). */
    fun home(live: Boolean, matched: Boolean): Kind = when {
        live -> Kind.SHADER
        matched -> Kind.THEME
        else -> Kind.YOURS
    }

    /**
     * The lock screen's, when it has one of its own ([own]); null when it shows the home screen's. [live]: it is
     * Kolahal's live wallpaper. `wallpaper theme` sets only the home screen's, so the lock screen's is never the
     * theme's color.
     */
    fun lock(own: Boolean, live: Boolean): Kind? = when {
        !own -> null
        live -> Kind.SHADER
        else -> Kind.YOURS
    }

    /**
     * The lines `wallpaper` prints: the home screen's, then the lock screen's when it is of another kind.
     * [shader]: the one the live wallpaper draws. [theme]: the theme in use, named when it takes its colors from
     * the wallpaper ([themeFromWallpaper]).
     */
    fun lines(home: Kind, lock: Kind?, shader: String, theme: String, themeFromWallpaper: Boolean): List<String> =
        listOfNotNull(
            when (home) {
                Kind.YOURS -> "Home screen wallpaper: your own\nKolahal doesn't change it"
                Kind.THEME ->
                    if (themeFromWallpaper) "Home screen wallpaper: Kolahal's\n" +
                        "It doesn't follow the $theme theme, which takes its colors from the wallpaper\n" +
                        "It follows again when you pick another theme"
                    else "Home screen wallpaper: the theme's color\nIt follows the theme"

                Kind.SHADER -> "Home screen wallpaper: shader $shader\nIt is Kolahal's live wallpaper"
            },
            lock?.takeIf { it != home }?.let {
                when (it) {
                    Kind.YOURS -> "Lock screen wallpaper: your own"
                    Kind.THEME -> "Lock screen wallpaper: the theme's color"
                    Kind.SHADER -> "Lock screen wallpaper: shader $shader\nIt is Kolahal's live wallpaper"
                }
            },
        )

    /**
     * One thing `wallpaper` alone offers after it has said what the wallpaper is: the [word] to type after
     * `wallpaper`, what becomes the wallpaper ([title]) and a few words more ([note]). A tap runs [run], or
     * puts [fill] in the prompt when a name is still to come.
     */
    class Choice(val word: String, val title: String, val note: String, val run: String? = null, val fill: String? = null)

    /** What can become the wallpaper, less the theme's color when the home screen has it already ([home]). */
    fun choices(home: Kind): List<Choice> = listOfNotNull(
        Choice(THEME, "A plain wallpaper in your theme's color", WHY, run = "wallpaper theme").takeIf { home != Kind.THEME },
        Choice(SHADER, "A shader as your live wallpaper", "type its name, or Set as wallpaper in Shaders", fill = "wallpaper shader "),
        Choice(LIVE, "Android's own list of live wallpapers", "for a phone whose wallpaper settings don't link to it", run = "wallpaper live"),
    )

    /** The words after `wallpaper`, which also name Settings' rows. */
    const val THEME = "theme"
    const val SHADER = "shader"
    const val LIVE = "live"

    /** One row of Settings → Look & feel → Wallpaper: which choice it is ([word]), its title and its second line. */
    class Row(val word: String, val title: String, val note: String)

    /**
     * Settings' three rows, in the order `wallpaper` lists its choices. The one the home screen has now
     * ([home]) says "In use", the shader by its name ([shader]).
     */
    fun rows(home: Kind, shader: String): List<Row> = listOf(
        Row(THEME, "Theme's color", if (home == Kind.THEME) "In use · it follows the theme" else WHY.replaceFirstChar(Char::uppercase)),
        Row(SHADER, "Shader", if (home == Kind.SHADER) "In use: $shader · tap to pick another" else "A shader as your live wallpaper: pick one in Shaders"),
        Row(LIVE, "Android's live wallpapers", "Android's own list, for a phone whose wallpaper settings don't link to it"),
    )

    /** Settings' question before the theme's color replaces the wallpaper, and what both say where Android has no list. */
    const val QUESTION = "Replace your home screen wallpaper?"
    const val NO_LIST = "This phone has no list of live wallpapers"

    /** Why one would want the theme's color: the words the command, its help and Settings share. */
    const val WHY = "so your wallpaper doesn't flash when you swipe home"

    /** What `wallpaper theme` says before it replaces the wallpaper. */
    const val REPLACES = "This replaces your home screen wallpaper\n" +
        "It becomes this theme's color, so it doesn't flash when you swipe home, and follows the theme until you set another wallpaper\n" +
        "Kolahal can't restore the old one\n" +
        "log.wallpaper and widgets.wallpaper will show only this color"

    /** The same, as Settings asks it in its dialog. */
    const val REPLACES_SHORT = "It becomes this theme's color and follows the theme, until you set another wallpaper. Kolahal can't bring the old one back."

    /** What is said of each [WallpaperMatch.Result]; [theme] is the theme in use, named where it takes its colors from the wallpaper. */
    fun said(result: com.mtwabpin.kolahal.control.WallpaperMatch.Result, theme: String): String = when (result) {
        com.mtwabpin.kolahal.control.WallpaperMatch.Result.SET ->
            "Home screen wallpaper set\n" +
                "It is this theme's color, and follows the theme until you set another wallpaper\n" +
                "The lock screen wallpaper isn't changed"
        com.mtwabpin.kolahal.control.WallpaperMatch.Result.ALREADY -> "Your wallpaper already follows the theme"
        com.mtwabpin.kolahal.control.WallpaperMatch.Result.FROM_WALLPAPER ->
            "The wallpaper can't follow $theme\nThat theme takes its colors from the wallpaper\nPick another theme, then try again"
        com.mtwabpin.kolahal.control.WallpaperMatch.Result.NO_COLOR -> "The theme's color isn't known yet\nGo home, then try again"
        com.mtwabpin.kolahal.control.WallpaperMatch.Result.FAILED -> "Couldn't change the wallpaper on this phone"
    }

    /** Whether [result] is a failure, which the log shows as an error. */
    fun failed(result: com.mtwabpin.kolahal.control.WallpaperMatch.Result): Boolean =
        result != com.mtwabpin.kolahal.control.WallpaperMatch.Result.SET && result != com.mtwabpin.kolahal.control.WallpaperMatch.Result.ALREADY
}
