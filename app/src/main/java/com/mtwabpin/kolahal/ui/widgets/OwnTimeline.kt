/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSource

/** The Reminders card's Next look of [t], as a card draws it in place of its list ([WidgetCard]'s `body`). */
internal fun timelineBody(t: OwnDesigns.Timeline): OwnBody = { box, _ -> TimelineBody(t, box) }

/** The column of the line and its points, and the room after it, in dp. */
private val LINE_COLUMN = 12.dp
private val LINE_GAP = 12.dp

/**
 * The Reminders card in the Next look ([com.mtwabpin.kolahal.widgets.WidgetLook.NEXT]): the next
 * reminder as a pill that empties as its time comes, with the time to it inside ("in 42 min"),
 * and the later ones as points on a line. The card is made again each minute ([OwnDesigns.timeline]),
 * so the pill moves by itself, on the theme's spring. The card has no taps inside: a tap on it runs
 * `reminders`, as in the other looks.
 * - 2×2 and larger: "Reminders", then the line: the pill at its first point with the reminder's
 *   text and time under it, then the later ones, each with its text and day, as many as fit.
 * - 4×1: the pill, and beside it the text over "19:30 · then Water plants, Wed".
 * - 2×1: the pill over the text and its time.
 * - 1×1: the pill alone.
 */
@Composable
private fun TimelineBody(t: OwnDesigns.Timeline, box: WidgetSizes.Box) {
    val type = MaterialTheme.typography
    val muted = lookColors().muted
    val content = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(ownRoom(box))
    ) {
        // Copied first: a Row or Column inside can't read them.
        val width = maxWidth
        val height = maxHeight
        when {
            box == WidgetSizes.Box.LARGE -> TimelineLine(t, height)
            box == WidgetSizes.Box.WIDE -> Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CountdownPill(t.next, min(height, 48.dp), type.titleMedium, Modifier.widthIn(min = 118.dp))
                Column(
                    Modifier
                        .padding(start = 16.dp)
                        .weight(1f)
                ) {
                    Text(
                        cardText(t.next.text),
                        style = type.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        cardText(OwnDesigns.pillLine(t)),
                        style = type.bodyMedium,
                        color = muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            width >= 150.dp -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                CountdownPill(t.next, 28.dp, type.labelLarge, Modifier.widthIn(min = 96.dp))
                Text(
                    cardText(t.next.text),
                    style = type.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    t.next.time,
                    style = type.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            else -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                // No room for "in": "42 min".
                CountdownPill(t.next, min(height, 36.dp), type.labelLarge, Modifier.fillMaxWidth(), short = true)
            }
        }
    }
}

/**
 * The large card: the title, then the line with the next reminder's pill and words at its first
 * point and the later ones at theirs, as many as fit in [height]. The points of the later ones
 * are quieter than the first.
 */
@Composable
private fun TimelineLine(t: OwnDesigns.Timeline, height: Dp) {
    val type = MaterialTheme.typography
    val muted = lookColors().muted
    val content = MaterialTheme.colorScheme.onSurface
    val role = cardRoleColor()
    val head = linesDp(type.titleSmall) + 10
    val nextDp = 28 + 4 + linesDp(type.titleSmall) + linesDp(type.bodySmall) + 8
    val laterDp = WidgetWords.textRowDp(28, linesDp(type.bodyMedium) + 8)
    val fit = ((height.value.toInt() - head - nextDp) / laterDp).coerceIn(0, t.later.size)
    val later = t.later.take(fit)
    Column(Modifier.fillMaxSize()) {
        Text(
            WidgetSource.builtinTitle(WidgetSource.REMINDERS).orEmpty(),
            style = type.titleSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        LinePoint(first = true, last = later.isEmpty(), dotY = 14.dp, dot = 12.dp, color = role) {
            Column(Modifier.padding(bottom = 8.dp)) {
                CountdownPill(t.next, 28.dp, type.labelLarge, Modifier.widthIn(min = 110.dp))
                Text(
                    cardText(t.next.text),
                    style = type.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = content,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    t.next.time,
                    style = type.bodySmall,
                    color = muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        later.forEachIndexed { i, r ->
            key(i) {
                val lineDp = linesDp(type.bodyMedium)
                LinePoint(
                    first = false,
                    last = i == later.lastIndex,
                    dotY = (lineDp / 2).dp,
                    dot = 8.dp,
                    color = muted
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Text(
                            cardText(r.text),
                            style = type.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            r.time,
                            style = type.labelMedium,
                            color = muted,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * One point of the line, with [content] after it. The point is a dot [dot] across in [color],
 * [dotY] from the top. The line runs through the points: from the one before ([first] false) and on
 * to the next ([last] false), in the quiet text color, faint.
 */
@Composable
private fun LinePoint(
    first: Boolean,
    last: Boolean,
    dotY: Dp,
    dot: Dp,
    color: Color,
    content: @Composable () -> Unit
) {
    val line = lookColors().muted.copy(alpha = 0.32f)
    Row(Modifier.height(IntrinsicSize.Min)) {
        Box(
            Modifier
                .width(LINE_COLUMN)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val y = dotY.toPx()
                    val stroke = 2.dp.toPx()
                    if (!first) drawLine(line, Offset(x, 0f), Offset(x, y), stroke, StrokeCap.Round)
                    if (!last) drawLine(line, Offset(x, y), Offset(x, size.height), stroke, StrokeCap.Round)
                    drawCircle(color, dot.toPx() / 2, Offset(x, y))
                }
        )
        Spacer(Modifier.width(LINE_GAP))
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * The next reminder's pill, [height] tall: the role's container, with the part of the last hour
 * that is left ([OwnDesigns.Due.left]) in a stronger tint from its start, and the time to it
 * ("in 42 min", or "42 min" with [short]) in the middle in [style]. The tint moves on the theme's spring when the minute
 * changes; while the card can't be seen it is simply there. The card says the time, so the pill
 * itself is said only as its words.
 */
@Composable
private fun CountdownPill(
    due: OwnDesigns.Due,
    height: Dp,
    style: TextStyle,
    modifier: Modifier = Modifier,
    short: Boolean = false
) {
    val role = cardRoleColor()
    val container = roleContainer(role)
    val strong = lerp(container, role, 0.3f)
    val left by animateFloatAsState(
        due.left,
        if (cardSeen()) MaterialTheme.motionScheme.defaultSpatialSpec() else snap(),
        label = "pill"
    )
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .drawBehind {
                val w = size.width * left.coerceIn(0f, 1f)
                drawRect(strong, Offset(if (rtl) size.width - w else 0f, 0f), Size(w, size.height))
            }
            .padding(horizontal = (height / 2).coerceAtMost(14.dp))
            .testTag("reminder-pill"),
        contentAlignment = Alignment.Center
    ) {
        BigText(
            if (short) due.until.removePrefix("in ") else due.until,
            style,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
