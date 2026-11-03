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
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Images that scripts attach to log lines (`log(text, { image: url })`). Each is downloaded once
 * into the cache directory (http and https only, at most [MAX_BYTES]), decoded off the main thread
 * no wider than the log, and kept in a small memory cache so scrolling back doesn't decode again.
 * Android may clear the cache directory; an image is then fetched again when its line shows.
 */
object LogImages {
    private const val MAX_BYTES = 10L * 1024 * 1024
    /** The disk cache is trimmed to this, least recently shown first. */
    private const val DISK_BYTES = 50L * 1024 * 1024
    /** A very tall image (a long infographic) is decoded smaller rather than taking 100 MB. */
    private const val MAX_PIXELS = 4_000_000f
    /** Larger sources (by their header) aren't decoded at all. */
    private const val MAX_SOURCE_PIXELS = 50_000_000f
    private val locks = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** At most two pictures load at a time, so a page of them can't use every IO thread. */
    val loader = Dispatchers.IO.limitedParallelism(2)

    private val memory = object : LruCache<String, ImageBitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun cached(url: String, widthPx: Int): ImageBitmap? = memory.get("$url@$widthPx")

    /** Android is short of memory: the kept pictures go (they're on disk too). Those on screen stay; the rest are decoded again when shown. */
    fun trim() = memory.evictAll()

    /** [url]'s image at most [widthPx] wide (smaller ones aren't scaled up here), or null if it can't be had. */
    fun load(context: Context, url: String, widthPx: Int): ImageBitmap? {
        cached(url, widthPx)?.let { return it }
        // Several lines showing the same picture: one download and one decode, the rest wait for it.
        val key = "$url@$widthPx"
        val lock = locks.getOrPut(key) { Any() }
        try {
            synchronized(lock) { return cached(url, widthPx) ?: decode(context, url, widthPx) }
        } finally {
            locks.remove(key)
        }
    }

    private fun decode(context: Context, url: String, widthPx: Int): ImageBitmap? {
        val file = file(context, url) ?: return null
        val image = runCatching {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val w = info.size.width.toFloat()
                val h = info.size.height.toFloat()
                // A small file can claim enormous dimensions: not worth decoding at any scale.
                if (w * h > MAX_SOURCE_PIXELS) throw java.io.IOException("image too large (${info.size.width}×${info.size.height})")
                var scale = min(1f, widthPx / w)
                if (w * h * scale * scale > MAX_PIXELS) scale = sqrt(MAX_PIXELS / (w * h))
                decoder.setTargetSize((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1))
            }.asImageBitmap()
        }.getOrNull()
        // Not an image (an error page, say): drop it, so the next showing tries again.
        if (image == null) {
            file.delete()
            return null
        }
        memory.put("$url@$widthPx", image)
        return image
    }

    /** The cached download of [url], fetching it first if needed. */
    private fun file(context: Context, url: String): File? {
        val dir = File(context.cacheDir, "log-images").apply { mkdirs() }
        val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
        val file = File(dir, name)
        if (file.exists()) {
            file.setLastModified(System.currentTimeMillis())
            return file
        }
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val tmp = File(dir, "$name.${System.nanoTime()}.part")
        val ok = runCatching {
            val request = okhttp3.Request.Builder().url(httpUrl).header("User-Agent", Net.USER_AGENT).build()
            Net.client.newCall(request).execute().use { resp ->
                resp.isSuccessful && resp.body.contentLength() <= MAX_BYTES &&
                    resp.body.byteStream().use { input -> tmp.outputStream().use { out -> copyLimited(input, out) } }
            }
        }.getOrDefault(false)
        if (!ok || !tmp.renameTo(file)) {
            tmp.delete()
            return file.takeIf { it.exists() }
        }
        trim(dir)
        return file
    }

    /** Copies at most [MAX_BYTES]; false when there is more (the length header can be missing or wrong). */
    private fun copyLimited(input: InputStream, out: OutputStream): Boolean {
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf)
            if (n < 0) return true
            total += n
            if (total > MAX_BYTES) return false
            out.write(buf, 0, n)
        }
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
 * A script's image under its log line, as wide as the log (up to twice its own size, so an icon
 * doesn't turn into a blur) and never taller than most of the screen. A dim note while it loads
 * or if it can't be loaded.
 */
@Composable
fun LogImage(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val maxHeightPx = LocalWindowInfo.current.containerSize.height * 0.75f
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val widthPx = constraints.maxWidth.coerceAtLeast(1)
        // null while loading; Result.failure once it can't be had. A new width (the phone turned) keeps
        // the image decoded for the old one until it's decoded again; a new address starts over.
        var state by remember(url) { mutableStateOf(LogImages.cached(url, widthPx)?.let { Result.success(it) }) }
        LaunchedEffect(url, widthPx) {
            val image = LogImages.cached(url, widthPx)
                ?: withContext(LogImages.loader) { LogImages.load(context.applicationContext, url, widthPx) }
            state = image?.let { Result.success(it) } ?: state?.takeIf { it.isSuccess } ?: Result.failure(IllegalStateException())
        }
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
