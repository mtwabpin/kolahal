package com.mtwabpin.kolahal.ui.widgets

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

/**
 * A progress card's stage details, a line under each stage's name where the names show under the
 * marks; and the badge of a list, a text and a progress card: beside the content where its line has
 * room, never over it, and left out where the drafts leave it out.
 */
@RunWith(AndroidJUnit4::class)
class StageDetailsAndBadgeCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    /** What is drawn: [spec]'s card named [title] in [look], [width] by [height]. */
    private data class Card(
        val spec: WidgetSpec,
        val look: WidgetLook,
        val width: Dp,
        val height: Dp,
        val title: String = "Parcel"
    )

    /** The sizes of a phone 411 dp wide: 4×2, 4×1, 2×2 and 1×1. */
    private val fourByTwo = 395.dp to 192.dp
    private val fourByOne = 395.dp to 92.dp
    private val twoByTwo = 193.dp to 192.dp
    private val oneByOne = 92.dp to 92.dp

    private val parcel = WidgetSpec.Progress(
        WidgetBase(title = "Parcel"), value = 0.5f, label = "By 6 PM",
        stages = listOf("Ordered", "Shipped", "Out for delivery", "Delivered"), stage = 3,
        details = listOf("Mon 09:12", "Tue 18:40", "Today 07:05"),
    )

    private val inbox = WidgetSpec.Rows(
        WidgetBase(title = "Inbox", badge = "12"),
        listOf(
            WidgetSpec.Row("Plans for Saturday", trailing = "09:12"),
            WidgetSpec.Row("Your order has shipped", trailing = "08:40"),
            WidgetSpec.Row("Book club notes", trailing = "Mon"),
        ),
    )

    private val quote = WidgetSpec.Text(WidgetBase(title = "Word", badge = "Sat"), "Petrichor", "Rain on dry earth")

    private var card by mutableStateOf(Card(parcel, WidgetLook.PLAIN, fourByTwo.first, fourByTwo.second))

    @Before
    fun setUp() {
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    val c = card
                    CompositionLocalProvider(LocalWidgetLook provides c.look) {
                        Box(Modifier.width(c.width)) {
                            WidgetCard(
                                title = c.title,
                                height = c.height,
                                spec = c.spec,
                                note = null,
                                description = WidgetWords.description(c.title, c.spec, 0L, ZoneId.systemDefault())
                            )
                        }
                    }
                }
            }
        }
    }

    private fun show(spec: WidgetSpec, look: WidgetLook, size: Pair<Dp, Dp>, title: String = "Parcel") {
        card = Card(spec, look, size.first, size.second, title)
        compose.waitForIdle()
    }

    /** How many times [text] is drawn. */
    private fun drawn(text: String): Int =
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().size

    /** Where [text] is drawn, once. */
    private fun boundsOf(text: String): Rect =
        compose.onAllNodesWithText(text, useUnmergedTree = true).fetchSemanticsNodes().single().boundsInRoot

    /** A 4×2 card has a line under each stage's name, in Plain, Tonal and Split; Bold leaves it out. */
    @Test
    fun detailsShowUnderTheNamesOfALargeCard() {
        for (look in listOf(WidgetLook.PLAIN, WidgetLook.TONAL, WidgetLook.SPLIT)) {
            show(parcel, look, fourByTwo)
            for (detail in parcel.details) assertEquals("$look: $detail", 1, drawn(detail))
            // Under its stage's name.
            assertTrue("$look: under the name", boundsOf("Tue 18:40").top >= boundsOf("Shipped").bottom - 1f)
        }
        show(parcel, WidgetLook.BOLD, fourByTwo)
        assertEquals("Bold", 0, drawn("Tue 18:40"))
    }

    /** A 4×1 is full with the names, and a 2×2 names only the current stage: no details there. */
    @Test
    fun detailsAreLeftOutWhereTheNamesDontShowOrHaveNoRoom() {
        show(parcel, WidgetLook.PLAIN, fourByOne)
        assertEquals(0, drawn("Tue 18:40"))
        show(parcel, WidgetLook.PLAIN, twoByTwo)
        assertEquals(0, drawn("Tue 18:40"))
        // Without details the card draws its names as before.
        show(parcel.copy(details = emptyList()), WidgetLook.PLAIN, fourByTwo)
        assertEquals(1, drawn("Shipped"))
    }

    /** A large list has the badge at the end of its name line, after the name; one without a badge has none. */
    @Test
    fun aListHasItsBadgeOnItsNameLine() {
        for (look in listOf(WidgetLook.PLAIN, WidgetLook.TONAL, WidgetLook.BOLD, WidgetLook.SPLIT)) {
            show(inbox, look, fourByTwo, title = "Inbox")
            assertEquals("$look", 1, drawn("12"))
            val name = boundsOf("Inbox")
            val badge = boundsOf("12")
            assertTrue("$look: after the name", badge.left >= name.right)
            // On the name's line, over the rows.
            assertTrue("$look: on the name line", badge.bottom <= boundsOf("Plans for Saturday").top + 1f)
        }
        show(inbox.copy(base = inbox.base.copy(badge = null)), WidgetLook.PLAIN, fourByTwo, title = "Inbox")
        assertEquals(0, drawn("12"))
    }

    /** The badge gives way: a name that fills its line keeps it, and a 1×1 has no badge. */
    @Test
    fun theBadgeGoesWhereItsLineHasNoRoom() {
        val long = "Messages from everyone in the reading group this week"
        show(inbox, WidgetLook.PLAIN, twoByTwo, title = long)
        assertEquals(0, drawn("12"))
        show(quote, WidgetLook.PLAIN, oneByOne, title = "Word")
        assertEquals(0, drawn("Sat"))
    }

    /** A text and a progress card have the badge too, beside their content, never over it. */
    @Test
    fun aTextAndAProgressCardHaveTheBadge() {
        show(quote, WidgetLook.PLAIN, fourByTwo, title = "Word")
        assertEquals(1, drawn("Sat"))
        // A wide text has no name line: the badge is beside the text's first line.
        show(quote, WidgetLook.PLAIN, fourByOne, title = "Word")
        assertEquals(1, drawn("Sat"))
        assertTrue(boundsOf("Sat").left >= boundsOf("Petrichor").right)
        show(parcel.copy(base = parcel.base.copy(badge = "late")), WidgetLook.PLAIN, fourByTwo)
        assertEquals(1, drawn("late"))
        assertTrue(boundsOf("late").left >= boundsOf("Parcel").right)
    }
}
