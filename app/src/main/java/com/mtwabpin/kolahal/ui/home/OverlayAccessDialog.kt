/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.ui.DialogBars
import com.mtwabpin.kolahal.ui.theme.GuiTheme

/**
 * Shown before Android's Display over other apps page opens: by the Shaders tab's Play over other apps, and by
 * `allow overlay` when the access isn't given. It says what is drawn over the app in front, when, that touches
 * go through, and that nothing of that app is read. In the form of [NotificationAccessDialog]:
 * - It shows inside the app, before the system page.
 * - Only Allow continues to the system page.
 * - Not now opens nothing; the switch stays as it was, and the app can ask again later.
 */
@Composable
fun OverlayAccessDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    GuiTheme {
        val colors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag("overlay-access-dialog"),
            icon = { Icon(painterResource(R.drawable.ic_shader), null) },
            title = { DialogBars(); Text("Let Kolahal display over other apps?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(OVERLAY_ACCESS_WHAT)
                    Text(OVERLAY_ACCESS_POINTS)
                    Text(OVERLAY_ACCESS_DATA)
                    Text(
                        "Android's Display over other apps page opens next: find Kolahal there and turn it on.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onAllow
                ) { Text("Allow") }
            },
            dismissButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onDismiss
                ) { Text("Not now") }
            },
        )
    }
}

/** What the access is for, the dialog's first paragraph. */
internal const val OVERLAY_ACCESS_WHAT =
    "With this access, a one-off shader can play over the app you are using when a notification has one of its words."

/** What is drawn and when. */
internal const val OVERLAY_ACCESS_POINTS =
    "• It plays for a few seconds, 10 at most, then its window closes.\n" +
            "• Touches go through it to the app, which works as before.\n" +
            "• Kolahal reads nothing of that app, and draws no text or buttons over it.\n" +
            "• It doesn't play on the lock screen or over a full-screen app."

/** What is read, and how to turn it off. */
internal const val OVERLAY_ACCESS_DATA =
    "Notifications are read on this phone to find the words, and sent nowhere. " +
            "You can turn this off at any time, on the Shaders tab or in Android's settings."
