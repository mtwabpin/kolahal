/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetPlacement

/** What the preview says when page 1 has no widgets. */
internal const val NO_WIDGETS_YET = "No widgets on the widgets page yet"

/** The tag of the preview of the widgets page, for tests. */
internal const val PAGE_PREVIEW_TAG = "widgets-page-preview"

/**
 * Widgets page 1 as home draws it, scaled to the width it is given: under Widget corners in Settings
 * (`widgets.corners`), so a pick shows on the person's own cards at once.
 * - Kolahal's cards are the real cards, live, as the settings page of a card draws its own
 *   ([LocalArranging]): they take no taps and ask for no runs. An app's widget is a plain box in its
 *   place: its view is on the page, and it keeps its own corners.
 * - At most [MAX_DP] of the page, from its top: a long page is cut there.
 * - It is composed only while its row is: Settings' list draws only the rows on screen.
 * - A screen reader skips it: the setting says what it does.
 */
@Composable
internal fun WidgetsPagePreview(vm: TerminalViewModel) {
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    val all by widgets.widgets.collectAsStateWithLifecycle()
    val panel = remember(all) {
        WidgetPlacement.readingOrder(all.filter { it.location == WidgetLocation.PANEL })
    }
    if (panel.isEmpty()) {
        Text(
            NO_WIDGETS_YET,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 8.dp)
                .testTag(PAGE_PREVIEW_TAG),
        )
        return
    }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .testTag(PAGE_PREVIEW_TAG)
            .clearAndSetSemantics {}
    ) {
        val pageDp = LocalContext.current.resources.configuration.screenWidthDp - 2 * WidgetLayout.GAP_DP
        val inner = maxWidth - (2 * PAD_DP).dp
        val scale = (inner.value / pageDp).coerceAtMost(1f)
        // A widget without a place yet goes under the others, as on the page ([WidgetGrid]).
        val unplaced = panel.filterNot { it.positioned }.sumOf { it.heightDp + WidgetLayout.GAP_DP }
        val tall = minOf(WidgetPlacement.pageHeightDp(panel) + unplaced, MAX_DP)
        Box(
            Modifier
                .padding(PAD_DP.dp)
                .height((tall * scale).dp)
                .fillMaxWidth()
                .clipToBounds()
        ) {
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredWidth(pageDp.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
            ) {
                CompositionLocalProvider(LocalArranging provides true) {
                    WidgetGrid(WidgetLayout.GAP_DP.dp, Modifier.requiredWidth(pageDp.dp)) {
                        panel.forEach { w ->
                            Box(Modifier.gridPlace(w)) {
                                if (w.source != null) SourceWidget(w, w.heightDp.dp, actions = null, feed = vm.widgetFeed)
                                else Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(w.heightDp.dp)
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.large)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** How much of the page the preview shows at most, in dp of the page. */
private const val MAX_DP = 520

/** The room around the page in the preview. */
private const val PAD_DP = 8
