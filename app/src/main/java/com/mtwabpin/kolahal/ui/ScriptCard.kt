package com.mtwabpin.kolahal.ui

import android.text.format.DateUtils
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.shortTime
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptLibrary
import com.mtwabpin.kolahal.script.ScriptWorker

/*
 * Card measures. Everything lines up on three edges:
 * - the gutter: the first 24 dp. The status dot and the icons of the detail lines are centred in it.
 * - the content edge, right after the gutter: the title, the summary and every line's text start here.
 * - the right edge: the switch, the lines and the buttons end here.
 */
private val CARD_SIDE = 12.dp
private val GUTTER = 24.dp
private val LINE_ICON = 14.dp

/**
 * The card for one script. The card uses the theme colour when the script is on, and is faded when
 * it is off.
 * - Compact: a dot (on, failed, off), the title, the file name and where it came from (shared or
 *   yours), Run for a command, the details arrow, the switch, and one line about how it runs and how
 *   its last run went.
 * - A pill beside that line offers what needs doing: "New version · Update", "Edited · Reset" or
 *   "Add key".
 * - Expanded: see [ScriptDetails]. Then a line of its own tells each problem with its button, so
 *   the summary leaves the problem out and the pill is hidden.
 */
@Composable
internal fun ScriptCard(
    info: ScriptInfo,
    line: String?,
    /** Declared secrets with no value yet. */
    missingKeys: List<String>,
    expanded: Boolean,
    /** How a library or shared copy compares with its source. Null for a script the user wrote. */
    copy: ScriptCopy?,
    /** The Kolahal version that updated it by itself, while the note about that shows. */
    selfUpdatedIn: String?,
    onExpand: () -> Unit,
    onClick: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onRun: () -> Unit,
    onAccess: (String, Boolean) -> Unit,
    onNotify: (Boolean) -> Unit,
    onCompare: () -> Unit,
    /** Update or Reset, from the pill or the details. */
    onRestore: () -> Unit,
    /** Add the value of the named secret. */
    onAddKey: (String) -> Unit,
) {
    val s = info.script
    val colors = MaterialTheme.colorScheme
    val failed = s.failed()
    // Each part starts with a capital: "Command · Daily 00:05 · Never run".
    val summary = buildAnnotatedString {
        fun part(text: String, color: Color? = null) {
            if (length > 0) append(" · ")
            val t = text.replaceFirstChar { it.uppercase() }
            if (color == null) append(t) else withStyle(SpanStyle(color = color)) { append(t) }
        }
        // Leave out events that are switched off, because they don't run the script.
        scheduleParts(s, info.isCommand, info.events.filter { info.grants.allows(it) }).forEach { part(it) }
        // Open, the card has a line for a failure and a missing key: say them only there.
        if (!(failed && expanded)) {
            part(s.lastRun?.let { (if (failed) "Failed " else "Ran ") + relative(it, short = true) } ?: "Never run", if (failed) colors.error else null)
        }
        if (missingKeys.isNotEmpty() && !expanded) part("Needs " + missingKeys.joinToString(", "), colors.error)
        if (selfUpdatedIn != null) part("Updated in $selfUpdatedIn", colors.primary)
    }
    // What needs doing, one tap away. A key comes first: without it, the script can't work.
    // Open, the lines below have the buttons, so there is no pill.
    val pills = if (expanded) emptyList() else listOfNotNull(
        if (missingKeys.isNotEmpty()) Pill(null, "Add key", R.drawable.ic_key) { onAddKey(missingKeys.first()) } else null,
        copy?.takeIf { it.canRestore }?.let { c ->
            if (c.state == CopyState.NEW_VERSION) Pill("New version", "Update", R.drawable.ic_arrow_up, onRestore)
            else Pill("Edited", "Reset", R.drawable.ic_refresh, onRestore)
        },
    )
    // Show on or off at a glance: an on script's card uses the theme colour, an off script's card fades into the page.
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = if (s.enabled) colors.primaryContainer else colors.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().animateContentSize().testTag("script-card"),
    ) {
        Column(Modifier.padding(start = CARD_SIDE, end = CARD_SIDE, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(GUTTER), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (!s.enabled) colors.outlineVariant else if (failed) colors.error else colors.primary)
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        info.title ?: s.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (s.enabled) colors.onPrimaryContainer else colors.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    // The file, and where it came from when that isn't the library.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${s.name}.${s.lang.ext}",
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = if (s.enabled) scriptColor(s.name) else colors.outline,
                            maxLines = 1,
                        )
                        val from = when (copy?.source) {
                            ScriptSource.LIBRARY -> null
                            ScriptSource.SHARED -> "shared"
                            else -> "yours"
                        }
                        if (from != null) Text(" · $from", style = MaterialTheme.typography.labelSmall, color = colors.outline, maxLines = 1)
                    }
                }
                if (info.isCommand && s.enabled) TextButton(shapes = ButtonDefaults.shapes(), onClick = onRun) { Text("Run") }
                IconButton(shapes = IconButtonDefaults.shapes(), onClick = onExpand) {
                    Icon(
                        painterResource(R.drawable.ic_expand_more),
                        contentDescription = if (expanded) "Hide details" else "Show details",
                        modifier = Modifier.rotate(if (expanded) 180f else 0f),
                    )
                }
                Switch(checked = s.enabled, onCheckedChange = onEnabled)
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = GUTTER)) {
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = if (expanded) 3 else 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                pills.firstOrNull()?.let { CardPill(it) }
            }
            // A second pill gets a line of its own, so the summary keeps its room.
            if (pills.size > 1) {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) { CardPill(pills[1]) }
            }
            if (expanded) ScriptDetails(info, line, missingKeys, copy, onAccess, onNotify, onCompare, onRestore, onAddKey)
        }
    }
}

