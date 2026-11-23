/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.terminal.KeyBarKey
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import com.mtwabpin.kolahal.ui.theme.LocalBottomCorners
import com.mtwabpin.kolahal.ui.theme.LocalHomePage
import com.mtwabpin.kolahal.ui.theme.ScreenCorners
import com.mtwabpin.kolahal.ui.theme.Term
import com.mtwabpin.kolahal.ui.theme.cornerX
import com.mtwabpin.kolahal.ui.theme.windowBottomCorners
import kotlin.math.roundToInt

/** The room each end of the prompt keeps from the screen's edge, and from a rounded corner past what it hides. */
private val PROMPT_PAD = 12.dp

/** Room kept clear past what a rounded corner hides, as the header keeps at the top. */
private val CORNER_GAP = 4.dp

/**
 * How far an end of the prompt moves in for the screen's rounded bottom corners, in pixels. It is
 * separate from Compose so it can be tested.
 */
internal object PromptEnds {
    /**
     * The pixels added to an end's [pad] where the screen's corner hides [hidden] pixels over the
     * line's text ([ScreenCorners]). The end then stands [gap] past what is hidden, as the header's
     * ends do (HeaderLayout.zones), and never nearer the edge than [pad]. 0 where the corner hides
     * nothing.
     */
    fun extra(hidden: Int, pad: Int, gap: Int): Int =
        if (hidden > 0) (hidden + gap - pad).coerceAtLeast(0) else 0
}

/**
 * [extra] more pixels of room before the content ([atStart]) or after it, inside what comes before
 * this in the chain, so a tap target there grows by it. [extra] is read when laid out, so a change
 * measures again without composing.
 */
private fun Modifier.room(atStart: Boolean, extra: () -> Int) = layout { measurable, constraints ->
    val px = extra().coerceAtLeast(0)
    val p = measurable.measure(constraints.offset(horizontal = -px))
    layout(constraints.constrainWidth(p.width + px), p.height) { p.placeRelative(if (atStart) px else 0, 0) }
}

/**
 * The prompt line: `$ ` (quick actions), the text field, the clear button while there is text, and
 * the key bar toggle. When the line stands where the screen's rounded bottom corners cut it (the
 * keyboard down, the line at the screen's bottom), the `$` and the toggle move in past what the
 * corners hide over the line's text, 4 dp clear, as the header does at the top
 * ([LocalBottomCorners], [PromptEnds]); their tap targets keep reaching where they did. A line above
 * the corners keeps its place.
 */
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
    val swipeUpOn = remember(configVersion) {
        vm.config[Keys.GESTURE_SWIPE_UP].trim().lowercase() !in setOf(
            "",
            "none"
        )
    }
    val promptStyle = Term.promptText.copy(fontSize = (Term.fontSize + 2).sp)
    // Use drawn icons, like the header does. As text, ✕ and ⌨ come from a fallback symbol font
    // and look hand-drawn.
    val iconSize = (Term.fontSize + 6).dp
    // The screen's rounded bottom corners: a test's, else the window's, read where the line is placed.
    val view = LocalView.current
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val givenCorners = LocalBottomCorners.current
    val homePage = LocalHomePage.current
    // The line's place in the window, once it is placed. Its x is on home's page, which slides while
    // home turns to the widgets page (cornerX), so the ends keep their place on the page as it slides.
    var placed by remember { mutableStateOf<IntRect?>(null) }
    // The rows of the line's text (or its icons, if taller), centered in the line.
    val contentPx = with(density) { maxOf(promptStyle.lineHeight.toPx(), iconSize.toPx()) }.toInt()
    val pad = with(density) { PROMPT_PAD.roundToPx() }
    val gap = with(density) { CORNER_GAP.roundToPx() }
    // The pixels the start (the `$`) and the end (the toggle) move in. They change only when the
    // line moves into the corners or out, so the keyboard's animation measures the line again only then.
    val ends by remember(givenCorners, view, rtl, contentPx, pad, gap) {
        derivedStateOf {
            val at = placed ?: return@derivedStateOf 0 to 0
            val corners = givenCorners ?: windowBottomCorners(view)
            val top = at.top + (at.height - contentPx) / 2
            val bottom = top + contentPx
            val left = PromptEnds.extra(ScreenCorners.hiddenBottomLeft(corners.left, top, bottom, edge = at.left), pad, gap)
            val right = PromptEnds.extra(ScreenCorners.hiddenBottomRight(corners.right, top, bottom, edge = at.right), pad, gap)
            if (rtl) right to left else left to right
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = height)
            // The line is as tall as [height] or the text, whichever is taller. The $, ✕ and ⌨
            // targets fill this height, so a tap anywhere in it hits them.
            .height(IntrinsicSize.Min)
            .onPlaced {
                val x = cornerX(it, homePage).roundToInt()
                val y = it.positionInWindow().y.roundToInt()
                placed = IntRect(x, y, x + it.size.width, y + it.size.height)
            }
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
            // The vertical space comes from [height] (centered), so small values really make the line
            // shorter. There is no end padding: the ⌨ target reaches the screen edge and pads its own icon.
            .padding(start = PROMPT_PAD, top = 2.dp, bottom = 2.dp),
    ) {
        // A tap on `$` opens quick actions (settings, battery, profiles, …), within thumb reach.
        // A screen reader hears "Quick actions", not "dollar".
        Text(
            "$ ",
            style = promptStyle.copy(color = Term.prompt, fontWeight = FontWeight.Bold),
            modifier = Modifier
                .testTag("prompt-quick")
                .semantics { contentDescription = "Quick actions" }
                .clickable(onClickLabel = "Open", onClick = onQuick)
                // Past a rounded corner, the `$` moves in and its target grows to reach it.
                .room(atStart = true) { ends.first }
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
                        e.key == Key.DirectionUp -> {
                            vm.historyUp(); true
                        }

                        e.key == Key.DirectionDown -> {
                            vm.historyDown(); true
                        }

                        e.key == Key.Tab -> {
                            vm.complete(); true
                        }

                        e.key == Key.Escape -> {
                            vm.escape(); true
                        }

                        e.key == Key.Enter || e.key == Key.NumPadEnter -> {
                            vm.submit(); true
                        }

                        e.isCtrlPressed && e.key == Key.L -> {
                            vm.clearScreen(); true
                        }

                        e.isCtrlPressed && e.key == Key.U -> {
                            vm.escape(); true
                        }

                        e.isCtrlPressed && e.key == Key.A -> {
                            vm.onKeyBar(KeyBarKey.HOME); true
                        }

                        e.isCtrlPressed && e.key == Key.E -> {
                            vm.onKeyBar(KeyBarKey.END); true
                        }

                        else -> false
                    }
                },
        )
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
                // Past a rounded corner, the icon moves in; the target still reaches the screen edge.
                .room(atStart = false) { ends.second }
                .padding(start = 8.dp, end = PROMPT_PAD),
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
