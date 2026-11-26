/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import com.mtwabpin.kolahal.ui.home.rememberNow
import com.mtwabpin.kolahal.ui.theme.page
import com.mtwabpin.kolahal.ui.tty.rememberShaderStill
import com.mtwabpin.kolahal.ui.tty.rememberTtyGrid
import com.mtwabpin.kolahal.widgets.CardScenes
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec

/**
 * The scene chosen for the placed widget being drawn on its settings page ([PlacedWidget.scene],
 * [CardScenes]): a scene's name, [CardScenes.NONE], or null for its own. [SourceWidget] provides it.
 */
val LocalChosenScene = staticCompositionLocalOf<String?> { null }

/**
 * A card's scene: a still of one of Kolahal's shaders as the card's ground, under a scrim of the
 * card's own color [card], so what is over it reads. [name] is the scene the card draws
 * ([CardScenes.shown]): the one chosen on its settings page, else a stat's `scene` ([WidgetSpec.Stat.scene]).
 * - The shader draws over the page's color, as over home's empty page, so the picture is all its
 *   own. It is a still: its motion's clock (`time`) stays at 0, so it never moves. What it shows is
 *   what home would show now: the phone's date and hour (`date`, read as it draws, and drawn again at
 *   each minute of the cards' clock, so the dunes go from day to dusk without a run), its parameters
 *   with the values they follow (the candle's screen time, a script's rain amount), the battery,
 *   and the phone's lean while home's own shader follows it.
 * - The shader is compiled off the main thread, once for every card ([rememberShaderStill]). Until it
 *   is ready the card is plain, and it draws again with its scene once it is.
 * - In the looks whose card has a fill for it to replace ([CardScenes.drawnIn]): Plain, Tonal, Bold,
 *   and Next, Stack and Now, drawn on a plain card. Split's tiles, Shape's shape and Poster's type keep their own.
 * - Nothing (the plain card) under Android 13, with a screen reader on, and for a shader that isn't
 *   one of Kolahal's own (a user's own shader isn't drawn on a card), doesn't compile, was turned off
 *   after Kolahal stopped with it, or is no scene: one that works on the text or plays once.
 * - A stat ([kind]) has the stat's scrim ([sceneScrim]): its figures stand at the start. Every other
 *   kind has words and marks all over the card: a scrim of [SCRIM_FULL] all over ([CardScenes.full]).
 *   Tiles, rows and pills keep their own fill over it.
 */
@Composable
internal fun CardScene(name: String, look: WidgetLook, kind: WidgetKind?, card: Color) {
    if (!CardScenes.drawnIn(look)) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    // The cards' minute tick: a scene that follows the hour goes from day to dusk without a run.
    val (now, _) = rememberNow()
    val ground = rememberShaderStill(name, rememberTtyGrid(), now.time / 60_000) ?: return
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val full = CardScenes.full(kind)
    val scrim = remember(card, rtl, full) {
        if (full) SolidColor(card.copy(alpha = SCRIM_FULL)) else sceneScrim(card, rtl)
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

/**
 * The scrim over a scene, in [card]'s color: [SCRIM_START] up to [SCRIM_HOLD] of the width from the
 * start, then lighter to [SCRIM_END] at the end. The start is the left, or the right in a
 * right-to-left language ([rtl]). The Now playing card has it over the track's cover too ([MediaArtGround]).
 */
internal fun sceneScrim(card: Color, rtl: Boolean): Brush {
    val stops = arrayOf(
        0f to card.copy(alpha = SCRIM_START),
        SCRIM_HOLD to card.copy(alpha = SCRIM_START),
        1f to card.copy(alpha = SCRIM_END),
    )
    return if (rtl) Brush.horizontalGradient(*stops.map { (at, c) -> (1f - at) to c }.reversed().toTypedArray())
    else Brush.horizontalGradient(*stops)
}

/** The tag of a scene that is drawn: the card has its picture. */
internal const val STAT_SCENE_TAG = "stat-scene"

/** The scrim's strength over the start of the card, up to [SCRIM_HOLD] of its width, where the figures are. */
internal const val SCRIM_START = 0.75f

/** How far across the card the scrim keeps its full strength. */
internal const val SCRIM_HOLD = 0.5f

/** The scrim's strength at the card's end, where the picture shows. */
internal const val SCRIM_END = 0.4f

/**
 * The scrim's strength all over a card that isn't a stat ([CardScenes.full]): a list, an agenda, a
 * chart, the cards of Kolahal's own. Measured on every scene behind a list, an agenda, a chart, Todos
 * and the battery in Plain, Tonal and Bold, in rose-pine dark and light: text that reads at 4.5:1 or
 * more on the plain card keeps at least 3.7:1 over 99% of what is behind it, and most cards keep
 * 4.5:1. The few below are bright dots (snowfall, fireflies, kintsugi's gold) behind text that is
 * near 4.5:1 on the plain card already. At 0.75 the lowest was 3:1, at 0.6 2.2:1.
 */
internal const val SCRIM_FULL = 0.85f

/**
 * A small still of the scene [name] with no scrim, for its choice on the settings page: the page's
 * color until it is compiled, and so with a screen reader on, under Android 13, or for a scene that
 * can't be drawn ([rememberShaderStill]).
 */
@Composable
internal fun SceneStill(name: String, modifier: Modifier) {
    val (now, _) = rememberNow()
    val ground = rememberShaderStill(name, rememberTtyGrid(), now.time / 60_000)
    Box(
        modifier
            .then(ground ?: Modifier)
            .page(marked = true)
    )
}
