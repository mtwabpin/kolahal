package com.mtwabpin.kolahal.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The words of a widget: what a card says, and what a screen reader hears. Pure, so it is unit
 * tested. Times are in seconds, as a script gives them.
 */
internal object WidgetWords {
    const val NOTHING = "Nothing to show"
    const val UNAVAILABLE = "widget unavailable"

    /** A number without a needless ".0": 9, 2.5, 3.14. At most two decimals. */
    fun num(v: Float): String {
        if (v.isNaN() || v.isInfinite()) return "–"
        val r = Math.round(v * 100.0) / 100.0
        return if (r == floor(r) && abs(r) < 1e15) r.toLong().toString() else r.toString()
    }

    /** A value with its unit: "24°C", "68%", but "12 km". A unit that starts with a letter gets a space. */
    fun joined(value: String, unit: String?): String = when {
        unit.isNullOrEmpty() -> value
        unit.first().isLetter() -> "$value $unit"
        else -> value + unit
    }

    /** How full a bar or ring is, from 0 to 1. A scale of nothing is empty. */
    fun fraction(value: Float, max: Float): Float =
        if (value.isNaN() || max.isNaN() || max <= 0f) 0f else (value / max).coerceIn(0f, 1f)

    fun percent(value: Float, max: Float): String = "${(fraction(value, max) * 100).roundToInt()}%"

    /** The arrow beside a change. An unknown trend has none. */
    fun arrow(trend: String?): String? = when (trend) {
        "up" -> "↑"
        "down" -> "↓"
        "flat" -> "→"
        else -> null
    }

    /** The same for a screen reader, which may not say an arrow. */
    fun trendWord(trend: String?): String? = when (trend) {
        "up" -> "up"
        "down" -> "down"
        "flat" -> "no change"
        else -> null
    }

    /** A chart in words, for a screen reader: "7 values, from 3 to 12, the latest 9". */
    fun chartSummary(points: List<Float>): String = when (points.size) {
        0 -> "No values"
        1 -> "1 value: ${num(points[0])}"
        else -> "${points.size} values, from ${num(points.min())} to ${num(points.max())}, the latest ${num(points.last())}"
    }

    /** A countdown's text. [ticks] is true when the text shows seconds, so it must be drawn again every second. */
    data class Count(val text: String, val ticks: Boolean)

    private const val HOUR = 3600L
    private const val DAY = 86_400L

    /** A length of time by its largest unit: "12 days", "1 hour", "5 min". */
    fun span(seconds: Long): String = when {
        seconds >= DAY -> (seconds / DAY).let { if (it == 1L) "1 day" else "$it days" }
        seconds >= HOUR -> (seconds / HOUR).let { if (it == 1L) "1 hour" else "$it hours" }
        seconds >= 60 -> "${seconds / 60} min"
        else -> "under a minute"
    }

    /** A length of time as a clock: "03:25:10". Past two days, the days come first: "12d 03:25:10". */
    fun clock(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val days = if (s > 2 * DAY) s / DAY else 0
        val rest = s - days * DAY
        val time = String.format(Locale.ROOT, "%02d:%02d:%02d", rest / HOUR, rest % HOUR / 60, rest % 60)
        return if (days > 0) "${days}d $time" else time
    }

    /**
     * What a countdown to [to] says at [now].
     * - Before the moment: "12 days" ([style] "days"), or "03:25:10" ([style] "clock"). With no
     *   style, it counts days while the moment is more than 48 hours away, and is a clock after that.
     * - On the day of the moment, once it has passed: [reached], or "today".
     * - On later days: the time since, as "3 days ago".
     */
    fun countdown(to: Long, now: Long, style: String?, reached: String?, zone: ZoneId): Count {
        val left = to - now
        if (left > 0) {
            val clock = style == "clock" || (style != "days" && left <= 48 * HOUR)
            return if (clock) Count(clock(left), ticks = true) else Count(span(left), ticks = false)
        }
        fun day(t: Long) = Instant.ofEpochSecond(t).atZone(zone).toLocalDate()
        return if (day(to) == day(now)) Count(reached ?: "today", ticks = false) else Count(span(-left) + " ago", ticks = false)
    }

