package com.mtwabpin.kolahal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.launch

/** The tour's pages, in order. */
internal enum class TourPage { WELCOME, TYPING, LOG, LOOK, KEYBOARD, SELECTOR, ACCESS, DONE }

/** The setup checks that the tour offers ([com.mtwabpin.kolahal.terminal.SetupCheck.fix]). Most people want these. */
internal val TOUR_CHECKS = listOf("home", "notifications", "notifs enable", "accessibility", "contacts --allow")

/**
 * The tour (on first start, from `tour`, or from Settings → About → Tour).
 * - It explains how Kolahal works, one page at a time.
 * - On the way, it offers the first few settings and permissions, each with one tap.
 * - A page with settings shows a small home screen above them that follows each choice
 *   (TourPreviews.kt).
 * - Everything it sets is also in Settings. The other permissions are in the setup checklist.
 */
@Composable
fun OnboardingScreen(vm: TerminalViewModel) {
    val pages = TourPage.entries
    val pager = rememberPagerState { pages.size }
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex
    fun go(page: Int) = scope.launch { pager.animateScrollToPage(page) }
    BackHandler { if (pager.currentPage > 0) go(pager.currentPage - 1) else vm.closeScreen() }

    GuiTheme {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surface,
            topBar = {
                Box(Modifier.fillMaxWidth().padding(top = 32.dp, end = 8.dp).heightIn(min = 48.dp), contentAlignment = Alignment.CenterEnd) {
                    if (!last) TextButton(shapes = ButtonDefaults.shapes(), onClick = vm::closeScreen, modifier = Modifier.testTag("tour-skip")) { Text("Skip") }
                }
            },
            bottomBar = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 16.dp),
                ) {
                    Box(Modifier.weight(1f)) {
                        if (pager.currentPage > 0) TextButton(shapes = ButtonDefaults.shapes(), onClick = { go(pager.currentPage - 1) }, modifier = Modifier.testTag("tour-back")) { Text("Back") }
                    }
                    PageDots(pager.currentPage, pages.size)
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        Button(
                            shapes = ButtonDefaults.shapes(),
                            onClick = { if (last) vm.closeScreen() else go(pager.currentPage + 1) },
                            modifier = Modifier.testTag("tour-next"),
                        ) { Text(if (last) "Start" else "Next") }
                    }
                }
            },
        ) { padding ->
            // Pages change only with Next and Back. The previews use swipes (the log's lines, the
            // app selector), and a page change would take those swipes away from them.
            HorizontalPager(
                state = pager,
                userScrollEnabled = false,
                verticalAlignment = Alignment.Top,
                modifier = Modifier.padding(padding).fillMaxSize().testTag("tour"),
            ) { page ->
                when (pages[page]) {
                    TourPage.WELCOME -> Welcome()
                    TourPage.TYPING -> Typing(vm)
                    TourPage.LOG -> TheLog(vm)
                    TourPage.LOOK -> Look(vm)
                    TourPage.KEYBOARD -> Keyboard(vm)
                    TourPage.SELECTOR -> Selector(vm)
                    TourPage.ACCESS -> Access(vm)
                    TourPage.DONE -> Done(vm)
                }
            }
        }
    }
}

@Composable
private fun PageDots(current: Int, count: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.semantics { contentDescription = "Page ${current + 1} of $count" },
    ) {
        repeat(count) { i ->
            val on = i == current
            Box(
                Modifier
                    .size(if (on) 10.dp else 8.dp)
                    .background(if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
        }
    }
}

/**
 * One tour page: its [title], then its [preview], which stays in view, then the rest, which scrolls.
 * So a choice and the preview it changes are always on screen together.
 */
@Composable
private fun Page(title: String, preview: (@Composable ColumnScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
        if (preview != null) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { preview() }
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = if (preview != null) 8.dp else 0.dp, bottom = 16.dp),
            content = content,
        )
    }
}

@Composable
private fun Body(text: String) = Text(text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** A setting's name and what it does, above its choices. */
@Composable
private fun Setting(title: String, detail: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        content()
    }
}

/** Shows `help` and other code in body text in the terminal's font and the accent colour. */
private fun withCode(text: String) = buildAnnotatedString {
    text.split('`').forEachIndexed { i, part ->
        if (i % 2 == 1) withStyle(SpanStyle(fontFamily = FontFamily.Monospace, color = Term.prompt)) { append(part) } else append(part)
    }
}

