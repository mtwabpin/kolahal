package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.data.StatusLine
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.status.DeviceStatus
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.terminal.WidgetAge
import com.mtwabpin.kolahal.terminal.WidgetCardActions
import com.mtwabpin.kolahal.terminal.WidgetFeed
import com.mtwabpin.kolahal.terminal.WidgetScript
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetState
import com.mtwabpin.kolahal.widgets.WidgetStates
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * True while the widgets page is the page in view: home's pager has settled on it. Home keeps the
 * page composed beside the log, so "composed" does not mean "seen". Things that move (the loading
 * shape, a wave, a countdown's seconds) and refresh requests run only while this is true.
 */
val LocalWidgetsShown = compositionLocalOf { false }

/**
 * The long-press of the card around a kind's content, or null when the card takes none. A part of
 * the content with a tap of its own (a list's row) takes the press before the card does, so it
 * hands a long-press on to this.
 */
internal val LocalCardLongPress = compositionLocalOf<(() -> Unit)?> { null }

/** What a card shows when it has no content to draw. */
internal enum class CardNote {
    /** The stored state is not read yet. The card stays empty for a moment. */
    WAITING,

    /** Nothing is stored yet, and a run is on its way. */
    LOADING,

    /** The script showed nothing, or cleared its widget. */
    NOTHING,

    /** The last run failed and nothing is stored. The card shows the error's first line. */
    FAILED,

    /** The stored content can't be read, or nothing can fill this widget. */
    UNAVAILABLE,

    /** A Status card whose script has set no status line. */
    NO_STATUS,

    /** No script has the card's name any more: it was deleted. The card stays until you remove it. */
    REMOVED,

    /** The card's script is switched off. */
    OFF,

    /** The script's code has no `@widget` line any more, so `widget.show` stores nothing. */
    NO_WIDGET,
}

/**
 * The note of a card whose script can't fill it, or null for a script that can.
 * - [read] is false while the view model has not read the scripts yet. Nothing is known then, and
 *   the card says nothing about its script.
 * - A null [script] means that no script has the name.
 * - [ownWidget] is true for the card of the script's own widget, which needs the `@widget` line.
 *   A Status card needs none.
 * Such a card asks for no runs: they would do nothing. A tap on it opens the Scripts screen.
 */
internal fun scriptNote(read: Boolean, script: WidgetScript?, ownWidget: Boolean): CardNote? = when {
    !read -> null
    script == null -> CardNote.REMOVED
    !script.enabled -> CardNote.OFF
    ownWidget && script.widget == null -> CardNote.NO_WIDGET
    else -> null
}

/**
 * The card of a script widget, [height] tall and as wide as it is given. Call it inside [GuiTheme].
 * - It has its own opaque surface, so its text reads over any wallpaper.
 * - It draws [spec] for the box it gets ([WidgetSizes.box]): large has the title in a row on top,
 *   wide has the badge at its start, small has the content only.
 * - With a [note], it shows that and not the content. [failure] is the text of [CardNote.FAILED].
 * - [footer] is the content's age, or "Failed 10:42". Large has it as its last line, wide at the
 *   end of the title's line. Small has no room for it.
 * - [description] is the whole card for a screen reader, in one item. Rows with their own tap are
 *   items of their own inside it.
 * - [onClick] is a tap on the card and [onLongClick] a long-press. [onTap] gets a row's own action.
 *   With all three null the card takes no touches (the Widgets screen).
 */
@Composable
internal fun WidgetCard(
    title: String,
    height: Dp,
    spec: WidgetSpec?,
    note: CardNote?,
    description: String,
    modifier: Modifier = Modifier,
    failure: String? = null,
    footer: WidgetWords.Footer? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onTap: ((WidgetTap) -> Unit)? = null,
) {
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier.fillMaxWidth().height(height)) {
        val box = WidgetSizes.box(maxWidth.value.toInt(), maxHeight.value.toInt())
        // The tap and the description are on one node, so a screen reader finds one item.
        val press = if (onClick != null) {
            Modifier.combinedClickable(
                onClickLabel = onClickLabel,
                onLongClickLabel = if (onLongClick != null) "arrange widgets" else null,
                onLongClick = onLongClick,
                onClick = onClick,
            )
        } else {
            Modifier.semantics(mergeDescendants = true) {}
        }
        Surface(
            color = scheme.surfaceContainerHigh,
            contentColor = scheme.onSurface,
            shape = RoundedCornerShape(if (box == WidgetSizes.Box.SMALL) 24.dp else 28.dp),
            modifier = Modifier.fillMaxSize().testTag("widget-card"),
        ) {
            Box(Modifier.fillMaxSize().then(press).semantics { contentDescription = description }) {
                CompositionLocalProvider(LocalCardLongPress provides onLongClick) {
                    CardLayout(title, spec, note, failure, footer, box, onTap)
                }
            }
        }
    }
}

