/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.cardBadge

/*
 * Where a list, a text and a progress card place their badge ([CardBadge]) when [BadgedLine] alone
 * doesn't fit the layout: a line whose content fills it, a block of lines, something centered, a
 * column that must not get wider. Each lays the badge out beside the content, never over it, and
 * leaves the badge out when the content would lose room. Without a badge each draws its content as
 * it is, with nothing around it, so a card without one draws as it always has.
 */

/**
 * Whether a card shows its badge in [box] and [look]: not in a small box (a 1×1), not in Poster,
 * whose value fills to the edges, and not in a card that is one shape ([oneShape]). Pure.
 */
internal fun badgeShown(box: WidgetSizes.Box, look: WidgetLook, oneShape: Boolean): Boolean =
    box != WidgetSizes.Box.SMALL && look != WidgetLook.POSTER && !oneShape

/** The badge [spec]'s body draws in [box] ([WidgetSpec.cardBadge]), or null where it is left out ([badgeShown]). */
@Composable
internal fun shownBadge(spec: WidgetSpec, box: WidgetSizes.Box): String? {
    val badge = spec.cardBadge ?: return null
    return badge.takeIf { badgeShown(box, LocalWidgetLook.current, LocalCardShaped.current) }
}

/**
 * Whether a badge of [badgeDp] fits at the end of a line [lineDp] wide that has content [contentDp]
 * wide in its middle, with [BADGE_GAP] between them: the content keeps its place. Pure.
 */
internal fun badgeBesideCentered(lineDp: Float, contentDp: Float, badgeDp: Float): Boolean =
    (lineDp - contentDp) / 2 >= BADGE_GAP.value + badgeDp

/**
 * [content], one line that may fill its width (a row with a label at its end, a title with "+3 more"),
 * with [badge] at the line's end. The badge comes only when the content's own width
 * (its max intrinsic width) still fits beside it ([badgeFits]); the content then gets the rest of
 * the line. Else the content keeps the whole line and the badge goes. The content must not hold a
 * layout that can't say its width ahead (BoxWithConstraints).
 */
@Composable
internal fun BadgedFill(
    badge: String?,
    role: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (badge == null) {
        content()
        return
    }
    BadgeLayout(badge, role, modifier, content) { inner, pill, constraints, pills ->
        val gap = BADGE_GAP.roundToPx()
        val badgeWidth = pill.width
        val wants = inner.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
        val fits = constraints.hasBoundedWidth &&
                badgeFits(constraints.maxWidth.toDp().value, wants.toDp().value, badgeWidth.toDp().value)
        val badges = if (fits) pills() else emptyList()
        val room = if (fits) (constraints.maxWidth - gap - badgeWidth).coerceAtLeast(0) else constraints.maxWidth
        val content = inner.map {
            it.measure(constraints.copy(minWidth = minOf(constraints.minWidth, room), maxWidth = room))
        }
        val high = maxOf(content.maxOfOrNull { it.height } ?: 0, if (fits) badges.maxOfOrNull { it.height } ?: 0 else 0)
        val width = (if (fits) constraints.maxWidth else content.maxOfOrNull { it.width } ?: 0)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = high.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            content.forEach { it.placeRelative(0, (height - it.height) / 2) }
            if (fits) badges.forEach { it.placeRelative(width - it.width, (height - it.height) / 2) }
        }
    }
}

/**
 * [content], a block of lines from the top (a quote), with [badge] at its top end. The badge comes
 * only when the whole content still fits its height in the width the badge leaves it (its max
 * intrinsic height there); the content then wraps in that width. Else the content keeps the whole
 * width and the badge goes. A text that fits itself to its room is measured at its largest size, so
 * the badge never makes it smaller. As [BadgedFill], no BoxWithConstraints in [content].
 */
@Composable
internal fun BadgedBlock(
    badge: String?,
    role: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (badge == null) {
        content()
        return
    }
    BadgeLayout(badge, role, modifier, content) { inner, pill, constraints, pills ->
        val gap = BADGE_GAP.roundToPx()
        val narrow = constraints.maxWidth - gap - pill.width
        val fits = constraints.hasBoundedWidth && narrow > 0 &&
                (!constraints.hasBoundedHeight || inner.all { it.maxIntrinsicHeight(narrow) <= constraints.maxHeight })
        val badges = if (fits) pills() else emptyList()
        val room = if (fits) narrow else constraints.maxWidth
        val content = inner.map {
            it.measure(constraints.copy(minWidth = minOf(constraints.minWidth, room), maxWidth = room))
        }
        val high = maxOf(content.maxOfOrNull { it.height } ?: 0, if (fits) badges.maxOfOrNull { it.height } ?: 0 else 0)
        val width = (if (fits) constraints.maxWidth else content.maxOfOrNull { it.width } ?: 0)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = high.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            content.forEach { it.placeRelative(0, 0) }
            if (fits) badges.forEach { it.placeRelative(width - it.width, 0) }
        }
    }
}

/**
 * [content] centered across the width it is given (a ring, a level's number), with [badge] at the
 * top end, where the content leaves the room on its end side ([badgeBesideCentered]). The content
 * is measured as it would be alone and keeps its place; else the badge goes.
 */
@Composable
internal fun CenteredBadged(
    badge: String?,
    role: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (badge == null) {
        content()
        return
    }
    BadgeLayout(badge, role, modifier, content) { inner, pill, constraints, pills ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val content = inner.map { it.measure(loose) }
        val wide = content.maxOfOrNull { it.width } ?: 0
        val width = (if (constraints.hasBoundedWidth) constraints.maxWidth else wide)
            .coerceIn(constraints.minWidth, constraints.maxWidth)
        val fits = badgeBesideCentered(width.toDp().value, wide.toDp().value, pill.width.toDp().value)
        val badges = if (fits) pills() else emptyList()
        val high = maxOf(content.maxOfOrNull { it.height } ?: 0, if (fits) badges.maxOfOrNull { it.height } ?: 0 else 0)
        val height = high.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            content.forEach { it.placeRelative((width - it.width) / 2, (height - it.height) / 2) }
            if (fits) badges.forEach { it.placeRelative(width - it.width, 0) }
        }
    }
}

/** The room between a badge on its own line and the content under it ([BadgeOver]). */
private val OVER_GAP = 2.dp

/**
 * [badge] on a line of its own over [content], at its end: the top of a column of words at a card's
 * end (Bold's progress in a 1-row card). The badge comes only when it is no wider than the content,
 * so the column never gets wider and what is beside it keeps its room, and when the height holds
 * both. Else the content is drawn alone.
 */
@Composable
internal fun BadgeOver(
    badge: String?,
    role: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (badge == null) {
        content()
        return
    }
    BadgeLayout(badge, role, modifier, content) { inner, pill, constraints, pills ->
        val content = inner.map { it.measure(constraints.copy(minHeight = 0)) }
        val wide = content.maxOfOrNull { it.width } ?: 0
        val tall = content.maxOfOrNull { it.height } ?: 0
        val over = pill.height + OVER_GAP.roundToPx()
        val fits = pill.width <= wide && (!constraints.hasBoundedHeight || tall + over <= constraints.maxHeight)
        val badges = if (fits) pills() else emptyList()
        val width = wide.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (tall + if (fits) over else 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            if (fits) badges.forEach { it.placeRelative(width - it.width, 0) }
            content.forEach { it.placeRelative(0, if (fits) over else 0) }
        }
    }
}
