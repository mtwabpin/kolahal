package com.mtwabpin.kolahal.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.CrashReports
import com.mtwabpin.kolahal.R

// Settings → About: the guide, crash reports and a way to write to the developer.

/** The website, which holds the guide. */
internal const val GUIDE_URL = "https://mtwabpin.github.io/kolahal/"
/** The community's Discord server: questions, ideas and help from other people using Kolahal. */
internal const val DISCORD_URL = "https://discord.gg/3D25w6gXR5"
/** Kolahal's source code on GitHub, where bugs and ideas are reported too. */
internal const val SOURCE_URL = "https://github.com/mtwabpin/kolahal"
internal const val DEVELOPER_EMAIL = "mtwabpin@gmail.com"

/** The page id (in [com.mtwabpin.kolahal.terminal.TerminalViewModel.settingsCategory]) of the crash list. */
internal const val CRASHES_PAGE = "crashes"

/** The page id of About, the links and the version. */
internal const val ABOUT_PAGE = "about"

internal const val VERSION_TEXT = "Kolahal ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

/** A crash report as the list shows it; the full text is read only when it is sent. */
internal class CrashRow(val report: CrashReports.Report, val title: String, val summary: String)

internal fun crashRows(context: Context): List<CrashRow> = CrashReports.list(context).map { r ->
    val title = r.kind.label.replaceFirstChar { it.uppercase() } + " " + CrashReports.whenText(context, r.time)
    val summary = r.summary.take(160) + if (r.inScriptProcess) " (script process)" else ""
    CrashRow(r, title, summary)
}

/** Opens the website's guide; false when no app can open it. */
internal fun openGuide(context: Context): Boolean = openLink(context, GUIDE_URL)

/** Opens [url] in the browser (or the app that takes it); false when no app can open it. */
internal fun openLink(context: Context, url: String): Boolean =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }.isSuccess

/**
 * A draft to [DEVELOPER_EMAIL] in the user's mail app, which they read and send themselves. Without
 * a mail app, any app that can send text; false when there is none.
 */
internal fun emailDeveloper(context: Context, subject: String, body: String): Boolean {
    val mail = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
    return try {
        context.startActivity(mail)
        true
    } catch (_: ActivityNotFoundException) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(DEVELOPER_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
        runCatching { context.startActivity(Intent.createChooser(send, "Send to $DEVELOPER_EMAIL")) }.isSuccess
    }
}

/** The share sheet with a crash report in it: email, a chat, or anywhere that takes text. */
internal fun crashReportShare(report: String): Intent =
    Intent.createChooser(
        Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Kolahal crash report")
            .putExtra(Intent.EXTRA_TEXT, report),
        "Share the crash report",
    )

/** What the developer needs to know about the phone, under a message the user writes. */
internal fun aboutThisPhone(): String =
    "\n\n--\n$VERSION_TEXT · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"

/** A tapped crash report: email it to the developer, share it, or delete it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CrashSheet(crash: CrashRow, onDismiss: () -> Unit, onEmail: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit) {
    val state = rememberExpandedSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
            Text(crash.title, style = MaterialTheme.typography.titleLarge)
            Text(
                crash.summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Emailing or sharing it includes the whole report: the app and Android versions, the phone model and the error. " +
                    "It opens in the other app, and nothing is sent until you send it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(shapes = ButtonDefaults.shapes(), onClick = onEmail) {
                    IconLabel(R.drawable.ic_mail, "Email us")
                }
                OutlinedIconButton(shapes = IconButtonDefaults.shapes(), onClick = onShare) {
                    Icon(painterResource(R.drawable.ic_share), "Share", Modifier.size(ButtonDefaults.IconSize))
                }
                OutlinedIconButton(shapes = IconButtonDefaults.shapes(), onClick = onDelete) {
                    Icon(painterResource(R.drawable.ic_delete), "Delete", Modifier.size(ButtonDefaults.IconSize))
                }
            }
        }
    }
}

/** The crash list page: [crashes] is null while it is being read; [onOpen] is a tap on one. */
internal fun LazyListScope.crashReportsPage(
    crashes: List<CrashRow>?,
    onOpen: (CrashRow) -> Unit,
    onDeleteAll: () -> Unit,
) {
    item(key = "crashes:about") {
        Text(
            "Kolahal keeps its last 10 crash reports on this phone. Tap one to email it to the developer, " +
                "share it or delete it.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
        )
    }
    if (crashes == null) return
    if (crashes.isEmpty()) {
        item(key = "crashes:none") {
            Text(
                "No crash reports. If Kolahal stops unexpectedly, a report is kept here.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }
    item(key = "crashes:list") {
        ActionGroup(crashes.map { c -> Action(R.drawable.ic_bug, c.title, c.summary) { onOpen(c) } })
    }
    item(key = "crashes:delete") {
        Spacer(Modifier.height(16.dp))
        val what = if (crashes.size == 1) "the report" else "the ${crashes.size} reports"
        ActionGroup(listOf(Action(R.drawable.ic_delete, "Delete all", "Remove $what from this phone", onClick = onDeleteAll)))
    }
}
