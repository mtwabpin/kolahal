/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.modifiers.TextAutoSizeLayoutScope
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.ui.home.widgetText
import com.mtwabpin.kolahal.ui.theme.Term
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap

/** The color of a widget's badge, bar and chart: its role's color in this theme, or the accent. */
internal fun roleColor(base: WidgetBase?): Color =
    colorOf(base?.color ?: StyledText.ColorRole.ACCENT)

/** The thickness of a flat bar's line ([LevelBar]). */
private val BAR_LINE = 6.dp

/** The thickness of a wave's line ([LevelBar]): Material's own. */
private val WAVE_LINE = 4.dp

/** The length of one wave of a bar ([LevelBar]), as the mockup draws it. A moving wave moves one length a second. */
private val WAVELENGTH = 20.dp

/** A wave of no height: a bar or ring that stands still. A wave would say "working". */
internal val STILL: (Float) -> Float = { 0f }

/**
 * Draws the content of [spec] for the [box] it gets, inside a card ([WidgetCard]). It fills the
 * space it is given and draws no frame and no title.
 * - Each kind has a small, a wide and a large layout. A small one leaves parts out; the card's
 *   description for a screen reader still has them.
 * - [onTap] gets the action of a row, a button, a switch or an event. With null, nothing inside
 *   the content takes a tap (the Widgets screen).
 * - [stamp] is a number that changes when the content was filled again, also with the same
 *   description. A switch that waits for its script watches it. 0 for a card that has none.
 */
@Composable
internal fun KindBody(
    spec: WidgetSpec,
    box: WidgetSizes.Box,
    onTap: ((WidgetTap) -> Unit)?,
    stamp: Long = 0
) {
    when (spec) {
        is WidgetSpec.Stat -> StatBody(spec, box)
        is WidgetSpec.Text -> TextBody(spec, box)
        is WidgetSpec.Rows -> RowsBody(spec, box, onTap)
        is WidgetSpec.Progress -> ProgressBody(spec, box)
        is WidgetSpec.Chart -> ChartBody(spec, box)
        is WidgetSpec.Countdown -> CountdownBody(spec, box)
        is WidgetSpec.Buttons -> ButtonsBody(spec, onTap)
        is WidgetSpec.Toggles -> TogglesBody(spec, onTap, stamp)
        is WidgetSpec.Clock -> ClockBody(spec, box, onTap)
        is WidgetSpec.Agenda -> AgendaBody(spec, onTap)
        is WidgetSpec.Image -> ImageBody(spec, box)
        is WidgetSpec.Versus -> VersusBody(spec, box)
        is WidgetSpec.Sky -> SkyBody(spec, box)
        is WidgetSpec.Media -> MediaBody(spec, box, onTap)
    }
}

/**
 * [text] as a card draws it, with its styles ([widgetText]). Only a few fields may have styles
 * ([com.mtwabpin.kolahal.widgets.WidgetParser.STYLED]). It is made again only when the text or the
 * theme's colors change.
 */
@Composable
internal fun cardText(text: String): AnnotatedString =
    remember(text, Term.palette) { widgetText(text) }

