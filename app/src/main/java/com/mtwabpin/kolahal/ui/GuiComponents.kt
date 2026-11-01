package com.mtwabpin.kolahal.ui

import android.text.format.DateUtils
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.terminal.CommandParsing

/** Single-select connected button group (Material 3 Expressive). */
@Composable
fun ConnectedChoices(choices: List<String>, selected: String, tagPrefix: String = "choice ", onPick: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        choices.forEachIndexed { i, choice ->
            ToggleButton(
                checked = choice == selected,
                onCheckedChange = { onPick(choice) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                modifier = Modifier.weight(1f).testTag(tagPrefix + choice),
            ) {
                Text(choice, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

data class Action(val icon: Int, val title: String, val subtitle: String, val chevron: Boolean = false, val onClick: () -> Unit)

/** A segmented group of clickable rows. */
@Composable
fun ActionGroup(actions: List<Action>) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        actions.forEachIndexed { i, a ->
            SegmentedListItem(
                colors = segColors(),
                onClick = a.onClick,
                shapes = segShapes(i, actions.size),
                leadingContent = { Icon(painterResource(a.icon), null, tint = MaterialTheme.colorScheme.primary) },
                supportingContent = { Text(a.subtitle) },
                trailingContent = if (a.chevron) {
                    { Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                } else null,
            ) { Text(a.title) }
        }
    }
}

@Composable
fun GroupHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun BackButton(onClick: () -> Unit) {
    IconButton(shapes = IconButtonDefaults.shapes(), onClick = onClick) { Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = "Back") }
}

/** "5 minutes ago"; [short] gives "5 min. ago" for one-line rows. */
internal fun relative(time: Long, short: Boolean = false): String =
    if (System.currentTimeMillis() - time < DateUtils.MINUTE_IN_MILLIS) "just now" else relativeSpan(time, short)

private fun relativeSpan(time: Long, short: Boolean = false): String =
    DateUtils.getRelativeTimeSpanString(
        time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS,
        // The three-argument form's flags, plus the short form when asked.
        DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_ABBREV_MONTH or
            (if (short) DateUtils.FORMAT_ABBREV_RELATIVE else 0),
    ).toString()

/** Full-width row (≥ 56dp) whose whole surface toggles the switch; [color] is what it sits on. */
@Composable
internal fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        onClick = { onChange(!checked) },
        shape = MaterialTheme.shapes.large,
        color = color,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(16.dp))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}

/**
 * Material's Expressive text field colours, on the container of the tonal buttons beside it: its own
 * (`surfaceContainer`) hardly shows on a sheet in these themes.
 */
@Composable
internal fun fieldColors() = TextFieldDefaults.tonalColors().copy(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
)

/** How often something runs: a row like [SwitchRow], whose button says the time and opens a menu of the presets. A time set from the terminal shows as it is. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun IntervalSelector(label: String, selected: Int, onPick: (Int) -> Unit, choices: List<Pair<Int, String>>) {
    var open by remember { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec())
    Surface(
        onClick = { open = !open },
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(16.dp))
            Box {
                FilledTonalToggleButton(checked = open, onCheckedChange = { open = it }) {
                    Text(choices.firstOrNull { it.first == selected }?.second ?: CommandParsing.formatInterval(selected))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.size(ButtonDefaults.IconSize).rotate(turn))
                }
                DropdownMenuPopup(expanded = open, onDismissRequest = { open = false }) {
                    DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                        // The theme's accent, as switches and tabs have: Material's default is tertiaryContainer, here the work profile's colour.
                        val colors = MenuDefaults.selectableItemColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                        )
                        choices.forEachIndexed { i, (minutes, name) ->
                            SelectableDropdownMenuItem(
                                selected = minutes == selected,
                                onClick = {
                                    open = false
                                    if (minutes != selected) onPick(minutes)
                                },
                                text = { Text(name) },
                                shapes = MenuDefaults.itemShape(i, choices.size),
                                colors = colors,
                            )
                        }
                    }
                }
            }
        }
    }
}
