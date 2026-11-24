/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.ui.home.rememberNow
import com.mtwabpin.kolahal.ui.home.rememberToday
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/*
 * The Shape look's shapes that change with what they show, in a card that is one shape:
 * - A countdown is a sun that loses a ray as each step of the wait goes ([CountdownSun]).
 * - A habit's week is a flower with a leaf for each day ([HabitFlower]).
 * - The battery goes from round when full to spiky when low ([batteryPolygon]).
 * Each morphs into its next shape and settles ([rememberSettle]). The steps are in [ShapeSteps].
 */

/**
 * The phone's battery, inside the built-in Battery and network card ([BatteryCards]). Null in every
 * other card: a script's progress card keeps its clover.
 */
internal val LocalBattery = compositionLocalOf<Status?> { null }

/**
 * Whether the card that is one shape leaves its shape to [spec]'s body, because the shape changes
 * with what the card shows: a countdown ([CountdownSun]) and a week of a habit ([HabitFlower]).
 * The frame then draws no shape and cuts nothing.
 */
internal fun carriesShape(spec: WidgetSpec): Boolean = when (spec) {
    is WidgetSpec.Countdown -> true
    is WidgetSpec.Chart -> ShapeSteps.weekFlower(spec.style, spec.points)
    else -> false
}

// ---- the shapes ------------------------------------------------------------------------------

/** A little rounding at each corner of a leaf, so its wave is smooth at any size. */
private val SMOOTH = CornerRounding(0.02f)

private val SUNS = arrayOfNulls<RoundedPolygon>(ShapeSteps.MAX_RAYS + 1)

/** A very little rounding at each of the sun's many corners: its waves are smooth, and a ray's tip stays pointed. */
private val RAY_SMOOTH = CornerRounding(0.005f)

/**
 * The countdown's sun with [rays] rays, in a box of 1 by 1, its middle at the middle: twelve places
 * round a round body, the first [rays] clockwise from the top with a ray ([ShapeSteps.sunCorners]).
 * Every count has the same corners, so a morph sinks one ray and moves nothing else. Made once
 * for each count.
 */
internal fun sunPolygon(rays: Int): RoundedPolygon {
    val n = rays.coerceIn(0, ShapeSteps.MAX_RAYS)
    return SUNS[n] ?: RoundedPolygon(
        ShapeSteps.sunCorners(n),
        rounding = RAY_SMOOTH,
        centerX = 0.5f,
        centerY = 0.5f
    ).also { SUNS[n] = it }
}

private val FLOWERS = HashMap<List<Boolean>, RoundedPolygon>()

/**
 * The week's flower with a leaf out for each day of [leaves] that is true, in a box of 1 by 1. A
 * leaf is a round petal, and where two meet the corner is sharp, so each stands on its own. Made
 * once for each week.
 */
internal fun flowerPolygon(leaves: List<Boolean>): RoundedPolygon =
    FLOWERS.getOrPut(leaves) {
        lobePolygon(
            leaves,
            ShapeSteps.FLOWER_BODY,
            ShapeSteps.FLOWER_SHARPNESS,
            ShapeSteps.FLOWER_STEPS,
            CornerRounding.Unrounded
        )
    }

/**
 * A shape with a point out for each of [out] ([ShapeSteps.lobeCorners]): each corner a little
 * rounded, and [meet], the one where two points meet, as rounded as it says.
 */
private fun lobePolygon(
    out: List<Boolean>,
    body: Float,
    sharpness: Double,
    steps: Int,
    meet: CornerRounding
): RoundedPolygon = RoundedPolygon(
    ShapeSteps.lobeCorners(out, body, sharpness, steps),
    perVertexRounding = out.flatMap { List(steps) { s -> if (s == 0) meet else SMOOTH } },
    centerX = 0.5f,
    centerY = 0.5f
)

/** The battery's Material shape for [form]: a circle, two cookies, a soft burst and a burst. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
internal fun batteryShape(form: ShapeSteps.BatteryForm): RoundedPolygon = when (form) {
    ShapeSteps.BatteryForm.ROUND -> MaterialShapes.Circle
    ShapeSteps.BatteryForm.FINE_SCALLOPS -> MaterialShapes.Cookie12Sided
    ShapeSteps.BatteryForm.SCALLOPS -> MaterialShapes.Cookie9Sided
    ShapeSteps.BatteryForm.SOFT_SPIKES -> MaterialShapes.SoftBurst
    ShapeSteps.BatteryForm.SPIKES -> MaterialShapes.Burst
}

/**
 * The battery card's shape in the Shape look, by its level ([ShapeSteps.batteryForm]), when [spec]
 * is that card's ([LocalBattery]). Null for any other card, and while the phone is charging: the
 * card keeps its clover then.
 */
@Composable
internal fun batteryPolygon(spec: WidgetSpec): RoundedPolygon? {
    val battery = LocalBattery.current ?: return null
    if (spec !is WidgetSpec.Progress) return null
    val form = ShapeSteps.batteryForm(battery.batteryPct?.coerceIn(0, 100), battery.charging)
    return form?.let(::batteryShape)
}

// ---- reading on a fill -------------------------------------------------------------------------

/** The contrast of two colors, from 1 to 21, as WCAG counts it. */
internal fun inkContrast(a: Color, b: Color): Float {
    val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
    return (hi + 0.05f) / (lo + 0.05f)
}

/** The least contrast of a big number with what is behind it: WCAG's for large text. */
internal const val INK_MIN = 3f

