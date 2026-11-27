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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.cardBadge

// ---- stat ------------------------------------------------------------------------------------

/** `stat` in Bold, Split, Shape or Poster ([StatBody] draws Plain and Tonal). */
@Composable
internal fun StatLook(spec: WidgetSpec.Stat, box: WidgetSizes.Box, look: WidgetLook) {
    when {
        look == WidgetLook.POSTER -> PosterStat(spec, box)
        look == WidgetLook.BOLD -> BoldStat(spec, box)
        look == WidgetLook.SPLIT -> SplitStat(spec, box)
        LocalCardShaped.current -> ShapedStat(spec, box)
        else -> ShapeBesideStat(spec, box)
    }
}

/**
 * Bold's stat: the title small at the top with the icon on a cookie at its end, the value as big as
 * it fits in the role's color, and the label in bold under it, with the change or the smaller
 * values ("28° · 19°") at the end of that line. With `points`, a wide or large card has them as a
 * line under all of that, in the card's text color, so it stays apart from the value ([Sparkline]).
 * - A target is on the label's line: "of 8,000 · Steps". Past it the bar has its mark ([StatBar]),
 *   and a large card writes the share under it ([TargetShare]).
 * - A badge is at the top end, before the icon's cookie; a wide card has it at the top of its end
 *   column, over the label.
 * - A small card has neither.
 */
