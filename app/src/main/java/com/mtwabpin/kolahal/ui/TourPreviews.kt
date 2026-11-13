package com.mtwabpin.kolahal.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.AppRepository
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.commands.CommandCatalog
import com.mtwabpin.kolahal.commands.InstantRun
import com.mtwabpin.kolahal.terminal.Suggestion
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// The tour's previews. Each setting gets a small home screen that uses the terminal's current
// colours, font and text size. Where possible, the previews use the real suggestion list, key bar
// and app selector. So the user sees the effect of a choice while making it.

/** A small home screen, in the terminal's colours as set now. */
@Composable
internal fun HomeFrame(height: Dp, modifier: Modifier = Modifier, tag: String = "tour-preview", content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(Term.bg, shape)
            .border(1.dp, Term.dim.copy(alpha = 0.3f), shape)
            .testTag(tag),
        content = content,
    )
}

/** The caption under a preview frame. It says what the preview shows and uses the tour's own text style. */
@Composable
internal fun PreviewCaption(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp).heightIn(min = 40.dp).testTag("tour-caption"),
    )
}

/** A log line in a preview. [time] goes in the right-hand column, like `log.time` places it. */
@Composable
private fun Line(text: String, color: Color = Term.fg, bold: Boolean = false, source: String? = null, time: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            buildAnnotatedString {
                append(text)
                source?.let { withStyle(SpanStyle(color = Term.dim)) { append(" · $it") } }
            },
            style = Term.text.copy(color = color, fontWeight = if (bold) FontWeight.Bold else null),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
        if (time != null) Text(time, style = Term.text.copy(color = Term.dim, fontSize = (Term.fontSize - 2).sp), modifier = Modifier.alignByBaseline().padding(start = 8.dp))
    }
}

@Composable
private fun CommandLine(text: String, time: String? = null) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Term.prompt)) { append("$ ") }
                append(text)
            },
            style = Term.text.copy(color = Term.fg, fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = Modifier.weight(1f).alignByBaseline(),
        )
        if (time != null) Text(time, style = Term.text.copy(color = Term.dim, fontSize = (Term.fontSize - 2).sp), modifier = Modifier.alignByBaseline().padding(start = 8.dp))
    }
}

/** The prompt: `$`, the typed text and a blinking caret. */
@Composable
private fun PreviewPrompt(text: String, enter: Boolean = false) {
    val blink = rememberInfiniteTransition(label = "caret")
    val caret by blink.animateFloat(1f, 0f, infiniteRepeatable(tween(530), RepeatMode.Reverse), label = "caret")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 12.dp),
    ) {
        Text("$ ", style = Term.text.copy(color = Term.prompt, fontWeight = FontWeight.Bold))
        Text(text, style = Term.text.copy(color = Term.fg), maxLines = 1)
        // The caret alpha is read only when the layer draws, so the blink does not recompose the prompt on every frame.
        Box(Modifier.width(2.dp).height((Term.fontSize + 4).dp).graphicsLayer { alpha = caret; clip = true }.background(Term.prompt))
        Spacer(Modifier.weight(1f))
        // Shows Enter being pressed. With `launch.instant off`, nothing runs until Enter.
        AnimatedVisibility(enter, enter = fadeIn() + scaleIn(), exit = fadeOut()) {
            Box(
                Modifier.background(Term.prompt, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp),
            ) { Text("Enter ⏎", style = Term.text.copy(color = Term.bg, fontWeight = FontWeight.Bold, fontSize = (Term.fontSize - 2).sp)) }
        }
    }
}

/** Formats a time the same way the log shows it. */
private fun logTime(clock24: Boolean, at: Date = Date()) = SimpleDateFormat(if (clock24) "HH:mm" else "h:mm", Locale.getDefault()).format(at)

@Composable
private fun rememberApps(): List<AppEntry> {
    val repo = AppRepository.get(LocalContext.current)
    val apps by repo.apps.collectAsStateWithLifecycle()
    return remember(apps) { apps.filter { !it.hidden && !it.profile.quiet }.sortedBy { it.label.lowercase() } }
}

/**
 * The typing preview. It types into the prompt:
 * - the first letters of an app, then
 * - `fetch`.
 *
 * Each one runs when `launch.instant` would run it (home uses the same check), or else when Enter
 * is pressed. The suggestions above the prompt follow `suggestions.style`.
 */