/** The title, the badge, the content and the footer, placed for [box]. */
@Composable
private fun CardLayout(
    title: String,
    spec: WidgetSpec?,
    note: CardNote?,
    failure: String?,
    footer: WidgetWords.Footer?,
    box: WidgetSizes.Box,
    onTap: ((WidgetTap) -> Unit)?,
) {
    val type = MaterialTheme.typography
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val icon = spec?.base?.icon
    val colour = roleColour(spec?.base)
    when (box) {
        WidgetSizes.Box.LARGE -> Column(Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    IconBadge(icon, colour, badgeShape(spec), 32.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = type.titleSmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            Box(Modifier.weight(1f).fillMaxWidth()) { CardBody(spec, note, failure, box, onTap) }
            if (footer != null) FooterLine(footer, Modifier.padding(top = 4.dp))
        }
        WidgetSizes.Box.WIDE -> Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                IconBadge(icon, colour, badgeShape(spec), 40.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = type.labelMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (footer != null) FooterLine(footer, Modifier.padding(start = 8.dp))
                }
                Box(Modifier.weight(1f).fillMaxWidth()) { CardBody(spec, note, failure, box, onTap) }
            }
        }
        // No room for a title or a footer: the content alone. A screen reader still hears both.
        WidgetSizes.Box.SMALL -> Box(Modifier.fillMaxSize().padding(8.dp)) { CardBody(spec, note, failure, box, onTap) }
    }
}

/**
 * The card's small line: the age of its content, or "Failed 10:42". A failure is in the error
 * colour, and its words say it too.
 */
