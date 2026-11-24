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
import com.mtwabpin.kolahal.data.Stores
import com.mtwabpin.kolahal.data.sha256Hex

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
        /** What it is and how it runs ([LibraryTags.of]). The library page has a filter chip for each. */
        val tags: Set<LibraryTag> = emptySet(),
        /** Its `@on` events ([ScriptMeta.events]). */
        val events: List<String> = emptyList(),
        /** It draws a full screen of its own ([ScriptMeta.isScreen]): the gui style doesn't offer it as a command. */
        val screen: Boolean = false,
    ) {
        companion object {
            /** The entry of the library file [file], a [lang] script with [code]: what its name and its directives say. */
            fun of(file: String, lang: ScriptLang, code: String): Entry {
                val name = file.substringBeforeLast('.')
                return Entry(
                    file = file,
                    name = name,
                    lang = lang,
                    title = ScriptMeta.title(code) ?: name,
                    description = ScriptMeta.description(code).orEmpty(),
                    command = ScriptMeta.isCommand(code),
                    suggestedEvery = ScriptMeta.every(code) ?: 0,
                    tags = LibraryTags.of(name, code),
                    events = ScriptMeta.events(code),
                    screen = ScriptMeta.isScreen(code),
                )
            }
        }
    }

    enum class State { NOT_INSTALLED, INSTALLED, MODIFIED, UPDATE_AVAILABLE }

    /** What the app brings: read once for the whole app ([Bundled]). */
    private val bundled = Bundled.of(context)

    /** Every `.lua` and `.js` file in assets/library, by name. */
    val entries: List<Entry> get() = bundled.entries

    fun code(entry: Entry): String = bundled.read(entry.file)

    /**
     * The library files, their entries and their history (assets): read once for the whole app, as
     * they don't change while it runs. Every ScriptLibrary shares them: a command, a screen, the
     * completion of `script install`.
     */
    private class Bundled(private val assets: android.content.res.AssetManager) {
        private val codes = HashMap<String, String>()

        fun read(file: String): String = synchronized(codes) {
            codes.getOrPut(file) {
                assets.open("library/$file").use { it.readBytes().toString(Charsets.UTF_8) }
            }
        }

        val entries: List<Entry> by lazy {
            assets.list("library").orEmpty().sorted().mapNotNull { file ->
                val lang = ScriptLang.of(file.substringAfterLast('.', "")) ?: return@mapNotNull null
                Entry.of(file, lang, read(file))
            }
        }

        /** Every version of each library file, by file: the first 16 hex digits of its SHA-256 (tools/library-history.sh). */
        val history: Map<String, Set<String>> by lazy {
            runCatching {
                assets.open(HISTORY).bufferedReader().readLines()
                    .filter { it.isNotBlank() && !it.startsWith("#") }
                    .mapNotNull { line ->
                        line.trim().split(' ').takeIf { it.size == 2 }?.let { it[0] to it[1] }
                    }
                    .groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
            }.getOrDefault(emptyMap())
        }

        companion object {
            @Volatile
            private var one: Bundled? = null

            fun of(context: Context): Bundled = one ?: synchronized(this) {
                one ?: Bundled(context.applicationContext.assets).also { one = it }
            }
        }
    }

    /**
     * The library state of [entry], compared with the user's scripts. A copy is unedited if it is the
     * text that was installed, or any version the library ever had ([shipped]): so a copy whose
     * installed text wasn't recorded (an older install, a restored backup) still follows the library.
     */
    fun state(entry: Entry, installed: List<Script>): State {
        val script = installed.firstOrNull { it.name == entry.name } ?: return State.NOT_INSTALLED
        val original = recorded(entry.name)
        val shippedNow = code(entry)
        return when {
            script.code == shippedNow -> State.INSTALLED
            original != null && script.code == original -> State.UPDATE_AVAILABLE
            script.lang == entry.lang && shipped(entry, script.code) -> State.UPDATE_AVAILABLE
            else -> State.MODIFIED
        }
    }

    /**
     * Whether the library has a newer version than the one your copy of [entry] came from. [state] is
     * the copy's [state].
     * - An older unedited copy ([State.UPDATE_AVAILABLE]) has one.
     * - An edited copy ([State.MODIFIED]) has one when the text it was installed from is not this
     *   version's: the library changed since. Without that text, it counts as only edited.
     */
    fun hasNewer(entry: Entry, state: State): Boolean = when (state) {
        State.UPDATE_AVAILABLE -> true
        State.MODIFIED -> recorded(entry.name)?.let { it != code(entry) } == true
        else -> false
    }

    /** Whether [code] is a version of [entry] that the library had, now or before (assets/library-history.txt). */
    fun shipped(entry: Entry, code: String): Boolean =
        fingerprint(code) in history[entry.file].orEmpty()

    private val history get() = bundled.history

    /**
     * Installs [entry] with its suggested timers, or updates it and keeps its own timers. It remembers
     * the installed text, so a later app update can tell "update available" from "you edited it".
     */
    fun install(entry: Entry, followTimers: Boolean = false): Script {
        // A command script with a built-in's name could never run by that name.
        require(entry.name !in com.mtwabpin.kolahal.commands.CommandCatalog.RESERVED_NAMES) { "'${entry.name}' is a built-in command" }
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
                val every = { c: String ->
                    ScriptMeta.every(c)?.coerceAtLeast(ScriptScheduler.MIN_INTERVAL_MIN) ?: 0
                }
                if (existing.intervalMin == every(existing.code)) repo.setInterval(
                    existing.id,
                    every(code)
                )
                if (existing.atTimes == ScriptMeta.at(existing.code)) repo.setAtTimes(
                    existing.id,
                    ScriptMeta.at(code)
                )
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
     * It logs the names updated and records them with this version, for the note in the Scripts tab ([selfUpdate]).
     * Returns the names updated. It runs at every start: it reads the scripts once and compares a few texts.
     */
    fun updateUnedited(): List<String> {
        val installed = ScriptRepository.get(context).all()
        for (e in entries) {
            val script = installed.firstOrNull { it.name == e.name } ?: continue
            if (recorded(e.name) == null && script.lang == e.lang && script.code == code(e)) prefs.edit {
                putString(
                    "installed:${e.name}",
                    script.code
                )
            }
        }
        // If one fails (a locked database), it is tried again on the next start.
        val updated = entries.filter { state(it, installed) == State.UPDATE_AVAILABLE }
            .filter { e -> runCatching { install(e, followTimers = true) }.isSuccess }
            .map { it.name }
        recordSelfUpdate(updated)
        if (updated.isNotEmpty()) {
            com.mtwabpin.kolahal.data.LogRepository.get(context).add(
                com.mtwabpin.kolahal.data.LogKind.INFO,
                "Library scripts updated with Kolahal: " + updated.joinToString(", "),
            )
        }
        return updated
    }

    private val prefs by lazy { Stores.SCRIPT_LIBRARY.open(context) }

    /** The note about copies Kolahal updated by itself. It is about this phone, so it stays out of backups (unlike [prefs]). */
    private val notePrefs by lazy { Stores.LIBRARY_UPDATES.open(context) }

    /** The text your copy of [name] was installed from, or null. Undo after an update puts it back with [record]. */
    fun recorded(name: String): String? = prefs.getString("installed:$name", null)

    /** Sets the text your copy of [name] was installed from. Null forgets it. */
    fun record(name: String, original: String?) = prefs.edit {
        if (original == null) remove("installed:$name") else putString(
            "installed:$name",
            original
        )
    }

    /** Library copies that [updateUnedited] updated, and the Kolahal [version] that did it. */
    class SelfUpdate(val version: String, val names: List<String>)

    /** The copies Kolahal updated by itself, until [dismissSelfUpdate]. The Scripts tab shows a note about them. */
    fun selfUpdate(): SelfUpdate? {
        val version = notePrefs.getString(SELF_UPDATED_IN, null) ?: return null
        val names = notePrefs.getString(SELF_UPDATED, null)?.split(',')?.filter { it.isNotBlank() }
            .orEmpty()
        return if (names.isEmpty()) null else SelfUpdate(version, names)
    }

    /** Forgets the note about the copies Kolahal updated by itself: the user closed it. */
    fun dismissSelfUpdate() = notePrefs.edit { remove(SELF_UPDATED); remove(SELF_UPDATED_IN) }

    /**
     * Records the copies [updateUnedited] just updated, with this Kolahal's version. Copies updated
     * earlier by the same version stay in the list. A note left by an older version goes.
     */
    private fun recordSelfUpdate(updated: List<String>) {
        val version = BuildConfig.VERSION_NAME
        val earlier = selfUpdate()
        if (updated.isEmpty()) {
            if (earlier != null && earlier.version != version) dismissSelfUpdate()
            return
        }
        val names =
            (earlier?.takeIf { it.version == version }?.names.orEmpty() + updated).distinct()
        notePrefs.edit {
            putString(
                SELF_UPDATED,
                names.joinToString(",")
            ); putString(SELF_UPDATED_IN, version)
        }
    }

    /** Forgets that [name] is the library's copy, because it was renamed or deleted. */
    fun forget(name: String) = prefs.edit { remove("installed:$name") }

    private companion object {
        /** Every version of each library file ([shipped]). */
        const val HISTORY = "library-history.txt"

        const val SELF_UPDATED = "self_updated"
        const val SELF_UPDATED_IN = "self_updated_in"

        /** The first 8 bytes of the code's SHA-256, as 16 hex digits. */
        fun fingerprint(code: String): String = sha256Hex(code.toByteArray(Charsets.UTF_8)).take(16)
    }
}
