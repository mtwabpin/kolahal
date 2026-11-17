package com.mtwabpin.kolahal.ui

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.documents.Documents
import com.mtwabpin.kolahal.ui.TextEditorViewModel.Event
import kotlinx.coroutines.launch

/**
 * The text editor: one text file, in a window of its own.
 *
 * Why a window of its own. Home ([com.mtwabpin.kolahal.MainActivity]) is the launcher: it is one
 * task that is cleared when you press Home and is not in Recents, so a screen inside it is lost
 * when you leave. This activity has its own task instead (see the manifest):
 * - Each file is a task of its own, with the file's name in Recents (`documentLaunchMode`). Android's
 *   Recents screen is the list of open files, so the editor needs no tabs.
 * - Home leaves it open. Coming back from Recents, or opening the same file again, shows it as it
 *   was: text, cursor, scroll and undo. (Opening the same file again brings this window to the
 *   front: Android finds the task by the file's address.)
 * - Back and Esc leave the editor the same way, with the file still open. Only the close button closes it.
 *
 * What it is opened with: a `content:` address in the intent's data. It comes from Kolahal's `edit`
 * command, from this editor's own menu, or from another app's "Open with".
 *
 * What it does itself: the file pickers, the window's colours and name, and telling the view model
 * when the window is hidden (the draft is written then) and shown. Everything else is in
 * [TextEditorViewModel] and [TextEditorScreen].
 */
class TextEditorActivity : ComponentActivity() {
    private val vm: TextEditorViewModel by viewModels()
    private val config by lazy { Config.get(this) }

