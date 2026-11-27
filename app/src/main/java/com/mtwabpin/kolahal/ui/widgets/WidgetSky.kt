/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.ui.home.rememberNow
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.cardBadge
import java.time.ZoneId
import java.util.Locale

// ---- sky ------------------------------------------------------------------------------------

/**
 * What a sky card shows at this minute, worked out once for all its looks.
 * - [main] is the big text: the time of the next rise or set ("17:20"), or the moon's lit part ("78%").
 * - [lines] is what comes next, from the longest to the shortest ("Sunset in 2 h 10 min", "Sunset"),
 *   or the moon's phase ("Waxing gibbous"): a card writes the first that fits.
 * - [rises] and [sets] are the times at the arc's ends, and [phases] the moon's next phases with
 *   their days ("28 Nov").
 */
private class SkyNow(
    val place: SkyWords.Place,
    val main: String,
    val lines: List<String>,
    val rises: String,
    val sets: String,
    val phases: List<Pair<WidgetSpec.Phase, String>>,
)

/**
 * `sky`: the sun on its path from its rise to its set, or the moon in its phase on its own path.
 * Kolahal moves it: the script gave the times once, and no run is needed as time passes.
 * - The place moves by the minute, with the header's clock ([rememberMillis]). By day the body is
 *   on the arc over the horizon, the part it has travelled stronger than the part to come. At night
 *   it is under the horizon, on a dotted arc that keeps inside the sky's own room, so it never runs
 *   through the times under it.
 * - Small: the sky over the big text. Wide: the place, the big text and what comes next, with the
 *   sky beside them. Large: the place and what comes next over the sky, and under the horizon the
 *   times at the arc's ends, over their names or the moon's next phases.
 * - The times follow Kolahal's 24-hour setting.
 * - Bold has the big text in the card's color and Bold's type; Split has the sky and the words on
 *   tiles of their own; Shape puts the sky in a Material shape: the sun's or, for the moon, a circle.
 * - The card's badge ([cardBadge]) is on the place's line, beside the place, and goes where the line
 *   can't hold both ([BadgedLine]). A small card, a card that is one shape and a card with no
 *   title have none: it never sits on the sky.
 */
@Composable
internal fun SkyBody(spec: WidgetSpec.Sky, box: WidgetSizes.Box) {
    val (_, zoneChanges) = rememberNow()
    val zone = remember(zoneChanges) { ZoneId.systemDefault() }
    val clock24 = rememberClock24()
    val now = rememberMillis(seconds = false)
    val moon = spec.phase != null
    val sky by remember(spec, zone, clock24) {
        derivedStateOf {
            val at = now.value / 1000
            val locale = Locale.getDefault()
            val place = SkyWords.place(spec.rises, spec.sets, at, moon, zone)
            SkyNow(
                place = place,
                main = SkyWords.main(spec, place, zone, clock24, locale),
                lines = spec.phase?.let { listOf(SkyWords.moonName(it, spec.waxing)) }
                    ?: SkyWords.nextLines(place, at, moon = false),
                rises = SkyWords.clock(place.rises, zone, clock24, locale),
                sets = SkyWords.clock(place.sets, zone, clock24, locale),
                phases = SkyWords.upcoming(spec.phases, at).map { it to SkyWords.phaseDay(it.at, zone, locale) },
            )
        }
    }
    val look = LocalWidgetLook.current
    val role = roleColor(spec.base)
    val title = LocalUntitled.current
    // The badge is on the title's line: none without a title, nor on a small card.
    val badge = spec.cardBadge.takeIf { title != null && box != WidgetSizes.Box.SMALL }
    when {
        look == WidgetLook.SPLIT -> SplitSky(spec, sky, box, role, title, badge)
        look == WidgetLook.SHAPE && LocalCardShaped.current -> ShapedSky(spec, sky, role, title)
        look == WidgetLook.SHAPE -> ShapeBesideSky(spec, sky, role, title, badge)
        else -> PlainSky(spec, sky, box, role, title, bold = look == WidgetLook.BOLD, badge = badge)
    }
}

