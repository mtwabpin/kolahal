/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.control

import android.app.WallpaperManager
import android.content.Context
import androidx.core.content.edit
import androidx.core.graphics.createBitmap
import com.mtwabpin.kolahal.data.Stores

/**
 * `wallpaper theme`: sets the home screen's wallpaper to the theme's color. The wallpaper follows
 * the theme as long as it is still the one Kolahal set.
 * - Android gives a wallpaper a new id each time anything sets one. An app can read the id without
 *   seeing the picture. So if another wallpaper was set since, Kolahal leaves it alone and forgets.
 * - An id is valid only on this phone. The file that holds it is kept out of backups and transfers,
 *   because the same number on another phone may be that person's own wallpaper.
 * - The `material-you` theme takes its colors from the wallpaper. The wallpaper doesn't follow
 *   that theme, because each would keep changing the other ([themeFromWallpaper]). It follows
 *   again when a theme with colors of its own is picked.
 */
object WallpaperMatch {
    private const val KEY_ID = "id"
    // The stored key keeps its first spelling, so a saved color is still found.
    private const val KEY_COLOR = "colour"

    /** The last color asked for. When themes change faster than the wallpaper can, this is the one to end on. */
    @Volatile
    private var wanted = 0

    private fun prefs(context: Context) = Stores.WALLPAPER_MATCH.open(context.applicationContext)

    /**
     * The color of the theme on screen, fully opaque, as the screen last reported it ([follow]).
     * Null until home has applied a theme in this process: then `wallpaper theme` refuses, rather
     * than paint over your wallpaper with a color it doesn't know.
     */
    @Volatile
    var themeColor: Int? = null
        @androidx.annotation.VisibleForTesting internal set

    /**
     * True when the theme on screen takes its colors from the wallpaper, as the screen last
     * reported it ([follow]). Then `wallpaper theme` refuses, and the wallpaper stays as it is.
     */
    @Volatile
    var themeFromWallpaper = false
        private set

    /** True while the home screen's wallpaper is still the one [set] put there. */
    fun isMatched(context: Context): Boolean {
        val id = prefs(context).getInt(KEY_ID, 0)
        return id != 0 && WallpaperManager.getInstance(context)
            .getWallpaperId(WallpaperManager.FLAG_SYSTEM) == id
    }

    /** How asking for the theme's color as the wallpaper ended ([useTheme]). */
    enum class Result { SET, ALREADY, FROM_WALLPAPER, NO_COLOR, FAILED }

    /**
     * Makes the theme's color the home screen's wallpaper, as `wallpaper theme` and Settings ask once
     * the person has agreed to lose the old one. It refuses a theme that takes its colors from the
     * wallpaper, and a color it doesn't know yet.
     */
    fun useTheme(context: Context): Result {
        if (themeFromWallpaper) return Result.FROM_WALLPAPER
        if (isMatched(context)) return Result.ALREADY
        val color = themeColor ?: return Result.NO_COLOR
        return if (set(context, color)) Result.SET else Result.FAILED
    }

    /** Sets the home screen's wallpaper to [color] and saves its id. Returns false if Android doesn't allow it. */
    @Synchronized
    fun set(context: Context, color: Int): Boolean {
        val wallpapers = WallpaperManager.getInstance(context)
        if (!wallpapers.isWallpaperSupported || !wallpapers.isSetWallpaperAllowed) return false
        val solid = createBitmap(64, 64).apply { eraseColor(color) }
        val id = runCatching {
            wallpapers.setBitmap(
                solid,
                null,
                true,
                WallpaperManager.FLAG_SYSTEM
            )
        }.getOrDefault(0)
        if (id == 0) return false
        prefs(context).edit { putInt(KEY_ID, id); putInt(KEY_COLOR, color) }
        return true
    }

    /**
     * The theme is now [color]. The wallpaper follows if it is still Kolahal's.
     * - With [fromWallpaper], the theme took this color from the wallpaper. The wallpaper then
     *   stays as it is.
     * - This is called each time a theme applies, so the "nothing to do" case returns quickly.
     *   The real work runs off the main thread.
     */
    fun follow(context: Context, color: Int, fromWallpaper: Boolean = false) {
        // Kept first: the usual case below returns at once, as the wallpaper isn't Kolahal's.
        themeColor = color
        themeFromWallpaper = fromWallpaper
        if (fromWallpaper) return
        val prefs = prefs(context)
        if (!prefs.contains(KEY_ID) || prefs.getInt(KEY_COLOR, color) == color) return
        wanted = color
        val app = context.applicationContext
        Thread {
            synchronized(this) {
                val now = wanted
                if (!prefs.contains(KEY_ID) || prefs.getInt(KEY_COLOR, now) == now) return@Thread
                if (isMatched(app)) set(app, now) else forget(app)
            }
        }.start()
    }

    /** Stops following the theme for good and leaves the wallpaper as it is. It is not Kolahal's any more. */
    fun forget(context: Context) = prefs(context).edit { clear() }
}
