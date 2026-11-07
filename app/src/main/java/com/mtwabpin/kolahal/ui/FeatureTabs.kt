package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
 * - Switching tabs replaces the screen ([TerminalViewModel.switchTab]), so one Back leaves all three.
 * - The Feeds tab shows the unread count.
 */
@Composable
internal fun FeatureTabs(vm: TerminalViewModel, current: Screen, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val feeds = remember { FeedRepository.get(context) }
    val feedVersion by feeds.version.collectAsStateWithLifecycle()
    val unread by produceState(0, feedVersion) {
        value = withContext(Dispatchers.IO) { runCatching { feeds.all().filter { it.enabled }.sumOf { it.unread } }.getOrDefault(0) }
    }
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainerHigh)
            .padding(4.dp)
            .selectableGroup()
            .testTag("feature-tabs"),
    ) {
        for (screen in FEATURE_TABS) {
            val on = screen == current
            val label = when (screen) {
                Screen.FEEDS -> if (unread > 0) "Feeds · $unread" else "Feeds"
                Screen.SCRIPTS -> "Scripts"
                else -> "Settings"
            }
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (on) colors.primary else Color.Transparent)
                    .selectable(selected = on, role = Role.Tab) { if (!on) vm.switchTab(screen) }
                    .heightIn(min = 48.dp)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = if (on) colors.onPrimary else colors.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
