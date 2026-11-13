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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.Profile
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.widgets.ProviderEntry
import com.mtwabpin.kolahal.widgets.WidgetHost
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
 * The widget picker: installed providers from every profile, grouped by app.
 * - Work and private apps get a badge.
 * - Each provider shows its preview and default size.
 * - Picking one starts the bind flow in MainActivity.
 */
@Composable
fun WidgetPickerScreen(vm: TerminalViewModel, onPick: (ProviderEntry) -> Unit) {
    val context = LocalContext.current
    val widgets = remember { WidgetHost.get(context) }
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
            when {
                shown == null -> Unit
                shown.isEmpty() -> item(key = "empty") {
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
