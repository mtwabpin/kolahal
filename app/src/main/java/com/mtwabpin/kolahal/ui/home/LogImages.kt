/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import android.content.Context
import android.graphics.ImageDecoder
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.script.Net
import com.mtwabpin.kolahal.ui.home.LogImages.MAX_BYTES
import com.mtwabpin.kolahal.ui.theme.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.File
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Pictures from the web that scripts show: under a log line (`log(text, { image: url })`) and in an
 * `image` widget. For each picture:
 * - It is downloaded once into the cache directory (at most [MAX_BYTES]). A log line takes http and
 *   https links. A widget's picture is fetched over https only, redirects included.
 * - It is decoded off the main thread, no larger than the log shows it, or than its card needs.
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

    /** Decoded pictures in memory, as many as fit in [bytes]. */
    private class Pictures(bytes: Int) : LruCache<String, ImageBitmap>(bytes) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    /**
     * The log's pictures and the cards' pictures have room of their own, 16 MB each. So one large
     * picture in the log never pushes the cards' pictures out, and a picture on a large card (about 9 MB) fits.
     */
    private val logMemory = Pictures(16 * 1024 * 1024)
    private val cardMemory = Pictures(16 * 1024 * 1024)
    private fun memoryFor(maxPixels: Float) = if (maxPixels == MAX_PIXELS) logMemory else cardMemory
    private fun inMemory(key: String): ImageBitmap? = logMemory.get(key) ?: cardMemory.get(key)

    /** For each link, the memory key of the picture decoded last ([cachedAny]). It holds keys only, and never many. */
    private val latest = LruCache<String, String>(64)

    fun cached(
        url: String,
        widthPx: Int,
        maxPixels: Float = MAX_PIXELS,
        maxHeightPx: Int = Int.MAX_VALUE
    ): ImageBitmap? =
        memoryFor(maxPixels).get(pictureKey(url, widthPx, maxPixels, maxHeightPx))

    /**
     * The picture of [url] that is in memory, at the size it was decoded last. Null when none is.
     * It reads no file and fetches nothing. A card that is not in view asks here.
     */
    fun cachedAny(url: String): ImageBitmap? = latest.get(url)?.let(::inMemory)

    /**
     * Called when Android is short of memory. It clears the memory cache (the images are also on disk).
     * Images on screen stay. The others are decoded again when they show. What is known of each
     * link (its ink, a link that is no picture) is small, and stays.
     */
    fun trim() {
        logMemory.evictAll()
        cardMemory.evictAll()
    }

    /**
     * Returns the image at [url], or null if it cannot be loaded. Smaller images are not scaled up here.
     * - It is at most [widthPx] wide and [maxHeightPx] tall, and it has at most [maxPixels] pixels
     *   (never more than [MAX_PIXELS]) ([pictureScale]).
     * - With [httpsOnly] it is fetched over https only. A link that isn't https gives null, and so
     *   does a redirect to plain http. A widget's picture is fetched so.
     *
     * This blocks. Call it off the main thread, on [loader].
     */
    fun load(
        context: Context,
        url: String,
        widthPx: Int,
        maxPixels: Float = MAX_PIXELS,
        httpsOnly: Boolean = false,
        maxHeightPx: Int = Int.MAX_VALUE
    ): ImageBitmap? {
        cached(url, widthPx, maxPixels, maxHeightPx)?.let { return it }
        // A link that gave no picture a short while ago is not downloaded again yet.
        if (notPicture(url)) return null
        // When several lines show the same image, only one download and one decode happen. The others wait for it.
        val key = pictureKey(url, widthPx, maxPixels, maxHeightPx)
        val lock = locks.getOrPut(key) { Any() }
        try {
            synchronized(lock) {
                return cached(url, widthPx, maxPixels, maxHeightPx) ?: decode(
                    context,
                    url,
                    widthPx,
                    maxHeightPx,
                    maxPixels,
                    httpsOnly
                )
            }
        } finally {
            locks.remove(key)
        }
    }

    private fun decode(
        context: Context,
        url: String,
        widthPx: Int,
        maxHeightPx: Int,
        maxPixels: Float,
        httpsOnly: Boolean
    ): ImageBitmap? {
        val file = file(context, url, httpsOnly) ?: return null
        val bitmap = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val w = info.size.width.toFloat()
                val h = info.size.height.toFloat()
                // A small file can claim huge dimensions. Such an image is not worth decoding at any scale.
                if (w * h > MAX_SOURCE_PIXELS) throw java.io.IOException("image too large (${info.size.width}×${info.size.height})")
                val scale = pictureScale(w, h, widthPx, maxHeightPx, maxPixels)
                decoder.setTargetSize(
                    (w * scale).roundToInt().coerceAtLeast(1),
                    (h * scale).roundToInt().coerceAtLeast(1)
                )
            }
        }.getOrNull()
        // The file is not an image (for example, an error page). Delete it, so a later try downloads it
        // again: after NOT_PICTURE_MS, not at each visit to the widgets page.
        if (bitmap == null) {
            file.delete()
            notPictures.put(url, System.currentTimeMillis())
            return null
        }
        val image = bitmap.asImageBitmap()
        // A see-through picture: how light its lines are, for the backdrop it may need (pictureBackdrop).
        // Measured once for each link, not for each size.
        if (inks.get(url) == null) inks.put(
            url,
            Ink(if (bitmap.hasAlpha()) measure(file) else null)
        )
        val key = pictureKey(url, widthPx, maxPixels, maxHeightPx)
        memoryFor(maxPixels).put(key, image)
        latest.put(url, key)
        return image
    }

    /** A link whose download was no picture is tried again after this long, in milliseconds. */
    private const val NOT_PICTURE_MS = 10 * 60_000L

    /**
     * Links whose download was no picture, with the time. A link that could not be fetched (no
     * network) is not here: it is tried again at once, when the network is back.
     */
    private val notPictures = LruCache<String, Long>(64)

    private fun notPicture(url: String): Boolean =
        notPictures.get(url)?.let { System.currentTimeMillis() - it < NOT_PICTURE_MS } == true

    /** What is known of a picture's ink: null for a picture with a background of its own. */
    private class Ink(val ink: PictureInk?)

    /** The ink of each link's picture ([PictureInk]), measured when it was first decoded. */
    private val inks = LruCache<String, Ink>(256)

    /** What the lines of [url]'s picture look like, when it is see-through; null for a picture with a background of its own. */
    fun inkOf(url: String): PictureInk? = inks.get(url)?.ink

    /**
     * The ink of the picture in [file], from a small copy of it: at most 96 pixels across, decoded in
     * memory the app can read (the picture shown may be in graphics memory, which it can't).
     */
    private fun measure(file: File): PictureInk? = runCatching {
        val small = ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val scale = min(1f, 96f / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1)
            )
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val pixels = IntArray(small.width * small.height)
        small.getPixels(pixels, 0, small.width, 0, 0, small.width, small.height)
        small.recycle()
        PictureInk.of(pixels)
    }.getOrNull()

    /** Stops a request at any hop that is not https. So a redirect to plain http ends the download. */
    private val httpsHops =
        Net.HopCheck { hop -> if (!hop.url.isHttps) throw java.io.IOException("this picture is only fetched over https") }

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
            val request =
                okhttp3.Request.Builder().url(httpUrl).header("User-Agent", Net.USER_AGENT)
                    .apply { if (httpsOnly) tag(Net.HopCheck::class.java, httpsHops) }
                    .build()
            Net.client.newCall(request).execute().use { resp ->
                resp.isSuccessful && resp.body.contentLength() <= MAX_BYTES &&
                        resp.body.byteStream().use { input ->
                            tmp.outputStream().use { out ->
                                com.mtwabpin.kolahal.script.Files.copyLimited(
                                    input,
                                    out,
                                    MAX_BYTES
                                )
                            }
                        }
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
 * The memory key of one decode of [url] ([LogImages.load]). A decode for the log
 * ([LogImages.MAX_PIXELS]) and a decode for a card (fewer pixels) never share a key, also at the
 * same width. Nor do two decodes for different greatest heights.
 */
internal fun pictureKey(url: String, widthPx: Int, maxPixels: Float, maxHeightPx: Int): String {
    val size = if (maxPixels == LogImages.MAX_PIXELS) "$url@$widthPx" else "$url@$widthPx/${maxPixels.toInt()}"
    return if (maxHeightPx == Int.MAX_VALUE) size else "$size^$maxHeightPx"
}

/**
 * How much a picture [w] by [h] pixels is scaled when it is decoded: no wider than [widthPx], no
 * taller than [maxHeightPx], with at most [maxPixels] pixels (never more than
 * [LogImages.MAX_PIXELS]), and never scaled up.
 */
internal fun pictureScale(w: Float, h: Float, widthPx: Int, maxHeightPx: Int, maxPixels: Float): Float {
    val most = maxPixels.coerceIn(1f, LogImages.MAX_PIXELS)
    var scale = min(1f, min(widthPx / w, maxHeightPx / h))
    if (w * h * scale * scale > most) scale = sqrt(most / (w * h))
    return scale
}

/**
 * The picture at [url] for a place [widthPx] wide and at most [maxHeightPx] tall, with at most
 * [maxPixels] pixels ([LogImages.load]). Null while it loads. A failure when it can't be loaded.
 * - With [enabled] it is fetched, once, and decoded, off the main thread.
 * - Without it nothing is fetched and nothing is decoded. Only a picture that is already in memory
 *   shows, at the size it has there. A card that is not in view asks so.
 * - When [enabled] goes back to false, what there is stays: a loaded picture is not dropped.
 * - On a new size, the picture for the old size stays until the new one is decoded. A new link starts over.
 * - [httpsOnly]: see [LogImages.load].
 */
@Composable
internal fun rememberWebImage(
    url: String,
    widthPx: Int,
    maxPixels: Float,
    enabled: Boolean,
    httpsOnly: Boolean = false,
    maxHeightPx: Int = Int.MAX_VALUE
): Result<ImageBitmap>? {
    val context = LocalContext.current
    var state by remember(url) {
        mutableStateOf(
            (LogImages.cached(url, widthPx, maxPixels, maxHeightPx)
                ?: if (enabled) null else LogImages.cachedAny(url))?.let { Result.success(it) })
    }
    if (enabled) LaunchedEffect(url, widthPx, maxPixels, httpsOnly, maxHeightPx) {
        val image = LogImages.cached(url, widthPx, maxPixels, maxHeightPx)
            ?: withContext(LogImages.loader) {
                LogImages.load(
                    context.applicationContext,
                    url,
                    widthPx,
                    maxPixels,
                    httpsOnly,
                    maxHeightPx
                )
            }
        state =
            image?.let { Result.success(it) } ?: state?.takeIf { it.isSuccess } ?: Result.failure(
                IllegalStateException()
            )
    }
    return state
}

/**
 * A script's image under its log line.
 * - It is as wide as the log, but at most twice its own size, so an icon does not become blurry.
 * - It is never taller than 75% of the screen.
 * - A dim note shows while it loads, or if it cannot be loaded.
 * - A see-through picture whose lines barely show on the log gets a light or a dark backdrop ([pictureBackdrop]).
 * - While home is scrambled, a plain block stands in ([ScrambledPicture]), and nothing is fetched.
 */
@Composable
fun LogImage(url: String, modifier: Modifier = Modifier) {
    if (com.mtwabpin.kolahal.terminal.Scramble.isPicture(url)) return ScrambledPicture(modifier)
    val density = LocalDensity.current
    val maxHeightPx = LocalWindowInfo.current.containerSize.height * 0.75f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        // null while loading; a failure if it cannot be loaded. On a new width (the phone was
        // rotated), the image for the old width stays until the new one is decoded. A new URL starts over.
        // A tall picture is decoded no taller than it shows, so it isn't held at a size nobody sees.
        val state = rememberWebImage(
            url,
            widthPx,
            LogImages.MAX_PIXELS,
            enabled = true,
            maxHeightPx = ceil(maxHeightPx).toInt().coerceAtLeast(1)
        )
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
            // Dark lines with nothing behind them, on a dark log (or light ones on a light log): on a backdrop.
            val backdrop = pictureBackdrop(LogImages.inkOf(url), Term.bg)
            with(density) {
                Image(
                    image,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(w.toDp(), h.toDp())
                        .then(
                            if (backdrop == null) Modifier
                            else Modifier
                                .background(backdrop, RoundedCornerShape(8.dp))
                                .padding(6.dp)
                                .testTag("picture-backdrop"),
                        ),
                )
            }
        }
    }
}
