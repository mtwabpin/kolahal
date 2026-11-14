package com.mtwabpin.kolahal.ui

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.activity.compose.BackHandler
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.terminal.WidgetScript
import com.mtwabpin.kolahal.widgets.PlacedIds
import com.mtwabpin.kolahal.widgets.ProviderEntry
import com.mtwabpin.kolahal.widgets.WidgetHost
import com.mtwabpin.kolahal.widgets.WidgetRef
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


private class ProviderGroup(
    val key: String,
    val label: String,
    val icon: ImageBitmap?,
    val work: ProfileKind?,
    val providers: List<ProviderEntry>,
    /** The labels of [providers] in lowercase, loaded once for the search. */
    val names: List<String>,
    /** The card text of [providers], loaded with them off the main thread. */
    val texts: List<CardText>,
)

/** A widget's name and, from Android 12, its description. */
private class CardText(val label: String, val description: String?)

/** Previews and icons decoded while the picker is open. A card that scrolls back into view shows them at once. */
private val pickerImages = object : android.util.LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

private fun Drawable.toImage(maxPx: Int): ImageBitmap? = runCatching {
    val w = intrinsicWidth.takeIf { it > 0 } ?: maxPx
    val h = intrinsicHeight.takeIf { it > 0 } ?: maxPx
    val scale = minOf(1f, maxPx.toFloat() / maxOf(w, h))
    toBitmap((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1)).asImageBitmap()
}.getOrNull()

private fun loadGroups(context: Context, widgets: WidgetHost, profiles: List<Profile>, iconPx: Int): List<ProviderGroup> {
    val pm = context.packageManager
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    return widgets.providers().groupBy { it.packageName to it.serial }.map { (k, list) ->
        val (pkg, serial) = k
        val user = list.first().user
        val ai = runCatching { launcherApps.getApplicationInfo(pkg, 0, user) }.getOrNull()
        val label = ai?.let { pm.getApplicationLabel(it).toString() } ?: pkg
        val icon = ai?.let { runCatching { pm.getUserBadgedIcon(pm.getApplicationIcon(it), user) }.getOrNull() }?.toImage(iconPx)
        val kind = profiles.firstOrNull { it.serial == serial }?.kind?.takeIf { it != ProfileKind.PERSONAL }
        val named = list.map { p ->
            val name = p.info.loadLabel(pm).toString()
            val description = if (Build.VERSION.SDK_INT >= 31) runCatching { p.info.loadDescription(context)?.toString() }.getOrNull() else null
            Triple(p, name.lowercase(), CardText(name, description))
        }.sortedBy { it.second }
        ProviderGroup("$pkg:$serial", label, icon, kind, named.map { it.first }, named.map { it.second }, named.map { it.third })
    }.sortedWith(compareBy({ it.label.lowercase() }, { it.work?.ordinal ?: -1 }))
}

/**
 * One of a saved script's widgets, for the picker's Kolahal section.
 * - [script] is the script's name.
 * - [source] says which: the script's own widget (`script:<name>`), or the Status card of its status line (`status:<name>`).
 * - [what] is the row's second line: the kind, or "Status card".
 * - [search] is its title, name and kind in lowercase.
 */
internal class PickerScriptRow(val script: String, val title: String, val source: String, val what: String, val size: WidgetSize, val search: String) {
    val isStatus: Boolean get() = source.startsWith(WidgetSource.STATUS)
    /** What a message calls it: "Weather", "Weather status". */
    val name: String get() = if (isStatus) "$title status" else title
}

/**
 * The widgets your scripts have, by title: one for a script with a `@widget` line, and a Status card
 * for a script that sets a status line. A script with both has two rows, its own widget first.
 *
 * [scripts] are the scripts as the view model has read them ([WidgetScript]). No code is read here,
 * so this is cheap enough for the main thread.
 */
internal fun pickerScriptRows(scripts: Collection<WidgetScript>): List<PickerScriptRow> = scripts.flatMap { s ->
    val title = s.title ?: s.name
    listOfNotNull(
        s.widget?.let { w ->
            PickerScriptRow(s.name, title, WidgetSource.script(s.name), w.kind.id.replaceFirstChar { it.uppercase() }, w.size, "$title ${s.name} ${w.kind.id}".lowercase())
        },
        if (s.setsStatus) PickerScriptRow(s.name, title, WidgetSource.STATUS + s.name, "Status card", WidgetSize.S2X1, "$title ${s.name} status card".lowercase()) else null,
    )
}.sortedBy { it.title.lowercase() }