@Composable
private fun FooterLine(footer: WidgetWords.Footer, modifier: Modifier = Modifier) {
    Text(
        footer.text,
        style = MaterialTheme.typography.labelSmall,
        color = if (footer.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

/** The content, or what the card says without any. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardBody(spec: WidgetSpec?, note: CardNote?, failure: String?, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val small = box == WidgetSizes.Box.SMALL
    when {
        note == CardNote.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // The shape changes form for as long as it is drawn. So it is drawn only while the page is in view.
            // The card's description says "loading", so the shape itself is no item for a screen reader.
            if (LocalWidgetsShown.current) LoadingIndicator(Modifier.size(if (box == WidgetSizes.Box.LARGE) 48.dp else 36.dp).clearAndSetSemantics {})
        }
        note == CardNote.FAILED -> QuietLine(failure ?: WidgetWords.NOTHING, small)
        note == CardNote.NOTHING -> QuietLine(WidgetWords.NOTHING, small)
        note == CardNote.UNAVAILABLE -> QuietLine(WidgetWords.UNAVAILABLE, small)
        note == CardNote.NO_STATUS -> QuietLine(StatusCards.NO_LINE, small)
        note == CardNote.REMOVED -> QuietLine(WidgetWords.REMOVED, small)
        note == CardNote.OFF -> QuietLine(WidgetWords.OFF, small)
        note == CardNote.NO_WIDGET -> QuietLine(WidgetWords.NO_WIDGET, small)
        note == null && spec != null -> KindBody(spec, box, onTap)
    }
}

/** The widget's icon (an emoji or a letter) on a shape in its role's colour. */
@Composable
private fun IconBadge(icon: String, colour: Color, shape: Shape, size: Dp) {
    Box(Modifier.size(size).clip(shape).background(colour.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) {
        Text(
            icon,
            style = if (size >= 40.dp) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
            color = colour,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** One shape for each kind: a cookie for a number, a pill for a list, and so on. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun badgeShape(spec: WidgetSpec?): Shape = when (spec) {
    is WidgetSpec.Stat -> MaterialShapes.Cookie9Sided
    is WidgetSpec.Text -> MaterialShapes.Square
    is WidgetSpec.Rows -> MaterialShapes.Pill
    is WidgetSpec.Progress -> MaterialShapes.Circle
    is WidgetSpec.Chart -> MaterialShapes.Gem
    is WidgetSpec.Countdown -> MaterialShapes.Sunny
    null -> MaterialShapes.Circle
}.toShape()

/** A tap on a card whose description has no action: the view model reads it as "refresh". */
private val NO_TAP = WidgetTap()

/** What a screen reader says a tap does on a card whose script can't fill it ([scriptNote]). */
private const val OPEN_SCRIPTS = "open scripts"

/** How long a card with nothing stored shows the loading shape. After that it says "Nothing to show": no spinner turns for ever. */
private const val LOADING_MS = 20_000L

/** How often a card in view looks whether its content has grown old. */
private const val LOOK_AGAIN_MS = 60_000L

/** The shortest `refresh` of a description, in seconds, as for a status line. */
private const val MIN_REFRESH_S = 10

/**
 * Draws placed widget [w], which Kolahal draws itself ([PlacedWidget.source]), [height] tall.
 * - A script's widget draws what the script stored with `widget.show` ([WidgetStates]). The main
 *   process never waits for a script: the card comes from stored state, and a run fills it later.
 * - A Status card draws its script's status line ([StatusLine]).
 * - The built-in Battery and network widget draws the device's status, live ([DeviceStatus]).
 * - Any other source says "widget unavailable": a built-in widget that this version does not have,
 *   from a newer Kolahal's backup.
 * - [actions] takes taps, the long-press and refresh requests. With null the card takes no touches
 *   and asks for no runs: this is the Widgets screen.
 * - [feed] is what the view model knows of the scripts. A card reads its script from there, and
 *   never from the database. With null the card knows nothing of its script, and draws what is
 *   stored under the script's name: a card drawn without a view model, in a test.
 * It brings its own [GuiTheme], because the widgets page is drawn in the terminal's styles.
 */
@Composable
internal fun SourceWidget(w: PlacedWidget, height: Dp, actions: WidgetCardActions?, feed: WidgetFeed? = null) {
    val name = WidgetSource.scriptName(w.source)
    val status = WidgetSource.statusName(w.source)
    GuiTheme {
        if (name != null) {
            ScriptWidget(name, height, actions, feed)
        } else if (status != null) {
            StatusWidget(status, height, actions, feed)
        } else if (WidgetSource.builtinId(w.source) == WidgetSource.BATTERY) {
            BatteryWidget(height, actions)
        } else {
            val what = w.source.orEmpty().substringAfter(':')
            // Nothing to tap here, so a tap opens the screen where the widget can be removed.
            val arrange: (() -> Unit)? = remember(actions) { actions?.let { a -> { a.longPress() } } }
            WidgetCard(
                title = what,
                height = height,
                spec = null,
                note = CardNote.UNAVAILABLE,
                description = "$what: ${WidgetWords.UNAVAILABLE}",
                onClick = arrange,
                onClickLabel = "arrange widgets",
                onLongClick = arrange,
            )
        }
    }
}

/**
 * What a card knows of its script, from the view model's [WidgetFeed].
 * - [read] is false until the view model has read the scripts, and for a card with no feed.
 * - With [read], a null [script] means that no script has the name.
 */
private data class Known(val read: Boolean, val script: WidgetScript?)

private val UNREAD = Known(read = false, script = null)

/**
 * [name]'s script as [feed] has it. The feed changes when any script's title, switch or `@widget`
 * line changes. A card is drawn again only when what it knows of its own script changes.
 */
@Composable
private fun knownScript(feed: WidgetFeed?, name: String): Known {
    if (feed == null) return UNREAD
    val changes = remember(feed, name) { feed.scripts.map { all -> Known(all != null, all?.get(name)) }.distinctUntilChanged() }
    val known by changes.collectAsStateWithLifecycle(initialValue = remember(feed, name) { feed.scripts.value.let { Known(it != null, it?.get(name)) } })
    return known
}

/**
 * The card of the script [name]'s own widget.
 * - It draws what the script stored with `widget.show`, and reads that again only when it changes.
 * - While in view, it asks for a quiet run when its content is old, or as often as its `refresh` says.
 * - A card whose script is gone, off or without its `@widget` line says so ([scriptNote]). It asks
 *   for no runs, and a tap on it opens the Scripts screen.
 */
@Composable
private fun ScriptWidget(name: String, height: Dp, actions: WidgetCardActions?, feed: WidgetFeed?) {
    val context = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var state by remember(name) { mutableStateOf<WidgetState?>(null) }
    // The state is read once, and again when this widget's own number moves ([WidgetStates.versionOf]):
    // another widget's change reads nothing here. Only while home is on screen, and off the main thread.
    LaunchedEffect(name, lifecycle) {
        var seen: Int? = null
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            WidgetStates.versionOf(name).collectLatest { version ->
                // Home came back and the number did not move: the state in use is still right.
                if (version == seen) return@collectLatest
                val read = withContext(Dispatchers.IO) { WidgetStates.read(context, name) }
                seen = version
                // An equal state keeps the one in use, so the card is not drawn again.
                if (read != state) state = read
            }
        }
    }
    // The script's @title, for a description without a title of its own. The view model has read it.
    val known = knownScript(feed, name)
    // The script is gone or off, or its code has lost the `@widget` line: the card says so, and asks
    // for no runs. Without the line a run stores nothing, so the card would ask again at every view.
    val missing = scriptNote(known.read, known.script, ownWidget = true)
    // With a feed, a run is asked for only once the scripts are read and the script can run. A card
    // with no feed knows nothing of its script, and asks as if it were there.
    val runnable = feed == null || (known.read && missing == null)
    val shown = LocalWidgetsShown.current
    if (actions != null && shown && runnable) {
        // The page came into view, or home came back with the page in view: run the script once if
        // it never filled the widget, or if its content is old. A script that is switched on again
        // starts this again, so its card is filled at once.
        LaunchedEffect(name, actions, lifecycle) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                var asked = -1L
                snapshotFlow { state }.collectLatest { s ->
                    if (s == null) return@collectLatest
                    // One request for each state. A run that fails or shows nothing moves the state's
                    // time, and that state is then not old (WidgetAge.due).
                    val last = WidgetAge.last(s.setAt, s.triedAt)
                    while (last != asked) {
                        if (WidgetAge.due(s.setAt, s.triedAt, s.spec?.base?.staleAfter, System.currentTimeMillis())) {
                            asked = last
                            actions.refresh(name)
                        } else {
                            // Content can grow old while the page shows.
                            delay(LOOK_AGAIN_MS)
                        }
                    }
                }
            }
        }
        // `refresh` in the description: run again that often, only while in view.
        val every = state?.spec?.base?.refresh
        if (every != null) LaunchedEffect(name, actions, lifecycle, every) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) {
                    delay(every.coerceAtLeast(MIN_REFRESH_S) * 1000L)
                    actions.refresh(name)
                }
            }
        }
    }

    // What is stored counts only while the script can fill the card. Kolahal forgets it when a script goes or is switched.
    val s = state.takeIf { missing == null }
    val spec = s?.spec
    // Nothing stored and no run ended yet: the widget was just placed, and its first run is on its way.
    val fresh = s != null && !s.hasContent && s.setAt == 0L && s.triedAt == 0L
    var gaveUp by remember(name) { mutableStateOf(false) }
    LaunchedEffect(name, fresh, shown) {
        if (!fresh) {
            // The next first run gets its own wait: a script that was off and is on again.
            gaveUp = false
        } else if (shown && !gaveUp) {
            delay(LOADING_MS)
            gaveUp = true
        }
    }
    // The failed run's own words, for a card with nothing else to show. Without "tap to edit": a tap here refreshes.
    val failure = remember(s?.error) { WidgetWords.cardError(s?.error) }
    val note = when {
        missing != null -> missing
        s == null -> CardNote.WAITING
        spec != null -> null
        // Something is stored, and it can't be read.
        s.hasContent -> CardNote.UNAVAILABLE
        failure != null -> CardNote.FAILED
        fresh && !gaveUp -> CardNote.LOADING
        else -> CardNote.NOTHING
    }
    val title = spec?.base?.title ?: known.script?.title ?: name
    // The minute's tick, from the header's clock: content grows old, and a countdown's words change.
    val (minute, zoneChanges) = rememberNow()
    val zone = remember(zoneChanges) { ZoneId.systemDefault() }
    val footer = remember(s, minute, zone) {
        s?.let { WidgetWords.footer(it.hasContent, WidgetAge.old(it.setAt, it.spec?.base?.staleAfter, minute.time), it.setAt, it.triedAt, it.error != null, minute.time, zone) }
    }
    // Only a countdown's own words change with the time.
    val now = if (spec is WidgetSpec.Countdown) minute.time / 1000 else 0L
    val description = remember(title, spec, note, failure, footer, now, zone) {
        val content = when {
            spec != null -> WidgetWords.description(title, spec, now, zone)
            note == CardNote.LOADING -> "$title: loading"
            note == CardNote.FAILED -> "$title: $failure"
            note == CardNote.NOTHING -> "$title: ${WidgetWords.NOTHING}"
            note == CardNote.UNAVAILABLE -> "$title: ${WidgetWords.UNAVAILABLE}"
            note == CardNote.REMOVED -> "$title: ${WidgetWords.REMOVED}"
            note == CardNote.OFF -> "$title: ${WidgetWords.OFF}"
            note == CardNote.NO_WIDGET -> "$title: ${WidgetWords.NO_WIDGET}"
            else -> title
        }
        if (footer == null) content else "$content. ${footer.said}"
    }
    val tap = spec?.base?.tap
    val onClick: (() -> Unit)? = remember(actions, name, tap, missing) {
        // A refresh would do nothing for a script that is gone or off. The Scripts screen is where it comes back.
        actions?.let { a -> if (missing != null) { { a.openScripts() } } else { { a.tap(name, tap ?: NO_TAP) } } }
    }
    val onLongClick: (() -> Unit)? = remember(actions) { actions?.let { a -> { a.longPress() } } }
    val onTap: ((WidgetTap) -> Unit)? = remember(actions, name) { actions?.let { a -> { t: WidgetTap -> a.tap(name, t) } } }
    WidgetCard(
        title = title,
        height = height,
        spec = spec,
        note = note,
        description = description,
        modifier = Modifier.testTag("widget:$name"),
        failure = failure,
        footer = footer,
        onClick = onClick,
        // With no action of its own, a tap on the card refreshes it.
        onClickLabel = when {
            missing != null -> OPEN_SCRIPTS
            tap == null -> "refresh"
            else -> tap.label
        },
        onLongClick = onLongClick,
        onTap = onTap,
    )
}

/** The Status card's pure part: what it draws for a status line. */
internal object StatusCards {
    /** What the card says while its script has set no status line. */
    const val NO_LINE = "No status line yet"

    /**
     * The stat card of [line], under [title]: the line's text is the value, with its bar and its colour.
     * Null with no line, or with a line that has no text. The card then says [NO_LINE].
     */
    fun spec(title: String, line: StatusLine?): WidgetSpec.Stat? {
        // The value is one line on the card, so a line break becomes a space.
        val text = line?.text?.lineSequence()?.map { it.trim() }?.filter { it.isNotEmpty() }?.joinToString(" ")
        if (line == null || text.isNullOrEmpty()) return null
        return WidgetSpec.Stat(
            base = WidgetBase(title = title, color = line.colour),
            value = text,
            progress = line.progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f),
        )
    }
}

