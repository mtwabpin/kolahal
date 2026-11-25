/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import com.mtwabpin.kolahal.ui.home.rememberNow
import com.mtwabpin.kolahal.ui.theme.page
import com.mtwabpin.kolahal.ui.tty.rememberShaderStill
import com.mtwabpin.kolahal.ui.tty.rememberTtyGrid
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec

/**
 * A stat's scene ([WidgetSpec.Stat.scene]): a still of one of Kolahal's shaders as the card's ground,
 * under a scrim of the card's own color [card], so the figures over it read.
 * - The shader draws over the page's color, as over home's empty page, so the picture is all its
 *   own. It is a still: its motion's clock (`time`) stays at 0, so it never moves. What it shows is
 *   what home would show now: the phone's date and hour (`date`, read as it draws, and drawn again at
 *   each minute of the cards' clock, so the dunes go from day to dusk without a run), its parameters
 *   with the values they follow (the candle's screen time, a script's rain amount), the battery,
 *   and the phone's lean while home's own shader follows it.
 * - The shader is compiled off the main thread, once for every card ([rememberShaderStill]). Until it
 *   is ready the card is the plain stat, and it draws again with its scene once it is.
 * - In Plain, Tonal and Bold, whose card has a fill for it to replace. Split's tiles and Shape's
 *   shape keep their own.
 * - Nothing (the plain stat) under Android 13, with a screen reader on, and for a shader that isn't
 *   one of Kolahal's own (a user's own shader isn't drawn on a card), doesn't compile, was turned off
 *   after Kolahal stopped with it, or is no scene: one that works on the text or plays once.
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
    // The cards' minute tick: a scene that follows the hour goes from day to dusk without a run.
    val (now, _) = rememberNow()
    val ground = rememberShaderStill(name, rememberTtyGrid(), now.time / 60_000) ?: return
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
            // Only once the shader is compiled: a test waits for it before it takes a picture.
            .testTag(STAT_SCENE_TAG)
            .then(ground)
            // The scene reads this page: marked, so it shows over all of it ([ShaderMeta.PAGE_ALPHA]).
            .page(marked = true)
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(scrim)
    )
}

/** The tag of a scene that is drawn: the card has its picture. */
internal const val STAT_SCENE_TAG = "stat-scene"

/** The scrim's strength over the start of the card, up to [SCRIM_HOLD] of its width, where the figures are. */
internal const val SCRIM_START = 0.75f

/** How far across the card the scrim keeps its full strength. */
internal const val SCRIM_HOLD = 0.5f

/** The scrim's strength at the card's end, where the picture shows. */
internal const val SCRIM_END = 0.4f
