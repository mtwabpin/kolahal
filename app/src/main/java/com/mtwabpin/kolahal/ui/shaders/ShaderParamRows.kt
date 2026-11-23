/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.shaders

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.shader.ShaderParam
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.ShaderValues
import com.mtwabpin.kolahal.terminal.ShaderActions
import com.mtwabpin.kolahal.ui.ConnectedChoices
import com.mtwabpin.kolahal.ui.HOLD_TO_TYPE
import com.mtwabpin.kolahal.ui.TypeValueDialog
import com.mtwabpin.kolahal.ui.TypedNumber
import com.mtwabpin.kolahal.ui.fieldColors
import com.mtwabpin.kolahal.ui.rememberHoldToType
import com.mtwabpin.kolahal.ui.theme.segColors
import com.mtwabpin.kolahal.ui.theme.segShapes
import com.mtwabpin.kolahal.ui.typeValueAction

/**
 * A shader's parameters: a slider for a number, connected buttons for words. What is on screen with the
 * shader follows them while a finger drags, and a value is kept when it lifts. Each can follow a value
 * instead ([ShaderValues]): a number's row is then one line, with what it follows and a thin bar for its value,
 * and its slider comes back when it stops. Reset shows once something differs from what the shader comes with.
 * A number's title or value, held, opens a box to type it ([TypeValueDialog]).
 *
 * Parameters that follow a row of values, such as the eight blocks of today's screen time ([ShaderSeries]), are
 * one row with a small chart of their values; a tap shows their own rows under it. Which are open is kept in
 * [opened] while the details are open.
 */
@Composable
internal fun ShaderParamRows(
    shader: String,
    params: List<ShaderParam>,
    actions: ShaderActions,
    modifier: Modifier = Modifier,
    opened: MutableMap<String, Boolean> = remember { mutableStateMapOf<String, Boolean>() },
) {
    val all by ShaderParams.values.collectAsStateWithLifecycle()
    val set = all[shader].orEmpty()
    val follows by ShaderParams.follows.collectAsStateWithLifecycle()
    val given by ShaderValues.given.collectAsStateWithLifecycle()
    val formulas by ShaderParams.formulas.collectAsStateWithLifecycle()
    val phone = remember(given, follows, formulas) { actions.phone() }
    // A run is found from what the header says each parameter follows, so it stays one row whatever is picked.
    val runs = remember(params) { ShaderSeries.runs(params.map { p -> p.follow.takeIf { p.choices.isEmpty() } }) }
    val follow = params.map { ShaderParams.follow(follows[shader].orEmpty(), it) }
    val formula = params.map { ShaderParams.formula(shader, it) }
    val followed = params.mapIndexed { i, param -> follow[i]?.let { ShaderParams.followed(param, it, formula[i], phone) } }
    val now = params.mapIndexed { i, param -> followed[i] ?: ShaderParams.of(set, param) }
    fun opener(run: ShaderSeries.Run) = "$shader ${params[run.first].name}"
    // What is on screen, in order: a parameter by its index, or a run's own row, then its parameters while it is open.
    val rows = buildList<Pair<Int, ShaderSeries.Run?>> {
        var i = 0
        while (i < params.size) {
            val run = runs.firstOrNull { it.first == i }
            if (run == null) {
                add(i to null)
                i++
                continue
            }
            add(i to run)
            if (opened[opener(run)] == true) run.indices.forEach { add(it to null) }
            i = run.last + 1
        }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { k, (i, run) ->
            key(i, run != null) {
                if (run != null) {
                    val name = opener(run)
                    SeriesRow(run, params, now, open = opened[name] == true, segShapes(k, rows.size)) {
                        opened[name] = opened[name] != true
                    }
                } else ParamRow(
                    shader, params[i], segShapes(k, rows.size), follow[i], formula[i], followed[i], now[i], given, phone, actions,
                )
            }
        }
        if (params.any { ShaderParams.of(set, it) != it.default || ShaderParams.follow(follows[shader].orEmpty(), it) != it.follow }) TextButton(
            onClick = { actions.resetParams(shader) },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.align(Alignment.End),
        ) { Text("Reset") }
    }
}

/**
 * One parameter. [follow] is the value it follows, with [formula]; [followed] what that makes it (null: nothing yet,
 * then what is set counts); [value] what it is now.
 */