/** The shortest time between two questions of a Status card about its line's `refresh`. A guard: the answer is never shorter. */
private const val MIN_ASK_MS = 1_000L

/**
 * [name]'s status line as [feed] has it, or null when the script has set none. The view model reads
 * the lines once for the pinned header, the Scripts screen and every card. A card is drawn again
 * only when its own line changes.
 */
@Composable
private fun statusLine(feed: WidgetFeed?, name: String): StatusLine? {
    if (feed == null) return null
    val changes = remember(feed, name) { feed.lines.map { all -> all.firstOrNull { it.name == name } }.distinctUntilChanged() }
    val line by changes.collectAsStateWithLifecycle(initialValue = remember(feed, name) { feed.lines.value.firstOrNull { it.name == name } })
    return line
}

/**
 * The Status card of the script [name]: its status line (`status(text, opts)`) as a stat card.
 * - The text is the value. The line's progress is the bar, in the line's colour.
 * - It needs no run and no access item. The line is stored whether or not a card shows it.
 * - A tap runs the script, as a tap on its pinned line does.
 * - A card whose script is gone or off says so ([scriptNote]). A tap on it opens the Scripts screen.
 * - The line's own `refresh` counts while the card is in view, as it does for a pinned line. The
 *   view model keeps one time of the last run for both, so a script that is pinned and has a card
 *   runs once in each period. That time outlives the card: turning the page away and back starts
 *   no extra run.
 * - The card itself reads nothing from the database. Its line and its script's title come from the
 *   view model ([feed]), which reads them for the pinned header anyway. So a new log line costs a
 *   card nothing, in view or not. A card with no feed stays empty.
 */
