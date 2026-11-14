package com.mtwabpin.kolahal.script

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The changes to a script's stored access that are made from the access itself: the `widget` item,
 * which follows the placed list, and a switch set by hand. Each is a function of what is stored at
 * that moment. The script list applies it in one step with the read (a transaction), so two changes
 * at the same time are applied one after the other, in either order. The tests here show that
 * either order keeps both.
 */
class ScriptAccessTest {
    private val widget = ScriptAccess.WIDGET

    /** What the widgets page writes when the script's widget is placed or removed. */
    private fun placed(on: Boolean) = { stored: Map<String, Boolean> -> ScriptAccess.following(stored, on) ?: stored }

    /** What `script allow|deny` and the sheet's switches write. */
    private fun switched(item: String, on: Boolean) = { stored: Map<String, Boolean> -> stored + (item to on) }

    /** The stored text between two writes, as the database holds it. */
    private fun stored(values: Map<String, Boolean>) = ScriptAccess.parse(ScriptAccess.encode(values))

    @Test fun theWidgetItemFollowsThePlacedList() {
        assertEquals(mapOf("apps" to true, widget to true), ScriptAccess.following(mapOf("apps" to true), placed = true))
        assertEquals(mapOf("apps" to false, widget to false), ScriptAccess.following(mapOf("apps" to false, widget to true), placed = false))
        assertEquals(mapOf(widget to true), ScriptAccess.following(mapOf(widget to false), placed = true))
        // It says so already: nothing to write.
        assertNull(ScriptAccess.following(mapOf(widget to true), placed = true))
        assertNull(ScriptAccess.following(mapOf(widget to false, "apps" to true), placed = false))
        // A script whose widget was never placed gets no item: a missing one is off.
        assertNull(ScriptAccess.following(emptyMap(), placed = false))
        assertNull(ScriptAccess.following(mapOf("internet" to false), placed = false))
    }

    /**
     * A widget is placed while you switch the script's internet off. Whichever is written first, the
     * other keeps it. Before, each wrote the whole access as it had read it earlier, so the second
     * write undid the first: `script deny … internet` could be lost.
     */
    @Test fun twoChangesAtTheSameTimeBothStay() {
        val start = mapOf("apps" to true, "internet" to true)
        val both = mapOf("apps" to true, "internet" to false, widget to true)
        val place = placed(true)
        val deny = switched("internet", false)
        assertEquals(both, deny(stored(place(start))))
        assertEquals(both, place(stored(deny(start))))

        // A widget removed while two switches are set: every order of the three gives the same.
        val writes = listOf(placed(false), switched("apps", false), switched("clipboard", true))
        val orders = listOf(listOf(0, 1, 2), listOf(0, 2, 1), listOf(1, 0, 2), listOf(1, 2, 0), listOf(2, 0, 1), listOf(2, 1, 0))
        val ends = orders.map { order -> order.fold(both) { now, i -> stored(writes[i](now)) } }
        assertEquals(List(6) { mapOf("apps" to false, "internet" to false, "clipboard" to true, widget to false) }, ends)
    }

    /** The lost change, as it was: both read first, then each writes what it read with its own item changed. */
    @Test fun aWriteFromAnOlderReadWouldLoseTheOther() {
        val read = mapOf("internet" to true)
        val placedWrite = placed(true)(read)
        val denyWrite = switched("internet", false)(read)
        // Whichever was written last stayed, without the other's change.
        assertEquals(mapOf("internet" to true, widget to true), placedWrite)
        assertEquals(mapOf("internet" to false), denyWrite)
        // Made from what is stored now, the second write has both.
        assertEquals(mapOf("internet" to false, widget to true), placed(true)(denyWrite))
    }

    /** Undo puts back an older copy of the access. The widget item stays as it is now: the placed list decides it. */
    @Test fun anOlderCopyKeepsTheWidgetItemOfNow() {
        // Placed since the copy was taken.
        assertEquals(mapOf("apps" to true, widget to true), ScriptAccess.keepingWidget(mapOf("apps" to true, widget to false), now = mapOf(widget to true)))
        assertEquals(mapOf("apps" to true, widget to true), ScriptAccess.keepingWidget(mapOf("apps" to true), now = mapOf("apps" to false, widget to true)))
        // Removed since.
        assertEquals(mapOf("apps" to true, widget to false), ScriptAccess.keepingWidget(mapOf("apps" to true, widget to true), now = mapOf(widget to false)))
        // No item now: none after.
        assertEquals(mapOf("apps" to true), ScriptAccess.keepingWidget(mapOf("apps" to true, widget to true), now = emptyMap()))
        // Nothing changed since: the copy as it was.
        assertEquals(mapOf("apps" to false, widget to true), ScriptAccess.keepingWidget(mapOf("apps" to false, widget to true), now = mapOf("apps" to true, widget to true)))
    }
}
