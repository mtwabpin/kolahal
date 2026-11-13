package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap

/**
 * True while the widgets page is the page in view: home's pager has settled on it. Home keeps the
 * page composed beside the log, so "composed" does not mean "seen". Things that move (the loading
 * shape, a wave, a countdown's seconds) and refresh requests run only while this is true.
 */
val LocalWidgetsShown = compositionLocalOf { false }

/** What a card shows when it has no content to draw. */
internal enum class CardNote {
    /** The stored state is not read yet. The card stays empty for a moment. */
    WAITING,

    /** Nothing is stored yet, and a run is on its way. */
    LOADING,

    /** The script showed nothing, or cleared its widget. */
    NOTHING,

    /** The stored content can't be read, or nothing can fill this widget. */
    UNAVAILABLE,
}

/**
 * The card of a script widget, [height] tall and as wide as it is given. Call it inside [GuiTheme].
 * - It has its own opaque surface, so its text reads over any wallpaper.
 * - It draws [spec] for the box it gets ([WidgetSizes.box]): large has the title in a row on top,
 *   wide has the badge at its start, small has the content only.
 * - With a [note], it shows that and not the content.
 * - [description] is the whole card for a screen reader, in one item. Rows with their own tap are
 *   items of their own inside it.
 * - [onClick] is a tap on the card and [onLongClick] a long-press. [onTap] gets a row's own action.
 *   With all three null the card takes no touches (the Widgets screen).
 */
@Composable
internal fun WidgetCard(
    title: String,
    height: Dp,
    spec: WidgetSpec?,
    note: CardNote?,
    description: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onTap: ((WidgetTap) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val box = WidgetSizes.box(maxWidth.value.toInt(), maxHeight.value.toInt())
        // The tap and the description are on one node, so a screen reader finds one item.
        val press = if (onClick != null) {
            Modifier.combinedClickable(
                onClickLabel = onClickLabel,
                onLongClickLabel = if (onLongClick != null) "arrange widgets" else null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
        } else {
            Modifier.semantics(mergeDescendants = true) {}
        }
        Surface(
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            shape = RoundedCornerShape(if (box == WidgetSizes.Box.SMALL) 24.dp else 28.dp),
            modifier = Modifier.fillMaxSize().testTag("widget-card"),
        ) {
            Box(Modifier.fillMaxSize().then(press).semantics { contentDescription = description }) {
                CardLayout(title, spec, note, box, onTap)
            }
        }
    }
}

/** The title, the badge and the content, placed for [box]. */
@Composable
private fun CardLayout(title: String, spec: WidgetSpec?, note: CardNote?, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val type = MaterialTheme.typography
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val icon = spec?.base?.icon
    val colour = roleColour(spec?.base)
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconBadge(icon, colour, badgeShape(spec), 32.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = type.titleSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { CardBody(spec, note, box, onTap) }
        }
        WidgetSizes.Box.WIDE -> Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                IconBadge(icon, colour, badgeShape(spec), 40.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(title, style = type.labelMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).fillMaxWidth()) { CardBody(spec, note, box, onTap) }
            }
        }
        // No room for a title: the content alone. A screen reader still hears the title.
        WidgetSizes.Box.SMALL -> Box(Modifier.fillMaxSize().padding(8.dp)) { CardBody(spec, note, box, onTap) }
    }
}

/** The content, or what the card says without any. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardBody(spec: WidgetSpec?, note: CardNote?, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val small = box == WidgetSizes.Box.SMALL
    when {
        note == CardNote.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // The shape changes form for as long as it is drawn. So it is drawn only while the page is in view.
            if (LocalWidgetsShown.current) LoadingIndicator(Modifier.size(if (box == WidgetSizes.Box.LARGE) 48.dp else 36.dp))
        }
        note == CardNote.NOTHING -> QuietLine(WidgetWords.NOTHING, small)
        note == CardNote.UNAVAILABLE -> QuietLine(WidgetWords.UNAVAILABLE, small)
        note == null && spec != null -> KindBody(spec, box, onTap)
    }
}

/** The widget's icon (an emoji or a letter) on a shape in its role's colour. */
@Composable
private fun IconBadge(icon: String, colour: Color, shape: Shape, size: Dp) {
    Box(Modifier.size(size).clip(shape).background(colour.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        Text(
            icon,
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            color = colour,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** One shape for each kind: a cookie for a number, a pill for a list, and so on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun badgeShape(spec: WidgetSpec?): Shape = when (spec) {
    is WidgetSpec.Stat -> MaterialShapes.Cookie9Sided
    is WidgetSpec.Text -> MaterialShapes.Square
    is WidgetSpec.Rows -> MaterialShapes.Pill
    is WidgetSpec.Progress -> MaterialShapes.Circle
    is WidgetSpec.Chart -> MaterialShapes.Gem
    is WidgetSpec.Countdown -> MaterialShapes.Sunny
    null -> MaterialShapes.Circle
}.toShape()
