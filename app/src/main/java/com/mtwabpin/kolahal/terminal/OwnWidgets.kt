/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import android.content.Context
import com.mtwabpin.kolahal.control.LastPlayer
import com.mtwabpin.kolahal.control.MediaRepository
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.productivity.Agenda
import com.mtwabpin.kolahal.productivity.AgendaEvent
import com.mtwabpin.kolahal.productivity.Note
import com.mtwabpin.kolahal.productivity.NoteStore
import com.mtwabpin.kolahal.productivity.Reminder
import com.mtwabpin.kolahal.productivity.ReminderStore
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.productivity.TodoStore
import com.mtwabpin.kolahal.widgets.Shortcut
import com.mtwabpin.kolahal.widgets.Shortcuts
import com.mtwabpin.kolahal.widgets.WidgetSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * What the calendar gave the Agenda widget.
 * - [allowed]: Kolahal has the calendar permission.
 * - [events]: the instances of the next days, as [Agenda.query] gives them. Empty without the permission.
 * - [at]: when this was read, in milliseconds on the wall clock. 0 means that nothing is read yet.
 */
data class AgendaRead(val allowed: Boolean, val events: List<AgendaEvent>, val at: Long) {
    companion object {
        /** Before the first read. The card stays empty: it does not say "allow" yet. */
        val NOT_READ = AgendaRead(allowed = false, events = emptyList(), at = 0L)
    }
}

/**
 * What the cards of the built-in widgets read from the view model. Kolahal fills these widgets from
 * its own data, in the main process: no script and no run.
 * - [todos], [notes], [reminders], [feeds]: the lists as the stores give them. The suggestions and
 *   the header's todo count read the same lists, so all of them agree. [shortcuts]: the Shortcuts
 *   widget's list. Each is null until it is read for the first time, so a card never says "none"
 *   before it knows.
 * - [playing]: the media session the header's line shows. Null with no session, and without
 *   notification access.
 * - [agenda]: the calendar's events, as last read.
 * - [mediaAllowed]: Kolahal has notification access, as last checked. Without it nothing can be in [playing].
 * - [lastPlayer]: the player that [playing] showed last ([MediaRepository.lastPlayer]), for the
 *   card to offer when nothing plays.
 */
class OwnFeed(
    val todos: StateFlow<List<Todo>?>,
    val notes: StateFlow<List<Note>?>,
    val reminders: StateFlow<List<Reminder>?>,
    val shortcuts: StateFlow<List<Shortcut>?>,
    val playing: StateFlow<NowPlaying?>,
    val agenda: StateFlow<AgendaRead>,
    val mediaAllowed: StateFlow<Boolean> = MutableStateFlow(true),
    val feeds: StateFlow<List<com.mtwabpin.kolahal.feeds.FeedInfo>?> = MutableStateFlow(null),
    val lastPlayer: StateFlow<LastPlayer?> = MutableStateFlow(null),
)

/** What a tap inside a built-in card asks for. [OwnTaps.parse] makes one from the card's words. */
internal sealed interface OwnTap {
    /** Runs the command of the Shortcuts button at [index], counted from 0. */
    data class RunShortcut(val index: Int) : OwnTap

    /** Opens the Widgets screen, where the shortcuts are changed. */
    data object EditShortcuts : OwnTap

    /** Marks the todo with this [id] done, or open again. */
    data class SetTodo(val id: Long, val done: Boolean) : OwnTap

    /** Runs [command] as if it was typed, and turns home to the log: `todo`, `notes`. */
    data class Typed(val command: String) : OwnTap

    /** One of the Now playing card's three buttons. */
    data class Media(val action: MediaRepository.Action) : OwnTap

    /** Moves the track to [ms], in milliseconds: the Now playing card's bar. */
    data class Seek(val ms: Long) : OwnTap

    /** Plays the last player's track again, with nothing playing: the Now playing card's Play. */
    data object Resume : OwnTap

    /** Opens the app that plays. */
    data object OpenPlayer : OwnTap

    /** Asks for notification access, which Now playing needs. */
    data object AllowNotifications : OwnTap

    /** Asks for the calendar permission, which the Agenda needs. */
    data object AllowCalendar : OwnTap

    /** Opens the calendar event [id]. [begin] says which time of a repeating event, in milliseconds, or is null. */
    data class OpenEvent(val id: Long, val begin: Long? = null) : OwnTap
}

