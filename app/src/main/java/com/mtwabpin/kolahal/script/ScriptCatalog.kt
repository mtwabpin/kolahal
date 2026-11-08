package com.mtwabpin.kolahal.script

import android.content.Context
import androidx.core.content.edit
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 * Scripts shared in the kolahal-scripts repository. Used by Scripts → Get more scripts, and by `script get`.
 * The repository's GitHub Action checks every script. It publishes index.json, with each file's
 * SHA-256, on GitHub Pages. This class reads the list only when asked and keeps it for an hour.
 * It installs a script only when the downloaded file matches the list.
 */
class ScriptCatalog(private val context: Context) {
    data class Entry(
        val name: String,
        val lang: ScriptLang,
        val path: String,
        val title: String,
        val desc: String,
        val author: String,
        val version: String,
        val tags: List<String>,
        /** What it declares with `@uses`, in [ScriptMeta.USES] order. */
        val uses: List<String>,
        val on: List<String>,
        val command: Boolean,
        val every: Int?,
        val at: List<String>,
        val requires: String?,
        val sha256: String,
    )

    enum class State {
        NOT_INSTALLED,
        INSTALLED,
        /** Your copy was installed from here, and the repository has a newer one. */
        UPDATE_AVAILABLE,
        /** You edited the copy installed from here. It is never replaced without asking. */
        MODIFIED,
        /** One of your own scripts, a built-in command or an app already has the name. */
        NAME_TAKEN,
        /** Its `@requires` names a newer Kolahal. */
        NEEDS_NEWER,
    }

    private val prefs by lazy { context.getSharedPreferences("script_catalog", Context.MODE_PRIVATE) }
    private val cacheFile get() = File(context.filesDir, "script-catalog.json")

    /** The list from the last download, or null when there is none yet. */
    fun cached(): List<Entry>? = cacheFile.takeIf { it.exists() }?.let { runCatching { parse(it.readText()) }.getOrNull() }

    /** The list. Returns the copy kept for an hour, or downloads it now when [refresh] is true or there is no copy. */
    fun entries(refresh: Boolean = false): List<Entry> {
        if (!refresh && System.currentTimeMillis() - prefs.getLong("fetched", 0) < KEEP_MS) cached()?.let { return it }
        val raw = get(INDEX_URL, MAX_INDEX).toString(Charsets.UTF_8)
        val entries = parse(raw)
        cacheFile.writeText(raw)
        prefs.edit { putLong("fetched", System.currentTimeMillis()) }
        return entries
    }

