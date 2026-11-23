/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.data.HomeScramble
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogEntry
import com.mtwabpin.kolahal.data.StyledText
import com.mtwabpin.kolahal.script.ScriptMeta
import com.mtwabpin.kolahal.terminal.KeyBarKey
import com.mtwabpin.kolahal.terminal.Scramble
import com.mtwabpin.kolahal.terminal.ScrambledLog
import com.mtwabpin.kolahal.terminal.Suggestion
import com.mtwabpin.kolahal.terminal.ScriptActions
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.UiEvent
import com.mtwabpin.kolahal.terminal.lineDetail
import com.mtwabpin.kolahal.ui.ActionMenu
import com.mtwabpin.kolahal.ui.ActionSheet
import com.mtwabpin.kolahal.ui.scripts.SecretDialog
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.tty.PromptKeys
import com.mtwabpin.kolahal.ui.tty.TtyHome
import com.mtwabpin.kolahal.ui.tty.TtyKeyBar
import com.mtwabpin.kolahal.ui.tty.TtyLog
import com.mtwabpin.kolahal.ui.tty.TtyPrompt
import com.mtwabpin.kolahal.ui.tty.TtySearchResults
import com.mtwabpin.kolahal.ui.tty.TtySecretSheet
import com.mtwabpin.kolahal.ui.tty.TtySheet
import com.mtwabpin.kolahal.ui.tty.TtySuggestionList
import com.mtwabpin.kolahal.ui.tty.TtySuggestionStrip
import com.mtwabpin.kolahal.ui.tty.rememberTtyGrid

/** The text the prompt shows when Settings previews home while typing. One letter matches many items. */
private const val PREVIEW_INPUT = "s"

/** How long "Copied" shows after a line is copied by a swipe. */
internal const val COPIED_MS = 2_000L

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
    /** Drawn on the character grid: `home.style tty` with `tty.selector tty` ([TtyParts.selector]). */
    tty: Boolean = false,
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
        // While the keyboard is up, the selector closes and its strip and list leave composition. Its state, its sorted
        // apps and its measured letters are kept for when the keyboard goes down.
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
        tty = if (tty) rememberTtyGrid() else null,
    )
}

/** Whether the terminal page is showing. False while the widgets page beside it is showing. */
val LocalTerminalShown = androidx.compose.runtime.compositionLocalOf { true }

