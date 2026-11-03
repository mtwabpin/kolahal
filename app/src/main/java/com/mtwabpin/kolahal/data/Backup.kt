package com.mtwabpin.kolahal.data

import android.content.Context
import android.net.Uri
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.script.Files
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.ScriptScheduler

/**
 * One JSON file with everything a user customises: settings, aliases, hidden/renamed apps and
 * all scripts (code + timers). Written and read through the Storage Access Framework, so it can
 * be kept on a computer, edited there, and imported again.
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
 *   "widgets": [ ... ],                                     // to offer adding them again
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

    /** The whole backup as a file holds it; throws when it's too big to import again. */
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
                    // Only what you switched off: what a script may use is allowed on each phone.
                    "access" to it.access.filterValues { allowed -> !allowed },
                )
            },
            // Everything else: todos, notes, reminders, feeds, script stores, log, history.
            "data" to FullBackup.export(context),
            // Widget ids can't move between devices; the list lets the Widgets page offer "Add again".
            // An imported terminal font travels with the backup (base64; imports are capped at 10 MB).
            "font" to com.mtwabpin.kolahal.ui.TermFonts.customFile(context)?.let { f ->
                mapOf("name" to f.name, "base64" to android.util.Base64.encodeToString(f.readBytes(), android.util.Base64.NO_WRAP))
            },
            "widgets" to runCatching { com.mtwabpin.kolahal.widgets.WidgetHost.get(context).exportWidgets() }.getOrDefault(emptyList()),
            // Themes made in the theme builder: name to colours by role, as "#RRGGBB".
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

    /** A backup bigger than an import takes: a smaller log fixes it, not another folder. */
    class TooBig(message: String) : IllegalStateException(message)

    data class Result(
        val settings: Int, val aliases: Int, val scripts: Int, val disabled: Int, val skipped: List<String>,
        /** Rows restored from the raw data section (0 for older, settings-only backups). */
        val restored: Int = 0,
        val full: Boolean = false,
        /** Settings from the file worth a look: where searches go, what gestures run. */
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

        // Themes before settings: `theme.name` may be one of them.
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
        // Older backups use the names settings had before 0.3.0, and have no choices for the one-line header.
        val carry = ((root["kolahal"] as? Number)?.toInt() ?: 0) < 3
        val savedConfig = (root["config"] as? Map<*, *>)?.entries?.mapNotNull { (k, v) -> if (k != null && v != null) k.toString().lowercase() to v.toString() else null }?.toMap()
        savedConfig?.let(Keys::migrate)?.let { if (carry) Keys.carryCompact(it) else it }?.forEach { (k, v) ->
            val key = Keys.byName(k)
            if (key != null && config.set(key, v)) settings++ else skipped += "config $k"
        }

        var aliases = 0
        (root["aliases"] as? Map<*, *>)?.forEach { (k, v) ->
            val name = k?.toString()
            when {
                name == null || v == null -> Unit
                // Same rule as `alias`: an alias must never shadow a built-in command.
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
        (root["scripts"] as? List<*>)?.filterIsInstance<Map<*, *>>()?.forEach { m ->
            val name = m["name"]?.toString()?.lowercase()
            val lang = m["lang"]?.toString()?.let(ScriptLang::of)
            val code = m["code"]?.toString()
            // Your own scripts: a name an app also has stays (it arrives switched off, as all do).
            if (name == null || lang == null || code == null || !com.mtwabpin.kolahal.script.ScriptNames.NAME.matches(name) || name in BUILT_INS) {
                skipped += "script ${m["name"]}"
                return@forEach
            }
            if (com.mtwabpin.kolahal.script.ScriptMeta.needsNewer(code, com.mtwabpin.kolahal.BuildConfig.VERSION_NAME)) {
                skipped += "script $name (needs Kolahal ${com.mtwabpin.kolahal.script.ScriptMeta.requires(code)})"
                return@forEach
            }
            val existing = scripts.byName(name)
            // New code, whether a new script or a replaced one, arrives switched off.
            var isNew = false
            val script = when {
                existing == null -> scripts.create(name, lang, code, enabled = false).also { isNew = true }
                existing.lang != lang -> { skipped += "script $name (language differs)"; return@forEach }
                else -> existing.also { if (it.code != code) { scripts.replaceCode(it.id, code); isNew = true } }
            }
            (m["every"] as? Number)?.toLong()?.let { every ->
                val minutes = every.coerceIn(0, 7L * 24 * 60).toInt()
                scripts.setInterval(script.id, if (minutes in 1 until ScriptScheduler.MIN_INTERVAL_MIN) ScriptScheduler.MIN_INTERVAL_MIN else minutes)
            }
            (m["at"] as? List<*>)?.let { at ->
                scripts.setAtTimes(script.id, at.mapNotNull { ScriptScheduler.normalizeTime(it.toString()) }.distinct().sorted())
            }
            (m["notify"] as? Boolean)?.let { scripts.setNotify(script.id, it) }
            // Access switched off travels with a backup; what a script may use is only allowed on this phone.
            (m["access"] as? Map<*, *>)?.forEach { (item, on) -> if (on == false) scripts.setAccess(script.id, item.toString(), false) }
            // New or changed scripts from a backup arrive switched off: review them before they run.
            if (isNew) {
                scripts.setEnabled(script.id, false)
                disabled++
            } else {
                (m["enabled"] as? Boolean)?.let { on ->
                    scripts.setEnabled(script.id, on)
                    if (on) scripts.byId(script.id)?.let { com.mtwabpin.kolahal.script.ScriptAccess.askForNew(context, it) }
                }
            }
            runCatching { scripts.byId(script.id)?.let { ScriptScheduler.sync(context, it) } }
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
                }
            }
        }
        (root["widgets"] as? List<*>)?.let { list ->
            @Suppress("UNCHECKED_CAST")
            runCatching { com.mtwabpin.kolahal.widgets.WidgetHost.get(context).importWidgets(list.filterIsInstance<Map<String, Any?>>()) }
        }
        if (data != null) {
            FullBackup.importScriptStores(context, data)
            runCatching { com.mtwabpin.kolahal.productivity.ReminderScheduler.rescheduleAll(context) }
            runCatching { com.mtwabpin.kolahal.feeds.FeedScheduler.sync(context) }
            runCatching { ScriptScheduler.syncAll(context) }
        }
        // A backup decides where searches go and what a gesture runs: name those that aren't the defaults.
        val review = listOf(Keys.SEARCH_URL, Keys.GESTURE_DOUBLE_TAP, Keys.GESTURE_SWIPE_DOWN, Keys.GESTURE_SWIPE_UP)
            .filterNot(config::isDefault).map { "${it.key} = ${config.display(it)}" }
        return Result(settings, aliases, count, disabled, skipped, restored, full = data != null, review = review)
    }

    companion object {
        const val VERSION = 3
        const val FILE_NAME = "kolahal-backup.json"
        private val BUILT_INS get() = com.mtwabpin.kolahal.terminal.CommandCatalog.RESERVED_NAMES
    }
}

/**
 * How many bytes [text] takes in UTF-8, as the file holds it: counted, rather than made a copy of
 * to measure, as a backup can be tens of MB. A lone surrogate counts as the one byte it's written as.
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