/**
 * The sky's title ([SkyTitle]) on a line that holds the card's [badge] too ([BadgedLine]): at the
 * line's end, or with [end] false right after the title, where the line's end has what comes next.
 * [modifier] is the line's. Without a badge, the title as it always was.
 */
@Composable
private fun BadgedSkyTitle(
    title: String?,
    color: Color,
    badge: String?,
    role: Color,
    modifier: Modifier = Modifier,
    end: Boolean = true,
) {
    if (badge == null) SkyTitle(title, color, modifier)
    else BadgedLine(badge, role, modifier, end = end) { SkyTitle(title, color) }
}

/** A wide card keeps this much of its width for the sky, at the end. */
private const val SKY_SHARE = 0.46f

/**
 * A wide moon card keeps less for the sky: the phase's name is the one thing it says in words, and
 * the longest ("Waning crescent") needs the room.
 */
private const val MOON_SHARE = 0.36f

/** The type of the times at the arc's ends. */
@Composable
private fun timeStyle(): TextStyle =
    MaterialTheme.typography.titleSmallEmphasized.copy(fontFeatureSettings = "tnum")

/** The place's name, in bold: a sky card's title. */
@Composable
private fun SkyTitle(title: String?, color: Color, modifier: Modifier = Modifier) {
    if (title == null) return
    Text(
        title,
        style = MaterialTheme.typography.labelLargeEmphasized,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * The big text: as a value ([BigText]), or in Bold's type and the card's [role] color, moved just
 * far enough to read on Bold's card ([valueInk]).
 */
@Composable
private fun SkyMain(text: String, style: TextStyle, bold: Boolean, role: Color, boldSize: Float) {
    if (bold) BoldText(text, boldSize.sp, valueInk(role, lookColors(WidgetLook.BOLD, role).card))
    else BigText(text, style)
}

/**
 * Plain and Tonal, and Bold with [bold]: the sky on the card itself.
 * - Small: the sky over the big text.
 * - Wide: the place, the big text and what comes next at the start, the sky at the end. The night
 *   band reaches under the words to the card's edge. In Bold the sky keeps to its own part, with
 *   no band: the horizon would cross the big value.
 * - Large: the place and what comes next (the moon's phase) over the sky, then the times at the
 *   arc's ends and their names, or the moon's next phases. Bold has the big text under the place.
 * - A [badge] is on the place's line: at its end, or right after the place where what comes next
 *   has the line's end (large, Plain and Tonal).
 */
@Composable
private fun PlainSky(
    spec: WidgetSpec.Sky,
    sky: SkyNow,
    box: WidgetSizes.Box,
    role: Color,
    title: String?,
    bold: Boolean,
    badge: String? = null,
) {
    val type = MaterialTheme.typography
    val c = lookColors(LocalWidgetLook.current, role)
    // Tonal's card is the role's container: the sky's ring and band are on that.
    val behind = c.card
    when (box) {
        WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SkyDrawing(sky, spec, role, behind, Modifier.weight(1f).fillMaxWidth(), band = false)
            SkyMain(sky.main, valueStyle(box), bold, role, 28f)
        }

        WidgetSizes.Box.WIDE -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val skyWidth = maxWidth * if (spec.phase != null) MOON_SHARE else SKY_SHARE
            // Plain and Tonal: the band is drawn first, and the words are over it. Bold's big
            // value is as tall as its line, so the horizon would cross it: its sky keeps to its
            // own part of the card, with no band under the words.
            if (!bold) SkyDrawing(sky, spec, role, behind, Modifier.fillMaxSize(), textRoom = maxWidth - skyWidth)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                // The title and what comes next are measured first, so neither is cut: the big
                // value takes the height they leave. Bold's gets smaller to fit it.
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    BadgedSkyTitle(title, c.content, badge, role)
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        SkyMain(sky.main, valueStyle(box), bold, role, 36f)
                    }
                    FirstThatFits(sky.lines, type.bodySmall, c.muted)
                }
                if (bold) SkyDrawing(
                    sky, spec, role, behind,
                    Modifier.width(skyWidth).fillMaxHeight().padding(start = 8.dp),
                    band = false
                ) else Spacer(Modifier.width(skyWidth))
            }
        }

        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            if (bold) {
                BadgedSkyTitle(title, c.content, badge, role)
                SkyMain(sky.main, type.displaySmall, true, role, 52f)
                FirstThatFits(sky.lines, type.bodySmall, c.muted)
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BadgedSkyTitle(title, c.content, badge, role, Modifier.weight(1f, fill = false), end = false)
                    Spacer(Modifier.width(8.dp))
                    FirstThatFits(sky.lines, type.bodySmall, c.muted, end = true)
                }
            }
            SkyDrawing(sky, spec, role, behind, Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp))
            SkyEnds(sky, spec, role, c.content, c.muted, names = !bold)
        }
    }
}

