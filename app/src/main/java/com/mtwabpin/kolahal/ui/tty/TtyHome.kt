/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.tty

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.commands.LogFilter
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.StatusLine
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.terminal.LivePreview
import com.mtwabpin.kolahal.terminal.SetupChecks
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.UndoOffer
import com.mtwabpin.kolahal.ui.HomeCalls
import com.mtwabpin.kolahal.ui.HomeState
import com.mtwabpin.kolahal.ui.LogArea
import com.mtwabpin.kolahal.ui.Term
import com.mtwabpin.kolahal.ui.page
import com.mtwabpin.kolahal.ui.pinchToZoomFont
import com.mtwabpin.kolahal.ui.rememberFontSizeToast
import kotlinx.coroutines.launch

/**
 * Home on the character grid (`home.style tty`): the header, the pinned line, the log, the bands,
 * the suggestions, the prompt and the key bar, each a part of one grid whose columns line up from
 * top to bottom ([LocalTtyFrame]). It is laid out as the GUI's home ([com.mtwabpin.kolahal.ui.GuiHome])
 * and takes the same state, the same calls and the same settings, so a switch between the two keeps
 * where the log is, what is typed and what is open. The widgets page beside it stays the GUI's.
 * - The log comes from [logArea] (the TTY log, or a search's results), the prompt, the suggestions,
 *   the app selector and the key bar from [prompt], [suggestions], [selector] and [keyBar], made by
 *   [com.mtwabpin.kolahal.ui.TerminalScreen]. The prompt is always on the grid; each of the others
 *   is on the grid or the GUI's own (`tty.suggestions`, `tty.selector`, `tty.keybar`), and goes in
 *   the same place either way, so the grid's columns around it stay where they are.
 * - [preview]: Settings draws home under it while a look setting changes; with [previewTyping]
 *   it looks as if something were typed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TtyHome(
    state: HomeState,
    calls: HomeCalls,
    config: Config,
    configVersion: Int,
    preview: Boolean,
    previewTyping: Boolean,
    /** The shader a preview shows home with, if any. */
    previewShader: String?,
    hasInput: Boolean,
    status: Status,
    unlocks: Int,
    profiles: List<Profile>,
    todoCount: Int,
    nowPlaying: NowPlaying?,
    pinnedLines: List<StatusLine>,
    search: TerminalViewModel.LogSearch?,
    filter: LogFilter?,
    filterCount: Int,
    undoOffer: UndoOffer?,
    isHome: Boolean,
    livePreview: LivePreview?,
    selectorFade: FloatState,
    prompt: @Composable (typing: Boolean) -> Unit,
    suggestions: @Composable (always: Boolean) -> Unit,
    selector: @Composable () -> Unit,
    /** The key bar's rows; home shows and hides them under the prompt (`keybar.show`, `keybar.auto`). */
    keyBar: @Composable () -> Unit,
    logArea: LogArea,
) {
    val density = LocalDensity.current
    val grid = rememberTtyGrid()
    // The grid's width comes from home's own width. Read once it is measured; until then the
    // window's, so the first frame is already close.
    val window = LocalWindowInfo.current.containerSize.width
    var widthPx by remember { mutableIntStateOf(window) }
    val margin = with(density) { GRID_MARGIN.roundToPx() }
    val frame = remember(grid, widthPx, margin) { TtyFrame.of(grid, widthPx, margin) }
    val fontSizeToast = rememberFontSizeToast()
    val saveFontSize by rememberUpdatedState(calls.onSaveFontSize)
    // Made once: a new modifier on each rebuild would stop the log's list from skipping recomposition.
    val pinch = remember(fontSizeToast) {
        Modifier.pinchToZoomFont(
            onChange = { Term.fontSize = it },
            onEnd = { from, to -> saveFontSize(to); fontSizeToast(from, to) })
    }
    val doubleTapOn = remember(configVersion) {
        config[Keys.GESTURE_DOUBLE_TAP].trim().let { it.isNotEmpty() && it != com.mtwabpin.kolahal.gesture.Gestures.NONE }
    }
    // screen.shader: a shader over all of it, the background too. Settings' preview of home has none.
    val shader = if (preview && previewShader != null) rememberPeekShader(previewShader, grid, frame.cols, frame.originX) else rememberTtyShader(
        remember(configVersion) { config[Keys.SCREEN_SHADER] }, grid, frame.cols, frame.originX, on = !preview,
    )

    CompositionLocalProvider(LocalTtyFrame provides frame) {
        Column(
            Modifier
                .fillMaxSize()
                .then(shader)
                .page(wallpaper = remember(configVersion) { config[Keys.LOG_WALLPAPER] })
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.displayCutout).only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(
                    WindowInsets.navigationBars
                        .add(WindowInsets(bottom = remember(configVersion) { config[Keys.PROMPT_BOTTOM_PADDING].dp }))
                        .union(WindowInsets.ime),
                )
                .onSizeChanged { widthPx = it.width }
                .testTag("tty-home"),
        ) {
            // A hardware keyboard has these keys already, so the bar follows the on-screen keyboard only.
            val typing = if (preview) previewTyping else (state.promptFocused && WindowInsets.isImeVisible)
            val searching = typing || hasInput
            val showHeader = remember(configVersion) { config[Keys.HEADER_SHOW] }
            if (showHeader) TtyHeader(
                status = status,
                unlocks = unlocks,
                profiles = profiles,
                todoCount = todoCount,
                config = config,
                version = configVersion,
                onTarget = calls.onHeaderTap,
                onProfileTap = calls.onProfileTap,
                onSwipeDown = { calls.onGesture(Keys.GESTURE_SWIPE_DOWN) },
            ) else Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars.union(WindowInsets.displayCutout)))
            // What's playing and the pinned status lines; hidden while typing, so the suggestions have the room.
            AnimatedVisibility(
                visible = !searching,
                enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
            ) {
                TtyPinned(
                    nowPlaying = nowPlaying,
                    lines = pinnedLines,
                    config = config,
                    version = configVersion,
                    onMediaToggle = calls.onMediaToggle,
                    onMediaNext = calls.onMediaNext,
                    onPinTap = calls.onPinTap,
                    onPinLongPress = calls.onPinLongPress,
                    onRefresh = calls.onRefreshPinned,
                    onMediaLongPress = calls.onMediaLongPress,
                )
            }
            // With the widgets page on the right, a line swiped toward it is copied, not deleted; in a
            // right-to-left layout the sides swap with the page.
            val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
            val deleteRight = remember(configVersion, rtl) { (config[Keys.WIDGETS_PAGE] == "right") != rtl }
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                val ime = WindowInsets.ime
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(
                            // The selector's area, for the widgets page's selector; only with the keyboard
                            // down, as the strip shows only then.
                            if (!preview) Modifier.onGloballyPositioned {
                                if (ime.getBottom(density) == 0) calls.onSelectorArea(
                                    Rect(0f, it.positionInWindow().y, it.size.width.toFloat(), it.positionInWindow().y + it.size.height),
                                )
                            } else Modifier,
                        ),
                ) {
                    val results = search?.takeIf { !it.isEmpty && !preview }
                    val logModifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - selectorFade.floatValue }
                        .onSizeChanged { calls.onLogWidth(it.width) }
                        .testTag("log")
                        .then(pinch)
                        .pointerInput(doubleTapOn) {
                            detectTapGestures(
                                onDoubleTap = if (doubleTapOn) { _ -> calls.onGesture(Keys.GESTURE_DOUBLE_TAP) } else null,
                            ) { calls.onFocusPrompt() }
                        }
                    val resultsModifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - selectorFade.floatValue }
                    logArea(results, deleteRight, logModifier, resultsModifier)
                    // Scrolled away from the newest lines: a chip over the log's bottom jumps back down.
                    androidx.compose.animation.AnimatedVisibility(
                        visible = state.scroll.awayFromBottom && results == null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .graphicsLayer { alpha = 1f - selectorFade.floatValue },
                    ) {
                        val scope = rememberCoroutineScope()
                        TtyJumpLatest {
                            state.scroll.followNewest = true
                            scope.launch { state.scroll.list.animateScrollToItem(0) }
                        }
                    }
                    selector()
                }
                // Behind the build flag, so R8 removes the updater from the Play build.
                if (BuildConfig.SELF_UPDATE && !preview) TtyUpdateProgress()
                search?.let { TtySearchSummary(it) } ?: filter?.let { TtyFilterBar(it, filterCount, onClear = calls.onClearFilter) }
                val suggestionsAuto = remember(configVersion) { config[Keys.SUGGESTIONS_AUTO] }
                val notice = undoOffer?.text ?: "Copied".takeIf { state.copiedShown }
                if (notice != null) TtyNotice(notice, undo = undoOffer != null, onUndo = calls.onUndo)
                suggestions(!suggestionsAuto || typing)
            }
            val keyBarAuto = remember(configVersion) { config[Keys.KEYBAR_AUTO] }
            val keyBarOn = remember(configVersion) { config[Keys.KEYBAR_SHOW] }
            if (!preview) {
                val context = LocalContext.current
                // Android's page for the home app: for Android 9, and when its dialog doesn't make Kolahal home.
                val homeSettings = { runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } }
                val askHome = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode != Activity.RESULT_OK) homeSettings()
                    calls.onRefreshIsHome()
                }
                TtyDefaultHomeBand(!isHome && livePreview == null) {
                    SetupChecks.homeRoleRequest(context)?.let { askHome.launch(it) } ?: homeSettings()
                }
            }
            TtyLivePreviewBand(if (!preview) livePreview else null)
            prompt(typing)
            // Under the prompt, so it sits on the keyboard like an extra row of keys.
            AnimatedVisibility(
                visible = keyBarOn && (!keyBarAuto || typing),
                enter = expandVertically(expandFrom = Alignment.Bottom) + slideInVertically { it },
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + slideOutVertically { it },
            ) {
                keyBar()
            }
        }
    }
}
