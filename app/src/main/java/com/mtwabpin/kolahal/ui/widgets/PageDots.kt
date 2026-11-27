/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.HomeEntry
import com.mtwabpin.kolahal.data.HomeOrder
import com.mtwabpin.kolahal.ui.theme.Term

/** What a mark in the page dots stands for: the log, a widgets page or the Assistant's page. */
internal enum class PageKind { LOG, WIDGETS, ASSISTANT }

/** One mark in the page dots: a page of [kind], [current] when it is the page shown. */
internal data class PageMark(val kind: PageKind, val current: Boolean)

/**
 * The marks for home's pages in [order], the one at [place] (from 0, the pager's place) current: one per
 * page home shows, in home's order from the start edge, as the pager lays them out. Pure, so it can be unit
 * tested. The Assistant is only ever a [HomeEntry.Assistant] in [order], never there in the Play build.
 */
internal fun pageMarks(order: HomeOrder, place: Int): List<PageMark> = order.entries.mapIndexed { i, entry ->
    PageMark(
        when (entry) {
            HomeEntry.Log -> PageKind.LOG
            HomeEntry.Assistant -> PageKind.ASSISTANT
            is HomeEntry.Widgets -> PageKind.WIDGETS
        },
        i == place,
    )
}

/**
 * The dots after a widgets page's name: one mark for each of home's pages ([marks], [pageMarks]), in
 * home's order. A widgets page is a dot, the current one a longer dash in the prompt's color; the log and
 * the Assistant are their icons (as in Page order), the current one in the prompt's color. A Row, so in a
 * right-to-left layout the marks run from the right, as the pager's pages do. A screen reader hears
 * "Page 2 of 3".
 */
@Composable
internal fun WidgetPageDots(marks: List<PageMark>, modifier: Modifier = Modifier) {
    val current = marks.indexOfFirst { it.current }
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = "Page ${current + 1} of ${marks.size}" },
    ) {
        marks.forEach { mark ->
            val color = if (mark.current) Term.prompt else Term.dim
            when (mark.kind) {
                PageKind.WIDGETS -> Box(
                    Modifier
                        .size(width = if (mark.current) 14.dp else 6.dp, height = 6.dp)
                        .background(color, CircleShape)
                )
                PageKind.LOG, PageKind.ASSISTANT -> Icon(
                    painterResource(if (mark.kind == PageKind.LOG) R.drawable.ic_stat_terminal else R.drawable.ic_forum),
                    null,
                    tint = color,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}