@Composable
private fun BoldStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.BOLD, role)
    // The value in the role's hue, moved just far enough to read on the card ([valueInk]).
    val ink = valueInk(role, c.card)
    val title = LocalUntitled.current
    val hasChange = spec.trend != null || spec.delta != null
    val spark = spec.points.size >= 2
    val badge = spec.cardBadge
    val target = spec.target
    val rest = spec.more.joinToString(" · ") { it.value }
    val end: @Composable () -> Unit = {
        if (hasChange) ChangeText(spec.trend, spec.delta, role, type.labelLarge, ground = c.card)
        else if (rest.isNotEmpty()) Text(
            rest,
            style = type.labelMedium,
            color = c.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            Row(verticalAlignment = Alignment.Top) {
                if (badge != null) BadgedLine(
                    badge, role, Modifier
                        .weight(1f)
                        .padding(top = 4.dp, end = 6.dp)
                ) { SmallTitle(title, c.content) }
                else SmallTitle(
                    title, c.content, Modifier
                        .weight(1f)
                        .padding(top = 4.dp)
                )
                spec.base.icon?.let { IconCookie(it, role, 40.dp) }
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomStart
            ) { BoldValue(spec.value, spec.unit, 88.sp, ink, rolls = true) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The label takes all the room the change leaves: a spacer beside it would take half of it.
                val label = targetAndLabel(target, spec.label)
                if (label != null) Text(
                    label,
                    style = type.titleSmallEmphasized,
                    color = c.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                else Spacer(Modifier.weight(1f))
                Box(Modifier.padding(start = 8.dp)) { end() }
            }
            spec.progress?.let { StatBar(spec, it, role, c.content, Modifier.padding(top = 8.dp)) }
            TargetShare(spec, c.muted)
            if (spark) Sparkline(
                spec.points,
                c.content,
                c.card,
                Modifier
                    .weight(0.7f)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                side = SPARK_SIDE,
                down = SPARK_DOWN_LARGE
            )
        }

        WidgetSizes.Box.WIDE -> Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    SmallTitle(title, c.content)
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomStart
                    ) { BoldValue(spec.value, spec.unit, 64.sp, ink, rolls = true) }
                }
                // The label's column takes at most about two fifths of the width: the value is the main thing.
                Column(
                    Modifier
                        .padding(start = 12.dp)
                        .weight(0.7f, fill = false),
                    horizontalAlignment = Alignment.End
                ) {
                    badge?.let { CardBadge(it, role, Modifier.padding(bottom = 2.dp)) }
                    targetAndLabel(target, spec.label)?.let {
                        Text(
                            it,
                            style = type.labelLargeEmphasized,
                            color = c.content,
                            // Under a badge, one line: the column keeps to the card's height.
                            maxLines = if (badge != null) 1 else 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }
                    end()
                }
            }
            spec.progress?.let { StatBar(spec, it, role, c.content, Modifier.padding(top = 4.dp)) }
            // The line takes about a third of a 1-row card, under the value.
            if (spark) Sparkline(
                spec.points,
                c.content,
                c.card,
                Modifier
                    .weight(0.45f)
                    .fillMaxWidth()
                    .padding(top = 2.dp),
                side = SPARK_SIDE,
                down = SPARK_DOWN_WIDE
            )
        }

        WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                val shown = WidgetWords.joined(spec.value, spec.unit)
                RollingText(shown) { roll, laid -> BoldText(shown, 48.sp, ink, roll, laid) }
            }
            spec.label?.let {
                Text(
                    cardText(it),
                    style = type.labelSmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Split's stat: a big tile with the title, the icon, the value and the label, over a row of small
 * tiles, one for each smaller value (three at most). A 1-row card has the smaller values beside
 * the value, as many as fit; a small one has the label in a tile under the value.
 * With `points`, the big tile has them as a line under the value, from edge to edge of the tile
 * ([Sparkline]). In a 1-row card the line takes the label's place, as a bar does.
 * - A target is in the big tile under the value: "of 8,000 · Steps", or "of 8,000" alone where a
 *   bar or the line takes the label's place. Past it the bar has its mark ([StatBar]), and a large
 *   card writes the share under it ([TargetShare]).
 * - A badge is at the top end of the big tile, before the icon ([BadgedTitleAndIcon]).
 * - A small card has neither.
 */
@Composable
private fun SplitStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.SPLIT, role)
    val title = LocalUntitled.current
    val gap = SplitCorners.GAP
    val hasChange = spec.trend != null || spec.delta != null
    val spark = spec.points.size >= 2
    val badge = spec.cardBadge
    val target = spec.target
    val value: @Composable (TextStyle) -> Unit = { style ->
        Row {
            RollingBigText(
                spec.value, style, Modifier
                    .weight(1f, fill = false)
                    .alignByBaseline()
            )
            spec.unit?.let {
                Text(
                    it,
                    style = type.titleMedium,
                    color = c.muted,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 2.dp)
                        .alignByBaseline()
                )
            }
            if (hasChange) ChangeText(
                spec.trend,
                spec.delta,
                role,
                type.labelLarge,
                Modifier
                    .padding(start = 8.dp)
                    .alignByBaseline()
            )
        }
    }
    when (box) {
        WidgetSizes.Box.LARGE -> {
            val more = spec.more.take(3)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Tile(
                    SplitCorners.inColumn(0, if (more.isEmpty()) 1 else 2),
                    c.tile,
                    Modifier
                        .fillMaxWidth()
                        .weight(1.9f)
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        BadgedTitleAndIcon(title, spec.base.icon, c.content, role, badge)
                        // With a line, the value goes up under the title and the line takes the room under it.
                        if (!spark) Spacer(Modifier.weight(1f))
                        value(type.displaySmall.copy(fontSize = 44.sp))
                        targetAndLabel(target, spec.label)?.let {
                            Text(
                                it,
                                style = type.bodySmall,
                                color = c.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        spec.progress?.let {
                            StatBar(
                                spec,
                                it,
                                role,
                                c.content,
                                Modifier.padding(top = 6.dp)
                            )
                        }
                        TargetShare(spec, c.muted)
                        // The tile's room around its content is 16 dp at the sides and 12 dp under it.
                        if (spark) Sparkline(
                            spec.points,
                            role,
                            c.tile,
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(top = 6.dp),
                            side = 16.dp,
                            down = 12.dp
                        )
                    }
                }
                if (more.isNotEmpty()) Row(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(gap)
                ) {
                    more.forEachIndexed { i, small ->
                        key(i) {
                            Tile(
                                SplitCorners.tile(topStart = false, topEnd = false, bottomEnd = i == more.lastIndex, bottomStart = i == 0),
                                c.tile,
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                SmallValue(
                                    small,
                                    c.muted,
                                    Modifier
                                        .align(Alignment.Center)
                                        .padding(horizontal = 4.dp),
                                    centered = true
                                )
                            }
                        }
                    }
                }
            }
        }

        WidgetSizes.Box.WIDE -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val fit =
                ((maxWidth - 112.dp) / (64.dp + gap)).toInt().coerceIn(0, minOf(3, spec.more.size))
            val more = spec.more.take(fit)
            val count = more.size + 1
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                Tile(
                    SplitCorners.inRow(0, count), c.tile, Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        BadgedTitleAndIcon(title, spec.base.icon, c.content, role, badge)
                        val progress = spec.progress
                        // The target under the value: with the label where it shows, else alone.
                        val under: @Composable () -> Unit = {
                            if (target != null) targetAndLabel(target, spec.label.takeIf { progress == null && !spark })?.let {
                                Text(
                                    it,
                                    style = type.bodySmall,
                                    color = c.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        // Without a bar, the line takes the label's place, under the value. The tile's room is 14 dp at the sides, 8 dp under.
                        if (spark && progress == null) {
                            value(type.headlineSmall)
                            under()
                            Sparkline(
                                spec.points,
                                role,
                                c.tile,
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                side = 14.dp,
                                down = 8.dp
                            )
                        } else {
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.CenterStart
                            ) { value(type.headlineSmall) }
                            under()
                        }
                        if (progress != null) StatBar(spec, progress, role, c.content)
                        else if (!spark && target == null) spec.label?.let {
                            Text(
                                cardText(it),
                                style = type.bodySmall,
                                color = c.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                more.forEachIndexed { i, small ->
                    key(i) {
                        Tile(
                            SplitCorners.inRow(i + 1, count),
                            c.tile,
                            Modifier
                                .width(64.dp)
                                .fillMaxHeight()
                        ) {
                            SmallValue(
                                small,
                                c.muted,
                                Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 4.dp),
                                centered = true
                            )
                        }
                    }
                }
            }
        }

        WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            val label = spec.label
            Tile(
                SplitCorners.inColumn(0, if (label != null) 2 else 1),
                c.tile,
                Modifier
                    .fillMaxWidth()
                    .weight(1.6f)
            ) {
                RollingBigText(
                    WidgetWords.joined(spec.value, spec.unit),
                    type.titleLarge,
                    Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp)
                )
            }
            if (label != null) Tile(
                SplitCorners.inColumn(1, 2),
                c.tile,
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    cardText(label),
                    style = type.labelSmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp)
                )
            }
        }
    }
}

