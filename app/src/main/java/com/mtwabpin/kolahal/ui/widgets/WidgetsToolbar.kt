/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.widgets

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.StyledText.ColorRole
import com.mtwabpin.kolahal.ui.Labeled
import com.mtwabpin.kolahal.ui.LabeledIconButton
import com.mtwabpin.kolahal.ui.home.colorOf
import com.mtwabpin.kolahal.widgets.PlacedWidget
import com.mtwabpin.kolahal.widgets.WidgetKind
import com.mtwabpin.kolahal.widgets.WidgetLayout
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetShape
import com.mtwabpin.kolahal.widgets.WidgetSize
import com.mtwabpin.kolahal.widgets.WidgetSource
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay

/** A button of the toolbar for what the selected widget itself holds: its [label] and [icon], and what a tap does. */
internal class ToolbarEdit(val label: String, val icon: Int, val onClick: () -> Unit)

/**
 * The controls for the chosen widget, docked at the bottom: its name and size, and Remove and Done.
 * - One of Kolahal's own widgets: its look and its size, one tap each. Look has its name over it,
 *   as its buttons take the whole width; Size has it at its start, which keeps the toolbar low.
 *   The Agenda's days are on its settings page. [edit] is Settings: a button that opens the card's settings
 *   page, with a dot in the card's color ([settingsColor]) and, in the Shape look, a small copy of
 *   its shape ([settingsShape]) in front of the word.
 * - An app's widget: width and height in steps, and Square. It draws itself, so it has no look.
 *   With [edit], Widget settings: its setup screen, opened again.
 * - With more than one widgets page, either kind has a Page row: a tap moves it to that page.
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
    settingsColor: ColorRole? = null,
    settingsShape: WidgetShape? = null,
    onSize: (WidgetSize) -> Unit,
    onHeight: (Int) -> Unit,
    onWidth: (Int) -> Unit,
    onSquare: () -> Unit,
    edit: ToolbarEdit?,
    onRemove: () -> Unit,
    onDone: () -> Unit,
    pageNames: List<String> = emptyList(),
    page: Int = 0,
    onPage: (Int) -> Unit = {},
) {
    val own = w.source != null
    // Only the looks that change this kind of card ([WidgetLook.offeredFor]); one it doesn't offer draws Plain.
    // One of Kolahal's own widgets has a look of its own too: Next for Todos, Stack for Notes.
    val builtin = WidgetSource.builtinId(w.source)
    val looks = WidgetLook.offeredFor(kind, builtin)
    val drawn = w.look.drawnFor(kind, builtin)
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
                        style = MaterialTheme.typography.titleSmallEmphasized,
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
                if (edit != null && own) {
                    SettingsButton(edit, settingsColor, settingsShape)
                } else if (edit != null) {
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
                NamedRow("Size") {
                    ChoiceRow(WidgetSize.entries.map { it.label }, size?.label, "size ") {
                        onSize(
                            WidgetSize.entries[it]
                        )
                    }
                }
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
            // With more than one widgets page ([pageNames]): the one it is on ([page]), one tap to move it.
            if (pageNames.size > 1) {
                NamedRow("Page") {
                    ChoiceRow(pageNames, pageNames.getOrNull(page), "page ") { onPage(it) }
                }
            }
            // Under the rows of buttons, so it splits none of them.
            if (own) {
                Text(
                    "Drag to move. Drag an edge to resize.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * A row of the toolbar with its [name] at its start and its buttons ([content]) in the rest of the
 * width: one line where the name over the buttons would take a line of its own. The name is at
 * least 48 dp wide, so the buttons of such rows line up.
 */
@Composable
private fun NamedRow(name: String, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
    ) {
        Text(
            name,
            style = MaterialTheme.typography.labelMediumEmphasized,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier
                .widthIn(min = 48.dp)
                .padding(end = 8.dp),
        )
        Box(Modifier.weight(1f)) { content() }
    }
}

