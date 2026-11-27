package com.mtwabpin.kolahal.ui.widgets

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.waitFor
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A card's badge on a clock, an agenda, a sky and a picture: on the content's first line where
 * there is one and room for it, and left out where the card has no room or no such line.
 */
@RunWith(AndroidJUnit4::class)
class BadgeOnMoreKindsCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val badge = "live"
    private val nowSeconds = System.currentTimeMillis() / 1000

    /** [spec]'s card in [look], [width] by [height]. */
    private fun show(
        spec: WidgetSpec,
        look: WidgetLook = WidgetLook.PLAIN,
        width: Dp = 320.dp,
        height: Dp = 200.dp,
    ) {
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    CompositionLocalProvider(LocalWidgetLook provides look) {
                        Box(Modifier.width(width)) {
                            val title = spec.base.title ?: "card"
                            WidgetCard(title = title, height = height, spec = spec, note = null, description = title)
                        }
                    }
                }
            }
        }
    }

    private val hasWords = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

    /** Every text the card draws, each as its own. */
    private fun texts(): List<String> = compose.onAllNodes(hasWords, useUnmergedTree = true)
        .fetchSemanticsNodes()
        .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    private fun draws(vararg expected: String) =
        compose.waitFor("the card doesn't draw ${expected.toList()}, it draws ${texts()}") {
            texts().containsAll(expected.toList())
        }

    /** Waits for [shown] to be drawn, then checks the badge is not. */
    private fun noBadge(vararg shown: String) {
        draws(*shown)
        assertFalse("the badge is drawn: ${texts()}", badge in texts())
    }

    private fun clock(withBadge: Boolean = true) = WidgetSpec.Clock(
        WidgetBase(title = "World", badge = badge.takeIf { withBadge }),
        listOf(WidgetSpec.Zone("UTC", "Greenwich")),
    )

    private fun agenda(vararg titles: String) = WidgetSpec.Agenda(
        WidgetBase(title = "Plans", badge = badge),
        titles.mapIndexed { i, title ->
            WidgetSpec.Event(title, starts = nowSeconds + 3600L * (i + 1), ends = nowSeconds + 3600L * (i + 1) + 1800)
        },
        days = 2,
    )

    @Test
    fun aLargeClockHasItsBadgeAtTheEndOfItsTime() {
        show(clock())
        draws("Greenwich", badge)
    }

    @Test
    fun aClockWithoutABadgeDrawsNone() {
        show(clock(withBadge = false))
        noBadge("Greenwich")
    }

    @Test
    fun aSmallClockHasNoRoomForItsBadge() {
        show(clock(), width = 92.dp, height = 92.dp)
        noBadge("Greenwich")
    }

    @Test
    fun anAgendaHasItsBadgeOnTheFirstDaysHeading() {
        show(agenda("Standup", "Lunch"))
        draws("Standup", badge)
    }

    @Test
    fun aBoldAgendaHasItsBadgeInTheRoomOverItsEvents() {
        show(agenda("Standup", "Lunch"), WidgetLook.BOLD)
        draws("Standup", "Lunch", badge)
    }

    @Test
    fun aLowBoldAgendaDropsItsBadgeBeforeItCoversAnEvent() {
        // One 42 dp row in a 68 dp room: 13 dp over it, less than the badge needs.
        show(agenda("Standup"), WidgetLook.BOLD, height = 92.dp)
        noBadge("Standup")
    }

    @Test
    fun theNowLookHasNoNameLineSoNoBadge() {
        show(agenda("Standup"), WidgetLook.NOW, height = 92.dp)
        noBadge("Standup")
    }

    @Test
    fun aSkyHasItsBadgeOnItsTitleLine() {
        show(
            WidgetSpec.Sky(
                WidgetBase(title = "Lisbon", badge = badge),
                rises = nowSeconds - 3 * 3600,
                sets = nowSeconds + 3 * 3600,
            )
        )
        draws("Lisbon", badge)
    }

    @Test
    fun aPictureThatFitsHasItsBadgeAfterItsCaption() {
        show(
            WidgetSpec.Image(
                WidgetBase(title = "Comic", badge = badge),
                image = "https://example.com/badge-picture-${System.nanoTime()}.png",
                alt = "A graph of sleep",
                caption = "Monday",
                fit = "fit",
            )
        )
        draws("Monday", badge)
    }

    @Test
    fun aWidePictureWithNoCaptionLineHasNoBadge() {
        show(
            WidgetSpec.Image(
                WidgetBase(title = "Comic", badge = badge),
                image = "https://example.com/badge-picture-${System.nanoTime()}.png",
                alt = "A graph of sleep",
                caption = "Monday",
                fit = "fit",
            ),
            height = 92.dp,
        )
        noBadge("A graph of sleep")
    }
}
