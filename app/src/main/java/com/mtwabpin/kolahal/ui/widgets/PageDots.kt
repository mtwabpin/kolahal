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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.ui.theme.Term

/**
 * The dots after a widgets page's name: one for each of the [count] pages, in their order, the
 * [current] one (from 0) a longer dash in the prompt's color. A screen reader hears "Page 2 of 3".
 */
@Composable
internal fun WidgetPageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.clearAndSetSemantics { contentDescription = "Page ${current + 1} of $count" },
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(width = if (i == current) 14.dp else 6.dp, height = 6.dp)
                    .background(if (i == current) Term.prompt else Term.dim, CircleShape)
            )
        }
    }
}
