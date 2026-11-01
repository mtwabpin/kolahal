package com.mtwabpin.kolahal.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.feeds.FeedInfo
import com.mtwabpin.kolahal.feeds.FeedItem
import com.mtwabpin.kolahal.feeds.FeedRepository
import com.mtwabpin.kolahal.feeds.FeedScheduler
import com.mtwabpin.kolahal.feeds.FeedUrl
import com.mtwabpin.kolahal.feeds.exportOpml
import com.mtwabpin.kolahal.feeds.importOpml
import com.mtwabpin.kolahal.script.Files
import com.mtwabpin.kolahal.terminal.CommandParsing
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/** Per-feed refresh presets (minutes). */
private val FEED_INTERVALS = listOf(15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour", 180 to "3 hours", 360 to "6 hours", 720 to "12 hours", 1440 to "24 hours")

/** Items shown per feed (the database keeps at most [com.mtwabpin.kolahal.feeds.FeedDb.MAX_ITEMS_PER_FEED]). */
private const val ITEM_LIMIT = 300

private fun hostOf(url: String): String =
    runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url

private fun FeedInfo.displayTitle(): String = title?.takeIf { it.isNotBlank() } ?: hostOf(url)

/**
 * Feed manager (Material 3 Expressive): subscriptions with unread counts, per-feed settings in a
 * bottom sheet, an item list per feed, and OPML import/export. Reads and writes go to
 * [FeedRepository] on [Dispatchers.IO]; lists re-query whenever [FeedRepository.version] changes
 * (the fetch worker runs in this process, so new items show up live).
 */
@Composable
fun FeedsScreen(vm: TerminalViewModel, onRequestNotifications: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { FeedRepository.get(context) }
    val repoVersion by repo.version.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val feeds by produceState<List<FeedInfo>?>(null, repoVersion) {
        value = withContext(Dispatchers.IO) { repo.all() }
    }
    // The feeds settings, shown under the subscriptions; read here, not in the list's content.
    val configVersion = vm.config.version.collectAsStateWithLifecycle()
    val applicable by remember { derivedStateOf { configVersion.value; Keys.ALL.filterTo(HashSet()) { applies(it, vm.config) } } }
    var openFeedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var sheetFeedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }

    BackHandler { if (openFeedId != null) openFeedId = null else vm.closeScreen() }

    /** Runs [block] off the main thread and shows its result (or failure) as a snackbar. */
    fun io(label: String, block: suspend () -> String?) {
        scope.launch {
            val msg = withContext(Dispatchers.IO) {
                runCatching { block() }.getOrElse { "$label failed: ${it.message ?: it.javaClass.simpleName}" }
            }
            msg?.let { snackbar.showSnackbar(it) }
        }
    }

    val importOpml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) io("import") {
            val added = repo.importOpml(Files.readText(context, uri))
            FeedScheduler.sync(context)
            if (added > 0) FeedScheduler.fetchNow(context)
            if (added == 0) "No new feeds in that file" else "Imported $added new feeds — fetching…"
        }
    }
    val exportOpml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        if (uri != null) io("export") {
            Files.writeText(context, uri, repo.exportOpml())
            "Exported ${repo.feeds().size} feeds"
        }
    }

    GuiTheme {
        val open = openFeedId?.let { id -> feeds?.firstOrNull { it.id == id } }
        if (openFeedId != null && open != null) {
            FeedItemsPage(
                feed = open,
                repo = repo,
                version = repoVersion,
                snackbar = snackbar,
                onBack = { openFeedId = null },
                onRefresh = { io("refresh") { FeedScheduler.fetchNow(context, open.id); "Fetching ${open.displayTitle()}…" } },
                onMarkAllRead = { io("mark read") { repo.markAllRead(open.id); null } },
                onOpenItem = { item -> openItem(context, scope, repo, item, snackbar) },
            )
        } else {
            FeedListPage(
                vm = vm,
                configVersion = configVersion,
                applicable = applicable,
                feeds = feeds,
                snackbar = snackbar,
                onBack = vm::closeScreen,
                onAdd = { adding = true },
                onRefreshAll = { io("refresh") { FeedScheduler.fetchNow(context); "Refreshing all feeds…" } },
                onImport = { importOpml.launch(arrayOf("*/*")) },
                onExport = { exportOpml.launch("kolahal-feeds.opml") },
                onOpenFeed = { openFeedId = it.id },
                onFeedMenu = { sheetFeedId = it.id },
            )
        }

        val sheetFeed = sheetFeedId?.let { id -> feeds?.firstOrNull { it.id == id } }
        if (sheetFeed != null) {
            FeedSheet(
                feed = sheetFeed,
                onDismiss = { sheetFeedId = null },
                onRefresh = { io("refresh") { FeedScheduler.fetchNow(context, sheetFeed.id); "Fetching ${sheetFeed.displayTitle()}…" } },
                onNotify = { on ->
                    if (on) onRequestNotifications()
                    io("notifications") { repo.setNotify(sheetFeed.id, on); null }
                },
                onEnabled = { on ->
                    io("update") { repo.setEnabled(sheetFeed.id, on); FeedScheduler.sync(context); null }
                },
                onInterval = { minutes -> io("update") { repo.setInterval(sheetFeed.id, minutes); FeedScheduler.sync(context); null } },
                onMarkAllRead = { io("mark read") { repo.markAllRead(sheetFeed.id); "Marked ${sheetFeed.displayTitle()} read" } },
                onRemove = {
                    sheetFeedId = null
                    if (openFeedId == sheetFeed.id) openFeedId = null
                    io("remove") { repo.remove(sheetFeed.id); FeedScheduler.sync(context); "Removed ${sheetFeed.displayTitle()}" }
                },
            )
        }

        if (adding) AddFeedDialog(repo, onDismiss = { adding = false }) { title ->
            adding = false
            scope.launch { snackbar.showSnackbar("Subscribed to $title — fetching…") }
        }
    }
}

