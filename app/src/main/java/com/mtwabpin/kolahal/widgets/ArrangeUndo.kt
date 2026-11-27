package com.mtwabpin.kolahal.widgets

/**
 * Undo in Arrange widgets: one step back after Close gaps, a drop that moved a widget, or a resize.
 * - [record] keeps the widgets as they were before the change ([Step]). Only the last change can be
 *   taken back: a second one takes the place of the first.
 * - Resizes of one widget in a row are one step, back to its size before the first: a size step
 *   that repeats while held is one change.
 * - [take] answers Undo: true while the step is still the last change. [expire] forgets a step
 *   whose snackbar went without Undo.
 * - A change Undo isn't offered after ([OFFERED]) forgets the step before it: taking that back now
 *   would undo this change too.
 * It uses no Android types, so it is tested on the JVM.
 */
class ArrangeUndo {
    /** What was done, and what the snackbar says of it. */
    enum class Change(val done: String) {
        CLOSE_GAPS("Gaps closed"),
        MOVE("Widget moved"),
        RESIZE("Widget resized"),
    }

    /** One step back: the widgets as they were [before] the [change] of the widget [key] (null for Close gaps). */
    class Step internal constructor(val change: Change, val key: String?, val before: List<PlacedWidget>)

    private var last: Step? = null

    /**
     * The [change] to the widget [key] turned [before] into [after]. Returns the step that Undo
     * takes back, or null when nothing moved or Undo isn't offered after it.
     */
    fun record(change: Change, key: String?, before: List<PlacedWidget>, after: List<PlacedWidget>): Step? {
        if (before == after) return null
        if (change !in OFFERED) {
            last = null
            return null
        }
        val goingOn = last?.takeIf { change == Change.RESIZE && it.change == Change.RESIZE && it.key == key }
        // A new step each time, also for a resize that goes on: the snackbar it replaces then
        // expires its own step, not this one.
        return Step(change, key, goingOn?.before ?: before).also { last = it }
    }

    /** Undo for [step]: true, and the step is used up, while it is the last change. */
    fun take(step: Step): Boolean {
        if (last !== step) return false
        last = null
        return true
    }

    /** The snackbar for [step] went without Undo. */
    fun expire(step: Step) {
        if (last === step) last = null
    }

    /** A change that can't be taken back (a widget removed, or moved to another page): the step before it is forgotten. */
    fun forget() {
        last = null
    }

    companion object {
        /** The changes Undo is offered after. */
        val OFFERED = setOf(Change.CLOSE_GAPS, Change.MOVE, Change.RESIZE)

        /**
         * [now] with every widget that [before] has back on its page, at its place and its size there.
         * Its look, color and the rest stay as they are now. A widget added since stays where it is;
         * one removed since stays removed. Entries waiting to be added again are left as they are.
         * A widget whose page then is no longer one of [pages] (null: every page is still there) stays
         * where it is now.
         */
        fun restored(
            now: List<PlacedWidget>,
            before: List<PlacedWidget>,
            pages: Set<String>? = null,
        ): List<PlacedWidget> {
            val was = before.associateBy { it.key }
            return now.map { w ->
                // An entry waiting to be added again has no place; its key holds its order.
                val b = was[w.key]?.takeUnless { w.pending }
                    ?.takeIf { pages == null || it.location in pages } ?: return@map w
                w.copy(
                    location = b.location,
                    column = b.column,
                    topDp = b.topDp,
                    span = b.span,
                    heightDp = b.heightDp,
                    order = b.order,
                )
            }
        }
    }
}