@Composable
private fun BodyCode(text: String) = Text(withCode(text), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** The choices of [key], shown by their labels. One tap sets the setting. */
@Composable
private fun Choices(vm: TerminalViewModel, key: ConfigKey<*>, choices: List<Pair<String, String>>) {
    val version by vm.config.version.collectAsStateWithLifecycle()
    val value = remember(version, key) { vm.config.display(key) }
    val selected = choices.firstOrNull { it.second == value }?.first ?: ""
    ConnectedChoices(choices.map { it.first }, selected) { label -> vm.setConfig(key, choices.first { it.first == label }.second) }
}

@Composable
private fun Welcome() = Page("") {
    Text("Kolahal", style = MaterialTheme.typography.displaySmall, modifier = Modifier.padding(top = 8.dp))
    Text("A home screen you type into.", style = MaterialTheme.typography.titleLarge)
    Body("Open apps, run commands and see what's happening on your phone, all from one line at the bottom of the screen. This tour takes a minute, and sets up a few things on the way: each page shows what a choice does as you make it.")
    val shape = RoundedCornerShape(16.dp)
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth().clip(shape).background(Term.bg, shape).border(1.dp, Term.dim.copy(alpha = 0.3f), shape).padding(14.dp),
    ) {
        @Composable
        fun line(text: String, command: Boolean = false) = Text(
            buildAnnotatedString {
                if (command) withStyle(SpanStyle(color = Term.prompt)) { append("$ ") }
                append(text)
            },
            style = Term.text.copy(color = Term.fg, fontWeight = if (command) FontWeight.Bold else null),
            maxLines = 1,
            overflow = TextOverflow.Clip,
            softWrap = false,
        )
        line("date tokyo london", command = true)
        line("Tokyo    10:45 PM · 3h30 ahead")
        line("London    2:45 PM · 4h30 behind")
        Spacer(Modifier.height(6.dp))
        line("todo add call the bank", command = true)
        line(" 1. [ ] call the bank")
        line("1 open")
    }
}

@Composable
private fun Typing(vm: TerminalViewModel) = Page("Type to open", preview = { TypingPreview(vm) }) {
    Setting("Open without Enter", "Once only one match is left. Calls and other sensitive commands always require Enter.") {
        Choices(vm, Keys.LAUNCH_INSTANT, listOf("Off" to "off", "Apps" to "apps", "All" to "all"))
    }
    Setting("Suggestions", "A list above the prompt, the best one nearest your thumb, or a strip of chips.") {
        Choices(vm, Keys.SUGGESTIONS_STYLE, listOf("List" to "list", "Chips" to "chips"))
    }
    BodyCode("Commands work as apps do: `help` lists them all, and `settings` opens Settings. Tap `$` on the prompt for quick actions.")
}