@Composable
internal fun TypingPreview(vm: TerminalViewModel) {
    val version by vm.config.version.collectAsStateWithLifecycle()
    val mode = remember(version) { vm.config[Keys.LAUNCH_INSTANT] }
    val minLetters = remember(version) { vm.config[Keys.LAUNCH_INSTANT_MIN] }
    val chips = remember(version) { vm.config[Keys.SUGGESTIONS_STYLE] == "chips" }
    val icons = remember(version) { vm.config[Keys.SUGGEST_ICONS] }
    val chipRows = remember(version) { vm.config[Keys.SUGGESTION_ROWS] }
    val clock24 = remember(version) { vm.config[Keys.CLOCK_24H] }
    val repo = AppRepository.get(LocalContext.current)
    val apps = rememberApps()
    val commands = CommandCatalog.COMMAND_USAGES

    fun target(typed: String, m: String) = InstantRun.target(
        typed, m, minLetters,
        rankedApps = { repo.searchRanked(it) }, appLabel = { it.label },
        commands = commands, aliases = emptyMap(), scriptCommands = emptyList(), isOn = { !it.profile.quiet },
    )

    // Finds one of the user's apps that `apps` mode would launch after only a few letters.
    val app by androidx.compose.runtime.produceState<AppEntry?>(null, apps, minLetters) {
        value = withContext(Dispatchers.Default) {
            apps.asSequence().filter { it.label.length >= 4 && it.label.first().isLetter() }.mapNotNull { a ->
                (minLetters..minOf(6, a.label.length)).firstOrNull { k ->
                    (target(a.label.take(k), InstantRun.APPS) as? InstantRun.Target.App)?.app?.key == a.key
                }?.let { a to it }
            }.minByOrNull { it.second }?.first
        }
    }

    var typed by remember { mutableStateOf("") }
    var enter by remember { mutableStateOf(false) }
    var opened by remember { mutableStateOf<AppEntry?>(null) }
    val lines = remember { mutableStateListOf<Pair<String, Boolean>>() } // text, is a command

    fun suggestionsFor(q: String): List<Suggestion> {
        if (q.isEmpty()) return emptyList()
        val appRows = repo.searchRanked(q).filter { it.second <= 2 }.take(3).map { Suggestion.App(it.first) }
        val commandRows = commands.keys.filter { it.startsWith(q.lowercase()) }.take(2).map { Suggestion.Completion(it, "$it ") }
        return (commandRows + appRows).take(4)
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(mode, app, minLetters, lifecycle) {
        lines.clear()
        val demo = app ?: return@LaunchedEffect
        val appWord = (minLetters..demo.label.length).firstOrNull { k ->
            (target(demo.label.take(k), InstantRun.APPS) as? InstantRun.Target.App)?.app?.key == demo.key
        }.let { demo.label.take(it ?: demo.label.length).lowercase() }

        /** Types [word] one letter at a time. Returns true if it ran by itself before the end. */
        suspend fun type(word: String): Boolean {
            for (i in 1..word.length) {
                typed = word.take(i)
                delay(420.milliseconds)
                if (target(typed, mode) != null) return true
            }
            delay(500.milliseconds)
            enter = true
            delay(650.milliseconds)
            enter = false
            return false
        }

        // Runs only while the tour is visible. Otherwise, if the tour stayed open behind another app, it would wake twice a second and search the apps.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // When the tour is visible again, start from the beginning, not from where it stopped.
            lines.clear()
            opened = null
            enter = false
            while (isActive) {
                typed = ""
                delay(900.milliseconds)
                type(appWord)
                typed = ""
                opened = demo
                lines += "Launched ${demo.label}" to false
                delay(1500.milliseconds)
                opened = null
                delay(500.milliseconds)
                // fetch takes no arguments, so it can run by itself (in `all` mode). Below are its first output lines.
                type("fetch")
                typed = ""
                lines += "fetch" to true
                lines += "${vm.config[Keys.USER_NAME]}@${android.os.Build.DEVICE}" to false
                lines += "OS  Android ${android.os.Build.VERSION.RELEASE} · API ${android.os.Build.VERSION.SDK_INT}" to false
                delay(2200.milliseconds)
                lines.clear()
            }
        }
    }

    HomeFrame(250.dp) {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            lines.forEach { (text, command) -> if (command) CommandLine(text, logTime(clock24)) else Line(text, color = Term.dim) }
            Spacer(Modifier.height(6.dp))
            val suggestions = remember(typed, apps) { suggestionsFor(typed) }
            if (chips) SuggestionStrip(suggestions, chipRows, onPick = {}, icons = icons)
            else SuggestionList(suggestions, onPick = {}, onAppLongPress = {}, icons = icons)
            PreviewPrompt(typed, enter)
        }
        // Shows the app opening: its icon and name, large, for a moment.
        AnimatedVisibility(opened != null, enter = fadeIn() + scaleIn(initialScale = 0.8f), exit = fadeOut(), modifier = Modifier.align(Alignment.Center)) {
            val a = opened ?: app
            if (a != null) Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.background(Term.surface, RoundedCornerShape(16.dp)).padding(16.dp),
            ) {
                AppIcon(a, 48.dp)
                Spacer(Modifier.height(6.dp))
                Text("${a.label} opens", style = Term.text.copy(color = Term.fg, fontWeight = FontWeight.Bold))
            }
        }
    }
    PreviewCaption(
        when (mode) {
            InstantRun.OFF -> "Off: nothing runs until you press Enter or tap a suggestion."
            InstantRun.APPS -> "Apps: an app opens once only one match is left; commands require Enter."
            else -> "All: an app or a command runs once only one match is left."
        }
    )
}

