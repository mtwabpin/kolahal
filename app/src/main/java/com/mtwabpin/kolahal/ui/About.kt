package com.mtwabpin.kolahal.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.mtwabpin.kolahal.BuildConfig
import com.mtwabpin.kolahal.CrashReports
import com.mtwabpin.kolahal.Links
import com.mtwabpin.kolahal.R

// Settings → About: the guide, crash reports and a way to contact the developer.

/** The page id (in [com.mtwabpin.kolahal.terminal.TerminalViewModel.settingsCategory]) of the crash list. */
internal const val CRASHES_PAGE = "crashes"

/** The page id of About, which shows the links and the version. */
internal const val ABOUT_PAGE = "about"

internal const val VERSION_TEXT = "Kolahal ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"

/** A crash report as the list shows it. The full text is read only when the report is sent. */
internal class CrashRow(val report: CrashReports.Report, val title: String, val summary: String)

internal fun crashRows(context: Context): List<CrashRow> = CrashReports.list(context).map { r ->
    val title = r.kind.label.replaceFirstChar { it.uppercase() } + " " + CrashReports.whenText(context, r.time)
    val summary = r.summary.take(160) + if (r.inScriptProcess) " (script process)" else ""
    CrashRow(r, title, summary)
}

/** Opens the guide on the website. Returns false if no app can open it. */
internal fun openGuide(context: Context): Boolean = openLink(context, Links.GUIDE)

/** Opens [url] in the browser (or in the app that handles it). Returns false if no app can open it. */
internal fun openLink(context: Context, url: String): Boolean =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }.isSuccess

/**
 * Opens a draft to [Links.DEVELOPER_EMAIL] in the user's mail app. The user reads and sends it themselves.
 * Without a mail app, it offers any app that can send text. Returns false if there is none.
 */
internal fun emailDeveloper(context: Context, subject: String, body: String): Boolean {
    val mail = Intent(Intent.ACTION_SENDTO, "mailto:".toUri())
        .putExtra(Intent.EXTRA_EMAIL, arrayOf(Links.DEVELOPER_EMAIL))
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, body)
    return try {
        context.startActivity(mail)
        true
    } catch (_: ActivityNotFoundException) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_EMAIL, arrayOf(Links.DEVELOPER_EMAIL))
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, body)
        runCatching { context.startActivity(Intent.createChooser(send, "Send to ${Links.DEVELOPER_EMAIL}")) }.isSuccess
    }
}

/** Phone details the developer needs. They go below the message the user writes. */
internal fun aboutThisPhone(): String =
    "\n\n--\n$VERSION_TEXT · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}"

/**
 * The About page's rows: the guide, the tour, Discord, the source code, crash reports, email and
 * the version.
 * - [crashCount]: how many crash reports are kept. Null while they are read.
 * - [onTour] and [onCrashReports] open those pages.
 * - [say] shows a message, when no app on the phone can open a link or send email.
 */
internal fun LazyListScope.aboutItems(crashCount: Int?, onTour: () -> Unit, onCrashReports: () -> Unit, say: (String) -> Unit) {
    item(key = "h:about") { Spacer(Modifier.height(8.dp)) }
    item(key = "about") {
        val context = LocalContext.current
        val kept = when (crashCount) {
            null -> "Reports kept on this phone"
            0 -> "None so far"
            1 -> "1 report kept on this phone"
            else -> "$crashCount reports kept on this phone"
        }
        ActionGroup(
            listOf(
                Action(R.drawable.ic_book, "Guide", "How everything works, on the Kolahal website") {
                    if (!openGuide(context)) say("No browser to open ${Links.GUIDE}")
                },
                Action(R.drawable.ic_info, "Tour", "How Kolahal works, and a few initial settings", chevron = true, onClick = onTour),
                Action(R.drawable.ic_forum, "Discord", "Questions, ideas and help from other people using Kolahal") {
                    if (!openLink(context, Links.DISCORD)) say("No app to open ${Links.DISCORD}")
                },
                Action(R.drawable.ic_code, "Source code", "Kolahal on GitHub") {
                    if (!openLink(context, Links.SOURCE)) say("No browser to open ${Links.SOURCE}")
                },
                Action(R.drawable.ic_bug, "Crash reports", kept, chevron = true, onClick = onCrashReports),
                Action(R.drawable.ic_mail, "Email us", Links.DEVELOPER_EMAIL) {
                    if (!emailDeveloper(context, VERSION_TEXT, aboutThisPhone())) say("No app on this phone can send email")
                },
                Action(R.drawable.ic_info, "Version", "$VERSION_TEXT · tap to copy") {
                    context.getSystemService(android.content.ClipboardManager::class.java)
                        ?.setPrimaryClip(android.content.ClipData.newPlainText("Kolahal version", VERSION_TEXT))
                    // Android 13 and later show their own "copied" confirmation.
                    if (Build.VERSION.SDK_INT < 33) say("Copied")
                },
            )
        )
    }
}

/**
 * The sheet of a tapped crash report: email it to the developer, share it, or delete it.
 * - It stays open after Email and Share, so the user can delete the report after sending it.
 * - Delete closes it ([onDismiss]) and deletes the report. Then [onDeleted] runs, for the page to
 *   read its list again.
 * - [say] shows a message: no app can send the report, or it was deleted.
 */
@Composable
internal fun CrashSheet(crash: CrashRow, onDismiss: () -> Unit, onDeleted: () -> Unit, say: (String) -> Unit) {
    val context = LocalContext.current
    val onEmail = {
        if (!emailDeveloper(context, "$VERSION_TEXT: ${crash.title}", crash.report.text())) say("No app on this phone can send email")
    }
    val onShare = {
        if (runCatching { context.startActivity(CrashReports.shareIntent(crash.report.text())) }.isFailure) say("No app on this phone can share it")
    }
    val onDelete = {
        onDismiss()
        crash.report.delete()
        onDeleted()
        say("Crash report deleted")
    }
    GuiSheet(onDismiss) {
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

/** The crash list page. [crashes] is null while the list loads. [onOpen] runs when the user taps a report. */
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
