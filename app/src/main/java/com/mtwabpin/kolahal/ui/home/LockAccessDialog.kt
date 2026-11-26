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
 * Shown by `allow lock` while the screen lock's accessibility service is off, before Android's accessibility settings
 * open ([com.mtwabpin.kolahal.gesture.LockAccess]). It says what the service is used for and what it doesn't touch.
 * This is Google Play's prominent disclosure for an accessibility service, in the form of [NotificationAccessDialog]:
 * - It shows inside the app, before the system screen.
 * - Only Allow continues to the system screen.
 * - Not now opens nothing; the lock still says it needs the service, and the app can ask again later.
 */
@Composable
fun LockAccessDialog(onAllow: () -> Unit, onDismiss: () -> Unit) {
    GuiTheme {
        val colors = MaterialTheme.colorScheme
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag("lock-access-dialog"),
            icon = { Icon(painterResource(R.drawable.ic_lock), null) },
            title = { DialogBars(); Text(LOCK_ACCESS_TITLE) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(LOCK_ACCESS_WHAT)
                    Text(LOCK_ACCESS_POINTS)
                    Text(
                        LOCK_ACCESS_NEXT,
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

internal const val LOCK_ACCESS_TITLE = "Let Kolahal lock the screen?"

/** What the service is for, and why it is an accessibility service. */
internal const val LOCK_ACCESS_WHAT =
    "Kolahal uses Android's accessibility service only to lock the screen when you ask: with the lock command, " +
            "or the gesture you set to lock, such as a double-tap on empty space. " +
            "Android lets an app lock the screen only this way."

/** What it doesn't touch. */
internal const val LOCK_ACCESS_POINTS =
    "• It doesn't read the screen, what you type or other apps.\n" +
            "• It collects nothing and sends nothing.\n" +
            "• It stays off until you turn it on. You can turn it off at any time in Android's settings."

/** The next screen is Android's. */
internal const val LOCK_ACCESS_NEXT =
    "Android's Accessibility settings open next: find Kolahal screen lock there and turn it on."
