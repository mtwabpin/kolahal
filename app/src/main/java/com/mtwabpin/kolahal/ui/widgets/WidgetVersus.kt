/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

// ---- versus ---------------------------------------------------------------------------------

/** From this width a wide versus card has its names beside the marks and the bar under them: a 4×1 card. */
private val VERSUS_ROW_FROM = 260.dp

/**
 * `versus`: two sides and the score between them, for a match or a game of chess.
 * - Each side has its name and its mark: a letter or two on a Material shape (a cookie for the
 *   first side, a clover for the second), in the side's color. A dot after a name marks the side
 *   whose turn it is.
 * - Under them, when both sides have a value, a bar of two parts, one for each side, with each
 *   side's figure at its end ("1:12", "58:10"). With `moving`, the turn's part is a wave that
 *   moves while the card can be seen: its clock runs. The parts move to new values on the theme's
 *   spring.
 * - Small: the score over the two marks. Wide and narrow (2×1): the score between the marks, the
 *   names under them, no bar. Wide (4×1): the names beside the marks, the score and the last line
 *   between them, the bar under all. Large: the title and the badge, the marks either side of the
 *   score with the names under them, the bar and the last line.
 * - Bold has the score in Bold's type, in the card's color. Split has each side and the score on
 *   tiles of their own. There is no Shape look ([WidgetLook.offeredFor]): the marks are shapes in
 *   every look.
 */
@Composable
internal fun VersusBody(spec: WidgetSpec.Versus, box: WidgetSizes.Box) {
    val look = LocalWidgetLook.current
    val roles = spec.sides.mapIndexed { i, side -> colorOf(VersusWords.role(side, i)) }
    if (look == WidgetLook.SPLIT) {
        SplitVersus(spec, roles, box)
        return
    }
    val bold = look == WidgetLook.BOLD
    val c = lookColors(look, roleColor(spec.base))
    val type = MaterialTheme.typography
    when (box) {
        WidgetSizes.Box.SMALL -> Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Score(spec, type.titleLarge, bold, 40f)
            Marks(spec, roles, 20.dp, Modifier.padding(top = 4.dp))
        }

        WidgetSizes.Box.WIDE -> BoxWithConstraints(Modifier.fillMaxSize()) {
            if (maxWidth >= VERSUS_ROW_FROM) VersusRow(spec, roles, bold, c, maxWidth)
            else VersusNarrow(spec, roles, bold)
        }

        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                LocalUntitled.current?.let {
                    Text(
                        it,
                        style = type.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = c.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
                Spacer(Modifier.weight(1f))
                spec.badge?.let { Badge(it, roleColor(spec.base), Modifier.padding(start = 8.dp)) }
            }
            Faces(spec, roles, bold, type.displaySmall, 56f, Modifier.weight(1f).fillMaxWidth())
            VersusBar(spec, roles, figures = true, Modifier.padding(top = 6.dp))
            spec.line?.let { LastLine(it, c.muted, Modifier.padding(top = 6.dp)) }
        }
    }
}

/**
 * A wide card (4×1): each side's mark and name at its end, the score and the last line in the
 * middle, the bar under them. The badge is in front of the last line.
 */
@Composable
private fun VersusRow(spec: WidgetSpec.Versus, roles: List<Color>, bold: Boolean, c: LookColors, width: Dp) {
    val type = MaterialTheme.typography
    val turn = VersusWords.turnIndex(spec)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Mark(spec, 0, roles, 28.dp)
                SideName(spec, 0, roles, turn == 0, type.titleSmall, Modifier.padding(start = 8.dp).weight(1f, fill = false))
            }
            Column(
                Modifier.padding(horizontal = 8.dp).widthIn(max = width * 0.44f),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Score(spec, type.headlineMedium, bold, 40f)
                if (spec.line != null || spec.badge != null) Row(verticalAlignment = Alignment.CenterVertically) {
                    spec.badge?.let { Badge(it, roleColor(spec.base), Modifier.padding(end = 6.dp)) }
                    spec.line?.let { LastLine(it, c.muted, Modifier.weight(1f, fill = false), center = true) }
                }
            }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SideName(spec, 1, roles, turn == 1, type.titleSmall, Modifier.padding(end = 8.dp).weight(1f, fill = false), end = true)
                Mark(spec, 1, roles, 28.dp)
            }
        }
        VersusBar(spec, roles, figures = true, Modifier.padding(top = 4.dp))
    }
}