/** The look preview: the header line (when `header.show` is on), a few log lines with their times, and the prompt. */
@Composable
internal fun LookPreview(vm: TerminalViewModel) {
    val version by vm.config.version.collectAsStateWithLifecycle()
    val clock24 = remember(version) { vm.config[Keys.CLOCK_24H] }
    val header = remember(version) { vm.config[Keys.HEADER_SHOW] }
    val context = LocalContext.current
    val battery = remember {
        context.getSystemService(android.os.BatteryManager::class.java)
            ?.getIntProperty(android.os.BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 } ?: 80
    }
    val now = remember { Date() }
    val (month, week) = remember {
        val today = java.time.LocalDate.now()
        val monday = today.with(java.time.DayOfWeek.MONDAY)
        today.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")) to
            (0L..6L).joinToString(" ") { "%2d".format(monday.plusDays(it).dayOfMonth) }
    }
    HomeFrame(230.dp) {
        // Drawn like the real header, when it is on: the time and date, then the battery at the end.
        val locale = LocalConfiguration.current.locales[0]
        if (header) Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Text(
                SimpleDateFormat(if (clock24) "HH:mm" else "h:mm a", locale).format(now),
                style = Term.text.copy(color = Term.fg, fontWeight = FontWeight.Bold),
                modifier = Modifier.testTag("tour-clock"),
            )
            Spacer(Modifier.width(8.dp))
            Text(SimpleDateFormat("EEE d MMM", locale).format(now), style = Term.text.copy(color = Term.dim))
            Spacer(Modifier.weight(1f))
            BatteryGlyph(battery, false, Term.fg, Term.fontSize.dp)
            Spacer(Modifier.width(3.dp))
            Text("$battery%", style = Term.text.copy(color = Term.fg, fontWeight = FontWeight.Bold))
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            CommandLine("cal", logTime(clock24, now))
            Line("    $month", color = Term.dim)
            Line("Mo Tu We Th Fr Sa Su", color = Term.dim)
            Line(week)
            PreviewPrompt("")
        }
    }
}

/** A fake on-screen keyboard: rows of keys in the frame's colours. */
@Composable
private fun KeyboardStandIn() {
    Column(
        verticalArrangement = Arrangement.spacedBy(5.dp),
        modifier = Modifier.fillMaxWidth().background(Term.surface).padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        listOf(10, 9, 7).forEach { keys ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                repeat(keys) { Box(Modifier.weight(1f, fill = false).width(22.dp).height(16.dp).background(Term.dim.copy(alpha = 0.25f), RoundedCornerShape(4.dp))) }
            }
        }
    }
}

