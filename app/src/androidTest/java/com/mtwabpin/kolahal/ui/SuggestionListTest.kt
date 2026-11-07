package com.mtwabpin.kolahal.ui

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
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The suggestion list's rows are `chips.height` tall, past 44 dp too (for a large font). */
@RunWith(AndroidJUnit4::class)
class SuggestionListTest {
    @get:Rule val compose = createComposeRule()

    @After fun tearDown() { Term.chipHeight = 44 }

    private fun row() = compose.onNode(hasClickAction() and hasText("todo", substring = true))

    @Test fun rowsFollowTheChipHeightAllTheWay() {
        compose.setContent {
            KolahalTheme {
                Box(Modifier.width(360.dp)) {
                    SuggestionList(listOf(Suggestion.Completion("todo", "todo ", enter = true), Suggestion.Completion("toggles", "toggles ")), {}, {})
                }
            }
        }
        for (h in listOf(44, 60, 30)) {
            compose.runOnIdle { Term.chipHeight = h }
            row().assertHeightIsEqualTo(h.dp)
        }
    }
}
