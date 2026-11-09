package com.mtwabpin.kolahal.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.shortTime
import com.mtwabpin.kolahal.script.ScriptAccess
import com.mtwabpin.kolahal.script.ScriptLibrary

/**
 * The card for one script. The card uses the theme colour when the script is on, and is faded when
 * it is off.
 * - Compact: a dot (on, failed, off), the title, the file name and where it came from (shared or
 *   yours), Run for a command, the details arrow, the switch, and one line about how it runs and how
 *   its last run went.
 * - A pill beside that line offers what needs doing: "New version · Update", "Edited · Reset" or
 *   "Add key".
 * - Expanded: also the status line, description, next run, usage, events and what it may use.
 */
@Composable
internal fun ScriptCard(
    info: ScriptInfo,
    line: String?,
    /** Declared secrets with no value yet, and (in [secretsSet]) those that have one. */
    missingKeys: List<String>,
    secretsSet: List<String>,
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
    onAddKey: () -> Unit,
) {
    val s = info.script
    val colors = MaterialTheme.colorScheme
    val failed = s.failed()
    val summary = buildAnnotatedString {
        fun part(text: String, color: Color? = null) {
            if (length > 0) append(" · ")
            if (color == null) append(text) else withStyle(SpanStyle(color = color)) { append(text) }
        }
        // Leave out events that are switched off, because they don't run the script.
        part(schedule(s, info.isCommand, info.events.filter { info.grants.allows(it) }))
        part(s.lastRun?.let { (if (failed) "failed " else "ran ") + relative(it, short = true) } ?: "never run", if (failed) colors.error else null)
        if (missingKeys.isNotEmpty()) part("needs " + missingKeys.joinToString(", "), colors.error)
        if (selfUpdatedIn != null) part("updated in $selfUpdatedIn", colors.primary)
    }
    // What needs doing, one tap away. A key comes first: without it, the script can't work.
    val pills = listOfNotNull(
        if (missingKeys.isNotEmpty()) Pill(null, "Add key", R.drawable.ic_key, onAddKey) else null,
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
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape)
                        .background(if (!s.enabled) colors.outlineVariant else if (failed) colors.error else colors.primary)
                )
                Spacer(Modifier.width(12.dp))
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 8.dp)) {
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
                Row(Modifier.fillMaxWidth().padding(end = 8.dp, top = 4.dp), horizontalArrangement = Arrangement.End) { CardPill(pills[1]) }
            }
            if (expanded) ScriptDetails(info, line, secretsSet, copy, onAccess, onNotify, onCompare, onRestore)
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
 * The details of a card:
 * - the status line, description, next run and usage;
 * - a pill for each thing the script may do (on or off; a tap switches it);
 * - its secrets;
 * - for a library or shared copy that differs: Compare (library copies only) and Reset or Update.
 */
@Composable
private fun ScriptDetails(
    info: ScriptInfo,
    line: String?,
    secretsSet: List<String>,
    copy: ScriptCopy?,
    onAccess: (String, Boolean) -> Unit,
    onNotify: (Boolean) -> Unit,
    onCompare: () -> Unit,
    onRestore: () -> Unit,
) {
    val s = info.script
    val colors = MaterialTheme.colorScheme
    val grants = info.grants
    Column(Modifier.padding(start = 20.dp, top = 10.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        line?.let {
            Surface(shape = MaterialTheme.shapes.small, color = colors.surfaceContainerHighest) {
                Text(
                    "${s.name} ▸ $it",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        info.desc?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        nextRun(s)?.let { (at, exact) ->
            val time = Config.get(LocalContext.current).shortTime(at)
            Fact("Next run", if (exact) time else "about $time")
        }
        info.usage?.let { Fact("Usage", it) }
        // Everything the script may do, as in its sheet. A tap switches one.
        // - Access: internet, pinning and notifications when its code uses them, and what it declares with @uses.
        // - Separately, the events from its @on lines.
        val (events, access) = info.accessItems.partition { it in info.events }
        if (access.isNotEmpty() || info.notifies) PillGroup("Access") {
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
        if (events.isNotEmpty()) PillGroup("Events") {
            for (item in events) {
                val allowed = grants.allows(item)
                AccessPill(item.replaceFirstChar { it.uppercase() }, allowed, PILL_ICONS[item]) { onAccess(item, !allowed) }
            }
        }
        if (info.secrets.isNotEmpty()) {
            Fact("Secrets", info.secrets.joinToString(", ") { d -> d.name + if (d.name in secretsSet) " (set)" else " (not set)" })
        }
        // The last run's failure, in the same words as the editor ("Line 7: …", "Stopped: …").
        if (s.failed()) Text(EditorLogic.runResult(s.lastStatus, com.mtwabpin.kolahal.script.ScriptWorker.TIMEOUT_MS / 1000, tapHint = false).text, style = MaterialTheme.typography.bodySmall, color = colors.error)
        // A copy that was edited or has a newer version: show how it differs, and offer the source's version.
        if (copy != null && copy.canRestore) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (copy.lib != null) CompareButton(ButtonDefaults.ExtraSmallContainerHeight, onCompare)
                CopyButton(copy, ButtonDefaults.ExtraSmallContainerHeight, onRestore)
            }
        }
    }
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
 * when off. A tap switches it.
 */
@Composable
private fun AccessPill(label: String, on: Boolean, icon: Int?, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = on,
        onClick = onToggle,
        // One line: a long label is cut, so a pill never grows to two lines and breaks the row.
        label = { Text(label, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
        leadingIcon = icon?.let { { Icon(painterResource(it), null, Modifier.size(FilterChipDefaults.IconSize)) } },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = colors.outline,
            iconColor = colors.outline,
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
            selectedLeadingIconColor = colors.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = on, borderColor = colors.outlineVariant),
        modifier = Modifier.semantics { stateDescription = if (on) "on" else "off" },
    )
}

/** An icon for every access and event pill, so the pills look alike. */
private val PILL_ICONS = mapOf(
    ScriptAccess.INTERNET to R.drawable.ic_public, "clipboard" to R.drawable.ic_clipboard, "dnd" to R.drawable.ic_do_not_disturb,
    "headset" to R.drawable.ic_headset,
    ScriptAccess.HEADER to R.drawable.ic_home, "notifications" to R.drawable.ic_notifications, "apps" to R.drawable.ic_apps,
    "commands" to R.drawable.ic_stat_terminal, "work" to R.drawable.ic_work,
    "unlock" to R.drawable.ic_lock_open, "charger" to R.drawable.ic_battery, "unplug" to R.drawable.ic_battery,
    "wifi" to R.drawable.ic_wifi, "offline" to R.drawable.ic_cloud_off, "notification" to R.drawable.ic_notifications,
    "boot" to R.drawable.ic_restart,
)

/** A row of pills in a card under a small [label]: what a script may use, or the events it runs on. */
@Composable
private fun PillGroup(label: String, pills: @Composable FlowRowScope.() -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = pills)
    }
}