/** Opens [item] in the browser and marks it read. */
private fun openItem(context: Context, scope: CoroutineScope, repo: FeedRepository, item: FeedItem, snackbar: SnackbarHostState) {
    val link = item.link
    if (link.isNullOrBlank()) {
        scope.launch { snackbar.showSnackbar("This item has no link") }
    } else {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, link.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // No handler, a malformed link, or a SecurityException from the target activity.
            scope.launch { snackbar.showSnackbar("No app can open $link") }
            return
        }
    }
    if (!item.read) scope.launch(Dispatchers.IO) { repo.markRead(item.id) }
}

// ---- feed list ----------------------------------------------------------------------------------

@Composable
private fun FeedListPage(
    vm: TerminalViewModel,
    configVersion: State<Int>,
    applicable: Set<ConfigKey<*>>,
    feeds: List<FeedInfo>?,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onRefreshAll: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onOpenFeed: (FeedInfo) -> Unit,
    onFeedMenu: (FeedInfo) -> Unit,
) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val unread = feeds?.filter { it.enabled }?.sumOf { it.unread } ?: 0
    Scaffold(
        modifier = Modifier.imePadding().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Feeds") },
                subtitle = {
                    Text(
                        when {
                            feeds.isNullOrEmpty() -> "RSS · Atom"
                            else -> "${feeds.size} feed${if (feeds.size == 1) "" else "s"} · $unread unread"
                        }
                    )
                },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onRefreshAll, enabled = !feeds.isNullOrEmpty()) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh all")
                    }
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onImport) {
                        Icon(painterResource(R.drawable.ic_upload), contentDescription = "Import OPML")
                    }
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onExport, enabled = !feeds.isNullOrEmpty()) {
                        Icon(painterResource(R.drawable.ic_download), contentDescription = "Export OPML")
                    }
                },
                scrollBehavior = scroll,
            )
        },
        floatingActionButton = {
            SmallExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(painterResource(R.drawable.ic_add), null) },
                text = { Text("Add feed") },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "tabs") { FeatureTabs(vm, Screen.FEEDS) }
            when {
                feeds == null -> Unit // first load; the list appears within a frame or two
                feeds.isEmpty() -> {
                    item(key = "empty") {
                        Text(
                            "No feeds yet. Tap “Add feed” or import an OPML file from another reader.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                        )
                    }
                    item(key = "import") {
                        ActionGroup(
                            listOf(
                                Action(R.drawable.ic_add, "Add a feed", "Paste a site or feed address (RSS or Atom)", onClick = onAdd),
                                Action(R.drawable.ic_upload, "Import OPML", "Subscriptions exported from another feed reader", onClick = onImport),
                            )
                        )
                    }
                }
                else -> {
                    item(key = "h:subs") { GroupHeader("Subscriptions") }
                    itemsIndexed(feeds, key = { _, f -> f.id }) { i, f ->
                        FeedRow(f, i, feeds.size, onClick = { onOpenFeed(f) }, onMenu = { onFeedMenu(f) })
                    }
                    item(key = "hint") {
                        Text(
                            "Tap a feed to read it. Terminal: feed add <url> · feed read · feed fetch",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
                        )
                    }
                }
            }
            // The feed settings live here, with the feeds; Settings search finds them too.
            item(key = "h:settings") { GroupHeader("Settings") }
            categorySettings(vm, FEEDS_CATEGORY, configVersion, applicable)
        }
    }
}

