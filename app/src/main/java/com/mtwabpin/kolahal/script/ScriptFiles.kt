package com.mtwabpin.kolahal.script

import android.content.Context
import android.net.Uri
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.ScriptRepository

/** Single-file script import/export, for files picked in the system file manager. */
class ScriptFiles(private val context: Context) {
    private val scripts = ScriptRepository.get(context)

    data class ImportResult(val created: List<String>, val updated: List<String>, val skipped: List<String>)

    /** Creates or updates one script per `.lua` / `.js` file. The file name becomes the script name. */
    fun import(uris: List<Uri>): ImportResult {
        val created = ArrayList<String>()
        val updated = ArrayList<String>()
        val skipped = ArrayList<String>()
        val importer = ScriptImport(context)
        for (uri in uris) {
            val fileName = Files.displayName(context, uri) ?: uri.lastPathSegment ?: "?"
            val lang = ScriptLang.of(fileName.substringAfterLast('.', ""))
            val name = fileName.substringBeforeLast('.').lowercase().replace(Regex("[^a-z0-9_-]"), "-").trim('-').take(40)
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
        return ImportResult(created, updated, skipped)
    }

    fun export(script: Script, uri: Uri) {
        Files.writeText(context, uri, script.code)
    }
}
