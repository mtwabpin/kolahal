package com.mtwabpin.kolahal.ui

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.mtwabpin.kolahal.apps.AppEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * App icons for the suggestions (`suggestions.icons`) and the app selector's list (`selector.icons`).
 * - They load off the main thread through LauncherApps, so work and private apps get the system's
 *   profile badge.
 * - They are kept in a memory cache, keyed by app and pixel size. The cache has room for every app
 *   in the selector's list (about 25 KB each), plus the suggestions' icons.
 */
object AppIcons {
    private val cache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** The [com.mtwabpin.kolahal.apps.AppRepository.packageChanges] count when the cache was filled. */
    private var filledAt = -1

    /** Increased when icons are dropped, so the icons on screen load again. */
    var generation by mutableIntStateOf(0)
        private set

    /**
     * Icons can change when an app is installed or updated. When [packageChanges] changes, the icons
     * of the changed apps ([changed]) are dropped. The other icons stay.
     */
    fun sync(packageChanges: Int, changed: Set<String>) {
        if (filledAt != -1 && filledAt != packageChanges) {
            // Keys start with the package name: `com.example/.Main#0@96`.
            cache.snapshot().keys.filter { it.substringBefore('/') in changed }.forEach { cache.remove(it) }
            generation++
        }
        filledAt = packageChanges
    }

    fun cached(app: AppEntry, px: Int): ImageBitmap? = cache.get("${app.key}@$px")

    /**
     * Called when Android is low on memory. It drops the cached icons. Icons on screen stay;
     * the others load again when shown.
     */
    fun trim() = cache.evictAll()

    fun load(context: Context, app: AppEntry, px: Int): ImageBitmap? {
        cached(app, px)?.let { return it }
        val icon = runCatching {
            val apps = context.getSystemService(LauncherApps::class.java)
            val info = apps.resolveActivity(Intent().setComponent(app.component), app.profile.user) ?: return null
            info.getBadgedIcon(0).toBitmap(px, px).asImageBitmap()
        }.getOrNull() ?: return null
        cache.put("${app.key}@$px", icon)
        return icon
    }
}

/** The icon of [app] at [size]. While it loads, an empty space of the same size shows, so the layout doesn't jump. */
@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    // The old icon stays visible while a dropped icon loads again ([AppIcons.generation]).
    val icon by produceState(AppIcons.cached(app, px), app.key, px, AppIcons.generation) {
        value = AppIcons.cached(app, px) ?: withContext(Dispatchers.IO) { AppIcons.load(context.applicationContext, app, px) }
    }
    val bitmap = icon
    if (bitmap == null) Box(modifier.size(size)) else Image(bitmap, contentDescription = null, modifier = modifier.size(size))
}

/**
 * Takes the place of an app icon on command and argument suggestions when `suggestions.icons` is on,
 * so every label starts in the same column. It is drawn in the prompt colour, like the `$` it replaces.
 */
@Composable
fun CommandIcon(size: Dp, modifier: Modifier = Modifier) = KindIcon(com.mtwabpin.kolahal.R.drawable.ic_stat_terminal, size, modifier)

/** An icon for the kind of suggestion (a feed, a script, a contact), in the prompt colour like [CommandIcon]. */
@Composable
fun KindIcon(id: Int, size: Dp, modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(
        androidx.compose.ui.res.painterResource(id),
        contentDescription = null,
        tint = Term.prompt,
        modifier = modifier.size(size),
    )
}
