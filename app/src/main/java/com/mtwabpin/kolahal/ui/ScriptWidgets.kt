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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.terminal.WidgetAge
import com.mtwabpin.kolahal.terminal.WidgetCardActions
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetSizes
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetState
import com.mtwabpin.kolahal.widgets.WidgetStates
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import java.time.ZoneId

/**
 * True while the widgets page is the page in view: home's pager has settled on it. Home keeps the
 * page composed beside the log, so "composed" does not mean "seen". Things that move (the loading
 * shape, a wave, a countdown's seconds) and refresh requests run only while this is true.
 */
val LocalWidgetsShown = compositionLocalOf { false }

/** What a card shows when it has no content to draw. */
internal enum class CardNote {
    /** The stored state is not read yet. The card stays empty for a moment. */
    WAITING,

    /** Nothing is stored yet, and a run is on its way. */
    LOADING,

    /** The script showed nothing, or cleared its widget. */
    NOTHING,

    /** The stored content can't be read, or nothing can fill this widget. */
    UNAVAILABLE,
}

/**
 * The card of a script widget, [height] tall and as wide as it is given. Call it inside [GuiTheme].
 * - It has its own opaque surface, so its text reads over any wallpaper.
 * - It draws [spec] for the box it gets ([WidgetSizes.box]): large has the title in a row on top,
 *   wide has the badge at its start, small has the content only.
 * - With a [note], it shows that and not the content.
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
                CardLayout(title, spec, note, box, onTap)
            }
        }
    }
}

/** The title, the badge and the content, placed for [box]. */
@Composable
private fun CardLayout(title: String, spec: WidgetSpec?, note: CardNote?, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
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
            Box(Modifier.weight(1f).fillMaxWidth()) { CardBody(spec, note, box, onTap) }
        }
        WidgetSizes.Box.WIDE -> Row(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                IconBadge(icon, colour, badgeShape(spec), 40.dp)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f).fillMaxHeight()) {
                Text(title, style = type.labelMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Box(Modifier.weight(1f).fillMaxWidth()) { CardBody(spec, note, box, onTap) }
            }
        }
        // No room for a title: the content alone. A screen reader still hears the title.
        WidgetSizes.Box.SMALL -> Box(Modifier.fillMaxSize().padding(8.dp)) { CardBody(spec, note, box, onTap) }
    }
}

/** The content, or what the card says without any. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CardBody(spec: WidgetSpec?, note: CardNote?, box: WidgetSizes.Box, onTap: ((WidgetTap) -> Unit)?) {
    val small = box == WidgetSizes.Box.SMALL
    when {
        note == CardNote.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // The shape changes form for as long as it is drawn. So it is drawn only while the page is in view.
            if (LocalWidgetsShown.current) LoadingIndicator(Modifier.size(if (box == WidgetSizes.Box.LARGE) 48.dp else 36.dp))
        }
        note == CardNote.NOTHING -> QuietLine(WidgetWords.NOTHING, small)
        note == CardNote.UNAVAILABLE -> QuietLine(WidgetWords.UNAVAILABLE, small)
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
 * - Any other source (a Status card, a built-in widget) says "widget unavailable" for now.
 * - [actions] takes taps, the long-press and refresh requests. With null the card takes no touches
 *   and asks for no runs: this is the Widgets screen.
 * It brings its own [GuiTheme], because the widgets page is drawn in the terminal's styles.
 */
@Composable
internal fun SourceWidget(w: PlacedWidget, height: Dp, actions: WidgetCardActions?) {
    val name = WidgetSource.scriptName(w.source)
    GuiTheme {
        if (name != null) {
            ScriptWidget(name, height, actions)
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

/** The card of the script [name]'s own widget. */
@Composable
private fun ScriptWidget(name: String, height: Dp, actions: WidgetCardActions?) {
    val context = LocalContext.current.applicationContext
    // The counter moves when any widget's state changes. It is collected only while home is on screen.
    val version by WidgetStates.version.collectAsStateWithLifecycle()
    var state by remember(name) { mutableStateOf<WidgetState?>(null) }
    LaunchedEffect(name, version) {
        val read = withContext(Dispatchers.IO) { WidgetStates.read(context, name) }
        // An equal state keeps the one in use, so the card is not drawn again.
        if (read != state) state = read
    }
    val shown = LocalWidgetsShown.current
    if (actions != null && shown) {
        val lifecycle = LocalLifecycleOwner.current.lifecycle
        // The page came into view, or home came back with the page in view: run the script once if
        // it never filled the widget, or if its content is old.
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

    val s = state
    val spec = s?.spec
    // Nothing stored and no run ended yet: the widget was just placed, and its first run is on its way.
    val fresh = s != null && !s.hasContent && s.setAt == 0L && s.triedAt == 0L
    var gaveUp by remember(name) { mutableStateOf(false) }
    if (fresh && shown && !gaveUp) LaunchedEffect(name) {
        delay(LOADING_MS)
        gaveUp = true
    }
    val note = when {
        s == null -> CardNote.WAITING
        spec != null -> null
        // Something is stored, and it can't be read.
        s.hasContent -> CardNote.UNAVAILABLE
        fresh && !gaveUp -> CardNote.LOADING
        else -> CardNote.NOTHING
    }
    val title = spec?.base?.title ?: name
    // Only a countdown's words change with the time, by the minute.
    val now = if (spec is WidgetSpec.Countdown) rememberNow().first.time / 1000 else 0L
    val description = remember(title, spec, note, now) {
        when {
            spec != null -> WidgetWords.description(title, spec, now, ZoneId.systemDefault())
            note == CardNote.LOADING -> "$title: loading"
            note == CardNote.NOTHING -> "$title: ${WidgetWords.NOTHING}"
            note == CardNote.UNAVAILABLE -> "$title: ${WidgetWords.UNAVAILABLE}"
            else -> title
        }
    }
    val tap = spec?.base?.tap
    val onClick: (() -> Unit)? = remember(actions, name, tap) { actions?.let { a -> { a.tap(name, tap ?: NO_TAP) } } }
    val onLongClick: (() -> Unit)? = remember(actions) { actions?.let { a -> { a.longPress() } } }
    val onTap: ((WidgetTap) -> Unit)? = remember(actions, name) { actions?.let { a -> { t: WidgetTap -> a.tap(name, t) } } }
    WidgetCard(
        title = title,
        height = height,
        spec = spec,
        note = note,
        description = description,
        modifier = Modifier.testTag("widget:$name"),
        onClick = onClick,
        // With no action of its own, a tap on the card refreshes it.
        onClickLabel = if (tap == null) "refresh" else tap.label,
        onLongClick = onLongClick,
        onTap = onTap,
    )
}
