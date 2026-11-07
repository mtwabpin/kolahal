package com.mtwabpin.kolahal.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.terminal.TerminalViewModel
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

/** Fades out while home shows through Settings. It stays visible if it is the setting being changed ([keep]). */
@Composable
internal fun PeekFade(
    peek: TerminalViewModel.Peek?,
    modifier: Modifier = Modifier,
    keep: Boolean = false,
    content: @Composable () -> Unit,
) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        if (peek == null || keep) 1f else 0f, tween(PEEK_FADE_MS), label = "fade",
    )
    Box(modifier.graphicsLayer { this.alpha = alpha }) { content() }
}

/** How long the fade between Settings and home takes when a peek starts or ends. */
internal const val PEEK_FADE_MS = 220

/** A press on the eye shorter than this is a tap. A tap tells the user to hold, instead of flashing home. */
private const val PEEK_HOLD_MS = 150L

/** The eye button in the top bar. Home shows while the user holds it. A tap ([onTap]) explains this. */
@Composable
internal fun PeekButton(onHold: (Boolean) -> Unit, onTap: () -> Unit) {
    val scope = rememberCoroutineScope()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .semantics { contentDescription = "Hold to see the home screen" }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    val hold = scope.launch {
                        kotlinx.coroutines.delay(PEEK_HOLD_MS.milliseconds)
                        onHold(true)
                    }
                    val released = tryAwaitRelease()
                    if (hold.isActive) {
                        hold.cancel()
                        if (released) onTap()
                    } else {
                        onHold(false)
                    }
                })
            },
    ) {
        Icon(painterResource(R.drawable.ic_visibility), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
    }
}

/** The hint at the top while home shows through Settings. */
@Composable
internal fun PeekHint(modifier: Modifier) {
    Text(
        "Release to return",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .statusBarsPadding()
            .padding(top = 8.dp)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}