    /** How far a countdown that started at [from] has come, from 0 to 1. Null without a start, or with one after the moment. */
    fun countdownFraction(from: Long?, to: Long, now: Long): Float? =
        if (from == null || to <= from) null else ((now - from).toFloat() / (to - from)).coerceIn(0f, 1f)

    /** The main text of [spec]: what its card must say even at its smallest. The plain card draws only this. */
    fun main(spec: WidgetSpec, now: Long, zone: ZoneId): String = when (spec) {
        is WidgetSpec.Stat -> joined(spec.value, spec.unit)
        is WidgetSpec.Text -> spec.text
        is WidgetSpec.Rows -> spec.items.firstOrNull()?.title ?: spec.empty ?: NOTHING
        is WidgetSpec.Progress -> spec.items.firstOrNull()?.let { it.text ?: percent(it.value, it.max) } ?: spec.text ?: percent(spec.value, spec.max)
        is WidgetSpec.Chart -> spec.value ?: spec.points.lastOrNull()?.let(::num) ?: NOTHING
        is WidgetSpec.Countdown -> countdown(spec.to, now, spec.style, spec.reached, zone).text
    }

    /** The smaller text that goes with [main], if the description has one. */
    fun label(spec: WidgetSpec): String? = when (spec) {
        is WidgetSpec.Stat -> spec.label
        is WidgetSpec.Text -> spec.caption
        is WidgetSpec.Rows -> null
        is WidgetSpec.Progress -> spec.label
        is WidgetSpec.Chart -> spec.label
        is WidgetSpec.Countdown -> spec.label
    }