/**
 * Under the horizon of a large card: the time of the rise at the start and of the set at the end,
 * as the arc runs. Under them their names ("Sunrise", "Sunset"), or the moon's next phases when it
 * has some. With [names] false, the times alone. The arc runs from left to right on any page, so
 * these do too.
 */
@Composable
private fun SkyEnds(
    sky: SkyNow,
    spec: WidgetSpec.Sky,
    role: Color,
    content: Color,
    muted: Color,
    names: Boolean,
    modifier: Modifier = Modifier,
) {
    val type = MaterialTheme.typography
    val moon = spec.phase != null
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Column(modifier.fillMaxWidth().padding(top = 4.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Text(sky.rises, style = timeStyle(), color = content, maxLines = 1)
                Spacer(Modifier.weight(1f))
                Text(sky.sets, style = timeStyle(), color = content, maxLines = 1)
            }
            if (!names) return@Column
            if (sky.phases.isNotEmpty()) {
                PhasesRow(sky.phases, role, muted, Modifier.padding(top = 4.dp))
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        SkyWords.eventName(rise = true, moon = moon),
                        style = type.bodySmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        SkyWords.eventName(rise = false, moon = moon),
                        style = type.bodySmall,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/** The moon's next phases in a row, each a small disc in its phase over its day ("28 Nov"). */
@Composable
private fun PhasesRow(
    phases: List<Pair<WidgetSpec.Phase, String>>,
    role: Color,
    muted: Color,
    modifier: Modifier = Modifier
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        phases.forEachIndexed { i, (phase, day) ->
            key(i) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    MoonDisc(phase.phase, phase.waxing, role, Modifier.size(16.dp))
                    Text(day, style = MaterialTheme.typography.labelSmall, color = muted, maxLines = 1)
                }
            }
        }
    }
}

/** A disc of the moon with [phase] lit, [waxing] or not, as large as its box. No item for a screen reader. */
@Composable
private fun MoonDisc(phase: Float, waxing: Boolean, role: Color, modifier: Modifier) {
    Box(modifier.clearAndSetSemantics {}.drawWithCache {
        val path = Path()
        onDrawBehind {
            drawMoon(center, size.minDimension / 2, phase, waxing, role, role.copy(alpha = DARK_ALPHA), path)
        }
    })
}

/**
 * Split: the sky and the words on tiles of their own. Wide: the words on a tile at the start, the
 * sky on one at the end. Large: the place and what comes next over the sky on the top tile, the
 * times and their names on the bottom one. Small: one tile. A [badge] is on the place's line, on
 * the first tile.
 */