/**
 * Home. With [preview], it is drawn under Settings while the user changes a look setting
 * (TerminalViewModel.Peek). A preview takes no focus, keyboard or events. With `typing`, it looks as
 * if [PREVIEW_INPUT] were typed.
 *
 * This reads what home shows from the view model and keeps home's state ([HomeState]) above the
 * body that draws it ([GuiHome], or [com.mtwabpin.kolahal.ui.tty.TtyHome] on the grid). The body
 * gets only what it shows and the calls it makes
 * ([HomeCalls]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(vm: TerminalViewModel, preview: TerminalViewModel.Peek? = null) {
    // An install or update may change icons, so suggestions.icons loads the icons of those apps again.
    val appsRepo =
        com.mtwabpin.kolahal.apps.AppRepository.get(LocalContext.current)
    val packageChanges by appsRepo.packageChanges.collectAsStateWithLifecycle()
    if (preview == null) LaunchedEffect(packageChanges) {
        AppIcons.sync(
            packageChanges,
            appsRepo.takeChanged()
        )
    }
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val unlocks by vm.unlocksToday.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    // `scramble`: its state is read before home's first frame, and what home is made from (the log's
    // rows, a search's, the pinned lines, what is playing, a sheet) is scrambled here, in the same
    // composition that reads it. So no frame shows them as they are while it is on, in either style.
    val scrambleSeed by HomeScramble.get(LocalContext.current).seed.collectAsStateWithLifecycle()
    val scramble = remember(scrambleSeed) { scrambleSeed?.let(::Scramble) }
    val scrambled = remember(scramble) { scramble?.let(::ScrambledLog) }
    val pinned by vm.pinned.collectAsStateWithLifecycle()
    val pinnedLines = remember(pinned, scramble) { scramble?.let { scrambledPins(pinned, it) } ?: pinned }
    val playing by vm.nowPlaying.collectAsStateWithLifecycle()
    val nowPlaying = remember(playing, scramble) { playing?.let { np -> scramble?.let { scrambledMedia(np, it) } ?: np } }
    val configVersion by vm.config.version.collectAsStateWithLifecycle()
    val config = vm.config
    val logRows by vm.rows.collectAsStateWithLifecycle()
    val rows = remember(logRows, scrambled) { scrambled?.rows(logRows) ?: logRows }
    val reversedRows = remember(rows) { rows.asReversed() }
    // A tap, a swipe or a sheet works on the line itself, never on its scrambled copy: nothing
    // scrambled is stored, run or copied. A copy that is no longer shown gets nothing.
    val scrambledNow by rememberUpdatedState(scrambled)
    val real: (LogEntry) -> LogEntry? = remember {
        { e -> if (Scramble.of(e) == null) e else scrambledNow?.real(e) }
    }
    // When a result is tapped: once its line is loaded, the log scrolls to it (with a few newer lines
    // below it) and highlights it briefly ([LogScroll]).
    val jumpTo by vm.jumpTo.collectAsStateWithLifecycle()
    // Where the log is scrolled, which lines are open, the prompt's focus and the menu, for any body.
    val state = rememberHomeState(
        reversedRows,
        jumpTo,
        onJumpDone = vm::jumpDone,
        onAtNewest = vm::atNewest,
        onLoadOlder = vm::loadOlder,
    )
    val keyboard = LocalSoftwareKeyboardController.current
    val hasHardwareKeyboard = LocalConfiguration.current.keyboard != Configuration.KEYBOARD_NOKEYS

    // Read through updated state, so a keyboard attached later (configChanges=keyboard) is seen.
    val currentKeyboard by rememberUpdatedState(keyboard)
    val currentHasHardwareKeyboard by rememberUpdatedState(hasHardwareKeyboard)

    // Puts the focus in the prompt and brings up the on-screen keyboard. It reads the keyboard
    // through the updated state above: the effect below starts once and must call the current one.
    // With [show] false, only the focus moves. The effect passes that when a hardware keyboard is attached.
    fun focusPrompt(show: Boolean = true) {
        runCatching { state.focus.requestFocus() }
        if (show) currentKeyboard?.show()
    }
    if (preview == null) LaunchedEffect(Unit) {
        // Home was pressed on another page: scroll and focus now that the terminal is shown.
        // Scrolls are requested for the list's next layout, never forced inside this one.
        vm.consumeArrival()?.let { a ->
            state.scroll.toNewest()
            if (a.focus) {
                androidx.compose.runtime.withFrameNanos { } // the prompt's focus target is attached after the first frame
                focusPrompt(show = !currentHasHardwareKeyboard)
            }
        }
        vm.events.collect { e ->
            when (e) {
                UiEvent.ScrollToBottom -> state.scroll.toNewest()
                // MainActivity handles these, so they work from every page (setup, script manager).
                UiEvent.RequestNotificationPermission, UiEvent.RequestContactsPermission, UiEvent.PickContact, UiEvent.RequestCalendarPermission, UiEvent.RequestCallLogPermission,
                UiEvent.PickFont, UiEvent.PickBackupFolder, UiEvent.PickScriptFolder,
                UiEvent.ExportBackup, UiEvent.ImportBackup, UiEvent.ImportScripts, is UiEvent.ExportScript,
                UiEvent.PickTextFile, is UiEvent.NewTextFile, is UiEvent.OpenTextFile -> Unit

                UiEvent.ShowQuick -> Unit // opened by the collector below, where the menu lives
                UiEvent.FocusPrompt -> focusPrompt(show = !currentHasHardwareKeyboard)
                UiEvent.HideKeyboard -> currentKeyboard?.hide()
            }
        }
    }
    // Typing `/words` at the prompt searches: the matching lines replace the log until the search ends.
    val found by vm.search.collectAsStateWithLifecycle()
    val search = remember(found, scrambled) {
        found?.let { f -> scrambled?.let { f.copy(rows = it.rows(f.rows, search = true)) } ?: f }
    }
    // Not while the widgets page is showing: there, Back returns to the terminal.
    if (preview == null) BackHandler(enabled = search != null && LocalTerminalShown.current) { vm.escape() }
    val filter by vm.filter.collectAsStateWithLifecycle()
    val filterCount by vm.filterCount.collectAsStateWithLifecycle()
    // Back leaves `log only …`, the same way it leaves a search.
    if (preview == null) BackHandler(enabled = search == null && filter != null && LocalTerminalShown.current) {
        vm.filterLog(
            null
        )
    }
    // Keyboard-first: take focus at once when a physical keyboard is attached.
    if (preview == null) LaunchedEffect(hasHardwareKeyboard) {
        if (hasHardwareKeyboard) runCatching { state.focus.requestFocus() }
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
    // The same for the preview of `theme …` or `font …` typed at the prompt: home is rebuilt only when it changes.
    // It also compares with the saved settings, hence the config version.
    val livePreview by remember(configVersion) { derivedStateOf { vm.livePreview } }
    if (preview == null) LaunchedEffect(Unit) {
        vm.events.collect {
            if (it == UiEvent.ShowQuick) state.menu =
                ActionMenu("Quick actions", vm.quickActions(), null, systemFont = true, quick = true)
        }
    }
    // How home is drawn (home.style). Everything here is the same for every style, so a switch
    // keeps where the log is scrolled, which lines are open, what is typed and the open menu.
    val style = remember(configVersion) { HomeStyle.of(config[Keys.HOME_STYLE]) }
    val tty = style == HomeStyle.TTY
    // tty.*: the parts the tty style draws on the grid. The others are the GUI's own, in their place.
    val parts = remember(configVersion) { TtyParts.of(style) { config[it] } }
    // While home is scrambled a sheet is too, but for the quick actions, which show the phone's state.
    val menu = state.menu
    val shownMenu = remember(menu, scramble) {
        menu?.let { m -> if (scramble == null || m.quick) m else scrambledMenu(m, scramble) }
    }
    shownMenu?.let {
        val onGrid = if (it.quick) parts.quickActions else parts.menu
        if (onGrid) TtySheet(it, onDismiss = { state.menu = null })
        else ActionSheet(it, onDismiss = { state.menu = null })
    }
    // `script secret <name> <NAME>` opens a masked input box for the value, over home.
    val secretPrompt by vm.scriptActions.secretPrompt.collectAsStateWithLifecycle()
    secretPrompt?.let { p -> TerminalSecretPrompt(vm, p, parts.menu) }
    // screen.saver: a screen over home after some idle minutes, while nothing is open and nothing is typed.
    // With keyboard.on-home the keyboard is up whenever home shows, so only text in the prompt is typing.
    val keyboardOnHome = remember(configVersion) { config[Keys.FOCUS_ON_HOME] }
    if (preview == null) ScreenSaverWatch(
        vm,
        sheetOpen = state.menu != null || secretPrompt != null,
        typing = hasInput || (imeUp && !keyboardOnHome),
    )
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
    // A swipe copy shows "Copied" where Undo shows after a delete, for COPIED_MS.
    LaunchedEffect(state.copies) {
        if (state.copies == 0) return@LaunchedEffect
        state.copiedShown = true
        kotlinx.coroutines.delay(COPIED_MS)
        state.copiedShown = false
    }
    val hiddenLines by vm.hiddenLines.collectAsStateWithLifecycle()
    val rowsLoaded by vm.rowsLoaded.collectAsStateWithLifecycle()
    val undoOffer by vm.undo.collectAsStateWithLifecycle()
    val isHome by vm.isHome.collectAsStateWithLifecycle()
    val todoCount by vm.todoCount.collectAsStateWithLifecycle()
    val appContext = LocalContext.current.applicationContext
    // Made once, so the body's lines and lists can skip recomposition.
    val calls = remember(vm, state) {
        HomeCalls(
            onHeaderTap = vm::onHeaderTap,
            onProfileTap = vm::onProfileTap,
            onGesture = vm::onGesture,
            onMediaToggle = vm::mediaToggle,
            onMediaNext = vm::mediaNext,
            onPinTap = vm::onPinTap,
            onPinLongPress = { name ->
                state.showMenu(
                    "$name ▸ ${vm.pinned.value.firstOrNull { it.name == name }?.text.orEmpty()}",
                    null
                ) { vm.scriptActions.statusActions(name) }
            },
            // As on a script's turn, a long press opens a sheet: Next track, Previous track, Open the player.
            onMediaLongPress = { np -> state.showMenu(np.line, null) { mediaActions(appContext, np) } },
            onRefreshPinned = { vm.refreshPinned() },
            apps = { e -> real(e)?.let(vm::appsOf).orEmpty() },
            onTap = { e -> real(e)?.let(vm::onTap) },
            onLineLongPress = { shown ->
                real(shown)?.let { e ->
                    state.showMenu(
                        StyledText.plain(e.text),
                        e.id,
                        e.ctx.url,
                        lineDetail(e, config[Keys.CLOCK_24H])
                    ) { vm.actionsFor(e) }
                }
            },
            onShowInLog = { e -> real(e)?.let(vm::showInLog) },
            onAppTap = vm::launchApp,
            // A long-press on an app opens its menu, wherever the app shows: in a line, in the search
            // results, in the selector and in the suggestions.
            onAppLongPress = { app -> state.showMenu(app.label, null) { vm.appActions(app) } },
            onSwipeCopy = { shown -> real(shown)?.let { e -> vm.swipeCopy(e); state.copies++ } },
            onSwipeDelete = { e -> real(e)?.let(vm::swipeDelete) },
            onAction = { e, index -> real(e)?.let { vm.runLineAction(it, index) } },
            onOpenUrl = vm::openUrl,
            onLogWidth = vm::onLogWidth,
            onSelectorArea = { vm.selectorArea = it },
            onShowHidden = { vm.showHidden() },
            onClearFilter = { vm.filterLog(null) },
            onUndo = vm::undo,
            onRefreshIsHome = { vm.refreshIsHome() },
            onKeyBar = { vm.onKeyBar(it) },
            onInsert = { vm.insert(it) },
            onSaveFontSize = vm::saveFontSize,
            onFocusPrompt = { focusPrompt() },
            shownLog = if (preview == null) vm.shownLog else null,
            logDecode = if (preview == null) vm.logDecode else null,
        )
    }

    // The prompt in the style home is drawn in. The suggestions, the app selector and the key bar on
    // the grid or as the GUI draws them ([parts]); each body puts them in their places.
    val prompt: @Composable (Boolean) -> Unit = { typing ->
        // keybar.auto: the bar shows only while typing, so an idle home shows more of the log.
        val keyBarAuto = remember(configVersion) { config[Keys.KEYBAR_AUTO] }
        val keyBarOn = remember(configVersion) { config[Keys.KEYBAR_SHOW] }
        val onToggleKeyBar = {
            config.set(Keys.KEYBAR_SHOW, if (keyBarOn) "off" else "on")
            // With keybar.auto the bar shows only while typing, so turning it on also brings up the keyboard.
            if (!keyBarOn && keyBarAuto && !typing) focusPrompt()
        }
        // Same prompt.height while typing or not, so the line doesn't jump under the finger when the keyboard opens.
        val height = remember(configVersion) { config[Keys.PROMPT_HEIGHT_TYPING].dp }
        val imeSuggestions = remember(configVersion) { config[Keys.IME_SUGGESTIONS] }
        if (tty) {
            val swipeUpOn = remember(configVersion) {
                config[Keys.GESTURE_SWIPE_UP].trim().lowercase() !in setOf("", "none")
            }
            val keys = remember(vm) {
                PromptKeys(
                    onSubmit = vm::submit,
                    onHistoryUp = vm::historyUp,
                    onHistoryDown = vm::historyDown,
                    onComplete = vm::complete,
                    onEscape = vm::escape,
                    onClearScreen = vm::clearScreen,
                    onKeyBar = vm::onKeyBar,
                )
            }
            TtyPrompt(
                value = if (preview?.typing == true) TextFieldValue(PREVIEW_INPUT, TextRange(PREVIEW_INPUT.length)) else vm.input,
                onValueChange = if (preview?.typing == true) { _ -> } else vm::onInputChange,
                readOnly = preview?.typing == true,
                focus = state.focus,
                imeSuggestions = imeSuggestions,
                onFocus = { state.promptFocused = it },
                height = height,
                keyBarOn = keyBarOn,
                keys = keys,
                onQuick = { vm.openQuick() },
                onToggleKeyBar = onToggleKeyBar,
                onTapPrompt = { focusPrompt() },
                onSwipeUp = if (swipeUpOn) { { vm.onGesture(Keys.GESTURE_SWIPE_UP) } } else null,
            )
        } else Prompt(
            vm = vm,
            focus = state.focus,
            previewText = if (preview?.typing == true) PREVIEW_INPUT else null,
            imeSuggestions = imeSuggestions,
            onFocus = { state.promptFocused = it },
            height = height,
            keyBarOn = keyBarOn,
            onQuick = { vm.openQuick() },
            onToggleKeyBar = onToggleKeyBar,
            onTapPrompt = { focusPrompt() },
        )
    }
    val suggestions: @Composable (Boolean) -> Unit = { always ->
        // [selector] suggestions.style: list (default, bottom-anchored above the prompt) or chips.
        PromptSuggestions(
            vm,
            previewTyping = preview?.typing == true,
            always = always,
            configVersion = configVersion,
            onAppLongPress = calls.onAppLongPress,
            tty = parts.suggestions,
            scramble = scramble,
        )
        // [/selector]
    }
    val selector: @Composable () -> Unit = {
        // [selector] selector.rail: fluid full-list app selector on an edge of the log, keyboard down only.
        HomeAppSelector(
            vm,
            onLongPress = calls.onAppLongPress,
            // While its row-height slider in Settings is dragged, open the list so its rows are visible.
            showList = preview?.key == Keys.SELECTOR_ROW_HEIGHT.key,
            fade = if (preview == null) vm.selectorFade else null,
            tty = parts.selector,
        )
        // [/selector]
    }
    // The bar itself; each body shows and hides it under the prompt (keybar.show, keybar.auto).
    val keyBar: @Composable () -> Unit = {
        val specs = remember(configVersion) { listOf(config[Keys.KEYBAR], config[Keys.KEYBAR2]) }
        val rowHeight = remember(configVersion) { config[Keys.KEYBAR_HEIGHT].dp }
        val onKey: (KeyBarKey) -> Unit = { key ->
            if (key == KeyBarKey.KBD) calls.onFocusPrompt() else calls.onKeyBar(key)
        }
        if (parts.keyBar) TtyKeyBar(specs, rowHeight, onKey = onKey, onText = calls.onInsert)
        else KeyBar(specs, rowHeight, onKey = onKey, onText = calls.onInsert)
    }
    // The header says while home is scrambled, and the parts that show more than the rows read it.
    CompositionLocalProvider(LocalScramble provides scramble) {
        if (tty) TtyHome(
            state = state,
            calls = calls,
            config = config,
            configVersion = configVersion,
            preview = preview != null,
            previewTyping = preview?.typing == true,
            previewShader = preview?.shader,
            hasInput = hasInput,
            status = status,
            unlocks = unlocks,
            profiles = profiles,
            todoCount = todoCount,
            nowPlaying = nowPlaying,
            pinnedLines = pinnedLines,
            search = search,
            filter = filter,
            filterCount = filterCount,
            undoOffer = undoOffer,
            isHome = isHome,
            livePreview = livePreview,
            selectorFade = vm.selectorFade,
            prompt = prompt,
            suggestions = suggestions,
            selector = selector,
            keyBar = keyBar,
            // The log on the character grid, or a search's results in its place.
            logArea = { results, deleteRight, log, found ->
                if (results != null) TtySearchResults(
                    search = results,
                    menuEntryId = state.menu?.entryId,
                    calls = calls,
                    config = config,
                    configVersion = configVersion,
                    timeFormat = lineTimeFormat,
                    today = today,
                    deleteRight = deleteRight,
                    modifier = found,
                ) else TtyLog(
                    rows = reversedRows,
                    state = state,
                    calls = calls,
                    config = config,
                    configVersion = configVersion,
                    hiddenLines = hiddenLines,
                    rowsLoaded = rowsLoaded,
                    timeFormat = lineTimeFormat,
                    today = today,
                    deleteRight = deleteRight,
                    modifier = log,
                )
            },
        ) else GuiHome(
            state = state,
            calls = calls,
            config = config,
            configVersion = configVersion,
            preview = preview != null,
            previewTyping = preview?.typing == true,
            previewShader = preview?.shader,
            hasInput = hasInput,
            status = status,
            unlocks = unlocks,
            profiles = profiles,
            todoCount = todoCount,
            nowPlaying = nowPlaying,
            pinnedLines = pinnedLines,
            rows = reversedRows,
            hiddenLines = hiddenLines,
            rowsLoaded = rowsLoaded,
            search = search,
            filter = filter,
            filterCount = filterCount,
            undoOffer = undoOffer,
            isHome = isHome,
            livePreview = livePreview,
            timeFormat = lineTimeFormat,
            today = today,
            selectorFade = vm.selectorFade,
            prompt = prompt,
            suggestions = suggestions,
            selector = selector,
            keyBar = keyBar,
        )
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
    /** On the character grid (`home.style tty` with `tty.suggestions tty`): no icons, the same settings otherwise. */
    tty: Boolean,
    /** `scramble`: the rows that name something of the user's are scrambled, and apps show no icon. */
    scramble: Scramble?,
) {
    val config = vm.config
    val apps by vm.apps.collectAsStateWithLifecycle()
    val maxSuggestions = remember(configVersion) { config[Keys.SUGGESTIONS] }
    val commandNames by vm.commandNames.collectAsStateWithLifecycle()
    val prefsVersion by vm.appPrefsVersion.collectAsStateWithLifecycle()
    val contacts by vm.contacts.collectAsStateWithLifecycle()
    val loaded by vm.suggestionsLoaded.collectAsStateWithLifecycle()
    val inputText = if (previewTyping) PREVIEW_INPUT else vm.input.text
    // Hidden, they aren't worked out: an idle home would rank every app again on each return.
    if (!always && inputText.isEmpty()) return
    val suggestions =
        remember(inputText, apps, maxSuggestions, commandNames, prefsVersion, contacts, loaded) {
            vm.suggestions(inputText).take(maxSuggestions)
        }
    // Scrambled, a row shows its scrambled copy, and a tap or a long press goes to the real one by its key.
    val shown = remember(suggestions, scramble) {
        if (scramble == null) suggestions else suggestions.mapIndexed { i, s -> scrambledSuggestion(s, i, scramble) }
    }
    val onPick: (Suggestion) -> Unit = remember(suggestions) {
        { picked -> suggestions.firstOrNull { it.key == picked.key }?.let(vm::onSuggestion) }
    }
    val onAppHeld: (com.mtwabpin.kolahal.apps.AppEntry) -> Unit = remember(suggestions, onAppLongPress) {
        { held ->
            suggestions.firstNotNullOfOrNull { (it as? Suggestion.App)?.app?.takeIf { app -> app.key == held.key } }
                ?.let(onAppLongPress)
        }
    }
    val showIcons = remember(configVersion) { config[Keys.SUGGEST_ICONS] } && scramble == null
    val chips = remember(configVersion) { config[Keys.SUGGESTIONS_STYLE] } == "chips"
    if (tty) {
        if (chips) TtySuggestionStrip(
            shown,
            rows = remember(configVersion) { config[Keys.SUGGESTION_ROWS] },
            onPick = onPick,
        ) else TtySuggestionList(
            shown,
            onPick = onPick,
            onAppLongPress = onAppHeld,
            wrapHelp = remember(configVersion) { config[Keys.SUGGESTIONS_WRAP] },
        )
    } else if (chips) {
        SuggestionStrip(
            shown,
            rows = remember(configVersion) { config[Keys.SUGGESTION_ROWS] },
            onPick = onPick,
            icons = showIcons
        )
    } else {
        SuggestionList(
            shown,
            onPick = onPick,
            onAppLongPress = onAppHeld,
            icons = showIcons,
            wrapHelp = remember(configVersion) { config[Keys.SUGGESTIONS_WRAP] },
        )
    }
}

