package com.mtwabpin.kolahal.data

import androidx.compose.runtime.Immutable
import org.json.JSONObject

enum class LogKind {
    /** A command the user typed. Tapping it recalls it into the prompt. */
    COMMAND,
    OUTPUT,
    INFO,
    ERROR,
    /** Output from a script's run, a feed or a notification, not from a typed command. */
    SCRIPT;

    val key: String get() = name.lowercase()

    companion object {
        fun of(key: String): LogKind = entries.firstOrNull { it.key == key } ?: OUTPUT
    }
}

/**
 * Structured context saved alongside a log line. The terminal uses it to decide what a
 * tap does and which actions to offer on long-press (open link, launch app, rerun script, ...).
 *
 * Well-known keys are exposed as properties. A script may set only url, image, copy and mono.
 */
@Immutable
data class LogContext(val values: Map<String, String> = emptyMap()) {
    val url: String? get() = values[URL]
    val pkg: String? get() = values[PKG]
    val cls: String? get() = values[CLS]
    val userSerial: Long? get() = values[USER]?.toLongOrNull()
    val script: String? get() = values[SCRIPT]
    val copy: String? get() = values[COPY]
    val command: String? get() = values[COMMAND]
    val image: String? get() = values[IMAGE]
    /** A script line's body, muted under its title ([TITLE]). */
    val body: String? get() = values[BODY]

    val isEmpty: Boolean get() = values.isEmpty()

    operator fun plus(other: Map<String, String>) = LogContext(values + other)

    fun toJson(): String? = if (values.isEmpty()) null else JSONObject(values).toString()

    companion object {
        const val URL = "url"
        const val PKG = "pkg"
        const val CLS = "cls"
        const val USER = "user"
        const val SCRIPT = "script"
        const val COPY = "copy"
        const val COMMAND = "command"
        /** A notification's saved status-bar icon ([com.mtwabpin.kolahal.notifications.NotifIcons] key), shown instead of the app name. */
        const val ICON = "icon"
        /** An image URL a script attached: it shows under the line, as wide as the log. */
        const val IMAGE = "image"
        /** A command that runs immediately when the line is tapped (setup checklist, hints). */
        const val RUN = "run"
        /** Phone number (tap dials) and contact lookup URI (long-press opens the contact). */
        const val PHONE = "phone"
        /** An app shortcut id (with PKG/USER): tap starts the shortcut. */
        const val SHORTCUT = "shortcut"
        const val CONTACT = "contact"
        /** Source line of a script error (tap opens the editor there). */
        const val LINE = "line"
        /**
         * The id of the command line whose run logged this line, wherever it landed: a script's or a
         * feed's lines come when the work is done, after other lines. "Delete command and output"
         * takes them along.
         */
        const val FROM = "from"
        /**
         * The `── name · 22:40 ──` line that started a script run or a feed fetch until 0.4.1. Each
         * line has its time and name now; old ones are deleted ([Db]) and not restored from backups.
         */
        const val RUN_HEADER = "runheader"
        /** A line a script logged: its text is a title, drawn bold, with [BODY] muted under it. */
        const val TITLE = "title"
        /** A script line's body: `log(title, body)`. */
        const val BODY = "body"
        /** Second column text (help, config, script listings render as two columns). */
        const val DESC = "desc"
        /** JSON list of {pkg, cls, user}: the line renders as a grid of app chips. */
        const val APPS = "apps"
        /** Productivity items by database id: a todo ("done" = 1 when finished), note, reminder. */
        const val TODO = "todo"
        const val TODO_DONE = "done"
        const val NOTE = "note"
        const val REMINDER = "reminder"
        /** Calendar event id (with BEGIN/END of the instance): tap opens it. */
        const val EVENT = "event"
        const val BEGIN = "begin"
        const val END = "end"
        /** The line lines columns up with spaces: drawn in the terminal font even with `font.sans all`. */
        const val MONO = "mono"

        val EMPTY = LogContext()

        /** [MONO], to add to a line's context. */
        val ALIGNED = mapOf(MONO to "1")

        fun of(vararg pairs: Pair<String, String?>) =
            LogContext(pairs.mapNotNull { (k, v) -> v?.let { k to it } }.toMap())

        fun fromJson(json: String?): LogContext {
            if (json.isNullOrBlank()) return EMPTY
            return runCatching {
                val obj = JSONObject(json)
                LogContext(obj.keys().asSequence().associateWith { obj.optString(it) })
            }.getOrDefault(EMPTY)
        }
    }
}

@Immutable
data class LogEntry(
    val id: Long,
    val ts: Long,
    val kind: LogKind,
    val source: String?,
    val text: String,
    val ctx: LogContext,
    /** Starred from its long-press: kept past the log's limits and through a clear ([LogRepository.trim]). */
    val starred: Boolean = false,
)

/** What the line shows, as plain text: its text, and a script line's body under it. */
val LogEntry.shownText: String get() = StyledText.plain(ctx.body?.let { "$text\n$it" } ?: text)