/** A pill on a card: what it says ([label], muted, may be null) and what a tap does ([action], with [icon]). */
private class Pill(val label: String?, val action: String, val icon: Int, val onClick: () -> Unit)

/** A [Pill]: outlined, with its label and action split by a thin line. The whole pill is one button. */
@Composable
private fun CardPill(pill: Pill) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = pill.onClick,
        shape = CircleShape,
        color = Color.Transparent,
        border = BorderStroke(1.dp, colors.outline),
        modifier = Modifier.padding(start = 8.dp).testTag("script-pill"),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(28.dp).padding(horizontal = 10.dp)) {
            pill.label?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant, maxLines = 1)
                Box(Modifier.padding(horizontal = 8.dp).width(1.dp).height(16.dp).background(colors.outlineVariant))
            }
            Icon(painterResource(pill.icon), null, Modifier.size(14.dp), tint = colors.primary)
            Spacer(Modifier.width(4.dp))
            Text(pill.action, style = MaterialTheme.typography.labelMedium, color = colors.primary, maxLines = 1)
        }
    }
}

/**
 * The details of an open card, from top to bottom:
 * - the status line and the description;
 * - a line for each problem, with its button: the last run failed, a key is not set, the copy was
 *   edited, or the library has a newer version;
 * - lines with an icon in the gutter: when it runs (clock, with the event pills), its usage
 *   (terminal, commands only) and what it may use (shield, as pills). A tap on a pill switches it.
 * Secrets that are set don't show: the sheet has them.
 */
