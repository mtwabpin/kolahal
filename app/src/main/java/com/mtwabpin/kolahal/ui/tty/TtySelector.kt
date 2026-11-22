/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellStyle
import com.mtwabpin.kolahal.terminal.tty.SelectorCells
import com.mtwabpin.kolahal.ui.home.SelectorEdge
import com.mtwabpin.kolahal.ui.home.SelectorMath
import kotlin.math.roundToInt

/** How far the rail's letters and handle stand from the screen's edge, as the GUI's do. */
private val FROM_EDGE = 3.dp

/** The strip's width, as the GUI's selector has it. */
private val STRIP = 36.dp

/**
 * The app selector's letters at rest (`selector.letters`) on the grid, in the strip: each letter a
 * cell in the middle of its stretch, nudged apart to a row of cells at least, and left out where
 * they don't fit (one font size: they can't shrink). Letters outside where a touch starts are
 * fainter. Each is a node tagged `app-selector-letter`, as the GUI's.
 */
@Composable
internal fun TtyRailLetters(
    grid: TtyGrid,
    edge: SelectorEdge,
    letters: List<Char>,
    middles: FloatArray,
    stripHPx: Float,
    zoneTopPx: Float,
    zoneHPx: Float,
) {
    val m = grid.metrics
    val density = LocalDensity.current
    val gap = m.cellH.toFloat()
    val placed = remember(middles, stripHPx, gap) {
        SelectorMath.labelPositions(FloatArray(middles.size) { middles[it] * stripHPx }, gap, gap / 2, stripHPx - gap / 2)
    }
    val x = railX(edge, m.cellW, with(density) { STRIP.roundToPx() }, with(density) { FROM_EDGE.roundToPx() })
    letters.forEachIndexed { i, letter ->
        val p = placed.getOrElse(i) { Float.NaN }
        if (p.isNaN()) return@forEachIndexed
        val inZone = p >= zoneTopPx && p <= zoneTopPx + zoneHPx
        val cells = remember(letter, inZone) { SelectorCells.letter(letter, current = false, faint = !inZone) }
        Spacer(
            Modifier
                .offset { IntOffset(x, (p - m.cellH / 2f).roundToInt()) }
                .exactSize(m.cellW, m.cellH)
                .testTag("app-selector-letter")
                .semantics { text = AnnotatedString(letter.toString()) }
                .drawBehind { paintCells(grid, cells.grid, 0f, 0f) },
        )
    }
}

/**
 * The app selector's handle at rest on the grid: `A`, a muted line [handlePx] tall, `Z`, in the
 * middle of the strip [stripHPx] tall. Fainter while the list is open ([faded]). The line is
 * tagged `app-selector-handle`, as the GUI's.
 */
@Composable
internal fun TtyRailHandle(grid: TtyGrid, edge: SelectorEdge, handlePx: Float, faded: Boolean, stripHPx: Float) {
    val m = grid.metrics
    val density = LocalDensity.current
    val x = railX(edge, m.cellW, with(density) { STRIP.roundToPx() }, with(density) { FROM_EDGE.roundToPx() })
    val a = remember { SelectorCells.letter('A', current = false, faint = true) }
    val z = remember { SelectorCells.letter('Z', current = false, faint = true) }
    val top = ((stripHPx - handlePx) / 2f).roundToInt()
    val line = Color(grid.palette.text(CellStyle.of(Ink.DIM, flags = CellStyle.FAINT)))
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (faded) 0.35f else 1f }
            .drawBehind {
                paintCells(grid, a.grid, x.toFloat(), (top - m.cellH).toFloat())
                paintCells(grid, z.grid, x.toFloat(), (top + handlePx.roundToInt()).toFloat())
            },
    ) {
        Spacer(
            Modifier
                .offset { IntOffset(x, top) }
                .exactSize(m.cellW, handlePx.roundToInt())
                .testTag("app-selector-handle")
                .drawBehind {
                    // A light vertical line in the middle of the column, as the grid draws │.
                    val left = (m.cellW / 2 - m.line / 2).toFloat()
                    drawRect(line, androidx.compose.ui.geometry.Offset(left, 0f), androidx.compose.ui.geometry.Size(m.line.toFloat(), size.height))
                },
        )
    }
}

