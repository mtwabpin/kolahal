package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.apps.AppEntry
import com.mtwabpin.kolahal.apps.ProfileKind
import com.mtwabpin.kolahal.data.Script

/**
 * One-off requests from the view model to the activity or the screen. The GUI pages ask the
 * activity for its file pickers this way too, so no page needs a callback from the activity.
 */
sealed interface UiEvent {
    data object RequestNotificationPermission : UiEvent
    data object ScrollToBottom : UiEvent
    data object FocusPrompt : UiEvent
    data object HideKeyboard : UiEvent
    data object PickScriptFolder : UiEvent
    data object RequestContactsPermission : UiEvent
    data object PickContact : UiEvent
    data object RequestCalendarPermission : UiEvent
    data object RequestCallLogPermission : UiEvent
    data object PickFont : UiEvent
    data object PickBackupFolder : UiEvent
    data object ShowQuick : UiEvent
    /** Settings → Backup → Export everything: asks where to save the backup file. */
    data object ExportBackup : UiEvent
    /** Settings → Backup → Import a backup: asks for the file. */
    data object ImportBackup : UiEvent
    /** Scripts → Add script → Import a file: asks for the script files. */
    data object ImportScripts : UiEvent
    /** Export, for one script: asks where to save [script]. */
    data class ExportScript(val script: Script) : UiEvent
}

/** Something the user can tap in the suggestion strip. Immutable, as each kind of it is. */
@androidx.compose.runtime.Immutable
sealed interface Suggestion {
    val label: String

    /** Identifies the row in the suggestion lists. [TerminalViewModel.suggestions] never returns two rows with the same key. */
    val key: String

    /** True when Enter does what this row does. Only the first row can have it. That row is marked with a ⏎. */
    val enter: Boolean

    // Immutable: a row whose suggestion is equal to the last one is skipped as you type.
    @androidx.compose.runtime.Immutable
    data class App(val app: AppEntry, override val enter: Boolean = false) : Suggestion {
        override val label get() = app.label
        override val key get() = "a:" + app.key
    }

    /**
     * The row's icon is chosen in this order:
     * - [app]: the app this completion is about (`uninstall maps`, `open maps:…`). Its icon is shown.
     * - [icon]: a drawable for the kind of thing it is (a feed, a script, a contact).
     * - Otherwise, the terminal glyph.
     *
     * [help] is shown beside the row when the command and config descriptions don't apply.
     */
    @androidx.compose.runtime.Immutable
    data class Completion(
        override val label: String,
        val replacement: String,
        val app: AppEntry? = null,
        val icon: Int? = null,
        val help: String? = null,
        override val enter: Boolean = false,
        /** The calculator's answer while you type: (the value, what goes under it), drawn large as in the log. */
        val big: Pair<String, String>? = null,
    ) : Suggestion {
        override val key get() = "c:" + replacement.trimEnd()
    }
}

/**
 * What the Undo bar under the log offers.
 * - [text] says what was done.
 * - [undo] reverses it.
 * - [expire] runs when undo is no longer possible: the offer timed out, or another offer replaced it.
 */
class UndoOffer(val text: String, val expire: () -> Unit = {}, val undo: () -> Unit)

/**
 * One entry of a log line's long-press menu. [icon] is drawn in a fixed-width column,
 * so the labels line up whatever the icon's width.
 */
data class LogAction(val label: String, val destructive: Boolean = false, val icon: ActionIcon? = null, val run: () -> Unit)

/** A menu icon in the header's style and size. */
sealed interface ActionIcon {
    /**
     * A drawable [id]. It is dim like the header's icons, or coloured the way the header colours it:
     * - [profile]: that profile's colour;
     * - [alert]: red.
     */
    data class Drawable(val id: Int, val profile: ProfileKind? = null, val alert: Boolean = false) : ActionIcon

    /** The header's battery icon, filled to the battery level. */
    data class Battery(val pct: Int, val charging: Boolean) : ActionIcon
}
