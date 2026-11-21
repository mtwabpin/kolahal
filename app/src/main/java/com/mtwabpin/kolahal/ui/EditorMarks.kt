/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp

/**
 * Find matches to draw: the start offsets in a window around the current match. Two instances are
 * equal when they mark the same text.
 */
internal class MatchHighlights(val starts: IntArray, val length: Int, val current: Int) {
    override fun equals(other: Any?) =
        other is MatchHighlights && other.length == length && other.current == current && other.starts.contentEquals(
            starts
        )

    override fun hashCode() = 31 * (31 * starts.contentHashCode() + length) + current
}

/**
 * The find matches and the flashed line (after a jump to a line or an error), drawn behind the
 * editor's text from its [layout]. They are not part of the text's colours, so a jump to the next
 * match, or the flash going out, draws again and does not lay the whole text out again.
 * - Each match has a light wash. The current one is stronger, with an outline.
 * - Offsets can be briefly out of date while matches are found again after an edit: those past the
 *   end are left out.
 */
@Composable
internal fun BoxScope.EditorMarks(
    highlights: MatchHighlights?,
    flashLine: Int?,
    layout: () -> TextLayoutResult?
) {
    if (highlights == null && flashLine == null) return
    val other = Term.info.copy(alpha = 0.3f)
    val current = Term.prompt.copy(alpha = 0.45f)
    val outline = Term.prompt
    val flash = Term.error.copy(alpha = 0.25f)
    Spacer(
        Modifier
            .matchParentSize()
            .testTag("editor-marks")
            .semantics {
                this[MarkedMatches] =
                    highlights?.let { h -> h.starts.toList() to h.current.takeIf { it >= 0 } }
                        ?: (emptyList<Int>() to null)
            }
            .graphicsLayer()
            .drawWithCache {
                // Read here, so a new layout (a key typed) makes the marks again.
                val l = layout()
                val length = l?.layoutInput?.text?.length ?: 0
                fun pathOf(start: Int, end: Int): Path? =
                    if (l == null || start < 0 || end > length || end <= start) null else l.getPathForRange(
                        start,
                        end
                    )

                val others = Path()
                var mine: Path? = null
                highlights?.let { h ->
                    for (s in h.starts) {
                        val p = pathOf(s, s + h.length) ?: continue
                        if (s == h.current) mine = p else others.addPath(p)
                    }
                }
                val flashed = if (l == null || flashLine == null) null else {
                    val text = l.layoutInput.text.text
                    val start = selectionAtLine(text, flashLine).start
                    pathOf(start, text.indexOf('\n', start).let { if (it < 0) text.length else it })
                }
                val stroke = Stroke(width = 1.5.dp.toPx())
                onDrawBehind {
                    flashed?.let { drawPath(it, flash) }
                    drawPath(others, other)
                    mine?.let {
                        drawPath(it, current)
                        drawPath(it, outline, style = stroke)
                    }
                }
            },
    )
}

/** What [EditorMarks] marks, for tests: each match's start, and the current one's (null when none is). */
internal val MarkedMatches = SemanticsPropertyKey<Pair<List<Int>, Int?>>("MarkedMatches")
