/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.data.ScriptLang
import com.mtwabpin.kolahal.data.SearchWords
import com.mtwabpin.kolahal.script.ApiDoc
import com.mtwabpin.kolahal.script.ApiDocs
import com.mtwabpin.kolahal.widgets.WidgetIcons

private typealias RefTab = ReferenceSearch.Tab

/** What the reference page has open over its tabs or its search's results: an example's own view. */
private sealed interface Opened {
    /** A widget example ([ex], a guide's example), with its [variant] chosen (0: the guide's). */
    data class Widget(val ex: ReferenceExamples.Widget, val variant: Int) : Opened
    data class Log(val ex: ReferenceExamples.Log) : Opened
}

/**
 * Where the reference page was: its search, tab, Functions group and each list's scroll position.
 * The editor holds one for each script ([ScriptEditor]), so after an insert, which closes the page,
 * it reopens where it was. An example's view is not kept: an insert from it ends with the list.
 */
@androidx.compose.runtime.Stable
internal class ReferenceState {
    var query by mutableStateOf("")
        private set
    var tab by mutableStateOf(ReferenceSearch.Tab.FUNCTIONS)
    /** The Functions group the chips show; null for All. */
    var namespace by mutableStateOf<String?>(null)
    /** Each tab's list, kept while an example's view is open over it and while the page is closed. */
    val lists: Map<ReferenceSearch.Tab, LazyListState> = ReferenceSearch.Tab.entries.associateWith { LazyListState() }
    /** The search's results. A new search starts them at the top. */
    var resultsList by mutableStateOf(LazyListState())
        private set

    /** Sets the search to [text]; results for a changed search start at the top. */
    fun search(text: String) {
        if (text != query) resultsList = LazyListState()
        query = text
    }
}

/**
 * The script editor's reference, a page over the editor: the API for the script's language, its `@`
 * lines, the widget and log examples, and the widget icons, in tabs ([ReferenceSearch.Tab]), with one
 * search for all of them.
 * - Functions: the API in groups, a namespace's own line over its entries. Chips show one group.
 *   A tap on an entry gives it to [onPick].
 * - Directives: the `@` lines; a tap gives the whole comment line to [onDirective]. The language's
 *   keywords follow them.
 * - Widgets and Log lines: a row for each example. A tap opens its view ([WidgetExampleView],
 *   [LogExampleView]); its Insert gives the code to [onExample].
 * - Icons: a grid by group; a tap gives the name to [onIcon].
 * - The search looks in every tab at once ([ReferenceSearch.find]) and lists what it finds by tab.
 *   A tap does what it does in the tab. Back from an example opened there returns to the results.
 * Back closes an example's view, then the page ([onBack]).
 *
 * The search, tab, group and scroll positions are in [ref], which outlives the page, so it reopens
 * where it was.
 */
@Composable
internal fun ReferencePage(
    lang: ScriptLang,
    onBack: () -> Unit,
    onPick: (ApiDoc) -> Unit,
    onDirective: (String) -> Unit,
    onIcon: (String) -> Unit,
    onExample: (String) -> Unit,
    ref: ReferenceState = remember { ReferenceState() },
) = GuiTheme {
    val query = ref.query
    val tab = ref.tab
    val namespace = ref.namespace
    var opened by remember { mutableStateOf<Opened?>(null) }
    val lists = ref.lists
    val resultsList = ref.resultsList
    BackHandler { if (opened != null) opened = null else onBack() }

    Box(Modifier.fillMaxSize().testTag("reference")) {
        when (val o = opened) {
            is Opened.Widget -> WidgetExampleView(o.ex, o.variant, lang, onBack = { opened = null }, onInsert = onExample)
            is Opened.Log -> LogExampleView(o.ex, lang, onBack = { opened = null }, onInsert = onExample)
            null -> Scaffold(
                topBar = { ReferenceBar("Reference", if (lang == ScriptLang.LUA) "Lua" else "JavaScript", onBack) },
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.imePadding(),
            ) { padding ->
                val bottom = padding.calculateBottomPadding() + 16.dp
                Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
                    SearchField(
                        query, { ref.search(it) }, "Search functions, widgets, icons",
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("reference-search"),
                        clear = "Clear the search",
                    )
                    val words = SearchWords.of(query)
                    val openWidget = { w: ReferenceExamples.Widget ->
                        val guide = ReferenceExamples.widgets.firstOrNull { it.name == w.of } ?: w
                        opened = Opened.Widget(guide, ReferenceExamples.variantsOf(guide.name).indexOf(w) + 1)
                    }
                    if (words.isEmpty()) {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = tab.ordinal,
                            edgePadding = 8.dp,
                            minTabWidth = 72.dp,
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            RefTab.entries.forEach { t ->
                                Tab(
                                    selected = t == tab,
                                    onClick = { ref.tab = t },
                                    text = { Text(t.label, maxLines = 1) },
                                    selectedContentColor = MaterialTheme.colorScheme.primary,
                                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag("tab ${t.label}"),
                                )
                            }
                        }
                        val state = lists.getValue(tab)
                        when (tab) {
                            RefTab.FUNCTIONS -> FunctionsTab(lang, namespace, { ref.namespace = it }, state, bottom, onPick)
                            RefTab.DIRECTIVES -> RefList(state, bottom) {
                                item { TabHint("Comment lines at the top of a script. A tap puts one above the cursor's line.") }
                                items(ApiDocs.directives) { DirectiveRow(it, lang, words, onDirective) }
                                item { Keywords(lang) }
                            }
                            RefTab.WIDGETS -> RefList(state, bottom) {
                                items(ReferenceExamples.widgets, key = { it.name }) { ex ->
                                    WidgetExampleRow(ex, AnnotatedString(ex.heading), AnnotatedString(ex.line)) { opened = Opened.Widget(ex, 0) }
                                }
                            }
                            RefTab.LOGS -> RefList(state, bottom) {
                                items(ReferenceExamples.logs, key = { it.name }) { ex ->
                                    LogExampleRow(ex, AnnotatedString(ex.heading), null) { opened = Opened.Log(ex) }
                                }
                            }
                            RefTab.ICONS -> RefList(state, bottom) {
                                item { TabHint("For a widget's icon, such as icon = \"sun\". A tap puts the name in quotes.") }
                                WidgetIcons.GROUPS.forEach { (group, names) -> iconGroup(group, names, words, onIcon) }
                            }
                        }
                    } else {
                        Spacer(Modifier.height(8.dp))
                        val found = remember(query, lang) { ReferenceSearch.find(query, lang) }
                        Results(found, words,lang, resultsList, bottom, onPick, onDirective, onIcon, openWidget) { opened = Opened.Log(it) }
                    }
                }
            }
        }
    }
}

