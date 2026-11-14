package com.mtwabpin.kolahal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import android.media.session.PlaybackState
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.TimeFormats
import com.mtwabpin.kolahal.data.takeWhole
import com.mtwabpin.kolahal.productivity.Agenda
import com.mtwabpin.kolahal.productivity.AgendaEvent
import com.mtwabpin.kolahal.productivity.Note
import com.mtwabpin.kolahal.productivity.Reminder
import com.mtwabpin.kolahal.productivity.Repeat
import com.mtwabpin.kolahal.productivity.TimeSpec
import com.mtwabpin.kolahal.productivity.Todo
import com.mtwabpin.kolahal.terminal.AgendaRead
import com.mtwabpin.kolahal.terminal.OwnData
import com.mtwabpin.kolahal.terminal.OwnFeed
import com.mtwabpin.kolahal.terminal.OwnTap
import com.mtwabpin.kolahal.terminal.OwnTaps
import com.mtwabpin.kolahal.terminal.WidgetCardActions
import com.mtwabpin.kolahal.widgets.Shortcut
import com.mtwabpin.kolahal.widgets.Shortcuts
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetJson
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * The built-in cards' pure parts: what each draws for Kolahal's own data. A card is a description
 * of one of the kinds a script can use too, made here in the main process. It is never stored.
 *
 * A row's tap is `WidgetTap(args = <words>)` with one of the widget's few words ([OwnTaps]). The
 * words go to [WidgetCardActions.own], not to a script, and a command is never among them.
 */
internal object OwnCards {
    /** The built-in widgets that [OwnWidget] draws. Battery and network has a card of its own. */
    private val DRAWN = setOf(WidgetSource.TODOS, WidgetSource.REMINDERS, WidgetSource.NOTES, WidgetSource.SHORTCUTS, WidgetSource.MEDIA, WidgetSource.AGENDA)

    /** Whether [OwnWidget] draws the built-in widget [id]. */
    fun draws(id: String?): Boolean = id in DRAWN

    /** The most todos a card holds. No card is tall enough for more. */
    const val MAX_TODOS = 50
    /** The Reminders card shows the next reminder and the two after it. */
    const val MAX_REMINDERS = 3
    /** A large Notes card shows the newest three notes. */
    const val MAX_NOTES = 3
    private const val MAX_NOTE = 500
    private const val MAX_NOTE_LINE = 120

    private val BREAKS = Regex("""\s*[\r\n]+\s*""")

    private fun cut(text: String, max: Int): String = if (text.length <= max) text else text.takeWhole(max - 1).trimEnd() + "…"

    /** A card that only says [words], in the middle: "No notes yet". */
    fun quiet(id: String, words: String): WidgetSpec.Rows =
        WidgetSpec.Rows(base = WidgetBase(title = WidgetSource.builtinTitle(id)), items = emptyList(), empty = words)

    // ---- shortcuts --------------------------------------------------------------------------

    /** What the Shortcuts card says while its list is empty. A tap on the card opens the editor. */
    const val NO_SHORTCUTS = "No shortcuts yet. Tap to add commands."

    /**
     * The Shortcuts card: a button for each shortcut of [list], with its label and its icon. Null
     * for an empty list: the card then says how to add some ([NO_SHORTCUTS]).
     *
     * A button's tap holds its place in the list (`run 2`), never its command. The view model looks
     * the command up in your list when the button is tapped.
     */
    fun shortcuts(list: List<Shortcut>): WidgetSpec.Buttons? {
        if (list.isEmpty()) return null
        return WidgetSpec.Buttons(
            base = WidgetBase(title = WidgetSource.builtinTitle(WidgetSource.SHORTCUTS)),
            items = list.take(Shortcuts.MAX).mapIndexed { i, s ->
                WidgetSpec.Button(label = s.label, tap = WidgetTap(args = OwnTaps.run(i), label = "run"), icon = s.icon)
            },
        )
    }

    // ---- now playing ------------------------------------------------------------------------

    /** What the Now playing card says with no media session to show. */
    const val NOTHING_PLAYING = "Nothing playing"
    /** What the Now playing card says without notification access. A tap on the card asks for it. */
    const val ALLOW_NOTIFICATIONS = "Allow notification access"

