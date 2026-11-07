package com.mtwabpin.kolahal.script

import android.content.Context
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.terminal.CommandCatalog

/** Names that a script from a file or from the shared catalog may not take. */
object ScriptNames {
    private val PUNCTUATION = Regex("[^a-z0-9]")

    /**
     * Why [name] is taken (a built-in command or an app's name), or null.
     * A command script runs before an app with the same name would open. So typing `gpay` could run
     * a script instead of the app.
     */
    fun clash(context: Context, name: String): String? {
        if (name in CommandCatalog.RESERVED_NAMES) return "'$name' is a built-in command"
        val bare = name.replace(PUNCTUATION, "")
        val app = AppRepository.get(context).apps.value.firstOrNull { it.label.lowercase().replace(PUNCTUATION, "") == bare }
        return app?.let { "'$name' is the name of the app ${it.label}" }
    }

    /** The allowed form of a script name. */
    val NAME = Regex("^[a-z0-9_-]{1,40}$")

    /** Why [name] can't be the name of your script, or null. [taken] tells whether a script already has the name. */
    fun invalid(name: String, taken: (String) -> Boolean): String? = when {
        !NAME.matches(name) -> "Script names: lowercase letters, digits, - and _, up to 40"
        name in CommandCatalog.RESERVED_NAMES -> "'$name' is a built-in command"
        taken(name) -> "There is already a script named $name"
        else -> null
    }
}
