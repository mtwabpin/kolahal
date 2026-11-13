package com.mtwabpin.kolahal.ui

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
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
import com.mtwabpin.kolahal.commands.CommandParsing
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI

/** Refresh interval presets for a feed, in minutes. */
private val FEED_INTERVALS = listOf(15 to "15 minutes", 30 to "30 minutes", 60 to "1 hour", 180 to "3 hours", 360 to "6 hours", 720 to "12 hours", 1440 to "24 hours")

/** Number of items shown per feed. The database keeps at most [com.mtwabpin.kolahal.feeds.FeedDb.MAX_ITEMS_PER_FEED]. */
private const val ITEM_LIMIT = 300

private fun hostOf(url: String): String =
    runCatching { URI(url).host }.getOrNull()?.removePrefix("www.") ?: url

private fun FeedInfo.displayTitle(): String = title?.takeIf { it.isNotBlank() } ?: hostOf(url)

/**
 * The feed manager (Material 3 Expressive). It shows:
 * - subscriptions with unread counts;
 * - per-feed settings in a bottom sheet;
 * - an item list per feed;
 * - OPML import and export.
 *
 * Reads and writes go to [FeedRepository] on [Dispatchers.IO]. Lists query again whenever
 * [FeedRepository.version] changes. The fetch worker runs in this process, so new items show up live.
 */
