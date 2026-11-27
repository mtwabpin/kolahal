/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.ui.LabeledIconButton
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.widgets.WidgetLayout

/** What adds a widget to a widgets page: the empty page's button, and the strip's action for a screen reader. */
internal const val ADD_WIDGET = "Add a widget"

/** What removes an empty widgets page other than page 1 from home: the empty page's text button. */
internal const val REMOVE_PAGE = "Remove page"

/** What opens Arrange widgets on a widgets page: the pencil at its end, and the strip's action for a screen reader. */
internal const val ARRANGE_WIDGETS = "Arrange widgets"

/**
 * Whether a page with widgets shows the pencil after its last card. Off for now, by the maintainer's
 * choice while the place of this action is thought over: the code stays, and Arrange widgets is reached
 * by a long press on a widget, the `widgets` command, the log header's menu, Settings and, for a screen
 * reader, the strip's actions.
 */
internal const val PAGE_END_PENCIL = false

/** What a widgets page shows after its cards, or in their place. */
internal enum class PageEnd {
    /** The pencil, under the last card: Arrange widgets. Add a widget is the + in Arrange's top bar. */
    ARRANGE,

    /** No cards: the empty page ([EmptyWidgetsPage]), with its own Add a widget and no pencil. */
    EMPTY_PAGE;

    companion object {
        /** What a page with [widgets] widgets on it shows. */
        fun of(widgets: Int): PageEnd = if (widgets > 0) ARRANGE else EMPTY_PAGE
    }
}

/**
 * The pencil at the end of a widgets page, under its last card and scrolling with it: Arrange widgets
 * ([onArrange]) on this page. A standard icon button with no container, its 20 dp icon in
 * `onSurfaceVariant`, its 48 dp touch box at the page's end edge, lined up with the cards' edge
 * (mirrored in a right-to-left language). A long press shows its label, as the app's other icon
 * buttons do.
 */
@Composable
internal fun PageEndAction(onArrange: () -> Unit, modifier: Modifier = Modifier) {
    GuiTheme {
        Row(
            modifier
                .fillMaxWidth()
                .padding(horizontal = WidgetLayout.GAP_DP.dp)
                .padding(top = 4.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            LabeledIconButton(
                R.drawable.ic_edit,
                ARRANGE_WIDGETS,
                onClick = onArrange,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                iconModifier = Modifier.size(20.dp),
            )
        }
    }
}