    private fun parse(raw: String): List<Entry> {
        val root = Json.parse(raw) as? Map<*, *> ?: throw IOException("the script list isn't readable")
        if ((root["format"] as? Number)?.toInt() != FORMAT) throw IOException("the script list is newer than this Kolahal: update it")
        fun Map<*, *>.str(key: String) = this[key]?.toString()?.takeIf { it.isNotBlank() }
        fun Map<*, *>.list(key: String) = (this[key] as? List<*>).orEmpty().map { it.toString() }
        // Keep one entry per name, because the screen uses the name as the list key.
        return (root["scripts"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { m ->
            val name = m.str("name")?.takeIf(ScriptNames.NAME::matches) ?: return@mapNotNull null
            val lang = m.str("lang")?.let(ScriptLang::of) ?: return@mapNotNull null
            // Accept only a file in the script's own folder, never a path that leaves scripts/.
            val path = m.str("path")?.takeIf { it == "scripts/$name/$name.${lang.ext}" } ?: return@mapNotNull null
            val sha = m.str("sha256")?.lowercase()?.takeIf(SHA::matches) ?: return@mapNotNull null
            Entry(
                name = name,
                lang = lang,
                path = path,
                title = m.str("title") ?: name,
                desc = m.str("desc").orEmpty(),
                author = m.str("author").orEmpty(),
                version = m.str("version").orEmpty(),
                tags = m.list("tags"),
                uses = ScriptMeta.USES.filter { it in m.list("uses") },
                on = ScriptMeta.EVENTS.filter { it in m.list("on") },
                command = m["command"] == true,
                every = (m["every"] as? Number)?.toInt()?.takeIf { it > 0 },
                at = m.list("at"),
                requires = m.str("requires"),
                sha256 = sha,
            )
        }.distinctBy { it.name }
    }

    /** Downloads [e]'s file and checks it against the list. Throws when it doesn't match (the list changed, so refresh it). */
    fun download(e: Entry): String {
        val bytes = get(BASE_URL + e.path, MAX_SCRIPT)
        if (sha256(bytes) != e.sha256) throw IOException("${e.name} doesn't match the script list; refresh it and try again")
        return bytes.toString(Charsets.UTF_8)
    }

    fun state(e: Entry, installed: List<Script>): State {
        if (e.requires != null && ScriptMeta.isNewer(e.requires, BuildConfig.VERSION_NAME)) return State.NEEDS_NEWER
        val mine = installed.firstOrNull { it.name == e.name }
            ?: return if (ScriptNames.clash(context, e.name) != null) State.NAME_TAKEN else State.NOT_INSTALLED
        val origin = origin(e.name) ?: return State.NAME_TAKEN
        return when {
            mine.code != origin.code -> State.MODIFIED
            origin.sha256 != e.sha256 -> State.UPDATE_AVAILABLE
            else -> State.INSTALLED
        }
    }

    /**
     * Installs [e] with [code] from [download].
     * - A new script is switched on. [allow] gives the answers for what it declares; anything not in
     *   [allow] is allowed, as `script on` does. It also gets the timers it suggests.
     * - Over the copy installed from here, it updates the code. Anything newly declared starts off,
     *   with a line to allow it.
     * [replace] lets it overwrite a copy you edited.
     */
    fun install(e: Entry, code: String, allow: Map<String, Boolean> = emptyMap(), replace: Boolean = false): Script {
        val repo = ScriptRepository.get(context)
        // What installing grants comes from the file, so the file must declare what the list (and the sheet) says.
        val uses = ScriptMeta.uses(code)
        if (uses != e.uses.toSet()) throw IllegalStateException("${e.name} doesn't match the script list; refresh it and try again")
        if (ScriptMeta.needsNewer(code, BuildConfig.VERSION_NAME)) throw IllegalStateException("${e.name} needs Kolahal ${ScriptMeta.requires(code)}")
        val existing = repo.byName(e.name)
        val script = if (existing == null) {
            ScriptNames.clash(context, e.name)?.let { throw IllegalStateException(it) }
            repo.create(e.name, e.lang, code, enabled = false).also { s ->
                repo.applySuggested(s.id, code)
                allow.filterKeys { it in uses }.forEach { (item, on) -> repo.setAccess(s.id, item, on) }
                repo.setEnabled(s.id, true, grant = true)
            }
        } else {
            val origin = origin(e.name) ?: throw IllegalStateException("you have your own script named ${e.name}")
            if (existing.lang != e.lang) throw IllegalStateException("your ${e.name} is a ${existing.lang.ext} script")
            if (existing.code != origin.code && !replace) throw IllegalStateException("you edited ${e.name}; replace it to install this version")
            repo.saveCode(existing.id, code)
            existing
        }
        prefs.edit { putString("installed:${e.name}", Json.stringify(mapOf("sha256" to e.sha256, "version" to e.version, "code" to code))) }
        return repo.byId(script.id)!!.also {
            ScriptScheduler.sync(context, it)
            ScriptAccess.askForSecrets(context, it)
        }
    }

    /** What is known of a copy installed from here: whether you [edited] it, and whether the list has a [newer] version. */
    class Copy(val edited: Boolean, val newer: Boolean)

    /**
     * Your copy [s] compared with what was installed from here and with [latest], its entry in the list
     * (null when the list isn't kept, or no longer has it). Null when [s] wasn't installed from here.
     * A version that needs a newer Kolahal doesn't count as newer.
     */
    fun copy(s: Script, latest: Entry?): Copy? {
        val origin = origin(s.name) ?: return null
        val newer = latest != null && latest.sha256 != origin.sha256 && (latest.requires == null || !ScriptMeta.isNewer(latest.requires, BuildConfig.VERSION_NAME))
        return Copy(edited = s.code != origin.code, newer = newer)
    }

    /** What was recorded when [name] was installed from here, as stored. Undo after an update puts it back with [record]. */
    fun recorded(name: String): String? = prefs.getString("installed:$name", null)

    /** Sets what [recorded] returned. Null forgets it. */
    fun record(name: String, raw: String?) = prefs.edit { if (raw == null) remove("installed:$name") else putString("installed:$name", raw) }

    /** Forgets that [name] was installed from here, because it was renamed or deleted. */
    fun forget(name: String) = prefs.edit { remove("installed:$name") }

    private class Origin(val sha256: String, val code: String)

    /** What was installed from here under [name], if anything. */
    private fun origin(name: String): Origin? {
        val m = prefs.getString("installed:$name", null)?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() } ?: return null
        return Origin(m["sha256"]?.toString() ?: return null, m["code"]?.toString() ?: return null)
    }

    /** Downloads [url]. Allows public addresses only, and at most [max] bytes. */
    private fun get(url: String, max: Long): ByteArray {
        val request = Request.Builder().url(url).header("User-Agent", "Kolahal/${BuildConfig.VERSION_NAME}").build()
        Net.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} from ${request.url.host}")
            val bytes = resp.peekBody(max + 1).bytes()
            if (bytes.size > max) throw IOException("${request.url.encodedPath} is too large")
            return bytes
        }
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        const val SOURCE = "kolahal-scripts"
        const val BASE_URL = "https://mtwabpin.github.io/kolahal-scripts/"
        private const val INDEX_URL = BASE_URL + "index.json"
        /** The index.json `format` that this Kolahal reads. */
        private const val FORMAT = 1
        private const val KEEP_MS = 60 * 60 * 1000L
        private const val MAX_INDEX = 2L * 1024 * 1024
        /** The repository's check allows 64 KB per script. */
        private const val MAX_SCRIPT = 64L * 1024
        private val SHA = Regex("^[0-9a-f]{64}$")
    }
}