    /**
     * Whether the Now playing card shows [p]: a session that plays or is paused, however long ago.
     * The header hides a paused one after a while. The card keeps it, so that you can start it again.
     * A session with no state, or in an error, has nothing to control.
     */
    fun shows(p: NowPlaying?): Boolean = p != null && p.state != PlaybackState.STATE_NONE && p.state != PlaybackState.STATE_ERROR

    /**
     * The Now playing card for the session [p]: the track, the artist, the app, and where the track
     * is. The card's three buttons send `prev`, `toggle` and `next` ([OwnTaps]).
     * - The position moves by itself only while the track really plays. While it buffers it stands.
     * - A position that is not known is 0, and so is a length that is not known.
     */
    fun media(p: NowPlaying): WidgetSpec.Media = WidgetSpec.Media(
        base = WidgetBase(title = WidgetSource.builtinTitle(WidgetSource.MEDIA)),
        title = p.title?.trim()?.takeIf { it.isNotEmpty() },
        artist = p.artist?.trim()?.takeIf { it.isNotEmpty() },
        app = p.app,
        playing = p.playing,
        position = p.position.coerceAtLeast(0),
        positionAt = p.positionAt,
        speed = if (p.state == PlaybackState.STATE_PLAYING) p.speed else 0f,
        duration = p.duration.coerceAtLeast(0),
    )

    // ---- agenda -----------------------------------------------------------------------------

    /** What the Agenda card says without the calendar permission. A tap on the card asks for it. */
    const val ALLOW_CALENDAR = "Allow calendar access"
    private const val MAX_EVENT_TITLE = 80

    /**
     * The Agenda card for the calendar's [events], as [Agenda.query] gave them at [now] (in
     * milliseconds): the events of [days] days from today, at most [WidgetJson.MAX_EVENTS].
     * - [Agenda.group] says which events count: the read is a day wider on each side than what is
     *   shown, and an event that has ended is left out.
     * - The calendar keeps an all-day event at midnight UTC. Here it starts at midnight in [zone],
     *   so it is on the right day wherever the phone is. One that started before today starts today.
     * - An event's tap holds its id and its start (`event 41 1800000000000`), so the right time of
     *   a repeating event opens.
     */
    fun agenda(events: List<AgendaEvent>, zone: ZoneId, now: Long, days: Int): WidgetSpec.Agenda {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        // An all-day event of several days is in the group of each of its days. It is one event here.
        val shown = Agenda.group(events, zone, today, days, now).flatMap { it.second }.distinct()
        return WidgetSpec.Agenda(
            base = WidgetBase(title = WidgetSource.builtinTitle(WidgetSource.AGENDA)),
            events = shown.take(WidgetJson.MAX_EVENTS).map { e ->
                val tap = WidgetTap(args = OwnTaps.event(e.id, e.begin), label = "open event")
                val title = cut(e.title.trim(), MAX_EVENT_TITLE)
                if (e.allDay) {
                    val first = Instant.ofEpochMilli(e.begin).atZone(ZoneOffset.UTC).toLocalDate()
                    val after = maxOf(Instant.ofEpochMilli(e.end).atZone(ZoneOffset.UTC).toLocalDate(), first.plusDays(1))
                    WidgetSpec.Event(
                        title = title,
                        starts = maxOf(first, today).atStartOfDay(zone).toEpochSecond(),
                        ends = after.atStartOfDay(zone).toEpochSecond(),
                        allDay = true,
                        tap = tap,
                    )
                } else {
                    WidgetSpec.Event(title = title, starts = e.begin / 1000, ends = e.end.takeIf { it > e.begin }?.let { it / 1000 }, allDay = false, tap = tap)
                }
            },
            days = days,
            empty = if (days == 1) "Nothing left today" else "No events in the next $days days",
        )
    }

    // ---- todos ------------------------------------------------------------------------------

    /**
     * The todos a card shows, in the order they were added: the open ones, and the ones in [kept].
     * [kept] are the todos that were ticked on this card since it came into view. Such a todo keeps
     * its place, shown as done, so that a second tap can undo a slip of the finger. It leaves the
     * card when the page is turned away.
     */
    fun todosShown(list: List<Todo>, kept: Set<Long>): List<Todo> = list.filter { !it.done || it.id in kept }.sortedBy { it.id }

