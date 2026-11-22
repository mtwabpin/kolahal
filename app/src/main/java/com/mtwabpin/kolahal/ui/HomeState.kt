/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.terminal.HeaderTarget
import com.mtwabpin.kolahal.terminal.KeyBarKey
import com.mtwabpin.kolahal.terminal.LogAction
import com.mtwabpin.kolahal.terminal.LogRow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * How home is drawn (`home.style`): [GUI] as text, chips and cards; [TTY] on a strict character
 * grid, like a terminal: home's log page and its parts, but for the ones [TtyParts] leaves to the
 * GUI. The widgets page stays as it is.
 */
internal enum class HomeStyle {
    GUI, TTY;

    companion object {
        /** The style `home.style`'s [value] names; the GUI for anything else. */
        fun of(value: String): HomeStyle = if (value == "tty") TTY else GUI
    }
}

/**
 * Which of home's parts are drawn on the character grid. In the [HomeStyle.TTY] style, each is set
 * by its own key (`tty.suggestions`, `tty.selector`, `tty.keybar`, `tty.quick-actions`, `tty.menu`):
 * `tty` puts it on the grid; `gui`, the default, draws it with the GUI's own composable, in the place
 * the grid has for it. In the [HomeStyle.GUI] style no part is on the grid ([NONE]).
 */
@Immutable
internal data class TtyParts(
    val suggestions: Boolean = false,
    val selector: Boolean = false,
    val keyBar: Boolean = false,
    val quickActions: Boolean = false,
    /** The menu a long press opens (on a line, an app or the pinned header), and the `script secret` box. */
    val menu: Boolean = false,
) {
    companion object {
        val NONE = TtyParts()

        /** The parts [style] draws on the grid, where [value] reads a key's value (`Config.get`). */
        fun of(style: HomeStyle, value: (ConfigKey<String>) -> String): TtyParts {
            if (style != HomeStyle.TTY) return NONE
            fun grid(key: ConfigKey<String>) = value(key) == "tty"
            return TtyParts(
                suggestions = grid(Keys.TTY_SUGGESTIONS),
                selector = grid(Keys.TTY_SELECTOR),
                keyBar = grid(Keys.TTY_KEYBAR),
                quickActions = grid(Keys.TTY_QUICK_ACTIONS),
                menu = grid(Keys.TTY_MENU),
            )
        }
    }
}

/**
 * Draws home's log in place of the GUI's list: the log, or a search's [results] in its place when
 * there are any. The lists' modifiers come from home's body: [log] (the "log" tag, the selector's
 * fade, pinch to zoom, taps on empty space) and [found] (the fade). [deleteRight]: a swipe right
 * deletes a line (`widgets.page right`).
 */
internal typealias LogArea = @Composable (
    results: com.mtwabpin.kolahal.terminal.TerminalViewModel.LogSearch?,
    deleteRight: Boolean,
    log: androidx.compose.ui.Modifier,
    found: androidx.compose.ui.Modifier,
) -> Unit

/**
 * Which long lines are open (`log.fold`), by entry id. It sits above home's body, so a line stays
 * open when it is scrolled away and back, and survives the app being stopped.
 */
@Stable
internal class FoldState(open: Set<Long> = emptySet()) {
    private var ids by mutableStateOf(open)

    fun isOpen(id: Long): Boolean = id in ids

    /** Opens the line [id] if it is folded, and folds it if it is open. */
    fun toggle(id: Long) {
        ids = if (id in ids) ids - id else ids + id
    }

    companion object {
        val Saver: Saver<FoldState, Any> = listSaver(
            save = { it.ids.toList() },
            restore = { FoldState(it.toSet()) },
        )
    }
}

@Composable
internal fun rememberFoldState(): FoldState = rememberSaveable(saver = FoldState.Saver) { FoldState() }

/**
 * What home keeps while it is on screen, whichever body draws it: where the log is scrolled, which
 * lines are open, the prompt's focus, the open long-press menu, and the "Copied" notice after a
 * swipe copy.
 */
@Stable
internal class HomeState(
    val scroll: LogScroll,
    val folds: FoldState,
    private val scope: CoroutineScope,
) {
    val focus = FocusRequester()

    /** Whether the prompt has the focus. */
    var promptFocused by mutableStateOf(false)

    /** The long-press menu that is open, if any. */
    var menu by mutableStateOf<ActionMenu?>(null)

    /** Counts swipe copies. Each one shows "Copied" for [COPIED_MS]. */
    var copies by mutableIntStateOf(0)

    /** Whether "Copied" shows, where Undo shows after a delete. */
    var copiedShown by mutableStateOf(false)

    /**
     * Opens a menu titled [title]. The menu's actions ask the system for an app's shortcuts, so
     * [build] runs off the main thread, and the menu shows when they are ready.
     */
    fun showMenu(
        title: String,
        id: Long?,
        url: String? = null,
        detail: String? = null,
        build: () -> List<LogAction>
    ) {
        scope.launch {
            menu = ActionMenu(
                title,
                withContext(Dispatchers.IO) { build() },
                id,
                url = url,
                detail = detail
            )
        }
    }
}

/**
 * Home's [HomeState], with the log's scroll effects ([rememberLogScroll]). [rows] are the log's
 * rows, newest first.
 */
@Composable
internal fun rememberHomeState(
    rows: List<LogRow>,
    jumpTo: Long?,
    onJumpDone: () -> Unit,
    onAtNewest: () -> Unit,
    onLoadOlder: () -> Unit,
): HomeState {
    val scroll = rememberLogScroll(rows, jumpTo, onJumpDone, onAtNewest, onLoadOlder)
    val folds = rememberFoldState()
    val scope = rememberCoroutineScope()
    return remember(scroll, folds, scope) { HomeState(scroll, folds, scope) }
}

/**
 * The calls home's body makes into the view model, one by one, so no body takes the whole view
 * model. [TerminalScreen] makes them once.
 */
@Immutable
internal class HomeCalls(
    val onHeaderTap: (HeaderTarget) -> Unit,
    val onProfileTap: (Profile) -> Unit,
    val onGesture: (ConfigKey.Text) -> Unit,
    val onMediaToggle: () -> Unit,
    val onMediaNext: () -> Unit,
    val onPinTap: (String) -> Unit,
    val onPinLongPress: (String) -> Unit,
    val onMediaLongPress: (NowPlaying) -> Unit,
    val onRefreshPinned: () -> Unit,
    val apps: (LogEntry) -> List<AppEntry>,
    val onTap: (LogEntry) -> Unit,
    val onLineLongPress: (LogEntry) -> Unit,
    val onShowInLog: (LogEntry) -> Unit,
    val onAppTap: (AppEntry) -> Unit,
    val onAppLongPress: (AppEntry) -> Unit,
    val onSwipeCopy: (LogEntry) -> Unit,
    val onSwipeDelete: (LogEntry) -> Unit,
    val onAction: (LogEntry, Int) -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onLogWidth: (Int) -> Unit,
    val onSelectorArea: (Rect) -> Unit,
    val onShowHidden: () -> Unit,
    val onClearFilter: () -> Unit,
    val onUndo: () -> Unit,
    val onRefreshIsHome: () -> Unit,
    val onKeyBar: (KeyBarKey) -> Unit,
    val onInsert: (String) -> Unit,
    val onSaveFontSize: (Int) -> Unit,
    /** Puts the focus in the prompt and brings up the keyboard. */
    val onFocusPrompt: () -> Unit,
)
