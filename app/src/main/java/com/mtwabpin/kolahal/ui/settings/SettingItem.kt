/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.settings

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.CustomThemes
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.SettingTitles
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.ConnectedChoices
import com.mtwabpin.kolahal.ui.HOLD_TO_TYPE
import com.mtwabpin.kolahal.ui.LabeledIconButton
import com.mtwabpin.kolahal.ui.TypeValueDialog
import com.mtwabpin.kolahal.ui.TypedNumber
import com.mtwabpin.kolahal.ui.fieldColors
import com.mtwabpin.kolahal.ui.rememberHoldToType
import com.mtwabpin.kolahal.ui.theme.Palettes
import com.mtwabpin.kolahal.ui.theme.segColors
import com.mtwabpin.kolahal.ui.theme.segShapes
import com.mtwabpin.kolahal.ui.typeValueAction
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Shows `text` in the page's snackbar with an Undo action, which runs `undo`. Each page that lists
 * settings passes its own ([undoMessage]).
 */
internal typealias UndoMessage = (text: String, undo: () -> Unit) -> Unit

/** An [UndoMessage] on [snackbar]: Undo stays for a long snackbar's time. */
internal fun undoMessage(
    scope: kotlinx.coroutines.CoroutineScope,
    snackbar: androidx.compose.material3.SnackbarHostState
): UndoMessage = { text, undo ->
    scope.launch {
        snackbar.currentSnackbarData?.dismiss()
        val r = snackbar.showSnackbar(
            text,
            actionLabel = "Undo",
            duration = androidx.compose.material3.SnackbarDuration.Long
        )
        if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) undo()
    }
}

/**
 * One setting's row in Settings (and in the Feeds tab's settings).
 * - Reset to default, once the value is changed: a 20 dp icon with a 48 dp touch area. It shows
 *   "Reset “<name>”" with Undo through [onUndoMessage], which puts the old value back.
 * - A text setting applies its typed value when the field loses focus or the row leaves the screen
 *   (Settings closed, the row scrolled off), as Done and the check mark do. A value that isn't valid
 *   stays marked red and is not applied.
 * - A number setting's title or value, held, opens a box to type the number ([TypeValueDialog]); a tap on
 *   them says so through [onHint] (a toast without one). A screen reader has the row's "Type a value".
 */