/**
 * The one of [first] and [second] that reads better on [background]: [first] unless [second] has
 * more contrast with it. A number drawn across a level's line is in this on each side of it.
 * Where neither reaches [INK_MIN] (a fill of middle lightness), the better one is taken toward
 * black or white, whichever is further from [background], just far enough to reach it.
 */
internal fun inkOn(background: Color, first: Color, second: Color): Color {
    val best = if (inkContrast(second, background) > inkContrast(first, background)) second else first
    if (inkContrast(best, background) >= INK_MIN) return best
    val toward =
        if (inkContrast(Color.Black, background) >= inkContrast(Color.White, background)) Color.Black else Color.White
    for (step in 1..10) {
        val ink = lerp(best, toward, step / 10f)
        if (inkContrast(ink, background) >= INK_MIN) return ink
    }
    return toward
}

// ---- a countdown's sun -------------------------------------------------------------------------

/**
 * A countdown's sun in the Shape look's square card, in [fill]: a ray for each step of the wait
 * that is left ([ShapeSteps.rays]). When a ray goes, the sun morphs and settles. It is worked out
 * at each minute's tick, with the header's clock: a ray stands for five minutes at the least.
 */
@Composable
internal fun CountdownSun(spec: WidgetSpec.Countdown, fill: Color, modifier: Modifier = Modifier) {
    val (minute, _) = rememberNow()
    val rays = remember(minute, spec.to, spec.from) {
        ShapeSteps.rays(spec.to, spec.from, System.currentTimeMillis() / 1000)
    }
    val settle = rememberSettle(sunPolygon(rays))
    Box(modifier.settledShape(settle, fill, clip = false))
}

// ---- a habit's week as a flower ----------------------------------------------------------------

/** From this width the flower has the days' letters on its leaves and a line under the count. */
private val FLOWER_WORDS = 120.dp

/**
 * A habit's week in the Shape look's square card ([ShapeSteps.weekFlower]): a flower with a leaf
 * for each day of the week, from the first day where the phone is, clockwise from the top.
 * - A day done is a leaf in the role's container; a day missed or still to come is a faint ghost
 *   of one. The whole flower is the week done.
 * - In the middle, the days done this week, with the words under it ([ShapeSteps.weekWords]):
 *   "this week" (on two lines where one is too narrow), "all week" for all seven, and "of 7" in
 *   a small card. A large card has the days' first letters on the leaves, bold for a day done.
 * - The last point is the day the content was set, as in [Dots]: a card that hasn't run since
 *   midnight keeps its days under their own weekdays.
 * - When a day is done, the flower morphs and settles ([rememberSettle]).
 * The card's description says the days for a screen reader; the letters say nothing.
 */
@Composable
internal fun HabitFlower(spec: WidgetSpec.Chart, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, color)
    val today = rememberToday()
    val setAt = LocalContentSetAt.current
    val last = remember(setAt, today) {
        if (setAt > 0) minOf(
            java.time.Instant.ofEpochMilli(setAt).atZone(java.time.ZoneId.systemDefault())
                .toLocalDate(), today
        ) else today
    }
    val locale = LocalLocale.current.platformLocale
    val first = remember(locale) { DotCalendar.firstDay(locale) }
    val column = DotCalendar.column(last.dayOfWeek, first)
    val leaves = remember(spec.points, column) { ShapeSteps.weekLeaves(spec.points, column) }
    val done = leaves.count { it }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // Right to left, the days go round the other way: the leaves in a mirror.
    val drawn = if (rtl) List(DotCalendar.DAYS) { leaves[(DotCalendar.DAYS - it) % DotCalendar.DAYS] } else leaves
    val whole = remember { flowerPolygon(List(DotCalendar.DAYS) { true }) }
    val ghostShape = rememberSettle(whole)
    val shape = rememberSettle(flowerPolygon(drawn))
    // A faint leaf: the container over the page's surface, so the letters on it keep their contrast.
    val ghost = lerp(MaterialTheme.colorScheme.surface, c.shapeFill, 0.4f)
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .settledShape(ghostShape, ghost, clip = false)
            .settledShape(shape, c.shapeFill, clip = false)
    ) {
        val side = maxWidth
        val words = box != WidgetSizes.Box.SMALL && side >= FLOWER_WORDS
        if (words) {
            val names = remember(first, locale) { DotCalendar.initials(first, locale) }
            val letters = type.labelSmall
            names.forEachIndexed { day, name ->
                // On the leaf, halfway out from the middle.
                val turn = 2 * PI * day / DotCalendar.DAYS
                val reach = side * (ShapeSteps.FLOWER_BODY + ShapeSteps.OUTER) / 2f
                val x = reach * sin(turn).toFloat()
                Text(
                    name,
                    style = if (leaves[day]) letters.copy(fontWeight = FontWeight.Bold) else letters,
                    color = if (leaves[day]) c.content else c.muted,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .align(Alignment.Center)
                        // Right to left, offset mirrors x, as the leaves are.
                        .offset(x = x, y = -reach * cos(turn).toFloat())
                        .clearAndSetSemantics {},
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = side * 0.2f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BigText(
                done.toString(),
                if (words) type.displayMedium.copy(fontSize = (side.value * 0.24f).sp) else type.titleLarge
            )
            // "this week" may take two lines; a small flower has room for "of 7" only.
            Text(
                ShapeSteps.weekWords(done, room = words),
                style = (if (words) type.labelMedium else type.labelSmall).copy(fontWeight = FontWeight.Bold),
                color = c.content,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
