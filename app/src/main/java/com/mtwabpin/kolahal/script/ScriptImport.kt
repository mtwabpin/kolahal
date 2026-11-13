package com.mtwabpin.kolahal.script

import android.content.Context
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository

/**
 * Brings code from outside into the script list: a picked file ([ScriptFiles]), the script folder
 * ([ScriptFolder]) or a backup ([com.mtwabpin.kolahal.data.Backup]). [add] does the steps they share:
 * - code written for a newer Kolahal is skipped;
 * - a new name makes a script; a known name gets the new code, unless it is in another language;
 * - what came with the code is applied: timers, notifications, the switch;
 * - new or changed code arrives switched off ([ImportPlan.plan]);
 * - the script's timers are set again.
 *
 * Each caller checks the name first, by its own rule ([named] for a folder and a backup), and puts
 * what [add] returns into its own words and counts.
 *
 * Make one for each import, not one to keep: it remembers the scripts this import changed.
 */
internal class ScriptImport(private val context: Context) {
    private val scripts = ScriptRepository.get(context)

    /**
     * Scripts this import created or changed, by name. A later entry with the same name (a backup's
     * second entry; `Foo.lua` beside `foo.lua` in a folder) finds the code already in place. It must
     * not count as unchanged and switch the script on.
     */
    private val changedHere = mutableSetOf<String>()

    /** What [add] did with one script. */
    sealed interface Outcome {
        /**
         * The script is in the list.
         * - [change]: what the import did to it.
         * - [off]: its code is new here, so it was left switched off, whatever came with it.
         * - [timersFailed]: its timers could not be set.
         */
        data class Imported(val change: Change, val off: Boolean, val timersFailed: Boolean = false) : Outcome

        /** Nothing was changed, because of [why]. */
        data class Skipped(val why: Why) : Outcome
    }

    /** [UPDATED]: the import changed the script's code, timers, notifications or switch. The same again is [UNCHANGED]. */
    enum class Change { CREATED, UPDATED, UNCHANGED }

    /** Why code was skipped. */
    sealed interface Why {
        /** Its `@requires` line names a newer Kolahal: [version]. It may call things this version doesn't have. */
        data class NeedsNewer(val version: String) : Why

        /** A script with its name exists in another language ([has]). */
        data class OtherLanguage(val has: ScriptLang) : Why
    }

    /**
     * Adds [code] as the script [name], or makes it the code of the script with that name.
     * - [settings]: what came with the code, as a backup's entry and the folder's kolahal.json hold it
     *   (`every`, `at`, `notify`, `enabled`). Null when nothing came: a new script then gets the
     *   timers its code suggests.
     * - [accessOff]: also switch off what [settings] lists as off under `access` (a backup). What a
     *   script may use is only allowed on this phone, so nothing is ever switched on from there.
     * - [resync]: set its timers again even when the import changed nothing. A picked file passes
     *   false: it brings no settings, so only replaced code can move a timer.
     */
    fun add(
        name: String,
        lang: ScriptLang,
        code: String,
        settings: Map<*, *>? = null,
        accessOff: Boolean = false,
        resync: Boolean = true,
    ): Outcome {
        val existing = scripts.byName(name)
        val plan = when (val p = ImportPlan.plan(existing, lang, code, BuildConfig.VERSION_NAME, changedEarlier = name in changedHere)) {
            is ImportPlan.Skip -> return Outcome.Skipped(p.why)
            is ImportPlan.Place -> p
        }
        val script = when (plan.step) {
            ImportPlan.Step.CREATE -> scripts.create(name, lang, code, enabled = false)
            // The script is switched off, to be reviewed like a new one. Its own timers stay, unless
            // its `@every` or `@at` lines changed (replaceCode handles that).
            ImportPlan.Step.REPLACE -> existing!!.also { scripts.replaceCode(it.id, code) }
            ImportPlan.Step.KEEP -> existing!!
        }
        if (plan.step != ImportPlan.Step.KEEP) changedHere += name
        settings?.let { m ->
            (m["every"] as? Number)?.toLong()?.let { scripts.setInterval(script.id, ImportPlan.minutes(it)) }
            (m["at"] as? List<*>)?.let { scripts.setAtTimes(script.id, ImportPlan.times(it)) }
            (m["notify"] as? Boolean)?.let { scripts.setNotify(script.id, it) }
            if (accessOff) (m["access"] as? Map<*, *>)?.forEach { (item, on) -> if (on == false) scripts.setAccess(script.id, item.toString(), false) }
        }
        if (plan.step == ImportPlan.Step.CREATE && settings == null) scripts.applySuggested(script.id, code)
        // The one place for the rule: new or changed code stays off until you review it and switch it
        // on. Only a script whose code you already had follows the switch that came with it.
        if (plan.off) {
            scripts.setEnabled(script.id, false)
        } else {
            (settings?.get("enabled") as? Boolean)?.let { on ->
                scripts.setEnabled(script.id, on)
                // Switched on without your approval of what it declares: a line asks for it.
                if (on) scripts.byId(script.id)?.let { ScriptAccess.askForNew(context, it) }
            }
        }
        val after = scripts.byId(script.id)
        val change = when {
            existing == null -> Change.CREATED
            after != null && ImportPlan.changed(existing, after) -> Change.UPDATED
            else -> Change.UNCHANGED
        }
        val timersSet = (!resync && plan.step != ImportPlan.Step.REPLACE) ||
            runCatching { after?.let { ScriptScheduler.sync(context, it) } }.isSuccess
        return Outcome.Imported(change, off = plan.off, timersFailed = !timersSet)
    }

