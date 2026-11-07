package com.mtwabpin.kolahal.script

import android.content.Context
import com.mtwabpin.kolahal.data.KvStore

/**
 * The values you gave a script's `@option`s ([ScriptMeta.options]), stored by script name, apart
 * from its code. A library update keeps them, and backups include them. An option reads as its
 * default if you never set it, or if you set it to a choice its code no longer offers.
 */
class ScriptOptions(private val context: Context) {
    private fun store(script: String) = KvStore(context, scope(script))

    /** The value of every declared option: yours, or its default. */
    fun values(script: String, declared: List<ScriptMeta.Option>): Map<String, String> {
        val set = store(script).all()
        return declared.associate { o -> o.name to (set[o.name]?.takeIf { o.choices.isEmpty() || it in o.choices } ?: o.default) }
    }

    /** The declared options that you set. */
    fun changed(script: String, declared: List<ScriptMeta.Option>): Set<String> {
        val set = store(script).all()
        return declared.filter { o -> set[o.name]?.let { o.choices.isEmpty() || it in o.choices } == true }.mapTo(HashSet()) { it.name }
    }

    /** Sets [option] to [value]. Null resets it to its default. */
    fun set(script: String, option: String, value: String?) = store(script).set(option, value)

    companion object {
        const val SCOPE_PREFIX = "option:"
        fun scope(script: String) = SCOPE_PREFIX + script
    }
}