/**
 * The button of one of Kolahal's own cards that opens its settings page ([edit]): "Settings",
 * after a dot in the card's [color] (null: the accent) and, when the card is in the Shape look with
 * a choice of shape, a small copy of its [shape]. So it shows what it holds.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsButton(edit: ToolbarEdit, color: ColorRole?, shape: WidgetShape?) {
    FilledTonalButton(
        shapes = ButtonDefaults.shapes(),
        onClick = edit.onClick,
        contentPadding = PaddingValues(start = 12.dp, end = 16.dp),
        modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("widget-settings"),
    ) {
        ColorDot(colorOf(color ?: ColorRole.ACCENT), MaterialTheme.colorScheme.secondaryContainer, 18.dp)
        if (shape != null) {
            val drawn = polygonOf(shape).toShape()
            Box(
                Modifier
                    .padding(start = 2.dp)
                    .size(16.dp)
                    .background(LocalContentColor.current, drawn)
            )
        }
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(edit.label, maxLines = 1)
    }
}

/** The name of a row of the toolbar over its buttons: "Look". */
@Composable
private fun ToolbarLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMediumEmphasized,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp, bottom = 6.dp),
    )
}

/** The smallest size a choice's word shrinks to, in sp: the page tabs' labels stop there too. */
private const val CHOICE_MIN_SP = 10

/** The room at each side of a choice's word inside its button, in dp. */
private const val CHOICE_PAD_DP = 4

/**
 * How many of [count] choices go in each row, in a space [widthDp] wide, where the widest word at its
 * smallest size needs a button [needDp] wide and [gapDp] is between two buttons.
 * - All in one row when each button then holds that word.
 * - Else the fewest rows whose buttons hold it, with the same count in each: six are three and three,
 *   never five and one. With a count that doesn't divide, the last row has fewer.
 * At least one to a row: a word wider than the space is cut.
 */
internal fun choicesPerRow(count: Int, widthDp: Float, needDp: Float, gapDp: Float): Int {
    if (count <= 1) return 1
    val fit = ((widthDp + gapDp) / (needDp + gapDp)).toInt().coerceIn(1, count)
    val rows = (count + fit - 1) / fit
    return (count + rows - 1) / rows
}

/**
 * [choices] as connected buttons that share the width, [selected] checked (none when it is not among them).
 * A tap picks one ([onPick] with its index). Each is tagged [tagPrefix] and its text.
 * - One row at the usual font size. With a larger font a word that doesn't fit its button shrinks to fit,
 *   as a page tab's does, down to [CHOICE_MIN_SP] sp.
 * - Where the widest word doesn't fit even then, the buttons are in rows of the same count
 *   ([choicesPerRow]), each row connected by itself: every word stays whole, and each choice is one tap.
 */
@Composable
private fun ChoiceRow(
    choices: List<String>,
    selected: String?,
    tagPrefix: String,
    onPick: (Int) -> Unit
) {
    val gap = ButtonGroupDefaults.ConnectedSpaceBetween
    val measurer = rememberTextMeasurer()
    val smallest = MaterialTheme.typography.labelLarge.copy(fontSize = CHOICE_MIN_SP.sp)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The button the widest word needs at its smallest size.
        val need = remember(choices, smallest, density) {
            val widest = choices.maxOfOrNull { measurer.measure(it, smallest, maxLines = 1, softWrap = false).size.width } ?: 0
            with(density) { widest.toDp() } + (2 * CHOICE_PAD_DP).dp
        }
        val perRow = choicesPerRow(choices.size, maxWidth.value, need.value, gap.value)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            choices.indices.chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { i ->
                        val choice = choices[i]
                        ToggleButton(
                            checked = choice == selected,
                            onCheckedChange = { onPick(i) },
                            shapes = when (i) {
                                row.first() -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                row.last() -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            contentPadding = PaddingValues(horizontal = CHOICE_PAD_DP.dp),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .semantics { role = Role.RadioButton }
                                .testTag(tagPrefix + choice),
                        ) {
                            Text(
                                choice,
                                autoSize = TextAutoSize.StepBased(
                                    minFontSize = CHOICE_MIN_SP.sp,
                                    maxFontSize = LocalTextStyle.current.fontSize,
                                    stepSize = 1.sp,
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
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