/** One line in the middle of the body, for "Nothing to show" and the like. */
@Composable
internal fun QuietLine(text: String, small: Boolean = false) = SplitWhole {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = lookColors().muted,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The card's name ([LocalUntitled]), small, over content that doesn't say what it is: a large
 * list or text, or what a card says in place of its content. Nothing when the card gives no name.
 * With [LocalListOpen] a tap on it opens the whole list.
 */
@Composable
internal fun CardName(modifier: Modifier = Modifier) {
    val name = LocalUntitled.current ?: return
    val open = LocalListOpen.current
    Text(
        cardText(name),
        style = MaterialTheme.typography.labelMedium,
        color = lookColors().muted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.then(
            if (open == null) Modifier else Modifier
                .fillMaxWidth()
                .opensList()
        ),
    )
}

/**
 * What a tap on a list's "+3 more" line, or on its name over the rows ([CardName]), does: open the
 * whole list. Null, the default, leaves those to the card's own tap. A card that sets it takes no
 * tap of its own, so a tap that misses a row does nothing (the Todos card).
 */
internal val LocalListOpen = compositionLocalOf<(() -> Unit)?> { null }

/** For a screen reader, what a tap on "+3 more" or the list's name does ([LocalListOpen]). */
private const val LIST_OPEN_LABEL = "Open the list"

/**
 * With [LocalListOpen], a tap here opens the whole list: on "+3 more" and on the list's name, in
 * every look. Its touches reach 48 dp (Compose's minimum), more than such a line is tall.
 */
@Composable
internal fun Modifier.opensList(): Modifier {
    val open = LocalListOpen.current ?: return this
    return clickable(onClickLabel = LIST_OPEN_LABEL, onClick = open).testTag("list-open")
}

/**
 * A bar from 0 to 1 in [color]. It moves to a new value on the theme's spring.
 * - With [moving], and while the widgets page is in view, it is a wave that moves: something is
 *   in progress.
 * - With [wavy] it is a wave that stands still, as the mockup draws a countdown. Its wave speed is
 *   0, so it costs nothing per frame: the indicator moves its wave only with a speed above 0. Its
 *   height changes once, on the theme's spring, when the bar passes 10% or 95%.
 * - Any other bar is flat, also with a wave speed of 0. With any other speed the indicator asks
 *   for every frame, also while its wave has no height.
 * - A flat bar's line is 6 dp, not Material's 4: a card is seen from afar. A wave keeps Material's
 *   4 dp, so it has room to wave in the 10 dp container.
 * - It is not an item for a screen reader: the card's description says the value.
 */
@Composable
internal fun LevelBar(
    fraction: Float,
    moving: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    wavy: Boolean = false
) {
    val target = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(
        target,
        MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "level"
    )
    LevelBar({ shown }, moving, color, modifier, wavy)
}

/**
 * [LevelBar] at the level [level] gives while drawing, with no spring: a media slider's track,
 * which follows a finger. A level that moves draws the bar again and composes nothing.
 */
@Composable
internal fun LevelBar(
    level: () -> Float,
    moving: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    wavy: Boolean = false
) {
    val runs = moving && cardSeen()
    val waves = runs || wavy
    val line = if (moving || wavy) WAVE_LINE else BAR_LINE
    val stroke = with(LocalDensity.current) { Stroke(width = line.toPx(), cap = StrokeCap.Round) }
    LinearWavyProgressIndicator(
        progress = { level().let { if (it.isNaN()) 0f else it.coerceIn(0f, 1f) } },
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {},
        color = color,
        trackColor = color.copy(alpha = 0.24f),
        stroke = stroke,
        trackStroke = stroke,
        // No dot at the track's end: the bar's end says where it stops.
        stopSize = 0.dp,
        // Both lambdas are the same objects every time, so a new frame of the card does not throw the indicator's drawing away.
        amplitude = if (waves) WavyProgressIndicatorDefaults.indicatorAmplitude else STILL,
        wavelength = WAVELENGTH,
        waveSpeed = if (runs) WAVELENGTH else 0.dp,
    )
}

/** The big text of a kind, by the room it has. */
@Composable
internal fun valueStyle(box: WidgetSizes.Box) = when (box) {
    WidgetSizes.Box.SMALL -> MaterialTheme.typography.titleLarge
    WidgetSizes.Box.WIDE -> MaterialTheme.typography.headlineSmall
    WidgetSizes.Box.LARGE -> MaterialTheme.typography.headlineLarge
}

/**
 * [style] for a value: extra bold and a little tighter, with digits of one width. A number that
 * ticks does not shake, and a value stands out from the words around it.
 */
internal fun TextStyle.asValue(): TextStyle = copy(
    fontWeight = FontWeight.ExtraBold,
    letterSpacing = (-0.02).em,
    fontFeatureSettings = "tnum"
)

/**
 * A kind's big text, on one line, as a value ([asValue]). A text too long for its room gets
 * smaller, down to 12 sp, before it is cut with "…". A text whose digits alone changed (a countdown
 * that ticks) keeps its size ([SteadyAutoSize]). [onTextLayout] gets its layout ([RollingText]).
 */
@Composable
internal fun BigText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val size = remember(style) {
        val largest = style.fontSize.value.let { if (it.isNaN()) 22f else maxOf(it, 12f) }
        SteadyAutoSize(
            TextAutoSize.StepBased(
                minFontSize = 12.sp,
                maxFontSize = largest.sp,
                stepSize = 1.sp
            )
        )
    }
    val value = remember(style) { style.asValue() }
    Text(
        text,
        modifier = modifier,
        color = color,
        autoSize = size,
        style = value,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        onTextLayout = onTextLayout
    )
}

/**
 * Bold's big value, [size] at most, on one line in Bold's narrow black type ([boldValueStyle]). As
 * [BigText], it gets smaller to fit its room, down to 12 sp, and keeps its size while only its
 * digits change. [onTextLayout] gets its layout ([RollingText]).
 */
@Composable
internal fun BoldText(
    text: String,
    size: TextUnit,
    color: Color,
    modifier: Modifier = Modifier,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val auto = remember(size) {
        SteadyAutoSize(
            TextAutoSize.StepBased(
                minFontSize = 12.sp,
                maxFontSize = maxOf(size.value, 12f).sp,
                stepSize = 2.sp
            )
        )
    }
    val style = remember(size) { boldValueStyle(size) }
    // The line is as tall as the type, so a digit's top or bottom may stand out of it a little: not clipped.
    Text(
        text,
        modifier = modifier,
        color = color,
        autoSize = auto,
        style = style,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Visible,
        onTextLayout = onTextLayout
    )
}

/**
 * [inner]'s size for a text, kept while only the text's digits change. Fitting a text lays it out
 * several times. A countdown that ticks would be fitted again every second, for the same size:
 * a value's digits are all one width ([asValue] has "tnum").
 */
private class SteadyAutoSize(private val inner: TextAutoSize) : TextAutoSize {
    /** What the size was found for: the room, the density, the font scale, and the text with each digit as 0. */
    private var fitted: List<Any>? = null
    private var size: TextUnit = TextUnit.Unspecified

    override fun TextAutoSizeLayoutScope.getFontSize(
        constraints: Constraints,
        text: AnnotatedString
    ): TextUnit {
        val shape = String(CharArray(text.length) { if (text[it].isDigit()) '0' else text[it] })
        val key = listOf(constraints, density, fontScale, shape)
        if (key == fitted && size.isSpecified) return size
        return with(inner) { getFontSize(constraints, text) }.also {
            fitted = key
            size = it
        }
    }

    // One for each BigText: it keeps that text's size.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