@Composable
fun FeedsScreen(vm: TerminalViewModel) {
    val context = LocalContext.current
    val repo = remember { FeedRepository.get(context) }
    val repoVersion by repo.version.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    val feeds by produceState<List<FeedInfo>?>(null, repoVersion) {
        value = withContext(Dispatchers.IO) { repo.all() }
    }
    // The feeds settings, shown under the subscriptions. They are read here, not inside the list's content.
    val configVersion = vm.config.version.collectAsStateWithLifecycle()
    val applicable by remember { derivedStateOf { configVersion.value; Keys.ALL.filterTo(HashSet()) { applies(it, vm.config) } } }
    var openFeedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var sheetFeedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingFeedId by rememberSaveable { mutableStateOf<Long?>(null) }

    BackHandler { if (openFeedId != null) openFeedId = null else vm.closeScreen() }

    /** Runs [block] off the main thread and shows its result, or its failure, in a snackbar. */
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
                onToggleRead = { item -> io("mark read") { if (item.read) repo.markUnread(item.id) else repo.markRead(item.id); null } },
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
                    if (on) vm.requestNotificationPermission()
                    io("notifications") { repo.setNotify(sheetFeed.id, on); null }
                },
                onEnabled = { on ->
                    io("update") { repo.setEnabled(sheetFeed.id, on); FeedScheduler.sync(context); null }
                },
                onInterval = { minutes -> io("update") { repo.setInterval(sheetFeed.id, minutes); FeedScheduler.sync(context); null } },
                onMarkAllRead = { io("mark read") { repo.markAllRead(sheetFeed.id); "Marked ${sheetFeed.displayTitle()} read" } },
                onEditAddress = { editingFeedId = sheetFeed.id },
                onRemove = {
                    sheetFeedId = null
                    if (openFeedId == sheetFeed.id) openFeedId = null
                    io("remove") { repo.remove(sheetFeed.id); FeedScheduler.sync(context); "Removed ${sheetFeed.displayTitle()}" }
                },
            )
        }

        if (adding) FeedAddressDialog(
            title = "Add feed",
            confirm = "Subscribe",
            initial = "",
            onDismiss = { adding = false },
            onSubmit = { url ->
                if (repo.byUrl(url) != null) throw IllegalArgumentException("Already subscribed to $url")
                val feed = repo.add(url)
                FeedScheduler.sync(context)
                FeedScheduler.fetchNow(context, feed.id)
                "Subscribed to ${feed.shortName} — fetching…"
            },
        ) { message ->
            adding = false
            scope.launch { snackbar.showSnackbar(message) }
        }

        val editing = editingFeedId?.let { id -> feeds?.firstOrNull { it.id == id } }
        if (editing != null) FeedAddressDialog(
            title = "Edit address",
            confirm = "Save",
            initial = editing.url,
            onDismiss = { editingFeedId = null },
            onSubmit = { url ->
                val feed = repo.setUrl(editing.id, url)
                FeedScheduler.fetchNow(context, feed.id)
                "${editing.displayTitle()} is at ${hostOf(feed.url)} now — fetching…"
            },
        ) { message ->
            editingFeedId = null
            scope.launch { snackbar.showSnackbar(message) }
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
            // No app can handle it, the link is malformed, or the target activity threw a SecurityException.
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
    val unread = feeds?.filter { it.enabled }?.sumOf { it.unread } ?: 0
    GuiListPage(
        title = { Text("Feeds") },
        subtitle = {
            Text(
                when {
                    feeds.isNullOrEmpty() -> "RSS · Atom"
                    else -> "${feeds.size} feed${if (feeds.size == 1) "" else "s"} · $unread unread"
                }
            )
        },
        onBack = onBack,
        snackbar = snackbar,
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
        floatingActionButton = {
            SmallExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(painterResource(R.drawable.ic_add), null) },
                text = { Text("Add feed") },
            )
        },
        bottom = 96.dp,
    ) {
        item(key = "tabs") { FeatureTabs(vm, Screen.FEEDS) }
        when {
            feeds == null -> Unit // first load; the list appears within a frame or two
            feeds.isEmpty() -> {
                item(key = "empty") {
                    EmptyNote("No feeds yet. Tap “Add feed” or import an OPML file from another reader.")
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
                    PageHint("Tap a feed to read it. Terminal: feed add <url> · feed read · feed fetch")
                }
            }
        }
        // The feed settings are here, next to the feeds. Settings search finds them too.
        item(key = "h:settings") { GroupHeader("Settings") }
        categorySettings(vm, FEEDS_CATEGORY, configVersion, applicable)
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

/** An "unread / total" pill. It is filled when any item is unread. */
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

@Composable
private fun FeedSheet(
    feed: FeedInfo,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit,
    onNotify: (Boolean) -> Unit,
    onEnabled: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onMarkAllRead: () -> Unit,
    onEditAddress: () -> Unit,
    onRemove: () -> Unit,
) {
    var confirmRemove by remember(feed.id) { mutableStateOf(false) }
    GuiSheet(onDismiss) {
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
                IconLabel(R.drawable.ic_refresh, "Refresh now")
            }
            FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = onMarkAllRead, enabled = feed.unread > 0) {
                IconLabel(R.drawable.ic_done_all, "Mark all read")
            }
        }

        Spacer(Modifier.height(8.dp))
        SwitchRow("Notifications", "Post a notification for new items", feed.notify, onChange = onNotify)
        SwitchRow("Enabled", "Fetch this feed on its schedule", feed.enabled, onChange = onEnabled)

        Spacer(Modifier.height(12.dp))
        IntervalSelector("Refresh every", feed.intervalMin, onInterval, FEED_INTERVALS)

        // Actions that change the subscription itself are grouped at the end:
        // - Edit address, to fix a typo or follow a feed that moved, without removing and adding it again.
        // - Remove feed.
        Spacer(Modifier.height(16.dp))
        Row {
            TextButton(shapes = ButtonDefaults.shapes(), onClick = onEditAddress) {
                IconLabel(R.drawable.ic_edit, "Edit address")
            }
            ErrorTextButton(onClick = { confirmRemove = true }) {
                IconLabel(R.drawable.ic_delete, "Remove feed")
            }
        }
    }

    if (confirmRemove) {
        ConfirmDialog(
            "Remove ${feed.displayTitle()}?",
            "Unsubscribes and deletes its ${feed.total} stored items. Export OPML first to keep your list.",
            "Remove",
            icon = R.drawable.ic_delete,
            onDismiss = { confirmRemove = false },
        ) { confirmRemove = false; onRemove() }
    }
}

// ---- address dialog -----------------------------------------------------------------------------

