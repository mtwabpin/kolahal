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
import com.mtwabpin.kolahal.widgets.WidgetShape
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
 * The shape the card being drawn takes in place of its own in the Shape look: the one chosen for
 * it, else the one its script names. Null where the card has no choice ([hasShapeChoice]) or keeps
 * its own: each part then draws the shape it always has. [WidgetCard] provides it.
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
 * The shape a card has of its own, as a square card draws it ([cardPolygon]): the one its script
 * names, else its kind's. A 12-sided cookie for a number, a chart, a picture and rings; a 4-leaf
 * clover for a level and a text's mark; a 9-sided cookie for a clock's dial. A script's shape the
 * card may not take ([shapesFor]) counts as none.
 */
internal fun ownShape(kind: WidgetKind?, spec: WidgetSpec?): WidgetShape {
    spec?.base?.shape?.takeIf { it in shapesFor(kind, spec) }?.let { return it }
    if (spec is WidgetSpec.Progress && ProgressStyles.isRings(spec)) return WidgetShape.COOKIE12
    return when (spec?.let(WidgetJson::kindOf) ?: kind) {
        WidgetKind.PROGRESS, WidgetKind.TEXT -> WidgetShape.CLOVER4
        WidgetKind.CLOCK -> WidgetShape.COOKIE9
        else -> WidgetShape.COOKIE12
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
 * The Shape group of the placed widget [w], of [kind], showing [spec] (null when not known yet).
 * Null when it has no choice ([hasShapeChoice]), and for an app's widget, which draws itself.
 */
internal fun shapeChoicesFor(w: PlacedWidget, kind: WidgetKind?, spec: WidgetSpec?): ShapeChoices? {
    val source = w.source ?: return null
    val battery = WidgetSource.builtinId(source) == WidgetSource.BATTERY
    if (!hasShapeChoice(kind, spec, battery)) return null
    return ShapeChoices(ownShape(kind, spec), w.shape, shapesFor(kind, spec))
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

/** [own], or the shape the card being drawn takes in its place ([LocalCardShape]). */
@Composable
internal fun cardShape(own: RoundedPolygon): RoundedPolygon =
    LocalCardShape.current?.let(::polygonOf) ?: own

/** The [extraInset] of the shape the card being drawn takes: 0 for its own. */
@Composable
internal fun cardInset(): Float = extraInset(LocalCardShape.current)