@Composable
private fun StatusWidget(name: String, height: Dp, actions: WidgetCardActions?, feed: WidgetFeed?) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val known = knownScript(feed, name)
    // The script is gone or off: the card says so, and asks for no runs.
    val missing = scriptNote(known.read, known.script, ownWidget = false)
    // The line counts once the scripts are read, and only for a script that can set it.
    val line = statusLine(feed, name).takeIf { known.read && missing == null }

    // `refresh` in the line's options: run again that often, only while in view.
    val every = line?.refresh
    if (actions != null && every != null && LocalWidgetsShown.current) LaunchedEffect(name, actions, lifecycle, every) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // The view model runs the script if it is due, and says how long to wait before the next question.
            while (true) delay(actions.refreshStatus(name, every).coerceAtLeast(MIN_ASK_MS))
        }
    }

    val title = known.script?.title ?: name
    val spec = remember(title, line) { StatusCards.spec(title, line) }
    val note = when {
        // Until the view model has read the scripts and the lines, the card stays empty. It does not say "no status line".
        !known.read -> CardNote.WAITING
        missing != null -> missing
        spec == null -> CardNote.NO_STATUS
        else -> null
    }
    val description = remember(title, spec, note) {
        when {
            // A stat's words have no time in them, so the moment and the zone don't matter.
            spec != null -> WidgetWords.description(title, spec, 0L, ZoneId.systemDefault())
            note == CardNote.NO_STATUS -> "$title: ${StatusCards.NO_LINE}"
            note == CardNote.REMOVED -> "$title: ${WidgetWords.REMOVED}"
            note == CardNote.OFF -> "$title: ${WidgetWords.OFF}"
            else -> title
        }
    }
    val onClick: (() -> Unit)? = remember(actions, name, missing) {
        // A script that is gone or off can't be run from here. The Scripts screen is where it comes back.
        actions?.let { a -> if (missing != null) { { a.openScripts() } } else { { a.runStatus(name) } } }
    }
    val onLongClick: (() -> Unit)? = remember(actions) { actions?.let { a -> { a.longPress() } } }
    WidgetCard(
        title = title,
        height = height,
        spec = spec,
        note = note,
        description = description,
        modifier = Modifier.testTag("widget:status:$name"),
        onClick = onClick,
        onClickLabel = if (missing != null) OPEN_SCRIPTS else "run",
        onLongClick = onLongClick,
    )
}

