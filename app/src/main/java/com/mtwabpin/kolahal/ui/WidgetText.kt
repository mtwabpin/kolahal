/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

/**
 * `text`: a few lines, cut with "…" where the box ends, and a caption under them. A small box has no
 * caption. A large box has the text in bold, as the mockup has it: there it is the card's main thing.
 * A large box has the card's name over the text ([CardName]): "Word of the day".
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
        WidgetSizes.Box.LARGE -> type.titleMedium.copy(fontWeight = FontWeight.Bold)
    }
    Column(Modifier.fillMaxSize()) {
        if (box == WidgetSizes.Box.LARGE) CardName(Modifier.padding(bottom = 4.dp))
        // The text gets the height that is left, and its last line that fits ends in "…".
        Text(
            cardText(spec.text),
            style = style,
            color = MaterialTheme.colorScheme.onSurface,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
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
