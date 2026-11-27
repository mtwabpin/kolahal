/**
 * Manual Review - Pending
 */
package com.mtwabpin.kolahal.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mtwabpin.kolahal.R
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.data.StatusLine
import com.mtwabpin.kolahal.terminal.ActionPlace
import com.mtwabpin.kolahal.terminal.Scramble
import com.mtwabpin.kolahal.terminal.Suggestion
import com.mtwabpin.kolahal.terminal.tty.screens.DecodeScreen
import com.mtwabpin.kolahal.terminal.tty.screens.LogDecode
import com.mtwabpin.kolahal.ui.ActionMenu
import com.mtwabpin.kolahal.ui.theme.Term
import kotlin.random.Random

/**
 * `scramble` on home, or null while home shows as it is. [TerminalScreen] provides it; the
 * header shows that it is on, and the parts made from it read it.
 */
internal val LocalScramble = compositionLocalOf<Scramble?> { null }

/** Salts of the texts home shows besides the log's lines. */
private const val PINNED = 1L shl 40
private const val MEDIA = 2L shl 40
private const val MENU = 3L shl 40
private const val SUGGESTION = 4L shl 40
private const val FILTER = 5L shl 40

/** The pinned scripts' lines, their texts scrambled. Their names stay: they show nowhere. */
internal fun scrambledPins(lines: List<StatusLine>, s: Scramble): List<StatusLine> =
    lines.mapIndexed { i, line -> line.copy(text = s.text(line.text, PINNED + i)) }

/** What is playing, its words scrambled and without its cover. The player's package stays, for its controls. */
internal fun scrambledMedia(np: NowPlaying, s: Scramble): NowPlaying = np.copy(
    app = s.text(np.app, MEDIA),
    title = np.title?.let { s.text(it, MEDIA + 1) },
    artist = np.artist?.let { s.text(it, MEDIA + 2) },
    cover = null,
)

/**
 * A sheet as it shows while home is scrambled: its title, the line's source and time, its link and
 * its actions scrambled. An action that names nothing of the user's keeps its label
 * ([PLAIN_LABELS]) and its short words ([PLAIN_SHORTS]), so the sheet can still be used; the
 * actions themselves are the line's own. A group's heading that holds the user's words (a script,
 * an app, an address: [ActionPlace.Group.own]) is scrambled too, the same for each of its actions.
 */
internal fun scrambledMenu(menu: ActionMenu, s: Scramble): ActionMenu {
    val keys = menu.actions.mapNotNull { (it.place as? ActionPlace.Group)?.key }.distinct()
    return menu.copy(
        title = s.text(menu.title, MENU),
        detail = menu.detail?.let { s.text(it, MENU + 1) },
        url = menu.url?.let { s.text(it, MENU + 2) },
        actions = menu.actions.mapIndexed { i, a ->
            val place = (a.place as? ActionPlace.Group)?.takeIf { it.own }
                ?.let { it.copy(heading = s.text(it.heading, MENU + 1_000 + keys.indexOf(it.key))) } ?: a.place
            a.copy(
                label = if (a.label in PLAIN_LABELS) a.label else s.text(a.label, MENU + 10 + i),
                short = if (a.short in PLAIN_SHORTS) a.short else s.text(a.short, MENU + 500 + i),
                place = place,
            )
        },
    )
}

/** The sheets' labels that hold nothing of the user's: the same words on every line. */
private val PLAIN_LABELS = setOf(
    "Open activity", "Copy command", "Run again", "Edit in prompt", "Open notification",
    "Mute this app's notifications", "Dismiss notification", "Send a message", "Open contact",
    "Copy number", "Open link", "Share link", "Copy link", "Open image", "Copy output",
    "Delete command and output", "Star line", "Unstar line", "Copy text", "Delete line",
    "Mark done", "Mark not done", "Delete todo", "Delete note", "Cancel reminder", "Open event",
    "App info", "Copy package name", "Pin to favorites", "Unpin from favorites", "Hide from lists",
    "Unhide", "Uninstall", "Mute notifications", "Notify on new items", "Next track",
    "Previous track", "Clear this line", "Fresh from here",
)

