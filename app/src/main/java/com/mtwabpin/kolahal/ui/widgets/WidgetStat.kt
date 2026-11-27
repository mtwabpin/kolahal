/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.cardBadge

/**
 * `stat`: one big value with its label.
 * - Small: the value and the label, centered.
 * - Wide: the value with its change at the start, and a column at the end with the label. The
 *   smaller values go between them in their order, as many as fit, and the next one under the label
 *   ([StatWide]). A box under 150 dp has no room for that column: the label goes under the value,
 *   and a bar takes the label's place.
 * - Large: the value and its label at the top. The smaller values and the bar are at the bottom.
 * - With no label, the card's name ([LocalUntitled]) is its label: there is no title line.
 * - With `points`, a wide or large card has them as a soft line under the value, from edge to edge
 *   ([Sparkline]): a wide card's value goes to the top, a large card's line takes the room between
 *   the label and the smaller values. A small card leaves it out.
 * - The value's digits roll to a new value ([RollingText]).
 * - With a `target` ("of 8,000"): a large card has it after the value when the line holds both,
 *   else at the start of the label's line ("of 8,000 · Steps"); a wide card has it after the value
 *   from [StatTarget.AFTER_FROM_DP] wide, else under the label at the end, or under the value when
 *   there is no column. Past the target the bar is full with a mark where the target was
 *   ([StatBar]), and a large card writes the share under it ([TargetShare]). A small card leaves it out.
 * - With a badge ([cardBadge]): a large card has it at the end of the value's line, a wide one at
 *   the top of the end column (at the end of the value's line when there is no column). A small
 *   card leaves it out.
 */