/** The area above the keyboard: most-used apps above an empty prompt, and the key bar below the prompt. */
@Composable
internal fun KeyboardPreview(vm: TerminalViewModel) {
    val version by vm.config.version.collectAsStateWithLifecycle()
    val keyBar = remember(version) { vm.config[Keys.KEYBAR_SHOW] }
    val mostUsed = remember(version) { vm.config[Keys.SUGGEST_IDLE] }
    val icons = remember(version) { vm.config[Keys.SUGGEST_ICONS] }
    val specs = remember(version) { listOf(vm.config[Keys.KEYBAR], vm.config[Keys.KEYBAR2]) }
    val rowHeight = remember(version) { vm.config[Keys.KEYBAR_HEIGHT].dp }
    val repo = AppRepository.get(LocalContext.current)
    val apps = rememberApps()
    // The user's most-used apps. If there are none yet, use any three of their apps.
    val top = remember(apps) { repo.top(3).ifEmpty { apps.take(3) } }
    HomeFrame(360.dp) {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            Line("Launched ${apps.firstOrNull()?.label ?: "Maps"}", color = Term.dim)
            Spacer(Modifier.height(6.dp))
            AnimatedVisibility(mostUsed, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                SuggestionList(top.map { Suggestion.App(it) }, onPick = {}, onAppLongPress = {}, icons = icons)
            }
            PreviewPrompt("")
            AnimatedVisibility(keyBar, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                KeyBar(specs, rowHeight, onKey = {}, onText = {})
            }
            KeyboardStandIn()
        }
    }
}

/**
 * The real app selector, on the chosen edge, with the user's apps. The user can try it here.
 * Picking an app shows what would open.
 */
