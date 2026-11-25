/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateTo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.WidgetScript
import com.mtwabpin.kolahal.ui.Action
import com.mtwabpin.kolahal.ui.ActionGroup
import com.mtwabpin.kolahal.ui.BackButton
import com.mtwabpin.kolahal.ui.ConfirmDialog
import com.mtwabpin.kolahal.ui.EmptyNote
import com.mtwabpin.kolahal.ui.GroupHeader
import com.mtwabpin.kolahal.ui.LabeledIconButton
import com.mtwabpin.kolahal.ui.PageHint
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.navigationBarScrim
import com.mtwabpin.kolahal.ui.theme.segColors
import com.mtwabpin.kolahal.ui.theme.segShapes
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.ProviderEntry
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLocation
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetOrder
import com.mtwabpin.kolahal.widgets.WidgetPages
import com.mtwabpin.kolahal.widgets.WidgetPlacement
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetStates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shows the messages of [WidgetHost] in a snackbar: each waiting message once, oldest first.
 * It works on whichever widget screen is open.
 */
@Composable
internal fun rememberWidgetMessages(widgets: WidgetHost): SnackbarHostState {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        widgets.messages.collect {
            if (it.isNotEmpty()) widgets.takeMessage()?.let { m -> snackbar.showSnackbar(m) }
        }
    }
    return snackbar
}

/**
 * The name of a widget Kolahal draws itself, from its source ([WidgetSource]). Null for an app's widget.
 * - `script:weather`: the script's `@title`, else its name.
 * - `script:weather#2`, a further widget of the script: the same, then its id: "Weather 2".
 * - `builtin:battery`: the built-in widget's name ([WidgetSource.builtinTitle]), "Battery and network".
 * - A script that is gone, and a built-in widget this version does not know: the name in the source.
 *
 * [scripts] are the scripts as the view model has read them, by name ([WidgetScript]). No code is
 * read here.
 */
internal fun sourceTitle(provider: String, scripts: Map<String, WidgetScript>): String? {
    if (!WidgetSource.isSource(provider)) return null
    WidgetSource.builtinId(provider)?.let { return WidgetSource.builtinTitle(it) ?: it }
    WidgetSource.scriptRef(provider)
        ?.let { ref -> return ref.titled(scripts[ref.script]?.title ?: ref.script) }
    // A script's source whose id can't be one: the text as it is stored.
    return provider.substringAfter(':')
}

/**
 * Whether Arrange widgets offers "Widget settings" for [w]: an app's widget, placed, whose setup can
 * run again ([setUpAgain], from [WidgetHost.canSetUpAgain]). Only while its profile is listed and
 * neither paused nor locked ([running], [isRunning]): a locked private space names no app, and nothing
 * there opens one, also when Android leaves it out of the list while it is locked.
 */
internal fun offersWidgetSettings(w: PlacedWidget, running: Boolean, setUpAgain: Boolean): Boolean =
    w.source == null && w.id != null && running && setUpAgain

/**
 * Whether Arrange widgets keeps its empty pages as it gives way to the screen [next]. The picker and
 * a card's settings page return to it, so the person has not left it. Any other screen leaves it,
 * and every empty page but page 1 goes ([WidgetHost.removeEmptyPages]).
 */
internal fun keepsEmptyPages(next: Screen): Boolean =
    next == Screen.WIDGETS || next == Screen.WIDGET_PICKER || next == Screen.WIDGET_SETTINGS

/**
 * The Widgets screen (Material 3 Expressive). It shows the widgets page as it is, live, each widget
 * where it was put ([WidgetGrid]), to arrange it.
 * - A tap chooses a widget: an outline, handles on its end and bottom edges, and its toolbar at the
 *   bottom. Dragging a handle resizes the widget, by columns and 8 dp steps.
 * - A long-press lifts a widget. Column guides show, a dashed box shows where it lands, and widgets
 *   in the way show where they will move down to. While it is held, a Remove target takes the top
 *   bar's place; dropping it there asks first.
 * - The toolbar of one of Kolahal's own widgets sets its look and its size, one tap each. An app's
 *   widget has steps for width and height (they repeat while held) and Square.
 * - The toolbar of one of Kolahal's own widgets has Settings, which opens its settings page
 *   ([WidgetSettingsScreen]): its options, color, shape and what shows. An app's widget's toolbar
 *   has Widget settings when its setup can run again ([onSettings]).
 * - The top bar has Close gaps and Add widget. A screen reader has actions on each widget: move it,
 *   resize it, the toolbar's Settings or Widget settings, remove it.
 * - Under the title, a tab for each widgets page and + for another ([PageTabs]). The screen arranges
 *   the tab's page, and opens on the page the person came from ([WidgetHost.addTo]) or on the page
 *   of the widget it is asked to choose. A long-press on a tab names or removes its page
 *   ([PageDialog]). With more than one page, a widget moves to another one by the toolbar's Page row,
 *   or held at the screen's edge ([ArrangedPage]'s onEdge). An empty page other than page 1 says so
 *   ([EmptyPage]), and goes by itself when the screen is left.
 * Entries restored from a backup show an "Add again" row.
 */
