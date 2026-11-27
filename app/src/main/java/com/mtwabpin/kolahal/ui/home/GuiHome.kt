/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.commands.LogFilter
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.StatusLine
import com.mtwabpin.kolahal.shader.ShaderCatalog
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.status.Status
import com.mtwabpin.kolahal.terminal.LineModel
import com.mtwabpin.kolahal.terminal.LineSwipes
import com.mtwabpin.kolahal.terminal.LineOptions
import com.mtwabpin.kolahal.terminal.LivePreview
import com.mtwabpin.kolahal.terminal.LogRow
import com.mtwabpin.kolahal.terminal.SetupChecks
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.TimeMode
import com.mtwabpin.kolahal.terminal.UndoOffer
import com.mtwabpin.kolahal.terminal.tty.screens.LogDecode
import com.mtwabpin.kolahal.ui.theme.Term
import com.mtwabpin.kolahal.ui.theme.page
import com.mtwabpin.kolahal.ui.tty.OverShaderBox
import com.mtwabpin.kolahal.ui.tty.rememberOverShader
import com.mtwabpin.kolahal.ui.tty.rememberPeekShader
import com.mtwabpin.kolahal.ui.tty.rememberPlayedOnHome
import com.mtwabpin.kolahal.ui.tty.rememberShaderMeta
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import kotlin.math.roundToInt
import com.mtwabpin.kolahal.ui.tty.rememberTtyGrid
import com.mtwabpin.kolahal.ui.tty.rememberTtyShader

