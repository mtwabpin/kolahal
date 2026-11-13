package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.Stores
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository

/**
 * Syncs scripts with a folder the user picks (Storage Access Framework). The user can then edit them
 * on a computer, keep them in git, or move them to another phone.
 * - Code goes to `<name>.lua` / `<name>.js`.
 * - Timers and flags go to `kolahal.json` in the same folder.
 */
class ScriptFolder(private val context: Context) {
    private val prefs = Stores.SCRIPT_FOLDER.open(context)
    private val scripts = ScriptRepository.get(context)

    val uri: Uri? get() = prefs.getString(KEY_URI, null)?.let(Uri::parse)

    fun setFolder(tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        context.contentResolver.takePersistableUriPermission(tree, flags)
        val old = uri
        prefs.edit { putString(KEY_URI, tree.toString()).remove(KEY_EXPORTED) }
        // Release the previous folder's grant, unless backups also go there.
        old?.takeIf { it != tree }?.let { Files.releaseFolder(context, it) }
    }

    private fun folder(): DocumentFile {
        val tree = uri ?: throw IllegalStateException("no script folder — run 'script dir' first")
        val dir = DocumentFile.fromTreeUri(context, tree)
        if (dir == null || !dir.canWrite()) throw IllegalStateException("script folder is not accessible — run 'script dir'")
        return dir
    }

    /** Writes every script plus the manifest. Returns the number of scripts written. */
    fun export(): Int = export(folder())

    /** [export] into [dir]: the picked folder, or a folder a test made. */
    internal fun export(dir: DocumentFile): Int {
        val all = scripts.all()
        // List the folder once, instead of one provider round-trip per findFile() call.
        val files = dir.listFiles().associateBy { it.name }
        // Remove the files of scripts deleted on this phone since its last export. Remove only those:
        // the folder can hold scripts that another phone exported and this one doesn't have.
        val current = all.map { "${it.name}.${it.lang.ext}" }.toSet()
        val deleted = prefs.getStringSet(KEY_EXPORTED, emptySet()).orEmpty().filterTo(HashSet()) { it !in current }
        deleted.forEach { files[it]?.delete() }
        // Keep the manifest entries of other phones' scripts too. Otherwise a phone that imports them
        // later would give them the timers their code suggests, not the ones set for them.
        val mine = all.mapTo(HashSet()) { it.name }
        val others = files[MANIFEST]?.let { readManifest(it.uri) }.orEmpty().values.filter { entry ->
            val file = "${entry["name"]}.${entry["lang"]}"
            entry["name"]?.toString()?.lowercase() !in mine && file in files && file !in deleted
        }
        for ((_, name, lang, code) in all) {
            val fileName = "$name.${lang.ext}"
            val file = files[fileName] ?: dir.createFile(MIME_TEXT, fileName)
                ?: throw IllegalStateException("can't create $fileName")
            context.contentResolver.openOutputStream(file.uri, "wt")!!.use { it.write(code.toByteArray()) }
        }
        val manifest = mapOf(
            "version" to 1,
            "scripts" to all.map {
                mapOf(
                    "name" to it.name,
                    "lang" to it.lang.ext,
                    "every" to it.intervalMin,
                    "at" to it.atTimes,
                    "notify" to it.notify,
                    "enabled" to it.enabled,
                )
            } + others,
        )
        val manifestFile = files[MANIFEST] ?: dir.createFile("application/json", MANIFEST)
            ?: throw IllegalStateException("can't create $MANIFEST")
        context.contentResolver.openOutputStream(manifestFile.uri, "wt")!!.use {
            it.write(Json.stringify(manifest).toByteArray())
        }
        // Remember what this export wrote. The next export deletes the files of scripts removed since then.
        prefs.edit { putStringSet(KEY_EXPORTED, current) }
        return all.size
    }

    data class ImportResult(val created: Int, val updated: Int, val skipped: List<String>)

    /** Reads `*.lua` / `*.js` from the folder, creates or updates scripts, and applies the manifest. */
    fun import(): ImportResult = import(folder())

