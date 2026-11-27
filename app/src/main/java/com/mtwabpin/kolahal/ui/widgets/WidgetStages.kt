/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

/*
 * `progress` with `stages`: a parcel or a flight as steps on a line. Stages done are ticked dots,
 * the one it is at is a cookie, the way from it to the next is a wave as far as `value` of `max`
 * says, and the stages to come are rings. The current stage's name is the card's main text, with
 * the `label` under it.
 */

/** Each stage needs this much width for its name under its mark: a 4×1 has it, a 2×1 doesn't. */
private val NAMED_STAGE = 56.dp

/** The name of the stage it is at. */
private fun currentName(spec: WidgetSpec.Progress): String =
    spec.stages[spec.stage.coerceIn(1, spec.stages.size) - 1]

/**
 * Stages in [look], for [box].
 * - Plain and Tonal: a 4×1 has the title and the label on its first line, and the marks with each
 *   stage's name under them, the current one in bold. A 2×1 has the title, the current stage in
 *   the card's color with the label after it, and the marks alone. A 2×2 or 4×2 has the title, the
 *   current stage large with the label under it, and the marks at the bottom. A 1×1 has the current
 *   stage over the marks.
 * - Bold: the card fills to how far along the path it is (the frame draws that), with the current
 *   stage as large as it fits, and the label. A 2-row card has the marks under them.
 * - Split: a tile with the words, and a tile with the marks.
 * - Shape: a cookie with "3/4" at the start, and beside it the current stage, the label and the
 *   marks. A 1×1 or 2×2 card is the clover, with the current stage and "3 of 4" in it.
 * - Details, a line under each stage's name: where the names show under the marks and the card has
 *   a line for them, a 4×2 and larger in Plain, Tonal and Split ([StagesTrack]). Elsewhere a
 *   screen reader still says them.
 * - The card's badge ([shownBadge]) is at the end of the first line: the title's, or in Split the
 *   words' tile's; Shape has it after the current stage. It goes where the line has no room for it.
 */
@Composable
internal fun StagesBody(
    spec: WidgetSpec.Progress,
    box: WidgetSizes.Box,
    look: WidgetLook,
    color: Color
) {
    when {
        look == WidgetLook.BOLD -> BoldStages(spec, box, color)
        look == WidgetLook.SPLIT -> SplitStages(spec, box, color)
        look == WidgetLook.SHAPE && LocalCardShaped.current -> ShapedStages(spec, box, color)
        look == WidgetLook.SHAPE -> ShapeBesideStages(spec, box, color)
        else -> PlainStages(spec, box, color)
    }
}