/**
 * Home as text, chips and cards: the header, the pinned line, the log, the suggestions, the prompt
 * and the key bar. [TerminalScreen] reads what it shows from the view model and passes it in, with
 * the [calls] it may make; [state] is kept above it. The prompt, the suggestions, the app selector
 * and the key bar are passed in ready made ([prompt], [suggestions], [selector], [keyBar]).
 * - [preview]: Settings draws home under it while a look setting changes. With [previewTyping],
 *   it looks as if something were typed.
 * - [rows]: the log's rows, newest first.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun GuiHome(
    state: HomeState,
    calls: HomeCalls,
    config: Config,
    configVersion: Int,
    preview: Boolean,
    previewTyping: Boolean,
    /** The shader a preview shows home with, if any. */
    previewShader: String?,
    /** Something is typed at the prompt. Its text is read only where it shows, so a key press doesn't rebuild home. */
    hasInput: Boolean,
    status: Status,
    unlocks: Int,
    profiles: List<Profile>,
    todoCount: Int,
    nowPlaying: NowPlaying?,
    pinnedLines: List<StatusLine>,
    rows: List<LogRow>,
    hiddenLines: Int,
    rowsLoaded: Boolean,
    search: TerminalViewModel.LogSearch?,
    filter: LogFilter?,
    filterCount: Int,
    undoOffer: UndoOffer?,
    isHome: Boolean,
    livePreview: LivePreview?,
    timeFormat: SimpleDateFormat,
    today: LocalDate,
    /** How far the log is faded out while the app selector is open. */
    selectorFade: FloatState,
    prompt: @Composable (typing: Boolean) -> Unit,
    suggestions: @Composable (always: Boolean) -> Unit,
    selector: @Composable () -> Unit,
    /** The key bar's rows; home shows and hides them under the prompt (`keybar.show`, `keybar.auto`). */
    keyBar: @Composable () -> Unit,
    /**
     * Draws the log in place of the list here, or null for this one. Home on the grid has a body
     * of its own ([com.mtwabpin.kolahal.ui.tty.TtyHome]), so none passes one now.
     */
    logArea: LogArea? = null,
) {
    val density = LocalDensity.current
    val indents = rememberLineIndents()
    val timeMode = remember(configVersion) { TimeMode.of(config[Keys.SHOW_TIMESTAMPS]) }
    val lineOptions = remember(timeMode, Term.foldLines) { LineOptions(timeMode, Term.foldLines) }
    val dimOld = remember(configVersion) { config[Keys.LOG_DIM] }
    val commandGap = remember(configVersion) { config[Keys.LOG_SPACING].dp }
    val itemGap = remember(configVersion) { config[Keys.LOG_ITEM_SPACING].dp }
    val showGutter = remember(configVersion) { config[Keys.LOG_GUTTER] }
    val fontSizeToast = rememberFontSizeToast()
    val saveFontSize by rememberUpdatedState(calls.onSaveFontSize)
    // Made once. A new modifier on each rebuild of home would stop the log's list from skipping recomposition.
    val pinch = remember(fontSizeToast) {
        Modifier.pinchToZoomFont(
            onChange = { Term.fontSize = it },
            onEnd = { from, to -> saveFontSize(to); fontSizeToast(from, to) })
    }
    val doubleTapOn = remember(configVersion) {
        config[Keys.GESTURE_DOUBLE_TAP].trim()
            .let { it.isNotEmpty() && it != com.mtwabpin.kolahal.gesture.Gestures.NONE }
    }
    // A tap on a link in a line's text (fmt.link) opens it as a tap on a linked line does.
    val openUrl by rememberUpdatedState(calls.onOpenUrl)
    val linkOpener = remember {
        object : UriHandler {
            override fun openUri(uri: String) = openUrl(uri)
        }
    }

    // screen.shader: a shader over all of home, the background too, as on the tty home. Settings' preview
    // of home has none, and the grid is measured only while one is on.
    val shaderName = remember(configVersion) { config[Keys.SCREEN_SHADER] }
    // The app selector is drawn over the picture of a shader that isn't for text, so its icons look as without one.
    val over = rememberOverShader(if (preview) previewShader else shaderName)
    val shader = when {
        preview -> if (previewShader != null) rememberPeekShader(previewShader, rememberTtyGrid(), over = over) else Modifier
        shaderName == ShaderCatalog.OFF -> Modifier
        else -> rememberTtyShader(shaderName, rememberTtyGrid(), over = over)
    }

    // `decode`: the log scrambles in place, and nothing else of home shows. It all keeps its place.
    val decode = calls.logDecode?.takeIf { it.active }
    val decodeArea = remember { DecodeArea() }
    // `scramble`: decode's waves run over the scrambled log when it scrolls and when a line comes.
    val scrambledHome = LocalScramble.current != null && !preview
    val scrambleWaves = remember { LogDecode() }.takeIf { scrambledHome }
    if (scrambleWaves != null) ScrambleWaves(scrambleWaves, state.scroll.list, rows.firstOrNull()?.key)
    val played = rememberPlayedOnHome(on = !preview)
    val shaderMeta = rememberShaderMeta(if (preview) previewShader else shaderName)

    Column(
        Modifier
            .fillMaxSize()
            .then(played)
            .then(shader)
            // Marked while a shader reads it, so text and pictures keep their colors ([ShaderMeta.PAGE_ALPHA]).
            .page(
                // A scene brings its own ground: the phone's wallpaper doesn't show through it ([WallpaperChoice.homeShare]).
                wallpaper = WallpaperChoice.homeShare(
                    remember(configVersion) { config[Keys.LOG_WALLPAPER] }, shaderMeta.takeIf { shader != Modifier }
                ),
                marked = shader != Modifier || played != Modifier,
            )
            .then(if (decode == null) Modifier else decodeArea.only)
            // No top padding here: the header fills the band beside the camera.
            .windowInsetsPadding(
                WindowInsets.statusBars.union(WindowInsets.displayCutout)
                    .only(WindowInsetsSides.Horizontal)
            )
            // prompt.bottom-padding: space under the lowest row (the prompt, or a key bar shown always),
            // clear of rounded screen corners. The keyboard takes the larger of the two, so while
            // typing the space is under the keyboard, and it shrinks and grows with its animation.
            .windowInsetsPadding(
                WindowInsets.navigationBars
                    .add(WindowInsets(bottom = remember(configVersion) { config[Keys.PROMPT_BOTTOM_PADDING].dp }))
                    .union(WindowInsets.ime),
            )
    ) {
        // A hardware keyboard already has these keys, so the bar follows the on-screen keyboard only.
        val typing = if (preview) previewTyping else (state.promptFocused && WindowInsets.isImeVisible)
        // While searching, the pinned header hides so the suggestions can use its space.
        val searching = typing || hasInput
        // With header.show off there is no header. The pinned header and the log start under the camera.
        val showHeader = remember(configVersion) { config[Keys.HEADER_SHOW] }
        if (showHeader) Header(
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
                onMediaToggle = calls.onMediaToggle,
                onMediaNext = calls.onMediaNext,
                onPinTap = calls.onPinTap,
                onPinLongPress = calls.onPinLongPress,
                onRefresh = calls.onRefreshPinned,
                // As on a script's turn, a long press opens a sheet: Next track, Previous track, Open the player.
                onMediaLongPress = calls.onMediaLongPress,
            )
        }
        // What a swipe each way does on a line (log.swipe-left, log.swipe-right). While both are the defaults, they
        // follow the page order: with the log first and the pages right of it, a line swiped left (as the page turns
        // toward them) is copied, not deleted. In a right-to-left layout those pages are on the left, so the sides
        // swap with them.
        val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val swipes = remember(configVersion, rtl) { LineSwipes.of(config, rtl) }
        // The log and the suggestions share the space the header, key bar and prompt leave. Those
        // are measured first, so a long suggestion list can't push the prompt off the screen.
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val ime = WindowInsets.ime
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .then(decodeArea.log)
                    .then(
                        // Save the selector's area, so the selector on the widgets page can match it. Only with the
                        // keyboard down, as the strip shows only then: the box shrinks on each frame of the keyboard's
                        // animation, and each new area would build the widgets page's selector again.
                        if (!preview) Modifier.onGloballyPositioned {
                            if (ime.getBottom(density) == 0) calls.onSelectorArea(
                                Rect(
                                    0f,
                                    it.positionInWindow().y,
                                    it.size.width.toFloat(),
                                    it.positionInWindow().y + it.size.height
                                )
                            )
                        }
                        else Modifier
                    )
            ) {
                val results = search?.takeIf { !it.isEmpty && !preview }
                // The log's list: it fades out while the app selector is open, and taps on its
                // empty space bring up the keyboard. Double taps are detected only when a
                // double-tap gesture is set, because waiting for one delays every tap.
                val logModifier = Modifier
                    .fillMaxSize()
                    // While the app selector is open, the log fades out so the background shows behind the list.
                    .graphicsLayer {
                        alpha = 1f - selectorFade.floatValue
                    }
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
                    .graphicsLayer {
                        alpha = 1f - selectorFade.floatValue
                    }
                if (logArea != null) logArea(results, swipes, logModifier, resultsModifier)
                else CompositionLocalProvider(LocalUriHandler provides linkOpener, LocalLogDecode provides (decode ?: scrambleWaves)) {
                    if (results != null) LogSearchResults(
                        search = results,
                        timeFormat = timeFormat,
                        gutter = showGutter,
                        indents = indents,
                        menuEntryId = state.menu?.entryId,
                        apps = calls.apps,
                        onOpen = calls.onShowInLog,
                        onLongPress = calls.onLineLongPress,
                        onAppTap = calls.onAppTap,
                        onAppLongPress = calls.onAppLongPress,
                        swipes = swipes,
                        onSwipe = calls.onSwipe,
                        swipeDoes = calls.swipeDoes,
                        modifier = resultsModifier,
                    ) else LazyColumn(
                        state = state.scroll.list,
                        reverseLayout = true,
                        // Taps that no log line used (empty space) bring up the keyboard.
                        modifier = logModifier,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        // Not before the first read: a cold start would flash it over a log that isn't empty.
                        if (rows.isEmpty() && hiddenLines == 0 && rowsLoaded) {
                            // An empty log: what to do, in the text color, then the other tips dim, one per line.
                            item(key = "empty-hint") {
                                Column(
                                    Modifier
                                        .padding(vertical = 12.dp)
                                        .testTag("empty-hint")
                                ) {
                                    LogText("Type an app name or a command", style = Term.text)
                                    Spacer(Modifier.height(8.dp))
                                    LogText(
                                        "help lists everything\n↑ recalls history\n/ searches the log\n" +
                                                "Tap a line to use it, long-press for more\n⚙ settings",
                                        style = Term.text.copy(color = Term.dim),
                                    )
                                }
                            }
                        }
                        items(
                            rows,
                            key = { it.key },
                            contentType = { if (it is LogRow.Entry) it.entry.kind else it.javaClass }) { row ->
                            when (row) {
                                is LogRow.Day -> DayDivider(row, today)
                                is LogRow.Unread -> UnreadDivider(row)
                                is LogRow.Entry -> {
                                    val model = rememberLineModel(row, lineOptions)
                                    LogLine(
                                        model = model,
                                        timeFormat = timeFormat,
                                        dim = dimOld,
                                        topGap = if (model.gapAbove == LineModel.Gap.GROUP) commandGap else 0.dp,
                                        itemGap = if (model.gapAbove == LineModel.Gap.ITEM) itemGap else 0.dp,
                                        gutter = showGutter,
                                        indents = indents,
                                        highlighted = state.menu?.entryId == row.entry.id || state.scroll.flash == row.entry.id,
                                        apps = calls.apps,
                                        onTap = calls.onTap,
                                        onLongPress = calls.onLineLongPress,
                                        onAppTap = calls.onAppTap,
                                        onAppLongPress = calls.onAppLongPress,
                                        swipes = swipes,
                                        onSwipe = calls.onSwipe,
                                        swipeDoes = calls.swipeDoes,
                                        onAction = calls.onAction,
                                        marks = emptyList(),
                                        foldOpen = state.folds.isOpen(row.entry.id),
                                        onToggleFold = { state.folds.toggle(row.entry.id) },
                                    )
                                }
                            }
                        }
                        // log.fresh: older output is hidden, not deleted. The last item is the top of the reversed list.
                        if (hiddenLines > 0) {
                            item(key = "hidden-lines") {
                                LogText(
                                    "▴ $hiddenLines earlier lines · tap to show",
                                    style = Term.text.copy(color = Term.dim),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(onClickLabel = "show earlier output") { calls.onShowHidden() }
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
                androidx.compose.animation.AnimatedVisibility(
                    visible = state.scroll.awayFromBottom && results == null && decode == null,
                    enter = androidx.compose.animation.fadeIn(),
                    exit = androidx.compose.animation.fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer { alpha = 1f - selectorFade.floatValue },
                ) {
                    val scope = rememberCoroutineScope()
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .testTag("jump-latest")
                            .heightIn(min = 48.dp)
                            .clickable(onClickLabel = "scroll to the bottom") {
                                state.scroll.followNewest =
                                    true; scope.launch { state.scroll.list.animateScrollToItem(0) }
                            }
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                    ) {
                        // The chip: a soft pill, no border, lifted off the log by a shadow. Its color is the
                        // surface's, a touch toward the text color, so it stands out on a dark theme too, where
                        // the surface is close to the background. About 40 dp tall at font size 14.
                        Box(
                            Modifier
                                .shadow(6.dp, RoundedCornerShape(50))
                                .background(
                                    lerp(Term.surface, Term.fg, 0.06f),
                                    RoundedCornerShape(50)
                                )
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                        ) {
                            // In the system font: it is a button over the log, not a line of it. One size
                            // up from the log's text, so it is easy to see and to hit.
                            Text(
                                "↓ Scroll to bottom",
                                style = Term.text.copy(
                                    color = Term.prompt,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Default,
                                    fontSize = (Term.fontSize + 1).sp
                                ),
                            )
                        }
                    }
                }
                // [selector] selector.rail: fluid full-list app selector on an edge of the log, keyboard down only.
                if (decode == null) OverShaderBox(over) { selector() }
                // [/selector]
            }
            // Behind the build flag, so R8 removes the updater from the Play build.
            if (BuildConfig.SELF_UPDATE && !preview) UpdateProgressLine()
            search?.let { LogSearchSummary(it) } ?: filter?.let {
                LogFilterBar(
                    it,
                    filterCount,
                    onClear = calls.onClearFilter
                )
            }
            // suggestions.auto: hidden on an idle home. Shown while typing, or whenever there is input
            // (so typing on a hardware keyboard still shows them).
            val suggestionsAuto = remember(configVersion) { config[Keys.SUGGESTIONS_AUTO] }
            // What was just done, with Undo after a delete or a clear, or "Copied" after a swipe copy ("Starred" after a star).
            // A polite live region: TalkBack reads it when it appears, after what it is saying.
            val notice = undoOffer?.text ?: state.swipeNote.takeIf { state.swipeNoteShown }
            if (notice != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Term.surface)
                        .heightIn(min = 44.dp)
                        .padding(start = 12.dp)
                        .testTag("undo-row")
                        .semantics { liveRegion = LiveRegionMode.Polite },
                ) {
                    Text(
                        notice,
                        style = Term.text.copy(color = Term.dim),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 44.dp)
                            .wrapContentHeight()
                    )
                    if (undoOffer != null) Text(
                        "Undo",
                        style = Term.text.copy(color = Term.prompt, fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .clickable(onClick = calls.onUndo)
                            .heightIn(min = 44.dp)
                            .wrapContentHeight()
                            .padding(horizontal = 16.dp),
                    )
                }
            }
            // [selector] suggestions.style: list (default, bottom-anchored above the prompt) or chips.
            suggestions(!suggestionsAuto || typing)
            // [/selector]
        }
        // keybar.auto: the bar shows only while typing, so an idle home shows more of the log.
        val keyBarAuto = remember(configVersion) { config[Keys.KEYBAR_AUTO] }
        val keyBarOn = remember(configVersion) { config[Keys.KEYBAR_SHOW] }
        // If Kolahal is not the home app, a band asks the user to pick it, unless a preview band uses
        // that place. The view model checks at each resume, so the band goes once Kolahal is picked.
        if (!preview) {
            val context = LocalContext.current
            // Android's settings page for the home app: for Android 9, and when its dialog doesn't make
            // Kolahal home (a "no", or no dialog at all after "Don't ask again").
            val homeSettings =
                { runCatching { context.startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } }
            val askHome =
                rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode != Activity.RESULT_OK) homeSettings()
                    calls.onRefreshIsHome()
                }
            DefaultHomeBand(!isHome && livePreview == null) {
                SetupChecks.homeRoleRequest(context)?.let { askHome.launch(it) } ?: homeSettings()
            }
        }
        LivePreviewBand(if (!preview) livePreview else null)
        prompt(typing)
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
            keyBar()
        }
    }
}

