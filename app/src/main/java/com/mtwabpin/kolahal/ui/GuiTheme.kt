package com.mtwabpin.kolahal.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * Material 3 Expressive theme for the GUI screens (settings, script manager). Colours come from
 * the terminal palette, so the screens match the chosen theme.
 */
@Composable
fun GuiTheme(content: @Composable () -> Unit) {
    val palette = Term.palette
    val scheme = remember(palette) { schemeFrom(palette) }
    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

/** Maps a terminal palette onto a full Material colour scheme (dark). */
fun schemeFrom(p: Palette): ColorScheme {
    fun mix(a: Color, b: Color, t: Float) = lerp(a, b, t)
    return darkColorScheme(
        primary = p.prompt,
        onPrimary = p.bg,
        primaryContainer = mix(p.bg, p.prompt, 0.30f),
        onPrimaryContainer = p.fg,
        secondary = p.info,
        onSecondary = p.bg,
        secondaryContainer = mix(p.bg, p.info, 0.25f),
        onSecondaryContainer = p.fg,
        tertiary = p.work,
        onTertiary = p.bg,
        tertiaryContainer = mix(p.bg, p.work, 0.25f),
        onTertiaryContainer = p.fg,
        error = p.error,
        onError = p.bg,
        errorContainer = mix(p.bg, p.error, 0.25f),
        onErrorContainer = p.fg,
        background = p.bg,
        onBackground = p.fg,
        surface = p.bg,
        onSurface = p.fg,
        surfaceVariant = mix(p.bg, p.fg, 0.12f),
        onSurfaceVariant = p.dim,
        surfaceTint = p.prompt,
        inverseSurface = p.fg,
        inverseOnSurface = p.bg,
        inversePrimary = mix(p.prompt, p.bg, 0.4f),
        outline = p.dim,
        outlineVariant = mix(p.bg, p.dim, 0.45f),
        surfaceBright = mix(p.bg, p.fg, 0.16f),
        surfaceDim = p.bg,
        surfaceContainerLowest = mix(p.bg, Color.Black, 0.4f),
        surfaceContainerLow = mix(p.bg, p.fg, 0.04f),
        surfaceContainer = mix(p.bg, p.fg, 0.07f),
        surfaceContainerHigh = mix(p.bg, p.fg, 0.10f),
        surfaceContainerHighest = mix(p.bg, p.fg, 0.14f),
        scrim = Color.Black,
    )
}

/** Segmented list rows sit on a raised container so each group reads as rounded cards. */
@Composable
fun segColors() = androidx.compose.material3.ListItemDefaults.segmentedColors(
    containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerHigh,
)

/**
 * Grouped rows (Android 16 style): large outer corners on the first/last row of a group, tight
 * inner corners. The selected, focused and hovered states keep them too, since switch rows are
 * "checked" list items; pressed rounds every corner, Material's press morph.
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