@Composable
private fun PlainStages(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors()
    val title = LocalUntitled.current
    val name = currentName(spec)
    val badge = shownBadge(spec, box)
    // On Tonal's card of the card's color, the current stage is in the text color.
    val strong = if (LocalWidgetLook.current == WidgetLook.TONAL) c.content else color
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val named = maxWidth / spec.stages.size >= NAMED_STAGE
        when (box) {
            WidgetSizes.Box.WIDE -> Column(Modifier.fillMaxSize()) {
                if (named) {
                    // The badge at the line's end, after the label (or the title), where they leave it room.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val label = spec.label
                        title?.let {
                            if (label == null && badge != null) BadgedLine(badge, color, Modifier.weight(1f)) {
                                StageTitle(it, c)
                            } else StageTitle(it, c, Modifier.weight(1f, fill = false))
                        }
                        if (label != null) {
                            val line: @Composable (Modifier) -> Unit = { modifier ->
                                Text(
                                    label,
                                    style = type.bodySmall,
                                    color = c.muted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.End,
                                    modifier = modifier
                                )
                            }
                            val place = Modifier
                                .padding(start = 12.dp)
                                .weight(1f)
                            if (badge != null) BadgedFill(badge, color, place) { line(Modifier.fillMaxWidth()) }
                            else line(place)
                        }
                        if (title == null && label == null && badge != null) BadgedLine(badge, color, Modifier.weight(1f)) {}
                    }
                    StagesTrack(
                        spec, color, c, 26.dp, names = true,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 4.dp)
                    )
                } else {
                    if (title != null) BadgedLine(badge, color) { StageTitle(title, c) }
                    BadgedLine(badge.takeIf { title == null }, color) {
                        StageLine(name, spec.label, strong, c, type.titleSmall)
                    }
                    StagesTrack(
                        spec, color, c, 20.dp, names = false,
                        modifier = Modifier
                            .weight(1f)
                            .padding(top = 2.dp)
                    )
                }
            }

            WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
                BadgedLine(badge, color) { SmallTitle(title, c.content) }
                Spacer(Modifier.weight(1f))
                Text(
                    name,
                    style = type.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = c.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                spec.label?.let {
                    Text(
                        it,
                        style = type.bodyMedium,
                        color = c.muted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.weight(1f))
                StagesTrack(spec, color, c, 28.dp, names = named, details = true)
            }

            WidgetSizes.Box.SMALL -> Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    name,
                    style = type.titleSmallEmphasized,
                    color = c.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                StagesTrack(spec, color, c, 16.dp, names = false, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

/** The card's title over stages, in bold. */
@Composable
private fun StageTitle(title: String, c: LookColors, modifier: Modifier = Modifier) {
    Text(
        cardText(title),
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = c.content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/** The current stage [name] in bold and in [strong], with the [label] after it, on one line: "Boarding · Gate 23". */
@Composable
private fun StageLine(name: String, label: String?, strong: Color, c: LookColors, style: TextStyle) {
    val line = buildAnnotatedString {
        withStyle(SpanStyle(color = strong, fontWeight = FontWeight.Bold)) { append(name) }
        label?.let { withStyle(SpanStyle(color = c.muted)) { append(" · $it") } }
    }
    Text(line, style = style, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

/**
 * Bold's stages: the current stage as large as it fits, the title small over it, the label under it.
 * The card's badge is at the title's end. Bold has no room for details.
 */
@Composable
private fun BoldStages(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.BOLD, color)
    val title = LocalUntitled.current
    val name = currentName(spec)
    val badge = shownBadge(spec, box)
    when (box) {
        WidgetSizes.Box.LARGE -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val named = maxWidth / spec.stages.size >= NAMED_STAGE
            Column(Modifier.fillMaxSize()) {
                BadgedLine(badge, color) { SmallTitle(title, c.content) }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomStart
                ) { BoldText(name, 56.sp, c.content) }
                spec.label?.let {
                    Text(
                        it,
                        style = type.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = c.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                StagesTrack(spec, color, c, 22.dp, names = named, modifier = Modifier.padding(top = 10.dp))
            }
        }

        WidgetSizes.Box.WIDE -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            BadgedLine(badge, color) { SmallTitle(title, c.content) }
            // The name takes the height the title and the label leave, and gets smaller to fit it.
            Box(Modifier.weight(1f, fill = false), contentAlignment = Alignment.CenterStart) {
                BoldText(name, 36.sp, c.content)
            }
            spec.label?.let {
                Text(
                    it,
                    style = type.labelMedium,
                    color = c.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                BoldText(name, 28.sp, c.content)
            }
            Text(
                ProgressStyles.stageCount(spec),
                style = type.labelSmall,
                color = c.content,
                maxLines = 1
            )
        }
    }
}

/**
 * Split's stages: a tile with the title, the current stage and the label, and a tile with the
 * marks: side by side in a 1-row card, one over the other in a 2-row one. A 1×1 is one tile with
 * the current stage over the marks. The card's badge is at the title's end in the words' tile; a
 * 2-row card has the details under the names in the marks' tile, room allowing.
 */
@Composable
private fun SplitStages(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SPLIT, color)
    val title = LocalUntitled.current
    val name = currentName(spec)
    val gap = SplitCorners.GAP
    val badge = shownBadge(spec, box)
    val words: @Composable (Modifier, Shape) -> Unit = { modifier, shape ->
        Tile(shape, c.tile, modifier) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // The title wraps to a second line in the room the stage and the label leave.
                if (badge != null) BadgedLine(badge, color, Modifier.weight(1f, fill = false)) {
                    SmallTitle(title, c.content, lines = 2)
                } else SmallTitle(title, c.content, Modifier.weight(1f, fill = false), lines = 2)
                Text(
                    name,
                    style = type.titleSmallEmphasized,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                spec.label?.let {
                    Text(
                        it,
                        style = type.bodySmall,
                        color = c.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    val marks: @Composable (Modifier, Shape) -> Unit = { modifier, shape ->
        Tile(shape, c.tile, modifier) {
            BoxWithConstraints(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                StagesTrack(
                    spec, color, c, 24.dp, names = maxWidth / spec.stages.size >= NAMED_STAGE,
                    details = box == WidgetSizes.Box.LARGE
                )
            }
        }
    }
    when (box) {
        WidgetSizes.Box.SMALL -> Tile(SplitCorners.inRow(0, 1), c.tile, Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    name,
                    style = type.titleSmallEmphasized,
                    color = c.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                StagesTrack(spec, color, c, 14.dp, names = false, modifier = Modifier.padding(top = 6.dp))
            }
        }

        WidgetSizes.Box.WIDE -> BoxWithConstraints(Modifier.fillMaxSize()) {
            val boxWidth = maxWidth
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
                words(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(), SplitCorners.inRow(0, 2)
                )
                marks(
                    Modifier
                        .weight(if (boxWidth > 300.dp) 2f else 1f)
                        .fillMaxHeight(), SplitCorners.inRow(1, 2)
                )
            }
        }

        WidgetSizes.Box.LARGE -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(gap)
        ) {
            words(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(), SplitCorners.inColumn(0, 2)
            )
            marks(Modifier.fillMaxWidth(), SplitCorners.inColumn(1, 2))
        }
    }
}

/** Shape's stages in a card that is the clover ([LocalCardShaped]): the current stage and "3 of 4", and in a 2×2 the label and the marks. */
@Composable
private fun ShapedStages(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, color)
    val large = box == WidgetSizes.Box.LARGE
    ShapeLines {
        Text(
            currentName(spec),
            style = if (large) type.titleMediumEmphasized else type.labelLargeEmphasized,
            color = c.content,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        Text(
            ProgressStyles.stageCount(spec),
            style = type.labelMedium,
            color = c.muted,
            maxLines = 1
        )
        if (large) {
            spec.label?.let {
                Text(
                    it,
                    style = type.bodySmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
            StagesTrack(
                spec, color, c, 14.dp, names = false,
                behind = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * Shape's stages in a wide card: a cookie (or the shape chosen for the card, [cardShape]) with "3/4"
 * at the start, and beside it the current stage, the label and the marks. The card's badge is at the
 * end of the current stage's line, where it leaves the room.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShapeBesideStages(spec: WidgetSpec.Progress, box: WidgetSizes.Box, color: Color) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SHAPE, color)
    BesideShape(
        cardShape(),
        c.shapeFill,
        inShape = { side ->
            BigText(
                "${spec.stage}/${spec.stages.size}",
                shapeValueStyle(side, 0.3f),
                Modifier.padding(horizontal = side / 6),
                color = c.content
            )
        },
        beside = {
            BadgedLine(shownBadge(spec, box), color) {
                Text(
                    currentName(spec),
                    style = type.titleSmallEmphasized,
                    color = c.content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            spec.label?.let {
                Text(
                    it,
                    style = type.bodySmall,
                    color = c.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            StagesTrack(spec, color, c, 14.dp, names = false, modifier = Modifier.padding(top = 4.dp))
        }
    )
}

/**
 * The stages on a line, as wide as it is given: a mark for each, in the middle of an equal share
 * of the width, and lines between them.
 * - A stage done is a dot in [color] with a tick; the one it is at is a cookie [mark] across, with
 *   a dot of [behind] in it; one to come is a ring in the dim track color.
 * - Between stages done the line is [color]; from the current stage to the next it is a wave
 *   ([LevelBar]) as far as `value` of `max`, a little of it always, that moves with `moving` while
 *   the page is in view; after that, the dim track.
 * - With [names], each stage's name is under its mark, the current one in bold.
 * - With [details] too, each stage's detail is on a line under its name, quiet, where the height
 *   holds that line with the marks at their full size ([ProgressStyles.detailsFit]).
 * - The cookie is [mark] across, or less when the height given is less; never under 10 dp.
 * It is no item for a screen reader: the card's description says the stages.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StagesTrack(
    spec: WidgetSpec.Progress,
    color: Color,
    c: LookColors,
    mark: Dp,
    names: Boolean,
    modifier: Modifier = Modifier,
    behind: Color = c.onAccent,
    details: Boolean = false,
) {
    val type = MaterialTheme.typography
    val count = spec.stages.size
    val at = spec.stage.coerceIn(1, count) - 1
    val track = color.copy(alpha = 0.24f)
    val namesRoom = if (names) with(LocalDensity.current) { 16.sp.toDp() } + 4.dp else 0.dp
    // A detail's line is as tall as a name's: labelMedium's 16 sp.
    val detailRoom = with(LocalDensity.current) { 16.sp.toDp() }
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        contentAlignment = Alignment.Center
    ) {
        val lines = details &&
                ProgressStyles.detailsFit(spec, names, maxHeight.value, mark.value, namesRoom.value, detailRoom.value)
        val side = min(mark, maxHeight - namesRoom - if (lines) detailRoom else 0.dp).coerceAtLeast(10.dp)
        val share = maxWidth / count
        val line = (side / 7.5f).coerceAtLeast(2.dp)
        // Each mark's size: a dot done, the cookie, a ring to come.
        fun sizeOf(i: Int): Dp = when {
            i < at -> side * 0.62f
            i == at -> side
            else -> side * 0.5f
        }
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(side)
            ) {
                for (i in 0 until count - 1) {
                    key(i) {
                        val from = share * (i + 0.5f) + sizeOf(i) / 2 + 3.dp
                        val to = share * (i + 1.5f) - sizeOf(i + 1) / 2 - 3.dp
                        if (to > from) {
                            if (i == at) {
                                Box(
                                    Modifier
                                        .offset(x = from)
                                        .width(to - from)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    // A little of the way is always a wave: it says the thing is on its way.
                                    LevelBar(
                                        maxOf(ProgressStyles.along(spec), 0.2f),
                                        spec.moving,
                                        color,
                                        wavy = true
                                    )
                                }
                            } else {
                                Box(
                                    Modifier
                                        .offset(x = from, y = (side - line) / 2)
                                        .width(to - from)
                                        .height(line)
                                        .clip(CircleShape)
                                        .background(if (i < at) color else track)
                                )
                            }
                        }
                    }
                }
                for (i in 0 until count) {
                    key(i) {
                        val size = sizeOf(i)
                        val place = Modifier
                            .offset(x = share * (i + 0.5f) - size / 2, y = (side - size) / 2)
                            .size(size)
                        when {
                            i < at -> Box(
                                place
                                    .clip(CircleShape)
                                    .background(color),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painterResource(R.drawable.ic_check),
                                    contentDescription = null,
                                    tint = c.onAccent,
                                    modifier = Modifier.padding(size * 0.16f)
                                )
                            }

                            i == at -> ShapedBox(MaterialShapes.Cookie9Sided, color, size, place) {
                                Box(
                                    Modifier
                                        .size(size / 3)
                                        .clip(CircleShape)
                                        .background(behind)
                                )
                            }

                            else -> Box(
                                place.border(
                                    width = (side / 10).coerceAtLeast(2.dp),
                                    color = track,
                                    shape = CircleShape
                                )
                            )
                        }
                    }
                }
            }
            if (names) Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                spec.stages.forEachIndexed { i, stage ->
                    key(i) {
                        Text(
                            stage,
                            style = if (i == at) type.labelMediumEmphasized else type.labelMedium,
                            color = if (i == at) c.content else c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(share)
                        )
                    }
                }
            }
            // Each stage's detail under its name ("Mon 09:12"); one with none leaves its place blank.
            if (lines) Row(Modifier.fillMaxWidth()) {
                for (i in 0 until count) {
                    key(i) {
                        Text(
                            ProgressStyles.stageDetail(spec, i).orEmpty(),
                            style = type.labelMedium,
                            color = c.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(share)
                        )
                    }
                }
            }
        }
    }
}
