package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.Stores
import android.content.Context
import com.mtwabpin.kolahal.data.KvStore
import com.mtwabpin.kolahal.widgets.WidgetRef

/**
 * The values you gave a script's `@option`s ([ScriptMeta.options]), stored by script name, apart
 * from its code. A library update keeps them, and backups include them. An option reads as its
 * default if you never set it, or if you set it to a choice its code no longer offers.
 *
 * A script can have several widgets on the page, and each further one can have values of its own.
 * - `widget` is the widget's id ([WidgetRef.id]). "" is the script itself: its first widget, and
 *   every run that is not for a widget.
 * - A further widget keeps only what you set for it, in a scope of its own (`option:weather#2`).
 *   Every other option follows the script's value.
 */
class ScriptOptions(private val context: Context) {
    private fun store(script: String, widget: String = "") = KvStore(context, scope(script, widget))

    /** What you set for the further widget [widget] only. Nothing for the script itself. */
    private fun forWidget(script: String, widget: String): Map<String, String> = if (widget.isEmpty()) emptyMap() else store(script, widget).all()

    /** The value of every declared option: the widget's, else the script's, else its default. */
    fun values(script: String, declared: List<ScriptMeta.Option>, widget: String = ""): Map<String, String> =
        values(declared, store(script).all(), forWidget(script, widget))

    /** The declared options that you set: for the script itself, or with [widget] for that widget only. */
    fun changed(script: String, declared: List<ScriptMeta.Option>, widget: String = ""): Set<String> =
        changed(declared, store(script, widget).all())

    /** Sets [option] to [value], for the script or for its widget [widget]. Null resets it: to its default, or to the script's value. */
    fun set(script: String, option: String, value: String?, widget: String = "") = store(script, widget).set(option, value)

    /**
     * Forgets everything you set for the further widget [widget]: it is taken off the page, and the
     * next widget with its id starts clean. The script's own values are not a widget's: with ""
     * this changes nothing.
     */
    fun clear(script: String, widget: String) {
        if (widget.isEmpty()) return
        val store = store(script, widget)
        store.setAll(store.all().mapValues { null })
    }

    companion object {
        /** The scope of the script's own values, or of what you set for its further widget [widget]. */
        fun scope(script: String, widget: String = "") = Stores.OPTION_PREFIX + WidgetRef(script, widget).key

        /**
         * What the scope of every further widget of [script] starts with: `option:weather#`. A
         * rename moves these scopes with the script's own, and a delete removes them.
         */
        fun furtherScopes(script: String) = Stores.OPTION_PREFIX + WidgetRef.further(script)

        /** Whether [value] can be [option]'s: any text, or one of its choices when it has them. */
        private fun takes(option: ScriptMeta.Option, value: String?): Boolean = value != null && (option.choices.isEmpty() || value in option.choices)

        /**
         * The value of every option in [declared]. [own] is what you set for the script, and
         * [forWidget] what you set for one widget. The widget's value wins, then the script's, then
         * the default. A value that is no longer one of the choices counts as not set.
         */
        internal fun values(declared: List<ScriptMeta.Option>, own: Map<String, String>, forWidget: Map<String, String> = emptyMap()): Map<String, String> =
            declared.associate { o -> o.name to (listOf(forWidget[o.name], own[o.name]).firstOrNull { takes(o, it) } ?: o.default) }

        /** The options in [declared] that have a value of their own in [set]. */
        internal fun changed(declared: List<ScriptMeta.Option>, set: Map<String, String>): Set<String> =
            declared.filter { takes(it, set[it.name]) }.mapTo(HashSet()) { it.name }
    }
}
