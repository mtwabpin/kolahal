/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.ui.theme.GuiTheme

/** What the empty widgets page draws, and when. Pure, so it can be unit tested. */
internal object EmptyPageArt {
    /** The picture's colors: [sky], [sun], [ridge], and [ink] for the outlines and the +. */
    data class Colors(val sky: Color, val sun: Color, val ridge: Color, val ink: Color)

    /** The app icon's own colors (ic_launcher_background, ic_launcher_foreground). */
    val ICON = Colors(
        sky = Color(0xFF3A3350),
        sun = Color(0xFFF0A868),
        ridge = Color(0xFF231D2C),
        ink = Color(0xFFF2E2C4),
    )

    /** Whether a theme whose page is [surface] is dark: from the colors the app resolved, not the phone's setting. */
    fun isDark(surface: Color): Boolean = surface.luminance() < 0.5f

    /**
     * The picture's colors on a page of [surface]: the icon's on a dark theme; on a light one the
     * theme's [sky] (surfaceContainerHigh), [ridge] (surfaceContainerHighest), [sun] (primary) and
     * [ink] (onSurface), so it isn't a dark block on a pale page.
     */
    fun colors(surface: Color, sky: Color, ridge: Color, sun: Color, ink: Color): Colors =
        if (isDark(surface)) ICON else Colors(sky = sky, sun = sun, ridge = ridge, ink = ink)

    /** Below this height, in dp, the page leaves the picture out: landscape, split screen, a large font. */
    const val PICTURE_MIN_HEIGHT_DP = 480f

    /** Whether a page [heightDp] tall shows the picture. */
    fun showsPicture(heightDp: Float): Boolean = heightDp >= PICTURE_MIN_HEIGHT_DP
}

/**
 * A widgets page with no widgets: the app icon's dusk scene with outlines where widgets would go, the
 * title "No widgets yet", one line, and Add a widget ([onAdd]: the picker, for this page), centered. A
 * page other than page 1 has Remove page under it too ([onRemove]), a quieter text button: the page is
 * empty, so it goes at once, with nothing to undo. The picture is left out on a short page
 * ([EmptyPageArt.showsPicture]). The same on every widgets page; the strip above names the page.
 */
@Composable
internal fun EmptyWidgetsPage(onAdd: () -> Unit, modifier: Modifier = Modifier, onRemove: (() -> Unit)? = null) {
    GuiTheme {
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .testTag("widgets-empty")
        ) {
            val picture = EmptyPageArt.showsPicture(maxHeight.value)
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = if (picture) 72.dp else 24.dp, bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top,
            ) {
                if (picture) {
                    EmptyPagePicture()
                    Spacer(Modifier.height(20.dp))
                }
                Text(
                    "No widgets yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Add a clock, weather, music or a script's card",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 260.dp),
                )
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAdd, shapes = ButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.ic_add), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(ADD_WIDGET)
                }
                onRemove?.let {
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = it, shapes = ButtonDefaults.shapes()) { Text(REMOVE_PAGE) }
                }
            }
        }
    }
}

/**
 * The empty page's picture, 240 × 150 dp with 28 dp corners, drawn as shapes: the icon's sky, sun and
 * ridge, widened; dashed outlines of a wide card and a half one, and a solid half with a +. Its
 * coordinates are the drafts', in a 240 × 150 box. Decorative: a screen reader passes over it.
 */
@Composable
private fun EmptyPagePicture() {
    val scheme = MaterialTheme.colorScheme
    val c = EmptyPageArt.colors(
        scheme.surface,
        sky = scheme.surfaceContainerHigh,
        ridge = scheme.surfaceContainerHighest,
        sun = scheme.primary,
        ink = scheme.onSurface,
    )
    Box(
        Modifier
            .size(240.dp, 150.dp)
            .clip(RoundedCornerShape(28.dp))
            .drawWithCache {
                val k = size.width / 240f
                fun o(x: Float, y: Float) = Offset(x * k, y * k)
                fun s(w: Float, h: Float) = Size(w * k, h * k)
                val ridge = Path().apply {
                    moveTo(0f, 104 * k)
                    lineTo(62 * k, 84 * k)
                    lineTo(106 * k, 98 * k)
                    lineTo(156 * k, 80 * k)
                    lineTo(240 * k, 104 * k)
                    lineTo(240 * k, 150 * k)
                    lineTo(0f, 150 * k)
                    close()
                }
                val corner = CornerRadius(12 * k)
                val dashed = Stroke(2 * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6 * k, 5 * k)))
                val solid = Stroke(2 * k)
                val plus = 8 * 0.55f * k
                onDrawBehind {
                    drawRect(c.sky)
                    drawCircle(c.sun, 18 * k, o(178f, 50f))
                    drawPath(ridge, c.ridge)
                    drawRoundRect(c.ink, o(22f, 20f), s(122f, 38f), corner, alpha = 0.7f, style = dashed)
                    drawRoundRect(c.ink, o(22f, 66f), s(57f, 40f), corner, alpha = 0.7f, style = dashed)
                    drawRoundRect(c.ink, o(87f, 66f), s(57f, 40f), corner, alpha = 0.16f)
                    drawRoundRect(c.ink, o(87f, 66f), s(57f, 40f), corner, style = solid)
                    drawLine(c.ink, o(107.5f, 86f), o(123.5f, 86f), plus, StrokeCap.Round)
                    drawLine(c.ink, o(115.5f, 78f), o(115.5f, 94f), plus, StrokeCap.Round)
                }
            },
    )
}
