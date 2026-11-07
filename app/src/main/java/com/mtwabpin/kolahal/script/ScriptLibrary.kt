package com.mtwabpin.kolahal.script

import android.content.Context
import androidx.core.content.edit
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository

/** Ready-made scripts bundled in assets/library. Each one describes itself with its directives ([ScriptMeta]). */
class ScriptLibrary(private val context: Context) {
    data class Entry(
        val file: String,
        val name: String,
        val lang: ScriptLang,
        val title: String,
        val description: String,
        val command: Boolean,
        val suggestedEvery: Int,
    )

    enum class State { NOT_INSTALLED, INSTALLED, MODIFIED, UPDATE_AVAILABLE }

    /** Every `.lua` and `.js` file in assets/library, by name. */
    val entries: List<Entry> by lazy {
        context.assets.list("library").orEmpty().sorted().mapNotNull { file ->
            val lang = ScriptLang.of(file.substringAfterLast('.', "")) ?: return@mapNotNull null
            val code = read(file)
            val name = file.substringBeforeLast('.')
            Entry(
                file = file,
                name = name,
                lang = lang,
                title = ScriptMeta.title(code) ?: name,
                description = ScriptMeta.description(code).orEmpty(),
                command = ScriptMeta.isCommand(code),
                suggestedEvery = ScriptMeta.every(code) ?: 0,
            )
        }
    }

    private val codeCache = HashMap<String, String>()

    fun code(entry: Entry): String = read(entry.file)

    private fun read(file: String): String = synchronized(codeCache) {
        codeCache.getOrPut(file) {
            context.assets.open("library/$file").use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    /** The library state of [entry], compared with the user's scripts. */
    fun state(entry: Entry, installed: List<Script>): State {
        val script = installed.firstOrNull { it.name == entry.name } ?: return State.NOT_INSTALLED
        val original = installedOriginal(entry.name)
        val bundled = code(entry)
        return when {
            script.code == bundled -> State.INSTALLED
            original != null && script.code == original -> State.UPDATE_AVAILABLE
            else -> State.MODIFIED
        }
    }

    /**
     * Installs [entry] with its suggested timers, or updates it and keeps its own timers. It remembers
     * the installed text, so a later app update can tell "update available" from "you edited it".
     */
    fun install(entry: Entry): Script {
        // A command script with a built-in's name could never run by that name.
        require(entry.name !in com.mtwabpin.kolahal.terminal.CommandCatalog.RESERVED_NAMES) { "'${entry.name}' is a built-in command" }
        val repo = ScriptRepository.get(context)
        val code = code(entry)
        val existing = repo.byName(entry.name)
        val script = if (existing == null) {
            repo.create(entry.name, entry.lang, code).also { s -> repo.applySuggested(s.id, code) }
        } else {
            if (existing.lang != entry.lang) throw IllegalStateException("a ${existing.lang.ext} script '${entry.name}' already exists")
            repo.saveCode(existing.id, code)
            existing
        }
        prefs.edit { putString("installed:${entry.name}", code) }
        return repo.byId(script.id)!!.also { ScriptScheduler.sync(context, it) }
    }

    /**
     * Library copies you never edited follow the app. When a new Kolahal brings new copies (or a
     * different library), each copy that is still as installed becomes the new one. Its timers and
     * switches are kept. Edited copies are left alone ([State.MODIFIED]); Reset brings back the
     * library's version.
     * Returns the names updated. It runs once per library, so a usual start costs nothing.
     */
    fun updateUnedited(): List<String> {
        val fingerprint = entries.joinToString("\n") { it.file + ":" + code(it).hashCode() }.hashCode()
        if (prefs.getInt(KEY_LIBRARY, 0) == fingerprint) return emptyList()
        val installed = ScriptRepository.get(context).all()
        val results = entries.filter { state(it, installed) == State.UPDATE_AVAILABLE }.map { e -> e.name to runCatching { install(e) }.isSuccess }
        val updated = results.filter { it.second }.map { it.first }
        // If one failed (a locked database, a changed language), it is tried again on the next start.
        if (results.all { it.second }) prefs.edit { putInt(KEY_LIBRARY, fingerprint) }
        if (updated.isNotEmpty()) {
            com.mtwabpin.kolahal.data.LogRepository.get(context).add(
                com.mtwabpin.kolahal.data.LogKind.INFO,
                "Library scripts updated with Kolahal: " + updated.joinToString(", "),
            )
        }
        return updated
    }

    private val prefs by lazy { context.getSharedPreferences("script_library", Context.MODE_PRIVATE) }

    private fun installedOriginal(name: String): String? = prefs.getString("installed:$name", null)

    /** Forgets that [name] is the library's copy, because it was renamed or deleted. */
    fun forget(name: String) = prefs.edit { remove("installed:$name") }

    private companion object {
        /** The library version that [updateUnedited] last updated copies to (a fingerprint of its files). */
        const val KEY_LIBRARY = "library"
    }
}
