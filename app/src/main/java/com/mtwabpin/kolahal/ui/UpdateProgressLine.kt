package com.mtwabpin.kolahal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtwabpin.kolahal.terminal.UpdateCommands

private const val MB = 1024 * 1024

/** The download `update install` has under way, if any. Read here, so it redraws this line and not all of home. */
@Composable
internal fun UpdateProgressLine() {
    val progress by UpdateCommands.progress.collectAsStateWithLifecycle()
    progress?.let { UpdateProgressLine(it) }
}

/** "Downloading update  4 MB / 10 MB  40%" over a bar, like [NowPlayingLine]. */
@Composable
internal fun UpdateProgressLine(progress: UpdateCommands.Progress) {
    val small = Term.text.copy(color = Term.dim, fontSize = (Term.fontSize - 1).sp)
    Column(Modifier.fillMaxWidth().testTag("update-progress").padding(horizontal = 12.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(
                "Downloading update",
                style = small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text("${progress.downloaded / MB} MB / ${progress.total / MB} MB  ${progress.percent}%", style = small, maxLines = 1)
        }
        ProgressTrack(progress.percent / 100f)
    }
}