/** A narrow wide card (2×1): the score between the marks, and the names under them. No bar. */
@Composable
private fun VersusNarrow(spec: WidgetSpec.Versus, roles: List<Color>, bold: Boolean) {
    val type = MaterialTheme.typography
    val turn = VersusWords.turnIndex(spec)
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Mark(spec, 0, roles, 28.dp)
            Box(Modifier.weight(1f).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                Score(spec, type.headlineSmall, bold, 36f)
            }
            Mark(spec, 1, roles, 28.dp)
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
            SideName(spec, 0, roles, turn == 0, type.labelMedium, Modifier.weight(1f))
            SideName(spec, 1, roles, turn == 1, type.labelMedium, Modifier.weight(1f).padding(start = 8.dp), end = true)
        }
    }
}

/** The marks either side of the score, each over its name: the middle of a large card. */
@Composable
private fun Faces(
    spec: WidgetSpec.Versus,
    roles: List<Color>,
    bold: Boolean,
    scoreStyle: TextStyle,
    boldSize: Float,
    modifier: Modifier,
) {
    val turn = VersusWords.turnIndex(spec)
    val nameStyle = MaterialTheme.typography.labelLarge
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        for (i in 0..1) {
            if (i == 1) Box(Modifier.weight(1.3f), contentAlignment = Alignment.Center) {
                Score(spec, scoreStyle, bold, boldSize)
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Mark(spec, i, roles, 40.dp)
                SideName(spec, i, roles, turn == i, nameStyle, Modifier.padding(top = 4.dp), center = true)
            }
        }
    }
}

/**
 * Split: each side and the score on tiles of their own. Wide: three tiles in a row, the sides'
 * figures under their names and the bar under the score (or the last line, with no bar). Large:
 * the title, the badge, the marks and the score on the top tile; the bar and the last line on the
 * bottom one. Small: one tile.
 */
@Composable
private fun SplitVersus(spec: WidgetSpec.Versus, roles: List<Color>, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val c = lookColors(WidgetLook.SPLIT, roleColor(spec.base))
    val gap = SplitCorners.GAP
    val turn = VersusWords.turnIndex(spec)
    val bar = VersusWords.shares(spec) != null
    when (box) {
        WidgetSizes.Box.SMALL -> SplitWhole {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Score(spec, type.titleLarge, false, 0f)
                Marks(spec, roles, 20.dp, Modifier.padding(top = 4.dp))
            }
        }

        WidgetSizes.Box.WIDE -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            for (i in 0..1) {
                if (i == 1) Tile(SplitCorners.inRow(1, 3), c.tile, Modifier.weight(1.3f).fillMaxHeight()) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Score(spec, type.headlineSmall, false, 0f)
                        if (bar) VersusBar(spec, roles, figures = false, Modifier.padding(top = 4.dp))
                        else spec.line?.let { LastLine(it, c.muted, center = true) }
                    }
                }
                Tile(SplitCorners.inRow(if (i == 0) 0 else 2, 3), c.tile, Modifier.weight(1f).fillMaxHeight()) {
                    Column(
                        Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Mark(spec, i, roles, 26.dp)
                        SideName(spec, i, roles, turn == i, type.labelLarge, Modifier.padding(top = 2.dp), center = true)
                        if (bar) VersusWords.figure(spec.sides[i])?.let {
                            Text(it, style = figureStyle(), color = roles[i], maxLines = 1)
                        }
                    }
                }
            }
        }

        WidgetSizes.Box.LARGE -> {
            val foot = bar || spec.line != null
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
                Tile(SplitCorners.inColumn(0, if (foot) 2 else 1), c.tile, Modifier.weight(1f).fillMaxWidth()) {
                    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SmallTitle(LocalUntitled.current, c.content, Modifier.weight(1f, fill = false))
                            Spacer(Modifier.weight(1f))
                            spec.badge?.let { Badge(it, roleColor(spec.base), Modifier.padding(start = 8.dp)) }
                        }
                        Faces(spec, roles, false, type.displaySmall, 0f, Modifier.weight(1f).fillMaxWidth())
                    }
                }
                if (foot) Tile(SplitCorners.inColumn(1, 2), c.tile, Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                        VersusBar(spec, roles, figures = true)
                        spec.line?.let { LastLine(it, c.muted, Modifier.padding(top = if (bar) 6.dp else 0.dp)) }
                    }
                }
            }
        }
    }
}

/** The score: as a value ([BigText]) in [style], or with [bold] in Bold's type, [boldSize] at most, in the card's color. */
@Composable
private fun Score(spec: WidgetSpec.Versus, style: TextStyle, bold: Boolean, boldSize: Float) {
    if (bold) BoldText(spec.score, boldSize.sp, roleColor(spec.base))
    else BigText(spec.score, style)
}