@Composable
fun WidgetsScreen(
    vm: TerminalViewModel,
    onSettings: (PlacedWidget) -> Unit = {},
    onReAdd: (ProviderEntry, PlacedWidget) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widgets = remember { WidgetHost.get(context) }
    val all by widgets.widgets.collectAsStateWithLifecycle()
    val providersVersion by widgets.providersVersion.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val version by vm.config.version.collectAsStateWithLifecycle()
    // With widgets.page off, no page next to the terminal shows the widgets, so this screen says so.
    val pageOff = remember(version) { vm.config[Keys.WIDGETS_PAGE] == "off" }
    val agendaDays = remember(version) { vm.config[Keys.AGENDA_WIDGET_DAYS] }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    // The page being arranged, by its location: at first the page the person came from.
    val pages by widgets.pages.collectAsStateWithLifecycle()
    var arranging by rememberSaveable { mutableStateOf(widgets.addTo) }
    val pageIndex = WidgetPages.indexOf(pages, arranging).coerceAtLeast(0)
    val location = pages[pageIndex].location
    // A widget added now goes on this page.
    LaunchedEffect(location) { widgets.addTo = location }
    // An empty page other than page 1 goes when the screen is left ([keepsEmptyPages]).
    DisposableEffect(Unit) {
        onDispose {
            if (!keepsEmptyPages(vm.screen.value)) widgets.removeEmptyPages()
        }
    }
    // A widget just placed, or long-pressed on the widgets page, is chosen as the screen shows, on its page.
    val toChoose by widgets.toChoose.collectAsStateWithLifecycle()
    LaunchedEffect(toChoose) {
        toChoose?.let { key ->
            widgets.widgets.value.firstOrNull { it.key == key }?.let { arranging = it.location }
            selected = key
            widgets.chosen()
        }
    }
    // The page whose name and removal are open, from a long-press on its tab.
    var pageDialog by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmRemove by remember { mutableStateOf<PlacedWidget?>(null) }
    // The Shortcuts widget's editor is open: from the toolbar of that widget, or because a tap on its card asked for it.
    var shortcutsEditor by rememberSaveable { mutableStateOf(false) }
    val editorAsked by vm.shortcutsEditorAsked.collectAsStateWithLifecycle()
    LaunchedEffect(editorAsked) {
        if (editorAsked) {
            shortcutsEditor = true
            vm.shortcutsEditorOpened()
        }
    }
    // The picker just placed a further widget of a script and asked for its options: its settings
    // page opens, with the widget chosen here for when Back comes to this screen.
    val optionsAsked by vm.widgetOptionsAsked.collectAsStateWithLifecycle()
    LaunchedEffect(optionsAsked) {
        optionsAsked?.let { ref ->
            vm.widgetOptionsOpened()
            val key = PlacedWidget(null, WidgetSource.script(ref), 0L, 0, 0).key
            selected = key
            vm.openWidgetSettings(key)
        }
    }
    val snackbar = rememberWidgetMessages(widgets)
    BackHandler { if (selected != null) selected = null else vm.closeScreen() }

    val panel = remember(all, location) {
        WidgetPlacement.readingOrder(
            WidgetOrder.inLocation(
                all,
                location
            )
        )
    }
    // Every page's widgets, for the title: "9 on 3 pages".
    val onPages = remember(all) { all.count(WidgetPlacement::onPage) }
    val pending = remember(all) { WidgetOrder.sorted(all.filter { it.pending }) }
    // Each label needs a binder call and a load from the other app, so they load off the main thread
    // ([WidgetHost.label]), again only when widgets are added or removed, not on every resize step.
    // Until then, the labels loaded before, or the provider's last part.
    val placed = remember(all) { all.map { Triple(it.key, it.id, it.provider) } }
    val labels by produceState(placed.associate { (key, id, provider) ->
        key to widgets.loadedLabel(
            id,
            provider
        )
    }, placed, providersVersion) {
        value = withContext(Dispatchers.IO) {
            placed.associate { (key, id, provider) ->
                key to widgets.label(
                    id,
                    provider
                )
            }
        }
    }
    // A widget Kolahal draws itself is named by its script's title. The view model has read the titles
    // from the code, off the main thread, and its answer changes only when a title does: not at
    // every run. They are apart from the labels above, which cost a call to another app each.
    val scripts by vm.widgetScripts.collectAsStateWithLifecycle()
    val titles = remember(placed, scripts) {
        placed.mapNotNull { (key, _, provider) ->
            sourceTitle(
                provider,
                scripts.orEmpty()
            )?.let { key to it }
        }.toMap()
    }

    // The app widgets whose setup can run again, by key. Each needs the widget's details, read off the
    // main thread, again when widgets are added or removed, or apps change.
    val setUpAgain by produceState(emptySet<String>(), placed, providersVersion) {
        value = withContext(Dispatchers.IO) {
            all.filter(widgets::canSetUpAgain).map { it.key }.toSet()
        }
    }

    // A locked private space hides its apps: its widget is named by what the page says of it, not by its app.
    fun lockedPrivate(w: PlacedWidget): Boolean =
        w.source == null && profiles.pausedKind(w.profileSerial) == ProfileKind.PRIVATE

    // What opens what the widget itself holds, for the toolbar and a screen reader:
    // - one of Kolahal's own widgets: its settings page, with its options (a script's), its list of
    //   commands (Shortcuts), its color, shape and what shows;
    // - an app's widget whose setup can run again: its setup screen. Not while its profile is
    //   paused or its private space locked: then it opens no app.
    fun editOf(w: PlacedWidget): ToolbarEdit? {
        return when {
            w.source != null -> ToolbarEdit("Settings", R.drawable.ic_tune) {
                vm.openWidgetSettings(w.key)
            }

            offersWidgetSettings(
                w,
                profiles.isRunning(w.profileSerial),
                w.key in setUpAgain
            ) -> ToolbarEdit("Widget settings", R.drawable.ic_settings) { onSettings(w) }

            else -> null
        }
    }

    fun labelOf(w: PlacedWidget): String? =
        if (lockedPrivate(w)) "Private space locked" else titles[w.key] ?: labels[w.key]

    // The widget held after a long-press, and the one whose handle is held. Neither outlives the screen.
    var dragged by remember { mutableStateOf<Dragged?>(null) }
    var resizing by remember { mutableStateOf<String?>(null) }
    val chosen = panel.firstOrNull { it.key == selected }
    val page = rememberScrollState()
    val scope = rememberCoroutineScope()

    // A widget held at the screen's edge ([ArrangedPage]): it moves to the page [step] from this one,
    // and the screen shows that page while the drag goes on.
    fun toEdge(key: String, step: Int) {
        val to = pages.getOrNull(WidgetPages.indexOf(pages, arranging).coerceAtLeast(0) + step)?.location ?: return
        widgets.moveToPage(key, to)
        arranging = to
    }

    // Arranges the page at [i]: from the top, with nothing chosen.
    fun pick(i: Int) {
        val to = pages.getOrNull(i)?.location ?: return
        if (to == location) return
        arranging = to
        selected = null
        scope.launch { page.scrollTo(0) }
    }

    GuiTheme {
        val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val barSettles = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
        // Where the Remove target is in the window while a widget is held.
        var removeAt by remember { mutableStateOf(Rect.Zero) }
        val overRemove by remember { derivedStateOf { dragged?.let { removeAt.contains(it.window) } == true } }
        Scaffold(
            modifier = Modifier
                .imePadding()
                .nestedScroll(scroll.nestedScrollConnection),
            containerColor = MaterialTheme.colorScheme.surface,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Column(Modifier.background(MaterialTheme.colorScheme.surface)) {
                Box {
                    MediumFlexibleTopAppBar(
                        title = { Text("Widgets") },
                        subtitle = {
                            Text(
                                when {
                                    pageOff -> "The widgets page is off"
                                    pages.size > 1 -> "$onPages on ${pages.size} pages"
                                    panel.isEmpty() -> "On the widgets page beside the terminal"
                                    else -> "${panel.size} on the widgets page"
                                }
                            )
                        },
                        navigationIcon = { BackButton(vm::closeScreen) },
                        actions = {
                            if (panel.isNotEmpty()) LabeledIconButton(
                                R.drawable.ic_vertical_align_top,
                                "Close gaps",
                                onClick = { widgets.closeGaps(location) })
                            LabeledIconButton(
                                R.drawable.ic_add,
                                "Add widget",
                                onClick = { vm.openScreen(Screen.WIDGET_PICKER) })
                        },
                        scrollBehavior = scroll,
                    )
                    // While a widget is held, the Remove target takes the bar's place.
                    if (dragged != null) RemoveTarget(
                        overRemove,
                        Modifier.matchParentSize()
                    ) { removeAt = it }
                }
                // A tab for each widgets page, and + for another.
                PageTabs(
                    pages,
                    current = pageIndex,
                    onPick = ::pick,
                    onAdd = {
                        widgets.addPage()?.let { added ->
                            arranging = added
                            selected = null
                            scope.launch { page.scrollTo(0) }
                        }
                    },
                    onLongPress = { pageDialog = it },
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
                }
            },
            bottomBar = {
                if (chosen != null && dragged == null) {
                    val chosenRef = WidgetSource.scriptRef(chosen.source)
                    // A built-in widget's kind is fixed; a script's is the one its @widget line names.
                    val chosenKind = WidgetSource.builtinId(chosen.source)?.let(WidgetSource::builtinKind)
                        ?: chosenRef?.let { scripts?.get(it.script)?.widget?.kind }
                    // A script's card is judged by what it shows, as the card on the page last read it.
                    val chosenSpec = chosenRef?.let { WidgetStates.lastRead(it.key)?.spec }
                    WidgetToolbar(
                        w = chosen,
                        label = labelOf(chosen) ?: chosen.provider,
                        size = widgets.sizeOf(chosen),
                        kind = chosenKind,
                        minHeight = remember(chosen.key, providersVersion) {
                            widgets.minHeightOf(
                                chosen
                            )
                        },
                        minSpan = remember(chosen.key, providersVersion) {
                            widgets.minSpanOf(chosen)
                        },
                        onLook = { widgets.setLook(chosen.key, it) },
                        // What the Settings button shows: the card's color, and its shape in the Shape look.
                        settingsColor = chosen.color ?: chosenSpec?.base?.color,
                        settingsShape = shapeChoicesFor(chosen, chosenKind, chosenSpec, widgets.measuredPageDp)?.shown?.takeIf {
                            val builtin = WidgetSource.builtinId(chosen.source)
                            chosen.look.drawnFor(chosenKind, builtin) == WidgetLook.SHAPE
                        },
                        onSize = { widgets.setSize(chosen.key, it) },
                        onHeight = { widgets.resize(chosen.key, it * WidgetLayout.STEP_DP) },
                        onWidth = { widgets.resizeWidth(chosen.key, it) },
                        onSquare = { widgets.square(chosen.key) },
                        edit = editOf(chosen),
                        onRemove = { confirmRemove = chosen },
                        onDone = { selected = null },
                        // The Agenda card: how many days it shows (widgets.agenda-days).
                        agendaDays = if (chosen.source == WidgetSource.builtin(WidgetSource.AGENDA)) agendaDays else null,
                        onAgendaDays = { vm.setConfig(Keys.AGENDA_WIDGET_DAYS, it) },
                        pageNames = pages.indices.map { WidgetPages.title(pages, it) },
                        page = pageIndex,
                        // The widget moves, and the screen goes with it to that page, still chosen.
                        onPage = { i ->
                            pages.getOrNull(i)?.location?.let { to ->
                                widgets.moveToPage(chosen.key, to)
                                arranging = to
                            }
                        },
                    )
                }
            },
        ) { padding ->
            // While a widget is held the toolbar is put away, but the page keeps the room it left.
            // Otherwise a page scrolled to its end would jump under the finger.
            val liveBottom = padding.calculateBottomPadding()
            var keptBottom by remember { mutableStateOf(liveBottom) }
            SideEffect { if (dragged == null) keptBottom = liveBottom }
            var viewport by remember { mutableStateOf(Rect.Zero) }
            Column(
                Modifier
                    .fillMaxSize()
                    .navigationBarScrim()
                    .onGloballyPositioned { viewport = it.boundsInWindow() }
                    .verticalScroll(page)
                    .padding(
                        top = padding.calculateTopPadding(),
                        bottom = (if (dragged != null) keptBottom else liveBottom) + 16.dp
                    )
                    // The widgets get the same width as on the widgets page (its margins and side
                    // insets). Text keeps the usual margin.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(horizontal = WidgetLayout.GAP_DP.dp),
            ) {
                val inset = Modifier.padding(horizontal = 16.dp - WidgetLayout.GAP_DP.dp)
                if (pageOff) {
                    Box(inset.padding(vertical = 8.dp)) {
                        ActionGroup(
                            listOf(
                                Action(
                                    R.drawable.ic_visibility,
                                    "Turn on the widgets page",
                                    "Beside the terminal: swipe sideways on the home screen to reach it"
                                ) {
                                    vm.config.set(Keys.WIDGETS_PAGE, "left")
                                },
                            )
                        )
                    }
                }
                if (pageIndex > 0 && panel.isEmpty()) {
                    EmptyPage(
                        WidgetPages.title(pages, pageIndex),
                        onAdd = { vm.openScreen(Screen.WIDGET_PICKER) },
                        onRemove = {
                            widgets.removePage(location)
                            arranging = WidgetLocation.PANEL
                        },
                        modifier = inset,
                    )
                } else if (panel.isEmpty() && pending.isEmpty()) {
                    EmptyNote(
                        if (pageOff) "No widgets yet." else "No widgets yet. They appear on the widgets page beside the terminal: swipe sideways on the home screen to reach it.",
                        inset,
                    )
                    Box(inset) {
                        ActionGroup(
                            listOf(
                                Action(
                                    R.drawable.ic_widgets,
                                    "Add a widget",
                                    "Clock, weather, calendar, music… from any app"
                                ) {
                                    vm.openScreen(Screen.WIDGET_PICKER)
                                },
                            )
                        )
                    }
                }
                if (panel.isNotEmpty()) {
                    ArrangedPage(
                        widgets = widgets,
                        all = all,
                        panel = panel,
                        selected = selected,
                        onSelect = { selected = it },
                        dragged = dragged,
                        onDrag = { dragged = it },
                        resizing = resizing,
                        onResize = { resizing = it },
                        page = page,
                        // The part of the window the page shows in, between the bars: held near its
                        // top or bottom, a widget scrolls the page.
                        shown = {
                            with(density) {
                                Rect(
                                    viewport.left,
                                    viewport.top + padding.calculateTopPadding().toPx(),
                                    viewport.right,
                                    viewport.bottom - liveBottom.toPx()
                                )
                            }
                        },
                        overRemove = { overRemove },
                        labelOf = { labelOf(it) ?: it.provider },
                        profiles = { profiles.pausedKind(it.profileSerial) },
                        feed = vm.widgetFeed,
                        providersVersion = providersVersion,
                        topRoom = padding.calculateTopPadding(),
                        bottomRoom = liveBottom,
                        onRemove = { confirmRemove = it },
                        editOf = { editOf(it) },
                        // With more than one page: held at the screen's edge, a widget goes to the next page.
                        onEdge = if (pages.size > 1) ::toEdge else null,
                        // The scroll that brings the chosen card into view takes the top bar part of the way with
                        // it, and no drag ends there to settle the bar: its small title would stay half faded in
                        // over the large one. So the bar closes the rest of the way.
                        onBrought = {
                            val bar = scroll.state
                            if (bar.collapsedFraction > 0.01f && bar.collapsedFraction < 0.99f) {
                                AnimationState(bar.heightOffset).animateTo(bar.heightOffsetLimit, barSettles) { bar.heightOffset = value }
                            }
                        },
                    )
                }
                if (pending.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    GroupHeader("Restored from backup")
                    Text(
                        "Widgets can't be copied between installs — add each one again with one tap.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = inset.padding(start = 16.dp, bottom = 8.dp),
                    )
                    Column(
                        inset,
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
                    ) {
                        pending.forEachIndexed { i, w ->
                            PendingRow(
                                w,
                                i,
                                pending.size,
                                providersVersion,
                                onReAdd = onReAdd,
                                onDismiss = { widgets.remove(w.key) })
                        }
                    }
                }
                if (panel.isNotEmpty()) {
                    PageHint(
                        "Tap a widget to change it; long-press it to move it. Terminal: widget ls · widget add",
                        inset
                    )
                }
                if (pages.size > 1 && panel.isNotEmpty()) {
                    PageHint("Long-press a page's tab to rename or remove it. Hold a widget at the screen's edge for the next page.", inset)
                }
            }
        }

        confirmRemove?.let { w ->
            ConfirmDialog(
                "Remove ${(if (lockedPrivate(w)) null else labelOf(w)) ?: "widget"}?",
                "The widget is deleted; add it again from the picker any time.",
                "Remove",
                icon = R.drawable.ic_delete,
                onDismiss = { confirmRemove = null },
            ) { confirmRemove = null; selected = null; widgets.remove(w.key) }
        }

        if (shortcutsEditor) ShortcutsEditor(vm) { shortcutsEditor = false }

        pageDialog?.let { i ->
            if (i !in pages.indices) {
                pageDialog = null
                return@let
            }
            val at = pages[i].location
            PageDialog(
                pages,
                i,
                count = all.count { it.location == at },
                onDismiss = { pageDialog = null },
                onRename = { name ->
                    widgets.renamePage(i, name)
                    pageDialog = null
                },
                onRemove = {
                    widgets.removePage(at)
                    if (arranging == at) arranging = WidgetLocation.PANEL
                    pageDialog = null
                },
            )
        }
    }
}

