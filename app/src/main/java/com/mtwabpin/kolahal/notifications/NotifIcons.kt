package com.mtwabpin.kolahal.notifications

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Icon
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.createBitmap
import java.io.File

/**
 * Status-bar icons of notifications. They show at the end of log lines instead of the app name.
 * - Each icon is saved once, as a small PNG, when its notification arrives.
 * - The key is the resource's package, its id and the app's version. So old lines keep the right
 *   icon after the app updates.
 * - Only resource icons are saved (nearly all apps use them). Other lines show the app name.
 */
object NotifIcons {
    private const val PX = 48
    private const val VERSION_TTL_MS = 10 * 60_000L
    /** Above this count, the oldest files are deleted. Their lines then show the app name. */
    private const val MAX_FILES = 400

    @Volatile private var known: MutableSet<String>? = null
    private val memory = object : LruCache<String, ImageBitmap>(64) {}

    private fun dir(context: Context) = File(context.filesDir, "notif-icons")

    /** Key for [icon] posted by [pkg], or null when it isn't a resource icon. It is cheap: it draws nothing. */
    fun key(context: Context, pkg: String, icon: Icon?): String? {
        if (icon == null || icon.type != Icon.TYPE_RESOURCE) return null
        val resPkg = icon.resPackage.takeIf { it.isNotEmpty() } ?: pkg
        val version = versionOf(context, resPkg)
        return "${resPkg}_${Integer.toHexString(icon.resId)}_$version"
    }

    /** App versions for icon keys. They are cached for a while, because a busy app can post many notifications a minute. */
    private val versions = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Long>>()

    private fun versionOf(context: Context, pkg: String): Long {
        val now = System.currentTimeMillis()
        versions[pkg]?.let { (version, at) -> if (now - at < VERSION_TTL_MS) return version }
        val version = runCatching { context.packageManager.getPackageInfo(pkg, 0).longVersionCode }.getOrDefault(0L)
        versions[pkg] = version to now
        return version
    }

    /** Lists the saved icons off the main thread, so that the first log line with an icon doesn't have to. */
    fun preload(context: Context) {
        knownSet(context)
    }

    /** True when the icon for [key] is saved. The directory is listed once, then kept in memory. */
    fun has(context: Context, key: String): Boolean = knownSet(context).contains(key)

    private fun knownSet(context: Context): MutableSet<String> = known ?: synchronized(this) {
        known ?: java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply {
            dir(context).list()?.forEach { if (it.endsWith(".png")) add(it.removeSuffix(".png")) }
        }.also { known = it }
    }

    /** Draws and saves [icon] under [key], unless it is already saved. Call it off the main thread. */
    fun save(context: Context, key: String, icon: Icon) {
        val set = knownSet(context)
        if (key in set) return
        val drawable = icon.loadDrawable(context) ?: return
        val bitmap = createBitmap(PX, PX, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, PX, PX)
        drawable.draw(Canvas(bitmap))
        val dir = dir(context).apply { mkdirs() }
        val tmp = File(dir, "$key.${System.nanoTime()}.part")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        if (!tmp.renameTo(File(dir, "$key.png"))) {
            tmp.delete()
            return
        }
        set += key
        trim(dir, set)
    }

    private fun trim(dir: File, set: MutableSet<String>) {
        val files = dir.listFiles { f -> f.name.endsWith(".png") } ?: return
        if (files.size <= MAX_FILES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_FILES).forEach {
            it.delete()
            set -= it.name.removeSuffix(".png")
        }
    }

    /** Returns the saved icon for [key], or null. Call it off the main thread the first time. */
    fun load(context: Context, key: String): ImageBitmap? {
        memory.get(key)?.let { return it }
        val file = File(dir(context), "$key.png")
        val image = runCatching { BitmapFactory.decodeFile(file.path)?.asImageBitmap() }.getOrNull() ?: return null
        memory.put(key, image)
        return image
    }

    fun cached(key: String): ImageBitmap? = memory.get(key)
}