/**
 * The words of a tap inside a built-in card, and what they mean. Pure.
 *
 * A built-in card's description holds a tap as `WidgetTap(args = <words>)`, and the card hands the
 * words to [WidgetCardActions.own] with its widget's id. The words are a fixed, small set. A command
 * is never among them: the Shortcuts card sends the button's place in the list, and the view model
 * looks the command up.
 *
 * | Widget           | Words                                                               |
 * |------------------|---------------------------------------------------------------------|
 * | shortcuts        | `run <index>`, `edit`                                               |
 * | todos            | `done #<id>`, `undo #<id>`, `open`                                  |
 * | reminders, notes | `open`                                                              |
 * | media            | `prev`, `toggle`, `next`, `seek <ms>`, `resume`, `open`, `allow`    |
 * | agenda           | `event <id> [<begin>]`, `open`, `allow`                             |
 */
internal object OwnTaps {
    const val OPEN = "open"
    const val EDIT = "edit"
    const val ALLOW = "allow"
    const val PREV = "prev"
    const val TOGGLE = "toggle"
    const val NEXT = "next"
    const val RESUME = "resume"

    /** The words of the Shortcuts button at [index], counted from 0. */
    fun run(index: Int): String = "run $index"

    /** The words that move the playing track to [ms], in milliseconds. */
    fun seek(ms: Long): String = "seek $ms"

    /** The words that mark the todo [id] done. */
    fun done(id: Long): String = "done #$id"

    /** The words that open the todo [id] again. */
    fun undo(id: Long): String = "undo #$id"

    /** The words that open the calendar event [id] at the time [begin], in milliseconds. */
    fun event(id: Long, begin: Long): String = "event $id $begin"

    private val SPACES = Regex("\\s+")

    /** `#12` as 12. Null for anything else. */
    private fun ref(word: String?): Long? =
        word?.takeIf { it.startsWith("#") }?.drop(1)?.toLongOrNull()?.takeIf { it >= 0 }

    /**
     * What the words [what] mean for the built-in widget [id]. Null for a widget or words that are
     * not in the table, and for words with anything extra: nothing happens then.
     */
    fun parse(id: String, what: String): OwnTap? {
        val words = what.trim().split(SPACES)
        val verb = words[0]
        val arg = words.getOrNull(1)
        // Only an event has a second argument.
        if (words.size > 3 || (words.size == 3 && !(id == WidgetSource.AGENDA && verb == "event"))) return null
        fun alone(tap: OwnTap): OwnTap? = tap.takeIf { arg == null }
        return when (id) {
            WidgetSource.SHORTCUTS -> when (verb) {
                "run" -> arg?.toIntOrNull()?.takeIf { it in 0 until Shortcuts.MAX }
                    ?.let(OwnTap::RunShortcut)

                EDIT -> alone(OwnTap.EditShortcuts)
                else -> null
            }

            WidgetSource.TODOS -> when (verb) {
                "done" -> ref(arg)?.let { OwnTap.SetTodo(it, done = true) }
                "undo" -> ref(arg)?.let { OwnTap.SetTodo(it, done = false) }
                OPEN -> alone(OwnTap.Typed("todo"))
                else -> null
            }

            WidgetSource.REMINDERS -> if (verb == OPEN) alone(OwnTap.Typed("reminders")) else null
            WidgetSource.NOTES -> if (verb == OPEN) alone(OwnTap.Typed("notes")) else null
            WidgetSource.MEDIA -> when (verb) {
                PREV -> alone(OwnTap.Media(MediaRepository.Action.PREV))
                TOGGLE -> alone(OwnTap.Media(MediaRepository.Action.TOGGLE))
                NEXT -> alone(OwnTap.Media(MediaRepository.Action.NEXT))
                "seek" -> arg?.toLongOrNull()?.takeIf { it >= 0 }?.let(OwnTap::Seek)
                RESUME -> alone(OwnTap.Resume)
                OPEN -> alone(OwnTap.OpenPlayer)
                ALLOW -> alone(OwnTap.AllowNotifications)
                else -> null
            }

            WidgetSource.AGENDA -> when (verb) {
                "event" -> {
                    val event = arg?.toLongOrNull()?.takeIf { it >= 0 } ?: return null
                    val begin = words.getOrNull(2)
                    if (begin == null) OwnTap.OpenEvent(event) else begin.toLongOrNull()
                        ?.let { OwnTap.OpenEvent(event, it) }
                }

                OPEN -> alone(OwnTap.Typed("agenda"))
                ALLOW -> alone(OwnTap.AllowCalendar)
                else -> null
            }

            else -> null
        }
    }
}

/** When the calendar is read again for the Agenda widget. Pure. Times are in milliseconds on the wall clock. */
internal object AgendaTurns {
    /** The calendar is read at most once in this time, however often the card asks. */
    const val GAP_MS = 60_000L

