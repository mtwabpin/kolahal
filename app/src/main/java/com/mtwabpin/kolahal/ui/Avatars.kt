package com.mtwabpin.kolahal.ui

import android.content.Context
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A person's picture beside their line (a call, a contact), round: their contact photo, or, without
 * one (or without the right to read it), their name's first letters on a colour of the theme picked
 * from the name, so a person always gets the same one. It is decoration: the line says the name.
 */
@Composable
internal fun Avatar(name: String, photo: String?, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState(photo?.let(Photos::cached), photo, px) {
        if (value == null && photo != null) value = withContext(Dispatchers.IO) { Photos.load(context, photo, px) }
    }
    val colour = avatarColour(name)
    val dark = Term.bg.luminance() < 0.5f
    Box(
        modifier.size(size).clip(CircleShape).background(colour.copy(alpha = if (dark) 0.26f else 0.18f)).testTag("avatar").clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val letters = remember(name) { initials(name) }
            Text(
                letters,
                style = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = (size.value * if (letters.length > 1) 0.36f else 0.44f).sp, color = colour),
                maxLines = 1,
            )
        }
    }
}

/**
 * Up to two letters for [name]: the first letters of its first and last words ("Ravi Kumar" → RK,
 * "Asha" → A), "#" for a number, "?" for nothing to show.
 */
internal fun initials(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty() || trimmed == "?") return "?"
    if (trimmed.first().isDigit() || trimmed.first() == '+') return "#"
    val words = trimmed.split(Regex("\\s+")).filter { it.first().isLetter() }
    if (words.isEmpty()) return trimmed.codePointAt(0).let { String(Character.toChars(it)) }.uppercase()
    val first = words.first().first().uppercaseChar()
    return if (words.size == 1) first.toString() else "$first${words.last().first().uppercaseChar()}"
}

/** A colour of the theme for [name], the same every time: the accent, link, info, warning and profile colours, in turn by the name. */
private fun avatarColour(name: String): Color {
    val p = Term.palette
    val colours = listOf(p.prompt, p.link, p.info, p.warn, p.work, p.private, p.script)
    return colours[Math.floorMod(name.trim().lowercase().hashCode(), colours.size)]
}

/**
 * Contact photos, read once and kept small. A photo that can't be read (no permission, gone) is
 * remembered too, so it isn't tried again on every scroll.
 */
private object Photos {
    private val cache = LruCache<String, ImageBitmap>(64)
    private val failed = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())

    fun cached(uri: String): ImageBitmap? = cache.get(uri)

    fun load(context: Context, uri: String, px: Int): ImageBitmap? {
        if (uri in failed) return null
        val image = runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()?.let { b ->
            val side = maxOf(1, px)
            (if (b.width > side * 2 || b.height > side * 2) android.graphics.Bitmap.createScaledBitmap(b, side * 2, side * 2 * b.height / maxOf(1, b.width), true) else b).asImageBitmap()
        }
        if (image == null) failed += uri else cache.put(uri, image)
        return image
    }
}
