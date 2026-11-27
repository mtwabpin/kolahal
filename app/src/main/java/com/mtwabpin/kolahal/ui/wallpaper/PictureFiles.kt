/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.wallpaper

import android.app.WallpaperManager
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.OutputStream

/**
 * Shares Lab's pictures with other apps ([PictureFiles.share]) and with Android's crop-and-set screen, from the
 * app's cache: only the picture made last is there. Kept apart from the provider of the full build's updates,
 * which the Play build doesn't have, so both builds have this one.
 */
class PictureProvider : FileProvider()

/**
 * Where Lab's pictures go: the gallery ([save], in Pictures/Kolahal), a file for sharing ([cache]), and the
 * wallpaper ([cropAndSet], or [setWallpaper] where the phone has no screen of its own for it). No permission is
 * needed for any of them on Android 13, which pictures need. Not on the main thread, but for the intents.
 */
object PictureFiles {
    /** The gallery's folder, under Pictures. */
    const val FOLDER = "Kolahal"

    private const val PNG = "image/png"

    /** The cache's folder that [PictureProvider] shares. */
    private const val CACHE = "pictures"

    private fun authority(context: Context) = "${context.packageName}.pictures"

    /** Writes [bitmap] as a PNG to [out]. */
    private fun write(bitmap: Bitmap, out: OutputStream) {
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) throw IllegalStateException("the picture couldn't be written")
    }

    /**
     * Saves [bitmap] in the gallery as [name] (Pictures/Kolahal), hidden from other apps while it is written. Its
     * address, which [delete] takes. Throws when it can't be written; nothing is left behind then.
     */
    fun save(context: Context, bitmap: Bitmap, name: String): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, PNG)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/$FOLDER")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY), values)
            ?: throw IllegalStateException("the gallery refused the picture")
        try {
            (resolver.openOutputStream(uri) ?: throw IllegalStateException("the gallery refused the picture")).use { write(bitmap, it) }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    /** Deletes the picture Kolahal saved at [uri] (Undo). Whether it was there to delete. */
    fun delete(context: Context, uri: Uri): Boolean = runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)

    /** Writes [bitmap] as [name] in the cache's folder, in place of the one before, and its address for other apps. */
    fun cache(context: Context, bitmap: Bitmap, name: String): Uri {
        val dir = File(context.cacheDir, CACHE)
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, name)
        file.outputStream().use { write(bitmap, it) }
        return FileProvider.getUriForFile(context, authority(context), file)
    }

    /** Offers the picture at [uri] to another app. */
    fun share(uri: Uri): Intent {
        val send = Intent(Intent.ACTION_SEND).setType(PNG)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        send.clipData = ClipData.newRawUri(null, uri)
        return Intent.createChooser(send, null)
    }

    /** Opens the picture at [uri] in the phone's gallery or another viewer. */
    fun open(uri: Uri): Intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, PNG)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Android's own screen that crops the picture at [uri] and sets it as the wallpaper, where it asks home screen,
     * lock screen or both; null where the phone has none.
     */
    fun cropAndSet(context: Context, uri: Uri): Intent? = runCatching {
        WallpaperManager.getInstance(context).getCropAndSetWallpaperIntent(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.getOrNull()

    /** Where [setWallpaper] puts a picture. */
    enum class Where(val label: String, val flags: Int) {
        HOME("Home screen", WallpaperManager.FLAG_SYSTEM),
        LOCK("Lock screen", WallpaperManager.FLAG_LOCK),
        BOTH("Both", WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK),
    }

    /** Sets the picture at [uri] as the wallpaper of [where]. Android can't give the old one back. Whether it was set. */
    fun setWallpaper(context: Context, uri: Uri, where: Where): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            WallpaperManager.getInstance(context).setStream(input, null, true, where.flags) != 0
        } ?: false
    }.getOrDefault(false)
}
