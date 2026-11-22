/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import android.content.Context
import androidx.core.content.edit
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.SearchWords
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.data.sha256Hex
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Scripts shared in kolahal-scripts and in the sources the user added ([ScriptSources]). Used by
 * Scripts → Get more scripts, and by `script get`.
 * - Each repository's GitHub Action checks its scripts. It publishes index.json, with each file's
 *   SHA-256, on GitHub Pages. Only kolahal-scripts is reviewed before a script is added.
 * - This class reads each list only when asked and keeps it for an hour. A source that can't be
 *   read keeps its last list, with the error, and the other sources still show.
 * - It installs a script only when the downloaded file matches its list.
 * - A source's list may have shaders too ([SharedShader]). They are read with the scripts, from the
 *   same file; [ShaderStore] installs them.
 */
class ScriptCatalog(private val context: Context) {
    data class Entry(
        override val name: String,
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
        /** The name of the source that lists it ([ScriptSources.Source.name]). */
        override val source: String = SOURCE,
    ) : ScriptSources.Listed

    /** Where a shared script stands on this phone, or a shared shader ([SharedShaders.state]). */
    enum class State {
        NOT_INSTALLED,
        INSTALLED,

        /** Your copy was installed from here, and the repository has a newer one. */
        UPDATE_AVAILABLE,

        /** You edited the copy installed from here. It is never replaced without asking. */
        MODIFIED,

        /**
         * One of your own scripts, a built-in command or an app already has the name, or you
         * installed a script of that name from another source ([installedFrom]).
         */
        NAME_TAKEN,

        /** Its `@requires` names a newer Kolahal. */
        NEEDS_NEWER,
    }

    private val prefs by lazy { Stores.SCRIPT_CATALOG.open(context) }
    private val sourcePrefs by lazy { Stores.SCRIPT_SOURCES.open(context) }

    // ---- sources ---------------------------------------------------------------------------

    /** The addresses of the sources the user added, in the order added. */
    fun added(): List<String> = sourcePrefs.getString(ADDED, null)
        ?.let { runCatching { (Json.parse(it) as? List<*>)?.map { a -> a.toString() } }.getOrNull() }
        .orEmpty()

    private fun setAdded(list: List<String>) =
        sourcePrefs.edit(commit = true) {
            if (list.isEmpty()) remove(ADDED) else putString(
                ADDED,
                Json.stringify(list)
            )
        }

    /** Every source in order: kolahal-scripts first. */
    fun sources(): List<ScriptSources.Source> = ScriptSources.list(added())

    /** Adds [source] at the end. False when it is a source already. */
    fun add(source: ScriptSources.Source): Boolean = synchronized(LOCK) {
        val before = added()
        val after = ScriptSources.adding(before, source)
        if (after == before) return false
        setAdded(after)
        true
    }

    /** Removes the added source that [name] means ([ScriptSources.find]) and forgets its list. Null when there is none. */
    fun remove(name: String): ScriptSources.Source? = synchronized(LOCK) {
        val source = ScriptSources.find(sources(), name)?.takeUnless { it.builtIn } ?: return null
        setAdded(ScriptSources.removing(added(), source.name))
        cacheFile(source).delete()
        Parsed.byFile.remove(cacheFile(source).path)
        prefs.edit { remove(fetchedKey(source)) }
        Errors.byIndex.remove(source.index)
        source
    }

    /** Adds the sources of a backup's [saved] list that aren't here yet. What isn't a source goes into [skipped]. */
    fun restore(saved: List<*>, skipped: MutableList<String>) = synchronized(LOCK) {
        setAdded(ScriptSources.fromBackup(added(), saved, skipped))
    }

    // ---- the lists -------------------------------------------------------------------------

    /**
     * One source's list: its scripts, when it was last read, and why the last try failed (null when
     * it didn't). A list that can't be read again keeps the scripts it had. [shaders]: the shaders
     * beside its scripts, if it has any.
     */
    data class Listing(
        val source: ScriptSources.Source,
        val entries: List<Entry>,
        val readAt: Long?,
        val error: String?,
        val shaders: List<SharedShader> = emptyList(),
    )

    /** What one index.json lists. */
    private class Index(val scripts: List<Entry>, val shaders: List<SharedShader>)

    private fun listing(source: ScriptSources.Source, index: Index?, error: String?) =
        Listing(source, index?.scripts.orEmpty(), readAt(source), error, index?.shaders.orEmpty())

