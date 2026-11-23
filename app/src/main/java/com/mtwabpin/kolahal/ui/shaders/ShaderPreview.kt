/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.shaders

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.ui.theme.Term
import com.mtwabpin.kolahal.ui.tty.rememberShaderPreview
import com.mtwabpin.kolahal.ui.tty.rememberTtyGrid

/**
 * A few sample log lines in the theme's colors, with the shader of [code] over them
 * ([rememberShaderPreview]). Nothing shows where the shader can't be drawn. A moving shader moves only
 * while this is shown, and stands still as on home (battery saver, Remove animations, after a minute).
 */
@Composable
internal fun ShaderPreview(name: String, code: String, modifier: Modifier = Modifier, moves: Boolean = true, size: Modifier = Modifier.aspectRatio(0.92f)) {
    val look = rememberShaderPreview(name, code, rememberTtyGrid(), moves = moves) ?: Modifier
    Box(
        modifier
            .fillMaxWidth()
            .then(size)
            .clip(MaterialTheme.shapes.extraLarge)
            .then(look)
            .background(Term.bg)
            // A picture: a screen reader has nothing to read here.
            .clearAndSetSemantics { testTag = "shader-preview" },
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(Modifier.padding(16.dp)) {
            SampleLine("$ weather", Term.prompt)
            SampleLine("  London  14°  light rain", Term.fg)
            SampleLine("  wttr.in/london", Term.link)
            SampleLine("$ ping example", Term.prompt)
            SampleLine("  No answer", Term.error)
            SampleLine("  14:02", Term.dim)
        }
    }
}

/**
 * The whole screen with the shader of [code] over it, small: [width] wide, in the window's proportions
 * stood upright, as in a list of templates. Inside, the density is smaller ([ScreenThumb]): the sample is
 * laid out as on the window, and drawn at the box's own pixels, so the shader sees a whole screen whose
 * characters are as small as the rest, and the text is only texture. It stands still: a one-off shows
 * itself part way through ([rememberShaderPreview]). Nothing shows where the shader can't be drawn.
 */
@Composable
internal fun SmallShaderPreview(name: String, code: String, modifier: Modifier = Modifier, width: Dp = 60.dp) {
    val density = LocalDensity.current
    val window = LocalWindowInfo.current.containerSize
    val thumb = ScreenThumb.of(window.width, window.height, density.density, width.value)
    Box(modifier.width(width)) {
        CompositionLocalProvider(LocalDensity provides Density(density.density * thumb.scale, density.fontScale)) {
            ShaderPreview(name, code, moves = false, size = Modifier.aspectRatio(thumb.aspect))
        }
    }
}

/**
 * How a small box shows a whole screen ([SmallShaderPreview]): the box has the screen's [aspect] (width over
 * height), and the density inside it is the screen's times [scale], so the screen's width in dp fits the box.
 */
internal data class ScreenThumb(val aspect: Float, val scale: Float) {
    companion object {
        /** A phone's screen in dp, for a window whose size isn't known yet: 1080 × 2340 px at density 3. */
        const val PHONE_W_DP = 360f
        const val PHONE_H_DP = 780f

        /**
         * For a window [w] × [h] px at [density], and a box [widthDp] wide: the window stood upright (its
         * short side across), so a wide screen gives a box nearly square, never one wider than tall.
         */
        fun of(w: Int, h: Int, density: Float, widthDp: Float): ScreenThumb {
            if (w <= 0 || h <= 0 || density <= 0f) return ScreenThumb(PHONE_W_DP / PHONE_H_DP, widthDp / PHONE_W_DP)
            val across = minOf(w, h).toFloat()
            return ScreenThumb(across / maxOf(w, h), widthDp * density / across)
        }
    }
}

@Composable
private fun SampleLine(text: String, color: androidx.compose.ui.graphics.Color) = Text(
    text,
    style = Term.code,
    color = color,
    maxLines = 1,
    overflow = TextOverflow.Clip,
)
