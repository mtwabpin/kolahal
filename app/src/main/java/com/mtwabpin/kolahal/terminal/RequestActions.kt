package com.mtwabpin.kolahal.terminal

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.mtwabpin.kolahal.contacts.Contact
import com.mtwabpin.kolahal.data.Config
import com.mtwabpin.kolahal.data.CustomThemes
import com.mtwabpin.kolahal.data.Keys
import com.mtwabpin.kolahal.data.LogContext
import com.mtwabpin.kolahal.data.LogKind
import com.mtwabpin.kolahal.data.LogRepository
import com.mtwabpin.kolahal.script.Files
import com.mtwabpin.kolahal.script.ScriptFolder
import com.mtwabpin.kolahal.data.FontFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * What Kolahal asks Android or the user for, and what it does with the answer:
 * - permissions: contacts, notifications, calendar and call log;
 * - notification access, which Kolahal explains first;
 * - pickers: a contact, the script folder, a font, and a theme file to import or export.
 *
 * The view model holds one as `requests`. Its [TerminalUi] calls and result handlers hand over to it.
 */
class RequestActions(private val context: Context, private val host: Host) {
    /** What these actions need from the view model. */
    interface Host {
        /** Runs [block] on the serial command queue. */
        fun io(block: suspend () -> Unit)
        /** Runs [block] outside the command queue. A failure shows as a notice on the GUI page. */
        fun fileOp(label: String, block: suspend () -> Unit)
        /** Shows [text] on the GUI page and logs it. */
        fun notice(text: String)
        fun emit(event: UiEvent)
        /** Runs the setup checks again, because a permission may have changed. */
        fun refreshSetup()
        fun startActivity(intent: Intent)
    }

    private val logs = LogRepository.get(context)
    private val config = Config.get(context)

    private fun io(block: suspend () -> Unit) = host.io(block)
    private fun fileOp(label: String, block: suspend () -> Unit) = host.fileOp(label, block)
    private fun notice(text: String) = host.notice(text)
    private fun refreshSetup() = host.refreshSetup()

    // ---- contacts -------------------------------------------------------------------------

    fun requestContactsPermission() {
        host.emit(UiEvent.RequestContactsPermission)
    }

    /** What to do with the contact being picked. Null once the picker returns (or after the process restarted). */
    @Volatile private var contactPicked: ((Contact) -> Unit)? = null

    fun pickContact(then: (Contact) -> Unit) {
        contactPicked = then
        host.emit(UiEvent.PickContact)
    }

    /**
     * Android's contact picker returned [uri], or null when nothing was picked. The contact's numbers
     * are read now, while the picker allows it, and remembered for the next time the name is typed.
     */
    fun onContactPicked(uri: Uri?) {
        val then = contactPicked
        contactPicked = null
        if (uri == null) return
        io {
            val picked = com.mtwabpin.kolahal.contacts.ContactPicker.read(context, uri)
            val c = picked.firstOrNull() ?: run {
                logs.add(LogKind.ERROR, "That contact has no phone number")
                return@io
            }
            com.mtwabpin.kolahal.contacts.ContactsRepository.get(context).remember(picked)
            if (then != null) then(c) else logs.add(LogKind.OUTPUT, c.name, ctx = PeopleCommands.ctx(c))
        }
    }

    fun onContactPickerMissing() {
        contactPicked = null
        io { logs.add(LogKind.ERROR, "This phone has no contact picker") }
    }

    fun onContactsPermission(granted: Boolean) {
        refreshSetup()
        if (granted) com.mtwabpin.kolahal.contacts.ContactsRepository.get(context).refresh()
        io { logs.add(if (granted) LogKind.INFO else LogKind.ERROR, if (granted) "Contacts allowed" else "Contacts permission denied") }
    }

    // ---- script folder --------------------------------------------------------------------

    fun pickScriptFolder() {
        host.emit(UiEvent.PickScriptFolder)
    }

    fun onScriptFolderPicked(uri: Uri?) {
        if (uri == null) return
        io {
            ScriptFolder(context).setFolder(uri)
            logs.add(LogKind.INFO, "Script folder set — 'script export' / 'script import' to sync")
        }
    }

    // ---- fonts and themes -----------------------------------------------------------------

    fun requestFontImport() {
        host.emit(UiEvent.PickFont)
    }

