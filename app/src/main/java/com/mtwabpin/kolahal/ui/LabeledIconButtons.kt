/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.annotation.DrawableRes
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource

/*
 * Icon buttons that say what they do. A long-press on one shows its label in a small bubble above
 * it, as Android's toolbars do for their icons, and a mouse pointer resting on it shows it too. The
 * same label is what TalkBack says, so the two never differ.
 *
 * Every icon button of the app is one of these (LabeledIconButtonsTest). A button with a long-press
 * of its own (Run with…, a step that repeats while held, a widget's long-press) has no bubble.
 */

/** [content] (an icon button) with [label] shown in a bubble above it on a long-press. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun Labeled(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(label) } },
        state = rememberTooltipState(),
        modifier = modifier,
        content = content,
    )
}

/**
 * An icon button: [icon], with [label] as what TalkBack says and as the bubble on a long-press.
 * [tint]: the icon's color; without it, the button's own (dimmed while not [enabled]).
 */
@Composable
internal fun LabeledIconButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color? = null,
    iconModifier: Modifier = Modifier,
) = Labeled(label) {
    IconButton(
        shapes = IconButtonDefaults.shapes(),
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        if (tint == null) Icon(
            painterResource(icon),
            contentDescription = label,
            modifier = iconModifier
        )
        else Icon(
            painterResource(icon),
            contentDescription = label,
            tint = tint,
            modifier = iconModifier
        )
    }
}
