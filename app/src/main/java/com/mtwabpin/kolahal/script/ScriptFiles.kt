/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.script

import android.content.Context
import android.net.Uri
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository

/** Single-file script import/export, for files picked in the system file manager. */
class ScriptFiles(private val context: Context) {
    private val scripts = ScriptRepository.get(context)

    data class ImportResult(
        val created: List<String>,
        val updated: List<String>,
        val skipped: List<String>,
        /** The shaders (`.agsl` files), now the user's own ([Shaders]). */
        val shaders: List<String> = emptyList(),
    )

    /**
     * Saves one shader per `.agsl` file as the user's own. Any other file is skipped, scripts too:
     * the Shaders screen adds only shaders.
     */
    fun importShaders(uris: List<Uri>): ImportResult {
        val shaders = ArrayList<String>()
        val skipped = ArrayList<String>()
        for (uri in uris) {
            val fileName = nameOf(uri)
            if (isShader(fileName)) importShader(uri, fileName, shaders, skipped)
            else skipped += "$fileName (not .${ShaderCatalog.EXT})"
        }
        return ImportResult(emptyList(), emptyList(), skipped, shaders)
    }

    private fun nameOf(uri: Uri): String =
        Files.displayName(context, uri) ?: uri.lastPathSegment ?: "?"

    private fun isShader(fileName: String) =
        fileName.endsWith(".${ShaderCatalog.EXT}", ignoreCase = true)

    /** Saves the shader in the file at [uri]: its name goes into [shaders], or why it can't into [skipped]. */
    private fun importShader(
        uri: Uri,
        fileName: String,
        shaders: MutableList<String>,
        skipped: MutableList<String>
    ) {
        runCatching {
            val name = ShaderCatalog.nameOf(fileName) ?: error("not a name a shader can have")
            Shaders.save(context, name, Files.readText(context, uri))
            shaders += name
        }.onFailure { skipped += "$fileName (${it.message})" }
    }

    /** Creates or updates one script per `.lua` / `.js` file. The file name becomes the script name. An `.agsl` file becomes a shader. */
    fun import(uris: List<Uri>): ImportResult {
        val created = ArrayList<String>()
        val updated = ArrayList<String>()
        val skipped = ArrayList<String>()
        val shaders = ArrayList<String>()
        val importer = ScriptImport(context)
        for (uri in uris) {
            val fileName = nameOf(uri)
            if (isShader(fileName)) {
                importShader(uri, fileName, shaders, skipped)
                continue
            }
            val lang = ScriptLang.of(fileName.substringAfterLast('.', ""))
            val name =
                fileName.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9_-]"), "-")
                    .trim('-').take(40)
            if (lang == null || name.isEmpty()) {
                skipped += "$fileName (not .lua / .js)"
                continue
            }
            // A new name can't be a built-in's or an app's name. A script that already exists is updated, whatever its name.
            if (scripts.byName(name) == null) ScriptNames.clash(context, name)?.let { why ->
                skipped += "$fileName ($why)"
                continue
            }
            val code = runCatching { Files.readText(context, uri) }.getOrElse {
                skipped += "$fileName (${it.message})"
                continue
            }
            // A script from a file arrives switched off. So does a script whose code a file replaces.
            // Switching it on is the user's approval of its timers, triggers and `@uses`.
            // A file brings no settings: a new script gets the timers its code suggests, and only
            // replaced code sets a script's timers again.
            when (val outcome = importer.add(name, lang, code, resync = false)) {
                is ScriptImport.Outcome.Skipped -> skipped += when (val why = outcome.why) {
                    // Written for a newer Kolahal, so it may call things this version doesn't have.
                    is ScriptImport.Why.NeedsNewer -> "$fileName (needs Kolahal ${why.version})"
                    is ScriptImport.Why.OtherLanguage -> "$fileName (a ${why.has.ext} script '$name' exists)"
                }

                is ScriptImport.Outcome.Imported -> when (outcome.change) {
                    ScriptImport.Change.CREATED -> created += "$name.${lang.ext}"
                    ScriptImport.Change.UPDATED -> updated += "$name.${lang.ext}"
                    ScriptImport.Change.UNCHANGED -> Unit
                }
            }
        }
        return ImportResult(created, updated, skipped, shaders)
    }

    fun export(script: Script, uri: Uri) {
        Files.writeText(context, uri, script.code)
    }
}
