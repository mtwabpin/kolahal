/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.terminal

import com.mtwabpin.kolahal.R
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

    /** `edit`: asks for a file, to open in the text editor. */
    data object PickTextFile : UiEvent

    /** `edit new`: asks where to make a file called [name], to open in the text editor. */
    data class NewTextFile(val name: String) : UiEvent

    /** `edit <file>`: opens the file at [uri] in the text editor. */
    data class OpenTextFile(val uri: String) : UiEvent
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
     *
     * [ownFrom]: where the user's own words start in [label] (an earlier note's text, an app named in
     * the log), which `scramble` hides from there on. Null when the label holds none.
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
        val ownFrom: Int? = null,
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
 * One entry of a log line's long-press menu.
 * - [label]: the full words. A screen reader hears them, the TTY sheet shows them, and a sheet
 *   without [place] shows them in a row, with [icon] in a fixed-width column so the labels line up.
 * - [short]: the word or two on its chip, or under its icon in the sheet's row. The group's heading
 *   already names the thing, so it doesn't repeat it: "Run now" for "Run weather now".
 * - [place]: where the GUI sheet puts it ([ActionPlace]). Without one, the sheet is a list of words
 *   (quick actions, the editor's menu).
 */
data class LogAction(
    val label: String,
    val destructive: Boolean = false,
    val icon: ActionIcon? = null,
    val short: String = label,
    val place: ActionPlace? = null,
    val run: () -> Unit
)

/** Where a line's or an app's sheet (ActionSheet) puts an action. */
sealed interface ActionPlace {
    /**
     * In the row of icons at the top, at [slot] (0 to [SLOTS] - 1). A slot is the same on every
     * line, and stays empty when the line hasn't its action, so the thumb learns the row.
     */
    data class Row(val slot: Int) : ActionPlace

    /**
     * Among the chips under [heading], which names the thing the line names: a script, an app, a
     * link. [key] tells two groups apart (two links); [own]: the heading holds the user's words (a
     * name, an address), which `scramble` hides.
     */
    data class Group(val key: String, val heading: String, val own: Boolean = false) : ActionPlace

    companion object {
        const val SLOTS = 5

        /** A line's row: Copy, Fresh (from here), Star, Only this, Delete. */
        val COPY = Row(0)
        val FRESH = Row(1)
        val STAR = Row(2)
        val ONLY = Row(3)
        val DELETE = Row(4)

        /** An app's row, when it is held in a list: Open, App info, Favorite, Hide, Uninstall. */
        val OPEN = Row(0)
        val APP_INFO = Row(1)
        val FAVORITE = Row(2)
        val HIDE = Row(3)
        val UNINSTALL = Row(4)
    }
}

/** A menu icon in the header's style and size. */
sealed interface ActionIcon {
    /**
     * A drawable [id]. It is dim like the header's icons, or colored the way the header colors it:
     * - [profile]: that profile's color;
     * - [alert]: red.
     */
    data class Drawable(val id: Int, val profile: ProfileKind? = null, val alert: Boolean = false) :
        ActionIcon

    /** The header's battery icon, filled to the battery level. */
    data class Battery(val pct: Int, val charging: Boolean) : ActionIcon

    /**
     * An app's shortcut's own picture, which the sheet loads: shortcut [id] of [pkg], in the profile
     * with serial [profile]. It is drawn as it is, not tinted. Until it has loaded, and when there is
     * none, the plain shortcut icon ([NEUTRAL]) shows.
     */
    data class Shortcut(val pkg: String, val id: String, val profile: Long) : ActionIcon {
        companion object {
            val NEUTRAL = R.drawable.ic_shortcut
        }
    }
}