    /**
     * True when the calendar is read at [now]. [last] is the last read, and [allowed] says whether
     * Kolahal has the calendar permission now.
     * - Nothing is read yet, or the permission changed: now.
     * - Without the permission there is nothing to read, and [last] says so already.
     * - Else when [last] is [GAP_MS] old. A read in the future (the clock was set back) counts as old.
     */
    fun due(last: AgendaRead, allowed: Boolean, now: Long): Boolean = when {
        last.at == 0L || last.allowed != allowed -> true
        !allowed -> false
        else -> (now - last.at).let { it !in 0..<GAP_MS }
    }
}

/**
 * Kolahal's own data for the built-in widgets. The view model holds one. It makes the [OwnFeed] the
 * cards read, and does what a card's tap changes.
 * - Nothing here touches what script widgets store (WidgetStates), and nothing runs a script.
 * - The stores and the calendar are read on IO threads, never on the main thread.
 * - A list is read at once, and again when its store counts a write ([TodoStore.changes],
 *   [com.mtwabpin.kolahal.feeds.FeedRepository.version]), a card asks ([shown]), or the view model
 *   reloads ([reload]). It is read even with no card on screen: the suggestions and the header
 *   read these lists too. They are small, and a write is read once however many follow it.
 * - The calendar is read when the Agenda card asks ([shown]), at most once in a minute
 *   ([AgendaTurns]), and once for the card's first reader. Nothing reads it for a widget that is not placed.
 */
internal class OwnData(context: Context, private val scope: CoroutineScope) {
    private val context = context.applicationContext
    private val todoStore = TodoStore.get(context)
    private val noteStore = NoteStore.get(context)
    private val reminderStore = ReminderStore.get(context)
    private val feedRepo = com.mtwabpin.kolahal.feeds.FeedRepository.get(context)
    private val media = MediaRepository.get(context)

    /** One thread for what a tap writes or sends, so two quick taps keep their order. */
    private val taps = Dispatchers.IO.limitedParallelism(1)

    /** One thread for the calendar, so two cards' questions never read it twice at once. */
    private val calendar = Dispatchers.IO.limitedParallelism(1)

    /** Rises when a card asks for its list again ([shown]) or the view model reloads ([reload]): for a write that went past the stores. */
    private val again = MutableStateFlow(0)

    /**
     * A list, read with [read] at each of the store's [changes] and each time it is asked [again].
     * Several changes in a row are read once. A read that fails keeps the last list.
     */
    private fun <T> list(changes: StateFlow<Int>, read: () -> List<T>): StateFlow<List<T>?> =
        combine(changes, again) { _, _ -> }
            .conflate()
            .mapNotNull { runCatching(read).getOrNull() }
            .flowOn(Dispatchers.IO)
            .stateIn(scope, SharingStarted.Eagerly, null)

    private val shortcuts = MutableStateFlow<List<Shortcut>?>(null)
    private val agenda = MutableStateFlow(AgendaRead.NOT_READ)
    private val mediaAllowed = MutableStateFlow(true)

    val feed = OwnFeed(
        todos = list(todoStore.changes) { todoStore.all() },
        notes = list(noteStore.changes) { noteStore.all() },
        reminders = list(reminderStore.changes) { reminderStore.all() },
        feeds = list(feedRepo.version) { feedRepo.all() },
        shortcuts = shortcuts.asStateFlow(),
        playing = media.nowPlaying,
        agenda = agenda.asStateFlow(),
        mediaAllowed = mediaAllowed.asStateFlow(),
        lastPlayer = media.lastPlayer,
    )

    /**
     * The card of the built-in widget [id] came into view. What has no count of its own writes is
     * looked at again here: the calendar, at most once in [AgendaTurns.GAP_MS], and notification
     * access. A store's list is read again too, for a write that went past the store (a restored backup).
     */
    fun shown(id: String) {
        when (id) {
            WidgetSource.AGENDA -> readAgenda()
            WidgetSource.MEDIA -> scope.launch(Dispatchers.IO) {
                mediaAllowed.value = runCatching { media.hasAccess() }.getOrDefault(false)
            }

            WidgetSource.TODOS, WidgetSource.NOTES, WidgetSource.REMINDERS -> reload()
        }
    }

    /** Reads the lists again: after a write that went past the stores, such as a restored backup. */
    fun reload() = again.update { it + 1 }

    /** The answer to the calendar permission came. The Agenda card must not say "allow" after a yes. */
    fun calendarAnswered() = readAgenda()

    /** Reads the calendar if that is due ([AgendaTurns.due]). Without the permission it only says so. */
    private fun readAgenda() {
        scope.launch(calendar) {
            val now = System.currentTimeMillis()
            val allowed = runCatching { Agenda.hasPermission(context) }.getOrDefault(false)
            if (!AgendaTurns.due(agenda.value, allowed, now)) return@launch
            val events = if (allowed) runCatching {
                Agenda.query(
                    context,
                    LocalDate.now(),
                    AGENDA_READ_DAYS
                )
            }.getOrDefault(emptyList()) else emptyList()
            agenda.value = AgendaRead(allowed, events, now)
        }
    }