/**
 * The letters around the thumb's while it slides along the edge, on the grid: they come out from
 * the edge as the GUI's wave does, but none grows, and the nearer ones are in the text color. The
 * current letter is beside the thumb, inverse in the accent color (` A `), on a node tagged
 * `app-selector-bubble` that reads the letter, as the GUI's bubble.
 */
@Composable
internal fun TtyRailWave(
    grid: TtyGrid,
    edge: SelectorEdge,
    letters: List<Char>,
    letter: Int,
    middles: FloatArray,
    stripTopPx: Float,
    stripHPx: Float,
    restPx: Float,
    bubbleFromEdge: Float,
    waveIn: () -> Float,
    waveAt: () -> Float,
    clearPx: Float,
    containerW: Float,
) {
    val m = grid.metrics
    val density = LocalDensity.current
    val muted = remember(letters) { letters.map { SelectorCells.letter(it, current = false) } }
    val near = remember(letters) { letters.map { SelectorCells.letter(it, current = true) } }
    val bubble = remember(letters, letter) { SelectorCells.bubble(letters[letter]) }
    val unit = stripHPx * (1f - 2 * SelectorMath.DEAD) / letters.size
    val spread = SelectorMath.waveSpread(unit, with(density) { 38.dp.toPx() })
    val clear = clearPx + with(density) { 6.dp.toPx() }
    val n = letters.size
    // Worked out once per frame, in the draw phase, into arrays kept here.
    val ys = remember(n) { FloatArray(n) }
    val outs = remember(n) { FloatArray(n) }
    val ones = remember(n) { FloatArray(n) { 1f } }
    Box(
        Modifier
            .fillMaxSize()
            .testTag("app-selector-wave")
            .graphicsLayer { alpha = waveIn() }
            .drawBehind {
                val ease = waveIn()
                val c = waveAt() * stripHPx
                for (i in 0 until n) {
                    ys[i] = SelectorMath.waveY(middles[i] * stripHPx, c, unit, spread * ease)
                    outs[i] = SelectorMath.waveOut((middles[i] * stripHPx - c) / unit) * ease
                }
                val drawn = SelectorMath.waveShown(ys, ones, letter, m.cellH.toFloat(), clear * ease)
                for (i in 0 until n) {
                    if (!drawn[i]) continue
                    val fromEdge = restPx + (bubbleFromEdge - restPx) * outs[i]
                    val cells = if (outs[i] > 0.5f) near[i] else muted[i]
                    paintCells(
                        grid, cells.grid,
                        (edge.x(fromEdge, size.width) - m.cellW / 2f).roundToInt().toFloat(),
                        (stripTopPx + ys[i] - m.cellH / 2f).roundToInt().toFloat(),
                    )
                }
            },
    )
    Spacer(
        Modifier
            .offset {
                // Comes out from the edge with the wave, as the GUI's bubble.
                val fromEdge = restPx + (bubbleFromEdge - restPx) * waveIn()
                IntOffset(
                    (edge.x(fromEdge, containerW) - bubble.cols * m.cellW / 2f).roundToInt(),
                    (stripTopPx + waveAt() * stripHPx - m.cellH / 2f).roundToInt(),
                )
            }
            .exactSize(bubble.cols * m.cellW, m.cellH)
            .graphicsLayer { alpha = waveIn() }
            .testTag("app-selector-bubble")
            .semantics { text = AnnotatedString(letters[letter].toString()) }
            .drawBehind { paintCells(grid, bubble.grid, 0f, 0f) },
    )
}

/** Where a rail cell starts in the strip: [fromEdge] pixels in from the screen's edge. */
private fun railX(edge: SelectorEdge, cellW: Int, stripW: Int, fromEdge: Int) =
    if (edge.left) fromEdge else stripW - fromEdge - cellW
