/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetColors
import com.mtwabpin.kolahal.widgets.WidgetParts

/*
 * A card's color ([WidgetColors]), chosen in the Color group of its settings page
 * ([WidgetSettingsScreen]). The card then draws with it wherever it uses its own color:
 * [WidgetCard] gives its body the description with that color, and a built-in card's look of its
 * own reads it from [LocalCardColor].
 */

/** The color chosen for the placed widget being drawn ([PlacedWidget.color]); null for its own. [SourceWidget] provides it. */
val LocalChosenColor = staticCompositionLocalOf<ColorRole?> { null }

/** The parts of the placed widget being drawn that it hides ([PlacedWidget.hidden], [WidgetParts]). [SourceWidget] provides them. */
val LocalHiddenParts = staticCompositionLocalOf<Set<String>> { emptySet() }

/**
 * The color role the card being drawn uses card-wide, as it is drawn: the chosen one, else its
 * own ([WidgetColors.drawn]). Null for the accent. [WidgetCard] provides it.
 */
internal val LocalCardColor = staticCompositionLocalOf<ColorRole?> { null }

/**
 * The card's color in this theme, for a body that is not given the description: the role of the
 * card being drawn ([LocalCardColor]), else [base]'s, else the accent.
 */
@Composable
internal fun cardRoleColor(base: WidgetBase? = null): Color =
    colorOf(LocalCardColor.current ?: base?.color ?: ColorRole.ACCENT)

/** The least contrast a dot keeps with what it is on, as WCAG asks of a mark that is no text. Under it, the dot gets an outline. */
internal const val DOT_CONTRAST = 3f

/** Whether a dot in [dot] on [ground] needs an outline to be seen ([DOT_CONTRAST]). */
internal fun dotNeedsOutline(dot: Color, ground: Color): Boolean = inkContrast(dot, ground) < DOT_CONTRAST

/**
 * How a cell of the Color and Shape grids of a card's settings page is marked, one way in both: a
 * chosen cell has a [fill] of the primary container, with its mark and its name in [ink], the color
 * on that container (the ring around the chosen color, the chosen shape). A cell not chosen has no
 * fill, and its name and its shape in the muted text color. Each reads at 3:1 ([DOT_CONTRAST]) on
 * its ground in every built-in theme.
 */
@Immutable
internal data class CellMarks(val fill: Color, val ink: Color)

/**
 * The least width of a cell of the Color and Shape grids at the usual text size, in dp: its longest
 * word ("Slanted", "Warning") in its small type, and the cell's own room. It grows with the font.
 */
internal const val GRID_CELL_DP = 48

/** The room between two cells of the grids, in dp. */
internal const val GRID_GAP_DP = 4

/**
 * How many cells of the Color or Shape grid go in a row [widthDp] wide at the font size
 * [fontScale]: [most] at the usual size on a phone, fewer with a larger font, so a cell is at
 * least [GRID_CELL_DP] times [fontScale] wide and its words are not cut. At least one.
 */
internal fun gridColumns(widthDp: Int, fontScale: Float, most: Int): Int {
    val cell = GRID_CELL_DP * maxOf(1f, fontScale) + GRID_GAP_DP
    return ((widthDp + GRID_GAP_DP) / cell).toInt().coerceIn(1, maxOf(1, most))
}

/** The [CellMarks] of a cell of the grids in [scheme], [selected] or not. */
internal fun cellMarks(scheme: ColorScheme, selected: Boolean): CellMarks =
    if (selected) CellMarks(scheme.primaryContainer, scheme.onPrimaryContainer)
    else CellMarks(Color.Transparent, scheme.onSurfaceVariant)

/**
 * A dot of a color, [size] across, on [ground] (what is behind it).
 * - [own]: the card's own color, as a ring open in the middle where the others are full dots.
 * - [ring]: the dot is the chosen one, with a ring of that color around it.
 * - Else, a dot too close to [ground] has a thin outline in the theme's outline color ([dotNeedsOutline]).
 * It says nothing to a screen reader: what holds it names the color.
 */
@Composable
internal fun ColorDot(
    color: Color,
    ground: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    own: Boolean = false,
    ring: Color? = null,
) {
    val edge = MaterialTheme.colorScheme.outline
    val outline = ring == null && dotNeedsOutline(color, ground)
    Canvas(modifier.size(size)) {
        // The dot leaves room for its ring around it.
        val full = this.size.minDimension / 2 * 0.75f
        if (own) {
            val width = full * 0.45f
            drawCircle(color, full - width / 2, style = Stroke(width))
        } else {
            drawCircle(color, full)
        }
        if (ring != null) {
            val width = full * 0.22f
            drawCircle(ring, full + width * 0.5f + full * 0.1f, style = Stroke(width))
        } else if (outline) {
            val width = full * 0.11f
            drawCircle(edge, full + width / 2, style = Stroke(width))
        }
    }
}