/**
 * The box that `script secret` opens, on the character grid when [onGrid] (`tty.menu`). It closes if the script or
 * its `@secret` line is gone.
 */
@Composable
private fun TerminalSecretPrompt(vm: TerminalViewModel, prompt: ScriptActions.SecretPrompt, onGrid: Boolean) {
    val scripts by vm.scriptList.collectAsStateWithLifecycle()
    val secret = remember(scripts, prompt) {
        scripts.firstOrNull { it.name == prompt.script }
            ?.let { s -> ScriptMeta.secrets(s.code).firstOrNull { it.name == prompt.name } }
    }
    if (secret == null) {
        LaunchedEffect(prompt) { vm.scriptActions.closeSecretPrompt() }
        return
    }
    val isSet by produceState(false, prompt) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            vm.scriptActions.secretStore.isSet(
                prompt.script,
                secret
            )
        }
    }
    if (onGrid) {
        TtySecretSheet(prompt.script, secret, isSet, onDismiss = vm.scriptActions::closeSecretPrompt) { value ->
            vm.scriptActions.closeSecretPrompt()
            vm.scriptActions.setSecret(prompt.script, secret, value)
        }
        return
    }
    GuiTheme {
        SecretDialog(
            prompt.script,
            secret,
            isSet,
            onDismiss = vm.scriptActions::closeSecretPrompt
        ) { value ->
            vm.scriptActions.closeSecretPrompt()
            vm.scriptActions.setSecret(prompt.script, secret, value)
        }
    }
}