    private fun cacheFile(s: ScriptSources.Source) =
        File(
            context.filesDir,
            if (s.builtIn) "script-catalog.json" else "script-catalog-${
                sha256Hex(s.index.toByteArray()).take(16)
            }.json"
        )

    private fun fetchedKey(s: ScriptSources.Source) =
        if (s.builtIn) "fetched" else "fetched:${s.index}"

    private fun readAt(s: ScriptSources.Source) = prefs.getLong(fetchedKey(s), 0).takeIf { it > 0 }

    /** Every source's list from the last download, in source order. Null when no list is kept yet. */
    fun cached(): List<Entry>? =
        sources().mapNotNull { cached(it) }.takeIf { it.isNotEmpty() }?.flatten()

    /** [source]'s list from the last download, or null when there is none yet. */
    fun cached(source: ScriptSources.Source): List<Entry>? = index(source)?.scripts

    /** Every source's shaders from the last download, in source order. */
    fun cachedShaders(): List<SharedShader> = sources().flatMap { index(it)?.shaders.orEmpty() }

    private fun index(source: ScriptSources.Source): Index? {
        val file = cacheFile(source).takeIf { it.exists() } ?: return null
        // The Scripts screen asks at each change to the scripts (a run, a save). The list is parsed
        // again only when the file changed: a new download.
        val stamp = file.lastModified() to file.length()
        Parsed.byFile[file.path]?.takeIf { it.first == stamp }?.let { return it.second }
        return runCatching { parse(file.readText(), source) }.getOrNull()
            ?.also { Parsed.byFile[file.path] = stamp to it }
    }

    /** Each list parsed last, by its file, with the file's time and size: one for the whole app. */
    private object Parsed {
        val byFile = ConcurrentHashMap<String, Pair<Pair<Long, Long>, Index>>()
    }

    /** Why each source's last read failed, by its address, until it is read again: one for the whole app. */
    private object Errors {
        val byIndex = ConcurrentHashMap<String, String>()
    }

    /** The names of the scripts that come with the app ([ScriptLibrary]). */
    private val libraryNames: Set<String> by lazy {
        runCatching {
            ScriptLibrary(context).entries.map { it.name }.toSet()
        }.getOrDefault(emptySet())
    }

    /**
     * The list without the scripts that come with the app. The repository has Kolahal's own
     * library too, but in the app those are under Add script → From the library, where a copy of
     * one is installed, compared and updated. Listed here as well, each would say "Name taken".
     */
    private fun shared(entries: List<Entry>): List<Entry> =
        entries.filter { it.name !in libraryNames }

    /** Each source's list as kept, without going online: for `script sources`. */
    fun kept(): List<Listing> =
        sources().map { listing(it, index(it), Errors.byIndex[it.index]) }

    /** Each source's list, in source order ([listing]). */
    fun listings(refresh: Boolean = false): List<Listing> = sources().map { listing(it, refresh) }

    /**
     * [source]'s list: the copy kept for an hour, or a download when [refresh] is true or there is
     * no copy. When the download fails, the listing has the copy kept (if any) and the error.
     */
    fun listing(source: ScriptSources.Source, refresh: Boolean = false): Listing {
        if (!refresh && System.currentTimeMillis() - prefs.getLong(
                fetchedKey(source),
                0
            ) < KEEP_MS
        ) {
            index(source)?.let { return listing(source, it, null) }
        }
        return try {
            val raw = get(source.index, MAX_INDEX).toString(Charsets.UTF_8)
            val index = parse(raw, source)
            keep(source, raw)
            Errors.byIndex.remove(source.index)
            listing(source, index, null)
        } catch (e: Exception) {
            val why = e.message ?: e.javaClass.simpleName
            Errors.byIndex[source.index] = why
            listing(source, index(source), why)
        }
    }

    /** Keeps [raw] as [source]'s list, read now. */
    @androidx.annotation.VisibleForTesting
    internal fun keep(source: ScriptSources.Source, raw: String) {
        cacheFile(source).writeText(raw)
        Parsed.byFile.remove(cacheFile(source).path)
        prefs.edit(commit = true) { putLong(fetchedKey(source), System.currentTimeMillis()) }
    }

    private fun parse(raw: String, source: ScriptSources.Source): Index {
        val root = runCatching { Json.parse(raw) }.getOrNull() as? Map<*, *>
            ?: throw IOException("its index.json isn't a script list")
        val format = (root["format"] as? Number)?.toInt()
        if (format != FORMAT) {
            throw IOException(if (format != null && format > FORMAT) "its script list is newer than this Kolahal: update it" else "its index.json isn't a script list")
        }
        fun Map<*, *>.str(key: String) = this[key]?.toString()?.takeIf { it.isNotBlank() }
        fun Map<*, *>.list(key: String) = (this[key] as? List<*>).orEmpty().map { it.toString() }
        // Keep one entry per name, because the screen uses the name as the list key.
        val scripts = shared(
            (root["scripts"] as? List<*>).orEmpty().filterIsInstance<Map<*, *>>().mapNotNull { m ->
                val name =
                    m.str("name")?.takeIf(ScriptNames.NAME::matches) ?: return@mapNotNull null
                val lang = m.str("lang")?.let(ScriptLang::of) ?: return@mapNotNull null
                // Accept only a file in the script's own folder, never a path that leaves scripts/.
                val path = m.str("path")?.takeIf { it == "scripts/$name/$name.${lang.ext}" }
                    ?: return@mapNotNull null
                val sha =
                    m.str("sha256")?.lowercase()?.takeIf(SHA::matches) ?: return@mapNotNull null
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
                    source = source.name,
                )
            }.distinctBy { it.name })
        return Index(scripts, SharedShaders.parse(root["shaders"], source.name))
    }

    /** Downloads [e]'s file from its source and checks it against the list. Throws when it doesn't match (the list changed, so refresh it). */
    fun download(e: Entry): String = download(e, e.path, e.sha256, MAX_SCRIPT, "script")

    /**
     * Downloads the file of [e] at [path] in its source, at most [max] bytes, and checks it against
     * the list ([sha256]). [kind] is the list's word in the error: a script or a shader list.
     */
    internal fun download(
        e: ScriptSources.Listed,
        path: String,
        sha256: String,
        max: Long,
        kind: String
    ): String {
        val source = sources().firstOrNull { it.name == e.source }
            ?: throw IOException("${e.source} isn't one of your sources")
        val bytes = get(source.base + path, max)
        if (sha256Hex(bytes) != sha256) throw IOException("${e.name} doesn't match the $kind list; refresh it and try again")
        return bytes.toString(Charsets.UTF_8)
    }

    fun state(e: Entry, installed: List<Script>): State {
        if (e.requires != null && ScriptMeta.isNewer(
                e.requires,
                BuildConfig.VERSION_NAME
            )
        ) return State.NEEDS_NEWER
        val mine = installed.firstOrNull { it.name == e.name }
            ?: return if (ScriptNames.clash(
                    context,
                    e.name
                ) != null
            ) State.NAME_TAKEN else State.NOT_INSTALLED
        val origin = origin(e.name) ?: return State.NAME_TAKEN
        if (origin.source != e.source) return State.NAME_TAKEN
        return when {
            mine.code != origin.code -> State.MODIFIED
            origin.sha256 != e.sha256 -> State.UPDATE_AVAILABLE
            else -> State.INSTALLED
        }
    }

    /**
     * Installs [e] with [code] from [download].
     * - A new script gets the timers it suggests. With [switchOn], it is switched on: [allow] gives
     *   the answers for what it declares, and anything not in [allow] is allowed, as `script on`
     *   does. Without it, the script stays off and nothing it declares is decided yet.
     * - Over the copy installed from the same source, it updates the code. Anything newly declared
     *   starts off, with a line to allow it.
     * [replace] lets it overwrite a copy you edited.
     */
    fun install(
        e: Entry,
        code: String,
        allow: Map<String, Boolean> = emptyMap(),
        replace: Boolean = false,
        switchOn: Boolean = true
    ): Script {
        val repo = ScriptRepository.get(context)
        // What installing grants comes from the file, so the file must declare what the list (and the sheet) says.
        val uses = ScriptMeta.uses(code)
        if (uses != e.uses.toSet()) throw IllegalStateException("${e.name} doesn't match the script list; refresh it and try again")
        if (ScriptMeta.needsNewer(
                code,
                BuildConfig.VERSION_NAME
            )
        ) throw IllegalStateException("${e.name} needs Kolahal ${ScriptMeta.requires(code)}")
        val existing = repo.byName(e.name)
        val script = if (existing == null) {
            ScriptNames.clash(context, e.name)?.let { throw IllegalStateException(it) }
            repo.create(e.name, e.lang, code, enabled = false).also { s ->
                repo.applySuggested(s.id, code)
                if (switchOn) {
                    allow.filterKeys { it in uses }
                        .forEach { (item, on) -> repo.setAccess(s.id, item, on) }
                    repo.setEnabled(s.id, true, grant = true)
                }
            }
        } else {
            val origin = origin(e.name)
                ?: throw IllegalStateException("you have your own script named ${e.name}")
            if (origin.source != e.source) throw IllegalStateException("you installed ${e.name} from ${origin.source}")
            if (existing.lang != e.lang) throw IllegalStateException("your ${e.name} is a ${existing.lang.ext} script")
            if (existing.code != origin.code && !replace) throw IllegalStateException("you edited ${e.name}; replace it to install this version")
            repo.saveCode(existing.id, code)
            existing
        }
        prefs.edit {
            putString(
                "installed:${e.name}",
                Json.stringify(mapOf("sha256" to e.sha256, "code" to code, "source" to e.source))
            )
        }
        return repo.byId(script.id)!!.also {
            ScriptScheduler.sync(context, it)
            // A script left off asks for its secrets when it is switched on.
            if (it.enabled) ScriptAccess.askForSecrets(context, it)
        }
    }

    /**
     * What is known of a copy installed from here: whether you [edited] it, whether its source's
     * list has a [newer] version, and the [source] it came from.
     */
    data class Copy(val edited: Boolean, val newer: Boolean, val source: String = SOURCE)

    /**
     * Your copy [s] compared with what was installed from here and with [latest], its entry in the list
     * (null when the list isn't kept, or no longer has it). Null when [s] wasn't installed from here.
     * A version that needs a newer Kolahal doesn't count as newer, nor one of another source.
     */
    fun copy(s: Script, latest: Entry?): Copy? {
        val origin = origin(s.name) ?: return null
        val newer =
            latest != null && latest.source == origin.source && latest.sha256 != origin.sha256 &&
                    (latest.requires == null || !ScriptMeta.isNewer(
                        latest.requires,
                        BuildConfig.VERSION_NAME
                    ))
        return Copy(edited = s.code != origin.code, newer = newer, source = origin.source)
    }

    /** What was recorded when [name] was installed from here, as stored. Undo after an update puts it back with [record]. */
    fun recorded(name: String): String? = prefs.getString("installed:$name", null)

    /** Sets what [recorded] returned. Null forgets it. */
    fun record(name: String, raw: String?) = prefs.edit {
        if (raw == null) remove("installed:$name") else putString(
            "installed:$name",
            raw
        )
    }

    /** Forgets that [name] was installed from here, because it was renamed or deleted. */
    fun forget(name: String) = prefs.edit { remove("installed:$name") }

    /** The source that [name] was installed from. Null when it wasn't installed from Get more scripts. */
    fun installedFrom(name: String): String? = origin(name)?.source

    /** A copy installed from here. A record from before sources has no source: it came from kolahal-scripts. */
    private class Origin(val sha256: String, val code: String, val source: String)

    /** What was installed from here under [name], if anything. */
    private fun origin(name: String): Origin? {
        val m = prefs.getString("installed:$name", null)
            ?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() } ?: return null
        return Origin(
            m["sha256"]?.toString() ?: return null,
            m["code"]?.toString() ?: return null,
            m["source"]?.toString() ?: SOURCE
        )
    }

    /** Downloads [url], at most [max] bytes. */
    private fun get(url: String, max: Long): ByteArray {
        val request =
            Request.Builder().url(url).header("User-Agent", "Kolahal/${BuildConfig.VERSION_NAME}")
                .build()
        Net.client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code} from ${request.url.host}")
            val bytes = resp.peekBody(max + 1).bytes()
            if (bytes.size > max) throw IOException("${request.url.encodedPath} is too large")
            return bytes
        }
    }

    companion object {
        /** kolahal-scripts' name ([ScriptSources.DEFAULT]). */
        const val SOURCE = "kolahal-scripts"

        /** The key of the added sources' addresses in [Stores.SCRIPT_SOURCES]. */
        private const val ADDED = "added"
        private val LOCK = Any()

        /** The index.json `format` that this Kolahal reads. */
        private const val FORMAT = 1
        private const val KEEP_MS = 60 * 60 * 1000L
        private const val MAX_INDEX = 2L * 1024 * 1024

        /** The repository's check allows 64 KB per script. */
        private const val MAX_SCRIPT = 64L * 1024
        private val SHA = Regex("^[0-9a-f]{64}$")

        /**
         * The scripts of [entries] that the search finds: each word of [query] is in the name, the
         * title, the description, the author or a tag ([SearchWords]). A blank [query] finds them all.
         */
        fun found(entries: List<Entry>, query: String): List<Entry> {
            val words = SearchWords.of(query)
            return entries.filter { e ->
                SearchWords.allIn(
                    words,
                    listOf(e.name, e.title, e.desc, e.author) + e.tags
                )
            }
        }
    }
}
