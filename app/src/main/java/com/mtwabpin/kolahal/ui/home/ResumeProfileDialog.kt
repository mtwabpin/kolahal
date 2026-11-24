/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.ui.DialogBars
import com.mtwabpin.kolahal.ui.theme.GuiTheme

/**
 * Asks whether to resume [app]'s profile and open [app], after a tap on an app whose work profile
 * is paused or whose private space is locked ([com.mtwabpin.kolahal.terminal.TerminalUi.askResume]).
 * It shows over any screen, so a tap in the app selector can't go unseen, as a line in the log could.
 * - The work profile: Resume turns work apps on, then Kolahal opens [app].
 * - The private space: Unlock asks Android to unlock it, and Android asks for your lock.
 * - Cancel, Back and a tap outside leave the profile as it is.
 */
@Composable
fun ResumeProfileDialog(app: AppEntry, onResume: () -> Unit, onDismiss: () -> Unit) {
    val private = app.profile.kind == ProfileKind.PRIVATE
    GuiTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            modifier = Modifier.testTag("resume-profile-dialog"),
            icon = {
                Icon(
                    painterResource(if (private) R.drawable.ic_lock else R.drawable.ic_work),
                    null
                )
            },
            title = { DialogBars(); Text(if (private) "Unlock the private space?" else "Resume work apps?") },
            text = {
                Text(
                    if (private) "${app.label} is in your private space, which is locked. Android asks for your lock, then Kolahal opens ${app.label}."
                    else "${app.label} is a work app, and work apps are paused. Kolahal turns them on, then opens ${app.label}.",
                )
            },
            confirmButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onResume
                ) { Text(if (private) "Unlock" else "Resume") }
            },
            dismissButton = {
                TextButton(
                    shapes = ButtonDefaults.shapes(),
                    onClick = onDismiss
                ) { Text("Cancel") }
            },
        )
    }
}
