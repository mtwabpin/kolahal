/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.CrashReports
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.commands.Access
import com.mtwabpin.kolahal.commands.Accesses
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.CustomThemes
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.AccessNeeded
import com.mtwabpin.kolahal.ui.Action
import com.mtwabpin.kolahal.ui.assistant.assistantSettings
import com.mtwabpin.kolahal.ui.ActionGroup
import com.mtwabpin.kolahal.ui.FEATURE_TABS
import com.mtwabpin.kolahal.ui.FeatureTabs
import com.mtwabpin.kolahal.ui.GroupHeader
import com.mtwabpin.kolahal.ui.GuiListPage
import com.mtwabpin.kolahal.ui.NoMatchNote
import com.mtwabpin.kolahal.ui.SearchField
import com.mtwabpin.kolahal.ui.home.TerminalScreen
import com.mtwabpin.kolahal.ui.produceIo
import com.mtwabpin.kolahal.ui.rememberMissing
import com.mtwabpin.kolahal.ui.rememberNoticeSnackbar
import com.mtwabpin.kolahal.ui.shaders.ShadersScreen
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.widgets.WidgetPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings, with home drawn underneath while the user changes a look setting (drags a slider or
 * holds the eye button). Settings fades out around the control, and home shows how it will look.
 * Home here is only for viewing: it takes no touches.
 */
@Composable
fun SettingsOverHome(vm: TerminalViewModel) {
    Box(Modifier.fillMaxSize()) {
        HomeUnder(vm)
        SettingsScreen(vm)
    }
}

/** The Shaders tab, with home drawn underneath while its eye button is held. */
@Composable
fun ShadersOverHome(vm: TerminalViewModel) {
    Box(Modifier.fillMaxSize()) {
        HomeUnder(vm)
        ShadersScreen(vm.shaderActions, vm.notice, vm::openScreen, vm::closeScreen, vm.peek, vm::peek, vm::askNotificationAccess, vm::openShaderEditor, tabs = {
            FeatureTabs(vm, Screen.SHADERS)
        })
    }
}

/** Home, while a page over it shows it through itself ([TerminalViewModel.Peek]). It takes no touches. */
@Composable
private fun HomeUnder(vm: TerminalViewModel) {
    Box(Modifier.fillMaxSize()) {
        val peek by vm.peek.collectAsStateWithLifecycle()
        // Keep the last peek after it ends, so home fades out instead of disappearing at once.
        val lastPeek = remember { arrayOfNulls<TerminalViewModel.Peek>(1) }
        if (peek != null) lastPeek[0] = peek
        AnimatedVisibility(
            visible = peek != null,
            enter = fadeIn(tween(PEEK_FADE_MS)),
            exit = fadeOut(tween(PEEK_FADE_MS)),
        ) {
            lastPeek[0]?.let { p ->
                Box(Modifier.fillMaxSize()) {
                    // widgets.wallpaper affects the widgets page, so show that page instead of the terminal.
                    if (p.key == Keys.WIDGETS_WALLPAPER.key) WidgetPanel(vm)
                    else TerminalScreen(vm, preview = p)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                            },
                    )
                }
            }
        }
    }
}

/**
 * Material 3 Expressive settings.
 * - The main page has a search box and links to the setting categories, Setup, Backup and About.
 * - Each category opens its own page with grouped rows.
 * - Each row observes only its own value, so a change redraws only that row.
 */