    /**
     * Copies a picked .ttf/.otf to filesDir/fonts/custom.<ext> (replacing any earlier import),
     * checks that Android can load it, and switches `font` to custom.
     */
    fun importFont(uri: Uri?) {
        if (uri == null) return
        fileOp("font import") {
            val name = FontFiles.importFrom(context, uri)
            fontErrorShown = false
            withContext(Dispatchers.Main) {
                config.set(Keys.FONT, FontFiles.CUSTOM)
            }
            notice("Font = custom (${name ?: "imported font"})")
        }
    }

    /** Settings → Your themes → Import a theme: imports a theme someone shared, saves it with yours, and uses it. */
    fun importTheme(uri: Uri?) {
        if (uri == null) return
        fileOp("theme import") {
            val (name, added) = CustomThemes.import(context, Files.readText(context, uri), Files.displayName(context, uri))
            withContext(Dispatchers.Main) { config.set(Keys.THEME, name) }
            notice(if (added) "Theme $name imported and in use" else "You already have theme $name: it's in use")
        }
    }

    /** The theme builder's Export: writes one of your themes, as last saved, to a file to share. */
    fun exportTheme(name: String, uri: Uri?) {
        if (uri == null) return
        fileOp("theme export") {
            val colors = CustomThemes.colors(name) ?: throw IllegalStateException("$name was deleted")
            Files.writeText(context, uri, CustomThemes.encode(colors, name))
            notice("Exported theme $name")
        }
    }

    @Volatile private var fontErrorShown = false

    /** The configured font could not be loaded (e.g. custom is set but the file is gone). Reported once per run. */
    fun onFontUnavailable(font: String) {
        if (fontErrorShown) return
        fontErrorShown = true
        io {
            logs.add(
                LogKind.ERROR,
                "Font '$font' could not be loaded — using the system monospace · tap to import one",
                ctx = LogContext.of(LogContext.RUN to "font import"),
            )
        }
    }

    // ---- notifications --------------------------------------------------------------------

    fun requestNotificationPermission() {
        host.emit(UiEvent.RequestNotificationPermission)
    }

    fun onNotificationPermission(granted: Boolean) {
        refreshSetup()
        io {
            logs.add(
                if (granted) LogKind.INFO else LogKind.ERROR,
                if (granted) "Notifications allowed" else "Notifications denied — tap to open notification settings",
                ctx = if (granted) LogContext.EMPTY else LogContext.of(LogContext.RUN to "notifications settings"),
            )
        }
    }

    private val _notificationAccessAsk = MutableStateFlow(false)
    /** True while Kolahal asks before Android's Notification access screen opens ([askNotificationAccess]). */
    val notificationAccessAsk: StateFlow<Boolean> = _notificationAccessAsk.asStateFlow()

    fun askNotificationAccess() {
        _notificationAccessAsk.value = true
    }

    /** The user's answer. Allow opens Android's screen. Not now opens nothing and leaves a line to ask again. */
    fun answerNotificationAccess(allow: Boolean) {
        _notificationAccessAsk.value = false
        if (allow) {
            host.startActivity(com.mtwabpin.kolahal.notifications.NotificationActions.settingsIntent(context))
        } else {
            io { logs.add(LogKind.INFO, "Notification access is off · tap to turn it on", ctx = LogContext.of(LogContext.RUN to "notifs enable")) }
        }
    }

    // ---- calendar and call log ------------------------------------------------------------

    fun requestCalendarPermission() {
        host.emit(UiEvent.RequestCalendarPermission)
    }

    fun onCalendarPermission(granted: Boolean) {
        refreshSetup()
        io {
            if (granted) {
                logs.add(LogKind.INFO, "Calendar allowed — tap to see your agenda", ctx = LogContext.of(LogContext.RUN to "agenda"))
            } else {
                logs.add(LogKind.ERROR, "Calendar permission denied — tap to open app settings", ctx = LogContext.of(LogContext.RUN to "agenda --settings"))
            }
        }
    }

    fun requestCallLogPermission() {
        host.emit(UiEvent.RequestCallLogPermission)
    }

    fun onCallLogPermission(granted: Boolean) {
        refreshSetup()
        if (granted) com.mtwabpin.kolahal.calls.CallEvents.get(context).start()
        io {
            if (granted) {
                logs.add(LogKind.INFO, "Call log allowed — new calls appear here; tap to list recent ones", ctx = LogContext.of(LogContext.RUN to "calls"))
            } else {
                logs.add(LogKind.ERROR, "Call log permission denied — tap to open app settings", ctx = LogContext.of(LogContext.RUN to "calls --settings"))
            }
        }
    }
}
