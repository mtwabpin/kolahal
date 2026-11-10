package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.data.Script

/** Full-screen GUI pages other than the terminal. The script editor is separate. */
enum class Screen { TERMINAL, SETTINGS, SCRIPTS, SCRIPT_LIBRARY, GET_SCRIPTS, SETUP, ONBOARDING, FEEDS, WIDGETS, WIDGET_PICKER, THEME_BUILDER, HEADER_PARTS }

/** Operations that only the UI can do. The processor cannot do them from an application context. */
interface TerminalUi {
    fun requestContactsPermission()
    /** Opens Android's contact picker (used by the Play build, which has no READ_CONTACTS). [then] gets the picked contact, if any. */
    fun pickContact(then: (com.mtwabpin.kolahal.contacts.Contact) -> Unit) {}
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
    fun freshScreen() {}
    /** Asks for READ_CALENDAR, for `agenda`. */
    fun requestCalendarPermission() {}
    /** Asks for READ_CALL_LOG, for calls in the log and for `calls`. */
    fun requestCallLogPermission() {}
    /**
     * For `notifs enable` when access is off. Explains what notification access reads and why,
     * and lets the user choose, before Android's Notification access screen opens.
     */
    fun askNotificationAccess() {}
    /** Sets the lines the log shows: `log only …` ([filter]) or `log all` (null). */
    fun filterLog(filter: LogFilter?) {}
    /** The log's width on screen in px, so output can be laid out to fit. Null until home has been shown. */
    fun logWidthPx(): Int? = null
    /**
     * For `backup dir`: asks for a backup folder.
     * If [thenBackUp] is true, writes a backup once the folder is picked (used by `backup` when no folder is set yet).
     */
    fun askBackupFolder(thenBackUp: Boolean) {}
}

/**
 * One built-in command.
 * - [usage]: its whole usage line. Completion and "Usage:" errors read it.
 * - [help]: what it does, in full. Suggestions and `help <command>` show it.
 * - [short]: what it does, in a few words, for the `help` list.
 * - [args]: its arguments and subcommands, split by " · ", for the muted line under [short]
 *   (`on · off · status`). Empty when it takes none.
 * - [uses]: each way to use it and what that does, for `help <command>`. When empty, that shows [usage].
 */
data class CommandSpec(
    val name: String,
    val usage: String,
    val help: String,
    val short: String,
    val args: String = "",
    val uses: List<Pair<String, String>> = emptyList(),
)