@Composable
private fun FeedRow(f: FeedInfo, index: Int, count: Int, onClick: () -> Unit, onMenu: () -> Unit) {
    val failed = f.lastStatus?.startsWith("error") == true
    val status = buildList {
        add("every ${CommandParsing.formatInterval(f.intervalMin)}")
        if (f.notify) add("notify")
        if (!f.enabled) add("off")
        f.lastFetch?.let { add(relative(it)) } ?: add("never fetched")
        f.lastStatus?.let { add(it) }
    }.joinToString(" · ")
    SegmentedListItem(
        onClick = onClick,
        shapes = segShapes(index, count),
        colors = segColors(),
        leadingContent = {
            Icon(
                painterResource(R.drawable.ic_rss),
                null,
                tint = if (f.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
            )
        },
        overlineContent = {
            Text(
                hostOf(f.url),
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                status,
                color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UnreadBadge(f.unread, f.total)
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onMenu) {
                    Icon(painterResource(R.drawable.ic_more_vert), contentDescription = "Feed options")
                }
            }
        },
    ) {
        Text(
            f.displayTitle(),
            fontWeight = if (f.unread > 0) FontWeight.SemiBold else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "unread / total" pill; filled when anything is unread. */
@Composable
private fun UnreadBadge(unread: Int, total: Int) {
    val active = unread > 0
    Surface(
        shape = CircleShape,
        color = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            "$unread / $total",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

// ---- per-feed sheet -----------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedSheet(
    feed: FeedInfo,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onNotify: (Boolean) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onMarkAllRead: () -> Unit,
    onRemove: () -> Unit,
) {
    val state = rememberBottomSheetState(SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    var confirmRemove by remember(feed.id) { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
            Text(feed.displayTitle(), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                feed.url,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                listOfNotNull(
                    "${feed.unread} unread of ${feed.total}",
                    feed.lastFetch?.let { "fetched ${relative(it)}" } ?: "never fetched",
                    feed.lastStatus,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (feed.lastStatus?.startsWith("error") == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
            )

            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shapes = ButtonDefaults.shapes(), onClick = onRefresh) {
                    Icon(painterResource(R.drawable.ic_refresh), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Refresh now")
                }
                FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onMarkAllRead, enabled = feed.unread > 0) {
                    Icon(painterResource(R.drawable.ic_done_all), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text("Mark all read")
                }
            }

            Spacer(Modifier.height(8.dp))
            SwitchRow("Notifications", "Post a notification for new items", feed.notify, onChange = onNotify)
            SwitchRow("Enabled", "Fetch this feed on its schedule", feed.enabled, onChange = onEnabled)

            Spacer(Modifier.height(12.dp))
            IntervalSelector("Refresh every", feed.intervalMin, onInterval, FEED_INTERVALS)

            Spacer(Modifier.height(16.dp))
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { confirmRemove = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Icon(painterResource(R.drawable.ic_delete), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text("Remove feed")
            }
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            icon = { Icon(painterResource(R.drawable.ic_delete), null) },
            title = { Text("Remove ${feed.displayTitle()}?") },
            text = { Text("Unsubscribes and deletes its ${feed.total} stored items. Export OPML first to keep your list.") },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = { confirmRemove = false; onRemove() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Remove") }
            },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = { confirmRemove = false }) { Text("Cancel") } },
        )
    }
}

// ---- add dialog ---------------------------------------------------------------------------------

@Composable
private fun AddFeedDialog(repo: FeedRepository, onDismiss: () -> Unit, onAdded: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val normalized = remember(url) { FeedUrl.normalize(url) }
    val invalid = url.isNotBlank() && normalized == null

    fun submit() {
        if (normalized == null || busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    if (repo.byUrl(normalized) != null) throw IllegalArgumentException("Already subscribed to $normalized")
                    val feed = repo.add(url)
                    FeedScheduler.sync(context)
                    FeedScheduler.fetchNow(context, feed.id)
                    feed.shortName
                }
            }
            busy = false
            result.onSuccess(onAdded).onFailure { error = it.message ?: "Couldn't add that feed" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_rss), null) },
        title = { Text("Add feed") },
        text = {
            TextField(
                shape = TextFieldDefaults.roundedShape,
                colors = fieldColors(),
                value = url,
                onValueChange = { url = it; error = null },
                label = { Text("Feed URL") },
                placeholder = { Text("example.com/feed.xml") },
                singleLine = true,
                isError = invalid || error != null,
                supportingText = {
                    Text(
                        error ?: if (invalid) "Enter an http(s) address" else "RSS or Atom; https:// is added if missing"
                    )
                },
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), enabled = normalized != null && !busy, onClick = ::submit) { Text("Subscribe") } },
        dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Cancel") } },
    )
}

