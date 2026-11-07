package com.mtwabpin.kolahal.ui

import android.app.WallpaperManager
import android.content.Context
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.edit
import androidx.core.graphics.createBitmap

/**
 * `wallpaper match`: sets the home screen's wallpaper to the theme's colour. The wallpaper follows
 * the theme as long as it is still the one Kolahal set.
 * - Android gives a wallpaper a new id each time anything sets one. An app can read the id without
 *   seeing the picture. So if another wallpaper was set since, Kolahal leaves it alone and forgets.
 * - An id is valid only on this phone. The file that holds it is kept out of backups and transfers,
 *   because the same number on another phone may be that person's own wallpaper.
 */
object WallpaperMatch {
    private const val PREFS = "wallpaper_match"
    private const val KEY_ID = "id"
    private const val KEY_COLOUR = "colour"

    /** The last colour asked for. When themes change faster than the wallpaper can, this is the one to end on. */
    @Volatile private var wanted = 0

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** The theme's colour in a form a wallpaper can show: fully opaque. */
    fun themeColour(): Int = Term.bg.copy(alpha = 1f).toArgb()

    /** True while the home screen's wallpaper is still the one [set] put there. */
    fun isMatched(context: Context): Boolean {
        val id = prefs(context).getInt(KEY_ID, 0)
        return id != 0 && WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM) == id
    }

    /** Sets the home screen's wallpaper to [colour] and saves its id. Returns false if Android doesn't allow it. */
    @Synchronized
    fun set(context: Context, colour: Int): Boolean {
        val wallpapers = WallpaperManager.getInstance(context)
        if (!wallpapers.isWallpaperSupported || !wallpapers.isSetWallpaperAllowed) return false
        val solid = createBitmap(64, 64).apply { eraseColor(colour) }
        val id = runCatching { wallpapers.setBitmap(solid, null, true, WallpaperManager.FLAG_SYSTEM) }.getOrDefault(0)
        if (id == 0) return false
        prefs(context).edit { putInt(KEY_ID, id); putInt(KEY_COLOUR, colour) }
        return true
    }

    /**
     * The theme is now [colour]. The wallpaper follows if it is still Kolahal's.
     * This is called each time a theme applies, so the "nothing to do" case returns quickly.
     * The real work runs off the main thread.
     */
    fun follow(context: Context, colour: Int) {
        val prefs = prefs(context)
        if (!prefs.contains(KEY_ID) || prefs.getInt(KEY_COLOUR, colour) == colour) return
        wanted = colour
        val app = context.applicationContext
        Thread {
            synchronized(this) {
                val now = wanted
                if (!prefs.contains(KEY_ID) || prefs.getInt(KEY_COLOUR, now) == now) return@Thread
                if (isMatched(app)) set(app, now) else forget(app)
            }
        }.start()
    }

    /** Stops following the theme for good and leaves the wallpaper as it is. It is not Kolahal's any more. */
    fun forget(context: Context) = prefs(context).edit { clear() }
}
