package com.mtwabpin.kolahal.data

import android.content.Context
import android.net.Uri
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.script.Files
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.terminal.InstantRun

/**
 * One JSON file with everything a user customises: settings, aliases, hidden/renamed apps and
 * all scripts (code + timers). It is written and read through the Storage Access Framework, so it
 * can be kept on a computer, edited there, and imported again.
 *
 * ```
 * { "kolahal": 3,
 *   "config":  { "theme.name": "gruvbox", ... },          // only changed settings
 *   "aliases": { "m": "open maps" },
 *   "apps":    { "hidden": ["<component>#<user>"], "labels": { "<component>#<user>": "Name" }, "pinned": [...] },
 *   "scripts": [ { "name": "hn", "lang": "lua", "code": "...", "every": 30,
 *                  "at": ["08:00"], "notify": true, "enabled": true,
 *                  "access": { "header": false } } ],                     // only "false" is restored
 *   "data":    { ... },                                     // everything else: [FullBackup]
 *   "font":    { "name": "...", "base64": "..." },          // an imported terminal font
 *   "widgets": [ ... ],                                     // so the app can offer to add them again
 *   "themes":  { "mine": { "background": "#0B0D0B", ... } } }   // made in the theme builder
 * ```
 */
class Backup(private val context: Context) {
    private val config = Config.get(context)
    private val prefs = AppPrefs.get(context)
    private val scripts = ScriptRepository.get(context)

    fun export(uri: Uri): Int {
        Files.writeText(context, uri, text())
        return scripts.all().size
    }