    /**
     * The Todos card: a row for each of [todosShown], at most [MAX_TODOS], with its checkbox. A tap
     * on a row marks its todo done, and a tap on a done row opens it again. The row names the todo
     * by its id, which stays the same when the list changes.
     */
    fun todos(list: List<Todo>, kept: Set<Long> = emptySet()): WidgetSpec.Rows = WidgetSpec.Rows(
        base = WidgetBase(title = WidgetSource.builtinTitle(WidgetSource.TODOS)),
        items = todosShown(list, kept).take(MAX_TODOS).map { t ->
            WidgetSpec.Row(
                title = t.text,
                tap = if (t.done) WidgetTap(args = OwnTaps.undo(t.id), label = "mark as not done") else WidgetTap(args = OwnTaps.done(t.id), label = "mark as done"),
                done = t.done,
            )
        },
        empty = if (list.isEmpty()) "No todos yet" else "Nothing open",
    )

    /** The Todos card for a screen reader. Each row is an item of its own, so this only counts them. */
    fun todosSaid(list: List<Todo>): String = when (val open = list.count { !it.done }) {
        0 -> if (list.isEmpty()) "Todos: no todos yet" else "Todos: nothing open"
        else -> "Todos: $open open"
    }

    // ---- reminders --------------------------------------------------------------------------

    /**
     * When a reminder is due, as the `reminders` command says it: "18:30" for today, "Tomorrow
     * 09:00", else "Fri 23 Oct 08:00". [at] and [now] are in milliseconds.
     */
    fun whenLabel(at: Long, now: Long, zone: ZoneId, clock24: Boolean, locale: Locale = Locale.getDefault()): String {
        val t = Instant.ofEpochMilli(at).atZone(zone)
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val time = TimeFormats.clockFormat(clock24, locale).format(t)
        return when (t.toLocalDate()) {
            today -> time
            today.plusDays(1) -> "Tomorrow $time"
            else -> "${t.format(TimeFormats.dayFormat(locale))} $time"
        }
    }

    /**
     * The time from [now] to [at], in whole minutes, rounded up: "in 25m", "in 2h 10m", "in 1d 1h".
     * A reminder whose time has come says "now". Both are in milliseconds.
     */
    fun until(at: Long, now: Long): String {
        val left = at - now
        if (left <= 0) return "now"
        return "in " + TimeSpec.formatDuration((left + 59_999) / 60_000 * 60)
    }

    /**
     * The Reminders card: the next [MAX_REMINDERS] reminders of [list], which is soonest first. Each
     * row has the text, when it is due and how it repeats, and the time to it at its end. The card
     * is made again each minute, so the time to it counts down with no run.
     */
    fun reminders(list: List<Reminder>, now: Long, zone: ZoneId, clock24: Boolean, locale: Locale = Locale.getDefault()): WidgetSpec.Rows = WidgetSpec.Rows(
        base = WidgetBase(title = WidgetSource.builtinTitle(WidgetSource.REMINDERS)),
        items = list.take(MAX_REMINDERS).map { r ->
            WidgetSpec.Row(
                title = r.text,
                // A rule that can't be read has no words. It must not end the card: such a reminder fires once.
                detail = whenLabel(r.at, now, zone, clock24, locale) + r.repeat?.let { rule -> runCatching { " · ${Repeat.label(rule)}" }.getOrNull() }.orEmpty(),
                trailing = until(r.at, now),
            )
        },
        empty = "No reminders",
    )

    // ---- notes ------------------------------------------------------------------------------

    /**
     * The Notes card for [list], which is newest first. Null with no notes: the card then says so ([quiet]).
     * - With [count] 1 it is the newest note, with its line breaks.
     * - With more, it is the newest [count] notes, each on one line of its own.
     * - The caption counts the notes that are not shown.
     */
    fun notes(list: List<Note>, count: Int = 1): WidgetSpec.Text? {
        if (list.isEmpty()) return null
        val shown = list.take(count.coerceIn(1, MAX_NOTES))
        val text = if (shown.size == 1) cut(shown[0].text.trim(), MAX_NOTE) else shown.joinToString("\n") { "• " + cut(it.text.trim().replace(BREAKS, " "), MAX_NOTE_LINE) }
        val more = list.size - shown.size
        return WidgetSpec.Text(
            base = WidgetBase(title = WidgetSource.builtinTitle(WidgetSource.NOTES)),
            text = text,
            caption = if (more > 0) "+$more more" else null,
        )
    }
}

