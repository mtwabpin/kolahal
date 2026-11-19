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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.mtwabpin.kolahal.data.MaterialYou

/**
 * The palettes of the Material You themes ([MaterialYou.VARIANTS]): a dark one and a light one each.
 * - On Android 12 and newer they come from the wallpaper's colours. [refresh] reads them.
 * - On Android 9 to 11, and before the first [refresh], they are Material's standard colours.
 */
object MaterialYouPalettes {
    /** Material's standard colours: the dark scheme, then the light one. */
    private val standard by lazy { darkColorScheme() to lightColorScheme() }

    /**
     * The wallpaper's colours: the dark scheme, then the light one. Null until [refresh] reads them.
     * This is Compose state, so the theme cards in Settings redraw when the wallpaper changes.
     */
    private var fromWallpaper by mutableStateOf<Pair<ColorScheme, ColorScheme>?>(null)

    /** The palettes of every variation, worked out once for each pair of schemes. */
    private var made: Pair<Pair<ColorScheme, ColorScheme>, Map<Pair<String, Boolean>, Palette>>? = null

    /** The [dark] or the light palette of the Material You theme [variant] (`material-you-soft`), one of [MaterialYou.VARIANTS]. */
    fun of(dark: Boolean, variant: String = MaterialYou.NAME): Palette {
        val schemes = fromWallpaper ?: standard
        val palettes = made?.takeIf { it.first === schemes }?.second
            ?: MaterialYou.VARIANTS.flatMap { v -> listOf(true, false).map { d -> (v to d) to paletteFrom(if (d) schemes.first else schemes.second, v) } }
                .toMap().also { made = schemes to it }
        return palettes.getValue(variant to dark)
    }

    /**
     * Reads the wallpaper's colours again. Does nothing on Android 9 to 11.
     * Android changes these colours when the wallpaper changes, and tells the screen with a
     * configuration change. So the screen calls this at start and on every configuration change.
     */
    fun refresh(context: Context) {
        if (MaterialYou.supported) fromWallpaper = dynamicDarkColorScheme(context) to dynamicLightColorScheme(context)
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
 *
 * The [variant]s ([MaterialYou.VARIANTS]) differ only in the page's background and the shade under
 * cards, the prompt and the key bar. Text, accents and the good and warn colours are the same.
 * - `material-you`: the darkest surface, and the container over it.
 * - `material-you-soft`: one step lighter: the container, and the high container over it. It is
 *   near Gboard's keyboard in its Material You theme.
 * - `material-you-tinted`: the surface with some of the primary colour in it: 18% dark, 12% light.
 * - `material-you-black`: black, and the lowest container over it. For an OLED screen. Its light
 *   version is the plain one: a light theme can't be black.
 */
fun paletteFrom(scheme: ColorScheme, variant: String = MaterialYou.NAME): Palette {
    val dark = scheme.surface.luminance() < 0.5f
    val (bg, surface) = when (variant) {
        MaterialYou.SOFT -> scheme.surfaceContainer to scheme.surfaceContainerHigh
        MaterialYou.TINTED -> lerp(scheme.surface, scheme.primary, if (dark) 0.18f else 0.12f) to
            lerp(scheme.surfaceContainer, scheme.primary, if (dark) 0.22f else 0.16f)
        MaterialYou.BLACK -> if (dark) Color.Black to scheme.surfaceContainerLowest else scheme.surface to scheme.surfaceContainer
        else -> scheme.surface to scheme.surfaceContainer
    }
    return Palette(
        bg = bg,
        surface = surface,
        fg = scheme.onSurface,
        // The plain theme keeps Material's own outline; on a variation's background it may need to be darker or lighter.
        dim = if (variant == MaterialYou.NAME) scheme.outline else legible(scheme.outline, toward = scheme.onSurface, on = bg),
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

/**
 * [colour], moved toward [toward] in small steps until it reads on [on]: a contrast of at least 4,
 * as every theme's muted text has (ThemeVersionsTest). Material's outline reads on its surface, but not
 * always on the lighter background of `material-you-soft` or `material-you-tinted`.
 */
internal fun legible(colour: Color, toward: Color, on: Color): Color {
    fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }
    var c = colour
    var t = 0f
    while (contrast(c, on) < 4f && t < 1f) {
        t += 0.1f
        c = lerp(colour, toward, t)
    }
    return c
}
