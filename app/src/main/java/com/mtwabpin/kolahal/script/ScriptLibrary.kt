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

    /**
     * The library state of [entry], compared with the user's scripts. A copy is unedited if it is the
     * text that was installed, or any version the library ever had ([shipped]): so a copy whose
     * installed text wasn't recorded (an older install, a restored backup) still follows the library.
     */
    fun state(entry: Entry, installed: List<Script>): State {
        val script = installed.firstOrNull { it.name == entry.name } ?: return State.NOT_INSTALLED
        val original = installedOriginal(entry.name)
        val bundled = code(entry)
        return when {
            script.code == bundled -> State.INSTALLED
            original != null && script.code == original -> State.UPDATE_AVAILABLE
            script.lang == entry.lang && shipped(entry, script.code) -> State.UPDATE_AVAILABLE
            else -> State.MODIFIED
        }
    }

    /** Whether [code] is a version of [entry] that the library had, now or before (assets/library-history.txt). */
    fun shipped(entry: Entry, code: String): Boolean = fingerprint(code) in history[entry.file].orEmpty()

    /** Every version of each library file, by file: the first 16 hex digits of its SHA-256 (tools/library-history.sh). */
    private val history: Map<String, Set<String>> by lazy {
        runCatching {
            context.assets.open(HISTORY).bufferedReader().readLines()
                .filter { it.isNotBlank() && !it.startsWith("#") }
                .mapNotNull { line -> line.trim().split(' ').takeIf { it.size == 2 }?.let { it[0] to it[1] } }
                .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
        }.getOrDefault(emptyMap())
    }

    /**
     * Installs [entry] with its suggested timers, or updates it and keeps its own timers. It remembers
     * the installed text, so a later app update can tell "update available" from "you edited it".
     */
    fun install(entry: Entry, followTimers: Boolean = false): Script {
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
            // An update you didn't ask for keeps the timers you chose. The ones you left as the old
            // version had them follow the new version.
            if (followTimers) {
                val every = { c: String -> ScriptMeta.every(c)?.coerceAtLeast(ScriptScheduler.MIN_INTERVAL_MIN) ?: 0 }
                if (existing.intervalMin == every(existing.code)) repo.setInterval(existing.id, every(code))
                if (existing.atTimes == ScriptMeta.at(existing.code)) repo.setAtTimes(existing.id, ScriptMeta.at(code))
            }
            existing
        }
        prefs.edit { putString("installed:${entry.name}", code) }
        return repo.byId(script.id)!!.also { ScriptScheduler.sync(context, it) }
    }

    /**
     * Library copies you never edited follow the app. Each copy that is a version the library had
     * ([state] is [State.UPDATE_AVAILABLE]) becomes the library's version now. Its switches are kept,
     * and its timers too, unless they are still the old version's ([install]'s followTimers). Edited
     * copies are left alone ([State.MODIFIED]); Reset brings back the library's version.
     * A copy that is the library's version but has no recorded original gets one, so it shows as installed.
     * Returns the names updated. It runs at every start: it reads the scripts once and compares a few texts.
     */
    fun updateUnedited(): List<String> {
        val installed = ScriptRepository.get(context).all()
        for (e in entries) {
            val script = installed.firstOrNull { it.name == e.name } ?: continue
            if (installedOriginal(e.name) == null && script.lang == e.lang && script.code == code(e)) prefs.edit { putString("installed:${e.name}", script.code) }
        }
        // If one fails (a locked database), it is tried again on the next start.
        val updated = entries.filter { state(it, installed) == State.UPDATE_AVAILABLE }
            .filter { e -> runCatching { install(e, followTimers = true) }.isSuccess }.map { it.name }
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
        /** Every version of each library file ([shipped]). */
        const val HISTORY = "library-history.txt"

        fun fingerprint(code: String): String =
            java.security.MessageDigest.getInstance("SHA-256").digest(code.toByteArray(Charsets.UTF_8))
                .take(8).joinToString("") { "%02x".format(it) }
    }
}
