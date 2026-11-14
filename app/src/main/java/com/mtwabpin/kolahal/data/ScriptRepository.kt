package com.mtwabpin.kolahal.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import androidx.core.database.sqlite.transaction
import com.mtwabpin.kolahal.script.HostApi
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptCatalog
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.script.SecretStore
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetStates

enum class ScriptLang(val ext: String) {
    LUA("lua"),
    JS("js");

    companion object {
        fun of(value: String): ScriptLang? = when (value.lowercase().removePrefix(".")) {
            "lua" -> LUA
            "js", "javascript" -> JS
            else -> null
        }
    }
}

data class Script(
    val id: Long,
    val name: String,
    val lang: ScriptLang,
    val code: String,
    /** 0 means manual only. WorkManager enforces a 15-minute minimum for periodic runs. */
    val intervalMin: Int,
    val enabled: Boolean,
    val notify: Boolean,
    val lastRun: Long?,
    val lastStatus: String?,
    /** Daily run times as "HH:mm", in addition to (or instead of) the interval. */
    val atTimes: List<String> = emptyList(),
    /** The user's access switches: item to on or off ([ScriptAccess]). A missing item is undecided. */
    val access: Map<String, Boolean> = emptyMap(),
    /** When its code or name last changed. Null for a script not changed since Kolahal 0.5.1. */
    val changedAt: Long? = null,
) {
    val grants: ScriptAccess.Grants get() = ScriptAccess.grants(code, access)
}

class ScriptRepository private constructor(private val context: Context) {
    private val db get() = Db.get(context)

    fun all(): List<Script> = query(null, null)

    fun byName(name: String): Script? = query("name = ?", arrayOf(name)).firstOrNull()

    fun byId(id: Long): Script? = query("id = ?", arrayOf(id.toString())).firstOrNull()

    /**
     * Creates a new script.
     * - A script created switched on (one of yours, a library install) may use what it declares.
     * - Code from a file, a folder or a backup comes with [enabled] false, and asks when switched on.
     */
    fun create(name: String, lang: ScriptLang, code: String, enabled: Boolean = true): Script {
        // Nothing from an earlier script with this name carries over: no status line a run left behind, no widget content, no values.
        StatusLines.clear(context, name)
        WidgetStates.forget(context, name)
        db.writableDatabase.delete("kv", "scope = ?", arrayOf(SecretStore.scope(name)))
        db.writableDatabase.insertOrThrow("scripts", null, ContentValues().apply {
            put("name", name)
            put("lang", lang.ext)
            put("code", code)
            put("enabled", if (enabled) 1 else 0)
            val placed = placedAccess(name, code)
            if (enabled || placed.isNotEmpty()) put("access", ScriptAccess.encode((if (enabled) ScriptAccess.allowUndecided(code, emptyMap()) else emptyMap()) + placed))
            put("changed_at", System.currentTimeMillis())
        })
        version.incrementAndGet()
        return byName(name)!!
    }

    /**
     * Whether a widget is already placed under [name], for a script whose [code] has a `@widget`
     * line: one a backup restored before its script, or one a deleted script left on the page. It is
     * this script's widget, so its `widget` access is on: the placed list decides it. The list
     * belongs to the main process.
     */
    private fun widgetPlaced(name: String, code: String): Boolean =
        ScriptMeta.widget(code) != null && runCatching { widgetsPage?.isPlaced(WidgetSource.script(name)) }.getOrNull() == true

    /**
     * The widgets page's list of placed widgets. It belongs to the main process: null in the
     * `:script` process, which can't follow it. This is the only way from here to [WidgetHost], for
     * two things: whether a name's widget is placed ([widgetPlaced]), and a rename ([rename]).
     * The access itself is written from there, never asked for from here ([setWidgetPlaced]).
     */
    private val widgetsPage: WidgetHost? get() = if (Singleton.inScriptProcess) null else WidgetHost.get(context)

    /**
     * The `widget` access that [code] gets under [name]: on when its widget is placed ([widgetPlaced]),
     * nothing otherwise. A new script starts with it, and so does code that gains its `@widget` line.
     */
    private fun placedAccess(name: String, code: String): Map<String, Boolean> =
        if (widgetPlaced(name, code)) mapOf(ScriptAccess.WIDGET to true) else emptyMap()