@Composable
private fun ParamRow(
    shader: String,
    param: ShaderParam,
    shapes: ListItemShapes,
    follow: String?,
    formula: String?,
    followed: Float?,
    value: Float,
    given: Map<String, String>,
    phone: ShaderValues.Phone,
    actions: ShaderActions,
) {
    // A tap on a number's title or value says it can be held, in a toast, as a tap on the sheet's eye does.
    val context = LocalContext.current
    val number = param.choices.isEmpty()
    // A number that isn't following a value can be typed: hold its title or its value, as in Settings.
    val typable = number && follow == null
    var typing by remember(shader, param.name) { mutableStateOf(false) }
    val hold = rememberHoldToType(
        onHold = { typing = true },
        onTap = { Toast.makeText(context, HOLD_TO_TYPE, Toast.LENGTH_SHORT).show() },
    ).takeIf { typable } ?: Modifier
    if (typing && typable) TypeValueDialog(
        title = param.title,
        value = param.text(value),
        range = TypedNumber.range(param.min, param.max, param.whole),
        decimals = !param.whole,
        read = { TypedNumber.read(it, param.min, param.max, param.whole) },
        onDismiss = { typing = false },
        onApply = { n ->
            typing = false
            actions.setParam(shader, param, n)
        },
    )
    // A formula can be worked out only on a number to follow.
    val numeric = follow != null && ShaderValues.of(follow, given, phone)?.toFloatOrNull() != null
    // A number that follows a value shows the formula field once one is set, or asked for in the Follow menu.
    var asked by remember(shader, param.name, follow) { mutableStateOf(false) }
    val field = numeric && (!number || formula != null || asked)
    val pick: (String?) -> Unit = { actions.follow(shader, param, it) }
    val addFormula = if (number && numeric && !field) ({ asked = true }) else null
    SegmentedListItem(
        shapes = shapes,
        colors = segColors(),
        modifier = if (typable) Modifier.typeValueAction { typing = true } else Modifier,
        trailingContent = if (!number) null else {
            {
                Text(
                    param.text(value),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = hold,
                )
            }
        },
        supportingContent = {
            Column {
                when {
                    !number -> {
                        Spacer(Modifier.height(8.dp))
                        ConnectedChoices(
                            param.choices, param.text(value), tagPrefix = "param ${param.name} ",
                            label = { it.replaceFirstChar(Char::uppercase) },
                        ) { word -> if (followed == null) param.parse(word)?.let { actions.setParam(shader, param, it) } }
                        Spacer(Modifier.height(4.dp))
                        FollowButton(param, follow, hasValue = followed != null, actions, onPick = pick)
                    }
                    follow == null -> {
                        ParamSlider(shader, param, value, actions)
                        FollowButton(param, null, hasValue = false, actions, onPick = pick)
                    }
                    else -> ValueBar(param, value)
                }
                if (field && follow != null) FormulaField(param, follow, formula, phone) { actions.follow(shader, param, follow, it) }
            }
        },
    ) {
        if (number && follow != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Text(param.title, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            FollowButton(
                param, follow, hasValue = followed != null, actions, onPick = pick, onFormula = addFormula,
                modifier = Modifier.weight(1f, fill = false),
            )
        } else Text(
            param.title,
            Modifier
                .fillMaxWidth()
                .then(hold),
        )
    }
}

/** Where [value] is in [param]'s range, from 0 to 1. */
private fun part(param: ShaderParam, value: Float): Float =
    if (param.max <= param.min) 0f else ((value - param.min) / (param.max - param.min)).coerceIn(0f, 1f)

/** A thin bar for the value of a parameter that follows one: it has no slider then. */
@Composable
private fun ValueBar(param: ShaderParam, value: Float) {
    Box(
        Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Box(
            Modifier
                .fillMaxWidth(part(param, value))
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.primary)
        )
    }
}

/**
 * The row of a [run] of parameters: what their values are, what they follow, and a bar for each of [now]. A tap
 * shows or hides their own rows ([open]). A screen reader hears it as one button, with the values.
 */
@Composable
private fun SeriesRow(
    run: ShaderSeries.Run,
    params: List<ShaderParam>,
    now: List<Float>,
    open: Boolean,
    shapes: ListItemShapes,
    onToggle: () -> Unit,
) {
    val title = ShaderSeries.title(run.ids)
    val follows = ShaderSeries.follows(run.ids)
    val members = run.indices.map { params[it] to now[it] }
    val turn by animateFloatAsState(if (open) 180f else 0f, MaterialTheme.motionScheme.defaultSpatialSpec())
    SegmentedListItem(
        onClick = onToggle,
        shapes = shapes,
        colors = segColors(),
        modifier = Modifier
            .testTag("series ${params[run.first].name}")
            .clearAndSetSemantics {
                contentDescription = "$title. $follows. " + members.joinToString(", ") { (p, v) -> "${p.title}: ${p.text(v)}" }
                stateDescription = if (open) "Expanded" else "Collapsed"
                role = Role.Button
                onClick { onToggle(); true }
            },
        supportingContent = {
            Column {
                Text(follows)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(28.dp),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    val color = MaterialTheme.colorScheme.primary
                    // A value of 0 is a sliver, so each bar is there.
                    for ((p, v) in members) Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(part(p, v).coerceAtLeast(0.08f))
                            .clip(RoundedCornerShape(2.dp))
                            .background(color)
                    )
                }
            }
        },
        trailingContent = {
            Icon(painterResource(R.drawable.ic_expand_more), null, Modifier.rotate(turn))
        },
    ) { Text(title) }
}

@Composable
private fun ParamSlider(shader: String, param: ShaderParam, value: Float, actions: ShaderActions) {
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
        modifier = Modifier
            .testTag("param ${param.name}")
            .semantics { contentDescription = param.title },
    )
}

/**
 * What a parameter follows, and the menu to pick it: Kolahal's values and those that scripts give. The value
 * it follows may have nothing yet: then what is set counts. With [onFormula], the menu also offers a formula
 * for the value it follows ([FormulaField]).
 */
@Composable
private fun FollowButton(
    param: ShaderParam,
    follow: String?,
    hasValue: Boolean,
    actions: ShaderActions,
    onPick: (String?) -> Unit,
    onFormula: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val sources by produceState(emptyList<ShaderValues.Source>(), open) { if (open) value = actions.sources() }
    Box(modifier) {
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
            val groups = if (onFormula != null) 2 else 1
            DropdownMenuGroup(shapes = MenuDefaults.groupShape(0, groups)) {
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
            if (onFormula != null) {
                Spacer(Modifier.height(MenuDefaults.GroupSpacing))
                DropdownMenuGroup(shapes = MenuDefaults.groupShape(1, groups)) {
                    DropdownMenuItem(
                        onClick = {
                            open = false
                            onFormula()
                        },
                        text = { Text("Add formula") },
                        shape = MenuDefaults.standaloneItemShape,
                        modifier = Modifier.testTag("add-formula ${param.name}"),
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
