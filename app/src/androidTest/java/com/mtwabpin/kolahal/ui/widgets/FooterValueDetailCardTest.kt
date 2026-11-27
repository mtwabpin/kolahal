package com.mtwabpin.kolahal.ui.widgets

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.ui.theme.GuiTheme
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.widgets.WidgetBase
import com.mtwabpin.kolahal.widgets.WidgetLook
import com.mtwabpin.kolahal.widgets.WidgetSpec
import com.mtwabpin.kolahal.widgets.WidgetTap
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Three small fields as a card draws them: the script's footer on the one foot line, a button's or
 * a switch's value where a switch says "On" or "Off", and a versus side's detail under its name.
 * Each shows where the drafts put it and is left out where there is no room. Sizes as on a phone
 * 411 dp wide: 2×1 is 196 by 94, 4×1 403 by 94, 2×2 196 by 196, 4×2 403 by 196.
 */
@RunWith(AndroidJUnit4::class)
class FooterValueDetailCardTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val synced = "From the shared list in Notes, synced every hour"

    /** One card as [show] draws it. */
    private data class Shown(val spec: WidgetSpec, val width: Dp, val height: Dp, val look: WidgetLook, val age: WidgetWords.Footer?)

    private var shown by mutableStateOf<Shown?>(null)
    private var composed = false

    /**
     * [spec]'s card, [width] by [height], in [look], with [age] as its line about the content. The
     * content is set once in a test; a later call draws another card in its place.
     */
    private fun show(
        spec: WidgetSpec,
        width: Dp,
        height: Dp,
        look: WidgetLook = WidgetLook.PLAIN,
        age: WidgetWords.Footer? = null,
    ) {
        val card = Shown(spec, width, height, look, age)
        if (composed) {
            compose.runOnIdle { shown = card }
            compose.waitForIdle()
            return
        }
        shown = card
        composed = true
        compose.setContent {
            KolahalTheme {
                GuiTheme {
                    shown?.let { card ->
                        CompositionLocalProvider(LocalWidgetLook provides card.look) {
                            Box(Modifier.width(card.width)) {
                                WidgetCard(
                                    title = card.spec.base.title ?: "card",
                                    height = card.height,
                                    spec = card.spec,
                                    note = null,
                                    description = "card",
                                    footer = card.age,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private val hasWords = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)

    /** Every text the card draws, each as its own. */
    private fun texts(): List<String> =
        compose.onAllNodes(hasWords, useUnmergedTree = true).fetchSemanticsNodes()
            .flatMap { node -> node.config[SemanticsProperties.Text].map { it.text } }

    private fun footers() = compose.onAllNodes(hasTestTag(SCRIPT_FOOTER_TAG), useUnmergedTree = true)

    private fun ageText(): String =
        compose.onAllNodes(hasTestTag(FOOTER_TAG), useUnmergedTree = true).fetchSemanticsNodes().single()
            .config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun aged(text: String) = WidgetWords.Footer(text, text, failed = false, asked = true)

    private fun stat(footer: String?) =
        WidgetSpec.Stat(WidgetBase(title = "Air", footer = footer), "AQI 42", label = "Good · Lisbon")

    /** A large Plain card has the footer on its foot line; the same card without one draws as before. */
    @Test
    fun aLargeCardHasTheFooterOnItsFootLine() {
        show(stat("City sensors"), 196.dp, 196.dp)
        footers().assertCountEquals(1)
        assertTrue(texts().toString(), "City sensors" in texts())
        show(stat(null), 196.dp, 196.dp)
        footers().assertCountEquals(0)
    }

    /**
     * The age is never cut: a 2×2 card's short line gives it its short form and cuts the long footer;
     * a 4×2 card keeps it whole, with the note first.
     */
    @Test
    fun theAgeKeepsItsWordsAndTheFooterIsCutFirst() {
        show(stat(synced), 196.dp, 196.dp, age = aged("Updated 25 min ago"))
        footers().assertCountEquals(1)
        compose.runOnIdle { assertTrue(ageText(), ageText() == "25 min ago") }
        val list = WidgetSpec.Rows(
            WidgetBase(title = "Groceries", note = "2 left", footer = synced),
            listOf(WidgetSpec.Row("Oat milk"), WidgetSpec.Row("Lemons")),
        )
        show(list, 403.dp, 196.dp, age = aged("Updated 25 min ago"))
        compose.runOnIdle {
            assertTrue(ageText(), ageText() == "Updated 25 min ago")
            assertTrue(texts().toString(), "2 left" in texts())
        }
        footers().assertCountEquals(1)
    }

    /**
     * Wide: the footer takes the line where Last updated would show, also with it off; a list's rows
     * leave no line free. Bold leaves it out, as it leaves out the age.
     */
    @Test
    fun aWideCardHasItWhereItsContentLeavesALineFree() {
        show(WidgetSpec.Text(WidgetBase(title = "Tokyo", footer = "Weather service"), "18° Partly cloudy"), 403.dp, 94.dp)
        footers().assertCountEquals(1)
        show(WidgetSpec.Rows(WidgetBase(title = "Groceries", footer = synced), listOf(WidgetSpec.Row("Oat milk"))), 403.dp, 94.dp)
        footers().assertCountEquals(0)
        show(stat("City sensors"), 196.dp, 196.dp, look = WidgetLook.BOLD)
        footers().assertCountEquals(0)
        assertFalse(texts().toString(), "City sensors" in texts())
    }

    private fun toggles() = WidgetSpec.Toggles(
        WidgetBase(title = "Home"),
        listOf(
            WidgetSpec.Toggle("Heating", true, WidgetTap(args = "heating"), icon = "🔥", value = "21°"),
            WidgetSpec.Toggle("Lights", true, WidgetTap(args = "lights"), icon = "💡", value = "40%"),
            WidgetSpec.Toggle("Fan", false, WidgetTap(args = "fan"), icon = "🌀"),
        ),
    )

    /** A switch's value takes the place of "On" or "Off" in a tall cell, and a button gets the same line. */
    @Test
    fun aValueIsUnderTheLabelInATallCell() {
        show(toggles(), 403.dp, 94.dp)
        assertTrue(texts().toString(), texts().containsAll(listOf("Heating", "21°", "Lights", "40%", "Fan")))
        val buttons = WidgetSpec.Buttons(
            WidgetBase(title = "Thermostat"),
            listOf(
                WidgetSpec.Button("Colder", WidgetTap(args = "down"), icon = "−"),
                WidgetSpec.Button("Living room", WidgetTap(args = "room"), value = "set 22°"),
                WidgetSpec.Button("Warmer", WidgetTap(args = "up"), icon = "+"),
            ),
        )
        show(buttons, 403.dp, 94.dp)
        assertTrue(texts().toString(), "set 22°" in texts())
    }

    /** Cells too narrow for the label beside the icon show the icon alone: no value. */
    @Test
    fun aNarrowCellLeavesTheValueOut() {
        show(toggles(), 196.dp, 94.dp)
        assertFalse(texts().toString(), "21°" in texts())
    }

    private fun match() = WidgetSpec.Versus(
        WidgetBase(title = "Final"),
        listOf(
            WidgetSpec.Side("North", detail = "3rd · 41 pts"),
            WidgetSpec.Side("South", detail = "7th · 33 pts"),
        ),
        score = "2 – 1",
        line = "Second half",
        badge = "67'",
    )

    /** A side's detail is under its name at 4×2 and 4×1; a 2×1 card has the names under the marks and no detail. */
    @Test
    fun aVersusSideHasItsDetailWhereThereIsRoom() {
        show(match(), 403.dp, 196.dp)
        assertTrue(texts().toString(), texts().containsAll(listOf("North", "3rd · 41 pts", "South", "7th · 33 pts")))
        show(match(), 403.dp, 94.dp)
        assertTrue(texts().toString(), texts().containsAll(listOf("3rd · 41 pts", "7th · 33 pts")))
        show(match(), 196.dp, 94.dp)
        assertFalse(texts().toString(), "3rd · 41 pts" in texts())
    }

    /** A 4×1 versus card writes the footer on its last line itself, with Last updated off. */
    @Test
    fun aOneRowVersusCardHasTheFooterOnItsLastLine() {
        show(match().copy(base = WidgetBase(title = "Final", footer = "League table")), 403.dp, 94.dp)
        footers().assertCountEquals(1)
        assertTrue(texts().toString(), texts().containsAll(listOf("Second half", "League table")))
    }
}