    /**
     * Saves new code for a script.
     * - [byUser] (typed in the editor): for a switched-on script, what the code newly declares with
     *   `@uses` is allowed, because you wrote it. What an update left for you to decide stays undecided.
     * - Code from elsewhere (a library or shared-script update): what it newly declares stays off,
     *   `@on` events included, and a line offers to allow it.
     * - What was allowed and the code no longer declares is forgotten. If a later version declares it
     *   again, it is new again: allowed for your own code, asked for code from elsewhere. Before, an
     *   update could drop a `@uses` line and a later one bring it back, allowed without a word. What
     *   you switched off stays off.
     * - A pin at the top ([ScriptAccess.HEADER]) stays, even while the code doesn't call `status()`:
     *   it is your choice of where its line shows, not something the code may do.
     * - Its place on the widgets page ([ScriptAccess.WIDGET]) stays in the same way: the placed list decides it.
     *   Code that gains a `@widget` line while a widget of its name waits on the page has it on at once.
     */
    fun saveCode(id: Long, code: String, byUser: Boolean = false) {
        // The placed list is the main process's own, so it is read before the database's step, not inside it.
        val placed = placedAccess(byId(id)?.name ?: return, code)
        var asks = emptyList<String>()
        val s = change(id) { found ->
            val declared = ScriptAccess.items(code).toSet()
            val kept = found.copy(access = found.access.filter { (item, on) -> !on || item in declared || item == ScriptAccess.HEADER || item == ScriptAccess.WIDGET })
            val newUses = ScriptMeta.uses(code) - ScriptMeta.uses(kept.code) - kept.access.keys
            val newEvents = ScriptMeta.events(code) - ScriptMeta.events(kept.code).toSet() - kept.access.keys
            val newInternet = newInternet(kept, code)
            val access = when {
                byUser -> if (kept.enabled) kept.access + newUses.associateWith { true } else kept.access
                else -> kept.access + (newEvents + newInternet).associateWith { false }
            } + placed
            put("code", code)
            put("access", ScriptAccess.encode(access))
            put("changed_at", System.currentTimeMillis())
            asks = newUses.toList() + newEvents + newInternet
        } ?: return
        // Code that no longer sets a status line leaves none behind on its card, in `status` or pinned.
        if (ScriptMeta.setsStatus(s.code) && !ScriptMeta.setsStatus(code)) StatusLines.clear(context, s.name)
        // The same for a widget: code without its `@widget` line leaves no content behind.
        if (ScriptMeta.widget(s.code) != null && ScriptMeta.widget(code) == null) WidgetStates.forget(context, s.name)
        forgetRemovedSecrets(s.name, code)
        if (!byUser && s.enabled) ScriptAccess.askToAllow(context, s.name, asks, "now requests")
    }

    /**
     * Reads the script [id] and writes what [block] puts for it, in one transaction: no other write
     * to the database comes between the read and the write, from this process or the `:script` one.
     * - Every write that makes the access from the stored one goes through here. So a change made
     *   beside it (a switch set by hand, a widget placed or removed) is never written over with
     *   what was read before it.
     * - [block] gets the script as stored now, and only decides what to write. It writes nothing
     *   itself and tells nobody: what follows from the change comes after this returns.
     * Returns the script as it was read, or null when there is no such script.
     */
    private fun change(id: Long, block: ContentValues.(Script) -> Unit): Script? {
        var wrote = false
        val read = db.writableDatabase.transaction {
            val s = byId(id) ?: return@transaction null
            val values = ContentValues().apply { block(s) }
            if (values.size() > 0) {
                update("scripts", values, "id = ?", arrayOf(id.toString()))
                wrote = true
            }
            s
        }
        if (wrote) version.incrementAndGet()
        return read
    }

    /**
     * The internet item, if [code] uses it, [s]'s current code doesn't, and it isn't decided yet.
     * Code you didn't write that starts making requests starts with internet off, like a new `@uses`.
     * So an update to a script that was allowed your clipboard or notifications can't send them out
     * without asking.
     */
    private fun newInternet(s: Script, code: String): List<String> =
        listOf(ScriptAccess.INTERNET).filter { it in ScriptAccess.items(code) && it !in ScriptAccess.items(s.code) && it !in s.access }

