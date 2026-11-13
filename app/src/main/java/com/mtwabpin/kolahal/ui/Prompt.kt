package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.terminal.KeyBarKey
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.TerminalViewModel

@Composable
internal fun Prompt(
    vm: TerminalViewModel,
    focus: FocusRequester,
    /** Text shown read-only instead of the typed input. Used when Settings previews home as if typing. */
    previewText: String? = null,
    imeSuggestions: Boolean,
    onFocus: (Boolean) -> Unit,
    height: Dp,
    keyBarOn: Boolean,
    onQuick: () -> Unit,
    onToggleKeyBar: () -> Unit,
    onTapPrompt: () -> Unit,
) {
    val configVersion by vm.config.version.collectAsStateWithLifecycle()
    val swipeUpOn = remember(configVersion) { vm.config[Keys.GESTURE_SWIPE_UP].trim().lowercase() !in setOf("", "none") }
    val promptStyle = Term.promptText.copy(fontSize = (Term.fontSize + 2).sp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            // The line is as tall as [height] or the text, whichever is taller. The $, ✕ and ⌨
            // targets fill this height, so a tap anywhere in it hits them.
            .height(IntrinsicSize.Min)
            // Swipe up on the prompt line runs gesture.swipe-up. When it is none (the default), the
            // detector is not added at all, so it cannot misfire.
            .then(
                if (!swipeUpOn) Modifier else Modifier.pointerInput(Unit) {
                    var total = 0f
                    detectVerticalDragGestures(
                        onDragStart = { total = 0f },
                        onDragEnd = { if (total < -48.dp.toPx()) vm.onGesture(Keys.GESTURE_SWIPE_UP) },
                    ) { _, dy -> total += dy }
                },
            )
            .clickable(onClick = onTapPrompt)
            // The vertical space comes from [height] (centred), so small values really make the line
            // shorter. There is no end padding: the ⌨ target reaches the screen edge and pads its own icon.
            .padding(start = 12.dp, top = 2.dp, bottom = 2.dp),
    ) {
        // A tap on `$` opens quick actions (settings, battery, profiles, …), within thumb reach.
        Text(
            "$ ",
            style = promptStyle.copy(color = Term.prompt, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .testTag("prompt-quick")
                .clickable(onClickLabel = "quick actions", onClick = onQuick)
                .fillMaxHeight()
                .padding(end = 2.dp)
                .wrapContentHeight(),
        )
        BasicTextField(
            value = previewText?.let { TextFieldValue(it, TextRange(it.length)) } ?: vm.input,
            onValueChange = if (previewText == null) vm::onInputChange else { _ -> },
            readOnly = previewText != null,
            singleLine = true,
            textStyle = promptStyle,
            cursorBrush = SolidColor(Term.prompt),
            keyboardOptions = KeyboardOptions(
                autoCorrectEnabled = imeSuggestions,
                capitalization = KeyboardCapitalization.None,
                // The Password type is the reliable way to turn off IME autocorrect and suggestions.
                keyboardType = if (imeSuggestions) KeyboardType.Text else KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { vm.submit() }),
            modifier = Modifier
                .weight(1f)
                .testTag("prompt")
                .focusRequester(focus)
                .onFocusChanged { onFocus(it.isFocused) }
                .onPreviewKeyEvent { e ->
                    if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        e.key == Key.DirectionUp -> { vm.historyUp(); true }
                        e.key == Key.DirectionDown -> { vm.historyDown(); true }
                        e.key == Key.Tab -> { vm.complete(); true }
                        e.key == Key.Escape -> { vm.escape(); true }
                        e.key == Key.Enter || e.key == Key.NumPadEnter -> { vm.submit(); true }
                        e.isCtrlPressed && e.key == Key.L -> { vm.clearScreen(); true }
                        e.isCtrlPressed && e.key == Key.U -> { vm.escape(); true }
                        e.isCtrlPressed && e.key == Key.A -> { vm.onKeyBar(KeyBarKey.HOME); true }
                        e.isCtrlPressed && e.key == Key.E -> { vm.onKeyBar(KeyBarKey.END); true }
                        else -> false
                    }
                },
        )
        // Use drawn icons, like the header does. As text, ✕ and ⌨ come from a fallback symbol font
        // and look hand-drawn.
        val iconSize = (Term.fontSize + 6).dp
        // The clear button. Without the key bar, touch typing has no Esc key.
        if ((previewText ?: vm.input.text).isNotEmpty()) {
            // The touch target extends to the left. The icon sits at its right end, close to ⌨.
            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .testTag("prompt-clear")
                    .fillMaxHeight()
                    .clickable(onClickLabel = "clear the prompt") { vm.escape() }
                    .padding(start = 16.dp, end = 8.dp),
            ) {
                Image(
                    painterResource(R.drawable.ic_close),
                    contentDescription = "Clear",
                    colorFilter = ColorFilter.tint(Term.dim),
                    modifier = Modifier.size(iconSize),
                )
            }
        }
        // The key bar toggle (keybar.show). It is dim while the bar is off. The icon lines up with
        // the log's right edge, and the touch target reaches the screen edge.
        Box(
            contentAlignment = Alignment.CenterEnd,
            modifier = Modifier
                .testTag("keybar-toggle")
                .fillMaxHeight()
                .clickable(onClickLabel = "toggle key bar", onClick = onToggleKeyBar)
                .padding(start = 8.dp, end = 12.dp),
        ) {
            Image(
                painterResource(R.drawable.ic_keyboard),
                contentDescription = "Key bar",
                colorFilter = ColorFilter.tint(if (keyBarOn) Term.prompt else Term.dim),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
