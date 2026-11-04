package com.mtwabpin.kolahal.data

import android.annotation.SuppressLint
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
    /** 0 = manual only. WorkManager enforces a 15-minute minimum for periodic runs. */
    val intervalMin: Int,
    val enabled: Boolean,
    val notify: Boolean,
    val lastRun: Long?,
    val lastStatus: String?,
    /** Daily run times as "HH:mm", in addition to (or instead of) the interval. */
    val atTimes: List<String> = emptyList(),
    /** The user's access switches, item to on or off ([ScriptAccess]); a missing item is undecided. */
    val access: Map<String, Boolean> = emptyMap(),
) {
    val grants: ScriptAccess.Grants get() = ScriptAccess.grants(code, access)
}

class ScriptRepository private constructor(private val context: Context) {
    private val db get() = Db.get(context)

    fun all(): List<Script> = query(null, null)

    fun byName(name: String): Script? = query("name = ?", arrayOf(name)).firstOrNull()

    fun byId(id: Long): Script? = query("id = ?", arrayOf(id.toString())).firstOrNull()

    /**
     * A new script. Created switched on (one of yours, a library install) it may use what it
     * declares; code from a file, a folder or a backup comes [enabled] false and asks when switched on.
     */
    fun create(name: String, lang: ScriptLang, code: String, enabled: Boolean = true): Script {
        // Nothing of an earlier script with this name carries over: a status line a run left behind, values.
        StatusLines.clear(context, name)
        db.writableDatabase.delete("kv", "scope = ?", arrayOf(SecretStore.scope(name)))
        db.writableDatabase.insertOrThrow("scripts", null, ContentValues().apply {
            put("name", name)
            put("lang", lang.ext)
            put("code", code)
            put("enabled", if (enabled) 1 else 0)
            if (enabled) put("access", ScriptAccess.encode(ScriptAccess.allowUndecided(code, emptyMap())))
        })
        version.incrementAndGet()
        return byName(name)!!
    }

    /**
     * New code for a script. [byUser] (typed in the editor): what it newly declares with `@uses` is
     * allowed, since you wrote it (for a switched-on script); what an update left for you to decide
     * stays undecided. Code from elsewhere (a library or shared-script update) leaves what it newly
     * declares off, `@on` events included, with a line to allow them.
     */
    fun saveCode(id: Long, code: String, byUser: Boolean = false) {
        val s = byId(id) ?: return
        val newUses = ScriptMeta.uses(code) - ScriptMeta.uses(s.code) - s.access.keys
        val newEvents = ScriptMeta.events(code) - ScriptMeta.events(s.code).toSet() - s.access.keys
        val newInternet = newInternet(s, code)
        val access = when {
            byUser -> if (s.enabled) s.access + newUses.associateWith { true } else s.access
            else -> s.access + (newEvents + newInternet).associateWith { false }
        }
        update(id) {
            put("code", code)
            put("access", ScriptAccess.encode(access))
        }
        forgetRemovedSecrets(s.name, code)
        if (!byUser && s.enabled) ScriptAccess.askToAllow(context, s.name, newUses.toList() + newEvents + newInternet, "now requests")
    }

    /**
     * The internet, used by [code] but not by [s]'s code and not decided yet: code you didn't write
     * that starts making requests starts with it off, like a new `@uses`, so an update to a script
     * allowed your clipboard or notifications can't send them out unasked.
     */
    private fun newInternet(s: Script, code: String): List<String> =
        listOf(ScriptAccess.INTERNET).filter { it in ScriptAccess.items(code) && it !in ScriptAccess.items(s.code) && it !in s.access }

    /** Values of `@secret` lines the code no longer has are forgotten, not kept unseen. */
    private fun forgetRemovedSecrets(script: String, code: String) {
        SecretStore(context).forgetUndeclared(script, ScriptMeta.secrets(code).map { it.name })
    }

    /**
     * Code from a file, a folder or a backup over an existing script: it is switched off, and what
     * it was allowed goes back to undecided (what you switched off stays off), so turning it on asks
     * again; internet it didn't use before starts off. Its values ([com.mtwabpin.kolahal.script.SecretStore])
     * stay, for the sites they were set for, but for `@secret` lines it no longer has.
     */
    fun replaceCode(id: Long, code: String) {
        val s = byId(id) ?: return
        update(id) {
            put("code", code)
            put("enabled", 0)
            put("access", ScriptAccess.encode(s.access.filterValues { !it } + newInternet(s, code).associateWith { false }))
        }
        forgetRemovedSecrets(s.name, code)
        StatusLines.clear(context, s.name)
    }

    /**
     * Changes whenever the script list would: this process's writes ([version]) and runs the
     * script process records. Cheap, it reads no code; lets home skip re-reading every script.
     */
    fun fingerprint(): String = db.readableDatabase.rawQuery(
        "SELECT COUNT(*), COALESCE(MAX(last_run), 0), COALESCE(SUM(enabled), 0), COALESCE(MAX(id), 0) FROM scripts", null,
    ).use { c ->
        c.moveToFirst()
        "${version.get()}:${c.getLong(0)}:${c.getLong(1)}:${c.getLong(2)}:${c.getLong(3)}"
    }

