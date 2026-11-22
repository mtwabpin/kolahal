/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.ui.theme.Term

/**
 * The grid as the terminal's settings make it: its cells ([metrics]) in the terminal font, size
 * and line height (`font.family`, `font.size`, `log.line-height`), the theme's colors
 * ([palette]), and the [painter] that paints with both.
 */
@Stable
class TtyGrid(val metrics: GridMetrics, val palette: TtyPalette) {
    val painter = GridPainter(metrics, palette)
}

/**
 * The [TtyGrid] for the terminal's font, size, line height and theme. It is made again when one of
 * them changes: a pinch that changes the font size makes a new grid, and the log lays out again.
 */
@Composable
fun rememberTtyGrid(): TtyGrid {
    val density = LocalDensity.current
    val palette = remember(Term.palette) { TtyPalette(Term.palette) }
    val resolver = LocalFontFamilyResolver.current
    val family = Term.fontFamily
    val textPx = with(density) { Term.fontSize.sp.toPx() }
    val linePx = with(density) { Term.lineHeight.sp.roundToPx() }
    val metrics = remember(resolver, family, textPx, linePx) {
        val regular = resolver.resolve(family, FontWeight.Normal).value as? Typeface ?: Typeface.MONOSPACE
        val bold = resolver.resolve(family, FontWeight.Bold).value as? Typeface
            ?: Typeface.create(regular, Typeface.BOLD)
        GridMetrics(regular, bold, textPx, linePx)
    }
    return remember(metrics, palette) { TtyGrid(metrics, palette) }
}
