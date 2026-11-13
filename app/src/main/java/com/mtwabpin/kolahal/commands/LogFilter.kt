package com.mtwabpin.kolahal.commands

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind

/**
 * A source of log lines, used to show only the lines from that source. It is either:
 * - one name (a script, a feed, an app's notifications), as shown at the end of each line (`· xkcd`);
 * - a whole [Kind].
 *
 * `log only xkcd` filters the log. `/@xkcd words` filters a search.
 */
sealed class LogFilter {
    /** The name shown on the filter bar and in suggestions: `xkcd`, `Hacker News`, `feeds`. */
    abstract val label: String

    /** The form typed after `@` or `log only`: lowercase letters and digits only (`hackernews`). */
    abstract val word: String

    abstract fun matches(e: LogEntry): Boolean

    /**
     * A condition on the log table that every line [matches] accepts passes too, with its arguments.
     * The database leaves out most other lines with it, so they aren't read and parsed. The lines it
     * lets through are still checked with [matches]. Null when it can't narrow.
     */
    abstract val sql: Sql?

    /** A WHERE condition and its arguments. */
    data class Sql(val where: String, val args: List<String>)

    /** [STARRED] means the lines you starred, of any kind ([LogEntry.starred]). */
    enum class Kind(val word: String) {
        COMMANDS("commands"), SCRIPTS("scripts"), FEEDS("feeds"), NOTIFICATIONS("notifications"),
        APPS("apps"), CALLS("calls"), REMINDERS("reminders"), STARRED("starred"),
    }

    data class OfKind(val kind: Kind) : LogFilter() {
        override val label get() = kind.word
        override val word get() = kind.word
        override fun matches(e: LogEntry) = if (kind == Kind.STARRED) e.starred else kind in kindsOf(e)
        override val sql get() = when (kind) {
            Kind.STARRED -> Sql("starred = 1", emptyList())
            Kind.FEEDS -> hasKey(com.mtwabpin.kolahal.feeds.FeedWorker.CTX_FEED)
            Kind.NOTIFICATIONS -> hasKey(com.mtwabpin.kolahal.notifications.KolahalNotificationListener.CTX_KEY)
            Kind.SCRIPTS -> Sql("(kind = ? OR ctx LIKE ?)", listOf(LogKind.SCRIPT.key, "%\"${LogContext.SCRIPT}\"%"))
            // Most lines are commands' (anything with no other source): nothing to leave out.
            Kind.COMMANDS -> null
            else -> KIND_SOURCES.entries.firstOrNull { it.value == kind }?.let { Sql("source = ?", listOf(it.key)) }
        }
    }

    /** A script, a feed or an app: the name at the end of a line. Case and spaces are ignored. */
    data class Named(override val label: String) : LogFilter() {
        override val word get() = key(label)
        override fun matches(e: LogEntry) = word.isNotEmpty() && (key(e.source) == word || key(e.ctx.script) == word)
        // The letters of [word] in order, anything between them (case, spaces and signs don't count). LIKE ignores
        // case for ASCII only, so a word with other letters isn't narrowed.
        override val sql get() = word.takeIf { w -> w.isNotEmpty() && w.all { it.code < 128 } }?.let { w ->
            val letters = w.toList().joinToString("%")
            Sql("(source LIKE ? OR ctx LIKE ?)", listOf("%$letters%", "%\"${LogContext.SCRIPT}\":\"%$letters%"))
        }
    }

    companion object {
        /** Sources that are a kind of their own. Every line from such a source ends with the same word. */
        private val KIND_SOURCES = mapOf(
            "apps" to Kind.APPS,
            com.mtwabpin.kolahal.calls.CallEvents.SOURCE to Kind.CALLS,
            "remind" to Kind.REMINDERS,
        )

        fun key(name: String?): String = name.orEmpty().lowercase().filter { it.isLetterOrDigit() }

        /** A line whose context has [key] (stored as JSON, so the key is in quotes). */
        private fun hasKey(key: String) = Sql("ctx LIKE ?", listOf("%\"$key\"%"))

        /** The condition for a line of any of [filters], or null if one of them can't narrow. */
        fun sqlOfAny(filters: List<LogFilter>): Sql? {
            val each = filters.map { it.sql ?: return null }.ifEmpty { return null }
            return Sql(each.joinToString(" OR ", "(", ")") { "(${it.where})" }, each.flatMap { it.args })
        }

        /** Parses `xkcd`, `@feeds`, `Hacker News`. A kind's word gives that kind. Anything else is a name. */
        fun parse(text: String): LogFilter? {
            val name = text.trim().removePrefix("@").trim()
            if (key(name).isEmpty()) return null
            return Kind.entries.firstOrNull { it.word == name.lowercase() }?.let(::OfKind) ?: Named(name)
        }

        /**
         * Returns the kinds that [e] belongs to. A line can belong to two kinds: when a command runs
         * a script, what the script logs belongs to both the script and the command ([LogContext.FROM]).
         */
        fun kindsOf(e: LogEntry): Set<Kind> {
            val ctx = e.ctx.values
            val own = when {
                com.mtwabpin.kolahal.feeds.FeedWorker.CTX_FEED in ctx -> Kind.FEEDS
                com.mtwabpin.kolahal.notifications.KolahalNotificationListener.CTX_KEY in ctx -> Kind.NOTIFICATIONS
                e.source in KIND_SOURCES -> KIND_SOURCES.getValue(e.source!!)
                e.ctx.script != null || e.kind == LogKind.SCRIPT -> Kind.SCRIPTS
                else -> null
            }
            // Commands are: typed lines, what they printed, and lines with no other source (output from before 0.4.2).
            val command = e.kind == LogKind.COMMAND || LogContext.FROM in ctx || (own == null && e.source == null)
            return setOfNotNull(own, Kind.COMMANDS.takeIf { command })
        }

        /** The filter for "Only …" in [e]'s long-press menu: its script, feed or app, or else its kind. */
        fun forLine(e: LogEntry): LogFilter? {
            val kinds = kindsOf(e)
            val name = e.ctx.script ?: e.source
            KIND_SOURCES[e.source]?.let { return OfKind(it) }
            return when {
                name != null && kinds.any { it == Kind.SCRIPTS || it == Kind.FEEDS || it == Kind.NOTIFICATIONS } -> Named(name)
                Kind.COMMANDS in kinds -> OfKind(Kind.COMMANDS)
                else -> null
            }
        }

        /** Returns the kinds, then [names] (the log's sources, newest first), that start with [typed]. */
        fun suggestions(typed: String, names: List<String>): List<LogFilter> {
            val start = key(typed)
            val kinds = Kind.entries.map(::OfKind)
            val named = names.filter { key(it).isNotEmpty() && it !in KIND_SOURCES }.map(::Named)
            return (kinds + named).filter { it.word.startsWith(start) }.distinctBy { it.word }
        }
    }
}