/** A side's mark, [side] across: its letters in its color on a Material shape of the color's container. */
@Composable
private fun Mark(spec: WidgetSpec.Versus, index: Int, roles: List<Color>, side: Dp) {
    val s = spec.sides.getOrNull(index) ?: return
    val role = roles[index]
    // The letters are sized to the shape, not to the phone's font size: the shape does not grow with it.
    val size = with(LocalDensity.current) { (side * if (VersusWords.mark(s).length > 1) 0.36f else 0.46f).toSp() }
    ShapedBox(rankPolygon(index), roleContainer(role), side) {
        Text(
            VersusWords.mark(s),
            style = MaterialTheme.typography.titleMedium.copy(fontSize = size, lineHeight = size, fontWeight = FontWeight.Bold),
            color = role,
            maxLines = 1,
            softWrap = false
        )
    }
}

/** Both marks side by side, as a small card has them. */
@Composable
private fun Marks(spec: WidgetSpec.Versus, roles: List<Color>, side: Dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Mark(spec, 0, roles, side)
        Mark(spec, 1, roles, side)
    }
}

/**
 * A side's name in [style], with a dot in its color when it is that side's [turn]: after the name,
 * or before it at the card's [end]. With [center] the name is centered in its room.
 */
@Composable
private fun SideName(
    spec: WidgetSpec.Versus,
    index: Int,
    roles: List<Color>,
    turn: Boolean,
    style: TextStyle,
    modifier: Modifier = Modifier,
    end: Boolean = false,
    center: Boolean = false,
) {
    val s = spec.sides.getOrNull(index) ?: return
    Row(
        modifier,
        horizontalArrangement = when {
            center -> Arrangement.Center
            end -> Arrangement.End
            else -> Arrangement.Start
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (turn && end) TurnDot(roles[index], Modifier.padding(end = 6.dp))
        Text(
            s.name,
            style = style.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = if (end) TextAlign.End else TextAlign.Start,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (turn && !end) TurnDot(roles[index], Modifier.padding(start = 6.dp))
    }
}

/** The dot that marks the side whose turn it is. */
@Composable
private fun TurnDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(7.dp).background(color, CircleShape))
}

/** A small text at the top of the card ("67'", "LIVE"), in [role] on its container. */
@Composable
private fun Badge(text: String, role: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = role,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(roleContainer(role))
            .padding(horizontal = 8.dp, vertical = 1.dp)
    )
}

/** The last line ("17. b3 · Berlin"), with the styles the script gave it, in [muted] where it has none. */
@Composable
private fun LastLine(text: String, muted: Color, modifier: Modifier = Modifier, center: Boolean = false) {
    Text(
        cardText(text),
        style = MaterialTheme.typography.labelMedium,
        color = muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = modifier
    )
}

/** The type of a side's figure at the bar's end: digits of one width, so a clock that ticks does not shake. */
@Composable
private fun figureStyle(): TextStyle =
    MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

/** A part of the bar is never thinner than this share, so a side with almost nothing still shows. */
private const val LEAST_SHARE = 0.04f

/**
 * The bar between the sides ([VersusWords.shares]): one part for each, in its color, the first
 * from the start. With [figures], each side's figure is at its end. With `moving`, the part of the
 * side whose turn it is is a wave that moves ([LevelBar]). Nothing without a bar.
 */
@Composable
private fun VersusBar(spec: WidgetSpec.Versus, roles: List<Color>, figures: Boolean, modifier: Modifier = Modifier) {
    val shares = VersusWords.shares(spec) ?: return
    val turn = VersusWords.turnIndex(spec)
    val first by animateFloatAsState(
        shares.first.coerceIn(LEAST_SHARE, 1f - LEAST_SHARE),
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "versus"
    )
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        if (figures) VersusWords.figure(spec.sides[0])?.let {
            Text(it, style = figureStyle(), color = roles[0], maxLines = 1, modifier = Modifier.padding(end = 8.dp))
        }
        Row(Modifier.weight(1f).clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(first)) { LevelBar(1f, spec.moving && turn == 0, roles[0]) }
            Spacer(Modifier.width(4.dp))
            Box(Modifier.weight(1f - first)) { LevelBar(1f, spec.moving && turn == 1, roles[1]) }
        }
        if (figures) VersusWords.figure(spec.sides[1])?.let {
            Text(it, style = figureStyle(), color = roles[1], maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
    }
}
