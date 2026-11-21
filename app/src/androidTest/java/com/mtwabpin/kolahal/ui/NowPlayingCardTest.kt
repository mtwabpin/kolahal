package com.mtwabpin.kolahal.ui

import android.graphics.Bitmap
import android.media.session.PlaybackState
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.control.LastPlayer
import com.mtwabpin.kolahal.control.NowPlaying
import com.mtwabpin.kolahal.terminal.AgendaRead
import com.mtwabpin.kolahal.terminal.OwnFeed
import com.mtwabpin.kolahal.terminal.WidgetCardActions
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.waitForNode
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSource
import com.mtwabpin.kolahal.widgets.WidgetTap
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The built-in Now playing card, drawn from a feed of the test's own: no media session plays. Its
 * cover, its bar that moves the track, and what it offers with nothing playing.
 */
@RunWith(AndroidJUnit4::class)
class NowPlayingCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** What the card hands its actions, in order: `media resume`, `arrange`. */
    private class Asked : WidgetCardActions {
        val all = CopyOnWriteArrayList<String>()
        override fun refresh(widget: String) {}
        override fun tap(widget: String, tap: WidgetTap) {}
        override fun openBattery() {}
        override fun openScripts() {}
        override fun longPress() {
            all += "arrange"
        }

        override fun own(id: String, what: String) {
            all += "$id $what"
        }
    }

    private val playing = MutableStateFlow<NowPlaying?>(null)
    private val last = MutableStateFlow<LastPlayer?>(null)
    private val allowed = MutableStateFlow(true)

    private val feed = OwnFeed(
        todos = MutableStateFlow(null),
        notes = MutableStateFlow(null),
        reminders = MutableStateFlow(null),
        shortcuts = MutableStateFlow(null),
        playing = playing,
        agenda = MutableStateFlow(AgendaRead.NOT_READ),
        mediaAllowed = allowed,
        lastPlayer = last,
    )

    /** A paused track, 1:12 into its 3:05, from a player that can seek or can't. */
    private fun track(canSeek: Boolean, cover: Bitmap? = null) = NowPlaying(
        pkg = "com.example.music",
        app = "Music",
        title = "Gymnopédie No. 1",
        artist = "Erik Satie",
        state = PlaybackState.STATE_PAUSED,
        position = 72_000,
        positionAt = System.currentTimeMillis(),
        speed = 1f,
        duration = 185_000,
        idleSince = 0,
        canSeek = canSeek,
        cover = cover,
    )

    private val clairDeLune =
        LastPlayer("com.example.music", "Music", "Clair de lune", "Claude Debussy")

    /** Album art as a player sends it, at the card's size. */
    private fun art(): Bitmap = Bitmap.createBitmap(168, 168, Bitmap.Config.ARGB_8888)
        .apply { eraseColor(android.graphics.Color.rgb(200, 120, 40)) }

    /** Whose actions the card has: null on the Widgets screen, where it takes no touches. */
    private var actions by mutableStateOf<WidgetCardActions?>(null)
    private var look by mutableStateOf(WidgetLook.PLAIN)

    /** The card's width and height: 4×2 on a page 395 dp wide. */
    private var size by mutableStateOf(395.dp to 192.dp)

    /** The card, with [actions], in [look], [size]. */
    private fun show(taps: WidgetCardActions?) {
        actions = taps
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    CompositionLocalProvider(LocalWidgetLook provides look) {
                        Box(Modifier.width(size.first)) {
                            OwnWidget(
                                WidgetSource.MEDIA,
                                size.second,
                                actions,
                                feed
                            )
                        }
                    }
                }
            }
        }
    }

    private val inCard =
        hasAnyAncestor(hasTestTag("widget:${WidgetSource.builtin(WidgetSource.MEDIA)}"))
    private val seekBar = inCard and SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress)

    private fun hasClickLabel(label: String) =
        SemanticsMatcher("click label \"$label\"") {
            it.config.getOrNull(SemanticsActions.OnClick)
                ?.let { click -> click.label == label } == true
        }

    private fun says(state: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, state)

    private fun covers(): Int =
        compose.onAllNodes(hasTestTag("media-cover"), useUnmergedTree = true)
            .fetchSemanticsNodes().size

    /**
     * With nothing playing, the card offers the player that played last: "Last played", its track,
     * and one Play, which hands `resume` over. A session with a track takes the card back. With no
     * player ever, it says "Nothing playing", and without notification access it asks for that.
     */
    @Test
    fun anIdleCardWithARememberedPlayerShowsPlay() {
        val asked = Asked()
        last.value = clairDeLune
        show(asked)
        compose.waitForNode(inCard and hasText("Last played"))
        compose.onNode(inCard and hasText("Clair de lune")).assertExists()
        compose.onNode(inCard and hasText("Claude Debussy · Music")).assertExists()
        // Play alone, with its word.
        compose.onAllNodes(inCard and hasContentDescription("Previous")).assertCountEquals(0)
        compose.onAllNodes(inCard and hasContentDescription("Next")).assertCountEquals(0)
        compose.onNode(inCard and hasText("Play") and hasClickAction()).performClick()
        compose.onNode(inCard and hasClickLabel("open Music"))
            .assertContentDescriptionEquals("Now playing: Clair de lune, Claude Debussy, Music, last played")
        compose.runOnIdle { assertEquals(listOf("media resume"), asked.all.toList()) }

        playing.value = track(canSeek = false)
        compose.waitForNode(inCard and hasContentDescription("Previous"))
        compose.onAllNodes(inCard and hasText("Last played")).assertCountEquals(0)

        playing.value = null
        last.value = null
        compose.waitForNode(inCard and hasText("Nothing playing"))
        compose.onAllNodes(inCard and hasText("Play")).assertCountEquals(0)

        last.value = clairDeLune
        allowed.value = false
        compose.waitForNode(inCard and hasText("Allow notification access"))
        compose.onAllNodes(inCard and hasText("Last played")).assertCountEquals(0)
    }

    /**
     * A player that can seek gets a bar that moves the track: a slider of its own inside the card,
     * which says where the track is and hands `seek <ms>` over. It keeps the place until the player
     * answers. A player that can't seek keeps the bar that only shows the position.
     */
    @Test
    fun theBarMovesTheTrackOnlyWhenThePlayerCanSeek() {
        val asked = Asked()
        playing.value = track(canSeek = true)
        show(asked)
        compose.waitForNode(seekBar)
        compose.onNode(seekBar).assert(says("1:12 of 3:05"))
        // The card itself is no slider.
        compose.onNode(inCard and hasClickLabel("open Music"))
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetProgress))
        // A screen reader moves it half way: 1:32.5.
        compose.onNode(seekBar).performSemanticsAction(SemanticsActions.SetProgress) { it(0.5f) }
        compose.runOnIdle { assertEquals(listOf("media seek 92500"), asked.all.toList()) }
        compose.onNode(seekBar).assert(says("1:32 of 3:05"))
        compose.onNode(inCard and hasText("1:32")).assertExists()

        playing.value = track(canSeek = false)
        compose.waitFor("the bar is still a slider") {
            compose.onAllNodes(seekBar).fetchSemanticsNodes().isEmpty()
        }
        compose.onNode(inCard and hasText("1:12")).assertExists()
        // On the Widgets screen, where the card takes no touches, there is no slider either.
        compose.runOnIdle {
            playing.value = track(canSeek = true)
            actions = null
        }
        compose.waitForNode(inCard and hasText("Gymnopédie No. 1"))
        compose.onAllNodes(seekBar).assertCountEquals(0)
    }

    /** A drag that starts on the bar moves the track, and the page stays. One that starts beside it turns the page. */
    @Test
    fun aDragOnTheBarIsNotThePagesSwipe() {
        val asked = Asked()
        playing.value = track(canSeek = true)
        lateinit var pager: PagerState
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    pager = rememberPagerState { 2 }
                    HorizontalPager(pager, Modifier.width(395.dp)) { page ->
                        if (page == 0) OwnWidget(WidgetSource.MEDIA, 192.dp, asked, feed) else Box(
                            Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
        compose.waitForNode(seekBar)
        compose.onNode(seekBar).performTouchInput {
            swipe(
                Offset(width * 0.2f, centerY),
                Offset(width * 0.7f, centerY),
                durationMillis = 300
            )
        }
        compose.runOnIdle {
            assertEquals(0, pager.currentPage)
            val seeks = asked.all.filter { it.startsWith("media seek ") }
            assertEquals(asked.all.toString(), 1, seeks.size)
            // About seven tenths of 3:05.
            val ms = seeks.single().removePrefix("media seek ").toLong()
            assertTrue("seek to $ms", ms in 110_000L..145_000L)
        }
        // Near the card's top edge there is nothing of its own: the drag is the page's.
        compose.onNode(inCard and hasClickLabel("open Music")).performTouchInput {
            swipe(
                Offset(width * 0.8f, 6.dp.toPx()),
                Offset(width * 0.1f, 6.dp.toPx()),
                durationMillis = 200
            )
        }
        compose.runOnIdle { assertEquals(1, pager.currentPage) }
    }

    /**
     * The player's art is the face of play or pause, under its icon. Without art the button keeps its
     * colour. The card has no title line: the track says what it is.
     */
    @Test
    fun theArtFillsPlayOrPause() {
        playing.value = track(canSeek = true)
        show(Asked())
        compose.waitForNode(inCard and hasText("Gymnopédie No. 1"))
        assertEquals(0, covers())
        compose.onAllNodes(inCard and hasText("Now playing")).assertCountEquals(0)

        playing.value = track(canSeek = true, cover = art())
        compose.waitFor("no cover is drawn") { covers() == 1 }
        // The track is paused: the art is under Play's icon.
        compose.onNode(
            hasTestTag("media-cover") and hasAnyDescendant(hasContentDescription("Play")),
            useUnmergedTree = true
        ).assertExists()
        compose.onNode(inCard and hasText("Gymnopédie No. 1")).assertExists()
        compose.onNode(seekBar).assert(says("1:12 of 3:05"))
    }

    /** The bars the card draws with nothing to seek: [MediaBar]'s plain bar. */
    private fun bars(): Int = compose.onAllNodes(hasTestTag("media-bar"), useUnmergedTree = true)
        .fetchSemanticsNodes().size

    /**
     * Each look of the card, at each size, draws the track and its Play, with the art in Play, and
     * with nothing playing. Wherever it draws the bar, the bar seeks for a player that can: one row
     * has the slim slider, a taller card the 48 dp one.
     */
    @Test
    fun everyLookDrawsTheNewParts() {
        show(Asked())
        val art = art()
        for (l in listOf(WidgetLook.PLAIN, WidgetLook.TONAL, WidgetLook.SPLIT, WidgetLook.SHAPE)) {
            for (s in listOf(
                395.dp to 192.dp,
                395.dp to 120.dp,
                395.dp to 92.dp,
                193.dp to 92.dp
            )) {
                val where = "${l.id}, ${s.first} by ${s.second}"
                // A player that can't seek: the bars this look draws at this size.
                compose.runOnIdle {
                    look = l
                    size = s
                    last.value = null
                    playing.value = track(canSeek = false)
                }
                compose.waitForNode(inCard and hasText("Gymnopédie No. 1"))
                compose.waitForIdle()
                val drawn = bars()
                if (s.second > 100.dp) assertEquals(where, 1, drawn)

                compose.runOnIdle { playing.value = track(canSeek = true, cover = art) }
                compose.waitFor("$where: no art is drawn") { covers() == 1 }
                compose.onAllNodes(inCard and hasContentDescription("Play")).assertCountEquals(1)
                // Each of those bars now seeks, and says where the track is.
                assertEquals(where, drawn, compose.onAllNodes(seekBar).fetchSemanticsNodes().size)
                assertEquals(where, 0, bars())
                if (drawn > 0) compose.onNode(seekBar).assert(says("1:12 of 3:05"))

                compose.runOnIdle {
                    playing.value = null
                    last.value = clairDeLune
                }
                compose.waitForNode(inCard and hasText("Clair de lune"))
                compose.onAllNodes(seekBar).assertCountEquals(0)
                assertEquals(
                    where,
                    1,
                    compose.onAllNodes(inCard and (hasContentDescription("Play") or hasText("Play")))
                        .fetchSemanticsNodes().size
                )
            }
        }
    }
}
