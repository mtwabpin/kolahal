/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetJson
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetShape
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec

/*
 * The Shape look's choice of shape ([WidgetShape]). A card that is one shape, or whose main value
 * sits in one, can take another of Material's shapes: chosen in the Shape row of the Arrange
 * widgets toolbar ([ShapeChoiceRow]), or named by its script (`shape`). One rule says which cards
 * have the choice ([hasShapeChoice]); the row and the drawing both follow it.
 */

/** The shape chosen for the placed widget being drawn ([PlacedWidget.shape]); null for its own. [SourceWidget] provides it. */
val LocalChosenShape = staticCompositionLocalOf<WidgetShape?> { null }

/**
 * The shape the card being drawn takes in place of its own in the Shape look: the one chosen for
 * it, else the one its script names. Null where the card has no choice ([hasShapeChoice]) or keeps
 * its own: each part then draws the shape it always has. [WidgetCard] provides it.
 */
internal val LocalCardShape = staticCompositionLocalOf<WidgetShape?> { null }

/**
 * True for the cards of the Widgets screen, drawn there to be arranged. A shape chosen there
 * settles as it does on the widgets page ([settleSpec]).
 */
internal val LocalArranging = staticCompositionLocalOf { false }

/** The kinds whose Shape look draws one shape for the card or for its main value. */
private val WITH_CHOICE = setOf(
    WidgetKind.STAT,
    WidgetKind.TEXT,
    WidgetKind.PROGRESS,
    WidgetKind.CHART,
    WidgetKind.CLOCK,
    WidgetKind.IMAGE,
)

/**
 * Whether a card has a choice of shape in the Shape look: whether the look draws one shape for the
 * card or for its main value. [spec] is what the card shows, when it is known, else [kind] decides.
 * - A stat, a text, a clock and a picture have the choice; so has a chart, but not a habit's week,
 *   whose flower carries the days ([ShapeSteps.weekFlower]); and a progress card with one value,
 *   a gauge, stages or rings, but not bars, where each bar takes a small shape of its own.
 * - No choice where the shape carries the value: a countdown's sun, the sky's sun and moon, and
 *   the battery's shapes by its level ([battery], the built-in Battery and network card).
 * - No choice where each part takes a shape of its own: buttons, switches, media controls, a list's
 *   rows, an agenda's days. A versus card has no Shape look.
 */
internal fun hasShapeChoice(kind: WidgetKind?, spec: WidgetSpec?, battery: Boolean = false): Boolean =
    when {
        battery -> false
        spec == null -> kind in WITH_CHOICE
        spec is WidgetSpec.Chart -> !ShapeSteps.weekFlower(spec.style, spec.points)
        spec is WidgetSpec.Progress -> spec.items.isEmpty() || ProgressStyles.isRings(spec)
        else -> WidgetJson.kindOf(spec) in WITH_CHOICE
    }

/**
 * The shape a card has of its own, as a square card draws it ([cardPolygon]): the one its script
 * names, else its kind's. A 12-sided cookie for a number, a chart, a picture and rings; a 4-leaf
 * clover for a level and a text's mark; a 9-sided cookie for a clock's dial.
 */
internal fun ownShape(kind: WidgetKind?, spec: WidgetSpec?): WidgetShape {
    spec?.base?.shape?.let { return it }
    if (spec is WidgetSpec.Progress && ProgressStyles.isRings(spec)) return WidgetShape.COOKIE12
    return when (spec?.let(WidgetJson::kindOf) ?: kind) {
        WidgetKind.PROGRESS, WidgetKind.TEXT -> WidgetShape.CLOVER4
        WidgetKind.CLOCK -> WidgetShape.COOKIE9
        else -> WidgetShape.COOKIE12
    }
}

/**
 * The Shape row of a card: its [own] shape and the one [chosen] for it (null: its own). The row
 * offers [own] first, then the others ([WidgetShape.choices]).
 */
internal data class ShapeChoices(val own: WidgetShape, val chosen: WidgetShape?) {
    val shapes: List<WidgetShape> get() = WidgetShape.choices(own)

    /** The shape the card has now. */
    val shown: WidgetShape get() = chosen ?: own

    /** What a tap on [shape] keeps for the card: null for its own, so it follows its own if that changes. */
    fun pick(shape: WidgetShape): WidgetShape? = shape.takeIf { it != own }
}

/**
 * The Shape row of the placed widget [w], of [kind], showing [spec] (null when not known yet). Null
 * when it has no choice ([hasShapeChoice]), and for an app's widget, which draws itself.
 */
internal fun shapeChoicesFor(w: PlacedWidget, kind: WidgetKind?, spec: WidgetSpec?): ShapeChoices? {
    val source = w.source ?: return null
    val battery = WidgetSource.builtinId(source) == WidgetSource.BATTERY
    if (!hasShapeChoice(kind, spec, battery)) return null
    return ShapeChoices(ownShape(kind, spec), w.shape)
}

/**
 * How much more room the words in [shape] keep from its edge than in the other shapes, as a share
 * of its side: a burst's points cut deeper, so a long number would touch its edge.
 */
internal fun extraInset(shape: WidgetShape?): Float = if (shape == WidgetShape.BURST) 0.04f else 0f

/** Material's shape for [shape]. The burst is Material's soft burst. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun polygonOf(shape: WidgetShape): RoundedPolygon = when (shape) {
    WidgetShape.COOKIE9 -> MaterialShapes.Cookie9Sided
    WidgetShape.COOKIE12 -> MaterialShapes.Cookie12Sided
    WidgetShape.CLOVER4 -> MaterialShapes.Clover4Leaf
    WidgetShape.CLOVER8 -> MaterialShapes.Clover8Leaf
    WidgetShape.SUN -> MaterialShapes.Sunny
    WidgetShape.BURST -> MaterialShapes.SoftBurst
}

/** [own], or the shape the card being drawn takes in its place ([LocalCardShape]). */
@Composable
internal fun cardShape(own: RoundedPolygon): RoundedPolygon =
    LocalCardShape.current?.let(::polygonOf) ?: own

/** The [extraInset] of the shape the card being drawn takes: 0 for its own. */
@Composable
internal fun cardInset(): Float = extraInset(LocalCardShape.current)

/**
 * The Shape row of the Arrange widgets toolbar: [choices] as one row of connected buttons, each a
 * small filled shape, the card's own first. A tap gives the card that shape at once ([onPick]: null
 * for its own). A screen reader names each shape, the first as the default.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ShapeChoiceRow(choices: ShapeChoices, onPick: (WidgetShape?) -> Unit) {
    val shapes = choices.shapes
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth()
    ) {
        shapes.forEachIndexed { i, shape ->
            val name = if (i == 0) "${shape.label}, default" else shape.label
            ToggleButton(
                checked = shape == choices.shown,
                onCheckedChange = { onPick(choices.pick(shape)) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    shapes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics {
                        role = Role.RadioButton
                        contentDescription = name
                    }
                    .testTag("shape " + shape.id),
            ) {
                // Material's toShape remembers the shape it makes.
                val drawn = polygonOf(shape).toShape()
                Box(
                    Modifier
                        .size(22.dp)
                        .background(LocalContentColor.current, drawn)
                )
            }
        }
    }
}
