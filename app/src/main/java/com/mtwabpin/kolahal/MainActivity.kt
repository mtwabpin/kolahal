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
import androidx.compose.ui.text.font.FontFamily
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mtwabpin.kolahal.data.Backup
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.UiEvent
import com.mtwabpin.kolahal.ui.FeedsScreen
import com.mtwabpin.kolahal.ui.KolahalTheme
import com.mtwabpin.kolahal.ui.Palettes
import com.mtwabpin.kolahal.ui.ScriptEditor
import com.mtwabpin.kolahal.ui.ScriptsScreen
import com.mtwabpin.kolahal.ui.SettingsOverHome
import com.mtwabpin.kolahal.ui.SetupScreen
import com.mtwabpin.kolahal.ui.Term
import com.mtwabpin.kolahal.ui.TermFonts
import com.mtwabpin.kolahal.ui.TerminalScreen
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

    // Font MIME types are rarely set right by file managers; importFont checks the name and header.
    private fun launchFontPicker() =
        fontPicker.launch(arrayOf("font/ttf", "font/otf", "application/x-font-ttf", "application/octet-stream", "*/*"))

    // Many file managers label .lua / .js as octet-stream or text, so accept anything and filter by name.
    private val importScripts =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.scriptActions.importScriptFiles(it) }

    private val exportScript =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { vm.scriptActions.finishExportScript(it) }

    /** Add-a-widget flow (bind permission + configure activity). */
    private val widgetBinder = WidgetBinder(this) { _ ->
        if (vm.screen.value == Screen.WIDGET_PICKER) vm.closeScreen()
        if (vm.screen.value != Screen.WIDGETS) vm.openScreen(Screen.WIDGETS)
    }

    override fun onStart() {
        super.onStart()
        WidgetHost.get(this).startListening()
        com.mtwabpin.kolahal.status.DeviceStatus.get(this).watchBattery(true)
        // Current from the first frame back: home is built again after Settings and the like.
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
        // The widget views kept for a return to home were made in this activity.
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
        // A widget being added when Android stopped the launcher: its result is on the way back.
        widgetBinder.restore(savedInstanceState)
        // A translucent surface from the start, so the phone's wallpaper can show behind home
        // (log.wallpaper, widgets.wallpaper): the format can't change once the surface exists (the
        // window kept an opaque one and the pages' faint colour piled up over old frames). With no
        // wallpaper showing, the page colour behind everything keeps it looking solid.
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        applyTheme()
        // Theme follows settings without reading config state in composition (which would recompose
        // the whole tree on every settings change).
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
                    else -> Unit
                }
            }
        }
        setContent {
            KolahalTheme {
                // GUI screens keep what they remember (filters, search, scroll) while the editor or
                // another screen is in front; going home starts them afresh.
                val saved = rememberSaveableStateHolder()
                val editing by vm.editing.collectAsStateWithLifecycle()
                val script = editing
                // A launcher never "goes back" off the home screen; the editor installs its own handler.
                BackHandler(enabled = script == null) { }
                if (script != null) {
                    val jumpLine by vm.editLine.collectAsStateWithLifecycle()
                    // The stored copy carries the last run's time and result, for the editor's [run] feedback.
                    val scriptList by vm.scriptList.collectAsStateWithLifecycle()
                    val stored = scriptList.firstOrNull { it.id == script.id }
                    // With the phone's wallpaper let through the window (log.wallpaper, widgets.wallpaper),
                    // everything but home stays solid.
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
                        TerminalScreen(
                            vm,
                            onPickFolder = { pickFolder.launch(null) },
                        )
                    } else saved.SaveableStateProvider(screen.name) { androidx.compose.foundation.layout.Box(
                        // Solid over the wallpaper too; Settings shows home through it while you hold the eye.
                        androidx.compose.ui.Modifier.fillMaxSize().then(
                            if (screen == Screen.SETTINGS) androidx.compose.ui.Modifier else androidx.compose.ui.Modifier.background(Term.bg),
                        ),
                    ) { when (screen) {
                        Screen.SETTINGS -> SettingsOverHome(
                            vm,
                            onExportBackup = { exportBackup.launch(Backup.FILE_NAME) },
                            onImportBackup = { importBackup.launch(arrayOf("application/json", "text/*", "application/octet-stream")) },
                            onImportFont = ::launchFontPicker,
                        )
                        Screen.SCRIPTS -> ScriptsScreen(
                            vm,
                            onImportFiles = { importScripts.launch(arrayOf("*/*")) },
                            onExportScript = { s ->
                                vm.scriptActions.beginExportScript(s)
                                exportScript.launch("${s.name}.${s.lang.ext}")
                            },
                        )
                        Screen.GET_SCRIPTS -> com.mtwabpin.kolahal.ui.GetScriptsScreen(vm)
                        Screen.SCRIPT_LIBRARY -> com.mtwabpin.kolahal.ui.ScriptLibraryScreen(vm)
                        Screen.FEEDS -> FeedsScreen(vm, onRequestNotifications = ::requestNotifications)
                        Screen.SETUP -> SetupScreen(vm)
                        Screen.ONBOARDING -> com.mtwabpin.kolahal.ui.OnboardingScreen(vm)
                        Screen.WIDGETS -> WidgetsScreen(vm, onReAdd = { entry, w -> widgetBinder.add(entry, w.key) })
                        Screen.WIDGET_PICKER -> WidgetPickerScreen(vm, onPick = { widgetBinder.add(it) })
                        Screen.THEME_BUILDER -> com.mtwabpin.kolahal.ui.ThemeBuilderScreen(vm)
                        Screen.TERMINAL -> Unit
                    } } }
                }
                // `notifs enable`, from the prompt, Setup or the tour: asked over whatever is showing.
                val askingNotifications by vm.notificationAccessAsk.collectAsStateWithLifecycle()
                if (askingNotifications) com.mtwabpin.kolahal.ui.NotificationAccessDialog(
                    onAllow = { vm.answerNotificationAccess(true) },
                    onDismiss = { vm.answerNotificationAccess(false) },
                )
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
        // Same check as Launcher3: only a Home press while home is showing resets the prompt;
        // returning from another app keeps whatever was typed.
        val alreadyHome = hasWindowFocus() &&
            (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        if (intent.hasCategory(Intent.CATEGORY_HOME) && alreadyHome) vm.onHomePressed()
    }

    /** `screen.bars`: show, hide (status bar only) or immersive (status and navigation bars). */
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
        // Dialogs and other apps can bring the bars back; re-apply when home regains focus.
        if (hasFocus) applyBars()
        if (hasFocus) vm.onWindowFocused()
    }

    /** The phone is in dark mode (or `theme.always-dark`): the theme's dark version shows. */
    private fun darkTheme(): Boolean = vm.config[Keys.THEME_ALWAYS_DARK] ||
        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES

    // uiMode is in configChanges: dark mode turning on or off comes here instead of a restart.
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme()
    }

    private fun applyTheme() {
        val live = vm.previewed
        Term.palette = Palettes.named(live?.theme ?: vm.config[Keys.THEME], darkTheme())
        // Not for a preview: the wallpaper changes only once it's kept.
        if (live == null) com.mtwabpin.kolahal.ui.WallpaperMatch.follow(this, com.mtwabpin.kolahal.ui.WallpaperMatch.themeColour())
        Term.fontSize = live?.fontSize ?: vm.config[Keys.FONT_SIZE]
        val font = live?.font ?: vm.config[Keys.FONT]
        val family = TermFonts.family(this, font)
        if (family == null) vm.onFontUnavailable(font)
        Term.fontFamily = family ?: FontFamily.Monospace
        Term.sans = Term.Sans.of(vm.config[Keys.FONT_SANS])
        Term.lineHeight = vm.config[Keys.LOG_LINE_HEIGHT]
        Term.tallerTaps = vm.config[Keys.LOG_TALLER_TAPS]
        Term.chipHeight = vm.config[Keys.CHIP_HEIGHT]
        // log.wallpaper, widgets.wallpaper: the window lets the phone's wallpaper through, and home's
        // pages draw their colour over it, each as faintly as it says (Modifier.page).
        val wallpaper = vm.config[Keys.LOG_WALLPAPER] > 0 ||
            (vm.config[Keys.WIDGETS_PAGE] != "off" && vm.config[Keys.WIDGETS_WALLPAPER] > 0)
        // Only when it changes: each flag set lays the window out again, and dragging a slider in
        // Settings previews (and comes here) on almost every frame.
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

    /** The wallpaper flag and page colour the window was last given ([applyTheme]). */
    private var windowLook: Pair<Boolean, Int>? = null

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * `widgets.page`: the terminal with a widgets page beside it (left or right). Swipe sideways on
 * home to reach it; Back or Home returns to the terminal. A swipe that starts on a log line still
 * belongs to the line (copy / delete), so the page swipe works from the header, empty space or
 * the prompt. With `widgets.start` the widgets page is home's first page instead: home opens on it,
 * Back and Home return to it, and a key typed there brings the log up with it.
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
    val widgetsFirst = androidx.compose.runtime.remember(version) { vm.config[Keys.WIDGETS_START] }
    // The page home opens on, and Home and Back return to.
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
    if (widgetsFirst) {
        androidx.compose.runtime.LaunchedEffect(pager, terminalPage) {
            androidx.compose.runtime.snapshotFlow { pager.settledPage }.collect { page -> vm.onHomePageShown(log = page == terminalPage) }
        }
        // The prompt keeps the focus on the widgets page, so a key typed there lands in it: the log comes to show it.
        androidx.compose.runtime.LaunchedEffect(pager, terminalPage) {
            androidx.compose.runtime.snapshotFlow { vm.input.text.isNotEmpty() }.collect { typed ->
                if (typed && pager.currentPage != terminalPage) pager.animateScrollToPage(terminalPage)
            }
        }
    } else {
        androidx.compose.runtime.SideEffect { vm.onHomePageShown(log = true, opened = false) }
    }
    // The pager's own scrolling grabs touches before its children, which starved the app selector.
    // Our detector runs after the children instead: a swipe the selector or a log line already
    // claimed (consumed) never turns the page.
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
            com.mtwabpin.kolahal.ui.WidgetPanel(vm)
        }
    }
}