    /** The whole backup as the file holds it. Throws when it's too big to import again. */
    internal fun text(): String {
        val all = scripts.all()
        val serials = FullBackup.privateSerials(context)
        val data = mapOf(
            "kolahal" to VERSION,
            "config" to Keys.ALL.filterNot(config::isDefault).associate { it.key to config.display(it) },
            "aliases" to prefs.aliases(),
            "apps" to mapOf(
                "hidden" to prefs.hiddenKeys().filterNot { FullBackup.isPrivateApp(it, serials) },
                "labels" to prefs.labels().filterKeys { !FullBackup.isPrivateApp(it, serials) },
                "pinned" to prefs.pinned().filterNot { FullBackup.isPrivateApp(it, serials) },
            ),
            "scripts" to all.map {
                mapOf(
                    "name" to it.name, "lang" to it.lang.ext, "code" to it.code, "every" to it.intervalMin,
                    "at" to it.atTimes, "notify" to it.notify, "enabled" to it.enabled,
                    // Only what you switched off. What a script may use must be allowed on each phone.
                    "access" to it.access.filterValues { allowed -> !allowed },
                )
            },
            // Everything else: todos, notes, reminders, feeds, script stores, log and history.
            "data" to FullBackup.export(context),
            // Widget ids can't move between devices. The list lets the Widgets page offer "Add again".
            // An imported terminal font travels with the backup (base64; imports are capped at 10 MB).
            "font" to com.mtwabpin.kolahal.ui.TermFonts.customFile(context)?.let { f ->
                mapOf("name" to f.name, "base64" to android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
            },
            "widgets" to runCatching { com.mtwabpin.kolahal.widgets.WidgetHost.get(context).exportWidgets() }.getOrDefault(emptyList()),
            // Themes made in the theme builder: the name, mapped to colours by role as "#RRGGBB".
            "themes" to CustomThemes.themes.value.mapValues { (_, colors) -> colors.mapValues { CustomThemes.hex(it.value) } },
        )
        val text = Json.stringify(data)
        val mb = utf8Size(text) / (1024 * 1024)
        if (mb >= Files.MAX_BACKUP_BYTES / (1024 * 1024)) throw TooBig(
            "the backup would be $mb MB, more than the ${Files.MAX_BACKUP_BYTES / (1024 * 1024)} MB import limit: " +
                "lower log.keep-lines or clear the log (clear all), then export again"
        )
        return text
    }

    /** A backup bigger than an import accepts. A smaller log fixes it; another folder doesn't. */
    class TooBig(message: String) : IllegalStateException(message)

    data class Result(
        val settings: Int, val aliases: Int, val scripts: Int, val disabled: Int, val skipped: List<String>,
        /** Rows restored from the raw data section (0 for older backups that hold only settings). */
        val restored: Int = 0,
        val full: Boolean = false,
        /** What from the file is worth a look: where searches go, what gestures run, and aliases that run code or change settings. */
        val review: List<String> = emptyList(),
    )

    fun import(uri: Uri): Result {
        val text = Files.readText(context, uri, Files.MAX_BACKUP_BYTES)
        val root = Json.parse(text) as? Map<*, *> ?: throw IllegalStateException("not a Kolahal backup")
        if (root["kolahal"] == null) throw IllegalStateException("not a Kolahal backup (missing \"kolahal\")")
        val skipped = ArrayList<String>()

        // Raw data first, so the readable sections below (which people edit by hand) win.
        val data = root["data"] as? Map<*, *>
        val restored = data?.let { FullBackup.importRaw(context, it) } ?: 0

        // Themes before settings, because `theme.name` may name one of them.
        (root["themes"] as? Map<*, *>)?.forEach { (name, roles) ->
            val colors = (roles as? Map<*, *>)?.entries?.mapNotNull { (role, hex) ->
                hex?.toString()?.let(CustomThemes::parseHex)?.let { role.toString() to it }
            }?.toMap()?.filterKeys(CustomThemes::isKey)
            val n = name?.toString()?.lowercase()
            if (n != null && CustomThemes.NAME.matches(n) && n !in Keys.BUILT_IN_THEMES && colors != null && CustomThemes.complete(colors) != null) {
                CustomThemes.save(context, n, colors)
            } else skipped += "theme $name"
        }

        var settings = 0
        // Older backups use the setting names from before 0.3.0, and the items of the full header.
        val savedConfig = (root["config"] as? Map<*, *>)?.entries?.mapNotNull { (k, v) -> if (k != null && v != null) k.toString().lowercase() to v.toString() else null }?.toMap()
        savedConfig?.let(Keys::migrate)?.forEach { (k, v) ->
            val key = Keys.byName(k)
            if (key != null && config.set(key, v)) settings++ else skipped += "config $k"
        }

        var aliases = 0
        (root["aliases"] as? Map<*, *>)?.forEach { (k, v) ->
            val name = k?.toString()
            when {
                name == null || v == null -> Unit
                // The same rule as `alias`: an alias must never hide a built-in command.
                name in BUILT_INS -> skipped += "alias $name (built-in command)"
                else -> { prefs.setAlias(name, v.toString()); aliases++ }
            }
        }

        (root["apps"] as? Map<*, *>)?.let { apps ->
            (apps["hidden"] as? List<*>)?.forEach { prefs.setHidden(it.toString(), true) }
            (apps["labels"] as? Map<*, *>)?.forEach { (k, v) -> if (k != null) prefs.setLabel(k.toString(), v?.toString()) }
            (apps["pinned"] as? List<*>)?.forEach { prefs.setPinned(it.toString(), true) }
        }

        var count = 0
        var disabled = 0
        // Scripts this import created or changed. A later entry with the same name finds the code already
        // in place, so it must not count as unchanged and switch the script on.
        val changedHere = mutableSetOf<String>()
        (root["scripts"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { m ->
            val name = m["name"]?.toString()?.lowercase()
            val lang = m["lang"]?.toString()?.let(ScriptLang::of)
            val code = m["code"]?.toString()
            // Your own scripts: a name that an app also has is kept (the script arrives switched off, as all do).
            if (name == null || lang == null || code == null || !com.mtwabpin.kolahal.script.ScriptNames.NAME.matches(name) || name in BUILT_INS) {
                skipped += "script ${m["name"]}"
                return@forEach
            }
            if (com.mtwabpin.kolahal.script.ScriptMeta.needsNewer(code, com.mtwabpin.kolahal.BuildConfig.VERSION_NAME)) {
                skipped += "script $name (needs Kolahal ${com.mtwabpin.kolahal.script.ScriptMeta.requires(code)})"
                return@forEach
            }
            val existing = scripts.byName(name)
            // New code arrives switched off, both for a new script and for a replaced one.
            var isNew = false
            val script = when {
                existing == null -> scripts.create(name, lang, code, enabled = false).also { isNew = true }
                existing.lang != lang -> { skipped += "script $name (language differs)"; return@forEach }
                else -> existing.also { if (it.code != code) { scripts.replaceCode(it.id, code); isNew = true } }
            }
            if (isNew) changedHere += name else if (name in changedHere) isNew = true
            (m["every"] as? Number)?.toLong()?.let { every ->
                val minutes = every.coerceIn(0, 7L * 24 * 60).toInt()
                scripts.setInterval(script.id, if (minutes in 1 until ScriptScheduler.MIN_INTERVAL_MIN) ScriptScheduler.MIN_INTERVAL_MIN else minutes)
            }
            (m["at"] as? List<*>)?.let { at ->
                scripts.setAtTimes(script.id, at.mapNotNull { ScriptScheduler.normalizeTime(it.toString()) }.distinct().sorted())
            }
            (m["notify"] as? Boolean)?.let { scripts.setNotify(script.id, it) }
            // Access that was switched off travels with a backup. What a script may use is only allowed on this phone.
            (m["access"] as? Map<*, *>)?.forEach { (item, on) -> if (on == false) scripts.setAccess(script.id, item.toString(), false) }
            // New or changed scripts from a backup arrive switched off. Review them before they run.
            if (isNew) {
                scripts.setEnabled(script.id, false)
                disabled++
            } else {
                (m["enabled"] as? Boolean)?.let { on ->
                    scripts.setEnabled(script.id, on)
                    if (on) scripts.byId(script.id)?.let { com.mtwabpin.kolahal.script.ScriptAccess.askForNew(context, it) }
                }
            }
            runCatching { scripts.byId(script.id)?.let { ScriptScheduler.sync(context, it) } }.onFailure { skipped += "timers of script $name" }
            count++
        }
        (root["font"] as? Map<*, *>)?.let { font ->
            val name = font["name"]?.toString()?.takeIf { it == "custom.ttf" || it == "custom.otf" }
            val data = (font["base64"] as? String)?.let { runCatching { android.util.Base64.decode(it, android.util.Base64.NO_WRAP) }.getOrNull() }
            if (name != null && data != null && data.size <= 10 * 1024 * 1024) {
                runCatching {
                    val dir = java.io.File(context.filesDir, "fonts").apply { mkdirs() }
                    dir.listFiles()?.filter { it.name.startsWith("custom.") }?.forEach { it.delete() }
                    java.io.File(dir, name).writeBytes(data)
                }.onFailure { skipped += "font" }
            } else skipped += "font"
        }
        (root["widgets"] as? List<*>)?.let { list ->
            @Suppress("UNCHECKED_CAST")
            runCatching { com.mtwabpin.kolahal.widgets.WidgetHost.get(context).importWidgets(list.filterIsInstance<Map<String, Any?>>()) }
                .onFailure { skipped += "widgets" }
        }
        if (data != null) {
            FullBackup.importScriptStores(context, data)
            // Home restarts after this and does them again; a failure is logged either way.
            Quietly.run(context, "Setting reminders again") { com.mtwabpin.kolahal.productivity.ReminderScheduler.rescheduleAll(context) }
            Quietly.run(context, "Scheduling feeds") { com.mtwabpin.kolahal.feeds.FeedScheduler.sync(context) }
            Quietly.run(context, "Scheduling scripts") { ScriptScheduler.syncAll(context) }
        }
        // A backup decides where searches go, what a gesture runs and what an alias runs. List those worth a look:
        // the settings that aren't the defaults, and the aliases that run code or change settings ([toReview]). An
        // alias can have an app's name, so typing that name would run it. (Read from the file: the raw data skips the cache.)
        val sensitiveAliases = context.getSharedPreferences("aliases", Context.MODE_PRIVATE).all
            .mapNotNull { (name, body) -> (body as? String)?.takeIf(::toReview)?.let { name to it } }
            .sortedBy { it.first }
            .map { (name, body) -> "alias $name = ${if (body.length > 60) body.take(59) + "…" else body}" }
        val review = listOf(Keys.SEARCH_URL, Keys.GESTURE_DOUBLE_TAP, Keys.GESTURE_SWIPE_DOWN, Keys.GESTURE_SWIPE_UP)
            .filterNot(config::isDefault).map { "${it.key} = ${config.display(it)}" } + sensitiveAliases
        return Result(settings, aliases, count, disabled, skipped, restored, full = data != null, review = review)
    }

    companion object {
        /**
         * Commands that an alias from a file is worth a look for: they run code, change settings or
         * aliases, delete things, or call and text. (Narrower than [InstantRun.SENSITIVE], which also
         * holds harmless commands that typing must not start by itself.)
         */
        private val REVIEW = setOf(
            "lua", "js", "config", "alias", "unalias", "script", "scripts", "clear", "uninstall", "update",
            "backup", "wallpaper", "call", "sms", "url",
        )

        /** Whether an alias that runs [body] is worth a look after an import ([REVIEW], or the calculator `=`, which runs Lua). */
        internal fun toReview(body: String): Boolean {
            val line = body.trim()
            return line.startsWith("=") || InstantRun.canonical(line.substringBefore(' ').lowercase()) in REVIEW
        }

        const val VERSION = 3
        const val FILE_NAME = "kolahal-backup.json"
        private val BUILT_INS get() = com.mtwabpin.kolahal.terminal.CommandCatalog.RESERVED_NAMES
    }
}

/**
 * How many bytes [text] takes in UTF-8, as the file holds it. It counts them instead of making a
 * copy to measure, because a backup can be tens of MB. A lone surrogate counts as one byte, as it is written.
 */
internal fun utf8Size(text: String): Long {
    var n = 0L
    var i = 0
    while (i < text.length) {
        val c = text[i]
        n += when {
            c.code < 0x80 -> 1
            c.code < 0x800 -> 2
            c.isHighSurrogate() && i + 1 < text.length && text[i + 1].isLowSurrogate() -> { i++; 4 }
            c.isSurrogate() -> 1
            else -> 3
        }
        i++
    }
    return n
}