/**
 * What a row of the picker's Kolahal section says about the widgets of its kind that are on the
 * page already. Null when none is: the row then says nothing.
 * - [placed]: how many are on the page. [most]: how many there may be.
 * - A built-in widget and a Status card are placed once: "Added".
 * - A script's own widget may be placed again, each with its own options, up to
 *   [WidgetRef.MAX_PER_SCRIPT]: "Added · tap to add another". At the most, the row counts them.
 */
internal fun pickerAdded(placed: Int, most: Int = 1): String? = when {
    placed <= 0 -> null
    placed < most -> "Added · tap to add another"
    most > 1 -> "Added · $placed of $most"
    else -> "Added"
}

/**
 * A built-in widget, for the picker's Kolahal section. Kolahal fills it from its own data, so it has no script.
 * - [id] is its id in its source: `battery` in `builtin:battery`.
 * - [what] is the row's second line.
 * - [search] is its title and what it shows, in lowercase.
 */
internal class PickerBuiltinRow(val id: String, val title: String, val what: String, val size: WidgetSize, val search: String) {
    val source: String get() = WidgetSource.builtin(id)
}

/**
 * The picker's row for the built-in widget [id], which starts at [size]. Its title is the widget's
 * one name ([WidgetSource.builtinTitle]). [words] are more words to find it by, in lowercase.
 */
private fun builtinRow(id: String, size: WidgetSize, words: String): PickerBuiltinRow {
    val title = WidgetSource.builtinTitle(id) ?: id
    return PickerBuiltinRow(id, title, "Built-in", size, "${title.lowercase()} built-in $words")
}

/**
 * The built-in widgets, in the order the picker lists them. They come before your scripts' widgets.
 * Only a widget whose card is drawn is listed ([OwnCards.draws]; Battery and network has a card of
 * its own). So a widget is taken out of this version in one place.
 */
internal val pickerBuiltinRows: List<PickerBuiltinRow> = listOf(
    builtinRow(WidgetSource.BATTERY, WidgetSize.S2X1, "charging wi-fi"),
    builtinRow(WidgetSource.SHORTCUTS, WidgetSize.S4X1, "buttons commands quick"),
    builtinRow(WidgetSource.MEDIA, WidgetSize.S4X1, "media music player song"),
    builtinRow(WidgetSource.TODOS, WidgetSize.S4X2, "todo to-do tasks checklist"),
    builtinRow(WidgetSource.AGENDA, WidgetSize.S4X2, "calendar events"),
    builtinRow(WidgetSource.REMINDERS, WidgetSize.S2X2, "remind alarm"),
    builtinRow(WidgetSource.NOTES, WidgetSize.S2X2, "note"),
).filter { it.id == WidgetSource.BATTERY || OwnCards.draws(it.id) }

/** The header of the picker's first section: Kolahal's own widgets. Some are built in, and your scripts fill the others. */
private val kolahalGroup = ProviderGroup("kolahal", "Kolahal", null, null, emptyList(), emptyList(), emptyList())

/**
 * The widget picker.
 * - First come Kolahal's own widgets: the built-in ones, then each saved script with a `@widget`
 *   line, and a Status card for each script that sets a status line. Picking one places it at once
 *   ([WidgetHost.placeBuiltin], [WidgetHost.placeScript], [WidgetHost.placeStatus]) and goes to the
 *   Widgets screen, as an app's widget does once it is bound.
 * - A built-in widget and a Status card are placed once. A script's own widget can be placed
 *   again: a tap on a row that says "Added" places a further widget of the script
 *   ([WidgetHost.placeAnother]), and the Widgets screen then shows that widget's options, so you
 *   can set what differs from the first one. When the script's first widget was removed and a
 *   further one is still there, the tap places the first one again: only that one is filled by
 *   the runs on the script's timer.
 * - Then the installed providers from every profile, grouped by app. Work and private apps get a
 *   badge. Each provider shows its preview and default size. Picking one starts the bind flow in MainActivity.
 */
