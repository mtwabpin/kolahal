package com.mtwabpin.kolahal.ui

import android.content.Context
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mtwabpin.kolahal.data.MaterialYou

/**
 * The palettes of the `material-you` theme ([MaterialYou]): a dark one and a light one.
 * - On Android 12 and newer they come from the wallpaper's colours. [refresh] reads them.
 * - On Android 9 to 11, and before the first [refresh], they are Material's standard colours.
 */
object MaterialYouPalettes {
    /** Material's standard colours: the dark palette, then the light one. */
    private val standard by lazy { paletteFrom(darkColorScheme()) to paletteFrom(lightColorScheme()) }

    /**
     * The wallpaper's colours: the dark palette, then the light one. Null until [refresh] reads them.
     * This is Compose state, so the theme cards in Settings redraw when the wallpaper changes.
     */
    private var fromWallpaper by mutableStateOf<Pair<Palette, Palette>?>(null)

    /** The [dark] or the light palette. */
    fun of(dark: Boolean): Palette = (fromWallpaper ?: standard).let { (d, l) -> if (dark) d else l }

    /**
     * Reads the wallpaper's colours again. Does nothing on Android 9 to 11.
     * Android changes these colours when the wallpaper changes, and tells the screen with a
     * configuration change. So the screen calls this at start and on every configuration change.
     */
    fun refresh(context: Context) {
        if (MaterialYou.supported) fromWallpaper = paletteFrom(dynamicDarkColorScheme(context)) to paletteFrom(dynamicLightColorScheme(context))
    }
}

/** Material's green at the tone of its accents: 80 for a dark scheme, then 40 for a light one. */
private val GOOD = Color(0xFF78DC77) to Color(0xFF006E1C)

/** Material's amber at the same two tones. */
private val WARN = Color(0xFFFFB951) to Color(0xFF825500)

/**
 * Maps a Material colour scheme to a terminal palette, for the `material-you` theme.
 *
 * Material has three accents. Each gets two jobs here:
 * - primary: the prompt, and personal apps (as in the other themes);
 * - secondary: Kolahal's messages, and the private space;
 * - tertiary: links, and work apps.
 *
 * The text colours are the ones Material itself puts on a surface, so they are easy to read:
 * - onSurface for text;
 * - onSurfaceVariant for script output;
 * - outline for muted text.
 *
 * Good and warn are fixed: green and amber, in a tone for a dark scheme or for a light one.
 * A wallpaper's colour can't mean "good" or "warning". Material keeps its error colour fixed
 * for the same reason.
 *
 * [schemeFrom] goes the other way, and the two agree: the GUI screens get back the scheme's own
 * primary, secondary, tertiary, error and surface.
 */
fun paletteFrom(scheme: ColorScheme): Palette {
    val dark = scheme.surface.luminance() < 0.5f
    return Palette(
        bg = scheme.surface,
        surface = scheme.surfaceContainer,
        fg = scheme.onSurface,
        dim = scheme.outline,
        prompt = scheme.primary,
        error = scheme.error,
        info = scheme.secondary,
        link = scheme.tertiary,
        script = scheme.onSurfaceVariant,
        personal = scheme.primary,
        work = scheme.tertiary,
        private = scheme.secondary,
        good = if (dark) GOOD.first else GOOD.second,
        warn = if (dark) WARN.first else WARN.second,
    )
}