@Composable
private fun ScriptDetails(
    info: ScriptInfo,
    line: String?,
    missingKeys: List<String>,
    copy: ScriptCopy?,
    onAccess: (String, Boolean) -> Unit,
    onNotify: (Boolean) -> Unit,
    onCompare: () -> Unit,
    onRestore: () -> Unit,
    onAddKey: (String) -> Unit,
) {
    val s = info.script
    val colors = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val grants = info.grants
    val warn = Term.palette.warn
    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        line?.let {
            Surface(shape = MaterialTheme.shapes.small, color = colors.surfaceContainerHighest, modifier = Modifier.padding(start = GUTTER)) {
                Text(
                    it,
                    style = type.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        info.desc?.let { Text(it, style = type.bodyMedium, modifier = Modifier.padding(start = GUTTER)) }

        // Problems first: they say what is wrong and offer the fix.
        // The last run's failure, said once: "Failed 10:42 · HTTP 401: invalid key".
        if (s.failed()) {
            val result = EditorLogic.runResult(s.lastStatus, ScriptWorker.TIMEOUT_MS / 1000, tapHint = false).text.removePrefix("Error: ")
            val text = buildAnnotatedString {
                append("Failed ${s.lastRun?.let { runTime(it) }.orEmpty()}".trimEnd())
                append(" · ")
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp)) { append(result) }
            }
            ProblemLine(R.drawable.ic_error, colors.error, text, textColor = colors.error)
        }
        for (key in missingKeys) {
            ProblemLine(R.drawable.ic_key, colors.error, AnnotatedString("$key is not set"), textColor = colors.error) {
                TextAction("Add key") { onAddKey(key) }
            }
        }
        if (copy != null && copy.canRestore) {
            val library = copy.lib != null
            if (copy.state == CopyState.NEW_VERSION) {
                ProblemLine(
                    R.drawable.ic_arrow_up, warn,
                    AnnotatedString(if (library) "Newer version in the library" else "Newer shared version"),
                    hint = "Updating replaces your edits".takeIf { copy.edited },
                ) {
                    if (copy.edited && library) TextAction("Compare", onCompare)
                    TextAction("Update", onRestore)
                }
            } else {
                ProblemLine(R.drawable.ic_edit, warn, AnnotatedString("Edited by you")) {
                    if (library) TextAction("Compare", onCompare)
                    TextAction(if (library) "Reset to library" else "Reset to shared", onRestore)
                }
            }
        }

        // When it runs. An event script has its event pills here, and the run info under them.
        val (events, access) = info.accessItems.partition { it in info.events }
        val timed = scheduleParts(s, isCommand = false, events = emptyList(), manual = events.isEmpty() && !info.isCommand)
        val next = nextRun(s)?.let { (at, exact) ->
            val time = Config.get(LocalContext.current).shortTime(at)
            if (exact) "Next $time" else "Next about $time"
        }
        // A failed run is said in its own line above.
        val last = if (s.failed()) null else s.lastRun?.let { "Ran " + relative(it, short = true) } ?: "Never run"
        val runs = (timed + listOfNotNull(next, last)).joinToString(" · ")
        if (events.isNotEmpty()) {
            IconLine(R.drawable.ic_schedule, PILL_HEIGHT) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Pills {
                        for (item in events) {
                            val allowed = grants.allows(item)
                            AccessPill(item.replaceFirstChar { it.uppercase() }, allowed, PILL_ICONS[item]) { onAccess(item, !allowed) }
                        }
                    }
                    if (runs.isNotEmpty()) Text(runs, style = type.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        } else if (runs.isNotEmpty()) {
            IconLine(R.drawable.ic_schedule, type.bodyMedium.lineHeightDp()) {
                Text(runs, style = type.bodyMedium)
            }
        }

        // How to call a command. It breaks only after "|", so each choice stays whole.
        info.usage?.let { usage ->
            val mono = type.bodySmall.copy(fontFamily = FontFamily.Monospace, lineHeight = 18.sp)
            IconLine(R.drawable.ic_terminal, mono.lineHeightDp()) {
                FlowRow {
                    val parts = usage.split("|")
                    parts.forEachIndexed { i, part -> Text(if (i < parts.lastIndex) "$part|" else part, style = mono) }
                }
            }
        }

        // Everything the script may do, as in its sheet: internet, pinning and notifications when
        // its code uses them, and what it declares with @uses.
        if (access.isNotEmpty() || info.notifies) IconLine(R.drawable.ic_shield, PILL_HEIGHT) {
            Pills {
                // Notifications come after internet and pinning, as in the sheet.
                val (base, declared) = access.partition { it == ScriptAccess.INTERNET || it == ScriptAccess.HEADER }
                for (item in base + listOfNotNull("notify".takeIf { info.notifies }) + declared) {
                    if (item == "notify") {
                        AccessPill("Notifies", s.notify, R.drawable.ic_notifications) { onNotify(!s.notify) }
                        continue
                    }
                    val name = (USES_SHORT[item] ?: if (item == ScriptAccess.HEADER) "pinned" else ScriptAccess.label(item).lowercase()).replaceFirstChar { it.uppercase() }
                    val allowed = grants.allows(item)
                    AccessPill(if (item in grants.undecided) "$name · not allowed yet" else name, allowed, PILL_ICONS[item]) { onAccess(item, !allowed) }
                }
            }
        }
    }
}

/** When a run was: "10:42" today, else how long ago ("3 days ago"). */
@Composable
private fun runTime(at: Long): String =
    if (DateUtils.isToday(at)) Config.get(LocalContext.current).shortTime(at) else relative(at, short = true)

/** The line height of a text style, in dp. It follows the phone's font size. */
@Composable
private fun TextStyle.lineHeightDp(): Dp = with(LocalDensity.current) { lineHeight.toDp() }

/** How tall a pill looks. Its touch area is taller (48 dp) and spills over the space around it. */
private val PILL_HEIGHT = 32.dp

/**
 * A line of the details with an [icon] in the gutter. The icon is centred on the first
 * [anchor] dp of the line: the first line of text, or the first row of pills.
 */
@Composable
private fun IconLine(icon: Int, anchor: Dp, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant, content: @Composable () -> Unit) {
    Row {
        Box(Modifier.width(GUTTER).height(anchor), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), null, Modifier.size(LINE_ICON), tint = tint)
        }
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * A problem, or a copy that differs from its source: an [icon] in [iconColor], the [text] (with a
 * smaller [hint] under it), and small text buttons at the right end.
 */
@Composable
private fun ProblemLine(
    icon: Int,
    iconColor: Color,
    text: AnnotatedString,
    textColor: Color = Color.Unspecified,
    hint: String? = null,
    buttons: @Composable () -> Unit = {},
) {
    val type = MaterialTheme.typography
    IconLine(icon, type.bodyMedium.lineHeightDp(), tint = iconColor) {
        Row {
            Column(Modifier.weight(1f)) {
                Text(text, style = type.bodyMedium, color = textColor)
                hint?.let { Text(it, style = type.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Row(Modifier.padding(start = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { buttons() }
        }
    }
}

/**
 * A small button that looks like text, in the theme colour. Its text lines up with the text beside
 * it. Its touch area is larger (40 dp tall, 6 dp more on each side) and spills over the space
 * around it.
 */
@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    val style = MaterialTheme.typography.labelLarge.copy(fontSize = 13.sp, lineHeight = MaterialTheme.typography.bodyMedium.lineHeight)
    val line = style.lineHeightDp()
    Box(
        Modifier
            .layout { measurable, constraints ->
                val side = 6.dp.roundToPx()
                val p = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0, maxWidth = constraints.maxWidth + 2 * side))
                val h = minOf(p.height, line.roundToPx())
                layout(p.width - 2 * side, h) { p.place(-side, (h - p.height) / 2) }
            }
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = style, color = MaterialTheme.colorScheme.primary, maxLines = 1)
    }
}

/** A row of pills: they wrap to more rows when there are many. */
@Composable
private fun Pills(content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/**
 * A button that brings the source's version of a library or shared [copy]. It is [height] tall (as
 * small as a pill in a card).
 * - Edited, nothing newer: Reset to library version (or shared version).
 * - A newer version: Update from library, or Update to its version for a shared copy.
 * The icon uses the error colour when the copy is edited, because the edits are lost. Then it asks first.
 */
@Composable
internal fun CopyButton(copy: ScriptCopy, height: Dp, onClick: () -> Unit) {
    val label = when {
        copy.state == CopyState.EDITED -> if (copy.lib != null) "Reset to library version" else "Reset to shared version"
        copy.lib != null -> "Update from library"
        else -> copy.shared?.version?.takeIf { it.isNotBlank() }?.let { "Update to $it" } ?: "Update"
    }
    OutlinedAction(label, R.drawable.ic_refresh, height, iconTint = if (copy.edited) MaterialTheme.colorScheme.error else null, onClick = onClick)
}

/** Whether a library copy is not this version's: it was edited, or it comes from an older Kolahal. */
internal fun ScriptLibrary.State.differs() = this == ScriptLibrary.State.MODIFIED || this == ScriptLibrary.State.UPDATE_AVAILABLE

/** The Compare button, [height] tall. It shows a library copy next to the library version. */
@Composable
internal fun CompareButton(height: Dp, onClick: () -> Unit) = OutlinedAction("Compare", R.drawable.ic_visibility, height, onClick = onClick)

/**
 * The button for Compare and Reset, [height] tall. It is outlined, in the theme's text and outline
 * colours, so it looks right both on an on script's tinted card and on the page, in every theme.
 * [iconTint] applies only to the icon.
 */
@Composable
private fun OutlinedAction(label: String, icon: Int, height: Dp, iconTint: Color? = null, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    OutlinedButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapesFor(height),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.onSurface),
        border = BorderStroke(1.dp, colors.outline),
        contentPadding = ButtonDefaults.contentPaddingFor(height),
        modifier = Modifier.heightIn(min = height),
    ) {
        Icon(painterResource(icon), null, Modifier.size(ButtonDefaults.iconSizeFor(height)), tint = iconTint ?: colors.onSurface)
        Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(label, style = ButtonDefaults.textStyleFor(height))
    }
}

/**
 * One thing a script may do. Its colour shows on or off: filled with the theme colour when on, faded
 * when off. A tap switches it. It takes the space of its visible 32 dp; its 48 dp touch area spills
 * over the space around it, so rows of pills sit close.
 */
@Composable
private fun AccessPill(label: String, on: Boolean, icon: Int?, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = on,
        onClick = onToggle,
        // One line: a long label is cut, so a pill never grows to two lines and breaks the row.
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = icon?.let { { Icon(painterResource(it), null, Modifier.size(FilterChipDefaults.IconSize)) } },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = colors.outline,
            iconColor = colors.outline,
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
            selectedLeadingIconColor = colors.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = on, borderColor = colors.outlineVariant),
        modifier = Modifier
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints.copy(minHeight = 0))
                val h = minOf(p.height, PILL_HEIGHT.roundToPx())
                layout(p.width, h) { p.place(0, (h - p.height) / 2) }
            }
            .semantics { stateDescription = if (on) "on" else "off" },
    )
}

/** An icon for every access and event pill, so the pills look alike. ScriptUsesTest checks that each `@uses` item has one. */
internal val PILL_ICONS = mapOf(
    ScriptAccess.INTERNET to R.drawable.ic_public, "clipboard" to R.drawable.ic_clipboard, "dnd" to R.drawable.ic_do_not_disturb,
    "headset" to R.drawable.ic_headset,
    ScriptAccess.HEADER to R.drawable.ic_home, "notifications" to R.drawable.ic_notifications, "apps" to R.drawable.ic_apps,
    "commands" to R.drawable.ic_stat_terminal, "work" to R.drawable.ic_work,
    "unlock" to R.drawable.ic_lock_open, "charger" to R.drawable.ic_battery, "unplug" to R.drawable.ic_battery,
    "wifi" to R.drawable.ic_wifi, "offline" to R.drawable.ic_cloud_off, "notification" to R.drawable.ic_notifications,
    "boot" to R.drawable.ic_restart,
    "todos" to R.drawable.ic_check, "notes" to R.drawable.ic_edit, "reminders" to R.drawable.ic_alarm,
    "calendar" to R.drawable.ic_calendar, "feeds" to R.drawable.ic_rss,
)