@Composable
fun WidgetPickerScreen(vm: TerminalViewModel, onPick: (ProviderEntry) -> Unit) {
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
    // The view model reads each script's title, `@widget` line and status call from its code, off the
    // main thread, and again only when the code changes. Its answer changes only when one of these
    // does. The script list itself changes at every run, and no row is made again for that.
    val scripts by vm.widgetScripts.collectAsStateWithLifecycle()
    val placed by widgets.widgets.collectAsStateWithLifecycle()
    val own = remember(scripts) { pickerScriptRows(scripts?.values.orEmpty()) }
    val added = remember(placed) { placed.mapNotNull { it.source }.toSet() }
    // How many widgets of each script are on the page, by script name: a script can have several.
    val scriptWidgets = remember(placed) { PlacedIds.of(placed).mapValues { it.value.size } }
    val providersVersion by widgets.providersVersion.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val version by vm.config.version.collectAsStateWithLifecycle()
    val pageOff = remember(version) { vm.config[Keys.WIDGETS_PAGE] == "off" }
    val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val groups by produceState<List<ProviderGroup>?>(null, providersVersion, profiles) {
        value = withContext(Dispatchers.IO) { loadGroups(context, widgets, profiles, iconPx) }
    }
    var query by rememberSaveable { mutableStateOf("") }
    val snackbar = rememberWidgetMessages(widgets)
    BackHandler { vm.closeScreen() }
    // Free the pictures when the picker closes, and also when Android asks (the app is hidden or
    // memory is low). The more memory Kolahal holds, the more likely Android is to close it.
    // Cards decode their pictures again when the user returns.
    DisposableEffect(Unit) {
        val trim = object : android.content.ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) pickerImages.evictAll()
            }

            override fun onConfigurationChanged(newConfig: android.content.res.Configuration) = Unit

            @Deprecated("Called on old Android versions only", ReplaceWith("onTrimMemory"))
            override fun onLowMemory() = pickerImages.evictAll()
        }
        context.registerComponentCallbacks(trim)
        onDispose {
            context.unregisterComponentCallbacks(trim)
            pickerImages.evictAll()
        }
    }

    GuiTheme {
        val shown = remember(groups, query) {
            val q = query.trim().lowercase()
            groups?.let { all ->
                if (q.isEmpty()) all else all.mapNotNull { g ->
                    if (g.label.lowercase().contains(q)) g
                    else g.providers.indices.filter { g.names[it].contains(q) }
                        .takeIf { it.isNotEmpty() }?.let { found -> ProviderGroup(g.key, g.label, g.icon, g.work, found.map(g.providers::get), found.map(g.names::get), found.map(g.texts::get)) }
                }
            }
        }
        val (builtinsShown, ownShown) = remember(own, query) {
            val q = query.trim().lowercase()
            // A search for "Kolahal" itself keeps the whole section, as an app's name keeps all its widgets.
            if (q.isEmpty() || kolahalGroup.label.lowercase().contains(q)) pickerBuiltinRows to own
            else pickerBuiltinRows.filter { it.search.contains(q) } to own.filter { it.search.contains(q) }
        }
        val anyOwn = builtinsShown.isNotEmpty() || ownShown.isNotEmpty()
        // One of Kolahal's own widgets is placed at once. [placedNow] is false when it was on the page already.
        val picked: (Boolean, String) -> Unit = { placedNow, name ->
            if (placedNow) {
                widgets.message("Added $name")
                // As after an app's widget is bound: the picker closes and the Widgets screen shows it.
                if (vm.screen.value == Screen.WIDGET_PICKER) vm.closeScreen()
                if (vm.screen.value != Screen.WIDGETS) vm.openScreen(Screen.WIDGETS)
            } else {
                widgets.message("$name is already on the widgets page")
            }
        }
        GuiListPage(
            title = { Text("Add widget") },
            subtitle = { Text(if (pageOff) "Added to the widgets page, which is off" else "Added to the widgets page") },
            onBack = { vm.closeScreen() },
            snackbar = snackbar,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "search") {
                SearchField(query, { query = it }, "Search widgets", Modifier.fillMaxWidth())
            }
            if (anyOwn) {
                item(key = "g:kolahal") { AppHeader(kolahalGroup) }
                items(builtinsShown, key = { "s:${it.source}" }) { b ->
                    KolahalWidgetCard(b.title, b.what, b.size, added = pickerAdded(if (b.source in added) 1 else 0)) {
                        picked(widgets.placeBuiltin(b.id, b.size), b.title)
                    }
                }
                items(ownShown, key = { "s:${it.source}" }) { w ->
                    // A Status card is placed once. A script's own widget can be placed several times.
                    val says = if (w.isStatus) pickerAdded(if (w.source in added) 1 else 0) else pickerAdded(scriptWidgets[w.script] ?: 0, WidgetRef.MAX_PER_SCRIPT)
                    KolahalWidgetCard(w.title, w.what, w.size, added = says) {
                        // The script as it is now. One that was deleted a moment ago places nothing.
                        val script = vm.scriptList.value.firstOrNull { it.name == w.script } ?: return@KolahalWidgetCard
                        val onPage = widgets.placedOf(w.script)
                        val full = "${w.title} has ${WidgetRef.MAX_PER_SCRIPT} widgets already, the most a script may have"
                        when {
                            w.isStatus -> picked(widgets.placeStatus(script, w.size), w.name)
                            !WidgetRef.roomFor(onPage.map { it.id }) -> widgets.message(full)
                            // Its first widget, when that one is not on the page. A run on the script's
                            // timer fills only the first, so it comes before any further widget.
                            onPage.none { it.first } -> picked(widgets.placeScript(script, w.size), w.name)
                            // It has its first widget: this is a further one, with options of its own.
                            else -> when (val ref = widgets.placeAnother(script, w.size)) {
                                null -> widgets.message(full)
                                else -> {
                                    picked(true, ref.titled(w.title))
                                    // The Widgets screen shows its options, to set what differs from the
                                    // script's own. A script that declares none has nothing to set.
                                    if (scripts?.get(w.script)?.hasOptions == true) vm.askWidgetOptions(ref)
                                }
                            }
                        }
                    }
                }
            }
            when {
                shown == null -> Unit
                // With a search that found one of Kolahal's own, the apps have nothing to add.
                shown.isEmpty() -> if (query.isBlank() || !anyOwn) item(key = "empty") {
                    EmptyNote(if (query.isBlank()) "No apps offer widgets." else "Nothing matches “$query”.")
                }
                else -> shown.forEach { g ->
                    item(key = "g:${g.key}") { AppHeader(g) }
                    itemsIndexed(g.providers, key = { _, p -> "p:${g.key}:${p.flat}" }) { i, p -> ProviderCard(p, g.texts[i], widgets) { onPick(p) } }
                }
            }
        }
    }
}

