/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import android.os.SystemClock
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.ui.Labeled
import com.mtwabpin.kolahal.ui.LabeledIconButton
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSize
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/** A button of the toolbar for what the selected widget itself holds: its [label] and [icon], and what a tap does. */
internal class ToolbarEdit(val label: String, val icon: Int, val onClick: () -> Unit)

/**
 * The controls for the chosen widget, docked at the bottom: its name and size, and Remove and Done.
 * - One of Kolahal's own widgets: its look and its size, one tap each. With [edit], a button for
 *   what the widget itself holds: the Shortcuts widget's list, or the options of a script's widget.
 * - An app's widget: width and height in steps, and Square. It draws itself, so it has no look.
 */
@Composable
internal fun WidgetToolbar(
    w: PlacedWidget,
    label: String,
    size: WidgetSize?,
    kind: WidgetKind?,
    minHeight: Int,
    minSpan: Int,
    onLook: (WidgetLook) -> Unit,
    onSize: (WidgetSize) -> Unit,
    onHeight: (Int) -> Unit,
    onWidth: (Int) -> Unit,
    onSquare: () -> Unit,
    edit: ToolbarEdit?,
    onRemove: () -> Unit,
    onDone: () -> Unit,
    agendaDays: String? = null,
    onAgendaDays: (String) -> Unit = {},
) {
    val own = w.source != null
    // Only the looks that change this kind of card ([WidgetLook.offeredFor]); one it doesn't offer draws Plain.
    val looks = WidgetLook.offeredFor(kind)
    val drawn = w.look.drawnFor(kind)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val shape = size?.label ?: WidgetLayout.sizeLine(w.span, w.heightDp)
                    Text(
                        if (own) "$shape · ${drawn.label}" else shape,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                if (edit != null) {
                    Labeled(edit.label) {
                        FilledTonalIconButton(
                            shapes = IconButtonDefaults.shapes(),
                            onClick = edit.onClick
                        ) { Icon(painterResource(edit.icon), edit.label) }
                    }
                }
                LabeledIconButton(
                    R.drawable.ic_delete,
                    "Remove",
                    onClick = onRemove,
                    tint = MaterialTheme.colorScheme.error
                )
                Labeled("Done") {
                    FilledIconButton(shapes = IconButtonDefaults.shapes(), onClick = onDone) {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            "Done"
                        )
                    }
                }
            }
            if (own) {
                ToolbarLabel("Look")
                ChoiceRow(looks.map { it.label }, drawn.label, "look ") { onLook(looks[it]) }
                ToolbarLabel("Size")
                ChoiceRow(WidgetSize.entries.map { it.label }, size?.label, "size ") {
                    onSize(
                        WidgetSize.entries[it]
                    )
                }
                if (agendaDays != null) {
                    ToolbarLabel("Days")
                    val choices = Keys.AGENDA_WIDGET_DAYS.choices
                    val labels = choices.map { it.replaceFirstChar(Char::titlecase) }
                    ChoiceRow(
                        labels,
                        labels.getOrNull(choices.indexOf(agendaDays)),
                        "days "
                    ) { onAgendaDays(choices[it]) }
                }
                Text(
                    "Drag to move. Drag an edge to resize.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            } else {
                // Side by side if they fit. On a narrow screen or with large text, one below the other.
                FlowRow(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Stepper(
                        "Width",
                        WidgetLayout.spanLabel(w.span),
                        w.span > minSpan,
                        w.span < WidgetLayout.COLUMNS,
                        onWidth
                    )
                    Stepper(
                        "Height",
                        "${w.heightDp}",
                        w.heightDp > minHeight,
                        w.heightDp < WidgetLayout.MAX_DP,
                        onHeight
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        "Looks are for Kolahal's own widgets.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    FilledTonalButton(
                        shapes = ButtonDefaults.shapes(),
                        onClick = onSquare
                    ) { Text("Square") }
                }
            }
        }
    }
}

/** The name of a row of the toolbar: "Look", "Size". */
@Composable
private fun ToolbarLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

/**
 * [choices] as one row of connected buttons, [selected] checked (none when it is not among them).
 * A tap picks one ([onPick] with its index). Each is tagged [tagPrefix] and its text.
 */
@Composable
private fun ChoiceRow(
    choices: List<String>,
    selected: String?,
    tagPrefix: String,
    onPick: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth()
    ) {
        choices.forEachIndexed { i, choice ->
            ToggleButton(
                checked = choice == selected,
                onCheckedChange = { onPick(i) },
                shapes = when (i) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    choices.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton }
                    .testTag(tagPrefix + choice),
            ) {
                Text(choice, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Shows "label − value +". Each button steps once on a tap and keeps stepping while held. */
@Composable
private fun Stepper(
    label: String,
    value: String,
    canLess: Boolean,
    canMore: Boolean,
    onStep: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp)
        )
        RepeatingButton(
            R.drawable.ic_remove,
            "Decrease ${label.lowercase()}",
            canLess
        ) { onStep(-1) }
        Text(
            value,
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(min = 36.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        )
        RepeatingButton(R.drawable.ic_add, "Increase ${label.lowercase()}", canMore) { onStep(1) }
    }
}

@Composable
private fun RepeatingButton(icon: Int, description: String, enabled: Boolean, onStep: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val step by rememberUpdatedState(onStep)
    // When held past the long-press delay, the button repeats. The release that ends such a hold
    // does not step again.
    // [heldSince] is reset each time a press ends. So a hold that slid off the button or reached the
    // limit doesn't block the next activation. (A TalkBack activation has no press at all.)
    var heldSince by remember { mutableLongStateOf(0L) }
    LaunchedEffect(pressed, enabled) {
        if (!pressed || !enabled) {
            heldSince = 0L
            return@LaunchedEffect
        }
        heldSince = SystemClock.uptimeMillis()
        delay(400.milliseconds)
        while (true) {
            step()
            delay(70.milliseconds)
        }
    }
    IconButton(
        shapes = IconButtonDefaults.shapes(),
        onClick = {
            if (heldSince == 0L || SystemClock.uptimeMillis() - heldSince < 400) step()
            heldSince = 0L
        },
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Icon(painterResource(icon), description)
    }
}
