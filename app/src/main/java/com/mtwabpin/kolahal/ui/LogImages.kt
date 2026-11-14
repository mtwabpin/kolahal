package com.mtwabpin.kolahal.ui

import android.content.Context
import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import com.mtwabpin.kolahal.script.Net
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pictures from the web that scripts show: under a log line (`log(text, { image: url })`) and in an
 * `image` widget. For each picture:
 * - It is downloaded once into the cache directory (at most [MAX_BYTES]). A log line takes http and
 *   https links. A widget's picture is fetched over https only, redirects included.
 * - It is decoded off the main thread, no wider than the log, or no larger than its card needs.
 * - It is kept in a small memory cache, so scrolling back does not decode it again.
 *
 * Android may clear the cache directory. The picture is then downloaded again when it shows.
 */
object LogImages {
    init {
        // Let go with the rest of what the screen keeps: when home is away a while, or memory is short.
        com.mtwabpin.kolahal.LauncherApp.keepsInMemory { trim() }
    }

    private const val MAX_BYTES = 10L * 1024 * 1024
    /** The disk cache is trimmed to this size. The least recently shown images are deleted first. */
    private const val DISK_BYTES = 50L * 1024 * 1024
    /** A very tall image (for example, a long infographic) is decoded at a smaller size, so it does not take 100 MB. */
    const val MAX_PIXELS = 4_000_000f
    /** Sources larger than this (by their header) are not decoded at all. */
    private const val MAX_SOURCE_PIXELS = 50_000_000f
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** At most two images load at a time, so a page full of images cannot use every IO thread. */
    val loader = Dispatchers.IO.limitedParallelism(2)

    private val memory = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /** For each link, the memory key of the picture decoded last ([cachedAny]). It holds keys only, and never many. */
    private val latest = LruCache<String, String>(64)

    /**
     * The memory key of one decode of [url]. A decode for the log ([MAX_PIXELS]) and a decode for a
     * card (fewer pixels) never share a key, also at the same width.
     */
    private fun key(url: String, widthPx: Int, maxPixels: Float): String =
        if (maxPixels == MAX_PIXELS) "$url@$widthPx" else "$url@$widthPx/${maxPixels.toInt()}"

    fun cached(url: String, widthPx: Int, maxPixels: Float = MAX_PIXELS): ImageBitmap? = memory.get(key(url, widthPx, maxPixels))

    /**
     * The picture of [url] that is in memory, at the size it was decoded last. Null when none is.
     * It reads no file and fetches nothing. A card that is not in view asks here.
     */
    fun cachedAny(url: String): ImageBitmap? = latest.get(url)?.let(memory::get)

    /**
     * Called when Android is short of memory. It clears the memory cache (the images are also on disk).
     * Images on screen stay. The others are decoded again when they show.
     */
    fun trim() = memory.evictAll()

    /**
     * Returns the image at [url], or null if it cannot be loaded. Smaller images are not scaled up here.
     * - It is at most [widthPx] wide, and it has at most [maxPixels] pixels (never more than [MAX_PIXELS]).
     * - With [httpsOnly] it is fetched over https only. A link that isn't https gives null, and so
     *   does a redirect to plain http. A widget's picture is fetched so.
     *
     * This blocks. Call it off the main thread, on [loader].
     */
    fun load(context: Context, url: String, widthPx: Int, maxPixels: Float = MAX_PIXELS, httpsOnly: Boolean = false): ImageBitmap? {
        cached(url, widthPx, maxPixels)?.let { return it }
        // When several lines show the same image, only one download and one decode happen. The others wait for it.
        val key = key(url, widthPx, maxPixels)
        val lock = locks.getOrPut(key) { Any() }
        try {
            synchronized(lock) { return cached(url, widthPx, maxPixels) ?: decode(context, url, widthPx, maxPixels, httpsOnly) }
        } finally {
            locks.remove(key)
        }
    }

