package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.data.SettingTitles
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.CustomThemes
import com.mtwabpin.kolahal.data.ConfigKey
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlin.math.roundToInt

@Composable
internal fun SettingItem(
    vm: TerminalViewModel,
    key: ConfigKey<*>,
    version: State<Int>,
    index: Int,
    count: Int,
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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text(
                    key.key,
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.outline,
                )
                if (changed && key !is ConfigKey.Bool) {
                    Spacer(Modifier.width(4.dp))
                    IconButton(shapes = IconButtonDefaults.shapes(), onClick = { vm.resetConfig(key) }, modifier = Modifier.size(28.dp)) {
                        Icon(
                            painterResource(R.drawable.ic_restart),
                            contentDescription = "Reset to default",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
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
                            val dark = androidx.compose.foundation.isSystemInDarkTheme() || run { version.value; config[Keys.THEME_ALWAYS_DARK] }
                            ThemeSwatches(value, dark) { vm.setConfig(key, it) }
                        } else if (key == Keys.FONT) {
                            FontChoices(value, { vm.setConfig(key, it) }, vm::requestFontImport)
                        } else if (key.choices.size > 4) {
                            ChipChoices(key.choices, value, label = { ChoiceLabels.of(key, it) }) { vm.setConfig(key, it) }
                        } else {
                            ConnectedChoices(key.choices, value, label = { ChoiceLabels.of(key, it) }) { vm.setConfig(key, it) }
                        }
                    }
                },
            ) { Text(title) }

            is ConfigKey.Num -> {
                var current by remember(key, value) { mutableFloatStateOf(value.toFloat()) }
                val slider = remember(key) {
                    // Show ticks only for short ranges. Wide ranges (dp sizes, days) slide smoothly.
                    val steps = (key.range.last - key.range.first - 1).let { if (it in 1..11) it else 0 }
                    SliderState(current, steps, key.range.first.toFloat()..key.range.last.toFloat())
                }
                slider.value = current
                SegmentedListItem(
                    colors = segColors(),
                    shapes = shapes,
                    trailingContent = {
                        Text(current.roundToInt().toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
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
                                    if (peekTyping != null) config.preview(key, current.roundToInt().toString())
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
                ) { Text(title) }
            }

            is ConfigKey.Text -> {
                var text by remember(key, value) { mutableStateOf(value) }
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
                                keyboardActions = KeyboardActions(onDone = { vm.setConfig(key, text) }),
                                trailingIcon = if (text != value && valid) {
                                    { IconButton(shapes = IconButtonDefaults.shapes(), onClick = { vm.setConfig(key, text) }) { Icon(painterResource(R.drawable.ic_check), "Apply") } }
                                } else null,
                                modifier = Modifier.fillMaxWidth(),
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
fun ChipChoices(choices: List<String>, selected: String, label: (String) -> String = { it }, onPick: (String) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        choices.forEach { c ->
            androidx.compose.material3.FilterChip(selected = c == selected, onClick = { onPick(c) }, label = { Text(label(c)) })
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
    Row(Modifier.horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        (Keys.BUILT_IN_THEMES + own).forEach { name ->
            val p = Palettes.named(name, dark)
            val on = name == selected
            Card(
                onClick = { onPick(name) },
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = p.bg),
                border = if (on) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, p.surface),
                modifier = Modifier.width(112.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(p.prompt, p.work, p.link, p.private).forEach {
                            Box(Modifier.size(14.dp).background(it, CircleShape))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("$ ls", color = p.prompt, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace))
                    Text(ChoiceLabels.theme(name), color = p.fg, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
