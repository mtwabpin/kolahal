/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.graphics.shapes.RoundedPolygon
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetJson
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetShape
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec

/*
 * The Shape look's choice of shape ([WidgetShape]). A card that is one shape, or whose main value
 * sits in one, can take another of Material's shapes: chosen in the Shape group of its settings page
 * ([WidgetSettingsScreen]), or named by its script (`shape`). One rule says which cards have the
 * choice ([hasShapeChoice]) and another which shapes ([shapesFor]); the page and the drawing both
 * follow them.
 */

/** The shape chosen for the placed widget being drawn ([PlacedWidget.shape]); null for its own. [SourceWidget] provides it. */
val LocalChosenShape = staticCompositionLocalOf<WidgetShape?> { null }

/**
 * The shape of the card being drawn in the Shape look ([cardShapeOf]): the one chosen for it, else
 * the one its script names, else its own at its size. Null in the other looks. [WidgetCard]
 * provides it; the bodies draw it ([cardShape]).
 */
internal val LocalCardShape = staticCompositionLocalOf<WidgetShape?> { null }

/**
 * True for the cards drawn to be changed: on the Widgets screen and at the top of a card's settings
 * page. A shape chosen there settles as it does on the widgets page ([settleSpec]).
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
 * The shapes a card of [kind] showing [spec] may take, in their order. A round thing drawn in the
 * shape, a clock's dial, a gauge's dial or rings, sits right only in a shape that is the same turned
 * a half turn ([WidgetShape.symmetric]): those cards leave out the slanted square, the arch, the
 * ghost and the pill. A number, a level, stages, a chart, a text's mark and a picture take all.
 */
internal fun shapesFor(kind: WidgetKind?, spec: WidgetSpec?): List<WidgetShape> {
    val dial = when (spec) {
        null -> kind == WidgetKind.CLOCK
        is WidgetSpec.Clock -> true
        is WidgetSpec.Progress -> ProgressStyles.isGauge(spec) || ProgressStyles.isRings(spec)
        else -> false
    }
    return if (dial) WidgetShape.entries.filter { it.symmetric } else WidgetShape.entries
}

/**
 * The shape a card of [kind] showing [spec] has of its own in the Shape look, in a box [widthDp] by
 * [heightDp]. It is the only answer: the card frame draws it ([cardShapeOf]), and the settings
 * page's first shape and the toolbar's Settings button show it ([shapeChoicesFor]).
 * - The one its script names, where the card has a choice ([hasShapeChoice]) and may take it
 *   ([shapesFor]); else its kind's.
 * - A 12-sided cookie for a number, a chart, rings and a picture, but a 4-leaf clover for a picture
 *   in a 1-row card ([WidgetSizes.Box.WIDE]).
 * - A 4-leaf clover for a level, a gauge and a text's mark; for stages too in a square box, where
 *   the card is the shape, and a 9-sided cookie beside the words in any other box.
 * - A 9-sided cookie for a clock's dial.
 * - A countdown's and the sky's sun, and a circle for the moon: shapes that carry what they show,
 *   with no choice.
 */
internal fun ownShape(kind: WidgetKind?, spec: WidgetSpec?, widthDp: Int, heightDp: Int): WidgetShape {
    if (hasShapeChoice(kind, spec)) spec?.base?.shape?.takeIf { it in shapesFor(kind, spec) }?.let { return it }
    return when {
        spec is WidgetSpec.Progress && ProgressStyles.isRings(spec) -> WidgetShape.COOKIE12
        spec is WidgetSpec.Progress && ProgressStyles.isStages(spec) ->
            if (squareBox(widthDp, heightDp)) WidgetShape.CLOVER4 else WidgetShape.COOKIE9
        spec is WidgetSpec.Countdown -> WidgetShape.SUN
        spec is WidgetSpec.Sky -> if (spec.phase == null) WidgetShape.SUN else WidgetShape.CIRCLE
        else -> when (spec?.let(WidgetJson::kindOf) ?: kind) {
            WidgetKind.PROGRESS, WidgetKind.TEXT -> WidgetShape.CLOVER4
            WidgetKind.CLOCK -> WidgetShape.COOKIE9
            WidgetKind.IMAGE ->
                if (WidgetSizes.box(widthDp, heightDp) == WidgetSizes.Box.WIDE) WidgetShape.CLOVER4
                else WidgetShape.COOKIE12
            else -> WidgetShape.COOKIE12
        }
    }
}

/**
 * The shape the card showing [spec] takes in place of its own: the one [chosen] for it, else the one
 * its script names, where it has a choice ([hasShapeChoice]) and may take that shape ([shapesFor]).
 * Null: it keeps its own.
 */