/** A stat's label and the card's title on one line: "Sunny · Pune". A [target] follows the label: "Steps of 8,000 · Pune". */
@Composable
private fun labelAndTitle(label: String?, title: String?, target: String? = null): AnnotatedString? {
    val words = target?.let(StatTarget::words)
    val styled = label?.let { cardText(it) }.let {
        when {
            words == null -> it
            it == null -> AnnotatedString(words)
            else -> it + AnnotatedString(" $words")
        }
    }
    return when {
        styled != null && title != null -> styled + AnnotatedString(" · $title")
        styled != null -> styled
        else -> title?.let(::AnnotatedString)
    }
}

/**
 * Shape's stat in a card that is a cookie: the icon, the value, and "Sunny · Pune", with a target
 * after the label ("Steps of 8,000 · Pune"). A small card has the value and the label. It has no
 * badge: the card is one shape.
 */
@Composable
private fun ShapedStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val small = box == WidgetSizes.Box.SMALL
    val line = labelAndTitle(spec.label, LocalUntitled.current.takeIf { !small }, spec.target.takeIf { !small })
    ShapeLines {
        if (!small) spec.base.icon?.let { WidgetIconText(it, type.titleMedium, role, size = 22.dp) }
        RollingBigText(
            WidgetWords.joined(spec.value, spec.unit),
            if (small) type.titleLarge else type.displayMedium.copy(fontSize = 46.sp)
        )
        line?.let {
            Text(
                it,
                style = type.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Shape's stat in a wide card: the value in a cookie (or the shape chosen for the card, [cardShape]),
 * and beside it "Sunny · Pune" over the smaller values. A target follows the label ("Steps of 8,000 ·
 * Pune"); past it the bar has its mark ([StatBar]), and a large card writes the share under it
 * ([TargetShare]). Shape has no badge.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBesideStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.SHAPE, role)
    val head = labelAndTitle(spec.label, LocalUntitled.current, spec.target)
    val more = spec.more.joinToString(" · ") { smallLine(it) }
    val large = box == WidgetSizes.Box.LARGE
    BesideShape(cardShape(), c.shapeFill, inShape = { side ->
        RollingBigText(
            WidgetWords.joined(spec.value, spec.unit),
            shapeValueStyle(side),
            Modifier.padding(horizontal = side / 7)
        )
    }) {
        head?.let {
            Text(
                it,
                style = type.titleSmallEmphasized,
                color = c.content,
                maxLines = if (large || more.isEmpty()) 2 else 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (more.isNotEmpty()) Text(
            more,
            style = type.bodySmall,
            color = c.muted,
            maxLines = if (large) 3 else 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )
        if (spec.trend != null || spec.delta != null) ChangeText(
            spec.trend,
            spec.delta,
            role,
            type.labelLarge,
            Modifier.padding(top = 4.dp)
        )
        spec.progress?.let { StatBar(spec, it, role, c.content, Modifier.padding(top = 6.dp)) }
        if (large) TargetShare(spec, c.muted)
    }
}

/**
 * [TitleAndIcon] with the card's [badge] at the end of the title's room, before the icon: the top
 * of a Split tile. The badge goes when the title leaves no room for it ([BadgedLine]). Without a
 * badge it is [TitleAndIcon] as it is.
 */
@Composable
internal fun BadgedTitleAndIcon(title: String?, icon: String?, color: Color, role: Color, badge: String?) {
    if (badge == null) return TitleAndIcon(title, icon, color, role)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BadgedLine(badge, role, Modifier.weight(1f)) {
            Text(
                title.orEmpty(),
                style = MaterialTheme.typography.labelMediumEmphasized,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        icon?.let {
            WidgetIconText(
                it,
                MaterialTheme.typography.titleMedium,
                role,
                size = 18.dp,
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

/** The card's [icon] in its [role] color on a cookie of the role's container, [side] across: Bold's badge. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun IconCookie(icon: String, role: Color, side: Dp) {
    ShapedBox(MaterialShapes.Cookie12Sided, roleContainer(role), side) {
        WidgetIconText(
            icon,
            MaterialTheme.typography.titleMedium,
            role,
            size = side / 2
        )
    }
}
