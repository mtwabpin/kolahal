/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.shaders

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilledTonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.material3.ToggleButton
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.shader.ShaderParam
import com.mtwabpin.kolahal.shader.ShaderParams
import com.mtwabpin.kolahal.shader.ShaderValues
import com.mtwabpin.kolahal.shader.ParamTarget
import com.mtwabpin.kolahal.ui.AccessNeeds
import com.mtwabpin.kolahal.ui.ConnectedChoices
import com.mtwabpin.kolahal.ui.HOLD_TO_TYPE
import com.mtwabpin.kolahal.ui.Labeled
import com.mtwabpin.kolahal.ui.TypeValueDialog
import com.mtwabpin.kolahal.ui.TypedNumber
import com.mtwabpin.kolahal.ui.fieldColors
import com.mtwabpin.kolahal.ui.rememberHoldToType
import com.mtwabpin.kolahal.ui.rememberMissing
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
    actions: ParamTarget,
    modifier: Modifier = Modifier,
    opened: MutableMap<String, Boolean> = remember { mutableStateMapOf<String, Boolean>() },
) {
    val all by actions.values.collectAsStateWithLifecycle()
    val set = all[shader].orEmpty()
    val follows by actions.follows.collectAsStateWithLifecycle()
    val given by ShaderValues.given.collectAsStateWithLifecycle()
    val formulas by actions.formulas.collectAsStateWithLifecycle()
    val phone = remember(given, follows, formulas) { actions.phone() }
    // A run is found from what the header says each parameter follows, so it stays one row whatever is picked.
    val runs = remember(params) { ShaderSeries.runs(params.map { p -> p.follow.takeIf { p.choices.isEmpty() } }) }
    val inRun = remember(runs) { runs.flatMap { it.indices }.toSet() }
    val follow = params.map { ShaderParams.follow(follows[shader].orEmpty(), it) }
    val formula = params.map { ShaderParams.formula(formulas[shader].orEmpty(), follows[shader].orEmpty(), it) }
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
                    // The run's row says what they follow, while one still follows what its header says.
                    inSeries = i in inRun && follow[i] == params[i].follow,
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
 * then what is set counts); [value] what it is now. [inSeries]: it is under the row of its run, which says what it
 * follows, so its Follow button is the link icon alone.
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
    actions: ParamTarget,
    inSeries: Boolean,
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
        // A number that follows a value has its value on the line with its name ([FollowLine]).
        trailingContent = if (!number || follow != null) null else {
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
                        val pickWord: (String) -> Unit = { word -> if (followed == null) param.parse(word)?.let { actions.setParam(shader, param, it) } }
                        if (param.choices.size <= WORDS_IN_ROW) ConnectedChoices(
                            param.choices, param.text(value), tagPrefix = "param ${param.name} ",
                            label = { it.replaceFirstChar(Char::uppercase) },
                            onPick = pickWord,
                        ) else ChoiceRows(param.choices, param.text(value), tagPrefix = "param ${param.name} ", onPick = pickWord)
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
        if (number && follow != null) FollowLine(
            name = { Text(param.title) },
            button = {
                FollowButton(
                    param, follow, hasValue = followed != null, actions, onPick = pick, onFormula = addFormula, icon = inSeries,
                )
            },
            value = {
                Text(
                    param.text(value),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            },
        ) else Text(
            param.title,
            Modifier
                .fillMaxWidth()
                .then(hold),
        )
    }
}

/** The most words in one row of a parameter's buttons. A choice of more is in rows ([choiceRows]). */
private const val WORDS_IN_ROW = 4

