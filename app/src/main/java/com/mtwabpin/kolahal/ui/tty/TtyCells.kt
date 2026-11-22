/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.terminal.Ink
import com.mtwabpin.kolahal.terminal.tty.CellBuffer
import com.mtwabpin.kolahal.terminal.tty.HomeCells
import com.mtwabpin.kolahal.terminal.tty.Spot

/** Room at the grid's sides, as the GUI's log has. The pixels left over from whole cells go to both sides. */
internal val GRID_MARGIN = 12.dp

/**
 * Where home's grid is: its cells ([grid]), how many columns fit ([cols]), and where column 0
 * starts ([originX]) in a row [widthPx] wide. Every part of home uses the same one, so columns line
 * up from the header to the key bar, and with the log's.
 */
@Stable
internal class TtyFrame(val grid: TtyGrid, val cols: Int, val originX: Int, val widthPx: Int) {
    val cellW: Int get() = grid.metrics.cellW
    val cellH: Int get() = grid.metrics.cellH

    companion object {
        /** The frame of a row [widthPx] wide, with [marginPx] at each side. */
        fun of(grid: TtyGrid, widthPx: Int, marginPx: Int): TtyFrame {
            val m = grid.metrics
            val width = widthPx.coerceAtLeast(m.cellW)
            val cols = m.cols((width - 2 * marginPx).coerceAtLeast(m.cellW))
            return TtyFrame(grid, cols, ((width - cols * m.cellW) / 2).coerceAtLeast(0), width)
        }
    }
}

/** The grid home's parts draw on, set by [TtyHome]. Null outside it: a part then draws nothing. */
internal val LocalTtyFrame = staticCompositionLocalOf<TtyFrame?> { null }

/**
 * What a spot of a part does and says.
 * - [tag]: its test tag; [text]: what it reads (TalkBack, and tests find it by these words);
 *   [description]: what TalkBack says instead, when the words alone don't say it.
 * - [onClick], [onLongClick] and their labels, as a clickable has them.
 * - [toStart], [toEnd]: its touch reaches the band's edge, past the grid's margin (a button at the
 *   row's end reaches the screen's edge, as the GUI's do).
 */
internal class SpotAction(
    val onClick: () -> Unit,
    val tag: String? = null,
    val text: String? = null,
    val description: String? = null,
    val clickLabel: String? = null,
    /** Words it reads after [text], as a second text (a suggestion's description). */
    val more: String? = null,
    val onLongClick: (() -> Unit)? = null,
    val longClickLabel: String? = null,
    val role: Role? = Role.Button,
    val toStart: Boolean = false,
    val toEnd: Boolean = false,
)

/**
 * One of home's parts on the grid: [cells] painted in a band [heightPx] tall, its rows in the
 * middle of it, on whole pixels. The box lines of its first and last rows go on through the room
 * above and below them ([GridPainter.drawBand]). [fill] paints the whole band (the key bar's
 * surface). [x] is where column 0 starts, the frame's origin unless a part scrolls.
 *
 * Each spot that [actions] gives an action becomes a node of its own: a tap, a long press, its
 * words for TalkBack, its test tag. A spot as tall as the cells takes the band's whole height.
 * While pressed, its cells are on the surface color.
 */
@Composable
internal fun TtyBand(
    cells: HomeCells,
    heightPx: Int,
    modifier: Modifier = Modifier,
    fill: Ink? = null,
    x: Int? = null,
    actions: (Spot) -> SpotAction? = { null },
) {
    val frame = LocalTtyFrame.current ?: return
    val grid = frame.grid
    val m = grid.metrics
    val originX = x ?: frame.originX
    val height = heightPx.coerceAtLeast(cells.rows * m.cellH)
    val top = (height - cells.rows * m.cellH) / 2
    var pressed by remember { mutableIntStateOf(NONE) }
    Box(
        modifier
            .fillMaxWidth()
            .exactHeight(height)
            .drawBehind {
                fill?.let { drawRect(Color(grid.palette.color(it))) }
                cells.spot(pressed)?.let { s ->
                    drawRect(
                        Color(grid.palette.color(Ink.SURFACE)),
                        Offset((originX + s.rect.col * m.cellW).toFloat(), (top + s.rect.row * m.cellH).toFloat()),
                        Size((s.rect.cols * m.cellW).toFloat(), (s.rect.rows * m.cellH).toFloat()),
                    )
                }
                grid.painter.drawBand(drawContext.canvas.nativeCanvas, cells.grid, originX.toFloat(), top.toFloat(), 0f, size.height)
            },
    ) {
        for (s in cells.spots) {
            val a = actions(s) ?: continue
            key(s.id) {
                val whole = s.rect.row == 0 && s.rect.rows == cells.rows
                val y = if (whole) 0 else top + s.rect.row * m.cellH
                val h = if (whole) height else s.rect.rows * m.cellH
                var left = originX + s.rect.col * m.cellW
                var right = left + s.rect.cols * m.cellW
                if (a.toStart) left = 0
                if (a.toEnd) right = maxOf(right, frame.widthPx)
                SpotNode(a, left, y, right - left, h) { down -> pressed = if (down) s.id else NONE }
            }
        }
    }
}

/** No spot is pressed. */
private const val NONE = Int.MIN_VALUE

/**
 * A spot's node: [width] × [height] pixels at [x], [y], with [a]'s tap, long press and words. It
 * draws nothing; [onPress] tells the band to show it pressed.
 */
@Composable
internal fun SpotNode(a: SpotAction, x: Int, y: Int, width: Int, height: Int, onPress: (Boolean) -> Unit) {
    val source = remember { MutableInteractionSource() }
    LaunchedEffect(source) {
        source.interactions.collect {
            when (it) {
                is PressInteraction.Press -> onPress(true)
                is PressInteraction.Release, is PressInteraction.Cancel -> onPress(false)
            }
        }
    }
    Box(
        Modifier
            .offset { IntOffset(x, y) }
            .exactSize(width, height)
            .then(if (a.tag != null) Modifier.testTag(a.tag) else Modifier)
            .semantics {
                val texts = listOfNotNull(a.text, a.more)
                if (texts.isNotEmpty()) this[SemanticsProperties.Text] = texts.map { AnnotatedString(it) }
                a.description?.let { contentDescription = it }
            }
            .combinedClickable(
                interactionSource = source,
                indication = null,
                role = a.role,
                onClickLabel = a.clickLabel,
                onLongClick = a.onLongClick,
                onLongClickLabel = a.longClickLabel,
                onClick = a.onClick,
            ),
    )
}

/** Paints [cells] with its top left corner at [x], [y] on this draw scope. */
internal fun DrawScope.paintCells(grid: TtyGrid, cells: CellBuffer, x: Float, y: Float, rowFrom: Int = 0, rowTo: Int = cells.rows) {
    grid.painter.draw(drawContext.canvas.nativeCanvas, cells, x, y, rowFrom, rowTo)
}

/** Exactly [px] pixels tall, so rows stay whole cells (no rounding through dp). */
internal fun Modifier.exactHeight(px: Int) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = px, maxHeight = px))
    layout(placeable.width, px) { placeable.place(0, 0) }
}

/** Exactly [width] × [height] pixels. */
internal fun Modifier.exactSize(width: Int, height: Int) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(width.coerceAtLeast(0), height.coerceAtLeast(0)))
    layout(width.coerceAtLeast(0), height.coerceAtLeast(0)) { placeable.place(0, 0) }
}
