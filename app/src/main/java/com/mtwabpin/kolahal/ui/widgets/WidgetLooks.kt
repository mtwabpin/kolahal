/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import android.graphics.Typeface
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.graphics.shapes.RoundedPolygon
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec

/**
 * The look of the card being drawn ([WidgetLook]). [SourceWidget] provides the placed widget's own;
 * the card frame ([WidgetCard]) and every kind's body read it. Plain outside a card.
 */
val LocalWidgetLook = staticCompositionLocalOf { WidgetLook.PLAIN }

/**
 * True inside a card that is one Material shape: the Shape look in a square box ([squareBox]). The
 * card frame draws the shape; the body draws a few short lines centered in it.
 */
val LocalCardShaped = staticCompositionLocalOf { false }

/** True inside a Split tile, so what is drawn there adds no tile of its own ([SplitWhole]). */
val LocalInTile = staticCompositionLocalOf { false }

/**
 * [content] on one tile that fills the card, in the Split look outside any tile; else [content] as
 * it is. Split's card draws nothing itself, so "Nothing playing", a list with no rows or "Loading"
 * would otherwise sit straight on the page or the wallpaper, with no card around it.
 */
@Composable
internal fun SplitWhole(content: @Composable () -> Unit) {
    if (LocalWidgetLook.current != WidgetLook.SPLIT || LocalInTile.current) return content()
    // Split's card has no room around its body: the words keep some from the tile's edges.
    Box(
        Modifier
            .fillMaxSize()
            .clip(SplitCorners.inColumn(0, 1))
            .background(lookColors(WidgetLook.SPLIT).tile)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        CompositionLocalProvider(LocalInTile provides true) { content() }
    }
}

/** Whether a box is square enough for the card to be one shape: a 1×1 or a 2×2. */
fun squareBox(widthDp: Int, heightDp: Int): Boolean =
    heightDp > 0 && widthDp.toFloat() / heightDp in 0.75f..1.34f

/**
 * Whether the card frame leaves the title to the body: Bold, Split and Shape have no title line.
 * The frame then gives the title through [LocalUntitled], for the body to place where it fits.
 */
fun WidgetLook.untitled(): Boolean =
    this == WidgetLook.BOLD || this == WidgetLook.SPLIT || this == WidgetLook.SHAPE

/**
 * The colors a card draws with, for its look and its color `role` ([roleColor]).
 * - [card]: the card's surface. Transparent for Split, whose tiles each have [tile], and for Shape
 *   in a square box, where the shape is the card ([shapeFill]).
 * - [content]: text and icons on the card. [muted]: secondary text that keeps its contrast on [card].
 * - [tile]: the surface of Split's tiles; a row's or a chip's container on the other looks.
 * - [accent]: the role's own color: a ring, a bar, the Bold value. [onAccent]: text on it.
 * - [shapeFill]: the fill of Shape's shape: the role's container.
 */
@Immutable
data class LookColors(
    val card: Color,
    val content: Color,
    val muted: Color,
    val tile: Color,
    val accent: Color,
    val onAccent: Color,
    val shapeFill: Color,
)

/**
 * The container of a color [role]: the role over the page's surface, as the theme's own
 * primary container is built from its primary ([com.mtwabpin.kolahal.ui.theme.schemeFrom]).
 */
@Composable
fun roleContainer(role: Color): Color = lerp(MaterialTheme.colorScheme.surface, role, 0.30f)

/** The colors for [look] and the card's color [role]. */
@Composable
fun lookColors(
    look: WidgetLook = LocalWidgetLook.current,
    role: Color = MaterialTheme.colorScheme.primary
): LookColors {
    val scheme = MaterialTheme.colorScheme
    val container = roleContainer(role)
    return when (look) {
        WidgetLook.TONAL -> LookColors(
            card = container,
            content = scheme.onSurface,
            // onSurfaceVariant is too dim on a tinted card: the text color, a little lighter.
            muted = scheme.onSurface.copy(alpha = 0.78f),
            tile = scheme.onSurface.copy(alpha = 0.12f),
            accent = role,
            onAccent = scheme.surface,
            shapeFill = container,
        )

        WidgetLook.SPLIT -> LookColors(
            card = Color.Transparent,
            content = scheme.onSurface,
            muted = scheme.onSurfaceVariant,
            tile = scheme.surfaceContainerHigh,
            accent = role,
            onAccent = scheme.surface,
            shapeFill = container,
        )

        else -> LookColors(
            card = scheme.surfaceContainerHigh,
            content = scheme.onSurface,
            muted = scheme.onSurfaceVariant,
            tile = scheme.onSurface.copy(alpha = 0.06f),
            accent = role,
            onAccent = scheme.surface,
            shapeFill = container,
        )
    }
}

/** The colors of the parts of a clock's day strip ([WidgetWords.DayPart]). */
@Immutable
internal data class StripShades(val night: Color, val day: Color, val work: Color) {
    /** The color of [part]. */
    fun of(part: WidgetWords.DayPart): Color = when (part) {
        WidgetWords.DayPart.NIGHT -> night
        WidgetWords.DayPart.DAY -> day
        WidgetWords.DayPart.WORK -> work
    }
}

