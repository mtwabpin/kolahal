package com.mtwabpin.kolahal.ui.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.terminal.Suggestion
import com.mtwabpin.kolahal.ui.theme.KolahalTheme
import com.mtwabpin.kolahal.ui.theme.Term
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The suggestion list's rows are `chips.height` tall, past 44 dp too (for a large font). */
@RunWith(AndroidJUnit4::class)
class SuggestionListTest {
    @get:Rule
    val compose = createComposeRule()

    @After
    fun tearDown() {
        Term.chipHeight = 44
    }

    private fun row() = compose.onNode(hasClickAction() and hasText("todo", substring = true))

    @Test
    fun rowsFollowTheChipHeightAllTheWay() {
        compose.setContent {
            KolahalTheme {
                Box(Modifier.width(360.dp)) {
                    SuggestionList(
                        listOf(
                            Suggestion.Completion("todo", "todo ", enter = true),
                            Suggestion.Completion("toggles", "toggles ")
                        ), {}, {})
                }
            }
        }
        for (h in listOf(44, 60, 30)) {
            compose.runOnIdle { Term.chipHeight = h }
            row().assertHeightIsEqualTo(h.dp)
        }
    }

    /** A private app's suggestion, made from one of the phone's own apps so its icon loads. */
    private fun privateApp(): com.mtwabpin.kolahal.apps.AppEntry {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val repo = com.mtwabpin.kolahal.apps.AppRepository.get(context)
        repo.refresh()
        com.mtwabpin.kolahal.waitUntil("the app list") { repo.apps.value.isNotEmpty() }
        val base = repo.apps.value.first { !it.hidden }
        return base.copy(
            label = "Zzprivate",
            profile = com.mtwabpin.kolahal.apps.Profile(base.profile.user, 4243, com.mtwabpin.kolahal.apps.ProfileKind.PRIVATE, quiet = false),
        )
    }

    /** The texts of the row or chip that shows Zzprivate: its name, and its tag if it has one. */
    private fun privateTexts() = compose.onNode(hasText("Zzprivate", substring = true)).fetchSemanticsNode()
        .config[androidx.compose.ui.semantics.SemanticsProperties.Text].map { it.text }

    /** Whether the row or chip shows the private space's tag: after the name in a row, beside it in a chip. */
    private fun tagged() = privateTexts().let { t -> t.any { "[S]" in it } || "S" in t }

    /** With icons, a private app's suggestion has the badge on its icon and no [S] tag; without, the tag shows. */
    @Test
    fun withIconsTheBadgeTakesTheTagsPlace() {
        val app = privateApp()
        var icons by androidx.compose.runtime.mutableStateOf(true)
        var strip by androidx.compose.runtime.mutableStateOf(false)
        compose.setContent {
            KolahalTheme {
                Box(Modifier.width(360.dp)) {
                    val items = listOf(Suggestion.App(app, enter = true))
                    if (strip) SuggestionStrip(items, 1, {}, icons) else SuggestionList(items, {}, {}, icons)
                }
            }
        }
        for (s in listOf(false, true)) {
            compose.runOnIdle { strip = s; icons = true }
            compose.waitForIdle()
            assertFalse(privateTexts().toString(), tagged())
            compose.runOnIdle { icons = false }
            compose.waitForIdle()
            assertTrue(privateTexts().toString(), tagged())
        }
    }
}
