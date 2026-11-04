package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind

/**
 * Where log lines come from, to see only those: one name (a script, a feed, an app's
 * notifications), as each line shows at its end (`· xkcd`), or a whole [Kind]. `log only xkcd`
 * filters the log; `/@xkcd words` a search.
 */
sealed class LogFilter {
    /** What it is called on the filter bar and in suggestions: `xkcd`, `Hacker News`, `feeds`. */
    abstract val label: String

    /** As typed after `@` or `log only`: lowercase letters and digits (`hackernews`). */
    abstract val word: String

    abstract fun matches(e: LogEntry): Boolean

    /** [STARRED]: the lines you starred, whatever they are ([LogEntry.starred]). */
    enum class Kind(val word: String) {
        COMMANDS("commands"), SCRIPTS("scripts"), FEEDS("feeds"), NOTIFICATIONS("notifications"),
        APPS("apps"), CALLS("calls"), REMINDERS("reminders"), STARRED("starred"),
    }

    data class OfKind(val kind: Kind) : LogFilter() {
        override val label get() = kind.word
        override val word get() = kind.word
        override fun matches(e: LogEntry) = if (kind == Kind.STARRED) e.starred else kind in kindsOf(e)
    }

    /** A script, a feed or an app: the name a line ends with, any case, spaces aside. */
    data class Named(override val label: String) : LogFilter() {
        override val word get() = key(label)
        override fun matches(e: LogEntry) = word.isNotEmpty() && (key(e.source) == word || key(e.ctx.script) == word)
    }

    companion object {
        /** Sources that are kinds of their own: every line of theirs ends with the same word. */
        private val KIND_SOURCES = mapOf(
            "apps" to Kind.APPS,
            com.mtwabpin.kolahal.calls.CallEvents.SOURCE to Kind.CALLS,
            "remind" to Kind.REMINDERS,
        )

        fun key(name: String?): String = name.orEmpty().lowercase().filter { it.isLetterOrDigit() }

        /** `xkcd`, `@feeds`, `Hacker News`: a kind's word is the kind, anything else a name. */
        fun parse(text: String): LogFilter? {
            val name = text.trim().removePrefix("@").trim()
            if (key(name).isEmpty()) return null
            return Kind.entries.firstOrNull { it.word == name.lowercase() }?.let(::OfKind) ?: Named(name)
        }

        /**
         * The kinds [e] belongs to. A line can be in two: what a command's script logged is the
         * script's and the command's ([LogContext.FROM]).
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
            // Typed lines and what they printed; and lines from nowhere else (output from before 0.4.2).
            val command = e.kind == LogKind.COMMAND || LogContext.FROM in ctx || (own == null && e.source == null)
            return setOfNotNull(own, Kind.COMMANDS.takeIf { command })
        }

        /** What "Only …" on [e]'s long-press shows: its script, feed or app, else its kind. */
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

        /** Kinds, then [names] (the log's sources, newest first), starting with [typed]. */
        fun suggestions(typed: String, names: List<String>): List<LogFilter> {
            val start = key(typed)
            val kinds = Kind.entries.map(::OfKind)
            val named = names.filter { key(it).isNotEmpty() && it !in KIND_SOURCES }.map(::Named)
            return (kinds + named).filter { it.word.startsWith(start) }.distinctBy { it.word }
        }
    }
}