@Composable
internal fun SelectorPreview(vm: TerminalViewModel) {
    val version by vm.config.version.collectAsStateWithLifecycle()
    val on = remember(version) { vm.config[Keys.SELECTOR_RAIL] }
    val left = remember(version) { vm.config[Keys.SELECTOR_SIDE] == "left" }
    val browse = remember(version) { vm.config[Keys.SELECTOR_BROWSE] }
    val apps = rememberApps()
    val pinned = remember(apps) { vm.pinnedKeys() }
    var picked by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(picked) { if (picked != null) { delay(2000.milliseconds); picked = null } }
    HomeFrame(330.dp, tag = "tour-selector-preview") {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 8.dp)) {
            CommandLine("cal")
            Line("Launched ${apps.firstOrNull()?.label ?: "Maps"}", color = Term.dim)
            PreviewPrompt("")
        }
        if (on) {
            AppSelector(
                apps = apps,
                pinned = pinned,
                onLaunch = { picked = "${it.label} would open" },
                onLongPress = { picked = "${it.label}'s actions would open" },
                left = left,
                position = remember(version) { vm.config[Keys.SELECTOR_POSITION] },
                showHandle = true,
                letters = remember(version) { vm.config[Keys.SELECTOR_LETTERS] },
                // The preview is not at the screen's edge, so a touch anywhere on the strip starts it.
                edgeBackGesture = false,
                icons = remember(version) { vm.config[Keys.SELECTOR_ICONS] },
                aimDp = remember(version) { vm.config[Keys.SELECTOR_AIM] },
                aimSpeed = remember(version) { vm.config[Keys.SELECTOR_AIM_SPEED] },
                browse = browse,
                filter = remember(version) { vm.config[Keys.SELECTOR_FILTER] },
                systemFont = remember(version) { vm.config[Keys.SELECTOR_SYSTEM_FONT] },
            )
        }
        AnimatedVisibility(picked != null, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)) {
            Text(
                picked.orEmpty(),
                style = Term.text.copy(color = Term.bg, fontWeight = FontWeight.Bold),
                modifier = Modifier.background(Term.prompt, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
    PreviewCaption(
        if (!on) "Off: the edge is free, and you type to open apps."
        else if (browse == "letters") "Try it: touch the ${if (left) "left" else "right"} edge and slide along it to a letter; slide off it onto the list and let go."
        else "Try it: touch the ${if (left) "left" else "right"} edge and slide along it, slowly for one app at a time; slide off it toward the list and let go."
    )
}

/** One line to try gestures on. The caption says what a tap and a long-press do. */
private class TryLine(val text: String, val command: Boolean, val source: String?, val tap: String, val longPress: String)

/**
 * A few log lines to try the log's gestures on:
 * - tap
 * - long-press
 * - swipe left (deletes)
 * - swipe right (copies)
 */
@Composable
internal fun LogTryPreview() {
    val apps = rememberApps()
    val appName = apps.firstOrNull { it.label.equals("Maps", true) }?.label ?: apps.firstOrNull()?.label ?: "Maps"
    val tries = remember(appName) {
        listOf(
            TryLine("cal", command = true, source = null, tap = "Tap: runs cal again.", longPress = "Long-press: run again, edit in the prompt, copy, delete, and when it ran."),
            TryLine("Launched $appName", command = false, source = null, tap = "Tap: opens $appName again.", longPress = "Long-press: $appName's actions, its shortcuts and app info."),
            TryLine("Show HN: A tiny terminal", command = false, source = "Hacker News", tap = "Tap: opens the story.", longPress = "Long-press: open, share or copy the link, and where it came from."),
        )
    }
    var caption by remember { mutableStateOf("Try them: tap a line, long-press it, or swipe it sideways.") }
    val gone = remember { mutableStateListOf<Int>() }
    val scope = rememberCoroutineScope()
    HomeFrame(170.dp, tag = "tour-log-preview") {
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(bottom = 8.dp)) {
            tries.forEachIndexed { i, t ->
                AnimatedVisibility(i !in gone, exit = shrinkVertically() + fadeOut(), enter = expandVertically() + fadeIn()) {
                    val offset = remember { Animatable(0f) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 40.dp)
                            .offset { IntOffset(offset.value.roundToInt(), 0) }
                            .pointerInput(i) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        val w = size.width
                                        val x = offset.value
                                        scope.launch {
                                            when {
                                                x < -w / 3f -> {
                                                    caption = "Swiped left: deleted. Undo shows for a moment."
                                                    offset.animateTo(-w.toFloat())
                                                    gone += i
                                                    delay(1800.milliseconds)
                                                    offset.snapTo(0f)
                                                    gone -= i
                                                }
                                                x > w / 3f -> {
                                                    caption = "Swiped right: copied."
                                                    offset.animateTo(0f)
                                                }
                                                else -> offset.animateTo(0f)
                                            }
                                        }
                                    },
                                ) { change, dx ->
                                    change.consume()
                                    scope.launch { offset.snapTo(offset.value + dx) }
                                }
                            }
                            .combinedClickable(onClick = { caption = t.tap }, onLongClick = { caption = t.longPress })
                            .testTag("tour-try-line $i"),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (t.command) CommandLine(t.text) else Line(t.text, color = if (t.source != null) Term.fg else Term.dim, source = t.source)
                    }
                }
            }
        }
    }
    PreviewCaption(caption)
}

/** A small example of what a permission adds. It uses the terminal's colours and fits beside its button. */
@Composable
internal fun PermissionExample(fix: String) {
    val dim = SpanStyle(color = Term.dim)
    val accent = SpanStyle(color = Term.prompt, fontWeight = FontWeight.Bold)
    val lines = when (fix) {
        "home" -> listOf(buildAnnotatedString { withStyle(dim) { append("Home, from any app, brings you back here") } })
        "notifications" -> listOf(buildAnnotatedString { withStyle(accent) { append("Kolahal") }; withStyle(dim) { append(" · ") }; append("hn: 3 new stories") })
        "notifs enable" -> listOf(buildAnnotatedString { append("Mom: Dinner at 8?"); withStyle(dim) { append(" · Messages") } })
        "accessibility" -> listOf(buildAnnotatedString { withStyle(dim) { append("Double-tap empty space: the screen locks") } })
        "contacts --allow" -> listOf(
            buildAnnotatedString { withStyle(accent) { append("  Mom") } },
            buildAnnotatedString { withStyle(accent) { append("$ ") }; append("call mo") },
        )
        else -> emptyList()
    }
    if (lines.isEmpty()) return
    Column(
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Term.bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        lines.forEach { Text(it, style = Term.text.copy(color = Term.fg, fontSize = (Term.fontSize - 3).sp), maxLines = 2) }
    }
}