/** The Battery and network card's pure parts: what it draws and says for the device's [Status]. */
internal object BatteryCards {
    /** At this level and under it, the ring is in the "bad" colour, as the header's battery is. */
    const val LOW = 15

    /**
     * The network in words. [network] is [Status.network]: a kind (`wifi`, `cell`, `eth`, `vpn`,
     * `net`), with `?` after it while Android has not found the internet through it, or `offline`.
     * With [short], such a network is "No internet" alone, to fit beside the ring.
     */
    fun network(network: String, short: Boolean = false): String {
        val kind = network.trim().lowercase()
        val word = when (kind.removeSuffix("?")) {
            "wifi" -> "Wi-Fi"
            "cell" -> "Mobile data"
            "eth" -> "Ethernet"
            "vpn" -> "VPN"
            "offline", "" -> "Offline"
            else -> "Online"
        }
        return when {
            !kind.endsWith("?") || word == "Offline" -> word
            short -> "No internet"
            else -> "$word, no internet"
        }
    }

    /** The level, 0 to 100. Null when the phone does not say. */
    private fun level(status: Status): Int? = status.batteryPct?.coerceIn(0, 100)

    /**
     * The card's title says whether the phone is on power, because a line beside the ring has no room
     * for that and the network: "Charging", "Charged" at 100%, or "Battery".
     */
    fun title(status: Status): String = when {
        !status.charging -> "Battery"
        (level(status) ?: 0) >= 100 -> "Charged"
        else -> "Charging"
    }

