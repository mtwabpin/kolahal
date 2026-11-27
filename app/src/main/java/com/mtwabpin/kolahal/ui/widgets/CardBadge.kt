/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.layout.SubcomposeMeasureScope
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * A card's badge ("67'", "LIVE", "3"): a few characters in a pill, bold, in [role] on the role's
 * container. A versus card has always had one; every kind but buttons and switches can have it
 * ([com.mtwabpin.kolahal.widgets.WidgetBase.badge]). It is not an item for a screen reader: the
 * card's description says it after the card's name.
 */
@Composable
internal fun CardBadge(text: String, role: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMediumEmphasized,
        color = role,
        maxLines = 1,
        softWrap = false,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(roleContainer(role))
            .padding(horizontal = PILL_SIDE, vertical = PILL_TOP)
    )
}

/** The room around the badge's text inside its pill ([CardBadge]). */
private val PILL_SIDE = 8.dp
private val PILL_TOP = 1.dp

/** The parts a [BadgeLayout] composes: its content always, the badge only where it has room. */
private enum class BadgeSlot { CONTENT, BADGE }

/**
 * [content] and the card's [badge] ([CardBadge]) in one layout, placed by [policy]. The badge is
 * composed only when [policy] asks for it (`pills()`), after it found the room: a badge that gives
 * way is not there at all, so it is in no tree a screen reader or a test reads, not only out of
 * sight. [policy] decides by `pill`, the badge's size measured from its text, at the card's density
 * and font scale, as [CardBadge] draws it.
 */
@Composable
internal fun BadgeLayout(
    badge: String,
    role: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
    policy: SubcomposeMeasureScope.(
        content: List<Measurable>,
        pill: IntSize,
        constraints: Constraints,
        pills: () -> List<Placeable>,
    ) -> MeasureResult,
) {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.labelMediumEmphasized
    SubcomposeLayout(modifier) { constraints ->
        val inner = subcompose(BadgeSlot.CONTENT, content)
        val text = measurer.measure(
            badge, style, maxLines = 1, softWrap = false, density = this, layoutDirection = layoutDirection
        ).size
        val pill = IntSize(text.width + 2 * PILL_SIDE.roundToPx(), text.height + 2 * PILL_TOP.roundToPx())
        val pills = { subcompose(BadgeSlot.BADGE) { CardBadge(badge, role) }.map { it.measure(Constraints()) } }
        policy(inner, pill, constraints, pills)
    }
}

/** The room between a line's content and its badge ([BadgedLine]). */
internal val BADGE_GAP = 8.dp

/**
 * [content], one line of a card, with the card's [badge] at its end ([CardBadge]), in the layout's
 * flow: the badge never covers the content. The content is measured as it would be alone; when the
 * line can't hold it and the badge with [BADGE_GAP] between them, the badge goes and the content
 * keeps its room. Without a badge, [content] is drawn as it is, with nothing around it, so a card
 * without one draws as it always has.
 * - With [end], the badge is at the line's end, else right after the content.
 * - Both are centered on the line's height.
 */
@Composable
internal fun BadgedLine(
    badge: String?,
    role: Color,
    modifier: Modifier = Modifier,
    end: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (badge == null) {
        content()
        return
    }
    BadgeLayout(badge, role, modifier, content) { inner, pill, constraints, pills ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = BADGE_GAP.roundToPx()
        val content = inner.map { it.measure(loose) }
        val wide = content.maxOfOrNull { it.width } ?: 0
        val fits = wide + gap + pill.width <= constraints.maxWidth
        val badges = if (fits) pills() else emptyList()
        val badgeWidth = badges.maxOfOrNull { it.width } ?: pill.width
        val high = maxOf(content.maxOfOrNull { it.height } ?: 0, if (fits) badges.maxOfOrNull { it.height } ?: 0 else 0)
        val width = when {
            !fits -> wide
            end && constraints.hasBoundedWidth -> constraints.maxWidth
            else -> wide + gap + badgeWidth
        }.coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = high.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width, height) {
            content.forEach { it.placeRelative(0, (height - it.height) / 2) }
            if (fits) badges.forEach {
                val x = if (end) width - it.width else wide + gap
                it.placeRelative(x, (height - it.height) / 2)
            }
        }
    }
}

/**
 * Whether a badge of [badgeDp] fits after content [contentDp] wide on a line [lineDp] wide, with
 * [BADGE_GAP] between them: [BadgedLine]'s rule, for the layouts that place it by numbers. Pure.
 */
internal fun badgeFits(lineDp: Float, contentDp: Float, badgeDp: Float): Boolean =
    contentDp + BADGE_GAP.value + badgeDp <= lineDp