@Composable
private fun SplitSky(
    spec: WidgetSpec.Sky,
    sky: SkyNow,
    box: WidgetSizes.Box,
    role: Color,
    title: String?,
    badge: String? = null,
) {
    val type = MaterialTheme.typography
    val moon = spec.phase != null
    val c = lookColors(WidgetLook.SPLIT, role)
    val gap = SplitCorners.GAP
    when (box) {
        WidgetSizes.Box.SMALL -> SplitWhole {
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                SkyDrawing(sky, spec, role, c.tile, Modifier.weight(1f).fillMaxWidth(), band = false)
                BigText(sky.main, valueStyle(box))
            }
        }

        WidgetSizes.Box.WIDE -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            Tile(SplitCorners.inRow(0, 2), c.tile, Modifier.weight(1f).fillMaxHeight()) {
                Column(
                    Modifier.fillMaxSize().padding(horizontal = if (moon) 12.dp else 14.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    BadgedSkyTitle(title, c.content, badge, role)
                    BigText(sky.main, valueStyle(box))
                    FirstThatFits(sky.lines, type.bodySmall, c.muted)
                }
            }
            // The moon's tile is narrower: its words need the room ([MOON_SHARE]).
            Tile(SplitCorners.inRow(1, 2), c.tile, Modifier.weight(if (moon) 0.55f else 0.8f).fillMaxHeight()) {
                SkyDrawing(
                    sky, spec, role, c.tile,
                    Modifier.fillMaxSize().padding(horizontal = if (moon) 4.dp else 8.dp, vertical = 8.dp)
                )
            }
        }

        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            Tile(SplitCorners.inColumn(0, 2), c.tile, Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BadgedSkyTitle(title, c.content, badge, role, Modifier.weight(1f, fill = false), end = false)
                        Spacer(Modifier.width(8.dp))
                        FirstThatFits(sky.lines, type.bodySmall, c.muted, end = true)
                    }
                    SkyDrawing(sky, spec, role, c.tile, Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp))
                }
            }
            Tile(SplitCorners.inColumn(1, 2), c.tile, Modifier.fillMaxWidth()) {
                SkyEnds(
                    sky, spec, role, c.content, c.muted, names = true,
                    modifier =
                    Modifier.padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                )
            }
        }
    }
}

/** The shape a Shape sky is drawn in: the sun's own, or a circle for the moon. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun skyPolygon(spec: WidgetSpec.Sky) =
    if (spec.phase == null) MaterialShapes.Sunny else MaterialShapes.Circle

/**
 * Shape in a square box: the card is the sun's shape, or a circle for the moon ([cardPolygon]).
 * In it, the sky over the big text and what comes next, or the moon's phase.
 */
@Composable
private fun ShapedSky(spec: WidgetSpec.Sky, sky: SkyNow, role: Color, title: String?) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, role)
    ShapeLines {
        SkyDrawing(sky, spec, role, c.shapeFill, Modifier.fillMaxWidth().weight(1f), band = false)
        BigText(sky.main, type.headlineMedium)
        FirstThatFits(sky.lines, type.labelMedium, c.content, center = true)
        if (title != null) Text(
            title,
            style = type.labelSmall,
            color = c.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Shape outside a square box: the sky in the sun's shape (a circle for the moon) at the start, and
 * beside it the place, the big text and what comes next. A [badge] is at the end of the place's line.
 */
@Composable
private fun ShapeBesideSky(spec: WidgetSpec.Sky, sky: SkyNow, role: Color, title: String?, badge: String? = null) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, role)
    BesideShape(skyPolygon(spec), c.shapeFill, inShape = { side ->
        SkyDrawing(sky, spec, role, c.shapeFill, Modifier.fillMaxSize().padding(side / 6), band = false)
    }) {
        BadgedSkyTitle(title, c.content, badge, role)
        // The title and the words are measured first; the big value takes the height they leave.
        Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.CenterStart) {
            BigText(sky.main, type.titleLarge)
        }
        FirstThatFits(sky.lines, type.bodySmall, c.muted)
    }
}

