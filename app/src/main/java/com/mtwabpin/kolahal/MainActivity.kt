package com.mtwabpin.kolahal

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mtwabpin.kolahal.data.Backup
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.MaterialYou
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.UiEvent
import com.mtwabpin.kolahal.ui.FeedsScreen
import com.mtwabpin.kolahal.ui.KolahalTheme
import com.mtwabpin.kolahal.ui.MaterialYouPalettes
import com.mtwabpin.kolahal.ui.ScriptEditor
import com.mtwabpin.kolahal.ui.ScriptsScreen
import com.mtwabpin.kolahal.ui.SettingsOverHome
import com.mtwabpin.kolahal.ui.SetupScreen
import com.mtwabpin.kolahal.ui.Term
import com.mtwabpin.kolahal.ui.TerminalScreen
import com.mtwabpin.kolahal.ui.TextEditorActivity
import com.mtwabpin.kolahal.ui.applyTermSettings
import com.mtwabpin.kolahal.ui.darkTheme
import com.mtwabpin.kolahal.ui.WidgetPickerScreen
import com.mtwabpin.kolahal.ui.WidgetsScreen
import com.mtwabpin.kolahal.widgets.WidgetBinder
import com.mtwabpin.kolahal.widgets.WidgetHost
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val vm: TerminalViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onNotificationPermission(it) }

    private val contactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onContactsPermission(it) }

    private val contactPicker =
        registerForActivityResult(com.mtwabpin.kolahal.contacts.ContactPicker) { vm.onContactPicked(it) }

    private val calendarPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onCalendarPermission(it) }

    private val callLogPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onCallLogPermission(it) }

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { vm.onScriptFolderPicked(it) }

    private val pickBackupFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { vm.backupActions.onBackupFolderPicked(it) }

    private val exportBackup =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { vm.backupActions.exportBackup(it) }

    private val importBackup =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { vm.backupActions.importBackup(it) }

    private val fontPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { vm.importFont(it) }

    // File managers rarely set font MIME types correctly. importFont checks the name and header.
    private fun launchFontPicker() =
        fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*"))

    // Many file managers label .lua / .js files as octet-stream or text. So accept any file and filter by name.
    private val importScripts =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.scriptActions.importScriptFiles(it) }

    private val exportScript =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { vm.scriptActions.finishExportScript(it) }

    // `edit`: the picked or new file opens in the text editor, which is a window of its own
    // ([TextEditorActivity]). Any file may be picked: the editor refuses what isn't text.
    private val pickTextFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> TextEditorActivity.open(this, uri) } }

    private val newTextFile =
        registerForActivityResult(com.mtwabpin.kolahal.ui.CreateTextFile) { it?.let { uri -> TextEditorActivity.open(this, uri) } }

    /** Asks where to save [s], then saves it there ([com.mtwabpin.kolahal.terminal.ScriptActions.finishExportScript]). */
    private fun exportScriptFile(s: com.mtwabpin.kolahal.data.Script) {
        vm.scriptActions.beginExportScript(s)
        exportScript.launch("${s.name}.${s.lang.ext}")
    }

    /** The add-a-widget flow (bind permission and configure activity). */
    private val widgetBinder = WidgetBinder(this) { _ ->
        if (vm.screen.value == Screen.WIDGET_PICKER) vm.closeScreen()
        if (vm.screen.value != Screen.WIDGETS) vm.openScreen(Screen.WIDGETS)
    }

    override fun onStart() {
        super.onStart()
        (application as? LauncherApp)?.homeShown()
        WidgetHost.get(this).startListening()
        com.mtwabpin.kolahal.status.DeviceStatus.get(this).watchBattery(true)
        // Show the current time from the first frame back. Home is built again after Settings and similar screens.
        com.mtwabpin.kolahal.ui.HomeClock.acquire(this)
        vm.onStarted(true)
    }

    override fun onStop() {
        super.onStop()
        WidgetHost.get(this).stopListening()
        com.mtwabpin.kolahal.status.DeviceStatus.get(this).watchBattery(false)
        com.mtwabpin.kolahal.ui.HomeClock.release(this)
        vm.onStarted(false)
    }

    override fun onDestroy() {
        // Release the widget views kept for a return to home, because this activity created them.
        WidgetHost.get(this).releaseViews(this)
        super.onDestroy()
    }

    @Deprecated("Needed for AppWidgetHost.startAppWidgetConfigureActivityForResult, which reports here")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        widgetBinder.onActivityResult(requestCode, resultCode)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // A widget was being added when Android stopped the launcher. Its result is still coming.
        widgetBinder.restore(savedInstanceState)
        // Use a translucent surface from the start, so the phone's wallpaper can show behind home
        // (log.wallpaper, widgets.wallpaper). The format can't change after the surface exists. (When
        // we tried, the window kept an opaque surface, and the pages' faint colour piled up over old
        // frames.) When no wallpaper shows, the page colour behind everything keeps home looking solid.
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        applyPhoneColours()
        // The theme follows settings without reading config state in composition. Reading it there
        // would recompose the whole tree on every settings change.
        lifecycleScope.launch { vm.config.version.collect { applyTheme() } }
        lifecycleScope.launch { androidx.compose.runtime.snapshotFlow { vm.previewed }.collect { applyTheme() } }
        lifecycleScope.launch { vm.screen.collect { if (vm.livePreview != null) applyTheme() } }
        lifecycleScope.launch {
            vm.events.collect {
                when (it) {
                    UiEvent.RequestNotificationPermission -> requestNotifications()
                    UiEvent.RequestContactsPermission -> contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    UiEvent.PickContact ->
                        try { contactPicker.launch(Unit) } catch (_: android.content.ActivityNotFoundException) { vm.onContactPickerMissing() }
                    UiEvent.RequestCalendarPermission -> calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    UiEvent.RequestCallLogPermission -> callLogPermission.launch(Manifest.permission.READ_CALL_LOG)
                    UiEvent.PickFont -> launchFontPicker()
                    UiEvent.PickBackupFolder -> pickBackupFolder.launch(null)
                    UiEvent.PickScriptFolder -> pickFolder.launch(null)
                    UiEvent.ExportBackup -> exportBackup.launch(Backup.FILE_NAME)
                    UiEvent.ImportBackup -> importBackup.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                    UiEvent.ImportScripts -> importScripts.launch(arrayOf("*/*"))
                    is UiEvent.ExportScript -> exportScriptFile(it.script)
                    UiEvent.PickTextFile -> pickTextFile.launch(arrayOf("*/*"))
                    is UiEvent.NewTextFile -> newTextFile.launch(it.name)
                    is UiEvent.OpenTextFile -> TextEditorActivity.open(this@MainActivity, android.net.Uri.parse(it.uri))
                    // The terminal screen handles these: they are about its log, prompt and menu.
                    UiEvent.ScrollToBottom, UiEvent.FocusPrompt, UiEvent.HideKeyboard, UiEvent.ShowQuick -> Unit
                }
            }
        }
        setContent {
            KolahalTheme {
                // GUI screens keep their state (filters, search, scroll) while the editor or another
                // screen is in front. Going home resets them.
                val saved = rememberSaveableStateHolder()
                val editing by vm.editing.collectAsStateWithLifecycle()
                val script = editing
                // A launcher never "goes back" from the home screen. The editor installs its own handler.
                BackHandler(enabled = script == null) { }
                if (script != null) {
                    val jumpLine by vm.editLine.collectAsStateWithLifecycle()
                    // The stored copy has the last run's time and result, for the editor's run feedback.
                    val scriptList by vm.scriptList.collectAsStateWithLifecycle()
                    val stored = scriptList.firstOrNull { it.id == script.id }
                    // When the window shows the phone's wallpaper (log.wallpaper, widgets.wallpaper),
                    // everything except home stays solid.
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.fillMaxSize().background(Term.bg),
                    ) { ScriptEditor(
                        script = script,
                        jumpToLine = jumpLine,
                        lastRun = stored?.lastRun,
                        lastStatus = stored?.lastStatus,
                        onSave = { code, done -> vm.saveScript(script, code, done) },
                        onRun = { code, args, event, done -> vm.runScript(script, code, args, event, done) },
                        runLines = { since -> vm.runLines(script.name, since) },
                        onClose = vm::closeEditor,
                    ) }
                } else {
                    val screen by vm.screen.collectAsStateWithLifecycle()
                    LaunchedEffect(screen) {
                        if (screen == Screen.TERMINAL) Screen.entries.forEach { saved.removeState(it.name) }
                    }
                    if (screen == Screen.TERMINAL) HomePager(vm) {
                        TerminalScreen(vm)
                    } else saved.SaveableStateProvider(screen.name) { androidx.compose.foundation.layout.Box(
                        // Solid over the wallpaper too. Settings is the exception: it shows home through
                        // it while you hold the eye button.
                        androidx.compose.ui.Modifier.fillMaxSize().then(
                            if (screen == Screen.SETTINGS) androidx.compose.ui.Modifier else androidx.compose.ui.Modifier.background(Term.bg),
                        ),
                    ) { when (screen) {
                        // A screen asks for a picker or a permission through vm.events (above). Only the
                        // widget screens get a callback, because adding a widget needs this activity.
                        Screen.SETTINGS -> SettingsOverHome(vm)
                        Screen.SCRIPTS -> ScriptsScreen(vm)
                        Screen.GET_SCRIPTS -> com.mtwabpin.kolahal.ui.GetScriptsScreen(vm)
                        Screen.SCRIPT_LIBRARY -> com.mtwabpin.kolahal.ui.ScriptLibraryScreen(vm)
                        Screen.FEEDS -> FeedsScreen(vm)
                        Screen.SETUP -> SetupScreen(vm)
                        Screen.ONBOARDING -> com.mtwabpin.kolahal.ui.OnboardingScreen(vm)
                        Screen.WIDGETS -> WidgetsScreen(vm, onReAdd = { entry, w -> widgetBinder.add(entry, w.key) })
                        Screen.WIDGET_PICKER -> WidgetPickerScreen(vm, onPick = { widgetBinder.add(it) })
                        Screen.THEME_BUILDER -> com.mtwabpin.kolahal.ui.ThemeBuilderScreen(vm)
                        Screen.HEADER_PARTS -> com.mtwabpin.kolahal.ui.HeaderPartsScreen(vm)
                        Screen.TERMINAL -> Unit
                    } } }
                }
                // `notifs enable` from the prompt, Setup or the tour shows this dialog over whatever screen is open.
                val askingNotifications by vm.notificationAccessAsk.collectAsStateWithLifecycle()
                if (askingNotifications) com.mtwabpin.kolahal.ui.NotificationAccessDialog(
                    onAllow = { vm.answerNotificationAccess(true) },
                    onDismiss = { vm.answerNotificationAccess(false) },
                )
                // A tap on an app whose profile is paused or locked, on any page of home: resume it and open the app?
                val resuming by vm.resumeAsk.collectAsStateWithLifecycle()
                resuming?.let { app ->
                    com.mtwabpin.kolahal.ui.ResumeProfileDialog(app, onResume = { vm.answerResume(true) }, onDismiss = { vm.answerResume(false) })
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        widgetBinder.save(outState)
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
        if (hasWindowFocus()) vm.onWindowFocused()
        com.mtwabpin.kolahal.apps.PackageEvents.get(this).recheck()
        com.mtwabpin.kolahal.calls.CallEvents.get(this).check()
    }

    override fun onPause() {
        super.onPause()
        vm.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Same check as Launcher3: only a Home press while home is showing resets the prompt.
        // Returning from another app keeps what was typed.
        val alreadyHome = hasWindowFocus() &&
            (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        if (intent.hasCategory(Intent.CATEGORY_HOME) && alreadyHome) vm.onHomePressed()
    }

    /** Applies `screen.bars`: show, hide (status bar only) or immersive (status and navigation bars). */
    private fun applyBars() {
        val controller = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        val status = androidx.core.view.WindowInsetsCompat.Type.statusBars()
        val nav = androidx.core.view.WindowInsetsCompat.Type.navigationBars()
        // A swipe from the edge shows hidden bars for a moment, then they hide again.
        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // Dark icons on a light theme.
        val light = Term.bg.luminance() > 0.5f
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
        when (vm.config[Keys.STATUS_BAR]) {
            "hide" -> { controller.hide(status); controller.show(nav) }
            "immersive" -> controller.hide(status or nav)
            else -> controller.show(status or nav)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs and other apps can bring the bars back, so apply the setting again when home gets focus.
        if (hasFocus) applyBars()
        if (hasFocus) vm.onWindowFocused()
    }

    // uiMode and assetsPaths are in configChanges. So a dark mode change, or a new wallpaper's
    // colours, calls this instead of restarting the activity.
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPhoneColours()
    }

    /**
     * Applies the theme when the phone's own colours may have changed: at start and on a
     * configuration change. The material-you theme takes its colours from the wallpaper, and
     * Android reports new wallpaper colours as a configuration change.
     */
    private fun applyPhoneColours() {
        MaterialYouPalettes.refresh(this)
        applyTheme()
    }

    private fun applyTheme() {
        // A value that Settings shows before it is kept takes the setting's place.
        val live = vm.previewed
        val theme = live?.theme ?: vm.config[Keys.THEME]
        val font = live?.font ?: vm.config[Keys.FONT]
        val loaded = applyTermSettings(
            this, vm.config, darkTheme(vm.config, resources.configuration), theme, live?.fontSize ?: vm.config[Keys.FONT_SIZE], font,
        )
        if (!loaded) vm.onFontUnavailable(font)
        // Skip this for a preview. The wallpaper changes only after the theme is kept.
        // - The wallpaper takes the theme's colour fully opaque: a wallpaper can't be see-through.
        // - A Material You theme took its colours from the wallpaper, so the wallpaper doesn't follow it.
        if (live == null) com.mtwabpin.kolahal.control.WallpaperMatch.follow(
            this, Term.bg.copy(alpha = 1f).toArgb(), fromWallpaper = MaterialYou.isOne(theme) && MaterialYou.supported,
        )
        // log.wallpaper, widgets.wallpaper: the window shows the phone's wallpaper, and each home page
        // draws its colour over it, as faintly as its setting says (Modifier.page).
        val wallpaper = vm.config[Keys.LOG_WALLPAPER] > 0 ||
            (vm.config[Keys.WIDGETS_PAGE] != "off" && vm.config[Keys.WIDGETS_WALLPAPER] > 0)
        // Change the window only when the look changes. Each flag change lays out the window again,
        // and dragging a slider in Settings previews (and calls this) on almost every frame.
        val look = wallpaper to Term.bg.toArgb()
        if (look != windowLook) {
            windowLook = look
            if (wallpaper) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                window.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                window.setBackgroundDrawable(Term.bg.toArgb().toDrawable())
            }
        }
        applyBars()
    }

    /** The last wallpaper flag and page colour set on the window ([applyTheme]). */
    private var windowLook: Pair<Boolean, Int>? = null

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * `widgets.page`: the terminal with a widgets page beside it (left or right).
 * - Swipe sideways on home to reach it. Back or Home returns to the terminal.
 * - A swipe that starts on a log line still belongs to the line (copy / delete). So the page swipe
 *   works from the header, empty space or the prompt.
 * - With `widgets.start`, the widgets page is home's first page instead. Home opens on it, and Back
 *   and Home return to it. A key typed there brings up the log and shows the key in it.
 */
@androidx.compose.runtime.Composable
private fun HomePager(vm: TerminalViewModel, terminal: @androidx.compose.runtime.Composable () -> Unit) {
    val version by vm.config.version.collectAsStateWithLifecycle()
    val side = androidx.compose.runtime.remember(version) { vm.config[Keys.WIDGETS_PAGE] }
    if (side == "off") {
        terminal()
        return
    }
    val terminalPage = if (side == "left") 1 else 0
    val widgetsFirst = androidx.compose.runtime.remember(version) { Keys.widgetsFirst(vm.config) }
    // The page that home opens on, and that Home and Back return to.
    val homePage = if (widgetsFirst) 1 - terminalPage else terminalPage
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = homePage) { 2 }
    val homePresses by vm.homePresses.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(homePresses, side, widgetsFirst) {
        if (pager.currentPage != homePage) pager.animateScrollToPage(homePage)
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    BackHandler(enabled = pager.currentPage != homePage) {
        scope.launch { pager.animateScrollToPage(homePage) }
    }
    // A widget's tap whose run prints in the log turns home to the log.
    // Each turn runs in its own coroutine, as Back's does. A swipe during the turn cancels it, and
    // that must not end this collector: later taps must still turn the page.
    androidx.compose.runtime.LaunchedEffect(pager, terminalPage) {
        vm.logTurns.collect { launch { pager.animateScrollToPage(terminalPage) } }
    }
    if (widgetsFirst) {
        androidx.compose.runtime.LaunchedEffect(pager, terminalPage) {
            androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { page -> vm.onHomePageShown(log = page == terminalPage) }
        }
        // The prompt keeps the focus on the widgets page, so a key typed there goes into the prompt.
        // Then the log page comes in to show it. This turn has its own coroutine too, for the same reason.
        androidx.compose.runtime.LaunchedEffect(pager, terminalPage) {
            androidx.compose.runtime.snapshotFlow { vm.input.text.isNotEmpty() }.collect { typed ->
                if (typed && pager.currentPage != terminalPage) launch { pager.animateScrollToPage(terminalPage) }
            }
        }
    } else {
        androidx.compose.runtime.SideEffect { vm.onHomePageShown(log = true, opened = false) }
    }
    // The pager's own scrolling takes touches before its children do, so the app selector got none.
    // Our detector runs after the children instead. A swipe that the selector or a log line already
    // consumed never turns the page.
    androidx.compose.foundation.pager.HorizontalPager(
        state = pager,
        beyondViewportPageCount = 1,
        userScrollEnabled = false,
        key = { it },
        modifier = androidx.compose.ui.Modifier.pointerInput(pager) {
            val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
            detectHorizontalDragGestures(
                onDragStart = { tracker.resetTracking() },
                onDragEnd = {
                    val v = tracker.calculateVelocity().x
                    val target = when {
                        v < -800f -> pager.currentPage + 1
                        v > 800f -> pager.currentPage - 1
                        else -> (pager.currentPage + pager.currentPageOffsetFraction).let { kotlin.math.round(it).toInt() }
                    }.coerceIn(0, 1)
                    scope.launch { pager.animateScrollToPage(target) }
                },
                onDragCancel = { scope.launch { pager.animateScrollToPage(pager.currentPage) } },
            ) { change, dx ->
                tracker.addPosition(change.uptimeMillis, change.position)
                change.consume()
                pager.dispatchRawDelta(-dx)
            }
        },
    ) { page ->
        if (page == terminalPage) {
            androidx.compose.runtime.CompositionLocalProvider(com.mtwabpin.kolahal.ui.LocalTerminalShown provides (pager.currentPage == terminalPage)) { terminal() }
        } else {
            // The pager keeps this page composed beside the log. Its cards move and ask for runs only
            // while the pager has settled on it.
            androidx.compose.runtime.CompositionLocalProvider(com.mtwabpin.kolahal.ui.LocalWidgetsShown provides (pager.settledPage == page)) {
                com.mtwabpin.kolahal.ui.WidgetPanel(vm)
            }
        }
    }
}
