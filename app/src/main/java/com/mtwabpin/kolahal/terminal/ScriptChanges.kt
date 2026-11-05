package com.mtwabpin.kolahal.terminal

import android.content.Context
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptNames
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.script.ScriptTemplates

/**
 * Changes to a script that the `script` command ([ScriptCommands]) and the script screens
 * ([ScriptActions]) both make, written once: what each one also switches on, asks for and
 * schedules. They run on the caller's thread; what to tell the user stays with the caller.
 */
internal class ScriptChanges(private val context: Context) {
    private val scripts = ScriptRepository.get(context)
    private val logs = LogRepository.get(context)

    /**
     * Its timer: [minutes], Android's 15 at the least, or 0 for none. A timer switches the script
     * on, but allows nothing it asks for: that stays a tap away. Returns the minutes it runs every.
     */
    fun interval(s: Script, minutes: Int): Int {
        val every = if (minutes > 0) minutes.coerceAtLeast(ScriptScheduler.MIN_INTERVAL_MIN) else 0
        scripts.setInterval(s.id, every)
        if (every > 0) switchOn(s)
        sync(s)
        return every
    }

    /** Its daily times, "HH:mm" as [CommandParsing.dailyTimes] reads them; times switch it on as a timer does. */
    fun times(s: Script, times: List<String>) {
        scripts.setAtTimes(s.id, times)
        if (times.isNotEmpty()) switchOn(s)
        sync(s)
    }

    /** Switched on or off by you: on is your go-ahead for what it declares and nobody decided yet. */
    fun enabled(s: Script, on: Boolean) {
        scripts.setEnabled(s.id, on, grant = on)
        sync(s)
    }

    /** One access switch; pinning or unpinning shows at the top of home at once. */
    fun access(s: Script, item: String, on: Boolean) {
        scripts.setAccess(s.id, item, on)
        if (item == ScriptAccess.HEADER) logs.onStatusChanged()
    }

    /** Renames [s] to [to], or says why it can't be its name. */
    fun rename(s: Script, to: String): String? {
        ScriptNames.invalid(to) { scripts.byName(it) != null }?.let { return it }
        scripts.rename(s.id, to)
        logs.onStatusChanged()
        return null
    }

    /** A new script [name] from its language's template, a command's with [asCommand]; the caller checks the name. */
    fun create(name: String, lang: ScriptLang, asCommand: Boolean): Script =
        scripts.create(name, lang, if (asCommand) ScriptTemplates.command(lang, name) else ScriptTemplates.forLang(lang, name))

    /** Installs [entry] from [library], or updates your copy; a key of its that isn't set gets a line. */
    fun install(library: ScriptLibrary, entry: ScriptLibrary.Entry): Script =
        library.install(entry).also { ScriptAccess.askForSecrets(context, it) }

    private fun switchOn(s: Script) {
        if (s.enabled) return
        scripts.setEnabled(s.id, true)
        scripts.byId(s.id)?.let { ScriptAccess.askForNew(context, it) }
    }

    private fun sync(s: Script) {
        scripts.byId(s.id)?.let { ScriptScheduler.sync(context, it) }
    }
}
