package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.script.ScriptScheduler

/** `feed` / `feeds`, `rss` and `notifs`. Kept out of [CommandProcessor] like the other command groups. */
class FeedCommands(
    context: Context,
    /** Finds the app `notifs mute <app>` names. */
    private val appCommands: AppCommands,
    private val ui: TerminalUi,
) : CommandGroup(context) {
    private val apps = AppRepository.get(context)


    fun rss(url: String) {
        if (url.isBlank()) {
            error("Usage: rss <feed url>")
            return
        }
        val quoted = url.replace("\\", "\\\\").replace("\"", "\\\"")
        ScriptScheduler.eval(
            context, ScriptLang.LUA,
            """
            local feed = rss.fetch("$quoted")
            for i, it in ipairs(feed.items) do
              if i > 15 then break end
              log(it.title, { url = it.link })
            end
            return (feed.title or "feed") .. ": " .. #feed.items .. " items"
            """.trimIndent(),
            name = "rss",
        )
    }

    /** Built-in RSS/Atom subscriptions (no scripting needed). */
    fun feeds(args: List<String>) {
        val repo = com.mtwabpin.kolahal.feeds.FeedRepository.get(context)
        val scheduler = com.mtwabpin.kolahal.feeds.FeedScheduler
        val id = args.getOrNull(1)?.toLongOrNull()
        when (args.firstOrNull()) {
            null, "ls", "list" -> {
                val all = repo.all()
                if (all.isEmpty()) return info("No feeds yet — feed add <url> (or 'feeds' for the manager and OPML import)")
                all.forEach { f ->
                    val flags = listOfNotNull(
                        "${f.unread}/${f.total} unread", "every ${CommandParsing.formatInterval(f.intervalMin)}",
                        if (f.notify) "notify" else null, if (!f.enabled) "off" else null, f.lastStatus,
                    ).joinToString(" · ")
                    out(
                        "#${f.id} ${f.title ?: f.url}",
                        LogContext.of(LogContext.COMMAND to "feed read ${f.id}", LogContext.COPY to f.url, LogContext.DESC to flags),
                    )
                }
                info("Tap a feed to read it · feed read = all unread · feed fetch = refresh now")
            }
            "add" -> {
                val url = args.getOrNull(1) ?: return error("Usage: feed add <url>")
                val feed = runCatching { repo.add(url) }.getOrElse { return error(it.message ?: "Invalid URL") }
                scheduler.sync(context)
                scheduler.fetchNow(context, feed.id)
                info("Subscribed #${feed.id} ${feed.url} — fetching…", LogContext.of(LogContext.URL to feed.url))
            }
            "url" -> {
                val url = args.getOrNull(2)
                if (id == null || url == null) return error("Usage: feed url <id> <address>")
                val feed = runCatching { repo.setUrl(id, url) }.getOrElse { return error((it.message ?: "Invalid URL") + if (repo.byId(id) == null) " — 'feeds' lists them" else "") }
                scheduler.fetchNow(context, feed.id)
                info("Feed #${feed.id} is at ${feed.url} — fetching…", LogContext.of(LogContext.URL to feed.url))
            }
            "rm", "remove" -> {
                id ?: return error("Usage: feed rm <id>")
                if (repo.byId(id) == null) return error("No feed #$id — 'feeds' lists them")
                repo.remove(id)
                scheduler.sync(context)
                info("Removed feed #$id")
            }
            "fetch", "refresh" -> {
                scheduler.fetchNow(context, id)
                info("Fetching ${id?.let { "feed #$it" } ?: "all feeds"}…")
            }
            "read", "all" -> {
                val unreadOnly = args[0] == "read"
                val items = repo.items(feedId = id, unreadOnly = unreadOnly, limit = 40)
                if (items.isEmpty()) return info(if (unreadOnly) "Nothing unread" else "No items")
                val names = repo.feeds().associate { it.id to it.shortName }
                items.forEach {
                    logs.add(LogKind.SCRIPT, it.title, names[it.feedId] ?: "feed", LogContext.of(LogContext.URL to it.link, LogContext.COPY to it.title, com.mtwabpin.kolahal.feeds.FeedWorker.CTX_ITEM to it.id.toString()))
                }
                // Only what was shown: older unread items beyond the limit stay unread.
                if (unreadOnly) repo.markRead(items.map { it.id })
                val left = if (unreadOnly) repo.unreadCount(id) else 0
                if (left > 0) info("$left more unread — feed read${id?.let { " $it" }.orEmpty()} again")
            }
            "open" -> {
                // Opens the newest unread items (with links) in the browser; only those are marked read.
                val items = repo.items(feedId = id, unreadOnly = true, limit = 40)
                    .filter { it.link != null }.take(MAX_FEED_OPEN)
                if (items.isEmpty()) return info("Nothing unread")
                val opened = items.filter { item ->
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link))) }.isSuccess
                }
                repo.markRead(opened.map { it.id })
                val left = repo.unreadCount(id)
                val failed = items.size - opened.size
                info(
                    "Opened ${opened.size} item${if (opened.size == 1) "" else "s"}" +
                        (if (failed > 0) " ($failed couldn't be opened)" else "") +
                        (if (left > 0) " · $left still unread" else ""),
                )
            }
            "notify" -> {
                id ?: return error("Usage: feed notify <id> on|off")
                if (repo.byId(id) == null) return error("No feed #$id — 'feeds' lists them")
                repo.setNotify(id, args.getOrNull(2) != "off")
                info("Feed #$id notifications ${args.getOrNull(2) ?: "on"}")
            }
            "every" -> {
                val minutes = CommandParsing.parseInterval(args.getOrNull(2))
                if (id == null || minutes == null || minutes <= 0) return error("Usage: feed every <id> <30m|2h|…>")
                if (repo.byId(id) == null) return error("No feed #$id — 'feeds' lists them")
                repo.setInterval(id, minutes)
                scheduler.sync(context)
                info("Feed #$id every ${CommandParsing.formatInterval(minutes.coerceAtLeast(15))}")
            }
            "on", "off" -> {
                id ?: return error("Usage: feed ${args[0]} <id>")
                if (repo.byId(id) == null) return error("No feed #$id — 'feeds' lists them")
                repo.setEnabled(id, args[0] == "on")
                scheduler.sync(context)
                info("Feed #$id ${args[0]}")
            }
            else -> error("Usage: feed [ls|add <url>|url <id> <url>|rm|fetch|read|all|open|notify|every|on|off] [id]")
        }
    }

    fun notifs(args: List<String>) {
        val actions = com.mtwabpin.kolahal.notifications.NotificationActions
        val store = com.mtwabpin.kolahal.notifications.NotificationStore
        val prefs = com.mtwabpin.kolahal.notifications.NotifPrefs.get(context)
        if (!actions.isEnabled(context) && args.firstOrNull() != "enable") {
            logs.add(LogKind.ERROR, "Notification access is off — tap to allow it", ctx = LogContext.of(LogContext.RUN to "notifs enable"))
            return
        }
        when (args.firstOrNull()) {
            null, "all" -> {
                val list = if (args.firstOrNull() == "all") store.items.value.take(40) else store.active().take(40)
                if (list.isEmpty()) return info("No notifications")
                list.forEach { n ->
                    logs.add(
                        LogKind.SCRIPT, n.line, n.app,
                        LogContext.of(
                            LogContext.PKG to n.pkg, LogContext.USER to n.userSerial.toString(), LogContext.COPY to n.line,
                            com.mtwabpin.kolahal.notifications.KolahalNotificationListener.CTX_KEY to n.key,
                            LogContext.ICON to n.icon,
                        ),
                    )
                }
            }
            // Turning it on, Kolahal first says what it reads and asks (Play's prominent disclosure);
            // already on, Android's screen opens straight away, to turn it off.
            "enable" -> if (actions.isEnabled(context)) startActivity(actions.settingsIntent(context)) else ui.askNotificationAccess()
            "unmute" -> args.drop(1).joinToString(" ").takeIf { it in prefs.muted.value }?.let { pkg ->
                prefs.unmute(pkg)
                info("${apps.apps.value.firstOrNull { it.pkg == pkg }?.label ?: pkg}: notifications will appear in the log")
            } ?: appCommands.withApp(args.drop(1)) { app -> prefs.unmute(app.pkg); info("${app.label}: notifications will appear in the log") }
            "mute" -> appCommands.withApp(args.drop(1)) { app ->
                if (args[0] == "mute") prefs.mute(app.pkg) else prefs.unmute(app.pkg)
                info("${app.label}: notifications ${if (args[0] == "mute") "won't" else "will"} appear in the log")
            }
            "muted" -> {
                val muted = prefs.muted.value
                if (muted.isEmpty()) info("No muted apps")
                muted.forEach { pkg -> out(apps.apps.value.firstOrNull { it.pkg == pkg }?.label ?: pkg, LogContext.of(LogContext.COMMAND to "notifs unmute $pkg")) }
            }
            "log" -> {
                prefs.logToTerminal = args.getOrNull(1) != "off"
                info("Notifications ${if (prefs.logToTerminal) "appear" else "no longer appear"} in the log")
            }
            "clear" -> {
                actions.dismissAll()
                store.clear()
                // Also drop the snapshot scripts read, so cleared notifications don't linger on disk.
                com.mtwabpin.kolahal.notifications.KolahalNotificationListener.clearMirror(context)
                info("Notifications cleared")
            }
            else -> error("Usage: notifs [all|enable|mute <app>|unmute <app>|muted|log on|off|clear]")
        }
    }

    companion object {
        /** Most browser tabs one `feed open` launches; the rest stay unread. */
        const val MAX_FEED_OPEN = 5
    }
}
