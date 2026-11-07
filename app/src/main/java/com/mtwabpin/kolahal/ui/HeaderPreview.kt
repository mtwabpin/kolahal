package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.terminal.TerminalViewModel

/**
 * A preview of the top of home with the Header page's settings. It shows while the header is on.
 * - It draws the header and the pinned header with the same code and live status as home,
 *   in the terminal's colours.
 * - It updates when a setting changes.
 * - It only shows. Taps and swipes on it do nothing.
 */
@Composable
internal fun HeaderPreview(vm: TerminalViewModel) {
    val config = vm.config
    val version by config.version.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val unlocks by vm.unlocksToday.collectAsStateWithLifecycle()
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val todoCount by vm.todoCount.collectAsStateWithLifecycle()
    val pinnedLines by vm.pinned.collectAsStateWithLifecycle()
    val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
    val shape = RoundedCornerShape(12.dp)
    val note = Term.small.copy(color = Term.dim)

    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(shape)
            .background(Term.bg)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            // One description for screen readers and a tag for tests. The semantics inside are cleared.
            .clearAndSetSemantics {
                contentDescription = "Preview of the top of home"
                testTag = "header-preview"
            },
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Header(
                status = status,
                unlocks = unlocks,
                profiles = profiles,
                todoCount = todoCount,
                config = config,
                version = version,
                onTarget = {},
                onProfileTap = {},
                onSwipeDown = {},
                preview = true,
            )
            // On home, an empty pinned header takes no space. Here we show a note instead.
            val media = nowPlaying
            if (pinnedLines.isEmpty() && media?.showInHeader() != true) {
                Text("Nothing playing or pinned", style = note, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp))
            } else PinnedHeader(
                nowPlaying = media,
                lines = pinnedLines,
                config = config,
                version = version,
                onMediaToggle = {},
                onMediaNext = {},
                onPinTap = {},
                onPinLongPress = {},
            )
        }
        // This layer sits on top, so touches reach it and not the parts below. It consumes nothing,
        // so the page can still scroll from here.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } },
        )
    }
}
