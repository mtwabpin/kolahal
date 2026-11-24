/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ButtonShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.feeds.FeedRepository
import com.mtwabpin.kolahal.terminal.Screen
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The screens behind the gear, in tab order. */
internal val FEATURE_TABS = listOf(Screen.SETTINGS, Screen.SCRIPTS, Screen.FEEDS)

/**
 * The Settings | Scripts | Feeds tab row at the top of each of these three screens.
 * - A connected button group, as [ConnectedChoices]: the open screen's button is filled with the
 *   accent and fully round; the others are in the rows' color.
 * - Each button is a tab to a screen reader: "Selected" on the open one, "1 of 3" and so on.
 * - Switching tabs replaces the screen ([TerminalViewModel.switchTab]), so one Back leaves all three.
 * - The Feeds tab shows the unread count.
 * - At a large font size, a word that doesn't fit shrinks to fit its button, then ends in "…".
 */
@Composable
internal fun FeatureTabs(vm: TerminalViewModel, current: Screen, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val feeds = remember { FeedRepository.get(context) }
    val feedVersion by feeds.version.collectAsStateWithLifecycle()
    val unread by produceState(0, feedVersion) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                feeds.all().filter { it.enabled }.sumOf { it.unread }
            }.getOrDefault(0)
        }
    }
    val colors = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.labelLarge
    val autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = style.fontSize, stepSize = 1.sp)
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .selectableGroup()
            .testTag("feature-tabs"),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        FEATURE_TABS.forEachIndexed { i, screen ->
            val on = screen == current
            val label = when (screen) {
                Screen.FEEDS -> if (unread > 0) "Feeds · $unread" else "Feeds"
                Screen.SCRIPTS -> "Scripts"
                else -> "Settings"
            }
            val shapes = when (i) {
                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                FEATURE_TABS.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
            }
            // A button, not a toggle button: a toggle button tells a screen reader "Checked" or "Not
            // checked", where a tab is "Selected". The open tab has the toggle button's checked shape.
            Button(
                onClick = { if (!on) vm.switchTab(screen) },
                shapes = ButtonShapes(
                    shape = if (on) shapes.checkedShape else shapes.shape,
                    pressedShape = shapes.pressedShape,
                ),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (on) colors.primary else colors.surfaceContainerHigh,
                    contentColor = if (on) colors.onPrimary else colors.onSurfaceVariant,
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics {
                        role = Role.Tab
                        selected = on
                    },
            ) {
                Text(
                    label,
                    style = style,
                    autoSize = autoSize,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
