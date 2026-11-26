/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.extras

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/**
 * Extras' one screen: what it is for, a row for each thing it holds with whether Android gives it and a button that
 * asks, its version, and whether Kolahal is installed with the same key.
 */
class MainActivity : ComponentActivity() {
    /** Bumped when the screen comes back, as from Android's settings: the rows read their state again. */
    private var shown by mutableIntStateOf(0)

    private val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) { shown++ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ExtrasTheme { Screen(shown, ::request) } }
    }

    override fun onResume() {
        super.onResume()
        shown++
    }

    /**
     * Asks Android for [permission]. Once Android has said no twice it no longer asks, so then Extras' page in
     * Android's settings opens instead.
     */
    private fun request(permission: String) {
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val asked = getPreferences(MODE_PRIVATE)
        if (asked.getBoolean(permission, false) && !shouldShowRequestPermissionRationale(permission)) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        } else {
            asked.edit().putBoolean(permission, true).apply()
            ask.launch(permission)
        }
    }
}

/** Rosé Pine, Kolahal's default theme: Dawn by day, the main one at night. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ExtrasTheme(content: @Composable () -> Unit) {
    val scheme = if (isSystemInDarkTheme()) darkColorScheme(
        primary = Color(0xFFEBBCBA), onPrimary = Color(0xFF191724),
        primaryContainer = Color(0xFF403D52), onPrimaryContainer = Color(0xFFE0DEF4),
        background = Color(0xFF191724), onBackground = Color(0xFFE0DEF4),
        surface = Color(0xFF191724), onSurface = Color(0xFFE0DEF4),
        onSurfaceVariant = Color(0xFF908CAA), surfaceContainerHigh = Color(0xFF26233A),
        error = Color(0xFFEB6F92),
    ) else lightColorScheme(
        primary = Color(0xFF9C5E5B), onPrimary = Color(0xFFFAF4ED),
        primaryContainer = Color(0xFFF2E9E1), onPrimaryContainer = Color(0xFF464261),
        background = Color(0xFFFAF4ED), onBackground = Color(0xFF464261),
        surface = Color(0xFFFAF4ED), onSurface = Color(0xFF464261),
        onSurfaceVariant = Color(0xFF6F6B89), surfaceContainerHigh = Color(0xFFF2E9E1),
        error = Color(0xFFAB526C),
    )
    MaterialExpressiveTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive()) {
        Surface(color = MaterialTheme.colorScheme.background, content = content)
    }
}

/** One row: a title, what it is for, whether it is given, and the button that asks. */
private class Row(val title: String, val detail: String, val state: String, val button: String?, val onClick: () -> Unit = {})

@Composable
private fun Screen(shown: Int, request: (String) -> Unit) {
    val context = LocalContext.current
    val rows = remember(shown) {
        fun state(on: Boolean) = if (on) "Allowed" else "Not allowed"
        val contacts = Kolahal.canReadContacts(context)
        val calls = Kolahal.canReadCalls(context)
        val lock = LockService.isEnabled(context)
        val kolahal = when (Kolahal.find(context)) {
            Kolahal.Found.SAME_KEY -> "Installed, with the same key"
            Kolahal.Found.OTHER_KEY -> "Installed with another key: Extras won't answer it. Install both from the same place."
            Kolahal.Found.MISSING -> "Not installed"
        }
        listOf(
            Row("Contacts", "People by name in call, sms and contacts", state(contacts), "Allow".takeIf { !contacts }) {
                request(Manifest.permission.READ_CONTACTS)
            },
            Row("Call log", "Calls in Kolahal's log, and the calls command", state(calls), "Allow".takeIf { !calls }) {
                request(Manifest.permission.READ_CALL_LOG)
            },
            Row(
                "Screen lock",
                "Double-tap and lock in Kolahal. Android lets an app lock the screen only through an accessibility " +
                        "service: this one locks the screen when Kolahal asks, and does nothing else.",
                if (lock) "On" else "Off",
                if (lock) "Turn off" else "Turn on",
            ) { context.startActivity(LockService.settingsIntent()) },
            Row("Version", "${BuildConfig.VERSION_NAME}, interface ${ExtrasContract.VERSION}", "", null),
            Row("Kolahal", kolahal, "", null),
        )
    }
    Column(
        Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Kolahal Extras", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Gives Kolahal from Google Play what Play's rules keep out of it: the screen lock, calls in the log, " +
                    "and people by name.\nIt has no internet access, and answers Kolahal alone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Rows(rows)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun Rows(rows: List<Row>) {
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        rows.forEachIndexed { i, row ->
            SegmentedListItem(
                onClick = row.onClick,
                shapes = shapes(i, rows.size),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                supportingContent = {
                    Column {
                        Text(row.detail)
                        if (row.state.isNotEmpty()) Text(
                            row.state,
                            color = if (row.state == "Allowed" || row.state == "On") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                },
                trailingContent = row.button?.let { label ->
                    { Button(onClick = row.onClick, shapes = ButtonDefaults.shapes()) { Text(label) } }
                },
            ) { Text(row.title, color = MaterialTheme.colorScheme.onSurface) }
        }
    }
}

/** Kolahal's grouped rows: large outer corners on the first and last, small inner ones. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun shapes(index: Int, count: Int) = ListItemDefaults.shapes(
    shape = RoundedCornerShape(
        topStart = if (index == 0) 24.dp else 6.dp,
        topEnd = if (index == 0) 24.dp else 6.dp,
        bottomStart = if (index == count - 1) 24.dp else 6.dp,
        bottomEnd = if (index == count - 1) 24.dp else 6.dp,
    ),
)
