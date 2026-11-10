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
 * Structured context saved with a log line. The terminal uses it to decide what a tap does and
 * which actions to offer on long-press (open link, launch app, rerun script, ...).
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
    /** A script line's body, shown muted under its title ([TITLE]). */
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
        /** An image URL that a script attached. It shows under the line, as wide as the log. */
        const val IMAGE = "image"
        /** A command that runs immediately when the line is tapped (setup checklist, hints). */
        const val RUN = "run"
        /** A phone number (tap dials) and a contact lookup URI (long-press opens the contact). */
        const val PHONE = "phone"
        /** An app shortcut id (with PKG/USER). A tap starts the shortcut. */
        const val SHORTCUT = "shortcut"
        /** An activity's class name (with PKG/USER), from `activity`. A tap opens that activity. */
        const val ACTIVITY = "activity"
        const val CONTACT = "contact"
        /** The source line of a script error. A tap opens the editor at that line. */
        const val LINE = "line"
        /**
         * The id of the command line whose run logged this line, wherever the line landed. A script's
         * or a feed's lines arrive when the work is done, after other lines. "Delete command and
         * output" deletes them too.
         */
        const val FROM = "from"
        /**
         * Until 0.4.1, a `── name · 22:40 ──` line started a script run or a feed fetch. Now each line
         * has its own time and name. Old header lines are deleted ([Db]) and not restored from backups.
         */
        const val RUN_HEADER = "runheader"
        /** A line a script logged. Its text is a title, drawn bold, with [BODY] muted under it. */
        const val TITLE = "title"
        /** A script line's body: `log(title, body)`. */
        const val BODY = "body"
        /** The second column text (help, config and script listings show as two columns). */
        const val DESC = "desc"
        /** A JSON list of {pkg, cls, user}. The line shows as a grid of app chips. */
        const val APPS = "apps"
        /** Productivity items by database id: a todo ("done" = 1 when finished), a note, a reminder. */
        const val TODO = "todo"
        const val TODO_DONE = "done"
        const val NOTE = "note"
        const val REMINDER = "reminder"
        /** A calendar event id (with BEGIN/END of the instance). A tap opens it. */
        const val EVENT = "event"
        const val BEGIN = "begin"
        const val END = "end"
        /** The line aligns columns with spaces, so it is drawn in the terminal font even with `font.sans all`. */
        const val MONO = "mono"
        /**
         * A bar after a built-in command's line, as `from,to,role` (fractions from 0 to 1, and a
         * colour role, which may be empty): `unlocks`, `usage`, `volume`. Scripts can't set it;
         * they put bars in a body with `fmt.bar`.
         */
        const val BAR = "bar"

        /**
         * An item of a list starts at this line ("1"): a story, a contact, a feed item. It gets the
         * `log.item-spacing` gap above it, and the colour bar runs through the gap, so the items stand
         * apart but stay one group. Not on a group's first line, nor right under the command that ran it.
         * Scripts set it with `ctx.gap`.
         */
        const val GAP = "gap"

        /**
         * A saved script's buttons under its line (`ctx.actions`), as JSON: `[["Snooze 30 min", "snooze 30"], …]`,
         * each a label and the arguments the script runs with when it is tapped. [ACTION_DONE] is
         * the index of the one tapped; the line then shows only that one, as done.
         */
        const val ACTIONS = "actions"
        const val ACTION_DONE = "action-done"

        /**
         * A person's line (a call, a contact): [PERSON] is the name (or number) that their picture
         * beside the line stands for, and [PHOTO] the contact's photo as Android keeps it (a
         * content: address). Without a photo, or when it can't be read, the picture is the name's
         * first letters. Only Kolahal's own lines have them.
         */
        const val PERSON = "person"
        const val PHOTO = "photo"

        /** The buttons in [ACTIONS]: each label and its arguments. Nothing that can't be read. */
        fun actionsOf(ctx: LogContext): List<Pair<String, String>> {
            val raw = ctx.values[ACTIONS] ?: return emptyList()
            val list = runCatching { org.json.JSONArray(raw) }.getOrNull() ?: return emptyList()
            return (0 until list.length()).mapNotNull { i ->
                val a = list.optJSONArray(i) ?: return@mapNotNull null
                val label = a.optString(0).takeIf { it.isNotBlank() } ?: return@mapNotNull null
                label to a.optString(1)
            }
        }

        /** [BAR] filled to [fraction] (0 to 1), in [role] (null: the accent colour). */
        fun bar(fraction: Double, role: String? = null): Map<String, String> =
            mapOf(BAR to "0,${StyledText.number(if (fraction.isNaN()) 0.0 else fraction.coerceIn(0.0, 1.0))},${role.orEmpty()}")

        val EMPTY = LogContext()

        /** [MONO], to add to a line's context. */
        val ALIGNED = mapOf(MONO to "1")

        /** [GAP]: the line starts an item of a list. To add to a line's context. */
        val ITEM = mapOf(GAP to "1")

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
    /** Starred from its long-press menu. It is kept beyond the log's limits and through a clear ([LogRepository.trim]). */
    val starred: Boolean = false,
)

/** What the line shows, as plain text: its text, with a script line's body under it. */
val LogEntry.shownText: String get() = StyledText.plain(ctx.body?.let { "$text\n$it" } ?: text)