@Composable
internal fun SettingItem(
    vm: TerminalViewModel,
    key: ConfigKey<*>,
    version: State<Int>,
    index: Int,
    count: Int,
    onUndoMessage: UndoMessage? = null,
    onHint: ((String) -> Unit)? = null,
) {
    val config = vm.config
    // Only this row recomposes when its own value changes.
    val value by remember(key) { derivedStateOf { version.value; config.display(key) } }
    val changed by remember(key) { derivedStateOf { version.value; !config.isDefault(key) } }
    val shapes = segShapes(index, count)
    val title = SettingTitles[key.key] ?: key.key
    // The setting's name, then a reset icon right after it once the value is changed.
    // - A switch has no reset icon: the user resets it by switching it.
    // - The icon is in the same place on every row, whatever is on the right of the title.
    // - An icon, not a Reset button, keeps this line short, so it never narrows the controls below.
    val supporting: @Composable () -> Unit = {
        Column {
            Text(key.help.replaceFirstChar { it.uppercase() })
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                Text(
                    key.key,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.outline,
                )
                if (changed && key !is ConfigKey.Bool) {
                    Spacer(Modifier.width(4.dp))
                    // The icon takes 28 dp in the line, as before, but its touch area is the 48 dp
                    // minimum ([touchArea]); the line keeps its height.
                    LabeledIconButton(
                        R.drawable.ic_restart,
                        "Reset to default",
                        onClick = {
                            val before = value
                            vm.resetConfig(key)
                            onUndoMessage?.invoke("Reset “$title”") { vm.setConfig(key, before) }
                        },
                        modifier = Modifier
                            .touchArea(28.dp)
                            .testTag("reset ${key.key}"),
                        tint = MaterialTheme.colorScheme.primary,
                        iconModifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }

    Box(Modifier.padding(bottom = ListItemDefaults.SegmentedGap)) {
        when (key) {
            is ConfigKey.Bool -> SegmentedListItem(
                colors = segColors(),
                checked = value == "on",
                onCheckedChange = { vm.setConfig(key, if (it) "on" else "off") },
                shapes = shapes,
                supportingContent = supporting,
                trailingContent = { Switch(checked = value == "on", onCheckedChange = null) },
            ) { Text(title) }

            is ConfigKey.Choice -> SegmentedListItem(
                colors = segColors(),
                shapes = shapes,
                supportingContent = {
                    Column {
                        supporting()
                        Spacer(Modifier.height(10.dp))
                        if (key == Keys.THEME) {
                            // Draw each card in the variant in use now: dark or light, as the phone (or
                            // Always dark) sets it. It is read with the version, so toggling Always dark
                            // redraws the cards.
                            val dark =
                                androidx.compose.foundation.isSystemInDarkTheme() || run { version.value; config[Keys.THEME_ALWAYS_DARK] }
                            ThemeSwatches(value, dark) { vm.setConfig(key, it) }
                        } else if (key == Keys.FONT) {
                            FontChoices(value, { vm.setConfig(key, it) }, vm::requestFontImport)
                        } else {
                            // The values this home style offers, and the one in effect: a screensaver the gui style
                            // doesn't run shows as off there. Read with the version, so a change of style redraws them.
                            val choices = run { version.value; shownChoices(key, config) }
                            val chosen = run { version.value; shownChoice(key, value, config) }
                            if (choices.size > 4) {
                                ChipChoices(
                                    choices,
                                    chosen,
                                    label = { ChoiceLabels.of(key, it) }) { vm.setConfig(key, it) }
                            } else {
                                ConnectedChoices(
                                    choices,
                                    chosen,
                                    label = { ChoiceLabels.of(key, it) }) { vm.setConfig(key, it) }
                            }
                        }
                    }
                },
            ) { Text(title) }

            is ConfigKey.Num -> {
                var current by remember(key, value) { mutableFloatStateOf(value.toFloat()) }
                val slider = remember(key) {
                    // Show ticks only for short ranges. Wide ranges (dp sizes, days) slide smoothly.
                    val steps =
                        (key.range.last - key.range.first - 1).let { if (it in 1..11) it else 0 }
                    SliderState(current, steps, key.range.first.toFloat()..key.range.last.toFloat())
                }
                slider.value = current
                // A hold on the title or the value opens a box to type the number. The slider keeps its
                // own hold: it moves to the finger.
                var typing by remember(key) { mutableStateOf(false) }
                val context = LocalContext.current
                val hold = rememberHoldToType(
                    onHold = { typing = true },
                    onTap = {
                        if (onHint != null) onHint(HOLD_TO_TYPE)
                        else Toast.makeText(context, HOLD_TO_TYPE, Toast.LENGTH_SHORT).show()
                    },
                )
                if (typing) TypeValueDialog(
                    title = title,
                    value = current.roundToInt().toString(),
                    range = TypedNumber.range(key.range),
                    decimals = false,
                    read = { TypedNumber.whole(it, key.range) },
                    onDismiss = { typing = false },
                    onApply = { n ->
                        typing = false
                        current = n.toFloat()
                        // As when the slider is let go.
                        vm.setConfig(key, n.toString())
                        config.preview(key, null)
                    },
                )
                SegmentedListItem(
                    colors = segColors(),
                    shapes = shapes,
                    modifier = Modifier.typeValueAction { typing = true },
                    trailingContent = {
                        Text(
                            ChoiceLabels.of(key, current.roundToInt()),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = hold,
                        )
                    },
                    supportingContent = {
                        Column {
                            supporting()
                            // A look setting (PEEK_SLIDERS). While the user drags, home shows through
                            // Settings with the value under the finger. The value is saved on release.
                            val peekTyping = PEEK_SLIDERS[key.key]
                            val interactions = remember { MutableInteractionSource() }
                            val dragging by interactions.collectIsDraggedAsState()
                            if (peekTyping != null) LaunchedEffect(dragging) {
                                if (dragging) vm.peek(TerminalViewModel.Peek(key.key, peekTyping))
                                else if (vm.peek.value?.key == key.key) vm.peek(null)
                            }
                            // Leaving during a drag (Home, another screen) never finishes the change, so
                            // drop the preview.
                            DisposableEffect(key) { onDispose { config.preview(key, null) } }
                            Slider(
                                state = slider,
                                onValueChange = {
                                    current = it
                                    if (peekTyping != null) config.preview(
                                        key,
                                        current.roundToInt().toString()
                                    )
                                },
                                onValueChangeFinished = {
                                    vm.setConfig(key, current.roundToInt().toString())
                                    config.preview(key, null)
                                },
                                interactionSource = interactions,
                                modifier = Modifier.testTag("slider ${key.key}"),
                            )
                        }
                    },
                ) {
                    // The whole width beside the value, so the hold is easy to find.
                    Text(
                        title,
                        Modifier
                            .fillMaxWidth()
                            .then(hold),
                    )
                }
            }

            is ConfigKey.Text -> {
                var text by remember(key, value) { mutableStateOf(value) }
                // A valid typed value is applied when the field loses focus, and when the row leaves
                // the screen (scrolled off, Settings closed), so typing is never lost without a word.
                val applyTyped = rememberUpdatedState {
                    if (text != value && key.parse(text) != null) vm.setConfig(key, text)
                }
                DisposableEffect(key) { onDispose { applyTyped.value() } }
                SegmentedListItem(
                    colors = segColors(),
                    shapes = shapes,
                    supportingContent = {
                        Column {
                            supporting()
                            Spacer(Modifier.height(8.dp))
                            // Red while the text is not a valid value (for example, a name with a space).
                            // Apply is hidden then.
                            val valid = key.parse(text) != null
                            TextField(
                                shape = TextFieldDefaults.roundedShape,
                                colors = fieldColors(),
                                value = text,
                                onValueChange = { text = it },
                                singleLine = true,
                                isError = !valid,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                keyboardOptions = KeyboardOptions(
                                    autoCorrectEnabled = false,
                                    capitalization = KeyboardCapitalization.None,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = {
                                    vm.setConfig(
                                        key,
                                        text
                                    )
                                }),
                                trailingIcon = if (text != value && valid) {
                                    {
                                        LabeledIconButton(
                                            R.drawable.ic_check,
                                            "Apply",
                                            onClick = { vm.setConfig(key, text) })
                                    }
                                } else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { if (!it.isFocused) applyTyped.value() }
                                    .testTag("field ${key.key}"),
                            )
                        }
                    },
                ) { Text(title) }
            }
        }
    }
}

/** For many options: single-select filter chips that wrap. [label] gives the words shown for a choice. */
@Composable
fun ChipChoices(
    choices: List<String>,
    selected: String,
    label: (String) -> String = { it },
    onPick: (String) -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { c ->
            androidx.compose.material3.FilterChip(
                selected = c == selected,
                onClick = { onPick(c) },
                label = { Text(label(c)) },
                shapes = FilterChipDefaults.shapes(),
                colors = FilterChipDefaults.filterChipColors(),
            )
        }
    }
}

/** The theme picker: one preview card per palette (in Settings and in the tour). */
@Composable
internal fun ThemeSwatches(selected: String, dark: Boolean, onPick: (String) -> Unit) {
    // Not lazy, for the reason given in FontChoices.
    // The built-in themes first, then the ones made in the theme builder (these can be added and removed).
    val own = CustomThemes.themes.collectAsStateWithLifecycle().value.keys.sorted()
    val scroll = rememberScrollState()
    val step = with(LocalDensity.current) { (112 + 10).dp.roundToPx() }
    // Open at the theme in use, not at the start of the row. Half of the card before it shows.
    LaunchedEffect(Unit) {
        val at = (Keys.BUILT_IN_THEMES + own).indexOf(selected)
        if (at > 0) scroll.scrollTo(at * step - step / 2)
    }
    // Every card is as tall as the tallest one, because a card can have a note under its name.
    Row(
        Modifier
            .horizontalScroll(scroll)
            .height(IntrinsicSize.Max),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        (Keys.BUILT_IN_THEMES + own).forEach { name ->
            val p = Palettes.named(name, dark)
            val on = name == selected
            Card(
                onClick = { onPick(name) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = p.bg),
                border = if (on) BorderStroke(
                    3.dp,
                    MaterialTheme.colorScheme.primary
                ) else BorderStroke(1.dp, p.surface),
                modifier = Modifier
                    .width(112.dp)
                    .fillMaxHeight(),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(p.prompt, p.work, p.link, p.private).forEach {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .background(it, CircleShape)
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "$ ls",
                        color = p.prompt,
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
                    )
                    Text(
                        ChoiceLabels.theme(name),
                        color = p.fg,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    ChoiceLabels.themeNote(name)?.let {
                        Text(
                            it,
                            color = p.dim,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

/**
 * Takes [shown] (square) in the layout, but measures what follows at the 48 dp minimum touch size,
 * centered over it. A small icon button in a line of text then keeps the line's height and still has
 * a 48 dp touch area. The 10 dp or so outside [shown] can overlap what is beside it.
 */
internal fun Modifier.touchArea(shown: androidx.compose.ui.unit.Dp): Modifier =
    layout { measurable, _ ->
        val touch = 48.dp.roundToPx()
        val size = shown.roundToPx()
        val placeable = measurable.measure(androidx.compose.ui.unit.Constraints.fixed(touch, touch))
        layout(size, size) { placeable.place((size - touch) / 2, (size - touch) / 2) }
    }
