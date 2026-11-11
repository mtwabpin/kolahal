package com.mtwabpin.kolahal.ui

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mtwabpin.kolahal.terminal.SetupChecks
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.terminal.LogAction
import com.mtwabpin.kolahal.terminal.LogRow
import com.mtwabpin.kolahal.terminal.ScriptActions
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.UiEvent
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** The text the prompt shows when Settings previews home while typing. One letter matches many items. */
private const val PREVIEW_INPUT = "s"

/**
 * selector.rail: the fluid full-list app selector on an edge of home. It shows only while the
 * keyboard is down, over the log and over the widgets page.
 * When [showList] is true, the list is open so its rows are visible.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HomeAppSelector(
    vm: TerminalViewModel,
    onLongPress: (com.mtwabpin.kolahal.apps.AppEntry) -> Unit,
    showList: Boolean = false,
    /** Set while the list is open, so the page behind fades out. Null: a scrim dims the page instead. */
    fade: androidx.compose.runtime.MutableFloatState? = null,
    /** Where the strip goes, if only in part of the height ([AppSelector]'s stripArea). The list uses the whole height. */
    stripArea: ClosedFloatingPointRange<Float>? = null,
) {
    val configVersion by vm.config.version.collectAsStateWithLifecycle()
    val config = vm.config
    val railOn = remember(configVersion) { config[Keys.SELECTOR_RAIL] }
    if (!railOn) return
    val apps by vm.apps.collectAsStateWithLifecycle()
    val prefsVersion by vm.appPrefsVersion.collectAsStateWithLifecycle()
    val pinnedKeys = remember(apps, prefsVersion) { vm.pinnedKeys() }
    AppSelector(
        apps = apps,
        pinned = pinnedKeys,
        // While the keyboard is up, the selector is only hidden, not removed, so its list stays built for when the keyboard goes down.
        active = !WindowInsets.isImeVisible,
        onLaunch = vm::launchApp,
        onLongPress = onLongPress,
        heightDp = remember(configVersion) { config[Keys.SELECTOR_HEIGHT] },
        showHandle = remember(configVersion) { config[Keys.SELECTOR_HANDLE] },
        letters = remember(configVersion) { config[Keys.SELECTOR_LETTERS] },
        left = remember(configVersion) { config[Keys.SELECTOR_SIDE] == "left" },
        position = remember(configVersion) { config[Keys.SELECTOR_POSITION] },
        actions = remember(configVersion) { config[Keys.SELECTOR_ACTIONS] },
        icons = remember(configVersion) { config[Keys.SELECTOR_ICONS] },
        rowHeightDp = remember(configVersion) { config[Keys.SELECTOR_ROW_HEIGHT] },
        showList = showList,
        aimDp = remember(configVersion) { config[Keys.SELECTOR_AIM] },
        aimSpeed = remember(configVersion) { config[Keys.SELECTOR_AIM_SPEED] },
        browse = remember(configVersion) { config[Keys.SELECTOR_BROWSE] },
        filter = remember(configVersion) { config[Keys.SELECTOR_FILTER] },
        systemFont = remember(configVersion) { config[Keys.SELECTOR_SYSTEM_FONT] },
        fade = fade,
        stripArea = stripArea,
    )
}

/** Whether the terminal page is showing. False while the widgets page beside it is showing. */
val LocalTerminalShown = androidx.compose.runtime.compositionLocalOf { true }