/**
 * How a parameter's choice of [count] words is laid out: how many buttons each row has, from the top. Every button in
 * a row has the same width, and every row the full width [widthDp], so the buttons of a shorter row are wider.
 * - Up to four words: one row, as any choice of a few words is ([ConnectedChoices]).
 * - Five to eight: two rows, as even as they go, the longer first: five are three and two, six three and three,
 *   seven four and three, eight four and four.
 * - At a large font a word shrinks to fit its button first. Only where fewer buttons than that fit in a row, each
 *   [needDp] wide for the widest word at its smallest, with [gapDp] between two, do the rows break further: into
 *   as many rows as it takes, again as even as they go, the longer first. Seven where three fit are three, two and two.
 */
internal fun choiceRows(count: Int, widthDp: Float, needDp: Float, gapDp: Float): List<Int> {
    if (count <= WORDS_IN_ROW) return listOf(count)
    val fit = ((widthDp + gapDp) / (needDp + gapDp)).toInt().coerceIn(1, WORDS_IN_ROW)
    val rows = (count + fit - 1) / fit
    return List(rows) { i -> count / rows + if (i < count % rows) 1 else 0 }
}

/** The smallest size of a word in [ChoiceRows], in sp, and the room at each side of it inside its button, in dp. */
private const val CHOICE_MIN_SP = 10
private const val CHOICE_PAD_DP = 4

/**
 * A choice of more than [WORDS_IN_ROW] words, as the Arrange toolbar's choices are: rows of connected buttons laid out
 * by [choiceRows], [selected] checked. A tap picks one ([onPick] with its word).
 * - A word that doesn't fit its button shrinks to fit, down to [CHOICE_MIN_SP] sp.
 * - Where the widest word doesn't fit even then, there are more rows: every word stays whole, and each choice is one tap.
 */