@Composable
internal fun StatBody(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val look = LocalWidgetLook.current
    if (look.untitled()) {
        StatLook(spec, box, look)
        return
    }
    val type = MaterialTheme.typography
    // Plain, or Tonal: the same layout, with chips and quiet text in the Tonal card's colors.
    val muted = lookColors(look).muted
    val label = spec.label ?: LocalUntitled.current
    if (box == WidgetSizes.Box.SMALL) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            RollingBigText(WidgetWords.joined(spec.value, spec.unit), valueStyle(box))
            label?.let {
                Text(
                    cardText(it),
                    style = type.labelSmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        return
    }
    val color = roleColor(spec.base)
    val large = box == WidgetSizes.Box.LARGE
    val hasChange = spec.trend != null || spec.delta != null
    // The recent numbers run behind the card's lower part, under the value ([Sparkline]).
    val spark = spec.points.size >= 2
    val badge = spec.cardBadge
    val target = spec.target
    val ink = MaterialTheme.colorScheme.onSurface
    // With [after], the target follows the value and its unit, in the unit's style.
    val value: @Composable (Modifier, Boolean) -> Unit = { modifier, after ->
        Row(modifier) {
            RollingBigText(
                spec.value,
                valueStyle(box),
                Modifier
                    .weight(1f, fill = false)
                    .alignByBaseline()
            )
            spec.unit?.let {
                Text(
                    it,
                    style = type.titleMedium,
                    color = muted,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .alignByBaseline()
                )
            }
            if (after && target != null) Text(
                StatTarget.words(target),
                style = type.titleMedium,
                color = muted,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .padding(start = StatTarget.GAP_DP.dp)
                    .alignByBaseline()
            )
            // A large card has the change under the value, beside the label, as the mockup has it.
            if (hasChange && !large) ChangeText(
                spec.trend,
                spec.delta,
                color,
                type.labelLarge,
                Modifier
                    .padding(start = 8.dp)
                    .alignByBaseline(),
                ground = lookColors(look, color).card
            )
        }
    }
    // With [after], the target is after the value; else at the start of the label's line.
    val largeBody: @Composable (Boolean) -> Unit = { after ->
        Column(Modifier.fillMaxSize()) {
            BadgedLine(badge, color, Modifier.fillMaxWidth()) { value(Modifier, after) }
            val line = targetAndLabel(target.takeIf { !after }, label)
            if (line != null || hasChange) Row(verticalAlignment = Alignment.CenterVertically) {
                line?.let {
                    Text(
                        it,
                        style = type.bodyMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                if (hasChange) ChangeText(
                    spec.trend,
                    spec.delta,
                    color,
                    type.titleSmall,
                    Modifier.padding(start = if (line != null) 8.dp else 0.dp),
                    ground = lookColors(look, color).card
                )
            }
            if (spark) Sparkline(
                spec.points,
                color,
                lookColors(look).card,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                side = SPARK_SIDE
            )
            else Spacer(Modifier.weight(1f))
            if (spec.more.isNotEmpty()) {
                if (look == WidgetLook.TONAL) {
                    // Tonal has the smaller values in chips of a lighter tint, as the mockup has them.
                    val tile = lookColors(look).tile
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        spec.more.forEach {
                            SmallValue(
                                it,
                                muted,
                                Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(tile)
                                    .padding(vertical = 6.dp),
                                centered = true
                            )
                        }
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        spec.more.forEach {
                            SmallValue(
                                it,
                                muted,
                                Modifier.weight(1f, fill = false)
                            )
                        }
                    }
                }
            }
            spec.progress?.let { StatBar(spec, it, color, ink, Modifier.padding(top = 8.dp)) }
            TargetShare(spec, muted)
        }
    }
    if (large) {
        // Only a card with a target measures its line, to know whether the target fits after the value.
        if (target == null) largeBody(false)
        else BoxWithConstraints(Modifier.fillMaxSize()) {
            largeBody(targetFitsAfter(spec, target, valueStyle(box), maxWidth))
        }
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val side = maxWidth >= STAT_SIDE_FROM
        // The target after the value where the card is wide enough, else under the label in the end column.
        val after = target != null && StatTarget.afterValue(maxWidth.value)
        val targetUnder = target != null && side && !after
        // The smaller values in the order given: as many as fit beside the value, then the next under the label.
        val split = StatWide.split(
            maxWidth.value,
            spec.more.size,
            label != null,
            targetAfter = after,
            targetUnder = targetUnder,
            badge = badge != null
        )
        val between = spec.more.take(split.between)
        val first = if (split.inColumn) spec.more[split.between] else null
        val half = maxWidth / 2
        // With a sparkline, the value is at the top and the line takes the room under it.
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = if (spark) Arrangement.Top else Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    // Without the end column, the badge is at the end of the value's line.
                    BadgedLine(badge.takeIf { !split.column }, color) { value(Modifier, after) }
                    // Without the end column, the target is under the value, before the label.
                    if (!side) targetAndLabel(target, label.takeIf { spec.progress == null })?.let {
                        Text(
                            it,
                            style = type.bodySmall,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                between.forEach {
                    SmallValue(
                        it,
                        muted,
                        Modifier
                            .padding(start = 12.dp)
                            .widthIn(max = 72.dp)
                    )
                }
                if (split.column) {
                    Column(
                        Modifier
                            .padding(start = 12.dp)
                            .widthIn(max = half),
                        horizontalAlignment = Alignment.End
                    ) {
                        // The badge is at the top of the column, over the label.
                        badge?.let { CardBadge(it, color, Modifier.padding(bottom = 2.dp)) }
                        label?.let {
                            // Over a smaller value or the target the label is the column's heading. Alone it is a quiet note, on up to two lines.
                            if (first != null || targetUnder) Text(
                                cardText(it),
                                style = type.labelLargeEmphasized,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            else Text(
                                cardText(it),
                                style = type.bodySmall,
                                color = muted,
                                // Under a badge, one line: the column keeps to the card's height.
                                maxLines = if (badge != null) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End
                            )
                        }
                        // In place of a smaller value, the target ([StatWide.split] leaves that value out).
                        if (targetUnder && target != null) Text(
                            StatTarget.words(target),
                            style = type.bodySmall,
                            color = muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        first?.let {
                            Text(
                                smallLine(it),
                                style = type.bodySmall,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
            spec.progress?.let { StatBar(spec, it, color, ink, Modifier.padding(top = 4.dp)) }
            if (spark) Sparkline(
                spec.points,
                color,
                lookColors(look).card,
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                side = SPARK_SIDE,
                down = SPARK_DOWN_WIDE
            )
        }
    }
}

/** From this width, a wide stat has a column at its end for its label and a smaller value. */
private val STAT_SIDE_FROM = StatWide.SIDE_FROM_DP.dp

/**
 * Where a wide stat puts its smaller values, always in the order given. Pure: widths are in dp.
 * - A smaller value beside the big one needs about [SMALL_DP], after [VALUE_DP] for the big one.
 * - From [SIDE_FROM_DP] wide, the card has a column at its end ([COLUMN_DP]) for the label. As many
 *   smaller values as fit go between the big one and that column; the next one goes under the label,
 *   and any after it are left out (a screen reader still says them).
 * - When there is no label, no target under it and no badge, and every smaller value fits beside the
 *   big one, there is no column.
 * - A target after the big one ([targetAfter]) takes [StatTarget.TARGET_DP] more of the room beside it.
 *   A target under the label ([targetUnder]) takes the next value's place: that value is left out.
 * - A badge sits at the top of the column, so a card with one has the column.
 * - Narrower: no column; as many as fit beside the big one, and the label goes under it.
 */
internal object StatWide {
    const val SIDE_FROM_DP = 150f
    const val VALUE_DP = 112f
    const val SMALL_DP = 64f
    const val COLUMN_DP = 96f

    /** [between] values beside the big one; [column]: the end column is there; [inColumn]: the next value is under the label in it. */
    data class Split(val column: Boolean, val between: Int, val inColumn: Boolean)

    /** The split for a card [widthDp] wide with [count] smaller values, with a [label] or not, and the target and the badge where they are. */
    fun split(
        widthDp: Float,
        count: Int,
        label: Boolean,
        targetAfter: Boolean = false,
        targetUnder: Boolean = false,
        badge: Boolean = false
    ): Split {
        val value = VALUE_DP + if (targetAfter) StatTarget.TARGET_DP else 0f
        fun fit(room: Float) = (room / SMALL_DP).toInt().coerceIn(0, count)
        if (widthDp < SIDE_FROM_DP) return Split(column = false, between = fit(widthDp - value), inColumn = false)
        if (!label && !targetUnder && !badge && fit(widthDp - value) == count) {
            return Split(column = false, between = count, inColumn = false)
        }
        val between = fit(widthDp - value - COLUMN_DP)
        return Split(column = true, between = between, inColumn = between < count && !targetUnder)
    }
}

/**
 * Where a stat's target goes ("of 8,000", [WidgetSpec.Stat.target]) and its bar past it. Pure:
 * widths are in dp.
 * - A wide card has the target after the value from [AFTER_FROM_DP] wide; it takes about
 *   [TARGET_DP] there. Narrower, it goes under the label.
 * - A large card has it after the value when the line holds both, [GAP_DP] apart ([fitsAfter]).
 * - Past the target ([over]) the bar is full, with a mark where the target was ([mark]).
 */
internal object StatTarget {
    const val AFTER_FROM_DP = 240f
    const val TARGET_DP = 72f
    const val GAP_DP = 4f

    /** The target as the card writes it: "of 8,000". */
    fun words(target: String): String = "of $target"

    /** Whether a wide card [widthDp] wide has the target after its value. */
    fun afterValue(widthDp: Float): Boolean = widthDp >= AFTER_FROM_DP

    /** Whether the target, [targetDp] wide, fits after a value [valueDp] wide (with its unit) on a line [lineDp] wide. */
    fun fitsAfter(lineDp: Float, valueDp: Float, targetDp: Float): Boolean =
        valueDp + GAP_DP + targetDp <= lineDp

    /** Whether a stat with [target] and [progress] is past its target. Without a target, progress is held at 1 and never is. */
    fun over(target: String?, progress: Float?): Boolean =
        target != null && progress != null && progress > 1f

    /** Where the target is along a full bar past it, from 0 at its start to 1 at its end: 1 / [progress]. */
    fun mark(progress: Float): Float = (1f / progress).coerceIn(0f, 1f)
}

/**
 * The target's words before the label on one line: "of 8,000 · Steps". Either alone is the line;
 * without both there is none. A label alone is drawn as it always is ([cardText]).
 */
@Composable
internal fun targetAndLabel(target: String?, label: String?): AnnotatedString? {
    val styled = label?.let { cardText(it) }
    val words = target?.let(StatTarget::words) ?: return styled
    return if (styled == null) AnnotatedString(words) else AnnotatedString("$words · ") + styled
}

/**
 * Whether a large card's line, [line] wide, holds the [target] after the value and its unit at
 * their full size ([StatTarget.fitsAfter]). The value in [style] grows smaller to fit its room
 * ([BigText]), so it is measured as it would be alone.
 */
@Composable
private fun targetFitsAfter(spec: WidgetSpec.Stat, target: String, style: TextStyle, line: Dp): Boolean {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val small = MaterialTheme.typography.titleMedium
    return remember(spec.value, spec.unit, target, style, small, line, density) {
        fun width(text: String, of: TextStyle): Float = with(density) {
            measurer.measure(text, of, maxLines = 1, softWrap = false).size.width.toDp().value
        }
        // The unit is 2 dp after the value, as the line draws it.
        val value = width(spec.value, style.asValue()) + (spec.unit?.let { width(it, small) + 2f } ?: 0f)
        StatTarget.fitsAfter(line.value, value, width(StatTarget.words(target), small))
    }
}

/**
 * A stat's bar at [progress] in [color] ([LevelBar]). Past its target ([StatTarget.over]) it is
 * full, with a mark 2 dp wide in [mark] (the card's text color) where the target was, and a little
 * of the bar cleared at each side of it so it reads on the bar. Not past it, it is the bar as it
 * always was.
 */
@Composable
internal fun StatBar(
    spec: WidgetSpec.Stat,
    progress: Float,
    color: Color,
    mark: Color,
    modifier: Modifier = Modifier
) {
    if (!StatTarget.over(spec.target, progress)) {
        LevelBar(progress, spec.moving, color, modifier)
        return
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val at = StatTarget.mark(progress)
    LevelBar(
        progress,
        spec.moving,
        color,
        modifier
            .testTag(TARGET_MARK_TAG)
            // Drawn apart from the card, so the clearing shows the card through the bar.
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val line = TARGET_MARK.toPx()
                val x = size.width * (if (rtl) 1f - at else at)
                drawRect(
                    Color.Black,
                    Offset(x - line * 1.5f, 0f),
                    Size(line * 3f, size.height),
                    blendMode = BlendMode.Clear
                )
                drawRect(mark, Offset(x - line / 2, 0f), Size(line, size.height))
            }
    )
}

/** The width of the mark on a stat's bar where its target was ([StatBar]). */
private val TARGET_MARK = 2.dp

/** The tag of a stat's bar past its target: it has the mark. */
internal const val TARGET_MARK_TAG = "stat-target-mark"

/**
 * Under a large card's bar past its target ([StatTarget.over]), at its end in [color], the share
 * of the target reached: "112%" ([WidgetWords.share]). It says nothing good or bad. Nothing when
 * the bar is not past it.
 */
@Composable
internal fun TargetShare(spec: WidgetSpec.Stat, color: Color) {
    val progress = spec.progress ?: return
    if (!StatTarget.over(spec.target, progress)) return
    Text(
        WidgetWords.share(progress),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        maxLines = 1,
        textAlign = TextAlign.End,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp)
    )
}

/**
 * A stat's change in [color]: a drawn arrow for up or down (a text arrow for flat), then the
 * delta in bold. The arrow is as tall as the text. With [ground] (what the change is drawn on), the
 * color is taken just far enough to read there as small text ([smallInk]).
 */
@Composable
internal fun ChangeText(
    trend: String?,
    delta: String?,
    color: Color,
    style: TextStyle,
    modifier: Modifier = Modifier,
    ground: Color? = null
) {
    val ink = ground?.let { smallInk(color, it) } ?: color
    val drawn = when (trend) {
        "up" -> R.drawable.ic_arrow_up
        "down" -> R.drawable.ic_arrow_down
        else -> null
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (drawn != null) Icon(
            painterResource(drawn),
            null,
            tint = ink,
            modifier = Modifier.size(with(LocalDensity.current) { style.fontSize.toDp() })
        )
        else WidgetWords.arrow(trend)?.let { Text(it, style = style, color = ink, maxLines = 1) }
        delta?.let {
            Text(
                it,
                style = style.copy(fontWeight = FontWeight.Bold),
                color = ink,
                maxLines = 1,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

/**
 * A smaller value and its label on one line, a space between them: "62% humidity", "31° 15:00".
 * A label is never a unit ([WidgetWords.joined]): a label that starts with a digit (an hour) still
 * gets its space.
 */
internal fun smallLine(small: WidgetSpec.Small): String =
    listOfNotNull(small.value, small.label).joinToString(" ")

/**
 * The color of a big value drawn as ink in a card's [color] on [ground] (the card's own surface):
 * [color] as it is where it reads there at 3:1 ([INK_MIN]), else the same hue moved toward white or
 * black just far enough ([inkOn]). A color a person chooses, or a theme's dim role such as Rosé
 * Pine's good, keeps its hue and still reads. Only for a value as text: fills, bars, rings and
 * shapes are areas and keep the theme's color. Bold's and Poster's values, Bold's chart, and Bold's
 * score, sky and day countdowns use it; so do the Next look's check and the Stack look's +, icons
 * in the role's color on its container.
 */
internal fun valueInk(color: Color, ground: Color): Color = inkOn(ground, color, color)

/**
 * The color of small words in a card's [color] on [ground]: [color] where it reads there at 4.5:1
 * ([TEXT_MIN]), else the same hue darker (or lighter on a dark card) just far enough ([inkOn]). A
 * stat's change, an agenda's day headings and Bold's list title use it.
 */
internal fun smallInk(color: Color, ground: Color): Color = inkOn(ground, color, color, TEXT_MIN)

/**
 * One of a stat's smaller values, over its label in [muted]. With [centered], both are centered.
 * - The label starts with a capital here, on a line of its own. Where a look writes it after the
 *   value on one line ("62% humidity"), it keeps the script's case.
 */
@Composable
internal fun SmallValue(
    small: WidgetSpec.Small,
    muted: Color,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    Column(
        modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start
    ) {
        Text(
            small.value,
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        small.label?.let {
            Text(
                StyledText.capitalized(it),
                style = MaterialTheme.typography.labelSmall,
                color = muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