internal fun takenShape(spec: WidgetSpec?, chosen: WidgetShape?, battery: Boolean): WidgetShape? {
    if (!hasShapeChoice(null, spec, battery)) return null
    return (chosen ?: spec?.base?.shape)?.takeIf { it in shapesFor(null, spec) }
}

/**
 * The shape the card frame ([WidgetCard]) gives the card showing [spec] in [look], in a box [widthDp]
 * by [heightDp]: in the Shape look, the one [chosen] for it or named by its script ([takenShape]),
 * else its own ([ownShape]). Null in every other look. The frame draws it where the card is the
 * shape ([cardPolygon]); everywhere else the body draws it ([cardShape]). [battery]: the built-in
 * Battery and network card, whose shape shows its level.
 */
internal fun cardShapeOf(
    look: WidgetLook,
    spec: WidgetSpec?,
    chosen: WidgetShape?,
    battery: Boolean,
    widthDp: Int,
    heightDp: Int
): WidgetShape? {
    if (look != WidgetLook.SHAPE) return null
    return takenShape(spec, chosen, battery) ?: ownShape(null, spec, widthDp, heightDp)
}

/**
 * The Shape group of a card: its [own] shape, the one [chosen] for it (null: its own), and the
 * shapes it may take ([offered]). The group offers its own first, then the others
 * ([WidgetShape.choices]).
 */
internal data class ShapeChoices(
    val own: WidgetShape,
    val chosen: WidgetShape?,
    val offered: List<WidgetShape> = WidgetShape.entries
) {
    val shapes: List<WidgetShape> get() = WidgetShape.choices(own, offered)

    /** The shape the card has now. */
    val shown: WidgetShape get() = chosen?.takeIf { it in offered } ?: own

    /** What a tap on [shape] keeps for the card: null for its own, so it follows its own if that changes. */
    fun pick(shape: WidgetShape): WidgetShape? = shape.takeIf { it != own }
}

/**
 * The Shape group of the placed widget [w], of [kind], showing [spec] (null when not known yet), on
 * a page [pageDp] wide: its own shape is the one it has at its size ([ownShape]).
 * Null when it has no choice ([hasShapeChoice]), and for an app's widget, which draws itself.
 */
internal fun shapeChoicesFor(w: PlacedWidget, kind: WidgetKind?, spec: WidgetSpec?, pageDp: Int): ShapeChoices? {
    val source = w.source ?: return null
    val battery = WidgetSource.builtinId(source) == WidgetSource.BATTERY
    if (!hasShapeChoice(kind, spec, battery)) return null
    val own = ownShape(kind, spec, WidgetLayout.widthDp(pageDp, w.span), w.heightDp)
    return ShapeChoices(own, w.shape, shapesFor(kind, spec))
}

/**
 * How much more room the words in [shape] keep from its edge than in the other shapes, as a share
 * of its side: a burst's points and a pill's narrow ends come closest to a long number.
 */
internal fun extraInset(shape: WidgetShape?): Float =
    if (shape == WidgetShape.BURST || shape == WidgetShape.PILL) 0.04f else 0f

/** Material's shape for [shape]. The burst is Material's soft burst, the ghost its "ghostish". */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun polygonOf(shape: WidgetShape): RoundedPolygon = when (shape) {
    WidgetShape.COOKIE9 -> MaterialShapes.Cookie9Sided
    WidgetShape.COOKIE12 -> MaterialShapes.Cookie12Sided
    WidgetShape.CLOVER4 -> MaterialShapes.Clover4Leaf
    WidgetShape.CLOVER8 -> MaterialShapes.Clover8Leaf
    WidgetShape.SUN -> MaterialShapes.Sunny
    WidgetShape.BURST -> MaterialShapes.SoftBurst
    WidgetShape.CIRCLE -> MaterialShapes.Circle
    WidgetShape.COOKIE4 -> MaterialShapes.Cookie4Sided
    WidgetShape.COOKIE6 -> MaterialShapes.Cookie6Sided
    WidgetShape.GEM -> MaterialShapes.Gem
    WidgetShape.PIXEL -> MaterialShapes.PixelCircle
    WidgetShape.SLANTED -> MaterialShapes.Slanted
    WidgetShape.ARCH -> MaterialShapes.Arch
    WidgetShape.GHOST -> MaterialShapes.Ghostish
    WidgetShape.PILL -> MaterialShapes.Pill
}

/**
 * The shape of the card being drawn in the Shape look, as the card frame resolved it
 * ([LocalCardShape]): the one chosen for it, else its own at its size. A body draws this, never a
 * shape of its own, so the card, its settings page and the toolbar show the same shape.
 */
@Composable
internal fun cardShape(): RoundedPolygon = polygonOf(LocalCardShape.current ?: WidgetShape.COOKIE12)

/** The [extraInset] of the shape the card being drawn takes: 0 for its own. */
@Composable
internal fun cardInset(): Float = extraInset(LocalCardShape.current)
