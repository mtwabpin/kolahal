package com.mtwabpin.kolahal.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mtwabpin.kolahal.terminal.LogRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The log's day dividers. */
@RunWith(AndroidJUnit4::class)
class DayDividerTest {
    @get:Rule val compose = createComposeRule()

    /** When the day changes with the log on screen (home passes in the new day at midnight), "Yesterday" becomes its date. */
    @Test fun yesterdayMovesAtMidnight() {
        val day = LocalDate.of(2026, 11, 1)
        var today by mutableStateOf(day.plusDays(1))
        compose.setContent { KolahalTheme { DayDivider(LogRow.Day(day), today) } }
        compose.onNodeWithText("── Yesterday ──").assertExists()
        compose.runOnIdle { today = day.plusDays(2) }
        val shown = compose.runOnIdle { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()).format(day) }
        compose.onNodeWithText("── $shown ──").assertExists()
    }
}