/** For a card with no feed: a list that is never read. The card stays empty. */
private val NOT_READ: StateFlow<Nothing?> = MutableStateFlow(null)

/** A card this tall is "large" ([com.mtwabpin.kolahal.widgets.WidgetSizes.box]): it has room for more than one note. */
private val LARGE = 150.dp

/**
 * Draws the built-in widget [id] ([OwnCards.draws]), [height] tall. Call it inside [GuiTheme].
 * - Kolahal fills the card from its own data, live: [own] is what the view model reads for it. No
 *   script runs, and nothing is stored for the card.
 * - A card collects only the flow it shows. With no [own] it stays empty: a card drawn without a
 *   view model, in a test.
 * - [actions] takes the taps and the long-press. With null the card takes no touches: this is the
 *   Widgets screen.
 */
@Composable
internal fun OwnWidget(id: String, height: Dp, actions: WidgetCardActions?, own: OwnFeed?) {
    when (id) {
        WidgetSource.TODOS -> TodosWidget(height, actions, own)
        WidgetSource.REMINDERS -> RemindersWidget(height, actions, own)
        WidgetSource.NOTES -> NotesWidget(height, actions, own)
        WidgetSource.SHORTCUTS -> ShortcutsWidget(height, actions, own)
        WidgetSource.MEDIA -> MediaWidget(height, actions, own)
        WidgetSource.AGENDA -> AgendaWidget(height, actions, own)
    }
}

/**
 * The card of the built-in widget [id], around [spec]. With a null [spec] the card stays empty for
 * a moment: its data is not read yet.
 * - A tap on the card sends [cardTap] to [WidgetCardActions.own], and a tap on a row sends the row's
 *   own words. [onRowTap] sees a row's words first.
 * - While the page is in view, and each time home comes back to it, the card says so
 *   ([WidgetCardActions.ownShown]), so that old data is read again. With [againMs] it says so
 *   again that often while it stays in view: for data that changes with no sign, as a calendar does.
 */
@Composable
private fun OwnCard(
    id: String,
    height: Dp,
    spec: WidgetSpec?,
    description: String,
    actions: WidgetCardActions?,
    cardTap: String,
    cardTapLabel: String,
    onRowTap: ((String) -> Unit)? = null,
    againMs: Long? = null,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    if (actions != null && LocalWidgetsShown.current) LaunchedEffect(id, actions, lifecycle, againMs) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            actions.ownShown(id)
            if (againMs != null) while (true) {
                delay(againMs)
                actions.ownShown(id)
            }
        }
    }
    val onClick: (() -> Unit)? = remember(actions, id, cardTap) { actions?.let { a -> { a.own(id, cardTap) } } }
    val onLongClick: (() -> Unit)? = remember(actions) { actions?.let { a -> { a.longPress() } } }
    val onTap: ((WidgetTap) -> Unit)? = remember(actions, id, onRowTap) {
        actions?.let { a ->
            { tap: WidgetTap ->
                tap.args?.let { words ->
                    onRowTap?.invoke(words)
                    a.own(id, words)
                }
                Unit
            }
        }
    }
    WidgetCard(
        title = spec?.base?.title ?: WidgetSource.builtinTitle(id) ?: id,
        height = height,
        spec = spec,
        note = if (spec == null) CardNote.WAITING else null,
        description = description,
        modifier = Modifier.testTag("widget:${WidgetSource.builtin(id)}"),
        onClick = onClick,
        onClickLabel = cardTapLabel,
        onLongClick = onLongClick,
        onTap = onTap,
    )
}

/**
 * The built-in Shortcuts card: a button for each command you picked in the Widgets screen.
 * - A button runs its command as if you had typed it, as a gesture runs the command you set for it.
 * - A tap beside the buttons, and a tap on the card while the list is empty, opens the editor.
 * - It has no data to read again, and nothing to run.
 */
