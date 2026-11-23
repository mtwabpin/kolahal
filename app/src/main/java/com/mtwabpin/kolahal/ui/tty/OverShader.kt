/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.ShaderMeta

/**
 * A part of home drawn over the picture of home's shader, not through it: the app selector, over a
 * shader that isn't for text ([ShaderMeta.coversSelector]). The part keeps
 * its place in home's layout, its touches and what a screen reader reads. Only where it is drawn moves:
 * it is recorded in its place ([OverShaderBox]) and drawn by home's surface after the shader's picture.
 *
 * Home's shader puts [surface] right before its own layer and turns it [on] while it is drawn
 * ([rememberTtyShader]). While it is off, the part is drawn in its place, as without it.
 */
@Stable
internal class OverShader(private val layer: GraphicsLayer) {
    /** The shader on home is drawn, and isn't for text. */
    var on by mutableStateOf(false)

    private var home: LayoutCoordinates? = null
    private var part: LayoutCoordinates? = null

    /** Where the part is in the window; unspecified while it isn't there. Read while drawing, so a move draws it again. */
    private var at by mutableStateOf(Offset.Unspecified)

    /** On home's surface, right before the shader's layer: the part, drawn after the picture. */
    val surface: Modifier = Modifier
        .onPlaced { home = it }
        .drawWithContent {
            drawContent()
            if (!on || !at.isSpecified) return@drawWithContent
            val from = home?.takeIf { it.isAttached } ?: return@drawWithContent
            val to = part?.takeIf { it.isAttached } ?: return@drawWithContent
            val offset = from.localPositionOf(to, Offset.Zero)
            translate(offset.x, offset.y) { drawLayer(layer) }
        }

    /** On the part: while [on], it is recorded for [surface] and not drawn in its place. */
    val content: Modifier = Modifier
        .onGloballyPositioned {
            part = it
            at = it.positionInWindow()
        }
        .drawWithContent {
            if (on) layer.record { this@drawWithContent.drawContent() } else drawContent()
        }

    /** The part has left home. */
    fun gone() {
        part = null
        at = Offset.Unspecified
    }
}

/** While composed by the shader that draws on home, [over] is on for one with [meta] that isn't for text. */
@Composable
internal fun DrawOverShader(over: OverShader, meta: ShaderMeta) {
    DisposableEffect(over, meta) {
        over.on = !meta.coversSelector
        onDispose { over.on = false }
    }
}

/**
 * An [OverShader] for home while [shader] (a name, or null for none) may be drawn on it. Null with no
 * shader, and on Android 12 and older, which draw none: then the part is drawn as it always is.
 */
@Composable
internal fun rememberOverShader(shader: String?): OverShader? {
    if (shader == null || shader == ShaderCatalog.OFF || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
    val layer = rememberGraphicsLayer()
    return remember(layer) { OverShader(layer) }
}

/**
 * [content] where it would be in this box, filling it, and drawn over home's shader by [over]. Without
 * [over], just [content].
 */
@Composable
internal fun BoxScope.OverShaderBox(over: OverShader?, content: @Composable () -> Unit) {
    if (over == null) {
        content()
        return
    }
    DisposableEffect(over) { onDispose { over.gone() } }
    Box(Modifier.matchParentSize().then(over.content)) { content() }
}
