/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue

/** The screen reader's action on a number row that opens [TypeValueDialog]. */
internal const val TYPE_VALUE = "Type a value"

/** What a tap on a number row's title or value says, as a tap on the eye says to hold it. */
internal const val HOLD_TO_TYPE = "Tap and hold to type a value"

/**
 * The box to type a number in place of a slider, opened by a hold on a number row's title or value
 * ([rememberHoldToType]) or by the row's [TYPE_VALUE] action ([typeValueAction]).
 * - The value now is in the box, selected, so typing replaces it. The number keyboard opens.
 * - Under the box is the range ([range]). It turns red while what is typed isn't a number in it ([read] gives
 *   null), and OK waits. Enter on an empty box turns it red too.
 * - Enter (Done on the keyboard) or OK gives the number to [onApply], which closes the box. Cancel or a tap
 *   outside it leaves the value as it was.
 */
@Composable
internal fun <T : Any> TypeValueDialog(
    title: String,
    value: String,
    range: String,
    decimals: Boolean,
    read: (String) -> T?,
    onDismiss: () -> Unit,
    onApply: (T) -> Unit,
) {
    var text by remember { mutableStateOf(TextFieldValue(value, TextRange(0, value.length))) }
    var tried by remember { mutableStateOf(false) }
    val typed = read(text.text)

    fun applyTyped() {
        if (typed != null) onApply(typed) else tried = true
    }

    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            TextField(
                shape = TextFieldDefaults.roundedShape,
                colors = fieldColors(),
                value = text,
                onValueChange = {
                    text = it
                    tried = false
                },
                singleLine = true,
                isError = typed == null && (text.text.isNotBlank() || tried),
                supportingText = { Text(range) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { applyTyped() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focus)
                    // Enter on a keyboard that sends keys rather than Done.
                    .onPreviewKeyEvent { e ->
                        if (e.type == KeyEventType.KeyDown && (e.key == Key.Enter || e.key == Key.NumPadEnter)) {
                            applyTyped()
                            true
                        } else false
                    }
                    .testTag("type-value"),
            )
            // In the dialog's own window, once the box is there: the keyboard opens with it.
            LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        },
        confirmButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = { applyTyped() },
                enabled = typed != null,
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(
                shapes = ButtonDefaults.shapes(),
                onClick = onDismiss,
            ) { Text("Cancel") }
        },
    )
}

/**
 * For a number row's title and value: a hold runs [onHold] (opens [TypeValueDialog]), with the buzz of a
 * long-press; a tap runs [onTap], which says the hold is there ([HOLD_TO_TYPE]).
 * Never on the slider: a hold there moves its thumb to the finger, and people hold and then drag.
 * A scroll that starts on it cancels the hold, as on any row.
 */
@Composable
internal fun rememberHoldToType(onHold: () -> Unit, onTap: (() -> Unit)? = null): Modifier {
    val haptic = LocalHapticFeedback.current
    val hold by rememberUpdatedState(onHold)
    val tap by rememberUpdatedState(onTap)
    return remember(haptic) {
        Modifier.pointerInput(Unit) {
            detectTapGestures(
                onTap = { tap?.invoke() },
                onLongPress = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    hold()
                },
            )
        }
    }
}

/** The row's [TYPE_VALUE] action for a screen reader: the same box, with no hold. The slider keeps its own. */
internal fun Modifier.typeValueAction(onType: () -> Unit): Modifier =
    semantics { customActions = listOf(CustomAccessibilityAction(TYPE_VALUE) { onType(); true }) }
