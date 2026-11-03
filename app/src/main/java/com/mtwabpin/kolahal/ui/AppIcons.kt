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
 * App icons for the suggestions (`suggestions.icons`) and the app selector's list (`selector.icons`). Loaded off the
 * main thread through LauncherApps, so work and private apps carry the system's profile badge, then
 * kept in a memory cache keyed by app and pixel size: room for every app in the selector's list
 * (about 25 KB each) besides the suggestions'.
 */
object AppIcons {
    private val cache = object : LruCache<String, ImageBitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** The [com.mtwabpin.kolahal.apps.AppRepository.packageChanges] count the cache was filled at. */
    private var filledAt = -1

    /** Bumped when the icons are dropped, so the ones on screen load again. */
    var generation by mutableIntStateOf(0)
        private set

    /**
     * Icons can change when an app is installed or updated: once [packageChanges] moves, the icons of
     * the apps it moved for ([changed]) go; the others stay.
     */
    fun sync(packageChanges: Int, changed: Set<String>) {
        if (filledAt != -1 && filledAt != packageChanges) {
            // Keys start with the package: `com.example/.Main#0@96`.
            cache.snapshot().keys.filter { it.substringBefore('/') in changed }.forEach { cache.remove(it) }
            generation++
        }
        filledAt = packageChanges
    }

    fun cached(app: AppEntry, px: Int): ImageBitmap? = cache.get("${app.key}@$px")

    /** Android is short of memory: the kept icons go. Those on screen stay where they are; the rest load again when shown. */
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

/** [app]'s icon at [size]; an empty space of the same size while it loads (no layout jump). */
@Composable
fun AppIcon(app: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    // The old icon stays up while a dropped one loads again ([AppIcons.generation]).
    val icon by produceState(AppIcons.cached(app, px), app.key, px, AppIcons.generation) {
        value = AppIcons.cached(app, px) ?: withContext(Dispatchers.IO) { AppIcons.load(context.applicationContext, app, px) }
    }
    val bitmap = icon
    if (bitmap == null) Box(modifier.size(size)) else Image(bitmap, contentDescription = null, modifier = modifier.size(size))
}

/**
 * Stands in for an app icon on command and argument suggestions when `suggestions.icons` is on,
 * so every label starts in the same column. Drawn in the prompt colour, like the `$` it replaces.
 */
@Composable
fun CommandIcon(size: Dp, modifier: Modifier = Modifier) = KindIcon(com.mtwabpin.kolahal.R.drawable.ic_stat_terminal, size, modifier)

/** A suggestion's icon for what kind of thing it is (a feed, a script, a contact), in the prompt colour like [CommandIcon]. */
@Composable
fun KindIcon(id: Int, size: Dp, modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(
        androidx.compose.ui.res.painterResource(id),
        contentDescription = null,
        tint = Term.prompt,
        modifier = modifier.size(size),
    )
}
