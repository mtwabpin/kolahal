/**
 * Manual Review - Pending
 */
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
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.pageLeft
import androidx.compose.ui.semantics.pageRight
import androidx.compose.ui.semantics.semantics
import androidx.core.graphics.drawable.toDrawable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.mtwabpin.kolahal.apps.PackageEvents
import com.mtwabpin.kolahal.calls.CallEvents
import com.mtwabpin.kolahal.contacts.ContactPicker
import com.mtwabpin.kolahal.control.WallpaperMatch
import com.mtwabpin.kolahal.data.Backup
import com.mtwabpin.kolahal.data.HomeEntry
import com.mtwabpin.kolahal.data.HomeOrder
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.MaterialYou
import com.mtwabpin.kolahal.data.Script
import com.mtwabpin.kolahal.shader.Shaders
import com.mtwabpin.kolahal.shader.UsageWatch
import com.mtwabpin.kolahal.shader.WallpaperChoice
import com.mtwabpin.kolahal.status.DeviceStatus
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.UiEvent
import com.mtwabpin.kolahal.ui.CreateTextFile
import com.mtwabpin.kolahal.ui.HiddenBars
import com.mtwabpin.kolahal.ui.HidesBars
import com.mtwabpin.kolahal.ui.TextEditorActivity
import com.mtwabpin.kolahal.ui.editor.ScriptEditor
import com.mtwabpin.kolahal.ui.editor.ShaderEditor
import com.mtwabpin.kolahal.ui.feeds.FeedsScreen
import com.mtwabpin.kolahal.ui.hideBars
import com.mtwabpin.kolahal.ui.home.HomeClock
import com.mtwabpin.kolahal.ui.home.HomeScreenOverlay
import com.mtwabpin.kolahal.ui.home.LocalTerminalShown
import com.mtwabpin.kolahal.ui.home.LockAccessDialog
import com.mtwabpin.kolahal.ui.home.NotificationAccessDialog
import com.mtwabpin.kolahal.ui.home.OverlayAccessDialog
import com.mtwabpin.kolahal.ui.home.ResumeProfileDialog
import com.mtwabpin.kolahal.ui.home.TerminalScreen
import com.mtwabpin.kolahal.ui.onboarding.OnboardingScreen
import com.mtwabpin.kolahal.ui.scripts.GetScriptsScreen
import com.mtwabpin.kolahal.ui.scripts.ScriptLibraryScreen
import com.mtwabpin.kolahal.ui.scripts.ScriptSourcesScreen
import com.mtwabpin.kolahal.ui.scripts.ScriptsScreen
import com.mtwabpin.kolahal.ui.settings.HeaderPartsScreen
import com.mtwabpin.kolahal.ui.settings.HomePagesScreen
import com.mtwabpin.kolahal.ui.settings.SettingsOverHome
import com.mtwabpin.kolahal.ui.settings.SetupScreen
import com.mtwabpin.kolahal.ui.settings.ShadersOverHome
import com.mtwabpin.kolahal.ui.settings.ThemeBuilderScreen
import com.mtwabpin.kolahal.ui.shaders.GetShadersScreen
import com.mtwabpin.kolahal.ui.theme.HomePage
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.ui.theme.LocalHomePage
import com.mtwabpin.kolahal.ui.theme.MaterialYouPalettes
import com.mtwabpin.kolahal.ui.theme.Term
import com.mtwabpin.kolahal.ui.theme.applyTermSettings
import com.mtwabpin.kolahal.ui.theme.darkTheme
import com.mtwabpin.kolahal.ui.wallpaper.WallpaperWriter
import com.mtwabpin.kolahal.ui.widgets.LocalWidgetsShown
import com.mtwabpin.kolahal.ui.widgets.WidgetPanel
import com.mtwabpin.kolahal.ui.widgets.WidgetsStrip
import com.mtwabpin.kolahal.ui.widgets.WidgetPickerScreen
import com.mtwabpin.kolahal.ui.widgets.WidgetSettingsScreen
import com.mtwabpin.kolahal.ui.widgets.WidgetsScreen
import com.mtwabpin.kolahal.widgets.WidgetBinder
import com.mtwabpin.kolahal.widgets.WidgetHost
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class MainActivity : ComponentActivity(), HidesBars {
    private val vm: TerminalViewModel by viewModels()

    // Every sheet and dialog over home and the other screens hides what home hides (DialogBars).
    override val hiddenBars: HiddenBars get() = HiddenBars.of(vm.config[Keys.SCREEN_HIDE_BARS])

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.onNotificationPermission(
                it
            )
        }

    private val contactsPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.onContactsPermission(
                it
            )
        }

    private val contactPicker =
        registerForActivityResult(ContactPicker) {
            vm.onContactPicked(
                it
            )
        }

    private val calendarPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.onCalendarPermission(
                it
            )
        }

    private val callLogPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            vm.onCallLogPermission(
                it
            )
        }

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            vm.onScriptFolderPicked(
                it
            )
        }

    private val pickBackupFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            vm.backupActions.onBackupFolderPicked(
                it
            )
        }

    private val exportBackup =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
            vm.backupActions.exportBackup(
                it
            )
        }

    private val importBackup =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            vm.backupActions.importBackup(
                it
            )
        }

    private val fontPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { vm.importFont(it) }

    // File managers rarely set font MIME types correctly. importFont checks the name and header.
    private fun launchFontPicker() =
        fontPicker.launch(
            arrayOf(
                "font/ttf",
                "font/otf",
                "application/x-font-ttf",
                "application/octet-stream",
                "*/*"
            )
        )

    // Many file managers label .lua / .js files as octet-stream or text. So accept any file and filter by name.
    private val importScripts =
        registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
            vm.scriptActions.importScriptFiles(
                it
            )
        }

    private val exportScript =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) {
            vm.scriptActions.finishExportScript(
                it
            )
        }

    // `edit`: the picked or new file opens in the text editor, which is a window of its own
    // ([TextEditorActivity]). Any file may be picked: the editor refuses what isn't text.
    private val pickTextFile =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let { uri ->
                TextEditorActivity.open(
                    this,
                    uri
                )
            }
        }

    private val newTextFile =
        registerForActivityResult(CreateTextFile) {
            it?.let { uri ->
                TextEditorActivity.open(
                    this,
                    uri
                )
            }
        }

    /** Asks where to save [s], then saves it there ([com.mtwabpin.kolahal.terminal.ScriptActions.finishExportScript]). */
    private fun exportScriptFile(s: Script) {
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
        DeviceStatus.get(this).watchBattery(true)
        // Show the current time from the first frame back. Home is built again after Settings and similar screens.
        HomeClock.acquire(this)
        vm.onStarted(true)
    }

    override fun onStop() {
        super.onStop()
        WidgetHost.get(this).stopListening()
        DeviceStatus.get(this).watchBattery(false)
        HomeClock.release(this)
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
        shownConfig = android.content.res.Configuration(resources.configuration)
        // A widget was being added when Android stopped the launcher. Its result is still coming.
        widgetBinder.restore(savedInstanceState)
        // Use a translucent surface from the start, so the phone's wallpaper can show behind home
        // (log.wallpaper, widgets.wallpaper). The format can't change after the surface exists. (When
        // we tried, the window kept an opaque surface, and the pages' faint color piled up over old
        // frames.) When no wallpaper shows, the page color behind everything keeps home looking solid.
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        followTheme()
        handleEvents()
        setHomeContent()
    }

    /**
     * Applies the theme now, and again whenever settings, a previewed value or the screen change.
     * Also writes what the live wallpaper draws with, for its own process.
     */
    private fun followTheme() {
        applyPhoneColors()
        // The theme follows settings without reading config state in composition. Reading it there
        // would recompose the whole tree on every settings change.
        lifecycleScope.launch { vm.config.version.collect { applyTheme() } }
        // What the live wallpaper draws with, for its own process.
        if (WallpaperChoice.supported) lifecycleScope.launch {
            WallpaperWriter.run(applicationContext)
        }
        // Unlocks and screen time for the shaders that follow them, here and in the live wallpaper.
        if (Shaders.supported) lifecycleScope.launch {
            UsageWatch.run(applicationContext, lifecycle)
        }
        // The window for a one-off shader over another app, in a build that has it (app/build.gradle.kts).
        if (BuildConfig.SHADER_OVER_APPS) com.mtwabpin.kolahal.ui.shaders.ShaderOverlay.install(application)
        lifecycleScope.launch {
            androidx.compose.runtime.snapshotFlow { vm.previewed }.collect { applyTheme() }
        }
        lifecycleScope.launch { vm.screen.collect { if (vm.livePreview != null) applyTheme() } }
    }

    /**
     * Does what a screen asks for through `vm.events`: pickers and permissions, which need this
     * activity.
     */
    private fun handleEvents() {
        lifecycleScope.launch {
            vm.events.collect {
                when (it) {
                    UiEvent.RequestNotificationPermission -> requestNotifications()
                    UiEvent.RequestContactsPermission -> contactsPermission.launch(Manifest.permission.READ_CONTACTS)
                    UiEvent.PickContact ->
                        try {
                            contactPicker.launch(Unit)
                        } catch (_: android.content.ActivityNotFoundException) {
                            vm.onContactPickerMissing()
                        }

                    UiEvent.RequestCalendarPermission -> calendarPermission.launch(Manifest.permission.READ_CALENDAR)
                    UiEvent.RequestCallLogPermission -> callLogPermission.launch(Manifest.permission.READ_CALL_LOG)
                    UiEvent.PickFont -> launchFontPicker()
                    UiEvent.PickBackupFolder -> pickBackupFolder.launch(null)
                    UiEvent.PickScriptFolder -> pickFolder.launch(null)
                    UiEvent.ExportBackup -> exportBackup.launch(Backup.FILE_NAME)
                    UiEvent.ImportBackup -> importBackup.launch(
                        arrayOf(
                            "application/json",
                            "text/*",
                            "application/octet-stream"
                        )
                    )

                    UiEvent.ImportScripts -> importScripts.launch(arrayOf("*/*"))
                    is UiEvent.ExportScript -> exportScriptFile(it.script)
                    UiEvent.PickTextFile -> pickTextFile.launch(arrayOf("*/*"))
                    is UiEvent.NewTextFile -> newTextFile.launch(it.name)
                    is UiEvent.OpenTextFile -> TextEditorActivity.open(
                        this@MainActivity,
                        it.uri.toUri()
                    )
                    // The terminal screen handles these: they are about its log, prompt and menu.
                    UiEvent.ScrollToBottom, UiEvent.FocusPrompt, UiEvent.HideKeyboard, UiEvent.ShowQuick -> Unit
                }
            }
        }
    }

    /**
     * The window's content: the script editor or the shader editor, or home and the other screens, and
     * the dialogs over them.
     */
    private fun setHomeContent() {
        setContent {
            KolahalTheme {
                // GUI screens keep their state (filters, search, scroll) while the editor or another
                // screen is in front. Going home resets them.
                val saved = rememberSaveableStateHolder()
                val editing by vm.editing.collectAsStateWithLifecycle()
                val script = editing
                val editingShader by vm.shaderActions.editing.collectAsStateWithLifecycle()
                val shader = editingShader
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
                        androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .background(Term.bg),
                    ) {
                        ScriptEditor(
                            script = script,
                            jumpToLine = jumpLine,
                            lastRun = stored?.lastRun,
                            lastStatus = stored?.lastStatus,
                            onSave = { code, done -> vm.saveScript(script, code, done) },
                            onRun = { code, args, event, done ->
                                vm.runScript(
                                    script,
                                    code,
                                    args,
                                    event,
                                    done
                                )
                            },
                            runLines = { since -> vm.runLines(script.name, since) },
                            onClose = vm::closeEditor,
                        )
                    }
                } else if (shader != null) {
                    // A shader's code, from its sheet or `shader edit`. Solid, as the script editor is.
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .background(Term.bg),
                    ) {
                        ShaderEditor(
                            name = shader.name,
                            code = shader.code,
                            bundled = shader.bundled,
                            onSave = { code -> vm.shaderActions.save(shader.name, code) },
                            onClose = vm.shaderActions::closeEditor,
                        )
                    }
                } else {
                    val screen by vm.screen.collectAsStateWithLifecycle()
                    LaunchedEffect(screen) {
                        if (screen == Screen.TERMINAL) Screen.entries.forEach { saved.removeState(it.name) }
                    }
                    if (screen == Screen.TERMINAL) HomePager(vm) {
                        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
                            TerminalScreen(vm)
                            // `matrix`, `top`, the screensaver and the other screens run over the log page until a key or a tap.
                            HomeScreenOverlay(vm)
                        }
                    } else saved.SaveableStateProvider(screen.name) {
                        androidx.compose.foundation.layout.Box(
                            // Solid over the wallpaper too. Settings and Shaders are the exceptions: they show home
                            // through them while you hold the eye button.
                            androidx.compose.ui.Modifier
                                .fillMaxSize()
                                .then(
                                    if (screen == Screen.SETTINGS || screen == Screen.SHADERS) androidx.compose.ui.Modifier else androidx.compose.ui.Modifier.background(
                                        Term.bg
                                    ),
                                ),
                        ) {
                            when (screen) {
                                // A screen asks for a picker or a permission through vm.events (above). Only the
                                // widget screens get a callback, because adding a widget needs this activity.
                                Screen.SETTINGS -> SettingsOverHome(vm)
                                Screen.SCRIPTS -> ScriptsScreen(vm)
                                Screen.GET_SCRIPTS -> GetScriptsScreen(vm)
                                Screen.SCRIPT_SOURCES -> ScriptSourcesScreen(
                                    vm
                                )

                                Screen.SHADERS -> ShadersOverHome(vm)

                                Screen.GET_SHADERS -> GetShadersScreen(
                                    vm.shaderActions,
                                    vm.notice,
                                    open = vm::openScreen,
                                    onBack = vm::closeScreen
                                )

                                Screen.SCRIPT_LIBRARY -> ScriptLibraryScreen(
                                    vm
                                )

                                Screen.FEEDS -> FeedsScreen(vm)
                                Screen.SETUP -> SetupScreen(vm)
                                Screen.ONBOARDING -> OnboardingScreen(vm)
                                Screen.WIDGETS -> WidgetsScreen(
                                    vm,
                                    onSettings = { widgetBinder.reconfigure(it) },
                                    onReAdd = { entry, w -> widgetBinder.add(entry, w.key) })

                                Screen.WIDGET_PICKER -> WidgetPickerScreen(
                                    vm,
                                    onPick = { widgetBinder.add(it) })

                                Screen.WIDGET_SETTINGS -> WidgetSettingsScreen(vm)

                                Screen.THEME_BUILDER -> ThemeBuilderScreen(
                                    vm
                                )

                                Screen.HEADER_PARTS -> HeaderPartsScreen(vm)
                                Screen.HOME_PAGES -> HomePagesScreen(vm)
                                Screen.TERMINAL -> Unit
                            }
                        }
                    }
                }
                // `allow read-notifications` from the prompt, Setup or the tour shows this dialog over whatever screen is open.
                val askingNotifications by vm.notificationAccessAsk.collectAsStateWithLifecycle()
                if (askingNotifications) NotificationAccessDialog(
                    onAllow = { vm.answerNotificationAccess(true) },
                    onDismiss = { vm.answerNotificationAccess(false) },
                )
                // `allow overlay` from the prompt, Setup or the tour, when Display over other apps isn't given.
                val askingOverlay by vm.overlayAccessAsk.collectAsStateWithLifecycle()
                if (askingOverlay) OverlayAccessDialog(
                    onAllow = { vm.answerOverlayAccess(true) },
                    onDismiss = { vm.answerOverlayAccess(false) },
                )
                // `allow lock` from the prompt, Setup, the tour or the line a lock leaves, while the service is off.
                val askingLock by vm.lockAccessAsk.collectAsStateWithLifecycle()
                if (askingLock) LockAccessDialog(
                    onAllow = { vm.answerLockAccess(true) },
                    onDismiss = { vm.answerLockAccess(false) },
                )
                // A tap on an app whose profile is paused or locked, on any page of home: resume it and open the app?
                val resuming by vm.resumeAsk.collectAsStateWithLifecycle()
                resuming?.let { app ->
                    ResumeProfileDialog(
                        app,
                        onResume = { vm.answerResume(true) },
                        onDismiss = { vm.answerResume(false) })
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
        PackageEvents.get(this).recheck()
        CallEvents.get(this).check()
    }

    override fun onPause() {
        super.onPause()
        vm.onPause()
    }

    // Each touch and key, for the idle screensaver's wait (screen.saver-after).
    override fun onUserInteraction() {
        super.onUserInteraction()
        vm.onUserInput()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Same check as Launcher3: only a Home press while home is showing resets the prompt.
        // Returning from another app keeps what was typed.
        val alreadyHome = hasWindowFocus() &&
                (intent.flags and Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) != Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
        if (intent.hasCategory(Intent.CATEGORY_HOME) && alreadyHome) vm.onHomePressed()
    }

    /** Applies `screen.hide-bars`: none, status (the status bar only) or both (the status and navigation bars). */
    private fun applyBars() {
        val controller =
            androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        // Dark icons on a light theme.
        val light = Term.bg.luminance() > 0.5f
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
        // A swipe from the edge shows hidden bars for a moment, then they hide again.
        window.hideBars(hiddenBars)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Dialogs and other apps can bring the bars back, so apply the setting again when home gets focus.
        if (hasFocus) applyBars()
        if (hasFocus) vm.onWindowFocused()
    }

    /** The configuration home last had, so [onConfigurationChanged] can tell what changed. */
    private var shownConfig: android.content.res.Configuration? = null

    // uiMode and assetsPaths are in configChanges. So a dark mode change, or a new wallpaper's
    // colors, calls this instead of restarting the activity.
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPhoneColors()
        // Other apps' widgets took their colors from the configuration their views were made in.
        val changed = shownConfig?.diff(newConfig) ?: 0
        shownConfig = android.content.res.Configuration(newConfig)
        if (changed and (android.content.pm.ActivityInfo.CONFIG_UI_MODE or CONFIG_ASSETS_PATHS) != 0) WidgetHost.get(
            this
        ).redraw()
    }

    /**
     * Applies the theme when the phone's own colors may have changed: at start and on a
     * configuration change. The material-you theme takes its colors from the wallpaper, and
     * Android reports new wallpaper colors as a configuration change.
     */
    private fun applyPhoneColors() {
        MaterialYouPalettes.refresh(this)
        applyTheme()
    }

    private fun applyTheme() {
        // A value that Settings shows before it is kept takes the setting's place.
        val live = vm.previewed
        val theme = live?.theme ?: vm.config[Keys.THEME]
        val font = live?.font ?: vm.config[Keys.FONT]
        val loaded = applyTermSettings(
            this,
            vm.config,
            darkTheme(vm.config, resources.configuration),
            theme,
            live?.fontSize ?: vm.config[Keys.FONT_SIZE],
            font,
        )
        if (!loaded) vm.onFontUnavailable(font)
        // Skip this for a preview. The wallpaper changes only after the theme is kept.
        // - The wallpaper takes the theme's color fully opaque: a wallpaper can't be see-through.
        // - A Material You theme took its colors from the wallpaper, so the wallpaper doesn't follow it.
        if (live == null) WallpaperMatch.follow(
            this,
            Term.bg.copy(alpha = 1f).toArgb(),
            fromWallpaper = MaterialYou.isOne(theme) && MaterialYou.supported,
        )
        // log.wallpaper, widgets.wallpaper: the window shows the phone's wallpaper, and each home page
        // draws its color over it, as faintly as its setting says (Modifier.page).
        val wallpaper = vm.config[Keys.LOG_WALLPAPER] > 0 ||
                (vm.config[Keys.WIDGETS_SHOW] && vm.config[Keys.WIDGETS_WALLPAPER] > 0)
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

    /** The last wallpaper flag and page color set on the window ([applyTheme]). */
    private var windowLook: Pair<Boolean, Int>? = null

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * `widgets.show`: the terminal with the widgets pages beside it, page 1 nearest the log, then page 2
 * and so on outward ([com.mtwabpin.kolahal.widgets.WidgetPages]), in the order `home.pages` sets.
 * - Swipe sideways on home to reach them, a page at a time. Back or Home returns to the terminal
 *   from any page, in one step.
 * - A swipe that starts on a log line still belongs to the line (copy / delete, `log.swipe-left` and
 *   `log.swipe-right`). So the page swipe works from the header, empty space or the prompt, and from a
 *   line on a side set to none. With the log first and both swipes the defaults, the line's swipes are
 *   swapped: a swipe toward them copies, never deletes ([com.mtwabpin.kolahal.terminal.LineSwipes]).
 * - With `widgets.start`, page 1 is home's first page instead. Home opens on it, and Back and Home
 *   return to it. A key typed on a page brings up the log and shows the key in it.
 * - `home.pages` puts the pages in an order of its own ([HomeOrder]). Every place below comes from
 *   that one list, the default order while the setting is empty.
 */
@androidx.compose.runtime.Composable
private fun HomePager(
    vm: TerminalViewModel,
    terminal: @androidx.compose.runtime.Composable () -> Unit
) {
    val version by vm.config.version.collectAsStateWithLifecycle()
    val widgetsOn = androidx.compose.runtime.remember(version) { vm.config[Keys.WIDGETS_SHOW] }
    // The Assistant's page (`assistant.page`, full build only), placed by the page order (`home.pages`).
    val assistant = androidx.compose.runtime.remember(version) { BuildConfig.ASSISTANT && vm.config[Keys.ASSISTANT_PAGE] }
    if (!widgetsOn && !assistant) {
        terminal()
        return
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val widgets = androidx.compose.runtime.remember { WidgetHost.get(context) }
    val widgetPages by widgets.pages.collectAsStateWithLifecycle()
    val pages = if (widgetsOn) widgetPages else emptyList()
    val listed = androidx.compose.runtime.remember(version) { vm.config[Keys.HOME_PAGES] }
    val order = HomeOrder.of(HomeOrder.listed(listed), pages, assistant)
    // A page added or removed (a backup's), or a new order, lays home out again: each page's place on the pager moves.
    androidx.compose.runtime.key(pages.size, assistant, listed) {
        HomePages(vm, widgets, pages, order, terminal)
    }
}

/**
 * The place home's pager opens on. [keptId] is the page it showed last ([TerminalViewModel.homePageShown]),
 * null after a Home press: then [homePage], home's first page. A page that is gone by now (widgets
 * pages turned off while a screen was open) opens the log. Never a place outside the pager.
 */
internal fun homeStartPlace(keptId: String?, order: HomeOrder, homePage: Int): Int {
    val log = order.logPlace.coerceAtLeast(0)
    val place = if (keptId == null) homePage else order.placeOf(keptId)
    return place.takeIf { it in 0 until order.count } ?: log
}

/** [HomePager] for [pages] in [order]. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class) // LocalBringIntoViewSpec
@androidx.compose.runtime.Composable
private fun HomePages(
    vm: TerminalViewModel,
    widgets: WidgetHost,
    pages: List<com.mtwabpin.kolahal.widgets.WidgetPage>,
    order: HomeOrder,
    terminal: @androidx.compose.runtime.Composable () -> Unit
) {
    val version by vm.config.version.collectAsStateWithLifecycle()
    val count = order.count
    val assistantPlace = order.assistantPlace
    val terminalPage = order.logPlace
    // The pager's place of the widgets page at [index] (from 0).
    fun placeOf(index: Int): Int = order.placeOf(index)
    val widgetsFirst = androidx.compose.runtime.remember(version) { Keys.widgetsFirst(vm.config) }
    // The page that home opens on, and that Home and Back return to. The log while page 1 isn't there yet.
    val homePage = if (widgetsFirst) placeOf(0).takeIf { it >= 0 } ?: terminalPage else terminalPage
    // Home is laid out again each time a screen closes, and for a new order or a page added. It opens
    // on the page it showed before (vm.homePageShown), wherever that is now: Back from Arrange widgets
    // returns to that widgets page, and from the Assistant's settings to the Assistant.
    val startPage = homeStartPlace(vm.homePageShown, order, homePage)
    val pager = androidx.compose.foundation.pager.rememberPagerState(initialPage = startPage) { count }
    androidx.compose.runtime.LaunchedEffect(pager, terminalPage) {
        androidx.compose.runtime.snapshotFlow { pager.settledPage }
            .collect { place ->
                vm.homePageShown = order.at(place)?.id
                // The page shown last is where `widget add` puts a new widget.
                (order.at(place) as? HomeEntry.Widgets)?.page?.let { widgets.addTo = it.location }
            }
    }
    val homePresses by vm.homePresses.collectAsStateWithLifecycle()
    // A Home press, or a change of home's first page, returns to it. Not the first run:
    // that is home appearing again, on the page it had.
    val firstRun =
        androidx.compose.runtime.remember { java.util.concurrent.atomic.AtomicBoolean(true) }
    androidx.compose.runtime.LaunchedEffect(homePresses, widgetsFirst) {
        if (firstRun.getAndSet(false)) return@LaunchedEffect
        if (pager.currentPage != homePage) pager.animateScrollToPage(homePage)
    }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // From any page, Back goes straight to home's first page, in one step.
    BackHandler(enabled = pager.currentPage != homePage) {
        scope.launch { pager.animateScrollToPage(homePage) }
    }
    // A widget's tap whose run prints in the log turns home to the log.
    // Each turn runs in its own coroutine, as Back's does. A swipe during the turn cancels it, and
    // that must not end this collector: later taps must still turn the page.
    androidx.compose.runtime.LaunchedEffect(pager, terminalPage) {
        vm.logTurns.collect { this@LaunchedEffect.launch { pager.animateScrollToPage(terminalPage) } }
    }
    // `widget page`, `widget page 2` and the "Widgets page" quick action turn home to a widgets page.
    androidx.compose.runtime.LaunchedEffect(pager, terminalPage) {
        vm.widgetsTurns.collect { page ->
            if (pages.isEmpty()) return@collect
            val to = placeOf((page - 1).coerceIn(0, pages.size - 1))
            this@LaunchedEffect.launch { pager.animateScrollToPage(to) }
        }
    }
    // The page actions a screen reader or Switch Access offers: the swipe is the only other way across.
    // Each names the page it turns to: "Log", "Widgets" while there is one page, else the page's name.
    // Left is the page before, as on the pager's own actions; in a right-to-left layout, the page after.
    val rtl =
        androidx.compose.ui.platform.LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    fun nameOf(place: Int): String = HomeOrder.name(order.at(place) ?: HomeEntry.Log, pages)
    val before = (pager.currentPage - 1).takeIf { it >= 0 }
    val after = (pager.currentPage + 1).takeIf { it < count }
    val leftOf = if (rtl) after else before
    val rightOf = if (rtl) before else after
    fun turnTo(place: Int): () -> Boolean = {
        scope.launch { pager.animateScrollToPage(place) }
        true
    }
    if (widgetsFirst) {
        androidx.compose.runtime.LaunchedEffect(pager, terminalPage) {
            androidx.compose.runtime.snapshotFlow { pager.settledPage }
                .collect { page -> vm.onHomePageShown(log = page == terminalPage) }
        }
        // The prompt keeps the focus on a widgets page, so a key typed there goes into the prompt.
        // Then the log page comes in to show it. This turn has its own coroutine too, for the same reason.
        androidx.compose.runtime.LaunchedEffect(pager, terminalPage) {
            androidx.compose.runtime.snapshotFlow { vm.input.text.isNotEmpty() }.collect { typed ->
                if (typed && pager.currentPage != terminalPage) this@LaunchedEffect.launch {
                    pager.animateScrollToPage(
                        terminalPage
                    )
                }
            }
        }
    } else {
        androidx.compose.runtime.SideEffect { vm.onHomePageShown(log = true, opened = false) }
    }
    // The pager's own scrolling takes touches before its children do, so the app selector got none.
    // Our detector runs after the children instead. A swipe that the selector or a log line already
    // consumed never turns the page.
    // The page turns only when Kolahal turns it. When home is laid out again, Android gives the prompt
    // the focus, and the pager would bring it into view: Back from Arrange widgets ended on the log.
    // The pages get the usual behavior back, so the log's own list still scrolls to what has the focus.
    val pageFocusSpec = androidx.compose.foundation.gestures.LocalBringIntoViewSpec.current
    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize()) {
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides StayOnPage) {
            androidx.compose.foundation.pager.HorizontalPager(
                state = pager,
                // Every page stays composed, the log too. A turn of more than one page (`widget page 3`)
                // would otherwise drop the log while it is measured, prompt and keyboard with it, and
                // Compose stops with "performMeasureAndLayout called during measure layout". At most six.
                beyondViewportPageCount = count - 1,
                userScrollEnabled = false,
                key = { it },
                modifier = androidx.compose.ui.Modifier
                    .fillMaxSize()
                    .testTag("home-pager")
                    .semantics {
                        customActions = listOfNotNull(before, after).map { CustomAccessibilityAction(nameOf(it), turnTo(it)) }
                        leftOf?.let { pageLeft(nameOf(it), turnTo(it)) }
                        rightOf?.let { pageRight(nameOf(it), turnTo(it)) }
                    }
                    .pointerInput(pager) {
                        val tracker = androidx.compose.ui.input.pointer.util.VelocityTracker()
                        // The page the drag started on: a fling turns one page from there. The pager's own
                        // current page has moved on already once the drag passed half a page, and a fling
                        // from that would skip one.
                        var from = pager.currentPage
                        detectHorizontalDragGestures(
                            onDragStart = {
                                tracker.resetTracking()
                                from = pager.currentPage
                            },
                            onDragEnd = {
                                val v = tracker.calculateVelocity().x
                                val target = when {
                                    v < -800f -> from + 1
                                    v > 800f -> from - 1
                                    else -> (pager.currentPage + pager.currentPageOffsetFraction).let {
                                        kotlin.math.round(
                                            it
                                        ).toInt()
                                    }
                                }.coerceIn(0, count - 1)
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
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.foundation.gestures.LocalBringIntoViewSpec provides pageFocusSpec) {
                    val entry = order.at(page)
                    if (BuildConfig.ASSISTANT && entry == HomeEntry.Assistant) {
                        com.mtwabpin.kolahal.ui.assistant.AssistantPage(vm.config, onSettings = {
                            vm.settingsCategory.value = com.mtwabpin.kolahal.ui.settings.ASSISTANT_CATEGORY
                            vm.openScreen(com.mtwabpin.kolahal.terminal.Screen.SETTINGS)
                        })
                    } else if (entry == HomeEntry.Log) {
                        // The prompt and the header measure the screen's corners from where they are on this page,
                        // which slides while home turns, not from where they are in the window.
                        val homePage = androidx.compose.runtime.remember { HomePage() }
                        androidx.compose.runtime.CompositionLocalProvider(
                            LocalTerminalShown provides (pager.currentPage == terminalPage),
                            com.mtwabpin.kolahal.ui.home.LocalPageTypes provides (pager.currentPage == assistantPlace),
                            LocalHomePage provides homePage,
                        ) {
                            androidx.compose.foundation.layout.Box(
                                androidx.compose.ui.Modifier
                                    .fillMaxSize()
                                    .onPlaced { homePage.coordinates = it },
                            ) { terminal() }
                        }
                    } else if (entry is HomeEntry.Widgets) {
                        // The pager keeps the pages next to the one shown composed. A page's cards move and ask
                        // for runs only while the pager has settled on it.
                        androidx.compose.runtime.CompositionLocalProvider(LocalWidgetsShown provides (pager.settledPage == page)) {
                            WidgetPanel(vm, entry.page.location)
                        }
                    }
                }
            }
        }
        // The widgets pages' name and marks: one line over the pager, so it stays put while the pages move. It
        // shows over the widgets pages only, and its actions work on the page it names.
        if (pages.isNotEmpty()) WidgetsStrip(
            pager,
            order,
            pages,
            fade = { vm.widgetsSelectorFade.floatValue },
            onAdd = { location -> widgets.addTo = location; vm.openScreen(Screen.WIDGET_PICKER) },
            onArrange = { location -> widgets.addTo = location; vm.openScreen(Screen.WIDGETS) },
        )
    }
}

/** Home's pager never scrolls to bring something into view: it moves only when Kolahal turns it. */
private object StayOnPage : androidx.compose.foundation.gestures.BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        0f
}

/** ActivityInfo.CONFIG_ASSETS_PATHS (hidden): new resource overlays, as a new wallpaper's colors bring. */
private const val CONFIG_ASSETS_PATHS = Int.MIN_VALUE // 0x80000000