    /** Forgets the values of `@secret` lines that the code no longer has, instead of keeping them unseen. */
    private fun forgetRemovedSecrets(script: String, code: String) {
        SecretStore(context).forgetUndeclared(script, ScriptMeta.secrets(code).map { it.name })
    }

    /**
     * Code from a file, a folder or a backup replaces an existing script's code.
     * - The script is switched off.
     * - What it was allowed goes back to undecided, so switching it on asks again. What you switched
     *   off stays off. Its place on the widgets page ([ScriptAccess.WIDGET]) stays: the placed list decides it.
     *   New code with a `@widget` line has it on when a widget of its name waits on the page.
     * - Internet that it didn't use before starts off.
     * - Its secret values ([com.mtwabpin.kolahal.script.SecretStore]) stay for the sites they were set
     *   for, except for `@secret` lines it no longer has.
     */
    fun replaceCode(id: Long, code: String) {
        val placed = placedAccess(byId(id)?.name ?: return, code)
        val s = change(id) { now ->
            put("code", code)
            put("enabled", 0)
            put("access", ScriptAccess.encode(now.access.filter { (item, on) -> !on || item == ScriptAccess.WIDGET } + newInternet(now, code).associateWith { false } + placed))
            put("changed_at", System.currentTimeMillis())
        } ?: return
        // Its timers follow a changed `@every` or `@at` line, as after an edit. A timer set in its sheet stays.
        setInterval(id, ScriptMeta.intervalAfter(s.code, code, s.intervalMin))
        setAtTimes(id, ScriptMeta.timesAfter(s.code, code, s.atTimes))
        forgetRemovedSecrets(s.name, code)
        StatusLines.clear(context, s.name)
        // What its widget showed came from the old code. The widget stays placed, and says the script is off.
        WidgetStates.forget(context, s.name)
    }

    /**
     * Changes whenever the script list would change: writes from this process ([version]) and runs
     * that the script process records. It is cheap because it reads no code. Home uses it to skip
     * reading every script again.
     */
    fun fingerprint(): String = db.readableDatabase.rawQuery(
        "SELECT COUNT(*), COALESCE(MAX(last_run), 0), COALESCE(SUM(enabled), 0), COALESCE(MAX(id), 0) FROM scripts", null,
    ).use { c ->
        c.moveToFirst()
        "${version.get()}:${c.getLong(0)}:${c.getLong(1)}:${c.getLong(2)}:${c.getLong(3)}"
    }

    /**
     * Puts back the code, access and change time that [before] had. Undo after an update or a reset
     * uses it, so the copy is exactly as it was. Its timers, switches and options were never changed.
     * Its place on the widgets page stays as it is now: the placed list decides it, and the widget
     * may have been placed or removed since [before] was read.
     */
    fun putBack(before: Script) {
        change(before.id) { now ->
            put("code", before.code)
            put("access", ScriptAccess.encode(ScriptAccess.keepingWidget(before.access, now.access)))
            if (before.changedAt == null) putNull("changed_at") else put("changed_at", before.changedAt)
        }
    }

    fun setInterval(id: Long, minutes: Int) = update(id) { put("interval_min", minutes) }

    /**
     * Switches a script on or off. [grant] is the user's approval for what the script declares that
     * nobody decided yet (the Scripts screen's dialog, `script on`, an install the user started).
     * Without it, those items stay off, and the caller offers a line to allow them ([ScriptAccess.askForNew]).
     */
    fun setEnabled(id: Long, enabled: Boolean, grant: Boolean = false) {
        val s = change(id) { now ->
            put("enabled", if (enabled) 1 else 0)
            if (enabled && grant) put("access", ScriptAccess.encode(ScriptAccess.allowUndecided(now.code, now.access)))
        } ?: return
        // Clear its status line either way. If switched off, the line would come back stale. If switched on,
        // a line left by a run while it was off would show.
        if (enabled != s.enabled) {
            StatusLines.clear(context, s.name)
            // Its widget's content goes for the same reasons. The widget stays placed.
            WidgetStates.forget(context, s.name)
        }
    }