@Composable
private fun ShortcutsWidget(height: Dp, actions: WidgetCardActions?, own: OwnFeed?) {
    val list by (own?.shortcuts ?: NOT_READ).collectAsStateWithLifecycle()
    val spec: WidgetSpec? = remember(list) { list?.let { OwnCards.shortcuts(it) ?: OwnCards.quiet(WidgetSource.SHORTCUTS, OwnCards.NO_SHORTCUTS) } }
    val description = remember(spec) { spec?.let { WidgetWords.description(it.base.title, it, 0L, ZoneId.systemDefault()) } ?: "Shortcuts" }
    OwnCard(WidgetSource.SHORTCUTS, height, spec, description, actions, cardTap = OwnTaps.EDIT, cardTapLabel = "edit shortcuts")
}

/** For a card with no feed: nothing plays, and nothing says that access is missing. */
private val NO_SESSION: StateFlow<NowPlaying?> = MutableStateFlow(null)
private val ALLOWED: StateFlow<Boolean> = MutableStateFlow(true)

/**
 * The built-in Now playing card: the track, the artist, the app and the position, with previous,
 * play or pause, and next.
 * - It shows the media session that the header's line shows, live. Kolahal follows the sessions
 *   for as long as it has notification access, whichever page is in view, so the card has nothing
 *   to start. Each time the page comes into view it asks whether the access is still there.
 * - A button answers every tap: there is no 2-second wait between two taps. A tap beside the
 *   buttons opens the app that plays.
 * - With no session it says "Nothing playing". Without notification access it says "Allow
 *   notification access", and a tap asks for it.
 */
@Composable
private fun MediaWidget(height: Dp, actions: WidgetCardActions?, own: OwnFeed?) {
    val playing by (own?.playing ?: NO_SESSION).collectAsStateWithLifecycle()
    val allowed by (own?.mediaAllowed ?: ALLOWED).collectAsStateWithLifecycle()
    val session = playing?.takeIf { allowed && OwnCards.shows(it) }
    val spec: WidgetSpec = remember(session, allowed) {
        when {
            !allowed -> OwnCards.quiet(WidgetSource.MEDIA, OwnCards.ALLOW_NOTIFICATIONS)
            session == null -> OwnCards.quiet(WidgetSource.MEDIA, OwnCards.NOTHING_PLAYING)
            else -> OwnCards.media(session)
        }
    }
    val description = remember(spec) { WidgetWords.description(spec.base.title, spec, 0L, ZoneId.systemDefault()) }
    OwnCard(
        id = WidgetSource.MEDIA,
        height = height,
        spec = spec,
        description = description,
        actions = actions,
        cardTap = if (allowed) OwnTaps.OPEN else OwnTaps.ALLOW,
        cardTapLabel = if (!allowed) "allow notification access" else if (session != null) "open ${session.app}" else "open the player",
    )
}

/** For a card with no feed: a calendar that is never read. The card stays empty. */
private val NO_AGENDA: StateFlow<AgendaRead> = MutableStateFlow(AgendaRead.NOT_READ)

/** While the Agenda card stays in view, it asks for the calendar again this often. */
private const val AGENDA_AGAIN_MS = 15 * 60_000L

/**
 * The built-in Agenda card: the phone's calendar for today and the next days.
 * - The view model reads the calendar on an IO thread when the page comes into view, and every 15
 *   minutes while it stays there, but at most once in a minute. An event that has ended drops out
 *   by itself in between.
 * - A tap on an event opens it in the calendar app. A tap on the card runs `agenda` and turns home
 *   to the log.
 * - Without the calendar permission the card says "Allow calendar access", and a tap asks for it.
 */