    // Any file may be picked, not only text/*: many apps call a .lua, .toml or .conf file a binary
    // one. The editor itself refuses what isn't text.
    private val openFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { uri -> open(this, uri) } }
    private val newFile = registerForActivityResult(CreateTextFile) { it?.let { uri -> open(this, uri) } }
    private val saveAs = registerForActivityResult(CreateTextFile) { vm.saveAs(it) }
    private val fromFolder = registerForActivityResult(OpenFileIn) { vm.reopened(it, openElsewhere = it != null && openInAnotherWindow(it)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        if (uri == null) {
            finishAndRemoveTask()
            return
        }
        // The editor can be Kolahal's first window in a new process ("Open with", or back from
        // Recents after Android closed it), so it applies the theme itself.
        applyPhoneColours()
        lifecycleScope.launch { config.version.collect { applyTheme() } }
        lifecycleScope.launch { snapshotFlow { vm.name }.collect(::nameTask) }
        lifecycleScope.launch {
            vm.events.collect { event ->
                when (event) {
                    Event.Close -> finishAndRemoveTask()
                    Event.AskSaveAs -> launchPicker { saveAs.launch(vm.name) }
                    is Event.AskFromFolder -> launchPicker { fromFolder.launch(event.folder) }
                    // A window stands for one file. So the other file opens in its own, and this one closes.
                    is Event.Moved -> {
                        open(this@TextEditorActivity, event.uri)
                        finishAndRemoveTask()
                    }
                }
            }
        }
        vm.open(uri)
        setContent {
            KolahalTheme {
                TextEditorScreen(
                    vm,
                    onOpen = { launchPicker { openFile.launch(arrayOf("*/*")) } },
                    onNew = { launchPicker { newFile.launch("untitled.txt") } },
                    // As Home does: the file stays open, and Recents or `edit` brings it back.
                    onLeave = { moveTaskToBack(true) },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        applyTheme()
        vm.onShown()
    }

    override fun onStop() {
        super.onStop()
        vm.onHidden()
    }

    // uiMode and assetsPaths are in configChanges, as for home. So a dark mode change, or a new
    // wallpaper's colours, calls this instead of building the window again.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyPhoneColours()
    }

    /** The material-you theme takes its colours from the wallpaper, which Android reports as a configuration change. */
    private fun applyPhoneColours() {
        MaterialYouPalettes.refresh(this)
        applyTheme()
    }

    /** The app's theme and font, as home applies them, on this window: its background and the colour of the bars' icons. */
    private fun applyTheme() {
        applyTermSettings(this, config, darkTheme(config, resources.configuration))
        window.setBackgroundDrawable(Term.bg.toArgb().toDrawable())
        val light = Term.bg.luminance() > 0.5f
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    /** The window's name in Recents: the file's name, then the editor's. */
    private fun nameTask(file: String) {
        val label = if (file.isEmpty()) getString(R.string.editor_name) else "$file · ${getString(R.string.editor_name)}"
        @Suppress("DEPRECATION") // The builder is from Android 13.
        setTaskDescription(
            if (Build.VERSION.SDK_INT >= 33) ActivityManager.TaskDescription.Builder().setLabel(label).build()
            else ActivityManager.TaskDescription(label),
        )
    }

    /**
     * Whether another editor window has the file at [uri]: Android's Recents has a task for it.
     * That window may have the file open with text of its own, and it doesn't read drafts again.
     */
    private fun openInAnotherWindow(uri: Uri): Boolean = runCatching {
        getSystemService(ActivityManager::class.java).appTasks.any { task ->
            // This window's task is for another address, so it never matches.
            runCatching { task.taskInfo?.baseIntent?.data == uri }.getOrDefault(false)
        }
    }.getOrDefault(false)

    /** A phone without a file picker (a rare stripped-down one) says so instead of stopping. */
    private fun launchPicker(launch: () -> Unit) {
        try {
            launch()
        } catch (_: android.content.ActivityNotFoundException) {
            android.widget.Toast.makeText(this, "This phone has no file picker", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        /**
         * Opens the file at [uri] in the editor, in a window of its own. If the file is open
         * already, its window comes to the front as it is.
         *
         * The permission to the file is kept first, where Android offers that: the caller has it
         * only for as long as its own window lives.
         */
        fun open(context: Context, uri: Uri) {
            Documents.keepPermission(context, uri)
            fun intent(grant: Int) = Intent(Intent.ACTION_EDIT, uri, context, TextEditorActivity::class.java).addFlags(
                Intent.FLAG_ACTIVITY_NEW_DOCUMENT or grant or if (context is android.app.Activity) 0 else Intent.FLAG_ACTIVITY_NEW_TASK,
            )
            try {
                // The new window gets the permission too, in case it could not be kept.
                context.startActivity(intent(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION))
            } catch (_: SecurityException) {
                // Kolahal can't pass on what it doesn't have (a recent file whose permission is
                // gone, or one it may only read). The editor opens and says what it can't do.
                context.startActivity(intent(0))
            }
        }
    }
}

/**
 * Asks where to make a new file, with a suggested name. The file's type comes from the name's
 * ending, so the picker adds no second ending to it (`notes.md.txt`). An ending Android doesn't
 * know gets the type for any file, which adds none either.
 */
object CreateTextFile : ActivityResultContract<String, Uri?>() {
    override fun createIntent(context: Context, input: String): Intent {
        val type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(input.substringAfterLast('.', "").lowercase())
        return Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType(type ?: "application/octet-stream")
            .putExtra(Intent.EXTRA_TITLE, input)
    }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data?.takeIf { resultCode == android.app.Activity.RESULT_OK }
}

/**
 * Asks for a file, with the picker starting in a folder when one is given: to open the same file
 * again from its folder. A file picked there comes from the phone's storage, which Kolahal may
 * write, and not from Recent, which Android may give read-only.
 */
object OpenFileIn : ActivityResultContract<Uri?, Uri?>() {
    override fun createIntent(context: Context, input: Uri?): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .apply { if (input != null) putExtra(android.provider.DocumentsContract.EXTRA_INITIAL_URI, input) }

    override fun parseResult(resultCode: Int, intent: Intent?): Uri? = intent?.data?.takeIf { resultCode == android.app.Activity.RESULT_OK }
}
