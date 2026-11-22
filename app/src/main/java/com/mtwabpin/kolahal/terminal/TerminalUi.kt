/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.commands.LogFilter
import com.mtwabpin.kolahal.data.Script

/** Full-screen GUI pages other than the terminal. The script editor is separate. */
enum class Screen { TERMINAL, SETTINGS, SCRIPTS, SCRIPT_LIBRARY, GET_SCRIPTS, SCRIPT_SOURCES, SHADERS, GET_SHADERS, SETUP, ONBOARDING, FEEDS, WIDGETS, WIDGET_PICKER, THEME_BUILDER, HEADER_PARTS }

/**
 * Operations that only the UI can do. The processor cannot do them from an application context.
 * No call has a default body: a new one must be written in every implementation, test fakes
 * included, rather than silently do nothing there.
 */
interface TerminalUi {
    fun requestContactsPermission()

    /** Opens Android's contact picker (used by the Play build, which has no READ_CONTACTS). [then] gets the picked contact, if any. */
    fun pickContact(then: (com.mtwabpin.kolahal.contacts.Contact) -> Unit)
    fun openScreen(screen: Screen)
    fun pickScriptFolder()

    /** Opens the file picker for `font import` (a .ttf/.otf file). */
    fun requestFontImport()
    fun openEditor(script: Script)

    /** For `script secret <name> <NAME>`: shows a masked box for that secret's value. */
    fun askSecret(script: Script, name: String)
    fun requestNotificationPermission()
    fun clearScreen()

    /** For `fresh`: hides what is on screen now. `log.fresh` does the same after you are away for a while. */
    fun freshScreen()

    /** Asks for READ_CALENDAR, for `agenda`. */
    fun requestCalendarPermission()

    /** Asks for READ_CALL_LOG, for calls in the log and for `calls`. */
    fun requestCallLogPermission()

    /**
     * For `notifs enable` when access is off. Explains what notification access reads and why,
     * and lets the user choose, before Android's Notification access screen opens.
     */
    fun askNotificationAccess()

    /** Sets the lines the log shows: `log only …` ([filter]) or `log all` (null). */
    fun filterLog(filter: LogFilter?)

    /** The log's width on screen in px, so output can be laid out to fit. Null until home has been shown. */
    fun logWidthPx(): Int?

    /**
     * For `backup dir`: asks for a backup folder.
     * If [thenBackUp] is true, writes a backup once the folder is picked (used by `backup` when no folder is set yet).
     */
    fun askBackupFolder(thenBackUp: Boolean)

    /** For `edit`: opens Android's file picker, and then the picked file in the text editor. */
    fun pickTextFile()

    /** For `edit new`: asks where to make a file called [name], and then opens it in the text editor. */
    fun newTextFile(name: String)

    /** For `edit <file>`: opens the file at [uri], a `content:` address, in the text editor. */
    fun openTextFile(uri: String)

    /**
     * A tap on [app] while its profile is paused (the work profile) or locked (the private space):
     * asks whether to resume it and open [app]. A typed `open` writes a line in the log instead.
     */
    fun askResume(app: com.mtwabpin.kolahal.apps.AppEntry)

    /** For `widgets page`: turns home to the widgets page beside the log, and hides the keyboard. */
    fun showWidgetsPage()

    /**
     * For `screen <name>` and `matrix`, `pipes` and `sl`: shows that full-screen screen over home
     * ([com.mtwabpin.kolahal.terminal.tty.screens.TtyScreens]) until a key, a tap or Back.
     */
    fun showTtyScreen(name: String)
}
