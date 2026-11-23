/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.editor

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.LayoutAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates
import androidx.compose.ui.relocation.BringIntoViewModifierNode
import androidx.compose.ui.relocation.bringIntoView

/**
 * Whether the code field may scroll its cursor into view.
 *
 * The text field asks the scrolls around it to show the cursor after each new layout whose text
 * differs from the last one. Colors count as text there. So a change of colors alone (the colored
 * window of a big text moving, the theme, the language) would take the view back to the cursor,
 * away from where the user scrolled.
 *
 * A request goes through when:
 * - the last layout changed the characters (typing, paste, undo, replace), or
 * - the last layout came while the field had no focus, or the field lost the focus since. The
 *   requests that come with a new focus then go through, as the field's own do.
 *
 * The field makes its request in the layout's pass and runs it later. [laidOut] is called in that
 * pass, so the request is judged by the layout that made it.
 */
internal class CursorScrollRule {
    private var focused = false

    /** The characters of the last layout. */
    private var chars: String? = null

    /** Whether a request to show the cursor goes through now. */
    var allows = true
        private set

    /**
     * After each new layout of the field, with its characters. Comparing them costs nothing when
     * the length changed (a key typed or deleted). The whole text is read only when the length is
     * the same: a change of colors, or a replace by text as long.
     */
    fun laidOut(text: String) {
        allows = !focused || chars != text
        chars = text
    }

    /** When the field gains or loses the focus. */
    fun focusChanged(isFocused: Boolean) {
        focused = isFocused
        if (!isFocused) allows = true
    }
}

/**
 * Stands between the code field and the scrolls around it: a request to show part of the field
 * reaches them only when [rule] allows it ([CursorScrollRule.allows]).
 */
internal fun Modifier.cursorScroll(rule: CursorScrollRule): Modifier = this then CursorScrollElement(rule)

private data class CursorScrollElement(val rule: CursorScrollRule) :
    ModifierNodeElement<CursorScrollNode>() {
    override fun create() = CursorScrollNode(rule)

    override fun update(node: CursorScrollNode) {
        node.rule = rule
    }
}

private class CursorScrollNode(var rule: CursorScrollRule) :
    Modifier.Node(), BringIntoViewModifierNode, LayoutAwareModifierNode {

    // Before it is placed, this node has no position to move a request's rectangle into.
    private var placed = false

    override fun onPlaced(coordinates: LayoutCoordinates) {
        placed = true
    }

    override suspend fun bringIntoView(
        childCoordinates: LayoutCoordinates,
        boundsProvider: () -> Rect?,
    ) {
        if (!rule.allows || !isAttached || !placed) return
        // Passes the request on, in this node's coordinates.
        bringIntoView bounds@{
            if (!isAttached || !childCoordinates.isAttached) return@bounds null
            val rect = boundsProvider() ?: return@bounds null
            val offset = requireLayoutCoordinates().localBoundingBoxOf(childCoordinates, clipBounds = false).topLeft
            rect.translate(offset)
        }
    }
}