    private fun decode(context: Context, url: String, widthPx: Int, maxPixels: Float, httpsOnly: Boolean): ImageBitmap? {
        val file = file(context, url, httpsOnly) ?: return null
        val most = maxPixels.coerceIn(1f, MAX_PIXELS)
        val image = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val w = info.size.width.toFloat()
                val h = info.size.height.toFloat()
                // A small file can claim huge dimensions. Such an image is not worth decoding at any scale.
                if (w * h > MAX_SOURCE_PIXELS) throw java.io.IOException("image too large (${info.size.width}×${info.size.height})")
                var scale = min(1f, widthPx / w)
                if (w * h * scale * scale > most) scale = sqrt(most / (w * h))
                decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
            }.asImageBitmap()
        }.getOrNull()
        // The file is not an image (for example, an error page). Delete it, so the next time the line shows it tries again.
        if (image == null) {
            file.delete()
            return null
        }
        val key = key(url, widthPx, maxPixels)
        memory.put(key, image)
        latest.put(url, key)
        return image
    }

    /** Stops a request at any hop that is not https. So a redirect to plain http ends the download. */
    private val httpsHops = Net.HopCheck { hop -> if (!hop.url.isHttps) throw java.io.IOException("this picture is only fetched over https") }

    /**
     * The cached download of [url], fetching it first if needed. With [httpsOnly], a link that
     * isn't https has no file, and the download stops at a hop that isn't https.
     */
    private fun file(context: Context, url: String, httpsOnly: Boolean): File? {
        val httpUrl = url.toHttpUrlOrNull()
        if (httpsOnly && httpUrl?.isHttps != true) return null
        val dir = File(context.cacheDir, "log-images").apply { mkdirs() }
        val name = com.mtwabpin.kolahal.data.sha256Hex(url.toByteArray())
        val file = File(dir, name)
        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        if (httpUrl == null) return null
        val tmp = File(dir, "$name.${System.nanoTime()}.part")
        val ok = runCatching {
            val request = okhttp3.Request.Builder().url(httpUrl).header("User-Agent", Net.USER_AGENT)
                .apply { if (httpsOnly) tag(Net.HopCheck::class.java, httpsHops) }
                .build()
            Net.client.newCall(request).execute().use { resp ->
                resp.isSuccessful && resp.body.contentLength() <= MAX_BYTES &&
                    resp.body.byteStream().use { input -> tmp.outputStream().use { out -> com.mtwabpin.kolahal.script.Files.copyLimited(input, out, MAX_BYTES) } }
            }
        }.getOrDefault(false)
        if (!ok || !tmp.renameTo(file)) {
            tmp.delete()
            return file.takeIf { it.exists() }
        }
        trim(dir)
        return file
    }


    private fun trim(dir: File) {
        val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
        var total = 0L
        files.forEach { f ->
            total += f.length()
            if (total > DISK_BYTES) f.delete()
        }
    }
}

/**
 * The picture at [url] for a place [widthPx] wide, with at most [maxPixels] pixels
 * ([LogImages.load]). Null while it loads. A failure when it can't be loaded.
 * - With [enabled] it is fetched, once, and decoded, off the main thread.
 * - Without it nothing is fetched and nothing is decoded. Only a picture that is already in memory
 *   shows, at the size it has there. A card that is not in view asks so.
 * - When [enabled] goes back to false, what there is stays: a loaded picture is not dropped.
 * - On a new size, the picture for the old size stays until the new one is decoded. A new link starts over.
 * - [httpsOnly]: see [LogImages.load].
 */
@Composable
internal fun rememberWebImage(url: String, widthPx: Int, maxPixels: Float, enabled: Boolean, httpsOnly: Boolean = false): Result<ImageBitmap>? {
    val context = LocalContext.current
    var state by remember(url) {
        mutableStateOf((LogImages.cached(url, widthPx, maxPixels) ?: if (enabled) null else LogImages.cachedAny(url))?.let { Result.success(it) })
    }
    if (enabled) LaunchedEffect(url, widthPx, maxPixels, httpsOnly) {
        val image = LogImages.cached(url, widthPx, maxPixels)
            ?: withContext(LogImages.loader) { LogImages.load(context.applicationContext, url, widthPx, maxPixels, httpsOnly) }
        state = image?.let { Result.success(it) } ?: state?.takeIf { it.isSuccess } ?: Result.failure(IllegalStateException())
    }
    return state
}

/**
 * A script's image under its log line.
 * - It is as wide as the log, but at most twice its own size, so an icon does not become blurry.
 * - It is never taller than 75% of the screen.
 * - A dim note shows while it loads, or if it cannot be loaded.
 */
@Composable
fun LogImage(url: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val maxHeightPx = LocalWindowInfo.current.containerSize.height * 0.75f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        // null while loading; a failure if it cannot be loaded. On a new width (the phone was
        // rotated), the image for the old width stays until the new one is decoded. A new URL starts over.
        val state = rememberWebImage(url, widthPx, LogImages.MAX_PIXELS, enabled = true)
        val image = state?.getOrNull()
        if (image == null) {
            Text(
                if (state == null) "Loading image…" else "Image unavailable",
                style = Term.text.copy(color = Term.dim),
            )
        } else {
            var w = min(widthPx.toFloat(), image.width * 2f)
            var h = w * image.height / image.width
            if (h > maxHeightPx) {
                w *= maxHeightPx / h
                h = maxHeightPx
            }
            with(density) {
                Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.size(w.toDp(), h.toDp()))
            }
        }
    }
}