/**
 * A dialog for a feed's address, to add a feed or to change its address ([initial] is filled in).
 * [onSubmit] runs off the main thread with the normalised address and returns the message to show.
 * If it throws, the error shows under the field.
 */
@Composable
private fun FeedAddressDialog(
    title: String,
    confirm: String,
    initial: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> String,
    onDone: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var url by rememberSaveable { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val normalized = remember(url) { FeedUrl.normalize(url) }
    val invalid = url.isNotBlank() && normalized == null

    fun submit() {
        if (normalized == null || busy) return
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { onSubmit(normalized) } }
            busy = false
            result.onSuccess(onDone).onFailure { error = it.message ?: "Couldn't use that address" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_rss), null) },
        title = { Text(title) },
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
        confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), enabled = normalized != null && !busy, onClick = ::submit) { Text(confirm) } },
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
    onToggleRead: (FeedItem) -> Unit,
) {
    val items by produceState<List<FeedItem>?>(null, feed.id, version) {
        value = withContext(Dispatchers.IO) { repo.items(feedId = feed.id, limit = ITEM_LIMIT) }
    }
    val list = items
    GuiListPage(
        title = { Text(feed.displayTitle(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
        subtitle = { Text("${feed.unread} unread · ${hostOf(feed.url)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onBack = onBack,
        snackbar = snackbar,
        actions = {
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onRefresh) {
                Icon(painterResource(R.drawable.ic_refresh), contentDescription = "Refresh feed")
            }
            IconButton(shapes = IconButtonDefaults.shapes(), onClick = onMarkAllRead, enabled = feed.unread > 0) {
                Icon(painterResource(R.drawable.ic_done_all), contentDescription = "Mark all read")
            }
        },
    ) {
        if (list != null && list.isEmpty()) {
            item(key = "empty") {
                EmptyNote(if (feed.lastFetch == null) "Not fetched yet. Tap refresh." else "No items in this feed.")
            }
        }
        if (list != null) itemsIndexed(list, key = { _, it -> it.id }) { i, item ->
            ItemRow(item, i, list.size, onClick = { onOpenItem(item) }, onToggleRead = { onToggleRead(item) })
        }
        if (!list.isNullOrEmpty()) item(key = "hint") {
            PageHint("Tap an item to open it · swipe it to mark it read or unread")
        }
    }
}

/**
 * One feed item.
 * - A tap opens it.
 * - A swipe in either direction marks it read, or unread if it is already read. The row moves
 *   back when released.
 * - Screen readers get the same toggle as an action.
 */
@Composable
private fun ItemRow(item: FeedItem, index: Int, count: Int, onClick: () -> Unit, onToggleRead: () -> Unit) {
    val date = item.publishedText?.takeIf { it.isNotBlank() } ?: relative(item.fetchedAt)
    val shapes = segShapes(index, count)
    val swipe = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()
    val label = if (item.read) "Mark unread" else "Mark read"
    // Keep one callback for the whole life of the row. The box runs the callback again each time it
    // changes while the row is still swiped out, and each run would toggle the item again.
    val toggle by rememberUpdatedState(onToggleRead)
    val dismissed = remember(swipe, scope) {
        { _: SwipeToDismissBoxValue ->
            toggle()
            scope.launch { swipe.reset() }
            Unit
        }
    }
    SwipeToDismissBox(
        state = swipe,
        backgroundContent = {
            // Shows what releasing the row does, on the side it was pulled from.
            Row(
                horizontalArrangement = if (swipe.dismissDirection == SwipeToDismissBoxValue.EndToStart) Arrangement.End else Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shapes.shape)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 24.dp),
            ) {
                val color = MaterialTheme.colorScheme.onSecondaryContainer
                Icon(painterResource(if (item.read) R.drawable.ic_mail else R.drawable.ic_done_all), null, tint = color)
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = color)
            }
        },
        onDismiss = dismissed,
        modifier = Modifier.semantics { customActions = listOf(CustomAccessibilityAction(label) { onToggleRead(); true }) },
    ) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            colors = segColors(),
            leadingContent = {
                // Unread dot. Its slot has a fixed size, so titles stay aligned when the dot is hidden.
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
}
