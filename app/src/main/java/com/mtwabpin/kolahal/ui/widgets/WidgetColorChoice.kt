/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
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
