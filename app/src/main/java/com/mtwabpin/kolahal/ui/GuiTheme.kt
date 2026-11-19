package com.mtwabpin.kolahal.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * The Material 3 Expressive theme for the GUI screens (settings, script manager) and the widget cards.
 * The colours come from the terminal palette, so these screens match the chosen theme.
 * - Inside another GuiTheme it adds nothing: the palette is the same. A screen of cards, or a
 *   card in a sheet, has one theme, not one for each card.
 * - The colour scheme is made once for each palette ([schemeOf]), not once for each card.
 */
@Composable
fun GuiTheme(content: @Composable () -> Unit) {
    val palette = Term.palette
    if (LocalGuiPalette.current === palette) {
        content()
        return
    }
    val scheme = remember(palette) { schemeOf(palette) }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
    ) {
        CompositionLocalProvider(LocalGuiPalette provides palette, content = content)
    }
}

/** The palette of the GuiTheme around, or null outside one. */
private val LocalGuiPalette = staticCompositionLocalOf<Palette?> { null }

/** The scheme made last, with its palette: the palette rarely changes, and every card asks. */
@Volatile private var lastScheme: Pair<Palette, ColorScheme>? = null

private fun schemeOf(palette: Palette): ColorScheme =
    lastScheme?.takeIf { it.first === palette }?.second ?: schemeFrom(palette).also { lastScheme = palette to it }

/** Maps a terminal palette to a full Material colour scheme (dark). */
fun schemeFrom(p: Palette): ColorScheme {
    return darkColorScheme(
        primary = p.prompt,
        onPrimary = p.bg,
        primaryContainer = lerp(p.bg, p.prompt, 0.30f),
        onPrimaryContainer = p.fg,
        secondary = p.info,
        onSecondary = p.bg,
        secondaryContainer = lerp(p.bg, p.info, 0.25f),
        onSecondaryContainer = p.fg,
        tertiary = p.work,
        onTertiary = p.bg,
        tertiaryContainer = lerp(p.bg, p.work, 0.25f),
        onTertiaryContainer = p.fg,
        error = p.error,
        onError = p.bg,
        errorContainer = lerp(p.bg, p.error, 0.25f),
        onErrorContainer = p.fg,
        background = p.bg,
        onBackground = p.fg,
        surface = p.bg,
        onSurface = p.fg,
        surfaceVariant = lerp(p.bg, p.fg, 0.12f),
        onSurfaceVariant = p.dim,
        surfaceTint = p.prompt,
        inverseSurface = p.fg,
        inverseOnSurface = p.bg,
        inversePrimary = lerp(p.prompt, p.bg, 0.4f),
        outline = p.dim,
        outlineVariant = lerp(p.bg, p.dim, 0.45f),
        surfaceBright = lerp(p.bg, p.fg, 0.16f),
        surfaceDim = p.bg,
        surfaceContainerLowest = lerp(p.bg, Color.Black, 0.4f),
        surfaceContainerLow = lerp(p.bg, p.fg, 0.04f),
        surfaceContainer = lerp(p.bg, p.fg, 0.07f),
        surfaceContainerHigh = lerp(p.bg, p.fg, 0.10f),
        surfaceContainerHighest = lerp(p.bg, p.fg, 0.14f),
        scrim = Color.Black,
    )
}

/** Segmented list rows use a raised container colour, so each group looks like a set of rounded cards. */
@Composable
fun segColors() = androidx.compose.material3.ListItemDefaults.segmentedColors(
    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
)

/**
 * Shapes for grouped rows (Android 16 style).
 * - The first and last rows of a group get large outer corners. Inner corners are small.
 * - The selected, focused and hovered states keep these shapes, because switch rows are
 *   "checked" list items.
 * - The pressed state rounds every corner (Material's press morph).
 */
@Composable
fun segShapes(index: Int, count: Int): androidx.compose.material3.ListItemShapes {
    val outer = 24.dp
    val inner = 6.dp
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
    return androidx.compose.material3.ListItemDefaults.shapes(
        shape = shape,
        selectedShape = shape,
        focusedShape = shape,
        hoveredShape = shape,
        pressedShape = androidx.compose.foundation.shape.RoundedCornerShape(outer),
    )
}