@Composable
private fun AppHeader(g: ProviderGroup) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 4.dp)) {
        if (g.icon != null) Image(g.icon, null, Modifier.size(32.dp)) else Icon(painterResource(R.drawable.ic_widgets), null, Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Text(g.label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f, fill = false))
        if (g.work != null) {
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
                Text(g.work.label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
            }
        }
    }
}

/**
 * One of Kolahal's own widgets: its [title], [what] it is (its kind, "Status card" or "Built-in"),
 * and the [size] it starts with. [added] is what it says when one is on the page already
 * ([pickerAdded]): "Added", or for a script's own widget that a tap adds another. Null says
 * nothing. A plain row for now, with no drawn sample of the kind.
 */
@Composable
private fun KolahalWidgetCard(title: String, what: String, size: WidgetSize, added: String?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    what,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (added != null) {
                Spacer(Modifier.width(8.dp))
                // The longer words wrap in their own corner, so the title keeps its room.
                Text(
                    added,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.widthIn(max = 132.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text("${size.cells}×${size.rows}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun ProviderCard(p: ProviderEntry, text: CardText, widgets: WidgetHost, onClick: () -> Unit) {
    val context = LocalContext.current
    val info = p.info
    val label = text.label
    val description = text.description
    val maxPx = with(LocalDensity.current) { 280.dp.roundToPx() }
    // The cache key is tied to this provider as loaded. After an update (new details), the key
    // changes, so the pictures are read again.
    val loaded = "${p.flat}#${p.serial}:${System.identityHashCode(info)}:$maxPx"
    val preview by produceState<ImageBitmap?>(pickerImages.get("$loaded:preview"), info) {
        value = pickerImages.get("$loaded:preview") ?: withContext(Dispatchers.IO) {
            runCatching { info.loadPreviewImage(context, 0) }.getOrNull()?.toImage(maxPx)
        }?.also { pickerImages.put("$loaded:preview", it) }
    }
    val icon by produceState<ImageBitmap?>(pickerImages.get("$loaded:icon"), info) {
        value = pickerImages.get("$loaded:icon") ?: withContext(Dispatchers.IO) {
            runCatching { info.loadIcon(context, 0) }.getOrNull()?.toImage(maxPx / 3)
        }?.also { pickerImages.put("$loaded:icon", it) }
    }
    val canLayoutPreview = Build.VERSION.SDK_INT >= 31 && info.previewLayout != 0 && p.user == Process.myUserHandle()
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.surfaceContainerLowest),
                contentAlignment = Alignment.Center,
            ) {
                val img = preview
                when {
                    img != null -> Image(img, null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(8.dp))
                    canLayoutPreview -> LayoutPreview(info)
                    icon != null -> Image(icon!!, null, Modifier.size(56.dp))
                    else -> Icon(painterResource(R.drawable.ic_widgets), null, Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(widgets.sizeLabel(info), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.outline)
            }
            if (!description.isNullOrBlank()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** The Android 12+ `previewLayout`, inflated as a view that ignores touches. Touches go to the card. */
@RequiresApi(31)
@Composable
private fun LayoutPreview(info: AppWidgetProviderInfo) {
    AndroidView(
        factory = { ctx ->
            object : FrameLayout(ctx) {
                override fun onInterceptTouchEvent(ev: MotionEvent) = true
                override fun dispatchTouchEvent(ev: MotionEvent) = false
            }.apply {
                runCatching {
                    val view = AppWidgetHostView(ctx)
                    view.setAppWidget(0, info)
                    view.updateAppWidget(RemoteViews(info.provider.packageName, info.previewLayout))
                    addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}