    /**
     * The card: the level as a ring with the percent, and the network as its label.
     * - The ring stands still, also while charging. A wave would say "working".
     * - The colour says what a 1×1 card has no words for: good while on power, bad when low.
     */
    fun spec(status: Status): WidgetSpec.Progress {
        val level = level(status)
        val colour = when {
            level == null -> StyledText.Colour.MUTED
            status.charging -> StyledText.Colour.GOOD
            level <= LOW -> StyledText.Colour.BAD
            else -> null
        }
        return WidgetSpec.Progress(
            base = WidgetBase(title = title(status), color = colour),
            value = (level ?: 0).toFloat(),
            max = 100f,
            text = level?.let { "$it%" } ?: "–",
            label = network(status.network, short = true),
            // A ring in every box. Without this a wide box draws a bar.
            style = "ring",
        )
    }

    /** The whole card for a screen reader: "Battery: 82%, charging, Wi-Fi". */
    fun said(status: Status): String {
        val level = level(status)
        val power = when {
            !status.charging -> null
            (level ?: 0) >= 100 -> "charged"
            else -> "charging"
        }
        return "Battery: " + listOfNotNull(level?.let { "$it%" } ?: "level unknown", power, network(status.network)).joinToString(", ")
    }
}

/**
 * The built-in Battery and network card. Kolahal fills it from [DeviceStatus], the flow the header
 * reads, so it is right when you look and needs no script and no run. A tap opens Android's battery
 * screen, as the header's battery does.
 */
@Composable
private fun BatteryWidget(height: Dp, actions: WidgetCardActions?) {
    val context = LocalContext.current.applicationContext
    val device = remember(context) { DeviceStatus.get(context) }
    val status by device.status.collectAsStateWithLifecycle()
    // The next alarm is in the status too. The card does not show it, so it is no reason to draw again.
    val shown = remember(status.batteryPct, status.charging, status.network) { status.copy(nextAlarm = null) }
    val spec = remember(shown) { BatteryCards.spec(shown) }
    val description = remember(shown) { BatteryCards.said(shown) }
    val onClick: (() -> Unit)? = remember(actions) { actions?.let { a -> { a.openBattery() } } }
    val onLongClick: (() -> Unit)? = remember(actions) { actions?.let { a -> { a.longPress() } } }
    WidgetCard(
        title = BatteryCards.title(shown),
        height = height,
        spec = spec,
        note = null,
        description = description,
        modifier = Modifier.testTag("widget:${WidgetSource.builtin(WidgetSource.BATTERY)}"),
        onClick = onClick,
        onClickLabel = "battery settings",
        onLongClick = onLongClick,
    )
}
