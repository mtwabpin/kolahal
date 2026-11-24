/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * A number that rolls to its new value: when [text] changes while the card can be seen ([cardSeen]),
 * each digit that changed slides up and out, and the new one comes up from below, on the theme's
 * spring (a little past, then back). The digits that stay don't move.
 * - Only between numbers of one shape ([RollingDigits.changed]): "6,238" to "6,240". Any other change
 *   is drawn at once, as before.
 * - [content] draws the text, exactly [text], on one line: it puts the modifier it gets last on its
 *   text and hands the text's layout to the callback. The text is laid out and sized as always; the
 *   roll only draws, so a card that ticks lays nothing out again.
 * - With animations off, the spring ends at once, as the other cards' motion does.
 */
@Composable
internal fun RollingText(
    text: String,
    content: @Composable (roll: Modifier, onTextLayout: (TextLayoutResult) -> Unit) -> Unit,
) {
    val last = remember { Shown() }
    val seen = cardSeen()
    // Worked out while composing, so the frame with the new text already hides its new digits.
    val roll = remember(text) {
        val before = last.text
        val changed = if (seen && before != null) RollingDigits.changed(before, text) else null
        if (before == null || changed.isNullOrEmpty()) null else Roll(before, changed)
    }
    SideEffect { last.text = text }
    val progress = remember(roll) { Animatable(if (roll == null) 1f else 0f) }
    val spring = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(progress) { if (progress.value < 1f) progress.animateTo(1f, spring) }
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val laid = remember { Shown() }
    val modifier = if (roll == null) Modifier else Modifier.drawWithContent {
        val layout = laid.layout
        val p = progress.value
        if (layout == null || p == 1f || layout.layoutInput.text.text != text || layout.isLineEllipsized(0)) {
            drawContent()
            return@drawWithContent
        }
        val style = layout.layoutInput.style
        // The line box, a little taller: a heavy digit may reach out of it.
        val boxes = roll.indices.map { i ->
            val b = layout.getBoundingBox(i)
            Rect(b.left, b.top - b.height * REACH, b.right, b.bottom + b.height * REACH)
        }
        val hidden = Path().apply { boxes.forEach { addRect(it) } }
        clipPath(hidden, ClipOp.Difference) { this@drawWithContent.drawContent() }
        roll.indices.forEachIndexed { n, i ->
            val box = boxes[n]
            val travel = box.height
            val top = layout.getBoundingBox(i).top
            val old = measurer.measure(roll.from[i].toString(), style)
            val new = measurer.measure(text[i].toString(), style)
            clipRect(box.left, box.top, box.right, box.bottom) {
                drawText(old, topLeft = Offset(box.left, top - p * travel))
                drawText(new, topLeft = Offset(box.left, top + (1f - p) * travel))
            }
        }
    }
    content(modifier) { laid.layout = it }
}

/** [BigText] whose digits roll to a new value ([RollingText]): a stat's main value. */
@Composable
internal fun RollingBigText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    RollingText(text) { roll, laid -> BigText(text, style, modifier.then(roll), color, laid) }
}

/** How far a rolling digit's clip reaches past its line, as a share of the line's height. */
private const val REACH = 0.12f

/** What was last shown, kept between compositions; nothing reads it to draw. */
private class Shown {
    var text: String? = null
    var layout: TextLayoutResult? = null
}

/** A roll from the text [from] at its digits [indices]. */
private class Roll(val from: String, val indices: List<Int>)

/** Which digits of a number roll when it changes. Pure. */
object RollingDigits {
    /**
     * A number as a card shows it: a sign, digits with separators (6,240, 1.0824, 12 345), and a
     * percent or degree sign at its end.
     */
    private val NUMBER = Regex("""[+\-−]?\d[\d.,'’   ]*[%°]?""")

    /**
     * The places of the digits that differ between [old] and [new], when both are numbers of the
     * same shape: as long, with their digits in the same places and every other character the same.
     * Null when they aren't: such a change is drawn at once. Empty when nothing changed.
     */
    fun changed(old: String, new: String): List<Int>? {
        if (old.length != new.length || !NUMBER.matches(old) || !NUMBER.matches(new)) return null
        val changed = ArrayList<Int>()
        for (i in old.indices) {
            val a = old[i]
            val b = new[i]
            when {
                a == b -> Unit
                a.isDigit() && b.isDigit() -> changed += i
                else -> return null
            }
        }
        return changed
    }
}