/**
 * Where a held widget can be dropped to remove it, in place of the top bar: "Remove" in a dashed
 * outline, filled while the widget is over it ([over]). [onPlaced] gets the area that counts, in
 * the window: the bar's whole row.
 */
@Composable
private fun RemoveTarget(over: Boolean, modifier: Modifier, onPlaced: (Rect) -> Unit) {
    val error = MaterialTheme.colorScheme.error
    Box(
        modifier
            .background(MaterialTheme.colorScheme.surface)
            .windowInsetsPadding(TopAppBarDefaults.windowInsets)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .onGloballyPositioned { onPlaced(it.boundsInWindow()) },
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .height(44.dp)
                    .background(
                        if (over) error.copy(alpha = 0.16f) else Color.Transparent,
                        CircleShape
                    )
                    .dashedBorder(error, 2.dp, 22.dp)
                    .padding(horizontal = 20.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Icon(painterResource(R.drawable.ic_delete), null, tint = error)
                Text(
                    "Remove",
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = error
                )
            }
        }
    }
}

@Composable
private fun PendingRow(
    w: PlacedWidget,
    index: Int,
    count: Int,
    providersVersion: Int,
    onReAdd: (ProviderEntry, PlacedWidget) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    val entry by produceState<ProviderEntry?>(null, w.provider, w.profileSerial, providersVersion) {
        value = withContext(Dispatchers.IO) { widgets.findProvider(w.provider, w.profileSerial) }
    }
    val found = entry
    // Loading a label reads the other app's resources: off the main thread, once per provider.
    val label by produceState(w.provider.substringAfterLast('.'), found) {
        found?.let { f ->
            value =
                withContext(Dispatchers.IO) { f.info.loadLabel(context.packageManager).toString() }
        }
    }
    SegmentedListItem(
        colors = segColors(),
        shapes = segShapes(index, count),
        leadingContent = {
            Icon(
                painterResource(R.drawable.ic_widgets),
                null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        overlineContent = {
            Text(
                w.packageName,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                if (found == null) "App not installed" else WidgetLayout.sizeLine(
                    w.span,
                    w.heightDp
                )
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { found?.let { onReAdd(it, w) } },
                    enabled = found != null
                ) { Text("Add again") }
                LabeledIconButton(R.drawable.ic_delete, "Forget", onClick = onDismiss)
            }
        },
    ) { Text(label, maxLines = 2, overflow = TextOverflow.Ellipsis) }
}
