/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.terminal.ActionIcon
import com.mtwabpin.kolahal.ui.theme.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

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

    init {
        // Let go with the rest of what the screen keeps, except the most-used apps' icons after a while away.
        com.mtwabpin.kolahal.LauncherApp.keepsInMemory { keep -> trim(keep) }
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
            cache.snapshot().keys.filter { it.substringBefore('/') in changed }
                .forEach { cache.remove(it) }
            generation++
        }
        filledAt = packageChanges
    }

    fun cached(app: AppEntry, px: Int): ImageBitmap? = cache.get("${app.key}@$px")

    /**
     * Drops the cached icons, except those of the apps in [keep] (their keys). Icons on screen stay;
     * the others load again when shown.
     */
    fun trim(keep: Set<String> = emptySet()) {
        if (keep.isEmpty()) return cache.evictAll()
        // Keys are the app's key and the size: `com.example/.Main#0@96`.
        cache.snapshot().keys.filter { it.substringBeforeLast('@') !in keep }
            .forEach { cache.remove(it) }
    }

    /** One lock for each icon being loaded, so two callers (a row and the selector's preload) don't decode it twice. */
    private val locks = ConcurrentHashMap<String, Any>()

    /**
     * The icon of [app] at [px] pixels, from the cache or loaded now. It blocks: call it off the main
     * thread. While another caller loads the same icon, it waits for that one.
     */
    fun load(context: Context, app: AppEntry, px: Int): ImageBitmap? {
        cached(app, px)?.let { return it }
        val key = "${app.key}@$px"
        val lock = locks.getOrPut(key) { Any() }
        try {
            synchronized(lock) {
                cache.get(key)?.let { return it }
                val icon = runCatching {
                    val apps = context.getSystemService(LauncherApps::class.java)
                    val info = apps.resolveActivity(Intent().setComponent(app.component), app.profile.user)
                        ?: return null
                    info.getBadgedIcon(0).toBitmap(px, px).asImageBitmap()
                }.getOrNull() ?: return null
                cache.put(key, icon)
                return icon
            }
        } finally {
            locks.remove(key)
        }
    }

    /**
     * The picture of an app's shortcut ([icon]) at [px] pixels, or null: it has none, its profile is
     * locked or paused, or Kolahal isn't the default launcher. It blocks: call it off the main thread.
     * It isn't cached: a sheet shows at most five, and lets them go when it closes. Tests replace it.
     */
    @androidx.annotation.VisibleForTesting
    var shortcut: (Context, ActionIcon.Shortcut, Int) -> ImageBitmap? = ::loadShortcut

    private fun loadShortcut(context: Context, icon: ActionIcon.Shortcut, px: Int): ImageBitmap? = runCatching {
        val profile = AppRepository.get(context).profileBySerial(icon.profile)
            ?.takeIf { !it.quiet } ?: return null
        val apps = context.getSystemService(LauncherApps::class.java)
        if (!apps.hasShortcutHostPermission()) return null
        val query = LauncherApps.ShortcutQuery()
            .setPackage(icon.pkg)
            .setShortcutIds(listOf(icon.id))
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
            )
        val info = apps.getShortcuts(query, profile.user)?.firstOrNull() ?: return null
        val picture = apps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)
            ?: return null
        val shaped = shaped(picture, px)
        // A work or private shortcut gets its profile's badge, as its app's icon does.
        if (profile.kind == ProfileKind.PERSONAL) shaped.asImageBitmap()
        else context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, shaped), profile.user)
            .toBitmap(px, px).asImageBitmap()
    }.getOrNull()

    /**
     * [picture] at [px] pixels in the system's icon shape: an adaptive icon draws itself in it, and any
     * other picture (a photo) is clipped to it.
     */
    private fun shaped(picture: Drawable, px: Int): Bitmap {
        if (picture is AdaptiveIconDrawable) return picture.toBitmap(px, px)
        val mask = AdaptiveIconDrawable(ColorDrawable(0), ColorDrawable(0))
            .apply { setBounds(0, 0, px, px) }.iconMask
        return createBitmap(px, px).also { out ->
            val canvas = Canvas(out)
            canvas.clipPath(mask)
            picture.setBounds(0, 0, px, px)
            picture.draw(canvas)
        }
    }
}

/**
 * The picture of an app's shortcut ([icon]) at [size]. The plain shortcut icon shows in [tint] until
 * it has loaded, and stays when there is none, so the layout doesn't move. It loads off the main
 * thread, and goes when this leaves the screen.
 */
@Composable
fun ShortcutIcon(icon: ActionIcon.Shortcut, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val picture by produceState<ImageBitmap?>(null, icon, px) {
        value = withContext(Dispatchers.IO) { AppIcons.shortcut(context.applicationContext, icon, px) }
    }
    val bitmap = picture
    if (bitmap == null) androidx.compose.material3.Icon(
        androidx.compose.ui.res.painterResource(ActionIcon.Shortcut.NEUTRAL),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    ) else Image(
        bitmap,
        contentDescription = null,
        modifier = modifier
            .size(size)
            .testTag("shortcut-picture"),
    )
}

/**
 * The icon of [app] at [size]. While it loads, an empty space of the same size shows, so the layout doesn't jump.
 * - [onLoaded] is told after each load whether there is an icon (false: it didn't load, and the space stays empty).
 */
@Composable
fun AppIcon(
    app: AppEntry,
    size: Dp,
    modifier: Modifier = Modifier,
    onLoaded: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val loaded by rememberUpdatedState(onLoaded)
    // The old icon stays visible while a dropped icon loads again ([AppIcons.generation]).
    val icon by produceState(AppIcons.cached(app, px), app.key, px, AppIcons.generation) {
        value = AppIcons.cached(app, px)
            ?: withContext(Dispatchers.IO) { AppIcons.load(context.applicationContext, app, px) }
        loaded?.invoke(value != null)
    }
    val bitmap = icon
    if (bitmap == null) Box(modifier.size(size)) else Image(
        bitmap,
        contentDescription = null,
        modifier = modifier.size(size)
    )
}

/**
 * Takes the place of an app icon on command and argument suggestions when `suggestions.icons` is on,
 * so every label starts in the same column. It is drawn in the prompt color, like the `$` it replaces.
 */
@Composable
fun CommandIcon(size: Dp, modifier: Modifier = Modifier) =
    KindIcon(com.mtwabpin.kolahal.R.drawable.ic_stat_terminal, size, modifier)

/** An icon for the kind of suggestion (a feed, a script, a contact), in the prompt color like [CommandIcon]. */
@Composable
fun KindIcon(id: Int, size: Dp, modifier: Modifier = Modifier) {
    androidx.compose.material3.Icon(
        androidx.compose.ui.res.painterResource(id),
        contentDescription = null,
        tint = Term.prompt,
        modifier = modifier.size(size),
    )
}
