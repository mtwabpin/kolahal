package com.mtwabpin.kolahal.ui

import androidx.compose.runtime.Composable
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

/**
 * `image`: a picture from the web.
 *
 * For now the card shows the picture's `alt` text and fetches nothing. The picture itself comes
 * in a later commit.
 */
@Composable
internal fun ImageBody(spec: WidgetSpec.Image, box: WidgetSizes.Box) {
    QuietLine(spec.alt, small = box == WidgetSizes.Box.SMALL)
}
