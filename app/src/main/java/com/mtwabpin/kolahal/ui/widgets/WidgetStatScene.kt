/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.shader.ShaderMeta
import com.mtwabpin.kolahal.shader.ShaderSafety
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.ui.theme.Term
import com.mtwabpin.kolahal.ui.tty.rememberShaderPreview
import com.mtwabpin.kolahal.ui.tty.rememberTtyGrid
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A stat's scene ([WidgetSpec.Stat.scene]): a still of one of Kolahal's shaders as the card's ground,
 * under a scrim of the card's own color [card], so the figures over it read.
 * - The shader draws over the page's color, as over home's empty page, so the picture is all its
 *   own. It is one frame at a fixed time: it never moves, and it is drawn again only when the card
 *   is (its time of day, its parameters and the phone's lean follow as on home's preview of it).
 * - In Plain, Tonal and Bold, whose card has a fill for it to replace. Split's tiles and Shape's
 *   shape keep their own.
 * - Nothing (the plain stat) under Android 13, with a screen reader on, and for a shader that is
 *   missing, doesn't compile, was turned off after Kolahal stopped with it, or is no scene: one that
 *   works on the text or plays once ([WallpaperChoice.suits]).
 * - The scrim is strongest at the start, where the figures are, and lighter toward the end, where
 *   the picture shows ([SCRIM_START], [SCRIM_END]). Measured on stills of twelve scenes at four
 *   hours, in rose-pine dark and light: over the start, text keeps at least 4.9:1 with 99% of the
 *   picture (5.9:1 in Plain), and anywhere on the card at least 4.1:1. Without it, 2:1.
 */
@Composable
internal fun StatScene(spec: WidgetSpec.Stat, look: WidgetLook, card: Color) {
    val name = spec.scene ?: return
    if (look != WidgetLook.PLAIN && look != WidgetLook.TONAL && look != WidgetLook.BOLD) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current.applicationContext
    // Read again when a shader file changes.
    val files by Shaders.version.collectAsStateWithLifecycle()
    val code by produceState<String?>(null, name, files) {
        value = withContext(Dispatchers.IO) {
            Shaders.source(context, name)?.takeIf { source ->
                WallpaperChoice.suits(ShaderMeta.parse(source)) &&
                        !ShaderSafety.get(context).isTripped(ShaderSafety.id(name, source))
            }
        }
    }
    val source = code ?: return
    val ground = rememberShaderPreview(name, source, rememberTtyGrid(), moves = false) ?: return
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val scrim = remember(card, rtl) {
        val stops = arrayOf(
            0f to card.copy(alpha = SCRIM_START),
            SCRIM_HOLD to card.copy(alpha = SCRIM_START),
            1f to card.copy(alpha = SCRIM_END),
        )
        // The strong part is at the start: the left, or the right in a right-to-left language.
        if (rtl) Brush.horizontalGradient(*stops.map { (at, c) -> (1f - at) to c }.reversed().toTypedArray())
        else Brush.horizontalGradient(*stops)
    }
    Box(
        Modifier
            .fillMaxSize()
            .then(ground)
            .background(Term.bg)
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(scrim)
    )
}

/** The scrim's strength over the start of the card, up to [SCRIM_HOLD] of its width, where the figures are. */
internal const val SCRIM_START = 0.75f

/** How far across the card the scrim keeps its full strength. */
internal const val SCRIM_HOLD = 0.5f

/** The scrim's strength at the card's end, where the picture shows. */
internal const val SCRIM_END = 0.4f