    companion object {
        /**
         * The name rule of a folder and a backup: the form of a script name, and not a built-in
         * command's name. An app's name is allowed, because these are your own scripts coming back.
         */
        fun named(name: String): Boolean = ScriptNames.NAME.matches(name) && name !in CommandCatalog.RESERVED_NAMES
    }
}

/** What an import does with one script's code, decided before anything is written. No Android here. */
internal object ImportPlan {
    enum class Step { CREATE, REPLACE, KEEP }

    sealed interface Plan

    /** The code is skipped. */
    data class Skip(val why: ScriptImport.Why) : Plan

    /** The code goes in by [step]. [off]: the script is left switched off, whatever came with the code. */
    data class Place(val step: Step, val off: Boolean) : Plan

    /**
     * What to do with [code] in [lang] for a name that [existing] has now (null: no script yet).
     * - [current] is this Kolahal's version name.
     * - [changedEarlier]: the same import already created this script or replaced its code.
     *
     * New or changed code arrives switched off: a new script, replaced code, and code that is only
     * the same because this import put it there a moment ago.
     */
    fun plan(existing: Script?, lang: ScriptLang, code: String, current: String, changedEarlier: Boolean): Plan {
        ScriptMeta.requires(code)?.takeIf { ScriptMeta.isNewer(it, current) }?.let { return Skip(ScriptImport.Why.NeedsNewer(it)) }
        return when {
            existing == null -> Place(Step.CREATE, off = true)
            existing.lang != lang -> Skip(ScriptImport.Why.OtherLanguage(existing.lang))
            existing.code != code -> Place(Step.REPLACE, off = true)
            else -> Place(Step.KEEP, off = changedEarlier)
        }
    }

    /** A timer from a file, in minutes: 0 (none) to a week, and WorkManager's shortest for anything shorter. */
    fun minutes(every: Long): Int {
        val minutes = every.coerceIn(0, 7L * 24 * 60).toInt()
        return if (minutes in 1 until ScriptScheduler.MIN_INTERVAL_MIN) ScriptScheduler.MIN_INTERVAL_MIN else minutes
    }

    /** Daily times from a file: the valid ones as "HH:mm", each once, in order. */
    fun times(at: List<*>): List<String> = at.mapNotNull { ScriptScheduler.normalizeTime(it.toString()) }.distinct().sorted()

    /** What an import can change in a script: its code, timers, notifications and switch. */
    fun changed(before: Script, after: Script): Boolean =
        before.code != after.code || before.intervalMin != after.intervalMin || before.atTimes != after.atTimes ||
            before.notify != after.notify || before.enabled != after.enabled
}