    fun setInterval(id: Long, minutes: Int) = update(id) { put("interval_min", minutes) }

    /**
     * Switches a script on or off. [grant]: the user's go-ahead for what it declares and nobody
     * decided yet (the Scripts screen's dialog, `script on`, an install they started). Without it,
     * those stay off; the caller offers a line to allow them ([ScriptAccess.askForNew]).
     */
    fun setEnabled(id: Long, enabled: Boolean, grant: Boolean = false) {
        val s = byId(id) ?: return
        update(id) {
            put("enabled", if (enabled) 1 else 0)
            if (enabled && grant) put("access", ScriptAccess.encode(ScriptAccess.allowUndecided(s.code, s.access)))
        }
        // Its status line goes either way: switched off, it would come back stale; switched on, a
        // line left by a run while it was off would.
        if (enabled != s.enabled) StatusLines.clear(context, s.name)
    }

    /** The timers and notifications [code] suggests (`@every`, `@at`, `@notify`), for a script just added. */
    fun applySuggested(id: Long, code: String) {
        ScriptMeta.every(code)?.let { setInterval(id, it.coerceAtLeast(ScriptScheduler.MIN_INTERVAL_MIN)) }
        ScriptMeta.at(code).takeIf { it.isNotEmpty() }?.let { setAtTimes(id, it) }
        if (ScriptMeta.notifies(code)) setNotify(id, true)
    }

    /** Switches one access item; see [ScriptAccess]. */
    fun setAccess(id: Long, item: String, allowed: Boolean) {
        val s = byId(id) ?: return
        update(id) { put("access", ScriptAccess.encode(s.access + (item to allowed))) }
    }

    fun setAtTimes(id: Long, times: List<String>) = update(id) {
        if (times.isEmpty()) putNull("at_times") else put("at_times", times.joinToString(","))
    }

    fun setNotify(id: Long, notify: Boolean) = update(id) { put("notify", if (notify) 1 else 0) }

    /** The last run's time and how it went ([status], cut to [STATUS_MAX]: a script's error text is its own to make long). */
    fun recordRun(id: Long, status: String) = update(id) {
        put("last_run", System.currentTimeMillis())
        put("last_status", status.take(STATUS_MAX))
    }

    /**
     * Renames a script; check the name first (`ScriptNames.invalid`). Its secrets and status line
     * with its bar and refresh, kept by name, move with it. A copy from the library or Get more
     * scripts becomes yours: it no longer takes their updates. Lines already in the log keep the old
     * name.
     */
    fun rename(id: Long, to: String) {
        val s = byId(id) ?: return
        if (s.name == to) return
        val w = db.writableDatabase
        w.transaction {
            update("scripts", ContentValues().apply { put("name", to) }, "id = ?", arrayOf(id.toString()))
            delete("kv", "scope = ?", arrayOf(SecretStore.scope(to)))
            update("kv", ContentValues().apply { put("scope", SecretStore.scope(to)) }, "scope = ?", arrayOf(SecretStore.scope(s.name)))
            delete("kv", "scope = ?", arrayOf(com.mtwabpin.kolahal.script.ScriptOptions.scope(to)))
            update("kv", ContentValues().apply { put("scope", com.mtwabpin.kolahal.script.ScriptOptions.scope(to)) }, "scope = ?", arrayOf(com.mtwabpin.kolahal.script.ScriptOptions.scope(s.name)))
            delete("kv", "scope = ? AND k = ?", arrayOf(HostApi.STATUS_SCOPE, to))
            update("kv", ContentValues().apply { put("k", to) }, "scope = ? AND k = ?", arrayOf(HostApi.STATUS_SCOPE, s.name))
            delete("kv", "scope = ? AND k = ?", arrayOf(StatusLines.EXTRAS_SCOPE, to))
            update("kv", ContentValues().apply { put("k", to) }, "scope = ? AND k = ?", arrayOf(StatusLines.EXTRAS_SCOPE, s.name))
        }
        forgetOrigin(s.name)
        version.incrementAndGet()
    }

    /** No longer the library's or Get more scripts' copy, for updates. */
    private fun forgetOrigin(name: String) {
        ScriptLibrary(context).forget(name)
        ScriptCatalog(context).forget(name)
    }

    fun delete(id: Long) {
        // Its secrets and options go with it.
        byId(id)?.let {
            SecretStore(context).clear(it.name)
            db.writableDatabase.delete("kv", "scope = ?", arrayOf(com.mtwabpin.kolahal.script.ScriptOptions.scope(it.name)))
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
        )
    }

    companion object {
        /**
         * Longest last-run status kept (an error's first line): a row past the 2 MB cursor window
         * can't be read back, and every script list would fail on it.
         */
        const val STATUS_MAX = 200

        fun scopeOf(scriptId: Long) = "script:$scriptId"

        /** Bumped on every write made in this process; caches compare it to know when to reload. */
        val version = java.util.concurrent.atomic.AtomicLong()

        @SuppressLint("StaticFieldLeak") // application context only

        @Volatile
        private var instance: ScriptRepository? = null

        fun get(context: Context): ScriptRepository = instance ?: synchronized(this) {
            instance ?: ScriptRepository(context.applicationContext).also { instance = it }
        }
    }
}
