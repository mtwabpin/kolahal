/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

@Composable
private fun SampleLine(text: String, color: androidx.compose.ui.graphics.Color) = Text(
    text,
    style = Term.code,
    color = color,
    maxLines = 1,
    overflow = TextOverflow.Clip,
)