/** One tab's list. Its test tag is the same in every tab: one shows at a time. */
@Composable
private fun RefList(state: LazyListState, bottom: Dp, content: LazyListScope.() -> Unit) {
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(bottom = bottom),
        modifier = Modifier.fillMaxSize().testTag("reference-list"),
        content = content,
    )
}

/** A short line at the top of a tab: what it holds and what a tap does. */
@Composable
private fun TabHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/** [text] with each place one of [words] is found marked. */
private fun marked(text: String, words: List<String>): AnnotatedString = markMatches(AnnotatedString(text), words)

/**
 * The Functions tab: a chip for each group ([ReferenceSearch.groups]) over the list. "All" shows
 * every group; a group's chip shows only that one ([namespace]), from its top.
 */
@Composable
private fun FunctionsTab(
    lang: ScriptLang,
    namespace: String?,
    onNamespace: (String?) -> Unit,
    state: LazyListState,
    bottom: Dp,
    onPick: (ApiDoc) -> Unit,
) {
    val groups = remember(lang) { ReferenceSearch.groups(lang) }
    val shown = if (namespace == null) groups else groups.filter { it.name == namespace }
    Column {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            modifier = Modifier.testTag("namespaces"),
        ) {
            item {
                FilterChip(
                    selected = namespace == null,
                    onClick = { onNamespace(null); state.requestScrollToItem(0) },
                    label = { Text("All") },
                    modifier = Modifier.testTag("namespace All"),
                )
            }
            items(groups, key = { it.name }) { g ->
                FilterChip(
                    selected = namespace == g.name,
                    onClick = { onNamespace(g.name); state.requestScrollToItem(0) },
                    label = { Text(g.name, fontFamily = if (g.head != null) Term.code.fontFamily else null) },
                    modifier = Modifier.testTag("namespace ${g.name}"),
                )
            }
        }
        RefList(state, bottom) {
            shown.forEach { g ->
                // No keys: a key would keep the list at the same group when the chips change it.
                item { GroupHead(g, onPick) }
                items(g.entries) { EntryRow(it, emptyList(), onPick) }
            }
        }
    }
}

/** A group's header: the namespace's name and its own line. A tap puts the namespace's name in, as its entry did. */
@Composable
private fun GroupHead(g: ReferenceSearch.Group, onPick: (ApiDoc) -> Unit) {
    val head = g.head
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (head != null) Modifier.clickable(onClickLabel = "Insert") { onPick(head) } else Modifier)
            .semantics(mergeDescendants = true) { heading() }
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
    ) {
        Text(
            g.name,
            style = if (head != null) codeStyle().copy(fontWeight = FontWeight.Bold) else MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            head?.doc ?: "Functions and values outside a namespace",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * An API entry: its signature and its line, with [words] marked. A `fmt` function also shows what
 * it makes, as the log draws it. A tap anywhere on it, the sample too, gives it to [onPick].
 */
@Composable
private fun EntryRow(d: ApiDoc, words: List<String>, onPick: (ApiDoc) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = "Insert") { onPick(d) }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(marked(d.signature, words), style = codeStyle())
        Text(marked(d.doc, words), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        ReferenceExamples.fmtSample(d.name)?.let { sample ->
            LinesPreview(sample, Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).testTag("fmt-sample ${d.name}"), source = false)
        }
    }
    RowDivider()
}

