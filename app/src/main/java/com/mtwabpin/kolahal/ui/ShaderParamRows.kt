/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.script.ShaderParam
import com.mtwabpin.kolahal.script.ShaderParams
import com.mtwabpin.kolahal.script.ShaderValues
import com.mtwabpin.kolahal.terminal.ShaderActions

/**
 * A shader's parameters: a slider for a number, connected buttons for words. What is on screen with the
 * shader follows them while a finger drags, and a value is kept when it lifts. Each can follow a value
 * instead ([ShaderValues]): its slider then only shows it. Reset shows once something differs from what the
 * shader comes with.
 */
@Composable
internal fun ShaderParamRows(shader: String, params: List<ShaderParam>, actions: ShaderActions, modifier: Modifier = Modifier) {
    val all by ShaderParams.values.collectAsStateWithLifecycle()
    val set = all[shader].orEmpty()
    val follows by ShaderParams.follows.collectAsStateWithLifecycle()
    val given by ShaderValues.given.collectAsStateWithLifecycle()
    val formulas by ShaderParams.formulas.collectAsStateWithLifecycle()
    val phone = remember(given, follows, formulas) { actions.phone() }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        params.forEachIndexed { i, param ->
            val follow = ShaderParams.follow(follows[shader].orEmpty(), param)
            val formula = remember(formulas, follows) { ShaderParams.formula(shader, param) }
            val followed = follow?.let { ShaderParams.followed(param, it, formula, phone) }
            val value = followed ?: ShaderParams.of(set, param)
            SegmentedListItem(
                shapes = segShapes(i, params.size),
                colors = segColors(),
                trailingContent = if (param.choices.isNotEmpty()) null else {
                    {
                        Text(
                            param.text(value),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                supportingContent = {
                    Column {
                        if (param.choices.isEmpty()) ParamSlider(shader, param, value, followed != null, actions)
                        else {
                            Spacer(Modifier.height(8.dp))
                            ConnectedChoices(
                                param.choices, param.text(value), tagPrefix = "param ${param.name} ",
                                label = { it.replaceFirstChar(Char::uppercase) },
                            ) { word -> if (followed == null) param.parse(word)?.let { actions.setParam(shader, param, it) } }
                            Spacer(Modifier.height(4.dp))
                        }
                        FollowButton(param, follow, hasValue = followed != null, actions) { actions.follow(shader, param, it) }
                        if (follow != null && ShaderValues.of(follow, given, phone)?.toFloatOrNull() != null) {
                            FormulaField(param, follow, formula, phone) { actions.follow(shader, param, follow, it) }
                        }
                    }
                },
            ) { Text(param.title) }
        }
        if (params.any { ShaderParams.of(set, it) != it.default || ShaderParams.follow(follows[shader].orEmpty(), it) != it.follow }) TextButton(
            onClick = { actions.resetParams(shader) },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.align(Alignment.End),
        ) { Text("Reset") }
    }
}

@Composable
private fun ParamSlider(shader: String, param: ShaderParam, value: Float, follows: Boolean, actions: ShaderActions) {
    val state = remember(shader, param.name, param.range) {
        // Stops only for a short row of whole numbers.
        val stops = if (param.whole) (param.max - param.min).toInt() - 1 else 0
        SliderState(value, if (stops in 1..11) stops else 0, param.min..param.max)
    }
    state.value = value
    // The sheet can go in the middle of a drag: what is shown then is kept.
    var dragged by remember(shader, param.name) { mutableStateOf<Float?>(null) }
    val keep by rememberUpdatedState {
        dragged?.let { actions.setParam(shader, param, it) }
        dragged = null
    }
    DisposableEffect(shader, param.name) { onDispose { keep() } }
    Slider(
        state = state,
        onValueChange = {
            dragged = it
            ShaderParams.tryOut(shader, param, it)
        },
        onValueChangeFinished = { keep() },
        enabled = !follows,
        modifier = Modifier
            .testTag("param ${param.name}")
            .semantics { contentDescription = param.title },
    )
}

/**
 * What a parameter follows, and the menu to pick it: Kolahal's values and those that scripts give. The value
 * it follows may have nothing yet: then the slider counts.
 */
@Composable
private fun FollowButton(param: ShaderParam, follow: String?, hasValue: Boolean, actions: ShaderActions, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val sources by produceState(emptyList<ShaderValues.Source>(), open) { if (open) value = actions.sources() }
    Box {
        FilledTonalToggleButton(
            checked = follow != null,
            onCheckedChange = { open = true },
            shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.ExtraSmallContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.ExtraSmallContainerHeight),
            modifier = Modifier
                .heightIn(ButtonDefaults.ExtraSmallContainerHeight)
                .testTag("follow ${param.name}"),
        ) {
            Icon(painterResource(R.drawable.wi_link), null, Modifier.size(ButtonDefaults.ExtraSmallIconSize))
            Spacer(Modifier.width(ButtonDefaults.ExtraSmallIconSpacing))
            Text(
                if (follow == null) "Follow" else "Follows ${ShaderValues.label(follow)}" + if (hasValue) "" else " (no value yet)",
                style = ButtonDefaults.textStyleFor(ButtonDefaults.ExtraSmallContainerHeight),
            )
        }
        DropdownMenuPopup(expanded = open && sources.isNotEmpty(), onDismissRequest = { open = false }) {
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, 1)) {
                // The theme's accent, as in IntervalSelector.
                val colors = MenuDefaults.selectableItemColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.onPrimary,
                )
                val rows = listOf(null to "Nothing") + sources.map { s ->
                    s.id to s.label + s.value?.let { " · ${ShaderValues.shown(s.id, it)}" }.orEmpty()
                }
                rows.forEachIndexed { i, (id, label) ->
                    SelectableDropdownMenuItem(
                        selected = id == follow,
                        onClick = {
                            open = false
                            onPick(id)
                        },
                        text = { Text(label) },
                        shapes = MenuDefaults.itemShape(i, rows.size),
                        colors = colors,
                    )
                }
            }
        }
    }
}

/**
 * The formula for the value that a parameter follows, such as `battery / 20`; without one the value goes over
 * the parameter's whole range. It counts as soon as it can be worked out. Under it is what it makes now.
 */
@Composable
private fun FormulaField(param: ShaderParam, follow: String, formula: String?, phone: ShaderValues.Phone, onFormula: (String?) -> Unit) {
    var text by remember(param.name, follow) { mutableStateOf(formula.orEmpty()) }
    val name = follow.substringAfter('.')
    val now = ShaderParams.followed(param, follow, text.trim().ifEmpty { null }, phone)
    val value = ShaderValues.of(follow, ShaderValues.given.value, phone)
    TextField(
        value = text,
        onValueChange = {
            text = it.take(ShaderParams.MAX_FORMULA)
            val typed = text.trim().ifEmpty { null }
            if (typed == null || ShaderParams.followed(param, follow, typed, phone) != null) onFormula(typed)
        },
        label = { Text("Formula") },
        placeholder = { Text("$name / 2") },
        isError = now == null,
        supportingText = {
            val at = value?.let { "$name is ${ShaderValues.shown(follow, it)}" }.orEmpty()
            Text(
                when {
                    now == null -> "Can't be worked out: use $name with + - * / and ( )"
                    text.isBlank() -> "$at, which makes ${param.text(now)}. Or use a formula, such as $name / 2"
                    else -> "$at, which makes ${param.text(now)}"
                }
            )
        },
        singleLine = true,
        shape = TextFieldDefaults.roundedShape,
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .testTag("formula ${param.name}"),
    )
}
