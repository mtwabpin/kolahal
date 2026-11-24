/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/*
 * Two pieces of motion of the widgets' cards:
 * - A shape that settles: a card's shape that changes with what it shows morphs into the new one
 *   and grows a little past its size on the way, once, then comes to rest ([rememberSettle]).
 * - A pressed button gives way: it widens while the buttons beside it narrow, as in Material's
 *   button group, and they spring back on release ([GiveWay]).
 */

// ---- a shape that settles --------------------------------------------------------------------

/**
 * The spring of a shape that settles: the theme's fast spatial spring, which goes about 10% past
 * its end once and comes back with no second swing that shows. While the card can't be seen, the
 * shape is simply there. On the Widgets screen ([LocalArranging]) it settles too: a shape chosen
 * there is seen. With the phone's animations off, Compose ends the spring at once.
 */
@Composable
internal fun settleSpec(): FiniteAnimationSpec<Float> =
    if (cardSeen() || LocalArranging.current) MaterialTheme.motionScheme.fastSpatialSpec() else snap()

/**
 * A shape that morphs into the next one it is given, on [settleSpec]. [morph] goes from the shape
 * before to [target], and [progress] from 0 to 1 along it. The drawing reads both
 * ([settledShape]), so a morph that runs draws again and composes nothing.
 */
@Stable
internal class Settle(start: RoundedPolygon) {
    /** The shape it ends on. */
    var target: RoundedPolygon = start
        private set

    var morph: Morph by mutableStateOf(morphOf(start, start))
        private set

    val progress = Animatable(1f)

    /** Starts the morph from the shape it ends on now to [next]. */
    fun towards(next: RoundedPolygon) {
        morph = morphOf(target, next)
        target = next
    }
}

/** The morphs made so far, one for each pair of shapes: a morph matches the shapes' corners, which takes a moment. */
private val MORPHS = HashMap<Pair<RoundedPolygon, RoundedPolygon>, Morph>()

private fun morphOf(from: RoundedPolygon, to: RoundedPolygon): Morph =
    MORPHS.getOrPut(from to to) { Morph(from, to) }

/**
 * [polygon] as a shape that settles: the first one is drawn as it is, and each new one is morphed
 * into from the one before, on [settleSpec]. A new shape that comes during a morph starts from
 * the shape the morph was going to.
 */
@Composable
internal fun rememberSettle(polygon: RoundedPolygon): Settle {
    val settle = remember { Settle(polygon) }
    val spec by rememberUpdatedState(settleSpec())
    LaunchedEffect(polygon) {
        if (polygon == settle.target) return@LaunchedEffect
        settle.towards(polygon)
        settle.progress.snapTo(0f)
        settle.progress.animateTo(1f, spec)
    }
    return settle
}

/**
 * Fills the space behind with [settle]'s shape in [fill], stretched to the size: a Material shape's
 * box of 1 by 1 is the size, with its middle at the middle. While its spring swings past its end the
 * shape is a little larger ([ShapeSteps.settleScale]), about its middle. With [clip], what is
 * drawn inside is cut to the shape.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun Modifier.settledShape(
    settle: Settle,
    fill: Color,
    clip: Boolean = true
): Modifier = drawWithCache {
    val path = Path()
    val stretch = Matrix()
    var madeAt = Float.NaN
    var madeOf: Morph? = null
    onDrawWithContent {
        val p = settle.progress.value
        val morph = settle.morph
        if (p != madeAt || morph !== madeOf) {
            val scale = ShapeSteps.settleScale(p)
            morph.toPath(ShapeSteps.settleMorph(p), path)
            stretch.reset()
            stretch.scale(size.width * scale, size.height * scale)
            path.transform(stretch)
            path.translate(Offset(size.width * (1f - scale) / 2f, size.height * (1f - scale) / 2f))
            madeAt = p
            madeOf = morph
        }
        drawPath(path, fill)
        if (clip) clipPath(path) { this@onDrawWithContent.drawContent() } else drawContent()
    }
}

// ---- a pressed button gives way --------------------------------------------------------------

/**
 * Which button of a grid is held down, so the buttons beside it in its row give way
 * ([ShapeSteps.giveWay]). -1 while none is.
 */
@Stable
internal class GiveWay {
    var pressed by mutableIntStateOf(-1)
}

/** A button's [index] in the grid whose buttons give way. */
internal class GiveWayPlace(val giveWay: GiveWay, val index: Int)

/** The place of the button drawn here in a grid whose buttons give way. Null elsewhere: nothing gives way there. */
internal val LocalGiveWay = compositionLocalOf<GiveWayPlace?> { null }

/** [content], a button or a switch, as the one at [index] of the grid whose buttons give way by [giveWay]. */
@Composable
internal fun GiveWayCell(giveWay: GiveWay, index: Int, content: @Composable () -> Unit) {
    val place = remember(giveWay, index) { GiveWayPlace(giveWay, index) }
    CompositionLocalProvider(LocalGiveWay provides place, content = content)
}

/** Tells the grid ([LocalGiveWay]) while the button that uses [source] is held down. */
@Composable
internal fun ReportPress(source: InteractionSource) {
    val place = LocalGiveWay.current ?: return
    val pressed by source.collectIsPressedAsState()
    DisposableEffect(place, pressed) {
        if (pressed) place.giveWay.pressed = place.index
        onDispose { if (place.giveWay.pressed == place.index) place.giveWay.pressed = -1 }
    }
}

/**
 * The width, as a weight, of the cell in [column] of a row whose first button is [rowStart] in the
 * grid, with [inRow] buttons in it: wider while its button is held, narrower while the one beside
 * it is. It springs to each width on the theme's fast spatial spring, as Material's button group
 * does, so on release it goes a little under and comes back. An empty cell keeps its width.
 */
@Composable
internal fun giveWayWeight(giveWay: GiveWay, rowStart: Int, inRow: Int, column: Int): Float {
    val target = if (column < inRow) {
        ShapeSteps.giveWay(inRow, giveWay.pressed - rowStart)[column]
    } else {
        1f
    }
    val weight by animateFloatAsState(
        target,
        if (cardSeen()) MaterialTheme.motionScheme.fastSpatialSpec() else snap(),
        label = "give way"
    )
    return weight
}