/** A directive: its comment line and what it does, with [words] marked. A tap gives the line to [onDirective]. */
@Composable
private fun DirectiveRow(d: ApiDocs.Directive, lang: ScriptLang, words: List<String>, onDirective: (String) -> Unit) {
    val comment = if (lang == ScriptLang.LUA) "--" else "//"
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClickLabel = "Insert") { onDirective("$comment @${d.name}" + if (d.args.isEmpty()) "\n" else " ") }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(marked("$comment @${d.name} ${d.args}".trimEnd(), words), style = codeStyle())
        Text(marked(d.doc, words), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    RowDivider()
}

/** The language's keywords, at the end of the Directives tab. */
@Composable
private fun Keywords(lang: ScriptLang) {
    Text(
        buildAnnotatedString {
            append("Keywords: ")
            withStyle(SpanStyle(fontFamily = Term.code.fontFamily)) { append(ApiDocs.keywords(lang).joinToString(" ")) }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

/** The icons per row of the grid. */
private const val ICON_COLUMNS = 5

/** An icon group's name, then its icons, [ICON_COLUMNS] to a row, each with its name under it. */
private fun LazyListScope.iconGroup(group: String?, names: List<String>, words: List<String>, onIcon: (String) -> Unit) {
    if (group != null) {
        item(key = "icons $group") {
            Text(
                group,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 2.dp).semantics { heading() },
            )
        }
    }
    items(names.chunked(ICON_COLUMNS)) { row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { name ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Insert") { onIcon(name) }
                        .testTag("icon $name"),
                ) {
                    widgetIconDrawable(name)?.let { Icon(painterResource(it), null, modifier = Modifier.size(22.dp)) }
                    Text(marked(name, words), style = codeStyle().copy(fontSize = 11.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            repeat(ICON_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** The name of a group of search results: the tab's, and how many were found there. */
@Composable
private fun ResultsHead(tab: RefTab, count: Int) {
    Text(
        "${tab.label.uppercase()} · $count",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.06.em,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 4.dp).semantics { heading() },
    )
}

/**
 * What the search found ([found]), by tab, each group under its name and count, with [words] marked.
 * A tap does what it does in the tab: it puts an entry, a directive or an icon in, or opens an
 * example ([onWidget], [onLog]).
 */
@Composable
private fun Results(
    found: ReferenceSearch.Results,
    words: List<String>,
    lang: ScriptLang,
    state: LazyListState,
    bottom: Dp,
    onPick: (ApiDoc) -> Unit,
    onDirective: (String) -> Unit,
    onIcon: (String) -> Unit,
    onWidget: (ReferenceExamples.Widget) -> Unit,
    onLog: (ReferenceExamples.Log) -> Unit,
) {
    LazyColumn(
        state = state,
        contentPadding = PaddingValues(bottom = bottom),
        modifier = Modifier.fillMaxSize().testTag("reference-results"),
    ) {
        if (found.count == 0) item { NoMatchNote("Nothing found") }
        if (found.functions.isNotEmpty()) {
            item { ResultsHead(RefTab.FUNCTIONS, found.functions.size) }
            items(found.functions) { EntryRow(it, words, onPick) }
        }
        if (found.directives.isNotEmpty()) {
            item { ResultsHead(RefTab.DIRECTIVES, found.directives.size) }
            items(found.directives) { DirectiveRow(it, lang, words, onDirective) }
        }
        if (found.widgets.isNotEmpty()) {
            item { ResultsHead(RefTab.WIDGETS, found.widgets.size) }
            items(found.widgets, key = { "widget ${it.name}" }) { w ->
                val name = w.heading + (w.variant?.let { " · $it" } ?: "")
                val line = ReferenceSearch.lineWith(w.code(lang), words) ?: w.line
                WidgetExampleRow(w, marked(name, words), marked(line, words)) { onWidget(w) }
            }
        }
        if (found.logs.isNotEmpty()) {
            item { ResultsHead(RefTab.LOGS, found.logs.size) }
            items(found.logs, key = { "log ${it.name}" }) { ex ->
                val line = ReferenceSearch.lineWith(ex.code(lang), words)
                LogExampleRow(ex, marked(ex.heading, words), line?.let { marked(it, words) }) { onLog(ex) }
            }
        }
        if (found.icons.isNotEmpty()) {
            item { ResultsHead(RefTab.ICONS, found.icons.size) }
            iconGroup(null, found.icons, words, onIcon)
        }
    }
}