/**
 * Home. With [preview], it is drawn under Settings while the user changes a look setting
 * (TerminalViewModel.Peek). A preview takes no focus, keyboard or events. With `typing`, it looks as
 * if [PREVIEW_INPUT] were typed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(vm: TerminalViewModel, onPickFolder: () -> Unit, preview: TerminalViewModel.Peek? = null) {
    // An install or update may change icons, so suggestions.icons loads the icons of those apps again.
    val appsRepo = com.mtwabpin.kolahal.apps.AppRepository.get(androidx.compose.ui.platform.LocalContext.current)
    val packageChanges by appsRepo.packageChanges.collectAsStateWithLifecycle()
    if (preview == null) LaunchedEffect(packageChanges) { AppIcons.sync(packageChanges, appsRepo.takeChanged()) }
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val unlocks by vm.unlocksToday.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val pinnedLines by vm.pinned.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val configVersion by vm.config.version.collectAsStateWithLifecycle()
    val config = vm.config
    val listState = rememberLazyListState()
    val rows by vm.rows.collectAsStateWithLifecycle()
    val reversedRows = remember(rows) { rows.asReversed() }
    var followNewest by remember { mutableStateOf(true) }
    var promptFocused by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val hasHardwareKeyboard = LocalConfiguration.current.keyboard != Configuration.KEYBOARD_NOKEYS

    // Read through updated state, so a keyboard attached later (configChanges=keyboard) is seen.
    val currentKeyboard by rememberUpdatedState(keyboard)
    val currentHasHardwareKeyboard by rememberUpdatedState(hasHardwareKeyboard)
    if (preview == null) LaunchedEffect(Unit) {
        // Home was pressed on another page: scroll and focus now that the terminal is shown.
        // Scrolls are requested for the list's next layout, never forced inside this one.
        // scrollToItem measures at once, which can happen inside a layout pass under the test clock.
        vm.consumeArrival()?.let { a ->
            followNewest = true
            listState.requestScrollToItem(0)
            if (a.focus) {
                androidx.compose.runtime.withFrameNanos { } // the prompt's focus target is attached after the first frame
                runCatching { focus.requestFocus() }
                if (!currentHasHardwareKeyboard) currentKeyboard?.show()
            }
        }
        vm.events.collect { e ->
            when (e) {
                UiEvent.ScrollToBottom -> { followNewest = true; listState.requestScrollToItem(0) }
                // MainActivity handles these, so they work from every page (setup, script manager).
                UiEvent.RequestNotificationPermission, UiEvent.RequestContactsPermission, UiEvent.PickContact, UiEvent.RequestCalendarPermission, UiEvent.RequestCallLogPermission,
                UiEvent.PickFont, UiEvent.PickBackupFolder -> Unit
                UiEvent.ShowQuick -> Unit // opened by the collector below, where the menu lives
                UiEvent.FocusPrompt -> {
                    runCatching { focus.requestFocus() }
                    if (!currentHasHardwareKeyboard) currentKeyboard?.show()
                }
                UiEvent.HideKeyboard -> currentKeyboard?.hide()
                UiEvent.PickScriptFolder -> onPickFolder()
            }
        }
    }
    // Follow the newest line (index 0 with reverseLayout) unless the user scrolled up.
    // Only user scrolls change `followNewest`. Inserting items shifts the anchor, and that must not stop following.
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.isScrollInProgress to
                (listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 48)
        }.collect { (scrolling, atBottom) ->
            // Also check when the list has stopped at the bottom. A fling that stops at the newest
            // line can end in the same frame it arrives, with no "scrolling and at bottom" state
            // between. Inserting items does not count as the list stopping.
            if (scrolling) followNewest = atBottom
            else if (atBottom) {
                followNewest = true
                // Back at the newest lines: older pages loaded while scrolling up can be dropped.
                vm.atNewest()
            }
        }
    }
    // Keyed on the newest *row* (rows are built after entries), so the scroll targets the new line.
    // Skip it while a finger or a fling is still scrolling: a forced scroll would yield to those too.
    LaunchedEffect(reversedRows.firstOrNull()?.key) {
        if (followNewest && !listState.isScrollInProgress) listState.requestScrollToItem(0)
    }
    // Near the oldest loaded line (the top of the reversed list), load the next page.
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 5
        }.collect { nearTop -> if (nearTop) vm.loadOlder() }
    }
    // Typing `/words` at the prompt searches: the matching lines replace the log until the search ends.
    val search by vm.search.collectAsStateWithLifecycle()
    // Not while the widgets page is showing: there, Back returns to the terminal.
    if (preview == null) BackHandler(enabled = search != null && LocalTerminalShown.current) { vm.escape() }
    val filter by vm.filter.collectAsStateWithLifecycle()
    val filterCount by vm.filterCount.collectAsStateWithLifecycle()
    // Back leaves `log only …`, the same way it leaves a search.
    if (preview == null) BackHandler(enabled = search == null && filter != null && LocalTerminalShown.current) { vm.filterLog(null) }
    // When a result is tapped: once its line is loaded, scroll to it (with a few newer lines below it) and highlight it briefly.
    val jumpTo by vm.jumpTo.collectAsStateWithLifecycle()
    var flash by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(jumpTo, reversedRows) {
        val id = jumpTo ?: return@LaunchedEffect
        val index = reversedRows.indexOfFirst { it is LogRow.Entry && it.entry.id == id }
        if (index < 0) return@LaunchedEffect
        // If it is one of the newest lines, keep following new ones.
        followNewest = index <= 3
        listState.requestScrollToItem((index - 3).coerceAtLeast(0))
        vm.jumpDone()
        flash = id
    }
    LaunchedEffect(flash) {
        if (flash != null) {
            kotlinx.coroutines.delay(2_500.milliseconds)
            flash = null
        }
    }
    // Keyboard-first: take focus at once when a physical keyboard is attached.
    if (preview == null) LaunchedEffect(hasHardwareKeyboard) {
        if (hasHardwareKeyboard) runCatching { focus.requestFocus() }
    }
    // The widgets page has no prompt, so the on-screen keyboard is hidden over it. The prompt keeps
    // the focus, so a hardware keyboard still types into it.
    val terminalShown = LocalTerminalShown.current
    val imeUp = WindowInsets.isImeVisible
    if (preview == null) LaunchedEffect(terminalShown, imeUp) {
        if (!terminalShown && imeUp) currentKeyboard?.hide()
    }

    // The prompt's text is read only where it shows ([PromptSuggestions], [Prompt]). If it were read
    // here, every key press would rebuild all of home.
    val hasInput by remember(preview?.typing) { derivedStateOf { preview?.typing == true || vm.input.text.isNotEmpty() } }
    val timeMode = remember(configVersion) {
        when (config[Keys.SHOW_TIMESTAMPS]) {
            "groups" -> TimeMode.GROUPS
            "off" -> TimeMode.OFF
            else -> TimeMode.COMMANDS
        }
    }
    val dimOld = remember(configVersion) { config[Keys.LOG_DIM] }
    var menu by remember { mutableStateOf<ActionMenu?>(null) }
    // Menus ask the system for an app's shortcuts, so they are built off the main thread and shown when ready.
    val menuScope = rememberCoroutineScope()
    fun showMenu(title: String, id: Long?, url: String? = null, detail: String? = null, build: () -> List<LogAction>) {
        menuScope.launch { menu = ActionMenu(title, kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { build() }, id, url = url, detail = detail) }
    }
    val clock24 = remember(configVersion) { config[Keys.CLOCK_24H] }
    if (preview == null) LaunchedEffect(Unit) {
        vm.events.collect { if (it == UiEvent.ShowQuick) menu = ActionMenu("Quick actions", vm.quickActions(), null, systemFont = true) }
    }
    menu?.let { ActionSheet(it, onDismiss = { menu = null }) }
    // `script secret <name> <NAME>` opens a masked input box for the value, over home.
    val secretPrompt by vm.scriptActions.secretPrompt.collectAsStateWithLifecycle()
    secretPrompt?.let { p -> TerminalSecretPrompt(vm, p) }
    val commandGap = remember(configVersion) { config[Keys.LOG_SPACING].dp }
    val itemGap = remember(configVersion) { config[Keys.LOG_ITEM_SPACING].dp }
    val showGutter = remember(configVersion) { config[Keys.LOG_GUTTER] }
    // Wrapped and detail lines are indented by the width of `$ ` (or `• `) in the log's font.
    // It is measured with a letter after it, because a trailing space alone may not count towards the width.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val indents = remember(Term.fontSize, Term.fontFamily, Term.sans, density) {
        fun width(prefix: String, style: androidx.compose.ui.text.TextStyle) = with(density) {
            (measurer.measure("${prefix}0", style).size.width - measurer.measure("0", style).size.width).toSp()
        }
        LineIndents(text = width("$ ", Term.text), code = width("$ ", Term.code), bullet = width("• ", Term.text))
    }
    val zone = rememberZoneChanges()
    val lineTimeFormat = remember(configVersion, zone) {
        com.mtwabpin.kolahal.terminal.lineTimeFormat(config[Keys.CLOCK_24H])
    }
    // The day dividers also depend on the time zone, so the rows are rebuilt when it changes, even if
    // the change happened while another screen was up. `told` is true only for a change after this
    // screen was built, because the count belongs to the app, not to this screen.
    val firstZone = remember { zone }
    if (preview == null) LaunchedEffect(zone) { vm.onZoneChanged(told = zone != firstZone) }
    // They also depend on the day: at midnight, today's first lines need a divider, and "Yesterday" moves.
    val today = rememberToday()
    if (preview == null) LaunchedEffect(today) { vm.onDayChanged(today) }
    val fontSizeToast = rememberFontSizeToast()
    // Made once. A new modifier on each rebuild of home would stop the log's list from skipping recomposition.
    val pinch = remember(vm, fontSizeToast) {
        Modifier.pinchToZoomFont(onChange = { Term.fontSize = it }, onEnd = { from, to -> vm.saveFontSize(to); fontSizeToast(from, to) })
    }
    val doubleTapOn = remember(configVersion) {
        config[Keys.GESTURE_DOUBLE_TAP].trim().let { it.isNotEmpty() && it != com.mtwabpin.kolahal.gesture.Gestures.NONE }
    }

    Column(
        Modifier
            .fillMaxSize()
            .page(wallpaper = remember(configVersion) { config[Keys.LOG_WALLPAPER] })
            // No top padding here: the header fills the band beside the camera.
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal))
            .navigationBarsPadding()
            .imePadding()
    ) {
        // A hardware keyboard already has these keys, so the bar follows the on-screen keyboard only.
        val typing = preview?.typing ?: (promptFocused && WindowInsets.isImeVisible)
        // While searching, the pinned header hides so the suggestions can use its space.
        val searching = typing || hasInput
        // With header.show off there is no header. The pinned header and the log start under the camera.
        val showHeader = remember(configVersion) { config[Keys.HEADER_SHOW] }
        if (showHeader) Header(
            status = status,
            unlocks = unlocks,
            profiles = profiles,
            todoCount = vm.todoCount.collectAsStateWithLifecycle().value,
            config = config,
            version = configVersion,
            onTarget = vm::onHeaderTap,
            onProfileTap = vm::onProfileTap,
            onSwipeDown = { vm.onGesture(Keys.GESTURE_SWIPE_DOWN) },
        ) else Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars.union(WindowInsets.displayCutout)))
        // What's playing and the pinned status lines, shown in turns.
        androidx.compose.animation.AnimatedVisibility(
            visible = !searching,
            enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Top) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Top) + androidx.compose.animation.fadeOut(),
        ) {
            PinnedHeader(
                nowPlaying = nowPlaying,
                lines = pinnedLines,
                config = config,
                version = configVersion,
                onMediaToggle = vm::mediaToggle,
                onMediaNext = vm::mediaNext,
                onPinTap = vm::onPinTap,
                onPinLongPress = { name -> showMenu("$name ▸ ${pinnedLines.firstOrNull { it.name == name }?.text.orEmpty()}", null) { vm.scriptActions.statusActions(name) } },
                onRefresh = vm::refreshPinned,
            )
        }
        val hiddenLines by vm.hiddenLines.collectAsStateWithLifecycle()
        // The log and the suggestions share the space the header, key bar and prompt leave. Those
        // are measured first, so a long suggestion list can't push the prompt off the screen.
        Column(Modifier.weight(1f).fillMaxWidth()) {
        Box(
            Modifier.weight(1f).fillMaxWidth().then(
                // Save the selector's area, so the selector on the widgets page can match it.
                if (preview == null) Modifier.onGloballyPositioned { vm.selectorArea = androidx.compose.ui.geometry.Rect(0f, it.positionInWindow().y, it.size.width.toFloat(), it.positionInWindow().y + it.size.height) }
                else Modifier
            )
        ) {
        // A tap on a link in a line's text (fmt.link) opens it as a tap on a linked line does.
        val linkOpener = remember(vm) { object : androidx.compose.ui.platform.UriHandler { override fun openUri(uri: String) = vm.openUrl(uri) } }
        val results = search?.takeIf { !it.isEmpty && preview == null }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalUriHandler provides linkOpener) {
        if (results != null) LogSearchResults(
            search = results,
            timeFormat = lineTimeFormat,
            gutter = showGutter,
            indents = indents,
            menuEntryId = menu?.entryId,
            apps = vm::appsOf,
            onOpen = vm::showInLog,
            onLongPress = { e -> showMenu(com.mtwabpin.kolahal.data.StyledText.plain(e.text), e.id, e.ctx.url, com.mtwabpin.kolahal.terminal.lineDetail(e, clock24)) { vm.actionsFor(e) } },
            onAppTap = vm::launchApp,
            onAppLongPress = { app -> showMenu(app.label, null) { vm.appActions(app) } },
            onSwipeCopy = vm::swipeCopy,
            onSwipeDelete = vm::swipeDelete,
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = 1f - vm.selectorFade.floatValue },
        ) else LazyColumn(
            state = listState,
            reverseLayout = true,
            modifier = Modifier
                .fillMaxSize()
                // While the app selector is open, the log fades out so the background shows behind the list.
                .graphicsLayer { alpha = 1f - vm.selectorFade.floatValue }
                .onSizeChanged { vm.onLogWidth(it.width) }
                .testTag("log")
                .then(pinch)
                // Taps that no log line used (empty space) bring up the keyboard. Double taps are
                // detected only when a double-tap gesture is set, because waiting for one delays every tap.
                .pointerInput(doubleTapOn) {
                    detectTapGestures(
                        onDoubleTap = if (doubleTapOn) { _ -> vm.onGesture(Keys.GESTURE_DOUBLE_TAP) } else null,
                    ) {
                        runCatching { focus.requestFocus() }
                        keyboard?.show()
                    }
                },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            if (reversedRows.isEmpty() && hiddenLines == 0) {
                // An empty log: what to do, in the text colour, then the other tips dim, one per line.
                item(key = "empty-hint") {
                    Column(Modifier.padding(vertical = 12.dp).testTag("empty-hint")) {
                        Text("Type an app name or a command", style = Term.text)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "help lists everything\n↑ recalls history\n/ searches the log\n" +
                                "Tap a line to use it, long-press for more\n⚙ settings",
                            style = Term.text.copy(color = Term.dim),
                        )
                    }
                }
            }
            items(reversedRows, key = { it.key }, contentType = { if (it is LogRow.Entry) it.entry.kind else it.javaClass }) { row ->
                when (row) {
                    is LogRow.Day -> DayDivider(row, today)
                    is LogRow.Unread -> UnreadDivider(row)
                    is LogRow.Entry -> LogLine(
                        row = row,
                        timeMode = timeMode,
                        timeFormat = lineTimeFormat,
                        dim = dimOld,
                        topGap = if (row.blockStart) commandGap else 0.dp,
                        itemGap = if (row.itemGap) itemGap else 0.dp,
                        gutter = showGutter,
                        indents = indents,
                        highlighted = menu?.entryId == row.entry.id || flash == row.entry.id,
                        apps = vm::appsOf,
                        onTap = vm::onTap,
                        onLongPress = { e -> showMenu(com.mtwabpin.kolahal.data.StyledText.plain(e.text), e.id, e.ctx.url, com.mtwabpin.kolahal.terminal.lineDetail(e, clock24)) { vm.actionsFor(e) } },
                        onAppTap = vm::launchApp,
                        onAppLongPress = { app -> showMenu(app.label, null) { vm.appActions(app) } },
                        onSwipeCopy = vm::swipeCopy,
                        onSwipeDelete = vm::swipeDelete,
                        onAction = vm::runLineAction,
                    )
                }
            }
            // log.fresh: older output is hidden, not deleted. The last item is the top of the reversed list.
            if (hiddenLines > 0) {
                item(key = "hidden-lines") {
                    Text(
                        "▴ $hiddenLines earlier lines · tap to show",
                        style = Term.text.copy(color = Term.dim),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClickLabel = "show earlier output") { vm.showHidden() }
                            .heightIn(min = 48.dp)
                            .wrapContentHeight(),
                    )
                }
            }
        }
        }
        // When scrolled away from the newest lines, a small chip above the prompt jumps back down with
        // one tap. It sits in the middle, so it covers little of a line, and its tap area is a full
        // 48dp tall though the chip itself is small.
        val awayFromBottom by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }
        androidx.compose.animation.AnimatedVisibility(
            visible = awayFromBottom && results == null,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).graphicsLayer { alpha = 1f - vm.selectorFade.floatValue },
        ) {
            val scope = rememberCoroutineScope()
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .testTag("jump-latest")
                    .heightIn(min = 48.dp)
                    .clickable(onClickLabel = "scroll to the bottom") { followNewest = true; scope.launch { listState.animateScrollToItem(0) } }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                // The chip: a soft pill, no border, lifted off the log by a shadow. Its colour is the
                // surface's, a touch toward the text colour, so it stands out on a dark theme too, where
                // the surface is close to the background. About 40 dp tall at font size 14.
                Box(
                    Modifier
                        .shadow(6.dp, RoundedCornerShape(50))
                        .background(lerp(Term.surface, Term.fg, 0.06f), RoundedCornerShape(50))
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    // In the system font: it is a button over the log, not a line of it. One size
                    // up from the log's text, so it is easy to see and to hit.
                    Text(
                        "↓ Scroll to bottom",
                        style = Term.text.copy(color = Term.prompt, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Default, fontSize = (Term.fontSize + 1).sp),
                    )
                }
            }
        }
        // [selector] selector.rail: fluid full-list app selector on an edge of the log, keyboard down only.
        HomeAppSelector(
            vm,
            onLongPress = { app -> showMenu(app.label, null) { vm.appActions(app) } },
            // While its row-height slider in Settings is dragged, open the list so its rows are visible.
            showList = preview?.key == Keys.SELECTOR_ROW_HEIGHT.key,
            fade = if (preview == null) vm.selectorFade else null,
        )
        // [/selector]
        }
        // Behind the build flag, so R8 removes the updater from the Play build.
        if (BuildConfig.SELF_UPDATE && preview == null) UpdateProgressLine()
        search?.let { LogSearchSummary(it) } ?: filter?.let { LogFilterBar(it, filterCount, onClear = { vm.filterLog(null) }) }
        // suggestions.auto: hidden on an idle home. Shown while typing, or whenever there is input
        // (so typing on a hardware keyboard still shows them).
        val suggestionsAuto = remember(configVersion) { config[Keys.SUGGESTIONS_AUTO] }
        val undoOffer by vm.undo.collectAsStateWithLifecycle()
        undoOffer?.let { offer ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().background(Term.surface).heightIn(min = 44.dp).padding(start = 12.dp),
            ) {
                Text(offer.text, style = Term.text.copy(color = Term.dim), modifier = Modifier.weight(1f))
                Text(
                    "Undo",
                    style = Term.text.copy(color = Term.prompt, fontWeight = FontWeight.Bold),
                    modifier = Modifier.clickable(onClick = vm::undo).heightIn(min = 44.dp).wrapContentHeight().padding(horizontal = 16.dp),
                )
            }
        }
        // [selector] suggestions.style: list (default, bottom-anchored above the prompt) or chips.
        PromptSuggestions(
            vm,
            previewTyping = preview?.typing == true,
            always = !suggestionsAuto || typing,
            configVersion = configVersion,
            onAppLongPress = { app -> showMenu(app.label, null) { vm.appActions(app) } },
        )
        // [/selector]
        }
        // keybar.auto: the bar shows only while typing, so an idle home shows more of the log.
        val keyBarAuto = remember(configVersion) { config[Keys.KEYBAR_AUTO] }
        val promptTypingHeight = remember(configVersion) { config[Keys.PROMPT_HEIGHT_TYPING].dp }
        val keyBarOn = remember(configVersion) { config[Keys.KEYBAR_SHOW] }
        // If Kolahal is not the home app, a band asks the user to pick it, unless a preview band uses
        // that place. The view model checks at each resume, so the band goes once Kolahal is picked.
        if (preview == null) {
            val context = LocalContext.current
            val isHome by vm.isHome.collectAsStateWithLifecycle()
            // Android's settings page for the home app: for Android 9, and when its dialog doesn't make
            // Kolahal home (a "no", or no dialog at all after "Don't ask again").
            val homeSettings = { runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } }
            val askHome = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode != Activity.RESULT_OK) homeSettings()
                vm.refreshIsHome()
            }
            DefaultHomeBand(!isHome && vm.livePreview == null) {
                SetupChecks.homeRoleRequest(context)?.let { askHome.launch(it) } ?: homeSettings()
            }
        }
        LivePreviewBand(if (preview == null) vm.livePreview else null)
        Prompt(
            vm = vm,
            focus = focus,
            previewText = if (preview?.typing == true) PREVIEW_INPUT else null,
            imeSuggestions = remember(configVersion) { config[Keys.IME_SUGGESTIONS] },
            onFocus = { promptFocused = it },
            // Same prompt.height while typing or not, so the line doesn't jump under the finger when the keyboard opens.
            height = promptTypingHeight,
            keyBarOn = keyBarOn,
            onQuick = { vm.openQuick() },
            onToggleKeyBar = {
                config.set(Keys.KEYBAR_SHOW, if (keyBarOn) "off" else "on")
                // With keybar.auto the bar shows only while typing, so turning it on also brings up the keyboard.
                if (!keyBarOn && keyBarAuto && !typing) {
                    runCatching { focus.requestFocus() }
                    keyboard?.show()
                }
            },
            onTapPrompt = {
                runCatching { focus.requestFocus() }
                keyboard?.show()
            },
        )
        // Below the prompt, so it sits on the keyboard like an extra row of keys, and the best
        // suggestion stays right above the typed text. It slides up into place and back down, like
        // the pinned header expands from the top. The prompt and the log follow the size change smoothly.
        androidx.compose.animation.AnimatedVisibility(
            visible = keyBarOn && (!keyBarAuto || typing),
            enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom) +
                androidx.compose.animation.slideInVertically { it },
            exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom) +
                androidx.compose.animation.slideOutVertically { it },
        ) {
            KeyBar(
                specs = remember(configVersion) { listOf(config[Keys.KEYBAR], config[Keys.KEYBAR2]) },
                rowHeight = remember(configVersion) { config[Keys.KEYBAR_HEIGHT].dp },
                onKey = { key ->
                    if (key == KeyBarKey.KBD) {
                        runCatching { focus.requestFocus() }
                        keyboard?.show()
                    } else {
                        vm.onKeyBar(key)
                    }
                },
                onText = vm::insert,
            )
        }
    }
}

/**
 * The suggestions for the prompt's text, shown while typing or when [always] is true. The text is
 * read here, so a key press rebuilds only this, not all of home.
 */