    /** Applies the timers and notifications that [code] suggests (`@every`, `@at`, `@notify`), for a script just added. */
    fun applySuggested(id: Long, code: String) {
        ScriptMeta.every(code)?.let { setInterval(id, it.coerceAtLeast(ScriptScheduler.MIN_INTERVAL_MIN)) }
        ScriptMeta.at(code).takeIf { it.isNotEmpty() }?.let { setAtTimes(id, it) }
        if (ScriptMeta.notifies(code)) setNotify(id, true)
    }

    /**
     * Switches one access item. See [ScriptAccess].
     * - The item is changed in the access as stored at that moment ([change]), so two switches set
     *   at the same time are both kept.
     * - [ScriptAccess.WIDGET] is not switched here, and a call for it changes nothing: the placed
     *   list sets it ([setWidgetPlaced]).
     */
    fun setAccess(id: Long, item: String, allowed: Boolean) {
        if (item == ScriptAccess.WIDGET) return
        change(id) { s -> put("access", ScriptAccess.encode(s.access + (item to allowed))) }
    }

    /**
     * Makes the `widget` access of the script [name] say [placed], as the placed list does. The list
     * is the truth. This item is its copy in the database, where the `:script` process can read it.
     * - Only [WidgetHost] calls it, from the list. The copy never goes the other way.
     * - It writes only when the item differs, and keeps every other item ([ScriptAccess.following]).
     * Returns the script as it is then, or null when no script has the name.
     */
    fun setWidgetPlaced(name: String, placed: Boolean): Script? {
        val id = byName(name)?.id ?: return null
        var access: Map<String, Boolean>? = null
        val s = change(id) { now ->
            // The name is checked again: this is the row as stored now, and a rename may have come between.
            if (now.name == name) access = ScriptAccess.following(now.access, placed)?.also { put("access", ScriptAccess.encode(it)) }
        } ?: return null
        return if (s.name != name) null else s.copy(access = access ?: s.access)
    }

    /**
     * Makes every script's `widget` access say what the placed list says: on for the scripts named
     * in [placed], off for every other one. It writes only the rows that differ, each in its own step.
     */
    fun followPlaced(placed: Set<String>) {
        for (s in all()) {
            if (ScriptAccess.following(s.access, s.name in placed) == null) continue
            change(s.id) { now -> ScriptAccess.following(now.access, now.name in placed)?.let { put("access", ScriptAccess.encode(it)) } }
        }
    }

    fun setAtTimes(id: Long, times: List<String>) = update(id) {
        if (times.isEmpty()) putNull("at_times") else put("at_times", times.joinToString(","))
    }

    fun setNotify(id: Long, notify: Boolean) = update(id) { put("notify", if (notify) 1 else 0) }

    /** Records the last run's time and result ([status], cut to [STATUS_MAX], because a script can make its error text as long as it likes). */
    fun recordRun(id: Long, status: String) = update(id) {
        put("last_run", System.currentTimeMillis())
        put("last_status", status.take(STATUS_MAX))
    }

    /**
     * Renames a script. Check the name first (`ScriptNames.invalid`).
     * - Its secrets and its status line (with its bar and refresh), stored by name, move with it.
     * - A copy from the library or from Get more scripts becomes yours. It no longer gets their updates.
     * - Lines already in the log keep the old name.
     */
    fun rename(id: Long, to: String) {
        val s = byId(id) ?: return
        if (s.name == to) return
        val w = db.writableDatabase
        w.transaction {
            update("scripts", ContentValues().apply { put("name", to); put("changed_at", System.currentTimeMillis()) }, "id = ?", arrayOf(id.toString()))
            delete("kv", "scope = ?", arrayOf(SecretStore.scope(to)))
            update("kv", ContentValues().apply { put("scope", SecretStore.scope(to)) }, "scope = ?", arrayOf(SecretStore.scope(s.name)))
            delete("kv", "scope = ?", arrayOf(com.mtwabpin.kolahal.script.ScriptOptions.scope(to)))
            update("kv", ContentValues().apply { put("scope", com.mtwabpin.kolahal.script.ScriptOptions.scope(to)) }, "scope = ?", arrayOf(com.mtwabpin.kolahal.script.ScriptOptions.scope(s.name)))
            delete("kv", "scope = ? AND k = ?", arrayOf(Stores.STATUS, to))
            update("kv", ContentValues().apply { put("k", to) }, "scope = ? AND k = ?", arrayOf(Stores.STATUS, s.name))
            delete("kv", "scope = ? AND k = ?", arrayOf(Stores.STATUS_EXTRAS, to))
            update("kv", ContentValues().apply { put("k", to) }, "scope = ? AND k = ?", arrayOf(Stores.STATUS_EXTRAS, s.name))
            // What its widget shows, and when it was set.
            for (scope in listOf(Stores.WIDGET, Stores.WIDGET_META)) {
                delete("kv", "scope = ? AND k = ?", arrayOf(scope, to))
                update("kv", ContentValues().apply { put("k", to) }, "scope = ? AND k = ?", arrayOf(scope, s.name))
            }
        }
        forgetOrigin(s.name)
        version.incrementAndGet()
        // Its placed widget follows the name. The list of placed widgets belongs to the main process.
        runCatching { widgetsPage?.renameScript(s.name, to) }
        WidgetStates.bump()
    }

