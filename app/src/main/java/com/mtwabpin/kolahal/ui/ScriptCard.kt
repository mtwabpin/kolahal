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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
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
 * - Compact: a dot (on, failed, off), the title and file name, Run for a command, the details
 *   arrow, the switch, and one line about how it runs and how its last run went.
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
    updateAvailable: Boolean,
    /** State of the library copy, edited or not. Null for a script the user wrote. */
    library: ScriptLibrary.State?,
    onExpand: () -> Unit,
    onClick: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onRun: () -> Unit,
    onAccess: (String, Boolean) -> Unit,
    onNotify: (Boolean) -> Unit,
    onCompare: () -> Unit,
    onReset: () -> Unit,
) {
    val s = info.script
    val colors = MaterialTheme.colorScheme
    val failed = s.failed()
    val summary = buildList {
        // Leave out events that are switched off, because they don't run the script.
        add(schedule(s, info.isCommand, info.events.filter { info.grants.allows(it) }))
        add(s.lastRun?.let { (if (failed) "failed " else "ran ") + relative(it, short = true) } ?: "never run")
        if (missingKeys.isNotEmpty()) add("needs " + missingKeys.joinToString(", "))
        if (updateAvailable) add("update available")
        if (library == ScriptLibrary.State.MODIFIED) add("edited")
    }.joinToString(" · ")
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
                    Text(
                        "${s.name}.${s.lang.ext}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                        color = if (s.enabled) scriptColor(s.name) else colors.outline,
                        maxLines = 1,
                    )
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
            Text(
                summary,
                style = MaterialTheme.typography.bodySmall,
                color = if (failed || missingKeys.isNotEmpty()) colors.error else if (updateAvailable) colors.primary else colors.onSurfaceVariant,
                maxLines = if (expanded) 3 else 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 20.dp, end = 8.dp),
            )
            if (expanded) ScriptDetails(info, line, secretsSet, library, onAccess, onNotify, onCompare, onReset)
        }
    }
}

/**
 * The details of a card:
 * - the status line, description, next run and usage;
 * - a pill for each thing the script may do (on or off; a tap switches it);
 * - its secrets;
 * - for an edited or older library copy, Compare and Reset or Update.
 */
@Composable
private fun ScriptDetails(
    info: ScriptInfo,
    line: String?,
    secretsSet: List<String>,
    library: ScriptLibrary.State?,
    onAccess: (String, Boolean) -> Unit,
    onNotify: (Boolean) -> Unit,
    onCompare: () -> Unit,
    onReset: () -> Unit,
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
                    AccessPill("notifies", s.notify) { onNotify(!s.notify) }
                    continue
                }
                val name = USES_SHORT[item] ?: if (item == ScriptAccess.HEADER) "pinned" else ScriptAccess.label(item).lowercase()
                val allowed = grants.allows(item)
                AccessPill(if (item in grants.undecided) "$name · not allowed yet" else name, allowed) { onAccess(item, !allowed) }
            }
        }
        if (events.isNotEmpty()) PillGroup("Events") {
            for (item in events) {
                val allowed = grants.allows(item)
                AccessPill(item, allowed) { onAccess(item, !allowed) }
            }
        }
        if (info.secrets.isNotEmpty()) {
            Fact("Secrets", info.secrets.joinToString(", ") { d -> d.name + if (d.name in secretsSet) " (set)" else " (not set)" })
        }
        // The last run's failure, in the same words as the editor ("Line 7: …", "Stopped: …").
        if (s.failed()) Text(EditorLogic.runResult(s.lastStatus, com.mtwabpin.kolahal.script.ScriptWorker.TIMEOUT_MS / 1000, tapHint = false).text, style = MaterialTheme.typography.bodySmall, color = colors.error)
        // A library script that was edited or comes from an older Kolahal: show how it differs, and offer this version's copy.
        if (library != null && library.differs()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CompareButton(ButtonDefaults.ExtraSmallContainerHeight, onCompare)
                LibraryCopyButton(library, ButtonDefaults.ExtraSmallContainerHeight, onReset)
            }
        }
    }
}

/**
 * A button that goes back to this version's library copy. It is [height] tall (as small as a pill
 * in a card).
 * - Edited copy: Reset. The icon uses the error colour because the user's edits are lost. It asks first.
 * - Older copy: Update. Nothing of the user's is lost.
 */
@Composable
internal fun LibraryCopyButton(state: ScriptLibrary.State, height: Dp, onClick: () -> Unit) {
    val edited = state == ScriptLibrary.State.MODIFIED
    OutlinedAction(
        if (edited) "Reset to library version" else "Update from library",
        R.drawable.ic_refresh,
        height,
        iconTint = if (edited) MaterialTheme.colorScheme.error else null,
        onClick = onClick,
    )
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
private fun AccessPill(label: String, on: Boolean, onToggle: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = on,
        onClick = onToggle,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            labelColor = colors.outline,
            selectedContainerColor = colors.primary,
            selectedLabelColor = colors.onPrimary,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = on, borderColor = colors.outlineVariant),
        modifier = Modifier.semantics { stateDescription = if (on) "on" else "off" },
    )
}

/** A row of pills in a card under a small [label]: what a script may use, or the events it runs on. */
@Composable
private fun PillGroup(label: String, pills: @Composable FlowRowScope.() -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp), content = pills)
    }
}