@Composable
private fun TheLog(vm: TerminalViewModel) = Page("Everything goes in the log", preview = { LogTryPreview() }) {
    val version by vm.config.version.collectAsStateWithLifecycle()
    // The header is off at first. Its swipe is explained only once the header is on (Make it yours,
    // or Settings → Header).
    val header = remember(version) { vm.config[Keys.HEADER_SHOW] }
    Body("Commands and their output, your notifications and your scripts' output: all in one log, the newest at the bottom.")
    val gestures = buildList {
        add(Triple(R.drawable.ic_search, "Type /word", "Searches the whole log"))
        add(Triple(R.drawable.ic_tune, "Pinch", "Makes the text bigger or smaller"))
        if (BuildConfig.LOCK_SERVICE) add(Triple(R.drawable.ic_lock, "Double-tap empty space", "Locks the screen, once you allow it on the Permissions page"))
        if (header) add(Triple(R.drawable.ic_arrow_down, "Swipe down on the header", "Pulls down the notifications"))
    }
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        gestures.forEachIndexed { i, (icon, title, detail) ->
            SegmentedListItem(
                onClick = {},
                enabled = false,
                shapes = segShapes(i, gestures.size),
                colors = segColors(),
                leadingContent = { Icon(painterResource(icon), null, tint = MaterialTheme.colorScheme.primary) },
                supportingContent = { Text(detail) },
            ) { Text(title, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
    BodyCode("The gestures can run any command: Settings → Gestures.")
}

@Composable
private fun Look(vm: TerminalViewModel) {
    val version by vm.config.version.collectAsStateWithLifecycle()
    Page("Make it yours", preview = { LookPreview(vm) }) {
        Setting("Theme", "Each has a dark and a light version, chosen by the phone's dark mode.") {
            val dark = isSystemInDarkTheme() || remember(version) { vm.config[Keys.THEME_ALWAYS_DARK] }
            ThemeSwatches(remember(version) { vm.config.display(Keys.THEME) }, dark) { vm.setConfig(Keys.THEME, it) }
        }
        Setting("Text size", "Pinch the log to change it later.") {
            Choices(vm, Keys.FONT_SIZE, listOf("12", "14", "16", "18").map { it to it })
        }
        Setting("Clock", "In the header and the log.") {
            Choices(vm, Keys.CLOCK_24H, listOf("12-hour" to "off", "24-hour" to "on"))
        }
        SwitchRow(
            "Header",
            "The time, the date, the battery and what else you choose, in a line at the top of home",
            remember(version) { vm.config[Keys.HEADER_SHOW] },
        ) { vm.setConfig(Keys.HEADER_SHOW, if (it) "on" else "off") }
    }
}

@Composable
private fun Keyboard(vm: TerminalViewModel) {
    val version by vm.config.version.collectAsStateWithLifecycle()
    Page("Above the keyboard", preview = { KeyboardPreview(vm) }) {
        SwitchRow(
            "Key bar",
            "History, arrows and a few symbols under the prompt, for keys the keyboard lacks",
            remember(version) { vm.config[Keys.KEYBAR_SHOW] },
        ) { vm.setConfig(Keys.KEYBAR_SHOW, if (it) "on" else "off") }
        SwitchRow(
            "Most-used apps",
            "Your most-used apps above an empty prompt, a tap away",
            remember(version) { vm.config[Keys.SUGGEST_IDLE] },
        ) { vm.setConfig(Keys.SUGGEST_IDLE, if (it) "on" else "off") }
    }
}

@Composable
private fun Selector(vm: TerminalViewModel) {
    val version by vm.config.version.collectAsStateWithLifecycle()
    Page("The app selector", preview = { SelectorPreview(vm) }) {
        Setting("App selector", "Every app along the screen's edge, to scroll through with your thumb.") {
            val on = remember(version) { vm.config[Keys.SELECTOR_RAIL] }
            val side = remember(version) { vm.config[Keys.SELECTOR_SIDE] }
            val selected = if (!on) "Off" else if (side == "left") "Left edge" else "Right edge"
            ConnectedChoices(listOf("Off", "Right edge", "Left edge"), selected) { pick ->
                when (pick) {
                    "Off" -> vm.setConfig(Keys.SELECTOR_RAIL, "off")
                    else -> {
                        vm.setConfig(Keys.SELECTOR_SIDE, if (pick == "Left edge") "left" else "right")
                        vm.setConfig(Keys.SELECTOR_RAIL, "on")
                    }
                }
            }
        }
    }
}

@Composable
private fun Access(vm: TerminalViewModel) = Page("Permissions") {
    val all by vm.setupChecks.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.refreshSetup() }
    val checks = TOUR_CHECKS.mapNotNull { fix -> all.firstOrNull { it.fix == fix } }
    BodyCode("Kolahal requests only what you turn on. These are the ones most people want; the rest are in Settings → Setup checklist, or type `setup`.")
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        checks.forEachIndexed { i, c ->
            SegmentedListItem(
                onClick = { if (!c.ok) vm.fixSetup(c) },
                shapes = segShapes(i, checks.size),
                colors = segColors(),
                leadingContent = { StatusDot(c.ok, informational = false) },
                supportingContent = {
                    Column {
                        Text(c.detail)
                        // An example of what this permission adds, as the user would see it.
                        PermissionExample(c.fix.orEmpty())
                    }
                },
                trailingContent = if (!c.ok) {
                    { FilledTonalButton(shapes = ButtonDefaults.shapes(), onClick = { vm.fixSetup(c) }) { Text(if (c.fix == "home") "Set" else "Allow") } }
                } else null,
            ) { Text(c.title) }
        }
    }
}

@Composable
private fun Done(vm: TerminalViewModel) = Page("You're all set") {
    BodyCode("`help` lists every command, `tour` reopens this, and Settings has everything you set here and more.")
    Text("Try one on the home screen:", style = MaterialTheme.typography.titleMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("fetch", "cal", "date tokyo", "help").forEach { cmd ->
            FilledTonalButton(
                shapes = ButtonDefaults.shapes(),
                onClick = {
                    // Go home, then run the command as if the user typed it there.
                    vm.openScreen(Screen.TERMINAL)
                    vm.runCommand(cmd)
                },
                modifier = Modifier.testTag("tour-try $cmd"),
            ) { Text(cmd, fontFamily = FontFamily.Monospace) }
        }
    }
}