@Composable
private fun PromptSuggestions(
    vm: TerminalViewModel,
    previewTyping: Boolean,
    always: Boolean,
    configVersion: Int,
    onAppLongPress: (com.mtwabpin.kolahal.apps.AppEntry) -> Unit,
) {
    val config = vm.config
    val apps by vm.apps.collectAsStateWithLifecycle()
    val maxSuggestions = remember(configVersion) { config[Keys.SUGGESTIONS] }
    val commandNames by vm.commandNames.collectAsStateWithLifecycle()
    val prefsVersion by vm.appPrefsVersion.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val inputText = if (previewTyping) PREVIEW_INPUT else vm.input.text
    // Hidden, they aren't worked out: an idle home would rank every app again on each return.
    if (!always && inputText.isEmpty()) return
    val suggestions = remember(inputText, apps, maxSuggestions, commandNames, prefsVersion, contacts) {
        vm.suggestions(inputText).take(maxSuggestions)
    }
    val showIcons = remember(configVersion) { config[Keys.SUGGEST_ICONS] }
    if (remember(configVersion) { config[Keys.SUGGESTIONS_STYLE] } == "chips") {
        SuggestionStrip(suggestions, rows = remember(configVersion) { config[Keys.SUGGESTION_ROWS] }, onPick = vm::onSuggestion, icons = showIcons)
    } else {
        SuggestionList(
            suggestions,
            onPick = vm::onSuggestion,
            onAppLongPress = onAppLongPress,
            icons = showIcons,
            wrapHelp = remember(configVersion) { config[Keys.SUGGESTIONS_WRAP] },
        )
    }
}