/**
 * The first of [lines] that fits whole on one line in the width it gets, in [style] at the phone's
 * font size ([SkyWords.fitLine]). When none does, the last one gets smaller, down to 10 sp; and
 * when even that is too wide, it takes two lines, broken between its words. So the moon's phase is
 * never cut mid-word. With [end] it keeps to the end of its room, with [center] to its middle.
 */
@Composable
private fun FirstThatFits(
    lines: List<String>,
    style: TextStyle,
    color: Color,
    end: Boolean = false,
    center: Boolean = false,
) {
    if (lines.isEmpty()) return
    val measurer = rememberTextMeasurer()
    BoxWithConstraints {
        val room = constraints.maxWidth
        val size = style.fontSize.takeIf { it.isSp }?.value ?: 12f
        val fit = remember(lines, style, room, measurer) {
            SkyWords.fitLine(lines, size, LEAST_LINE_SP, room.toFloat()) { text, to ->
                measurer.measure(text, sized(style, size, to), maxLines = 1, softWrap = false).size.width.toFloat()
            }
        }
        Text(
            fit.text,
            style = sized(style, size, fit.size),
            color = color,
            maxLines = if (fit.twoLines) 2 else 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = when {
                end -> TextAlign.End
                center -> TextAlign.Center
                else -> TextAlign.Start
            },
        )
    }
}

/** The smallest size a sky card's words get before they take two lines, in sp. */
private const val LEAST_LINE_SP = 10f

/** [style], whose font is [size] sp, at [to] sp, its line height in proportion. */
private fun sized(style: TextStyle, size: Float, to: Float): TextStyle =
    if (to == size) style
    else style.copy(
        fontSize = to.sp,
        lineHeight = if (style.lineHeight.isSp) style.lineHeight * (to / size) else style.lineHeight
    )

/** The dark part of the moon, and the part of the sun's path still to come: the color at this alpha. */
private const val DARK_ALPHA = 0.3f

/** How far the night band reaches past the sky's box, sideways and down: to the card's edge, which clips it. */
private val BAND_OUT = 40.dp
private val BAND_DOWN = 400.dp

/**
 * The night band's color on [behind]: darker in a dark theme, a little tinted in a light one, as
 * the mockup has it.
 */
@Composable
private fun nightBand(behind: Color): Color {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.surface.luminance() < 0.5f) lerp(behind, Color.Black, 0.22f)
    else lerp(behind, scheme.onSurfaceVariant, 0.13f)
}