@Composable
private fun AgendaWidget(height: Dp, actions: WidgetCardActions?, own: OwnFeed?) {
    val read by (own?.agenda ?: NO_AGENDA).collectAsStateWithLifecycle()
    // Nothing is read yet: the card stays empty, and does not say "allow" before it knows.
    val known = read.at != 0L
    val zone = remember(read) { ZoneId.systemDefault() }
    val spec: WidgetSpec? = remember(read, zone) {
        when {
            !known -> null
            !read.allowed -> OwnCards.quiet(WidgetSource.AGENDA, OwnCards.ALLOW_CALENDAR)
            else -> OwnCards.agenda(read.events, zone, read.at, OwnData.AGENDA_DAYS)
        }
    }
    val description = remember(spec) { spec?.let { WidgetWords.description(it.base.title, it, read.at / 1000, zone) } ?: "Agenda" }
    val ask = known && !read.allowed
    OwnCard(
        id = WidgetSource.AGENDA,
        height = height,
        spec = spec,
        description = description,
        actions = actions,
        cardTap = if (ask) OwnTaps.ALLOW else OwnTaps.OPEN,
        cardTapLabel = if (ask) "allow calendar access" else "open the agenda",
        againMs = AGENDA_AGAIN_MS,
    )
}

/**
 * The built-in Todos card: the open todos, each with a checkbox.
 * - A tap on a row marks its todo done. The row stays, ticked, until the page is turned away, and
 *   a tap on it opens the todo again.
 * - A tap on the card runs `todo` and turns home to the log.
 */
@Composable
private fun TodosWidget(height: Dp, actions: WidgetCardActions?, own: OwnFeed?) {
    val list by (own?.todos ?: NOT_READ).collectAsStateWithLifecycle()
    // The todos ticked here since the page came into view. They are forgotten when it is turned away.
    var kept by remember { mutableStateOf(emptySet<Long>()) }
    val inView = LocalWidgetsShown.current
    LaunchedEffect(inView) { if (!inView) kept = emptySet() }
    val spec = remember(list, kept) { list?.let { OwnCards.todos(it, kept) } }
    val description = remember(list) { list?.let(OwnCards::todosSaid) ?: "Todos" }
    val onRowTap: (String) -> Unit = remember {
        { words ->
            (OwnTaps.parse(WidgetSource.TODOS, words) as? OwnTap.SetTodo)?.let { kept = if (it.done) kept + it.id else kept - it.id }
        }
    }
    OwnCard(WidgetSource.TODOS, height, spec, description, actions, cardTap = OwnTaps.OPEN, cardTapLabel = "open todos", onRowTap = onRowTap)
}

/**
 * The built-in Reminders card: the next reminder and the two after it, with the time to each.
 * The header's minute tick makes the card again, so the time counts down with no run. A tap runs
 * `reminders` and turns home to the log.
 */
@Composable
private fun RemindersWidget(height: Dp, actions: WidgetCardActions?, own: OwnFeed?) {
    val list by (own?.reminders ?: NOT_READ).collectAsStateWithLifecycle()
    val context = LocalContext.current.applicationContext
    val config = remember(context) { Config.get(context) }
    val version by config.version.collectAsStateWithLifecycle()
    val clock24 = remember(version) { config[Keys.CLOCK_24H] }
    val (minute, zoneChanges) = rememberNow()
    val zone = remember(zoneChanges) { ZoneId.systemDefault() }
    val spec = remember(list, minute, zone, clock24) { list?.let { OwnCards.reminders(it, minute.time, zone, clock24) } }
    val description = remember(spec) { spec?.let { WidgetWords.description(it.base.title, it, 0L, zone) } ?: "Reminders" }
    OwnCard(WidgetSource.REMINDERS, height, spec, description, actions, cardTap = OwnTaps.OPEN, cardTapLabel = "open reminders")
}

/**
 * The built-in Notes card: the newest note, or the newest three in a large card. A tap runs `notes`
 * and turns home to the log.
 */
@Composable
private fun NotesWidget(height: Dp, actions: WidgetCardActions?, own: OwnFeed?) {
    val list by (own?.notes ?: NOT_READ).collectAsStateWithLifecycle()
    val count = if (height >= LARGE) OwnCards.MAX_NOTES else 1
    val spec: WidgetSpec? = remember(list, count) { list?.let { OwnCards.notes(it, count) ?: OwnCards.quiet(WidgetSource.NOTES, "No notes yet") } }
    val description = remember(spec) { spec?.let { WidgetWords.description(it.base.title, it, 0L, ZoneId.systemDefault()) } ?: "Notes" }
    OwnCard(WidgetSource.NOTES, height, spec, description, actions, cardTap = OwnTaps.OPEN, cardTapLabel = "open notes")
}