    /** The Shortcuts button at [index], counted from 0, as the card shows it now. Null when there is none. */
    fun shortcut(index: Int): Shortcut? = shortcuts.value?.getOrNull(index)

    /**
     * Stores the Shortcuts widget's list. The cards see it at once. Only the Widgets screen's
     * editor calls this, through the view model: never a command, never a script (ShortcutsWriteTest).
     * When the list can't be written, the cards show the stored list again, and the log says why.
     */
    fun saveShortcuts(list: List<Shortcut>) {
        val cleaned = Shortcuts.clean(list)
        val before = shortcuts.value
        shortcuts.value = cleaned
        scope.launch(taps) {
            try {
                Shortcuts.write(context, cleaned)
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                // Only if no later save has changed the list since.
                shortcuts.compareAndSet(cleaned, before)
                com.mtwabpin.kolahal.data.Quietly.report(context, "Saving the shortcuts", e)
            }
        }
    }

    private val editorAsked = MutableStateFlow(false)

    /**
     * True from a tap that asks for the Shortcuts editor (the card's `edit`) until the Widgets
     * screen has opened it ([shortcutsEditorOpened]).
     */
    val shortcutsEditorAsked: StateFlow<Boolean> = editorAsked.asStateFlow()

    /** The Shortcuts card asks for its editor. The Widgets screen, which is opened next, shows it. */
    fun askShortcutsEditor() {
        editorAsked.value = true
    }

    /** The Widgets screen has opened the editor that was asked for. */
    fun shortcutsEditorOpened() {
        editorAsked.value = false
    }

    /** Marks the todo [id] done, or open again. The store counts the write, so the card follows. */
    fun setTodo(id: Long, done: Boolean) {
        scope.launch(taps) { runCatching { todoStore.setDone(id, done) } }
    }

    /** Sends a media button to the session that [OwnFeed.playing] shows. */
    fun media(action: MediaRepository.Action) {
        scope.launch(taps) { runCatching { media.send(action) } }
    }

    /** Moves the track that [OwnFeed.playing] shows to [ms], in milliseconds. */
    fun seek(ms: Long) {
        scope.launch(taps) { runCatching { media.seekTo(ms) } }
    }

    /**
     * Plays the last player's track again ([MediaRepository.resume]). When nothing can be sent to it,
     * [open] gets the player, so that its app opens instead.
     */
    fun resume(open: (LastPlayer) -> Unit) {
        scope.launch(taps) {
            val done = runCatching { media.resume() }.getOrDefault(MediaRepository.Resumed.NOTHING)
            if (done == MediaRepository.Resumed.NOTHING) media.lastPlayer.value?.let(open)
        }
    }

    /**
     * The player the card shows, for a tap that opens it: the last player, which is the session's
     * while one shows ([MediaRepository.lastPlayer]), else any session's.
     */
    val player: LastPlayer?
        get() = media.lastPlayer.value ?: media.nowPlaying.value?.let { LastPlayer(it.pkg, it.app) }

    /** The calendar event [id] that starts at [begin], as last read. Null when it is not among them. */
    fun event(id: Long, begin: Long?): AgendaEvent? =
        agenda.value.events.firstOrNull { it.id == id && (begin == null || it.begin == begin) }

    // Last in the class: everything above is set when this starts.
    init {
        // The shortcuts are read when their first reader comes: the widget's card, or the editor.
        scope.launch {
            shortcuts.subscriptionCount.first { it > 0 }
            val read =
                withContext(taps) { runCatching { Shortcuts.read(context) }.getOrDefault(emptyList()) }
            // A list saved in the meantime is newer than what was read.
            shortcuts.compareAndSet(null, read)
        }
        // A card can be drawn before it is in view: beside the log, or on the Widgets screen, where
        // it never asks. So the first reader of the access and of the calendar starts one look, once.
        // The card is then right when it is first seen. Every later look comes from [shown].
        scope.launch {
            mediaAllowed.subscriptionCount.first { it > 0 }
            shown(WidgetSource.MEDIA)
        }
        scope.launch {
            agenda.subscriptionCount.first { it > 0 }
            shown(WidgetSource.AGENDA)
        }
    }

    companion object {
        /**
         * The days the Agenda widget reads from the calendar: the most it can show. The card shows
         * fewer when widgets.agenda-days says so, with no new read when that setting changes.
         */
        const val AGENDA_READ_DAYS = 14

        /** The days the Agenda card shows for widgets.agenda-days: "auto" looks as far as it reads. */
        fun agendaDays(setting: String): Int =
            setting.toIntOrNull()?.coerceIn(1, AGENDA_READ_DAYS) ?: AGENDA_READ_DAYS
    }
}
