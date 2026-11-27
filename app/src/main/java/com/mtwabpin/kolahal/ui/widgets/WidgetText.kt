/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

/**
 * `text`: a few lines, cut with "…" where the box ends, and a caption under them. A small box has no
 * caption. A large box has the text in bold, as the mockup has it: there it is the card's main thing.
 * A large box has the card's name over the text ([CardName]): "Word of the day".
 * The card's badge ([shownBadge]) is at the end of the name line; a wide box, with no name line,
 * has it at the top end beside the text, where the whole text still fits in the width it leaves
 * ([BadgedBlock]).
 */
@Composable
internal fun TextBody(spec: WidgetSpec.Text, box: WidgetSizes.Box) {
    val look = LocalWidgetLook.current
    if (look.untitled()) {
        TextLook(spec, box, look)
        return
    }
    val type = MaterialTheme.typography
    val style = when (box) {
        WidgetSizes.Box.SMALL -> type.bodySmall
        WidgetSizes.Box.WIDE -> type.bodyMedium
        WidgetSizes.Box.LARGE -> type.titleMediumEmphasized
    }
    val badge = shownBadge(spec, box)
    val role = roleColor(spec.base)
    Column(Modifier.fillMaxSize()) {
        if (box == WidgetSizes.Box.LARGE) {
            if (badge != null) BadgedLine(badge, role, Modifier.padding(bottom = 4.dp)) { CardName() }
            else CardName(Modifier.padding(bottom = 4.dp))
        }
        // The text gets the height that is left, and its last line that fits ends in "…".
        val text: @Composable (Modifier) -> Unit = { modifier ->
            Text(
                cardText(spec.text),
                style = style,
                color = MaterialTheme.colorScheme.onSurface,
                overflow = TextOverflow.Ellipsis,
                modifier = modifier
            )
        }
        if (badge != null && box == WidgetSizes.Box.WIDE) BadgedBlock(
            badge,
            role,
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { text(Modifier.fillMaxSize()) }
        else text(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        if (box != WidgetSizes.Box.SMALL) spec.caption?.let {
            Text(
                cardText(it),
                style = type.labelMedium,
                color = lookColors(look).muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