/**
 * The shades of a clock's day strips on a card of color [card], on a page of color [surface]:
 * tones of the card's [role] color that differ in lightness.
 * - Working hours are the role color itself.
 * - In a dark theme, night is the darkest: darker than the page, with a trace of the role.
 * - In a light theme, day is the lightest, lighter than the card, and night is the darker, tinted part.
 * - The part between is the card tinted with the role, as far in contrast from the part on each
 *   side. So the three are as far apart as they can be in any theme.
 */
internal fun stripShades(surface: Color, card: Color, role: Color): StripShades {
    return if (surface.luminance() < 0.5f) {
        val night = lerp(lerp(surface, role, 0.08f), Color.Black, 0.4f)
        StripShades(night = night, day = midway(night, role, card, role), work = role)
    } else {
        val day = lerp(surface, Color.White, 0.5f)
        StripShades(night = midway(day, role, card, role), day = day, work = role)
    }
}

/** [from] moved toward [toward] until its contrast with [a] is that with [b]. */
private fun midway(a: Color, b: Color, from: Color, toward: Color): Color {
    fun contrast(x: Color, y: Color): Float {
        val (hi, lo) = listOf(x.luminance(), y.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    var low = 0f
    var high = 1f
    repeat(16) {
        val t = (low + high) / 2
        val c = lerp(from, toward, t)
        if (contrast(a, c) < contrast(c, b)) low = t else high = t
    }
    return lerp(from, toward, (low + high) / 2)
}

/** Split's corners: 28 dp on the card's outside edges, 8 dp inside. */
object SplitCorners {
    val OUTER: Dp = 28.dp
    val INNER: Dp = 8.dp

    /** Space between two tiles. */
    val GAP: Dp = 4.dp

    /**
     * The shape of one tile, by which of the card's outside corners it touches: top start, top
     * end, bottom end, bottom start.
     */
    fun tile(topStart: Boolean, topEnd: Boolean, bottomEnd: Boolean, bottomStart: Boolean): Shape =
        RoundedCornerShape(
            topStart = if (topStart) OUTER else INNER,
            topEnd = if (topEnd) OUTER else INNER,
            bottomEnd = if (bottomEnd) OUTER else INNER,
            bottomStart = if (bottomStart) OUTER else INNER,
        )

    /** Tile [index] of [count] in a column that fills the card: the first takes the top corners, the last the bottom. */
    fun inColumn(index: Int, count: Int): Shape =
        tile(index == 0, index == 0, index == count - 1, index == count - 1)

    /** Tile [index] of [count] in a row that fills the card: the first takes the start corners, the last the end. */
    fun inRow(index: Int, count: Int): Shape =
        tile(index == 0, index == count - 1, index == count - 1, index == 0)

    /**
     * Tile [index] of a grid [columns] wide and [count] long that fills the card: only the four
     * corner tiles take an outside corner.
     */
    fun inGrid(index: Int, count: Int, columns: Int): Shape {
        val rows = (count + columns - 1) / columns
        val row = index / columns
        val col = index % columns
        val lastInRow = col == columns - 1 || index == count - 1
        return tile(
            row == 0 && col == 0,
            row == 0 && lastInRow,
            row == rows - 1 && lastInRow,
            row == rows - 1 && col == 0
        )
    }
}

/**
 * The Material shape of a card that is one shape: the Shape look in a square box ([squareBox]).
 * Null for a kind that keeps its card there, and for a progress with several bars.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun cardPolygon(spec: WidgetSpec?): RoundedPolygon? = when (spec) {
    is WidgetSpec.Stat -> MaterialShapes.Cookie12Sided
    is WidgetSpec.Countdown -> MaterialShapes.Sunny
    is WidgetSpec.Progress -> if (spec.items.isEmpty()) MaterialShapes.Clover4Leaf else null
    is WidgetSpec.Clock -> MaterialShapes.Cookie9Sided
    is WidgetSpec.Chart -> MaterialShapes.Cookie12Sided
    else -> null
}

/** The small shape of rank [index] in a list, in turn: a cookie, a clover, a sun. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun rankPolygon(index: Int): RoundedPolygon = when (index % 3) {
    0 -> MaterialShapes.Cookie9Sided
    1 -> MaterialShapes.Clover4Leaf
    else -> MaterialShapes.Sunny
}

/** Whether this phone has a narrow sans-serif for Bold's big values. Android returns its default type for a name it lacks. */
private val hasNarrow: Boolean by lazy {
    runCatching {
        Typeface.create(
            "sans-serif-condensed",
            Typeface.NORMAL
        ) !== Typeface.DEFAULT
    }.getOrDefault(false)
}

/** Android's own narrow sans-serif, the condensed Roboto, in the weights Bold uses. */
private val narrowFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Black),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.ExtraBold),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold),
)

/**
 * The type of Bold's big value, [size] at most: narrow and black with tight letters, as the mockup's
 * Roboto Flex at 45% width. Without a narrow type: ExtraBold, with letters a little tighter still.
 * Digits are all one width, so a number that ticks does not shake. The line is no taller than the
 * text, so a big number sits close to the words under it.
 */
fun boldValueStyle(size: TextUnit, weight: FontWeight = FontWeight.Black): TextStyle = TextStyle(
    fontFamily = if (hasNarrow) narrowFamily else FontFamily.Default,
    fontWeight = if (hasNarrow) weight else FontWeight.ExtraBold,
    fontSize = size,
    lineHeight = 1.em,
    letterSpacing = if (hasNarrow) (-0.02).em else (-0.04).em,
    fontFeatureSettings = "tnum",
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
)
