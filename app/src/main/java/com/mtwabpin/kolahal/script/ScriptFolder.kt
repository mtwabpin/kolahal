package com.mtwabpin.kolahal.script

import com.mtwabpin.kolahal.data.Stores
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.edit
import androidx.documentfile.provider.DocumentFile
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
        // One for this import: two files can give one name (`Foo.lua`, `foo.lua`), and the second must
        // stay switched off, as the first does.
        val importer = ScriptImport(context)
        for (file in files) {
            val fileName = file.name ?: continue
            val lang = ScriptLang.of(fileName.substringAfterLast('.', "")) ?: continue
            if (file.isDirectory) continue
            val name = fileName.substringBeforeLast('.').lowercase()
            // The same rules as a backup: no names of built-in commands.
            if (!ScriptImport.named(name)) {
                skipped += fileName
                continue
            }
            val code = readText(file.uri) ?: continue
            // The script's entry in kolahal.json holds its timers and switch. A new script without one gets
            // the timers its code suggests. New scripts from a folder arrive switched off, as from a backup.
            when (val outcome = importer.add(name, lang, code, settings = settings[name])) {
                is ScriptImport.Outcome.Skipped -> skipped += when (val why = outcome.why) {
                    is ScriptImport.Why.NeedsNewer -> "$fileName (needs Kolahal ${why.version})"
                    is ScriptImport.Why.OtherLanguage -> fileName
                }
                is ScriptImport.Outcome.Imported -> when (outcome.change) {
                    ScriptImport.Change.CREATED -> created++
                    ScriptImport.Change.UPDATED -> updated++
                    ScriptImport.Change.UNCHANGED -> Unit
                }
            }
        }
        return ImportResult(created, updated, skipped)
    }

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
