package com.mtwabpin.kolahal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.terminal.LivePreview

/**
 * Tells the user that home shows a preview of the typed line, not the settings in use.
 * It sits right above the prompt, where the user is looking, in the theme's solid colour.
 * Its text size stays fixed while a font size is being previewed.
 */
@Composable
internal fun LivePreviewBand(live: LivePreview?) {
    // Keep the last text while the band slides away.
    val shown = remember { arrayOfNulls<LivePreview>(1) }
    if (live != null) shown[0] = live
    PromptBand(live != null, "Preview: ${shown[0]?.label}  ·  Enter to apply", "live-preview")
}

/**
 * Tells the user that Kolahal is not the phone's home app, in the same kind of band.
 * A tap ([onClick]) asks Android to make Kolahal the home app. The band goes away once it is.
 */
@Composable
internal fun DefaultHomeBand(visible: Boolean, onClick: () -> Unit) =
    PromptBand(visible, "Kolahal isn't your home app  ·  Tap to make it", "default-home", onClick, "make Kolahal the home app")

/** A band right above the prompt, in the theme's solid colour, that slides in and out. */
@Composable
private fun PromptBand(visible: Boolean, text: String, tag: String, onClick: (() -> Unit)? = null, clickLabel: String? = null) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(expandFrom = Alignment.Bottom),
        exit = shrinkVertically(shrinkTowards = Alignment.Bottom),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .background(Term.prompt)
                .then(if (onClick != null) Modifier.clickable(onClickLabel = clickLabel, onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag(tag)
                .semantics { liveRegion = LiveRegionMode.Polite },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = Term.bg,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
