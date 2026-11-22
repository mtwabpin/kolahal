/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.script.ShaderParam
import com.mtwabpin.kolahal.script.ShaderParams
import com.mtwabpin.kolahal.terminal.ShaderActions

/**
 * A shader's parameters: a slider for a number, connected buttons for words. What is on screen with the
 * shader follows them while a finger drags, and a value is kept when it lifts. Reset shows once one
 * differs from what the shader comes with.
 */
@Composable
internal fun ShaderParamRows(shader: String, params: List<ShaderParam>, actions: ShaderActions, modifier: Modifier = Modifier) {
    val all by ShaderParams.values.collectAsStateWithLifecycle()
    val set = all[shader].orEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        params.forEachIndexed { i, param ->
            val value = ShaderParams.of(set, param)
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
                    if (param.choices.isEmpty()) ParamSlider(shader, param, value, actions)
                    else Column {
                        Spacer(Modifier.height(8.dp))
                        ConnectedChoices(
                            param.choices, param.text(value), tagPrefix = "param ${param.name} ",
                            label = { it.replaceFirstChar(Char::uppercase) },
                        ) { word -> param.parse(word)?.let { actions.setParam(shader, param, it) } }
                    }
                },
            ) { Text(param.title) }
        }
        if (params.any { ShaderParams.of(set, it) != it.default }) TextButton(
            onClick = { actions.resetParams(shader) },
            shapes = ButtonDefaults.shapes(),
            modifier = Modifier.align(Alignment.End),
        ) { Text("Reset") }
    }
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
