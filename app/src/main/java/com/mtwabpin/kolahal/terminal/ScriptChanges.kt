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
 * Script changes that both the `script` command ([ScriptCommands]) and the script screens
 * ([ScriptActions]) make. They are written once here, together with what each change also
 * switches on, asks for and schedules.
 *
 * These functions run on the caller's thread. The caller decides what to tell the user.
 */
internal class ScriptChanges(private val context: Context) {
    private val scripts = ScriptRepository.get(context)
    private val logs = LogRepository.get(context)

    /**
     * Sets the script's timer to [minutes], or turns it off with 0. Android's minimum is 15 minutes.
     * A timer switches the script on, but it does not grant any access the script asks for.
     * The user grants that with a tap. Returns the interval in minutes.
     */
    fun interval(s: Script, minutes: Int): Int {
        val every = if (minutes > 0) minutes.coerceAtLeast(ScriptScheduler.MIN_INTERVAL_MIN) else 0
        scripts.setInterval(s.id, every)
        if (every > 0) switchOn(s)
        sync(s)
        return every
    }

    /** Sets the script's daily times ("HH:mm", as [CommandParsing.dailyTimes] reads them). Like a timer, times switch the script on. */
    fun times(s: Script, times: List<String>) {
        scripts.setAtTimes(s.id, times)
        if (times.isNotEmpty()) switchOn(s)
        sync(s)
    }

    /**
     * The user switches the script on or off. Switching it on also grants the access the script
     * declares that the user has not allowed or denied yet.
     */
    fun enabled(s: Script, on: Boolean) {
        scripts.setEnabled(s.id, on, grant = on)
        sync(s)
    }

    /** Sets one access switch. Pinning or unpinning updates the top of home at once. */
    fun access(s: Script, item: String, on: Boolean) {
        scripts.setAccess(s.id, item, on)
        if (item == ScriptAccess.HEADER) logs.onStatusChanged()
    }

    /** Renames [s] to [to]. If [to] is not a valid name, returns the reason instead. */
    fun rename(s: Script, to: String): String? {
        ScriptNames.invalid(to) { scripts.byName(it) != null }?.let { return it }
        scripts.rename(s.id, to)
        logs.onStatusChanged()
        return null
    }

    /**
     * Creates a new script [name] from its language's template. With [asCommand], it uses the
     * command template. The caller must check the name first.
     */
    fun create(name: String, lang: ScriptLang, asCommand: Boolean): Script =
        scripts.create(name, lang, if (asCommand) ScriptTemplates.command(lang, name) else ScriptTemplates.forLang(lang, name))

    /** Installs [entry] from [library], or updates your copy. For each of its keys that is not set, a line asks for it. */
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