    /**
     * The whole card in one sentence, for a screen reader: "Weather: 24°C, Sunny, 68% humidity".
     * It has everything in the description, also what a small card leaves out. A countdown is said
     * in words ("5 hours"), never as a running clock.
     */
    fun description(title: String?, spec: WidgetSpec, now: Long, zone: ZoneId): String {
        fun words(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(" ")
        val parts = buildList<String?> {
            when (spec) {
                is WidgetSpec.Stat -> {
                    add(joined(spec.value, spec.unit))
                    add(spec.label)
                    add(words(trendWord(spec.trend), spec.delta))
                    spec.more.forEach { add(words(it.value, it.label)) }
                    add(spec.progress?.let { percent(it, 1f) })
                }
                is WidgetSpec.Text -> {
                    add(spec.text)
                    add(spec.caption)
                }
                is WidgetSpec.Rows -> {
                    if (spec.items.isEmpty()) add(spec.empty ?: NOTHING)
                    spec.items.forEach { r -> add(listOfNotNull(r.title, r.detail, r.trailing).joinToString(", ")) }
                }
                is WidgetSpec.Progress -> {
                    if (spec.items.isEmpty()) add(spec.text ?: percent(spec.value, spec.max))
                    spec.items.forEach { b -> add(words(b.label, b.text ?: percent(b.value, b.max))) }
                    add(spec.label)
                }
                is WidgetSpec.Chart -> {
                    add(spec.value)
                    add(spec.label)
                    add(spec.summary ?: chartSummary(spec.points))
                }
                is WidgetSpec.Countdown -> {
                    add(countdown(spec.to, now, "days", spec.reached, zone).text)
                    add(spec.label)
                }
            }
        }
        val said = parts.filterNot { it.isNullOrBlank() }.joinToString(", ")
        return if (title.isNullOrBlank()) said else "$title: $said"
    }

    /**
     * How many of [count] rows to draw in [heightDp], each [rowDp] tall. When rows are left out,
     * [moreDp] is kept free for the "+3 more" line, as long as one row still fits above it.
     */
    fun rowsShown(count: Int, heightDp: Int, rowDp: Int, moreDp: Int): Int {
        if (count <= 0 || rowDp <= 0) return 0
        if (count * rowDp <= heightDp) return count
        val aboveMore = (heightDp - moreDp) / rowDp
        return if (aboveMore >= 1) aboveMore else (heightDp / rowDp).coerceIn(1, count)
    }

    /** Whether the "+3 more" line fits under [shown] rows. */
    fun showsMore(count: Int, shown: Int, heightDp: Int, rowDp: Int, moreDp: Int): Boolean =
        shown < count && shown * rowDp + moreDp <= heightDp
}

/** The colour of a widget's badge, bar and chart: its role's colour in this theme, or the accent. */
internal fun roleColour(base: WidgetBase?): Color = colourOf(base?.color ?: StyledText.Colour.ACCENT)

/** Height of the "+3 more" line under a list, in dp. */
private const val MORE_DP = 20

/** A wave of no height: a bar or ring that stands still. A wave would say "working". */
private val STILL: (Float) -> Float = { 0f }

/**
 * Draws the content of [spec] for the [box] it gets, inside a card ([WidgetCard]). It fills the
 * space it is given and draws no frame and no title.
 * - Each kind has a small, a wide and a large layout. A small one leaves parts out; the card's
 *   description for a screen reader still has them.
 * - [onTap] gets a row's own action. With null, rows take no taps (the Widgets screen).
 */
@Composable
internal fun KindBody(spec: WidgetSpec, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    when (spec) {
        is WidgetSpec.Stat -> StatBody(spec, box)
        is WidgetSpec.Text -> TextBody(spec, box)
        is WidgetSpec.Rows -> RowsBody(spec, onTap)
        is WidgetSpec.Progress -> ProgressBody(spec, box)
        is WidgetSpec.Countdown -> CountdownBody(spec, box)
        else -> PlainBody(spec, box)
    }
}

/** One line in the middle of the body, for "Nothing to show" and the like. */
@Composable
internal fun QuietLine(text: String, small: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * A bar from 0 to 1 in [colour]. It moves to a new value on the theme's spring. With [moving], and
 * while the widgets page is in view, it is a wave: something is in progress.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun LevelBar(fraction: Float, moving: Boolean, colour: Color, modifier: Modifier = Modifier) {
    val target = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    val shown by animateFloatAsState(target, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "level")
    LinearWavyProgressIndicator(
        progress = { shown.coerceIn(0f, 1f) },
        modifier = modifier.fillMaxWidth(),
        color = colour,
        trackColor = colour.copy(alpha = 0.24f),
        amplitude = if (moving && LocalWidgetsShown.current) WavyProgressIndicatorDefaults.indicatorAmplitude else STILL,
    )
}

/** The big text of a kind, by the room it has. */
@Composable
private fun valueStyle(box: WidgetSizes.Box) = when (box) {
    WidgetSizes.Box.SMALL -> MaterialTheme.typography.titleLarge
    WidgetSizes.Box.WIDE -> MaterialTheme.typography.headlineSmall
    WidgetSizes.Box.LARGE -> MaterialTheme.typography.headlineLarge
}

/** A kind's big text, on one line. A text too long for its room gets smaller, down to 12 sp, before it is cut with "…". */
@Composable
private fun BigText(text: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface) {
    val size = remember(style.fontSize) {
        val largest = style.fontSize.value.let { if (it.isNaN()) 22f else maxOf(it, 12f) }
        TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = largest.sp, stepSize = 1.sp)
    }
    Text(text, modifier = modifier, color = color, autoSize = size, style = style, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
}

/**
 * `stat`: one big value with its label.
 * - Small: the value and the label, centred.
 * - Wide: the change beside the value, and as many of the smaller values as fit beside them. A bar
 *   takes the label's place: one row has no room for both.
 * - Large: everything, with the smaller values in a row below.
 */
@Composable
private fun StatBody(spec: WidgetSpec.Stat, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    if (box == WidgetSizes.Box.SMALL) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            BigText(WidgetWords.joined(spec.value, spec.unit), valueStyle(box))
            spec.label?.let { Text(it, style = type.labelSmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        return
    }
    val colour = roleColour(spec.base)
    val large = box == WidgetSizes.Box.LARGE
    val change = listOfNotNull(WidgetWords.arrow(spec.trend), spec.delta).joinToString(" ")
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // A smaller value beside the big one needs about 64 dp, after 112 dp for the big one.
        val beside = if (large) 0 else ((maxWidth - 112.dp) / 64.dp).toInt().coerceIn(0, spec.more.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row {
                        BigText(spec.value, valueStyle(box), Modifier.weight(1f, fill = false).alignByBaseline())
                        spec.unit?.let {
                            Text(it, style = type.titleMedium, color = scheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(start = 2.dp).alignByBaseline())
                        }
                        if (change.isNotEmpty()) {
                            Text(change, style = type.labelLarge, color = colour, maxLines = 1, modifier = Modifier.padding(start = 8.dp).alignByBaseline())
                        }
                    }
                    if (large || spec.progress == null) spec.label?.let {
                        Text(it, style = if (large) type.bodyMedium else type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                spec.more.take(beside).forEach { SmallValue(it, Modifier.padding(start = 12.dp).widthIn(max = 72.dp)) }
            }
            if (large && spec.more.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    spec.more.forEach { SmallValue(it, Modifier.weight(1f, fill = false)) }
                }
            }
            spec.progress?.let { LevelBar(it, spec.moving, colour, Modifier.padding(top = if (large) 8.dp else 4.dp)) }
        }
    }
}

/** One of a stat's smaller values, over its label. */
@Composable
private fun SmallValue(small: WidgetSpec.Small, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(small.value, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
        small.label?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** `text`: a few lines, cut with "…" where the box ends, and a caption under them. A small box has no caption. */
@Composable
private fun TextBody(spec: WidgetSpec.Text, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val style = when (box) {
        WidgetSizes.Box.SMALL -> type.bodySmall
        WidgetSizes.Box.WIDE -> type.bodyMedium
        WidgetSizes.Box.LARGE -> type.bodyLarge
    }
    Column(Modifier.fillMaxSize()) {
        // The text gets the height that is left, and its last line that fits ends in "…".
        Text(spec.text, style = style, color = MaterialTheme.colorScheme.onSurface, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).fillMaxWidth())
        if (box != WidgetSizes.Box.SMALL) spec.caption?.let {
            Text(it, style = type.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/**
 * `list`: as many rows as fit, then "+3 more".
 * - A row that can be tapped is 48 dp tall, and so is a row with a second line. Other rows are 32 dp.
 * - A narrow list (under 200 dp) leaves the second lines out.
 */
@Composable
private fun RowsBody(spec: WidgetSpec.Rows, onTap: ((WidgetTap) -> Unit)?) {
    if (spec.items.isEmpty()) {
        QuietLine(spec.empty ?: WidgetWords.NOTHING)
        return
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val details = maxWidth >= 200.dp && spec.items.any { it.detail != null }
        val rowDp = if (details || spec.items.any { it.tap != null }) 48 else 32
        val heightDp = maxHeight.value.toInt()
        val shown = WidgetWords.rowsShown(spec.items.size, heightDp, rowDp, MORE_DP)
        val more = spec.items.size - shown
        Column(Modifier.fillMaxSize()) {
            spec.items.take(shown).forEachIndexed { i, row ->
                key(i) { ListRow(row, rowDp.dp, details, onTap) }
            }
            if (WidgetWords.showsMore(spec.items.size, shown, heightDp, rowDp, MORE_DP)) {
                Text(
                    "+$more more",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.height(MORE_DP.dp).wrapContentHeight(),
                )
            }
        }
    }
}

/** One row of a list. Its colour is on its icon and on the text at its end, or on a dot when it has neither. */
@Composable
private fun ListRow(row: WidgetSpec.Row, height: Dp, details: Boolean, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    val tap = row.tap
    val colour = row.color?.let { colourOf(it) }
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .then(if (tap != null && onTap != null) Modifier.clickable(onClickLabel = tap.label, role = Role.Button) { onTap(tap) } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = row.icon
        if (icon != null) {
            Text(icon, style = type.titleMedium, color = colour ?: scheme.onSurface, maxLines = 1, modifier = Modifier.padding(end = 8.dp))
        } else if (colour != null && row.trailing == null) {
            Box(Modifier.padding(end = 8.dp).size(8.dp).clip(CircleShape).background(colour))
        }
        Column(Modifier.weight(1f)) {
            Text(row.title, style = type.bodyMedium, color = scheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (details) row.detail?.let {
                Text(it, style = type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        row.trailing?.let {
            Text(it, style = type.labelLarge, color = colour ?: scheme.onSurfaceVariant, maxLines = 1, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

/**
 * `progress`: how far along something is.
 * - A ring with its text in the middle, or a bar. With no `style`, a wide box gets the bar and the
 *   others get the ring.
 * - With `items`, it is several bars, each with its label and its text while there is room.
 * - With `moving`, the ring and the bars are waves while the page is in view.
 */
@Composable
private fun ProgressBody(spec: WidgetSpec.Progress, box: WidgetSizes.Box) {
    val colour = roleColour(spec.base)
    when {
        spec.items.isNotEmpty() -> BarsBody(spec, colour)
        spec.style == "ring" || (spec.style != "bar" && box != WidgetSizes.Box.WIDE) -> RingBody(spec, box, colour)
        else -> BarBody(spec, box, colour)
    }
}

/** A ring from 0 to 1 in [colour], [side] across. It moves to a new value on the theme's spring. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Ring(fraction: Float, moving: Boolean, colour: Color, side: Dp) {
    val shown by animateFloatAsState(fraction, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "ring")
    // A bigger ring gets a thicker line.
    val stroke = with(LocalDensity.current) { Stroke(width = (side / 12).coerceIn(4.dp, 10.dp).toPx(), cap = StrokeCap.Round) }
    CircularWavyProgressIndicator(
        progress = { shown.coerceIn(0f, 1f) },
        modifier = Modifier.size(side),
        color = colour,
        trackColor = colour.copy(alpha = 0.24f),
        stroke = stroke,
        trackStroke = stroke,
        amplitude = if (moving && LocalWidgetsShown.current) WavyProgressIndicatorDefaults.indicatorAmplitude else STILL,
    )
}

/**
 * The ring.
 * - Small and large: as big as the box allows, with its text inside. Large has the label under it.
 * - Wide: the ring at the start, with its text and its label beside it.
 */
@Composable
private fun RingBody(spec: WidgetSpec.Progress, box: WidgetSizes.Box, colour: Color) {
    val type = MaterialTheme.typography
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val fraction = WidgetWords.fraction(spec.value, spec.max)
    val text = spec.text ?: WidgetWords.percent(spec.value, spec.max)
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (box == WidgetSizes.Box.WIDE) {
            val side = min(maxHeight, maxWidth / 2).coerceAtLeast(24.dp)
            Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Ring(fraction, spec.moving, colour, side)
                Column(Modifier.padding(start = 12.dp).weight(1f)) {
                    BigText(text, type.titleLarge)
                    spec.label?.let { Text(it, style = type.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        } else {
            val label = spec.label.takeIf { box == WidgetSizes.Box.LARGE }
            val labelRoom = if (label != null) 24.dp else 0.dp
            val side = min(maxWidth, maxHeight - labelRoom).coerceAtLeast(24.dp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(side), contentAlignment = Alignment.Center) {
                    Ring(fraction, spec.moving, colour, side)
                    // The text stays clear of the ring's line.
                    BigText(text, if (side >= 96.dp) type.titleLarge else type.titleSmall, Modifier.padding(horizontal = side / 6))
                }
                if (label != null) {
                    Text(label, style = type.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.height(labelRoom).wrapContentHeight())
                }
            }
        }
    }
}

/** One bar under its text. The label is beside the text. */
@Composable
private fun BarBody(spec: WidgetSpec.Progress, box: WidgetSizes.Box, colour: Color) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Row {
            BigText(spec.text ?: WidgetWords.percent(spec.value, spec.max), valueStyle(box), Modifier.weight(1f, fill = false).alignByBaseline())
            if (box != WidgetSizes.Box.SMALL) spec.label?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp).widthIn(max = 96.dp).alignByBaseline(),
                )
            }
        }
        LevelBar(WidgetWords.fraction(spec.value, spec.max), spec.moving, colour, Modifier.padding(top = 4.dp))
    }
}

/** A bar with its label and its text over it needs this much height, in dp. */
private const val NAMED_BAR_DP = 30

/** A bar alone needs this much. */
private const val BAR_DP = 14

/**
 * Several bars. Each has its label and its text over it when the box is tall enough for all of
 * them. A lower box draws the bars alone, as many as fit. A bar's own colour wins over the card's.
 */
@Composable
private fun BarsBody(spec: WidgetSpec.Progress, colour: Color) {
    val type = MaterialTheme.typography
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val named = maxHeight >= NAMED_BAR_DP.dp * spec.items.size
        val count = if (named) spec.items.size else (maxHeight / BAR_DP.dp).toInt().coerceIn(1, spec.items.size)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
            spec.items.take(count).forEachIndexed { i, bar ->
                key(i) {
                    Column {
                        if (named) Row {
                            Text(bar.label.orEmpty(), style = type.bodySmall, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(bar.text ?: WidgetWords.percent(bar.value, bar.max), style = type.labelMedium, color = scheme.onSurface, maxLines = 1)
                        }
                        LevelBar(WidgetWords.fraction(bar.value, bar.max), spec.moving, bar.color?.let { colourOf(it) } ?: colour)
                    }
                }
            }
        }
    }
}

/**
 * `countdown`: the time to a moment, or since it. Kolahal does the ticking: the script gave the
 * moment once, and no run is needed as time passes.
 * - The time moves by the minute, with the header's clock ([rememberNow]).
 * - It moves by the second only while the text shows seconds and the page is in view.
 * - With a start (`from`), a bar runs from the start to the moment, as a wave until the moment
 *   comes. In a wide box the bar takes the label's place. A small box has no bar.
 */
@Composable
private fun CountdownBody(spec: WidgetSpec.Countdown, box: WidgetSizes.Box) {
    val (minute, zoneChanges) = rememberNow()
    val zone = remember(zoneChanges) { ZoneId.systemDefault() }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    // The minute's tick. It also comes when home is back on screen, and when the clock is set.
    LaunchedEffect(minute) { now = System.currentTimeMillis() / 1000 }
    val count = WidgetWords.countdown(spec.to, now, spec.style, spec.reached, zone)
    if (count.ticks && LocalWidgetsShown.current) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        LaunchedEffect(lifecycle) {
            // Only while home is on screen.
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    val ms = System.currentTimeMillis()
                    now = ms / 1000
                    // Wake when the next second starts.
                    delay(1000 - ms % 1000)
                }
            }
        }
    }
    val small = box == WidgetSizes.Box.SMALL
    val bar = if (small) null else WidgetWords.countdownFraction(spec.from, spec.to, now)
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (small) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        BigText(count.text, valueStyle(box))
        if (bar == null || box == WidgetSizes.Box.LARGE) spec.label?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (bar != null) LevelBar(bar, moving = now < spec.to, roleColour(spec.base), Modifier.padding(top = 4.dp))
    }
}

/** A kind with no drawing of its own yet: its main text, and its label under it. */
@Composable
private fun PlainBody(spec: WidgetSpec, box: WidgetSizes.Box) {
    // The minute's tick, for a countdown.
    val (now, _) = rememberNow()
    val main = WidgetWords.main(spec, now.time / 1000, ZoneId.systemDefault())
    val small = box == WidgetSizes.Box.SMALL
    Column(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = if (small) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(main, style = valueStyle(box), color = MaterialTheme.colorScheme.onSurface, maxLines = if (box == WidgetSizes.Box.LARGE) 3 else 1, overflow = TextOverflow.Ellipsis)
        WidgetWords.label(spec)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