@Composable
private fun ChoiceRows(choices: List<String>, selected: String, tagPrefix: String, onPick: (String) -> Unit) {
    val labels = choices.map { it.replaceFirstChar(Char::uppercase) }
    val gap = ButtonGroupDefaults.ConnectedSpaceBetween
    val measurer = rememberTextMeasurer()
    val smallest = MaterialTheme.typography.labelLarge.copy(fontSize = CHOICE_MIN_SP.sp)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // The button the widest word needs at its smallest size.
        val need = remember(labels, smallest, density) {
            val widest = labels.maxOfOrNull { measurer.measure(it, smallest, maxLines = 1, softWrap = false).size.width } ?: 0
            with(density) { widest.toDp() } + (2 * CHOICE_PAD_DP).dp
        }
        val counts = choiceRows(choices.size, maxWidth.value, need.value, gap.value)
        val rows = counts.runningFold(0, Int::plus).zipWithNext { from, to -> (from until to).toList() }
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { i ->
                        ToggleButton(
                            checked = choices[i] == selected,
                            onCheckedChange = { onPick(choices[i]) },
                            shapes = when (i) {
                                row.first() -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                row.last() -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                            contentPadding = PaddingValues(horizontal = CHOICE_PAD_DP.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag(tagPrefix + choices[i]),
                        ) {
                            Text(
                                labels[i],
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

/** The room between a following parameter's name and its Follow button, and between the button and its value. */
private val NAME_GAP = 8.dp
private val VALUE_GAP = 12.dp

/**
 * The line of a number that follows a value: its [name] at the start, then its Follow [button], and its [value] at
 * the end, centered on the line. The value has the room it needs first, then the name, as long as the button keeps
 * what it needs or half of what is left; the button has the rest, and its words end in "…" where they don't fit. A
 * name with less room than it needs wraps.
 */
@Composable
private fun FollowLine(
    name: @Composable () -> Unit,
    button: @Composable () -> Unit,
    value: @Composable () -> Unit,
) {
    Layout(listOf(name, button, value)) { (names, buttons, values), constraints ->
        val title = names.first()
        val follow = buttons.first()
        val nameGap = NAME_GAP.roundToPx()
        val valueGap = VALUE_GAP.roundToPx()
        val shown = values.first().measure(Constraints(maxWidth = constraints.maxWidth))
        val needs = follow.maxIntrinsicWidth(Constraints.Infinity)
        val wants = title.maxIntrinsicWidth(Constraints.Infinity)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else wants + nameGap + needs + valueGap + shown.width
        val room = (width - shown.width - nameGap - valueGap).coerceAtLeast(0)
        val least = minOf(needs, room / 2)
        val nameWidth = minOf(wants, room - least)
        val titlePlaced = title.measure(Constraints(maxWidth = nameWidth))
        val buttonPlaced = follow.measure(Constraints(maxWidth = minOf(needs, room - nameWidth)))
        val height = constraints.constrainHeight(maxOf(titlePlaced.height, buttonPlaced.height, shown.height))
        layout(width, height) {
            titlePlaced.placeRelative(0, (height - titlePlaced.height) / 2)
            buttonPlaced.placeRelative(width - shown.width - valueGap - buttonPlaced.width, (height - buttonPlaced.height) / 2)
            shown.placeRelative(width - shown.width, (height - shown.height) / 2)
        }
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
private fun ParamSlider(shader: String, param: ShaderParam, value: Float, actions: ParamTarget) {
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
            actions.tryOut(shader, param, it)
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
 *
 * The button says what it follows in the fewest words beside its link icon ([ShaderSeries.followsOne]), on one line
 * that ends in "…" where it doesn't fit, and a screen reader hears "Follows" and the value's whole name. With [icon] it is the link icon alone,
 * which says the whole name on a long-press, as the app's other icon buttons do ([Labeled]).
 */
@Composable
private fun FollowButton(
    param: ShaderParam,
    follow: String?,
    hasValue: Boolean,
    actions: ParamTarget,
    onPick: (String?) -> Unit,
    onFormula: (() -> Unit)? = null,
    icon: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    val sources by produceState(emptyList<ShaderValues.Source>(), open) { if (open) value = actions.sources() }
    // A value of the phone's use says that it needs usage access, while that isn't given ([AccessNeeds.valueNote]).
    val missing = rememberMissing(remember(follow, sources) { (listOfNotNull(follow) + sources.map { it.id }).mapNotNullTo(HashSet<String>(), AccessNeeds::ofValue) })
    val noValue = follow?.let { AccessNeeds.valueNote(it, missing) }?.let { " ($it)" } ?: if (hasValue) "" else " (no value yet)"
    val words = if (follow == null) "Follow" else "Follows ${ShaderValues.label(follow)}$noValue"
    val short = if (follow == null) "Follow" else ShaderSeries.followsOne(follow) + noValue
    Box(modifier) {
        if (icon) Labeled(words) {
            FilledTonalIconToggleButton(
                checked = follow != null,
                onCheckedChange = { open = true },
                shapes = IconButtonDefaults.toggleableShapes(),
                modifier = Modifier.testTag("follow ${param.name}"),
            ) { Icon(painterResource(R.drawable.wi_link), words, Modifier.size(IconButtonDefaults.smallIconSize)) }
        } else FilledTonalToggleButton(
            checked = follow != null,
            onCheckedChange = { open = true },
            shapes = ToggleButtonDefaults.shapesFor(ButtonDefaults.ExtraSmallContainerHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.ExtraSmallContainerHeight),
            modifier = Modifier
                .heightIn(ButtonDefaults.ExtraSmallContainerHeight)
                .testTag("follow ${param.name}")
                .semantics { if (words != short) contentDescription = words },
        ) {
            Icon(painterResource(R.drawable.wi_link), null, Modifier.size(ButtonDefaults.ExtraSmallIconSize))
            Spacer(Modifier.width(ButtonDefaults.ExtraSmallIconSpacing))
            Text(
                short,
                style = ButtonDefaults.textStyleFor(ButtonDefaults.ExtraSmallContainerHeight),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                    s.id to s.label + (AccessNeeds.valueNote(s.id, missing)?.let { " ($it)" } ?: s.value?.let { " · ${ShaderValues.shown(s.id, it)}" }.orEmpty())
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
