/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.shader

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.wallpaper.WallpaperService
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.Stores
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The shader that Kolahal's live wallpaper draws: apart from the one over home (`screen.shader`). Setting one
 * opens Android's own preview the first time, as Android asks people before a live wallpaper is set; when
 * Kolahal's is the wallpaper already, it changes at once.
 */
object WallpaperChoice {
    /** The one drawn while none was chosen: someone picked Kolahal in Android's own list. */
    const val DEFAULT = "pond"

    private const val KEY = "shader"

    private val _name = MutableStateFlow<String?>(null)

    /** The shader that was chosen, or null. */
    val name: StateFlow<String?> = _name.asStateFlow()

    val supported: Boolean get() = Build.VERSION.SDK_INT >= Shaders.MIN_SDK

    fun load(context: Context) {
        _name.value = Stores.WALLPAPER_SHADER.open(context).getString(KEY, null)
    }

    fun set(context: Context, name: String?) {
        _name.value = name
        Stores.WALLPAPER_SHADER.open(context).edit { if (name == null) remove(KEY) else putString(KEY, name) }
    }

    /** Kolahal's live wallpaper, the only one it has: found in the manifest, as the service is the screen's. */
    private fun component(context: Context): ComponentName? =
        context.packageManager.queryIntentServices(Intent(WallpaperService.SERVICE_INTERFACE).setPackage(context.packageName), 0)
            .firstOrNull()?.serviceInfo?.let { ComponentName(it.packageName, it.name) }

    /**
     * Whether Kolahal's live wallpaper is the phone's: the home screen's, or from Android 14 the lock screen's
     * alone. Asks Android: not on the main thread.
     */
    fun isWallpaper(context: Context): Boolean = supported && runCatching {
        val manager = WallpaperManager.getInstance(context)
        val own = component(context) ?: return@runCatching false
        val lock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) manager.getWallpaperInfo(WallpaperManager.FLAG_LOCK) else null
        manager.wallpaperInfo?.component == own || lock?.component == own
    }.getOrDefault(false)

    /** Android's preview of it, where people set it. */
    fun preview(context: Context): Intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
        .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component(context))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Why shader [name] with [meta] is no wallpaper, or null when it can be one. */
    fun problem(name: String, meta: ShaderMeta): String? = when {
        !supported -> Shaders.NEEDS
        meta.once != null -> "$name is a one-off: it plays once"
        meta.text -> "$name works on text, and a wallpaper has none"
        else -> null
    }
}