    internal fun import(dir: DocumentFile): ImportResult {
        val files = dir.listFiles()
        val settings = files.firstOrNull { it.name == MANIFEST }?.let { readManifest(it.uri) }.orEmpty()

        var created = 0
        // Counts only scripts the import changed. The same code and settings again are not an update.
        var updated = 0
        val skipped = ArrayList<String>()
        // Scripts this import created or changed, by name.
        val changedHere = mutableSetOf<String>()
        for (file in files) {
            val fileName = file.name ?: continue
            val lang = ScriptLang.of(fileName.substringAfterLast('.', "")) ?: continue
            if (file.isDirectory) continue
            val name = fileName.substringBeforeLast('.').lowercase()
            // The same rules as a backup: no names of built-in commands.
            if (!ScriptNames.NAME.matches(name) || name in com.mtwabpin.kolahal.terminal.CommandCatalog.RESERVED_NAMES) {
                skipped += fileName
                continue
            }
            val code = readText(file.uri) ?: continue
            if (ScriptMeta.needsNewer(code, com.mtwabpin.kolahal.BuildConfig.VERSION_NAME)) {
                skipped += "$fileName (needs Kolahal ${ScriptMeta.requires(code)})"
                continue
            }
            val existing = scripts.byName(name)
            var isNew = false
            val script = if (existing == null) {
                created++
                isNew = true
                scripts.create(name, lang, code, enabled = false)
            } else {
                if (existing.lang != lang) {
                    skipped += fileName
                    continue
                }
                // Code from the folder replaces a script's code: the script is switched off, to be reviewed like a new one.
                if (existing.code != code) {
                    scripts.replaceCode(existing.id, code)
                    isNew = true
                }
                existing
            }
            // Two files can give one name (`Foo.lua`, `foo.lua`). The second finds the first one's code
            // already in place: it must stay switched off, as the first does.
            if (isNew) changedHere += name else if (name in changedHere) isNew = true
            settings[name]?.let { m ->
                (m["every"] as? Number)?.toLong()?.let { every ->
                    val minutes = every.coerceIn(0, 7L * 24 * 60).toInt()
                    scripts.setInterval(script.id, if (minutes in 1 until ScriptScheduler.MIN_INTERVAL_MIN) ScriptScheduler.MIN_INTERVAL_MIN else minutes)
                }
                (m["at"] as? List<*>)?.let { at ->
                    scripts.setAtTimes(script.id, at.mapNotNull { ScriptScheduler.normalizeTime(it.toString()) }.distinct().sorted())
                }
                (m["notify"] as? Boolean)?.let { scripts.setNotify(script.id, it) }
                if (!isNew) (m["enabled"] as? Boolean)?.let { on ->
                    scripts.setEnabled(script.id, on)
                    if (on) scripts.byId(script.id)?.let { ScriptAccess.askForNew(context, it) }
                }
            }
            // A new script without settings in kolahal.json gets the timers its code suggests. A changed
            // script keeps its own timers, unless its `@every` or `@at` lines changed (replaceCode handles that).
            if (existing == null && settings[name] == null) scripts.applySuggested(script.id, code)
            // New scripts from a folder arrive switched off, as from a backup. Review them before
            // they run on a timer.
            if (isNew) scripts.setEnabled(script.id, false)
            if (existing != null && scripts.byId(script.id)?.let { changed(existing, it) } == true) updated++
            runCatching { scripts.byId(script.id)?.let { ScriptScheduler.sync(context, it) } }
        }
        return ImportResult(created, updated, skipped)
    }

    /** What an import can change in a script: its code, timers, notifications and switch. */
    private fun changed(before: Script, after: Script) =
        before.code != after.code || before.intervalMin != after.intervalMin || before.atTimes != after.atTimes ||
            before.notify != after.notify || before.enabled != after.enabled

    /** Manifest entries keyed by script name. */
    private fun readManifest(uri: Uri): Map<String?, Map<*, *>> =
        (readText(uri)?.let { runCatching { Json.parse(it) as? Map<*, *> }.getOrNull() }?.get("scripts") as? List<*>)
            .orEmpty().filterIsInstance<Map<*, *>>().associateBy { it["name"]?.toString()?.lowercase() }

    private fun readText(uri: Uri): String? =
        runCatching { Files.readText(context, uri) }.getOrNull() // size-capped, like single-file import

    private companion object {
        const val KEY_URI = "uri"
        /** The files this phone's last export wrote. */
        private const val KEY_EXPORTED = "exported"
        const val MANIFEST = "kolahal.json"
        const val MIME_TEXT = "application/octet-stream"
    }
}
