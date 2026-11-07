package com.mtwabpin.kolahal.ui

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

/**
 * Shown by `notifs enable` when notification access is off, before Android's Notification access
 * screen opens. It explains what Kolahal reads, why, and that the data stays on the phone.
 * This is Google Play's prominent disclosure:
 * - It shows inside the app, before the system screen.
 * - Only Allow continues to the system screen.
 * - Not now opens nothing, and the app can ask again later.
 */
@Composable
fun NotificationAccessDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    GuiTheme {
        val colors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag("notification-access-dialog"),
            icon = { Icon(painterResource(R.drawable.ic_notifications), null) },
            title = { Text("Let Kolahal read your notifications?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("With notification access, Kolahal reads the notifications your apps post: which app, the title and the text.")
                    Text(
                        "• They show in the log as they arrive, and with notifs.\n" +
                            "• Media controls see what's playing.\n" +
                            "• A script gets them only if you allow it, with @uses notifications.",
                    )
                    Text("They stay on this phone, with the rest of the log: Kolahal sends them nowhere. You can turn access off at any time in Android's settings.")
                    Text(
                        "Android's Notification access screen opens next: turn Kolahal on there.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onAllow) { Text("Allow") } },
            dismissButton = { TextButton(shapes = ButtonDefaults.shapes(), onClick = onDismiss) { Text("Not now") } },
        )
    }
}