// ---- items of one feed --------------------------------------------------------------------------

@Composable
private fun FeedItemsPage(
    feed: FeedInfo,
    repo: FeedRepository,
    version: Int,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenItem: (FeedItem) -> Unit,
) {
    val items by produceState<List<FeedItem>?>(null, feed.id, version) {
        value = withContext(Dispatchers.IO) { repo.items(feedId = feed.id, limit = ITEM_LIMIT) }
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.imePadding().nestedScroll(scroll.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(feed.displayTitle(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                subtitle = { Text("${feed.unread} unread · ${hostOf(feed.url)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { BackButton(onBack) },
                actions = {
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onRefresh) {
                        Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh feed")
                    }
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onMarkAllRead, enabled = feed.unread > 0) {
                        Icon(painterResource(R.drawable.ic_done_all), contentDescription = "Mark all read")
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { padding ->
        val list = items
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (list != null && list.isEmpty()) {
                item(key = "empty") {
                    Text(
                        if (feed.lastFetch == null) "Not fetched yet. Tap refresh." else "No items in this feed.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    )
                }
            }
            if (list != null) itemsIndexed(list, key = { _, it -> it.id }) { i, item ->
                ItemRow(item, i, list.size) { onOpenItem(item) }
            }
        }
    }
}

@Composable
private fun ItemRow(item: FeedItem, index: Int, count: Int, onClick: () -> Unit) {
    val date = item.publishedText?.takeIf { it.isNotBlank() } ?: relative(item.fetchedAt)
    SegmentedListItem(
        onClick = onClick,
        shapes = segShapes(index, count),
        colors = segColors(),
        leadingContent = {
            // Unread dot; a fixed-size slot keeps titles aligned when it's hidden.
            Box(Modifier.size(10.dp).then(
                if (!item.read) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier
            ))
        },
        supportingContent = {
            Text(
                date,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    ) {
        Text(
            item.title,
            fontWeight = if (item.read) FontWeight.Normal else FontWeight.SemiBold,
            color = if (item.read) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