/** Shows a pinch's new font size in a toast. A new pinch replaces the toast instead of queueing another. */
@Composable
private fun rememberFontSizeToast(): (from: Int, to: Int) -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val shown = remember { arrayOfNulls<Toast>(1) }
    return remember(context) {
        { from, to ->
            shown[0]?.cancel()
            shown[0] = Toast.makeText(context, "Font size $to (was $from)", Toast.LENGTH_SHORT).also { it.show() }
        }
    }
}

/**
 * A two-finger pinch changes the terminal font size. It runs in the Initial pass and consumes only
 * multi-touch events, so one-finger scrolls and taps still reach the list. When the fingers lift,
 * [onEnd] gets the size before and after, unless the size ended where it started.
 */
private fun Modifier.pinchToZoomFont(onChange: (Int) -> Unit, onEnd: (from: Int, to: Int) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val before = Term.fontSize
        var startDistance = 0f
        var startSize = Term.fontSize
        var changed = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val distance = (pressed[0].position - pressed[1].position).getDistance()
                if (startDistance == 0f) {
                    startDistance = distance
                    startSize = Term.fontSize
                } else if (startDistance > 0f) {
                    val size = (startSize * distance / startDistance).roundToInt().coerceIn(10, 24)
                    if (size != Term.fontSize) {
                        onChange(size)
                        changed = true
                    }
                }
                event.changes.forEach { it.consume() }
            }
        } while (event.changes.any { it.pressed })
        if (changed && Term.fontSize != before) onEnd(before, Term.fontSize)
    }
}

/** The box that `script secret` opens. It closes if the script or its `@secret` line is gone. */
@Composable
private fun TerminalSecretPrompt(vm: TerminalViewModel, prompt: ScriptActions.SecretPrompt) {
    val scripts by vm.scriptList.collectAsStateWithLifecycle()
    val secret = remember(scripts, prompt) {
        scripts.firstOrNull { it.name == prompt.script }?.let { s -> ScriptMeta.secrets(s.code).firstOrNull { it.name == prompt.name } }
    }
    if (secret == null) {
        LaunchedEffect(prompt) { vm.scriptActions.closeSecretPrompt() }
        return
    }
    val isSet by produceState(false, prompt) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { vm.scriptActions.secretStore.isSet(prompt.script, secret) }
    }
    GuiTheme {
        SecretDialog(prompt.script, secret, isSet, onDismiss = vm.scriptActions::closeSecretPrompt) { value ->
            vm.scriptActions.closeSecretPrompt()
            vm.scriptActions.setSecret(prompt.script, secret, value)
        }
    }
}