/**
 * The sky, drawn in its box: the horizon, the day arc over it, and the sun or the moon at its place.
 * - By day the arc's travelled part is in [role], the part to come dimmer. At night the whole day
 *   arc is dim, and the body is on a dotted arc under the horizon, inside this box.
 * - With [band], the part under the horizon is the night band, which reaches past the box to the
 *   card's edges ([BAND_OUT]); the card clips it. Draw the words that sit on it after this.
 * - [textRoom] is kept free at the start of the box for words: the arc is in the rest.
 * - The body has a ring of [behind], the color it is drawn on, so it stands clear of the arc.
 * - The place is read while drawing. It is no item for a screen reader: the card's description
 *   says where the sun is and what comes next.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SkyDrawing(
    sky: SkyNow,
    spec: WidgetSpec.Sky,
    role: Color,
    behind: Color,
    modifier: Modifier,
    band: Boolean = true,
    textRoom: Dp = 0.dp,
) {
    val sun = MaterialShapes.Sunny.toShape()
    val bandColor = nightBand(behind)
    val horizon = lookColors().muted.copy(alpha = 0.45f)
    val dots = lookColors().muted
    val phase = spec.phase
    val place = sky.place
    Box(
        modifier
            .clearAndSetSemantics {}
            .drawWithCache {
                val unit = 1.dp.toPx()
                val ltr = layoutDirection == LayoutDirection.Ltr
                val room = textRoom.toPx().coerceIn(0f, size.width * 0.7f)
                val left = if (ltr) room else 0f
                val arcs = SkyWords.arcs(size.width - room, size.height, unit)
                val cx = left + arcs.cx
                val hy = arcs.horizon
                val line = Stroke(width = 4 * unit, cap = StrokeCap.Round)
                val dotted = Stroke(
                    width = 2.5f * unit,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 6 * unit))
                )
                val r = arcs.body
                val sunOutline = sun.createOutline(Size(2 * r, 2 * r), layoutDirection, this)
                val ring = 3 * unit
                val path = Path()
                onDrawBehind {
                    val out = BAND_OUT.toPx()
                    if (band) drawRect(
                        bandColor,
                        topLeft = Offset(-out, hy),
                        size = Size(size.width + 2 * out, BAND_DOWN.toPx())
                    )
                    drawLine(
                        horizon,
                        Offset(if (band) -out else cx - arcs.rx - r, hy),
                        Offset(if (band) size.width + out else cx + arcs.rx + r, hy),
                        strokeWidth = 1.5f * unit
                    )
                    val day = Rect(cx - arcs.rx, hy - arcs.ry, cx + arcs.rx, hy + arcs.ry)
                    drawArc(role.copy(alpha = DARK_ALPHA), 180f, 180f, false, day.topLeft, day.size, style = line)
                    if (place.up) {
                        drawArc(role, 180f, 180f * place.along, false, day.topLeft, day.size, style = line)
                    } else {
                        drawArc(
                            dots,
                            0f,
                            180f,
                            false,
                            Offset(cx - arcs.rx, hy - arcs.depth),
                            Size(2 * arcs.rx, 2 * arcs.depth),
                            style = dotted
                        )
                    }
                    val (dx, dy) = SkyWords.onArc(place.along, place.up)
                    val at = Offset(cx + dx * arcs.rx, hy + dy * if (place.up) arcs.ry else arcs.depth)
                    val under = if (!place.up && band) bandColor else behind
                    if (phase == null) {
                        translate(at.x - r, at.y - r) {
                            drawOutline(sunOutline, under, style = Stroke(width = 2 * ring))
                            drawOutline(sunOutline, role)
                        }
                    } else {
                        drawCircle(under, r + ring, at)
                        drawMoon(at, r, phase, spec.waxing, role, lerp(under, role, DARK_ALPHA), path)
                    }
                }
            })
}

/**
 * The moon at [center], [radius] across, with [phase] lit ([SkyWords.moonShape]): the disc in
 * [dark], the lit part in [lit]. [path] is reused from one drawing to the next.
 */
internal fun DrawScope.drawMoon(
    center: Offset,
    radius: Float,
    phase: Float,
    waxing: Boolean,
    lit: Color,
    dark: Color,
    path: Path
) {
    drawCircle(dark, radius, center)
    val k = phase.coerceIn(0f, 1f)
    if (k < 0.005f) return
    if (k > 0.995f) {
        drawCircle(lit, radius, center)
        return
    }
    val shape = SkyWords.moonShape(k, waxing)
    path.reset()
    // The lit edge of the disc, from its top over the lit side to its bottom.
    path.arcTo(Rect(center, radius), -90f, if (shape.litRight) 180f else -180f, forceMoveTo = true)
    // Back to the top along the line between light and dark: a crescent's bows toward the lit side.
    val w = radius * shape.edge
    if (w < 0.5f) {
        path.lineTo(center.x, center.y - radius)
    } else {
        val towardRight = shape.litRight == shape.crescent
        path.arcTo(
            Rect(center.x - w, center.y - radius, center.x + w, center.y + radius),
            90f,
            if (towardRight) -180f else 180f,
            forceMoveTo = false
        )
    }
    path.close()
    drawPath(path, lit)
}
