/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetColors
import com.mtwabpin.kolahal.widgets.WidgetSpec

/*
 * A card's color ([WidgetColors]), chosen in the Color row of the Arrange widgets toolbar
 * ([ColorChoiceRow]). The card then draws with it wherever it uses its own color: [WidgetCard]
 * gives its body the description with that color, and a built-in card's look of its own reads it
 * from [LocalCardColor].
 */

/** The color chosen for the placed widget being drawn ([PlacedWidget.color]); null for its own. [SourceWidget] provides it. */
val LocalChosenColor = staticCompositionLocalOf<ColorRole?> { null }

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

/**
 * The Color row of a card: its [own] role (null: the accent) and the one [chosen] for it (null: its
 * own). The row offers its own first, then the others ([WidgetColors.choices]).
 */
internal data class ColorChoices(val own: ColorRole?, val chosen: ColorRole?) {
    val roles: List<ColorRole> get() = WidgetColors.choices(own)

    /** The role the row shows as chosen. */
    val shown: ColorRole get() = chosen ?: own ?: ColorRole.ACCENT

    /** What a tap on [role] keeps for the card: null for its own, so it follows its own if that changes. */
    fun pick(role: ColorRole): ColorRole? = role.takeIf { it != (own ?: ColorRole.ACCENT) }
}

/**
 * The Color row of the placed widget [w], showing [spec] (null when not known yet): its own color
 * is [spec]'s. Null for an app's widget, which draws itself ([WidgetColors.offeredFor]).
 */
internal fun colorChoicesFor(w: PlacedWidget, spec: WidgetSpec?): ColorChoices? =
    if (WidgetColors.offeredFor(w.source)) ColorChoices(spec?.base?.color, w.color) else null

/** The least contrast a dot keeps with its chip, as WCAG asks of a mark that is no text. Under it, the dot gets an outline. */
internal const val DOT_CONTRAST = 3f

/** Whether a dot in [dot] on a chip in [chip] needs an outline to be seen ([DOT_CONTRAST]). */
internal fun dotNeedsOutline(dot: Color, chip: Color): Boolean = inkContrast(dot, chip) < DOT_CONTRAST

/**
 * The Color row of the Arrange widgets toolbar: [choices] as one row of connected buttons, each a
 * dot in its role's color as the theme has it now. The first is the card's own: a ring, open in the
 * middle, where the others are full dots. A tap gives the card that color at once ([onPick]: null
 * for its own).
 * - The chosen chip is in the theme's primary color, which can be the dot's own: its dot has a ring
 *   in the chip's content color around it.
 * - On another chip, a dot too close to the chip's color has a thin outline in the theme's outline
 *   color ([dotNeedsOutline]).
 * A screen reader names each color, the first as the default.
 */
@Composable
internal fun ColorChoiceRow(choices: ColorChoices, onPick: (ColorRole?) -> Unit) {
    val roles = choices.roles
    val scheme = MaterialTheme.colorScheme
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth()
    ) {
        roles.forEachIndexed { i, role ->
            val own = i == 0
            val checked = role == choices.shown
            val name = WidgetColors.label(role).let { if (own) "$it, default" else it }
            ToggleButton(
                checked = checked,
                onCheckedChange = { onPick(choices.pick(role)) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    roles.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics {
                        this.role = Role.RadioButton
                        contentDescription = name
                    }
                    .testTag("color " + role.role),
            ) {
                val dot = colorOf(role)
                val ring = LocalContentColor.current
                val outline = !checked && dotNeedsOutline(dot, scheme.surfaceContainer)
                val edge = scheme.outline
                Canvas(Modifier.size(24.dp)) {
                    val full = 9.dp.toPx()
                    if (own) {
                        // The card's own: a ring, open in the middle.
                        val width = 4.dp.toPx()
                        drawCircle(dot, full - width / 2, style = Stroke(width))
                    } else {
                        drawCircle(dot, full)
                    }
                    if (checked) {
                        val width = 2.dp.toPx()
                        drawCircle(ring, full + 1.dp.toPx() + width / 2, style = Stroke(width))
                    } else if (outline) {
                        val width = 1.dp.toPx()
                        drawCircle(edge, full + width / 2, style = Stroke(width))
                    }
                }
            }
        }
    }
}