@Composable
fun SettingsScreen(vm: TerminalViewModel) {
    val config = vm.config
    val version = vm.config.version.collectAsStateWithLifecycle()
    // The view model holds the open page, so returning from a manager screen (Feeds, Widgets) reopens it.
    var openId by vm.settingsCategory
    var query by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    val open = allCategories().firstOrNull { it.id == openId }
    // The Commands page's search. It is empty again each time that page opens.
    val commandsOpen = open?.id == COMMANDS_CATEGORY
    var commandQuery by androidx.compose.runtime.saveable.rememberSaveable(commandsOpen) {
        mutableStateOf(
            ""
        )
    }
    val crashPage = openId == CRASHES_PAGE
    val aboutPage = openId == ABOUT_PAGE
    // Crash reports open from About, so Back returns to About.
    val closePage = { openId = if (crashPage) ABOUT_PAGE else null }
    val pageSubtitle = open?.subtitle ?: when {
        crashPage -> "Kept on this phone"
        aboutPage -> "Kolahal"
        else -> null
    }
    // For Look & feel → Your themes.
    val customThemes by CustomThemes.themes.collectAsStateWithLifecycle()
    val ownThemes = remember(customThemes) { customThemes.keys.sorted() }
    val themeInUse by remember { derivedStateOf { version.value; config[Keys.THEME] } }
    val inUse = themeInUse
    // Some settings only apply when another setting is on (for example, the key bar's rows need the
    // key bar). They show only once that setting is on.
    val applicable by remember {
        derivedStateOf {
            version.value; Keys.ALL.filterTo(HashSet()) {
            applies(
                it,
                config
            )
        }
        }
    }
    // Read it here, not in the list's content below. Reading a derived value while the list measures
    // can deadlock with a snapshot applied from another thread (Compose tests do that).
    val applicableKeys = applicable
    // Settings that need an access Android hasn't given have a line and a button in their place ([NEEDS_ACCESS]).
    val missing = rememberMissing(remember { NEEDS_ACCESS.values.flatten().toSet() })
    val ask = { access: Access -> vm.runCommand(Accesses.command(access)) }
    val context = LocalContext.current
    // For About → Crash reports. They are read off the main thread, and read again after a delete.
    var crashReads by remember { mutableIntStateOf(0) }
    var openCrash by remember { mutableStateOf<CrashRow?>(null) }
    val crashes by produceIo(
        null,
        crashReads,
        what = "Reading the crash reports"
    ) { crashRows(context) }
    val crashList = crashes
    // While home shows through Settings (slider dragged or eye held), everything except that slider fades out.
    val peek by vm.peek.collectAsStateWithLifecycle()
    val isHome by vm.isHome.collectAsStateWithLifecycle()
    // Read only at draw time, so each fade frame only redraws and does not recompose Settings.
    val shown = androidx.compose.animation.core.animateFloatAsState(
        if (peek == null) 1f else 0f, tween(PEEK_FADE_MS), label = "peek",
    )
    val tapScope = rememberCoroutineScope()
    val importTheme =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::importTheme)
    // Back closes, in order: the Commands page's search, an open category page, then the search, then Settings.
    BackHandler {
        when {
            commandsOpen && commandQuery.isNotEmpty() -> commandQuery = ""
            open != null || aboutPage || crashPage -> closePage()
            query.isNotEmpty() -> query = ""
            else -> vm.closeScreen()
        }
    }

    GuiTheme {
        Box(Modifier.fillMaxSize()) {
            val snackbar = rememberNoticeSnackbar(vm)
            // A message from the About and crash pages (ui/About.kt): for example, no app can open a link.
            val say: (String) -> Unit = { text -> tapScope.launch { snackbar.showSnackbar(text) } }
            // "Reset …" with Undo, after a setting is reset to its default.
            val undo = undoMessage(tapScope, snackbar)
            // A tap on a number setting's title or value says to hold it to type the number.
            val hint: (String) -> Unit = { text ->
                tapScope.launch {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(text)
                }
            }

            GuiListPage(
                title = {
                    Text(
                        open?.title
                            ?: if (crashPage) "Crash reports" else if (aboutPage) "About" else "Settings"
                    )
                },
                subtitle = if (pageSubtitle != null) {
                    { Text(pageSubtitle) }
                } else null,
                onBack = { if (open != null || aboutPage || crashPage) closePage() else vm.closeScreen() },
                snackbar = snackbar,
                actions = {
                    PEEK_PAGES[open?.id]?.let { typing ->
                        PeekButton(
                            onHold = { holding ->
                                vm.peek(
                                    if (holding) TerminalViewModel.Peek(
                                        null,
                                        typing
                                    ) else null
                                )
                            },
                            onTap = {
                                tapScope.launch {
                                    snackbar.currentSnackbarData?.dismiss()
                                    snackbar.showSnackbar("Tap and hold the eye to see the home screen")
                                }
                            },
                        )
                    }
                },
                fade = { shown.value },
                top = 0.dp,
                verticalArrangement = Arrangement.Top,
                // The Header page pins a preview of the top of home, so the user sees each change.
                // Only when the header is on: when it is off, there is no header to show.
                pinned = if (remember(version.value) { config[Keys.HEADER_SHOW] } && open?.id == HEADER_CATEGORY) {
                    { PeekFade(peek) { HeaderPreview(vm) } }
                } else null,
            ) {
                if (open?.id == ASSISTANT_CATEGORY) {
                    assistantSettings(vm, version, undo, say)
                    return@GuiListPage
                }
                if (open != null) {
                    open.manage?.let { (title, subtitle, screen) ->
                        item(key = "manage:${open.id}") {
                            PeekFade(peek) {
                                Spacer(Modifier.height(8.dp))
                                ActionGroup(
                                    listOf(
                                        Action(
                                            open.icon,
                                            title,
                                            subtitle,
                                            chevron = true
                                        ) { vm.openScreen(screen) })
                                )
                            }
                        }
                    }
                    for ((title, all) in open.groups) {
                        val shows = groupShows(all.filter { it in applicableKeys }, missing)
                        val keys = shows.keys
                        if (shows.isEmpty) continue
                        item(key = "h:${open.id}:$title") {
                            PeekFade(
                                peek,
                                modifier = Modifier.animateItem()
                            ) { if (title != null) GroupHeader(title) else Spacer(Modifier.height(8.dp)) }
                        }
                        keys.forEachIndexed { index, key ->
                            item(key = key.key) {
                                PeekFade(
                                    peek,
                                    keep = peek?.key == key.key,
                                    modifier = Modifier.animateItem()
                                ) {
                                    SettingItem(vm, key, version, index, keys.size, undo, hint)
                                }
                            }
                        }
                        accessItems(shows, "${open.id}:$title", { peek }, ask)
                    }
                    if (open.id == BACKUP_CATEGORY) {
                        item(key = "h:backup-files") { Spacer(Modifier.height(16.dp)) }
                        item(key = "backup") {
                            val backupVersion by vm.backupActions.backupVersion.collectAsStateWithLifecycle()
                            val folder by produceState<String?>(null, backupVersion) {
                                value =
                                    withContext(Dispatchers.IO) { vm.backupActions.backupFolderLine() }
                            }
                            ActionGroup(
                                listOf(
                                    Action(
                                        R.drawable.ic_folder,
                                        "Backup folder",
                                        folder
                                            ?: "Not chosen yet: automatic backups and Back up now write there"
                                    ) { vm.askBackupFolder(false) },
                                    Action(
                                        R.drawable.ic_download,
                                        "Back up now",
                                        "Today's backup file, in the backup folder",
                                        onClick = { vm.backupActions.backUpNow() }),
                                    Action(
                                        R.drawable.ic_download,
                                        "Export everything",
                                        "Settings, apps, scripts, todos, notes, reminders, feeds and the log in one file (notification and call lines stay on the phone)",
                                        onClick = vm::requestBackupExport
                                    ),
                                    Action(
                                        R.drawable.ic_upload,
                                        "Import a backup",
                                        "Restores everything (replaces todos, notes, feeds and the log)",
                                        onClick = vm::requestBackupImport
                                    ),
                                )
                            )
                        }
                    }
                    if (commandsOpen) commandsPage(vm, version, commandQuery) { commandQuery = it }
                    if (open.id == "look") {
                        // What the wallpaper can become, as `wallpaper` lists it: the theme's color, a shader, Android's list.
                        item(key = "h:wallpaper") { PeekFade(peek) { GroupHeader("Wallpaper") } }
                        item(key = "wallpaper") {
                            PeekFade(peek) {
                                WallpaperGroup(
                                    inUse.takeIf(com.mtwabpin.kolahal.data.MaterialYou::isOne) ?: com.mtwabpin.kolahal.data.MaterialYou.NAME,
                                    say,
                                ) { vm.openScreen(Screen.SHADERS) }
                            }
                        }
                        item(key = "h:own-themes") { PeekFade(peek) { GroupHeader("Your themes") } }
                        item(key = "own-themes") {
                            PeekFade(peek) {
                                ActionGroup(
                                    ownThemes.map { name ->
                                        Action(
                                            R.drawable.ic_palette,
                                            name,
                                            if (name == inUse) "In use · tap to change it" else "Tap to change it",
                                            chevron = true
                                        ) {
                                            vm.openThemeBuilder(name)
                                        }
                                    } + Action(
                                        R.drawable.ic_add,
                                        "Create a theme",
                                        "Start from the theme in use and pick any color",
                                        chevron = true
                                    ) {
                                        vm.openThemeBuilder(null)
                                    } + Action(
                                        R.drawable.ic_upload,
                                        "Import a theme",
                                        "A theme file someone shared, or one you exported"
                                    ) {
                                        importTheme.launch(
                                            arrayOf(
                                                "application/json",
                                                "text/*",
                                                "application/octet-stream"
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    }
                    return@GuiListPage
                }

                if (aboutPage) {
                    aboutItems(
                        crashList?.size,
                        isHome = isHome,
                        onRefreshIsHome = { vm.refreshIsHome() },
                        onTour = { vm.openScreen(Screen.ONBOARDING) },
                        onCrashReports = { openId = CRASHES_PAGE },
                        say = say
                    )
                    return@GuiListPage
                }

                if (crashPage) {
                    crashReportsPage(
                        crashList,
                        onOpen = { openCrash = it },
                        onDeleteAll = {
                            val n = CrashReports.clear(context)
                            crashReads++
                            say(if (n == 1) "Deleted 1 crash report" else "Deleted $n crash reports")
                        },
                    )
                    return@GuiListPage
                }

                item(key = "tabs") { PeekFade(peek) { FeatureTabs(vm, Screen.SETTINGS) } }
                item(key = "search") {
                    PeekFade(peek) {
                        SearchField(
                            query,
                            { query = it },
                            "Search settings",
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 4.dp),
                            clear = "Clear",
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        )
                    }
                }

                if (query.isNotBlank()) {
                    val hits = allCategories().flatMap { c ->
                        c.keys.filter {
                            it in applicableKeys && matches(
                                it,
                                query.trim()
                            )
                        }.map { c to it }
                    }
                    val screens = SCREEN_ROWS.filter { it.matches(query.trim()) }
                    if (hits.isEmpty() && screens.isEmpty()) item(key = "nohits") { NoMatchNote("No setting matches “${query.trim()}”") }
                    if (screens.isNotEmpty()) item(key = "hit:screens") {
                        PeekFade(peek) {
                            Column(Modifier.padding(top = 12.dp)) {
                                // A tab (Shaders) is switched to, as its tab does; another screen opens over Settings.
                                ActionGroup(screens.map { it.action { s -> if (s in FEATURE_TABS) vm.switchTab(s) else vm.openScreen(s) } })
                            }
                        }
                    }
                    hits.groupBy({ it.first }, { it.second }).forEach { (c, found) ->
                        val shows = groupShows(found, missing)
                        val keys = shows.keys
                        item(key = "h:hit:${c.id}") { PeekFade(peek) { GroupHeader(c.title) } }
                        keys.forEachIndexed { index, key ->
                            item(key = "hit:" + key.key) {
                                PeekFade(
                                    peek,
                                    keep = peek?.key == key.key
                                ) { SettingItem(vm, key, version, index, keys.size, undo, hint) }
                            }
                        }
                        accessItems(shows, "hit:${c.id}", { peek }, ask)
                    }
                    return@GuiListPage
                }

                item(key = "h:settings") { Spacer(Modifier.height(12.dp)) }
                item(key = "categories") {
                    // Feed settings are in the Feeds tab, but search here still finds them.
                    ActionGroup(allCategories().filter { it.id != FEEDS_CATEGORY && it.id != BACKUP_CATEGORY }
                        .flatMap { c ->
                            listOf(Action(c.icon, c.title, c.subtitle, chevron = true) {
                                openId = c.id
                            }) + SCREEN_ROWS.filter { it.after == c.id }
                                .map { it.action(vm::openScreen) }
                        })
                }
                item(key = "h:more") { Spacer(Modifier.height(16.dp)) }
                item(key = "more") {
                    val backup = allCategories().first { it.id == BACKUP_CATEGORY }
                    ActionGroup(
                        listOf(
                            Action(
                                R.drawable.ic_check,
                                "Setup checklist",
                                "Default launcher, permissions, battery",
                                chevron = true
                            ) { vm.openScreen(Screen.SETUP) },
                            Action(
                                backup.icon,
                                backup.title,
                                backup.subtitle,
                                chevron = true
                            ) { openId = BACKUP_CATEGORY },
                            Action(
                                R.drawable.ic_info,
                                "About",
                                "Guide, tour, crash reports, version",
                                chevron = true
                            ) { openId = ABOUT_PAGE },
                        )
                    )
                }
            }
            if (peek != null) PeekHint(Modifier.align(Alignment.TopCenter))
            // The tapped crash report. After a delete, the list is read again.
            openCrash?.let { c ->
                CrashSheet(
                    c,
                    onDismiss = { openCrash = null },
                    onDeleted = { crashReads++ },
                    say = say
                )
            }
        }
    }
}

/** The row that opens this screen. */
private fun ScreenRow.action(open: (Screen) -> Unit) =
    Action(icon, title, subtitle, chevron = true) { open(screen) }

/**
 * Under a group's settings, the line and the button for each access that [shows] asks for ([groupShows]).
 * [group] keeps their keys apart from another group's.
 */
private fun androidx.compose.foundation.lazy.LazyListScope.accessItems(
    shows: GroupShows,
    group: String,
    peek: () -> TerminalViewModel.Peek?,
    ask: (Access) -> Unit,
) {
    for (word in shows.asks) item(key = "access:$group:$word") {
        PeekFade(peek(), modifier = Modifier.animateItem()) {
            AccessNeeded(
                word,
                ask,
                Modifier.padding(start = 16.dp, end = 16.dp, top = if (shows.keys.isEmpty()) 0.dp else 8.dp),
            )
        }
    }
}

/**
 * Adds a settings category's grouped rows to a list in another screen. For example, the Feeds tab
 * shows the feeds category. The caller must read [applicable] outside the list's content (see [SettingsScreen]).
 * [undo] shows "Reset …" with Undo in the page's snackbar after a reset.
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.categorySettings(
    vm: TerminalViewModel,
    id: String,
    version: State<Int>,
    applicable: Set<ConfigKey<*>>,
    undo: UndoMessage? = null,
) {
    val category = allCategories().firstOrNull { it.id == id } ?: return
    for ((title, all) in category.groups) {
        val keys = all.filter { it in applicable }
        if (keys.isEmpty()) continue
        item(key = "h:$id:$title") {
            if (title != null) GroupHeader(title) else Spacer(
                Modifier.height(
                    8.dp
                )
            )
        }
        keys.forEachIndexed { index, key ->
            item(key = key.key) { SettingItem(vm, key, version, index, keys.size, undo) }
        }
    }
}
