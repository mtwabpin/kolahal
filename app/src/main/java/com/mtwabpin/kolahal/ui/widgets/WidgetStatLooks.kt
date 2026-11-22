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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

// ---- stat ------------------------------------------------------------------------------------

/** `stat` in Bold, Split or Shape ([StatBody] draws Plain and Tonal). */
@Composable
internal fun StatLook(spec: WidgetSpec.Stat, box: WidgetSizes.Box, look: WidgetLook) {
    when {
        look == WidgetLook.BOLD -> BoldStat(spec, box)
        look == WidgetLook.SPLIT -> SplitStat(spec, box)
        LocalCardShaped.current -> ShapedStat(spec, box)
        else -> ShapeBesideStat(spec, box)
    }
}

/**
 * Bold's stat: the title small at the top with the icon on a cookie at its end, the value as big as
 * it fits in the role's color, and the label in bold under it, with the change or the smaller
 * values ("28° · 19°") at the end of that line.
 */
@Composable
private fun BoldStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.BOLD, role)
    val title = LocalUntitled.current
    val hasChange = spec.trend != null || spec.delta != null
    val rest = spec.more.joinToString(" · ") { it.value }
    val end: @Composable () -> Unit = {
        if (hasChange) ChangeText(spec.trend, spec.delta, role, type.labelLarge)
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
                SmallTitle(
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
            ) { BoldValue(spec.value, spec.unit, 88.sp, role) }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                spec.label?.let {
                    Text(
                        cardText(it),
                        style = type.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = c.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(Modifier.weight(1f))
                Box(Modifier.padding(start = 8.dp)) { end() }
            }
            spec.progress?.let { LevelBar(it, spec.moving, role, Modifier.padding(top = 8.dp)) }
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
                    ) { BoldValue(spec.value, spec.unit, 64.sp, role) }
                }
                // The label's column takes at most about two fifths of the width: the value is the main thing.
                Column(
                    Modifier
                        .padding(start = 12.dp)
                        .weight(0.7f, fill = false),
                    horizontalAlignment = Alignment.End
                ) {
                    spec.label?.let {
                        Text(
                            cardText(it),
                            style = type.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = c.content,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.End
                        )
                    }
                    end()
                }
            }
            spec.progress?.let { LevelBar(it, spec.moving, role, Modifier.padding(top = 4.dp)) }
        }

        WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) { BoldText(WidgetWords.joined(spec.value, spec.unit), 48.sp, role) }
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
 */
@Composable
private fun SplitStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.SPLIT, role)
    val title = LocalUntitled.current
    val gap = SplitCorners.GAP
    val hasChange = spec.trend != null || spec.delta != null
    val value: @Composable (TextStyle) -> Unit = { style ->
        Row {
            BigText(
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
                        TitleAndIcon(title, spec.base.icon, c.content, role)
                        Spacer(Modifier.weight(1f))
                        value(type.displaySmall.copy(fontSize = 44.sp))
                        spec.label?.let {
                            Text(
                                cardText(it),
                                style = type.bodySmall,
                                color = c.muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        spec.progress?.let {
                            LevelBar(
                                it,
                                spec.moving,
                                role,
                                Modifier.padding(top = 6.dp)
                            )
                        }
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
                        TitleAndIcon(title, spec.base.icon, c.content, role)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.CenterStart
                        ) { value(type.headlineSmall) }
                        val progress = spec.progress
                        if (progress != null) LevelBar(progress, spec.moving, role)
                        else spec.label?.let {
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
                BigText(
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

/** A stat's label and the card's title on one line: "Sunny · Pune". */
@Composable
private fun labelAndTitle(label: String?, title: String?): AnnotatedString? {
    val styled = label?.let { cardText(it) }
    return when {
        styled != null && title != null -> styled + AnnotatedString(" · $title")
        styled != null -> styled
        else -> title?.let(::AnnotatedString)
    }
}

/** Shape's stat in a card that is a cookie: the icon, the value, and "Sunny · Pune". A small card has the value and the label. */
@Composable
private fun ShapedStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val small = box == WidgetSizes.Box.SMALL
    val line = labelAndTitle(spec.label, LocalUntitled.current.takeIf { !small })
    ShapeLines {
        if (!small) spec.base.icon?.let { WidgetIconText(it, type.titleMedium, role, size = 22.dp) }
        BigText(
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

/** Shape's stat in a wide card: the value in a cookie, and beside it "Sunny · Pune" over the smaller values. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBesideStat(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val role = roleColor(spec.base)
    val c = lookColors(WidgetLook.SHAPE, role)
    val head = labelAndTitle(spec.label, LocalUntitled.current)
    val more = spec.more.joinToString(" · ") { WidgetWords.joined(it.value, it.label) }
    val large = box == WidgetSizes.Box.LARGE
    BesideShape(MaterialShapes.Cookie12Sided, c.shapeFill, inShape = { side ->
        BigText(
            WidgetWords.joined(spec.value, spec.unit),
            shapeValueStyle(side),
            Modifier.padding(horizontal = side / 7)
        )
    }) {
        head?.let {
            Text(
                it,
                style = type.titleSmall.copy(fontWeight = FontWeight.Bold),
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
        spec.progress?.let { LevelBar(it, spec.moving, role, Modifier.padding(top = 6.dp)) }
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