/** Where the log is on home, so that `decode` can show it alone. */
private class DecodeArea {
    private var page: LayoutCoordinates? = null
    private var box: LayoutCoordinates? = null

    /** The log's box. */
    val log: Modifier = Modifier.onPlaced { box = it }

    /** The page: only the part where the log is gets drawn. */
    val only: Modifier = Modifier
        .onPlaced { page = it }
        .drawWithContent {
            val home = page?.takeIf { it.isAttached }
            val at = box?.takeIf { it.isAttached && home != null }?.let { home!!.localBoundingBoxOf(it) }
            if (at == null) drawContent()
            else clipRect(at.left, at.top, at.right, at.bottom) { this@drawWithContent.drawContent() }
        }
}

/** Shows a pinch's new font size in a toast. A new pinch replaces the toast instead of queueing another. */
@Composable
internal fun rememberFontSizeToast(): (from: Int, to: Int) -> Unit {
    val context = LocalContext.current
    val shown = remember { arrayOfNulls<Toast>(1) }
    return remember(context) {
        { from, to ->
            shown[0]?.cancel()
            shown[0] = Toast.makeText(context, "Font size $to (was $from)", Toast.LENGTH_SHORT)
                .also { it.show() }
        }
    }
}

/**
 * A two-finger pinch changes the terminal font size. It runs in the Initial pass and consumes only
 * multi-touch events, so one-finger scrolls and taps still reach the list. When the fingers lift,
 * [onEnd] gets the size before and after, unless the size ended where it started.
 */
internal fun Modifier.pinchToZoomFont(onChange: (Int) -> Unit, onEnd: (from: Int, to: Int) -> Unit) =
    pointerInput(Unit) {
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
                    } else {
                        val size =
                            (startSize * distance / startDistance).roundToInt().coerceIn(10, 24)
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
