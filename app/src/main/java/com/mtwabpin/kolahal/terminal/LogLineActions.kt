/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppPrefs
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.apps.ProfileWords
import com.mtwabpin.kolahal.commands.LogFilter
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.data.ScriptRepository
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.data.shownText
import com.mtwabpin.kolahal.notifications.NotifPrefs
import com.mtwabpin.kolahal.notifications.NotificationActions
import com.mtwabpin.kolahal.script.Json
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.script.ScriptScheduler
import com.mtwabpin.kolahal.terminal.commands.ActivityCommands
import com.mtwabpin.kolahal.terminal.commands.CommandProcessor
import com.mtwabpin.kolahal.terminal.commands.ProductivityCommands

/**
 * Decides what a tap or a long-press on a log line does, based on the line's saved [LogContext].
 * The view model owns the prompt and the command queue, and gives access to them through [Host].
 */
class LogLineActions(
    private val context: Context,
    private val processor: CommandProcessor,
    private val host: Host
) {
    /** What the actions need from the view model. */
    interface Host {
        /** The loaded log, oldest first. A command's block is the lines after it. */
        val entries: List<LogEntry>
        val scripts: List<Script>

        /** Runs [block] on the serial command queue. */
        fun io(block: suspend () -> Unit)
        fun setText(text: String)
        fun submit()
        fun emit(event: UiEvent)
        fun runsOnTap(line: String): Boolean
        fun runCommand(cmd: String)
        fun openEditorAt(script: Script, line: Int?)
        fun startActivity(intent: Intent)

        /** Runs `log only …` from a line's long-press menu. */
        fun filterLog(filter: LogFilter?)
    }

    private val logs = LogRepository.get(context)
    private val appsRepo = AppRepository.get(context)
    private val appPrefs = AppPrefs.get(context)
    private val scripts = ScriptRepository.get(context)

    /** Single tap: runs the most likely action for this line. */
    fun onTap(entry: LogEntry) {
        val ctx = entry.ctx
        val errorLine = ctx.values[LogContext.LINE]?.toIntOrNull()
        when {
            errorLine != null && ctx.script != null -> {
                val name = ctx.script!!
                host.io { scripts.byName(name)?.let { host.openEditorAt(it, errorLine) } }
            }

            ctx.values[LogContext.NOTE] != null -> copy(ctx.copy ?: entry.text)
            ctx.values[LogContext.EVENT] != null -> ProductivityCommands.eventIntent(ctx)
                ?.let(host::startActivity)
            // A tap only fills the prompt. To cancel, the user presses Enter (or uses the long-press "Cancel reminder").
            ctx.values[LogContext.REMINDER] != null -> {
                host.setText("remind rm #${ctx.values.getValue(LogContext.REMINDER)}")
                host.emit(UiEvent.FocusPrompt)
            }

            ctx.values[LogContext.RUN] != null -> {
                val cmd = ctx.values.getValue(LogContext.RUN)
                host.emit(UiEvent.ScrollToBottom)
                // Only Kolahal writes these (scripts can't, restores drop them), so a command turned off runs too.
                host.io { processor.run(cmd, expand = false, fromApp = true) }
            }

            entry.kind == LogKind.COMMAND || ctx.command != null -> {
                val line = ctx.command ?: entry.text
                if (host.runsOnTap(line)) {
                    host.emit(UiEvent.ScrollToBottom)
                    host.io { processor.run(line) }
                } else {
                    host.setText(line)
                    host.emit(UiEvent.FocusPrompt)
                }
            }

            ctx.values[NOTIF] != null -> {
                // Use the notification's own tap action if it is still active. Otherwise, just open the app.
                if (!NotificationActions.open(
                        context,
                        ctx.values.getValue(NOTIF)
                    )
                ) appFor(ctx)?.let { app -> host.io { processor.launch(app) } }
            }

            ctx.values[LogContext.SHORTCUT] != null && appFor(ctx) != null -> {
                val app = appFor(ctx)!!
                val id = ctx.values.getValue(LogContext.SHORTCUT)
                host.io {
                    runCatching { appsRepo.startShortcut(app, id) }.onFailure {
                        logs.add(
                            LogKind.ERROR,
                            "Shortcut unavailable: ${it.message}"
                        )
                    }
                }
            }

            ctx.values[LogContext.ACTIVITY] != null && ctx.pkg != null -> {
                val cls = ctx.values.getValue(LogContext.ACTIVITY)
                host.io { ActivityCommands.openFromLine(context, ctx.pkg!!, cls) }
            }

            ctx.values[LogContext.PHONE] != null -> host.startActivity(
                Intent(
                    Intent.ACTION_DIAL,
                    Uri.fromParts("tel", ctx.values.getValue(LogContext.PHONE), null)
                )
            )

            ctx.pkg != null -> appFor(ctx)?.let { app -> host.io { processor.launch(app) } }
            ctx.url != null -> openLink(ctx)
        }
    }

    /**
     * Long-press: returns every action that makes sense for the saved context. Destructive ones come
     * last. Each has its place on the GUI sheet ([ActionPlace]): what every line has goes in the row
     * of icons, and each thing the line names (a script, an app, a link…) has a group of its own.
     */
    fun actionsFor(entry: LogEntry): List<LogAction> {
        val ctx = entry.ctx
        val actions = ArrayList<LogAction>()
        val danger = ArrayList<LogAction>()
        // An activity line (`activity`): open it, or copy the command that opens it (for an alias).
        ctx.values[LogContext.ACTIVITY]?.let { cls ->
            val pkg = ctx.pkg ?: return@let
            val activity = ActionPlace.Group("activity", "Activity")
            actions += LogAction("Open activity", short = "Open", icon = icon(R.drawable.ic_open_in_new), place = activity) {
                host.io {
                    ActivityCommands.openFromLine(
                        context,
                        pkg,
                        cls
                    )
                }
            }
            appFor(ctx)?.let { app ->
                actions += LogAction("Copy command", icon = icon(R.drawable.ic_content_copy), place = activity) {
                    copy(
                        ActivityCommands.commandFor(
                            context,
                            app,
                            cls
                        )
                    )
                }
            }
        }
        val command = ctx.command ?: entry.text.takeIf { entry.kind == LogKind.COMMAND }
        val commandGroup = ActionPlace.Group(COMMAND_GROUP, "Command")
        if (command != null) {
            actions += LogAction("Run again", icon = icon(R.drawable.ic_restart), place = commandGroup) { host.setText(command); host.submit() }
            actions += LogAction("Edit in prompt", icon = icon(R.drawable.ic_edit), place = commandGroup) { host.setText(command); host.emit(UiEvent.FocusPrompt) }
        } else if (entry.kind != LogKind.SCRIPT || LogContext.FROM in ctx.values) {
            // For output (also a script's output, when a command started the run), offer the command that printed it.
            producingCommand(entry)?.let { cmd ->
                val printed = ActionPlace.Group(COMMAND_GROUP, "Command · ${clip(cmd.text)}", own = true)
                actions += LogAction("Run '${cmd.text}' again", short = "Run again", icon = icon(R.drawable.ic_restart), place = printed) { host.setText(cmd.text); host.submit() }
                actions += LogAction("Edit '${cmd.text}' in prompt", short = "Edit in prompt", icon = icon(R.drawable.ic_edit), place = printed) {
                    host.setText(cmd.text); host.emit(
                    UiEvent.FocusPrompt
                )
                }
            }
        }
        appFor(ctx)?.let { app ->
            // A line made up for an app (an apps line's chip) has nothing of a line: its menu is the app's alone.
            val group = if (entry.id < 0) null else ActionPlace.Group(
                "app",
                if (app.profile.kind == ProfileKind.PERSONAL) app.label else "${app.label} · ${ProfileWords.title(app.profile.kind)}",
                own = true,
            )
            val (safe, destructive) = appActions(app, group).partition { !it.destructive }
            actions += safe
            danger += destructive
        }
        ctx.values[NOTIF]?.let { key ->
            val notification = ActionPlace.Group("notification", "Notification")
            actions += LogAction("Open notification", short = "Open", icon = icon(R.drawable.ic_notifications), place = notification) {
                if (!NotificationActions.open(
                        context,
                        key
                    )
                ) appFor(ctx)?.let { app -> host.io { processor.launch(app) } }
            }
            ctx.pkg?.let { pkg ->
                actions += LogAction("Mute this app's notifications", short = "Mute app", icon = icon(R.drawable.ic_notifications_off), place = notification) {
                    host.io {
                        NotifPrefs.get(
                            context
                        ).mute(pkg); logs.add(
                        LogKind.INFO,
                        "Muted notifications from ${appFor(ctx)?.label ?: pkg}"
                    )
                    }
                }
            }
            danger += LogAction(
                "Dismiss notification",
                destructive = true,
                short = "Dismiss",
                icon = icon(R.drawable.ic_close),
                place = notification,
            ) { NotificationActions.dismiss(key) }
        }
        ctx.values[LogContext.PHONE]?.let { number ->
            // The person's name when the line has it, else the number.
            val person = ActionPlace.Group("phone", ctx.values[LogContext.PERSON] ?: number, own = true)
            actions += LogAction("Call $number", short = "Call", icon = icon(R.drawable.ic_call), place = person) {
                host.startActivity(
                    Intent(
                        Intent.ACTION_DIAL,
                        Uri.fromParts("tel", number, null)
                    )
                )
            }
            actions += LogAction("Send a message", short = "Message", icon = icon(R.drawable.ic_forum), place = person) {
                host.startActivity(
                    Intent(
                        Intent.ACTION_SENDTO,
                        Uri.fromParts("smsto", number, null)
                    )
                )
            }
            ctx.values[LogContext.CONTACT]?.let { uri ->
                actions += LogAction("Open contact", short = "Contact", icon = icon(R.drawable.ic_person), place = person) {
                    host.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            uri.toUri()
                        )
                    )
                }
            }
            actions += LogAction("Copy number", short = "Copy", icon = icon(R.drawable.ic_content_copy), place = person) { copy(number) }
        }
        ctx.url?.let { url ->
            val link = ActionPlace.Group("link", "Link · ${MenuLayout.shortAddress(url)}", own = true)
            actions += LogAction("Open link", short = "Open", icon = icon(R.drawable.ic_public), place = link) { openLink(ctx) }
            actions += LogAction("Share link", short = "Share", icon = icon(R.drawable.ic_share), place = link) { share(StyledText.plain(entry.text), url) }
            actions += LogAction("Copy link", short = "Copy", icon = icon(R.drawable.ic_content_copy), place = link) { copy(url) }
        }
        ctx.image?.takeIf { it != ctx.url }
            ?.let { image ->
                actions += LogAction("Open image", short = "Open", icon = icon(R.drawable.ic_image), place = ActionPlace.Group("image", "Image")) { openUrl(image) }
            }
        // Links in the text (fmt.link), by what each one shows. A script checked them when it logged the line.
        if (entry.kind == LogKind.SCRIPT) {
            val links = StyledText.linkTexts(listOfNotNull(entry.text, ctx.body).joinToString("\n"))
                .filter { it.second != ctx.url }
            links.take(MAX_LINK_ACTIONS).forEachIndexed { i, (text, url) ->
                val name =
                    text.ifEmpty { url }.let { if (it.length > 40) it.take(39) + "…" else it }
                val link = ActionPlace.Group("link $i", "Link · ${MenuLayout.shortAddress(url)}", own = true)
                actions += LogAction("Open “$name”", short = "Open", icon = icon(R.drawable.ic_public), place = link) {
                    com.mtwabpin.kolahal.script.Net.webUrl(url)?.let(::openUrl)
                }
            }
        }
        ctx.script?.let { name ->
            host.scripts.firstOrNull { it.name == name }?.let { s ->
                val script = ActionPlace.Group("script", name, own = true)
                actions += LogAction("Run $name now", short = "Run now", icon = icon(R.drawable.ic_play), place = script) {
                    host.io {
                        ScriptScheduler.runNow(
                            context,
                            s
                        )
                    }
                }
                // For a script with a status line: pin it to the top of home, or unpin it.
                if (ScriptMeta.setsStatus(s.code)) {
                    val pinned = s.grants.allows(ScriptAccess.HEADER)
                    actions += LogAction(
                        if (pinned) "Unpin $name" else "Pin $name to the top",
                        short = if (pinned) "Unpin" else "Pin to top",
                        icon = icon(R.drawable.ic_vertical_align_top),
                        place = script,
                    ) {
                        host.io {
                            scripts.setAccess(s.id, ScriptAccess.HEADER, !pinned)
                            logs.onStatusChanged()
                            logs.add(
                                LogKind.INFO,
                                "$name: ${if (pinned) "unpinned" else "pinned to the top"}",
                                ctx = LogContext.of(LogContext.SCRIPT to name)
                            )
                        }
                    }
                }
                ctx.values[LogContext.LINE]?.toIntOrNull()?.let { line ->
                    actions += LogAction("Edit $name at line $line", short = "Edit at line $line", icon = icon(R.drawable.ic_bug), place = script) {
                        host.io {
                            scripts.byId(s.id)?.let { host.openEditorAt(it, line) }
                        }
                    }
                }
                actions += LogAction("Edit $name.${s.lang.ext}", short = "Edit", icon = icon(R.drawable.ic_code), place = script) {
                    host.io {
                        scripts.byId(s.id)?.let { host.openEditorAt(it, null) }
                    }
                }
                actions += LogAction(
                    if (s.enabled) "Turn off $name" else "Turn on $name",
                    short = if (s.enabled) "Turn off" else "Turn on",
                    icon = icon(R.drawable.ic_power),
                    place = script,
                ) {
                    host.io {
                        scripts.setEnabled(s.id, !s.enabled)
                        scripts.byId(s.id)?.let {
                            ScriptScheduler.sync(context, it)
                            if (it.enabled) ScriptAccess.askForNew(context, it)
                        }
                        logs.add(
                            LogKind.INFO,
                            "$name: ${if (s.enabled) "turned off" else "turned on"}",
                            ctx = LogContext.of(LogContext.SCRIPT to name)
                        )
                    }
                }
                actions += LogAction(
                    if (s.notify) "Mute notifications" else "Notify on new items",
                    short = if (s.notify) "Mute" else "Notify",
                    icon = icon(if (s.notify) R.drawable.ic_notifications_off else R.drawable.ic_notifications),
                    place = script,
                ) {
                    host.io {
                        scripts.setNotify(s.id, !s.notify)
                        logs.add(
                            LogKind.INFO,
                            "$name: notifications ${if (s.notify) "off" else "on"}",
                            ctx = LogContext.of(LogContext.SCRIPT to name)
                        )
                    }
                    if (!s.notify) host.emit(UiEvent.RequestNotificationPermission)
                }
            }
        }
        productivityActions(ctx, actions, danger)
        if (entry.id < 0) return actions + danger // synthetic entry (e.g. an app chip): nothing to copy or delete
        val block = if (entry.kind == LogKind.COMMAND) blockOf(entry) else emptyList()
        if (block.size > 1) {
            actions += LogAction("Copy output", icon = icon(R.drawable.ic_clipboard), place = commandGroup) {
                copy(
                    block.drop(1).joinToString("\n") { it.shownText })
            }
            danger += LogAction(
                "Delete command and output",
                destructive = true,
                short = "Delete with output",
                icon = icon(R.drawable.ic_delete),
                place = commandGroup,
            ) {
                val ids = block.map { it.id }
                host.io { ids.forEach(logs::delete) }
            }
        }
        // Shows only this line's script, feed or app (or its kind), until the user taps ✕ on the filter bar.
        LogFilter.forLine(entry)
            ?.let { f ->
                actions += LogAction("Show only ${f.label}", short = "Only this", icon = icon(R.drawable.ic_filter_list), place = ActionPlace.ONLY) { host.filterLog(f) }
            }
        // A starred line is kept forever, beyond the log's limits and through a clear. `/@starred` finds it.
        actions += LogAction(
            if (entry.starred) "Unstar line" else "Star line",
            short = if (entry.starred) "Unstar" else "Star",
            icon = icon(if (entry.starred) R.drawable.ic_star else R.drawable.ic_star_border),
            place = ActionPlace.STAR,
        ) {
            host.io {
                logs.setStarred(
                    entry.id,
                    !entry.starred
                )
            }
        }
        actions += LogAction("Copy text", short = "Copy", icon = icon(R.drawable.ic_content_copy), place = ActionPlace.COPY) {
            copy(
                ctx.copy?.let(StyledText::plain) ?: entry.shownText, sensitive(entry)
            )
        }
        // Fresh from here: the lines above this one are hidden, as `fresh` hides the screen. Not on the first line shown.
        if (logs.shownBefore(entry.id)) {
            actions += LogAction("Fresh from here") { host.io { logs.hideAbove(entry.id) } }
        }
        // No Undo follows, so a notification's line dismisses its notification at once ([DismissOnDelete]).
        danger += LogAction("Delete line", destructive = true, short = "Delete", icon = icon(R.drawable.ic_delete), place = ActionPlace.DELETE) {
            host.io {
                logs.delete(entry.id)
                DismissOnDelete.keyOf(ctx, Config.get(context)[Keys.NOTIFS_DISMISS_ON_DELETE]) {
                    NotificationActions.isEnabled(context)
                }?.let(NotificationActions::dismissIfClearable)
            }
        }
        return (actions + danger).map(MenuLayout::placeKnown)
    }

    /**
     * The actions for one app, for log lines and grid chips.
     * - Held in a list ([group] null), the five every app has go in the sheet's row (Open, App
     *   info, Favorite, Hide, Uninstall), the shortcuts under "Shortcuts", the rest under "More".
     * - Named by a line, all of them go in the line's [group] for the app.
     */
    fun appActions(app: AppEntry, group: ActionPlace.Group? = null): List<LogAction> = buildList {
        fun at(row: ActionPlace.Row) = group ?: row
        val more = group ?: ActionPlace.Group("more", "More")
        add(LogAction("Launch ${app.label} [${app.profile.kind.tag}]", short = "Open", icon = icon(R.drawable.ic_open_in_new), place = at(ActionPlace.OPEN)) {
            host.io {
                processor.launch(
                    app
                )
            }
        })
        // The app's own shortcuts (e.g. "New message"). They are available when Kolahal is the default launcher.
        appsRepo.shortcuts(app).take(5).forEach { s ->
            add(MenuLayout.shortcut(app.label, app.pkg, app.profile.serial, s.id, "${s.shortLabel}", group) {
                host.io {
                    runCatching {
                        appsRepo.startShortcut(
                            app,
                            s.id
                        )
                    }
                }
            })
        }
        appsRepo.apps.value.firstOrNull { it.pkg == app.pkg && it.profile.serial != app.profile.serial }
            ?.let { twin ->
                add(LogAction(
                    "Launch in ${ProfileWords.name(twin.profile.kind)}",
                    short = ProfileWords.title(twin.profile.kind),
                    icon = ActionIcon.Drawable(
                        when (twin.profile.kind) {
                            ProfileKind.WORK -> R.drawable.ic_work
                            ProfileKind.PRIVATE -> R.drawable.ic_lock
                            ProfileKind.PERSONAL -> R.drawable.ic_person
                            else -> R.drawable.ic_apps
                        },
                        profile = twin.profile.kind,
                    ),
                    place = more,
                ) {
                    host.io {
                        processor.launch(
                            twin
                        )
                    }
                })
            }
        add(LogAction("App info", icon = icon(R.drawable.ic_info), place = at(ActionPlace.APP_INFO)) { host.io { appsRepo.openAppInfo(app) } })
        add(LogAction("Copy package name", short = "Copy package", icon = icon(R.drawable.ic_content_copy), place = more) { copy(app.pkg) })
        val pinned = appPrefs.isPinned(app.key)
        add(LogAction(
            if (pinned) "Unpin from favorites" else "Pin to favorites",
            short = if (pinned) "Unfavorite" else "Favorite",
            icon = icon(if (pinned) R.drawable.ic_favorite else R.drawable.ic_favorite_border),
            place = at(ActionPlace.FAVORITE),
        ) {
            appPrefs.setPinned(
                app.key,
                !pinned
            )
        })
        add(LogAction(
            if (app.hidden) "Unhide" else "Hide from lists",
            short = if (app.hidden) "Unhide" else "Hide",
            icon = icon(if (app.hidden) R.drawable.ic_visibility else R.drawable.ic_visibility_off),
            place = at(ActionPlace.HIDE),
        ) {
            host.io {
                appPrefs.setHidden(app.key, !app.hidden)
                appsRepo.refresh()
            }
        })
        add(LogAction("Uninstall", destructive = true, icon = icon(R.drawable.ic_delete), place = at(ActionPlace.UNINSTALL)) { host.io { appsRepo.uninstall(app) } })
    }

    /**
     * Returns the command line that printed [entry]:
     * - the command that the line is marked with; or
     * - for an older line without a mark, the command at the start of its group. A pause longer
     *   than [GROUP_GAP_MS] ends the group, as in the log display.
     *
     * A line that came on its own (a tap launch, a call) has no command.
     */
    private fun producingCommand(entry: LogEntry): LogEntry? {
        val all = host.entries
        entry.ctx.values[LogContext.FROM]?.toLongOrNull()
            ?.let { from -> return all.firstOrNull { it.id == from } }
        val index = all.indexOfFirst { it.id == entry.id }
        if (index < 0) return null
        var next = entry
        for (i in index - 1 downTo 0) {
            val e = all[i]
            if (next.ts - e.ts > GROUP_GAP_MS) return null
            if (e.kind == LogKind.COMMAND) return e
            // Timer script lines can appear inside a block, but they are not part of it.
            if (e.kind != LogKind.SCRIPT) next = e
        }
        return null
    }

    /**
     * Returns a command line plus its output: the lines marked as its output ([LogContext.FROM]),
     * wherever they are in the log. (A script's lines arrive when its run is done, after other lines.)
     */
    private fun blockOf(command: LogEntry): List<LogEntry> {
        val all = host.entries
        val start = all.indexOfFirst { it.id == command.id }
        if (start < 0) return listOf(command)
        val after = all.subList(start + 1, all.size)
        val id = command.id.toString()
        val marked = after.filter { it.ctx.values[LogContext.FROM] == id }
        if (marked.isNotEmpty()) return listOf(command) + marked
        // Lines from before 0.4.2 have no mark. Take the lines that followed the command, up to the next
        // command or a pause longer than [GROUP_GAP_MS], as the log groups them. Leave out script lines.
        val block = arrayListOf(command)
        var previous = command
        for (e in after) {
            if (e.kind == LogKind.COMMAND || e.ts - previous.ts > GROUP_GAP_MS) break
            if (e.kind != LogKind.SCRIPT) block += e
            previous = e
        }
        return block
    }

    /** Returns the apps in a grid line. `ctx.apps` is a JSON list of {pkg, cls, user}. */
    fun appsOf(entry: LogEntry): List<AppEntry> {
        val raw = entry.ctx.values[LogContext.APPS] ?: return emptyList()
        val list = runCatching { Json.parse(raw) as? List<*> }.getOrNull().orEmpty()
        return list.filterIsInstance<Map<*, *>>().mapNotNull { m ->
            val pkg = m["pkg"]?.toString() ?: return@mapNotNull null
            appsRepo.find(pkg, m["cls"]?.toString(), m["user"]?.toString()?.toLongOrNull())
                // A locked private space shows none of its apps, in a grid of several profiles (`pins`) too.
                ?.takeUnless { com.mtwabpin.kolahal.apps.PrivateSpace.locked(it.profile) }
        }
    }

    private fun appFor(ctx: LogContext): AppEntry? =
        ctx.pkg?.let {
            appsRepo.find(it, ctx.cls, ctx.userSerial) ?: appsRepo.find(
                it,
                null,
                ctx.userSerial
            )
        }

    /** An action's icon, in the header's style. */
    private fun icon(id: Int) = ActionIcon.Drawable(id)

    /** A command, short enough for a heading. */
    private fun clip(text: String) = if (text.length > 32) text.take(31) + "…" else text

    private fun openUrl(url: String) = host.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))

    /** Opens a line's link. A feed item is marked as read once you open it. */
    private fun openLink(ctx: LogContext) {
        openUrl(ctx.url ?: return)
        ctx.values[com.mtwabpin.kolahal.feeds.FeedWorker.CTX_ITEM]?.toLongOrNull()?.let { id ->
            host.io { com.mtwabpin.kolahal.feeds.FeedRepository.get(context).markRead(id) }
        }
    }

    private fun share(title: String, url: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, title)
            .putExtra(Intent.EXTRA_TEXT, url)
        host.startActivity(Intent.createChooser(send, null))
    }

    /** Swipe right on a log line. */
    fun swipeCopy(entry: LogEntry) = copy(entry.shownText, sensitive(entry))

    /** True for notification and call lines. They contain other people's messages, codes and numbers. */
    private fun sensitive(entry: LogEntry) =
        entry.ctx.values.containsKey(NOTIF) || entry.source == com.mtwabpin.kolahal.calls.CallEvents.SOURCE

    /**
     * Copies [text]. [sensitive] text is marked, so the keyboard's clipboard preview and history hide it.
     * While home is scrambled (`scramble`) every copy is, so Android's preview of it shows nothing real.
     */
    private fun copy(text: String, sensitive: Boolean = false) {
        val clip = ClipData.newPlainText("kolahal", text)
        if (sensitive || com.mtwabpin.kolahal.data.HomeScramble.get(context).on) clip.description.extras = android.os.PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }

    /** Adds the long-press entries for todo, note, reminder and event lines. Destructive ones go to [danger]. */
    private fun productivityActions(
        ctx: LogContext,
        actions: MutableList<LogAction>,
        danger: MutableList<LogAction>
    ) {
        ctx.values[LogContext.TODO]?.let { id ->
            val todo = ActionPlace.Group("todo", "Todo")
            val done = ctx.values[LogContext.TODO_DONE] == "1"
            actions += LogAction(
                if (done) "Mark not done" else "Mark done",
                short = if (done) "Not done" else "Done",
                icon = icon(if (done) R.drawable.ic_undo else R.drawable.ic_check),
                place = todo,
            ) { host.runCommand("todo ${if (done) "undo" else "done"} #$id") }
            actions += LogAction("Edit in prompt", short = "Edit", icon = icon(R.drawable.ic_edit), place = todo) {
                host.setText("todo edit #$id ${ctx.copy.orEmpty()}")
                host.emit(UiEvent.FocusPrompt)
            }
            danger += LogAction(
                "Delete todo",
                destructive = true,
                short = "Delete",
                icon = icon(R.drawable.ic_delete),
                place = todo,
            ) { host.runCommand("todo rm #$id") }
        }
        ctx.values[LogContext.NOTE]?.let { id ->
            danger += LogAction(
                "Delete note",
                destructive = true,
                short = "Delete",
                icon = icon(R.drawable.ic_delete),
                place = ActionPlace.Group("note", "Note"),
            ) { host.runCommand("notes rm #$id") }
        }
        ctx.values[LogContext.REMINDER]?.let { id ->
            danger += LogAction(
                "Cancel reminder",
                destructive = true,
                short = "Cancel",
                icon = icon(R.drawable.ic_close),
                place = ActionPlace.Group("reminder", "Reminder"),
            ) { host.runCommand("remind rm #$id") }
        }
        if (ctx.values[LogContext.EVENT] != null) {
            actions += LogAction("Open event", short = "Open", icon = icon(R.drawable.ic_calendar), place = ActionPlace.Group("event", "Event")) {
                ProductivityCommands.eventIntent(ctx)?.let(host::startActivity)
            }
        }
    }

    companion object {
        /** The key of a command's group: its Run again and its Delete with output go together. */
        private const val COMMAND_GROUP = "command"

        private const val NOTIF =
            com.mtwabpin.kolahal.notifications.KolahalNotificationListener.CTX_KEY

        /** The most links in a line's text that its long-press menu offers to open. */
        private const val MAX_LINK_ACTIONS = 4
    }
}
