package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSpec

/** The corners of a picture inside its card. The card's own corners are rounder. */
private val PICTURE_SHAPE = RoundedCornerShape(12.dp)

/**
 * `image`: a picture from the web, with its caption under it.
 * - The picture fills its place. With `fit = "crop"` (the default) it covers the place and loses
 *   its edges. With `fit = "fit"` the whole picture shows, with room around it.
 * - Only a large card has the caption: a small or a low one gives all its room to the picture.
 *   A screen reader still hears it.
 * - With [bleed], the picture covers the whole card, to its edges, and the card puts the caption on
 *   a chip over it ([BleedImageCard]). This body then draws neither the caption nor corners.
 *
 * Home fetches the picture, not the script ([rememberWebImage], [LogImages]):
 * - Only while the widgets page is in view ([LocalWidgetsShown]). The page is composed beside the
 *   log, and the Widgets screen draws real cards too. Neither fetches or decodes anything.
 * - Where the page is not in view, a picture that is already in memory shows. Without one, the
 *   card shows the `alt` text. So no card says "Loading" for ever.
 * - Over https only, redirects included.
 * - Decoded off the main thread, for the size of its place ([WidgetWords.pictureSize]).
 *
 * The picture is no item for a screen reader. The card's description says its `alt` text
 * ([WidgetWords.description]), whether the picture has loaded or not.
 */
@Composable
internal fun ImageBody(spec: WidgetSpec.Image, box: WidgetSizes.Box, bleed: Boolean = false) {
    val inView = LocalWidgetsShown.current
    val caption = spec.caption.takeIf { box == WidgetSizes.Box.LARGE && !bleed }
    Column(Modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().then(if (bleed) Modifier else Modifier.clip(PICTURE_SHAPE))) {
            val size = WidgetWords.pictureSize(constraints.maxWidth, constraints.maxHeight)
            val state = rememberWebImage(spec.image, size.widthPx, size.maxPixels, enabled = inView, httpsOnly = true)
            val picture = state?.getOrNull()
            if (picture != null) {
                Image(
                    picture,
                    contentDescription = null,
                    contentScale = if (spec.fit == "fit") ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().testTag("widget-picture"),
                )
            } else {
                val note = WidgetWords.pictureNote(loaded = false, failed = state?.isFailure == true, inView = inView, alt = spec.alt)
                QuietLine(note ?: spec.alt, small = box == WidgetSizes.Box.SMALL)
            }
        }
        if (caption != null) {
            Text(
                cardText(caption),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}
