package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.CrashReports
import com.mtwabpin.kolahal.Links
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.CustomThemes
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
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
                        Modifier.fillMaxSize().pointerInput(Unit) {
                            awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
                        },
                    )
                }
            }
        }
        SettingsScreen(vm)
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
    val applicable by remember { derivedStateOf { version.value; Keys.ALL.filterTo(HashSet()) { applies(it, config) } } }
    // Read it here, not in the list's content below. Reading a derived value while the list measures
    // can deadlock with a snapshot applied from another thread (Compose tests do that).
    val applicableKeys = applicable
    val context = LocalContext.current
    // For About → Crash reports. They are read off the main thread, and read again after a delete.
    var crashReads by remember { mutableIntStateOf(0) }
    var openCrash by remember { mutableStateOf<CrashRow?>(null) }
    val crashes by produceState<List<CrashRow>?>(null, crashReads) {
        value = withContext(Dispatchers.IO) { crashRows(context) }
    }
    val crashList = crashes
    // While home shows through Settings (slider dragged or eye held), everything except that slider fades out.
    val peek by vm.peek.collectAsStateWithLifecycle()
    // Read only at draw time, so each fade frame only redraws and does not recompose Settings.
    val shown = androidx.compose.animation.core.animateFloatAsState(
        if (peek == null) 1f else 0f, tween(PEEK_FADE_MS), label = "peek",
    )
    val tapScope = rememberCoroutineScope()
    val importTheme = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument(), vm::importTheme)
    // Back closes, in order: an open category page, then the search, then Settings.
    BackHandler {
        when {
            open != null || aboutPage || crashPage -> closePage()
            query.isNotEmpty() -> query = ""
            else -> vm.closeScreen()
        }
    }

    GuiTheme {
      Box(Modifier.fillMaxSize()) {
        val snackbar = rememberNoticeSnackbar(vm)

        GuiListPage(
            title = { Text(open?.title ?: if (crashPage) "Crash reports" else if (aboutPage) "About" else "Settings") },
            subtitle = if (pageSubtitle != null) { { Text(pageSubtitle) } } else null,
            onBack = { if (open != null || aboutPage || crashPage) closePage() else vm.closeScreen() },
            snackbar = snackbar,
            actions = {
                PEEK_PAGES[open?.id]?.let { typing ->
                    PeekButton(
                        onHold = { holding -> vm.peek(if (holding) TerminalViewModel.Peek(null, typing) else null) },
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
            pinned = if (remember(version.value) { config[Keys.HEADER_SHOW] } && open?.id == HEADER_CATEGORY) { { PeekFade(peek) { HeaderPreview(vm) } } } else null,
        ) {
            if (open != null) {
                open.manage?.let { (title, subtitle, screen) ->
                    item(key = "manage:${open.id}") {
                        PeekFade(peek) {
                            Spacer(Modifier.height(8.dp))
                            ActionGroup(listOf(Action(open.icon, title, subtitle, chevron = true) { vm.openScreen(screen) }))
                        }
                    }
                }
                for ((title, all) in open.groups) {
                    val keys = all.filter { it in applicableKeys }
                    if (keys.isEmpty()) continue
                    item(key = "h:${open.id}:$title") {
                        PeekFade(peek, modifier = Modifier.animateItem()) { if (title != null) GroupHeader(title) else Spacer(Modifier.height(8.dp)) }
                    }
                    keys.forEachIndexed { index, key ->
                        item(key = key.key) {
                            PeekFade(peek, keep = peek?.key == key.key, modifier = Modifier.animateItem()) {
                                SettingItem(vm, config, key, version, index, keys.size)
                            }
                        }
                    }
                }
                if (open.id == BACKUP_CATEGORY) {
                    item(key = "h:backup-files") { Spacer(Modifier.height(16.dp)) }
                    item(key = "backup") {
                        val backupVersion by vm.backupActions.backupVersion.collectAsStateWithLifecycle()
                        val folder by produceState<String?>(null, backupVersion) { value = withContext(Dispatchers.IO) { vm.backupActions.backupFolderLine() } }
                        ActionGroup(
                            listOf(
                                Action(R.drawable.ic_folder, "Backup folder", folder ?: "Not chosen yet: automatic backups and Back up now write there") { vm.askBackupFolder(false) },
                                Action(R.drawable.ic_download, "Back up now", "Today's backup file, in the backup folder", onClick = { vm.backupActions.backUpNow() }),
                                Action(R.drawable.ic_download, "Export everything", "Settings, apps, scripts, todos, notes, reminders, feeds and the log in one file (notification and call lines stay on the phone)", onClick = vm::requestBackupExport),
                                Action(R.drawable.ic_upload, "Import a backup", "Restores everything (replaces todos, notes, feeds and the log)", onClick = vm::requestBackupImport),
                            )
                        )
                    }
                }
                if (open.id == COMMANDS_CATEGORY) commandsPage(vm, version)
                if (open.id == "look") {
                    item(key = "h:own-themes") { PeekFade(peek) { GroupHeader("Your themes") } }
                    item(key = "own-themes") {
                        PeekFade(peek) {
                            ActionGroup(
                                ownThemes.map { name ->
                                    Action(R.drawable.ic_palette, name, if (name == inUse) "In use · tap to change it" else "Tap to change it", chevron = true) {
                                        vm.openThemeBuilder(name)
                                    }
                                } + Action(R.drawable.ic_add, "Create a theme", "Start from the theme in use and pick any colour", chevron = true) {
                                    vm.openThemeBuilder(null)
                                } + Action(R.drawable.ic_upload, "Import a theme", "A theme file someone shared, or one you exported") {
                                    importTheme.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                                },
                            )
                        }
                    }
                }
                return@GuiListPage
            }

            if (aboutPage) {
                item(key = "h:about") { Spacer(Modifier.height(8.dp)) }
                item(key = "about") {
                    val kept = when (val n = crashList?.size) {
                        null -> "Reports kept on this phone"
                        0 -> "None so far"
                        1 -> "1 report kept on this phone"
                        else -> "$n reports kept on this phone"
                    }
                    ActionGroup(
                        listOf(
                            Action(R.drawable.ic_book, "Guide", "How everything works, on the Kolahal website") {
                                if (!openGuide(context)) tapScope.launch { snackbar.showSnackbar("No browser to open ${Links.GUIDE}") }
                            },
                            Action(R.drawable.ic_info, "Tour", "How Kolahal works, and a few initial settings", chevron = true) { vm.openScreen(Screen.ONBOARDING) },
                            Action(R.drawable.ic_forum, "Discord", "Questions, ideas and help from other people using Kolahal") {
                                if (!openLink(context, Links.DISCORD)) tapScope.launch { snackbar.showSnackbar("No app to open ${Links.DISCORD}") }
                            },
                            Action(R.drawable.ic_code, "Source code", "Kolahal on GitHub") {
                                if (!openLink(context, Links.SOURCE)) tapScope.launch { snackbar.showSnackbar("No browser to open ${Links.SOURCE}") }
                            },
                            Action(R.drawable.ic_bug, "Crash reports", kept, chevron = true) { openId = CRASHES_PAGE },
                            Action(R.drawable.ic_mail, "Email us", Links.DEVELOPER_EMAIL) {
                                if (!emailDeveloper(context, VERSION_TEXT, aboutThisPhone())) {
                                    tapScope.launch { snackbar.showSnackbar("No app on this phone can send email") }
                                }
                            },
                            Action(R.drawable.ic_info, "Version", "$VERSION_TEXT · tap to copy") {
                                context.getSystemService(android.content.ClipboardManager::class.java)
                                    ?.setPrimaryClip(android.content.ClipData.newPlainText("Kolahal version", VERSION_TEXT))
                                // Android 13 and later show their own "copied" confirmation.
                                if (android.os.Build.VERSION.SDK_INT < 33) tapScope.launch { snackbar.showSnackbar("Copied") }
                            },
                        )
                    )
                }
                return@GuiListPage
            }

            if (crashPage) {
                crashReportsPage(
                    crashList,
                    onOpen = { openCrash = it },
                    onDeleteAll = {
                        val n = com.mtwabpin.kolahal.CrashReports.clear(context)
                        crashReads++
                        tapScope.launch { snackbar.showSnackbar(if (n == 1) "Deleted 1 crash report" else "Deleted $n crash reports") }
                    },
                )
                return@GuiListPage
            }

            item(key = "tabs") { PeekFade(peek) { FeatureTabs(vm, Screen.SETTINGS) } }
            item(key = "search") {
              PeekFade(peek) {
                TextField(
                    shape = TextFieldDefaults.roundedShape,
                    colors = fieldColors(),
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("Search settings") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        { IconButton(shapes = IconButtonDefaults.shapes(), onClick = { query = "" }) { Icon(painterResource(R.drawable.ic_close), "Clear") } }
                    } else null,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp),
                )
              }
            }

            if (query.isNotBlank()) {
                val hits = allCategories().flatMap { c -> c.keys.filter { it in applicableKeys && matches(it, query.trim()) }.map { c to it } }
                if (hits.isEmpty()) {
                    item(key = "nohits") {
                        Text(
                            "No setting matches “${query.trim()}”",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
                hits.groupBy({ it.first }, { it.second }).forEach { (c, keys) ->
                    item(key = "h:hit:${c.id}") { PeekFade(peek) { GroupHeader(c.title) } }
                    keys.forEachIndexed { index, key ->
                        item(key = "hit:" + key.key) { PeekFade(peek, keep = peek?.key == key.key) { SettingItem(vm, config, key, version, index, keys.size) } }
                    }
                }
                return@GuiListPage
            }

            item(key = "h:settings") { Spacer(Modifier.height(12.dp)) }
            item(key = "categories") {
                // Feed settings are in the Feeds tab, but search here still finds them.
                ActionGroup(allCategories().filter { it.id != FEEDS_CATEGORY && it.id != BACKUP_CATEGORY }.map { c -> Action(c.icon, c.title, c.subtitle, chevron = true) { openId = c.id } })
            }
            item(key = "h:more") { Spacer(Modifier.height(16.dp)) }
            item(key = "more") {
                val backup = allCategories().first { it.id == BACKUP_CATEGORY }
                ActionGroup(
                    listOf(
                        Action(R.drawable.ic_check, "Setup checklist", "Default launcher, permissions, battery", chevron = true) { vm.openScreen(Screen.SETUP) },
                        Action(backup.icon, backup.title, backup.subtitle, chevron = true) { openId = BACKUP_CATEGORY },
                        Action(R.drawable.ic_info, "About", "Guide, tour, crash reports, version", chevron = true) { openId = ABOUT_PAGE },
                    )
                )
            }
        }
        if (peek != null) PeekHint(Modifier.align(Alignment.TopCenter))
        // The tapped crash report. The sheet stays open after Email, so the user can delete the report after sending it.
        openCrash?.let { c ->
            CrashSheet(
                c,
                onDismiss = { openCrash = null },
                onEmail = {
                    if (!emailDeveloper(context, "$VERSION_TEXT: ${c.title}", c.report.text())) {
                        tapScope.launch { snackbar.showSnackbar("No app on this phone can send email") }
                    }
                },
                onShare = {
                    if (runCatching { context.startActivity(CrashReports.shareIntent(c.report.text())) }.isFailure) {
                        tapScope.launch { snackbar.showSnackbar("No app on this phone can share it") }
                    }
                },
                onDelete = {
                    openCrash = null
                    c.report.delete()
                    crashReads++
                    tapScope.launch { snackbar.showSnackbar("Crash report deleted") }
                },
            )
        }
      }
    }
}

/**
 * Adds a settings category's grouped rows to a list in another screen. For example, the Feeds tab
 * shows the feeds category. The caller must read [applicable] outside the list's content (see [SettingsScreen]).
 */
internal fun androidx.compose.foundation.lazy.LazyListScope.categorySettings(
    vm: TerminalViewModel,
    id: String,
    version: State<Int>,
    applicable: Set<ConfigKey<*>>,
) {
    val category = allCategories().firstOrNull { it.id == id } ?: return
    for ((title, all) in category.groups) {
        val keys = all.filter { it in applicable }
        if (keys.isEmpty()) continue
        item(key = "h:$id:$title") { if (title != null) GroupHeader(title) else Spacer(Modifier.height(8.dp)) }
        keys.forEachIndexed { index, key ->
            item(key = key.key) { SettingItem(vm, vm.config, key, version, index, keys.size) }
        }
    }
}