/** The words on the chips and under the icons that hold nothing of the user's. */
private val PLAIN_SHORTS = setOf(
    "Copy", "Copy output", "Fresh", "Star", "Unstar", "Only this", "Delete", "Delete with output", "Run again",
    "Edit in prompt", "Open", "Copy command", "App info", "Copy package", "Favorite", "Unfavorite",
    "Hide", "Unhide", "Uninstall", "Personal profile", "Work profile", "Private space",
    "Clone profile", "Mute app", "Dismiss", "Call", "Message", "Contact", "Share", "Run now",
    "Pin to top", "Unpin", "Edit", "Turn off", "Turn on", "Mute", "Notify", "Done", "Not done",
    "Cancel",
) + PLAIN_LABELS

/** The kinds of completion that name something of the user's: a contact, a todo, a note or a file, a reminder, a feed. */
private val OWN_ICONS = setOf(
    R.drawable.ic_person,
    R.drawable.ic_check,
    R.drawable.ic_edit,
    R.drawable.ic_alarm,
    R.drawable.ic_rss,
)

/**
 * [s] as the suggestions show it while home is scrambled: an app, or a completion about an app, a
 * contact, a todo, a note, a file, a reminder or a feed, with its words scrambled. A completion
 * that holds the user's own words ([Suggestion.Completion.ownFrom]: an earlier line, a source of
 * the log) keeps what comes before them, such as its command. Commands, settings and the rest stay
 * readable. Its key stays, so a tap finds the real one.
 */
internal fun scrambledSuggestion(s: Suggestion, index: Int, scramble: Scramble): Suggestion = when (s) {
    is Suggestion.App -> s.copy(app = s.app.copy(label = scramble.text(s.app.label, SUGGESTION + index)))
    is Suggestion.Completion -> {
        val icon = s.icon
        val own = s.ownFrom
        if (own != null) s.copy(
            label = s.label.take(own) + scramble.text(s.label.drop(own), SUGGESTION + index),
            help = s.help?.let { scramble.text(it, SUGGESTION + 1000 + index) },
        ) else if (s.app == null && (icon == null || icon !in OWN_ICONS)) s else s.copy(
            label = scramble.text(s.label, SUGGESTION + index),
            help = s.help?.let { scramble.text(it, SUGGESTION + 1000 + index) },
            app = s.app?.let { it.copy(label = scramble.text(it.label, SUGGESTION + 2000 + index)) },
        )
    }
}

/** What `log only …` shows on its bar, scrambled while home is: it can name an app, a script or a feed. */
internal fun scrambledFilter(label: String, s: Scramble?): String = s?.text(label, FILTER) ?: label

/**
 * A picture of the log while home is scrambled ([Scramble.PICTURE]): a plain block as wide as the
 * log. Nothing is fetched.
 */
@Composable
internal fun ScrambledPicture(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Term.dim.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
            .testTag("scrambled-picture"),
    )
}

/**
 * `decode`'s waves over the scrambled log, in the gui style: a wave runs when the log scrolls and
 * when a line comes ([newest] changes), and home rests in between, drawing nothing. The log's
 * texts draw them ([DecodedText]) over what they show, which is scrambled already, so a wave
 * settles into scrambled text. With animations off there are none.
 */
@Composable
internal fun ScrambleWaves(waves: LogDecode, list: LazyListState, newest: Any?) {
    val owner = remember { Any() }
    val rowPx = with(LocalDensity.current) { Term.lineHeight.sp.roundToPx() }
    var asked by remember { mutableIntStateOf(0) }
    LaunchedEffect(newest) { asked++ }
    LaunchedEffect(list) { snapshotFlow { list.isScrollInProgress }.collect { if (it) asked++ } }
    LaunchedEffect(asked) {
        if (asked == 0 || !android.animation.ValueAnimator.areAnimatorsEnabled()) return@LaunchedEffect
        waves.start(owner, Random.Default, rowPx)
        try {
            val from = withFrameMillis { it }
            var shown = -WAVE_STEP_MS
            while (true) {
                // The wave starts at once, and moves at the decode screen's pace.
                val t = withFrameMillis { it } - from + DecodeScreen.FIRST_MS
                if (t - shown < WAVE_STEP_MS) continue
                shown = t
                waves.frame(t)
                val wave = waves.wave
                if (wave != null && t >= waves.waveAt + wave.lengthMs) break
                if (t > DecodeScreen.FIRST_MS + WAVE_MAX_MS) break
            }
        } finally {
            waves.stop(owner)
        }
    }
}

/** A wave moves a step this often, as the decode screen's 24 frames a second do. */
private const val WAVE_STEP_MS = 41L

/** A wave that never found a line to run over gives up after this long. */
private const val WAVE_MAX_MS = 8_000L