    /** Stops treating the script as the library's or Get more scripts' copy, so it gets no updates. */
    private fun forgetOrigin(name: String) {
        ScriptLibrary(context).forget(name)
        ScriptCatalog(context).forget(name)
    }

    fun delete(id: Long) {
        // Its timers, secrets, options, status line and widget content are deleted with it.
        ScriptScheduler.cancel(context, id)
        byId(id)?.let {
            SecretStore(context).clear(it.name)
            db.writableDatabase.delete("kv", "scope = ?", arrayOf(com.mtwabpin.kolahal.script.ScriptOptions.scope(it.name)))
            StatusLines.clear(context, it.name)
            // What its widget showed goes. The widget stays on the page and says the script was removed,
            // until you remove it or a script takes the name again.
            WidgetStates.forget(context, it.name)
            forgetOrigin(it.name)
        }
        db.writableDatabase.delete("scripts", "id = ?", arrayOf(id.toString()))
        db.writableDatabase.delete("kv", "scope = ?", arrayOf(scopeOf(id)))
        version.incrementAndGet()
    }

    private fun update(id: Long, block: ContentValues.() -> Unit) {
        db.writableDatabase.update("scripts", ContentValues().apply(block), "id = ?", arrayOf(id.toString()))
        version.incrementAndGet()
    }

    private fun query(where: String?, args: Array<String>?): List<Script> {
        val out = ArrayList<Script>()
        db.readableDatabase.query("scripts", null, where, args, null, null, "name").use { c ->
            while (c.moveToNext()) c.toScript()?.let(out::add)
        }
        return out
    }

    private fun Cursor.toScript(): Script? {
        val lang = ScriptLang.of(getString(getColumnIndexOrThrow("lang"))) ?: return null
        val lastRunIdx = getColumnIndexOrThrow("last_run")
        return Script(
            id = getLong(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            lang = lang,
            code = getString(getColumnIndexOrThrow("code")),
            intervalMin = getInt(getColumnIndexOrThrow("interval_min")),
            enabled = getInt(getColumnIndexOrThrow("enabled")) != 0,
            notify = getInt(getColumnIndexOrThrow("notify")) != 0,
            lastRun = if (isNull(lastRunIdx)) null else getLong(lastRunIdx),
            lastStatus = getString(getColumnIndexOrThrow("last_status")),
            atTimes = getString(getColumnIndexOrThrow("at_times"))?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
            access = ScriptAccess.parse(getString(getColumnIndexOrThrow("access"))),
            changedAt = getColumnIndexOrThrow("changed_at").let { if (isNull(it)) null else getLong(it) },
        )
    }

    companion object : Singleton<ScriptRepository>(::ScriptRepository) {
        /**
         * The longest last-run status kept (an error's first line). A row larger than the 2 MB cursor
         * window can't be read back, and every script list would fail on it.
         */
        const val STATUS_MAX = 200

        fun scopeOf(scriptId: Long) = Stores.SCRIPT_PREFIX + scriptId

        /** Incremented on every write in this process. Caches compare it to know when to reload. */
        val version = java.util.concurrent.atomic.AtomicLong()
    }
}
